#include "ngpc.h"

u8 data[1024];

#define	MAGIC_NB	0xcafebabe	// java rules !

void flash(void *data)
{
#ifndef WIN32
	__ASM("SAVEOFFSET	EQU	0x1e0000");

	__ASM("BLOCK_NB		EQU	30");
	__ASM("VECT_FLASHWRITE	EQU	6");
	__ASM("VECT_FLASHERS	EQU	8");
	__ASM("rWDCR		EQU	0x6f");
	__ASM("WD_CLR		EQU	0x4e");

	// Erase block first (mandatory) : 64kb for only 1024 bytes
	__ASM("	ld	ra3,0");
	__ASM("	ld	rb3,BLOCK_NB");
	__ASM("	ld	rw3,VECT_FLASHERS");
	__ASM("	ld	(rWDCR),WD_CLR");
	__ASM("	swi	1");

	// Then write data
	__ASM("	ld	ra3,0");
	__ASM("	ld	rbc3,4");	// 1024 bytes
	__ASM("	ld	xhl,(xsp+4)");
	__ASM("	ld	xhl3,xhl");
	__ASM("	ld	xde3,SAVEOFFSET");
	__ASM("	ld	rw3,VECT_FLASHWRITE");
	__ASM("	ld	(rWDCR),WD_CLR");
	__ASM("	swi	1");

	__ASM("	ld	(rWDCR),WD_CLR");
#endif
}

void getSavedData()
{
#ifndef WIN32
	u32 *ptr = (u32*)(0x200000+0x1e0000);
	u32 *ptrData = (u32*)data;
	u16 i;
	if (*ptr == MAGIC_NB) // Data saved
	{
		for (i=0;i<256;i++)
			ptrData[i] = ptr[i];
	}
	else // No data
	{
		ptrData[0] = MAGIC_NB;
		for (i=1;i<256;i++)
			ptrData[i] = 0;
	}
#endif
}
