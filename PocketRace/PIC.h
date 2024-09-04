#include "img.h"

#define PIC_WIDTH 12
#define PIC_HEIGHT 10
#define PIC_NPALS1 16
#define PIC_NPALS2 16

const u16 PIC_TILES1[960] = {
0x5555,0x4000,0x4000,0x4000,0x4000,0x4000,0x4000,0x4000,
0x5555,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x5555,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x5555,0x0000,0x0000,0x0000,0x0000,0x000b,0x0028,0x0200,
0x5555,0x0082,0x003c,0x003a,0x3f00,0xc000,0x0000,0x0f00,
0x5555,0xc000,0xec00,0xfaf0,0x00ff,0x0003,0xfc0f,0x0fff,
0x5555,0x0fec,0x000e,0x0000,0x0f00,0xbfff,0x030f,0xfff3,
0x5555,0x3c33,0x0882,0xa882,0x2882,0x0bcb,0xbf3c,0xaaaa,
0x5555,0xc000,0xc000,0xc3af,0xeaff,0x3cfc,0xffff,0xabab,
0x5555,0x0000,0x0000,0xfc00,0x00c0,0x3c3c,0xffc3,0x00e0,
0x5555,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0xc000,
0x5555,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x4000,0x4000,0x4000,0x4000,0x4000,0x4000,0x4000,0x4000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0142,0x050b,
0x0007,0x4069,0x02a0,0x0ac0,0x2903,0xa41c,0xd140,0x4400,
0xd000,0x001f,0x07ec,0x6df0,0xc344,0x0c3c,0x3480,0xd283,
0x56a9,0x425a,0x056a,0x09af,0x29bf,0xaacc,0xaac0,0xbbff,
0x82a0,0x92a4,0xa0a9,0xf4be,0xf86e,0xfd5a,0x020a,0xff96,
0xaaaa,0x2aaa,0x1aaa,0x8a9a,0x8aa6,0xc2aa,0xa4aa,0xa8aa,
0xeaaa,0xfaaf,0xbd6a,0xa9fa,0xe953,0xfa7c,0xfa9f,0x4a92,
0x5005,0x8a00,0x80c0,0xa424,0xad0a,0xab01,0x2bc0,0x8ae0,
0x0000,0x0100,0x0040,0x0000,0x0000,0x8000,0x5000,0x1000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x4000,0x4000,0x4000,0x4000,0x4000,0x4000,0x4000,0x4000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x003f,0x00d7,0x035d,0x0524,0x04b0,0x33c0,0x5200,0x4c01,
0x4001,0x4005,0x0045,0x0107,0x0416,0x1059,0x5078,0x4164,
0x4a42,0x2f0a,0xbd1a,0x8d2a,0x0479,0x31b5,0x3192,0x52ba,
0xaaab,0x8afc,0x8b00,0xab00,0x7000,0x8000,0xc000,0x00ea,
0xffe8,0xc039,0x0002,0x0000,0x0000,0x0000,0x3000,0xaaab,
0x6819,0x160a,0x9146,0xea80,0x03a8,0x0009,0x000e,0x0002,
0x5aa1,0x1ba0,0x5be9,0x6bfa,0x052d,0x0000,0xa041,0xaa9a,
0x82ac,0x519b,0x559a,0x06aa,0x06a5,0x2aa9,0x66a9,0x96aa,
0x0000,0x0000,0x8000,0xa000,0xa400,0xa900,0xaa80,0xaa30,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x4000,0x4000,0x4000,0x4000,0x4000,0x4000,0x4000,0x4000,
0x0001,0x0001,0x0000,0x0000,0x0003,0x0003,0x0008,0x0008,
0x1802,0x3008,0x8028,0x8020,0x0080,0x0280,0x0200,0x0a00,
0x07a0,0x0db1,0x1f81,0x12c1,0x0600,0x0e0c,0x1808,0x583c,
0xa6ab,0x52a8,0x42a9,0x21aa,0x241a,0x92a9,0x9269,0x822a,
0x0ec0,0x2c00,0x8000,0x0000,0x001a,0x02a4,0x1a40,0xa640,
0x003e,0x0000,0x0000,0x1eaa,0xaa56,0x0001,0x0005,0x0060,
0x9402,0x0fb3,0x00fe,0xaafe,0x6aaa,0x4555,0x4144,0x0145,
0xaa88,0x4af8,0x06fa,0x84fa,0x84aa,0x86a2,0xaa6a,0xa6a5,
0xbfef,0x24ef,0xacef,0xa31a,0xa30e,0xa2c2,0xaab3,0xea90,
0xa730,0x60e4,0x6464,0x581a,0xa80a,0xa906,0xaa02,0x6a00,
0x0000,0x0000,0x0000,0x0000,0x4000,0xc000,0xc000,0xc000,
0x4000,0x4000,0x4000,0x4000,0x4000,0x4000,0x4000,0x4000,
0x0000,0x0010,0x0010,0x0040,0x0040,0x02c3,0x0103,0x0101,
0x0500,0x1501,0x1401,0x5c04,0x5404,0x6010,0xf010,0x9010,
0x5c25,0x3c65,0x24e3,0x21a2,0x22a2,0x72e2,0x56e6,0xdee7,
0x833f,0x837d,0x937d,0x927d,0xb6fd,0xb7fd,0xf7bd,0xffbd,
0x5200,0xa200,0xa200,0xaa00,0xa960,0x2550,0x2568,0x25c8,
0x0280,0x0efe,0x3ba2,0x0fa7,0x03af,0x00f7,0x0000,0x0000,
0x008a,0xaaa2,0xa6aa,0x0aaa,0xeaaa,0x808a,0x0082,0x0082,
0x4257,0x4edf,0x6057,0x5d57,0x5d55,0xd057,0xd054,0xd373,
0x6aaa,0xfa3a,0x5a7e,0x1a7a,0x3a7a,0x6efa,0xaada,0xaa9a,
0xfe00,0x5e00,0x9e41,0x12c5,0xbffd,0xf7f4,0x1fc0,0x0fc0,
0xd000,0xd000,0x8000,0x8000,0x0000,0x0000,0x0000,0x0000,
0x4000,0x4000,0x4000,0x4000,0x4000,0x4000,0x4000,0x4000,
0x0502,0x0c0e,0x0c0b,0x001c,0x0030,0x1063,0x10f2,0x0082,
0x8040,0xc040,0x4040,0x0001,0x0101,0x0100,0x0000,0x0000,
0xcbe7,0xd96f,0xd96f,0xf96a,0xed6a,0xec6a,0xa43b,0xa43f,
0xffad,0xfbaf,0xffeb,0xffeb,0xfffb,0xffef,0xffe9,0xffa9,
0xf970,0xef00,0xec00,0xe800,0xe800,0x3b03,0x3a03,0x3ec0,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0xb400,0xf400,
0x0003,0x0003,0x0003,0x0000,0x0000,0x0000,0x0000,0x1000,
0x7076,0x7cff,0x35ff,0x70ff,0x3cfe,0x15f7,0x30f1,0x34c7,
0xaaba,0xaf8a,0xacbe,0xe4be,0xe0ae,0xe06e,0xe027,0x2c2c,
0x0580,0x0b80,0x0590,0x09d0,0x05f0,0x0b70,0xb510,0x4c04,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x4000,0x4000,0x4000,0x4000,0x4000,0x4000,0x4000,0x4002,
0x0183,0x0387,0x0e4f,0x0e0d,0x0a0d,0x0b0c,0x3808,0x2808,
0x0803,0x0803,0x0003,0x000a,0x2008,0x2008,0x200a,0x000a,
0xa53b,0xf43b,0x742b,0x546b,0x54fb,0x50db,0x50dd,0x41dd,
0xffbb,0xfe0b,0xfe0b,0xf90b,0xf80b,0xec0e,0xe003,0xa000,
0x3e90,0x3eb4,0x3fa9,0x1aae,0x5a96,0xfeb7,0xaa77,0xea73,
0x0007,0x1500,0x0e00,0xc155,0xa014,0xb900,0x9240,0xbe90,
0xc000,0x0000,0x0000,0x0000,0x0003,0x001a,0x00ea,0x060a,
0x398e,0x34d6,0x24d7,0xec93,0xa093,0xa481,0xa49e,0xe0aa,
0x6819,0x6aa4,0x5aa9,0x46fa,0x5afa,0x6afa,0xabfe,0x7ffe,
0xe000,0x8000,0x8000,0x7000,0x6000,0xec00,0x9800,0xa800,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x4002,0x4002,0x4000,0x4002,0x4000,0x4000,0x4002,0x4000,
0x2c0c,0x2408,0x2408,0x2408,0x740c,0xf40c,0xd00c,0xc004,
0x0001,0x0001,0x0002,0x0001,0x4005,0x4005,0x4005,0x0004,
0x4155,0x4155,0x0115,0x0513,0x0552,0x0557,0x04d5,0x05c4,
0xb000,0x8000,0x8003,0xc003,0x0000,0x0000,0x000e,0x0028,
0x4615,0x470d,0x0d25,0x04e4,0x07ad,0x3bf0,0x5034,0x003d,
0x55ac,0x5aaa,0x5aaa,0x5aaa,0x5bea,0x56fe,0x55bf,0x691b,
0xe555,0x6aaa,0xaaaa,0xaaaa,0x5aaa,0x559a,0xaaaa,0xaaaa,
0x506a,0xa06a,0xa0a9,0xac69,0x5459,0x5c55,0x5756,0xafda,
0xaaaa,0xbaaa,0x4eaa,0xeaaa,0x8aaa,0x82aa,0x82aa,0x82ab,
0xf600,0xfa00,0xfa00,0xfa40,0xea80,0xed64,0xbf5e,0xbf55,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0xb400,0xfa80,
0x4000,0x4000,0x4000,0x4000,0x4000,0x4000,0x4000,0x4000,
0xc018,0xc022,0x8032,0x0032,0x2020,0x0020,0x0080,0x00c8,
0x0004,0x0014,0x0010,0x0050,0x0050,0x01c0,0x0181,0x0241,
0x0aec,0x23c8,0x2bc8,0x2aa8,0x0ca4,0xa8a4,0xa4a0,0xb0a0,
0x00b0,0x03c0,0x0200,0x0300,0x0d00,0x0800,0x0800,0x1c00,
0x0024,0x0060,0x00f1,0x01f1,0x0371,0x0373,0x0553,0x0d57,
0xae02,0xae0a,0xbe2b,0xbaab,0xbaaf,0xfaaf,0xeaaf,0xe9bc,
0x6aaa,0x56aa,0xd556,0xfd55,0xfff5,0xc033,0x0000,0x0000,
0xacea,0xa4d5,0xa7d5,0x54d5,0x57d5,0xf517,0x0d5f,0x0333,
0xc1ea,0xc30b,0x918b,0xb0e2,0x7062,0x5470,0x5418,0x040a,
0x6a6b,0x6aaf,0xaa9b,0xaaa6,0xaaa9,0x6aa9,0x6aaa,0x5aaa,
0x41e9,0x4006,0x5000,0x5000,0x5000,0xd000,0x9000,0xb000,
0x4000,0x4000,0x4000,0x4000,0x4000,0x4000,0x4000,0x5555,
0x00c8,0x0380,0x0320,0x0220,0x0020,0x00a0,0x02c0,0x5555,
0x0fc3,0x0f0f,0x0f0f,0x3f0e,0x2c3e,0xec0f,0xac3c,0x5555,
0xe0f0,0xb0b0,0x80b0,0xc0b0,0x00f0,0x00f0,0x00f0,0x5555,
0x3c00,0x2c00,0x2000,0x2000,0x2000,0x2000,0xe003,0x5555,
0x1549,0x154d,0x1559,0x5515,0x5515,0x5415,0x5415,0xaaaa,
0xd55c,0x9570,0x5570,0x5540,0x5540,0x5540,0x7340,0x5555,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x5555,
0x0200,0x0200,0x0000,0x0000,0x0000,0x0030,0x0030,0x5555,
0x000e,0x000e,0x0002,0x0002,0x0002,0x0003,0x0003,0x5555,
0x5aaa,0x5aaa,0x56aa,0x56aa,0x56aa,0x36aa,0x55aa,0x5555,
0xa000,0xec00,0xe800,0xfb00,0xfb00,0xfe00,0xfec0,0x5555
};

const u16 PIC_TILES2[960] = {
0x0000,0x1555,0x1555,0x1555,0x1555,0x1555,0x1555,0x1555,
0x0000,0x5555,0x5555,0x5555,0x5555,0x5555,0x5555,0x5555,
0x0000,0x5555,0x5555,0x5555,0x5555,0x5555,0x5555,0x5555,
0x0000,0x5555,0x5555,0x5555,0x5556,0x5560,0x55c2,0x58ea,
0x0000,0x573c,0x5583,0x5ac0,0xc0fa,0x2aaa,0xaabf,0xb0ff,
0x0000,0x1b57,0x01ad,0x000e,0xf500,0xfdd4,0x0150,0x5000,
0x0000,0xb001,0xebf0,0xfefb,0xf0fe,0x0000,0xfcf0,0x000c,
0x0000,0x4308,0xf33c,0x033c,0xc33c,0xf030,0x00c3,0x0000,
0x0000,0x2555,0x25aa,0x2800,0x0000,0xc303,0x0000,0x0000,
0x0000,0x5555,0x5555,0x0255,0xeb25,0xc282,0x0028,0xab0a,
0x0000,0x5555,0x5555,0x5555,0x5555,0x5555,0x9555,0x2555,
0x0000,0x5555,0x5555,0x5555,0x5555,0x5555,0x5555,0x5555,
0x1555,0x1555,0x1555,0x1555,0x1555,0x1555,0x1555,0x1555,
0x5555,0x5555,0x5555,0x5555,0x5555,0x5555,0x5555,0x5555,
0x5555,0x5555,0x5557,0x555f,0x557d,0x55f7,0x5418,0x5060,
0x7ea0,0x3a00,0xfc0e,0xb02a,0x80ac,0x02c3,0x083a,0x33ab,
0x07f5,0xeb40,0xb001,0x000d,0x1421,0x91c2,0x4105,0x0418,
0x0000,0x1400,0x5000,0x9000,0x4000,0x0033,0x003f,0x0000,
0x1806,0x0802,0x0600,0x0100,0x0100,0x0000,0xfc60,0x0000,
0x0000,0x4000,0x8000,0x3000,0x2000,0x2c00,0x0300,0x0300,
0x0000,0x0000,0x0000,0x0000,0x0004,0x0003,0x0000,0x3004,
0x0960,0x2096,0x3635,0x0141,0x0050,0x0094,0xc025,0x3005,
0xbd55,0xdc55,0xbb15,0x59f5,0x557d,0x155e,0x0557,0x4d56,
0x5555,0x5555,0x5555,0x5555,0x5555,0x5555,0x5555,0x9555,
0x1555,0x1555,0x1555,0x1555,0x1555,0x1555,0x1555,0x1555,
0x5555,0x5555,0x5555,0x5556,0x5555,0x5555,0x5555,0x5556,
0x7d80,0xb600,0x9400,0x50c2,0x620a,0x882a,0x0caa,0x23a8,
0x1eb4,0x2a50,0xa910,0xa450,0xb140,0xc700,0x0d01,0x1c03,
0x1024,0x4050,0x0080,0x1040,0x5300,0xcc00,0xc404,0x0c00,
0x0000,0x1002,0x10af,0x00aa,0x0aab,0x2fff,0x3ffa,0xfe00,
0x0001,0x3fc0,0xfff8,0xffff,0xffff,0xffff,0x8aaf,0x0000,
0x0140,0x4050,0x0410,0x0015,0xf801,0xfff0,0xfff0,0xbffc,
0x0004,0x4007,0x0000,0x0000,0xd040,0x5f77,0x0524,0x0000,
0x3c01,0x0c00,0x0000,0xe000,0xf000,0xc000,0x0000,0x0000,
0x5955,0x5555,0x1555,0x0555,0x0155,0x0055,0x0025,0x00c9,
0x9555,0x5555,0x5555,0x5555,0x5555,0x5555,0x5555,0x5555,
0x1555,0x1555,0x1555,0x1555,0x1555,0x1555,0x1555,0x1555,
0x5554,0x5554,0x555f,0x555f,0x5554,0x555c,0x5553,0x5552,
0x42a4,0x8ab1,0x1a81,0x2a45,0x6b17,0xa81f,0xac7f,0xb07f,
0x700f,0xf008,0xc028,0xc42c,0xd0e5,0xd0a1,0x4191,0x0381,
0x0000,0x0402,0x3400,0x4c00,0xc340,0x0400,0x0400,0x3c40,
0x5025,0x8155,0x2555,0x5555,0x5580,0x5802,0xc03f,0x003f,
0x5540,0xaaa9,0xaa95,0x4000,0x0000,0xfffc,0xfff0,0xff0f,
0x03fc,0xf00c,0xff00,0x0000,0x0000,0x1000,0x2421,0x5820,
0x0000,0x2000,0x6000,0x3000,0x2000,0x2000,0x0000,0x0000,
0x0000,0x0100,0x0100,0x0040,0x00a0,0x0014,0x0004,0x0009,
0x00cd,0x0b01,0x0102,0x0140,0x0150,0x0050,0x0054,0x0056,
0x5555,0x5555,0x5555,0x5555,0x1555,0x1555,0x2555,0x3555,
0x1555,0x1555,0x1555,0x1555,0x1555,0x1555,0x1555,0x1555,
0x557e,0x554a,0x55ca,0x552a,0x562b,0x5428,0x54ac,0x58ac,
0xb06d,0x80ac,0x41a4,0x03b1,0x0291,0x0685,0x0ac7,0x0a4f,
0x02c0,0x4200,0x4304,0x4404,0x4404,0x0404,0x0000,0x0000,
0x1c40,0x1400,0x0c00,0x0400,0x0000,0x0000,0x0000,0x0000,
0x046a,0x046a,0x04aa,0x006a,0x000a,0x400a,0x4002,0x4032,
0xa83a,0xa000,0x8000,0x9000,0xb400,0xb500,0xb7ea,0xbfaa,
0xd910,0x0004,0x0000,0xa000,0x0000,0x1d10,0xad14,0xbd14,
0x2c00,0x1000,0x0500,0x0000,0x0000,0x0500,0x0501,0x0804,
0x0000,0x00c0,0x0000,0x4000,0x4000,0x0000,0x0000,0x0000,
0x0096,0x0096,0x0014,0x0820,0x0000,0x0002,0x8025,0xa015,
0x0555,0x0555,0x2555,0x2555,0x9555,0x5555,0x5555,0x5555,
0x1555,0x1555,0x1555,0x1555,0x1555,0x1555,0x1555,0x1555,
0x50bc,0x52b0,0x52f0,0x7ec3,0x7acf,0x4b0c,0xcb04,0xfb34,
0x1a1a,0x2a19,0x2929,0x6964,0x6864,0x64a5,0xa595,0xa595,
0x1000,0x0000,0x0000,0x0000,0x0000,0x0100,0x0140,0x0140,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0006,0x006b,0x02a9,0x02a7,0x01a9,0x40a4,0x40a8,0x4029,
0xafaa,0xaaaa,0xaaaa,0xaaaa,0xaaaa,0xafea,0x03fa,0x03ea,
0xab5c,0xbf5c,0xaffc,0xbffd,0xabfd,0xbfff,0x97ff,0x4fff,
0x0e00,0x0300,0x4000,0x0e00,0x4300,0x4000,0xcf04,0xc230,
0x0000,0x0030,0x0200,0x0100,0x0900,0x0900,0x0980,0xc183,
0xa015,0x5025,0x6005,0x5005,0x6005,0xa009,0x008d,0x3271,
0x5555,0x5555,0x5555,0x5555,0x5555,0x5555,0x5555,0x5555,
0x1555,0x1555,0x1556,0x1556,0x1557,0x1557,0x1557,0x1554,
0xec18,0xec10,0xe010,0xe090,0xf050,0xb053,0x8353,0x8253,
0xa194,0xa254,0x9654,0x9650,0x8651,0x8a51,0x8950,0x5950,
0x00c0,0x01c0,0x0140,0x0300,0x0300,0x0500,0x0d00,0x1c00,
0x0000,0x00f0,0x0070,0x0060,0x0270,0x0150,0x0d58,0x0555,
0xc00a,0xc002,0x8000,0x8000,0x0000,0x0000,0x0000,0x0004,
0xfff0,0xc055,0xf07f,0x3c00,0x0743,0x00ff,0x082a,0x000a,
0x1fff,0xffff,0xffff,0xffff,0xfffc,0xffc0,0xff00,0xb000,
0x4030,0x4100,0xc200,0x0104,0x0704,0x031c,0x0100,0x0f00,
0x0140,0x0003,0x0000,0x3000,0x0000,0x0000,0x0000,0x0000,
0x095e,0x2557,0x2555,0x0555,0x0955,0x0155,0x0255,0x0255,
0x5555,0x5555,0x9555,0x9555,0x5555,0x5555,0x5555,0x5555,
0x1558,0x1558,0x155b,0x1558,0x1557,0x1557,0x1554,0x1557,
0xc163,0xc163,0xc163,0xc163,0x0163,0x0163,0x0d63,0x2973,
0x5954,0x5954,0x6954,0x6554,0x2950,0x1a50,0x2550,0x6951,
0x1800,0x2400,0x6440,0x6044,0x6004,0xa000,0xa100,0x9013,
0x0555,0x2556,0x1554,0x1554,0x955a,0x5556,0x5550,0x5583,
0x1080,0x1070,0x9080,0x5203,0x6000,0xc00f,0x0e82,0xaa80,
0x0001,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0600,0x0500,0x0a00,0x0100,0x0100,0x0200,0x0000,0x0000,
0x0000,0x0000,0x1000,0x0000,0x2000,0x3800,0x3800,0x2c00,
0x0095,0x0095,0x0095,0x0015,0x0025,0x0002,0x0000,0x0000,
0x5555,0x5555,0x5555,0x5555,0x5555,0x9555,0x0255,0x0039,
0x1557,0x1556,0x1555,0x1555,0x1555,0x1555,0x1555,0x1555,
0x2943,0x254c,0x254c,0xb58c,0x858f,0x75cf,0x653f,0x6a33,
0x7d51,0x7d41,0xfd45,0xfd05,0xfd05,0xf415,0xf414,0xf414,
0xf002,0xcc31,0xc031,0xc001,0xf201,0x0101,0x0109,0x0909,
0x550a,0x542a,0x58ea,0x5caa,0x50aa,0x63aa,0x72aa,0x42aa,
0xff41,0xbf05,0xbd04,0xb804,0xac0c,0xa40c,0xb004,0x9000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0001,
0x0000,0x0000,0x0000,0x0000,0x0000,0x1544,0xffff,0xffaa,
0x0300,0x0100,0x0000,0x0100,0x0000,0x0040,0x5000,0xf444,
0x1c00,0x1c00,0x0c00,0x0d00,0x0700,0x0100,0x0140,0x5150,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x3c00,0x17f0,0x05ff,0x07ff,0x06aa,0x07aa,0x07aa,0x0faa,
0x1555,0x1555,0x1555,0x1555,0x1555,0x1555,0x1555,0x0000,
0x5732,0x543e,0x54ce,0x58ca,0x5fca,0x5b0a,0x5c3b,0x0000,
0xf014,0xd050,0xd050,0xc050,0x4140,0x0150,0x0142,0x0000,
0x0609,0x0605,0x2605,0x1505,0x9505,0x5505,0x5505,0x0000,
0x42aa,0x429a,0x4eaa,0x4aaa,0x8aaa,0x8aab,0x0aa8,0x0000,
0xc010,0xc010,0x8000,0x0080,0x00c0,0x02c0,0x0380,0x0000,
0x0001,0x0005,0x0006,0x0016,0x001a,0x001a,0x0c2a,0x0000,
0xbaaa,0xeaaa,0xaaaa,0xeaaa,0xaaaa,0xaaaa,0xaaaa,0x0000,
0xac95,0xacff,0xa9fb,0xa9fa,0xab5e,0xabce,0xabc6,0x0000,
0x6aa0,0xa9a0,0xa9ac,0xa9ac,0xa9a8,0xa9a8,0xa9a8,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x4000,0x0000,0x0000,
0x0faa,0x03ea,0x03ea,0x00ea,0x00fa,0x00ea,0x003a,0x0000
};

const u16 PIC_PALS1[64] = {
0x0000,0x0360,0x038b,0x037a,
0x0000,0x0360,0x047a,0x0358,
0x0000,0x0360,0x0125,0x0369,
0x0000,0x047a,0x0014,0x0147,
0x0000,0x0469,0x0124,0x0844,
0x0000,0x0789,0x0129,0x0012,
0x0000,0x0689,0x037a,0x0146,
0x0000,0x0468,0x0789,0x047c,
0x0000,0x0124,0x0369,0x0795,
0x0000,0x0125,0x012c,0x046b,
0x0000,0x0360,0x058b,0x038b,
0x0000,0x037b,0x0269,0x0148,
0x0000,0x0013,0x0017,0x022d,
0x0000,0x0119,0x022d,0x0259,
0x0000,0x022a,0x0350,0x0955,
0x0000,0x0232,0x022c,0x0795
};

const u16 PIC_PALS2[64] = {
0x0000,0x0fff,0x0cef,0x09bc,
0x0000,0x0fff,0x0ade,0x06ac,
0x0000,0x06ad,0x0bff,0x08cf,
0x0000,0x0fff,0x09bc,0x069c,
0x0000,0x06ad,0x08df,0x0c97,
0x0000,0x059d,0x0adf,0x07be,
0x0000,0x07ad,0x0c87,0x0ea9,
0x0000,0x0fff,0x09ab,0x0b87,
0x0000,0x07be,0x0a87,0x059c,
0x0000,0x0fdc,0x0999,0x09cf,
0x0000,0x0c98,0x0fec,0x09df,
0x0000,0x07ad,0x0adf,0x09bb,
0x0000,0x059d,0x08cf,0x09ab,
0x0000,0x0a87,0x07ad,0x0adf,
0x0000,0x068d,0x0000,0x0000,
0x0000,0x08ab,0x0000,0x0000
};

const u8 PIC_PALIDX1[120] = {
0x00,0x00,0x00,0x01,0x02,0x02,0x02,0x02,0x02,0x02,0x02,0x00,
0x00,0x00,0x03,0x03,0x03,0x04,0x04,0x04,0x03,0x05,0x06,0x00,
0x00,0x00,0x03,0x03,0x03,0x04,0x04,0x04,0x04,0x03,0x04,0x00,
0x00,0x06,0x06,0x03,0x04,0x04,0x04,0x04,0x04,0x03,0x04,0x06,
0x00,0x07,0x03,0x03,0x03,0x08,0x02,0x08,0x09,0x03,0x03,0x06,
0x00,0x03,0x03,0x03,0x03,0x02,0x03,0x07,0x03,0x03,0x03,0x00,
0x0a,0x03,0x00,0x03,0x03,0x03,0x03,0x03,0x03,0x03,0x03,0x00,
0x0a,0x03,0x0b,0x03,0x02,0x03,0x0c,0x09,0x0d,0x05,0x03,0x03,
0x00,0x06,0x0b,0x06,0x03,0x03,0x02,0x09,0x09,0x03,0x09,0x03,
0x00,0x01,0x02,0x02,0x02,0x0e,0x09,0x00,0x0a,0x02,0x0f,0x02
};

const u8 PIC_PALIDX2[120] = {
0x00,0x00,0x00,0x01,0x01,0x02,0x01,0x01,0x01,0x01,0x03,0x00,
0x00,0x00,0x03,0x01,0x02,0x04,0x04,0x05,0x05,0x01,0x00,0x00,
0x00,0x00,0x01,0x02,0x02,0x06,0x06,0x06,0x02,0x01,0x07,0x00,
0x00,0x00,0x02,0x02,0x08,0x09,0x0a,0x04,0x05,0x04,0x07,0x03,
0x00,0x01,0x02,0x02,0x05,0x0b,0x05,0x02,0x02,0x08,0x03,0x03,
0x00,0x01,0x04,0x05,0x00,0x0b,0x05,0x05,0x05,0x01,0x03,0x00,
0x01,0x01,0x04,0x05,0x00,0x05,0x02,0x02,0x05,0x03,0x03,0x00,
0x01,0x01,0x04,0x0c,0x01,0x01,0x05,0x00,0x0c,0x0d,0x03,0x01,
0x00,0x01,0x02,0x01,0x00,0x02,0x0e,0x05,0x02,0x02,0x00,0x05,
0x00,0x01,0x02,0x00,0x01,0x0d,0x0c,0x05,0x05,0x0c,0x0f,0x05
};

const SOD_IMG PIC_ID = {PIC_WIDTH,PIC_HEIGHT,(u16*)PIC_TILES1,(u16*)PIC_TILES2,PIC_NPALS1,(u16*)PIC_PALS1,PIC_NPALS2,(u16*)PIC_PALS2,(u8*)PIC_PALIDX1,(u8*)PIC_PALIDX2};
