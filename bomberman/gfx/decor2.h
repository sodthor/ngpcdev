#include "img.h"

#define DECOR2_WIDTH 32
#define DECOR2_HEIGHT 4
#define DECOR2_TILES 256

#define DECOR2_NPALS1 16
#define DECOR2_NPALS2 16

const u16 DECOR2_TILES1[1024] = {
0x4000,0x4000,0x4000,0x4000,0x4000,0x4000,0x4000,0x4000,
0x0001,0x0001,0x0001,0x0001,0x0001,0x0001,0x0001,0x0001,
0x0001,0x0006,0x1556,0x6a9e,0x6a93,0x6a6c,0x69aa,0x65ea,
0x4000,0x9000,0x9554,0xb6a9,0x06a9,0x09a9,0xaa69,0xaf69,
0x006f,0x07ea,0x1aaa,0x7aaa,0xaabf,0xe6ff,0xe6ff,0xe6b8,
0x5b00,0x6970,0x56dc,0x55b7,0x5549,0x556d,0x556d,0x2569,
0x0015,0x006b,0x08b9,0x1dbe,0xa2e6,0xbf95,0x7b94,0x6690,
0x5000,0xfe00,0x7f30,0xafe4,0x5be0,0x5550,0x16d4,0x0bf4,
0x0000,0x005a,0x00a5,0x08b9,0x1de5,0x2e96,0x2b90,0x1640,
0x0000,0xaa00,0x7e80,0xace0,0x9be0,0x5550,0x0590,0x06f8,
0x0000,0x0000,0x0065,0x01b9,0x1225,0x2796,0x1b50,0x0640,
0x0000,0x5400,0x7d00,0x5340,0x5550,0x4550,0x0550,0x01f4,
0x0000,0x0000,0x0162,0x0b36,0x09a8,0x0689,0x0a00,0x0100,
0x0000,0x1400,0xbe00,0x9f80,0x55e0,0x4150,0x0050,0x0178,
0x0000,0x0004,0x0068,0x08a8,0x0900,0x0902,0x0400,0x0400,
0x0000,0x2800,0xbe00,0x2840,0x0250,0x8020,0x0000,0x0030,
0x0015,0x0160,0x0600,0x1800,0x1000,0x6000,0x4000,0x4000,
0x5404,0xa550,0x1041,0x4104,0x4054,0x4005,0x1019,0x2569,
0x0000,0x0005,0x005a,0x0182,0x0603,0x0403,0x1003,0x1000,
0x0000,0xa000,0xea91,0x8200,0x0820,0x02a0,0x0028,0x80b8,
0x0000,0x0000,0x0005,0x005a,0x0101,0x040c,0x040c,0x180c,
0x0000,0x0000,0x8000,0xaa00,0x0820,0x2080,0x0a80,0x00a0,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x003f,0x03fa,0x0f97,0x0e03,0x3903,0x3406,0x3515,
0x0000,0x5450,0x7524,0x0410,0x0550,0x0d54,0x7554,0x5554,
0x0000,0x0001,0x0004,0x0050,0x0100,0x0400,0x0430,0x1030,
0x0000,0x0410,0x5144,0x4104,0x0004,0x0004,0xc004,0xc004,
0x0000,0x000d,0x000c,0x0030,0x000c,0x0030,0x03fc,0x0f00,
0x0000,0x5570,0x00f0,0x20f0,0x00f0,0x20f0,0x00f0,0x00f0,
0x0000,0x0005,0x0050,0x0100,0x0400,0x08a0,0x22a8,0x22b8,
0x0000,0x5000,0x0500,0x0040,0x0010,0x0a20,0x2a88,0x2e88,
0x4000,0x4000,0x4000,0x9555,0xbfff,0xbfff,0xbfff,0xaaaa,
0x0001,0x0001,0x0001,0x5556,0xfffe,0xfffe,0xfffe,0xaaaa,
0x66fe,0x66c0,0x66b0,0x69aa,0x6a6a,0x6a96,0x6aaa,0x5555,
0x406e,0x026e,0x0b6e,0x7f9e,0xf65e,0x555e,0xfff6,0xaaaa,
0x58a5,0x562a,0x556a,0x5555,0x9555,0x1555,0x0955,0x0295,
0xffad,0xaabd,0xab7d,0x55fd,0x57f6,0x55d4,0x5560,0x5600,
0x7cff,0x5f0f,0x55f0,0x5555,0x5555,0x1455,0x0157,0x001c,
0xffcd,0xfffd,0xff55,0x5555,0x5555,0x5504,0x5540,0xd400,
0x556f,0x16bf,0x1af0,0x255a,0x1515,0x1445,0x0857,0x0018,
0xebcc,0xfbfc,0xff98,0xa654,0x5544,0x5400,0x9500,0xe400,
0x0154,0x12be,0x12f0,0x554a,0x1505,0x0401,0x0005,0x0001,
0x1b3c,0xaff8,0xfe90,0xa554,0x5450,0x4010,0x9400,0x9000,
0x0154,0x006b,0x15e2,0x151f,0x0105,0x0100,0x0007,0x0001,
0x1bfc,0x95e8,0x9550,0x5450,0x5040,0x4000,0x9000,0x4000,
0x0100,0x01b9,0x06ff,0x059a,0x0555,0x0111,0x0006,0x0000,
0x02a8,0x05bc,0x5174,0xd510,0x5400,0x5000,0x4000,0x0000,
0x6000,0x6802,0x6aaa,0x1aaa,0x1aaa,0x06aa,0x016a,0x0015,
0xaaa9,0xaaa9,0xaaa9,0xaaa4,0xaaa4,0xaa90,0xa940,0x5400,
0x2c30,0x2f03,0x2fff,0x0bff,0x0bff,0x02ff,0x00af,0x000a,
0xb5a4,0xaaa4,0xaaa4,0xaa90,0xaa90,0xaa40,0xa500,0x5000,
0x2c02,0x2f0f,0x2fff,0x0bff,0x0bff,0x02ff,0x00af,0x000a,
0x0390,0x5690,0xaa90,0xaa40,0xaa40,0xa900,0x9400,0x4000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x3555,0x3955,0x3e56,0x0faa,0x0fea,0x03ff,0x003f,0x0000,
0x6afc,0xabfc,0xabfc,0xaff0,0xfff0,0xffc0,0xfc00,0x0000,
0x1030,0x1000,0x107f,0x101a,0x0405,0x0140,0x0015,0x0000,
0xc004,0x0004,0xf010,0xc010,0x0040,0x0500,0x5000,0x0000,
0x3800,0x30f0,0x3b0e,0x0c63,0x0ca3,0x030c,0x00f0,0x0000,
0x002c,0x0fac,0xb0ec,0xc63c,0xca30,0x30c0,0x0f00,0x0000,
0x32f0,0x3003,0x0f03,0x0300,0x0333,0x0333,0x03ff,0x0000,
0x0f8c,0xc00c,0xc0f0,0x00c0,0xccc0,0xccc0,0xffc0,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0003,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0xc3f0,0xfc03,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0xf3f0,0x003f,0x0000,0x0000,0x0000,0x0000,0x0000,
0x3fc3,0xf03f,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x30c0,0xfccc,0x03f0,0x000c,0x0003,0x0003,0x0003,0x0000,
0x03c0,0x3f00,0x3c00,0xf000,0xc000,0x0000,0x0000,0x0000,
0x00f0,0x00fc,0x00ff,0x003f,0x0000,0x0000,0x0000,0x0000,
0x0000,0x030c,0xcfff,0xff0f,0x0f00,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0c0c,0x30c3,0xfffc,0x0000,0x0000,0x0000,
0x0000,0xc030,0x0300,0xffff,0xc00f,0x0000,0x0000,0x0000,
0x0000,0x3000,0xc300,0xfc00,0x3f00,0x0300,0x0300,0x0300,
0x03c0,0x30c0,0x03c0,0x0f00,0xfc00,0xf000,0x0000,0x0000,
0x03c0,0x03cc,0x00c3,0x00f0,0x000f,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x030c,0xcfff,0xff03,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0c00,0x30c3,0xffff,0x000f,0x0000,
0x0000,0x0000,0x0030,0xc300,0x0fff,0xfc00,0xc000,0x0000,
0x0000,0x0000,0x0000,0x3000,0xf000,0xfc00,0x0300,0x0300,
0x0c00,0x0c00,0x3c00,0x3000,0x3000,0x0000,0x3000,0x3000,
0x0003,0x000c,0x003c,0x0030,0x0030,0x000c,0x000c,0x0000,
0x00fc,0x3300,0x0c00,0xfc00,0x0c00,0x3000,0xf000,0x3000,
0x0ff0,0x0030,0x003c,0x000c,0x000c,0x000f,0x000f,0x003c,
0x0300,0x0cc0,0x00c0,0x03c0,0x00c0,0x0cc0,0x03c0,0x00c0,
0x0300,0x00c0,0x00c0,0x00cc,0x0000,0x0000,0x00c0,0x03c0,
0x0000,0x0000,0x0000,0x0000,0x0cff,0x03c0,0x33c0,0x0f00,
0x0000,0x0000,0x0000,0x0000,0xcc00,0xf000,0x3c00,0x0fc0,
0x00fc,0x003c,0x0030,0x00f0,0x0030,0x0330,0x00f0,0x0030,
0x3c00,0x0c00,0x0f00,0x0f00,0x0f00,0x0c00,0x0f00,0x0f00,
0x0000,0x0000,0x0000,0x0000,0x000f,0x0030,0x03f0,0x00f0,
0x0000,0x0000,0x0000,0x0000,0xc000,0xf000,0x3000,0x3c00,
0xaaaa,0x8000,0x8000,0x8000,0x8000,0x8000,0x800a,0x800a,
0xaaaa,0x0002,0x0002,0x0002,0x0002,0x0002,0xa002,0xa002,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0xc000,0xc000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0003,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0xffc0,0xf03f,
0x0000,0x0000,0x0000,0x0000,0x0000,0x03f0,0x3c3c,0x0003,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0fff,0xf0c0,0x0000,
0x0000,0x0000,0x0003,0x0003,0x0003,0xc03f,0xfff0,0x3c00,
0x0000,0x0000,0x0000,0x0000,0xf000,0x3c00,0x0f00,0xc300,
0x0000,0x0000,0x0000,0x0000,0x0003,0x000f,0x003c,0x00f3,
0x0000,0x0000,0x0000,0x003f,0xffff,0xc3f0,0x0c03,0x0000,
0x0000,0x0000,0x0000,0xc003,0xf0fc,0x0000,0x00c0,0x0000,
0x0000,0x0000,0x0003,0xcfff,0xfcc0,0x0c0c,0x0000,0x0000,
0x0f00,0x3c00,0xf300,0xc000,0xc000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0xf000,0x3f00,0x0300,0x00c0,0x30c0,
0x0000,0x0000,0x0000,0x0000,0x003f,0x00fc,0x03c0,0x030c,
0x0000,0x0000,0x0000,0xffff,0xc0f0,0x0c03,0x0000,0x0000,
0x0000,0x0003,0xffff,0xf3f0,0x0000,0x0000,0x0000,0x0000,
0x0000,0x03c3,0xffff,0xfcc0,0x0c0c,0x0000,0x0000,0x0000,
0x0f00,0xfc00,0xc000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x3000,0x0000,0xc000,0xc000,0xc000,0x3000,0x3000,0xf000,
0x0003,0x0003,0x0003,0x000c,0x000c,0x000c,0x000f,0x000f,
0xf000,0x3000,0x3000,0xc000,0xc000,0xc000,0xf000,0x3000,
0x0030,0x0030,0x0030,0x003c,0x0030,0x0030,0x000c,0x000c,
0x0f00,0x3f00,0x0c00,0x0c00,0x3fc0,0x0fc0,0x0300,0x0f00,
0x03cc,0x03c0,0x03f0,0x00f0,0x00f0,0x00cc,0x00c0,0x00f0,
0x03c0,0x03c0,0x3300,0x0300,0x0f00,0x0300,0x0300,0x33c0,
0x0f00,0x0330,0x0300,0x03c0,0x0300,0x03f0,0x00c0,0x03c0,
0x00f0,0x03c0,0x00c0,0x00c0,0x03f0,0x00f0,0x0030,0x00f0,
0x0330,0x0300,0x03c0,0x03c0,0x0300,0x0330,0x0300,0x03c0,
0x00c0,0x00c0,0x0cc0,0x00c0,0x03c0,0x00f0,0x0030,0x033c,
0x3c00,0x0cc0,0x0c00,0x3f00,0x3c00,0x0fc0,0x0f00,0x0f00,
0x800a,0x800a,0x8000,0x8000,0x8000,0x8000,0x8000,0xaaaa,
0xa002,0xa002,0x0002,0x0002,0x0002,0x0002,0x0002,0xaaaa
};

const u16 DECOR2_TILES2[1024] = {
0x1555,0x1555,0x1555,0x1555,0x1555,0x1555,0x1555,0x1555,
0x5554,0x5554,0x5554,0x5554,0x5554,0x5554,0x5554,0x5554,
0x6558,0x9990,0x4000,0x0000,0x0004,0x0003,0x0000,0x0000,
0x2aaa,0x0aaa,0x0002,0x0000,0xe000,0xf000,0x0000,0x0000,
0x6500,0x9000,0x4000,0x0000,0x0000,0x0000,0x0000,0x0001,
0x0059,0x0006,0x0001,0x0000,0x0010,0x0000,0x0000,0x8000,
0x6540,0x9900,0x6200,0x8000,0x0c00,0x0000,0x0002,0x0009,
0x0559,0x0096,0x00c9,0x0002,0x0009,0x0005,0x4002,0x9001,
0x6559,0x9900,0x6600,0x9100,0x4000,0x4000,0x4006,0x8019,
0x6559,0x0096,0x0029,0x0306,0x0009,0x0005,0x6006,0x9001,
0x6559,0x9996,0x6600,0x9800,0x44c0,0x4000,0x4006,0x9019,
0x6559,0x0196,0x0069,0x0c16,0x0009,0x1005,0x6006,0x9801,
0x6559,0x9996,0x6408,0x90c0,0x6001,0x5010,0x6066,0x9859,
0xaaaa,0x82aa,0x00aa,0x002a,0x000a,0x280a,0xaa0a,0xa802,
0xaaaa,0xaaa2,0xaa02,0xa202,0xa0aa,0xa0a8,0xa2aa,0xa2aa,
0x6559,0x8196,0x0069,0x8316,0x6409,0x1985,0x6566,0x9a49,
0x5540,0x540a,0x5076,0x41fe,0x49f6,0x09fa,0x2a5a,0x2aaa,
0x0152,0x000a,0x4528,0x1ca1,0x3f01,0x1d50,0x4540,0x4000,
0x5555,0x5550,0x5500,0x5428,0x50b8,0x52fc,0x4af8,0x4aaa,
0x5559,0x056a,0x0008,0x14a9,0x7285,0xfc05,0x7541,0x1501,
0x5555,0x5555,0x5550,0x5500,0x54a8,0x52b2,0x52f3,0x42e2,
0x5565,0x55a9,0x15a9,0x00a5,0x5285,0xca15,0xf015,0xd505,
0x6559,0x9996,0x6669,0x9996,0x6659,0x5995,0x6566,0x9a59,
0xbaae,0xeeeb,0xbbbe,0xeeeb,0xbbae,0xaeea,0xbabb,0xefae,
0x5555,0x6a80,0x6800,0x6000,0x60b8,0x40e8,0x42a0,0x4080,
0x5555,0x0209,0x00c1,0xb389,0xe009,0xa001,0x0001,0x0001,
0x5555,0x6aa8,0x6aa1,0x6a05,0x687d,0x61f5,0x634d,0x454d,
0x5555,0xa289,0x0411,0x1451,0x5551,0xd551,0x3551,0x3551,
0x5555,0x6aa0,0x6aa2,0x6a85,0x6aa3,0x6a85,0x6803,0x60be,
0x5555,0x0009,0xeb09,0x4b09,0xfb09,0x4b09,0xfb09,0xab09,
0x5555,0x6aa0,0x6a0b,0x68bf,0x62ff,0x620b,0x4803,0x4803,
0x5555,0x0aa9,0xe0a9,0xfe29,0xff89,0xe089,0xc021,0xc021,
0x1555,0x1555,0x1aaa,0x0000,0x0000,0x0000,0x0000,0x0000,
0x5554,0x5554,0xaaa4,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x001f,0x0007,0x0000,0x0000,0x0000,0x0000,0x0000,
0x1d00,0xfc00,0xf000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0200,0x0080,0x0000,0x0000,0x0000,0x8000,0xb000,0xec00,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0002,0x000b,0x00ae,
0x0300,0x00f0,0x000f,0x0000,0x0000,0x4100,0x6400,0x9a43,
0x0030,0x0000,0x0000,0x0000,0x0000,0x0091,0x0026,0x0259,
0x0000,0x8000,0x400f,0x8000,0x4040,0x4110,0x6100,0x9a43,
0x0031,0x0002,0x0001,0x0002,0x0011,0x0195,0x0066,0x0259,
0x6401,0x8800,0x440f,0x0010,0x4050,0x5194,0x6560,0x9a58,
0x40c1,0x0002,0x0009,0x0002,0x0209,0x1985,0x0166,0x0a59,
0x6401,0x9900,0x400c,0x8080,0x6450,0x5895,0x6560,0x9a58,
0x8002,0x0002,0x000a,0x020a,0x0a2a,0x2aaa,0x0aaa,0x2aaa,
0xa8aa,0xa800,0xa000,0xa000,0xa000,0xa888,0xaaa0,0xaaaa,
0xa802,0xa002,0x0802,0x008a,0x02aa,0x0aaa,0x2aaa,0xaaaa,
0x0fff,0x03fc,0x0000,0x8000,0x4000,0x5000,0x6400,0x9a40,
0x0000,0x0000,0x0000,0x0002,0x0001,0x0005,0x0026,0x0259,
0x43cf,0x80fc,0x4000,0x9000,0x6000,0x5800,0x6500,0x9a50,
0x0002,0x0003,0x0002,0x000b,0x000e,0x002a,0x00bb,0x0fae,
0x43fc,0x80f0,0x4000,0x9000,0x6000,0x5800,0x6500,0x9a50,
0x5409,0x0006,0x0009,0x0016,0x0019,0x0095,0x0166,0x1a59,
0x6559,0x9996,0x6669,0x9996,0x6659,0x5995,0x6566,0x9a59,
0x6559,0x9996,0x6669,0x9996,0x6659,0x5995,0x6566,0x9a59,
0x4000,0x4000,0x4000,0x6000,0x6000,0x6800,0x6a80,0x5555,
0x0001,0x0001,0x0001,0x0009,0x0009,0x0029,0x02a9,0x5555,
0x454d,0x45d5,0x4500,0x4540,0x6150,0x6815,0x6a80,0x5555,
0x1ab2,0xaab2,0x0ac6,0x3ac6,0xeb16,0xb056,0x0556,0xaaaa,
0x42ea,0x4e0a,0x4050,0x6104,0x6104,0x6852,0x6a0a,0x5555,
0xafc1,0xf001,0x0501,0x1041,0x1049,0x8529,0xa0a9,0x5555,
0x480a,0x4bfc,0x60b8,0x68ff,0x68cc,0x6888,0x6800,0x5555,
0xa021,0x3fe1,0x2e09,0xff29,0x3329,0x2229,0x0029,0x5555,
0x557f,0x57ff,0x5fea,0x7eaa,0xfaaa,0xfaaa,0xeaaa,0xabaa,
0xff54,0xfd55,0xabd5,0xaaa5,0xaaaf,0xaeab,0xabab,0xaaeb,
0x1405,0x02a8,0xaaaa,0xaaea,0xebeb,0xffef,0xffff,0xffff,
0x5555,0x0405,0xaa80,0xaaaa,0xffea,0xffef,0xffff,0xffff,
0x4014,0x0a80,0xaaaa,0xaaae,0xfebe,0xfeff,0xffff,0xffff,
0x4515,0x0111,0xa805,0xaaa1,0xffe8,0xfff8,0xfff8,0xfffa,
0x542b,0x40ab,0x42aa,0x0aaa,0x2aaf,0xaaab,0xaaaf,0xfebe,
0xfa05,0xfa01,0xaa00,0xaa80,0xfaaa,0xfaaa,0xfeaa,0xfeab,
0x5555,0x5451,0x1000,0x00a0,0xa0aa,0xaaaa,0xaaaa,0xfaff,
0x5555,0x5555,0x5151,0x4514,0x0002,0xaaaa,0xaaaf,0xabff,
0x5555,0x1545,0x5455,0x0000,0x2aa0,0xaaaa,0xaaaa,0xebfe,
0x5555,0x4555,0x1455,0x0155,0x8055,0xe855,0xf855,0xf855,
0x542f,0x452f,0x542f,0x50af,0x02be,0x0afa,0xfaeb,0xffef,
0xf815,0xf811,0xfe14,0xfe05,0xbfa0,0xafea,0xffea,0xffff,
0x5555,0x5555,0x5555,0x5451,0x1000,0x00a8,0xaaba,0xfbfe,
0x5555,0x5555,0x5555,0x5155,0x4514,0x0000,0xaaa0,0xaffa,
0x5555,0x5555,0x5545,0x1455,0x5000,0x02aa,0x2abf,0xbfff,
0x5555,0x5555,0x5555,0x4555,0x0555,0x0155,0xe855,0xf855,
0x52bf,0x52bf,0x42af,0x4aff,0x4aff,0x5aff,0x4aff,0x4aff,
0xffa8,0xffa1,0xfa81,0xfe85,0xff85,0xffa1,0xffa1,0xffe9,
0x5502,0x44aa,0x52bf,0x02ff,0x52ff,0x4aff,0x0aff,0x4aff,
0xa005,0xaa85,0xfe81,0xffa1,0xffa1,0xaaa0,0xffa0,0xff81,
0x54af,0x512f,0x552b,0x542b,0x552b,0x512a,0x542a,0x552a,
0xa855,0xea15,0xfa15,0xfa11,0xea55,0xaa55,0xaa15,0xa815,
0x5555,0x5555,0x5555,0x5555,0x5100,0x542a,0x442f,0x50bf,
0x6559,0x9996,0x6669,0x9996,0x2259,0x0995,0xc166,0xf019,
0x5502,0x5542,0x554b,0x550b,0x554b,0x544b,0x550a,0x554a,
0x8155,0xe155,0xe055,0xe055,0xe055,0xe155,0xe055,0xa055,
0x5555,0x5555,0x5555,0x5555,0x5550,0x554a,0x540b,0x550f,
0x6559,0x9996,0x6669,0x9996,0x2659,0x0995,0xc566,0xc259,
0x0000,0x2aaa,0x2aaa,0x2955,0x29aa,0x29aa,0x29a0,0x29a0,
0x0000,0xaaa8,0xaaa8,0x5568,0xaa68,0xaa68,0x0a68,0x0a68,
0xabaa,0xebaa,0xfaaa,0xfeaa,0x7eaa,0x5fea,0x17ff,0x155f,
0xaaeb,0xaaeb,0xabab,0xaaaf,0xaab5,0xaa95,0xead5,0xab54,
0xffff,0xffff,0xffbf,0xfeaf,0xaaab,0xaaaa,0x002a,0x0540,
0xffff,0xffff,0xffef,0xffaf,0xeaaa,0xa80a,0x8142,0x5554,
0xffff,0xfeff,0xfeff,0xeabe,0xaaaa,0xa000,0x0515,0x5555,
0xfbfa,0xfbfa,0xfbf8,0xfbe8,0xaaa8,0x2a80,0x0005,0x4155,
0xfabe,0xaabe,0xaaaf,0xaaab,0x0aaa,0x42aa,0x50ab,0x14ab,
0x6956,0x6995,0xa997,0xa65f,0x557c,0x55f0,0x9fc3,0x9f0c,
0xebfe,0xaaaa,0xaaaa,0xaa80,0x0000,0x1405,0x5154,0x5555,
0xabfe,0xaafa,0xaaaa,0x2aa8,0x0501,0x5555,0x5515,0x5555,
0xefff,0xeffa,0xaaa8,0x2000,0x0115,0x5151,0x5555,0x5555,
0xf059,0xc196,0x0469,0x1996,0x2659,0x5995,0x6566,0x9a59,
0xffbf,0xffbf,0xbbef,0x0abf,0x40af,0x54aa,0x552f,0x452f,
0xffbf,0xffbf,0xffba,0xfeaa,0xfa80,0xfa01,0xf815,0xf851,
0xffff,0xeafe,0xaaaa,0x0000,0x1505,0x5154,0x5555,0x5555,
0xbffe,0xaaa8,0x0000,0x0405,0x5555,0x5555,0x5555,0x5555,
0xfffe,0xa828,0x0000,0x0115,0x5151,0x5555,0x5555,0x5555,
0xf059,0x0196,0x2669,0x9996,0x6659,0x5995,0x6566,0x9a59,
0x4aff,0x6abf,0x2aaf,0x2bff,0x2aff,0x4abf,0x4abf,0x0aff,
0xffe8,0xffa8,0xfea8,0xfaa1,0xfea1,0xffa1,0xffa0,0xffa0,
0x0abf,0x4bff,0x4aff,0x2abf,0x2aaf,0x2aff,0x0aff,0x4aff,
0xfe85,0xff85,0xff85,0xfe81,0xea85,0xfe85,0xfea1,0xffa1,
0x50ab,0x40ab,0x52ab,0x52ab,0x402a,0x502a,0x54ab,0x50ab,
0xa811,0xe815,0xe805,0xea05,0xea05,0xaa11,0xaa15,0xea05,
0x542a,0x542b,0x44ab,0x54ab,0x50ab,0x54aa,0x54aa,0x442b,
0xe055,0xe845,0xf855,0xf815,0xf855,0xf805,0xaa15,0xf815,
0x550a,0x542b,0x552f,0x552b,0x540b,0x550a,0x554b,0x550b,
0xe845,0xf855,0xf815,0xf815,0xe855,0xe845,0xe855,0xf815,
0x552f,0x552f,0x512f,0x552b,0x542b,0x550b,0x554b,0x5442,
0x8155,0xe115,0xe155,0xc055,0xc155,0xe015,0xe055,0xe055,
0x29a0,0x29a0,0x29aa,0x29aa,0x2955,0x2aaa,0x2aaa,0x0000,
0x0594,0x0594,0x5594,0x5594,0xaa94,0x5554,0x5554,0x0000
};

const u16 DECOR2_PALS1[64] = {
0x0000,0x017b,0x0323,0x0733,
0x0000,0x0113,0x002a,0x0666,
0x0000,0x0122,0x003c,0x0776,
0x0000,0x0875,0x0343,0x0222,
0x0000,0x0332,0x0555,0x0777,
0x0000,0x0018,0x001a,0x004d,
0x0000,0x001a,0x0018,0x004e,
0x0000,0x0017,0x002c,0x0222,
0x0000,0x0323,0x0733,0x0323,
0x0000,0x0343,0x0222,0x003f,
0x0000,0x0343,0x0222,0x003f,
0x0000,0x018b,0x0046,0x0168,
0x0000,0x018b,0x0046,0x0168,
0x0000,0x002b,0x0223,0x005e,
0x0000,0x0222,0x0555,0x0333,
0x0000,0x0018,0x004d,0x001a
};

const u16 DECOR2_PALS2[64] = {
0x0000,0x03ef,0x0998,0x0787,
0x0000,0x0998,0x0887,0x0ccc,
0x0000,0x0987,0x0787,0x009f,
0x0000,0x0998,0x0787,0x0966,
0x0000,0x0987,0x0966,0x0edc,
0x0000,0x0987,0x058d,0x0fcc,
0x0000,0x00cf,0x0cb8,0x0fff,
0x0000,0x03cf,0x0fff,0x0aff,
0x0000,0x0998,0x0787,0x0000,
0x0000,0x0cc9,0x00df,0x008f,
0x0000,0x0887,0x02bf,0x0eff,
0x0000,0x0887,0x02bf,0x0aff,
0x0000,0x0887,0x028f,0x03df,
0x0000,0x0887,0x028f,0x03df,
0x0000,0x03df,0x0aff,0x028f,
0x0000,0x0966,0x0b88,0x0000
};

const u8 DECOR2_PALIDX1[128] = {
0x00,0x00,0x01,0x02,0x03,0x04,0x05,0x05,0x05,0x05,0x05,0x05,0x06,0x05,0x07,0x06,0x08,0x08,0x08,0x00,0x08,0x00,0x00,0x00,0x03,0x02,0x07,0x07,0x07,0x07,0x09,0x0a,
0x0b,0x0c,0x01,0x0d,0x0e,0x0e,0x05,0x05,0x05,0x05,0x05,0x05,0x0f,0x05,0x05,0x0f,0x08,0x08,0x00,0x08,0x00,0x08,0x00,0x00,0x03,0x03,0x07,0x07,0x03,0x03,0x03,0x03,
0x00,0x06,0x06,0x06,0x06,0x06,0x06,0x06,0x06,0x06,0x06,0x06,0x06,0x06,0x06,0x06,0x06,0x06,0x06,0x06,0x06,0x06,0x06,0x06,0x06,0x06,0x06,0x06,0x06,0x06,0x08,0x08,
0x06,0x06,0x06,0x06,0x06,0x06,0x06,0x06,0x06,0x06,0x06,0x06,0x06,0x06,0x06,0x06,0x06,0x06,0x06,0x06,0x06,0x06,0x06,0x06,0x06,0x06,0x06,0x06,0x06,0x06,0x08,0x08
};

const u8 DECOR2_PALIDX2[128] = {
0x00,0x00,0x01,0x01,0x01,0x01,0x02,0x02,0x03,0x02,0x02,0x02,0x02,0x01,0x01,0x02,0x04,0x05,0x04,0x05,0x04,0x05,0x01,0x00,0x06,0x06,0x06,0x06,0x06,0x06,0x06,0x06,
0x07,0x07,0x01,0x01,0x00,0x00,0x02,0x02,0x02,0x02,0x02,0x02,0x02,0x01,0x01,0x01,0x03,0x03,0x03,0x00,0x03,0x02,0x08,0x08,0x06,0x06,0x06,0x09,0x06,0x06,0x06,0x06,
0x07,0x07,0x0a,0x0a,0x0a,0x0a,0x0b,0x0b,0x0b,0x0b,0x0b,0x0c,0x0d,0x0d,0x0d,0x0c,0x0d,0x0c,0x0a,0x0a,0x0a,0x0b,0x0b,0x0b,0x0d,0x02,0x0d,0x0d,0x0d,0x02,0x04,0x04,
0x07,0x07,0x0a,0x0a,0x0a,0x0b,0x0b,0x0e,0x0b,0x0b,0x0c,0x02,0x0c,0x0d,0x0c,0x0c,0x0d,0x02,0x0a,0x0a,0x0a,0x0a,0x0b,0x0b,0x0b,0x0c,0x0d,0x0c,0x0c,0x0d,0x04,0x0f
};

#define DECOR2_ID {DECOR2_WIDTH,DECOR2_HEIGHT,DECOR2_TILES,(u16*)DECOR2_TILES1,(u16*)DECOR2_TILES2,DECOR2_NPALS1,(u16*)DECOR2_PALS1,DECOR2_NPALS2,(u16*)DECOR2_PALS2,(u8*)DECOR2_PALIDX1,(u8*)DECOR2_PALIDX2}

const SOD_IMG DECOR2_IMG = DECOR2_ID;
