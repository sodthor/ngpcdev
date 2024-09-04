#include "ngpc.h"
#include "carthdr.h"
#include "library.h"
#include "music.h"
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

void waitVBL()
{
	VBCounter = 0;
	while (!VBCounter)
		__ASM("  NOP");
	Z80_COMM = 0xff; // PSG next frame
}

void playBGM(int cur)
{
	int j;
	SL_StopBGM();
	SL_LoadData(list[cur].data, list[cur].len, 0);
	SL_PlayBGM(0, 0);
	PrintDecimal(SCR_1_PLANE, 0, 13, 12, cur, 2);
	while ((j=JOYPAD)&(J_A|J_B))
		waitVBL();
}

void main()
{
	int j = 0, cur = 0;
    InitNGPC();
	SysSetSystemFont();
	ClearScreen(SCR_1_PLANE);
	ClearScreen(SCR_2_PLANE);
	SetBackgroundColour(RGB(0, 0, 0));

	SetPalette(SCR_1_PLANE, 0, 0, 0, 0, RGB(15,15,15));
	PrintString(SCR_1_PLANE, 0, 6, 1, "PSG DEMO");
    PrintString(SCR_1_PLANE, 0, 4, 6, "A - NEXT BGM");
    PrintString(SCR_1_PLANE, 0, 2, 8, "B - PREVIOUS BGM");
    PrintString(SCR_1_PLANE, 0, 4, 12, "CURRENT:");
    PrintString(SCR_1_PLANE, 0, 3, 16, "OPTION - SFX");

    SL_SoundInit();
	playBGM(cur);
	while (1)
	{
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
			while ((j=JOYPAD)&J_OPTION)
				waitVBL();
		}
		else
			waitVBL();
		j = JOYPAD;
	}
}