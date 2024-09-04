#include "ngpc.h"
#include "carthdr.h"
#include "library.h"
#include "hicolor.h"
#include "mplayer.h"
#include "flash.h"
#include "music.h"
#include "img.h"
#include "gfx/perso.h"
#include "gfx/common.h"
#include "gfx/font0.h"
#include "gfx/bkgd.h"
#include "gfx/cursor.h"
#include "gfx/present0.h"
#include "gfx/present1.h"
#include "gfx/loose.h"
#include "gfx/victory.h"

#include "cotton0.mh"
#include "magical0.mh"
#include "magical1.mh"
#include "magical2.mh"
#include "metalslug0.mh"
#include "metalslug1.mh"
#include "metalslug2.mh"
#include "metalslug3.mh"
#include "metalslug4.mh"
#include "metalslug5.mh"
#include "metalslug6.mh"
#include "metalslug7.mh"
#include "metalslug8.mh"
#include "metalslug9.mh"
#include "metalslug10.mh"

#include "gfx/titlescr.h"
#include "maps.h"

#include "videos/bman.h"
#include "videos/bend.h"

void *curimg;
extern volatile u8 VBCounter;
volatile u16 mn,ds,us,ms,paused; // time counter
volatile u32 moviecnt = 0;
MOVIE curmovie;

typedef struct
{
	u16 idx;	// sprite tile
	u8	x,y;	// coords
	u16	speed;
	u8	nbmax;	// max bombs
	u8	nb;		// cur nb bombs
	u8	power;
	u8	dir;
	u8	anim;	// anim frame
	u8	count;	// anim tempo
	u8	alive;
	u8	tempo;	// bomb tempo
	u8	invers;
	u8	num;	// num in sprite sheet
	u8	kind;	// kind of enemy : 0=normal, 1=flying
	u8	atype;	// anim type : 0=normal (dduurr), 1=static*4
} Entity;

typedef struct
{
	u8	owner;	// 0 : player
	u8	time;
	u8	anim;	// anim frame
	u8	count;	// anim tempo
	u8	x,y;
	u8	power;
} Bomb;

#define MAX_ENTITIES 8
#define MAX_BOMBS 64
#define NO_OWNER 0xff

Entity chars[MAX_ENTITIES];
Bomb bombs[MAX_BOMBS];
u8 nb_bombs;
u8 cntupd;
u8 door;
u8 scr_x,scr_y;
u8 lvl;
u8 lives;
u8 boss;
u8 chain;
u16 score;

#define EXPLODE 0xff
#define ENEMY 0xfe
#define FINISHED 0xfd

u8 curmap[15][15];
u8 curflag[15][15];
u8 curbombs[15][15];
u8 curmod[15][15];
u8 curmark[15][15];

#define HF_TILE 0x8000
#define VF_TILE 0x4000

void print(const char *s,u8 x,u8 y)
{
	u16 i,n,t;
	t = (((u16)y)<<5)+x;
	for (i=0;s[i];i++)
	{
		u8 c = s[i];
		if (c>='A'&&c<='Z')
		{
			if (c > 'G')
				n = 20 + c - 'H';
			else
				n = 13 + c - 'A';
		}
		else if (c>='0'&&c<='9')
			n = c - '0' + 1;
		else
		{
			switch(c)
			{
				case '!': n = 0; break;
				case ':': n = 11; break;
				case '?': n = 12; break;
				case '.': n = 39; break;
				default : n = 255; break;
			}
		}
		if (n!=255)
		{
			n = (n > 19 ? FONT0_WIDTH+n-20 : n)<<1;
			(SCROLL_PLANE_1)[t]   = (((u16)FONT0_PALIDX1[n])<<9)+n+1;
			(SCROLL_PLANE_1)[t+1] = (((u16)FONT0_PALIDX1[n+1])<<9)+n+2;
			(SCROLL_PLANE_1)[t+32]= (((u16)FONT0_PALIDX1[n+FONT0_WIDTH])<<9)+n+FONT0_WIDTH+1;
			(SCROLL_PLANE_1)[t+33]= (((u16)FONT0_PALIDX1[n+FONT0_WIDTH+1])<<9)+n+FONT0_WIDTH+2;
		}
		else
			(SCROLL_PLANE_1)[t] = (SCROLL_PLANE_1)[t+1] = (SCROLL_PLANE_1)[t+32] = (SCROLL_PLANE_1)[t+33] = 0;
		t+=2;
	}
}

void printi(u16 i,u8 x,u8 y)
{
	char buf[9];
	u8 j = 9;
	buf[j]=0;
	do
	{
		buf[--j]='0'+(i%10);
		i /= 10;
	} while (i>0);
	print(buf+j,x,y);
}

void clearPals()
{
	u16 *p1 = SCROLL_1_PALETTE;
	u16 *p2 = SCROLL_2_PALETTE;
	u16 *p3 = SPRITE_PALETTE;
	u16 i;
	for (i=0;i<64;i++)
		p1[i] = p2[i] = p3[i] = 0;
}

u16 loadFont()
{
	u16 *p0 = TILE_RAM;
	u16 *p1 = SCROLL_1_PALETTE;
	u16 i,j,l;

	for (i=0;i<8;i++)
		p0[i] = 0;

	l = FONT0_TILES<<3;
	for (j=0;j<l;j++)
		p0[i++] = FONT0_TILES1[j];

	for (j=0;j<FONT0_NPALS1*4;j++)
		p1[j] = FONT0_PALS1[j];

	return i;
}

void loadMenuGfx()
{
	u16 i,j,k,l;
	u16 *p0 = TILE_RAM;
	u16 *p2 = SCROLL_2_PALETTE;
	u16 *p3 = SPRITE_PALETTE;

	i = loadFont();

	l = BKGD_TILES<<3;
	for (j=0;j<l;j++)
		p0[i++] = BKGD_TILES1[j];

	l = (CURSOR_TILES>>1)<<3;
	for (j=0;j<l;j++)
		p0[i++] = CURSOR_TILES1[j];
	for (j=0;j<l;j++)
		p0[i++] = CURSOR_TILES2[j];

	for (i=0;i<BKGD_NPALS1*4;i++)
		p2[i] = BKGD_PALS1[i];
	for (i=0;i<CURSOR_NPALS1*4;i++)
		p3[i] = CURSOR_PALS1[i];
	for (j=0;j<CURSOR_NPALS2*4;j++)
		p3[i++] = CURSOR_PALS2[j];

	ClearScreen(SCR_1_PLANE);
	ClearScreen(SCR_2_PLANE);
	i = 0;
	for (j=0;j<BKGD_HEIGHT;j++)
	{
		for (k=0;k<BKGD_WIDTH;k++)
		{
			(SCROLL_PLANE_2)[((j+2)<<5)+k] = (((u16)BKGD_PALIDX1[i])<<9)+(FONT0_TILES+1+i);
			i+=1;
		}
	}
}

const SOD_IMG presents[2] =
{
	PRESENT0_ID,
	PRESENT1_ID
};

void loadPresentGfx()
{
	u16 i,j,k,l,t;
	u8 ii;
	u16 *p0 = TILE_RAM;
	u16 *p2 = SCROLL_2_PALETTE;
	u16 *p3 = SPRITE_PALETTE;

	clearPals();
	ClearScreen(SCR_1_PLANE);
	ClearScreen(SCR_2_PLANE);

	i = loadFont();

	l = presents[lvl&1].nbt<<3;
	for (j=0;j<l;j++)
		p0[i++] = presents[lvl&1].t1[j];

	t = (i>>3);
	l = COMMON_TILES<<3;
	for (j=0;j<l;j++)
		p0[i++] = COMMON_TILES1[j];

	for (i=0;i<presents[lvl&1].np1*4;i++)
		p2[i] = presents[lvl&1].p1[i];

	for (i=0;i<COMMON_NPALS1*4;i++)
		p3[i] = COMMON_PALS1[i];

	i = 0;
	for (j=0;j<presents[lvl&1].h;j++)
	{
		for (k=0;k<presents[lvl&1].w;k++)
		{
			(SCROLL_PLANE_2)[((j+2)<<5)+k] = (((u16)presents[lvl&1].pi1[i])<<9)+(FONT0_TILES+1+i);
			i+=1;
		}
	}

	for (ii=0;ii<lives;ii++)
		SetSprite(ii,t+11,(lvl&1) ? 144-ii*10 : 4+ii*10,140,0,TOP_PRIO);
}

void loadLoseGfx()
{
	u16 i,j,k,l,t;
	u8 ii;
	u16 *p0 = TILE_RAM;
	u16 *p2 = SCROLL_2_PALETTE;
	u16 *p3 = SPRITE_PALETTE;

	clearPals();
	ClearScreen(SCR_1_PLANE);
	ClearScreen(SCR_2_PLANE);

	i = loadFont();

	l = LOOSE_IMG.nbt<<3;
	for (j=0;j<l;j++)
		p0[i++] = LOOSE_IMG.t1[j];

	for (i=0;i<LOOSE_IMG.np1*4;i++)
		p2[i] = LOOSE_IMG.p1[i];

	i = 0;
	for (j=0;j<LOOSE_IMG.h;j++)
	{
		for (k=0;k<LOOSE_IMG.w;k++)
		{
			(SCROLL_PLANE_2)[((j+2)<<5)+k+7] = (((u16)LOOSE_IMG.pi1[i])<<9)+(FONT0_TILES+1+i);
			i+=1;
		}
	}
}

void showHC(void *img)
{
	SCR1_X = SCR1_Y = SCR2_X = SCR2_Y = 0;
	curimg=0;
	hc_load(img);
	curimg=img;
}

void loadGfx()
{
	u16 i,j,l;
	u16 *p0 = TILE_RAM;

	clearPals();

	l = (decors[lvl].nbt>>1)<<3;
	for (i=0;i<l;i++)
		p0[i] = decors[lvl].t1[i];
	for (j=0;j<l;j++)
		p0[i++] = decors[lvl].t2[j];
	l = (PERSO_TILES>>1)<<3;
	for (j=0;j<l;j++)
		p0[i++] = PERSO_TILES1[j];
	for (j=0;j<l;j++)
		p0[i++] = PERSO_TILES2[j];
	l = enmy_img[lvl].nbt<<3;
	for (j=0;j<l;j++)
		p0[i++] = enmy_img[lvl].t1[j];
	l = COMMON_TILES<<3;
	for (j=0;j<l;j++)
		p0[i++] = COMMON_TILES1[j];
}

u16 scol(u16 c)
{
	u16 r = c&0xf;
	u16 g = (c>>4)&0xf;
	u16 b = (c>>8)&0xf;
	u16 a = (r+g+b)/3;
	switch(lvl)
	{
		case 2:
		case 5:
			r = (a<<8)|(a<<4)|(a);	// silver
			break;
		case 8:
		case 11:
			r = (a<<4)|(a);			// gold
			break;
	}
	return r;
}

void loadPals()
{
	u16 i,j;
	u16 *p1 = SCROLL_1_PALETTE;
	u16 *p2 = SCROLL_2_PALETTE;
	u16 *p3 = SPRITE_PALETTE;

	for (i=0;i<decors[lvl].np1*4;i++)
	{
		p1[i] = decors[lvl].p1[i];
		p2[i] = decors[lvl].p2[i];
	}
	for (i=0;i<PERSO_NPALS1*4;i++)
		p3[i] = PERSO_PALS1[i];
	for (j=0;j<PERSO_NPALS2*4;j++)
		p3[i++] = PERSO_PALS2[j];
	if (boss)
	{
		for (j=0;j<PERSO_NPALS1*4;j++)
			p3[i++] = scol(PERSO_PALS1[j]);
		for (j=0;j<PERSO_NPALS2*4;j++)
			p3[i++] = scol(PERSO_PALS2[j]);
	}
	else
	{
		for (j=0;j<enmy_img[lvl].np1*4;j++)
			p3[i++] = enmy_img[lvl].p1[j];
		for (j=0;j<COMMON_NPALS1*4;j++)
			p3[i++] = COMMON_PALS1[j];
	}
}

void setTile16(u16 i,u16 j,u16 k,u8 flag)
{
	u16 t = 0;
	u16 a,b,c,d,f;
	i<<=1;
	j<<=1;
	k<<=1;
	while (k>=decors[lvl].w)
	{
		t+=(decors[lvl].w<<1);
		k-=decors[lvl].w;
	}
	k+=t;
	switch(flag)
	{
		case 0: // RIGHT
		case 2: // UP
			a = k;
			b = k+1;
			c = k+decors[lvl].w;
			d = k+decors[lvl].w+1;
			f = 0;
			break;
		case 1: // LEFT
			a = k+1;
			b = k;
			c = k+decors[lvl].w+1;
			d = k+decors[lvl].w;
			f = HF_TILE;
			break;
		case 3: // DOWN
		default:
			a = k+decors[lvl].w;
			b = k+decors[lvl].w+1;
			c = k;
			d = k+1;
			f = VF_TILE;
			break;
	}
	t = (j<<5)+i;
	(SCROLL_PLANE_1)[t]    = a    +(((u16)decors[lvl].pi1[a])<<9)+f;
	(SCROLL_PLANE_2)[t]    = a+128+(((u16)decors[lvl].pi2[a])<<9)+f;
	(SCROLL_PLANE_1)[t+1]  = b    +(((u16)decors[lvl].pi1[b])<<9)+f;
	(SCROLL_PLANE_2)[t+1]  = b+128+(((u16)decors[lvl].pi2[b])<<9)+f;
	(SCROLL_PLANE_1)[t+32] = c    +(((u16)decors[lvl].pi1[c])<<9)+f;
	(SCROLL_PLANE_2)[t+32] = c+128+(((u16)decors[lvl].pi2[c])<<9)+f;
	(SCROLL_PLANE_1)[t+33] = d    +(((u16)decors[lvl].pi1[d])<<9)+f;
	(SCROLL_PLANE_2)[t+33] = d+128+(((u16)decors[lvl].pi2[d])<<9)+f;
}

#define MIN(a,b) ((a)<(b)?(a):(b))

void showBoard()
{
	u16 i,j;
	u16 minx = scr_x>>4;
	u16 miny = scr_y>>4;
	u16 maxx,maxy;
	maxx = MIN(15,minx+12);
	maxy = MIN(15,miny+12);
	SCR2_X = SCR1_X = scr_x;
	SCR2_Y = SCR1_Y = scr_y;
	for (i=minx;i<maxx;i++)
	{
		for (j=miny;j<maxy;j++)
		{
			if (curmod[i][j])
			{
				setTile16(i,j,curmap[i][j],curflag[i][j]);
				curmod[i][j] = 0;
			}
		}
	}
}

void markBomb(u8 x,u8 y,u8 power,u8 dx,s8 dy,s8 dn)
{
	u8 i;
	curmark[x][y]+=dn;
	for (i=0;i<power && curmap[x][y]>1;i++)
	{
		x+=dx;
		y+=dy;
		curmark[x][y]+=dn;
	}
}

void addBomb(u8 owner,u8 time,u8 x,u8 y,u8 power)
{
	if (curbombs[x][y]!=MAX_BOMBS)
		return;
	bombs[nb_bombs].owner = owner;
	bombs[nb_bombs].time = time;
	bombs[nb_bombs].x = x;
	bombs[nb_bombs].y = y;
	bombs[nb_bombs].anim = 8;
	bombs[nb_bombs].count = 8;
	bombs[nb_bombs].power = power;
	curmap[x][y] = bombs[nb_bombs].anim;
	curmod[x][y] = 1;
	curbombs[x][y] = nb_bombs;
	if (owner!=NO_OWNER)
	{
		chars[owner].tempo = 16;
		chars[owner].nb++;
		SL_PlaySFX(2);
	}
	if (boss)
	{
		markBomb(x,y,power, 1, 0, 1);
		markBomb(x,y,power,-1, 0, 1);
		markBomb(x,y,power, 0,-1, 1);
		markBomb(x,y,power, 0, 1, 1);
	}
	nb_bombs+=1;
}

void updateBoard()
{
	u8 i,j,k,l;
	if ((--cntupd)>0)
		return;
	cntupd = 4;
	for (i=1;i<14;i++)
	{
		for (j=1;j<14;j++)
		{
			k = curmap[i][j];
			if (k==31)
				continue;
			else if (k>2 && k<7)
			{
				curmap[i][j]+=1;
				curmod[i][j]=1;
			}
			else if (k==7 || (k>=22 && k<25) || k>=29)
			{
				if (k==7) // Options
				{
					l = GetRandom(boss?8:31);
					if (l<4)
						curmap[i][j]=12+l;
					else if ((lvl>0) && (((lvl-1)%3)==0) && (l<8))
						addBomb(NO_OWNER,8,i,j,2);
					else
						curmap[i][j]=11;
				}
				else
					curmap[i][j]=11;
				curflag[i][j]=0;
				curmod[i][j]=1;
			}
			else if (k>=16 && k<22)
			{
				curmap[i][j]+=3;
				curmod[i][j]=1;
			}
			else if (k>=25)
			{
				curmap[i][j]+=2;
				curmod[i][j]=1;
			}
		}
	}
}

u8 setSprite(u8 idx,u8 i)
{
	u16 n0,n1,t0,t1;
	u8 x = chars[i].x-scr_x;
	u8 y = chars[i].y-scr_y;
	u8 flag = TOP_PRIO;

	n0 = (chars[i].anim<<1);

	if (chars[i].atype==0)
	{
		switch(chars[i].dir)
		{
			case J_UP:
				n0 += 4;
				n1 = n0+1;
				break;
			case J_RIGHT:
				n0 += 8;
				n1 = n0+1;
				break;
			case J_LEFT:
				n0 += 9;
				n1 = n0-1;
				flag |= H_FLIP;
				break;
			case J_DOWN:
			default:
				n1 = n0+1;
				break;
		}
	}
	else
	{
		n0+=(chars[i].num<<3);
		n1 = n0+1;
	}
	
	t0 = n0+decors[lvl].nbt+PERSO_TILES;
	t1 = n1+decors[lvl].nbt+PERSO_TILES;

	if ((--chars[i].count)==0)
	{
		chars[i].count = 4;
		if ((++chars[i].anim)==2+(chars[i].atype<<1))
			chars[i].anim = 0;
		if (chars[i].alive>1)
		{
			if (--chars[i].alive==1)
			{
				chars[i].alive = 0;
				score += 10;
				SL_PlaySFX(8);
			}
		}
	}

	if (chars[i].alive==1 || (chars[i].anim&1)==0)
	{
		if (enmy_img[lvl].h>2)
		{
			SetSprite(idx++,t0						,x  ,y-8,8+enmy_img[lvl].pi1[n0]						,flag);
			SetSprite(idx++,t1						,x+8,y-8,8+enmy_img[lvl].pi1[n1]						,flag);
			SetSprite(idx++,t0+enmy_img[lvl].w		,x  ,y  ,8+enmy_img[lvl].pi1[n0+enmy_img[lvl].w]		,flag);
			SetSprite(idx++,t1+enmy_img[lvl].w		,x+8,y  ,8+enmy_img[lvl].pi1[n1+enmy_img[lvl].w]		,flag);
			SetSprite(idx++,t0+(enmy_img[lvl].w<<1)	,x  ,y+8,8+enmy_img[lvl].pi1[n0+(enmy_img[lvl].w<<1)]	,flag);
			SetSprite(idx  ,t1+(enmy_img[lvl].w<<1)	,x+8,y+8,8+enmy_img[lvl].pi1[n1+(enmy_img[lvl].w<<1)]	,flag);
			return 6;
		}
		else
		{
			SetSprite(idx++,t0						,x  ,y	,8+enmy_img[lvl].pi1[n0]						,flag);
			SetSprite(idx++,t1						,x+8,y	,8+enmy_img[lvl].pi1[n1]						,flag);
			SetSprite(idx++,t0+enmy_img[lvl].w		,x  ,y+8,8+enmy_img[lvl].pi1[n0+enmy_img[lvl].w]		,flag);
			SetSprite(idx  ,t1+enmy_img[lvl].w		,x+8,y+8,8+enmy_img[lvl].pi1[n1+enmy_img[lvl].w]		,flag);
			return 4;
		}
	}
	return 0;
}

void setMainSprite(u8 n,u8 i)
{
	u16 n0,n1,t0,t1;
	u8 x = chars[i].x-scr_x;
	u8 y = chars[i].y-scr_y;
	u8 mask = 0;

	if (chars[i].alive==1)
	{
		switch(chars[i].dir)
		{
			case J_LEFT:
				n0 = 12+(chars[i].anim<<1);
				mask = H_FLIP;
				break;
			case J_RIGHT:
				n0 = 12+(chars[i].anim<<1);
				break;
			case J_UP:
				n0 = 6 + ((chars[i].anim < 3 ? chars[i].anim : 5-chars[i].anim)<<1);
				mask = chars[i].anim > 2 ? H_FLIP : 0;
				break;
			case J_DOWN:
			default:
				n0 = (chars[i].anim < 3 ? chars[i].anim : 5-chars[i].anim)<<1;
				mask = chars[i].anim > 2 ? H_FLIP : 0;
				break;
		}
	}
	else if (chars[i].alive==ENEMY) // enemy
	{
		n0 = 30;
	}
	else if (chars[i].alive==FINISHED) // level finished
	{
		n0 = chars[i].anim&1 ? 32 : 0;
	}
	else // explosion
	{
		n0 = 20 + (chars[i].anim<<1);
	}
	if (mask)
	{
		n1 = n0;
		n0 = n0+1;
	}
	else
		n1 = n0+1;
	mask |= TOP_PRIO;

	if ((--chars[i].count)==0)
	{
		if (chars[i].dir || chars[i].alive!=1)
			chars[i].anim+=1;
		if (chars[i].anim==5)
		{
			chars[i].anim = 0;
			if (chars[i].alive==ENEMY || chars[i].alive==EXPLODE)
				chars[i].alive = 0;
			else if (chars[i].alive==FINISHED)
				door = 0;
		}
		chars[i].count = 4;
	}

	t0=n0+256;
	t1=n1+256;
	i = i>0 ? PERSO_NPALS1+PERSO_NPALS2 : 0;

	SetSprite(n++,t0              ,x  ,y-8,i+PERSO_PALIDX1[n0]              ,mask);
	SetSprite(n++,t1              ,x+8,y-8,i+PERSO_PALIDX1[n1]              ,mask);
	SetSprite(n++,t0+PERSO_WIDTH  ,x  ,y  ,i+PERSO_PALIDX1[n0+PERSO_WIDTH]  ,mask);
	SetSprite(n++,t1+PERSO_WIDTH  ,x+8,y  ,i+PERSO_PALIDX1[n1+PERSO_WIDTH]  ,mask);
	SetSprite(n++,t0+2*PERSO_WIDTH,x  ,y+8,i+PERSO_PALIDX1[n0+2*PERSO_WIDTH],mask);
	SetSprite(n++,t1+2*PERSO_WIDTH,x+8,y+8,i+PERSO_PALIDX1[n1+2*PERSO_WIDTH],mask);

	t0+=(PERSO_TILES>>1);
	t1+=(PERSO_TILES>>1);
	SetSprite(n++,t0              ,x  ,y-8,i+PERSO_NPALS1+PERSO_PALIDX2[n0]              ,mask);
	SetSprite(n++,t1              ,x+8,y-8,i+PERSO_NPALS1+PERSO_PALIDX2[n1]              ,mask);
	SetSprite(n++,t0+PERSO_WIDTH  ,x  ,y  ,i+PERSO_NPALS1+PERSO_PALIDX2[n0+PERSO_WIDTH]  ,mask);
	SetSprite(n++,t1+PERSO_WIDTH  ,x+8,y  ,i+PERSO_NPALS1+PERSO_PALIDX2[n1+PERSO_WIDTH]  ,mask);
	SetSprite(n++,t0+2*PERSO_WIDTH,x  ,y+8,i+PERSO_NPALS1+PERSO_PALIDX2[n0+2*PERSO_WIDTH],mask);
	SetSprite(n  ,t1+2*PERSO_WIDTH,x+8,y+8,i+PERSO_NPALS1+PERSO_PALIDX2[n1+2*PERSO_WIDTH],mask);
}

void getData()
{
	u16 i;
	getSavedData();
	if (data[4]>0)
		return;
	for (i=0;i<3;i++)
	{
		data[4+i*6]='C';
		data[4+i*6+1]='O';
		data[4+i*6+2]='M';
		data[4+i*6+3]=0;
	}
	((u16*)data)[4]  = 1000;
	((u16*)data)[7]  = 750;
	((u16*)data)[10] = 500;
	data[22] = 0;
}

void showTitle()
{
	u8 j;
	SL_LoadGroup(magical2,sizeof(magical2));
	SL_PlayTune(0);
	showHC((void*)TITLESCR_ID);
	while(!((j=JOYPAD)&J_A));
	SL_PlaySFX(0);
	while((j=JOYPAD)&J_A);
	curimg=0;
	clearPals();
	ClearScreen(SCR_1_PLANE);
	ClearScreen(SCR_2_PLANE);
}

void initMap()
{
	u8 i,j;
	for (i=0;i<15;i++)
	{
		for (j=0;j<15;j++)
		{
			curmap[i][j] = maps[lvl][j][i];
			curmod[i][j] = 1;
			curflag[i][j] = 0;
			curbombs[i][j] = MAX_BOMBS;
			curmark[i][j] = 0;
		}
	}

	j = 1;
	i = 1;
	while (i<enemies[lvl][0]*3+1)
	{
		chars[j].alive = 1;
		chars[j].num = enemies[lvl][i++];
		chars[j].kind = enmy_types[lvl][chars[j].num*2];
		chars[j].atype = enmy_types[lvl][chars[j].num*2+1];
		chars[j].x = enemies[lvl][i++]<<4;
		chars[j].y = enemies[lvl][i++]<<4;
		chars[j].idx = 0;
		chars[j].dir = J_DOWN;
		chars[j].anim = 0;
		chars[j].count = 4;
		chars[j].speed = 0;
		chars[j].power = 1;
		chars[j].nbmax = 1;
		chars[j].nb = 0;
		chars[j].tempo = 0;
		j+=1;
	}

	for (i=j;i<MAX_ENTITIES;i++)
		chars[i].alive = 0;

	scr_x = scr_y = 0;
	showBoard();
	cntupd = 1;
	nb_bombs = 0;
}

void initChar()
{
	chars[0].alive = 1;
	chars[0].idx = 0;
	chars[0].x = 16;
	chars[0].y = 16;
	chars[0].speed = 0;
	chars[0].power = 1;
	chars[0].nbmax = 1;
	chars[0].nb = 0;
	chars[0].dir = J_DOWN;
	chars[0].anim = 0;
	chars[0].count = 4;
	chars[0].tempo = 0;
	chars[0].invers = 0;
	chars[0].kind = 0;
}

void clearSprites(u8 start)
{
	u16 *theSprite = (u16*)SPRITE_RAM;
	u16 i;

	for (i=(start<<1);i<128;i+=2)
		*(theSprite+i) = 0;
}

u8 isFree(u8 x,u8 y,u8 dir,u8 nomark,u8 flying)
{
	u8 x0,x1;
	u8 y0,y1;
	u8 lim = flying ? 1 : 10;

	x0 = x>>4;
	y0 = y>>4;
	x1 = x0 + (x&0x0f ? 1 : 0);
	y1 = y0 + (y&0x0f ? 1 : 0);

	if (nomark==0 && curmap[x0][y0]<11) // boss on bomb
		nomark = 1;

	switch(dir)
	{
		case J_LEFT:
			x0 -= 1;
			return (curmap[x0][y0]>lim && curmap[x0][y1]>lim) && (nomark || (curmark[x0][y0]==0 && curmap[x0][y0]<16));
		case J_RIGHT:
			x0 += 1;
			return (curmap[x0][y0]>lim && curmap[x0][y1]>lim) && (nomark || (curmark[x0][y0]==0 && curmap[x0][y0]<16));
		case J_UP:
			y0 -= 1;
			return (curmap[x0][y0]>lim && curmap[x1][y0]>lim) && (nomark || (curmark[x0][y0]==0 && curmap[x0][y0]<16));
		case J_DOWN:
			y0 += 1;
			return (curmap[x0][y0]>lim && curmap[x1][y0]>lim) && (nomark || (curmark[x0][y0]==0 && curmap[x0][y0]<16));
	}
	return (curmap[x0][y0]>lim && curmap[x1][y0]>lim) && (nomark || curmark[x0][y0]==0);
}

void checkItem(u8 i,u8 x,u8 y,u8 b)
{
	u8 item = curmap[x][y];
	switch(item)
	{
		case 12:
			if (chars[i].kind)
				return;
			chars[i].nbmax+=1;
			break;
		case 13:
			if (chars[i].kind)
				return;
			chars[i].power+=1;
			break;
		case 14:
			if (b || chars[i].kind)
				return;
			chars[i].speed += 512;
			break;
		case 15:
			if (chars[i].kind)
				return;
			chars[i].invers = 255;
			break;
		case 31:
			chars[0].alive = FINISHED;
			chars[0].count = 4;
			chars[0].anim = 0;
			SL_PlaySFX(3);
			return;
		default:
			if (item>=16 && item<31)
			{
				chars[i].alive = (i==0||boss) ? EXPLODE : 16; // explode
				if (i>0 && boss)
					score += 15;
				chars[i].anim = 1; // main character anim
				chars[i].count = 4;
				if (i==0)
					SL_PlaySFX(0);
			}
			return;
	}
	curmap[x][y] = 11;
	curmod[x][y] = 1;
	if (i==0)
		score += 5;
	SL_PlaySFX(1);
}

void checkMap(u8 i)
{
	u8 x = chars[i].x;
	u8 y = chars[i].y;
	u8 x0 = x>>4;
	u8 y0 = y>>4;
	u8 x1 = x&0x0f;
	u8 y1 = y&0x0f;
	u8 b;

	if (x1>11)
		x1 = x0 = x0+1;
	else if (x1>3)
		x1 = x0+1;
	else
		x1 = x0;

	if (y1>11)
		y1 = y0 = y0+1;
	else if (y1>3)
		y1 = y0+1;
	else
		y1 = y0;

	b = (x&0x3)||(y&0x3); // alignment for speed

	checkItem(i,x0,y0,b);

	if (x0!=x1 || y0!=y1)
		checkItem(i,x1,y1,b);
}

void moveChar()
{
	u8 j = JOYPAD,i = 1;

	if (chars[0].alive!=1)
		return;

	if (chars[0].invers)
		chars[0].invers-=1;
	if (chars[0].speed)
		chars[0].speed-=1;

	if ((chars[0].invers==0 && (j&J_DOWN)) || (chars[0].invers && (j&J_UP)) ||
		((chars[0].y&0xf) && chars[0].dir==J_DOWN))
	{
		if ((chars[0].y&0x0f)||isFree(chars[0].x,chars[0].y,J_DOWN,1,0))
		{
			i = 0;
			if (chars[0].dir!=J_DOWN)
			{
				chars[0].dir = J_DOWN;
				chars[0].anim = 0;
			}
			chars[0].y += chars[0].speed?2:1;
			if (chars[0].y>80 && chars[0].y<=168)
				scr_y += chars[0].speed?2:1;
		}
	}
	if ((chars[0].invers==0 && (j&J_LEFT)) || (chars[0].invers && (j&J_RIGHT)) ||
		((chars[0].x&0xf) && chars[0].dir==J_LEFT))
	{
		if ((chars[0].x&0x0f)||isFree(chars[0].x,chars[0].y,J_LEFT,1,0))
		{
			i = 0;
			if (chars[0].dir!=J_LEFT)
			{
				chars[0].dir = J_LEFT;
				chars[0].anim = 0;
			}
			chars[0].x -= chars[0].speed?2:1;
			if (chars[0].x>=80 && chars[0].x<160)
				scr_x -= chars[0].speed?2:1;
		}
	}
	if ((chars[0].invers==0 && (j&J_UP)) || (chars[0].invers && (j&J_DOWN)) ||
		((chars[0].y&0xf) && chars[0].dir==J_UP))
	{
		if ((chars[0].y&0x0f)||isFree(chars[0].x,chars[0].y,J_UP,1,0))
		{
			i = 0;
			if (chars[0].dir!=J_UP)
			{
				chars[0].dir = J_UP;
				chars[0].anim = 0;
			}
			chars[0].y -= chars[0].speed?2:1;
			if (chars[0].y>=80 && chars[0].y<168)
				scr_y -= chars[0].speed?2:1;
		}
	}
	if ((chars[0].invers==0 && (j&J_RIGHT)) || (chars[0].invers && (j&J_LEFT)) ||
		((chars[0].x&0xf) && chars[0].dir==J_RIGHT))
	{
		if ((chars[0].x&0x0f)||isFree(chars[0].x,chars[0].y,J_RIGHT,1,0))
		{
			i = 0;
			if (chars[0].dir!=J_RIGHT)
			{
				chars[0].dir = J_RIGHT;
				chars[0].anim = 0;
			}
			chars[0].x += chars[0].speed?2:1;
			if (chars[0].x>80 && chars[0].x<=160)
				scr_x += chars[0].speed?2:1;
		}
	}
	if (i)
		chars[0].anim = 0;

	if (chars[0].tempo>0)
		chars[0].tempo-=1;
	if (j&J_A && nb_bombs<MAX_BOMBS && chars[0].tempo==0 && chars[0].nb<chars[0].nbmax)
	{
		u8 x = chars[0].x>>4;
		u8 y = chars[0].y>>4;

		if (chars[0].dir==J_LEFT && (chars[0].x&0xf)>5)
			x+=1;
		if (chars[0].dir==J_UP && (chars[0].y&0xf)>5)
			y+=1;
		if (chars[0].dir==J_RIGHT && (chars[0].x&0xf)>10)
			x+=1;
		if (chars[0].dir==J_DOWN && (chars[0].y&0xf)>10)
			y+=1;
		if (curmap[x][y]==11)
			addBomb(0,16-(chars[0].power<<1),x,y,chars[0].power);
	}

	checkMap(0);
}

void moveEnemy()
{
	u8 i,j,k,x,y,l,m,nb=0;
	u8 d[4] = {J_UP,J_RIGHT,J_DOWN,J_LEFT};
	k = GetRandom(16);
	for (i=1;i<MAX_ENTITIES;i++)
	{
		if (chars[i].alive!=1)
			continue;
		nb+=1;
		if (chars[i].tempo>0)
			chars[i].tempo-=1;
		if (chars[i].speed)
			chars[i].speed-=1;
		checkMap(i);
		x = chars[i].x;
		y = chars[i].y;
		if ((x&0x0f)==0 && (y&0x0f)==0)
		{
			m = 0xff;
			if (boss && nb<2)
			{
				if (chars[0].x<chars[i].x)
					k = (chars[0].y>chars[i].y) ? 2 : 3;
				else
					k = (chars[0].y<chars[i].y) ? 0 : 1;
			}
			for (j=0;j<4;j++)
			{
				l = (boss ? j+k : j+i+k)&3;
				if (isFree(x,y,d[l],1-boss,chars[i].kind))
				{
					if (chars[i].dir==d[(l+2)&3])
						m = d[l];
					else
					{
						chars[i].dir = d[l];
						break;
					}
				}
			}
			if (j==4 && m==0xff && boss)
			{
				for (j=0;j<4;j++)
				{
					if (boss && (!isFree(x,y,0,0,0)) && isFree(x,y,d[j],1,0))
					{
						chars[i].dir = d[j];
						break;
					}
				}
			}
			if (j==4) // locked
			{
				if (m==0xff)
					chars[i].anim = chars[i].dir = 0;
				else
					chars[i].dir = m;
			}
			if (boss && chars[i].dir && chars[i].tempo==0 && chars[i].nb<chars[i].nbmax && GetRandom(16)<4)
			{
				switch(chars[i].dir)
				{
					case J_RIGHT:
						j = isFree(x+16,y-16,0,0,0) && isFree(x+16,y+16,0,0,0);
						break;
					case J_LEFT:
						j = isFree(x-16,y-16,0,0,0) && isFree(x-16,y+16,0,0,0);
						break;
					case J_UP:
						j = isFree(x-16,y-16,0,0,0) && isFree(x+16,y-16,0,0,0);
						break;
					case J_DOWN:
						j = isFree(x-16,y+16,0,0,0) && isFree(x+16,y+16,0,0,0);
						break;
				}
				if (j)
					addBomb(i,12,x>>4,y>>4,chars[i].power);
			}
		}
		switch(chars[i].dir)
		{
			case J_LEFT:
				chars[i].x -= chars[i].speed?2:1;
				break;
			case J_RIGHT:
				chars[i].x += chars[i].speed?2:1;
				break;
			case J_UP:		
				chars[i].y -= chars[i].speed?2:1;
				break;
			case J_DOWN:
				chars[i].y += chars[i].speed?2:1;
				break;
		}
		// check collision
		if (!boss)
		{
			if (chars[i].x>chars[0].x)
				m = chars[i].x-chars[0].x;
			else
				m = chars[0].x-chars[i].x;
			if (m<12)
			{
				if (chars[i].y>chars[0].y)
					m = chars[i].y-chars[0].y;
				else
					m = chars[0].y-chars[i].y;
				if (m<12)
				{
					chars[0].alive = ENEMY;
					chars[0].count = 4;
					chars[0].anim = 0;
					SL_PlaySFX(0);
				}
			}
		}
	}
}

u8 remove[MAX_BOMBS];

void explode(u8);

void checkBomb(u8 x,u8 y,u8 power,u8 dx,s8 dy,u8 flag)
{
	u8 i;
	for (i=0;i<power;i++)
	{
		x+=dx;
		y+=dy;
		if (curmap[x][y]<8)
		{
			if (curmap[x][y]==2)
			{
				curmap[x][y] = 3;
				curmod[x][y] = 1;
			}
			break;
		}
		if (curmap[x][y]>10)
		{
			curmap[x][y] = (i+1==power?18:17)+(flag>1?8:0);
			curmod[x][y] = 1;
			curflag[x][y] = flag;
			continue;
		}
		explode(curbombs[x][y]);
		break;
	}
}

void explode(u8 i)
{
	u8 x,y;
	if (remove[i])
		return;
	x = bombs[i].x;
	y = bombs[i].y;
	cntupd = 6;
	remove[i] = 1;
	curmap[x][y] = 16;
	curmod[x][y] = 1;
	curbombs[x][y] = MAX_BOMBS;
	checkBomb(x,y,bombs[i].power, 1, 0, 0);
	checkBomb(x,y,bombs[i].power,-1, 0, 1);
	checkBomb(x,y,bombs[i].power, 0,-1, 2);
	checkBomb(x,y,bombs[i].power, 0, 1, 3);
	if (boss)
	{
		markBomb(x,y,bombs[i].power, 1, 0,-1);
		markBomb(x,y,bombs[i].power,-1, 0,-1);
		markBomb(x,y,bombs[i].power, 0,-1,-1);
		markBomb(x,y,bombs[i].power, 0, 1,-1);
	}
	if (!chain)
	{
		SL_PlaySFX(4);
		chain = 1;
	}
}

void mngBombs()
{
	u8 i,j,k,l;

	for (i=0;i<nb_bombs;i++)
		remove[i] = 0;

	chain = 0;

	for (i=0;i<nb_bombs;i++)
	{
		if (remove[i] || (--bombs[i].count)>0)
			continue;
		if ((--bombs[i].time)==0)
		{
			explode(i);
			continue;
		}
		bombs[i].anim += 1;
		if (bombs[i].anim==11)
			bombs[i].anim = 8;
		bombs[i].count = 8;
		curmap[bombs[i].x][bombs[i].y] = bombs[i].anim;
		curmod[bombs[i].x][bombs[i].y] = 1;
	}

	k = l = 0;
	for (i=0;i<nb_bombs;i++,l++)
	{
		if (remove[l])
		{
			k+=1;
			if (bombs[i].owner!=NO_OWNER)
				chars[bombs[i].owner].nb-=1;
			for (j=i;j+1<nb_bombs;j++)
				bombs[j] = bombs[j+1];
			i--;
			nb_bombs--;
		}
		remove[l] = k;
	}

	for (i=1;i<14;i++)
	{
		for (j=1;j<14;j++)
		{
			if (curbombs[i][j]!=MAX_BOMBS)
				curbombs[i][j]-=remove[curbombs[i][j]];
		}
	}
}

u8 curtune;

void playNextTune()
{
	const u8* musix[7] = {	metalslug0,metalslug1,metalslug2,metalslug3,
					 		metalslug4,metalslug5,metalslug9};
	u16 sizes[7] =	{sizeof(metalslug0),sizeof(metalslug1),sizeof(metalslug2),
					 sizeof(metalslug3),sizeof(metalslug4),sizeof(metalslug5),
					 sizeof(metalslug9)};
	SL_LoadGroup(musix[curtune],sizes[curtune]);
	if ((++curtune)==7)
		curtune = 0;
	SL_PlayTune(0);
}

void doPresent()
{
	u8 i,j;

	SCR1_Y = 50;
	SCR1_X = (lvl&1) ? -16 : 10 ;
	SCR2_Y = 0;
	SCR2_X = (lvl&1) ? -160 : 100;
	clearSprites(0);
	loadPresentGfx();

	print("LEVEL",2,3);
	printi(lvl+1,14,3);

	for (i=0;i<50 & !((j=JOYPAD)&J_A);i++)
	{
		Sleep(1);
		SCR1_Y-=2;
		SCR2_X+=(lvl&1) ? -2 : 2;
	}

	VBCounter = 0;
	while (VBCounter<100 && !((j=JOYPAD)&J_A));
	while((j=JOYPAD)&J_A);
}

void highscores()
{
	u8 i,j;

	loadMenuGfx();
	print("HISCORES",2,3);
	for (i=0;i<3;i++)
	{
		print((const char*)(data+4+i*6),2,8+i*3);
		printi(((u16*)data)[2+i*3+2],10,8+i*3);
	}
	while(!((j=JOYPAD)&J_A));
	SL_PlaySFX(0);
	while((j=JOYPAD)&J_A);
}

void newHighScore()
{
	u8 i,j;
	char buf[2],n[4];

	SL_LoadGroup(magical0,sizeof(magical0));
	SL_PlayTune(0);

	loadMenuGfx();

	SCR1_X = 4;
	print("GREAT !",4,3);
	print("ENTER",1,7);
	print("YOUR",12,7);
	print("NAME:",6,10);

	buf[0] = 'A';
	n[3] = buf[1] = 0;
	for (i=0;i<3;i++)
	{
		do
		{
			print(buf,8+i*2,13);
			j = JOYPAD;
			if (j&J_UP || j&J_LEFT)
				buf[0] = buf[0]=='A'?'Z':buf[0]-1;
			if (j&J_RIGHT || j&J_DOWN)
				buf[0] = buf[0]=='Z'?'A':buf[0]+1;
			Sleep(j?8:4);
		}
		while(!(j&J_A));
		n[i] = buf[0];
		while((j=JOYPAD)&J_A);
	}

	for (i=0;score<((u16*)data)[2+i*3+2];i++);

	i = 4+i*6;
	for (j=16;j>i;j--)
		data[j+5] = data[j-1];
	for (j=0;j<4;j++)
		data[i++] = n[j];
	((u16*)data)[i>>1] = score;
}

void youLose()
{
	u8 j;

	SL_LoadGroup(magical1,sizeof(magical1));
	SL_PlayTune(0);

	loadLoseGfx();
	print("YOU",7,11);
	print("LOSE!",6,14);

	while(!((j=JOYPAD)&J_A));
	while((j=JOYPAD)&J_A);
}

void youWin()
{
	u8 j;

	ClearScreen(SCR_1_PLANE);
	ClearScreen(SCR_2_PLANE);
	clearPals();

	SL_LoadGroup(cotton0,sizeof(cotton0));
	SL_PlayTune(0);

	moviecnt = 0;

	curmovie.data = (u16*)BEND_DATA;
	curmovie.count = BEND_COUNT;
	curmovie.delay = BEND_DELAY;
	mp_load(&curmovie);

	while((moviecnt<BEND_COUNT*BEND_DELAY) && (!((j=JOYPAD)&J_A)));
	while((j=JOYPAD)&J_A);

	mp_stop(&curmovie);

	SCR1_X = SCR2_X = -4;
	showImage16(0,&VICTORY2_IMG,CENTER,CENTER);
	while(!((j=JOYPAD)&J_A));
	while((j=JOYPAD)&J_A);
	SCR1_X = SCR2_X = 0;
}

void game()
{
	u8 i,j,n;
	u16 t,s;

	lives = 3;
	score = 0;
	while (lives && lvl<NB_MAPS)
	{
		playNextTune();
		doPresent();
		loadGfx();
		boss = ((lvl+1)%3)==0;
		initMap();
		loadPals();
		initChar();
		door = 1;
		mn = 0;
		ds = 0;
		us = 0;
		ms = 0;
		paused = 0;
		t = decors[lvl].nbt+PERSO_TILES+enmy_img[lvl].nbt;
		while(chars[0].alive && door && (boss || mn<4))
		{
			updateBoard();
			moveChar();
			moveEnemy();
			mngBombs();
			Sleep(1);
			showBoard();
			setMainSprite(0,0);
			n = 12;
			if (!boss)
			{
				for (i=0;i<lives;i++)
					SetSprite(n++,t+11,i*10,1,15,TOP_PRIO);
				if (mn<3 || ds<4 || ms<30)
				{
					SetSprite(n++,t+mn,126,0,15,TOP_PRIO);
					SetSprite(n++,t+10,134,0,15,TOP_PRIO);
					SetSprite(n++,t+ds,142,0,15,TOP_PRIO);
					SetSprite(n++,t+us,150,0,15,TOP_PRIO);
				}
				s = score;
				for (j=0;j<4;j++)
				{
					SetSprite(n++,t+(s%10),150-j*8,143,15,TOP_PRIO);
					s/=10;
				}
			}
			j = n;
			for (i=1;i<MAX_ENTITIES;i++)
			{
				if (chars[i].alive)
				{
					if (boss)
					{
						setMainSprite(n,i);
						n+=12;
					}
					else
						n+=setSprite(n,i);
				}
			}
			if (n==j && curmap[7][7]!=31) // Open exit door
			{
				curmap[7][7] = 31;
				curmod[7][7] = 1;
			}
			clearSprites(n);

			if ((j=JOYPAD)&J_OPTION && (lvl<data[22]))
			{
				while((j=JOYPAD)&J_OPTION);
				score = 0;
				lives = 3;
				door = 0;
				break;
			}
			else if (j&J_B)
			{
				while((j=JOYPAD)&J_B);
				paused = 1;
				while(1)
				{
					j = JOYPAD;
					if (j&J_B)
						break;
					if (j&J_OPTION)
					{
						clearSprites(0);
						return;
					}
				}
				while((j=JOYPAD)&J_B);
				paused = 0;
			}
		}
		// Wait 1s max
		VBCounter = 0;
		while (VBCounter<50 && !((i=JOYPAD)&J_A));
		while((i=JOYPAD)&J_A);
		// next level or restart
		if (door || (boss==0 && mn==4))
			lives-=1;
		else
		{
			if (score) // Not OPTION button
				score += (3-mn)*30+(6-ds)*5; // Time bonus
			lvl += 1;
			if (boss && lives<4 && score)
				lives += 1;
		}
	}
	SCR1_X = SCR1_Y = SCR2_X = SCR2_Y = 0;
	clearSprites(0);
	paused = 1;
	if (lives==0)
		youLose();
	else
		youWin();
	i = (lvl>data[22] || score>((u16*)data)[10]);
	if (score>((u16*)data)[10])
		newHighScore();
	if (lvl>data[22])
		data[22] = lvl<NB_MAPS ? lvl : lvl-1;
	if (i)
		flash(data);
}

void __interrupt myVBL()
{
	WATCHDOG = WATCHDOG_CLEAR;
	if (USR_SHUTDOWN) { SysShutdown(); while (1); }
	VBCounter++;
	moviecnt++;

	if (hc_emu())
	{
		hc_showEmu(curimg);
		mp_play_emu(&curmovie);
	}
	else
	{
		hc_showHW(curimg);
		mp_play_hw(&curmovie);
	}

	if (paused)
		return;
	if (++ms==50)
	{
		ms = 0;
		if (++us==10)
		{
			us = 0;
			if (++ds==6)
			{
				ds = 0;
				mn++;
			}
		}
	}
}

u8 menu()
{
	u16 i = 0,j,k,l;
	s16 dl = 2;
	u8 x,y;
	loadMenuGfx();

	print("CONTINUE",3,6);
	print("NEW GAME",3,9);
	print("HISCORES",3,12);
	i = data[22]>0?0:1;
	x = 4;
	l = 0;
	do
	{
		y = 46+i*24;
		k = FONT0_TILES+BKGD_TILES+1;
		l += dl;
		if (l==4 || l==0)
			dl = -dl;
		SetSprite(0,l+k					,x  ,y  ,CURSOR_PALIDX1[l],TOP_PRIO);
		SetSprite(1,l+k+1				,x+8,y  ,CURSOR_PALIDX1[l+1],TOP_PRIO);
		SetSprite(2,l+k+CURSOR_WIDTH	,x  ,y+8,CURSOR_PALIDX1[l+CURSOR_WIDTH],TOP_PRIO);
		SetSprite(3,l+k+CURSOR_WIDTH+1	,x+8,y+8,CURSOR_PALIDX1[l+CURSOR_WIDTH+1],TOP_PRIO);
		k+=(CURSOR_TILES>>1);
		SetSprite(4,l+k					,x  ,y  ,CURSOR_NPALS1+CURSOR_PALIDX2[l],TOP_PRIO);
		SetSprite(5,l+k+1				,x+8,y  ,CURSOR_NPALS1+CURSOR_PALIDX2[l+1],TOP_PRIO);
		SetSprite(6,l+k+CURSOR_WIDTH	,x  ,y+8,CURSOR_NPALS1+CURSOR_PALIDX2[l+CURSOR_WIDTH],TOP_PRIO);
		SetSprite(7,l+k+CURSOR_WIDTH+1	,x+8,y+8,CURSOR_NPALS1+CURSOR_PALIDX2[l+CURSOR_WIDTH+1],TOP_PRIO);

		j = JOYPAD;

		if ((j&J_UP) && (i>0))
		{
			i-=1;
			SL_PlaySFX(17);
		}
		if ((j&J_DOWN) && (i<2))
		{
			i+=1;
			SL_PlaySFX(17);
		}

		Sleep(j?8:4);
	}
	while(!(j&J_A));
	SL_PlaySFX(16);
	Sleep(30);
	while((j=JOYPAD)&J_A);
	clearPals();
	clearSprites(0);
	return i;
}

void showIntro()
{
	u8 j;

	clearPals();

	SL_LoadGroup(cotton0,sizeof(cotton0));
	SL_PlayTune(0);

	moviecnt = 0;
	curmovie.data = (u16*)BMAN_DATA;
	curmovie.count = BMAN_COUNT;
	curmovie.delay = BMAN_DELAY;
	mp_load(&curmovie);

	while((moviecnt<BMAN_COUNT*BMAN_DELAY) && (!((j=JOYPAD)&J_A)));
	while((j=JOYPAD)&J_A);

	mp_stop(&curmovie);
}

void main()
{
	InitNGPC();

	ClearScreen(SCR_1_PLANE);
	ClearScreen(SCR_2_PLANE);
	SetBackgroundColour(RGB(0, 0, 0));

	hc_detect();

	getData();
	SL_SoundInit();

	curimg = 0;
	curtune = 0;
	paused = 1;

	__asm(" di");
	VBL_INT = myVBL;
	__asm(" ei");

	showIntro();

	while(1)
	{
		showTitle();
		switch(menu())
		{
			case 0: // Continue
				lvl = data[22];
				break;
			case 1: // New game
				lvl = 0;
				break;
			case 2:
				highscores();
				continue;
		}
		SeedRandom();
		game();
	}
}
