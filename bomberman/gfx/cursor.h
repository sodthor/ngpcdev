#include "img.h"

#define CURSOR_WIDTH 6
#define CURSOR_HEIGHT 2
#define CURSOR_TILES 24

#define CURSOR_NPALS1 2
#define CURSOR_NPALS2 2

const u16 CURSOR_TILES1[96] = {
0x0000,0x0000,0x0005,0x0018,0x001f,0x0140,0x0400,0x1000,
0x0000,0x0000,0x5000,0xe400,0xf400,0x0140,0x0010,0x0004,
0x0000,0x0005,0x0018,0x001f,0x0140,0x0400,0x1000,0x10ff,
0x0000,0x5000,0xe400,0xf400,0x0140,0x0010,0x0004,0xff04,
0x0000,0x0000,0x0015,0x0140,0x04df,0x1354,0x1344,0x1344,
0x0000,0x0000,0x5400,0x0140,0xf710,0x15c4,0x11c4,0x11c4,
0x10df,0x1354,0x1344,0x1344,0x1344,0x10ff,0x0400,0x0155,
0xf704,0x15c4,0x11c4,0x11c4,0x11c4,0xff04,0x0010,0x5540,
0x1310,0x1310,0x1310,0x1310,0x10ff,0x0400,0x0155,0x0000,
0x04c4,0x04c4,0x04c4,0x04c4,0xff04,0x0010,0x5540,0x0000,
0x1344,0x1300,0x10ff,0x1000,0x0400,0x0155,0x0000,0x0000,
0x11c4,0x00c4,0xff04,0x0004,0x0010,0x5540,0x0000,0x0000
};

const u16 CURSOR_TILES2[96] = {
0x0000,0x0000,0x0000,0x0001,0x0000,0x002a,0x02ff,0x0bff,
0x0000,0x0000,0x0000,0x0000,0x0000,0xa800,0xff80,0xffe0,
0x0000,0x0000,0x0001,0x0000,0x002a,0x02ff,0x0bff,0x0f00,
0x0000,0x0000,0x0000,0x0000,0xa800,0xff80,0xffe0,0x00f0,
0x0000,0x0000,0x0000,0x001a,0x0100,0x0403,0x0833,0x0833,
0x0000,0x0000,0x0000,0xa400,0x0040,0xc010,0xcc20,0xcc20,
0x0a00,0x0803,0x0833,0x0833,0x0833,0x0600,0x01aa,0x0000,
0x00a0,0xc020,0xcc20,0xcc20,0xcc20,0x0090,0xaa40,0x0000,
0x08cf,0x08cf,0x08cf,0x08cf,0x0600,0x01aa,0x0000,0x0000,
0xf320,0xf320,0xf320,0xf320,0x0090,0xaa40,0x0000,0x0000,
0x0833,0x08ff,0x0a00,0x06aa,0x01aa,0x0000,0x0000,0x0000,
0xcc20,0xff20,0x00a0,0xaa90,0xaa40,0x0000,0x0000,0x0000
};

const u16 CURSOR_PALS1[8] = {
0x0000,0x0112,0x0237,0x063d,
0x0000,0x0112,0x0237,0x063d
};

const u16 CURSOR_PALS2[8] = {
0x0000,0x0f7f,0x0fbc,0x0fff,
0x0000,0x0fbc,0x0fff,0x09bf
};

const u8 CURSOR_PALIDX1[12] = {
0x00,0x00,0x01,0x01,0x00,0x00,
0x01,0x01,0x01,0x01,0x00,0x00
};

const u8 CURSOR_PALIDX2[12] = {
0x00,0x00,0x00,0x00,0x01,0x01,
0x01,0x01,0x01,0x01,0x01,0x01
};

#define CURSOR_ID {CURSOR_WIDTH,CURSOR_HEIGHT,CURSOR_TILES,(u16*)CURSOR_TILES1,(u16*)CURSOR_TILES2,CURSOR_NPALS1,(u16*)CURSOR_PALS1,CURSOR_NPALS2,(u16*)CURSOR_PALS2,(u8*)CURSOR_PALIDX1,(u8*)CURSOR_PALIDX2}

const SOD_IMG CURSOR_IMG = CURSOR_ID;
