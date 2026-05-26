#include "display.h"
#include <stdlib.h>
#include <string.h>
#include <pthread.h>

static DisplayInfo g_display_info = {0, 0, 0};
static int g_initialized = 0;
static int g_ready = 0;
static void* g_framebuffer = NULL;
static pthread_mutex_t g_display_mutex = PTHREAD_MUTEX_INITIALIZER;

int display_init(void) {
    pthread_mutex_lock(&g_display_mutex);
    
    if (g_initialized) {
        pthread_mutex_unlock(&g_display_mutex);
        return 0;
    }
    
    g_display_info.width = 1920;
    g_display_info.height = 1080;
    g_framebuffer = malloc(g_display_info.width * g_display_info.height * 4);
    
    if (g_framebuffer) {
        memset(g_framebuffer, 0, g_display_info.width * g_display_info.height * 4);
        g_display_info.framebuffer = (uint64_t)g_framebuffer;
        g_initialized = 1;
    }
    
    pthread_mutex_unlock(&g_display_mutex);
    
    return g_initialized ? 0 : -1;
}

void display_cleanup(void) {
    pthread_mutex_lock(&g_display_mutex);
    
    if (g_framebuffer) {
        free(g_framebuffer);
        g_framebuffer = NULL;
    }
    
    g_initialized = 0;
    g_ready = 0;
    g_display_info.framebuffer = 0;
    
    pthread_mutex_unlock(&g_display_mutex);
}

int display_is_ready(void) {
    return g_ready;
}

DisplayInfo display_get_info(void) {
    return g_display_info;
}

void* display_get_framebuffer(void) {
    return g_framebuffer;
}

void display_set_ready(int width, int height, void* fb) {
    pthread_mutex_lock(&g_display_mutex);
    
    g_display_info.width = width;
    g_display_info.height = height;
    
    if (g_framebuffer) {
        free(g_framebuffer);
    }
    g_framebuffer = fb;
    g_display_info.framebuffer = (uint64_t)fb;
    g_ready = 1;
    
    pthread_mutex_unlock(&g_display_mutex);
}

void display_shutdown(void) {
    pthread_mutex_lock(&g_display_mutex);
    g_ready = 0;
    pthread_mutex_unlock(&g_display_mutex);
}
