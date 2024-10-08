#define SPRITE_WIDTH 2
#define SPRITE_HEIGHT 2
#define SPRITE_TILES_COUNT 4

#define SPRITE_NPALS1 2

const u16 SPRITE_TILES[32] = {
0x0001,0x0005,0x0015,0x0055,0x005a,0x0069,0x006b,0x005b,
0xf000,0x5400,0x5700,0x55c0,0x69c0,0xbac0,0xbac0,0xb9c0,
0x0015,0x00a5,0x0f81,0x3ec5,0x3fc5,0x0f01,0x0014,0x0055,
0xa900,0x5680,0x50bc,0xa4ef,0xa4ff,0x503c,0x0900,0x2a40
};

const u16 SPRITE_PALS1[8] = {
0x0f0f,0x0a69,0x0bcc,0x0524,
0x0f0f,0x0747,0x077b,0x011f
};

const u8 SPRITE_PALIDX1[4] = {
0x00,0x00,
0x01,0x01
};
