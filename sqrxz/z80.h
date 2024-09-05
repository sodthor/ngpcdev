//---------------------------------------------------------------------------
//	This program is free software; you can redistribute it and/or modify
//	it under the terms of the GNU General Public License as published by
//	the Free Software Foundation; either version 2 of the License, or
//	(at your option) any later version. See also the license.txt file for
//	additional informations.
//---------------------------------------------------------------------------

// z80.h: interface for the z80 class.
//
//////////////////////////////////////////////////////////////////////

#ifndef _Z80_H
#define _Z80_H

#include <stdio.h>

#define Z80NMI	0xFF00

#define FALSE 0
#define TRUE 1

void z80SetRunning(int running);
void z80Init();
void z80Interrupt(int prio);
void z80UnInterrupt(int kind);
int z80Step();
void z80Print(FILE *output);
void z80orIFF(unsigned char bits);
unsigned short z80PC();


#endif
