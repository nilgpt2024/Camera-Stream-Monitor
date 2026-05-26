#ifndef DISPLAY_H
#define DISPLAY_H

#include <stdint.h>

typedef struct {
    int width;
    int height;
    uint64_t framebuffer;
} DisplayInfo;

int display_init(void);
void display_cleanup(void);
int display_is_ready(void);
DisplayInfo display_get_info(void);
void* display_get_framebuffer(void);
void display_shutdown(void);

#endif
