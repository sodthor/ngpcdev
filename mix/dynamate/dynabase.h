/*
   Jonas Norberg
*/

#ifndef DYNAMATE_ENGINE_H
#define DYNAMATE_ENGINE_H

typedef unsigned char dm_u8;
typedef   signed char dm_s8;

enum dm_piece_type
{
	DM_T_EMPTY = 0,
	DM_T_EXPL  = 0x10,
	DM_T_TELE  = 0x20
};

/* de fasta bitarna „r + 128
*/
enum dm_piece
{
	DM_P_EMPTY = DM_T_EMPTY,

	DM_P_RED = DM_T_EXPL,
	DM_P_GREEN,
	DM_P_BLUE,
	DM_P_BLACK = DM_T_EXPL + 8,
	DM_P_YELLOW,

	DM_P_HORI = DM_T_TELE, /* denna grupp kan bli full av 'modifiers' */
	DM_P_VERT,
	DM_P_TELE1,
	DM_P_TELE2,
	DM_P_ROTCOL,

	DM_P_ROT_CW,
	DM_P_ROT_CCW,

	DM_P_GRAY = 0x30,
	DM_P_DIE
};

typedef enum
{
	DM_NOTHING,  /* animate nothing */
	DM_MOVE,    /* animate movement (from src to dst) and contionue event-loop */
	DM_EXPL,    /* animate explosion (at src) and contionue event-loop  */
	DM_TELE    /* move piece (from src to dst), clear after it and contionue event-loop  */
} dm_cmds;

typedef enum
{
	DM_NORMAL,		   /* the game is running */
	DM_FINISHED,      /* the level is completed */
	DM_OUT_OF_MOVES,  /* have consumed to many moves */
	DM_OUT_OF_FIELD,  /* have gone out of field */
	DM_HIT_DIE_PIECE, /* have hit the die-piece */
	DM_INFINITE_LOOP /* have entered a infinite loop */
} dm_states;

/* är detta plain c? */
typedef enum
{
	DM_UP = 0,
	DM_RIGHT,
	DM_DOWN,
	DM_LEFT
} dm_dir;



#define SOLID(x)	((dm_u8)(0x80 & (x)))	/* returns 0x80 if solid else 0 */
#define SUBTYPE(x)	((dm_u8)(0x70 & (x))) /* returns 0, 0x10, 0x20, 0x30, 0x40*/
#define L7(x)		((dm_u8)(0x7f & (x)))		/* returns the low 7 bits */

//om man har en bättre kopilator bör man använda detta makro istället för POS-funktionen
#define POS(x, y) ((y)<<4|(x))


/*****************************************
   interfacet ser ut s…h„r ...
   lite extra fult för att funka med c++
 */

#ifdef __cplusplus
extern "C" {
#endif

   extern void  dm_init_level(const dm_u8 *field);
   extern void  dm_init_step(const dm_u8 x, const dm_u8 y, const dm_dir direction );

   extern void dm_depack_move(const char *hs, dm_u8 *x, dm_u8 *y, dm_dir *direction );
   extern dm_u8 dm_step(void); /* anropa tills den returnerar 1 */
   extern dm_u8 dm_cmd(void);  /* vad man skall g÷ra, (tele,expl) (move) stop*/ 
   extern dm_u8 dm_srcx(void);  /* fr…n vilken bit */
   extern dm_u8 dm_srcy(void);  /* fr…n vilken bit */
   extern dm_u8 dm_dstx(void);  /* till vilken bit */
   extern dm_u8 dm_dsty(void);  /* till vilken bit */
   /* info */
   extern const dm_u8 *dm_field(void);     /* pekare till spelplanen */
   extern dm_u8 dm_state(void);      /* huruvida man „r klar, eller gameover */
   extern dm_u8 dm_stones_left(void);
   extern dm_u8 dm_movecount(void);

#ifdef __cplusplus
}
#endif

#endif
