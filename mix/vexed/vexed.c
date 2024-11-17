#include "ngpc.h"
#include "library.h"
#include "hicolor.h"
#include "flash.h"
#include "music.h"
#include "img.h"

#include "metalslug0.mh"
#include "metalslug1.mh"
#include "metalslug2.mh"

#include "gamedata.h"

#include "vexed/vexed.h"
#include "vexed/levels.h"
#include "vexed/vxd_title.h"
#include "vexed/tables.h"

extern volatile u8 VBCounter;

extern GAME_DATA DATA;

#define OK 0
#define EXIT 1
#define RESTART 2
#define SOLVE 3
#define PREVIOUS 4
#define NEXT 5
#define LEFT 0x80

#define EMPTY_BLOCK 9

void vexed_setTile(u16* plane,u16 x,u16 y,u16 tile) {
    plane[(y<<5)+x] = tile == 0 ? 0 : (tile+(((u16)VEXED_PALIDX1[tile-1])<<9));
}

void vexed_print(const char *s,u16 x,u16 y,u8 dir) {
    u16 i;
    for (i=0;s[i];i++)
    {
        u8 c = s[i];
        u16 t = 0;
        if (c != ' ')
        {
            c = (c>='a'&&c<='z') ? (c-'a'+10) : ((c>='A'&&c<='Z') ? (c-'A'+10) : ((c>='0'&&c<='9') ? (c-'0') : 255));
            if (c==255)
            {
                switch(s[i])
                {
                    case ':': c = 36; break;
                    case '!': c = 37; break;
                    case '.': c = 38; break;
                    default : c = 0; break;
                }
            }
            t = VEXED_WIDTH*2+c+1;
        }
        vexed_setTile(SCROLL_PLANE_1,x+(dir?0:i),y+(dir?i:0),t);
    }
}

void vexed_printl(const char *s,u16 x,u16 y,u8 dir,u8 l)
{
    char buf[32];
    u8 i;
    for (i=0;s[i];i++)
        buf[i]=s[i];
    while (i<l)
        buf[i++]=' ';
    buf[i]=0;
    vexed_print(buf,x,y,dir);
}

void vexed_printi(u16 i,u16 x,u16 y,u8 dir,u8 l)
{
    char buf[9];
    u8 j = 9;
    buf[--j]=0;
    do
    {
        buf[--j]='0'+(i%10);
        i /= 10;
    } while (i>0);
    vexed_printl(buf+j,x,y,dir,l);
}

void vexed_clearTile16(u16 i,u16 j)
{
    u16 i2 = i<<1, j2 = j<<1;
    vexed_setTile(SCROLL_PLANE_1,i2,j2,0);
    vexed_setTile(SCROLL_PLANE_1,i2+1,j2,0);
    vexed_setTile(SCROLL_PLANE_1,i2,j2+1,0);
    vexed_setTile(SCROLL_PLANE_1,i2+1,j2+1,0);
}

void vexed_setTile16(u16 i,u16 j,u16 k)
{
    u16 i2 = i<<1, j2 = j<<1;
    vexed_setTile(SCROLL_PLANE_1,i2,j2,k);
    vexed_setTile(SCROLL_PLANE_1,i2+1,j2,k+1);
    vexed_setTile(SCROLL_PLANE_1,i2,j2+1,k+VEXED_WIDTH);
    vexed_setTile(SCROLL_PLANE_1,i2+1,j2+1,k+VEXED_WIDTH+1);
}

void vexed_setTile8(u16 i,u16 j,u16 k)
{
    u16 i2 = i<<1, j2 = j<<1;
    vexed_setTile(SCROLL_PLANE_1,i2,j2,k);
    vexed_setTile(SCROLL_PLANE_1,i2+1,j2,k);
    vexed_setTile(SCROLL_PLANE_1,i2,j2+1,k);
    vexed_setTile(SCROLL_PLANE_1,i2+1,j2+1,k);
}

void vexed_getLevel(const char *level)
{
    u8 i, j, k, idx = 0, row;
    DATA.data.vexed.count = 0;
    for (i = 0; i < 8; ++i)
    {
        row = 0;
        while (row < 10)
        {
            if (level[idx] >= '1' && level[idx] <= '9')
            {
                if (level[idx+1] == '0')
                {
                    k = 10;
                    idx += 1;
                }
                else
                    k = level[idx]-'0';
                for (j = 0; j < k; ++j)
                    DATA.data.vexed.board[row+j][i] = 0;
                idx += 1;
                row += k;
                continue;
            }
            switch(level[idx++])
            {
                case 'a': k = 2; break;
                case 'b': k = 1; break;
                case 'c': k = 3; break;
                case 'd': k = 4; break;
                case 'e': k = 5; break;
                case 'f': k = 6; break;
                case 'g': k = 7; break;
                case 'h': k = 8; break;
                case '~': k = 9; break;
            }
            DATA.data.vexed.board[row++][i] = k;
            if (k != EMPTY_BLOCK)
                DATA.data.vexed.count += 1;
        }
        idx += 1; // skip '/'
    }
}

void vexed_getSolution(const char *sol)
{
    u8 i, x, y, b=0;
    DATA.data.vexed.solen = 0;
    for (i=0;sol[i];i+=2)
    {
        x = sol[i];
        b = (x>='A' && x<='Z') ? (LEFT | ((x - 'A') << 3)) : ((x - 'a') << 3);
        y = sol[i+1];
        b |= (y>='A' && y<='Z') ? (y - 'A') : (y - 'a');
        DATA.data.vexed.sol[DATA.data.vexed.solen++] = b;
    }
}

void vexed_loadLevel(u8 pack, u8 level)
{
    u16 i;
    DATA.data.vexed.idx = level;
    for (i = 0; i < pack; ++i)
        DATA.data.vexed.idx += vexed_level_count[i];
    vexed_getLevel((const char*)vexed_level_struct[DATA.data.vexed.idx]);
    vexed_getSolution((const char*)vexed_level_solutions[DATA.data.vexed.idx]);
}

u8 vexed_getSol(char* sol, u8* xx, u8* yy, u8* dd)
{
    int len = strlen(sol), i, j = 0;
    for (i=0; i<len; i+=2, ++j)
    {
        u8 x = sol[i];
        u8 y = sol[i+1];
        dd[j] = x < 'a' ? 0 : 1;
        xx[j] = x - (x >= 'A' ? 'A' : 'a');
        yy[j] = y - (y >= 'A' ? 'A' : 'a');
    }
    return j;
}

void vexed_clearSprite(u8 idx)
{
    idx<<=2;
    SetSprite(idx,0,0,0,0,0,0);
    SetSprite(idx+1,0,0,0,0,0,0);
    SetSprite(idx+2,0,0,0,0,0,0);
    SetSprite(idx+3,0,0,0,0,0,0);
}

void vexed_setSprite(u8 idx,u8 num,u8 x,u8 y)
{
    idx<<=2;
    SetSprite(idx,num,0,x,y,VEXED_PALIDX1[num-1],TOP_PRIO);
    SetSprite(idx+1,num+1,0,x+8,y,VEXED_PALIDX1[num],TOP_PRIO);
    SetSprite(idx+2,num+VEXED_WIDTH,0,x,y+8,VEXED_PALIDX1[num+VEXED_WIDTH-1],TOP_PRIO);
    SetSprite(idx+3,num+1+VEXED_WIDTH,0,x+8,y+8,VEXED_PALIDX1[num+1+VEXED_WIDTH-1],TOP_PRIO);
}

void vexed_setSpritePos(u8 spr,u8 x,u8 y)
{
    spr<<=2;
    SetSpritePosition(spr,x,y);
    SetSpritePosition(spr+1,x+8,y);
    SetSpritePosition(spr+2,x,y+8);
    SetSpritePosition(spr+3,x+8,y+8);
}

void vexed_setCursorPosition(u8 x,u8 y)
{
    vexed_setSpritePos(0,x,y+16);
}

void __interrupt vexed_myVBL()
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
        u8 c = 19+(DATA.curs<<1);
        p[0] = c;
        p[4] = c+1;
        p[8] = c+VEXED_WIDTH;
        p[12]= c+VEXED_WIDTH+1;
        if (--DATA.cpt==0)
        {
            SCR2_X++;
            SCR2_Y++;
            if (DATA.data.vexed.inmove || DATA.data.vexed.solve)
                DATA.curs = DATA.curs==3 ? 0 : DATA.curs+1;
            else
                DATA.curs = DATA.curs==0 ? 3 : DATA.curs-1;
            DATA.cpt = 7;
        }
        if (DATA.data.vexed.ismoving)
        {
            DATA.data.vexed.cursx+=DATA.data.vexed.cdx;
            DATA.data.vexed.cursy+=DATA.data.vexed.cdy;
            vexed_setCursorPosition(DATA.data.vexed.cursx,DATA.data.vexed.cursy);
            if (DATA.data.vexed.cursx==DATA.data.vexed.destx && DATA.data.vexed.cursy==DATA.data.vexed.desty)
                DATA.data.vexed.ismoving = 0;
        }
        else if (DATA.data.vexed.solve == 0)
        {
            u8 j =JOYPAD;
            if (DATA.data.vexed.inmove && (j&J_A))
            {
                if ((j&J_LEFT) && (DATA.data.vexed.cursx>0))
                    DATA.data.vexed.dir = J_LEFT;
                else if ((j&J_RIGHT) && (DATA.data.vexed.cursx<144))
                    DATA.data.vexed.dir = J_RIGHT;
                if (DATA.data.vexed.dir!=0)
                    DATA.data.vexed.inmove = 0;
            }
            else if (DATA.data.vexed.dir==0)
            {
                DATA.data.vexed.destx=DATA.data.vexed.cursx;
                DATA.data.vexed.desty=DATA.data.vexed.cursy;
                if (j&J_LEFT)
                    DATA.data.vexed.destx = DATA.data.vexed.destx>0 ? DATA.data.vexed.destx - 16 : 0;
                if (j&J_RIGHT)
                    DATA.data.vexed.destx = DATA.data.vexed.destx<144 ? DATA.data.vexed.destx + 16 : 144;
                if (j&J_UP)
                    DATA.data.vexed.desty = DATA.data.vexed.desty>0 ? DATA.data.vexed.desty - 16 : 0;
                if (j&J_DOWN)
                    DATA.data.vexed.desty = DATA.data.vexed.desty<112 ? DATA.data.vexed.desty + 16 : 112;
                if (DATA.data.vexed.destx!=DATA.data.vexed.cursx||DATA.data.vexed.desty!=DATA.data.vexed.cursy)
                {
                    DATA.data.vexed.cdx = (DATA.data.vexed.destx>DATA.data.vexed.cursx)?1:(DATA.data.vexed.destx<DATA.data.vexed.cursx?-1:0);
                    DATA.data.vexed.cdy = (DATA.data.vexed.desty>DATA.data.vexed.cursy)?1:(DATA.data.vexed.desty<DATA.data.vexed.cursy?-1:0);
                    DATA.data.vexed.ismoving=1;
                }
            }
        }
    }
}

void vexed_clearTextZone()
{
    u16 i;
    for (i=0;i<20;i++)
    {
        vexed_setTile(SCROLL_PLANE_1,i,0,0);
        vexed_setTile(SCROLL_PLANE_1,i,1,0);
        vexed_setTile(SCROLL_PLANE_1,i,18,0);
    }
}

void vexed_showBoard()
{
    u16 i, j;
    const char* s;
    for (i=0;i<10;++i)
    {
        for (j=0;j<8;++j)
        {
            if (DATA.data.vexed.board[i][j] == EMPTY_BLOCK)
                vexed_clearTile16(i,j+1);
            else
                vexed_setTile16(i,j+1,(DATA.data.vexed.board[i][j]<<1)+1);
        }
    }
    vexed_clearTextZone();
    s = (const char*)vexed_packs[DATA.data.vexed.pack];
    i = (20-strlen(s))>>1;
    vexed_print(s, i, 0, 0);
    s = (const char*)vexed_level_names[DATA.data.vexed.idx];
    i = (20-strlen(s))>>1;
    vexed_print(s, i, 1, 0);
    vexed_print("SOL:", 13, 18, 0);
    vexed_printi(DATA.data.vexed.solen, 18, 18, 0, 2);
    vexed_print("COUNT:", 0, 18, 0);
    vexed_printi(DATA.data.vexed.cur, 7, 18, 0, 2);
}

void vexed_loadGfx()
{
    u16 i;
    u16 *p0 = TILE_RAM;
    u16 *p1 = SCROLL_1_PALETTE;
    u16 *p2 = SCROLL_2_PALETTE;
    u16 *ps = SPRITE_PALETTE;
    for (i=0;i<8;++i)
        p0[i] = 0;
    for (i=0;i<VEXED_TILES*8;i++)
        p0[i+8] = VEXED_TILES1[i];
}

void vexed_setPals()
{
    u16 *p1 = SCROLL_1_PALETTE;
    u16 *p2 = SCROLL_2_PALETTE;
    u16 *ps = SPRITE_PALETTE;
    u16 i;
    for (i=0;i<VEXED_NPALS1*4;++i)
    {
        p1[i] = VEXED_PALS1[i];
        p2[i] = VEXED_PALS1[i];
        ps[i] = VEXED_PALS1[i];
    }
}

u8 vexed_getMove()
{
    u8 i, j, ret = OK;
    DATA.data.vexed.dir = 0;
    DATA.data.vexed.inmove = 1;
    while(DATA.data.vexed.inmove)
    {
        j = JOYPAD;
        if (j)
            while (i=JOYPAD);
        if (j&J_OPTION)
        {
            DATA.ingame = 0;
            vexed_clearTextZone();
            vexed_print("A: RESUME  B: EXIT",1,0,0);
            vexed_print("OPTION: SOLUTION",2,1,0);
            if (DATA.data.vexed.lvl>0)
                vexed_print("LEFT:PREV",0,18,0);
            if (DATA.data.vexed.lvl+1<vexed_level_count[DATA.data.vexed.pack]
             && data[VEXED_SAVED_OFFSET+DATA.data.vexed.idx] > 0)
                vexed_print("RIGHT:NEXT",10,18,0);
            while(1)
            {
                j=JOYPAD;
                if (j&(J_A|J_B|J_OPTION))
                    break;
                if (DATA.data.vexed.lvl>0 && (j&J_LEFT))
                    break;
                if (DATA.data.vexed.lvl+1 < vexed_level_count[DATA.data.vexed.pack]
                 && data[VEXED_SAVED_OFFSET+DATA.data.vexed.idx] > 0
                 && (j&J_RIGHT))
                    break;
            }
            while (i=JOYPAD);
            vexed_showBoard();
            DATA.ingame = 1;
            if (j&J_B)
            {
                ret = EXIT;
                break;
            }
            else if (j&J_OPTION)
            {
                ret = SOLVE;
                break;
            }
            else if (j&J_LEFT)
            {
                ret = PREVIOUS;
                break;
            }
            else if (j&J_RIGHT)
            {
                ret = NEXT;
                break;
            }
        }
        else if (j&J_B)
        {
            ret = RESTART;
            break;
        }
        else if (DATA.data.vexed.solve)
        {
            break;
        }
    }
    DATA.data.vexed.inmove = 0;
    while (j=JOYPAD);
    return ret;
}

void vexed_push(u8 x,u8 y,u8 xx)
{
    u8 a = DATA.data.vexed.board[x][y],
       b = DATA.data.vexed.board[xx][y],
       xa,ya,xb;
    s8 dx;

    if (a == 0 || a == EMPTY_BLOCK || b != EMPTY_BLOCK)
        return;

    SL_PlaySFX(1);

    vexed_printi(++DATA.data.vexed.cur, 7, 18, 0, 2);

    xa = (x<<4);
    ya = (y<<4);
    xb = (xx<<4);
    if (xa<xb)
        dx = 4;
    else if (xa>xb)
        dx = -4;

    DATA.data.vexed.board[x][y] = EMPTY_BLOCK;
    vexed_setSprite(1,(a<<1)+1,xa,ya+16);
    vexed_clearTile16(x,y+1);
    while (xa!=xb)
    {
        xa+=dx;
        vexed_setSpritePos(1,xa,ya+16);
        Sleep(3);
    }
    DATA.data.vexed.board[xx][y] = a;
    vexed_setTile16(xx,y+1,(a<<1)+1);
    vexed_clearSprite(1);

    Sleep(4);
}

u8 vexed_check()
{
    u16 i, j;
    u8 b, b2;
    for (i=0;i<10;++i)
    {
        for (j=0;j<8;++j)
        {
            b = DATA.data.vexed.board[i][j];
            if (b == EMPTY_BLOCK || b == 0)
                continue;
            if (j < 7)
            {
                b2 = DATA.data.vexed.board[i][j+1];
                if (b2 == EMPTY_BLOCK || b2 == b)
                    return 1;
            }
            if (i > 0 && DATA.data.vexed.board[i-1][j] == b)
                return 1;
        }
    }
    return 0;
}

#define DY 2

void vexed_gravity()
{
    u8 i, j, k, l, m;
    u8 x[32];
    u8 y[32];
    u8 b[32];
    u8 n[32];
    u8 s[32];
    u8 t[15];
    while(1)
    {
        // find falling blocks
        k = 0;
        for (i=0;i<10;++i)
        {
            for (j=0;j<7;++j)
            {
                b[k] = DATA.data.vexed.board[i][6-j];
                if (b[k] == EMPTY_BLOCK || b[k] == 0 || DATA.data.vexed.board[i][6-j+1] != EMPTY_BLOCK)
                    continue;
                x[k] = i;
                y[k] = 6-j;
                n[k++] = 0;
            }
        }
        if (k == 0)
            break;
        // make them fall
        for (i = 0; i < 15; ++i)
            t[i] = 1;
        l = 0; // sprite count
        i = k-1; // current index
        j = k; // remaining tile count
        do
        {
            if (++i == k) // loop
                i = 0;
            if (n[i] == 0 && l < 15) // move new tile
            {
                DATA.data.vexed.board[x[i]][y[i]] = EMPTY_BLOCK;
                // find free sprite
                for (m = 0; m < 15; ++m)
                {
                    if (t[m])
                    {
                        // reserve sprite
                        t[m] = 0;
                        s[i] = m + 1;
                        l += 1;
                        break;
                    }
                }
                vexed_setSprite(s[i], (b[i]<<1)+1, x[i]<<4, (y[i]<<4)+DY+16);
                vexed_clearTile16(x[i],y[i]+1);
                n[i] = DY;
            }
            else if (n[i] < 16) // falling tile
            {
                n[i] += DY;
                if (n[i] < 16) // still falling
                {
                    vexed_setSpritePos(s[i], x[i]<<4, (y[i]<<4) + n[i] + 16);
                }
                else // stop falling
                {
                    DATA.data.vexed.board[x[i]][y[i]+1] = b[i];
                    vexed_setTile16(x[i],y[i]+2,(b[i]<<1)+1);
                    vexed_clearSprite(s[i]);
                    t[s[i]-1] = 1; // free sprite
                    l -= 1;
                    j -= 1; // tile done
                }
            }
            else
                continue;
            Sleep(1);
        } while (j > 0);
    }
}

void vexed_remove()
{
    u16 i, j, k;
    u8 b, found = 0;
    u8 board[10][8];
    memset(board,0,80);
    // find blocks to remove
    for (i=0;i<10;++i)
    {
        for (j=0;j<8;++j)
        {
            b = DATA.data.vexed.board[i][j];
            if (b == EMPTY_BLOCK || b == 0)
                continue;
            if (j > 0 && DATA.data.vexed.board[i][j-1] == b)
            {
                board[i][j] = 1;
                board[i][j-1] = 1;
                found = 1;
            }
            if (i > 0 && DATA.data.vexed.board[i-1][j] == b)
            {
                board[i][j] = 1;
                board[i-1][j] = 1;
                found = 1;
            }
        }
    }
    if (!found)
        return;

    SL_PlaySFX(4);

    // Animation
    for (k=0;k<4;++k)
    {
        for (i=0;i<10;++i)
        {
            for (j=0;j<8;++j)
            {
                if (board[i][j])
                    vexed_setTile8(i,j+1,28+k);
            }
        }
        Sleep(4);
    }
    // remove blocks
    for (i=0;i<10;++i)
    {
        for (j=0;j<8;++j)
        {
            if (board[i][j])
            {
                DATA.data.vexed.board[i][j] = EMPTY_BLOCK;
                vexed_clearTile16(i,j+1);
                DATA.data.vexed.count -= 1;
            }
        }
    }
}

void vexed_doSolve()
{
    u8 idx = DATA.data.vexed.solen - (DATA.data.vexed.solve - 1);
    u8 v = DATA.data.vexed.sol[idx];
    u8 x = (v>>3)&0xf;
    u8 y = v&0x7;
    vexed_push(x,y,(v&LEFT)?x-1:x+1);
    while(vexed_check())
    {
        vexed_gravity();
        vexed_remove();
    }
    DATA.data.vexed.solve -= 1;
}

void vexed_doMove()
{
    u8 xx,x=(DATA.data.vexed.cursx>>4),y=(DATA.data.vexed.cursy>>4);
    if (DATA.data.vexed.dir == J_LEFT)
    {
        if (x==0)
            return;
        xx = x-1;
    }
    else
    {
        if (x==9)
            return;
        xx = x+1;
    }
    DATA.data.vexed.dir=0;
    vexed_push(x,y,xx);
    while(vexed_check())
    {
        vexed_gravity();
        vexed_remove();
    }
}

void vexed_fillPlane(u16* plane, u16 tile)
{
    u16 i, v = (tile+1+(((u16)VEXED_PALIDX1[tile])<<9));
    for (i = 0; i < 1024; ++i)
        plane[i] = v;
}

void vexed_play()
{
    u8 move = RESTART, j;

    SL_LoadGroup(metalslug1,sizeof(metalslug1));
    SL_PlayTune(0);

    ClearAll();

    DATA.cpt = 1;
    DATA.curs = 1;
    DATA.data.vexed.cursx = DATA.data.vexed.cursy = 64;
    DATA.data.vexed.inmove = DATA.data.vexed.ismoving = 0;
    DATA.data.vexed.solve = 0;
    DATA.data.vexed.cur = 0;

    vexed_setSprite(0,19,0,0);
    vexed_setCursorPosition(DATA.data.vexed.cursx,DATA.data.vexed.cursy);

    vexed_loadLevel(DATA.data.vexed.pack, DATA.data.vexed.lvl);
    vexed_showBoard();

    vexed_fillPlane(SCROLL_PLANE_2, 36+VEXED_WIDTH);

    vexed_setPals();

    DATA.ingame = 1;

    while(move != EXIT) {
        switch(move)
        {
            case RESTART:
                DATA.data.vexed.solve = 0;
                DATA.data.vexed.cur = 0;
                vexed_loadLevel(DATA.data.vexed.pack, DATA.data.vexed.lvl);
                vexed_showBoard();
                move = OK;
                break;
            case SOLVE: // reset and solve level
                DATA.data.vexed.cur = 0;
                vexed_loadLevel(DATA.data.vexed.pack, DATA.data.vexed.lvl);
                vexed_showBoard();
                DATA.data.vexed.solve = DATA.data.vexed.solen + 1;
                move = OK;
                break;
            case PREVIOUS: // go to previous level
                DATA.data.vexed.lvl -= 1;
                move = RESTART;
                break;
            case NEXT: // go to next level
                DATA.data.vexed.lvl += 1;
                move = RESTART;
                break;
            case OK:
                if (DATA.data.vexed.dir || DATA.data.vexed.solve)
                {
                    if (DATA.data.vexed.solve == 0)
                        vexed_doMove();
                    else
                        vexed_doSolve();
                    if (DATA.data.vexed.count == 0)
                    {
                        SL_PlaySFX(0);
                        Sleep(30);
                        if (DATA.data.vexed.solve == 0)
                        {
                            if (data[VEXED_SAVED_OFFSET+DATA.data.vexed.idx] == 0
                             || data[VEXED_SAVED_OFFSET+DATA.data.vexed.idx] > DATA.data.vexed.cur)
                            {
                                data[VEXED_SAVED_OFFSET+DATA.data.vexed.idx] = DATA.data.vexed.cur;
                                flash(data);
                            }
                            // Level completed
                            if (++DATA.data.vexed.lvl == vexed_level_count[DATA.data.vexed.pack])
                            {
                                // Pack completed
                                vexed_clearTextZone();
                                vexed_print("PACK COMPLETED", 3, 17, 0);
                                vexed_print("CONGRATULATIONS!", 2, 18, 0);
                                while(!((j=JOYPAD)&(J_A|J_B))); // wait button press
                                while((j=JOYPAD)&(J_A|J_B)); // wait button release
                                break;
                            }
                        }
                        move = RESTART;
                        break;
                    }
                }
            default:
                move = vexed_getMove();
        }
    }
    // back to menu
    vexed_clearSprite(0);
    ClearPals();
}

void vexed_showHC(void *img)
{
    DATA.curimg=0;
    hc_load(img);
    DATA.curimg=img;
}

u8 vexed_showTitle()
{
    u8 i,j;

    SL_LoadGroup(metalslug2,sizeof(metalslug2));
    SL_PlayTune(0);
    vexed_showHC((void*)VEXED_TITLE_ID);
    while (((j=JOYPAD) & (J_A | J_B)) == 0);
    SL_PlaySFX(0);
    Sleep(30);
    while (i=JOYPAD);
    DATA.curimg = 0;

    return j & J_B;
}

void vexed_printInfos(u8 lvl)
{
    u8 b = data[VEXED_SAVED_OFFSET+DATA.data.vexed.idx];
    vexed_printl((const char*)vexed_packs[DATA.data.vexed.pack], 1, 12, 0, 20);
    vexed_printl((const char*)vexed_level_names[DATA.data.vexed.idx], 1, 16, 0, 20);
    vexed_printi(lvl+1, 14, 15, 0, 2);
    if (b>0)
        vexed_printi(b, 8, 17, 0, 2);
    else
        vexed_print("  ", 8, 17, 0);
}

u8 vexed_getCurrentLevelIndex()
{
    u8 lvl = 0, j;
    DATA.data.vexed.idx = 0;
    for (j = 0; j < DATA.data.vexed.pack; ++j)
        DATA.data.vexed.idx += vexed_level_count[j];
    while (data[VEXED_SAVED_OFFSET+DATA.data.vexed.idx] > 0)
    {
        lvl += 1;
        DATA.data.vexed.idx += 1;
    }
    vexed_printi(DATA.data.vexed.pack+1, 13, 11, 0, 2);
    vexed_printi(lvl, 18, 13, 0, 2);
    vexed_printInfos(lvl);
    return lvl;
}

const u8 vexed_logo[5][25] = {
    {1,0,0,0,1,0,2,2,2,2,0,3,0,0,3,0,4,4,4,4,0,5,5,5,0},
    {1,0,0,0,1,0,2,0,0,0,0,3,0,0,3,0,4,0,0,0,0,5,0,0,5},
    {1,0,0,0,1,0,2,2,2,0,0,0,3,3,0,0,4,4,4,0,0,5,0,0,5},
    {0,1,0,1,0,0,2,0,0,0,0,3,0,0,3,0,4,0,0,0,0,5,0,0,5},
    {0,0,1,0,0,0,2,2,2,2,0,3,0,0,3,0,4,4,4,4,0,5,5,5,0}
};

u8 vexed_mainMenu()
{
    u8 i,k,wait=0,e;
    u16 a=0,b=0,j;

    SL_LoadGroup(metalslug0,sizeof(metalslug0));
    SL_PlayTune(0);

    ClearAll();

    vexed_fillPlane(SCROLL_PLANE_2, 36+VEXED_WIDTH);
    SetSprite(0,27,0,0,96,VEXED_PALIDX1[26],TOP_PRIO);
    vexed_print("CHOOSE PACK:", 0, 11, 0);
    vexed_print("UNLOCKED LEVELS:", 2, 13, 0);
    vexed_print("CHOOSE LEVEL:", 0, 15, 0);
    vexed_print("BEST:", 2, 17, 0);
    DATA.data.vexed.pack = 0;
    e = 0;
    DATA.data.vexed.lvl = vexed_getCurrentLevelIndex();
    for (i = 0; i < 20; ++i)
    {
        vexed_setTile(SCROLL_PLANE_1,i,0,28+VEXED_WIDTH);
        vexed_setTile(SCROLL_PLANE_1,i,10,28+VEXED_WIDTH);
        vexed_setTile(SCROLL_PLANE_1,i,18,28+VEXED_WIDTH);
    }
    vexed_setPals();
    while(1)
    {
        SCR2_X++;
        SCR2_Y = (tsin[a&PMASK]>>6)+16;
        k = 1;
        for (i=0;i<5;++i)
        {
            u16 x = (tcos[((b>>1)+(i<<2))&PMASK]>>5) - 16, y;
            for (j=0;j<25;++j)
            {
                u8 s = vexed_logo[i][j];
                if (s == 0)
                    continue;
                s += 31;
                y = (i<<3) + (tsin[(a+(j<<2))&PMASK]>>6) + 24;
                SetSprite(k++,s+1,0,(u8)(x+(j<<3)),(u8)y,VEXED_PALIDX1[s],TOP_PRIO);
            }
        }
        b += 5;
        a += 2;
        if (wait)
        {
            wait -= 1;
            Sleep(1);
            continue;
        }
        j = JOYPAD;
        if (j&J_A)
        {
            i = 1;
            break;
        }
        else if (j&J_B)
        {
            i = 0;
            break;
        }
        else if (j&J_LEFT)
        {
            switch(e)
            {
                case 0: // Pack selection
                    if (DATA.data.vexed.pack == 0)
                        DATA.data.vexed.pack = VEXED_PACK_COUNT - 1;
                    else
                        DATA.data.vexed.pack -= 1;
                    DATA.data.vexed.lvl = vexed_getCurrentLevelIndex();
                    SL_PlaySFX(3);
                    break;
                case 1:
                    if (DATA.data.vexed.lvl > 0)
                    {
                        DATA.data.vexed.idx -= 1;
                        vexed_printInfos(--DATA.data.vexed.lvl);
                        SL_PlaySFX(3);
                    }
                    break;
            }
            wait = 20;
        }
        else if (j&J_RIGHT)
        {
            switch(e)
            {
                case 0: // Pack selection
                    DATA.data.vexed.pack += 1;
                    if (DATA.data.vexed.pack == VEXED_PACK_COUNT)
                        DATA.data.vexed.pack = 0;
                    DATA.data.vexed.lvl = vexed_getCurrentLevelIndex();
                    SL_PlaySFX(3);
                    break;
                case 1:
                    if (DATA.data.vexed.lvl+1 < vexed_level_count[DATA.data.vexed.pack])
                    {
                        if (data[VEXED_SAVED_OFFSET+DATA.data.vexed.idx] > 0)
                        {
                            DATA.data.vexed.idx += 1;
                            vexed_printInfos(++DATA.data.vexed.lvl);
                            SL_PlaySFX(3);
                        }
                    }
                    break;
            }
            wait = 20;
        }
        else if (j&J_UP)
        {
            e = e == 0 ? 1 : 0;
            SetSpritePosition(0,0,96+e*8*4);
            wait = 20;
            SL_PlaySFX(2);
        }
        else if (j&J_DOWN)
        {
            e = e == 1 ? 0 : 1;
            SetSpritePosition(0,0,96+e*8*4);
            wait = 20;
            SL_PlaySFX(2);
        }
        Sleep(1);
    }
    SL_PlaySFX(0);
    Sleep(30);
    while((j=JOYPAD)&(J_A|J_B)); // wait button release
    SCR2_X = 0;
    SCR2_Y = 0;
    ClearAll();
    return i;
}

void vexed_main()
{
    u8 i = 0;

    DATA.ingame = 0;
    DATA.curimg = 0;

    ClearAll();

    __asm(" di");
    VBL_INT = vexed_myVBL;
    __asm(" ei");

    if (vexed_showTitle())
        return;

    ClearAll();
    vexed_loadGfx();
    do
    {
        i = vexed_mainMenu();
        if (i == 1)
            vexed_play();
        DATA.ingame = 0;
    } while (i>0);
}
