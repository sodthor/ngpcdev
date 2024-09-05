//---------------------------------------------------------------------------
//	This program is free software; you can redistribute it and/or modify
//	it under the terms of the GNU General Public License as published by
//	the Free Software Foundation; either version 2 of the License, or
//	(at your option) any later version. See also the license.txt file for
//	additional informations.
//---------------------------------------------------------------------------

// sound.h: interface for the sound class.
//
//////////////////////////////////////////////////////////////////////

#ifndef _SOUND_H
#define _SOUND_H

#include <sdl_mixer.h>

//
// General sound system functions
//
void soundStep(int cycles);

#define NUM_CHANNELS 32
extern Mix_Chunk *chunks[NUM_CHANNELS];

///
/// Neogeo pocket sound functions
///
void sound_update(u16* chip_buffer, int length_bytes);
void ngpSoundStart();
void ngpSoundExecute();
void ngpSoundOff();
void ngpSoundInterrupt();

#endif
