#include "input.h"
#include <pthread.h>
#include <string.h>

static int g_initialized = 0;
static pthread_mutex_t g_input_mutex = PTHREAD_MUTEX_INITIALIZER;

typedef struct {
    int type;
    int code;
    int value;
} InputEvent;

#define MAX_EVENTS 1024
static InputEvent g_event_queue[MAX_EVENTS];
static int g_event_head = 0;
static int g_event_tail = 0;

int input_init(void) {
    pthread_mutex_lock(&g_input_mutex);
    g_initialized = 1;
    g_event_head = 0;
    g_event_tail = 0;
    memset(g_event_queue, 0, sizeof(g_event_queue));
    pthread_mutex_unlock(&g_input_mutex);
    return 0;
}

void input_cleanup(void) {
    pthread_mutex_lock(&g_input_mutex);
    g_initialized = 0;
    pthread_mutex_unlock(&g_input_mutex);
}

void input_send_event(int type, int code, int value) {
    pthread_mutex_lock(&g_input_mutex);
    
    if (!g_initialized) {
        pthread_mutex_unlock(&g_input_mutex);
        return;
    }
    
    int next_tail = (g_event_tail + 1) % MAX_EVENTS;
    if (next_tail != g_event_head) {
        g_event_queue[g_event_tail].type = type;
        g_event_queue[g_event_tail].code = code;
        g_event_queue[g_event_tail].value = value;
        g_event_tail = next_tail;
    }
    
    pthread_mutex_unlock(&g_input_mutex);
}

int input_get_event(InputEvent* event) {
    pthread_mutex_lock(&g_input_mutex);
    
    if (g_event_head == g_event_tail) {
        pthread_mutex_unlock(&g_input_mutex);
        return 0;
    }
    
    *event = g_event_queue[g_event_head];
    g_event_head = (g_event_head + 1) % MAX_EVENTS;
    
    pthread_mutex_unlock(&g_input_mutex);
    return 1;
}
