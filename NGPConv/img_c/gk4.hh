#include "img.h"

#define GK4_WIDTH 12
#define GK4_HEIGHT 10
#define GK4_TILES 240

#define GK4_NPALS1 15
#define GK4_NPALS2 15

const u16 GK4_TILES1[960] = {
0x5555,0x400a,0x6aaf,0x7fff,0x7aaf,0x7aaf,0x7eaf,0x7eae,
0x5555,0xaaac,0xffaf,0xf55f,0xd057,0x516a,0x556a,0x417a,
0x5555,0x67ad,0x6af5,0x550d,0x550d,0x950f,0xa93b,0x8130,
0x5555,0xaabc,0xfff8,0xaaa8,0xaaa8,0xbef8,0xbc0c,0x800e,
0x5555,0x2eaf,0x2eff,0xaeff,0xaeee,0xfefb,0xffba,0xfffe,
0x5555,0xfeab,0xffac,0xbfbf,0xefff,0xffec,0xfef3,0xfac0,
0x5555,0x03ff,0xf3ff,0xf00c,0xc000,0xc000,0xf000,0x0002,
0x5555,0xc2aa,0x03f0,0x0000,0x0000,0x0000,0x0000,0xb00e,
0x5555,0xf2c0,0x32c0,0x3bc0,0x3bc0,0x3fc0,0x3ec0,0xffaa,
0x5555,0x0003,0x0003,0x0003,0x0000,0x0000,0x0003,0xaaaa,
0x5555,0x00bb,0xa2fc,0xabff,0xafea,0xefc1,0xaf81,0x0f84,
0x5555,0x0020,0x0000,0x018a,0x140a,0xfe2a,0x00a6,0x0029,
0x7eaf,0x6eac,0x60be,0x4000,0x4000,0x4000,0x4000,0x4000,
0x61ff,0x61ff,0x69ff,0xa9ff,0xa9ff,0xa97f,0x297f,0x29f3,
0x031d,0x012e,0xfe08,0xfe00,0xe800,0x8000,0xc000,0x0000,
0x2008,0x155f,0x0001,0x0001,0x0001,0x0001,0x0003,0x0000,
0xfffb,0xfebf,0xfefc,0xffff,0xffff,0x3c0f,0x300f,0x0000,
0x3b30,0xfcf0,0xeccf,0xbfbc,0xfcbc,0xffcc,0x0000,0x003c,
0x0003,0x0001,0x8802,0x0000,0x0000,0x0001,0x0000,0x0004,
0x5514,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x1500,0x0044,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0005,0x0008,0x0002,0x000b,0x002f,0x002c,
0x1b84,0x5a84,0xba14,0xfe84,0xe705,0x8295,0x0095,0x001d,
0x001b,0x015b,0x011a,0x401a,0x501a,0xd006,0xd406,0xe406,
0x8000,0x8000,0x8000,0x8000,0x8001,0x8050,0x9000,0x8000,
0x2953,0x2573,0xa55f,0xa557,0xa557,0x2555,0x0555,0x0155,
0x0000,0x1fc0,0x6000,0xe400,0x9300,0xb400,0xaec0,0xa5c0,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x800a,0x3800,0x0080,0x0038,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x4000,0x0015,0x15aa,0xf6aa,
0x0030,0x00c1,0x0100,0x0000,0x0000,0xfd00,0xfff4,0xaaa1,
0x0000,0x0000,0x0000,0x0000,0x0000,0x06aa,0x2aaa,0xabff,
0x0000,0x0000,0x0000,0x0000,0x0000,0x4000,0xd000,0xfc00,
0x009c,0x0ea0,0x0f70,0x0e40,0x01c0,0x0f00,0x0800,0x0000,
0x001b,0x0002,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x580a,0x500a,0xd02a,0x382a,0x0829,0x3024,0xc035,0xc03f,
0x4000,0x4000,0x4000,0x4000,0x4000,0x40aa,0x40af,0x4003,
0x02aa,0x02aa,0x02aa,0x00aa,0x00bc,0x0ff0,0x0ffc,0xc03f,
0xbc00,0xbd00,0xae00,0xad00,0xf000,0x1400,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0005,
0x0000,0x0000,0x000f,0x00ff,0x0ffc,0x0ff0,0xffc0,0xff00,
0x39aa,0xfd6a,0xf016,0xc081,0x0010,0x0020,0x0030,0x000f,
0x5542,0x5545,0x9555,0xe955,0xfa55,0x3ea5,0x17a5,0x06e5,
0x5aff,0x56af,0x5aaf,0x556b,0x656b,0x55ab,0x56af,0x5aac,
0xff00,0x3f80,0x3f80,0x3fa0,0x3fa0,0xfea0,0xfaa0,0x3fe0,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0041,0x015a,0x0158,0x0560,0x5560,0x1060,0x0042,
0x7009,0xc006,0x0006,0x0006,0x00c6,0x00c2,0x00c2,0x0040,
0x4083,0x4080,0x403e,0x4000,0x4000,0x4028,0x4000,0x4000,
0x80e3,0x00e8,0x00c8,0x00e4,0x006c,0x004c,0x003c,0x000c,
0x0000,0x0000,0x0000,0x0005,0x0015,0x0154,0x0550,0x1540,
0x0015,0x0054,0x0500,0x5000,0x4000,0x0000,0x0000,0x0000,
0x5000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x06e5,0x05f5,0x01f9,0x02fd,0x02bd,0x01ac,0x0154,0x0154,
0x6afc,0x6abc,0x55ac,0x05af,0x016f,0x006b,0x016b,0x016b,
0xffe0,0xffe0,0xffc0,0xffc0,0xff00,0xff00,0xfe00,0xc000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0083,0x0003,0x0003,0x0003,0x0002,0x0000,0x0000,0x0000,
0x8800,0x04c0,0x04f0,0xa4f2,0x24f1,0x20c1,0x20c9,0x0005,
0x8000,0x8000,0x8000,0x8000,0x8000,0x8000,0x8000,0x8000,
0x0353,0x035d,0x0303,0x0103,0x03fa,0x00ea,0x0ea8,0x3a8a,
0xfc00,0x3401,0xf5fe,0xdaa9,0xaaaa,0x5aaa,0x295a,0x2555,
0x0000,0x0000,0xfa40,0xaff9,0xaaff,0xaabf,0xaaaf,0xbfff,
0x0000,0x0000,0x0000,0x0000,0xf400,0xffd0,0xffd6,0xff6a,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x5000,0xa540,
0x00fc,0x003c,0x01fc,0x01fc,0x01fc,0x01fc,0x01ff,0x01ff,
0x01af,0x01af,0x01af,0x00af,0x01af,0x006f,0x015f,0x151f,
0xe000,0xe080,0xe000,0xe280,0xeaa2,0xe802,0xc001,0xc005,
0x0000,0x0000,0x0000,0x0000,0xa000,0x6800,0x5580,0x0180,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0955,0x0555,0x0955,0x0a25,0x28a9,0x2aaa,0xaaaa,0x02aa,
0x8000,0x8000,0x8000,0x8003,0x8000,0x8000,0x8003,0x800f,
0x6845,0x59d4,0x1fd4,0xcf57,0xff7f,0xff7f,0xfdff,0xffff,
0xaaa2,0xbafa,0xafff,0xafff,0xafff,0xafff,0xbfff,0xffff,
0xabff,0xaaff,0xaabf,0xbffa,0xffc8,0xbffc,0xaffc,0xaffc,
0xfd0a,0xf42a,0xfc02,0x5602,0x02aa,0x006a,0x0006,0x0001,
0xa950,0xa97f,0x29ff,0x2bff,0xa7ff,0x97ff,0xafff,0x5fff,
0x0069,0x80a9,0xfaee,0xafff,0x56ab,0x5556,0x5555,0x5555,
0x501f,0x240b,0x240b,0x940b,0xf80b,0xbf0b,0xafdb,0xabeb,
0xd558,0xd560,0xc020,0xc0a0,0xc080,0xd0a0,0xd0a0,0xf0a0,
0x0000,0x0001,0x0002,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0f58,0x00fc,0x000c,0x0003,0x0003,0x0003,
0x026a,0x096a,0x055a,0x0f1a,0x0fce,0x03ce,0x03c3,0x03c0,
0x803f,0x800f,0x800f,0x800f,0x800f,0x800f,0x800f,0x800f,
0xfdd5,0xdb55,0xd975,0xafd5,0xea55,0x9faa,0x5556,0x7555,
0x5555,0x5555,0x5555,0x5555,0x5556,0x5556,0xaaaa,0x55aa,
0xaa94,0xaa54,0xaa5c,0xaa5c,0xa94c,0xa550,0x9530,0xa540,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x01aa,0x006a,0x0006,0x0001,0x0000,0x0000,0x0000,0x0000,
0xaaaa,0xaaaa,0xefaa,0xffea,0x1ffe,0x0bff,0x0bd7,0x07c0,
0xaaab,0xaaaa,0xaaab,0xaaab,0xaaab,0xaabf,0xfbff,0x6f55,
0xf420,0xfc28,0xffa8,0xffda,0xffc6,0xff02,0xff01,0xff0d,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x8000,
0x0001,0x0001,0x0001,0x0005,0x0006,0x0012,0x0015,0x0006,
0x0100,0x0100,0x0100,0xc100,0xc200,0x8000,0xc000,0x0800,
0x800f,0x8003,0x8003,0x8000,0x8000,0x8000,0x8000,0x8000,
0xebaf,0xeaff,0xfaff,0xffff,0x3fff,0x0fff,0x03ff,0x0000,
0x555a,0x556a,0x55aa,0x56ab,0xaaaf,0xaafc,0xae90,0x0000,
0xa4c0,0x9600,0x5c00,0x4000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x1400,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0001,0x0003,0x0007,
0x0940,0x2940,0xa900,0x2500,0x5500,0xf100,0xf100,0xfc00,
0x003f,0x003f,0x008d,0x00a1,0x0280,0x0a00,0x0800,0x0000,
0x7f35,0xabd5,0xabd5,0xad55,0x1055,0x0150,0x1400,0x1000,
0xa000,0xe400,0xd800,0xc500,0x0100,0x0100,0x0000,0x0000,
0x0007,0x0003,0x0007,0x0107,0x0057,0x0017,0x0007,0x0004,
0x0400,0x0400,0x0400,0x0400,0x0400,0x0400,0x0000,0x0000,
0x8000,0x8000,0x8000,0x8000,0x8000,0x8000,0x8000,0xaaaa,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0xaaaa,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0xaaaa,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0xaaaa,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0xaaaa,
0x0019,0x0028,0x0065,0x006a,0x00aa,0x0566,0x01a4,0xffff,
0xf400,0xf000,0xe000,0x0015,0x0000,0x0000,0x0000,0xaaaa,
0x0200,0x2e00,0xb000,0xac02,0x0c02,0x0c02,0x0a02,0x5555,
0x0000,0x0000,0x8800,0x8800,0xa800,0xe800,0xe800,0x5555,
0x0000,0x00a9,0x00a9,0x00a9,0x00a9,0x01a9,0x02a9,0xffff,
0x0000,0x5400,0x56a5,0x55aa,0x55aa,0x55aa,0x55aa,0xffff,
0x2000,0x6000,0x9000,0x8000,0x8000,0x4000,0x4000,0xffff
};

const u16 GK4_TILES2[960] = {
0x0000,0x1550,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0500,0x0400,0x0000,0x1400,
0x0000,0x0000,0x0000,0x0050,0x0050,0x0060,0x0040,0x0042,
0x0000,0x0002,0x0001,0x0001,0x0001,0x0002,0x0002,0x0000,
0x0000,0x4000,0x4000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0400,
0x0000,0x0400,0x0400,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0xff00,0x0c00,0x0000,0x0000,0x0034,0x0018,0x5012,
0x0000,0x0006,0x002a,0x0020,0xc260,0x0080,0xd600,0x6a80,
0x0000,0x0000,0x0a00,0x2aab,0x2aaa,0x2aab,0x3ffd,0x1555,
0x0400,0x0400,0x0000,0x0000,0x0000,0x0000,0x4000,0x4000,
0x0040,0x0040,0x0062,0x00ff,0x03ff,0x1575,0x1555,0x355f,
0x8002,0xc000,0xaffc,0xfffc,0xfffc,0x5554,0x5554,0xf556,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0001,0x0002,
0x0082,0xebef,0xefff,0x5555,0x5555,0x5555,0x5555,0x5555,
0xc0af,0xff22,0xbffd,0x5555,0x5555,0x5555,0x5555,0x5555,
0xffff,0xffff,0xd7f0,0x5572,0x5578,0x55e0,0x57c0,0x5783,
0xc033,0x0031,0x0083,0x0033,0x00b0,0x1400,0x5500,0x5540,
0xffc0,0xfc00,0xb8c0,0x2fc0,0x0fc0,0x0ef0,0x03e0,0x03e0,
0x2aa9,0x2aaa,0x2aa9,0x2aa9,0x2a9c,0x270d,0x0f6a,0x1aaa,
0x4000,0x4000,0x0000,0x0000,0x0000,0x8000,0xf000,0xf800,
0x35f6,0x003a,0x0daa,0x02aa,0x00aa,0x006a,0x002a,0x002a,
0xaaaa,0xaaaa,0xaaaa,0xaaaa,0xaaaa,0xaaaa,0xaaaa,0xaaaa,
0x0000,0x4000,0x5a00,0x5540,0x5556,0x5555,0x5555,0x5556,
0x0000,0x0000,0x0000,0x0000,0x0000,0x8000,0x4000,0x0000,
0x000a,0x0018,0x005d,0x0255,0x0255,0x00bb,0x0003,0x000c,
0x6aaa,0x6aaa,0xaaaa,0xaaaa,0xabff,0xb000,0x4000,0x0000,
0xaaaa,0xaaaa,0xaaaa,0xaaaa,0xfaaa,0x1fea,0x077f,0x01d5,
0x5b01,0x5005,0x5005,0x5015,0x5815,0x7055,0xf155,0xe955,
0x5540,0x5554,0x5555,0x5555,0x5555,0x5555,0x5556,0x5555,
0x02a0,0x0aa0,0x0a80,0x4280,0x7280,0xca80,0x1680,0x3a80,
0x1555,0x1555,0x15a9,0x15a9,0x1aa5,0x2b00,0x1b00,0x17dc,
0xa800,0xa800,0xa800,0xaa00,0xaa00,0x6000,0xe000,0x3640,
0x0255,0x0055,0x0055,0x0055,0x0257,0x035f,0x97fd,0x7d5f,
0x5555,0x5555,0x5555,0x5555,0x5555,0x5555,0x5556,0x5560,
0x555f,0x5557,0x5570,0x5700,0x5002,0xe00d,0x0037,0x00d7,
0x8000,0x0000,0x0a00,0x3e00,0xfb40,0x9540,0x9f40,0xfff0,
0x0014,0x0020,0x0000,0x0000,0x0000,0x0000,0x0000,0xf000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0069,0x0029,0x0019,0x000a,0x0005,0x000e,0x000e,0x0006,
0xf555,0xf555,0xff55,0xffd5,0xfff5,0xfffd,0xffff,0xffff,
0x5555,0x573c,0x5400,0x5c02,0x700a,0x000a,0x8d0a,0xa928,
0x0560,0x2aa0,0x6aa0,0xaaa0,0xa920,0x562c,0x962c,0x562f,
0x2a28,0x1a2c,0x15c0,0x15c3,0x15c1,0x1681,0x2a95,0x2a55,
0x2500,0xdd03,0x7d01,0x5503,0x5703,0xfa03,0xea83,0xaa73,
0x5555,0x5555,0x555f,0x5570,0xff80,0xf802,0xe00b,0x803f,
0x5580,0x5602,0xa0bd,0x0b7d,0x2ffd,0xffff,0xffff,0xffff,
0x0b7f,0x57ff,0x57ff,0x57ff,0x57ff,0xd7ba,0x57aa,0x57aa,
0xefff,0xeefe,0xeaea,0xeaaa,0xaaaa,0xaaaa,0xaaab,0xaaaa,
0x5000,0x5000,0x5400,0x5400,0x5400,0x5402,0x5402,0x5402,
0x0000,0x0000,0x0000,0xa000,0xa800,0xea00,0xe800,0xf800,
0x0009,0x0009,0x0019,0x0015,0x00e5,0x0065,0x00a5,0x1a99,
0x655a,0x5656,0x6596,0x5556,0x556a,0x555a,0x556a,0x556a,
0xab28,0xa5f8,0xa95c,0xa95c,0xaa5c,0xaa57,0xaa55,0xaa56,
0x23ab,0xa32f,0xe20e,0x030c,0x810c,0x8918,0x8530,0xe960,
0x1fff,0x3fbf,0x2aaa,0x2aaa,0x2aaa,0x2aaa,0x2aaa,0x2aaa,
0xfc00,0xfc00,0xfc5c,0xfc7c,0xfc00,0xff00,0xf002,0xc020,
0x0155,0x0154,0x0000,0x0000,0x0000,0x0000,0x8000,0x8000,
0x5555,0x5555,0x0015,0x0000,0x0000,0x0000,0x0000,0x0000,
0x57aa,0x5faa,0x7faa,0xb7aa,0x02aa,0x000a,0x0000,0x0000,
0x5555,0x5555,0x5555,0x5555,0x5555,0x5555,0x0555,0x0015,
0x5502,0x5682,0x5402,0x5402,0x5402,0x5402,0x5400,0x5400,
0xf800,0xf800,0xb800,0xbe00,0xb800,0xaa00,0xa800,0x8080,
0x0fff,0x0f3f,0x0fff,0x0c3f,0x000c,0x01a4,0x16a4,0x1550,
0xa6aa,0x5aaa,0x6aaa,0x556a,0x0555,0x0156,0x0016,0xfc16,
0xa956,0xa55a,0x956a,0x56aa,0x5aaa,0x6aa9,0xaaa7,0x555f,
0xb000,0x9000,0x5000,0xb0c0,0x4300,0xc000,0x0000,0xd400,
0x1555,0x1555,0x1555,0x1554,0x1556,0x155a,0x1558,0x1560,
0x0330,0x0003,0xc003,0x3000,0x0000,0x0000,0x0000,0x0000,
0x0008,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0001,0x0001,0x0001,0x0001,
0x0090,0x0240,0x0254,0x0054,0xf400,0xff00,0xffe0,0xffe8,
0x0005,0x0000,0x8000,0x8000,0x0000,0x0000,0x0000,0x0000,
0x5500,0x1500,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0a80,0x82a0,0x82a0,0x02f0,0x02e0,0x00a0,0x0000,0x0000,
0x0001,0x0005,0x2a45,0x2905,0x2915,0x0905,0x0905,0x0905,
0x556a,0x55d4,0xf754,0xff55,0xfffd,0xffff,0xffff,0xffff,
0xaaaa,0xaaaa,0x5002,0x7502,0x5552,0xd554,0xf554,0xff54,
0xfc00,0xf000,0xa000,0xa000,0xa010,0xa420,0x6814,0x6415,
0x1540,0x1560,0x1560,0x1560,0x1560,0x1560,0x1560,0x1560,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0001,0x0001,0x0001,0x0001,0x0001,0x0005,0x0005,0x0015,
0x557f,0x557f,0x655f,0x6a5f,0x6a9f,0x6a9f,0xaa5f,0xaa9f,
0xa800,0xaa00,0xaae0,0xaaa8,0xaaab,0xaaad,0xaa95,0xaab5,
0x0000,0x0000,0x0000,0x0000,0xc000,0x6000,0x6000,0x602f,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0145,0x0241,0x0001,0x0000,0x0000,0x0000,0x0000,0x0000,
0x5555,0x9555,0xf955,0xff95,0xfea5,0xff95,0xee95,0x3ba5,
0x5aac,0x56bc,0x56bc,0x55b0,0x55b0,0x558c,0x56c0,0x5af0,
0x64d5,0xe4d5,0xe4d5,0x2455,0x2455,0x2755,0x2f56,0xe15a,
0x1570,0x155c,0x2a9c,0x2aaa,0x2aaa,0x296a,0x2aaa,0x2aaa,
0x0000,0x0000,0x0000,0x0000,0xc000,0xb000,0xac00,0xa96a,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0003,0x0009,0x796b,
0x003f,0x00ff,0x03a5,0x3555,0xd555,0x5556,0x5aab,0xbfff,
0x555e,0x555f,0x555f,0x5d57,0x5f5e,0x5ede,0xdf57,0x8157,
0xfff5,0xffff,0xffff,0xffff,0xffff,0xfffc,0xfffc,0xfff0,
0x502a,0x402a,0x00aa,0x40aa,0x00aa,0x00aa,0x00aa,0x02aa,
0x5f40,0x5540,0x5530,0x5704,0x5435,0x50d5,0x7367,0x7d69,
0x0000,0x0000,0x0000,0x0000,0xc700,0x5c0e,0xc3f6,0xcfda,
0x0556,0x0155,0x0155,0x2065,0xf469,0xf86a,0xfe5a,0xffff,
0xa950,0xaa54,0xa550,0xa450,0x5900,0x9540,0xa550,0xe553,
0xd255,0xd155,0xe156,0xe2aa,0xa2aa,0xa2aa,0xbeaa,0xbeaa,
0x3fff,0x3fff,0x3fff,0x3fff,0x3fff,0x3fff,0x3fff,0x0000,
0xffff,0xffff,0xffff,0xffff,0xffff,0xffff,0xffff,0x0000,
0x57fe,0xeabe,0xfbba,0xfeaa,0xaaaa,0xaaaa,0xeaba,0x0000,
0xffff,0xfeff,0xfaff,0xffff,0xffff,0xffff,0xffff,0x0000,
0x5aa9,0xaaa5,0xaa95,0xaaa9,0x6aa9,0x6aa9,0x6a6a,0x0000,
0xffc0,0xffc2,0xff00,0xff00,0xfe00,0xd000,0x5401,0x0000,
0x03ff,0x0fea,0x0eab,0xfac0,0x9fff,0x9baf,0x5faa,0x0000,
0xfcd6,0xc057,0x0357,0x0258,0xf258,0xf358,0xb0fc,0x0000,
0x7f6a,0xffaa,0x31a9,0x3355,0x0355,0x0355,0x0155,0x0000,
0xaaaa,0xa900,0x5500,0x5500,0x5500,0x5400,0x5800,0x0000,
0xaa9a,0x03ea,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x497f,0x09fd,0x0fdf,0x2757,0x3555,0x1ff5,0x3ffd,0x0000
};

const u16 GK4_PALS1[60] = {
0x0000,0x0360,0x0666,0x0112,
0x0000,0x0862,0x046a,0x0114,
0x0000,0x0444,0x0101,0x0887,
0x0000,0x0432,0x0559,0x0c94,
0x0000,0x0842,0x0d92,0x0004,
0x0000,0x0999,0x0360,0x027e,
0x0000,0x057b,0x014b,0x0016,
0x0000,0x025c,0x0129,0x0005,
0x0000,0x0102,0x0139,0x0877,
0x0000,0x0776,0x0831,0x0212,
0x0000,0x0877,0x0d82,0x035b,
0x0000,0x0666,0x026d,0x003c,
0x0000,0x016d,0x012a,0x028f,
0x0000,0x0ea3,0x0c71,0x0963,
0x0000,0x046b,0x0127,0x0360
};

const u16 GK4_PALS2[60] = {
0x0000,0x0bbb,0x0fc5,0x07ae,
0x0000,0x0fd7,0x0bbb,0x0ddc,
0x0000,0x0eed,0x0bbb,0x0ddc,
0x0000,0x0ccc,0x0eee,0x07cf,
0x0000,0x0bbb,0x069e,0x08bf,
0x0000,0x0ddd,0x07af,0x0bbb,
0x0000,0x0edd,0x0aaa,0x0ccb,
0x0000,0x0ccc,0x0fee,0x0aaa,
0x0000,0x0fd5,0x0ff9,0x0fff,
0x0000,0x0ace,0x0eee,0x0cdf,
0x0000,0x0fff,0x0ddd,0x0bbb,
0x0000,0x08df,0x09ef,0x06af,
0x0000,0x06bf,0x09df,0x0fc6,
0x0000,0x0fd6,0x08df,0x0ff9,
0x0000,0x0ff9,0x0fd6,0x0fc4
};

const u8 GK4_PALIDX1[120] = {
0x00,0x01,0x02,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x01,0x03,
0x00,0x04,0x01,0x02,0x00,0x00,0x02,0x05,0x05,0x06,0x01,0x07,
0x05,0x04,0x02,0x05,0x02,0x08,0x06,0x07,0x07,0x08,0x09,0x08,
0x00,0x09,0x09,0x0a,0x02,0x08,0x07,0x07,0x07,0x05,0x0b,0x0a,
0x00,0x02,0x0b,0x0b,0x0a,0x05,0x06,0x07,0x07,0x05,0x0b,0x03,
0x05,0x08,0x07,0x07,0x04,0x0a,0x0a,0x06,0x01,0x0b,0x05,0x0b,
0x05,0x0c,0x0b,0x07,0x04,0x0a,0x07,0x06,0x04,0x0a,0x03,0x08,
0x05,0x0c,0x07,0x08,0x05,0x06,0x06,0x06,0x04,0x0a,0x0d,0x03,
0x05,0x0b,0x07,0x08,0x0b,0x06,0x04,0x03,0x09,0x0d,0x0d,0x0a,
0x05,0x05,0x05,0x05,0x05,0x0e,0x05,0x00,0x00,0x0e,0x0e,0x0e
};

const u8 GK4_PALIDX2[120] = {
0x00,0x01,0x02,0x01,0x03,0x04,0x04,0x04,0x04,0x04,0x00,0x05,
0x02,0x01,0x06,0x06,0x04,0x04,0x07,0x06,0x06,0x05,0x05,0x04,
0x07,0x08,0x07,0x03,0x02,0x02,0x02,0x09,0x09,0x05,0x02,0x05,
0x0a,0x07,0x02,0x02,0x06,0x02,0x05,0x04,0x0b,0x03,0x05,0x0c,
0x07,0x02,0x06,0x06,0x06,0x01,0x04,0x04,0x0b,0x0b,0x0b,0x01,
0x06,0x05,0x04,0x04,0x06,0x04,0x04,0x04,0x03,0x0c,0x0b,0x0b,
0x04,0x00,0x04,0x04,0x01,0x00,0x04,0x04,0x01,0x0d,0x0d,0x03,
0x04,0x04,0x04,0x04,0x07,0x06,0x02,0x04,0x01,0x0e,0x0e,0x07,
0x03,0x0a,0x02,0x0a,0x06,0x0a,0x01,0x07,0x07,0x08,0x08,0x0a,
0x01,0x01,0x02,0x06,0x03,0x05,0x0a,0x0a,0x07,0x03,0x03,0x02
};

#define GK4_ID {GK4_WIDTH,GK4_HEIGHT,GK4_TILES,(u16*)GK4_TILES1,(u16*)GK4_TILES2,GK4_NPALS1,(u16*)GK4_PALS1,GK4_NPALS2,(u16*)GK4_PALS2,(u8*)GK4_PALIDX1,(u8*)GK4_PALIDX2}

const SOD_IMG GK4_IMG = GK4_ID;

