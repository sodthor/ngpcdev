#include "ngpc.h"
#include "library.h"
#include "hicolor.h"
#include "mplayer.h"
#include "music.h"

#include "gamedata.h"

#include "freeplay.h"
#include "pback.h"
#include "mixit.h"

#include "cotton1.mh"

extern void removeVBL();

extern volatile u8 VBCounter;

extern GAME_DATA DATA;

MOVIE curmovie;

void __interrupt menu_myVBL1()
{
    WATCHDOG = WATCHDOG_CLEAR;
    if (USR_SHUTDOWN) { SysShutdown(); while (1); }
    VBCounter++;

    if (DATA.curimg)
    {
        if (hc_emu())
            hc_showEmu((void*)DATA.curimg);
        else
            hc_showHW((void*)DATA.curimg);
	}
}

void __interrupt menu_myVBL2()
{
    WATCHDOG = WATCHDOG_CLEAR;
    if (USR_SHUTDOWN) { SysShutdown(); while (1); }
    VBCounter++;

    if (hc_emu())
		mp_play_emu(&curmovie);
    else
		mp_play_hw(&curmovie);
}

void menu_showFreePlayTech()
{
    u8 j, i;

    DATA.curimg = 0;
    hc_load((void*)FREEPLAY_ID);
    DATA.curimg=(void*)FREEPLAY_ID;

    __asm(" di");
    VBL_INT = menu_myVBL1;
    __asm(" ei");

    while (j=JOYPAD);

    while ((((j=JOYPAD) & (J_A | J_B)) == 0) && (i++ < 150))
        Sleep(1);

    while (j=JOYPAD);
    DATA.curimg = 0;

    removeVBL();
}

void menu_loadGfx()
{
    u16 i;
    u16 *p0 = TILE_RAM;
    u16 *ps = SPRITE_PALETTE;
    for (i=0;i<MIXIT_TILES*8;++i)
        p0[256*8+i] = MIXIT_TILES1[i];
    for (i=0;i<MIXIT_NPALS1*4;++i)
        ps[i] = MIXIT_PALS1[i];
}

u8 menu_print(const char *s,u8 x,u8 y,u8 k) {
    u8 i;
    for (i=0;s[i];++i)
    {
        u8 c = s[i];
        u16 t = 0;
        if (c != ' ')
            t = MIXIT_WIDTH + ((c>='a'&&c<='z') ? (c-'a') : (c-'A'));
        else
            t = MIXIT_WIDTH + 26;
        SetSprite(k++,256 + t,0,x+(i<<3),y,MIXIT_PALIDX1[t],TOP_PRIO);
    }
    return k;
}

const u8 mixit_logo[5][13] = {
    {5,0,0,0,5,0,8,0,0,0,0,0,2},
    {5,5,0,5,5,0,0,0,0,0,0,0,2},
    {5,0,5,0,5,0,8,0,4,0,4,0,2},
    {5,0,0,0,5,0,8,0,0,4,0,0,0},
    {5,0,0,0,5,0,8,0,4,0,4,0,2}
};

u8 menu_mainMenu()
{
    u8 i, j, k = 0, x, delay;
    s8 dx;
    u8 mx[3] = {40, 88, 64};

    DATA.curimg = 0;
    memset(&curmovie, 0, sizeof(MOVIE));

    SL_LoadGroup(cotton1,sizeof(cotton1));
    SL_PlayTune(0);

    __asm(" di");
    VBL_INT = menu_myVBL2;
    __asm(" ei");

    ClearAll();

    menu_loadGfx();

    for (i=0;i<5;++i)
    {
        for (j=0;j<13;++j)
        {
            u16 s = mixit_logo[i][j];
            if (s == 0)
                continue;
            SetSprite(k++,s+255,0,(j<<3)+28,(i<<3)+24,MIXIT_PALIDX1[s-1],TOP_PRIO);
        }
    }
    i = 106;
    k = menu_print("VEXED", 56, i, k);
    k = menu_print("PUZZLE GEMS", 56, i + 10, k);
    k = menu_print("DYNAMATE", 56, i + 20, k);

	curmovie.data = (u16*)PBACK_DATA;
	curmovie.count = PBACK_COUNT;
	curmovie.delay = PBACK_DELAY;
	mp_load(&curmovie);

    __asm(" di");
    VBL_INT = menu_myVBL2;
    __asm(" ei");

    i = 0;
    x = 0;
    dx = 2;
    delay = 0;
	while((!((j=JOYPAD)&J_A)))
    {
        SetSprite(k,1+255,0,x+48,(i*10)+106,MIXIT_PALIDX1[1-1],TOP_PRIO);
        SetSprite(k+1,8+255,0,x+44,(i*10)+106,MIXIT_PALIDX1[8-1],TOP_PRIO);
        SetSprite(k+2,7+255,0,x+40,(i*10)+106,MIXIT_PALIDX1[7-1],TOP_PRIO);
        x += dx;
        if (x == 0 || x == mx[i]+8)
            dx = -dx;
        if (delay == 0)
        {
            if (j&J_UP)
            {
                i = i == 0 ? 2 : i - 1;
                x = 0;
                dx = 2;
                delay = 10;
            }
            else if (j&J_DOWN)
            {
                i = i == 2 ? 0 : i + 1;
                x = 0;
                dx = 2;
                delay = 10;
            }
        }
        else
            delay -= 1;
        Sleep(1);
    }
	while((j=JOYPAD)&J_A);

	mp_stop(&curmovie);

    removeVBL();

    return i;
}

