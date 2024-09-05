#include "img.h"

#define ENEMY0_WIDTH 3
#define ENEMY0_HEIGHT 2
#define ENEMY0_TILES 6

#define ENEMY0_NPALS1 3

const u16 ENEMY0_TILES1[48] = {
0x0000,0x0000,0x0150,0x0594,0x06a5,0x06a7,0x0667,0x0669,
0x0000,0x0000,0x0540,0x1650,0x5a90,0xda90,0xd990,0x6990,
0x0000,0x0550,0x1aa4,0x1be4,0x1be4,0x1aa4,0x0550,0x0000,
0x06ba,0x05bf,0x01aa,0x0165,0x0064,0x0054,0x0000,0x0000,
0xae90,0xfe50,0xaa40,0x5940,0x1900,0x1500,0x0000,0x0000,
0x0000,0x1824,0x2d78,0x0410,0x0410,0x2d78,0x1824,0x0000
};

const u16 ENEMY0_PALS1[12] = {
0x0000,0x0756,0x0a88,0x0c04,
0x0000,0x0088,0x00cc,0x00ff,
0x0000,0x0756,0x0a88,0x0860
};

const u8 ENEMY0_PALIDX1[6] = {
0x00,0x00,0x01,
0x02,0x02,0x01
};

#define ENEMY0_ID {ENEMY0_WIDTH,ENEMY0_HEIGHT,ENEMY0_TILES,(u16*)ENEMY0_TILES1,(u16*)0,ENEMY0_NPALS1,(u16*)ENEMY0_PALS1,0,(u16*)0,(u8*)ENEMY0_PALIDX1,(u8*)0}

const SOD_IMG ENEMY0_IMG = ENEMY0_ID;
