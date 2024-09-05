#ifndef _HICOLOR_H
#define _HICOLOR_H

#include "types.h"

void hc_load(u16 *);
void hc_showEmu(u16 *);
void hc_showHW(u16 *);
void hc_detect();
u8 hc_emu();

#endif
