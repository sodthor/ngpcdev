#include "ngpc.h"
#include "driver.h"

#define Z80_NMI (*((u8*)0xba))
#define Z80_COM (*((u8*)0xbc))

void SL_SoundInit()
{
	u8 *ram = Z80_RAM;
	u16 i,l = sizeof(driver);
	SOUNDCPU_CTRL = 0xaaaa;
	for (i=0;i<l;i++)
		ram[i] = driver[i];
	SOUNDCPU_CTRL =  0x5555;

	// set up timer for im1 z80 interrupt
#ifdef CLANG
	__ASM("	andb	(020h), 0f7h");
	__ASM("	andb	(028h), 033h");
	__ASM("	orb	(028h), 04h");
	__ASM("	andb	(025h), 0fh");
	__ASM("	orb	(025h), 0b0h");
	__ASM("	ldb	(027h), 062h");
	__ASM("	ldb	(020h), 088h");
#else
	__ASM("	and	(0x20), 0xf7");		// turn off timer 3
	__ASM("	and	(0x28), 0x33");		// set timer 2,3 to 8 bit timers
	__ASM("	or	(0x28), 0x4");		// timer 3 internal clock = t1 (1.302uS)
	__ASM("	and	(0x25), 0xf");		// clear timer 3 settings
	__ASM("	or	(0x25), 0xb0");		// clear flipflip and flip with timer 3
	__ASM("	ld	(0x27), 0x62");		// set comparitor to interrupt z80 about 7800/sec
	__ASM("	ld	(0x20), 0x88");		// turn timer 3 back on
#endif
}

void SL_LoadGroup(const u8 *data,u16 l)
{
	u8 *ram = Z80_RAM;
	u16 i,ld = sizeof(driver);

	Z80_COM = 0xff;
	Z80_NMI = 1;
	__ASM("wait_z80:");
#ifdef CLANG
	__ASM("ld	a,(0bch)");
#else
	__ASM("ld	a,(0xbc)");
#endif
	__ASM("cp	a,0");
	__ASM("jr	nz,wait_z80");

	for (i=0;i<l;i++)
		ram[i+ld] = data[i];

#ifdef CLANG
	__ASM("ld	a,(070deh)");
#else
	__ASM("ld	a,(0x70de)");
#endif
	__ASM("wait_driver:");
#ifdef CLANG
	__ASM("ld	w,(070deh)");
#else
	__ASM("ld	w,(0x70de)");
#endif
	__ASM("cp	a,w");
	__ASM("jr	z,wait_driver");
}

void SL_PlayTune(u8 tune)
{
	Z80_COM = ((tune<<2)|3);
	Z80_NMI = 1;
}

void SL_PlaySFX(u8 sfx)
{
	Z80_COM = ((sfx<<2)|2);
	Z80_NMI = 1;
}
