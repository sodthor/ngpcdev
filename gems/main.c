#include "ngpc.h"
#include "carthdr.h"
#include "library.h"
#include "hicolor.h"
#include "flash.h"
#include "music.h"
#include "img.h"

#include "magical0.mh"
#include "magical1.mh"
#include "magical2.mh"
#include "menu.hh"
#include "gems.h"
#include "title.h"
#include "back.hh"

#define NONE 0xff
#define RESERVED 0xfe
#define MOVE 0x80
#define MOVE_MASK 0x7f
#define MAX_MOVE 15

void *curimg;

extern volatile u8 VBCounter;

volatile u8 ingame;
u8 curs;
u8 cpt;
volatile u8 cursx,cursy,destx,desty,dir;
volatile s8 cdx,cdy;
volatile u8 inmove;
volatile u8 ismoving;

const u8 lvl_values[] = {5,6,7,8};
u8 lvl;
u8 ttrial;
u8 board[8][8];
volatile u8 top;
volatile u16 tcpt;
volatile u8 tcmd;
volatile u8 innew;
u16 score;
u8 left;
u8 psx0,psx1,psy0,psy1;

void setTile(u16* plane,u16 x,u16 y,u16 tile)
{
	plane[y*32+x] = tile+(((u16)gems_palidx[tile-1])<<9);
}

void print(const char *s,u16 x,u16 y,u8 dir)
{
	u16 i;
	for (i=0;s[i];i++)
	{
		u8 c = s[i];
		c = (c>='a'&&c<='z') ? c-'a'+GEMS_LINE*3 : ((c>='A'&&c<='Z') ? c-'A'+GEMS_LINE*3 : ((c>='0'&&c<='9') ? c-'0'+GEMS_LINE*3-10: 255));
		if (c==255)
		{
			switch(s[i])
			{
				case ':': c= GEMS_LINE*3+26; break;
				case '!': c= GEMS_LINE*3+27; break;
				case '.': c= GEMS_LINE*3+28; break;
				default : c= 255; break;
			}
		}
		setTile(SCROLL_PLANE_1,x+(dir?0:i),y+(dir?i:0),c+1);
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

void printi(u16 i,u16 x,u16 y,u8 dir,u8 l)
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

void showScore()
{
	print("   SCORE:       ",3,17,0);
	printi(score,13,17,0,4);
}

void hurryUp()
{
	print("   HURRY UP !   ",3,17,0);
	SL_PlaySFX(11);
}

void showCountDown()
{
	print("                ",3,17,0);
	printi(7 - tcmd,11,17,0,1);
	SL_PlaySFX(12);
}

void setSpritePos(u8 spr,u8 x,u8 y)
{
	spr*=4;
	SetSpritePosition(spr,x,y);
	SetSpritePosition(spr+1,x+8,y);
	SetSpritePosition(spr+2,x,y+8);
	SetSpritePosition(spr+3,x+8,y+8);
}

void setCursorPosition(u8 x,u8 y)
{
	setSpritePos(0,x+24,y);
}

void __interrupt myVBL()
{
	WATCHDOG = WATCHDOG_CLEAR;
	if (USR_SHUTDOWN) { SysShutdown(); while (1); }
	VBCounter++;

	if (hc_emu())
		hc_showEmu(curimg);
	else
		hc_showHW(curimg);
	if (ingame)
	{
		u8 *p = SPRITE_RAM;
		p[0] = 17+curs*2;
		p[4] = 18+curs*2;
		p[8] = 17+GEMS_LINE+curs*2;
		p[12]= 18+GEMS_LINE+curs*2;
		if (--cpt==0)
		{
			if (inmove)
				curs = curs==3 ? 0 : curs+1;
			else
				curs = curs==0 ? 3 : curs-1;
			cpt = 7;
		}
		if (ismoving)
		{
			cursx+=cdx;
			cursy+=cdy;
			setCursorPosition(cursx,cursy);
			if (cursx==destx && cursy==desty)
				ismoving = 0;
		}
		else
		{
			u8 j =JOYPAD;
			if (inmove && (j&J_A || j&J_B))
			{
				if ((j&J_LEFT) && (cursx>0))
					dir = J_LEFT;
				else if ((j&J_RIGHT) && (cursx<112))
					dir = J_RIGHT;
				else if ((j&J_UP) && (cursy>0))
					dir = J_UP;
				else if ((j&J_DOWN) && (cursy<112))
					dir = J_DOWN;
				if (dir!=0)
					inmove = 0;
			}
			else if (dir==0)
			{
				destx=cursx;
				desty=cursy;
				if (j&J_LEFT)
					destx = destx>0 ? destx - 16 : 0;
				if (j&J_RIGHT)
					destx = destx<112 ? destx + 16 : 112;
				if (j&J_UP)
					desty = desty>0 ? desty - 16 : 0;
				if (j&J_DOWN)
					desty = desty<112 ? desty + 16 : 112;
				if (destx!=cursx||desty!=cursy)
				{
					cdx = (destx>cursx)?1:(destx<cursx?-1:0);
					cdy = (desty>cursy)?1:(desty<cursy?-1:0);
					ismoving=1;
				}
			}
		}
		if (innew)
			return;
		if (ttrial)
		{
			if (--tcpt==0)
			{
				tcpt = 16+((4-lvl)*4)-(top<32?8:0);
				if (top>0&&top<128)
					top-=1;
			}
		}
		else
		{
			if (--tcpt==0)
			{
				switch(tcmd)
				{
					case 0:
						showScore();
						tcpt = 500;
						break;
					case 1:
						hurryUp();
						tcpt = 100;
						break;
					default:
						if (tcmd<8)
							showCountDown();
						tcpt = 60;
						break;
				}
				tcmd+=1;
			}
		}
	}
}

void showHC(void *img)
{
	curimg=0;
	hc_load(img);
	curimg=img;
}

void loadGfx()
{
	u16 i;
	u16 *p0 = TILE_RAM;
	u16 *p1 = SCROLL_1_PALETTE;
	u16 *p2 = SPRITE_PALETTE;
	for (i=0;i<GEMS_TILES*8;i++)
		p0[i+8] = gems_tiles[i];
	for (i=0;i<GEMS_PALS*4;i++)
	{
		p1[i] = gems_pals[i];
		p2[i] = gems_pals[i];
	}
}

void initBoard()
{
	u8 i,j,k;
	for (i=0;i<8;i++)
	{
		for (j=0;j<8;j++)
		{
			while (1)
			{
				k = GetRandom(lvl_values[lvl]);
				if (!((i>0 && k==board[i-1][j]) || (j>0 && k==board[i][j-1])))
					break;
			}
			board[i][j] = k;
		}
	}
}

void showBar()
{
	u8 i,j,k;
	k = GEMS_LINE*2+11;
	j = top/8;
	for (i=0;i<j;i++)
		setTile(SCROLL_PLANE_1,1,17-i,k);
	setTile(SCROLL_PLANE_1,1,17-j,k+top%8+1);
	for (i=1;i<17-j;i++)
		setTile(SCROLL_PLANE_1,1,i,0);
}

void clearTile16(u16 i,u16 j,u16 r)
{
	setTile(SCROLL_PLANE_1,i*2+3,j*2,r);
	setTile(SCROLL_PLANE_1,i*2+4,j*2,r);
	setTile(SCROLL_PLANE_1,i*2+3,j*2+1,r);
	setTile(SCROLL_PLANE_1,i*2+4,j*2+1,r);
}

void setTile16(u16 i,u16 j,u16 k)
{
	setTile(SCROLL_PLANE_1,i*2+3,j*2,k);
	setTile(SCROLL_PLANE_1,i*2+4,j*2,k+1);
	setTile(SCROLL_PLANE_1,i*2+3,j*2+1,k+GEMS_LINE);
	setTile(SCROLL_PLANE_1,i*2+4,j*2+1,k+GEMS_LINE+1);
}

void showBoard(u16 r)
{
	u16 i,j,k;
	r = r>0 ? GEMS_LINE - r + 1 : 0;
	for (i=0;i<8;i++)
	{
		for (j=0;j<8;j++)
		{
			if (board[i][j]==NONE||board[i][j]==RESERVED)
				clearTile16(i,j,r);
			else
				setTile16(i,j,(board[i][j]&MOVE_MASK)*2+1);
		}
	}
	showBar();
}

void hideBoard()
{
	u16 i,j;
	for (i=0;i<8;i++)
	{
		for (j=0;j<8;j++)
			clearTile16(i,j,0);
	}
}

void getMove()
{
	u8 i,j;

	dir = 0;
	inmove = 1;
	while(inmove)
	{
		if (ttrial)
		{
			if (top==0)	// time elapsed !
				break;
			showBar();
		}
		else
		{
			if (tcmd>7)	// time out !
				break;
		}
		j = JOYPAD;
		if (j&J_OPTION)
		{
			ingame = 0;
			print("A:RESUME  B:EXIT",3,17,0);
			hideBoard();
			while(1)
			{
				j=JOYPAD;
				if (j&J_A||j&J_B)
					break;
			}
			while (i=JOYPAD);
			showScore();
			showBoard(0);
			ingame = 1;
			if (j&J_B)
			{
				dir = NONE;
				break;
			}
		}
	}
	inmove = 0;
	while (j=JOYPAD);
}

u8 findBlock()
{
	u8 i,j,k;

	for (i=0;i<8;i++)
	{
		for (j=0;j<8;j++)
		{
			for (k=i+1;k<8&&board[k][j]==board[i][j];k++);
			if (k-i>2)
				return 1;
			for (k=j+1;k<8&&board[i][k]==board[i][j];k++);
			if (k-j>2)
				return 1;
		}
	}
	return 0;
}

#define swap(a,b) { u8 tmp = a; a = b; b = tmp; }

u8 moveExists()
{
	u8 i,j,a,b;

	for (i=0;i<8;i++)
	{
		for (j=0;j<7;j++)
		{
			if (i<7)
			{
				swap(board[i][j],board[i+1][j]);
				b = findBlock();
				swap(board[i][j],board[i+1][j]);
				if (b)
				{
					psx0 = i;
					psx1 = i+1;
					psy0 = psy1 = j;
					return 1;
				}
			}
			swap(board[i][j],board[i][j+1]);
			b = findBlock();
			swap(board[i][j],board[i][j+1]);
			if (b)
			{
				psx0 = psx1 = i;
				psy0 = j;
				psy1 = j+1;
				return 1;
			}
		}
	}
	return 0;
}

u8 removeGems()
{
	u8 i,j,k,l,n=0;
	u8 remove[8][8];
	for (i=0;i<8;i++)
	{
		for (j=0;j<8;j++)
			remove[i][j] = (board[i][j] == NONE ? 1 : 0);
	}
	for (i=0;i<8;i++)
	{
		for (j=0;j<8;j++)
		{
			for (k=i+1;k<8&&board[k][j]==board[i][j];k++);
			if (k-i>2)
			{
				for(l=i;l<k;l++)
					remove[l][j] = 1;
			}
			for (k=j+1;k<8&&board[i][k]==board[i][j];k++);
			if (k-j>2)
			{
				for(l=j;l<k;l++)
					remove[i][l] = 1;
			}
		}
	}
	for (i=0;i<8;i++)
	{
		for (j=0;j<8;j++)
		{
			if (remove[i][j])
			{
				board[i][j] = NONE;
				n+=1;
			}
		}
	}
	return n;
}

void clearSprite(u8 idx)
{
	idx<<=2;
	SetSprite(idx,0,0,0,0,gems_palidx[0],TOP_PRIO);
	SetSprite(idx+1,0,0,8,0,gems_palidx[0],TOP_PRIO);
	SetSprite(idx+2,0,0,0,8,gems_palidx[0],TOP_PRIO);
	SetSprite(idx+3,0,0,8,8,gems_palidx[0],TOP_PRIO);
}

void setSprite(u8 idx,u8 num,u8 x,u8 y)
{
	idx<<=2;
	SetSprite(idx,num,0,x,y,gems_palidx[num-1],TOP_PRIO);
	SetSprite(idx+1,num+1,0,x+8,y,gems_palidx[num],TOP_PRIO);
	SetSprite(idx+2,num+GEMS_LINE,0,x,y+8,gems_palidx[num+GEMS_LINE-1],TOP_PRIO);
	SetSprite(idx+3,num+1+GEMS_LINE,0,x+8,y+8,gems_palidx[num+1+GEMS_LINE-1],TOP_PRIO);
}

u16 addNewGems()
{
	u16 v = 0;
	u8 rv,i,j,k,l,n,nb = 0;
	u8 moves[64][3]; // num,x,y
	u8 spr[MAX_MOVE][4]; // num,x,ysrc,ydst

	innew = 1;
	showBoard(0);
	while (rv = removeGems())
	{
		SL_PlaySFX(1);
		for (j=4;j!=255;j--)
		{
			showBoard(j);
			Sleep(4);
		}
		v+=rv;
		nb+=1;
		top+=rv*nb;
		score+=rv*(lvl+1);
		if (top>128) // level up !
			top = 128;
		if (nb>1)
		{
			print("   CHAIN COMBO !",3,17,0);
			printi(nb,4,17,0,2);
			if (!ttrial)
				left += 1;
		}
		k = 0;
		for (i=0;i<8;i++)
		{
			l = 0;
			for (j=0;j<8;j++) // gems to move
			{
				if (board[i][j]==NONE)
					l+=1;
				if ((j>0) && (board[i][7-j]!=NONE) && (board[i][7-j+1]&MOVE))
				{
					moves[k][0] = board[i][7-j];
					moves[k][1] = i;
					moves[k][2] = 7-j;
					board[i][7-j] |= MOVE;
					k += 1;
				}
			}
			for (j=0;j<l;j++) // new gems
			{
				moves[k][0] = GetRandom(lvl_values[lvl]);
				moves[k][1] = i;
				moves[k][2] = 255;
				k+=1;
			}
		}
		// do moves
		for (i=0;i<MAX_MOVE;i++)
			spr[i][0] = NONE;
		i=0;
		n=0;
		while (n<MAX_MOVE)
		{
			n=0;
			for (l=0;l<MAX_MOVE;l++)
			{
				if (spr[l][0]==NONE)
				{
					if (i==k)
					{
						n+=1;
						continue;
					}
					j = moves[i][2];
					if (j==255)
					{
						j = 0;
						spr[l][2] = 240;
					}
					else
					{
						board[moves[i][1]][j] = NONE;
						clearTile16(moves[i][1],j,0);
						spr[l][2] = j<<4;
					}
					spr[l][0] = moves[i][0];
					spr[l][1] = (moves[i][1]<<4)+24;
					for (;j<8 && board[moves[i][1]][j] == NONE;j++);
					spr[l][3] = (j-1)<<4;
					board[moves[i][1]][j-1] = RESERVED;
					setSprite(l+1,(spr[l][0]*2)+1,spr[l][1],spr[l][2]);
					i+=1;
					break;
				}
				else
				{
					spr[l][2]+=4;
					setSpritePos(l+1,spr[l][1],spr[l][2]);
					if (spr[l][2]==spr[l][3])
					{
						u8 a = (spr[l][1]-24)>>4, b = spr[l][3]>>4;
						clearSprite(l+1);
						board[a][b] = spr[l][0];
						setTile16(a,b,spr[l][0]*2+1);
						spr[l][0] = NONE;
					}
				}
			}
			Sleep(1);
		}
	}
	innew = 0;
	return v;
}

void swapGems(u8 x,u8 y,u8 xx,u8 yy)
{
	u8 a = board[x][y],
	   b = board[xx][yy],
	   xa,ya,xb,yb,x0,y0;
	s8 dx=0,dy=0;

	board[x][y] = board[xx][yy] = NONE;

	x0 = xa = (x<<4)+24;
	y0 = ya = (y<<4);
	xb = (xx<<4)+24;
	yb = (yy<<4);

	if (xa<xb)
		dx = 4;
	else if (xa>xb)
		dx = -4;
	if (ya<yb)
		dy = 4;
	else if (ya>yb)
		dy = -4;

	setSprite(1,a*2+1,xa,ya);
	setSprite(2,b*2+1,xb,yb);

	showBoard(0);

	while (x0!=xb||y0!=yb)
	{
		xa+=dx;
		xb-=dx;
		ya+=dy;
		yb-=dy;
		setSpritePos(1,xa,ya);
		setSpritePos(2,xb,yb);
		Sleep(3);
	}

	board[x][y] = b;
	board[xx][yy] = a;

	showBoard(0);

	clearSprite(1);
	clearSprite(2);

	Sleep(4);
}

u8 doMove()
{
	u8 xx,yy,x=(cursx>>4),y=(cursy>>4);
	u16 v;
	switch(dir)
	{
		case J_LEFT:
			if (x==0)
				return 0;
			xx = x-1;
			yy = y;
			break;
		case J_UP:
			if (y==0)
				return 0;
			xx = x;
			yy = y-1;
			break;
		case J_DOWN:
			if (y==15)
				return 0;
			xx = x;
			yy = y+1;
			break;
		case J_RIGHT:
			if (x==15)
				return 0;
			xx = x+1;
			yy = y;
			break;
	}
	dir=0;
	swapGems(x,y,xx,yy);
	v = addNewGems();
	if (!v)
	{
		swapGems(x,y,xx,yy);
		SL_PlaySFX(17);
		showBoard(0);
		return 0;
	}
	if (!ttrial)
		left -= 1;
	if (top==128) // Level up
	{
		u8 count[7],i,j,k;
		SL_PlaySFX(16);
		print("   LEVEL UP !   ",3,17,0);
		Sleep(60);
		print("                ",3,17,0);
		for (i=0;i<7;i++)
			count[i] = 0;
		for (i=0;i<8;i++)
			for (j=0;j<8;j++)
				count[board[i][j]]+=1;
		k = 0;
		for (i=1;i<7;i++)
		{
			if (count[i]>count[k])
				k = i;
		}
		if (lvl<3)
			lvl += 1;
		score += 50*lvl;
		for (i=0;i<8;i++)
		{
			for (j=0;j<8;j++)
			{
				if (board[i][j]==k)
				{
					if ((i+j)%2)
						board[i][j] = NONE;
				}
			}
		}
		top = ttrial ? 32 : 0;
		addNewGems();
	}
	return v>0?1:0;
}

void drawBorders()
{
	u8 i,d = GEMS_LINE*2+1;
	setTile(SCROLL_PLANE_1,0,0,d);
	setTile(SCROLL_PLANE_1,1,0,d+1);
	setTile(SCROLL_PLANE_1,2,0,d+2);
	setTile(SCROLL_PLANE_1,19,0,d+7);
	for (i=0;i<17;i++)
	{
		setTile(SCROLL_PLANE_1,0,i+1,d+5);
		setTile(SCROLL_PLANE_1,2,i+1,d+5);
		setTile(SCROLL_PLANE_1,19,i+1,d+5);
		if (i<16)
		{
			setTile(SCROLL_PLANE_1,3+i,18,d+1);
			setTile(SCROLL_PLANE_1,3+i,16,d+1);
		}
	}
	setTile(SCROLL_PLANE_1,2,16,d+8);
	setTile(SCROLL_PLANE_1,19,16,d+9);
	setTile(SCROLL_PLANE_1,0,18,d+3);
	setTile(SCROLL_PLANE_1,2,18,d+6);
	setTile(SCROLL_PLANE_1,1,18,d+1);
	setTile(SCROLL_PLANE_1,19,18,d+4);
}

void swapLeft()
{
	print(" SWAPS LEFT:    ",3,17,0);
	printi(left,16,17,0,2);
}

void showHiscores()
{
	u8 i,d = GEMS_LINE*2+1,j;

	ClearScreen(SCR_1_PLANE);
	ClearScreen(SCR_2_PLANE);

	showImage8(GEMS_TILES,&SKULL2_IMG,CENTER,CENTER);

	SL_LoadGroup(magical1,sizeof(magical1));
	SL_PlayTune(0);

	loadGfx();
	setTile(SCROLL_PLANE_1,0,0,d);
	setTile(SCROLL_PLANE_1,19,0,d+2);
	setTile(SCROLL_PLANE_1,0,0+10,d);
	setTile(SCROLL_PLANE_1,19,0+10,d+2);

	setTile(SCROLL_PLANE_1,0,8,d+3);
	setTile(SCROLL_PLANE_1,19,8,d+4);
	setTile(SCROLL_PLANE_1,0,8+10,d+3);
	setTile(SCROLL_PLANE_1,19,8+10,d+4);

	for (i=1;i<19;i++)
	{
		setTile(SCROLL_PLANE_1,i,0,d+1);
		setTile(SCROLL_PLANE_1,i,2,d+1);
		setTile(SCROLL_PLANE_1,i,8,d+1);
		setTile(SCROLL_PLANE_1,i,0+10,d+1);
		setTile(SCROLL_PLANE_1,i,2+10,d+1);
		setTile(SCROLL_PLANE_1,i,8+10,d+1);
		if (i<8)
		{
			setTile(SCROLL_PLANE_1,0,i,d+5);
			setTile(SCROLL_PLANE_1,19,i,d+5);
			setTile(SCROLL_PLANE_1,0,i+10,d+5);
			setTile(SCROLL_PLANE_1,19,i+10,d+5);
		}
	}

	setTile(SCROLL_PLANE_1,0,2,d+8);
	setTile(SCROLL_PLANE_1,19,2,d+9);
	setTile(SCROLL_PLANE_1,0,2+10,d+8);
	setTile(SCROLL_PLANE_1,19,2+10,d+9);

	print("  ORIGINAL  GAME  ",1,1,0);
	print("    TIME TRIAL    ",1,11,0);

	for (i=0;i<10;i++)
	{
		print((char*)&data[4+i*6],4,(i>4?8:3)+i,0);
		printi(*((u16*)&data[4+i*6+4]),12,(i>4?8:3)+i,0,5);
	}

       	while((((JOYPAD)&J_A) == 0) && (((JOYPAD)&J_B) == 0));
       	while((((JOYPAD)&J_A) != 0) || (((JOYPAD)&J_B) != 0));
}

void play()
{
	u8 i,j,k,l,exists;

	SL_LoadGroup(magical2,sizeof(magical2));
	SL_PlayTune(0);

	loadGfx();
	showImage8(GEMS_TILES,&SKULL2_IMG,3,0);
	drawBorders();

	print("   HAVE FUN !   ",3,17,0);
	Sleep(60);

	lvl = 0;
	cpt = 1;
	curs = 1;
	top = ttrial ? 64 : 0;
	score = 0;
	left = 20;
	tcpt = ttrial ? 1 : 300;
	tcmd = 0;
	cursx = cursy = 64;
	inmove = ismoving = 0;

	initBoard();
	showBoard(0);

	setSprite(0,17,0,0);

	if (ttrial)
		showScore();
	else
		swapLeft();

	setCursorPosition(cursx,cursy);

	ingame = 1;

	while (exists=moveExists())
	{
		getMove();
		if (dir==NONE)	// exit from menu
			break;
		if (ttrial && top==0) // no time left in time trial : game over
			break;
		if (!ttrial && tcmd>7) // time out in limited game : game over
			break;
		i = doMove();
		if (ttrial)
			showScore();
		else
		{
			if (i)
			{
				tcpt = 300;
				tcmd = 0;
			}
			swapLeft();
			if (left==0) // no swap left in restricted mode : game over
				break;
		}
		exists = moveExists();
	}

	ingame = 0;

	clearSprite(0);

	SL_PlaySFX(17);

	if (dir==NONE)
		return;
	else if (!exists)
		print(" NO MOVE LEFT ! ",3,17,0);
	else if (ttrial)
	{
		print(" NO TIME LEFT ! ",3,17,0);
		for (i=0;i<5;i++)
			swapGems(psx0,psy0,psx1,psy1);
	}
	else
	{
		if (tcmd>7)
		{
			print("   TIME OUT !   ",3,17,0);
			for (i=0;i<5;i++)
				swapGems(psx0,psy0,psx1,psy1);
		}
		else
			print(" NO SWAP LEFT ! ",3,17,0);
	}
	Sleep(100);
	SL_PlaySFX(22);
	print("  GAME OVER  !  ",3,17,0);
	Sleep(100);
	showScore();
	Sleep(100);
	if (ttrial)
		i = 9;
	else
		i = 4;
	if (score>*((u16*)&data[4+i*6+4]))
	{
		char s[4] = {' ',' ',' ',0};
		print("ENTER NAME:     ",3,17,0);
		for (k=0;k<3;k++)
		{
			s[k] = 'A';
			while(((j=JOYPAD)&J_A) == 0)
			{
				print(s,15,17,0);
				if (j&J_UP || j&J_RIGHT)
				{
					if (s[k]<'Z')
						s[k]+=1;
					else
						s[k] = 'A';
				}
				else if (j&J_DOWN || j&J_LEFT)
				{
					if (s[k]>'A')
						s[k]-=1;
					else
						s[k] = 'Z';
				}
				if (j)
					Sleep(10);
			}
			while(JOYPAD&J_A);
		}
		for (k=i-4;k<i;k++)
		{
			if (score>*((u16*)&data[4+k*6+4]))
				break;
		}
		k = 4+k*6;
		for (l=4+i*6-1;l>=k;l--)
			data[l+6] = data[l];
		*((u16*)&data[k+4]) = score;
		data[k] = s[0];
		data[k+1] = s[1];
		data[k+2] = s[2];
		data[k+3] = 0;
		flash(data);
		showHiscores();
	}
}

void loadSpr(u16 offset)
{
	u16 i;
	u16 *p0 = TILE_RAM+(offset*8),*p1 = SPRITE_PALETTE;
	for (i=0;i<TITLE_TILES*8;i++)
		p0[i] = title_tiles[i];
	for (i=0;i<TITLE_PALS*4;i++)
		p1[i] = title_pals[i];
}

const u8 idx[] = {110,124,138};

u8 mainMenu()
{
	u8 i,j,k,cptk,cptl,l;
	s8 dl;

	SL_LoadGroup(magical0,sizeof(magical0));
	SL_PlayTune(0);

	loadSpr(420);
	showHC((void*)MENU_ID);

	SetSprite(0,420,0,64,idx[k],title_palidx[0],TOP_PRIO);
	k=0;
	while((((j=JOYPAD)&J_A) == 0) && ((j&J_B) == 0))
	{
		if ((j&J_DOWN)||(j&J_LEFT))
			k = k < 2 ? k+1: 0;
		else if ((j&J_UP)||(j&J_RIGHT))
			k = k > 0 ? k-1: 2;
		SetSpritePosition(0,64,idx[k]);
		if (j)
			Sleep(10);
       	}
	SeedRandom();
	SL_PlaySFX(1);
	Sleep(30);
       	while((((JOYPAD)&J_A) != 0) || (((JOYPAD)&J_B) != 0));

	for (i=0;i<64;i++)
		SetSprite(i,0,0,255,0,0,TOP_PRIO);

	curimg = 0;

	ClearScreen(SCR_1_PLANE);
	ClearScreen(SCR_2_PLANE);
	return k;
}

void getData()
{
	u8 i;
	getSavedData();
	if (data[4]>0)
		return;
	for (i=0;i<10;i++)
	{
		data[4+i*6]='C';
		data[4+i*6+1]='O';
		data[4+i*6+2]='M';
		data[4+i*6+3]=0;
		*((u16*)&data[4+i*6+4]) = 900-(((u16)(i<5?i:i-5))*100);
	}
}

void main()
{
	InitNGPC();
	ClearScreen(SCR_1_PLANE);
	ClearScreen(SCR_2_PLANE);
	SetBackgroundColour(RGB(0, 0, 0));

	hc_detect();

	ingame = 0;
	curimg = 0;

	__asm(" di");
	VBL_INT = myVBL;
	__asm(" ei");

	getData();
	SL_SoundInit();

	while(1)
	{
		u8 i = mainMenu();
		switch(i)
		{
			case 0:
				ttrial=0;
				play();
				break;
			case 1:
				ttrial=1;
				play();
				break;
			case 2:
				showHiscores();
				break;
		}
		ingame = 0;
	}
}
