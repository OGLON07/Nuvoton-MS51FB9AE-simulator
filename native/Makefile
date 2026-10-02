# Builds the native POSIX launcher:  make -C native   ->  bin/nuvoton_launcher
CC     ?= gcc
CFLAGS ?= -O2 -Wall -Wextra -std=gnu11

../bin/nuvoton_launcher: posix_launcher.c
	mkdir -p ../bin
	$(CC) $(CFLAGS) -o $@ $<

clean:
	rm -f ../bin/nuvoton_launcher
