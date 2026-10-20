#include "ngpc.h"
#include "driver.h"

#define Z80_NMI (*((volatile u8*)0xba))
#define Z80_COM (*((volatile u8*)0xbc))

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
	__ASM(" and	(20h), 0f7h");		// turn off timer 3
	__ASM(" and	(28h), 33h");		// set timer 2,3 to 8 bit timers
	__ASM(" or	(28h), 4h");		// timer 3 internal clock = t1 (1.302uS)
	__ASM(" and	(25h), 0fh");		// clear timer 3 settings
	__ASM(" or	(25h), 0b0h");		// clear flipflip and flip with timer 3
	__ASM(" ld	(27h), 62h");		// set comparitor to interrupt z80 about 7800/sec
	__ASM(" ld	(20h), 88h");		// turn timer 3 back on
#else
	__ASM(" and	(0x20), 0xf7");		// turn off timer 3
	__ASM(" and	(0x28), 0x33");		// set timer 2,3 to 8 bit timers
	__ASM(" or	(0x28), 0x4");		// timer 3 internal clock = t1 (1.302uS)
	__ASM(" and	(0x25), 0xf");		// clear timer 3 settings
	__ASM(" or	(0x25), 0xb0");		// clear flipflip and flip with timer 3
	__ASM(" ld	(0x27), 0x62");		// set comparitor to interrupt z80 about 7800/sec
	__ASM(" ld	(0x20), 0x88");		// turn timer 3 back on
#endif
}

void SL_LoadGroup(const u8 *data,u16 l)
{
	u8 *ram = Z80_RAM;
	u16 i,ld = sizeof(driver);

	Z80_COM = 0xff;
	Z80_NMI = 1;
#ifdef CLANG
	__ASM("wait_z80:");
	__ASM("ld	a,(0bch)");
	__ASM("cp	a,0");
	__ASM("jr	nz,wait_z80");

	for (i=0;i<l;i++)
		ram[i+ld] = data[i];

	__ASM("ld	a,(70deh)");
	__ASM("wait_driver:");
	__ASM("ld	w,(70deh)");
	__ASM("cp	a,w");
	__ASM("jr	z,wait_driver");
#else
	__ASM("wait_z80:");
	__ASM("ld	a,(0xbc)");
	__ASM("cp	a,0");
	__ASM("jr	nz,wait_z80");

	for (i=0;i<l;i++)
		ram[i+ld] = data[i];

	__ASM("ld	a,(0x70de)");
	__ASM("wait_driver:");
	__ASM("ld	w,(0x70de)");
	__ASM("cp	a,w");
	__ASM("jr	z,wait_driver");
#endif
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
