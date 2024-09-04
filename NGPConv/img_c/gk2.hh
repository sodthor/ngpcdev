#include "img.h"

#define GK2_WIDTH 12
#define GK2_HEIGHT 10
#define GK2_TILES 240

#define GK2_NPALS1 15
#define GK2_NPALS2 15

const u16 GK2_TILES1[960] = {
0x5555,0x4000,0x4000,0x4000,0x4000,0x4000,0x4000,0x4000,
0x5555,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x5555,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x5555,0x0000,0x0000,0x0000,0x0000,0x000b,0x0038,0x0300,
0x5555,0x0082,0x0028,0x002f,0x2a00,0x8000,0x0000,0x0a00,
0x5555,0xc000,0xfc00,0xffe0,0x00be,0x0002,0xa80b,0x0afe,
0x5555,0x0af8,0x000f,0x0000,0x0a00,0xfbef,0x020f,0xbbf2,
0x5555,0x2822,0x0cc3,0xfcc3,0x3cc3,0x0e8e,0xea28,0xffbf,
0x5555,0x8000,0x8000,0x82fa,0xbfaa,0x28a8,0xaaaa,0xfefe,
0x5555,0x0000,0x0000,0xf800,0x0080,0x2828,0xab82,0x00f0,
0x5555,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x8000,
0x5555,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x4000,0x4000,0x4000,0x4000,0x4000,0x4000,0x4000,0x4000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0142,0x050b,
0x0006,0x407d,0x02e0,0x0f40,0x3d02,0xa414,0x9140,0x4400,
0xd000,0x001f,0x0fec,0xeff0,0xc344,0x0c3c,0x3480,0xd283,
0x56a9,0x415a,0x056a,0x09af,0x29bf,0xaacc,0xaac0,0xbbff,
0x82a0,0x92a4,0xa0a9,0xf4be,0xf86e,0xfd5a,0x020a,0xff96,
0xfbfb,0x3aaf,0x1abf,0xce9f,0xcea7,0x83ae,0xb4eb,0xa8eb,
0x6aaa,0x5aa5,0x97ea,0xab5a,0x6bf1,0x5ad4,0x5ab5,0xcab2,
0x5005,0xce00,0x8080,0xa434,0xa90f,0xea01,0x3a80,0xceb0,
0x0000,0x0200,0x0080,0x0000,0x0000,0xc000,0xa000,0x2000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x4000,0x4000,0x4000,0x4000,0x4000,0x4000,0x4000,0x4000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x002f,0x0097,0x025d,0x0524,0x04b0,0x3280,0x5200,0x4801,
0xc001,0x4005,0x0045,0x0107,0x041e,0x1059,0x5078,0x41e4,
0x4a42,0x2f0a,0xbd1a,0x8d2a,0x0479,0x31b5,0x3192,0x52ba,
0x6957,0x89fc,0x8b00,0xa700,0xf000,0x4000,0xc000,0x00d5,
0xffe8,0xc036,0x0001,0x0000,0x0000,0x0000,0x3000,0x5557,
0x6819,0x160a,0x9146,0xea80,0x03a8,0x0009,0x000e,0x0002,
0x5aa1,0x1ba0,0x5be9,0x6bfa,0x0525,0x0000,0xa041,0xaa9a,
0x82ac,0x51bb,0x57ba,0x06aa,0x06ad,0x2aab,0xeeab,0x96aa,
0x0000,0x0000,0xc000,0xb000,0xf400,0xb900,0xaec0,0xea30,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x4000,0x4000,0x4000,0x4000,0x4000,0x4000,0x4000,0x4000,
0x0001,0x0001,0x0000,0x0000,0x0002,0x0002,0x0004,0x0004,
0x1801,0x200c,0xc014,0x8030,0x00c0,0x0140,0x0100,0x0500,
0x07a0,0x0db1,0x1f81,0x1281,0x0600,0x0e0c,0x1808,0x583c,
0xf6bb,0x52b8,0x42b9,0x21ab,0x341b,0xd3f9,0x9269,0x833b,
0x0ec0,0x2c00,0x8000,0x0000,0x001a,0x02a4,0x1a40,0xa640,
0x003e,0x0000,0x0000,0x1eaa,0xaa56,0x0001,0x0005,0x0060,
0x5801,0x0f73,0x00fd,0x55fd,0x9555,0x89aa,0x8288,0x028a,
0xa544,0x85f4,0x09f5,0x88f5,0x4855,0x8951,0xa695,0xa96a,
0xe5ba,0x347a,0xf87a,0xf11f,0xf207,0xf343,0xffd1,0xbfd0,
0xa730,0x60e4,0x6464,0x581a,0xa80a,0xa906,0xaa02,0x6a00,
0x0000,0x0000,0x0000,0x0000,0x4000,0x8000,0x8000,0x8000,
0x4000,0x4000,0x4000,0x4000,0x4000,0x4000,0x4000,0x4000,
0x0000,0x0010,0x0010,0x0040,0x0040,0x0143,0x0103,0x0101,
0x0f00,0x3d03,0x3403,0xf80c,0xd40c,0xe030,0xa030,0xb030,
0x5c25,0x3c65,0x24e2,0x21a2,0x22a2,0x72e2,0x56e6,0xdee7,
0x833e,0x827d,0x927d,0x927d,0xb6fd,0xa6ad,0xa6bd,0xefbd,
0xf200,0xa200,0xa200,0xaa00,0xabe0,0x3ff0,0x3fe8,0x2d48,
0x02c0,0x0bab,0x2ef3,0x0af5,0x02f5,0x00a6,0x0000,0x0000,
0x008e,0xabb2,0xaafe,0x0afe,0x5fff,0x40cf,0x00c3,0x00c3,
0x83a7,0x8fef,0xb0a7,0xae9b,0x5d69,0xd05b,0xd064,0xd351,
0x7faa,0x6e2f,0x5b5a,0x1f6a,0x2f5a,0x6baa,0xab5f,0xfb9f,
0xea00,0x5a00,0x9e41,0x1285,0xaefd,0xf6b4,0x1f80,0x0a80,
0x9000,0x9000,0x4000,0x4000,0x0000,0x0000,0x0000,0x0000,
0x4000,0x4000,0x4000,0x4000,0x4000,0x4000,0x4000,0x4000,
0x0502,0x0c0e,0x0c0b,0x001c,0x0030,0x1063,0x10f2,0x0082,
0x80c0,0x40c0,0xc0c0,0x0003,0x0303,0x0300,0x0000,0x0000,
0x4965,0x5be5,0x5be5,0x5bea,0x67ea,0x64ea,0xac19,0xac15,
0x56f6,0x5ef6,0x657d,0xa5bd,0x699d,0x9a75,0x6a7e,0x6afe,
0xad60,0xba00,0xb800,0xbc00,0xbc00,0x2e02,0x2f02,0x2b80,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0xb400,0xf400,
0x0003,0x0003,0x0003,0x0000,0x0000,0x0000,0x0000,0x1000,
0xd0de,0xd8aa,0x2f96,0xe0aa,0x189a,0x3f9e,0x2093,0x1c8d,
0xaafa,0xef8e,0xe4be,0xe4b6,0xe0be,0x6067,0xe025,0x242c,
0x0580,0x0b80,0x0590,0x09d0,0x05f0,0x0b70,0xb510,0x4c04,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x4000,0x4000,0x4000,0x4000,0x4000,0x4000,0x4000,0x4002,
0x0183,0x0387,0x0e4f,0x0e0d,0x0a0d,0x0b0c,0x3808,0x2808,
0x0c03,0x0c03,0x0003,0x000f,0x300c,0x300c,0x300f,0x000f,
0xaf3b,0x5c19,0xdc29,0xfce9,0xfcd9,0xf07b,0xf0f7,0xc3f7,
0xf7bb,0xf60b,0xfe0d,0x5909,0xf809,0x7c06,0x6003,0xe000,
0x3e90,0x3eb4,0x3fa9,0x1aae,0x5a96,0xfeb7,0xaa77,0xea73,
0x0007,0x1500,0x0e00,0xc155,0xa014,0xb900,0x9240,0xbe90,
0x8000,0x0000,0x0000,0x0000,0x0002,0x001f,0x00bf,0x070f,
0x298a,0x2456,0x2496,0x7891,0xf091,0xf481,0xb496,0xa0aa,
0xbc2e,0xbff8,0xaffe,0x8b5f,0xaf5f,0xbf5f,0xfd57,0x9557,
0xe000,0x8000,0x8000,0x7000,0x6000,0xfc00,0x9800,0xb800,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x4002,0x4002,0x4000,0x4002,0x4000,0x4000,0x4002,0x4000,
0x2c08,0x2408,0x2408,0x2408,0x6408,0xf40c,0xd00c,0xc004,
0x0003,0x0003,0x0003,0x0003,0xc00f,0xc00f,0xc00f,0x000c,
0xc3f7,0xc1ff,0x011d,0x0f12,0x0fd2,0x0f5e,0x0cb5,0x0f84,
0x9000,0x8000,0x8001,0x4001,0x0000,0x0000,0x0006,0x0028,
0x4715,0x4609,0x0935,0x04b4,0x06f9,0x2ea0,0x5024,0x0029,
0x55ac,0x5aaa,0x5aaa,0x5aaa,0x5bea,0x56fe,0x55bf,0x691b,
0x6fff,0xeaa9,0xa555,0xa955,0xfaaa,0xffba,0x6aaa,0x55aa,
0x506a,0xa06a,0xa0a9,0xac69,0x5459,0x5c55,0x5756,0xafda,
0x5555,0x7955,0x8d55,0xe555,0x4955,0x4155,0x8255,0x4257,
0xaa00,0xaa00,0xaf00,0xae80,0xbbc0,0xb9b8,0xea5a,0xeaa5,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0xb400,0xfa80,
0x4000,0x4000,0x4000,0x4000,0x4000,0x4000,0x4000,0x4000,
0x803c,0x8033,0xc023,0x0023,0x3030,0x0030,0x00c0,0x008c,
0x000c,0x003c,0x0030,0x00f0,0x00f0,0x0380,0x0383,0x02c3,
0x0798,0x128c,0x3e8c,0x1fdc,0x08f4,0x5cf4,0xf4f0,0xe0f0,
0x00b0,0x03c0,0x0200,0x0300,0x0d00,0x0800,0x0800,0x1c00,
0x0014,0x0050,0x0053,0x0353,0x01d3,0x0191,0x0eb1,0x069d,
0xae02,0xae06,0xbe1b,0xb95b,0xb95f,0xf96f,0xe56f,0xe56c,
0xaaaa,0xfeaa,0xbffa,0x5aff,0x556a,0x4011,0x0000,0x0000,
0xacea,0xa4d5,0xa7d5,0x54d5,0x57d5,0xf517,0x0d5f,0x0333,
0x81bf,0x820e,0xd1ce,0xe0b3,0x6073,0x5460,0x541c,0x040f,
0xeae9,0xeaa5,0xaab9,0xaaad,0xaaab,0xeaab,0xeaaa,0xfaaa,
0x41eb,0x400e,0x5000,0x5000,0x5000,0xd000,0x9000,0xb000,
0x4000,0x4000,0x4000,0x4000,0x4000,0x4000,0x4000,0x5555,
0x00c8,0x0380,0x0320,0x0220,0x0020,0x00a0,0x02c0,0x5555,
0x0b83,0x0b0b,0x0f0f,0x2e0b,0x3c2b,0xf80f,0xf82c,0x5555,
0xf0f0,0xe0f0,0xc0e0,0x80e0,0x00e0,0x00e0,0x00a0,0x5555,
0x2800,0x3800,0x3000,0x3000,0x3000,0x3000,0xb002,0x5555,
0x1645,0x1645,0x1a56,0x5a16,0x6916,0x6819,0x6819,0xffff,
0xbfe8,0xbfe0,0xfbe0,0xff80,0xfb80,0xff80,0xe280,0x5555,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x5555,
0x0200,0x0200,0x0000,0x0000,0x0000,0x0020,0x0020,0x5555,
0x000b,0x000b,0x0003,0x0003,0x0003,0x0002,0x0002,0x5555,
0x5aaa,0x5aaa,0x56aa,0x56aa,0x56aa,0x36aa,0x95aa,0xffff,
0x5000,0x5400,0x9400,0x9500,0x9500,0xa500,0xa940,0xffff
};

const u16 GK2_TILES2[960] = {
0x0000,0x1555,0x1555,0x1555,0x1555,0x1555,0x1555,0x1555,
0x0000,0x5555,0x5555,0x5555,0x5555,0x5555,0x5555,0x5555,
0x0000,0x5555,0x5555,0x5555,0x5555,0x5555,0x5555,0x5555,
0x0000,0x5555,0x5555,0x5555,0x5556,0x5560,0x55c2,0x58ea,
0x0000,0x5628,0x55c2,0x5f80,0x80af,0x3fff,0xffea,0xe0aa,
0x0000,0x1bd6,0x03ad,0x000e,0xfd00,0xbffc,0x01d0,0x5000,
0x0000,0x6003,0x5660,0x6956,0xa0a9,0x0000,0xa4a0,0x0004,
0x0000,0x420c,0xa22c,0x0228,0xc228,0xb020,0x0082,0x0000,
0x0000,0x1aaa,0x1a55,0x1400,0x0000,0xc303,0x0000,0x0000,
0x0000,0x5555,0x5555,0x0255,0xfb35,0xc282,0x0028,0xab0e,
0x0000,0xaaaa,0xaaaa,0xaaaa,0xaaaa,0xaaaa,0x6aaa,0x3aaa,
0x0000,0x5555,0x5555,0x5555,0x5555,0x5555,0x5555,0x5555,
0x1555,0x1555,0x1555,0x1555,0x1555,0x1555,0x1555,0x1555,
0x5555,0x5555,0x5555,0x5555,0x5555,0x5555,0x5555,0x5555,
0x5555,0x5556,0x555b,0x556f,0x55be,0x56f7,0x581c,0x5070,
0x6b50,0x2f00,0xa809,0xe017,0xc058,0x0182,0x0c2d,0x22fe,
0x0bfa,0xd780,0x7002,0x000e,0x2812,0x62c1,0x820a,0x0824,
0x0000,0x1400,0x5000,0x9000,0x4000,0x0033,0x003f,0x0000,
0x1806,0x0802,0x0600,0x0100,0x0100,0x0000,0xfc60,0x0000,
0x0000,0x4000,0x8000,0x3000,0x2000,0x2c00,0x0300,0x0300,
0x0000,0x0000,0x0000,0x0000,0x0004,0x0002,0x0000,0x1004,
0x0690,0x1069,0x393a,0x0282,0x00a0,0x0068,0xc01a,0x300a,
0xbd55,0xdc55,0xbb15,0x59f5,0x557d,0x155f,0x0557,0x4d56,
0x5555,0x5555,0x5555,0x5555,0x5555,0x5555,0x5555,0x9555,
0x1555,0x1555,0x1555,0x1555,0x1555,0x1555,0x1555,0x1555,
0x5555,0x5555,0x5555,0x5556,0x5555,0x5555,0x5555,0x5556,
0x7d80,0xb600,0x9400,0x50c2,0x620a,0x882a,0x0caa,0x23a8,
0x2d78,0x15a0,0x5620,0x58a0,0x7280,0xcb00,0x0e02,0x2c03,
0x1024,0x4050,0x0080,0x1040,0x5200,0xcc00,0xc404,0x0c00,
0x0000,0x1002,0x10af,0x00aa,0x0aab,0x2fff,0x3ffa,0xfe00,
0x0001,0x2a80,0xaaa4,0xaaaa,0xaaaa,0xfffe,0x455b,0x0000,
0x0140,0x4050,0x0410,0x0015,0xf801,0xffa0,0xffe0,0xbff8,
0x000c,0x400e,0x0000,0x0000,0x9040,0x5aee,0x0d2c,0x0000,
0x2801,0x0800,0x0000,0xb000,0xa000,0x8000,0x0000,0x0000,
0x5955,0x5555,0x1555,0x0555,0x0155,0x0055,0x0025,0x00c9,
0x9555,0x5555,0x5555,0x5555,0x5555,0x5555,0x5555,0x5555,
0x1555,0x1555,0x1555,0x1555,0x1555,0x1555,0x1555,0x1555,
0x5554,0x5558,0x555f,0x555f,0x5558,0x555c,0x5553,0x5562,
0x8158,0x4572,0x2542,0x158a,0x972b,0x542f,0x5cbf,0x70bf,
0x700f,0xf008,0xc03c,0xc42c,0xd0e5,0xd0a1,0x4191,0x0381,
0x0000,0x0801,0x1800,0x8400,0x4180,0x0800,0x0800,0x1480,
0x602a,0x8155,0x2555,0x9555,0x55c0,0x5c03,0xc03f,0x003f,
0xaa80,0x5555,0x556a,0x8000,0x0000,0xfffc,0xfff0,0xff0f,
0x02fc,0xa008,0xfa00,0x0000,0x0000,0x1000,0x1411,0x5410,
0x0000,0x2000,0x6000,0x3000,0x2000,0x2000,0x0000,0x0000,
0x0000,0x0100,0x0100,0x0040,0x00a0,0x0014,0x0004,0x0009,
0x00cd,0x0b01,0x0102,0x0140,0x0150,0x0050,0x0054,0x0056,
0x5555,0x5555,0x5555,0x9555,0x1555,0x2555,0x3555,0x3555,
0x1555,0x1555,0x1555,0x1555,0x1555,0x1555,0x1555,0x1555,
0x556b,0x554f,0x558f,0x552e,0x573e,0x5438,0x54b8,0x5ce8,
0xb06d,0x80ac,0x41a4,0x03b1,0x0291,0x0685,0x0ac7,0x0a4f,
0x0280,0xc200,0xc204,0xcc0c,0x4c0c,0x0c04,0x0000,0x0000,
0x1840,0x1800,0x0800,0x0400,0x0000,0x0000,0x0000,0x0000,
0x08bf,0x08bf,0x08f7,0x00bd,0x0005,0x800d,0x8001,0x8033,
0x5c2f,0x7000,0x4000,0xe000,0xe800,0xea00,0xeabd,0x6ad5,
0xe620,0x0008,0x0000,0x5000,0x0000,0x2e20,0x5e28,0x7e28,
0x2800,0x3000,0x0f00,0x0000,0x0000,0x0f00,0x0d01,0x0804,
0x0000,0x0040,0x0000,0xc000,0xc000,0x0000,0x0000,0x0000,
0x0069,0x00e9,0x0028,0x0410,0x0000,0x0003,0xc01a,0x502a,
0x0aaa,0x0aaa,0x3aaa,0x1aaa,0xeaaa,0xaaaa,0xaaaa,0xaaaa,
0x1555,0x1555,0x1555,0x1555,0x1555,0x1555,0x1555,0x1555,
0xf068,0xf160,0xf1a0,0xd981,0xd589,0xc604,0x460c,0x661c,
0x3a1f,0x2b3f,0x293d,0x6d7c,0xece4,0xecf5,0xa5b5,0xa7f5,
0x1000,0x0000,0x0000,0x0000,0x0000,0x0100,0x0140,0x0140,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x000b,0x009e,0x035e,0x035a,0x027e,0x80f8,0x80fc,0x803e,
0x6a55,0x6a55,0x5955,0x5555,0x5a95,0xafa5,0x03a9,0x02a6,
0xab5c,0xbf5c,0xaffc,0xbffd,0xabfd,0xbfff,0x97ff,0x47ff,
0x0e00,0x0300,0x4000,0x0e00,0x4300,0x4000,0xcf04,0xc230,
0x0000,0x0030,0x0100,0x0200,0x0600,0x0600,0x0640,0xc243,
0xb025,0xa035,0xb005,0xa005,0xb005,0xf00d,0x00cd,0x3372,
0x5555,0x5555,0x5555,0x5555,0x5555,0x5555,0x5555,0x5555,
0x1555,0x1555,0x1556,0x1557,0x1557,0x1557,0x1557,0x1554,
0xb81c,0xb810,0xb010,0xb0d0,0xa050,0xe052,0xc252,0xc352,
0xf2d4,0xf394,0xe794,0xd750,0xcb61,0xcf61,0xce50,0x9e90,
0x0080,0x0180,0x0140,0x0200,0x0200,0x0500,0x0900,0x1800,
0x0000,0x00f0,0x0070,0x0060,0x0270,0x0150,0x0d58,0x0555,
0xc00a,0xc002,0x8000,0x8000,0x0000,0x0000,0x0000,0x0004,
0xfab0,0xc055,0xb07b,0x3c00,0x0743,0x00aa,0x082a,0x000a,
0x3aaa,0xeaaa,0xefaa,0xeba6,0xee9c,0xaa80,0x5700,0x5000,
0x4010,0x4100,0xc200,0x0104,0x0704,0x031c,0x0100,0x0f00,
0x03c0,0x0002,0x0000,0x2000,0x0000,0x0000,0x0000,0x0000,
0x0d6e,0x255b,0x2556,0x0956,0x0955,0x0255,0x0355,0x0355,
0x5555,0x5555,0x9555,0x9555,0x5555,0x5555,0x5555,0x5555,
0x1554,0x1558,0x155b,0x1558,0x1557,0x1557,0x1554,0x1557,
0xc293,0xc293,0xc293,0xc293,0x0293,0x0293,0x0e93,0x16b3,
0xad94,0x6e94,0x7e94,0x7a94,0x3e90,0x2f50,0x3a60,0x7e51,
0x1800,0x2400,0x6440,0x6044,0x6004,0xa000,0xa100,0x9011,
0x0aaa,0x1aa9,0x2aa8,0x2aa8,0x6aa5,0xaaa9,0xaaa0,0xaa43,
0x10c0,0x1060,0xd0c0,0x5302,0x7000,0x800a,0x0bc3,0xffc0,
0x0001,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0900,0x0a00,0x0500,0x0200,0x0200,0x0100,0x0000,0x0000,
0x0000,0x0000,0x3000,0x0000,0x1000,0x2400,0x2400,0x1800,
0x006a,0x00ea,0x00ea,0x002a,0x003a,0x0003,0x0000,0x0000,
0x5555,0x5555,0x5555,0x5555,0x5555,0x9555,0x0255,0x0039,
0x1557,0x1556,0x1555,0x1555,0x1555,0x1555,0x1555,0x1555,
0x1683,0x1a8c,0x1a8c,0xfa4c,0x4acf,0xbacf,0x9a3f,0x9533,
0x7e91,0xbe81,0xfe46,0xfe06,0xfe0a,0xf819,0xf818,0xf428,
0xf001,0xcc32,0xc032,0xc002,0xf302,0x0202,0x0206,0x0606,
0x550a,0x542a,0x58ea,0x5caa,0x50aa,0x63aa,0x72aa,0x42aa,
0xff82,0x7f0a,0x7e08,0x7408,0x5c0c,0x580c,0x7008,0x6000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0002,
0x0000,0x0000,0x0000,0x0000,0x0000,0x1544,0xffff,0xffaa,
0x0100,0x0200,0x0000,0x0200,0x0000,0x0080,0xa000,0x5888,
0x3800,0x1800,0x0800,0x0b00,0x0e00,0x0300,0x0340,0x53f0,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x3c00,0x17f0,0x05ff,0x07ff,0x06aa,0x07aa,0x07aa,0x0faa,
0x1555,0x1555,0x1555,0x1555,0x1555,0x1555,0x1555,0x0000,
0x5623,0x542b,0x548b,0x5c8f,0x5a8f,0x5e0f,0x582e,0x0000,
0xf028,0xe060,0xd060,0xc090,0x8140,0x0250,0x0243,0x0000,
0x0609,0x0605,0x2605,0x1505,0x9505,0x5505,0x5505,0x0000,
0x43ff,0x43ff,0x4bff,0xcfff,0xcfff,0xcffe,0x0ffc,0x0000,
0x8030,0x8030,0x4000,0x0040,0x0080,0x0180,0x0240,0x0000,
0x0002,0x000a,0x000b,0x002b,0x002f,0x002f,0x043f,0x0000,
0xaa5a,0xa556,0xa955,0xa555,0xa555,0xa555,0xaa55,0x0000,
0xac95,0xacef,0xa9eb,0xa9fa,0xa95a,0xa9ce,0xabc6,0x0000,
0x7ff0,0xf5e0,0xedec,0xedec,0xa9e8,0xa9e8,0xa9e8,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x4000,0x0000,0x0000,
0x0f95,0x0395,0x03a5,0x00aa,0x00ea,0x00e9,0x003a,0x0000
};

const u16 GK2_PALS1[60] = {
0x0000,0x0360,0x058b,0x0247,
0x0000,0x0371,0x047a,0x0225,
0x0000,0x057a,0x0125,0x0258,
0x0000,0x047a,0x0137,0x0013,
0x0000,0x047a,0x0224,0x0855,
0x0000,0x0259,0x0136,0x047b,
0x0000,0x058b,0x0136,0x0369,
0x0000,0x0223,0x0358,0x0855,
0x0000,0x0579,0x0247,0x048b,
0x0000,0x0129,0x0369,0x0125,
0x0000,0x0113,0x0117,0x034d,
0x0000,0x045d,0x022b,0x0016,
0x0000,0x0128,0x023e,0x046b,
0x0000,0x0239,0x033e,0x0360,
0x0000,0x0223,0x023c,0x0471
};

const u16 GK2_PALS2[60] = {
0x0000,0x0fff,0x0cef,0x09bc,
0x0000,0x0fff,0x07ad,0x0aef,
0x0000,0x06ad,0x0aff,0x07df,
0x0000,0x09df,0x06ad,0x0fff,
0x0000,0x0bde,0x0fff,0x07bd,
0x0000,0x0cff,0x07ae,0x09df,
0x0000,0x07ad,0x0adf,0x0d98,
0x0000,0x069d,0x0aef,0x08bf,
0x0000,0x05ad,0x06ce,0x08ef,
0x0000,0x07be,0x0c87,0x0fb9,
0x0000,0x0a88,0x0fa9,0x0fca,
0x0000,0x0fff,0x0abc,0x0c87,
0x0000,0x0fed,0x0d98,0x09df,
0x0000,0x07ac,0x07ae,0x09cf,
0x0000,0x0bef,0x09cf,0x07ae
};

const u8 GK2_PALIDX1[120] = {
0x00,0x00,0x00,0x00,0x01,0x00,0x00,0x00,0x01,0x00,0x00,0x00,
0x00,0x00,0x02,0x03,0x02,0x04,0x04,0x02,0x05,0x02,0x00,0x00,
0x00,0x00,0x02,0x02,0x06,0x07,0x07,0x04,0x04,0x02,0x02,0x00,
0x00,0x08,0x06,0x06,0x06,0x04,0x04,0x07,0x07,0x03,0x04,0x08,
0x00,0x08,0x05,0x06,0x02,0x01,0x01,0x08,0x05,0x03,0x06,0x08,
0x00,0x06,0x05,0x05,0x09,0x01,0x06,0x08,0x05,0x02,0x06,0x00,
0x00,0x06,0x08,0x05,0x02,0x02,0x02,0x03,0x03,0x09,0x02,0x00,
0x00,0x06,0x08,0x05,0x02,0x03,0x0a,0x0b,0x0c,0x09,0x03,0x02,
0x00,0x08,0x08,0x06,0x02,0x0c,0x0a,0x0b,0x0c,0x03,0x0b,0x06,
0x00,0x00,0x00,0x00,0x01,0x0d,0x01,0x00,0x00,0x01,0x0e,0x0d
};

const u8 GK2_PALIDX2[120] = {
0x00,0x00,0x00,0x00,0x01,0x02,0x03,0x01,0x04,0x00,0x04,0x00,
0x00,0x00,0x00,0x05,0x05,0x06,0x06,0x07,0x08,0x04,0x00,0x00,
0x00,0x00,0x00,0x05,0x02,0x09,0x0a,0x09,0x07,0x01,0x0b,0x00,
0x00,0x00,0x05,0x02,0x08,0x0c,0x0c,0x09,0x07,0x07,0x0b,0x00,
0x00,0x01,0x02,0x07,0x0d,0x05,0x05,0x05,0x07,0x07,0x04,0x04,
0x00,0x03,0x02,0x08,0x00,0x05,0x0e,0x07,0x07,0x04,0x00,0x00,
0x00,0x01,0x08,0x08,0x00,0x07,0x07,0x0e,0x07,0x03,0x00,0x00,
0x00,0x04,0x08,0x08,0x04,0x01,0x0d,0x00,0x03,0x06,0x04,0x00,
0x00,0x04,0x08,0x04,0x00,0x05,0x03,0x07,0x03,0x07,0x00,0x07,
0x00,0x01,0x08,0x00,0x01,0x06,0x0d,0x0e,0x07,0x07,0x0d,0x0e
};

#define GK2_ID {GK2_WIDTH,GK2_HEIGHT,GK2_TILES,(u16*)GK2_TILES1,(u16*)GK2_TILES2,GK2_NPALS1,(u16*)GK2_PALS1,GK2_NPALS2,(u16*)GK2_PALS2,(u8*)GK2_PALIDX1,(u8*)GK2_PALIDX2}

const SOD_IMG GK2_IMG = GK2_ID;
