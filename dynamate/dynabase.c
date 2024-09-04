
//#include <stdio.h>
//#include <stdlib.h>

#include "dynabase.h"
/*
	bitar att till…ta

	red, green, blue, black, yellow
	
	change pos		(telep, hori+vert)
	change speed	(rot left, right)
	change color

	solid
	|
	|subtype (empty,exploding, c-pos, c-speed, c-col)
	||||
   xxxx xxxx <- the byte that describes the piece
        ||||
	     specific

	solid:	0 = moving
				1 = solid

	subtype:	0 = empty
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


/* statisk (hemlig data)
*/

static dm_u8   field[256];	/* banan */
static dm_u8	state;		/* om man har d÷tt */

static dm_u8	moves;
static dm_u8	stonesleft;

static dm_u8	cmd;	

static dm_u8	srcx;		/* the pos (after dm_step, s… „r det den f÷rra positionen)*/
static dm_u8	srcy;		/* the pos (after dm_step, s… „r det den f÷rra positionen)*/
static dm_u8    sp;         /* the piece that */
static dm_u8	dstx;		/* the position */
static dm_u8	dsty;		/* the position */

static dm_s8	speedx;		/* internal, the way it is heading */
static dm_s8	speedy;		/* internal, the way it is heading */
static dm_u8	firstx;		/* internal, f÷r att hindra loop */
static dm_u8	firsty;		/* internal, f÷r att hindra loop */
static dm_s8	firstspeedx;	/* internal, f÷r att hindra loop */
static dm_s8	firstspeedy;	/* internal, f÷r att hindra loop */


/*
 ********************************************************************************
 ********************************************************************************

 This is the dynamate engine implementation
*/

dm_u8 dm_cmd(void)	   {return cmd;	} 
dm_u8 dm_srcx(void)	   {return srcx;	}
dm_u8 dm_srcy(void)	   {return srcy;	}
dm_u8 dm_dstx(void)	   {return dstx;	}
dm_u8 dm_dsty(void)	   {return dsty;	}
dm_u8 dm_state(void)    {return state;	}
dm_u8 dm_movecount(void){return moves;	}
dm_u8 dm_stones_left(void){return stonesleft;	}
const dm_u8 *dm_field(void)   {return field;	}

void dm_init_level(const dm_u8 *otherfield)
{
	dm_u8 c;

	cmd   = DM_NOTHING;
	state = DM_NORMAL;

	moves     = 0;

	/*
		r„kna stenar
	*/
	stonesleft = 0;
	
	c = 0;
	do {
      dm_u8 p = field[c] = otherfield[c];
		if (SUBTYPE(p) == DM_T_EXPL) stonesleft++;

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
   cmd = DM_NOTHING;
	
   /* initiera loopskydd*/
	dstx = firstx = x;
	dsty = firsty = y;
	speedx = firstspeedx = dm_xspeeds[direction];
	speedy = firstspeedy = dm_yspeeds[direction];
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
	  dm_u8 tmp = field[c];
	  tmp = L7(tmp);
      if (tmp == piece) break;
      c++;
   }

   if (c == pos){
      c++;
      while (c != 0)
      {
	     dm_u8 tmp = field[c];
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
   dm_u8 dpos, dp, ldp, td, lsp, ts;
   /* vi vet att det „r en tele */

   dpos = POS(dstx,dsty);
   dp = field[dpos];
   ldp = L7(dp);
   lsp = L7(sp);
   ts = SUBTYPE(sp);

   do {
      P("tele");
      switch (ldp)
      {
         case DM_P_TELE1:
            {
               if (lsp == DM_P_TELE1) break;
               dpos = find(dpos,ldp);          
               dstx = dpos & 15;
               dsty = dpos >> 4;
            } break;

         case DM_P_TELE2:
            {
               if (lsp == DM_P_TELE2) break;
               dpos = find(dpos,ldp);          
               dstx = dpos & 15;
               dsty = dpos >> 4;
            } break;

         case DM_P_ROTCOL:
            {
			   dm_u8 temp = sp & 8;
               /* om det är r-g-b rotera */
               if ((ts == DM_T_EXPL) && (temp == 0))
               {
                  sp ++;
                  if ((sp & 7) == 3) sp &= ~7; 
               }
            } break;
         case DM_P_VERT:
            {
               if (speedy == 0) return;
            } break;

         case DM_P_HORI:
            {
               if (speedx == 0) return;
            } break;

         case DM_P_ROT_CW:
            {
               dm_u8 x = speedx;
               speedx = -speedy;
               speedy = x;
            } break;

         case DM_P_ROT_CCW:
            {
               dm_u8 x = speedx;
               speedx = speedy;
               speedy = -x;
            } break;
      }

      dstx += speedx;
      dsty += speedy;

      dpos = POS(dstx,dsty);
      dp = field[dpos];
      ldp = L7(dp);
	  td = SUBTYPE(dp);

   } while ( td == DM_T_TELE );
}

/* ta ett steg */
dm_u8 dm_step(void)
{
   dm_u8 spos, lsp, dpos, dp, ldp;
   dm_u8 prev_cmd = cmd;

   if ( moves > 200 )
   {
      state = DM_OUT_OF_MOVES;
      cmd = DM_NOTHING;
   }
   
   /* move */
	srcx = dstx;
	srcy = dsty;
	dstx += speedx;
	dsty += speedy;

	/* ladda in bitarna */
	spos = POS(srcx,srcy);
	sp = field[spos];
	
   /* om man kan flytta denna bit */
   if (SOLID(sp) || SUBTYPE(sp) == DM_T_EMPTY)
   {
      state = DM_NORMAL;
		cmd = DM_NOTHING;
		return 1;
   }
   

   /* om utanf÷r*/
	if ( dstx > 15 ||  dsty > 15 )
	{
      P("out of field");
		state = DM_OUT_OF_FIELD;
		cmd = DM_NOTHING;
      /* om man exploderade nyss så är det ok*/
      if (prev_cmd == DM_EXPL)
      {
         P("prev explode");
         cmd = DM_EXPL;
         field[spos] = DM_P_EMPTY;
         state = DM_NORMAL;
         /* kolla om man har klarat banan*/
         stonesleft--;
         if (stonesleft == 0) state=DM_FINISHED;
      }
      return 1;
	}
  
	/* ladda in bitarna */
	dpos = POS(dstx,dsty);
	dp = field[dpos];

   cmd = DM_MOVE;
	
   /* om man skall teleportera */
	if (SUBTYPE(dp) == DM_T_TELE)
   {
		P("istele");
		cmd = DM_TELE;
		tele(); /* „ndrar p… dst */

      /* om utanf÷r*/
      if (dstx > 15 || dsty > 15 )
      {
         P("out of field");
         state = DM_OUT_OF_FIELD;
         cmd = DM_NOTHING;
         /* om man exploderade nyss så är det ok*/
         if (prev_cmd == DM_EXPL)
         {
            P("prev explode");
            cmd = DM_EXPL;
            field[spos] = DM_P_EMPTY;
            state = DM_NORMAL;
            /* kolla om man har klarat banan*/
            stonesleft--;
            if (stonesleft == 0) state=DM_FINISHED;
         }
         return 1;
      }

      	dpos = POS(dstx,dsty);
		dp = field[dpos];
	}

	lsp=L7(sp);
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
		field[spos] = DM_P_EMPTY;
		field[dpos] = sp;
		cmd = DM_EXPL;
		stonesleft--;
      if (prev_cmd == DM_NOTHING) moves++;
		return 0;
	}

	/* om det har exploderat klart */
	if (prev_cmd == DM_EXPL)
    {
		P("prev explode");
		field[spos] = DM_P_EMPTY;
		stonesleft--;
		/* kolla om man har klarat banan*/
		if (stonesleft == 0) state=DM_FINISHED;
		cmd = DM_EXPL;
      return 1;
	}


   /* skydd mot evig loop */
   if ( (firstx == dstx) &&
	    (firsty == dsty) &&
        (firstspeedx == speedx) &&
		(firstspeedy == speedy)
	  )
   {
      state = DM_INFINITE_LOOP;
		cmd = DM_NOTHING;
		return 1;
   }

	
    /* flytta biten */
	if ( DM_P_EMPTY == ldp )
	{
		P("empty");
		field[spos] = DM_P_EMPTY;
		field[dpos] = sp;
        if (prev_cmd == DM_NOTHING) moves++;
		return 0;
	}


	cmd = DM_NOTHING;

	if ( DM_P_DIE == ldp )
	{
		P("die");
		state = DM_HIT_DIE_PIECE;
		return 1;
	}

	P("just stop");

	return 1;
}
