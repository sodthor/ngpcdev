#include "img.h"

#define COMMON_WIDTH 11
#define COMMON_HEIGHT 1
#define COMMON_TILES 11

#define COMMON_NPALS1 1

const u16 COMMON_TILES1[88] = {
0x0000,0x0150,0x0404,0x0404,0x0404,0x0404,0x0404,0x0150,
0x0000,0x0140,0x0040,0x0040,0x0040,0x0040,0x0040,0x0554,
0x0000,0x0150,0x0404,0x0010,0x0040,0x0100,0x0400,0x0554,
0x0000,0x0150,0x0404,0x0004,0x0050,0x0004,0x0404,0x0150,
0x0000,0x0010,0x0050,0x0110,0x0410,0x0554,0x0010,0x0054,
0x0000,0x0550,0x0400,0x0400,0x0540,0x0010,0x1010,0x0540,
0x0000,0x0150,0x0400,0x1000,0x1540,0x1010,0x1010,0x0540,
0x0000,0x1550,0x1010,0x0010,0x0040,0x0040,0x0100,0x0100,
0x0000,0x0540,0x1010,0x1010,0x0540,0x1010,0x1010,0x0540,
0x0000,0x0540,0x1010,0x1010,0x0550,0x0010,0x0040,0x1500,
0x0220,0x0888,0x0220,0x0888,0x0220,0x0888,0x0220,0x0080
};

const u16 COMMON_PALS1[4] = {
0x0000,0x0fff,0x000f,0x0000
};

const u8 COMMON_PALIDX1[11] = {
0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00
};

#define COMMON_ID {COMMON_WIDTH,COMMON_HEIGHT,COMMON_TILES,(u16*)COMMON_TILES1,(u16*)0,COMMON_NPALS1,(u16*)COMMON_PALS1,0,(u16*)0,(u8*)COMMON_PALIDX1,(u8*)0}

const SOD_IMG COMMON_IMG = COMMON_ID;

