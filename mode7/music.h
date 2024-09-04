#ifndef _MUSIC_H
#define _MUSIC_H

void SL_SoundCPUStop();
void SL_SoundInit();
void SL_WaitZ80();
void SL_StopBGM();
void SL_LoadData(const u8*, u16, u16);
void SL_PlayBGM(u16, u8);
void SL_PlayPCM(u8*, u16);

#endif
