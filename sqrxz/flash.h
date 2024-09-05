#ifndef _FLASH_H_
#define _FLASH_H_

#include "ngpc.h"

extern u8 data[1024];

void flash(void *data);
void getSavedData();

#endif
