#include "img.h"

#define EXPL0_WIDTH 2
#define EXPL0_HEIGHT 5
#define EXPL0_TILES 10

#define EXPL0_NPALS1 4

const u16 EXPL0_TILES1[80] = {
0x0100,0x0000,0x0000,0x000a,0x000a,0x00ab,0x02ae,0x06ba,
0x4000,0x4000,0x0400,0xaa00,0xe940,0xfa80,0xfe80,0xfa40,
0x07ff,0x07ff,0x06ff,0x01be,0x006e,0x0006,0x0000,0x0000,
0x5680,0x56a0,0x5660,0x5580,0x5580,0x66c0,0xab30,0x0000,
0xeaab,0x9556,0x9ff6,0x9d76,0x9ff6,0x9d56,0x9d56,0xeaab,
0xeaab,0x9556,0x9ff6,0x9d56,0x9ff6,0x9576,0x9ff6,0xeaab,
0xeaab,0x9556,0x9d56,0x9d56,0x9d56,0x9d56,0x9ff6,0xeaab,
0xeaab,0x9556,0x9fe6,0x9d76,0x9ff6,0x9d76,0x9fe6,0xeaab,
0x0000,0x0550,0x1be4,0x1ff4,0x1ff4,0x1be4,0x0550,0x0000,
0x2e00,0x3f2e,0x2e3f,0x002e,0xb800,0xfcb8,0xb8fc,0x00b8
};

const u16 EXPL0_PALS1[16] = {
0x0000,0x0158,0x05df,0x0cff,
0x0000,0x0027,0x028c,0x0bff,
0x0000,0x04be,0x017b,0x0026,
0x0000,0x004c,0x008f,0x00ff
};

const u8 EXPL0_PALIDX1[10] = {
0x00,0x01,
0x00,0x02,
0x02,0x02,
0x02,0x02,
0x03,0x03
};

#define EXPL0_ID {EXPL0_WIDTH,EXPL0_HEIGHT,EXPL0_TILES,(u16*)EXPL0_TILES1,(u16*)0,EXPL0_NPALS1,(u16*)EXPL0_PALS1,0,(u16*)0,(u8*)EXPL0_PALIDX1,(u8*)0}

const SOD_IMG EXPL0_IMG = EXPL0_ID;
