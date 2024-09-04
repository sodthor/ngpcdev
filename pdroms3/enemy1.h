#include "img.h"

#define ENEMY1_WIDTH 3
#define ENEMY1_HEIGHT 2
#define ENEMY1_TILES 6

#define ENEMY1_NPALS1 3

const u16 ENEMY1_TILES1[48] = {
0x0015,0x016a,0x06ff,0x1b95,0x1e41,0x6d05,0x6d1a,0x6d5a,
0x5400,0xa940,0xff90,0x56e4,0x41b4,0x5079,0xa479,0xa579,
0x0000,0x0550,0x1aa4,0x1be4,0x1be4,0x1aa4,0x0550,0x0000,
0x6d1f,0x6d07,0x6d01,0x1e40,0x1b95,0x06ff,0x016a,0x0015,
0x589e,0x609e,0x809e,0x02d8,0xab78,0x55e0,0xfe80,0xa800,
0x0000,0x1824,0x2d78,0x0410,0x0410,0x2d78,0x1824,0x0000
};

const u16 ENEMY1_PALS1[12] = {
0x0000,0x000c,0x010e,0x044f,
0x0000,0x0808,0x0c0c,0x0f0f,
0x0000,0x088f,0x000c,0x044f
};

const u8 ENEMY1_PALIDX1[6] = {
0x00,0x00,0x01,
0x00,0x02,0x01
};

#define ENEMY1_ID {ENEMY1_WIDTH,ENEMY1_HEIGHT,ENEMY1_TILES,(u16*)ENEMY1_TILES1,(u16*)0,ENEMY1_NPALS1,(u16*)ENEMY1_PALS1,0,(u16*)0,(u8*)ENEMY1_PALIDX1,(u8*)0}

const SOD_IMG ENEMY1_IMG = ENEMY1_ID;

