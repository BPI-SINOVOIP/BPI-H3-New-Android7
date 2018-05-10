#ifndef IR_KEY_CODE_H
#define IR_KEY_CODE_H

#include "multi_ir.h"
#include "minui/ir_keycode.h"
#include <linux/input.h>

const int IR_KEYCODE_HOME = 3;
const int IR_KEYCODE_UP =  19;
const int IR_KEYCODE_DOWN = 20;
const int IR_KEYCODE_ENTER =  23;

extern int convertKey(struct input_event* event);
IR_KEY_CODE ir_convert_keycode(int ir_keycode){
	switch(ir_keycode){
		case IR_KEYCODE_HOME:
			return IR_KEY_HOME;
		case IR_KEYCODE_UP:
			return IR_KEY_UP;
		case IR_KEYCODE_DOWN:
			return IR_KEY_DOWN;
		case IR_KEYCODE_ENTER:
			return IR_KEY_ENTER;
		default:
			break;
	}
	return IR_KEY_FAULT;
}

int ir_handle_input(input_event ev,RecoveryUI *ui){


	//the ev.value contains the device infomation,key code and key value
	int ir_key_type = ev.value >> 24;
	int ir_key_code = ev.value & 0x000000ff;
	int ir_device = (ev.value >> 8) & 0x0000ffff;
	//printf("the  type is %x ,code is %x,device is %x \n",ir_key_type,ir_key_code,ir_device);

    //use the multir ir to parse key code
	IR_KEY_CODE ir_keycode = ir_convert_keycode(convertKey(&ev));
    int handled = 0;

	if(ir_key_type == EV_KEY && ir_key_code <= KEY_MAX){
		switch(ir_keycode){
			case IR_KEY_UP:
				ui->DeviceProcessKey(KEY_UP,1);
				ui->DeviceProcessKey(KEY_UP,0);
				handled = 1;
				break;
			case IR_KEY_DOWN:
				ui->DeviceProcessKey(KEY_DOWN,1);
				ui->DeviceProcessKey(KEY_DOWN,0);
				handled = 1;
				break;
			case IR_KEY_ENTER:
				ui->DeviceProcessKey(KEY_ENTER,1);
				ui->DeviceProcessKey(KEY_ENTER,0);
				handled = 1;
				break;
			case IR_KEY_HOME:
				ui->DeviceProcessKey(KEY_HOME,1);
				ui->DeviceProcessKey(KEY_HOME,0);
				handled = 1;
				break;
			default:break;
		}
	}
	return handled;
}

#endif
