#include "ngpc.h"
#include "dynamate/dynabase.h"

#define GEMS_SAVED_OFFSET 4
#define DYNAMATE_SAVED_OFFSET (GEMS_SAVED_OFFSET + 6 * 10)
#define VEXED_SAVED_OFFSET (DYNAMATE_SAVED_OFFSET + 30)
#define END_SAVED_OFFSET (VEXED_SAVED_OFFSET + 538)

typedef struct
{
    dm_u8   field[256];	/* banan */
    dm_u8	state;		/* om man har d÷tt */

    dm_u8	moves;
    dm_u8	stonesleft;

    dm_u8	cmd;	

    dm_u8	srcx;		/* the pos (after dm_step, s… „r det den f÷rra positionen)*/
    dm_u8	srcy;		/* the pos (after dm_step, s… „r det den f÷rra positionen)*/
    dm_u8   sp;         /* the piece that */
    dm_u8	dstx;		/* the position */
    dm_u8	dsty;		/* the position */

    dm_s8	speedx;		/* internal, the way it is heading */
    dm_s8	speedy;		/* internal, the way it is heading */
    dm_u8	firstx;		/* internal, f÷r att hindra loop */
    dm_u8	firsty;		/* internal, f÷r att hindra loop */
    dm_s8	firstspeedx;	/* internal, f÷r att hindra loop */
    dm_s8	firstspeedy;	/* internal, f÷r att hindra loop */

    volatile u8 tutorial;
} DYNAMATE_DATA;

typedef struct
{
    volatile u8 cursx,cursy,destx,desty,dir;
    volatile s8 cdx,cdy;
    volatile u8 inmove;
    volatile u8 ismoving;
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
} GEMS_DATA;

typedef struct
{
    u16 idx;
    volatile u8 cursx,cursy,destx,desty,dir;
    volatile s8 cdx,cdy;
    volatile u8 inmove;
    volatile u8 ismoving;
    u8 pack;
    u8 lvl;
    u8 board[10][8];
    u8 sol[96];
    u8 solen;
    u8 solve;
    u8 cur;
    u8 count;
} VEXED_DATA;

typedef union
{
    GEMS_DATA gems;
    DYNAMATE_DATA dynamate;
    VEXED_DATA vexed;
} UNION_DATA;

typedef struct
{
    volatile void *curimg;
    volatile u8 ingame;
    u8 curs;
    u8 cpt;
	UNION_DATA data;
} GAME_DATA;
