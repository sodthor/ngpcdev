#include "ngpc.h"
#include "library.h"
#include "flash.h"
#include "hicolor.h"
#include "gamedata.h"

#include "dynamate/sprites.h"
#include "dynamate/dynabase.h"
#include "dynamate/levels.h"
#include "dynamate/demo.h"
#include "dynamate/menu.hh"
#include "musix/bustamove0.mh"
#include "musix/bustamove1.mh"
#include "musix/bustamove2.mh"

#define TUTORIAL 255

extern volatile u8 VBCounter;

extern GAME_DATA DATA;

void __interrupt hicolorVBL()
{
    u8 j;
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
        *(SPRITE_RAM) = 20+DATA.curs;
        if (--DATA.cpt==0)
        {
            DATA.curs = (DATA.curs+1)%4;
            DATA.cpt = 5;
        }
        if (DATA.data.dynamate.tutorial && (((j=JOYPAD)&J_A)||(j&J_B)))
            DATA.data.dynamate.tutorial = 2;
            
    }
}

void dynamate_showHC(void *img)
{
    DATA.curimg=0;
    hc_load(img);
    DATA.curimg=img;
}

void dynamate_setTile(u16* plane,u16 x,u16 y,u16 tile)
{
    plane[y*32+x] = tile+(((u16)sprites_palidx[tile])<<9);
}

void dynamate_print(const char *s,u16 x,u16 y,u8 dir)
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
        dynamate_setTile(SCROLL_PLANE_1,x+(dir?0:i),y+(dir?i:0),30+c);
    }
}

void dynamate_printl(const char *s,u16 x,u16 y,u8 dir,u8 l)
{
    char buf[32];
    u8 i;
    for (i=0;s[i];i++)
        buf[i]=s[i];
    while (i<l)
        buf[i++]=' ';
    buf[i]=0;
    dynamate_print(buf,x,y,dir);
}

void dynamate_printi(int i,u16 x,u16 y,u8 dir,u8 l)
{
    char buf[9];
    u8 j = 9;
    buf[j]=0;
    do
    {
        buf[--j]='0'+(i%10);
        i /= 10;
    } while (i>0);
    dynamate_printl(buf+j,x,y,dir,l);
}

void dynamate_loadGfx()
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

void dynamate_loadSpr(u16 offset)
{
    u16 i;
    u16 *p0 = TILE_RAM+(offset*8),*p1 = SPRITE_PALETTE;
    for (i=0;i<SPRITES_TILES*8;i++)
        p0[i] = sprites_tiles[i];
    for (i=0;i<SPRITES_PALS*4;i++)
        p1[i] = sprites_pals[i];
}

void dynamate_moveCursorTo(u8 x,u8 y,u8 nx,u8 ny,s8 d)
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

u8 dynamate_fastMove(u8 *x,u8 *y,s8 dx,s8 dy)
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
    dynamate_moveCursorTo(*x,*y,l*8,k*8,2);
    *x = l*8;
    *y = k*8;
    return 1;
}

void dynamate_update(u16 x,u16 y)
{
    u8 p = dm_field()[(y<<4)+x];
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
            spr = (p&0x80)?5:14;
            break;
        case DM_P_DIE:
            spr = 16;
            break;
    }
    dynamate_setTile(SCROLL_PLANE_2,x,y,spr);
    spr = ((p&0x7f)==DM_P_ROTCOL) ? 12 : (p&0x80) && (p!=DM_P_GRAY+0x80) && (p!=DM_P_DIE+0x80) ? 13 : 0;
    dynamate_setTile(SCROLL_PLANE_1,x,y,spr);
}

void dynamate_draw_level()
{
   u8 x,y;
   for (y = 0 ; y < 16 ; y++)
      for (x = 0 ; x < 16 ; x++)
         dynamate_update(x,y);
}

u8 dynamate_getMove(u8 *x,u8 *y,u8 *d,u8 lvl)
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
                if (dynamate_fastMove(&xx,&yy,-1,0))
                    continue;
                *d = DM_LEFT;
                break;
            }
            if (j&J_RIGHT)
            {
                if (dynamate_fastMove(&xx,&yy,1,0))
                    continue;
                *d = DM_RIGHT;
                break;
            }
            if (j&J_UP)
            {
                if (dynamate_fastMove(&xx,&yy,0,-1))
                    continue;
                *d = DM_UP;
                break;
            }
            if (j&J_DOWN)
            {
                if (dynamate_fastMove(&xx,&yy,0,1))
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
                dynamate_print(" RESET  SELECT LEVEL",0,18,0);
                dynamate_print("B TO EXIT",19,1,1);
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
                dynamate_print("         ",19,1,1);
                SetSpritePosition(1,160,144);
                while (i=JOYPAD);
                if ((j&J_OPTION))
                    return 3;
                else if ((j&J_B))
                    return 4;
                if (k)
                {
                    dynamate_print("LEFT/RIGHT TO SELECT",0,18,0);
                    do
                    {
                        if (j&J_LEFT)
                            lvl = lvl>0 ? lvl-1 : 0;
                        else if (j&J_RIGHT)
                            lvl = (lvl<NB_LEVELS-1 && data[DYNAMATE_SAVED_OFFSET+lvl]>0) ? lvl+1 : lvl;
                        if (j)
                        {
                            dm_init_level(dml_levels[lvl].field);
                            dynamate_printl(dml_levels[lvl].name,0,1,1,16);
                            dynamate_draw_level();
                            SL_PlaySFX(1);
                            Sleep(10);
                        }
                    } while (!((j=JOYPAD)&J_A));
                    return lvl+5;
                }
                else
                    return 2;
            }
            dynamate_moveCursorTo(ox,oy,xx,yy,1);
        }
    }
    *x = xx/8;
    *y = yy/8;
    return j&J_A?1:0;
}

void dynamate_animateexpl(u8 x,u8 y)
{
}

void dynamate_showMoveC(u8 lvl)
{
    dynamate_print(" MOVES:     BEST:",0,18,0);
    if (data[DYNAMATE_SAVED_OFFSET+lvl]>0)
        dynamate_printi(data[DYNAMATE_SAVED_OFFSET+lvl],17,18,0,4);
    else
        dynamate_printl("NO",17,18,0,3);
}

void dynamate_play(u8 lvl)
{
    u8 i,j,x,y,d,cx=0,cy=0,ds=0,dd=0,dn=demo_nb[0];

    if (lvl==TUTORIAL)
    {
        SL_LoadGroup(bustamove2,sizeof(bustamove2));
        DATA.data.dynamate.tutorial = 1;
    }
    else
    {
        SL_LoadGroup(bustamove1,sizeof(bustamove1));
        DATA.data.dynamate.tutorial = 0;
    }
    SL_PlayTune(0);

    DATA.ingame = 1;

    // draw borders
    for (i=0;i<16;i++)
    {
        dynamate_setTile(SCROLL_PLANE_1,i+2,0,25);
        dynamate_setTile(SCROLL_PLANE_1,i+2,17,25);
        dynamate_setTile(SCROLL_PLANE_1,1,i+1,24);
        dynamate_setTile(SCROLL_PLANE_1,18,i+1,24);
    }
    dynamate_setTile(SCROLL_PLANE_1,1,0,27);
    dynamate_setTile(SCROLL_PLANE_1,18,0,29);
    dynamate_setTile(SCROLL_PLANE_1,1,17,26);
    dynamate_setTile(SCROLL_PLANE_1,18,17,28);

    SetSprite(0,20,0,16,8,sprites_palidx[20],TOP_PRIO);

    do
    {
        if (lvl!=TUTORIAL)
        {
            dm_init_level(dml_levels[lvl].field);
            dynamate_printl(dml_levels[lvl].name,0,1,1,16);
            dynamate_showMoveC(lvl);
        }
        else
        {
            dm_init_level(demo_level.field);
            dynamate_printl(demo_level.name,0,1,1,16);
        }

        dynamate_draw_level();
        do
        {
            x = cx;
            y = cy;
            if (lvl!=TUTORIAL)
            {
                dynamate_printi(dm_movecount(),7,18,0,3);
                j = dynamate_getMove(&x,&y,&d,lvl);
            }
            else
            {
                if (DATA.data.dynamate.tutorial==2)
                    return;
                dynamate_print(demo_texts[ds],3,3,0);
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
                    dynamate_moveCursorTo(cx*8,cy*8,x*8,y*8,2);
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
                dynamate_showMoveC(lvl);
                dynamate_printi(dm_movecount(),7,18,0,3);
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
                            dynamate_update(dm_dstx(),dm_dsty());
                            dynamate_update(dm_srcx(),dm_srcy());
                        }
                        break;
                    case DM_EXPL:
                        {
                            SL_PlaySFX(11);
                            dynamate_animateexpl(dm_srcx(),dm_srcy());
                            dynamate_update(dm_srcx(),dm_srcy());
                        }
                        break;
                }
                Sleep(lvl==TUTORIAL?5:3);
            } while (!i);

            if (j)
            {
                cx = dm_srcx();
                cy = dm_srcy();
                dynamate_moveCursorTo(x*8,y*8,dm_srcx()*8,dm_srcy()*8,8);
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
                        dynamate_print("      HAVE FUN      ",0,18,0);
                        break;
                    }
                    else
                    {
                        if (data[DYNAMATE_SAVED_OFFSET+lvl]==0 || dm_movecount()<data[DYNAMATE_SAVED_OFFSET+lvl])
                        {
                            data[DYNAMATE_SAVED_OFFSET+lvl] = dm_movecount();
                            flash(data);
                            dynamate_print("  NEW BEST SCORE !  ",0,18,0);
                        }
                        else
                            dynamate_print("  CONGRATULATIONS!  ",0,18,0);
                        lvl++;
                    }
                    SL_PlaySFX(7);
                }
                else
                    continue;
                break;
            case DM_OUT_OF_MOVES:
                dynamate_print("   OUT OF MOVES !   ",0,18,0);
                SL_PlaySFX(8);
                break;
            case DM_OUT_OF_FIELD:
                dynamate_print("   OUT OF FIELD !   ",0,18,0);
                SL_PlaySFX(8);
                break;
            case DM_HIT_DIE_PIECE:
                dynamate_print("  HIT DIE PIECE !   ",0,18,0);
                SL_PlaySFX(8);
                break;
            case DM_INFINITE_LOOP:
                dynamate_print("  INFINITE LOOP !   ",0,18,0);
                SL_PlaySFX(8);
                break;
        }
        if (i<255)
            Sleep(120);
    } while (lvl<NB_LEVELS);
    if (lvl!=TUTORIAL)
    {
        dynamate_print("YOU HAVE ALL CLEARED",0,18,0);
        Sleep(120);
    }
    SetSpritePosition(0,160,0);
}

u8 dynamate_mainMenu()
{
    u8 j = 0,k=data[DYNAMATE_SAVED_OFFSET]>0?1:0;
    const u8 idx[] = {80,93,106};

    SL_LoadGroup(bustamove0,sizeof(bustamove0));
    SL_PlayTune(0);

    ClearSprites();

    dynamate_loadSpr(420);
    dynamate_showHC((void*)MENU_ID);

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
    if (j&J_B)
        k = 3;
    while((((JOYPAD)&J_A) != 0) || (((JOYPAD)&J_B) != 0));

    DATA.curimg = 0;

    ClearScreen(SCR_1_PLANE);
    ClearScreen(SCR_2_PLANE);
    return k;
}

void dynamate_main()
{
    u8 i = 0;

    DATA.ingame = 0;
    DATA.curs = 0;
    DATA.cpt = 1;
    DATA.curimg = 0;

    ClearAll();

    __asm(" di");
    VBL_INT = hicolorVBL;
    __asm(" ei");

    while(i != 3)
    {
        i = dynamate_mainMenu();
        dynamate_loadGfx();
        switch(i)
        {
            case 1:
                for (i=0;i<NB_LEVELS-1 && data[DYNAMATE_SAVED_OFFSET+i]!=0;i++);
            case 0:
                dynamate_play(i);
                break;
            case 2:
                dynamate_play(TUTORIAL);
                while(i=JOYPAD);
                break;
        }
        DATA.ingame = 0;
    }
}
