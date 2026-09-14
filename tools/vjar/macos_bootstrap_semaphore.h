#ifndef V_JAR_MACOS_BOOTSTRAP_SEMAPHORE_H
#define V_JAR_MACOS_BOOTSTRAP_SEMAPHORE_H

// The portable vc snapshot is generated on Linux. Its embedded sync runtime
// uses unnamed POSIX semaphores, which macOS does not implement. Keep the
// snapshot layout unchanged and redirect only its semaphore calls to a small
// pthread mutex/condition-variable implementation while bootstrapping V1.

#if defined(__APPLE__)

#include <errno.h>
#include <pthread.h>
#include <semaphore.h>
#include <stdlib.h>
#include <time.h>

typedef struct v_jar_sem_entry {
    sem_t *key;
    pthread_mutex_t mutex;
    pthread_cond_t cond;
    unsigned int count;
    struct v_jar_sem_entry *next;
} v_jar_sem_entry;

static pthread_mutex_t v_jar_sem_entries_mutex = PTHREAD_MUTEX_INITIALIZER;
static v_jar_sem_entry *v_jar_sem_entries;

static v_jar_sem_entry *v_jar_sem_find_locked(sem_t *key) {
    v_jar_sem_entry *entry = v_jar_sem_entries;
    while (entry) {
        if (entry->key == key) {
            return entry;
        }
        entry = entry->next;
    }
    return NULL;
}

static v_jar_sem_entry *v_jar_sem_find(sem_t *key) {
    pthread_mutex_lock(&v_jar_sem_entries_mutex);
    v_jar_sem_entry *entry = v_jar_sem_find_locked(key);
    pthread_mutex_unlock(&v_jar_sem_entries_mutex);
    if (!entry) {
        errno = EINVAL;
    }
    return entry;
}

static int v_jar_sem_init(sem_t *key, int pshared, unsigned int value) {
    if (pshared != 0) {
        errno = ENOSYS;
        return -1;
    }
    v_jar_sem_entry *entry = (v_jar_sem_entry *)calloc(1, sizeof(*entry));
    if (!entry) {
        errno = ENOMEM;
        return -1;
    }
    entry->key = key;
    entry->count = value;
    if (pthread_mutex_init(&entry->mutex, NULL) != 0) {
        free(entry);
        errno = EINVAL;
        return -1;
    }
    if (pthread_cond_init(&entry->cond, NULL) != 0) {
        pthread_mutex_destroy(&entry->mutex);
        free(entry);
        errno = EINVAL;
        return -1;
    }
    pthread_mutex_lock(&v_jar_sem_entries_mutex);
    entry->next = v_jar_sem_entries;
    v_jar_sem_entries = entry;
    pthread_mutex_unlock(&v_jar_sem_entries_mutex);
    return 0;
}

static int v_jar_sem_post(sem_t *key) {
    v_jar_sem_entry *entry = v_jar_sem_find(key);
    if (!entry) {
        return -1;
    }
    pthread_mutex_lock(&entry->mutex);
    entry->count++;
    pthread_cond_signal(&entry->cond);
    pthread_mutex_unlock(&entry->mutex);
    return 0;
}

static int v_jar_sem_wait(sem_t *key) {
    v_jar_sem_entry *entry = v_jar_sem_find(key);
    if (!entry) {
        return -1;
    }
    pthread_mutex_lock(&entry->mutex);
    while (entry->count == 0) {
        pthread_cond_wait(&entry->cond, &entry->mutex);
    }
    entry->count--;
    pthread_mutex_unlock(&entry->mutex);
    return 0;
}

static int v_jar_sem_trywait(sem_t *key) {
    v_jar_sem_entry *entry = v_jar_sem_find(key);
    if (!entry) {
        return -1;
    }
    pthread_mutex_lock(&entry->mutex);
    if (entry->count == 0) {
        pthread_mutex_unlock(&entry->mutex);
        errno = EAGAIN;
        return -1;
    }
    entry->count--;
    pthread_mutex_unlock(&entry->mutex);
    return 0;
}

static int v_jar_sem_timedwait(sem_t *key, const struct timespec *deadline) {
    v_jar_sem_entry *entry = v_jar_sem_find(key);
    if (!entry) {
        return -1;
    }
    pthread_mutex_lock(&entry->mutex);
    while (entry->count == 0) {
        int result = pthread_cond_timedwait(&entry->cond, &entry->mutex, deadline);
        if (result == ETIMEDOUT) {
            pthread_mutex_unlock(&entry->mutex);
            errno = ETIMEDOUT;
            return -1;
        }
        if (result != 0) {
            pthread_mutex_unlock(&entry->mutex);
            errno = result;
            return -1;
        }
    }
    entry->count--;
    pthread_mutex_unlock(&entry->mutex);
    return 0;
}

static int v_jar_sem_destroy(sem_t *key) {
    pthread_mutex_lock(&v_jar_sem_entries_mutex);
    v_jar_sem_entry **link = &v_jar_sem_entries;
    while (*link && (*link)->key != key) {
        link = &(*link)->next;
    }
    v_jar_sem_entry *entry = *link;
    if (entry) {
        *link = entry->next;
    }
    pthread_mutex_unlock(&v_jar_sem_entries_mutex);
    if (!entry) {
        errno = EINVAL;
        return -1;
    }
    pthread_mutex_destroy(&entry->mutex);
    pthread_cond_destroy(&entry->cond);
    free(entry);
    return 0;
}

#define sem_init v_jar_sem_init
#define sem_post v_jar_sem_post
#define sem_wait v_jar_sem_wait
#define sem_trywait v_jar_sem_trywait
#define sem_timedwait v_jar_sem_timedwait
#define sem_destroy v_jar_sem_destroy

#endif // defined(__APPLE__)

#endif // V_JAR_MACOS_BOOTSTRAP_SEMAPHORE_H
