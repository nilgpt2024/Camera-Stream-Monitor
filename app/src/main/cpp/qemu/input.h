#ifndef INPUT_H
#define INPUT_H

void input_send_event(int type, int code, int value);
int input_init(void);
void input_cleanup(void);

#define EV_SYN 0x00
#define EV_KEY 0x01
#define EV_REL 0x02
#define EV_ABS 0x03

#define BTN_LEFT 0x110
#define BTN_RIGHT 0x111
#define BTN_MIDDLE 0x112

#define REL_X 0x00
#define REL_Y 0x01

#define ABS_X 0x00
#define ABS_Y 0x01

#endif
