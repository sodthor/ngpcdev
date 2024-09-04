#include "ngpc.h"
#include "carthdr.h"
#include "library.h"
#include "hicolor.h"

#include "test.hh"
#include "gx.hh"
#include "tulipes.hh"

#define	 NB_IMAGES	3

void *curimg;

void __interrupt myVBL()
{
	WATCHDOG = WATCHDOG_CLEAR;
	if (USR_SHUTDOWN) { SysShutdown(); while (1); }
	if (hc_emu())
		hc_showEmu(curimg);
	else
		hc_showHW(curimg);
}

void showHC(void *img)
{
	curimg=0;
	hc_load(img);
	curimg=img;
}

void main()
{
	u8 i,j;

	void* images[NB_IMAGES] = {(void*)TEST_ID,(void*)GX_ID,(void*)TULIPES_ID};

	InitNGPC();

	SetBackgroundColour(RGB(0, 0, 0));

	hc_detect();

	i=0;
	showHC(images[0]);

	__asm(" di");
	VBL_INT = myVBL;
	__asm(" ei");

	while (1)
	{
        	while((((j=JOYPAD)&J_A) == 0) && ((j&J_B) == 0));
        	while((((JOYPAD)&J_A) != 0) || (((JOYPAD)&J_B) != 0));
		i = j&J_B? (i>0?i-1:NB_IMAGES-1) : (i+1)%NB_IMAGES;
		showHC(images[i]);
	}
}
