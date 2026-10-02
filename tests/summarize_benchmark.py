import sys, statistics as st
d = sys.argv[1]
def rows(f): return [[float(x) for x in l.split(',')] for l in open(f'{d}/{f}') if l.strip()]
def col(r, i): return [x[i] for x in r]
def m(v): return st.mean(v)
def sd(v): return st.stdev(v) if len(v) > 1 else 0.0
s = rows('standalone.csv'); u = rows('multi.csv')
n = len(s)
# standalone: 0 step_us_med 1 step_us_mean 2 run_ms_med 3 run_ms_mean 4 cpu_ms 5 rss 6 hwm
# multi:      0 rtt_med 1 rtt_mean 2 step_med 3 step_mean 4 step_p95 5 run_med 6 run_mean 7-9 cpu ui/core/log 10-12 rss 13-15 hwm
S = dict(step=m(col(s,0)), run=m(col(s,2)), cpu=m(col(s,4)), rss=m(col(s,5)), hwm=m(col(s,6)))
U = dict(rtt=m(col(u,0)), step=m(col(u,2)), p95=m(col(u,4)), run=m(col(u,5)),
         cui=m(col(u,7)), cco=m(col(u,8)), clo=m(col(u,9)),
         rui=m(col(u,10)), rco=m(col(u,11)), rlo=m(col(u,12)),
         hui=m(col(u,13)), hco=m(col(u,14)), hlo=m(col(u,15)), big=m(col(u,16)))
ss = [float(x) for x in open(f'{d}/startup_standalone.txt')]
sm = [float(x) for x in open(f'{d}/startup_multi.txt')]
out = []
p = out.append
p(f"Benchmark: {n} independent sessions per variant (each session = fresh JVM(s)); values are the mean of the per-session medians.\n")
p("| Metric | Standalone | Multi-process | Difference |")
p("|---|---|---|---|")
p(f"| RUN: 8001 instructions to HALT, median (ms) | {S['run']:.2f} | {U['run']:.2f} | {U['run']-S['run']:+.2f} ms ({(U['run']/S['run']-1)*100:+.0f} %) |")
p(f"| STEP: one instruction, median (us) | {S['step']:.2f} (direct call) | {U['step']:.1f} (round trip) | {U['step']-S['step']:+.1f} us |")
p(f"| STEP: 95th percentile (us) | - | {U['p95']:.1f} | - |")
p(f"| STEP with an 8 KB program loaded, median (us) | - | {U['big']:.1f} | snapshot carries the whole program image |")
p(f"| **IPC overhead** = GET_STATE round trip, median (us) | 0 | {U['rtt']:.1f} | {U['rtt']:+.1f} us per command |")
p(f"| CPU time used during measured phase, all processes (ms) | {S['cpu']:.0f} | {U['cui']+U['cco']+U['clo']:.0f} (UI {U['cui']:.0f} + Core {U['cco']:.0f} + Logger {U['clo']:.0f}) | {U['cui']+U['cco']+U['clo']-S['cpu']:+.0f} ms |")
p(f"| Memory, resident set (MB) | {S['rss']:.0f} | {U['rui']+U['rco']+U['rlo']:.0f} (UI {U['rui']:.0f} + Core {U['rco']:.0f} + Logger {U['rlo']:.0f}) | {U['rui']+U['rco']+U['rlo']-S['rss']:+.0f} MB |")
p(f"| Memory, peak resident (MB) | {S['hwm']:.0f} | {U['hui']+U['hco']+U['hlo']:.0f} (UI {U['hui']:.0f} + Core {U['hco']:.0f} + Logger {U['hlo']:.0f}) | {U['hui']+U['hco']+U['hlo']-S['hwm']:+.0f} MB |")
p(f"| Start-up to clean exit (ms), mean of {len(ss)} | {st.mean(ss):.0f} | {st.mean(sm):.0f} | {st.mean(sm)-st.mean(ss):+.0f} ms |")
p("")
p(f"Per-session spread (stdev across sessions): standalone RUN {sd(col(s,2)):.2f} ms, multi RUN {sd(col(u,5)):.2f} ms, multi STEP {sd(col(u,2)):.1f} us, IPC RTT {sd(col(u,0)):.1f} us.")
open(f'{d}/results.md','w').write('\n'.join(out)+'\n')
print('\n'.join(out))
