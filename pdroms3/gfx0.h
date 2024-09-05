#include "img.h"

#define MAINSHIP_WIDTH 6
#define MAINSHIP_HEIGHT 1
#define MAINSHIP_TILES 6

#define MAINSHIP_NPALS1 6

const u16 MAINSHIP_TILES1[48] = {
0x0000,0x0015,0x0019,0x005a,0x05fa,0x1fab,0x7fd5,0x5540,
0x0550,0x1aa4,0x6be9,0xffff,0xeaab,0x9556,0x4001,0x0000,
0x0000,0x5400,0x6400,0xa500,0xaf50,0xeaf4,0x57fd,0x0154,
0x0000,0x0140,0x0690,0x1be4,0x1be4,0x0690,0x0140,0x0000,
0x5555,0x6aa9,0x6be9,0x1be4,0x1be4,0x0690,0x0690,0x0140,
0x0000,0x0140,0x0690,0x1be4,0x1be4,0x0690,0x0140,0x0000
};

const u16 MAINSHIP_PALS1[24] = {
0x0000,0x0800,0x0f08,0x0c04,
0x0000,0x0800,0x0c04,0x0f08,
0x0000,0x0800,0x0f08,0x0c04,
0x0000,0x004f,0x008f,0x00cf,
0x0000,0x0080,0x00c0,0x00f0,
0x0000,0x0808,0x080c,0x080f
};

const u8 MAINSHIP_PALIDX1[6] = {
0x00,0x01,0x02,0x03,0x04,0x05
};

#define MAINSHIP_ID {MAINSHIP_WIDTH,MAINSHIP_HEIGHT,MAINSHIP_TILES,(u16*)MAINSHIP_TILES1,(u16*)0,MAINSHIP_NPALS1,(u16*)MAINSHIP_PALS1,0,(u16*)0,(u8*)MAINSHIP_PALIDX1,(u8*)0}

const SOD_IMG MAINSHIP_IMG = MAINSHIP_ID;
