// Unlimited multidirectional scrolling sample
// CopyLeft 12/2003 Thor - Shadow Of Dawn

#include "ngpc.h"
#include "carthdr.h"
#include "library.h"

#include "levels.h"

#include "gfx.h"

#define MAIN_X 72
#define MAIN_Y 72

const u8* map;   // map
u16 map_w,map_h; // map size

u16 map_x,map_y; // top left visible corner (completly or not)
u16 map_max_x,map_max_y; // max values for top left visible corner

u16 spr_x,spr_y; // main sprite coordinates
u16 spr_anim; // anim frame # (just for fun)

u8 curLvl; // current map

// Move gfx to tile ram and set palettes
void loadTiles()
{
	memcpy(TILE_RAM,gfx_tiles,GFX_TILES*sizeof(u16)*8);
	memcpy(SCROLL_1_PALETTE,gfx_pals,GFX_PALS*sizeof(u16)*4);
	memcpy(SPRITE_PALETTE,gfx_pals,GFX_PALS*sizeof(u16)*4);
}

// Set tile on scrollplane
void setTile(u16 idx,u16 tile)
{
	u16 pal = gfx_palidx[tile];
	(SCROLL_PLANE_1)[idx] = (pal<<9)+tile;
}

// fill new visible column on scrollplane
void fillColumn(u16 c,u16 r,u16 dx)
{
	u16 i;
	c = (c+dx)&0x1f;
	dx += map_x;
	for (i=0;i<20;i++)
		setTile(c+(((r+i)&0x1f)<<5),map[(map_y+i)*map_w+dx]);
}

// fill new visible row on scrollplane
void fillRow(u16 c,u16 r,u16 dy)
{
	u16 i;
	r = (((r+dy)&0x1f)<<5);
	dy = (map_y+dy)*map_w;
	for (i=0;i<21;i++)
		setTile(((c+i)&0x1f)+r,map[dy+map_x+i]);
}

// first visible screen
void initScreen(u16 x,u16 y)
{
	u16 i;
	SCR1_X = 0;
	SCR1_Y = 0;
	map_x = x;
	map_y = y;
	for (i=0;i<21;i++)
		fillColumn(0,0,i);
}

// Set new main sprite position
void showSprite()
{
	SetSprite(0,GFX_LINE+spr_anim,0,spr_x,spr_y,gfx_palidx[GFX_LINE+spr_anim],TOP_PRIO);
}

// init level
void initLevel(u16 lvl)
{
	map = levels[lvl].map;
	map_w = levels[lvl].w;
	map_h = levels[lvl].h;
	map_max_x = map_w-20;
	map_max_y = map_h-19;
	// set first visible screen to center of map
	initScreen((map_w>>1)-10,(map_h>>1)-9); // center by default, you can change it with a map dependant starting point
	// initSprite position
	spr_x = MAIN_X;
	spr_y = MAIN_Y;
	spr_anim = 0;
	showSprite();
	curLvl = lvl;
}

// do move according to mask a (JOYPAD or program)
void doMove(u8 a)
{
	u8 sx = SCR1_X;
	u8 sy = SCR1_Y;
	u8 old_x = map_x;
	u8 old_y = map_y;
	u8 m,canMove = 1;

	if (a&J_RIGHT)
	{
		// Collision detection
		if ((sx&0x7)==0 && (spr_x&0x7)==0)
		{
			m = map[(map_x+(spr_x>>3)+1)+(map_y+(spr_y>>3))*map_w];
			canMove = m!=1;
			if (canMove && ((sy&0x7) || (spr_y&0x7)))
			{
				m = map[(map_x+(spr_x>>3)+1)+(map_y+(spr_y>>3)+1)*map_w];
				canMove = m!=1; // tile 1 is a wall
			}
		}
		if (canMove)
		{
			if (map_x==map_max_x || spr_x<MAIN_X) // right limit for scrolling : move sprite
				spr_x += 1;
			else
			{
				sx += 1;
				if ((sx&0x7)==0)
					map_x += 1;
			}
		}
	}
	else if (a&J_LEFT)
	{
		// Collision detection
		if ((sx&0x7)==0 && (spr_x&0x7)==0)
		{
			m = map[(map_x+(spr_x>>3)-1)+(map_y+(spr_y>>3))*map_w];
			canMove = m!=1;
			if (canMove && ((sy&0x7) || (spr_y&0x7)))
			{
				m = map[(map_x+(spr_x>>3)-1)+(map_y+(spr_y>>3)+1)*map_w];
				canMove = m!=1;
			}
		}
		if (canMove)
		{
			if (((sx&0x7)==0 && map_x==0) || (spr_x>MAIN_X)) // left limit for scrolling : move sprite
				spr_x -= 1;
			else
			{
				sx -= 1;
				if ((sx&0x7)==7)
					map_x -= 1;
			}
		}
	}
	canMove = 1;
	if (a&J_DOWN)
	{
		// Collision detection
		if ((sy&0x7)==0 && (spr_y&0x7)==0)
		{
			m = map[(map_x+(spr_x>>3))+(map_y+(spr_y>>3)+1)*map_w];
			canMove = m!=1;
			if (canMove && ((sx&0x7) || (spr_x&0x7)))
			{
				m = map[(map_x+(spr_x>>3)+1)+(map_y+(spr_y>>3)+1)*map_w];
				canMove = m!=1;
			}
		}
		if (canMove)
		{
			if (map_y==map_max_y || spr_y<MAIN_Y)
				spr_y += 1;
			else
			{
				sy += 1;
				if ((sy&0x7)==0)
					map_y += 1;
			}
		}
	}
	else if (a&J_UP)
	{
		// Collision detection
		if ((sy&0x7)==0 && (spr_y&0x7)==0)
		{
			m = map[(map_x+(spr_x>>3))+(map_y+(spr_y>>3)-1)*map_w];
			canMove = m!=1;
			if (canMove && ((sx&0x7) || (spr_x&0x7)))
			{
				m = map[(map_x+(spr_x>>3)+1)+(map_y+(spr_y>>3)-1)*map_w];
				canMove = m!=1;
			}
		}
		if (canMove)
		{
			if (((sy&0x7)==0 && map_y==0) || (spr_y>MAIN_Y))
				spr_y -= 1;
			else
			{
				sy -= 1;
				if ((sy&0x7)==7)
					map_y -= 1;
			}
		}
	}
	// update scroll indexes
	SCR1_X = sx;
	SCR1_Y = sy;
	sx = (sx>>3);
	sy = (sy>>3);
	// Update new visible column/row if necessary
	if (old_x<map_x)
	{
		if (map_x<map_max_x)
			fillColumn(sx,sy,20);
	}
	else if (old_x>map_x)
		fillColumn(sx,sy,0);
	if (old_y<map_y)
	{
		if (map_y<map_max_y)
			fillRow(sx,sy,19);
	}
	else if (old_y>map_y)
		fillRow(sx,sy,0);
}

void main()
{
	u8 anim_wait = 1;

	InitNGPC();

	ClearScreen(SCR_1_PLANE);
	ClearScreen(SCR_2_PLANE);
	SetBackgroundColour(RGB(0, 0, 0));

	loadTiles();

	// Start with map 0
	initLevel(0);

	while (1)
	{
		u8 a = JOYPAD;
		if (a)
			doMove(a);
		if (a&J_A)
		{
			initLevel((curLvl+1)%NB_LEVELS); // switch level
			initScreen((map_w>>1)-10,(map_h>>1)-9); // center map
			while (a = JOYPAD); // please, free the button !
		}
		// sprite anim :)
		if (--anim_wait==0)
		{
			spr_anim = (spr_anim+1)&0x7; // only 8 anim frames in my sample
			anim_wait = 10;
		}
		// Show sprite
		showSprite();
		// wait vbl
		Sleep(1);
	}
}
