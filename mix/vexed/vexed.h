#include "img.h"

#define VEXED_WIDTH 39
#define VEXED_HEIGHT 3
#define VEXED_TILES 117

#define VEXED_NPALS1 16

const u16 VEXED_TILES1[936] = {
0x0000,0x0555,0x1aaa,0x1bff,0x1bff,0x1bff,0x1bff,0x1bff,
0x0000,0x5550,0xaaa4,0xffe4,0xffe4,0xffe4,0xffe4,0xffe4,
0x0000,0x0555,0x1aaa,0x1bff,0x1bff,0x1bff,0x1bff,0x1bff,
0x0000,0x5550,0xaaa4,0xffe4,0xffe4,0xffe4,0xffe4,0xffe4,
0x0000,0x0555,0x1aaa,0x1aaa,0x1aaa,0x1aaa,0x1aaa,0x1aaa,
0x0000,0x5550,0xaaa4,0xaaa4,0xaaa4,0xaaa4,0xaaa4,0xaaa4,
0x0000,0x0555,0x1555,0x16aa,0x16aa,0x16aa,0x16aa,0x16aa,
0x0000,0x5550,0x5554,0xaa94,0xaa94,0xaa94,0xaa94,0xaa94,
0x0000,0x0555,0x1aaa,0x1bff,0x1bff,0x1bff,0x1bff,0x1bff,
0x0000,0x5550,0xaaa4,0xffe4,0xffe4,0xffe4,0xffe4,0xffe4,
0x0000,0x0555,0x1aaa,0x1bff,0x1bff,0x1bff,0x1bff,0x1bff,
0x0000,0x5550,0xaaa4,0xffe4,0xffe4,0xffe4,0xffe4,0xffe4,
0x0000,0x0555,0x1aaa,0x1bff,0x1bff,0x1bff,0x1bff,0x1bff,
0x0000,0x5550,0xaaa4,0xffe4,0xffe4,0xffe4,0xffe4,0xffe4,
0x0000,0x0555,0x1aaa,0x1bff,0x1bff,0x1bff,0x1bff,0x1bff,
0x0000,0x5550,0xaaa4,0xffe4,0xffe4,0xffe4,0xffe4,0xffe4,
0x0000,0x0555,0x1aaa,0x1bff,0x1bff,0x1bff,0x1bff,0x1bff,
0x0000,0x5550,0xaaa4,0xffe4,0xffe4,0xffe4,0xffe4,0xffe4,
0x0006,0x000b,0x000b,0x0005,0x0000,0x0000,0x0000,0x0000,
0x6000,0xe000,0xe000,0xa000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x6900,0xbd00,
0x01a4,0x02f4,0x02f4,0x0154,0x0000,0x0000,0x0000,0x0000,
0x0000,0x6900,0xbd00,0xbd00,0x5500,0x0000,0x0000,0x0000,
0x0000,0x0069,0x00bd,0x00bd,0x0055,0x0000,0x0000,0x0000,
0x1a40,0x2f40,0x2f40,0x1540,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0069,0x00bd,
0x3000,0x3c00,0x3f00,0x3fc0,0x3fd0,0x3f40,0x3d00,0x3400,
0x03c0,0x03c0,0x0ff0,0xffff,0xffff,0x0ff0,0x03c0,0x03c0,
0x0000,0x03c0,0x0ff0,0x3ffc,0x3ffc,0x0ff0,0x03c0,0x0000,
0x0000,0x0000,0x03c0,0x0ff0,0x0ff0,0x03c0,0x0000,0x0000,
0x0000,0x0000,0x0000,0x03c0,0x03c0,0x0000,0x0000,0x0000,
0x5555,0x6aa9,0x6ff9,0x6ff9,0x6ff9,0x6ff9,0x6aa9,0x5555,
0x5555,0x6aa9,0x6aa9,0x6aa9,0x6aa9,0x6aa9,0x6aa9,0x5555,
0x5555,0x6aa9,0x6ff9,0x6ff9,0x6ff9,0x6ff9,0x6aa9,0x5555,
0x5555,0x7ffd,0x7ffd,0x7ffd,0x7ffd,0x7ffd,0x7ffd,0x5555,
0x5555,0x6aa9,0x6ff9,0x6ff9,0x6ff9,0x6ff9,0x6aa9,0x5555,
0x5555,0x7ffd,0x7ffd,0x7ffd,0x7ffd,0x7ffd,0x7ffd,0x5555,
0x5555,0x6aa9,0x6ff9,0x6ff9,0x6ff9,0x6ff9,0x6aa9,0x5555,
0x5555,0x6aa9,0x6ff9,0x6ff9,0x6ff9,0x6ff9,0x6aa9,0x5555,
0x1bff,0x1bff,0x1bff,0x1bff,0x1bff,0x1aaa,0x0555,0x0000,
0xffe4,0xffe4,0xffe4,0xffe4,0xffe4,0xaaa4,0x5550,0x0000,
0x1bff,0x1bff,0x1bff,0x1bff,0x1bff,0x1aaa,0x0555,0x0000,
0xffe4,0xffe4,0xffe4,0xffe4,0xffe4,0xaaa4,0x5550,0x0000,
0x1aaa,0x1aaa,0x1aaa,0x1aaa,0x1aaa,0x1aaa,0x0555,0x0000,
0xaaa4,0xaaa4,0xaaa4,0xaaa4,0xaaa4,0xaaa4,0x5550,0x0000,
0x16aa,0x16aa,0x16aa,0x16aa,0x16aa,0x1555,0x0555,0x0000,
0xaa94,0xaa94,0xaa94,0xaa94,0xaa94,0x5554,0x5550,0x0000,
0x1bff,0x1bff,0x1bff,0x1bff,0x1bff,0x1aaa,0x0555,0x0000,
0xffe4,0xffe4,0xffe4,0xffe4,0xffe4,0xaaa4,0x5550,0x0000,
0x1bff,0x1bff,0x1bff,0x1bff,0x1bff,0x1aaa,0x0555,0x0000,
0xffe4,0xffe4,0xffe4,0xffe4,0xffe4,0xaaa4,0x5550,0x0000,
0x1bff,0x1bff,0x1bff,0x1bff,0x1bff,0x1aaa,0x0555,0x0000,
0xffe4,0xffe4,0xffe4,0xffe4,0xffe4,0xaaa4,0x5550,0x0000,
0x1bff,0x1bff,0x1bff,0x1bff,0x1bff,0x1aaa,0x0555,0x0000,
0xffe4,0xffe4,0xffe4,0xffe4,0xffe4,0xaaa4,0x5550,0x0000,
0x1bff,0x1bff,0x1bff,0x1bff,0x1bff,0x1aaa,0x0555,0x0000,
0xffe4,0xffe4,0xffe4,0xffe4,0xffe4,0xaaa4,0x5550,0x0000,
0x0000,0x0000,0x0000,0x6900,0xbd00,0xbd00,0x5500,0x0000,
0x0000,0x0000,0x0000,0x0069,0x00bd,0x00bd,0x0055,0x0000,
0x7e00,0xaa00,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x01a4,0x02f4,0x02f4,0x0154,
0x0000,0x0000,0x0000,0x0000,0x0009,0x0007,0x0007,0x000a,
0x0000,0x0000,0x0000,0x0000,0x6000,0xe000,0xe000,0xa000,
0x0000,0x0000,0x0000,0x0000,0x1a40,0x2f40,0x2f40,0x1540,
0x00bd,0x0055,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x1555,0x1aaa,0x1bff,0x1bff,0x1bea,0x1be5,0x1be4,
0x0000,0x5555,0xaaaa,0xffff,0xffff,0xaaaa,0x5555,0x0000,
0x0000,0x5554,0xaaa4,0xffe4,0xffe4,0xabe4,0x5be4,0x1be4,
0x1be4,0x1be5,0x1bea,0x1bff,0x1bff,0x1aaa,0x1555,0x0000,
0x1be4,0x5be4,0xabe4,0xffe4,0xffe4,0xaaa4,0x5554,0x0000,
0x1be4,0x1be4,0x1be4,0x1be4,0x1be4,0x1be4,0x1be4,0x1be4,
0x27d8,0xa7da,0x57d5,0xffff,0xffff,0x5555,0xaaaa,0x0000,
0x1554,0x1aa4,0x1be4,0x1be4,0x1be4,0x1be4,0x1be4,0x1be4,
0x1be4,0x1be5,0x1bea,0x1bff,0x1bff,0x1bea,0x1be5,0x1be4,
0x1be4,0x5be4,0xabe4,0xffe4,0xffe4,0xabe4,0x5be4,0x1be4,
0x4001,0x1005,0x0414,0x0050,0x0140,0x0510,0x1404,0x5001,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x03f0,0x0c0c,0x0c0c,0x0c0c,0x0c0c,0x0c0c,0x03f0,
0x0000,0x03c0,0x00c0,0x00c0,0x00c0,0x00c0,0x00c0,0x0ffc,
0x0000,0x03f0,0x0c0c,0x0030,0x00c0,0x0300,0x0c00,0x0ffc,
0x0000,0x03f0,0x0c0c,0x000c,0x00f0,0x000c,0x0c0c,0x03f0,
0x0000,0x0030,0x00f0,0x0330,0x0c30,0x0ffc,0x0030,0x00fc,
0x0000,0x0ff0,0x0c00,0x0c00,0x0fc0,0x0030,0x3030,0x0fc0,
0x0000,0x03f0,0x0c00,0x3000,0x3fc0,0x3030,0x3030,0x0fc0,
0x0000,0x3ff0,0x3030,0x0030,0x00c0,0x00c0,0x0300,0x0300,
0x0000,0x0fc0,0x3030,0x3030,0x0fc0,0x3030,0x3030,0x0fc0,
0x0000,0x0fc0,0x3030,0x3030,0x0ff0,0x0030,0x00c0,0x3f00,
0x0000,0x0f00,0x0300,0x0cc0,0x0cc0,0x0fc0,0x3030,0xfcfc,
0x0000,0xffc0,0x3030,0x3030,0x3fc0,0x3030,0x3030,0xffc0,
0x0000,0x0ff0,0x3030,0x3000,0x3000,0x3000,0x3030,0x0fc0,
0x0000,0xff00,0x30c0,0x3030,0x3030,0x3030,0x30c0,0xff00,
0x0000,0x3ff0,0x0c30,0x0cc0,0x0fc0,0x0cc0,0x0c30,0x3ff0,
0x0000,0x3ff0,0x0c30,0x0cc0,0x0fc0,0x0cc0,0x0c00,0x3f00,
0x0000,0x0ff0,0x3030,0x3000,0x3000,0x30fc,0x3030,0x0fc0,
0x0000,0xfcfc,0x3030,0x3030,0x3ff0,0x3030,0x3030,0xfcfc,
0x0000,0x3ff0,0x0300,0x0300,0x0300,0x0300,0x0300,0x3ff0,
0x0000,0x0ff0,0x00c0,0x00c0,0x00c0,0x30c0,0x30c0,0x0f00,
0x0000,0xfcfc,0x3030,0x30c0,0x3f00,0x30c0,0x3030,0xfc3c,
0x0000,0x3f00,0x0c00,0x0c00,0x0c00,0x0c30,0x0c30,0x3ff0,
0x0000,0xfcfc,0x3cf0,0x3cf0,0x3330,0x3030,0x3030,0xfcfc,
0x0000,0xfcfc,0x3c30,0x3c30,0x3330,0x3330,0x30f0,0xfcf0,
0x0000,0x0fc0,0x3030,0x3030,0x3030,0x3030,0x3030,0x0fc0,
0x0000,0x3fc0,0x0c30,0x0c30,0x0c30,0x0fc0,0x0c00,0x3f00,
0x0000,0x0fc0,0x3030,0x3030,0x3030,0x3330,0x30c0,0x0f30,
0x0000,0x3fc0,0x0c30,0x0c30,0x0c30,0x0fc0,0x0c30,0x3f0c,
0x0000,0x0f30,0x30f0,0x3000,0x0fc0,0x0030,0x3030,0x3fc0,
0x0000,0x3ff0,0x3330,0x0300,0x0300,0x0300,0x0300,0x0fc0,
0x0000,0xfcfc,0x3030,0x3030,0x3030,0x3030,0x3030,0x0fc0,
0x0000,0xfcfc,0x3030,0x3030,0x30c0,0x0cc0,0x0cc0,0x0f00,
0x0000,0xfcfc,0x3030,0x3330,0x3330,0x3330,0x3330,0x0cc0,
0x0000,0xf03c,0x3030,0x0cc0,0x0300,0x0cc0,0x3030,0xf03c,
0x0000,0xfcfc,0x3030,0x0cc0,0x0300,0x0300,0x0300,0x0fc0,
0x0000,0x3ff0,0x3030,0x00c0,0x0300,0x0c00,0x3030,0x3ff0,
0x0000,0x0000,0x0000,0x0f00,0x0000,0x0000,0x0000,0x0f00,
0x0000,0x0300,0x0300,0x0300,0x0300,0x0300,0x0000,0x0300,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0f00
};

const u16 VEXED_PALS1[64] = {
0x0000,0x0444,0x0888,0x0aaa,
0x0000,0x0008,0x002c,0x000f,
0x0000,0x004b,0x007e,0x06ad,
0x0000,0x0169,0x08ce,0x00ee,
0x0000,0x0080,0x00c0,0x00f0,
0x0000,0x0800,0x0c00,0x0f00,
0x0000,0x0888,0x0ccc,0x0fff,
0x0000,0x0088,0x00cc,0x00ff,
0x0000,0x0808,0x0c0c,0x0c0e,
0x0000,0x0888,0x0ccc,0x0fff,
0x0000,0x0ccc,0x0888,0x0fff,
0x0000,0x0888,0x0ccc,0x0fff,
0x0000,0x0888,0x0ccc,0x0fff,
0x0000,0x0888,0x0ccc,0x0fff,
0x0000,0x0888,0x0ccc,0x0fff,
0x0000,0x0888,0x0ccc,0x0fff
};

const u8 VEXED_PALIDX1[117] = {
0x00,0x00,0x01,0x01,0x02,0x02,0x03,0x03,0x04,0x04,0x05,0x05,0x06,0x06,0x07,0x07,0x08,0x08,0x09,0x0a,0x09,0x09,0x09,0x09,0x09,0x09,0x01,0x0a,0x0a,0x0a,0x0a,0x01,0x02,0x08,0x03,0x04,0x02,0x05,0x0b,
0x00,0x00,0x01,0x01,0x02,0x02,0x03,0x03,0x04,0x04,0x05,0x05,0x06,0x06,0x07,0x07,0x08,0x08,0x09,0x09,0x0a,0x09,0x0a,0x0a,0x09,0x09,0x0c,0x0c,0x0d,0x0d,0x0e,0x0e,0x0a,0x09,0x0f,0x0f,0x05,0x0a,0x0a,
0x0a,0x0a,0x0a,0x0a,0x0a,0x0a,0x0a,0x0a,0x0a,0x0a,0x0a,0x0a,0x0a,0x0a,0x0a,0x0a,0x0a,0x0a,0x0a,0x0a,0x0a,0x0a,0x0a,0x0a,0x0a,0x0a,0x0a,0x0a,0x0a,0x0a,0x0a,0x0a,0x0a,0x0a,0x0a,0x0a,0x0a,0x0a,0x0a
};

#define VEXED_ID {VEXED_WIDTH,VEXED_HEIGHT,VEXED_TILES,(u16*)VEXED_TILES1,(u16*)0,VEXED_NPALS1,(u16*)VEXED_PALS1,0,(u16*)0,(u8*)VEXED_PALIDX1,(u8*)0}

const SOD_IMG VEXED_IMG = VEXED_ID;
