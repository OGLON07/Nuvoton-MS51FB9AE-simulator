/*
 * posix_launcher.c - Native POSIX launcher for the 3-process simulator.
 *
 * Creates the three simulator processes and connects them ONLY with
 * anonymous POSIX pipes, using pipe(), fork(), dup2() and exec():
 *
 *        UI process                Core process              Logger process
 *   (Main.UI.UIProcessMain)  (Main.core.CoreProcess)   (Main.Logger.LoggerProcess)
 *
 *   fd1 (stdout) ---ui_to_core--->  fd0 (stdin)
 *   fd0 (stdin)  <--core_to_ui----  fd1 (stdout)
 *                                   fd3 -----core_to_log----->  fd0 (stdin)
 *
 * Three pipes, one direction each:
 *   ui_to_core   : commands   (UI   -> Core)
 *   core_to_ui   : responses  (Core -> UI)
 *   core_to_log  : log lines  (Core -> Logger)
 * stderr (fd 2) of every process stays on the terminal for diagnostics.
 *
 * Shutdown needs no signals in the normal case: when the UI exits, the
 * write end of ui_to_core closes, Core sees EOF and exits, which closes
 * core_to_log, so the Logger sees EOF and exits. This is why every process
 * must close the pipe ends it does not use (otherwise EOF never arrives).
 *
 * The launcher waits for all three children and, as a safety net, sends
 * SIGTERM (then SIGKILL) if one of them does not exit in time.
 *
 * Build:  make -C native        Usage: see usage() below.
 */
#define _GNU_SOURCE
#include <errno.h>
#include <fcntl.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <time.h>
#include <unistd.h>

#define NCHILD 3
enum { UI = 0, CORE = 1, LOGGER = 2 };
static const char *NAMES[NCHILD] = { "UI", "CORE", "LOGGER" };

static pid_t pids[NCHILD];
static int   alive[NCHILD];
static int   exit_status[NCHILD];
static volatile sig_atomic_t stop_requested = 0;

/* ---------- configuration (command line) ---------- */
static const char *java_bin   = "java";
static const char *classpath  = "out";
static const char *ui_class   = "Main.UI.UIProcessMain";
static const char *step_delay = NULL;      /* --step-delay-ms */
static const char *log_file   = NULL;      /* --log-file      */
static int grace_seconds      = 5;

static void usage(const char *prog) {
    fprintf(stderr,
        "Usage: %s [options]\n"
        "  --classpath DIR      compiled Java classes (default: out)\n"
        "  --java PATH          java executable       (default: java)\n"
        "  --ui-class CLASS     class run as the UI process (default: Main.UI.UIProcessMain)\n"
        "  --step-delay-ms N    delay between instructions during RUN (Core default 120)\n"
        "  --log-file PATH      Logger output file    (default: simulator.log)\n",
        prog);
}

static void die(const char *what) {
    perror(what);
    exit(2);
}

static long long now_ms(void) {
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return (long long)ts.tv_sec * 1000 + ts.tv_nsec / 1000000;
}

static void on_stop_signal(int sig) {
    (void)sig;
    stop_requested = 1;
}

/* Move fd to a high number so later dup2() calls cannot clobber it. */
static int high(int fd) {
    int n = fcntl(fd, F_DUPFD, 10);
    if (n < 0) die("fcntl(F_DUPFD)");
    return n;
}

/* Close every descriptor >= first (the child must only keep what it needs). */
static void close_from(int first) {
    long max = sysconf(_SC_OPEN_MAX);
    if (max < 0 || max > 4096) max = 4096;
    for (int fd = first; fd < max; fd++) close(fd);
}

/* In the child: restore signal mask, then exec the JVM. Never returns. */
static void exec_java(const sigset_t *oldmask, char *const argv[]) {
    sigprocmask(SIG_SETMASK, oldmask, NULL);
    execvp(argv[0], argv);
    fprintf(stderr, "[launcher] exec %s failed: %s\n", argv[0], strerror(errno));
    _exit(127);
}

/* ---------- the three children ---------- */

static pid_t spawn_logger(int core_to_log[2], const sigset_t *oldmask) {
    pid_t pid = fork();
    if (pid < 0) die("fork(logger)");
    if (pid == 0) {
        int in = high(core_to_log[0]);
        if (dup2(in, 0) < 0) die("dup2(logger stdin)");       /* pipe read end -> stdin */
        close_from(3);

        char prop[1100] = "-Dsimulator.log=simulator.log";
        if (log_file) snprintf(prop, sizeof prop, "-Dsimulator.log=%s", log_file);
        char *argv[] = { (char *)java_bin, prop, "-cp", (char *)classpath,
                         "Main.Logger.LoggerProcess", "--ipc", NULL };
        exec_java(oldmask, argv);
    }
    return pid;
}

static pid_t spawn_core(int ui_to_core[2], int core_to_ui[2], int core_to_log[2],
                        const sigset_t *oldmask) {
    pid_t pid = fork();
    if (pid < 0) die("fork(core)");
    if (pid == 0) {
        int in  = high(ui_to_core[0]);
        int out = high(core_to_ui[1]);
        int log = high(core_to_log[1]);
        if (dup2(in,  0) < 0) die("dup2(core stdin)");        /* commands in   */
        if (dup2(out, 1) < 0) die("dup2(core stdout)");       /* responses out */
        if (dup2(log, 3) < 0) die("dup2(core log fd)");       /* logs out      */
        close_from(4);

        char *argv[12]; int n = 0;
        argv[n++] = (char *)java_bin;
        argv[n++] = "-cp";
        argv[n++] = (char *)classpath;
        argv[n++] = "Main.core.CoreProcess";
        argv[n++] = "--ipc";
        argv[n++] = "--log-fd";
        argv[n++] = "3";
        if (step_delay) { argv[n++] = "--step-delay-ms"; argv[n++] = (char *)step_delay; }
        argv[n] = NULL;
        exec_java(oldmask, argv);
    }
    return pid;
}

static pid_t spawn_ui(int ui_to_core[2], int core_to_ui[2], const sigset_t *oldmask) {
    pid_t pid = fork();
    if (pid < 0) die("fork(ui)");
    if (pid == 0) {
        int in  = high(core_to_ui[0]);
        int out = high(ui_to_core[1]);
        if (dup2(in,  0) < 0) die("dup2(ui stdin)");          /* responses in */
        if (dup2(out, 1) < 0) die("dup2(ui stdout)");         /* commands out */
        close_from(3);

        char *argv[] = { (char *)java_bin, "-cp", (char *)classpath,
                         (char *)ui_class, "--ipc", NULL };
        exec_java(oldmask, argv);
    }
    return pid;
}

/* ---------- supervision ---------- */

static void kill_all(int sig) {
    for (int i = 0; i < NCHILD; i++)
        if (alive[i]) kill(pids[i], sig);
}

static int reap_children(int *first_exit_seen, long long *deadline) {
    int status; pid_t p; int reaped = 0;
    while ((p = waitpid(-1, &status, WNOHANG)) > 0) {
        for (int i = 0; i < NCHILD; i++) {
            if (pids[i] == p && alive[i]) {
                alive[i] = 0;
                exit_status[i] = WIFEXITED(status) ? WEXITSTATUS(status) : 128 + WTERMSIG(status);
                fprintf(stderr, "[launcher] %-6s pid=%d exited (status %d)\n",
                        NAMES[i], (int)p, exit_status[i]);
                reaped++;
                if (!*first_exit_seen) {
                    *first_exit_seen = 1;
                    *deadline = now_ms() + grace_seconds * 1000LL;
                }
            }
        }
    }
    return reaped;
}

int main(int argc, char **argv) {
    for (int i = 1; i < argc; i++) {
        const char *a = argv[i];
        int has_val = (i + 1 < argc);
        if      (!strcmp(a, "--classpath")     && has_val) classpath  = argv[++i];
        else if (!strcmp(a, "--java")          && has_val) java_bin   = argv[++i];
        else if (!strcmp(a, "--ui-class")      && has_val) ui_class   = argv[++i];
        else if (!strcmp(a, "--step-delay-ms") && has_val) step_delay = argv[++i];
        else if (!strcmp(a, "--log-file")      && has_val) log_file   = argv[++i];
        else { usage(argv[0]); return 2; }
    }

    /* 1. Create the three pipes BEFORE forking, so children inherit them. */
    int ui_to_core[2], core_to_ui[2], core_to_log[2];
    if (pipe(ui_to_core)  < 0) die("pipe(ui_to_core)");
    if (pipe(core_to_ui)  < 0) die("pipe(core_to_ui)");
    if (pipe(core_to_log) < 0) die("pipe(core_to_log)");

    /* Block SIGCHLD so sigtimedwait() below can wait for it without races. */
    sigset_t chld, oldmask;
    sigemptyset(&chld);
    sigaddset(&chld, SIGCHLD);
    sigprocmask(SIG_BLOCK, &chld, &oldmask);

    struct sigaction sa;
    memset(&sa, 0, sizeof sa);
    sa.sa_handler = on_stop_signal;
    sigaction(SIGINT,  &sa, NULL);
    sigaction(SIGTERM, &sa, NULL);

    /* 2. Fork+exec the children. Logger first, UI last (UI gets sibling pids via env). */
    pids[LOGGER] = spawn_logger(core_to_log, &oldmask);
    pids[CORE]   = spawn_core(ui_to_core, core_to_ui, core_to_log, &oldmask);

    char buf[32];
    snprintf(buf, sizeof buf, "%d", (int)pids[CORE]);   setenv("NUVOTON_CORE_PID", buf, 1);
    snprintf(buf, sizeof buf, "%d", (int)pids[LOGGER]); setenv("NUVOTON_LOGGER_PID", buf, 1);
    pids[UI]     = spawn_ui(ui_to_core, core_to_ui, &oldmask);

    for (int i = 0; i < NCHILD; i++) alive[i] = 1;

    /* 3. The launcher must close ALL pipe ends: it only supervises. If it kept
     *    one open, the readers would never see EOF at shutdown. */
    close(ui_to_core[0]);  close(ui_to_core[1]);
    close(core_to_ui[0]);  close(core_to_ui[1]);
    close(core_to_log[0]); close(core_to_log[1]);

    fprintf(stderr, "[launcher] pid=%d  LOGGER pid=%d  CORE pid=%d  UI pid=%d\n",
            (int)getpid(), (int)pids[LOGGER], (int)pids[CORE], (int)pids[UI]);
    fprintf(stderr, "[launcher] pipes: ui_to_core(commands) core_to_ui(responses) core_to_log(logs)\n");

    /* 4. Supervise until every child has exited. */
    int  remaining = NCHILD, first_exit_seen = 0, term_sent = 0, kill_sent = 0;
    long long deadline = 0;

    for (;;) {
        remaining -= reap_children(&first_exit_seen, &deadline);
        if (remaining <= 0) break;

        long long now = now_ms();
        if (stop_requested && !term_sent) {
            fprintf(stderr, "[launcher] stop requested -> SIGTERM to children\n");
            kill_all(SIGTERM); term_sent = 1; deadline = now + 3000;
        } else if (first_exit_seen && now >= deadline) {
            if (!term_sent) {
                fprintf(stderr, "[launcher] children still running %ds after first exit -> SIGTERM\n", grace_seconds);
                kill_all(SIGTERM); term_sent = 1; deadline = now + 3000;
            } else if (!kill_sent) {
                fprintf(stderr, "[launcher] still running -> SIGKILL\n");
                kill_all(SIGKILL); kill_sent = 1; deadline = now + 3000;
            }
        }

        struct timespec ts, *tp = NULL;
        if (first_exit_seen || term_sent) {
            long long wait = deadline - now_ms();
            if (wait < 1) wait = 1;
            ts.tv_sec = wait / 1000; ts.tv_nsec = (wait % 1000) * 1000000L;
            tp = &ts;
        }
        sigtimedwait(&chld, NULL, tp);       /* wakes on SIGCHLD, timeout, or EINTR */
    }

    int rc = 0;
    for (int i = 0; i < NCHILD; i++) if (exit_status[i] != 0) rc = 1;
    fprintf(stderr, "[launcher] all processes exited -> %s\n", rc == 0 ? "clean shutdown" : "NON-ZERO exit");
    return rc;
}
