#include "ngpc.h"
#include "carthdr.h"
#include "library.h"

#include "sprites.h"
#include "dynabase.h"
#include "levels.h"
#include "demo.h"
#include "hicolor.h"
#include "menu.hh"
#include "bustamove0.mh"
#include "bustamove1.mh"
#include "bustamove2.mh"

#define TUTORIAL 255

extern volatile u8 VBCounter;

u8 data[256];

#define	MAGIC_NB	0xcafebabe	// java rules !

void flash(void *data)
{
	__ASM("SAVEOFFSET	EQU	0x1e0000");

	__ASM("BLOCK_NB		EQU	30");
	__ASM("VECT_FLASHWRITE	EQU	6");
	__ASM("VECT_FLASHERS	EQU	8");
	__ASM("rWDCR		EQU	0x6f");
	__ASM("WD_CLR		EQU	0x4e");

	// Erase block first (mandatory) : 64kb for only 256 bytes
	__ASM("	ld	ra3,0");
	__ASM("	ld	rb3,BLOCK_NB");
	__ASM("	ld	rw3,VECT_FLASHERS");
	__ASM("	ld	(rWDCR),WD_CLR");
	__ASM("	swi	1");

	// Then write data
	__ASM("	ld	ra3,0");
	__ASM("	ld	rbc3,1");	// 256 bytes
	__ASM("	ld	xhl,(xsp+4)");
	__ASM("	ld	xhl3,xhl");
	__ASM("	ld	xde3,SAVEOFFSET");
	__ASM("	ld	rw3,VECT_FLASHWRITE");
	__ASM("	ld	(rWDCR),WD_CLR");
	__ASM("	swi	1");

	__ASM("	ld	(rWDCR),WD_CLR");
}

void getSavedData()
{
	u32 *ptr = (u32*)(0x200000+0x1e0000);
	u32 *ptrData = (u32*)data;
	u8 i;
	if (*ptr == MAGIC_NB) // Data saved
	{
		for (i=0;i<64;i++)
			ptrData[i] = ptr[i];
	}
	else // No data
	{
		ptrData[0] = MAGIC_NB;
		for (i=1;i<64;i++)
			ptrData[i] = 0;//x20202020;
	}
}

void *curimg;
volatile u8 ingame;
volatile u8 tutorial;
u8 curs;
u8 cpt;

void __interrupt hicolorVBL()
{
	u8 j;
	WATCHDOG = WATCHDOG_CLEAR;
	if (USR_SHUTDOWN) { SysShutdown(); while (1); }

	VBCounter++;

	if (hc_emu())
		hc_showEmu(curimg);
	else
		hc_showHW(curimg);

	if (ingame)
	{
		*(SPRITE_RAM) = 20+curs;
		if (--cpt==0)
		{
			curs = (curs+1)%4;
			cpt = 5;
		}
		if (tutorial && (((j=JOYPAD)&J_A)||(j&J_B)))
			tutorial = 2;
			
	}
}

void showHC(void *img)
{
	curimg=0;
	hc_load(img);
	curimg=img;
}

void setTile(u16* plane,u16 x,u16 y,u16 tile)
{
	plane[y*32+x] = tile+(((u16)sprites_palidx[tile])<<9);
}

void print(const char *s,u16 x,u16 y,u8 dir)
{
	u16 i;
	for (i=0;s[i];i++)
	{
		u8 c = s[i];
		c = (c>='a'&&c<='z') ? c-'a' : ((c>='A'&&c<='Z') ? c-'A' : ((c>='0'&&c<='9') ? c-'0'+27: 255));
		if (c==255)
		{
			switch(s[i])
			{
				case '/': c= 37; break;
				case ':': c= 38; break;
				case '.': c= 39; break;
				case '!': c= 40; break;
				case '?': c= 41; break;
				default : c= 26; break;
			}
		}
		setTile(SCROLL_PLANE_1,x+(dir?0:i),y+(dir?i:0),30+c);
	}
}

void printl(const char *s,u16 x,u16 y,u8 dir,u8 l)
{
	char buf[32];
	u8 i;
	for (i=0;s[i];i++)
		buf[i]=s[i];
	while (i<l)
		buf[i++]=' ';
	buf[i]=0;
	print(buf,x,y,dir);
}

void printi(int i,u16 x,u16 y,u8 dir,u8 l)
{
	char buf[9];
	u8 j = 9;
	buf[j]=0;
	do
	{
		buf[--j]='0'+(i%10);
		i /= 10;
	} while (i>0);
	printl(buf+j,x,y,dir,l);
}

void loadGfx()
{
	u16 i;
	u16 *p0 = TILE_RAM,*p1,*p2;
	for (i=0;i<SPRITES_TILES*8;i++)
		p0[i] = sprites_tiles[i];
	p0 = SCROLL_1_PALETTE;
	p1 = SCROLL_2_PALETTE;
	p2 = SPRITE_PALETTE;
	for (i=0;i<SPRITES_PALS*4;i++)
	{
		p0[i] = sprites_pals[i];
		p1[i] = sprites_pals[i];
		p2[i] = sprites_pals[i];
	}
}

void loadSpr(u16 offset)
{
	u16 i;
	u16 *p0 = TILE_RAM+(offset*8),*p1 = SPRITE_PALETTE;
	for (i=0;i<SPRITES_TILES*8;i++)
		p0[i] = sprites_tiles[i];
	for (i=0;i<SPRITES_PALS*4;i++)
		p1[i] = sprites_pals[i];
}

void moveCursorTo(u8 x,u8 y,u8 nx,u8 ny,s8 d)
{
	s8 dx = (nx>x)?d:(nx<x?-d:0);
	s8 dy = (ny>y)?d:(ny<y?-d:0);
	while((x!=nx)||(y!=ny))
	{
		if (x!=nx)
			x+=dx;
		if (y!=ny)
			y+=dy;
		SetSpritePosition(0,x+16,y+8);
		Sleep(1);
	}
}

u8 fastMove(u8 *x,u8 *y,s8 dx,s8 dy)
{
	u8 k = (*y/8), l=(*x/8),p=(dm_field()[k*16+l] == DM_P_EMPTY);
	if (!p)
		return 0;
	while (l>=0&&l<16&&k>=0&&k<16&&p)
	{
		l+=dx;
		k+=dy;
		p = (k>15||l>15) ? 0 : (dm_field()[k*16+l] == DM_P_EMPTY);
	}
	if (!p)
	{
		l-=dx;
		k-=dy;
	}
	moveCursorTo(*x,*y,l*8,k*8,2);
	*x = l*8;
	*y = k*8;
	return 1;
}

void update(u16 x,u16 y)
{
	u8 p = dm_field()[y*16+x];
	u16 spr;
	y+=1;
	x+=2;

	switch(p&0x7f)
	{
		case DM_P_EMPTY:
			spr = 0;
			break;
		case DM_P_RED:
			spr = 1;
			break;
		case DM_P_GREEN:
			spr = 2;
			break;
		case DM_P_BLUE:
			spr = 3;
			break;
		case DM_P_BLACK:
			spr = 15;
			break;
		case DM_P_YELLOW:
			spr = 4;
			break;
		case DM_P_HORI:
			spr = 7;
			break;
		case DM_P_VERT:
			spr = 6;
			break;
		case DM_P_TELE1:
			spr = 8;
			break;
		case DM_P_TELE2:
			spr = 9;
			break;
		case DM_P_ROTCOL:
			spr = (p&0x80)?17:11;
			break;
		case DM_P_ROT_CW:
			spr = 10;
			break;
		case DM_P_ROT_CCW:
			spr = 18;
			break;
		case DM_P_GRAY:
			spr = p&0x80?5:14;
			break;
		case DM_P_DIE:
			spr = 16;
			break;
	}
	setTile(SCROLL_PLANE_2,x,y,spr);
	spr = ((p&0x7f)==DM_P_ROTCOL) ? 12 : (p&0x80) && (p!=DM_P_GRAY+0x80) && (p!=DM_P_DIE+0x80) ? 13 : 0;
	setTile(SCROLL_PLANE_1,x,y,spr);
}

void draw_level()
{
   u8 x,y;
   for (y = 0 ; y < 16 ; y++)
      for (x = 0 ; x < 16 ; x++)
         update(x,y);
}

u8 getMove(u8 *x,u8 *y,u8 *d,u8 lvl)
{
	u8 xx = (*x)*8;
	u8 yy = (*y)*8;
	u8 j,i;

	SetSpritePosition(0,xx+16,yy+8);
	while(1)
	{
		//Sleep(4);
		j = JOYPAD;
		if (j&J_A || j&J_B)
		{
			if (j&J_LEFT)
			{
				if (fastMove(&xx,&yy,-1,0))
					continue;
				*d = DM_LEFT;
				break;
			}
			if (j&J_RIGHT)
			{
				if (fastMove(&xx,&yy,1,0))
					continue;
				*d = DM_RIGHT;
				break;
			}
			if (j&J_UP)
			{
				if (fastMove(&xx,&yy,0,-1))
					continue;
				*d = DM_UP;
				break;
			}
			if (j&J_DOWN)
			{
				if (fastMove(&xx,&yy,0,1))
					continue;
				*d = DM_DOWN;
				break;
			}
		}
		else
		{
			u8 ox=xx,oy=yy;
			if (j&J_LEFT)
				xx = xx>0 ? xx - 8 : 0;
			if (j&J_RIGHT)
				xx = xx<120 ? xx + 8 : 120;
			if (j&J_UP)
				yy = yy>0 ? yy - 8 : 0;
			if (j&J_DOWN)
				yy = yy<120 ? yy + 8 : 120;
			if (j&J_OPTION)
			{
				u8 k=0;
				while (j=JOYPAD);
				// game menu
				print(" RESET  SELECT LEVEL",0,18,0);
				print("B TO EXIT",19,1,1);
				SetSprite(1,19,0,0,0,sprites_palidx[19],TOP_PRIO);
				do
				{
					if (j&J_LEFT)
						k = 0;
					else if (j&J_RIGHT)
						k = 7;
					SetSpritePosition(1,k*8,144);
					if (j)
					{
						SL_PlaySFX(2);
						Sleep(10);
					}
					j = JOYPAD;
				} while (!((j&J_A)||(j&J_OPTION)||(j&J_B)));
				print("         ",19,1,1);
				SetSpritePosition(1,160,144);
				while (i=JOYPAD);
				if ((j&J_OPTION))
					return 3;
				else if ((j&J_B))
					return 4;
				if (k)
				{
					print("LEFT/RIGHT TO SELECT",0,18,0);
					do
					{
						if (j&J_LEFT)
							lvl = lvl>0 ? lvl-1 : 0;
						else if (j&J_RIGHT)
							lvl = (lvl<NB_LEVELS-1 && data[4+lvl]>0) ? lvl+1 : lvl;
						if (j)
						{
							dm_init_level(dml_levels[lvl].field);
							printl(dml_levels[lvl].name,0,1,1,16);
							draw_level();
							SL_PlaySFX(1);
							Sleep(10);
						}
					} while (!((j=JOYPAD)&J_A));
					return lvl+5;
				}
				else
					return 2;
			}
			moveCursorTo(ox,oy,xx,yy,1);
		}
	}
	*x = xx/8;
	*y = yy/8;
	return j&J_A?1:0;
}

void animateexpl(u8 x,u8 y)
{
}

void showMoveC(u8 lvl)
{
	print(" MOVES:     BEST:",0,18,0);
	if (data[4+lvl]>0)
		printi(data[4+lvl],17,18,0,4);
	else
		printl("NO",17,18,0,3);
}

void play(u8 lvl)
{
	u8 i,j,x,y,d,cx=0,cy=0,ds=0,dd=0,dn=demo_nb[0];

	if (lvl==TUTORIAL)
	{
		SL_LoadGroup(bustamove2,sizeof(bustamove2));
		tutorial = 1;
	}
	else
	{
		SL_LoadGroup(bustamove1,sizeof(bustamove1));
		tutorial = 0;
	}
	SL_PlayTune(0);

	ingame = 1;

	// draw borders
	for (i=0;i<16;i++)
	{
		setTile(SCROLL_PLANE_1,i+2,0,25);
		setTile(SCROLL_PLANE_1,i+2,17,25);
		setTile(SCROLL_PLANE_1,1,i+1,24);
		setTile(SCROLL_PLANE_1,18,i+1,24);
	}
	setTile(SCROLL_PLANE_1,1,0,27);
	setTile(SCROLL_PLANE_1,18,0,29);
	setTile(SCROLL_PLANE_1,1,17,26);
	setTile(SCROLL_PLANE_1,18,17,28);

	SetSprite(0,20,0,16,8,sprites_palidx[20],TOP_PRIO);

	do
	{
		if (lvl!=TUTORIAL)
		{
			dm_init_level(dml_levels[lvl].field);
			printl(dml_levels[lvl].name,0,1,1,16);
			showMoveC(lvl);
		}
		else
		{
			dm_init_level(demo_level.field);
			printl(demo_level.name,0,1,1,16);
		}

		draw_level();
		do
		{
			x = cx;
			y = cy;
			if (lvl!=TUTORIAL)
			{
				printi(dm_movecount(),7,18,0,3);
				j = getMove(&x,&y,&d,lvl);
			}
			else
			{
				if (tutorial==2)
					return;	
				print(demo_texts[ds],3,3,0);
				x = demo_moves[dd++];
				y = demo_moves[dd++];
				d = demo_moves[dd++];
				if ((--dn)==0)
				{
					if (++ds == DEMO_STEPS)
					{
						i = DM_FINISHED;
						break;
					}
					dn = demo_nb[ds];
				}
				if (d==255)
				{
					moveCursorTo(cx*8,cy*8,x*8,y*8,2);
					cx = x;
					cy = y;
					Sleep(60);
					continue;
				}
				j = 0;
			}
			if (j==2) // retart level
			{
				i = 255;
				break;
			}
			else if (j==3)
			{
				showMoveC(lvl);
				printi(dm_movecount(),7,18,0,3);
			}
			else if (j==4)
			{
				return;
			}
			else if (j>=5)
			{
				lvl = j-5;
				i = 255;
				break;
			}

			dm_init_step (x,y,d);

			SL_PlaySFX(1);
			do
			{
				i = dm_step();
				switch (dm_cmd())
				{
					case DM_TELE:
						SL_PlaySFX(4);
					case DM_MOVE:
						{
							update(dm_dstx(),dm_dsty());
							update(dm_srcx(),dm_srcy());
						}
						break;
            				case DM_EXPL:
						{
							SL_PlaySFX(11);
							animateexpl(dm_srcx(),dm_srcy());
							update(dm_srcx(),dm_srcy());
						}
						break;
				}
				Sleep(lvl==TUTORIAL?5:3);
			} while (!i);

			if (j)
			{
				cx = dm_srcx();
				cy = dm_srcy();
				moveCursorTo(x*8,y*8,dm_srcx()*8,dm_srcy()*8,8);
			}
			else
			{
				cx = x;
				cy = y;
			}

			if (lvl==TUTORIAL)
				Sleep(40);

		} while ((i=dm_state())==DM_NORMAL);

		Sleep(10);
		switch(i)
		{
			case DM_FINISHED:
				if (dm_movecount())
				{
					if (lvl==TUTORIAL)
					{
						print("      HAVE FUN      ",0,18,0);
						break;
					}
					else
					{
						if (data[4+lvl]==0 || dm_movecount()<data[4+lvl])
						{
							data[4+lvl] = dm_movecount();
							flash(data);
							print("  NEW BEST SCORE !  ",0,18,0);
						}
						else
							print("  CONGRATULATIONS!  ",0,18,0);
						lvl++;
					}
					SL_PlaySFX(7);
				}
				else
					continue;
				break;
			case DM_OUT_OF_MOVES:
				print("   OUT OF MOVES !   ",0,18,0);
				SL_PlaySFX(8);
				break;
			case DM_OUT_OF_FIELD:
				print("   OUT OF FIELD !   ",0,18,0);
				SL_PlaySFX(8);
				break;
			case DM_HIT_DIE_PIECE:
				print("  HIT DIE PIECE !   ",0,18,0);
				SL_PlaySFX(8);
				break;
			case DM_INFINITE_LOOP:
				print("  INFINITE LOOP !   ",0,18,0);
				SL_PlaySFX(8);
				break;
		}
		if (i<255)
			Sleep(120);
	} while (lvl<NB_LEVELS);
	if (lvl!=TUTORIAL)
	{
		print("YOU HAVE ALL CLEARED",0,18,0);
		Sleep(120);
	}
	SetSpritePosition(0,160,0);
}

u8 mainMenu()
{
	u8 j = 0,k=data[4]>0?1:0;
	const u8 idx[] = {80,93,106};

	SL_LoadGroup(bustamove0,sizeof(bustamove0));
	SL_PlayTune(0);

	loadSpr(420);
	showHC((void*)MENU_ID);

	SetSprite(0,420+19,0,80,idx[k],sprites_palidx[19],TOP_PRIO);
	while((((j=JOYPAD)&J_A) == 0) && ((j&J_B) == 0))
	{
		if ((j&J_DOWN)||(j&J_LEFT))
			k = k < 2 ? k+1: 0;
		else if ((j&J_UP)||(j&J_RIGHT))
			k = k > 0 ? k-1: 2;
		SetSpritePosition(0,80,idx[k]);
		if (j)
		{
			SL_PlaySFX(2);
			Sleep(10);
		}
       	}
       	while((((JOYPAD)&J_A) != 0) || (((JOYPAD)&J_B) != 0));

	curimg = 0;

	ClearScreen(SCR_1_PLANE);
	ClearScreen(SCR_2_PLANE);
	return k;
}

void main()
{
	u8 i;

	InitNGPC();

	ClearScreen(SCR_1_PLANE);
	ClearScreen(SCR_2_PLANE);

	hc_detect();

	SetBackgroundColour(RGB(0, 0, 0));

	getSavedData();

	ingame = 0;
	curs = 0;
	cpt = 1;
	curimg = 0;

	__asm(" di");
	VBL_INT = hicolorVBL;
	__asm(" ei");

	SL_SoundInit();

	while(1)
	{
		i = mainMenu();
		loadGfx();
		switch(i)
		{
			case 1:
				for (i=0;i<NB_LEVELS-1 && data[4+i]!=0;i++);
			case 0:
				play(i);
				break;
			case 2:
				play(TUTORIAL);
				while(i=JOYPAD);
				break;
		}
		ingame = 0;
	}
}
