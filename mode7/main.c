// MODE 7 study
// based on https://github.com/bquenin/mode7

#include "ngpc.h"
#include "carthdr.h"
#include "library.h"
#include "music.h"
#include "trigo.h"

#include "Aladdin01.h"
#include "Aladdin03.h"
#include "Aladdin13.h"
#include "Aladdin16.h"
#include "boss_battle.h"
#include "burning_town.h"
#include "GnG07.h"
#include "GNG13.h"
#include "mastertracker.h"
#include "ryu_ending.h"
#include "Sonic05.h"
#include "SONIC12.h"
#include "chime.h"

#include "track.h"

#include "sky.h"

typedef struct {
	u8* data;
	u16 len;
} ENTRY;

const ENTRY list[] = {
	(u8*)ALADDIN01_PSG, ALADDIN01_PSG_SIZE,
	(u8*)ALADDIN03_PSG, ALADDIN03_PSG_SIZE,
	(u8*)ALADDIN13_PSG, ALADDIN13_PSG_SIZE,
	(u8*)ALADDIN16_PSG, ALADDIN16_PSG_SIZE,
	(u8*)GNG07_PSG, GNG07_PSG_SIZE,
	(u8*)GNG13_PSG, GNG13_PSG_SIZE,
	(u8*)SONIC05_PSG, SONIC05_PSG_SIZE,
	(u8*)SONIC12_PSG, SONIC12_PSG_SIZE,
	(u8*)RYU_ENDING_PSG, RYU_ENDING_PSG_SIZE,
	(u8*)BOSS_BATTLE_PSG, BOSS_BATTLE_PSG_SIZE,
	(u8*)BURNING_TOWN_PSG, BURNING_TOWN_PSG_SIZE,
	(u8*)MASTERTRACKER_PSG, MASTERTRACKER_PSG_SIZE
};

#define TRACK_SHIFT 9 // TRACK_WIDTH=512
#define DY 1024 // 128 tiles per plan

#define FMULT 1024
#define FSHIFT 8
#define FMASK 0xfffe0000
#define FMASK_INV 0x0001ff00
#define TF_SHIFT 1 // TRACK_SHIFT - FSHIFT
#define NEAR 5 //(int) (FMULT * .005);
#define FAR 31 //(int) (FMULT * .03);

#define FOV_HALF 64 //(PRECISION >> 3) // Math.PI / 4;

volatile u16 skyX;
volatile u16 teta;
s32 worldX;
s32 worldY;

u8* track;

#define PIXEL \
	if ((aX & FMASK) || (aY & FMASK))\
		tile0 |= 3;\
	else {\
		color = track[(aX >> FSHIFT) + ((aY & FMASK_INV) << TF_SHIFT)];\
		if (color & 4)\
			tile1 |= color - 3;\
		else\
			tile0 |= color;\
	}\
	aX+=dX;\
	aY+=dY;

#define PIXEL_SHIFT \
	PIXEL\
	tile0 <<= 2;\
	tile1 <<= 2;

void update()
{
	s32 far1X =	worldX + ((tcos[(teta - FOV_HALF) & PMASK] * FAR) >> PSHIFT);
	s32 near1X = worldX + ((tcos[(teta - FOV_HALF) & PMASK] * NEAR) >> PSHIFT);
	s32 fn1X = (far1X - near1X) << 6;

	s32 far1Y = worldY + ((tsin[(teta - FOV_HALF) & PMASK] * FAR) >> PSHIFT);
	s32 near1Y = worldY + ((tsin[(teta - FOV_HALF) & PMASK] * NEAR) >> PSHIFT);
	s32 fn1Y = (far1Y - near1Y) << 6;

	s32 far2X =	worldX + ((tcos[(teta + FOV_HALF) & PMASK] * FAR) >> PSHIFT);
	s32 near2X = worldX + ((tcos[(teta + FOV_HALF) & PMASK] * NEAR) >> PSHIFT);
	s32 fn2X = (far2X - near2X) << 6;

	s32 far2Y =	worldY + ((tsin[(teta + FOV_HALF) & PMASK] * FAR) >> PSHIFT);
	s32 near2Y = worldY + ((tsin[(teta + FOV_HALF) & PMASK] * NEAR) >> PSHIFT);
	s32 fn2Y = (far2Y - near2Y) << 6;

	s32 startX, startY, dX, dY, sampleX, sampleY, aX, aY;
	u16 x, y, color;
	register u16 tile0, tile1;
	register u16 *ty;

	for (y = 1; y < 64; ++y)
	{
		startX = (fn1X / y) + near1X;
		startY = (fn1Y / y) + near1Y;
		dX = (fn2X / y) + near2X - startX;
		dY = ( fn2Y/ y) + near2Y - startY;
		ty = TILE_RAM + ((y >> 3) << 7) + (y & 7) + 8;

		aX = startX << 7;
		aY = startY << 7;
		for (x = 0; x < 16; ++x, ty += 8)
		{
			tile0 = tile1 = 0;
			PIXEL_SHIFT
			PIXEL_SHIFT
			PIXEL_SHIFT
			PIXEL_SHIFT
			PIXEL_SHIFT
			PIXEL_SHIFT
			PIXEL_SHIFT
			PIXEL
			*ty = tile0;
			*(ty + DY) = tile1;
		}
	}
}

volatile u8 playing;

void __interrupt myVBLInterrupt(void)
{
    WATCHDOG = WATCHDOG_CLEAR;
    if (USR_SHUTDOWN)
    {
	   SysShutdown();
	   while (1);
    }
    VBCounter++;
    SCR2_X = ((teta>>1) + (++skyX>>6)) & 255;
	if (playing)
		Z80_COMM = 0xff; // PSG next frame
}

void __interrupt myHBLInterrupt()
{
    if (RAS_Y == 76) // 64 + WIN_Y
    	SCR2_X = 240; // Track dx
}

void setHBLTimer()
{
    __asm("TRUN          equ 0x0020");
    __asm("T01MOD        equ 0x0024");
    __asm("TREG0         equ 0x0022");
    __asm("VECT_INTLVSET equ 0x4");

    __asm("andb (TRUN),0x8e");
    __asm("ldb  (T01MOD),0x00");
    __asm("ldb  (TREG0),0x01");
    __asm("ldb  rw3,VECT_INTLVSET");
    __asm("ldb  rb3,0x03");
    __asm("ldb  rc3,0x02");
    __asm("swi  1");
    __asm("orb  (TRUN),0x1");
}

void playBGM(int cur)
{
	int j;
	playing = 0;
	SL_StopBGM();
	SL_LoadData(list[cur].data, list[cur].len, 0);
	SL_PlayBGM(0, 0);
	playing = 1;
	while ((j=JOYPAD)&(J_A|J_B));
}

s32 sqrt(s32 n)
{
	s32 b = 262144; // 512 * 512
	s32 x = 0;
	while (b > n)
		b >>= 2;
	while (b > 0)
	{
		if (n >= x + b)
		{
			n = n - x - b;
			x = (x >> 1) + b;
		}
		else
			x >>= 1;
		b >>= 2;
	}
	return x;
}

s32 sprX, sprY;

void showSprite()
{
	s32 dX = sprX - worldX;
	s32 dY = sprY - worldY;
	s32 len = dX * dX + dY * dY;
	s32 sql = sqrt(len);
	s32 farX = (sql * tcos[teta & PMASK]) >> PSHIFT;
	s32 farY = (sql * tsin[teta & PMASK]) >> PSHIFT;
    s32 dot = (((dX * farX + dY * farY) * PMULT) / len) + PMULT - 1;
   	SetSpritePosition(0, (u8) (72 + ((tsin[tacos[dot&0xff]] * 64) >> PSHIFT)), 64);
	//SetSpritePosition(0, (u8) (72 - (len >> 8)), 64);
}


void main()
{
	int i, j = 0, cur = 9, b;


	teta = (PRECISION * 3) >> 2; // -Math.PI / 2;
	worldX = ((s32) FMULT * 93) / 100;
	worldY = ((s32) FMULT * 75) / 100;

	skyX = 0;

	track = (u8*) TRACK_DATA;

	InitNGPC();

	WIN_X = 16;
	WIN_Y = 12;
	WIN_W = 128;
	WIN_H = 128;
	SCR1_X = 256-16;
	SCR1_Y = 256-12;
	SCR2_X = 256-16;
	SCR2_Y = 256-12;

	ClearScreen(SCR_1_PLANE);
	ClearScreen(SCR_2_PLANE);

	SetBackgroundColour(RGB(0, 0, 0));

	SetPalette(SCR_1_PLANE, 0, TRACK_PAL[0], TRACK_PAL[1], TRACK_PAL[2], TRACK_PAL[3]);
	SetPalette(SCR_2_PLANE, 0, TRACK_PAL[0], TRACK_PAL[4], TRACK_PAL[5], TRACK_PAL[6]);

	for (i = 0; i < 512 * 8; ++i)
		(TILE_RAM)[i] = 0;

	for (i = 0; i < SKY_TILES_COUNT * 8; ++i)
		(TILE_RAM)[(257 << 3) + i] = SKY_TILES[i];
	for (i = 0; i < SKY_NPALS1; ++i)
		SetPalette(SCR_2_PLANE, (u8)i + 1, SKY_PALS1[i * 4], SKY_PALS1[i* 4 + 1], SKY_PALS1[i * 4 + 2], SKY_PALS1[i * 4 + 3]);

	// track
	for (i = 0; i < 128; ++i)
	{
		PutTile(SCR_1_PLANE, 0, (u8) (i%16), (u8)(i/16 + 8), i + 1);
		PutTile(SCR_2_PLANE, 0, (u8) (i%16), (u8)(i/16 + 8), i + 1 + 8 * 16);
	}
	// sky
	b = 0;
	for (i = 0; i < SKY_HEIGHT; ++i)
	{
		for (j = 0; j < SKY_WIDTH; ++j, b++)
		{
			PutTile(SCR_2_PLANE, SKY_PALIDX1[b] + 1, (u8) j, (u8) i, 257 + SKY_IDX1[b] + SKY_FLIP1[b]);
		}
	}

	playing = 0;

	DISABLE_INTERRUPTS;
	VBL_INT = myVBLInterrupt;
    HBL_INT = myHBLInterrupt;
	ENABLE_INTERRUPTS;

	setHBLTimer();
	SL_SoundInit();
	playBGM(cur);

	b = 1;
	while (1)
	{
		j = JOYPAD;

		if (j&J_A)
		{
			if (++cur>11)
				cur = 0;
			playBGM(cur);
		}
		else if (j&J_B)
		{
			if (--cur<0)
				cur = 11;
			playBGM(cur);
		}
		else if (j&J_OPTION)
		{
			SL_PlayPCM((u8*)CHIME_PCM, CHIME_PCM_SIZE);
			while ((j=JOYPAD)&J_OPTION);
		}

		if (j&J_UP)
		{
			worldX += (tcos[teta & PMASK] * 8) >> PSHIFT;
			worldY += (tsin[teta & PMASK] * 8) >> PSHIFT;
			b = 1;
		}
		else if (j&J_DOWN)
		{
			worldX -= (tcos[teta & PMASK] * 8) >> PSHIFT;
			worldY -= (tsin[teta & PMASK] * 8) >> PSHIFT;
			b = 1;
		}

		if (j&J_RIGHT)
		{
			teta += 4;
			b = 1;
		}
		else if (j&J_LEFT)
		{
			teta -= 4;
			b = 1;
		}

		if (b)
		{
			b = 0;
			update();
		}
	}
}
