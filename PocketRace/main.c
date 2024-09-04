#include "ngpc.h"
#include "carthdr.h"
#include "library.h"
#include "levels.h"
#include "speed.h"
#include "sprites.h"
#include "colision.h"
#include "sintab.h"

#include "PIC.h"

#define MIN(a,b) ((a)<(b)?(a):(b))
#define MAX(a,b) ((a)>(b)?(a):(b))

#define WALL   0
#define GROUND 1
#define GRASS  2
#define EMPTY  3

#define PLAYERS 4
#define RPLAYERS 2

#define LEVEL_IDX	0
#define SPRITES_IDX	128

#define DIR_COUNT 4
#define SPEED_COUNT 3

#define OK 0xff

#define ADD(a,b,c) ((((u16)a)+b)%c)
#define ADD64(a,b) ((a+b) & 0x3f)
#define ADD32(a,b) ((((u16)a)+b) & 0x1f)
#define ADD1024(a,b) ((((u16)a)+b) & 0x03ff)

#define MAP_1(x,y) lvl_map_1[(x)*lvl_h+y]
#define MAP_2(x,y) lvl_map_2[(x)*lvl_h+y]
#define TYPE(l,x,y) lvl_type[l][(x)*lvl_h+y]

#define TEST_EMPTY_1(t,x,y) { t = TYPE(0,x,y); if (t==EMPTY) { t = TYPE(1,x,y); nb++; }}
#define TEST_EMPTY_2(t,x,y) { t = TYPE(1,x,y); if (t==EMPTY) t = TYPE(0,x,y); else nb++; }

const s16 start_pos[PLAYERS][2] = {{0,0},{9,-2},{6,0},{3,-2}};

const s8 check_pos[PLAYERS][2] = {{0,0},{-1,-1},{1,1},{0,0}};

u16 lvl_w,lvl_h,*lvl_map_1,*lvl_map_2;
u8 *lvl_type[2],*lvl_mark,lvl_nbm;

u8 cptJoy;
s8 dx,dy;
u16 x[PLAYERS],y[PLAYERS],xx[PLAYERS],yy[PLAYERS];
u16 scrx[PLAYERS],scry[PLAYERS],_scrx[PLAYERS],_scry[PLAYERS];
u8 speed[PLAYERS],dir[PLAYERS];
u8 cpt_speed[PLAYERS];
u8 cpt_dir[PLAYERS],dir_mv[PLAYERS];
u8 reset,spr_prio[PLAYERS],spr_lvl[PLAYERS];
u8 max_speed;
u8 spr_cnt;
u8 spr_col[64];
u32 spr_buf[64];
u8 lap[PLAYERS];
u16 old_mx1[PLAYERS];
u8 checkPointNo[PLAYERS];
u16 mx1[PLAYERS],mx2[PLAYERS],my1[PLAYERS],my2[PLAYERS];
u8 print_buf[16];

void _SetSprite(u8 SpriteNo,u16 TileNo,u8 XPos,u8 YPos,u8 PaletteNo,u8 Prio)
{
	u8* theSprite = (u8*)&spr_buf[spr_cnt];
	*(theSprite++)   = TileNo&0xff;
	*(theSprite++) = Prio|(TileNo>>8);
	*(theSprite++) = XPos;
	*theSprite = YPos;
	spr_col[spr_cnt++] = PaletteNo;
}

void SprPrint(u8 *buf,u8 x,u8 y)
{
	u8 i;
	for (i=0;buf[i];i++)
	{
		u16 a = buf[i];
		a = SPRITES_LINE*2*4+((a>='0'&&a<='9')?a-'0':((a>='A'&&a<='Z')?a-'A'+10:(a==':'?36:(a=='@'?37:(a=='!'?38:(a=='?'?39:40))))));
		_SetSprite(spr_cnt++,a+SPRITES_IDX,x,y,sprites_palidx[a],TOP_PRIO);
		x+=8;
	}
}

#define SprPrintS(s,x,y) SprPrint((u8*)s,x,y)

void SprPrintN(u16 n,u8 x,u8 y)
{
	u8 i=0,j=0;
	u8 buf[16];

	do
	{
		buf[i++] = '0'+(n%10);
		n=n/10;
	}
	while (n>0);

	while (i>0)
		print_buf[j++] = buf[--i];
	print_buf[j]=0;
	SprPrint(print_buf,x,y);
}

void userAction()
{
	u8 a = JOYPAD;

	if (a&J_OPTION)
	{
		while ((a=JOYPAD)&J_OPTION);
		reset = 1;
	}

	if (cpt_dir[0]==0)
	{
		if (a&J_RIGHT)
		{
			dir_mv[0] = 63;
			cpt_dir[0] = DIR_COUNT;
		}
		else if (a&J_LEFT)
		{
			dir_mv[0] = 1;
			cpt_dir[0] = DIR_COUNT;
		}
	}
	else
	{
		--cpt_dir[0];
		dir[0] = ADD64(dir[0],dir_mv[0]);
	}

	if (a&J_A)
	{
		if (speed[0]<max_speed-3)
			speed[0]+=3;
	}
	else if (a&J_B)
	{
		if (speed[0]>0)
			--speed[0];
	}

}

void computerAction()
{
	u8 i,di,mv,j,k;
	s8 cx,cy,xi,yi;
	s16 sqr,dx,dy;
	u32 calc;
	for (i=1;i<RPLAYERS;i++)
	{
		if (speed[i]<max_speed-i)
			speed[i]+=2;

		do
		{
                	cx = lvl_mark[checkPointNo[i]]+check_pos[i][0];
                	cy = lvl_mark[checkPointNo[i]+1];check_pos[i][1];
                        if (cx+1<mx1[i] || cx>mx2[i]+1 || cy+1<my1[i] || cy>my2[i]+1)
				break;
			checkPointNo[i]=ADD(checkPointNo[i],2,lvl_nbm);
		}
		while(1);

		if (cpt_dir[i]>0)
		{
			--cpt_dir[i];
			dir[i] = ADD64(dir[i],dir_mv[i]);
			continue;
		}

		xi = ((cx<8)&&(mx1[i]>lvl_w-8)) ? mx1[i]-lvl_w : ((cx>lvl_w-8)&&(mx1[i]<8)) ? mx1[i]+lvl_w: mx1[i];
		yi = ((cy<8)&&(my1[i]>lvl_h-8)) ? my1[i]-lvl_h : ((cy>lvl_h-8)&&(my1[i]<8)) ? my1[i]+lvl_h: my1[i];
		di = dir[i];
		mv = dir_mv[i] = 0;
		// Compute angle
		dx = cx-xi;
		dy = cy-yi;
		sqr = (dx*dx)+(dy*dy);
		calc = (((u32)(dy*dy))*255)/sqr;
		if (dy>=0)
		{
			if (dx>=0)
				j=48;
			else
				j=32;
		}
		else
		{
			if (dx>=0)
				j=0;
			else
				j=16;
		}
		k = j+1;
		while (1)
		{
			if ((calc>=sintab[j]&&calc<sintab[k])||(calc<=sintab[j]&&calc>sintab[k]))
				break;
			j = ADD64(j,1);
			k = ADD64(k,1);
		}
		// Test if check point missed
/*
		k = MAX(di,j)-MIN(di,j);
		if (k>24 && k<40)
		{
			checkPointNo[i]=ADD(checkPointNo[i],2,lvl_nbm); // Try next one
			i-=1;
			continue;
		}
*/
		// Modify direction to reach next check point
		if (di>j)
		{
			if (di-j<64-di+j)
				mv = 63;
			else
				mv = 1;
		}
		else if (di<j)
		{
			if (j-di<64-j+di)
				mv = 1;
			else
				mv = 63;
		}
		if (mv)
		{
			if (sqr<400)
				speed[i]=speed[i]>3?speed[i]-4:0;
			cpt_dir[i]=DIR_COUNT;
			dir_mv[i] = mv;
		}
	}
}

void initLevel(int lvl)
{
	u16 i,j;

	for (i=0;i<levels[lvl].nbt*8;i++)
		(TILE_RAM)[LEVEL_IDX+i] = levels[lvl].tiles[i];

	for (i=0;i<16;i++)
	{
		(SCROLL_1_PALETTE)[i] = 0;
		(SCROLL_2_PALETTE)[i] = 0;
	}

	lvl_w = levels[lvl].w;
	lvl_h = levels[lvl].h;
	lvl_map_1 = levels[lvl].map_1;
	lvl_map_2 = levels[lvl].map_2;
	lvl_type[0] = levels[lvl].type_1;
	lvl_type[1] = levels[lvl].type_2;
	lvl_mark = levels[lvl].mark;
	lvl_nbm = levels[lvl].nbm;

	for (i=0;i<24;i++)
	{
		u16 sx = ADD(i,lvl_w-2,lvl_w);
		u16 syy = (30<<5)+ADD32(i,30);
		u16 sy = ADD(0,lvl_h-2,lvl_h);
		for (j=0;j<23;j++)
		{
			(SCROLL_PLANE_1)[syy] = MAP_1(sx,sy);
			(SCROLL_PLANE_2)[syy] = MAP_2(sx,sy);
			sy = ADD(sy,1,lvl_h);
			syy = ADD1024(syy,32);
		}
	}

	for (i=0;i<levels[lvl].nbp*4;i++)
	{
		(SCROLL_1_PALETTE)[i] = levels[lvl].pals[i];
		(SCROLL_2_PALETTE)[i] = levels[lvl].pals[i];
	}
}

void initSprites()
{
	u16 i;
	for (i=0;i<SPRITES_TILES*8;i++)
		(TILE_RAM)[i+SPRITES_IDX*8] = sprites_tiles[i];

	for (i=0;i<SPRITES_PALS*4;i++)
		(SPRITE_PALETTE)[i] = sprites_pals[i];
}

void screenUpdate()
{
	u16 i,j,k;
	u8 b = 0,n;
	u8 x0l,x0r,y0u,y0d;

	for (n = 0; n < RPLAYERS; n++)
	{
		if ((speed[n]>0)&&(cpt_speed[n]==0))
			--speed[n];
		if (cpt_speed[n]>0)
			--cpt_speed[n];
		else	
			cpt_speed[n] = SPEED_COUNT;

		dx = speed_x[speed[n]][dir[n]];
		dy = speed_y[speed[n]][dir[n]];

		i = (scrx[n]+dx)>>6;
		j = scrx[n]>>6;
		k = dir[n]>>2;
		if (dx && i!=j)
		{
			u8 ix;
			for (i=0;i<3 && (ix = COLISION_X[k][i][0]);i++)
			{
				u16 mx = ADD(x[n]+2,ix,lvl_w);
				u16 my = ADD(y[n]+2,COLISION_X[k][i][1],lvl_h);
				if (TYPE(spr_lvl[n],mx,my)==WALL)
				{
					dx = 0;
					break;
				}
			}
			if (dx)
			{
				u16 sx, sxx, syy = ADD32(yy[n],30)<<5;
				if (dx>0) // RIGHT
				{
					x[n] = ADD(x[n],1,lvl_w); // x++
					xx[n] = ADD32(xx[n],1); // xx++
					sx = ADD(x[n],21,lvl_w);
					sxx = ADD32(xx[n],21);
				}
				else// LEFT
				{
					x[n] = ADD(x[n],lvl_w-1,lvl_w); // x--
					sx = ADD(x[n],lvl_w-2,lvl_w); // x - 2
					xx[n] = ADD32(xx[n],31); // xx--
					sxx = ADD32(xx[n],30); // xx - 2
				}
				syy += sxx;
				for (i=0;n==0 && i<23;i++)
				{
					(SCROLL_PLANE_1)[syy] = MAP_1(sx,ADD(i+y[n],lvl_h-2,lvl_h));
					(SCROLL_PLANE_2)[syy] = MAP_2(sx,ADD(i+y[n],lvl_h-2,lvl_h));
					syy=ADD1024(syy,32);
				}
				b = 1;
			}
		}
		i = (scry[n]+dy)>>6;
		j = scry[n]>>6;
		if (dy && i!=j)
		{
			u8 ix;
			for (i=0;i<3 && (ix = COLISION_Y[k][i][0]);i++)
			{
				u16 mx = ADD(x[n]+2,ix,lvl_w);
				u16 my = ADD(y[n]+2,COLISION_Y[k][i][1],lvl_h);
				if (TYPE(spr_lvl[n],mx,my)==WALL)
				{
					dy = 0;
					break;
				}
			}
			if (dy)
			{
				u16 sy,syy;
				if (dy>0) // DOWN
				{
					y[n] = ADD(y[n],1,lvl_h); // y++
					sy = ADD(y[n],20,lvl_h);
					yy[n] = ADD32(yy[n],1); // yy++
					syy = ADD32(yy[n],20);
				}
				else // UP
				{
					y[n] = ADD(y[n],lvl_h-1,lvl_h); // y--
					sy = ADD(y[n],lvl_h-2,lvl_h); // y - 2
					yy[n] = ADD32(yy[n],31); // yy--
					syy = ADD32(yy[n],30); // yy-2
				}
				syy = (syy<<5);
				for (i=0;n==0 && i<24;i++)
				{
					(SCROLL_PLANE_1)[syy+ADD32(xx[n]+i,30)] = MAP_1(ADD(x[n]+i,lvl_w-2,lvl_w),sy);
					(SCROLL_PLANE_2)[syy+ADD32(xx[n]+i,30)] = MAP_2(ADD(x[n]+i,lvl_w-2,lvl_w),sy);
				}
				b = 1;
			}
		}
		scrx[n]+=dx;
		scry[n]+=dy;
		if (!n)
		{
			SCR1_X = scrx[n]>>3;
			SCR2_X = scrx[n]>>3;
			SCR1_Y = scry[n]>>3;
			SCR2_Y = scry[n]>>3;
		}
		if (b)
		{
			_scry[n] = scry[n];
			_scrx[n] = scrx[n];
		}
		if (!dx && !dy)
			speed[n] = 0;

		if (speed[n]>0)
		{
			u8 t11,t12,t21,t22,nb=0;
			mx1[n] = ADD(x[n],(scrx[n]<_scrx[n]?8:9),lvl_w);
			mx2[n] = ADD(x[n],(scrx[n]<=_scrx[n]?11:12),lvl_w);
			my1[n] = ADD(y[n],(scry[n]<_scry[n]?7:8),lvl_h);
			my2[n] = ADD(y[n],(scry[n]<=_scry[n]?10:11),lvl_h);

			if (old_mx1[n]==20 && my1[n]<13)
			{
				if (mx1[n]==21)
					lap[n]++;
				else if (mx1[n]==19 && lap>0)
					lap[n]--;
			}
			old_mx1[n]=mx1[n];
/**/
if (n==1)
{
SprPrintN(mx1[0],0,32);
SprPrintN(my1[0],32,32);
SprPrintN(mx1[n],0,40);
SprPrintN(my1[n],32,40);
}
/**/
			if (spr_lvl[n]==0)
			{
				TEST_EMPTY_1(t11,mx1[n],my1[n])
				TEST_EMPTY_1(t12,mx1[n],my2[n])
				TEST_EMPTY_1(t21,mx2[n],my1[n])
				TEST_EMPTY_1(t22,mx2[n],my2[n])
			}
			else
			{
				TEST_EMPTY_2(t11,mx1[n],my1[n])
				TEST_EMPTY_2(t12,mx1[n],my2[n])
				TEST_EMPTY_2(t21,mx2[n],my1[n])
				TEST_EMPTY_2(t22,mx2[n],my2[n])
			}
			if (t11==GRASS || t12==GRASS || t21==GRASS || t22==GRASS)
				--speed[n];
			if (spr_lvl[n]==0)
			{
				if (nb>0)
				{
					spr_prio[n] = TOP_PRIO;
					spr_lvl[n] = 1;
				}
			}
			else if (nb==0)
			{
				spr_lvl[n] = 0;
				spr_prio[n] = MIDDLE_PRIO;
			}
		}
		j = (dir[n]>>2)*2+(SPRITES_LINE*2*n);
		i = j+SPRITES_IDX;
		if (n)
		{
			if ( ((x0l<x0r && x[n]>x0l && x[n]<x0r)||(x0l>x0r && (x[n]>x0l || x[n]<x0r)))
                           &&((y0u<y0d && y[n]>y0u && y[n]<y0d)||(y0u>y0d && (y[n]>y0u || y[n]<y0d))))
			{
				u8 xs = 72+(u8)((scrx[n]-scrx[0])>>3);
				u8 ys = 64+(u8)((scry[n]-scry[0])>>3);
				_SetSprite(spr_cnt++,i,xs,ys,sprites_palidx[j],spr_prio[n]);
				_SetSprite(spr_cnt++,i+1,xs+8,ys,sprites_palidx[j+1],spr_prio[n]);
				_SetSprite(spr_cnt++,i+SPRITES_LINE,xs,ys+8,sprites_palidx[j+SPRITES_LINE],spr_prio[n]);
				_SetSprite(spr_cnt++,i+SPRITES_LINE+1,xs+8,ys+8,sprites_palidx[j+SPRITES_LINE+1],spr_prio[n]);
			}
		}
		else
		{
			x0l = ADD(x[0],lvl_w-11,lvl_w);
			x0r = ADD(x0l,22,lvl_w);
			y0u = ADD(y[0],lvl_h-10,lvl_h);
			y0d = ADD(y0u,20,lvl_h);
			_SetSprite(spr_cnt++,i,72,64,sprites_palidx[j],spr_prio[0]);
			_SetSprite(spr_cnt++,i+1,80,64,sprites_palidx[j+1],spr_prio[0]);
			_SetSprite(spr_cnt++,i+SPRITES_LINE,72,72,sprites_palidx[j+SPRITES_LINE],spr_prio[0]);
			_SetSprite(spr_cnt++,i+SPRITES_LINE+1,80,72,sprites_palidx[j+SPRITES_LINE+1],spr_prio[0]);
		}
	SprPrintN(lap[n],32,n*8);
	}
	SprPrint((u8*)"LAP:",0,0);
}

void showSprites()
{
	u32 *spr_ram = (u32*)(SPRITE_RAM);
	u8 *spr_cram = SPRITE_COLOUR;
	u8 i;
	for (i=0;i<spr_cnt;i++)
	{
		spr_ram[i] = spr_buf[i];
		spr_cram[i] = spr_col[i];
	}
	while (i<64)
	{
		spr_ram[i] = 0;
		spr_cram[i++] = 0;
	}
	spr_cnt=0;
}

const u16 starter[5][4] = {
{43,43,43,44},
{41,43,43,44},
{41,41,43,44},
{41,41,41,44},
{41,41,41,42}
};

void main()
{
	u8 i,j,l;
	s16 k;
	InitNGPC();

	ClearScreen(SCR_1_PLANE);
	ClearScreen(SCR_2_PLANE);
	SetBackgroundColour(RGB(0, 0, 0));

	showImage(0,(SOD_IMG*)&PIC_ID,CENTER,CENTER);
        while(((i = JOYPAD)&J_A) == 0);

	SCRL_PRIO|=0x80;

	while (1)
	{
		initLevel(0);
		initSprites();

		SCR1_X = SCR2_X = 0;
		SCR1_Y = SCR2_Y = 0;

		for (i=0;i<RPLAYERS;i++)
		{
			x[i] = ADD(lvl_w,start_pos[i][0],lvl_w);
			xx[i] = x[i]&0x1f;
			y[i] = ADD(lvl_h,start_pos[i][1],lvl_h);
			yy[i] = y[i]&0x1f;
			scrx[i] = _scrx[i] = x[i]<<6;
			scry[i] = _scry[i] = y[i]<<6;
			cpt_speed[i] = 0;
			speed[i] = 0;
			cpt_dir[i] = DIR_COUNT;
			dir[i] = 0;
			spr_prio[i] = MIDDLE_PRIO;
			spr_lvl[i] = 0;
			lap[i] = 0;
			old_mx1[i] = 0;
			dir_mv[i] = 0;
			checkPointNo[i] = 0;
		}
		max_speed = NORM_SPEED;
		reset = 0;

		showSprites();
		k = SPRITES_LINE*2*4;
		for (i=0;i<5;i++)
		{
			for (j=0;j<4;j++)
				_SetSprite(spr_cnt++,SPRITES_IDX+k+starter[i][j],60+j*9,16,sprites_palidx[k+starter[i][j]],TOP_PRIO);
			screenUpdate();
			showSprites();
			if (i<4)
				Sleep(60); // 1 s ?
		}

		l = 0;
		while(!reset)
		{
			Sleep(1); // Synchro VBL
			if (l<60) // starter
			{
				for (j=0;j<4;j++)
					_SetSprite(spr_cnt++,SPRITES_IDX+k+starter[4][j],60+j*9,16,sprites_palidx[k+starter[4][j]],TOP_PRIO);
				l++;
			}
			showSprites();
			screenUpdate();
			computerAction();
			userAction();
		}
	}
}
