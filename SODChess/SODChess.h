#ifdef WIN32
#include "win32.h"
#include <string.h>
#else
#ifdef NGPC
#include "ngpc.h"
#endif
#endif

#define WHITE	0
#define BLACK	16
#define DEAD	-1

#define INV(a) (s8)(BLACK-a)

//#define NGPC_EMU
//#define EASY
//#define MEDIUM
//#ifndef NGPC
//#define TEST
//#endif

#ifdef TEST

#define DEPTH 6
#define INITDEPTH 0
#define MAXKEEP 32
#define MAX_BACKBOARDS 64

#ifndef SODCHESS_MAIN
const s8 keep[] = {1,2,4,8,16,MAXKEEP};
#endif

#else

#if defined NGPC || defined NGPC_EMU

#define DEPTH 4
#define INITDEPTH 0
#define MAXKEEP 4
#define MAX_BACKBOARDS 12

#ifndef SODCHESS_MAIN
const s8 keep[] = {1,2,3,MAXKEEP};
#endif

#else

#ifdef EASY

#define DEPTH 6
#define INITDEPTH 0
#define MAXKEEP 6
#define MAX_BACKBOARDS 23

#ifndef SODCHESS_MAIN
const s8 keep[] = {1,2,3,4,5,MAXKEEP};
#endif

#else

#ifdef MEDIUM

#define DEPTH 6
#define INITDEPTH 2
#define MAXKEEP 16
#define MAX_BACKBOARDS 43

#ifndef SODCHESS_MAIN
const s8 keep[] = {1,4,4,8,8,MAXKEEP};
#endif

#else

#define DEPTH 6
#define INITDEPTH 2
#define MAXKEEP 32
#define MAX_BACKBOARDS 86

#ifndef SODCHESS_MAIN
const s8 keep[] = {4,8,8,16,16,MAXKEEP};
#endif

#endif // MEDIUM
#endif // EASY
#endif // NGPC
#endif // TEST

#define POS(a,b) (((a)<<3)+b)
#define COL(a) (a&0x07)
#define ROW(a) ((a>>3)&0x07)
#define TYPE(a) (a&0x0f)
#define COLOR(a) (a&BLACK)

#define Q_ROOK		8
#define Q_KNIGHT	9
#define Q_BISHOP	10
#define QUEEN		11
#define KING		12
#define K_BISHOP	13
#define K_KNIGHT	14
#define K_ROOK		15

#define COUNT_IDX 274

typedef struct _control {
	s8 buffer[COUNT_IDX+32];
	u32	pinned;
	s8 passantb,passantw;
} Control;

typedef struct _board {
	s8 pieces[32]; // PPPPPPPPRNBQKBNRpppppppprnbqkbnr
	s8 board[64];
	s8 castle;
	Control c;
	s8 check;
} *Board;

#ifndef SODCHESS_MAIN
//							  4,  4,  4,  4,  4,  4,  4,  4, 14,  8, 13, 27,  8, 13,  8, 14
const s16 control_idx[32] = {  0,  4,  8, 12, 16, 20, 24, 28, 32, 46, 54, 67, 94,102,115,123,
							 137,141,145,149,153,157,161,165,169,183,191,204,231,239,252,260};

#ifdef NGPC

#define Z80_BACKBOARDS 9 // number of boards that can be stored in Z80 memory
Board _bboards_z80 = (Board)0x7000;
struct _board _bboards_tlcs[MAX_BACKBOARDS-Z80_BACKBOARDS];

#else

struct _board _bboards[MAX_BACKBOARDS];

#endif

Board bboards[MAX_BACKBOARDS];
u8 free_bb[MAX_BACKBOARDS];

#else

extern Board bboards[MAX_BACKBOARDS];
extern const s8 control_idx[32];

#endif

#define isCheck(b,color) ((b)->check&(color==WHITE?1:2))
#define copy(dst,src) memcpy(dst,src,sizeof(struct _board));
#define isPinned(b,i) ((b)->c.pinned&(1<<i))

#ifndef SODCHESS_MAIN

const s16 vboard[] = {
	1,1,1,1,1,1,1,1,
	1,2,2,2,2,2,2,1,
	1,2,3,3,3,3,2,1,
	1,2,3,4,4,3,2,1,
	1,2,3,4,4,3,2,1,
	1,2,3,3,3,3,2,1,
	1,2,2,2,2,2,2,1,
	1,1,1,1,1,1,1,1
};

const s16 vpieces[] = {	10,10,10,10,10,10,10,10,50,30,35,100,0,35,30,50,
						10,10,10,10,10,10,10,10,50,30,35,100,0,35,30,50};

const s8 promotion[] = {QUEEN,Q_ROOK,K_ROOK,K_BISHOP,Q_BISHOP,Q_KNIGHT,K_KNIGHT};

#endif

#define MAX 20000
#define DBL_PAWN 60
#define PIECE 5
#define LOCKED 1
#define CASE 1
#define CASTLE 40
#define QUEENMV 100 // penality if queen moves too early
#define QUEENMVLIM 10 // queen move penality limit
#define KINGPROTECT 6

void control(Board,s8);
s16 eval(Board,s8);
s8 play(s8,s8,Board,u8);
void initSearch();
void init(Board);
s16 compute(s8,s8,Board,s16,s16);
unsigned char random();
void userAction();
void progress(s8 max,s8 cur);

#ifdef SODCHESS_MAIN
extern s8 cursrc,curdst;
extern u8 reset,nbmoves;
#else
long curbook;
u8 reset = 0,nbmoves;
extern unsigned char book[];
#endif

void initrand();
