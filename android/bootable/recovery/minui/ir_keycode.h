#include "ui.h"

enum IR_KEY_CODE {
	IR_KEY_FAULT = -1,
	IR_KEY_HOME,
	IR_KEY_UP,
	IR_KEY_DOWN,
	IR_KEY_ENTER
};

IR_KEY_CODE ir_convert_keycode(int ir_keycode);
int ir_handle_input(input_event ev,RecoveryUI *ui);
