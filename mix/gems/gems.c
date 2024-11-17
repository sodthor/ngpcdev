#include "ngpc.h"
#include "library.h"
#include "hicolor.h"
#include "flash.h"
#include "music.h"
#include "img.h"

#include "gamedata.h"

#include "magical0.mh"
#include "magical1.mh"
#include "magical2.mh"
#include "gems/gems_menu.hh"
#include "gems/gems.h"
#include "gems/title.h"
#include "gems/back.hh"

#define NONE 0xff
#define RESERVED 0xfe
#define MOVE 0x80
#define MOVE_MASK 0x7f
#define MAX_MOVE 15

extern volatile u8 VBCounter;

extern GAME_DATA DATA;

const u8 gems_lvl_values[] = {5,6,7,8};

void gems_setTile(u16* plane,u16 x,u16 y,u16 tile)
{
    plane[y*32+x] = tile+(((u16)gems_palidx[tile-1])<<9);
}

void gems_print(const char *s,u16 x,u16 y,u8 dir)
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
        gems_setTile(SCROLL_PLANE_1,x+(dir?0:i),y+(dir?i:0),c+1);
    }
}

void gems_printl(const char *s,u16 x,u16 y,u8 dir,u8 l)
{
    char buf[32];
    u8 i;
    for (i=0;s[i];i++)
        buf[i]=s[i];
    while (i<l)
        buf[i++]=' ';
    buf[i]=0;
    gems_print(buf,x,y,dir);
}

void gems_printi(u16 i,u16 x,u16 y,u8 dir,u8 l)
{
    char buf[9];
    u8 j = 9;
    buf[j]=0;
    do
    {
        buf[--j]='0'+(i%10);
        i /= 10;
    } while (i>0);
    gems_printl(buf+j,x,y,dir,l);
}

void gems_showScore()
{
    gems_print("   SCORE:       ",3,17,0);
    gems_printi(DATA.data.gems.score,13,17,0,4);
}

void gems_hurryUp()
{
    gems_print("   HURRY UP !   ",3,17,0);
    SL_PlaySFX(11);
}

void gems_showCountDown()
{
    gems_print("                ",3,17,0);
    gems_printi(7 - DATA.data.gems.tcmd,11,17,0,1);
    SL_PlaySFX(12);
}

void gems_setSpritePos(u8 spr,u8 x,u8 y)
{
    spr*=4;
    SetSpritePosition(spr,x,y);
    SetSpritePosition(spr+1,x+8,y);
    SetSpritePosition(spr+2,x,y+8);
    SetSpritePosition(spr+3,x+8,y+8);
}

void gems_setCursorPosition(u8 x,u8 y)
{
    gems_setSpritePos(0,x+24,y);
}

void __interrupt gems_myVBL()
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
    if (DATA.ingame)
    {
        u8 *p = SPRITE_RAM;
        p[0] = 17+DATA.curs*2;
        p[4] = 18+DATA.curs*2;
        p[8] = 17+GEMS_LINE+DATA.curs*2;
        p[12]= 18+GEMS_LINE+DATA.curs*2;
        if (--DATA.cpt==0)
        {
            if (DATA.data.gems.inmove)
                DATA.curs = DATA.curs==3 ? 0 : DATA.curs+1;
            else
                DATA.curs = DATA.curs==0 ? 3 : DATA.curs-1;
            DATA.cpt = 7;
        }
        if (DATA.data.gems.ismoving)
        {
            DATA.data.gems.cursx+=DATA.data.gems.cdx;
            DATA.data.gems.cursy+=DATA.data.gems.cdy;
            gems_setCursorPosition(DATA.data.gems.cursx,DATA.data.gems.cursy);
            if (DATA.data.gems.cursx==DATA.data.gems.destx && DATA.data.gems.cursy==DATA.data.gems.desty)
                DATA.data.gems.ismoving = 0;
        }
        else
        {
            u8 j =JOYPAD;
            if (DATA.data.gems.inmove && (j&J_A || j&J_B))
            {
                if ((j&J_LEFT) && (DATA.data.gems.cursx>0))
                    DATA.data.gems.dir = J_LEFT;
                else if ((j&J_RIGHT) && (DATA.data.gems.cursx<112))
                    DATA.data.gems.dir = J_RIGHT;
                else if ((j&J_UP) && (DATA.data.gems.cursy>0))
                    DATA.data.gems.dir = J_UP;
                else if ((j&J_DOWN) && (DATA.data.gems.cursy<112))
                    DATA.data.gems.dir = J_DOWN;
                if (DATA.data.gems.dir!=0)
                    DATA.data.gems.inmove = 0;
            }
            else if (DATA.data.gems.dir==0)
            {
                DATA.data.gems.destx=DATA.data.gems.cursx;
                DATA.data.gems.desty=DATA.data.gems.cursy;
                if (j&J_LEFT)
                    DATA.data.gems.destx = DATA.data.gems.destx>0 ? DATA.data.gems.destx - 16 : 0;
                if (j&J_RIGHT)
                    DATA.data.gems.destx = DATA.data.gems.destx<112 ? DATA.data.gems.destx + 16 : 112;
                if (j&J_UP)
                    DATA.data.gems.desty = DATA.data.gems.desty>0 ? DATA.data.gems.desty - 16 : 0;
                if (j&J_DOWN)
                    DATA.data.gems.desty = DATA.data.gems.desty<112 ? DATA.data.gems.desty + 16 : 112;
                if (DATA.data.gems.destx!=DATA.data.gems.cursx||DATA.data.gems.desty!=DATA.data.gems.cursy)
                {
                    DATA.data.gems.cdx = (DATA.data.gems.destx>DATA.data.gems.cursx)?1:(DATA.data.gems.destx<DATA.data.gems.cursx?-1:0);
                    DATA.data.gems.cdy = (DATA.data.gems.desty>DATA.data.gems.cursy)?1:(DATA.data.gems.desty<DATA.data.gems.cursy?-1:0);
                    DATA.data.gems.ismoving=1;
                }
            }
        }
        if (DATA.data.gems.innew)
            return;
        if (DATA.data.gems.ttrial)
        {
            if (--DATA.data.gems.tcpt==0)
            {
                DATA.data.gems.tcpt = 16+((4-DATA.data.gems.lvl)*4)-(DATA.data.gems.top<32?8:0);
                if (DATA.data.gems.top>0&&DATA.data.gems.top<128)
                    DATA.data.gems.top-=1;
            }
        }
        else
        {
            if (--DATA.data.gems.tcpt==0)
            {
                switch(DATA.data.gems.tcmd)
                {
                    case 0:
                        gems_showScore();
                        DATA.data.gems.tcpt = 500;
                        break;
                    case 1:
                        gems_hurryUp();
                        DATA.data.gems.tcpt = 100;
                        break;
                    default:
                        if (DATA.data.gems.tcmd<8)
                            gems_showCountDown();
                        DATA.data.gems.tcpt = 60;
                        break;
                }
                DATA.data.gems.tcmd+=1;
            }
        }
    }
}

void gems_showHC(void *img)
{
    DATA.curimg=0;
    hc_load(img);
    DATA.curimg=img;
}

void gems_loadGfx()
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

void gems_initBoard()
{
    u8 i,j,k;
    for (i=0;i<8;i++)
    {
        for (j=0;j<8;j++)
        {
            while (1)
            {
                k = GetRandom(gems_lvl_values[DATA.data.gems.lvl]);
                if (!((i>0 && k==DATA.data.gems.board[i-1][j]) || (j>0 && k==DATA.data.gems.board[i][j-1])))
                    break;
            }
            DATA.data.gems.board[i][j] = k;
        }
    }
}

void gems_showBar()
{
    u8 i,j,k;
    k = GEMS_LINE*2+11;
    j = DATA.data.gems.top/8;
    for (i=0;i<j;i++)
        gems_setTile(SCROLL_PLANE_1,1,17-i,k);
    gems_setTile(SCROLL_PLANE_1,1,17-j,k+DATA.data.gems.top%8+1);
    for (i=1;i<17-j;i++)
        gems_setTile(SCROLL_PLANE_1,1,i,0);
}

void gems_clearTile16(u16 i,u16 j,u16 r)
{
    gems_setTile(SCROLL_PLANE_1,i*2+3,j*2,r);
    gems_setTile(SCROLL_PLANE_1,i*2+4,j*2,r);
    gems_setTile(SCROLL_PLANE_1,i*2+3,j*2+1,r);
    gems_setTile(SCROLL_PLANE_1,i*2+4,j*2+1,r);
}

void gems_setTile16(u16 i,u16 j,u16 k)
{
    gems_setTile(SCROLL_PLANE_1,i*2+3,j*2,k);
    gems_setTile(SCROLL_PLANE_1,i*2+4,j*2,k+1);
    gems_setTile(SCROLL_PLANE_1,i*2+3,j*2+1,k+GEMS_LINE);
    gems_setTile(SCROLL_PLANE_1,i*2+4,j*2+1,k+GEMS_LINE+1);
}

void gems_showBoard(u16 r)
{
    u16 i,j,k;
    r = r>0 ? GEMS_LINE - r + 1 : 0;
    for (i=0;i<8;i++)
    {
        for (j=0;j<8;j++)
        {
            if (DATA.data.gems.board[i][j]==NONE||DATA.data.gems.board[i][j]==RESERVED)
                gems_clearTile16(i,j,r);
            else
                gems_setTile16(i,j,(DATA.data.gems.board[i][j]&MOVE_MASK)*2+1);
        }
    }
    gems_showBar();
}

void gems_hideBoard()
{
    u16 i,j;
    for (i=0;i<8;i++)
    {
        for (j=0;j<8;j++)
            gems_clearTile16(i,j,0);
    }
}

void gems_getMove()
{
    u8 i,j;

    DATA.data.gems.dir = 0;
    DATA.data.gems.inmove = 1;
    while(DATA.data.gems.inmove)
    {
        if (DATA.data.gems.ttrial)
        {
            if (DATA.data.gems.top==0)    // time elapsed !
                break;
            gems_showBar();
        }
        else
        {
            if (DATA.data.gems.tcmd>7)    // time out !
                break;
        }
        j = JOYPAD;
        if (j&J_OPTION)
        {
            DATA.ingame = 0;
            gems_print("A:RESUME  B:EXIT",3,17,0);
            gems_hideBoard();
            while(1)
            {
                j=JOYPAD;
                if (j&J_A||j&J_B)
                    break;
            }
            while (i=JOYPAD);
            gems_showScore();
            gems_showBoard(0);
            DATA.ingame = 1;
            if (j&J_B)
            {
                DATA.data.gems.dir = NONE;
                break;
            }
        }
    }
    DATA.data.gems.inmove = 0;
    while (j=JOYPAD);
}

u8 gems_findBlock()
{
    u8 i,j,k;

    for (i=0;i<8;i++)
    {
        for (j=0;j<8;j++)
        {
            for (k=i+1;k<8&&DATA.data.gems.board[k][j]==DATA.data.gems.board[i][j];k++);
            if (k-i>2)
                return 1;
            for (k=j+1;k<8&&DATA.data.gems.board[i][k]==DATA.data.gems.board[i][j];k++);
            if (k-j>2)
                return 1;
        }
    }
    return 0;
}

#define swap(a,b) { u8 tmp = a; a = b; b = tmp; }

u8 gems_moveExists()
{
    u8 i,j,a,b;

    for (i=0;i<8;i++)
    {
        for (j=0;j<7;j++)
        {
            if (i<7)
            {
                swap(DATA.data.gems.board[i][j],DATA.data.gems.board[i+1][j]);
                b = gems_findBlock();
                swap(DATA.data.gems.board[i][j],DATA.data.gems.board[i+1][j]);
                if (b)
                {
                    DATA.data.gems.psx0 = i;
                    DATA.data.gems.psx1 = i+1;
                    DATA.data.gems.psy0 = DATA.data.gems.psy1 = j;
                    return 1;
                }
            }
            swap(DATA.data.gems.board[i][j],DATA.data.gems.board[i][j+1]);
            b = gems_findBlock();
            swap(DATA.data.gems.board[i][j],DATA.data.gems.board[i][j+1]);
            if (b)
            {
                DATA.data.gems.psx0 = DATA.data.gems.psx1 = i;
                DATA.data.gems.psy0 = j;
                DATA.data.gems.psy1 = j+1;
                return 1;
            }
        }
    }
    return 0;
}

u8 gems_removeGems()
{
    u8 i,j,k,l,n=0;
    u8 remove[8][8];
    for (i=0;i<8;i++)
    {
        for (j=0;j<8;j++)
            remove[i][j] = (DATA.data.gems.board[i][j] == NONE ? 1 : 0);
    }
    for (i=0;i<8;i++)
    {
        for (j=0;j<8;j++)
        {
            for (k=i+1;k<8&&DATA.data.gems.board[k][j]==DATA.data.gems.board[i][j];k++);
            if (k-i>2)
            {
                for(l=i;l<k;l++)
                    remove[l][j] = 1;
            }
            for (k=j+1;k<8&&DATA.data.gems.board[i][k]==DATA.data.gems.board[i][j];k++);
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
                DATA.data.gems.board[i][j] = NONE;
                n+=1;
            }
        }
    }
    return n;
}

void gems_clearSprite(u8 idx)
{
    idx<<=2;
    SetSprite(idx,0,0,0,0,gems_palidx[0],TOP_PRIO);
    SetSprite(idx+1,0,0,8,0,gems_palidx[0],TOP_PRIO);
    SetSprite(idx+2,0,0,0,8,gems_palidx[0],TOP_PRIO);
    SetSprite(idx+3,0,0,8,8,gems_palidx[0],TOP_PRIO);
}

void gems_setSprite(u8 idx,u8 num,u8 x,u8 y)
{
    idx<<=2;
    SetSprite(idx,num,0,x,y,gems_palidx[num-1],TOP_PRIO);
    SetSprite(idx+1,num+1,0,x+8,y,gems_palidx[num],TOP_PRIO);
    SetSprite(idx+2,num+GEMS_LINE,0,x,y+8,gems_palidx[num+GEMS_LINE-1],TOP_PRIO);
    SetSprite(idx+3,num+1+GEMS_LINE,0,x+8,y+8,gems_palidx[num+1+GEMS_LINE-1],TOP_PRIO);
}

u16 gems_addNewGems()
{
    u16 v = 0;
    u8 rv,i,j,k,l,n,nb = 0;
    u8 moves[64][3]; // num,x,y
    u8 spr[MAX_MOVE][4]; // num,x,ysrc,ydst

    DATA.data.gems.innew = 1;
    gems_showBoard(0);
    while (rv = gems_removeGems())
    {
        SL_PlaySFX(1);
        for (j=4;j!=255;j--)
        {
            gems_showBoard(j);
            Sleep(4);
        }
        v+=rv;
        nb+=1;
        DATA.data.gems.top+=rv*nb;
        DATA.data.gems.score+=rv*(DATA.data.gems.lvl+1);
        if (DATA.data.gems.top>128) // level up !
            DATA.data.gems.top = 128;
        if (nb>1)
        {
            gems_print("   CHAIN COMBO !",3,17,0);
            gems_printi(nb,4,17,0,2);
            if (!DATA.data.gems.ttrial)
                DATA.data.gems.left += 1;
        }
        k = 0;
        for (i=0;i<8;i++)
        {
            l = 0;
            for (j=0;j<8;j++) // gems to move
            {
                if (DATA.data.gems.board[i][j]==NONE)
                    l+=1;
                if ((j>0) && (DATA.data.gems.board[i][7-j]!=NONE) && (DATA.data.gems.board[i][7-j+1]&MOVE))
                {
                    moves[k][0] = DATA.data.gems.board[i][7-j];
                    moves[k][1] = i;
                    moves[k][2] = 7-j;
                    DATA.data.gems.board[i][7-j] |= MOVE;
                    k += 1;
                }
            }
            for (j=0;j<l;j++) // new gems
            {
                moves[k][0] = GetRandom(gems_lvl_values[DATA.data.gems.lvl]);
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
                        DATA.data.gems.board[moves[i][1]][j] = NONE;
                        gems_clearTile16(moves[i][1],j,0);
                        spr[l][2] = j<<4;
                    }
                    spr[l][0] = moves[i][0];
                    spr[l][1] = (moves[i][1]<<4)+24;
                    for (;j<8 && DATA.data.gems.board[moves[i][1]][j] == NONE;j++);
                    spr[l][3] = (j-1)<<4;
                    DATA.data.gems.board[moves[i][1]][j-1] = RESERVED;
                    gems_setSprite(l+1,(spr[l][0]*2)+1,spr[l][1],spr[l][2]);
                    i+=1;
                    break;
                }
                else
                {
                    spr[l][2]+=4;
                    gems_setSpritePos(l+1,spr[l][1],spr[l][2]);
                    if (spr[l][2]==spr[l][3])
                    {
                        u8 a = (spr[l][1]-24)>>4, b = spr[l][3]>>4;
                        gems_clearSprite(l+1);
                        DATA.data.gems.board[a][b] = spr[l][0];
                        gems_setTile16(a,b,spr[l][0]*2+1);
                        spr[l][0] = NONE;
                    }
                }
            }
            Sleep(1);
        }
    }
    DATA.data.gems.innew = 0;
    return v;
}

void gems_swapGems(u8 x,u8 y,u8 xx,u8 yy)
{
    u8 a = DATA.data.gems.board[x][y],
       b = DATA.data.gems.board[xx][yy],
       xa,ya,xb,yb,x0,y0;
    s8 dx=0,dy=0;

    DATA.data.gems.board[x][y] = DATA.data.gems.board[xx][yy] = NONE;

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

    gems_setSprite(1,a*2+1,xa,ya);
    gems_setSprite(2,b*2+1,xb,yb);

    gems_showBoard(0);

    while (x0!=xb||y0!=yb)
    {
        xa+=dx;
        xb-=dx;
        ya+=dy;
        yb-=dy;
        gems_setSpritePos(1,xa,ya);
        gems_setSpritePos(2,xb,yb);
        Sleep(3);
    }

    DATA.data.gems.board[x][y] = b;
    DATA.data.gems.board[xx][yy] = a;

    gems_showBoard(0);

    gems_clearSprite(1);
    gems_clearSprite(2);

    Sleep(4);
}

u8 gems_doMove()
{
    u8 xx,yy,x=(DATA.data.gems.cursx>>4),y=(DATA.data.gems.cursy>>4);
    u16 v;
    switch(DATA.data.gems.dir)
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
    DATA.data.gems.dir=0;
    gems_swapGems(x,y,xx,yy);
    v = gems_addNewGems();
    if (!v)
    {
        gems_swapGems(x,y,xx,yy);
        SL_PlaySFX(17);
        gems_showBoard(0);
        return 0;
    }
    if (!DATA.data.gems.ttrial)
        DATA.data.gems.left -= 1;
    if (DATA.data.gems.top==128) // Level up
    {
        u8 count[7],i,j,k;
        SL_PlaySFX(16);
        gems_print("   LEVEL UP !   ",3,17,0);
        Sleep(60);
        gems_print("                ",3,17,0);
        for (i=0;i<7;i++)
            count[i] = 0;
        for (i=0;i<8;i++)
            for (j=0;j<8;j++)
                count[DATA.data.gems.board[i][j]]+=1;
        k = 0;
        for (i=1;i<7;i++)
        {
            if (count[i]>count[k])
                k = i;
        }
        if (DATA.data.gems.lvl<3)
            DATA.data.gems.lvl += 1;
        DATA.data.gems.score += 50*DATA.data.gems.lvl;
        for (i=0;i<8;i++)
        {
            for (j=0;j<8;j++)
            {
                if (DATA.data.gems.board[i][j]==k)
                {
                    if ((i+j)%2)
                        DATA.data.gems.board[i][j] = NONE;
                }
            }
        }
        DATA.data.gems.top = DATA.data.gems.ttrial ? 32 : 0;
        gems_addNewGems();
    }
    return v>0?1:0;
}

void gems_drawBorders()
{
    u8 i,d = GEMS_LINE*2+1;
    gems_setTile(SCROLL_PLANE_1,0,0,d);
    gems_setTile(SCROLL_PLANE_1,1,0,d+1);
    gems_setTile(SCROLL_PLANE_1,2,0,d+2);
    gems_setTile(SCROLL_PLANE_1,19,0,d+7);
    for (i=0;i<17;i++)
    {
        gems_setTile(SCROLL_PLANE_1,0,i+1,d+5);
        gems_setTile(SCROLL_PLANE_1,2,i+1,d+5);
        gems_setTile(SCROLL_PLANE_1,19,i+1,d+5);
        if (i<16)
        {
            gems_setTile(SCROLL_PLANE_1,3+i,18,d+1);
            gems_setTile(SCROLL_PLANE_1,3+i,16,d+1);
        }
    }
    gems_setTile(SCROLL_PLANE_1,2,16,d+8);
    gems_setTile(SCROLL_PLANE_1,19,16,d+9);
    gems_setTile(SCROLL_PLANE_1,0,18,d+3);
    gems_setTile(SCROLL_PLANE_1,2,18,d+6);
    gems_setTile(SCROLL_PLANE_1,1,18,d+1);
    gems_setTile(SCROLL_PLANE_1,19,18,d+4);
}

void gems_swapLeft()
{
    gems_print(" SWAPS LEFT:    ",3,17,0);
    gems_printi(DATA.data.gems.left,16,17,0,2);
}

void gems_showHiscores()
{
    u8 i,d = GEMS_LINE*2+1,j;

    ClearScreen(SCR_1_PLANE);
    ClearScreen(SCR_2_PLANE);

    showImage8(GEMS_TILES,&SKULL2_IMG,CENTER,CENTER);

    SL_LoadGroup(magical1,sizeof(magical1));
    SL_PlayTune(0);

    gems_loadGfx();
    gems_setTile(SCROLL_PLANE_1,0,0,d);
    gems_setTile(SCROLL_PLANE_1,19,0,d+2);
    gems_setTile(SCROLL_PLANE_1,0,0+10,d);
    gems_setTile(SCROLL_PLANE_1,19,0+10,d+2);

    gems_setTile(SCROLL_PLANE_1,0,8,d+3);
    gems_setTile(SCROLL_PLANE_1,19,8,d+4);
    gems_setTile(SCROLL_PLANE_1,0,8+10,d+3);
    gems_setTile(SCROLL_PLANE_1,19,8+10,d+4);

    for (i=1;i<19;i++)
    {
        gems_setTile(SCROLL_PLANE_1,i,0,d+1);
        gems_setTile(SCROLL_PLANE_1,i,2,d+1);
        gems_setTile(SCROLL_PLANE_1,i,8,d+1);
        gems_setTile(SCROLL_PLANE_1,i,0+10,d+1);
        gems_setTile(SCROLL_PLANE_1,i,2+10,d+1);
        gems_setTile(SCROLL_PLANE_1,i,8+10,d+1);
        if (i<8)
        {
            gems_setTile(SCROLL_PLANE_1,0,i,d+5);
            gems_setTile(SCROLL_PLANE_1,19,i,d+5);
            gems_setTile(SCROLL_PLANE_1,0,i+10,d+5);
            gems_setTile(SCROLL_PLANE_1,19,i+10,d+5);
        }
    }

    gems_setTile(SCROLL_PLANE_1,0,2,d+8);
    gems_setTile(SCROLL_PLANE_1,19,2,d+9);
    gems_setTile(SCROLL_PLANE_1,0,2+10,d+8);
    gems_setTile(SCROLL_PLANE_1,19,2+10,d+9);

    gems_print("  ORIGINAL  GAME  ",1,1,0);
    gems_print("    TIME TRIAL    ",1,11,0);

    for (i=0;i<10;i++)
    {
        gems_print((char*)&data[GEMS_SAVED_OFFSET+i*6],4,(i>4?8:3)+i,0);
        gems_printi(*((u16*)&data[GEMS_SAVED_OFFSET+i*6+4]),12,(i>4?8:3)+i,0,5);
    }

    while((((JOYPAD)&J_A) == 0) && (((JOYPAD)&J_B) == 0));
    while((((JOYPAD)&J_A) != 0) || (((JOYPAD)&J_B) != 0));
}

void gems_play()
{
    u8 i,j,k,l,exists;

    SL_LoadGroup(magical2,sizeof(magical2));
    SL_PlayTune(0);

    gems_loadGfx();
    showImage8(GEMS_TILES,&SKULL2_IMG,3,0);
    gems_drawBorders();

    gems_print("   HAVE FUN !   ",3,17,0);
    Sleep(60);

    DATA.data.gems.lvl = 0;
    DATA.cpt = 1;
    DATA.curs = 1;
    DATA.data.gems.top = DATA.data.gems.ttrial ? 64 : 0;
    DATA.data.gems.score = 0;
    DATA.data.gems.left = 20;
    DATA.data.gems.tcpt = DATA.data.gems.ttrial ? 1 : 300;
    DATA.data.gems.tcmd = 0;
    DATA.data.gems.cursx = DATA.data.gems.cursy = 64;
    DATA.data.gems.inmove = DATA.data.gems.ismoving = 0;

    gems_initBoard();
    gems_showBoard(0);

    ClearSprites();

    gems_setSprite(0,17,0,0);

    if (DATA.data.gems.ttrial)
        gems_showScore();
    else
        gems_swapLeft();

    gems_setCursorPosition(DATA.data.gems.cursx,DATA.data.gems.cursy);

    DATA.ingame = 1;

    while (exists=gems_moveExists())
    {
        gems_getMove();
        if (DATA.data.gems.dir==NONE)    // exit from menu
            break;
        if (DATA.data.gems.ttrial && DATA.data.gems.top==0) // no time left in time trial : game over
            break;
        if (!DATA.data.gems.ttrial && DATA.data.gems.tcmd>7) // time out in limited game : game over
            break;
        i = gems_doMove();
        if (DATA.data.gems.ttrial)
            gems_showScore();
        else
        {
            if (i)
            {
                DATA.data.gems.tcpt = 300;
                DATA.data.gems.tcmd = 0;
            }
            gems_swapLeft();
            if (DATA.data.gems.left==0) // no swap left in restricted mode : game over
                break;
        }
        exists = gems_moveExists();
    }

    DATA.ingame = 0;

    gems_clearSprite(0);

    SL_PlaySFX(17);

    if (DATA.data.gems.dir==NONE)
        return;
    else if (!exists)
        gems_print(" NO MOVE LEFT ! ",3,17,0);
    else if (DATA.data.gems.ttrial)
    {
        gems_print(" NO TIME LEFT ! ",3,17,0);
        for (i=0;i<5;i++)
            gems_swapGems(DATA.data.gems.psx0,DATA.data.gems.psy0,DATA.data.gems.psx1,DATA.data.gems.psy1);
    }
    else
    {
        if (DATA.data.gems.tcmd>7)
        {
            gems_print("   TIME OUT !   ",3,17,0);
            for (i=0;i<5;i++)
                gems_swapGems(DATA.data.gems.psx0,DATA.data.gems.psy0,DATA.data.gems.psx1,DATA.data.gems.psy1);
        }
        else
            gems_print(" NO SWAP LEFT ! ",3,17,0);
    }
    Sleep(100);
    SL_PlaySFX(22);
    gems_print("  GAME OVER  !  ",3,17,0);
    Sleep(100);
    gems_showScore();
    Sleep(100);
    if (DATA.data.gems.ttrial)
        i = 9;
    else
        i = 4;
    if (DATA.data.gems.score>*((u16*)&data[GEMS_SAVED_OFFSET+i*6+4]))
    {
        char s[4] = {' ',' ',' ',0};
        gems_print("ENTER NAME:     ",3,17,0);
        for (k=0;k<3;k++)
        {
            s[k] = 'A';
            while(((j=JOYPAD)&J_A) == 0)
            {
                gems_print(s,15,17,0);
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
            if (DATA.data.gems.score>*((u16*)&data[GEMS_SAVED_OFFSET+k*6+4]))
                break;
        }
        k = k*6;
        for (l=i*6;l>k;--l)
            data[GEMS_SAVED_OFFSET+l+6-1] = data[GEMS_SAVED_OFFSET+l-1];
        *((u16*)&data[GEMS_SAVED_OFFSET+k+4]) = DATA.data.gems.score;
        data[GEMS_SAVED_OFFSET+k] = s[0];
        data[GEMS_SAVED_OFFSET+k+1] = s[1];
        data[GEMS_SAVED_OFFSET+k+2] = s[2];
        data[GEMS_SAVED_OFFSET+k+3] = 0;
        flash(data);
        gems_showHiscores();
    }
}

void gems_loadSpr(u16 offset)
{
    u16 i;
    u16 *p0 = TILE_RAM+(offset*8),*p1 = SPRITE_PALETTE;
    for (i=0;i<TITLE_TILES*8;i++)
        p0[i] = title_tiles[i];
    for (i=0;i<TITLE_PALS*4;i++)
        p1[i] = title_pals[i];
}

const u8 idx[] = {110,124,138};

u8 gems_mainMenu()
{
    u8 i,j,k,cptk,cptl,l;
    s8 dl;

    ClearAll();

    SL_LoadGroup(magical0,sizeof(magical0));
    SL_PlayTune(0);

    gems_loadSpr(420);
    gems_showHC((void*)GEMS_MENU_ID);

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
	if (j&J_B)
		k = 3;
    SeedRandom();
    SL_PlaySFX(1);
    Sleep(30);
    while((((JOYPAD)&J_A) != 0) || (((JOYPAD)&J_B) != 0));

    for (i=0;i<64;i++)
        SetSprite(i,0,0,255,0,0,TOP_PRIO);

    DATA.curimg = 0;

    ClearScreen(SCR_1_PLANE);
    ClearScreen(SCR_2_PLANE);
    return k;
}

void gems_main()
{
    u8 i = 0;

    DATA.ingame = 0;
    DATA.curimg = 0;

    __asm(" di");
    VBL_INT = gems_myVBL;
    __asm(" ei");

    while(i != 3)
    {
        i = gems_mainMenu();
        switch(i)
        {
            case 0:
                DATA.data.gems.ttrial=0;
                gems_play();
                break;
            case 1:
                DATA.data.gems.ttrial=1;
                gems_play();
                break;
            case 2:
                gems_showHiscores();
                break;
        }
        DATA.ingame = 0;
    }
}
