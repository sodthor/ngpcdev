
//#include <stdio.h>
//#include <stdlib.h>

#include "gamedata.h"
/*
    bitar att till…ta

    red, green, blue, black, yellow
    
    change pos        (telep, hori+vert)
    change speed    (rot left, right)
    change color

    solid
    |
    |subtype (empty,exploding, c-pos, c-speed, c-col)
    ||||
   xxxx xxxx <- the byte that describes the piece
        ||||
         specific

    solid:    0 = moving
                1 = solid

    subtype:    0 = empty
                1 = exploding
                2 = change pos, change speed, change color
                3 = gray, die


    stilla och l÷sa av alla
*/
/*
#define P(x) puts(x)
*/
#define P(x)


/*
********************************************************************************
********************************************************************************

  This is the dynamate engine interface
*/


/* (hemlig data)
*/

extern GAME_DATA DATA;

/*
 ********************************************************************************
 ********************************************************************************

 This is the dynamate engine implementation
*/

dm_u8 dm_cmd(void)       {return DATA.data.dynamate.cmd;    } 
dm_u8 dm_srcx(void)       {return DATA.data.dynamate.srcx;    }
dm_u8 dm_srcy(void)       {return DATA.data.dynamate.srcy;    }
dm_u8 dm_dstx(void)       {return DATA.data.dynamate.dstx;    }
dm_u8 dm_dsty(void)       {return DATA.data.dynamate.dsty;    }
dm_u8 dm_state(void)    {return DATA.data.dynamate.state;    }
dm_u8 dm_movecount(void){return DATA.data.dynamate.moves;    }
dm_u8 dm_stones_left(void){return DATA.data.dynamate.stonesleft;    }
const dm_u8 *dm_field(void)   {return DATA.data.dynamate.field;    }

void dm_init_level(const dm_u8 *otherfield)
{
    dm_u8 c;

    DATA.data.dynamate.cmd   = DM_NOTHING;
    DATA.data.dynamate.state = DM_NORMAL;

    DATA.data.dynamate.moves = 0;

    /*
        r„kna stenar
    */
    DATA.data.dynamate.stonesleft = 0;
    
    c = 0;
    do {
      dm_u8 p = DATA.data.dynamate.field[c] = otherfield[c];
        if (SUBTYPE(p) == DM_T_EXPL) DATA.data.dynamate.stonesleft++;

        c++;
    } while (c != 0);
}



static dm_u8 depack(dm_u8 c)
{
   if (c >= 'a')
      c = ((c - 'a')<<1)+1;
   else
      c = ((c - 'A')<<1);
   return c;
}

void dm_depack_move(const char *hs, dm_u8 *x, dm_u8 *y, dm_dir *direction )
{
   const static dm_dir d[] = {DM_UP, DM_RIGHT, DM_DOWN, DM_LEFT};

   dm_u8 a = depack(hs[0]);
   dm_u8 b = depack(hs[1]);
   dm_u8 dir;

   dir = ((a >> 3) & 2) | (b >> 4);
   a &= 15;
   b &= 15;
   
   // kopiera tebax
   *x = a;
   *y = b;
   *direction = d[dir];
}

const static dm_s8 dm_xspeeds[4] = { 0, 1, 0,-1};
const static dm_s8 dm_yspeeds[4] = {-1, 0, 1, 0};

void dm_init_step(const dm_u8 x, const dm_u8 y, const dm_dir direction)
{
   /* se till att räkningen funkar */
   DATA.data.dynamate.cmd = DM_NOTHING;
    
   /* initiera loopskydd*/
    DATA.data.dynamate.dstx = DATA.data.dynamate.firstx = x;
    DATA.data.dynamate.dsty = DATA.data.dynamate.firsty = y;
    DATA.data.dynamate.speedx = DATA.data.dynamate.firstspeedx = dm_xspeeds[direction];
    DATA.data.dynamate.speedy = DATA.data.dynamate.firstspeedy = dm_yspeeds[direction];
}

dm_u8 will_expl(const dm_u8 source, const dm_u8 dest)
{
    /*
    r,g,b 123
    black,yellow 89
    */
    dm_u8 ls, ld, both;

    if (SUBTYPE(source) != DM_T_EXPL) return 0;
    if (SUBTYPE(dest)   != DM_T_EXPL) return 0;

    ls = source & 0xf;
    ld = dest & 0xf;
    both = (ls<<4) | ld;

    switch (both)
    {
        case 0x01: return 1; /* r->g */
        case 0x12: return 1; /* g->b */
        case 0x20: return 1; /* b->r */        

        case 0x80: return 1; /* black->r */        
        case 0x81: return 1; /* black->g */        
        case 0x82: return 1; /* black->b */        
        case 0x88: return 1; /* black->black */        
        case 0x89: return 1; /* black->yellow */        

        case 0x09: return 1; /* r->yellow */        
        case 0x19: return 1; /* g->yellow */        
        case 0x29: return 1; /* b->yellow */        

    }
    return 0;
}

/* find the other teleporter */
dm_u8 find(const dm_u8 pos, const dm_u8 piece)
{
   dm_u8 c = 0;
   P("find");
   while (c < pos)
   {
      dm_u8 tmp = DATA.data.dynamate.field[c];
      tmp = L7(tmp);
      if (tmp == piece) break;
      c++;
   }

   if (c == pos){
      c++;
      while (c != 0)
      {
         dm_u8 tmp = DATA.data.dynamate.field[c];
         tmp = L7(tmp);
         if (tmp == piece) break;
         c++;
      }
   }else return c;


//   if (c == 0){ P("!!!! SERIOUS ERROR !!!!"); exit(-1);}
   return c;
}

/*
   flyger genom teleporters och returnerar f÷rsta icke teleportern
   eller den som tar stopp, fixa skydd mot loopar:

   om man skickar in en tele i en tele skall den stanna, eler …ka igenom?
 */
void tele(void)
{
   dm_u8 dpos, dp, ldp, td, lsp, ts, temp;
   /* vi vet att det „r en tele */

   dpos = POS(DATA.data.dynamate.dstx,DATA.data.dynamate.dsty);
   dp = DATA.data.dynamate.field[dpos];
   ldp = L7(dp);

   lsp = L7(DATA.data.dynamate.sp);
   ts = SUBTYPE(DATA.data.dynamate.sp);

   do {
      P("tele");
      switch (ldp)
      {
         case DM_P_TELE1:
            if (lsp == DM_P_TELE1)
                break;
            dpos = find(dpos,ldp);
            DATA.data.dynamate.dstx = (dpos & 15);
            DATA.data.dynamate.dsty = (dpos >> 4);
            break;

         case DM_P_TELE2:
            if (lsp == DM_P_TELE2)
                break;
            dpos = find(dpos,ldp);
            DATA.data.dynamate.dstx = dpos & 15;
            DATA.data.dynamate.dsty = dpos >> 4;
            break;

         case DM_P_ROTCOL:
            temp = DATA.data.dynamate.sp & 8;
            /* om det är r-g-b rotera */
            if ((ts == DM_T_EXPL) && (temp == 0))
            {
                DATA.data.dynamate.sp ++;
                if ((DATA.data.dynamate.sp & 7) == 3) DATA.data.dynamate.sp &= ~7; 
            }
            break;

         case DM_P_VERT:
            if (DATA.data.dynamate.speedy == 0)
                return;
            break;

         case DM_P_HORI:
            if (DATA.data.dynamate.speedx == 0)
                return;
            break;

         case DM_P_ROT_CW:
            temp = DATA.data.dynamate.speedx;
            DATA.data.dynamate.speedx = -DATA.data.dynamate.speedy;
            DATA.data.dynamate.speedy = temp;
            break;

         case DM_P_ROT_CCW:
            temp = DATA.data.dynamate.speedx;
            DATA.data.dynamate.speedx = DATA.data.dynamate.speedy;
            DATA.data.dynamate.speedy = -temp;
            break;
      }

      DATA.data.dynamate.dstx += DATA.data.dynamate.speedx;
      DATA.data.dynamate.dsty += DATA.data.dynamate.speedy;

      dpos = POS(DATA.data.dynamate.dstx,DATA.data.dynamate.dsty);
      dp = DATA.data.dynamate.field[dpos];
      ldp = L7(dp);
      td = SUBTYPE(dp);

   } while ( td == DM_T_TELE );
}

/* ta ett steg */
dm_u8 dm_step(void)
{
   dm_u8 spos, lsp, dpos, dp, ldp;
   dm_u8 prev_cmd = DATA.data.dynamate.cmd;

   if ( DATA.data.dynamate.moves > 200 )
   {
      DATA.data.dynamate.state = DM_OUT_OF_MOVES;
      DATA.data.dynamate.cmd = DM_NOTHING;
   }
   
   /* move */
    DATA.data.dynamate.srcx = DATA.data.dynamate.dstx;
    DATA.data.dynamate.srcy = DATA.data.dynamate.dsty;
    DATA.data.dynamate.dstx += DATA.data.dynamate.speedx;
    DATA.data.dynamate.dsty += DATA.data.dynamate.speedy;

    /* ladda in bitarna */
    spos = POS(DATA.data.dynamate.srcx,DATA.data.dynamate.srcy);
    DATA.data.dynamate.sp = DATA.data.dynamate.field[spos];
    
   /* om man kan flytta denna bit */
   if (SOLID(DATA.data.dynamate.sp) || SUBTYPE(DATA.data.dynamate.sp) == DM_T_EMPTY)
   {
       DATA.data.dynamate.state = DM_NORMAL;
       DATA.data.dynamate.cmd = DM_NOTHING;
       return 1;
   }
   

   /* om utanf÷r*/
    if ( DATA.data.dynamate.dstx > 15 ||  DATA.data.dynamate.dsty > 15 )
    {
      P("out of field");
        DATA.data.dynamate.state = DM_OUT_OF_FIELD;
        DATA.data.dynamate.cmd = DM_NOTHING;
        /* om man exploderade nyss så är det ok*/
        if (prev_cmd == DM_EXPL)
        {
         P("prev explode");
            DATA.data.dynamate.cmd = DM_EXPL;
            DATA.data.dynamate.field[spos] = DM_P_EMPTY;
            DATA.data.dynamate.state = DM_NORMAL;
            /* kolla om man har klarat banan*/
            DATA.data.dynamate.stonesleft--;
            if (DATA.data.dynamate.stonesleft == 0)
                DATA.data.dynamate.state=DM_FINISHED;
        }
        return 1;
    }
  
    /* ladda in bitarna */
    dpos = POS(DATA.data.dynamate.dstx,DATA.data.dynamate.dsty);
    dp = DATA.data.dynamate.field[dpos];

    DATA.data.dynamate.cmd = DM_MOVE;
    
   /* om man skall teleportera */
    if (SUBTYPE(dp) == DM_T_TELE)
    {
        P("istele");
        DATA.data.dynamate.cmd = DM_TELE;
        tele(); /* „ndrar p… dst */

        /* om utanf÷r*/
        if (DATA.data.dynamate.dstx > 15 || DATA.data.dynamate.dsty > 15 )
        {
            P("out of field");
            DATA.data.dynamate.state = DM_OUT_OF_FIELD;
            DATA.data.dynamate.cmd = DM_NOTHING;
            /* om man exploderade nyss så är det ok*/
            if (prev_cmd == DM_EXPL)
            {
                P("prev explode");
                DATA.data.dynamate.cmd = DM_EXPL;
                DATA.data.dynamate.field[spos] = DM_P_EMPTY;
                DATA.data.dynamate.state = DM_NORMAL;
                /* kolla om man har klarat banan*/
                DATA.data.dynamate.stonesleft--;
                if (DATA.data.dynamate.stonesleft == 0)
                    DATA.data.dynamate.state=DM_FINISHED;
            }
            return 1;
        }

        dpos = POS(DATA.data.dynamate.dstx,DATA.data.dynamate.dsty);
        dp = DATA.data.dynamate.field[dpos];
    }

    lsp=L7(DATA.data.dynamate.sp);
    ldp=L7(dp);
/* 
   printf("outside-dst = %d, %d\n", (int)dstx, (int)dsty);
   printf("outside-dpos = %u\n", (int)dpos);
   printf("outside-dp = %u\n", (int)dp);
   printf("outside-ldp = %u\n", (int)ldp);
*/

    /* om man skall explodera */
    if (will_expl(lsp,ldp))
    {
        P("will explode");
        DATA.data.dynamate.field[spos] = DM_P_EMPTY;
        DATA.data.dynamate.field[dpos] = DATA.data.dynamate.sp;
        DATA.data.dynamate.cmd = DM_EXPL;
        DATA.data.dynamate.stonesleft--;
        if (prev_cmd == DM_NOTHING)
            DATA.data.dynamate.moves++;
        return 0;
    }

    /* om det har exploderat klart */
    if (prev_cmd == DM_EXPL)
    {
        P("prev explode");
        DATA.data.dynamate.field[spos] = DM_P_EMPTY;
        DATA.data.dynamate.stonesleft--;
        /* kolla om man har klarat banan*/
        if (DATA.data.dynamate.stonesleft == 0)
            DATA.data.dynamate.state=DM_FINISHED;
        DATA.data.dynamate.cmd = DM_EXPL;
        return 1;
    }


   /* skydd mot evig loop */
    if ( (DATA.data.dynamate.firstx == DATA.data.dynamate.dstx) &&
         (DATA.data.dynamate.firsty == DATA.data.dynamate.dsty) &&
         (DATA.data.dynamate.firstspeedx == DATA.data.dynamate.speedx) &&
         (DATA.data.dynamate.firstspeedy == DATA.data.dynamate.speedy)
       )
    {
        DATA.data.dynamate.state = DM_INFINITE_LOOP;
        DATA.data.dynamate.cmd = DM_NOTHING;
        return 1;
    }

    
    /* flytta biten */
    if ( DM_P_EMPTY == ldp )
    {
        P("empty");
        DATA.data.dynamate.field[spos] = DM_P_EMPTY;
        DATA.data.dynamate.field[dpos] = DATA.data.dynamate.sp;
        if (prev_cmd == DM_NOTHING)
            DATA.data.dynamate.moves++;
        return 0;
    }


    DATA.data.dynamate.cmd = DM_NOTHING;

    if ( DM_P_DIE == ldp )
    {
        P("die");
        DATA.data.dynamate.state = DM_HIT_DIE_PIECE;
        return 1;
    }

    P("just stop");

    return 1;
}
