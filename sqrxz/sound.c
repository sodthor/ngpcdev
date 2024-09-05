//---------------------------------------------------------------------------
//	This program is free software; you can redistribute it and/or modify
//	it under the terms of the GNU General Public License as published by
//	the Free Software Foundation; either version 2 of the License, or
//	(at your option) any later version. See also the license.txt file for
//	additional informations.
//---------------------------------------------------------------------------

// sound.cpp: implementation of the sound class.
//
//////////////////////////////////////////////////////////////////////

//Flavor - Convert from DirectSound to SDL

#include "ngpc.h"

#include "sound.h"
#include "neopopsound.h"

#include "z80.h"
#include <math.h>

#define Machine (&m_emuInfo)


//////////////////////////////////////////////////////////////////////////
//////////////////////////////////////////////////////////////////////////
//////////////////////////////////////////////////////////////////////////

int sndCycles = 0;

void soundStep(int cycles)
{
	sndCycles+= cycles;
}

/////////////////////////////////////////////////////////////////////////////////
///
/// Neogeo Pocket Sound system
///
/////////////////////////////////////////////////////////////////////////////////

unsigned int	ngpRunning;

void ngpSoundStart()
{
	ngpRunning = 1;	// ?
	z80Init();
	z80SetRunning(1);
}

/// Execute all gained cycles (divided by 2)
void ngpSoundExecute()
{
	int		elapsed;
	while(sndCycles > 0)
	{
		elapsed = z80Step();
		sndCycles-= (2*elapsed);
		//timer_add_cycles(elapsed);
	}
}

/// Switch sound system off
void ngpSoundOff() {
	ngpRunning = 0;
	z80SetRunning(0);
}

// Generate interrupt to ngp sound system
void ngpSoundInterrupt() {
	if (ngpRunning)
	{
		z80Interrupt(0);
	}
}

void tlcs_interrupt_wrapper(int irq)
{
	if (irq == 0x3) {
		// interrupt from z80
		(*Z80_INT)();
	} else if (irq == 0x09) {
		ngpSoundExecute();
		ngpSoundInterrupt();
	}
}

////////////////////////////////////////////////////////////////////////////////////
///
/// Neogeo Pocket z80 functions
///
////////////////////////////////////////////////////////////////////////////////////

unsigned char z80MemReadB(unsigned short addr) {
	unsigned char temp;
	if (addr < 0x4000) {
		return MEM_Z80_RAM[addr];
	}
	switch (addr) {
	case 0x4000:	// sound chip read
		break;
	case 0x4001:
		break;
	case 0x8000:
		temp = Z80_COM;
		return temp;
	case 0xC000:
		break;
	}
	return 0x00;
}

unsigned short z80MemReadW(unsigned short addr)
{
	return (z80MemReadB(addr + 1) << 8) | z80MemReadB(addr);
}

void z80MemWriteB(unsigned short addr, unsigned char data) {
	if (addr < 0x4000) {
		MEM_Z80_RAM[addr] = data;	return;
	}

	switch (addr)
	{
	case 0x4000:
		Write_SoundChipNoise(data);//Flavor SN76496Write(0, data);
		return;
	case 0x4001:
		Write_SoundChipTone(data);//Flavor SN76496Write(0, data);
		return;
	case 0x8000:
		Z80_COM = data;
		return;
	case 0xC000:
		tlcs_interrupt_wrapper(0x03);
		return;
	}
}

void z80MemWriteW(unsigned short addr, unsigned short data) {
	if (addr < 0x4000) {
		MEM_Z80_RAM[addr] = data & 0xFF;
		MEM_Z80_RAM[addr + 1] = data >> 8;
		return;
	}

	switch (addr)
	{
	case 0x4000:
		Write_SoundChipNoise(data & 0xFF);//Flavor SN76496Write(0, data);
		Write_SoundChipNoise(data >> 8);//Flavor SN76496Write(0, data);
		return;
	case 0x4001:
		Write_SoundChipTone(data & 0xFF);//Flavor SN76496Write(0, data);
		Write_SoundChipTone(data >> 8);//Flavor SN76496Write(0, data);
		return;
	case 0x8000:
		Z80_COM = data & 0xFF;
		Z80_COM = data >> 8;
		return;
	case 0xC000:
		tlcs_interrupt_wrapper(0x03);
		tlcs_interrupt_wrapper(0x03);
		return;
	}
}

void z80PortWriteB(unsigned char port, unsigned char data) {
	// acknowledge interrupt, any port works
}

unsigned char z80PortReadB(unsigned char port) {
	return 0xFF;
}


/*
inline void tlcsMemWriteBaddrB(unsigned char addr, unsigned char data)
{
	switch (addr) {
		//case 0x80:	// CPU speed
		//    break;
	case 0xA0:	// L CH Sound Source Control Register
		if (cpuram[0xB8] == 0x55 && cpuram[0xB9] == 0xAA)
			Write_SoundChipNoise(data);//Flavor SN76496Write(0, data);
		break;
	case 0xA1:	// R CH Sound Source Control Register
		if (cpuram[0xB8] == 0x55 && cpuram[0xB9] == 0xAA)
			Write_SoundChipTone(data); //Flavor SN76496Write(0, data);
		break;
	case 0xA2:	// L CH DAC Control Register
		ngpSoundExecute();
		if (cpuram[0xB8] == 0xAA)
			dac_writeL(data); //Flavor DAC_data_w(0,data);
		break;
	case 0xA3:	// R CH DAC Control Register  //Flavor hack for mono only sound
		ngpSoundExecute();
		if (cpuram[0xB8] == 0xAA)
		dac_writeR(data);//Flavor DAC_data_w(1,data);
		break;
	case 0xB8:	// Z80 Reset
				//				if (data == 0x55)	DAC_data_w(0,0);
	case 0xB9:	// Sourd Source Reset Control Register
		switch (data) {
		case 0x55:
			ngpSoundStart();
			break;
		case 0xAA:
			ngpSoundExecute();
			ngpSoundOff();
			break;
		}
		break;
	case 0xBA:
		ngpSoundExecute();
#ifdef DRZ80
		Z80_Cause_Interrupt(Z80_NMI_INT);
#else
		z80Interrupt(Z80NMI);
#endif
		break;
	}
	cpuram[addr] = data;
	return;
}
*/
