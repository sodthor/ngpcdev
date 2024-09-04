#include "img.h"

#define GK1_WIDTH 12
#define GK1_HEIGHT 10
#define GK1_TILES 240

#define GK1_NPALS1 15
#define GK1_NPALS2 15

const u16 GK1_TILES1[960] = {
0x5555,0x6faa,0x6fab,0x7eab,0x7eaf,0x7eaf,0x7ebf,0x7abf,
0x5555,0xaeaf,0xbebf,0xbabe,0xfafa,0xeafa,0xebea,0xebaa,
0x5555,0xaaaf,0xaabe,0xabfb,0xafff,0xabff,0xaffc,0xbff0,
0x5555,0xa880,0xaa00,0xa800,0xa000,0x0200,0x0800,0x2000,
0x5555,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0002,
0x5555,0x00aa,0x02aa,0x0aaa,0x0aaa,0x2aaa,0xaaaa,0xaaaa,
0x5555,0xaaaa,0xaaaa,0xaaaa,0xaaaa,0xa080,0xa82a,0x8000,
0x5555,0xaaaa,0xaaaa,0xaaaa,0xaaaa,0x0aaa,0xaaaa,0x282a,
0x5555,0xa800,0xa800,0xaa00,0xaa00,0xaa00,0xaa80,0xaa80,
0x5555,0x028a,0x0082,0x0002,0x0800,0x0380,0x02e0,0x00f8,
0x5555,0x9f68,0x9fd8,0xa7da,0x29f6,0x027a,0x0026,0x0000,
0x5555,0x33bc,0xf0fc,0xc0ff,0xc03f,0x800f,0xf00f,0x3000,
0x7ffa,0x7fea,0x7fea,0x7fea,0x7feb,0x6feb,0x6feb,0x6fef,
0x65ef,0xe7bf,0xb7f9,0xbfed,0xffb5,0xbe95,0xba55,0xab55,
0x5500,0x5502,0x540a,0x5008,0x5018,0x4020,0x0080,0x0280,
0x6000,0x8000,0x0009,0x0014,0x0960,0x2400,0x0020,0x0902,
0x0001,0x4005,0xc017,0x0057,0x0dff,0x05df,0xd77d,0x555f,
0xffff,0xfffc,0xfffc,0xfcf0,0xffc0,0xff00,0xc000,0xfc00,
0x8000,0x0000,0x0000,0x0028,0x0028,0x0000,0x0000,0x0000,
0x3fff,0x00ff,0x003f,0x000f,0x0003,0x0003,0x0000,0x0000,
0xff70,0xff50,0xff54,0xff55,0xfd55,0xff55,0xf7f5,0xdfcc,
0x00e9,0x003b,0x001e,0x001e,0x4007,0x5100,0x5100,0x5040,
0x0004,0x0401,0x8600,0xc1c1,0xc0f1,0xf038,0x303e,0x002f,
0x0000,0x0000,0x0000,0x4000,0x8000,0xf000,0x7010,0xb810,
0x6767,0x4767,0x675f,0x6b5f,0x6b7f,0x6bff,0x41fd,0x61fd,
0xa954,0xad54,0xa550,0xa5d0,0xb7c0,0x9740,0x9500,0xd403,
0x0300,0x080a,0x000a,0x0020,0x3000,0xa00a,0xc099,0xc2ad,
0x6415,0x5055,0x0150,0x0000,0x5000,0x5a50,0xfafe,0x0003,
0x5555,0xaaaa,0x208a,0x0000,0x0000,0x0000,0x8000,0xf600,
0xfc00,0xfc00,0xc000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0002,0x0008,0x00a0,0x0000,0x0000,0x0000,0x0000,0x0007,
0x0000,0x0000,0x0000,0x0005,0x00fb,0x0ed0,0x7c00,0xc000,
0x1440,0x1440,0x0140,0xa410,0xfa50,0x0050,0x0004,0x0000,
0x5002,0x7d00,0x7302,0x2f42,0x2f82,0x2fc1,0x0be0,0x0bf0,
0x9c10,0x0000,0x0000,0xb500,0xba00,0x9d40,0xd941,0xda41,
0x62ff,0x62ff,0x60ff,0x68bf,0x68bf,0x683f,0x682f,0x6a2f,
0x5402,0x7406,0xf00b,0xd008,0xc018,0xc02c,0xc024,0xc000,
0x0158,0x0950,0x0660,0x2660,0x1940,0x1960,0xa665,0x9557,
0x0000,0x0000,0x0000,0x0000,0x0000,0x006a,0xbebf,0xf97b,
0x1ac0,0x0d9c,0x00d9,0x000d,0x0000,0x5570,0xa9a7,0x9b35,
0x0000,0x0000,0x4000,0xe400,0xbe00,0x0000,0x9000,0x2400,
0x0000,0x0000,0x0000,0x0001,0x0000,0x0000,0x0000,0x0000,
0x00f7,0x0f5c,0xd5c0,0x5700,0xf000,0x00fa,0x0da6,0x37bd,
0x0000,0x0000,0x0000,0x0000,0xd6aa,0xaa7f,0x6970,0x69f0,
0x0000,0x0005,0x0001,0x0101,0xfc05,0xeb45,0x1b05,0x1955,
0x02a4,0x03ac,0x01a8,0x00a9,0x40eb,0x406b,0x502a,0x703e,
0x9a41,0x6640,0x6640,0xa680,0x5640,0x5288,0x518c,0x519c,
0x62ab,0x62ab,0x782a,0x7822,0x7e22,0x7f82,0x7fa2,0x7fe2,
0xc060,0x00a0,0xc0a0,0x006c,0x035c,0x0350,0x0370,0x0370,
0x5541,0x56a8,0x5628,0xa829,0x2a8a,0x102a,0x0a02,0x822a,
0xe5ab,0xa022,0x2800,0x4a40,0x1559,0x4400,0x4100,0x1500,
0x3a02,0xe800,0x2400,0x0000,0x0000,0x0000,0x0055,0x0155,
0x9c00,0xfd00,0x3900,0x6500,0x0500,0x0400,0x5000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0xb28e,0x9b06,0x6e95,0x1946,0x0504,0x1550,0x0555,0x0555,
0xeb30,0x0000,0x000f,0xf030,0x0000,0x0000,0xc00f,0xffff,
0x3d55,0xf755,0x4955,0x0a55,0x0a55,0xa1a6,0xaaa5,0x6aaa,
0xb827,0xb803,0x9401,0x9402,0x9600,0x9800,0xb800,0xb8e0,
0x4158,0x8068,0x8074,0x8071,0x80b1,0x40e1,0x41d0,0x02c0,
0x6abf,0x6aaf,0x7aab,0x7aaa,0x7aaa,0x7eaa,0x7faa,0x7faa,
0x02a0,0x02b8,0x02ac,0x41ea,0x40aa,0x80ea,0xc06a,0xd02a,
0x0008,0x0000,0x0000,0x0000,0x4000,0xc000,0xf000,0xf400,
0x0a00,0x0080,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0aa8,0xaa80,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x00c0,0x0000,
0x0295,0x0000,0x2800,0x9800,0x9a00,0x5600,0x5600,0x9600,
0x5555,0x2540,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x802a,0x002a,0x0020,0x0000,0x0000,0x0000,0x0002,0x0002,
0xa4b0,0x90f0,0x90e1,0x80d0,0x80c0,0x4180,0x0248,0x0120,
0x0140,0x0140,0x02c0,0x0e00,0x0503,0x0b31,0x1802,0x240a,
0x7feb,0x7ffb,0x6ffe,0x7fff,0x7abf,0x7fff,0x7faf,0x6faf,
0xe02a,0xa01a,0xf40a,0xf80a,0xfd0a,0xfc0a,0xfd02,0xfe02,
0x7c00,0xbd00,0x2f00,0x4f40,0xc140,0xc200,0xd020,0xd000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x026a,0x07da,0x2d5a,0x2f60,0x2600,0x0000,0x00b6,
0x0000,0x0008,0xa00a,0xa8aa,0x2aa8,0x0000,0x0000,0x0003,
0x9400,0x9e00,0x9f00,0x7f00,0x2700,0x0200,0x0000,0x4000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0002,0x0002,0x0002,0x0003,0x0007,0x0009,0x0000,0x0002,
0x0050,0x0080,0x0243,0x0506,0x1418,0x1014,0xa550,0xf502,
0x6038,0x8010,0xc0e0,0x0080,0x0300,0x0200,0x4400,0xfa00,
0x7fff,0x7fff,0x7fff,0x7fff,0x4ebf,0x62a2,0x6aa8,0x6aaa,
0x5503,0x55c0,0x5540,0x9570,0x9d50,0x6554,0x2745,0x3701,
0xc000,0xc000,0x0000,0x0300,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x00c0,0x0000,0x0000,0x0000,0x0000,0x0000,0x0028,0x00a0,
0x0000,0x0000,0x0000,0x0000,0x2020,0x9a80,0x3fe0,0x0940,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0xffc0,0x03c0,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x000e,0x000a,0x0038,0x0019,0x0028,0x00ec,0x00a2,0x033c,
0xb405,0x901b,0x4061,0x02b8,0x1a22,0xa7a2,0x1688,0xa80a,
0x1000,0x4800,0x0200,0x8000,0x8200,0x8828,0x2000,0xa800,
0x7fcf,0x7fff,0x7ff3,0x7fff,0x7fcc,0x70ff,0x4033,0x40cc,
0x0803,0xaa03,0xaa90,0xaaac,0x2aa7,0xaaa1,0xaaaa,0x0aaa,
0x0000,0x4000,0xc000,0xc010,0xc020,0xb0b8,0xb9ff,0xbfbf,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x9500,0x416b,
0x0000,0x0000,0x0000,0x003f,0x000c,0x0000,0x0000,0x6a57,
0x0000,0x0000,0x0006,0x55af,0x7faa,0x06aa,0x006a,0x0001,
0x0000,0x0000,0x9019,0xfabe,0xafea,0xaaaa,0xaaaa,0x5555,
0x0050,0x0006,0x004e,0xffe9,0xff80,0xaa00,0xa900,0x0000,
0x0000,0x0000,0x0000,0x4000,0x0000,0x0000,0x0000,0x0003,
0x01bc,0x0ea6,0x06a5,0x380a,0x1aa8,0xe61d,0x5593,0x559f,
0x20aa,0x08aa,0x8aaa,0x82aa,0x8aaa,0x556a,0xcffd,0xff00,
0xaa00,0xa000,0xaa8a,0xaaaa,0xaaaa,0xaaaa,0x5aaa,0x3d6a,
0x4cc0,0x400c,0x4000,0x4000,0x4000,0x4000,0x4000,0x5555,
0xc0fc,0x000c,0x0000,0x0000,0x0000,0x0000,0x0000,0x5555,
0x7f5f,0x3fdd,0x1fdd,0x0fd5,0x07f5,0x07f5,0x03f5,0xaaaa,
0xc007,0xd001,0x7000,0x7400,0x5c00,0x5d00,0x5500,0xaaaa,
0xdfff,0x4001,0x0000,0x0000,0x0000,0x0000,0x0000,0xaaaa,
0xd000,0x5000,0x0000,0x0000,0x0000,0x0000,0x0000,0xaaaa,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x5555,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0xaaaa,
0x0007,0x000f,0x001d,0x0075,0x00f5,0x03f7,0x0775,0xaaaa,
0xfff5,0xfff5,0xfffd,0x7ff5,0x7ff5,0x5f75,0xfffd,0xaaaa,
0xffcc,0x3cff,0x30cf,0x0000,0xc000,0xc000,0xc000,0x5555,
0xaaaf,0xa80b,0x0002,0x0000,0x0000,0x0000,0x0000,0x5555
};

const u16 GK1_TILES2[960] = {
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0001,0x0006,
0x0000,0x011a,0x0065,0x0296,0x0a5b,0x646e,0x91b9,0x47e6,
0x0000,0x56fa,0x7f95,0xf9a9,0xd6d5,0x5b66,0x6959,0x9564,
0x0000,0xfb00,0xfc00,0xb000,0x9000,0xc000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0515,0x0140,0x1555,
0x0000,0x0000,0x0000,0x0000,0x0000,0x5000,0x0000,0x4140,
0x0000,0x02fd,0x039f,0x00bd,0x00ef,0x006f,0x002f,0x0019,
0x0000,0x5410,0x5514,0xfe5c,0xe3bf,0xa42b,0x680a,0xf902,
0x0000,0x0002,0x0003,0x0000,0x4000,0xe800,0xba40,0xafb7,
0x0000,0x4402,0x0501,0x2f00,0x3740,0x2fd0,0x0fd0,0xcbf6,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x005a,0x0064,0x01a0,0x0692,0x0a83,0x1a8f,0x6b2e,0xac3f,
0x0b95,0x2e55,0xb950,0xe541,0xd006,0x41a5,0x5a45,0xa064,
0xfff4,0x1f90,0x2b40,0xa900,0xe000,0xd000,0x0000,0x0000,
0x0000,0x0001,0x0001,0x0105,0x0015,0x0055,0x1555,0x0155,
0x1555,0x5555,0x5555,0x5541,0x5541,0x5555,0x5555,0x5555,
0x4000,0x5500,0x5540,0x5550,0x5554,0x5554,0x5555,0x5555,
0x000e,0x0006,0x0001,0x0000,0x0000,0x0000,0x0000,0x0011,
0xff00,0xff40,0x7f40,0x7fc0,0x15f0,0x04fd,0x047f,0x052a,
0x7b51,0x53d4,0x30e5,0x1834,0x1a04,0x0a41,0x4680,0xab80,
0xe6b5,0x7595,0xfd95,0x3bb5,0x1e75,0x0a5d,0x074b,0x034e,
0x0000,0x1000,0x0000,0x0000,0x0000,0x0000,0x1400,0x0400,
0x0001,0x0001,0x0005,0x0005,0x0017,0x001b,0x0059,0x016c,
0x64bd,0xa2f0,0xabd0,0xe786,0x4aa9,0x0a90,0x2d00,0x3800,
0x03c0,0x0f00,0x6c05,0xb555,0x0f5f,0x0007,0x0000,0x7aa4,
0x0000,0x0000,0x8a20,0xaaaa,0x56aa,0xd5a9,0x3ff5,0x00f7,
0x0155,0x0155,0x1555,0x5555,0x5555,0x5695,0xa995,0xaaaa,
0x5555,0x5555,0x5565,0x5556,0x5555,0x5555,0x556a,0xaaaa,
0xaaa8,0xaaa2,0xaa06,0xaaaa,0xa9a9,0x9555,0x5577,0x75f0,
0x5555,0x555d,0x7dd5,0x57f0,0xfd00,0xd00f,0x01aa,0x1aaa,
0x412e,0x411e,0x541e,0x014b,0x0007,0x690b,0xaaa2,0xea96,
0x06ac,0x009a,0x005c,0xc02c,0x4024,0x4038,0xb00b,0x5005,
0x01c6,0xab97,0xfaaa,0x00fa,0x0067,0x0025,0x002c,0x0038,
0x0400,0x0400,0x0500,0x0100,0x0100,0x0140,0x0140,0x0040,
0x0164,0x0160,0x0560,0x0593,0x3581,0x3d82,0x1642,0x16ce,
0x7401,0xb006,0xd006,0xc006,0x8016,0x4006,0x0000,0x0000,
0x6aa9,0xaefa,0xbabb,0xbfea,0xafa9,0x9500,0x0000,0x0000,
0xc037,0xa001,0xa900,0xaa50,0xa997,0x000f,0x0000,0x00c0,
0x6eaa,0xaaad,0x2aaa,0x036a,0x00e6,0x5d6a,0x0955,0x4155,
0x55a6,0x556a,0x5aa9,0xaaa8,0xaaa9,0xaaaa,0xaaaa,0xaa9a,
0x5500,0x5002,0x001f,0x00bf,0x0ae9,0x6900,0x5000,0x4000,
0x7ff6,0xff9a,0xfee9,0xfd55,0x0000,0x0000,0x0005,0x0005,
0xffa5,0xffa0,0xff94,0xb864,0x0190,0x0010,0x8050,0x8000,
0xf403,0xb803,0xac02,0x6e00,0x2f00,0x1f00,0x0b80,0x0b80,
0x0018,0x001b,0x002b,0x002b,0x002d,0x0c21,0x0c31,0x0401,
0x0800,0x0400,0x0280,0x0288,0x0044,0x0014,0x0004,0x000c,
0x1606,0xf606,0x360e,0xf902,0xf802,0xd80e,0xd805,0xd805,
0x0014,0x0002,0x00c3,0x0340,0xc030,0x8f40,0xb0fc,0x1cc0,
0x0000,0x05c8,0xc296,0x3036,0xc000,0x3356,0x3cfd,0xc0ff,
0x007c,0x0395,0x82a7,0xb97f,0x6555,0xaa9d,0x5700,0xfc00,
0x01aa,0x006a,0x406a,0x00aa,0xa0ea,0x92fa,0x0bfa,0x6ffe,
0x5555,0x5557,0x55d7,0x55f7,0x57ff,0x57f7,0x57df,0x57ff,
0x0430,0x00b0,0x0000,0x4010,0x5092,0x4005,0x9000,0x5000,
0x00c5,0x5dd9,0xd950,0x0d4d,0x5555,0x5557,0x3ff0,0x0000,
0x8000,0x0000,0x3000,0x9000,0x7000,0x0c00,0x0000,0x0000,
0x0180,0x01bc,0x02b4,0x03a4,0x00b7,0x03f6,0x03f5,0x020a,
0x3401,0x3701,0x3701,0x1704,0x1b08,0x2908,0x2409,0x6419,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x5805,0xd801,0xd803,0x1800,0x1b00,0x1500,0x1600,0x0640,
0xd551,0xf555,0xfd55,0xfe55,0x3faa,0x1fea,0x0ffa,0x03fe,
0xf0ff,0xff3f,0xffff,0xf7fd,0x5556,0xaaab,0xaaaa,0xaaaa,
0xf001,0x0036,0xffda,0xff5a,0x555a,0x5569,0x66aa,0xaaa9,
0x5556,0x555a,0xa55a,0xa55a,0xaa6a,0xaaab,0xba6b,0xbbbf,
0x57ff,0x57ff,0x57ff,0x5fff,0x5fff,0x7fff,0xff3f,0xffff,
0xfc00,0xfff5,0xc3d5,0x03d7,0x00d5,0x00f5,0x00eb,0x00aa,
0x0000,0xc03f,0x5fd5,0x5577,0x5d57,0xa5aa,0x6aaa,0xaaaa,
0x3fc0,0xfdc0,0xffcf,0xfd5f,0x555f,0xa55f,0xaa74,0xa9f4,
0x010a,0x0906,0x0904,0x2506,0x1e26,0x3c19,0xbc23,0xb48b,
0xec1f,0xec2d,0x6c1d,0x505d,0x90bc,0xb044,0x81fc,0xc250,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0940,0x0a40,0x0270,0x0360,0x0090,0x0390,0x00d8,0x00e4,
0x0155,0x0055,0x8095,0x1015,0x1827,0x24a7,0x0687,0x0667,
0x5555,0x5555,0xa995,0xaaaa,0xbeaa,0xffff,0xffff,0xffff,
0x5555,0x5555,0x5555,0x5aaa,0xaaaa,0xfffb,0xfffe,0xfffd,
0x57ff,0x5c00,0x7000,0x4000,0x400d,0x40ea,0x76a6,0x5500,
0xffff,0xfff3,0x0ff0,0x0300,0xc003,0x955a,0xaaaa,0xaaa4,
0x03bf,0x00aa,0x00ea,0x00ea,0xc0ea,0x5ce6,0xd695,0x3a95,
0x5555,0x5555,0x5555,0x5555,0xaa56,0xaaaa,0xbbff,0xffff,
0xebfc,0xeafc,0xaafc,0x95ac,0x5550,0x5560,0x556e,0x5558,
0x7a0f,0x5a2e,0x5834,0xa0d0,0x4182,0x4a81,0x0007,0x00bc,
0x05c0,0x2f40,0x3700,0x7d00,0xfc00,0x5800,0x2000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x3000,0x0c0c,0x0003,0x0000,
0x00f8,0x0015,0x0036,0x000e,0x000e,0x0001,0xc030,0xc0dc,
0x16fe,0x36ea,0x65aa,0xe8aa,0xb96a,0xba6a,0xbeaa,0xaaaa,
0x5555,0xa555,0x6555,0xa555,0xa555,0xa955,0xa555,0xa555,
0xaabd,0xaab5,0xaab5,0xaab5,0xaab5,0xaab5,0xaab5,0xaaf5,
0xbf3b,0xbfbe,0xbffe,0xfbfe,0xfffe,0xffbe,0xffc2,0xef09,
0x955b,0x6e96,0x5aa7,0xaaeb,0x8fcb,0x003b,0xc00a,0x6035,
0x6a95,0xaba5,0xfbe9,0xfff9,0xfff9,0xfffe,0x003e,0x9c3d,
0x5555,0x5555,0x5555,0x5555,0x5555,0x5555,0x5555,0x5555,
0xff50,0xfed0,0xfec0,0xfe80,0xfb41,0xaa01,0xb904,0xec00,
0x0250,0x0940,0x260c,0x5c00,0x80c0,0x0000,0x0000,0x0000,
0x8000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0030,0x0000,0x000c,0x0000,0x0033,0x0f00,0x3fcc,0x3f33,
0xf358,0x00d8,0x0007,0x0003,0xc000,0x000c,0x0000,0xf000,
0x6a9a,0x2a9a,0x2a9a,0x1a8a,0x3aca,0x0503,0x0000,0x0000,
0x9555,0x9555,0x9555,0x5555,0xa555,0x5a55,0x00d6,0x1c00,
0xaabd,0xaaaf,0xaaaf,0xaa80,0xaad1,0xaaab,0xb7fa,0x0000,
0xaabf,0x57f5,0xfd60,0x0000,0x0000,0xe000,0xf500,0x9fe8,
0xafee,0x9ba9,0x0b80,0x0000,0x0000,0x0000,0x0000,0x0000,
0x5606,0x5560,0xaa20,0x0000,0x0025,0x009f,0x00ff,0xa7ff,
0xafff,0x6afd,0x6af5,0x3af5,0x7bd7,0xffd5,0xf755,0xbd54,
0x5800,0x5000,0x6000,0x4000,0x8000,0x0000,0x000c,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x3000,0x00fe,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0xc000,
0x222a,0x2aa2,0x2eeb,0x2aea,0x3fff,0x3fff,0x3fff,0x0000,
0x2a03,0xbaa3,0xabbf,0xafbf,0xffff,0xffff,0xffff,0x0000,
0x0000,0xc000,0xc000,0xf000,0xf000,0xf000,0xfc00,0x0000,
0x1aa0,0x0aa8,0x0eaa,0x03ea,0x01da,0x00ff,0x007f,0x0000,
0x0000,0x3554,0xbf7f,0xaaae,0xaaaa,0xaaaa,0xaaaa,0x0000,
0x0eaa,0x0eaa,0xfaaa,0xaaaa,0xaaab,0xaaff,0xabd5,0x0000,
0xaaaa,0xaaaa,0xaaa9,0x5955,0x7fff,0xffff,0xffff,0x0000,
0xaaaa,0xaaab,0xaaaa,0xaabb,0xfbff,0xffff,0x5555,0x0000,
0xbdf0,0x95f0,0xd780,0xde00,0xf900,0x7c00,0xe000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0033,0xc300,0xcf30,0xffff,0x3fff,0x3fff,0x3fff,0x0000,
0x0000,0x03f0,0xfffc,0xffff,0xffff,0xffff,0xffff,0x0000
};

const u16 GK1_PALS1[60] = {
0x0000,0x0360,0x0123,0x0356,
0x0000,0x0360,0x0457,0x068b,
0x0000,0x0360,0x078b,0x0999,
0x0000,0x0360,0x078a,0x0234,
0x0000,0x0354,0x078a,0x0122,
0x0000,0x078a,0x0233,0x0457,
0x0000,0x068b,0x0789,0x088b,
0x0000,0x0788,0x0899,0x0667,
0x0000,0x0899,0x0567,0x0345,
0x0000,0x078a,0x0678,0x0456,
0x0000,0x078a,0x089a,0x0556,
0x0000,0x0665,0x0360,0x0433,
0x0000,0x0899,0x0667,0x0122,
0x0000,0x0988,0x0665,0x0333,
0x0000,0x0444,0x0111,0x0888
};

const u16 GK1_PALS2[60] = {
0x0000,0x089b,0x09ab,0x0aaa,
0x0000,0x09ac,0x0bcd,0x0abd,
0x0000,0x08ac,0x0abd,0x0eee,
0x0000,0x0aac,0x0ccd,0x0eef,
0x0000,0x09ac,0x089c,0x0abd,
0x0000,0x09ab,0x0bbc,0x0ccd,
0x0000,0x0ccc,0x0eee,0x0aab,
0x0000,0x0cbc,0x0edd,0x0fee,
0x0000,0x0bbb,0x0bbc,0x09ab,
0x0000,0x09ab,0x0bbc,0x099b,
0x0000,0x0ddc,0x0bbb,0x0aaa,
0x0000,0x0ccc,0x0bba,0x0eed,
0x0000,0x0bbb,0x0dcc,0x0edc,
0x0000,0x0ccb,0x0fec,0x0ddb,
0x0000,0x0eec,0x0fee,0x0bba
};

const u8 GK1_PALIDX1[120] = {
0x00,0x01,0x01,0x02,0x02,0x02,0x02,0x02,0x02,0x03,0x04,0x01,
0x03,0x05,0x06,0x07,0x06,0x06,0x02,0x06,0x06,0x05,0x08,0x09,
0x04,0x05,0x07,0x09,0x0a,0x06,0x0b,0x0a,0x05,0x09,0x0c,0x09,
0x03,0x05,0x07,0x0d,0x0e,0x08,0x0b,0x0e,0x0e,0x05,0x05,0x09,
0x03,0x0e,0x07,0x0c,0x0c,0x09,0x0b,0x09,0x0e,0x0a,0x07,0x08,
0x00,0x09,0x07,0x02,0x07,0x0b,0x02,0x07,0x07,0x07,0x0c,0x0e,
0x00,0x08,0x07,0x0b,0x0b,0x07,0x07,0x07,0x0b,0x07,0x0d,0x0e,
0x03,0x0e,0x02,0x0b,0x0b,0x07,0x07,0x02,0x0b,0x0e,0x0e,0x0e,
0x02,0x07,0x0d,0x0d,0x0e,0x08,0x08,0x09,0x0d,0x0e,0x0e,0x0e,
0x02,0x02,0x0b,0x0b,0x0b,0x0b,0x02,0x0b,0x0b,0x0b,0x02,0x03
};

const u8 GK1_PALIDX2[120] = {
0x00,0x00,0x01,0x02,0x03,0x01,0x00,0x00,0x01,0x01,0x01,0x04,
0x00,0x00,0x02,0x03,0x01,0x00,0x00,0x00,0x01,0x01,0x01,0x01,
0x00,0x03,0x03,0x01,0x04,0x00,0x00,0x04,0x01,0x05,0x06,0x01,
0x00,0x06,0x03,0x07,0x06,0x08,0x08,0x07,0x07,0x05,0x05,0x09,
0x04,0x06,0x09,0x06,0x06,0x05,0x08,0x05,0x06,0x09,0x0a,0x09,
0x00,0x06,0x05,0x09,0x08,0x0a,0x08,0x09,0x09,0x09,0x0b,0x06,
0x00,0x0a,0x0b,0x0c,0x0c,0x06,0x06,0x0a,0x0c,0x0a,0x0b,0x0b,
0x08,0x06,0x0d,0x0e,0x0d,0x0a,0x0a,0x0a,0x0e,0x0c,0x0a,0x00,
0x08,0x06,0x06,0x0e,0x0d,0x0b,0x07,0x0b,0x0d,0x0a,0x00,0x08,
0x00,0x00,0x00,0x0d,0x0d,0x0d,0x0e,0x0d,0x0d,0x00,0x00,0x00
};

#define GK1_ID {GK1_WIDTH,GK1_HEIGHT,GK1_TILES,(u16*)GK1_TILES1,(u16*)GK1_TILES2,GK1_NPALS1,(u16*)GK1_PALS1,GK1_NPALS2,(u16*)GK1_PALS2,(u8*)GK1_PALIDX1,(u8*)GK1_PALIDX2}

const SOD_IMG GK1_IMG = GK1_ID;

