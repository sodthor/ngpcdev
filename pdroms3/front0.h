#include "img.h"

#define PIPES_WIDTH 13
#define PIPES_HEIGHT 3
#define PIPES_TILES 39

#define PIPES_NPALS1 16

const u16 PIPES_TILES1[312] = {
0x0000,0x5555,0xaaaa,0xffff,0xffff,0xaaaa,0x5555,0x0000,
0x1be4,0x1be4,0x1be4,0x1be4,0x1be4,0x1be4,0x1be4,0x1be4,
0x1be4,0x5be5,0xabea,0xffff,0xffff,0xabea,0x5be5,0x1be4,
0x1be4,0x5be4,0xabe4,0xffe4,0xffe4,0xaaa4,0x5554,0x0000,
0x1be4,0x5be4,0xabe4,0xffe4,0xffe4,0xabe4,0x5be4,0x1be4,
0x1be4,0x5be5,0xabea,0xffff,0xffff,0xaaaa,0x5555,0x0000,
0xffff,0xeaaa,0xe5aa,0xe7aa,0xeaaa,0xeaaa,0xeaaa,0xeaaa,
0xffff,0xaaa9,0xaa59,0xaa79,0xaaa9,0xaaa9,0xaaa9,0xaaa9,
0xeaaa,0xeaaa,0xeaaa,0xeaaa,0xedaa,0xe5aa,0xeaaa,0xd555,
0xaaa9,0xaaa9,0xaaa9,0xaaa9,0xaad9,0xaa59,0xaaa9,0x5555,
0xeaaa,0xeaaa,0xeaaa,0xeaaa,0xeaaa,0xeaaa,0xeaaa,0xeaaa,
0xffff,0xaaaa,0xaaaa,0xaaaa,0xaaaa,0xaaaa,0xaaaa,0xaaaa,
0xaaaa,0xaaaa,0xaaaa,0xaaaa,0xaaaa,0xaaaa,0xaaaa,0xaaaa,
0x0000,0x5555,0xaaaa,0xffff,0xffff,0xaaaa,0x5555,0x0000,
0x1be4,0x1be4,0x1be4,0x1be4,0x1be4,0x1be4,0x1be4,0x1be4,
0x1be4,0x5be5,0xabea,0xffff,0xffff,0xabea,0x5be5,0x1be4,
0x1be4,0x5be4,0xabe4,0xffe4,0xffe4,0xaaa4,0x5554,0x0000,
0x1be4,0x5be4,0xabe4,0xffe4,0xffe4,0xabe4,0x5be4,0x1be4,
0x1be4,0x5be5,0xabea,0xffff,0xffff,0xaaaa,0x5555,0x0000,
0x5555,0x6aaa,0x6faa,0x6daa,0x6aaa,0x6aaa,0x6aaa,0x6aaa,
0x5555,0xaaab,0xaafb,0xaadb,0xaaab,0xaaab,0xaaab,0xaaab,
0x6aaa,0x6aaa,0x6aaa,0x6aaa,0x67aa,0x6faa,0x6aaa,0x7fff,
0x5556,0x5556,0x5556,0x5556,0x55e6,0x55a6,0x5556,0xaaaa,
0xaaa9,0xaaa9,0xaaa9,0xaaa9,0xaaa9,0xaaa9,0xaaa9,0xaaa9,
0xaaaa,0xaaaa,0xaaaa,0xaaaa,0xaaaa,0xaaaa,0xaaaa,0x5555,
0x5555,0x5555,0x5555,0x5555,0x5555,0x5555,0x5555,0x5555,
0x1554,0x5aa5,0xabea,0xfbef,0xfbef,0xabea,0x5aa5,0x1554,
0x1be4,0x5be5,0x6aa9,0x6ff9,0x6ff9,0x6aa9,0x5be5,0x1be4,
0x1554,0x5aa5,0xabea,0xfbef,0xfbef,0xabea,0x5aa5,0x1554,
0x1be4,0x5be5,0x6aa9,0x6ff9,0x6ff9,0x6aa9,0x5be5,0x1be4,
0x5540,0x6a55,0x6e6a,0x6e6f,0x6e6f,0x6e6a,0x6a55,0x5540,
0x5540,0x6a55,0x6e6a,0x6e6f,0x6e6f,0x6e6a,0x6a55,0x5540,
0x5555,0x6aa9,0x6ff9,0x6aa9,0x5555,0x1aa4,0x1be4,0x1be4,
0x5555,0x6aa9,0x6ff9,0x6aa9,0x5555,0x1aa4,0x1be4,0x1be4,
0x5555,0x5555,0x5555,0x5555,0x5555,0x5555,0x5555,0xaaaa,
0x5555,0xaaaa,0xaaaa,0xaaaa,0xaaaa,0xaaaa,0xaaaa,0xaaaa,
0x6aaa,0x6aaa,0x6aaa,0x6aaa,0x6aaa,0x6aaa,0x6aaa,0x6aaa,
0x5556,0x5556,0x5556,0x5556,0x5556,0x5556,0x5556,0x5556,
0xaaaa,0xaaaa,0xafda,0xae9a,0xae9a,0xad5a,0xaaaa,0xaaaa
};

const u16 PIPES_PALS1[64] = {
0x0000,0x0444,0x0888,0x0ccc,
0x0000,0x0644,0x0a88,0x0ecc,
0x0000,0x0644,0x0a88,0x0ecc,
0x0000,0x0644,0x0a88,0x0ecc,
0x0000,0x0644,0x0a88,0x0ecc,
0x0000,0x0644,0x0a88,0x0ecc,
0x0000,0x0ecc,0x0a88,0x0644,
0x0000,0x0ecc,0x0a88,0x0644,
0x0000,0x0ecc,0x0a88,0x0644,
0x0000,0x0a88,0x0644,0x0ecc,
0x0000,0x0644,0x0a88,0x0ecc,
0x0000,0x0644,0x0a88,0x0ecc,
0x0000,0x0644,0x0a88,0x0ecc,
0x0000,0x0644,0x0a88,0x0ecc,
0x0000,0x0ecc,0x0a88,0x0000,
0x0000,0x0ecc,0x0a88,0x0000
};

const u8 PIPES_PALIDX1[39] = {
0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,
0x00,0x01,0x02,0x03,0x04,0x05,0x06,0x07,0x08,0x09,0x00,0x00,0x09,
0x0a,0x0b,0x00,0x00,0x0c,0x00,0x0d,0x00,0x09,0x0e,0x0f,0x09,0x00
};

#define PIPES_ID {PIPES_WIDTH,PIPES_HEIGHT,PIPES_TILES,(u16*)PIPES_TILES1,(u16*)0,PIPES_NPALS1,(u16*)PIPES_PALS1,0,(u16*)0,(u8*)PIPES_PALIDX1,(u8*)0}

const SOD_IMG PIPES_IMG = PIPES_ID;

