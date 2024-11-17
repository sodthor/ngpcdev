#ifndef _MPLAYER_H_
#define _MPLAYER_H_

typedef struct
{
	u16 *data;
	u16	count;
	u16 delay;
	u16 *curdata;
	u16	curcount;
	u16 curdelay;
} MOVIE;

void mp_load(void*);
void mp_stop(void*);
void mp_play_emu(void*);
void mp_play_hw(void*);

#endif
