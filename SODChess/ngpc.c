#define SODCHESS_MAIN

#include "stdio.h"
#include "sodchess.h"
#include "chessfont.h"

#define NEWGAME	1
#define REVERSE	2

struct _board _curboard;
Board curboard = &_curboard;

s8 square = DEAD;
u8 flip = 0;
u8 curcolor = WHITE;
s8 lastsrc = DEAD,lastdst;
u8 counter = 0;
u8 prgmax =0;
u8 prgcur = 0;
u8 prgcnt = 0;

void sleep(u16 ms)
{
	counter = ms/20;
	while (counter>0)
		__asm(" nop");
}

void drawBack()
{
	u16 i,j,l,k,m;
	u16 *scr2 = (u16*)_SCR2RAM;

	l = 34;
	k = 41;
	m = 56;
	for (i=0;i<8;i++)
	{
		for (j=0;j<8;j++)
		{
			u16 pal = (u16)((m+j==(flip?63-cursrc:cursrc)?3:(m+j==(flip?63-square:square)?4:pal_idx[k]-1)));
			if ((pal<3)&&(lastsrc!=DEAD))
				pal+=(m+j==(flip?63-lastsrc:lastsrc))||(m+j==(flip?63-lastdst:lastdst))?5:0; // highlight
			pal <<= 9;
			scr2[l] = pal+k;
			scr2[l+1] = pal+k+1;
			scr2[l+32] = pal+k+14;
			scr2[l+32+1] = pal+k+15;
			l+=2;
			k=(k==13?41:13);
		}
		l+=48;
		k=(k==13?41:13);
		m-=8;
	}
}

const pals[4][4] =
	{
	{0x0, 0xf00, 0xf00, 0xf00}, 
	{0x0, 0x00f, 0x00f, 0x00f},
	{0x0, 0xf0,  0x0ff, 0xfc4},
	{0x0, 0xf0,  0x0ff, 0xffc} 
	};

void initGfx()
{
	u16 *tileram = (u16*)_TILERAM;
	u16 *scr1pal = (u16*)_SCR1PAL;
	u16 *scr2pal = (u16*)_SCR2PAL;
	u16 *img = (u16*)PLAN_1;
	u16 *scr1 = (u16*)_SCR1RAM;
	u16 *scr2 = (u16*)_SCR2RAM;
	u16 i,j;

	for (i=0;i<8;i++)
		*tileram++ = 0; // empty tile
	for (i=0;i<IMG_S*8;i++)
		*tileram++ = *img++;
	for (i=0;i<1024;i++)
	{
		*scr1++=0;
		*scr2++=0;
	}
	for (i=0;i<4;i++)
		*scr1pal++ = pals_plan1[0][i];

	for (i=1;i<4;i++)
		for (j=0;j<4;j++)
			*scr2pal++ = pals_plan1[i][j];
	for (i=0;i<4;i++)
		for (j=0;j<4;j++)
			*scr2pal++ = pals[i][j];

	drawBack();
}


const u8 display[32] =
{
	10,10,10,10,10,10,10,10, 4, 0, 2, 6, 8, 2, 0, 4, // white
	38,38,38,38,38,38,38,38,32,28,30,34,36,30,28,32	 // black
};

void showBoard()
{
	// draw pieces
	u16 *scr1 = (u16*)_SCR1RAM;
	s8 i,j,k=56;
	u16 l = 34;
	// draw background
	drawBack();
	for (i=0;i<8;i++)
	{
		for (j=0;j<8;j++)
		{
			s8 p = curboard->board[flip?63-k-j:k+j];
			if (p!=DEAD)
			{
				u8 n = display[p]+1;
				u16 pal = ((u16)pal_idx[n-1])<<9;
				scr1[l] = pal+n;
				scr1[l+1] = pal+n+1;
				scr1[l+32] = pal+n+14;
				scr1[l+32+1] = pal+n+15;
			}
			else
			{
				scr1[l] = 0;
				scr1[l+1] = 0;
				scr1[l+32] = 0;
				scr1[l+32+1] = 0;
			}
			l+=2;
		}
		l+=48;
		k-=8;
	}
}

u8 _getMove()
{
	u8 a,*sl = (u8*)rSL,x=(flip?63-square:square)%8,y=(flip?63-square:square)/8,ret;
	drawBack();
	while (((a=*sl)&PADF_A)==0 && (a&PADF_OPTION)==0)
	{
		if (a&PADF_UP && y<7)
			y++;
		if (a&PADF_DOWN && y>0)
			y--;
		if (a&PADF_LEFT && x>0)
			x--;
		if (a&PADF_RIGHT && x<7)
			x++;
		if (a&PADF_B)
		{
			flip=!flip;
			if (nbmoves==0&&cursrc==DEAD)
			{
				sleep(100);
				curcolor=BLACK;
				return REVERSE;
			}
			x=7-x;
			y=7-y;
		}
		if (a)
		{
			square=flip?63-POS(y,x):POS(y,x);
			showBoard();
			if (a&PADF_B)
				sleep(300);
			else
				sleep(100);
		}
	}
	ret = (a&PADF_OPTION)!=0 ? NEWGAME : 0;
	drawBack();
	while (((a=*sl)&PADF_A)!=0 || (a&PADF_OPTION)!=0)
		sleep(20);
	return ret;
}

int getMove()
{
	u8 ret;
	square = 27;
	curdst = DEAD;
	while (1)
	{
		cursrc = DEAD;
		ret=_getMove();
		if (ret==NEWGAME)
			return ret;
		if (ret==REVERSE&&nbmoves==0)
		{
			curcolor==BLACK;
			return ret;
		}
		if (curboard->board[square]==DEAD || COLOR(curboard->board[square])!=curcolor)
			continue;
		cursrc = square;
		if (_getMove()==NEWGAME)
			return NEWGAME;
		if (square==cursrc)
			continue;
		if (verify(curboard,cursrc,square))
			continue;
		copy(bboards[0],curboard);
		play(cursrc,square,bboards[0],0);
		if (!isCheck(bboards[0],curcolor))
			break;
	}
	curdst = square;
	square = DEAD;
	return 0;
}

void rect()
{
	u16 *scr1 = (u16*)_SCR1RAM;
	u16 i,pal=((u16)pal_idx[58])<<9;
	scr1[1]=59;
	scr1[18]=61;
	scr1[1+17*32]=62;
	scr1[18+17*32]=64;
	for (i=0;i<16;i++)
	{
		scr1[i+2+17*32] = 63;
		scr1[i+2] = 60;
		scr1[18+32*(i+1)] = 66;
		scr1[1+32*(i+1)] = 65;
	}
}

void progress(s8 max,s8 cur)
{
	prgmax = max;
	prgcur = cur;
}

void showProgress()
{
	u16 *scr2 = (u16*)_SCR2RAM,l=18*32+1;
	u8 i,k=prgcnt>20?1:0;
	prgcnt = (prgcnt+1)%40;
	for (i=0;i<prgmax;i++)
	{
		u8 tile = i>=prgcur+k?57:56;
		u16 pal = ((u16)(pal_idx[tile]-1))<<9;
		scr2[l+i] = pal+tile+1;
	}
}

void __interrupt myVBL()
{
	WATCHDOG = WATCHDOG_CLEAR;
	if (USR_SHUTDOWN) { SysShutdown(); while (1); }
	if (counter>0)
		counter--;
	showProgress();
}

void clearProgress()
{
	u16 *scr2 = (u16*)_SCR2RAM,l=18*32+1;
	u8 i;
	prgmax = 0;
	for (i=0;i<MAXKEEP;i++)
		scr2[l+i] = 0;
}

void userAction() // called from fct compute
{
	u8 a,*sl = (u8*)rSL;
	if ((a=*sl)&PADF_B)
	{
		s8 svsrc = cursrc;
		flip=!flip;
		cursrc = DEAD; // do not show blue square
		showBoard();
		cursrc = svsrc;
	}
	else if (a&PADF_OPTION)
		reset = 1;
	while (((a=*sl)&PADF_B)!=0 || (a&PADF_OPTION)!=0)
		sleep(20);
}

void resetGame()
{
	initSearch();
	init(curboard);
	flip = 0;
	reset = 0;
	curcolor = WHITE;
	lastsrc = lastdst = DEAD;
}

extern unsigned char eatdot[]; // Thanks to Dark Fader
extern unsigned char rsonic[];

void main()
{
	*((u8*)rWBAX) = 0;
	*((u8*)rWBAY) = 0;
	*((u8*)rWSIX) = 160;
	*((u8*)rWSIY) = 152;
	*((u16*)_BGCPAL) = 0;

	SysPlayWave(rsonic);

	__asm(" di");
	VBL_INT = myVBL;
	__asm(" ei");		// Enable interrupts

	initGfx();
	rect();
	initrand();
	resetGame();
	while (1)
	{
		showBoard();
		if (curcolor==WHITE)
		{
			if (getMove()==NEWGAME)
			{
				resetGame();
				continue;
			}
			if (curcolor==BLACK) // flip at the beginning
				continue;
			play(cursrc,curdst,curboard,1);
			lastsrc = cursrc;
			lastdst = curdst;
			nbmoves++;
			cursrc=DEAD;
			showBoard();
		}
		compute(INV(curcolor),DEPTH-1,curboard,-MAX,MAX);
		clearProgress();
		SysPlayWave(eatdot);
		if (cursrc!=DEAD&&(!reset))
		{
			play(cursrc,curdst,curboard,1);
			lastsrc = cursrc;
			lastdst = curdst;
			nbmoves++;
		}
		else
		{
			resetGame();
			continue;
		}
		if (curcolor==BLACK)
		{
			showBoard();
			if (getMove()==NEWGAME)
			{
				resetGame();
				continue;
			}
			play(cursrc,curdst,curboard,1);
			lastsrc = cursrc;
			lastdst = curdst;
			cursrc=DEAD;
		}
	}
}
