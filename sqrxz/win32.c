#include <SDL.h>
#include <SDL_types.h>

#include "debug.h"
#include "types.h"

u8 MEM_6f83;
u8 MEM_6da0;

u8 WATCHDOG;
u16 SOUNDCPU_CTRL;
u8 INT_ROM;
u8 CART_ROM;
u8 BAT_VOLT;
u8 JOYPAD = 0;
u8 USR_BOOT;
u8 USR_SHUTDOWN = 0;
u8 USR_ANSWER;
u8 LANGUAGE;
u8 OS_VERSION;
u8 DISP_CTL0;
u8 STS_RG;
u8 LCD_CTR;
u8 RESET;
u8 VERSION;
u8 SCRL_PRIO = 0;
u8 SPR_X = 0;
u8 SPR_Y = 0;
u8 SCR1_X = 0;
u8 SCR1_Y = 0;
u8 SCR2_X = 0;
u8 SCR2_Y = 0;
u8 WIN_X = 0;
u8 WIN_Y = 0;
u8 WIN_W = 160;
u8 WIN_H = 152;
u8 REF;
u8 BG_COL;
u8 RAS_H;
u8 RAS_Y;
u16 BG_PAL = 0;
u8 WIN_PAL;
u8 GE_MODE;

u16 MEM_SPRITE_PALETTE[64];
u16 MEM_SCROLL_1_PALETTE[64];
u16 MEM_SCROLL_2_PALETTE[64];
u16 MEM_SCROLL_PLANE_1[1024];
u16 MEM_SCROLL_PLANE_2[1024];
u16 MEM_TILE_RAM[4096];
u8 MEM_SPRITE_RAM[256];
u8 MEM_SPRITE_COLOUR[64];
u8 MEM_Z80_RAM[4096];

SDL_Surface *screen = NULL;
SDL_Surface *plane1 = NULL;
SDL_Surface *plane2 = NULL;
SDL_Surface *planeS[3];
SDL_Surface* tiles[2][1024];
SDL_Surface* sprTiles[64];
Uint32 colorKey;

#define MAX_FRAME_SKIP 2
#define MSEC_SINGLE_FRAME 20
#define MSEC_GROUP_FRAME  60

unsigned long fpsRefTick;
unsigned long fpsCurTick;
unsigned long fpsGrpTick;
int cntFrames;
int skipFrame;
int fpsSum;
int fpsValue;
int framesSkipped;
int fpsFrameCount;
unsigned long fpsTotalValue = 0;
unsigned long fpsSamples = 0;

void initFPS();
void controlFPS();

#define ZOOM 1

void initVideo() {
	int i;

	SDL_Init(SDL_INIT_VIDEO);
	SDL_putenv("SDL_VIDEO_WINDOW_POS=center");

	screen = SDL_SetVideoMode(160 * ZOOM, 152 * ZOOM, 16, SDL_HWSURFACE|SDL_DOUBLEBUF);

	colorKey = SDL_MapRGB(screen->format, 0xFF, 0, 0xFF);
	plane1 = SDL_CreateRGBSurface(SDL_SWSURFACE,
                                 256 * ZOOM, 256 * ZOOM,
                                 screen->format->BitsPerPixel,
                                 screen->format->Rmask, screen->format->Gmask,
                                 screen->format->Bmask, screen->format->Amask);
	SDL_SetColorKey(plane1, SDL_SRCCOLORKEY, colorKey);
	plane2 = SDL_CreateRGBSurface(SDL_SWSURFACE,
                                 256 * ZOOM, 256 * ZOOM,
                                 screen->format->BitsPerPixel,
                                 screen->format->Rmask, screen->format->Gmask,
                                 screen->format->Bmask, screen->format->Amask);
	SDL_SetColorKey(plane2, SDL_SRCCOLORKEY, colorKey);
	for (i = 0; i < 3; ++i)
	{
		planeS[i] = SDL_CreateRGBSurface(SDL_SWSURFACE,
									 256 * ZOOM, 256 * ZOOM,
									 screen->format->BitsPerPixel,
									 screen->format->Rmask, screen->format->Gmask,
									 screen->format->Bmask, screen->format->Amask);
		SDL_SetColorKey(planeS[i], SDL_SRCCOLORKEY, colorKey);
	}

	for (i = 0; i < 1024; ++i)
	{
		tiles[0][i] = SDL_CreateRGBSurface(SDL_SWSURFACE,
											8 * ZOOM, 8 * ZOOM,
											screen->format->BitsPerPixel,
											screen->format->Rmask, screen->format->Gmask,
											screen->format->Bmask, screen->format->Amask);
		SDL_SetColorKey(tiles[0][i], SDL_SRCCOLORKEY, colorKey);
		tiles[1][i] = SDL_CreateRGBSurface(SDL_SWSURFACE,
											8 * ZOOM, 8 * ZOOM,
											screen->format->BitsPerPixel,
											screen->format->Rmask, screen->format->Gmask,
											screen->format->Bmask, screen->format->Amask);
		SDL_SetColorKey(tiles[0][i], SDL_SRCCOLORKEY, colorKey);
	}
	for (i = 0; i < 64; ++i)
	{
		sprTiles[i] = SDL_CreateRGBSurface(SDL_SWSURFACE,
											8 * ZOOM, 8 * ZOOM,
											screen->format->BitsPerPixel,
											screen->format->Rmask, screen->format->Gmask,
											screen->format->Bmask, screen->format->Amask);
		SDL_SetColorKey(sprTiles[i], SDL_SRCCOLORKEY, colorKey);
	}
	initFPS();
}

void blitImage(SDL_Surface* src, int x, int y, SDL_Surface* dst)
{
    SDL_Rect srcrect, dstrect;
    srcrect.x = 0;
    srcrect.y = 0;
    srcrect.w = src->w;
    srcrect.h = src->h;
    dstrect.x = x;
    dstrect.y = y;
    SDL_BlitSurface(src, &srcrect, dst, &dstrect);
}

void blitImageLoop(SDL_Surface* src, int x, int y, SDL_Surface* dst)
{
    SDL_Rect srcrect, dstrect;
	srcrect.x = 0;
	srcrect.y = 0;
	srcrect.w = src->w;
	srcrect.h = src->h;
	for (dstrect.y = y; dstrect.y < dst->h; dstrect.y += src->h)
		for (dstrect.x = x; dstrect.x < dst->w; dstrect.x += src->w)
			SDL_BlitSurface(src, &srcrect, dst, &dstrect);
}

u16 mapRGB(u16 color)
{
	u16 b = (color >> 8) & 0xf;
	u16 g = (color >> 4) & 0xf;
	u16 r = color & 0xf;
	return SDL_MapRGB(screen->format, r << 4, g << 4, b << 4);
}

void fillTile(SDL_Surface *tile, int id, u16* pal, u16 hFlip, u16 vFlip)
{
	int i, j, k, l, pitch, idk = id << 3;
	unsigned short* pix;
	u16 palSDL[4];

	for (i = 0; i < 4; ++i)
		palSDL[i] = mapRGB(pal[i]);

	SDL_LockSurface(tile);
	pix = (u16*) tile->pixels;
	pitch = tile->pitch >> 1;
	for (i = 0; i < 8; ++i)
	{
		u16 row = MEM_TILE_RAM[idk++];
		for (j = 0; j < 8; ++j)
		{
			u16 palIdx = (row >> (14 - (j << 1))) & 0x3;
			for (k = 0; k < ZOOM; ++k)
			{
				for (l = 0; l < ZOOM; ++l)
				{
					pix[((vFlip ? (7 - i) : i) * ZOOM + k) * pitch + (hFlip ? (7 - j) : j) * ZOOM + l] = palIdx > 0 ? palSDL[palIdx] : colorKey;
				}
			}
		}
	}
	SDL_UnlockSurface(tile);
}

void clearSurface(SDL_Surface *surf, u16 color)
{
	int i, j, pitch;
	u16* pix;
	
	SDL_LockSurface(surf);
	pix = (u16*) surf->pixels;
	pitch = surf->pitch >> 1;
	for (i = 0; i < surf->h; ++i)
	{
		for (j = 0; j < surf->w; ++j)
		{
			pix[i * pitch + j] = color;
		}
	}
	SDL_UnlockSurface(surf);
}

void flipScreen()
{
	int i;
	u16 tileID, palID;
	u16 hFlip, vFlip;
	u16 flags, xy;
	u8 x0, y0, x[64], y[64];
	u16 prio[64], hChain, vChain;

	controlFPS();
	if (skipFrame)
		return;

	clearSurface(screen, mapRGB(BG_PAL));
	clearSurface(plane1, colorKey);
	clearSurface(plane2, colorKey);
	clearSurface(planeS[0], colorKey);
	clearSurface(planeS[1], colorKey);
	clearSurface(planeS[2], colorKey);

	for (i = 0; i < 1024; ++i)
	{
		tileID = MEM_SCROLL_PLANE_1[i] & 0x1ff;
		palID = (MEM_SCROLL_PLANE_1[i] >> 9) & 0xf;
		hFlip = MEM_SCROLL_PLANE_1[i] & 0x8000;
		vFlip = MEM_SCROLL_PLANE_1[i] & 0x4000;
		fillTile(tiles[0][i], tileID, MEM_SCROLL_1_PALETTE + (palID << 2), hFlip, vFlip);
		blitImage(tiles[0][i], ((i & 0x1f) << 3) * ZOOM, ((i >> 5) << 3) * ZOOM, plane1);

		tileID = MEM_SCROLL_PLANE_2[i] & 0x1ff;
		palID = (MEM_SCROLL_PLANE_2[i] >> 9) & 0xf;
		hFlip = MEM_SCROLL_PLANE_2[i] & 0x8000;
		vFlip = MEM_SCROLL_PLANE_2[i] & 0x4000;
		fillTile(tiles[1][i], tileID, MEM_SCROLL_2_PALETTE + (palID << 2), hFlip, vFlip);
		blitImage(tiles[1][i], ((i & 0x1f) << 3) * ZOOM, ((i >> 5) << 3) * ZOOM, plane2);
	}

	x0 = 0;
	y0 = 0;
	for (i = 0; i < 64; ++i)
	{
		flags = ((u16*)MEM_SPRITE_RAM)[i * 2];
		hChain = flags & 0x0400;
		vChain = flags & 0x0200;

		xy = ((u16*)MEM_SPRITE_RAM)[i * 2 + 1];
		x[i] = xy & 0xff;
		if (hChain)
			x[i] += x0;
		y[i] = (xy >> 8) & 0xff;
		if (vChain)
			y[i] += y0;
		x0 = x[i];
		y0 = y[i];

		prio[i] = (flags & 0x1800) >> 11;
		if (prio[i] > 0)
		{
			tileID = flags & 0x01ff;
			hFlip = flags & 0x8000;
			vFlip = flags & 0x4000;
			fillTile(sprTiles[i], tileID, MEM_SPRITE_PALETTE + (MEM_SPRITE_COLOUR[i] << 2), hFlip, vFlip);
		}
	}
	for (i = 63; i >= 0; i--)
		if (prio[i] > 0)
			blitImage(sprTiles[i], x[i] * ZOOM, y[i] * ZOOM, planeS[prio[i] - 1]);

	blitImageLoop(planeS[0], -SPR_X * ZOOM, -SPR_Y * ZOOM, screen);
	if (SCRL_PRIO == 0)
		blitImageLoop(plane2, -SCR2_X * ZOOM, -SCR2_Y * ZOOM, screen);
	else
		blitImageLoop(plane1, -SCR1_X * ZOOM, -SCR1_Y * ZOOM, screen);
	blitImageLoop(planeS[1], -SPR_X * ZOOM, -SPR_Y * ZOOM, screen);
	if (SCRL_PRIO == 0x80)
		blitImageLoop(plane2, -SCR2_X * ZOOM, -SCR2_Y * ZOOM, screen);
	else
		blitImageLoop(plane1, -SCR1_X * ZOOM, -SCR1_Y * ZOOM, screen);
	blitImageLoop(planeS[2], -SPR_X * ZOOM, -SPR_Y * ZOOM, screen);

	SDL_Flip(screen);
}

// FPS

void initFPS()
{
    fpsRefTick = SDL_GetTicks();
    fpsCurTick = fpsRefTick;
    fpsGrpTick = fpsRefTick;
    skipFrame = 0;    
    cntFrames = 0;
    fpsSum = 0;
    fpsFrameCount = 0;
    fpsValue = 0;
    framesSkipped = 0;
}

void controlFPS()
{
    signed int delay;
    signed int duration;
    unsigned long lastTick = fpsCurTick;
    fpsCurTick = SDL_GetTicks();

// s      a       b       c
// |------|                 20 ms D=20-a-s
//        |-------|         20 ms D=20-b-a
// |----------------------| 60 ms D=60-c-s              
// |------|D------|D------|De
//        1       2       3

	if (cntFrames<2)    // First and Second Frames
	{
		if (fpsCurTick < lastTick)
			fpsCurTick = lastTick + MSEC_SINGLE_FRAME;
		fpsSum+=fpsCurTick-lastTick;
		duration = MSEC_SINGLE_FRAME;
	}
	else        // Third Frame
	{
		if (fpsCurTick < fpsGrpTick)
			fpsCurTick = fpsGrpTick + MSEC_GROUP_FRAME;
		fpsSum+=fpsCurTick-fpsGrpTick;
		duration = MSEC_GROUP_FRAME;
	}
    
    // 60 fps ?    
    if (fpsSum>duration && framesSkipped < MAX_FRAME_SKIP)
    {
        fpsSum -= duration; //was 17
        skipFrame = 1;
        framesSkipped += 1;
    }
    else
    {
        delay = duration-fpsSum;
        if (delay>0)
            SDL_Delay(delay);
        fpsSum = 0;
        skipFrame = 0;
        framesSkipped = 0;
        fpsFrameCount += 1;
    }

	// update stats
    if (fpsCurTick < fpsRefTick || fpsCurTick - fpsRefTick >= 1000)
    {
        fpsFrameCount = 0;
        fpsRefTick = fpsCurTick;
        fpsTotalValue += fpsValue;
        fpsSamples++;
    }
	
    fpsCurTick = SDL_GetTicks();
    if (cntFrames<2)    // First and Second Frames
    {
        cntFrames++;
    }
    else        // Third Frame
    {
        fpsGrpTick = fpsCurTick;
        cntFrames = 0;
    }
}

// Keys

#define KEY_UP       		0x01
#define KEY_DOWN     		0x02
#define KEY_LEFT     		0x04
#define KEY_RIGHT    		0x08
#define KEY_BUTTON_A 		0x10
#define KEY_BUTTON_B 		0x20
#define KEY_BUTTON_OPTION	0x40

#define MAX_JOY_MOTION 31000

int A_BUTTON = 0;
int B_BUTTON = 3;
int OPTION_BUTTON = 7;

int UP_BUTTON = 1;
int DOWN_BUTTON = 4;
int LEFT_BUTTON = 8;
int RIGHT_BUTTON = 2;

int A_KEY = SDLK_SPACE;
int B_KEY = SDLK_x;
int OPTION_KEY = SDLK_RETURN;
int LEFT_KEY = SDLK_LEFT;
int RIGHT_KEY = SDLK_RIGHT;
int UP_KEY = SDLK_UP;
int DOWN_KEY = SDLK_DOWN;

#define done USR_SHUTDOWN
#define lastKey JOYPAD
int lastKeyTyped;
int lastKeySym;
int lastJButton;

/*
 * Test joypad/keyboard input
 */
void checkInput()
{
    SDL_Event evt;

    lastKeyTyped = 0;

    while (SDL_PollEvent(&evt))
    {
        // check for messages
        switch (evt.type)
        {
            // exit if the window is closed
            case SDL_QUIT:
                done = 1;
                break;

            // check for keypresses
            case SDL_KEYDOWN:
                {
                    lastKeySym = evt.key.keysym.sym;
                    lastJButton = -1;
                    if (evt.key.keysym.sym == UP_KEY)
                    {
                        if (lastKey&KEY_DOWN)
                            lastKey ^= KEY_DOWN;
                        lastKey |= KEY_UP;
                    }
                    else if (evt.key.keysym.sym == DOWN_KEY)
                    {
                        if (lastKey&KEY_UP)
                            lastKey ^= KEY_UP;
                        lastKey |= KEY_DOWN;
                    }
                    else if (evt.key.keysym.sym == RIGHT_KEY)
                    {
                        if (lastKey&KEY_LEFT)
                            lastKey ^= KEY_LEFT;
                        lastKey |= KEY_RIGHT;
                    }
                    else if (evt.key.keysym.sym == LEFT_KEY)
                    {
                        if (lastKey&KEY_RIGHT)
                            lastKey ^= KEY_RIGHT;
                        lastKey |= KEY_LEFT;
                    }
                    if (evt.key.keysym.sym == A_KEY)
                    {
                        lastKey |= KEY_BUTTON_A;
                    }
                    else if (evt.key.keysym.sym == B_KEY)
                    {
                        lastKey |= KEY_BUTTON_B;
                    }
                    else if (evt.key.keysym.sym == OPTION_KEY)
                    {
                        lastKey |= KEY_BUTTON_OPTION;
                    }
                    // exit if ESCAPE is pressed
                    else if (evt.key.keysym.sym == SDLK_ESCAPE)
                        done = 1;
                    break;
                }
            case SDL_KEYUP:
                {
                    lastJButton = -1;
                    if (evt.key.keysym.sym == UP_KEY)
                    {
                        if (lastKey&KEY_UP)
                            lastKey ^= KEY_UP;
                        lastKeyTyped = KEY_UP;
                    }
                    else if (evt.key.keysym.sym == DOWN_KEY)
                    {
                        if (lastKey&KEY_DOWN)
                            lastKey ^= KEY_DOWN;
                        lastKeyTyped = KEY_DOWN;
                    }
                    else if (evt.key.keysym.sym == RIGHT_KEY)
                    {
                        if (lastKey&KEY_RIGHT)
                            lastKey ^= KEY_RIGHT;
                        lastKeyTyped = KEY_RIGHT;
                    }
                    else if (evt.key.keysym.sym == LEFT_KEY)
                    {
                        if (lastKey&KEY_LEFT)
                            lastKey ^= KEY_LEFT;
                        lastKeyTyped = KEY_LEFT;
                    }
                    if (evt.key.keysym.sym == A_KEY)
                    {
                        if (lastKey&KEY_BUTTON_A)
                            lastKey ^= KEY_BUTTON_A;
                        lastKeyTyped = KEY_BUTTON_A;
                    }
                    else if (evt.key.keysym.sym == B_KEY)
                    {
                        if (lastKey&KEY_BUTTON_B)
                            lastKey ^= KEY_BUTTON_B;
                        lastKeyTyped = KEY_BUTTON_B;
                    }
                    else if (evt.key.keysym.sym == OPTION_KEY)
                    {
                        if (lastKey&KEY_BUTTON_OPTION)
                            lastKey ^= KEY_BUTTON_OPTION;
                        lastKeyTyped = KEY_BUTTON_OPTION;
                    }
                    break;
                }
            case SDL_JOYBUTTONDOWN:
                {
                    lastKeySym = 0;
                    lastJButton = evt.jbutton.button;
                    if (evt.jbutton.button == A_BUTTON)
                    {
                        lastKey |= KEY_BUTTON_A;
                    }
                    else if (evt.jbutton.button == B_BUTTON)
                    {
                        lastKey |= KEY_BUTTON_B;
                    }
                    else if (evt.jbutton.button == OPTION_BUTTON)
                    {
                        lastKey |= KEY_BUTTON_OPTION;
                    }
                    break;
                }
            case SDL_JOYBUTTONUP:
                {
                    lastKeySym = 0;
                    if (evt.jbutton.button == A_BUTTON)
                    {
                        if (lastKey&KEY_BUTTON_A)
                            lastKey ^= KEY_BUTTON_A;
                        lastKeyTyped = KEY_BUTTON_A;
                    }
                    else if (evt.jbutton.button == B_BUTTON)
                    {
                        if (lastKey&KEY_BUTTON_B)
                            lastKey ^= KEY_BUTTON_B;
                        lastKeyTyped = KEY_BUTTON_B;
                    }
                    else if (evt.jbutton.button == OPTION_BUTTON)
                    {
                        if (lastKey&KEY_BUTTON_OPTION)
                            lastKey ^= KEY_BUTTON_OPTION;
                        lastKeyTyped = KEY_BUTTON_OPTION;
                    }
                    break;
                }
            case SDL_JOYHATMOTION:
                {
                    if (evt.jhat.value & UP_BUTTON)
                    {
                        if (lastKey&KEY_DOWN)
                            lastKey ^= KEY_DOWN;
                        lastKey |= KEY_UP;
                    }
                    else if (evt.jhat.value & DOWN_BUTTON)
                    {
                        if (lastKey&KEY_UP)
                            lastKey ^= KEY_UP;
                        lastKey |= KEY_DOWN;
                    }
                    if (evt.jhat.value & LEFT_BUTTON)
                    {
                        if (lastKey&KEY_RIGHT)
                             lastKey ^= KEY_RIGHT;
                        lastKey |= KEY_LEFT;
                    }
                    else if (evt.jhat.value & RIGHT_BUTTON)
                    {
                        if (lastKey&KEY_LEFT)
                             lastKey ^= KEY_LEFT;
                        lastKey |= KEY_RIGHT;
                    }
                    if (evt.jhat.value == 0)
                    {
                        if (lastKey&KEY_DOWN)
                        {
                            lastKey ^= KEY_DOWN;
                            lastKeyTyped = KEY_DOWN;
                        }
                        else if (lastKey&KEY_UP)
                        {
                            lastKey ^= KEY_UP;
                            lastKeyTyped = KEY_UP;
                        }
                        if (lastKey&KEY_RIGHT)
                        {
                            lastKey ^= KEY_RIGHT;
                            lastKeyTyped = KEY_RIGHT;
                        }
                        else if (lastKey&KEY_LEFT)
                        {
                            lastKey ^= KEY_LEFT;
                            lastKeyTyped = KEY_LEFT;
                        }
                    }
                    break;
                }
            case SDL_JOYAXISMOTION:
                {
                    if (evt.jaxis.axis == 1)
                    {
                        if (evt.jaxis.value < -MAX_JOY_MOTION)
                        {
                            if (lastKey&KEY_DOWN)
                                lastKey ^= KEY_DOWN;
                            lastKey |= KEY_UP;
                        }
                        else if (evt.jaxis.value > MAX_JOY_MOTION)
                        {
                            if (lastKey&KEY_UP)
                                lastKey ^= KEY_UP;
                            lastKey |= KEY_DOWN;
                        }
                        else
                        {
                            if (lastKey&KEY_DOWN)
                            {
                                lastKey ^= KEY_DOWN;
                                lastKeyTyped = KEY_DOWN;
                            }
                            else if (lastKey&KEY_UP)
                            {
                                lastKey ^= KEY_UP;
                                lastKeyTyped = KEY_UP;
                            }
                        }
                    }
                    else if (evt.jaxis.axis == 0)
                    {
                        if (evt.jaxis.value > MAX_JOY_MOTION)
                        {
                            if (lastKey&KEY_LEFT)
                                lastKey ^= KEY_LEFT;
                            lastKey |= KEY_RIGHT;
                        }
                        else if (evt.jaxis.value < -MAX_JOY_MOTION)
                        {
                            if (lastKey&KEY_RIGHT)
                                lastKey ^= KEY_RIGHT;
                            lastKey |= KEY_LEFT;
                        }
                        else
                        {
                            if (lastKey&KEY_RIGHT)
                            {
                                lastKey ^= KEY_RIGHT;
                                lastKeyTyped = KEY_RIGHT;
                            }
                            else if (lastKey&KEY_LEFT)
                            {
                                lastKey ^= KEY_LEFT;
                                lastKeyTyped = KEY_LEFT;
                            }
                        }
                    }
                    break;
                }
        }
    }
}



