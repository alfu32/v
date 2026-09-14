#ifndef V_JAR_MACOS_BOOTSTRAP_SEMAPHORE_H
#define V_JAR_MACOS_BOOTSTRAP_SEMAPHORE_H

// The portable vc snapshot is generated on Linux and can still contain the
// POSIX sem_timedwait call when it is used to bootstrap V on Apple platforms.
// Apple libc exposes sem_trywait but not sem_timedwait, so provide the small
// timed-wait adapter needed only by that bootstrap translation unit.
#ifndef _POSIX_C_SOURCE
#define _POSIX_C_SOURCE 200809L
#endif
#define _DARWIN_C_SOURCE 1
#include <errno.h>
#include <semaphore.h>
#include <time.h>

static int v_jar_sem_timedwait(sem_t *sem, const struct timespec *deadline) {
    for (;;) {
        if (sem_trywait(sem) == 0) {
            return 0;
        }
        if (errno != EAGAIN && errno != EINTR) {
            return -1;
        }
        struct timespec now;
        clock_gettime(CLOCK_REALTIME, &now);
        if (now.tv_sec > deadline->tv_sec || (now.tv_sec == deadline->tv_sec
                && now.tv_nsec >= deadline->tv_nsec)) {
            errno = ETIMEDOUT;
            return -1;
        }
        struct timespec pause = {0, 1000000};
        nanosleep(&pause, NULL);
    }
}

#define sem_timedwait v_jar_sem_timedwait

#endif
