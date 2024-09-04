#include "ngpc.h"
#include "carthdr.h"
#include "library.h"
#include "hicolor.h"
#include "mplayer.h"
#include "flash.h"
#include "music.h"
#include "img.h"

#include "font.h"
#include "front0.h"
#include "ship0.h"
#include "common.h"
#include "back0.h"
#include "enemy0.h"
#include "enemy1.h"
#include "enemy2.h"
#include "expl0.h"
#include "boss0.h"
#include "ready.h"
#include "bossi.h"
#include "fight.h"

#include "metalslug0.mh"

#include "backgrounds.h"

#include "movies/tunnel.h"
#include "movies/fire_b.h"
#include "movies/fire_c.h"
#include "movies/explosion.h"

void *curimg;
extern volatile u8 VBCounter;
volatile u32 moviecnt = 0;
MOVIE curmovie;

const SOD_IMG eImg[3] = {ENEMY0_ID,ENEMY1_ID,ENEMY2_ID};

u8 curEnmy;
u16 t_enmy;
u16 t_expl;
u16 t_scr1,t_scr2;
u16 t_font;
u8 p_expl,p_font,p_enmy;
u8 scr1_x;
u16 score;
u8 curpres = 0;

#define LVL_SIZE 512
extern const u16 lvl0_0[LVL_SIZE][32];
extern const u16 lvl0_1[LVL_SIZE][32];

typedef struct
{
	u16 len;
	u8	coords[2048];
} CURVE;

#define MAX_CURV 10
const CURVE curves[MAX_CURV] =
{
#include "curvb.h"
,
#include "curv0.h"
,
#include "curv1.h"
,
#include "curv2.h"
,
#include "curv3.h"
,
#include "curv4.h"
,
#include "curv5.h"
,
#include "curv6.h"
,
#include "curv7.h"
,
#include "curv8.h"
};
u8 cur_curv;

typedef struct
{
	u8 x,y,life;
	s8 dx,dy;
	u8 power;
} SHOOT;

typedef struct
{
	u8 x,y,life;
	u8 sy;
} EXPL;

typedef struct
{
	u8 x,y;
	u8 tempo;
	u8 power;
	u8 speed;
	u8 life;
} ENTITY;

typedef struct
{
	u8 x,y;
	u8 type;
} BONUS;

typedef struct
{
	ENTITY	ent;
	u16		idx;
	u8		type;
	u8		wait;
} ENEMY;

u8		goback;
ENTITY	player;
u8		nbs[2];
SHOOT	sh[2][16];
u8		nbx[3];
EXPL	expl[3][16];
u8		nbe = 0;
ENEMY	enmy[16];
u8		nbb = 0;
BONUS	bonus[4];
u8		lastX,lastY,lastN;
u8		boss;

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
			n = c - '0';
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
			(SCROLL_PLANE_2)[t++] = (((u16)p_font+COMMON_PALIDX1[n])<<9)+n+t_font;
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
	u16 i;
	for (i=0;i<64;i++)
		p1[i] = p2[i] = 0;
}

void showHC(void *img)
{
	(SCR1_X) = (SCR1_Y) = (SCR2_X) = (SCR2_Y) = 0;
	curimg = 0;
	hc_load(img);
	curimg = img;
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

void clearSprites(u8 start)
{
	u16 *theSprite = (u16*)SPRITE_RAM;
	u16 i;

	for (i=(start<<1);i<128;i+=2)
		*(theSprite+i) = 0;
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
}

void loadCommon()
{
	u16 i,n = t_font<<3;
	for (i=0;i<(COMMON_TILES<<3);i++)
		(TILE_RAM)[n++] = COMMON_TILES1[i];
	n = (p_font<<2);
	for (i=0;i<(COMMON_NPALS1<<2);i++)
		(SPRITE_PALETTE)[n++] = COMMON_PALS1[i];
}

void loadGfx(u8 b)
{
	u16 i,n=0;

	// empty tile at 0
	for (i=0;i<8;i++)
		(TILE_RAM)[n++] = 0;

	// ship & shoot
	for (i=0;i<(SHIP0_TILES<<2);i++)
		(TILE_RAM)[n++] = SHIP0_TILES1[i];
	for (i=0;i<(SHIP0_TILES<<2);i++)
		(TILE_RAM)[n++] = SHIP0_TILES2[i];
	t_expl = n>>3;
	for (i=0;i<(EXPL0_TILES<<3);i++)
		(TILE_RAM)[n++] = EXPL0_TILES1[i];

	// background
	t_scr2 = (n-1)>>3;
	for (i=0;i<(EARTH0_TILES<<3);i++)
		(TILE_RAM)[n++] = EARTH0_TILES1[i];
	// foreground
	t_scr1 = (n-1)>>3;
	if (b)
	{
		n+=(BOSS0_TILES<<3);
	}
	else
	{
		for (i=0;i<(PIPES_TILES<<3);i++)
			(TILE_RAM)[n++] = PIPES_TILES1[i];
	}
	t_enmy = (n>>3);
	t_font = t_enmy+6;

	// palettes
	n = 0;
	for (i=0;i<(SHIP0_NPALS1<<2);i++)
		(SPRITE_PALETTE)[n++] = SHIP0_PALS1[i];
	for (i=0;i<(SHIP0_NPALS2<<2);i++)
		(SPRITE_PALETTE)[n++] = SHIP0_PALS2[i];
	p_expl = n>>2;
	for (i=0;i<(EXPL0_NPALS1<<2);i++)
		(SPRITE_PALETTE)[n++] = EXPL0_PALS1[i];
	p_font = n>>2;
	p_enmy = p_font+COMMON_NPALS1;

	if (!b)
	{
		for (i=0;i<(PIPES_NPALS1<<2);i++)
			(SCROLL_1_PALETTE)[i] = PIPES_PALS1[i];
	}
	for (i=0;i<(EARTH0_NPALS1<<2);i++)
		(SCROLL_2_PALETTE)[i] = EARTH0_PALS1[i];
}

void loadHCGfx()
{
	u16 i,n=448*8;

	// font
	for (i=0;i<(FONT_TILES<<3);i++)
		(TILE_RAM)[n++] = FONT_TILES1[i];
	for (i=0;i<(FONT_NPALS1<<2);i++)
		(SPRITE_PALETTE)[i] = FONT_PALS1[i];
}

u8 printHC(const char *buf,u8 x,u8 y,u8 n)
{
	u16 i,j;
	for (i=0;buf[i];i++,x+=8)
	{
		if (buf[i]!=' ')
		{
			j = buf[i]!='.'? buf[i]-'A' : 26;
			SetSprite(n++,j+448,x,y,FONT_PALIDX1[j],TOP_PRIO);
		}
	}
	return n;
}

void loadHCGfx2(u16* tiles,u16 nt,u16* pal,u16 np,u16 st,u8 sp,u8* pidx)
{
	u16 i,n=st*8;
	u8 j;
	for (i=0;i<(nt<<3);i++)
		(TILE_RAM)[n++] = tiles[i];
	for (i=0;i<(np<<2);i++)
		(SPRITE_PALETTE)[(sp<<2)+i] = pal[i];
	clearSprites(0);
	n = st;
	for (j=0;j<10;j++,n++)
	{
		SetSprite(j,n,40+(j<<3),64,sp+pidx[j],TOP_PRIO);
		SetSprite(j+10,n+10,40+(j<<3),72,sp+pidx[j+10],TOP_PRIO);
	}
}

u16 currow;

void fillLine(u16 n,u16 r)
{
	u16 *sp1 = SCROLL_PLANE_1;
	u16 *sp2 = SCROLL_PLANE_2;
	u16 j,f,t;
	for (j=0;j<32;j++)
	{
		f = lvl0_1[r][j]&0xf000;
		t = lvl0_1[r][j]&0x0fff;
		sp1[n]   = f|(t==0?0:(t+t_scr1)|(((u16)PIPES_PALIDX1[t-1])<<9));
		f = lvl0_0[r][j]&0xf000;
		t = lvl0_0[r][j]&0x0fff;
		sp2[n++] = f|(t==0?0:(t+t_scr2)|(((u16)EARTH0_PALIDX1[t-1])<<9));
	}
}

void initLevel(u8 b)
{
	u16 i,n = 19*32;
	u16 d = b?LVL_SIZE-20:0;
	u16 *sp1 = SCROLL_PLANE_1;

	for (i=0;i<20;i++)
	{
		fillLine(n,d+i);
		n-=32;
	}
	for (i=0;b && i<1024;i++)
		sp1[i] = 0;

	currow = b?LVL_SIZE:20;
	(SCR1_X) = (SCR2_X) = 48;
	(SCR1_Y) = (SCR2_Y) = b?0:8;
	cur_curv = 1;
	nbs[0] = nbs[1] = 0;
	nbx[0] = nbx[1] = nbx[2] = 0;
	nbb = 0;
	player.x = 128;
	player.y = 128;
}

u8 isEmpty(u16 x,u16 y,u16 sy,u8 b)
{
	y+=sy;
	if (b || ((y&0x7)==0))
	{
		x = (x>>3);
		y = currow-(y>>3)-1;
		return lvl0_1[y][x]==0;
	}
	return 1;
}

void scrollLevel()
{
	u8 y = (SCR2_Y);
	u8 xx,yy;

	if (player.x<80)
		xx = 0;
	else if (player.x>175)
		xx = 96;
	else
		xx = player.x-80;
	scr1_x = xx;
	(SCR2_X) = xx<48?(48-((48-xx)>>1)):48+((xx-48)>>1);
	if (boss)
		return;
	else
		(SCR1_X) = scr1_x;

	yy = (y&0x7);
	if (goback && isEmpty(player.x,player.y+12,yy,0))
	{
		if (currow<20)
			return;
		y+=1;
		(SCR1_Y) = y;
		(SCR2_Y) = y;
		if (yy==0)
		{
			y+=159;
			fillLine((((u16)y)<<2),currow-21);
			currow-=1;
		}
		return;
	}
	if (goback==0 && isEmpty(player.x,player.y-20,yy,0))
	{
		if (currow==LVL_SIZE)
			return;
		y-=1;
		(SCR1_Y) = y;
		(SCR2_Y) = y;
		if (yy==1)
		{
			y-=8;
			currow+=1;
			fillLine((((u16)y)<<2),currow);
		}
	}
}

void addShoot(u8 c,u8 x,u8 y,s8 dy,u8 power,u8 speed)
{
	const u8 pdx[4][4] = {{0,0,0,0},{-1,1,0,0},{-1,0,1,0},{-1,1,-1,1}};
	u8 i,n;
	for (i=0;i<=power;i++)
	{
 		n = nbs[c]++;
		sh[c][n].x		= x;
		sh[c][n].y		= y;
		sh[c][n].dx		= pdx[power][i]*speed;
		sh[c][n].dy		= (power < 3 ? dy : (i<2?-1:1))*speed;
		sh[c][n].life	= 64;
		sh[c][n].power	= boss?2:1;
	}
}

u8 touchEntity(u8 x,u8 y,u8 s,ENTITY *e,u8 se,s8 p)
{
	u8 ret = (x>(e->x-se-s) && x<(e->x+se+s) &&	y>(e->y-se-s) && y<(e->y+se+s));
	if (ret)
	{
		if (p>0)
			e->life = p>e->life ? 0 : e->life-p;
		else
		{
			if (p==0 && e->power<3) // power
				e->power+=1;
			else if (p==-1 && e->speed<4) // speed
				e->speed+=1;
			else if (p==-2 && e->life<32) // life
				e->life+=4;
		}
	}
	return ret;
}

void addBonus(u8 x,u8 y)
{
	bonus[nbb].x		= x;
	bonus[nbb].y		= y;
	bonus[nbb++].type	= GetRandom(2); // bomb later
	lastN = 1;
}

void bonusMove()
{
	u8 i,j=0;
	for (i=0;i<nbb;i++)
	{
		if (!goback)
			bonus[i].y+=1;
		if ((bonus[i].y>151) || touchEntity(bonus[i].x,bonus[i].y,4,&player,4,-bonus[i].type))
			continue;
		if (i>j)
			bonus[j] = bonus[i];
		j+=1;
	}
	nbb = j;
}

void playerMove()
{
	u8 j = JOYPAD;
	u8 sy = ((SCR2_Y)&0x7);
	goback = 0;
	if ((j&J_LEFT)	&&(player.x>12)
					&& isEmpty(player.x-10,player.y-12,sy,1)
			 		&& isEmpty(player.x-12,player.y-4,sy,1)
			 		&& isEmpty(player.x-10,player.y+4,sy,1))
		player.x-=2;
	else if ((j&J_RIGHT)&&(player.x<244)
			 			&& isEmpty(player.x+10,player.y-12,sy,1)
			 			&& isEmpty(player.x+12,player.y-4,sy,1)
			 			&& isEmpty(player.x+10,player.y+4,sy,1))
		player.x+=2;
	if ((j&J_UP) && (player.y>32) && isEmpty(player.x,player.y-20,sy,0))
		player.y-=2;
	else if (j&J_DOWN)
	{
		if ((player.y<132) && isEmpty(player.x,player.y+12,sy,0))
			player.y+=2;
		else
			goback = 1;
	}
	if (player.tempo==0)
	{
		if ((j&J_A)&&(nbs[0]+player.power<16))
		{
			addShoot(0,player.x,player.y-10,-2,player.power,1);
			player.tempo = 24+(player.power<<2)-(player.speed<<1);
		}
	}
	else
		player.tempo-=1;
}

const u8 nbshoots[16] = {0,0,0,0,0,0,1,1,1,1,1,2,2,2,3,3};

void addEnnemies()
{
	u16 i;
	u16 nt;
	u8 t = GetRandom(10)<7;
	u8 pwr = 0;
	nbe = GetRandom(3)+3;
	for (i=0;i<nbe;i++)
	{
		if (t) // curve
			enmy[i].ent.x = enmy[i].ent.y = 224;
		else // rush
		{
			enmy[i].ent.x = (((u16)(i+1))<<8)/(nbe+1);
			enmy[i].ent.y = 248;
		}
		enmy[i].ent.tempo = 0;
		enmy[i].ent.power = pwr>10 ? nbshoots[GetRandom(15)] : 1;
		pwr += enmy[i].ent.power;
		enmy[i].wait = i<<4;
		enmy[i].idx = 0;
		enmy[i].type = t;
		enmy[i].ent.life = 1;
	}
	lastN = nbe;
	// load tiles
	curEnmy = GetRandom(3);
	nt = t_enmy<<3;
	for (i=0;i<(eImg[curEnmy].nbt<<3);i++)
		(TILE_RAM)[nt++] = eImg[curEnmy].t1[i];
	nt = p_enmy<<2;
	for (i=0;i<(eImg[curEnmy].np1<<2);i++)
		(SPRITE_PALETTE)[nt++] = eImg[curEnmy].p1[i];
}

void addBoss()
{
	u16 i,j,n = (t_scr1<<3)+8;
	nbe = 1;
	enmy[0].ent.x = enmy[0].ent.y = 224;
	enmy[0].ent.tempo = 0;
	enmy[0].ent.power = 3;
	enmy[0].wait = 0;
	enmy[0].idx = 0;
	enmy[0].type = 1;
	enmy[0].ent.life = 50;
	for (i=0;i<(BOSS0_TILES<<3);i++)
		(TILE_RAM)[n++] = BOSS0_TILES1[i];
	for (i=0;i<(BOSS0_NPALS1<<2);i++)
		(SCROLL_1_PALETTE)[i] = BOSS0_PALS1[i];
	for (j=0;j<6;j++)
		for (i=0;i<7;i++)
			(SCROLL_PLANE_1)[i+(j<<5)] = (((u16)BOSS0_PALIDX1[(j*7)+i])<<9)+(j*7)+i+t_scr1+1;
	lastN = 1;
}

void addExpl(u8 j,u8 x,u8 y,u8 sy,u8 lf)
{
	u8 e = nbx[j];
	expl[j][e].life	= lf;
	expl[j][e].x	= x;
	expl[j][e].y	= y;
	expl[j][e].sy	= sy;
	nbx[j] += 1;
	if (j==1)
		SL_PlaySFX(2);
	if (j==2)
		SL_PlaySFX(4);
}

void enemiesMove()
{
	u8 i,j=0;
	u8 sy = (SCR2_Y);
	for (i=0;i<nbe;i++)
	{
		if (enmy[i].ent.life==0 || touchEntity(enmy[i].ent.x,enmy[i].ent.y,boss?24:6,&player,8,2))
		{
			if (boss==0)
			{
				addExpl(2,enmy[i].ent.x,enmy[i].ent.y,sy,15);
				score+=10;
				lastX = enmy[i].ent.x;
				lastY = enmy[i].ent.y;
			}
			if (boss==0 || enmy[i].ent.life==0)
			{
				lastN--;
				continue;
			}
		}
		if (enmy[i].type && enmy[i].idx==curves[cur_curv].len)
		{
			if (boss==0)
				continue;
			enmy[i].idx = 0;
		}
		if (enmy[i].type==0 && enmy[i].ent.y>156 &&  enmy[i].ent.y<248)
			continue;
		if (j<i)
			enmy[j] = enmy[i];
		if (enmy[j].wait)
		{
			enmy[j++].wait-=1;
			continue;
		}
		if (enmy[j].type)
		{
			enmy[j].ent.x = curves[cur_curv].coords[enmy[j].idx++];
			enmy[j].ent.y = curves[cur_curv].coords[enmy[j].idx++];
		}
		else
		{
			enmy[j].ent.y += 2;
		}
		// shoot
		if ((enmy[j].ent.tempo==0))
		{
			if (nbs[1]+enmy[j].ent.power<16)
			{
				addShoot(1,enmy[j].ent.x,enmy[j].ent.y+4,2,enmy[j].ent.power,enmy[j].type?1:2);
				enmy[j].ent.tempo = boss ? 12 : 16+(enmy[j].ent.power<<3);
			}
		}
		else if (enmy[j].ent.tempo)
			enmy[j].ent.tempo -= 1;
		j+=1;
	}
	nbe = j;
	if (nbe==0)
	{
		if (currow==LVL_SIZE || boss==1)
		{
			boss = 1;
			return;
		}
		if (nbs[1]==0 && nbx[1]==0 && boss==0)
		{
			addEnnemies();
			if (++cur_curv==MAX_CURV)
				cur_curv = 1;
		}
		if (lastN==0)
			addBonus(lastX,lastY);
	}
}

void shootsMove()
{ 
	u8 i,j,k,b,l;
	u8 sy = (SCR2_Y);
	u8 yy = sy & 0x7;

	// shoots explosions
	for (j=0;j<3;j++)
	{
		k = 0;
		for (i=0;i<nbx[j];i++)
		{
			if (--expl[j][i].life)
			{
				if (k<i)
					expl[j][k] = expl[j][i];
				k+=1;
			}
		}
		nbx[j] = k;
	}

	// move shoots
	for (j=0;j<2;j++)
	{
		k = 0;
		for (i=0;i<nbs[j];i++)
		{
			if (sh[j][i].y>155) // 151+4
				continue;
			if (--sh[j][i].life)
			{
				if (j==0)
				{
					b = 0;
					for (l=0;b==0 && l<nbe;l++)
						b = touchEntity(sh[j][i].x,sh[j][i].y,4,&enmy[l].ent,boss?24:4,sh[j][i].power);
				}
				else
					b = touchEntity(sh[j][i].x,sh[j][i].y,4,&player,6,sh[j][i].power);
				if 	( b==0 &&
					 ((j==1 && sh[j][i].x>7 && sh[j][i].x<244) ||
					  (j==0 && isEmpty(sh[j][i].x,sh[j][i].y,yy,1)))
					)
				{
					if (k!=i)
						sh[j][k] = sh[j][i];
					sh[j][k].x+=sh[j][k].dx;
					sh[j][k].y+=sh[j][k].dy;
					k+=1;
				}
				else
					addExpl(j,sh[j][i].x-4,sh[j][i].y-4,sy,8);
			}
		}
		nbs[j] = k;
	}
}

const s8 dxi[9] = {-12,-4,4,-12,-4,4,-12,-4,4};
const s8 dyi[9] = {-12,-12,-12,-4,-4,-4,4,4,4};
const u8 expli[4][4][4] =
{
	{{0,0,0,0},{1,8,0,0},{2,0,8,0},{3,8,8,0}},
	{{2,0,0,V_FLIP},{3,8,0,V_FLIP},{0,0,8,V_FLIP},{1,8,8,V_FLIP}},
	{{3,0,0,H_FLIP|V_FLIP},{2,8,0,H_FLIP|V_FLIP},{1,0,8,H_FLIP|V_FLIP},{0,8,8,H_FLIP|V_FLIP}},
	{{1,0,0,H_FLIP},{0,8,0,H_FLIP},{3,0,8,H_FLIP},{2,8,8,H_FLIP}}
}; // spr,dx,dy,flags

u16 showScore(u8 x,u8 y,u8 n)
{
	u16 j = score, k;
	if (n==64)
		return n;
	do
	{
		k = j%10;
		SetSprite(n++,t_font+k,x,y,p_font+COMMON_PALIDX1[k],TOP_PRIO);
		j/=10;
		x-=8;
	} while (j && (n<64));
	return n;
}

void showSprites()
{
	u8 i,j,n=0;
	u16 x,xi,yi;
	u8 sy = SCR2_Y;
	// player
	if (player.x<80)
		x = player.x;
	else if (player.x>176)
		x = player.x-96;
	else
		x = 80;
	i = 0;
	for (i=0;i<9;i++)
	{
		u8 xi = (u8)(x+dxi[i]);
		u8 yi = (u8)(player.y+dyi[i]);
		SetSprite(n++,i+1, xi,yi,SHIP0_PALIDX1[i],TOP_PRIO);
		SetSprite(n++,i+10,xi,yi,SHIP0_NPALS1+SHIP0_PALIDX2[i],TOP_PRIO);
	}
	// enemies
	x = scr1_x;
	for (i=0;i<nbe && boss==0;i++)
	{
		u8 xi = enmy[i].ent.x-8;
		u8 yi = enmy[i].ent.y-8;
		if ((xi+8>x)&&(xi<x+160))
		{
			if ((yi+8>0)&&(yi<152))
				SetSprite(n++,t_enmy,(u8)(xi-x),yi,p_enmy+eImg[curEnmy].pi1[0],TOP_PRIO);
			if ((yi+16>0)&&(yi+8<152))
				SetSprite(n++,t_enmy+eImg[curEnmy].w,(u8)(xi-x),yi+8,p_enmy+eImg[curEnmy].pi1[eImg[curEnmy].w],TOP_PRIO);
		}
		xi+=8;
		if ((xi+8>x)&&(xi<x+160))
		{
			if ((yi+8>0)&&(yi<152))
				SetSprite(n++,t_enmy+1,(u8)(xi-x),yi,p_enmy+eImg[curEnmy].pi1[1],TOP_PRIO);
			if ((yi+16>0)&&(yi+8<152))
				SetSprite(n++,t_enmy+1+eImg[curEnmy].w,(u8)(xi-x),yi+8,p_enmy+eImg[curEnmy].pi1[1+eImg[curEnmy].w],TOP_PRIO);
		}
	}
	if (boss)
	{
		SCR1_X = 256-(enmy[0].ent.x-scr1_x-28);
		SCR1_Y = 256-(enmy[0].ent.y-24);
	}
	// player shoots
	for (i=0;i<nbs[0]&&n<64;i++)
	{
		u8 xi = sh[0][i].x-4;
		u8 yi = sh[0][i].y-4;
		if ((xi+8>x)&&(xi<x+160)&&(yi+8>0)&&(yi<152))
			SetSprite(n++,t_expl+8,(u8)(xi-x),yi,p_expl+EXPL0_PALIDX1[8],TOP_PRIO);
	}
	// enemies shoots
	if (boss==0)
	{
		for (i=0;i<nbs[1]&&n<64;i++)
		{
			u8 xi = sh[1][i].x-4;
			u8 yi = sh[1][i].y-4;
			if ((xi+8>x)&&(xi<x+160)&&(yi+8>0)&&(yi<152))
				SetSprite(n++,t_enmy+2,(u8)(xi-x),yi,p_enmy+eImg[curEnmy].pi1[2],TOP_PRIO);
		}
	}
	else
	{
		for (i=0;i<nbs[1]&&n<64;i++)
		{
			u8 xi = sh[1][i].x-4;
			u8 yi = sh[1][i].y-4;
			if ((xi+8>x)&&(xi<x+160)&&(yi+8>0)&&(yi<152))
				SetSprite(n++,t_expl+8,(u8)(xi-x),yi,p_expl+EXPL0_PALIDX1[8],TOP_PRIO);
		}
	}
	// bonus
	for (i=0;i<nbb&&n<64;i++)
	{
		u8 xi = bonus[i].x-4;
		u8 yi = bonus[i].y-4;
		if ((xi+8>x)&&(xi<x+160)&&(yi+8>0)&&(yi<152))
		{
			u8 ti = bonus[i].type+4;
			SetSprite(n++,t_expl+ti,(u8)(xi-x),yi,p_expl+EXPL0_PALIDX1[ti],TOP_PRIO);
		}
	}
	// life
	for (i=0;i<(player.life>>1)&&n<64;i++)
		SetSprite(n++,t_font+10,0,(u8)(144-(i<<3)),p_font+COMMON_PALIDX1[10],TOP_PRIO);
	// player shoots explosions
	for (i=0;i<nbx[0]&&n<64;i++)
	{
		u8 xi = expl[0][i].x;
		u8 yi = expl[0][i].y+(expl[1][i].sy-sy);
		if ((xi+8>x)&&(xi<x+160)&&(yi+8>0)&&(yi<152))
			SetSprite(n++,t_expl+9,(u8)(xi-x),yi,p_expl+EXPL0_PALIDX1[9],TOP_PRIO);
	}
	// big explosions
	for (i=0;i<nbx[2] && n<60;i++)
	{
		u8 xi = expl[2][i].x-8;
		u8 yi = expl[2][i].y+(expl[2][i].sy-sy)-8;
		u8 ai = expl[2][i].life>>2;
		for (j=0;j<4;j++)
		{
			u8 xj = xi+expli[ai][j][1];
			u8 yj = yi+expli[ai][j][2];
			if ((xj+8>x)&&(xj<x+160)&&(yj+8>0)&&(yj<152))
				SetSprite(n++,t_expl+expli[ai][j][0],(u8)(xj-x),yj,p_expl+EXPL0_PALIDX1[expli[ai][j][0]],TOP_PRIO|expli[ai][j][3]);
		}
	}
	// enemies shoots explosions
	for (i=0;i<nbx[1]&&n<64;i++)
	{
		u8 xi = expl[1][i].x;
		u8 yi = expl[1][i].y+(expl[1][i].sy-sy);
		if ((xi+8>x)&&(xi<x+160)&&(yi+8>0)&&(yi<152))
		{
			if (boss==0)
				SetSprite(n++,t_enmy+5,(u8)(xi-x),yi,p_enmy+eImg[curEnmy].pi1[5],TOP_PRIO);
			else
				SetSprite(n++,t_expl+9,(u8)(xi-x),yi,p_expl+EXPL0_PALIDX1[9],TOP_PRIO);
		}
	}
	n = showScore(152,144,n);
	clearSprites(n);
}

void showMovie(u16*data,u16 count,u16 delay,u8 loop)
{
	u8 j;
	u16 i;

	clearPals();
	SCR1_X = SCR1_Y = SCR2_X = SCR2_Y = 0;

	moviecnt = 0;

	curmovie.data = data;
	curmovie.count = count;
	curmovie.delay = delay;
	mp_load(&curmovie);

	while((loop==0 && moviecnt<count*delay) || (loop==1 && (!((j=JOYPAD)&J_A))));
	while(loop==1 && ((j=JOYPAD)&J_A));

	mp_stop(&curmovie);
}

void gallery()
{
	u8 i,j;
	void* images[15] =
	{
		(void*)APLANET_MAX_ID,
		(void*)AWORLD_MAX_ID,
		(void*)EAGLE_MAX_ID,
		(void*)ESO269_MAX_ID,
		(void*)FIRST_MAX_ID,
		(void*)I203S_MAX_ID,
		(void*)PLANET_MAX_ID,
		(void*)SCIFI2_MAX_ID,
		(void*)SCIFI3_MAX_ID,
		(void*)SCIFI4_MAX_ID,
		(void*)SCIFI7_MAX_ID,
		(void*)SCIFI8_MAX_ID,
		(void*)SPACE1_MAX_ID,
		(void*)SPACE2_MAX_ID,
		(void*)SUN_MAX_ID
	};
	clearSprites(0);
	for (i=0;i<15;i++)
	{
		showHC((void*)images[i]);
		while(((j=JOYPAD)&J_A)==0);
		while(((j=JOYPAD)&J_A));
		curimg = 0;
	}
}

void game()
{
	u8 i;

	loadHCGfx2((u16*)READY_TILES1,READY_TILES,(u16*)READY_PALS1,READY_NPALS1,448,0,(u8*)READY_PALIDX1);
	showMovie((u16*)FIRE_C_DATA,FIRE_C_COUNT,FIRE_C_DELAY,0);
	loadGfx(0);

	player.tempo = 0;
	player.power = 0;
	player.life = 31;
	player.speed = 0;
	boss = 0;
	score = 0;
	initLevel(0);
	loadHCGfx2((u16*)FIGHT_TILES1,FIGHT_TILES,(u16*)FIGHT_PALS1,FIGHT_NPALS1,t_enmy,p_enmy,(u8*)FIGHT_PALIDX1);
	Sleep(60);
	loadCommon();
	addEnnemies();

	while(player.life && boss==0)
	{
		shootsMove();
		playerMove();
		enemiesMove();
		bonusMove();
		scrollLevel();
		showSprites();
		VBCounter = 0;
		while(VBCounter<1);
	}

	if (boss)
	{
		loadHCGfx2((u16*)BOSSI_TILES1,BOSSI_TILES,(u16*)BOSSI_PALS1,BOSSI_NPALS1,448,0,(u8*)BOSSI_PALIDX1);
		showMovie((u16*)FIRE_B_DATA,FIRE_B_COUNT,FIRE_B_DELAY,0);
		loadGfx(1);
		initLevel(1);
		loadHCGfx2((u16*)FIGHT_TILES1,FIGHT_TILES,(u16*)FIGHT_PALS1,FIGHT_NPALS1,t_scr1,p_enmy,(u8*)FIGHT_PALIDX1);
		Sleep(60);
		loadCommon();
		cur_curv = 0;
		addBoss();

		while(player.life && enmy[0].ent.life)
		{
			shootsMove();
			playerMove();
			enemiesMove();
			scrollLevel();
			showSprites();
			VBCounter = 0;
			while(VBCounter<1);
		}
	}
	clearSprites(0);
	loadHCGfx();
	if (player.life==0)
	{
		i = printHC("YOU LOSE",48,32,0);
		i = printHC("TRY AGAIN",44,116,i);
	}
	else
	{
		score+=100;
		i = printHC("YOU WIN",48,32,0);
		i = printHC("ENJOY THE PICTURES",8,116,i);
	}
	i = printHC("SCORE",32,144,i);
	showScore(120,144,i);
	SL_PlaySFX(4);
	showMovie((u16*)EXPLOSION_DATA,EXPLOSION_COUNT,EXPLOSION_DELAY,0);
	Sleep(60);
	clearSprites(0);
	if (player.life)
		gallery();
}

void present()
{
	u8 i,j,k;

	char * scenario_t[6] = {
		"IN A GALAXY FAR AWAY"	,"REALLY FAR FAR AWAY",
		" THE EVIL IS BACK"		,"TO DESTROY ALL LIFE",
		"YOU ARE THE ONLY ONE"	,"AVAILABLE TO FIGHT"
	};

	void* scenario_i[3][3] = {
		{
		(void*)FIRST_LARGE_ID,
		(void*)I203S_LARGE_ID,
		(void*)PLANET_LARGE_ID
		},
		{
		(void*)EAGLE_LARGE_ID,
		(void*)SCIFI2_LARGE_ID,
		(void*)AWORLD_LARGE_ID
		},
		{
		(void*)SCIFI4_LARGE_ID,
		(void*)APLANET_LARGE_ID,
		(void*)SCIFI3_LARGE_ID
		}
	};

	clearSprites(0);
	loadHCGfx();
	/*j = printHC("WWW.CONSOLEFEVER.COM",0,0,0);*/
	j = printHC("WWW.PDROMS.COM COMPO",0,5,0/*j*/);
	j = printHC("THOR AND ICEMAN",16,132,j);
	j = printHC("PRESENT",52,142,j);
	showMovie((u16*)TUNNEL_DATA,TUNNEL_COUNT,TUNNEL_DELAY,1);
	clearSprites(0);

	SeedRandom();
	j = printHC("ANOTHER DAY IN HELL",4,2,0);
	j = printHC("PRESS A TO START",20,140,j);
	showHC((void*)SCIFI7_LARGE_ID);
	while(((j=JOYPAD)&J_A)==0)
		GetRandom(2);
	while(((j=JOYPAD)&J_A));

	for (i=0;i<3;i++)
	{
		clearSprites(0);
		j = printHC(scenario_t[i<<1],0,0,0);
		j = printHC(scenario_t[(i<<1)+1],8,140,j);
		showHC((void*)scenario_i[curpres][i]);
		while(((j=JOYPAD)&J_A)==0);
		while(((j=JOYPAD)&J_A));
		curimg=0;
	}
	curpres = curpres<2?curpres+1:0;
	clearSprites(0);
}

void main()
{
	InitNGPC();

	ClearScreen(SCR_1_PLANE);
	ClearScreen(SCR_2_PLANE);
	SetBackgroundColour(RGB(0, 0, 0));

	hc_detect();

	//getData();
	SL_SoundInit();
	SL_LoadGroup(metalslug0,sizeof(metalslug0));
	SL_PlayTune(0);

	curimg = 0;

	__asm(" di");
	VBL_INT = myVBL;
	__asm(" ei");

	while(1)
	{
		present();
		game();
	}
}
