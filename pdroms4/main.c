#include "ngpc.h"
#include "carthdr.h"
#include "library.h"
#include "hicolor.h"
#include "mplayer.h"
#include "flash.h"
#include "music.h"
#include "img.h"
#include "wav.h"

#include "font.h"
#include "common.h"

#include "tables.h"

#define MOVIES

#ifdef MOVIES
#include "tunnel.h"
#include "success.h"
#include "death.h"
#endif

#include "menubg.h"

#include "sfx/music1.mh"
#include "sfx/menusnd.mh"

#include "levels.h"

/*
 * Gfx vars
 */

void *curimg;
extern volatile u8 VBCounter;
volatile u8 updateBG = 0;
volatile u32 moviecnt = 0;
MOVIE curmovie;
u16 t_font;
u16 p_font;

/*
 * Game vars
 */

u8 curlvl;
u8 curstate;
u8 lvlstate[2][16][16];
u8 tcount[3];
s8 tgtx,tgty,srcx,srcy;

u8 removed[16][16];
u8 remX[32];
u8 remY[32];
u8 remCount,remAnim;

/*
 * Gfx stuff
 */

void print(const char *s,u8 x,u8 y, u8 v)
{
    u16 i,n,t;
    t = (((u16)y)<<5)+x;
    for (i=0;s[i];++i)
    {
        u8 c = s[i];
        if (c>='A' && c<='Z')
            n = c - 'A' + 10;
        else if (c>='a' && c<='z')
            n = c - 'a' + 10;
        else if (c>='0' && c<='9')
            n = c - '0';
        else
        {
            switch(c)
            {
                case '.': n = 36; break;
                case ':': n = 37; break;
                case '/': n = 38; break;
                case '!': n = 39; break;
                default : n = 255; break;
            }
        }
        if (n!=255)
            (SCROLL_PLANE_1)[t] = (((u16)(p_font+COMMON_PALIDX1[n])<<9))+n+t_font+1;
        t += v ? 32 : 1;
    }
}

void printi(s16 i,u8 x,u8 y)
{
    char buf[9];
    u8 j = 9;
    u8 k = (i<0) ? 1 : 0;
    if (i < 0)
        i = -i;
    buf[j]=0;
    do
    {
        buf[--j]='0'+(i%10);
        i /= 10;
    } while (i>0);
    if (k)
        buf[--j] = '.';
    print(buf+j,x,y,0);
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

void clearSprites(u8 start)
{
    u16 *theSprite = (u16*)SPRITE_RAM;
    u16 i;

    for (i=(start<<1);i<128;i+=2)
        *(theSprite+i) = 0;
}

void copyMem();

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

    if (updateBG)
    {
        updateBG = 0;
        copyMem();
    }
}

void loadCommon()
{
    u16 i,n;
    t_font = 256;
    p_font = 0;
    n = t_font<<3;
    for (i=0;i<8;i++)
        (TILE_RAM)[n++] = 0;
    for (i=0;i<(COMMON_TILES*8);i++)
        (TILE_RAM)[n++] = COMMON_TILES1[i];
    n = p_font<<2;
    for (i=0;i<(COMMON_NPALS1*4);i++)
    {
        (SCROLL_1_PALETTE)[n] = COMMON_PALS1[i];
        (SPRITE_PALETTE)[n++] = COMMON_PALS1[i];
    }
}

void loadHCGfx()
{
    u16 i,n=448*8;

    // font
    for (i=0;i<(FONT_TILES*8);i++)
        (TILE_RAM)[n++] = FONT_TILES1[i];
    for (i=0;i<(FONT_NPALS1<<2);i++)
        (SPRITE_PALETTE)[i] = FONT_PALS1[i];
}

u8 printHC(char *buf,u8 x,u8 y,u8 n)
{
    u16 i,j;
    for (i=0;buf[i];i++,x+=8)
    {
        if (buf[i]!=' ')
        {
            j = buf[i]-'A'+(buf[i]<'Y'?0:18);
            SetSprite(n++,j+448,x,y,FONT_PALIDX1[j],TOP_PRIO);
        }
    }
    return n;
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

    while((loop==1 || moviecnt<count*delay) && (((j=JOYPAD)&J_A)==0))
        __ASM("nop");
    while((j=JOYPAD)&J_A)
        __ASM("nop");

    mp_stop(&curmovie);
}

/*
 * 3D stuff
 */

s16 mat3d[16];

void matrix3D(u16 ax,u16 ay,u16 az,s16 tx,s16 ty,s16 tz)
{
    s16 cx = tcos[ax], cy =  tcos[ay], cz =  tcos[az];
    s16 sx = tsin[ax], sy =  tsin[ay], sz =  tsin[az];
    s16 v;

    mat3d[0] = (cy*cz)>>SHIFT;
    mat3d[1] = (cy*sz)>>SHIFT;
    mat3d[2] = -sy;
    mat3d[3] = 0;

    v = (sx*sy)>>SHIFT;
    v *=cz;
    v -= (cx*sz);
    mat3d[4] = v>>SHIFT;

    v = (sx*sy)>>SHIFT;
    v *= sz;
    v += (cx*cz);
    mat3d[5] = v>>SHIFT;

    mat3d[6] = (sx*cy)>>SHIFT;
    mat3d[7] = 0;

    v = (cx*sy)>>SHIFT;
    v *= cz;
    v += (sx*sz);
    mat3d[8] = v>>SHIFT;

    v = (cx*sy)>>SHIFT;
    v *= sz;
    v -= (sx*cz);
    mat3d[9] = v>>SHIFT;

    mat3d[10] = (cx*cy)>>SHIFT;
    mat3d[11] = 0;

    mat3d[12] = tx<<SHIFT;
    mat3d[13] = ty<<SHIFT;
    mat3d[14] = tz<<SHIFT;
    mat3d[15] = MULT;
}

void transform3D(s16* x,s16* y,s16* z, s16* rx, s16* ry, u16 n)
{
    u16 i;
    s32 vx,vy,vz,v;
    for (i=0;i<n;++i)
    {
        vx  = x[i] * mat3d[0];
        vx += y[i] * mat3d[4];
        vx += z[i] * mat3d[8];
        vx += mat3d[12];

        vy  = x[i] * mat3d[1];
        vy += y[i] * mat3d[5];
        vy += z[i] * mat3d[9];
        vy += mat3d[13];

        vz  = x[i] * mat3d[2];
        vz += y[i] * mat3d[6];
        vz += z[i] * mat3d[10];
        vz += mat3d[14];
        vz >>= SHIFT;

        v = DIVIDE_NS(vx,vz);
        v >>= 8;
        rx[i] = 64 + (s16)(v>>8);
        v = DIVIDE_NS(vy,vz);
        v >>= 8;
        ry[i] = 64 + (s16)(v>>8);
    }
}

/*
 * Bitmap Gfx stuff
 */

void clearMem()
{
__ASM("	ld	xhl,0x7000	");
__ASM("	xor	xwa,xwa		");
__ASM("	ld	bc,64           ");
__ASM("_clear_loop_:            ");
__ASM("	ld	(xhl+),xwa	");
__ASM("	ld	(xhl+),xwa	");
__ASM("	ld	(xhl+),xwa	");
__ASM("	ld	(xhl+),xwa	");
__ASM("	ld	(xhl+),xwa	");
__ASM("	ld	(xhl+),xwa	");
__ASM("	ld	(xhl+),xwa	");
__ASM("	ld	(xhl+),xwa	");
__ASM("	ld	(xhl+),xwa	");
__ASM("	ld	(xhl+),xwa	");
__ASM("	ld	(xhl+),xwa	");
__ASM("	ld	(xhl+),xwa	");
__ASM("	ld	(xhl+),xwa	");
__ASM("	ld	(xhl+),xwa	");
__ASM("	ld	(xhl+),xwa	");
__ASM("	ld	(xhl+),xwa	");
__ASM("	djnz	bc,_clear_loop_	");
}

void copyMem()
{
__ASM("	ld	xhl,0x7000	");
__ASM("	ld	xde,0xa000	");
__ASM("	ld	bc,16*16*8      ");
__ASM("	ldirw	(xde+),(xhl+)   ");
}

void gfxLayer()
{
    u16 i,j;
    u16* pal = SCROLL_2_PALETTE;
    u16* plane = SCROLL_PLANE_2;
    clearMem();
    pal[0] = 0;
    pal[1] = 0xfff;
    pal[2] = 0xccc;
    pal[3] = 0x888;
    pal[4] = 0;
    pal[5] = 0;
    pal[6] = 0;
    pal[7] = 0;
    SCR2_X = 240;
    SCR2_Y = 240;
    copyMem();
    for (i=0;i<32;++i)
        for (j=0;j<32;++j)
            plane[i*32+j] = (i<16&&j<16)?((i<<4)+j):512;
}

const u16 masks[8][4] =
{
    {0, 0x0001, 0x0002, 0x0003},
    {0, 0x0004, 0x0008, 0x000c},
    {0, 0x0010, 0x0020, 0x0030},
    {0, 0x0040, 0x0080, 0x00c0},
    {0, 0x0100, 0x0200, 0x0300},
    {0, 0x0400, 0x0800, 0x0c00},
    {0, 0x1000, 0x2000, 0x3000},
    {0, 0x4000, 0x8000, 0xc000}
};

const u16 umasks[8] =
{
    0xfffc,
    0xfff3,
    0xffcf,
    0xff3f,
    0xfcff,
    0xf3ff,
    0xcfff,
    0x3fff
};

#define BUF_RAM ((u16*)0x7000)

void setPixel(s16 x,s16 y, u16 c)
{
    u16 xt, yt, x0, *tile;
    if (x<0 || y<0 || x>127 || y>127)
        return;
    xt=x&0xfff8;
    yt=(y&0xfff8)<<4;
    x0 = 7-(x&0x7);
    tile = (BUF_RAM)+(xt+yt)+(y&0x7);
    *tile = (*tile & umasks[x0]) | masks[x0][c];
}

#define ABS(a) ((a)<0?-(a):(a))

void line(s16 x1,s16 y1,s16 x2,s16 y2,u16 color)
{
    s16 dx, dy, inx, iny, e;

    if ((x1<0 || y1<0 || x1>127 || y1>127) && (x2<0 || y2<0 || x2>127 || y2>127))
    {
        dx = (x1+x2)>>1;
        dy = (y1+y2)>>1;
        if (dx<0 || dy<0 || dx>127 || dy>127)
            return;
    }

    dx = x2 - x1;
    dy = y2 - y1;
    inx = dx > 0 ? 1 : -1;
    iny = dy > 0 ? 1 : -1;

    dx = ABS(dx);
    dy = ABS(dy);

    if(dx >= dy)
    {
        dy <<= 1;
        e = dy - dx;
        dx <<= 1;
        while (x1 != x2)
        {
            setPixel(x1, y1, color);
            if(e >= 0)
            {
                y1 += iny;
                e-= dx;
            }
            e += dy; x1 += inx;
        }
    }
    else
    {
        dx <<= 1;
        e = dx - dy;
        dy <<= 1;
        while (y1 != y2)
        {
            setPixel(x1, y1, color);
            if(e >= 0)
            {
                x1 += inx;
                e -= dy;
            }
            e += dx; y1 += iny;
        }
    }
    setPixel(x1, y1, color);
}

void circle(s16 xc, s16 yc, u16 r, u16 color)
{
    s16 x=0,y=r;
    s16 d=3-(2*r);

    while(x<=y)
    {
        setPixel(xc+x,yc+y,color);
        setPixel(xc+y,yc+x,color);
        setPixel(xc-x,yc+y,color);
        setPixel(xc+y,yc-x,color);
        setPixel(xc-x,yc-y,color);
        setPixel(xc-y,yc-x,color);
        setPixel(xc+x,yc-y,color);
        setPixel(xc-y,yc+x,color);

        if (d<0)
            d += (4*x)+6;
        else
        {
            d += (4*(x-y))+10;
            y -= 1;
        }
        x++;
    }
}

void gear(s32 xc,s32 yc,u16 r0,u16 r1,u16 a,u16 step,u16 delta,u16 color)
{
    u16 i=0;
    u16 sub = step>>1;
    s16 v;
    s16 x1,y1,x2,y2;
    v = tcos[(i+a+delta)&PMASK]*r1;
    x1 = xc+(v>>SHIFT);
    v = tsin[(i+a+delta)&PMASK]*r1;
    y1 = yc-(v>>SHIFT);
    for (i=0;i<PRECISION;i+=step)
    {
        v = tcos[(i+a+sub-delta)&PMASK]*r1;
        x2 = xc+(v>>SHIFT);
        v = tsin[(i+a+sub-delta)&PMASK]*r1;
        y2 = yc-(v>>SHIFT);
        line(x1,y1,x2,y2,color);
        x1 = x2;
        y1 = y2;
        v = tcos[(i+a+sub)&PMASK]*r0;
        x2 = xc+(v>>SHIFT);
        v = tsin[(i+a+sub)&PMASK]*r0;
        y2 = yc-(v>>SHIFT);
        line(x1,y1,x2,y2,color);
        x1 = x2;
        y1 = y2;
        v = tcos[(i+a+step)&PMASK]*r0;
        x2 = xc+(v>>SHIFT);
        v = tsin[(i+a+step)&PMASK]*r0;
        y2 = yc-(v>>SHIFT);
        line(x1,y1,x2,y2,color);
        x1 = x2;
        y1 = y2;
        v = tcos[(i+a+step+delta)&PMASK]*r1;
        x2 = xc+(v>>SHIFT);
        v = tsin[(i+a+step+delta)&PMASK]*r1;
        y2 = yc-(v>>SHIFT);
        line(x1,y1,x2,y2,color);
        x1 = x2;
        y1 = y2;
    }
}

volatile u16 startAngle;

// Rotating level in wireframe
void wireFrame(u16 theta)
{
    u16 i;
    s16 x,y;
    s16 tx1,ty1,tx2,ty2;
    for (i=0;i<MAX_LINES;++i)
    {
        x = lines[curlvl][0][i];
        if (x == LINE_END)
            break;
        y = lines[curlvl][2][i];
        tx1 = (x*tcos[theta]-y*tsin[theta])>>SHIFT;
        ty1 = (y*tcos[theta]+x*tsin[theta])>>SHIFT;
        x = lines[curlvl][1][i];
        y = lines[curlvl][3][i];
        tx2 = (x*tcos[theta]-y*tsin[theta])>>SHIFT;
        tx2 = (x*tcos[theta]-y*tsin[theta])>>SHIFT;
        ty2 = (y*tcos[theta]+x*tsin[theta])>>SHIFT;
        line(tx1+64,ty1+64,tx2+64,ty2+64,1);
    }
    theta = (theta-startAngle)&PMASK;
    if (srcx>=0)
    {
        x = (srcx*8)-60;
        y = (srcy*8)-60;
        tx1 = (x*tcos[theta]-y*tsin[theta])>>SHIFT;
        ty1 = (y*tcos[theta]+x*tsin[theta])>>SHIFT;
        SetSprite(0,t_font+52,(u8)(77+tx1),(u8)(77+ty1),COMMON_PALIDX1[51],TOP_PRIO);
    }
    if (tgtx>=0)
    {
        x = (tgtx*8)-60;
        y = (tgty*8)-60;
        tx1 = (x*tcos[theta]-y*tsin[theta])>>SHIFT;
        ty1 = (y*tcos[theta]+x*tsin[theta])>>SHIFT;
        SetSprite(1,t_font+53,(u8)(77+tx1),(u8)(77+ty1),COMMON_PALIDX1[52],TOP_PRIO);
    }
}

volatile u16 angGear0 = 0;
volatile u16 angGear1 = 0;
volatile u16 angGear2 = 0;
volatile u16 curAng = 0;
volatile u16 x3d = 0;
volatile u16 y3d = 0;
volatile u16 z3d = 0;

const s16 cube_x[8] = {-72,72,72,-72,-72,72,72,-72};
const s16 cube_y[8] = {-72,-72,72,72,-72,-72,72,72};
const s16 cube_z[8] = {72,72,72,72,-72,-72,-72,-72};
const s16 cube_segs[12][2] = {
    {0,1},{1,2},{2,3},{3,0},
    {4,5},{5,6},{6,7},{7,4},
    {0,4},{1,5},{2,6},{3,7}
};

// Draw 2D animations
void updateGfxLayer(u8 rotDir,u8 wf,u8 upd)
{
    u8 d = 4-wf*4;
    clearMem();

    circle(36,36,6,2);
    gear(36,36,16,20,angGear0&PMASK,64,3,2);
    circle(64,64,5,1);
    gear(64,64,16-d,20-d,angGear1&PMASK,64,3,3-wf);
    circle(77,117,8,3);
    gear(78,118,32,38,angGear2&PMASK,32,3,2);
    if (!rotDir)
    {
        angGear0 -= 2;
        angGear1 += 2;
        angGear2 -= 1;
    }
    else
    {
        angGear0 += 2;
        angGear1 -= 2;
        angGear2 += 1;
    }

    if (wf)
        wireFrame(curAng);

    if (upd)
        updateBG = 1;
}

s16 level3d_x[MAX_LINES*4];
s16 level3d_y[MAX_LINES*4];
s16 level3d_z[MAX_LINES*4];

// Change level to 3D
u16 level3D(u8 lvl, u16 n, s16 tz, u8 swap)
{
    u8 i;
    for (i=0;i<MAX_LINES;++i)
    {
        if (lines[lvl][0][i] == LINE_END)
            break;
        if (swap==0)
        {
            level3d_x[n] = -lines[lvl][0][i];
            level3d_y[n] = -lines[lvl][2][i];
            level3d_z[n++] = tz;
            level3d_x[n] = -lines[lvl][1][i];
            level3d_y[n] = -lines[lvl][3][i];
            level3d_z[n++] = tz;
        }
        else
        {
            level3d_z[n] = -lines[lvl][0][i];
            level3d_y[n] = -lines[lvl][2][i];
            level3d_x[n++] = tz;
            level3d_z[n] = -lines[lvl][1][i];
            level3d_y[n] = -lines[lvl][3][i];
            level3d_x[n++] = tz;
        }
    }
    return n;
}

// Draw 3D stuff
void updateGfxLayer3D(s16 tz, u16 n, u8 c)
{
    u16 i;
    s16 res_x[MAX_LINES*4],res_y[MAX_LINES*4];

    clearMem();

    matrix3D(x3d,y3d,z3d,0,0,tz);

    transform3D(level3d_x,level3d_y,level3d_z,res_x,res_y,n);
    for (i=0;i<n;i+=2)
        line(res_x[i],res_y[i],res_x[i+1],res_y[i+1],1);

    if (c)
    {
        transform3D((s16*)cube_x,(s16*)cube_y,(s16*)cube_z,res_x,res_y,8);
        for (i=0;i<12;++i)
            line(res_x[cube_segs[i][0]],res_y[cube_segs[i][0]],res_x[cube_segs[i][1]],res_y[cube_segs[i][1]],1);
    }

    updateBG = 1;
}

/*
 * Presentation stuff
 */

void logo(u8 idx,u8 y)
{
    u16 i;
    SetSprite(idx++,448+24,6,y,FONT_PALIDX1[24],TOP_PRIO);
    for (i=1;i<18;++i)
        SetSprite(idx++,448+24+i,8,0,FONT_PALIDX1[24+i],TOP_PRIO|H_CHAIN|V_CHAIN);
    SetSprite(idx++,448+45,120,8,FONT_PALIDX1[45],TOP_PRIO|H_CHAIN|V_CHAIN);
    for (i=1;i<18;++i)
        SetSprite(idx++,448+45+i,8,0,FONT_PALIDX1[45+i],TOP_PRIO|H_CHAIN|V_CHAIN);
}

void present()
{
    u8 j;
    clearSprites(0);
    loadHCGfx();
    j = printHC((char*) "PDROMS COMPO ENTRY",8,136,0);
    j = printHC((char*) "BY THOR",40,144,j);
    logo(j,0);
#ifdef MOVIES
    showMovie((u16*)TUNNEL_DATA,TUNNEL_COUNT,TUNNEL_DELAY,1);
#endif
    clearSprites(0);
}

void congrats()
{
    u8 j;
    clearSprites(0);
    loadHCGfx();
    j = printHC((char*) "CONGRATULATIONS",20,2,0);
    if (curlvl+1<LEVEL_COUNT)
        printHC((char*) "TRY NEXT LEVEL",24,142,j);
    else
        printHC((char*) "GAME COMPLETED",24,142,j);
#ifdef MOVIES
    showMovie((u16*)SUCCESS_DATA,SUCCESS_COUNT,SUCCESS_DELAY,0);
#endif
    clearSprites(0);
}

void badluck()
{
    u8 j;
    clearSprites(0);
    loadHCGfx();
    j = printHC((char*) "BAD LUCK",48,2,0);
    if (curlvl+1<LEVEL_COUNT)
        printHC((char*) "TRY ONCE AGAIN",24,142,j);
#ifdef MOVIES
    showMovie((u16*)DEATH_DATA,DEATH_COUNT,DEATH_DELAY,0);
#endif
    clearSprites(0);
}

void dancingLogo()
{
    u8 joy,x;
    u16 y=152*4,xx=0;

    ClearScreen(SCR_1_PLANE);
    ClearScreen(SCR_2_PLANE);
    clearSprites(0);
    gfxLayer();

    wavPlayLoop((u8*)music1);
    loadCommon();
    logo(0,152);

    while(((joy=JOYPAD)&J_A)==0)
    {
        x = (u8)((80+tcos[xx&PMASK]*80)>>SHIFT);
        SetSpritePosition(0, x, (u8)(y>>2));
        if (y>0)
            y-=1;
        if (x!=6 || y!=0)
            xx+=2;
        if (!updateBG)
            updateGfxLayer(0,0,1);
        else
            Sleep(1);
    }

    while((joy=JOYPAD)&J_A)
        __ASM("nop");
    wavStop();
    clearSprites(0);
}

u8 menu()
{
    u8 joy;
    u8 m=0,j;
    ClearScreen(SCR_1_PLANE);
    ClearScreen(SCR_2_PLANE);

    clearSprites(0);
    loadHCGfx();
    showHC((void*)MENUBG_ID);
    j = printHC((char*)"NEW",124,98,0);
    j = printHC((char*)"CONTINUE",84,106,j);
    j = printHC((char*)"TUTORIAL",84,114,j);
    SetSprite(j,448+44,150,100,FONT_PALIDX1[44],TOP_PRIO);
    logo(j+1,0);
    wavStop();
    while(1)
    {
        joy=JOYPAD;
        if (joy&J_A)
            break;
        if (joy&J_UP)
            m=m==0?2:m-1;
        else if (joy&J_DOWN)
            m=m==2?0:m+1;
        else
        {
            wavCheck();
            continue;
        }
        while((joy=JOYPAD)&(J_DOWN|J_UP));
        wavPlay((u8*)menusnd);
        SetSprite(j,448+44,150,100+(m*8),FONT_PALIDX1[44],TOP_PRIO);
    }
    while(((joy=JOYPAD)&J_A));
    curimg = 0;
    Sleep(1);
    wavStop();
    clearSprites(0);
    clearPals();
    return m;
}

/*
 * Game stuff
 */

// Game background
const u8 board[19][20] =
{
    {8,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,8},
    {6,1,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,3,6},
    {4,10,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,9,5},
    {0,6,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,6,0},
    {0,6,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,6,0},
    {0,6,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,6,0},
    {0,6,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,6,0},
    {0,6,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,6,0},
    {0,6,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,6,0},
    {0,6,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,6,0},
    {0,6,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,6,0},
    {0,6,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,6,0},
    {0,6,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,6,0},
    {0,6,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,6,0},
    {0,6,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,6,0},
    {0,6,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,6,0},
    {0,6,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,6,0},
    {1,10,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,9,3},
    {4,7,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,7,5}
};

// draw background
void showBoard()
{
    u16 i,j,t,n;
    for (i=0;i<19;++i)
    {
        for (j=0;j<20;++j)
        {
            n = 60+board[i][j];
            t = (i<<5)+j;
            if (n==60)
                n = t_font;
            else
                n = (((u16)(p_font+COMMON_PALIDX1[n]))<<9)+n+t_font;
            (SCROLL_PLANE_1)[t] = n;
        }
    }
}

// draw level
void showLevel(u8 w)
{
    u16 i,j,t,n;
    for (i=0;i<16;++i)
    {
        for (j=0;j<16;++j)
        {
            t = ((i+2)<<5)+j+2;
            n = lvlstate[curstate][i][j];
            if (n>0)
                n = (((u16)(p_font+COMMON_PALIDX1[n+49]))<<9)+n+t_font+50;
            else
                n = t_font;
            (SCROLL_PLANE_1)[t] = n;
        }
    }
    if (remAnim>0)
    {
        n = 45 - remAnim--;
        n = (((u16)(p_font+COMMON_PALIDX1[n]))<<9)+n+t_font;
        for (i=0;i<remCount;++i)
        {
            t = ((((u16)remY[i])+2)<<5)+remX[i]+2;
            (SCROLL_PLANE_1)[t] = n;
        }
    }
    if (w)
        Sleep(w);
}

// clear level tiles
void hideLevel()
{
    u16 i,j,t;
    for (i=0;i<16;++i)
    {
        for (j=0;j<16;++j)
        {
            t = ((i+2)<<5)+j+2;
            (SCROLL_PLANE_1)[t] = t_font;
        }
    }
}

/*
 * Game routs
 */

void rotateLeft()
{
    u16 i,j,s=1-curstate,t;
    for (i=0;i<3;++i)
        tcount[i] = 0;
    for (i=0;i<16;++i)
    {
        for (j=0;j<16;++j)
        {
            t = lvlstate[curstate][15-j][i];
            lvlstate[s][i][j] = t;
            if (t>3 && t<7)
                tcount[t-4] += 1;
        }
    }
    curstate = s;
}

void rotateRight()
{
    u8 i,j,s=1-curstate,t;
    for (i=0;i<3;++i)
        tcount[i] = 0;
    for (i=0;i<16;++i)
    {
        for (j=0;j<16;++j)
        {
            t = lvlstate[curstate][j][15-i];
            lvlstate[s][i][j] = t;
            if (t>3 && t<7)
                tcount[t-4] += 1;
        }
    }
    curstate = s;
}

u8 isMoveable(u8 t)
{
    return t==2 || (t>3 && t<7);
}

u8 gravity()
{
    u8 moved = 0;
    s8 i,j;
    for (i=16;i>=0;--i)
    {
        for (j=0;j<16;++j)
        {
            if (isMoveable(lvlstate[curstate][i][j]) && (i==15 || lvlstate[curstate][i+1][j]==0 || (lvlstate[curstate][i][j]==2 && lvlstate[curstate][i+1][j]==3)))
            {
                if (i<15)
                {
                    if (lvlstate[curstate][i+1][j]==3)
                        tgtx = -1;
                    lvlstate[curstate][i+1][j] = lvlstate[curstate][i][j];
                }
                lvlstate[curstate][i][j] = 0;
                moved = 1;
            }
        }
    }
    return moved;
}

void tagRemove(u8 y, u8 x)
{
    remX[remCount]=x;
    remY[remCount++]=y;
    lvlstate[curstate][y][x]=0;
}

void remove(u8 i,u8 j,u8 t)
{
    if (removed[i][j] || lvlstate[curstate][i][j]!=t)
        return;
    removed[i][j] = 1;
    if (i>0)
        remove(i-1,j,t);
    if (i<15)
        remove(i+1,j,t);
    if (j>0)
        remove(i,j-1,t);
    if (j<15)
        remove(i,j+1,t);
}

u8 reduce()
{
    u8 i,j,k,l,t,tc;
    for (i=0;i<16;++i)
        for (j=0;j<16;++j)
            removed[i][j] = 0;

    for (i=0;i<16;++i)
    {
        for (j=0;j<16;++j)
        {
            if (lvlstate[curstate][i][j]>3 && lvlstate[curstate][i][j]<7)
            {
                if (removed[i][j])
                    continue;
                t = lvlstate[curstate][i][j];
                tc = 0;
                if (i>0 && (lvlstate[curstate][i-1][j]==t))
                    tc+=1;
                if (i<15 && lvlstate[curstate][i+1][j]==t)
                    tc+=1;
                if (j>0 && lvlstate[curstate][i][j-1]==t)
                    tc+=1;
                if (j<15 && lvlstate[curstate][i][j+1]==t)
                    tc+=1;
                if (tc>1)
                   remove(i,j,t);
            }
        }
    }
    remCount = 0;
    srcx = tgtx = -1;
    for (i=0;i<16;++i)
    {
        for (j=0;j<16;++j)
        {
            t = lvlstate[curstate][i][j];
            if (removed[i][j])
            {
                if (--tcount[t-4] == 0)
                {
                    tc = tcount[0]+tcount[1]+tcount[2];
                    t+=3;
                    for (k=0;k<16;++k)
                        for (l=0;l<16;++l)
                            if (lvlstate[curstate][k][l]==t || (tc==0 && lvlstate[curstate][k][l]==10))
                                tagRemove(k,l);
                }
                tagRemove(i,j);
            }
            else if (t==2)
            {
                srcx = j;
                srcy = i;
            }
            else if (t==3)
            {
                tgtx = j;
                tgty = i;
            }
        }
    }
    remAnim = remCount ? 4 : 0;
    return remCount>0;
}

u16 STEPS;
u16 DELTA; // STEPS * DELTA = 128

// Level rotation management
u8 update(u8 rotDir)
{
    u16 i;
    u8 joy;
    u16 delta = rotDir ? DELTA : -DELTA;
    hideLevel();
    while(updateBG)
        __ASM("nop");
    startAngle = curAng;
    for (i=0;i<STEPS;++i)
    {
        joy=JOYPAD;
        if ((rotDir && (joy&J_A)) || ((!rotDir) && (joy&J_B))) // undo requested
            break;
        curAng = (curAng+delta)&PMASK;
        updateGfxLayer(rotDir,1,1);
        while (updateBG)
            __ASM("nop");
    }
    if (i<STEPS) // undo
    {
        for (i=i;i>0;--i)
        {
            curAng = (curAng-delta)&PMASK;
            updateGfxLayer(1-rotDir,1,1);
            while (updateBG)
                __ASM("nop");
        }
        clearSprites(0);
        updateGfxLayer(0,0,1);
        return 0;
    }
    clearSprites(0);
    updateGfxLayer(0,0,1);
    if (rotDir)
        rotateLeft();
    else
        rotateRight();
    showLevel(0);
    remAnim = 0;
    do
    {
        while (remAnim>0)
            showLevel(5);
        while (gravity() && tgtx>=0)
            showLevel(5);
    } while (reduce() && tgtx>=0);
    return 1;
}

/*
 * 3D animation between levels
 */

u16 DELTA3D;

void resetPos()
{
    s16 delta = ((curAng>PRECISION2)?DELTA:-DELTA)<<1;
    u8 rotDir = delta>0?1:0;
    u16 n;
    showLevel(10);
    hideLevel();
    srcx = -1; // no sprite
    while (curAng!=0)
    {
        curAng = (curAng+delta)&PMASK;
        updateGfxLayer(rotDir,1,1);
        while (updateBG)
            __ASM("nop");
    }
    clearSprites(0);
    curlvl += 1;
    n = level3D(curlvl-1,0,72,0);
    n = level3D(curlvl,n,-72,1);
    x3d = z3d = 0;
    for (y3d=0;y3d<128;y3d+=DELTA3D)
    {
        updateGfxLayer3D(-224,n,1);
        while(updateBG)
            __ASM("nop");
    }
}

void clearScreen()
{
    u16 i;
    for (i=0;i<32*32;++i)
        (SCROLL_PLANE_1)[i] = t_font; // empty tile
}

u8 gameMenu()
{
    const char* gmenu[5] = {"RESUME","RESTART","NEXT","PREVIOUS","CANCEL"};
    u8 i,joy=0;
    clearMem();
    copyMem();
    hideLevel();
    for (i=0;i<5;++i)
        print(gmenu[i],6,6+i*2,0);
    i = 1;
    SetSprite(0,t_font+41,40,48+i*16,COMMON_PALIDX1[40],TOP_PRIO);
    while(1)
    {
        joy=JOYPAD;
        if (joy)
        {
            if (joy&J_UP)
                i=i==0?4:i-1;
            else if (joy&J_DOWN)
                i=i==4?0:i+1;
            else if (joy&J_A)
                break;
            while(joy=JOYPAD)
                __ASM("nop");
            SetSprite(0,t_font+41,40,48+i*16,COMMON_PALIDX1[40],TOP_PRIO);
        }
        else
            __ASM("nop");
    }
    while(((joy=JOYPAD)&J_A))
        __ASM("nop");
    clearSprites(0);
    return i;
}

/*
 * Play current level
 */

#define GAME_RESTART 250
#define GAME_NEXT 251
#define GAME_PREVIOUS 252
#define GAME_CANCEL 253

u8 playLevel()
{
    u16 i,j;
    u8 rotDir;
    u8 joy,n=0;
    for (i=0;i<16;++i)
    {
        for (j=0;j<16;++j)
        {
            lvlstate[curstate][i][j]=levels[curlvl][i][j];
            if (lvlstate[curstate][i][j]==2)
            {
                srcx = j;
                srcy = i;
            }
            else if (lvlstate[curstate][i][j]==3)
            {
                tgtx = j;
                tgty = i;
            }
        }
    }
    curAng = 0;
    remAnim = 0;
    print("LVL",3,0,0);
    printi(curlvl+1,7,0);
    print(": PAR ",10,0,0);
    printi(2,16,0);
    print("....LEVEL.....",0,3,1);
    print("....LEVEL.....",19,3,1);
    showLevel(0);
    while(tgtx>=0 && srcx>=0)
    {
        joy=JOYPAD;
        if (joy&J_A)
        {
            while(((joy=JOYPAD)&J_A))
                __ASM("nop");
            rotDir = 0;
        }
        else if (joy&J_B)
        {
            while(((joy=JOYPAD)&J_B))
                __ASM("nop");
            rotDir = 1;
        }
        else if (joy&J_OPTION)
        {
            while(((joy=JOYPAD)&J_OPTION))
                __ASM("nop");
            switch(gameMenu())
            {
                case 0: // resume
                    break;
                case 1:  // restart
                    return GAME_RESTART;
                case 2: // next
                    return GAME_NEXT;
                case 3: // previous
                    return GAME_PREVIOUS;
                case 4: // cancel
                    return GAME_CANCEL;
            }
            showLevel(0);
            continue;
        }
        else
        {
            if (!updateBG)
                updateGfxLayer(0,0,1);
            continue;
        }
        if (update(rotDir) && n<250)
            n += 1;
        showLevel(5);
    }
    return n;
}

void resetBack()
{
    loadCommon();
    gfxLayer();
    showBoard();
}

/*
 * Game main loop
 */
void game()
{
    s16 i;
    u16 n;
    curstate = 0;
    clearScreen();
    resetBack();
    while(curlvl<LEVEL_COUNT)
    {
        n = playLevel();
        switch(n)
        {
            case GAME_RESTART:
                continue;
            case GAME_NEXT:
                if (curlvl<data[4])
                    curlvl+=1;
                continue;
            case GAME_PREVIOUS:
                if (curlvl>0)
                    curlvl-=1;
                continue;
            case GAME_CANCEL:
                return;
        }
        wavStop();
        if (tgtx<0)
        {
            congrats();
            if (curlvl+1<LEVEL_COUNT)
            {
                u8 mod = 0;
                resetBack();
                resetPos();
                // save data if necessary
                if (curlvl>data[4])
                {
                    mod = 1;
                    data[4] = curlvl;
                }
                if (data[4+curlvl]==0 || data[4+curlvl]>n)
                {
                     data[4+curlvl] = n;
                     mod = 1;
                }
                if (mod)
                    flash(data);
            }
            else // end game
                curlvl += 1;
        }
        else
        {
            // you lose 3D anim
            n = level3D(curlvl,0,0,0);
            x3d = y3d = z3d = 0;
            hideLevel();
            for (i=0;i<128;i+=4)
            {
                updateGfxLayer3D(-i-128,n,0);
                while(updateBG)
                    __ASM("nop");
                x3d = (x3d+5)&PMASK;
                y3d = (y3d+7)&PMASK;
                z3d = (z3d+3)&PMASK;
            }
            badluck();
            resetBack();
        }
    }
}

void tutorial()
{
    const char*tuto[30]={
       //01234567890123456789
        "WELCOME!",
        "",
        "THE GOAL IS TO REACH",
        "THE RED SQUARE WITH",
        "THE GREEN BALL.",
        
        "PRESS .A. OR .B. TO",
        "ROTATE THE BOARD",
        "",
        "UNDO IS POSSIBLE",
        "DURING THE ROTATION",

        "COLORED TILES ARE",
        "REMOVED WHEN 3 OF",
        "THEM ARE REGROUPED.",
        "GREY TILES CANT BE",
        "REMOVED.",

        "DARK COLORED TILES",
        "ARE REMOVED ONLY",
        "WHEN ALL TILES OF",
        "THE SAME COLOR HAVE",
        "BEEN REMOVED.",

        "PRESS .OPTION. TO",
        "GET THE INGAME MENU",
        "TO RESTART OR CANCEL",
        "THE CURRENT LEVEL.",
        "",

        "HAVE FUN!",
        "PDROMS.DE RULES!",
        "PDROMS COMPO 4.01",
        "",
        "               THOR"
    };
    u8 joy,i,j;

    ClearScreen(SCR_1_PLANE);
    ClearScreen(SCR_2_PLANE);
    loadCommon();

    logo(0,0);
    
    for (j=0;j<6;++j)
    {
        ClearScreen(SCR_1_PLANE);
        for (i=0;i<5;++i)
            print(tuto[j*5+i],0,6+i*2,0);
        while(((joy=JOYPAD)&J_A)==0)
            __ASM("nop");
        while(((joy=JOYPAD)&J_A))
            __ASM("nop");
    }
}

/*
 * Main
 */

void main()
{
    u8 m;
    InitNGPC();

    ClearScreen(SCR_1_PLANE);
    ClearScreen(SCR_2_PLANE);
    SetBackgroundColour(RGB(0, 0, 0));

    hc_detect();
    if (hc_emu())
    {
        DELTA = 8;
        STEPS = 16;
        DELTA3D = 8;
    }
    else
    {
        DELTA = 16;
        STEPS = 8;
        DELTA3D = 8;
    }

    getSavedData();

    CpuSpeed(0);
    __ASM("    and   (0x20), 0xf7"); // turn off timer 3
    SOUNDCPU_CTRL = 0xaaaa;

    __asm(" di");
    VBL_INT = myVBL;
    __asm(" ei");

    present();
    dancingLogo();

    while(1)
    {
        m = menu();
        switch(m)
        {
            case 0:
                curlvl = 0;
                game();
                break;
            case 1:
                curlvl = data[4];
                if (curlvl>=LEVEL_COUNT)
                    curlvl = 0;
                game();
                break;
            case 2:
                tutorial();
                break;
        }
        wavStop();
    }
}
