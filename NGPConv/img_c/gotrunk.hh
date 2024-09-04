#include "img.h"

#define GOTRUNK_WIDTH 18
#define GOTRUNK_HEIGHT 12
#define GOTRUNK_TILES 432

#define GOTRUNK_NPALS1 15
#define GOTRUNK_NPALS2 15

const u16 GOTRUNK_TILES1[1728] = {
0x5555,0x5555,0x5555,0x5555,0x5555,0x5555,0x5555,0x5555,
0x5555,0x5555,0x5555,0x5555,0x5555,0x5555,0x5555,0x5555,
0x5555,0x5555,0x5555,0x5555,0x5555,0x5555,0x5555,0x5555,
0x5555,0x5555,0x5555,0x5555,0x5555,0x5555,0x5555,0x5555,
0x5555,0x5555,0x5555,0x5555,0x5555,0x5555,0x5555,0x5555,
0x5555,0x5555,0x5555,0x0155,0x0000,0x5000,0x5000,0x5400,
0x5555,0x5500,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x5400,0x5000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x5555,0x5555,0x5555,0x5555,0x5555,0x5555,0x5555,0x5555,
0x5555,0x5555,0x5555,0x5555,0x5555,0x5554,0x5554,0x5550,
0x5555,0x5555,0x5555,0x5555,0x5554,0x5454,0x5050,0x4000,
0x5555,0x5555,0x5554,0x5540,0x0000,0x0000,0x0000,0x0000,
0x5005,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x4000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0001,0x4005,0x0156,
0xc000,0x0000,0x0000,0xc000,0x3fff,0xff00,0xaa80,0xaaaa,
0x0000,0x0000,0x0000,0x0000,0xf000,0xf000,0xb000,0xb000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0014,0x0000,0x0015,0x0001,0x0010,0x0001,
0x0000,0x0000,0x0141,0x1469,0xafff,0x56bf,0x146f,0x001b,
0x0000,0x0050,0x0054,0x4414,0xf905,0xff45,0xff45,0xffe5,
0x0000,0x0000,0x0000,0x0000,0x0000,0x4000,0x4000,0x4000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x5555,0x5555,0x5555,0x5450,0x0000,0x0000,0x0000,0x0000,
0x5500,0x5000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0001,0x0001,
0x0000,0x0001,0x0055,0x0454,0x0558,0x052a,0x551a,0x594a,
0x1688,0x5a00,0x8000,0x0aab,0xa8af,0xaabe,0x82aa,0x8a28,
0x5695,0x5a9f,0x5bff,0xff0f,0xff3f,0xffff,0xc0fc,0x03f0,
0x9000,0x9004,0x4000,0x5000,0x5000,0x1000,0x1401,0x1400,
0x0000,0x0000,0x0001,0x007a,0x07aa,0x5aaa,0x5555,0x0000,
0x001f,0x07ea,0xfaaa,0x8aaa,0xaaaa,0xaaaa,0xeaaa,0x1eaa,
0xa946,0xfe96,0xffea,0xffff,0xffff,0xffcf,0xffff,0xffff,
0xaab5,0xa2a5,0xa2ad,0xa2ad,0xaaad,0xaaab,0xaaab,0xaa2b,
0x4000,0x4000,0x4000,0x4000,0x4000,0x4000,0x5000,0x5400,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x4400,0x4410,0x0011,0x0006,0x000b,0x001b,0x001b,0x001b,
0x1101,0x1540,0xaa90,0xffe0,0xffe4,0xffe4,0xfea9,0xaaaa,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x5000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0001,0x0001,0x0001,0x0001,0x0001,0x0000,0x0001,0x0005,
0xa54e,0xa56a,0x8baa,0x8bae,0x6afa,0x5efe,0x6fbf,0x423f,
0x0540,0x1000,0x1540,0x5450,0x5515,0x5555,0x5040,0x5550,
0x0440,0x0005,0x0014,0x0141,0x4001,0x0015,0x0055,0x0500,
0x9600,0xa800,0x0000,0x0000,0xa600,0xad00,0x2d00,0xaf00,
0x0000,0x0000,0x000a,0x002f,0x0bfa,0x1f91,0x6815,0x5016,
0x01aa,0x5fe2,0xaaaa,0xaaaa,0x7faa,0x5eaa,0x6aaa,0xaaaa,
0x5555,0x5555,0x4555,0x5555,0x5545,0x5556,0x555b,0x556f,
0xaa8a,0xa28a,0xaaaa,0x882a,0xe8aa,0xeaaa,0xeaaa,0xeaaa,
0xfbd5,0xaaaf,0x2aaa,0xaaaa,0xaaaa,0xaaaa,0xaaaa,0xaaaa,
0x0000,0x4000,0xd000,0xb400,0xad00,0xab40,0xaa40,0xaad0,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0005,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x5400,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x011a,0x001a,0x001a,0x005a,0x005e,0x001f,0x0007,0x0043,
0x5555,0x5555,0x5556,0x556a,0x57aa,0x5eaa,0xdeaa,0xfeab,
0x9400,0xa554,0xeaa4,0xaaa9,0xaaaa,0xaaaa,0xffaa,0xfeaa,
0x0000,0x0000,0x0000,0x5001,0xaaaa,0xaaaa,0xaaaa,0xaaaa,
0x0154,0x0150,0x1550,0x6a45,0xaa54,0xaa54,0xaa40,0xaa41,
0x0004,0x0101,0x1501,0x0001,0x0014,0x0114,0x0015,0x5054,
0x5504,0x5410,0x5401,0x5540,0x5500,0x1554,0x0555,0x0555,
0x0001,0x0155,0x5555,0x0000,0x0000,0x0540,0x5545,0x5551,
0xeff0,0xaff0,0x0bf4,0xafb4,0xafb4,0x0ab4,0x8af4,0xcaf0,
0x005b,0x016f,0x452f,0x057f,0x04bf,0x01ff,0x02fa,0x02e0,
0xa8aa,0xaaaa,0xaa8a,0xaaaa,0xab7a,0xd41a,0x0006,0x0005,
0x5abf,0x5ffe,0x5f3e,0x6ffa,0xb0ff,0x83ff,0xf3ff,0xefff,
0x9555,0x5551,0x5555,0x9555,0x9555,0xe5a5,0xe6f9,0xebf9,
0xaaaa,0xaaaa,0xaaaa,0xaaaa,0xaaaa,0xaaaa,0xaaaa,0xaaaa,
0xaab1,0xaab5,0x8aaf,0xaaaa,0xaaaa,0xaaaa,0x2aaa,0xaaaa,
0x557f,0xfeaa,0xaaaa,0xaaaa,0xaaab,0xaaaf,0xaaaa,0xaaaa,
0xff55,0xaaad,0xabd5,0xd140,0x4000,0x5400,0xad00,0xab50,
0x0000,0x5000,0x4000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0005,0x0001,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0xafbf,0x5fff,0x1fff,0x1fff,0x0bff,0x0bff,0x06ff,0x01bf,
0xfaaa,0xaaaa,0xaaaa,0xaaaa,0xaaaa,0xeabe,0xeabe,0xfabf,
0xaaaa,0xaaaa,0xaaa9,0xaaa5,0xaa95,0xaab5,0xaab7,0xaabf,
0xaa01,0xaa41,0xaa41,0xaa41,0xaa55,0xaa55,0xaaa5,0xaaa9,
0xb080,0xfac0,0xeaca,0xeae8,0xfbe8,0xfea8,0xfea2,0xfeea,
0x3fff,0xd555,0x1555,0xd555,0xd555,0x1555,0xd355,0x3ffd,
0x72af,0x73a7,0x6f95,0x6f95,0x5f95,0x5f9b,0x6a6b,0x6a68,
0xb4f4,0xfdd0,0xed40,0x8d00,0x8400,0xa400,0xb000,0x3000,
0x0fc0,0x0300,0x0000,0x0030,0x3f3f,0x3f0f,0x300a,0xf02a,
0x0003,0x0003,0x0000,0x0000,0xc00c,0xf00f,0xb003,0xa000,
0xaaa5,0x0055,0x0015,0x0055,0x0015,0xa015,0xf015,0x3015,
0xeba9,0xffb9,0xf3a9,0xffe9,0xfff9,0xfce9,0xfbe9,0xfef9,
0xa800,0xa800,0xa800,0xa800,0xa800,0xa800,0xa800,0xa800,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0xaaaa,0x000a,0xaaaa,0xaaaa,0xaaaa,0xaaaa,0x8aaa,0xabf5,
0xaab4,0xaaa9,0xaaaa,0xaaaa,0xaaaa,0xaaaa,0xaaaa,0x5557,
0x0000,0x0000,0xc000,0x9000,0xb400,0xad04,0xab04,0xeb40,
0x0000,0x0000,0x5500,0x5500,0x5554,0x0155,0x0005,0x0001,
0x002f,0x001b,0x0006,0x0004,0x0000,0x1000,0x5400,0x5540,
0x5555,0x5555,0x9555,0xa555,0x3a55,0x3fea,0xc3ff,0xff3f,
0xffff,0xffff,0xffff,0xffff,0xffff,0xffff,0x51bf,0xaaaf,
0xaaaa,0xaaaa,0xeaaa,0xfaaa,0xffaa,0xffaa,0xffaa,0xffaa,
0x6fff,0x6fc3,0x5bff,0x963f,0xa5af,0xaab0,0xa555,0x9595,
0xffc3,0xf3c0,0x03f3,0xc0ff,0x00ff,0x00ff,0xb003,0xac0f,
0xe56a,0xe9aa,0xe955,0x5569,0x55e9,0x57d5,0x57d6,0x555b,
0xb000,0xb003,0xb00f,0xb03f,0xb03e,0xb3fc,0xcff0,0xff80,
0xf559,0x1569,0x556a,0x51aa,0x016b,0x016b,0x015f,0x1573,
0xa0f0,0xa0f0,0xa0f0,0xa0f0,0xb0c0,0xb000,0xc000,0x0000,
0x0c05,0x0c15,0x0005,0x3015,0x3005,0x08d5,0x0eee,0x00fa,
0xfffe,0xfffe,0xffff,0xf0ff,0xf0ff,0xc0ff,0xc0ff,0x00ff,
0x5555,0x5555,0x9555,0xe555,0xfe95,0xfb39,0xfb0f,0xfaf0,
0xaaaa,0xa2aa,0xaaaa,0xaaaa,0xafea,0xad57,0xe900,0x7900,
0xc001,0xb411,0xaf44,0xaad4,0xaa90,0xaaa4,0x006b,0x001f,
0x1410,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x8000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x1500,0x5550,0x5555,0x0145,
0x1550,0x0140,0x0000,0x0000,0x0000,0x0000,0x4000,0x5540,
0xfff5,0x03f5,0x00f5,0x00fa,0x0000,0x0000,0x0000,0x0000,
0x6a9c,0x5500,0x5c00,0xf000,0x0000,0x0003,0x0000,0x0000,
0x6fea,0x1bea,0x06eb,0x00ea,0x002a,0x412b,0x55ab,0x055b,
0xabfa,0xafff,0xaffc,0xbfff,0x0c00,0xfc00,0xbf00,0xafff,
0xabff,0xfebf,0x03ff,0xfffc,0x0000,0x0003,0x03ff,0xfff0,
0x556c,0xaabf,0x547f,0x0156,0x1aa4,0x6a40,0x5400,0x0055,
0x3900,0xf400,0x4001,0x0001,0x0016,0x016a,0x5556,0x56a4,
0x56bf,0x56fc,0x5b00,0x5b00,0x6f00,0xa003,0xf00e,0x003a,
0x0000,0x0401,0x4456,0x11aa,0x1aaa,0x6aff,0xabff,0xabff,
0x0555,0x6aaa,0xaaaa,0xaaaa,0xaaaa,0xfeaa,0xffaa,0xffea,
0x0055,0x55aa,0xaaaa,0xaaaa,0xaaaa,0xaaaa,0xaaaa,0xaaaa,
0x5a40,0x5a54,0xffe4,0xffe9,0xffea,0xbfaf,0xaafe,0xfffe,
0x1500,0x0000,0x0000,0x4000,0x9000,0xad00,0xbf40,0xbff4,
0x0002,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x8000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x5555,0x0555,0x0005,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x5400,0x5550,0x0155,0x0001,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x5550,0x5055,0x0000,0x0000,0x0000,
0x0005,0x0000,0x0000,0x0015,0x5555,0x1550,0x0000,0x0000,
0xaa55,0x0a95,0x002a,0x0000,0xffc0,0x003f,0x0000,0x0000,
0xaaaa,0xaaaa,0xf3ff,0x000f,0x3fff,0xffff,0x0000,0x0000,
0x2aaa,0xaaaa,0xaabf,0x5550,0x5500,0x0000,0x007f,0x01ff,
0xaa90,0xa900,0x8000,0x0000,0x1410,0x1040,0xffff,0xffff,
0x0016,0x0556,0x055a,0x151a,0x042a,0x057a,0xfffa,0xffff,
0x5aaa,0x5aaa,0x6aaa,0x6aaa,0x6aaa,0x6aab,0xaaaf,0xaaaf,
0xafd5,0xafd5,0xafd5,0xfff5,0xfff5,0xfff5,0xfff5,0xfff5,
0xefff,0xefff,0xafff,0xafff,0xbfff,0xbfff,0xbffe,0xbffa,
0x555a,0x556b,0x556f,0x55bf,0x56bf,0x6aff,0xafff,0xbfff,
0xeaa8,0xeaa8,0xffaa,0xfeaa,0xfaaa,0xfaea,0xfffa,0xfff8,
0x0000,0x0000,0x0000,0x0000,0xf000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x01ff,0x07ff,0x57ff,0x1fff,0x1fd4,0x07d0,0x0106,0x002a,
0x5555,0x5555,0x5aaa,0xbf0f,0x3f00,0xf003,0xc003,0xff32,
0xfffe,0xfd7a,0xf57a,0x7ffa,0x6aba,0xaaba,0xaaaa,0xaeaa,
0xabff,0xafff,0xafff,0xafaf,0xaabf,0xaaff,0xabff,0xafff,
0xffe5,0xffd5,0xff95,0xfe55,0xffaa,0xffff,0xfaa9,0xe555,
0xaaaa,0xaaaa,0xaaa4,0xaa54,0x9400,0x8000,0x4000,0x4000,
0xeaaa,0x5eab,0x0555,0x0000,0x0000,0x0000,0x0000,0x0000,
0xffe4,0xe400,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x003f,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0xc000,0x0000,0x0000,0x0000,0x0030,0x0c00,0x0c01,0x3c05,
0x0000,0x0000,0x0000,0x000a,0x002a,0x0aaa,0x2aaa,0xaaaa,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0003,0x0003,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0005,0x0006,0x001a,
0x05bf,0x06ff,0x07ff,0x1bff,0x6fff,0xafff,0xbfff,0xffff,
0x5555,0xfff5,0xfffe,0xfffe,0xffff,0xffff,0xffea,0xfaaa,
0xaaaf,0xeaaa,0x7eaa,0x5fff,0x5555,0x5555,0x5555,0x5555,
0xffff,0xffff,0xffff,0xfffa,0xffaa,0xaaaa,0xaaaa,0xaa50,
0xaaa9,0xaaa9,0xaaa5,0xaa94,0xaa50,0x9540,0x5000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0002,0xaaaa,0xaaaa,
0x0000,0x0000,0x002a,0x00aa,0xaaaa,0xaaaa,0xaa80,0xa000,
0x0aaa,0xa82a,0xaaaa,0xaa80,0xa800,0x0000,0x0000,0x0000,
0xaa80,0xa800,0x8000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0003,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0xc000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0xc000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0c00,0xf000,
0x001a,0x001a,0x017a,0x01fa,0x01fa,0x07fa,0x1ffa,0x7ffa,
0xffff,0xffff,0xfabf,0xfaaa,0xfeaa,0xffaa,0xffff,0xffff,
0xffff,0xffff,0xfffe,0xffea,0xaaa9,0xaaa4,0xff94,0xff50,
0xffaa,0xeaff,0xbfff,0xbfff,0x57ff,0x07fe,0x01fa,0x01fa,
0xaa40,0xaa80,0xaa80,0xaa80,0xaa90,0xbfe4,0xfff4,0xfff4,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0c00,0x0000,0x0355,
0x0000,0x0000,0x0000,0x0015,0x0155,0x5555,0x5555,0x5555,
0x0005,0x0105,0x5541,0x5554,0x5550,0x5500,0x5400,0x4000,
0xaaaa,0xaaa0,0xaa00,0xa000,0x0000,0x0000,0x0000,0x0000,
0x8000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x00f0,
0x0000,0x0000,0x0000,0x0000,0x0003,0x00fc,0x0000,0x0f00
};

const u16 GOTRUNK_TILES2[1728] = {
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x5400,0x5555,0x0555,0x055a,0x016a,
0x0000,0x0055,0x5555,0x5555,0x5555,0x5555,0xaaaa,0xaaaa,
0x0155,0x0555,0x5555,0x55aa,0x5aaa,0xaaaa,0xaaaa,0xaaaa,
0xaaaa,0xaaaa,0xaaaa,0xaaaa,0xaaaa,0xaaaa,0xaaaa,0xaaaa,
0xaaaa,0xaaaa,0xaaaa,0xaaaa,0xaaaa,0xaaaa,0xaaaa,0xaaaa,
0xaaaa,0xaaaa,0xaaaa,0xaaaa,0xaaaa,0xaaaa,0xaaaa,0xaaaa,
0xaaaa,0xaaaa,0xaaaa,0xaaaa,0xaaaa,0xaaaa,0xaaaa,0xaaaa,
0x5555,0x5555,0x5555,0x5555,0x5555,0x5555,0x5555,0x5555,
0x5555,0x5555,0x5555,0x5555,0x5555,0x5555,0x5555,0x5555,
0x5555,0x5555,0x5555,0x5555,0x5555,0x5555,0x5555,0x5555,
0x5555,0x5555,0x5555,0x5555,0x556a,0x56aa,0xaaaa,0xaaaa,
0x5555,0xa555,0xaaaa,0xaaaa,0xaaaa,0xaaaa,0xaaaa,0xaaaa,
0x5555,0x5555,0x5569,0xa555,0xaa95,0xaaa9,0xaaaa,0xaaaa,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0001,0x0001,0x0005,
0x0000,0x0000,0x0000,0x0000,0x0001,0x0101,0x0505,0x1555,
0x0000,0x0000,0x0001,0x0015,0x5555,0x5555,0x555a,0x555a,
0x0550,0x5555,0x5555,0x5555,0x5555,0x9556,0xaaaa,0x5695,
0x155a,0x555a,0x55aa,0xaaaa,0xaaaa,0xaaaa,0xaaaa,0xaaaa,
0x5555,0x5555,0x5555,0x5555,0x5555,0x5554,0x1550,0x5400,
0x1555,0x5555,0x5555,0x1555,0x4000,0x00aa,0x002a,0x0000,
0x5555,0x5555,0x5555,0x5555,0x0555,0x0555,0x0555,0x0555,
0x5555,0x5555,0x5555,0x5555,0x5555,0x5555,0x5555,0x556a,
0xaaaa,0xaaaa,0xaa82,0xaaaa,0xaa80,0xaaa8,0xaa8a,0xffa8,
0x5555,0x5555,0x5414,0x4100,0x0000,0x0000,0x4100,0x5540,
0x5555,0x5505,0x5501,0x1141,0x0050,0x0010,0x0010,0x0000,
0x5556,0x5556,0x555a,0x556a,0x5aaa,0x1aaa,0x1aaa,0x1aaa,
0xaaaa,0xaaaa,0xaaaa,0xaaaa,0xaaaa,0xaaaa,0xaaaa,0xaaaa,
0xaaaa,0xaaaa,0xaaaa,0xaaaa,0xaaaa,0xaaaa,0xaaaa,0xaaaa,
0xaaaa,0xaaaa,0xaaaa,0xaaaa,0xaaaa,0xaaaa,0xaaaa,0xaaaa,
0xaaaa,0xaaaa,0xaaaa,0xaaaa,0xaaaa,0xaaaa,0xaaaa,0xaaaa,
0x0000,0x0000,0x0000,0x0105,0x5555,0x5555,0x5555,0x5555,
0x0055,0x0555,0x5555,0x5555,0x5555,0x5555,0x556a,0x56aa,
0x5555,0x5555,0x5555,0x5555,0x556a,0x5aaa,0xaaaa,0xaaaa,
0x5555,0x5555,0x5555,0x5555,0x5555,0x5555,0x5555,0x5555,
0xaaaa,0xaaaa,0xaaaa,0xaaaa,0xaaaa,0xaaaa,0xaaa8,0xaaa8,
0x5555,0x5554,0x5500,0x5203,0x5003,0x5080,0x0080,0x0030,
0x4022,0x00aa,0x2aaa,0xa000,0x0300,0x0000,0x3c00,0x30c3,
0x0000,0x0000,0x0000,0x0050,0x0040,0x0000,0x3f01,0xfc0f,
0x0555,0x0551,0x1556,0x0556,0x055b,0xc55b,0xc168,0xc1aa,
0x555b,0x555a,0xaabc,0xef00,0xb000,0x0000,0x0000,0x6fff,
0x5580,0x6000,0x0000,0x0000,0x0000,0x0000,0x0000,0x8000,
0x0010,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x1aaa,0x1aaa,0x1aaa,0x1aaa,0x1aaa,0x1aaa,0x0aaa,0x01aa,
0xaaaa,0xaaaa,0xaaaa,0xaaaa,0xaaaa,0xaaaa,0xaaaa,0xaaaa,
0xaaaa,0xaaaa,0xaaaa,0xaaaa,0xaaaa,0xaaaa,0xaaaa,0xaaaa,
0x5555,0x5555,0x5555,0x5555,0x5555,0x5555,0x5555,0x5555,
0x5555,0x5555,0x5555,0x5555,0x5555,0x5555,0x5555,0x5555,
0x2195,0x2145,0x5644,0x5a50,0x5950,0x5940,0x6a40,0x5a40,
0x4454,0x4015,0x0005,0x0005,0x0001,0x0001,0x0000,0x0000,
0x5555,0x5555,0x5555,0x5555,0x5555,0x5555,0x5555,0x0555,
0x5555,0x5555,0x5555,0x5555,0x5555,0x5555,0x5555,0x5555,
0x5554,0x5554,0x5554,0x5554,0x5554,0x5555,0x5554,0x5550,
0x0010,0x0000,0x1000,0x1000,0x0000,0x0000,0x0000,0x1840,
0x501a,0x4566,0x4016,0x0105,0x0040,0x0000,0x0516,0x0005,
0x6219,0x6a90,0xa742,0xf828,0x2d68,0x6940,0xbe00,0xa0a6,
0x005a,0x0395,0xfea5,0xffa5,0x00a9,0x006a,0xc0a5,0x00d5,
0x6a99,0x66aa,0x6550,0x5a40,0xa000,0x8008,0x0140,0x0540,
0xfc00,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x6aaa,0x2a9a,0x06aa,0x01aa,0x00aa,0x002a,0x0026,0x0009,
0xffff,0xffff,0xffff,0xffff,0xffff,0xffff,0xffff,0xff50,
0x5555,0x5555,0x5555,0x5555,0x5555,0x5555,0x5555,0x0155,
0xaaaa,0xaaaa,0xaaaa,0xaaaa,0xaaaa,0xaaaa,0xaaaa,0xaaaa,
0x5440,0x5540,0x5540,0x5500,0x5500,0x5540,0x5550,0x5514,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0155,0x0002,0x0002,0x0000,0x0000,0x0000,0x0000,0x0000,
0x5555,0x5555,0x5555,0x0554,0x0000,0x0000,0x0000,0x0000,
0x5403,0x540f,0x400f,0x0030,0x0003,0x0003,0x003f,0x003c,
0x6991,0x9454,0x4054,0x5a54,0xa941,0xa441,0x5540,0x0501,
0x0061,0x0146,0x0154,0x0016,0x0056,0x4001,0x5000,0x5000,
0xaa94,0x9400,0x0000,0x9569,0x55a9,0x5029,0x0010,0x0004,
0x0009,0x0009,0xf001,0x0001,0x0001,0xf001,0x3001,0x3009,
0x5900,0xa400,0x2040,0x6000,0x9100,0x9400,0xa400,0x940a,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0180,0x5590,0x5a50,
0x0000,0x0000,0x0080,0x0000,0x0a00,0x2c00,0x0c00,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0004,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0c35,0x3557,0x0375,0x00f5,0x0007,
0xaaaa,0x0aaa,0x2aaa,0xaaaa,0xaaaa,0xaaaa,0xaaaa,0xaaaa,
0x5550,0x5554,0x9555,0x9555,0xaa55,0xeaa5,0xffaa,0xfffa,
0x0000,0x0000,0x4000,0x4000,0x5000,0x5000,0x5000,0x5400,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0094,0x001c,0x003c,0x003c,0x0000,0x0000,0x0000,0x0000,
0x0f3f,0x003f,0x0030,0x0003,0x0003,0x0003,0x000c,0x0000,
0xc000,0x0000,0x8000,0x0000,0x0000,0x8000,0x0800,0x8000,
0x0800,0x0800,0x0000,0x0000,0x0000,0x0000,0x0000,0x0003,
0x0201,0x000d,0x0035,0x20d5,0x2355,0x0355,0x0d55,0x8555,
0x503f,0x54d5,0x5575,0x57cd,0xc080,0xc0a0,0xcaa0,0x0a80,
0x755c,0x555c,0x557e,0x5d7e,0x1d72,0x0d70,0x057c,0x0ff5,
0x0000,0xef00,0xff80,0xfe00,0xff80,0x0f80,0x0f80,0x4f80,
0x0000,0x0000,0x0800,0x0000,0x0000,0x0200,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0001,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x5555,0x5555,0x1555,0x0555,0x01aa,0x00a2,0x00a2,0x0029,
0x5fff,0x6aff,0x00ab,0x00ab,0x0002,0x6800,0x5aa0,0x5568,
0x5540,0x9540,0xa550,0x6691,0x55a6,0x455a,0x0155,0x0015,
0x0000,0x0000,0x0000,0x0000,0x4000,0x4000,0x2800,0x0080,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0400,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0028,0x0000,0x0080,0x0000,0x000a,0x0000,0x0000,
0x0028,0x082a,0xa808,0x2a00,0xaa00,0xaa00,0x0aa8,0x02a0,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0555,0x0554,0x0550,0x0540,0x0540,0x0402,0x100a,0x002a,
0x0000,0x4000,0x0000,0x0800,0xa800,0xe800,0xa800,0x8004,
0x0f0d,0x0f0d,0x0f05,0x0f05,0x0f35,0x0ff5,0x3fd7,0xffd7,
0x53e0,0x52c0,0x5be0,0x4ec0,0x4ba0,0x5300,0x5000,0x5500,
0x0000,0x0000,0x0000,0x0a00,0x0f00,0x2f00,0x3f00,0xbe00,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0040,0x00a0,0x000a,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x00fe,0x00be,
0x3ffc,0x03cc,0x0033,0x0003,0x000f,0x0002,0xaa00,0x6540,
0xc3cf,0xfffa,0xaaaa,0xaaaa,0xaa55,0x5555,0x9555,0x1555,
0x5555,0x5555,0x55aa,0xfeaa,0xfeaa,0xffaa,0xffaa,0xffaa,
0xaa9a,0x6a6a,0x96aa,0x6a6a,0x40aa,0x000a,0x0000,0xa410,
0x4006,0xa41a,0xaaaa,0xaaaa,0xaaaa,0xaaaa,0x1aaa,0x002a,
0x0000,0x5400,0x6a00,0xa900,0x56aa,0xaaaa,0x6aaa,0xa6aa,
0x0001,0x00ad,0x03ff,0x0d5f,0x5555,0x5554,0x5555,0x5555,
0x0000,0x4000,0xa000,0xaa00,0x5980,0x1480,0x0000,0x5000,
0x0000,0x0000,0x0002,0x0000,0xa2aa,0x02aa,0x00aa,0x0000,
0x0000,0x0000,0xa800,0x0002,0xaaaa,0xaaa8,0xa800,0x000a,
0x0001,0x0000,0x0300,0xfc00,0x8003,0x003e,0x03fa,0xaa00,
0x40ff,0x03fe,0x3ffc,0xbffc,0xff80,0xf800,0x0000,0x0002,
0x0000,0x0003,0x00ff,0x00ff,0x00ff,0x0ffc,0x0ff0,0xffc0,
0xdff5,0xf37c,0x3300,0xcc00,0xc000,0x0000,0x0000,0x0000,
0x7000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0xfe00,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0015,0x0001,0x0003,0x0000,0x0000,0x0000,0x0000,0x0000,
0x805b,0xba77,0x75f7,0x1d77,0x0577,0x00f5,0x001e,0x0001,
0x5ff8,0x5ffe,0x7fff,0x7fff,0x5fff,0x55ff,0x5555,0x5555,
0x2fff,0xbfff,0xffff,0xffff,0x5556,0x55aa,0x9aa9,0x9655,
0x5faa,0x5faa,0x5faa,0x55ff,0x5555,0x5555,0x5555,0x5555,
0x5555,0x5555,0x5555,0x5555,0x5555,0x5555,0x5555,0x5555,
0x0000,0x5000,0x5690,0xa555,0xaa95,0xa5a9,0x5aaa,0x9555,
0x57ff,0x0155,0x0005,0xf400,0x7f54,0xffd5,0x5ffd,0xffff,
0xffff,0xffff,0x57ff,0x0007,0x0500,0xdff5,0xfffd,0xffff,
0x55a0,0x55aa,0xaaaa,0x5540,0x0000,0x4005,0xaaaa,0xaaaa,
0x0000,0xf000,0xffc0,0xffff,0x003f,0xffc0,0xffff,0xffff,
0x0000,0x0000,0x0c00,0xfff0,0xc000,0x0000,0xffff,0xffff,
0x8000,0x0000,0x0000,0x000f,0x00ff,0xffff,0xff00,0xfc00,
0x0005,0x0055,0x25aa,0x56aa,0x4285,0x8a16,0x0000,0x0000,
0xffc0,0xf000,0xf000,0xc0c0,0xf3c0,0xf000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0001,0x0001,0x0000,0x0000,0x0000,0x0000,0x0000,0x0001,
0x5555,0x556a,0x55aa,0xfdaa,0x0f6a,0xfd55,0xffd5,0x5ff5,
0x557f,0x957f,0xafff,0xafff,0x5fff,0x5fff,0xabff,0xaaff,
0xaffa,0xafea,0xafaa,0xaaaa,0xaaaa,0xaaaa,0xaaaa,0xaaaa,
0x5555,0x5555,0x5555,0x5555,0x5555,0x5555,0x5555,0x5555,
0x5555,0x5555,0x5555,0x5555,0x5555,0x5555,0x5555,0x5555,
0x5555,0x5555,0x5555,0x5555,0x5555,0x5555,0x5555,0x5555,
0xffff,0xffff,0xffff,0xffff,0xffff,0xffea,0xfaaa,0xaaaa,
0xffff,0xffff,0xffff,0xffea,0xaaaa,0xaaaa,0xaaaa,0xaaaa,
0x5555,0x5556,0x56aa,0xaaaa,0xaa95,0x9555,0x5555,0x5555,
0xffff,0xffff,0xffff,0xffff,0xfeaa,0xaa55,0xa955,0xa955,
0xfc00,0xf000,0x0000,0x8000,0x8002,0x6006,0xa8a0,0x5680,
0x0000,0x0000,0x0000,0x00a0,0x80aa,0x0bfc,0x2ef8,0x0088,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0002,0x0001,0x015a,0x1f55,0x2fff,0x27d5,
0x0000,0x0000,0x9000,0xa95f,0xaaff,0xabff,0xffff,0xffff,
0x0001,0x0255,0xafd5,0xffd5,0xffff,0xffff,0xffff,0xffff,
0x5a80,0x5aaa,0x556a,0x5556,0x57dd,0xf7d5,0xff56,0xf566,
0x2955,0xfd55,0xff55,0xfffd,0xffcf,0x73fa,0x73a8,0xc2a0,
0xffff,0xfd5f,0x555a,0x55a0,0xaa80,0xa000,0x8000,0x0000,
0xffff,0xffff,0xffff,0xffff,0xfffa,0xaaaa,0xaaaa,0xaaaa,
0xffff,0xffff,0xffff,0xffaa,0xaaaa,0xaaaa,0xaa95,0xaa55,
0xffea,0xfeaa,0xaaaa,0xaaaa,0xaaaa,0xa954,0x5554,0x5555,
0xffff,0xffff,0xfffa,0xfeaa,0xaa55,0xa555,0xa555,0xa555,
0xffaa,0xfeaa,0xaa95,0x9555,0x5555,0x5555,0x5555,0x5555,
0x5555,0x5555,0x5555,0x55ff,0x5fff,0xffff,0xffff,0xffff,
0x557d,0x55ff,0x57ff,0x5fff,0x7ffe,0x7ff0,0x7d60,0x5540,
0xa000,0xd000,0xd000,0xc000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x000f,
0x0000,0x0000,0x0000,0x0003,0x000f,0x001d,0x07fd,0x7f5f,
0xbd55,0xbd55,0xfd55,0xfd55,0xff55,0xff55,0xff55,0xff55,
0xffff,0xffff,0xffff,0x7fff,0x5ff5,0x5555,0x556a,0x55aa,
0xffff,0xffff,0xffff,0x5555,0x556a,0x56a8,0x0000,0x0000,
0x555f,0x55fa,0x5f80,0xfa00,0x0000,0x0000,0x002a,0x0aaf,
0x5000,0x0140,0x0000,0x0016,0x01aa,0x5aaa,0xaaaa,0xaaaa,
0x001a,0x016a,0x16aa,0xaaaa,0xaaaa,0xaaaf,0xaaff,0xaffa,
0xffff,0xfff8,0xfaaa,0xaaa5,0xaa95,0x9555,0x5555,0x5555,
0x5555,0x57ff,0xffff,0xffff,0xaaff,0xaabf,0xaabf,0xaabf,
0x556a,0x555a,0xffd6,0xffd5,0xff56,0xff5a,0xff5a,0xff5a,
0x2955,0xaa55,0x6a95,0xaaa5,0x6aa5,0x6a95,0xaaaa,0x96aa,
0xffff,0xffff,0xffff,0xffff,0xffef,0xffaa,0x5faa,0x17a9,
0xffff,0xffff,0xffff,0xabff,0xaaff,0xa55f,0x515a,0x06aa,
0xdd40,0x7540,0xf400,0x6400,0x6800,0x6000,0x8000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0002,0x0003,0x000d,
0x0000,0x0000,0x0000,0x0000,0x0000,0xd000,0x7400,0x7400,
0x0015,0x001f,0x001f,0x0017,0x0005,0x0002,0x0002,0x0002,
0xfff5,0xfff5,0xfd55,0xf555,0xd555,0x52aa,0x5aaa,0x5800,
0x5555,0x555f,0x55fa,0x5f80,0xa800,0x0000,0x0000,0x0000,
0xfd50,0x5450,0x0014,0x0001,0x0005,0x005a,0x01aa,0x16aa,
0x0000,0x0005,0x005a,0x05aa,0x5aaa,0xaaaa,0xaaaa,0xaaaa,
0x15aa,0x5aaa,0xaaaa,0xaaaa,0xaaaf,0xaaaf,0xabff,0xabff,
0xaaaf,0xaaff,0xafff,0xbfff,0xffff,0xffff,0xffff,0xff0f,
0xffff,0xffff,0xffff,0xffff,0xfffc,0xff03,0xffff,0xf0ff
};

const u16 GOTRUNK_PALS1[60] = {
0x0000,0x0fe6,0x07bf,0x0bc9,
0x0000,0x0ee7,0x0692,0x0330,
0x0000,0x0cc8,0x0abb,0x0999,
0x0000,0x0dd7,0x0885,0x0111,
0x0000,0x0ab8,0x0000,0x0453,
0x0000,0x07be,0x069b,0x0acb,
0x0000,0x0de7,0x059a,0x0147,
0x0000,0x0bab,0x0584,0x07bd,
0x0000,0x0110,0x0576,0x08bc,
0x0000,0x0aba,0x038c,0x0322,
0x0000,0x039b,0x0692,0x0243,
0x0000,0x06ad,0x027d,0x01af,
0x0000,0x089c,0x015a,0x017d,
0x0000,0x026a,0x0910,0x0311,
0x0000,0x017c,0x0147,0x0101
};

const u16 GOTRUNK_PALS2[60] = {
0x0000,0x0ffa,0x0adf,0x0bef,
0x0000,0x0ff7,0x0ff7,0x0efb,
0x0000,0x0ff8,0x0ff9,0x0ffb,
0x0000,0x0ff9,0x0aef,0x0deb,
0x0000,0x0ff8,0x0ff9,0x0ddd,
0x0000,0x0eed,0x0adf,0x0dde,
0x0000,0x0ffa,0x0ffc,0x0eeb,
0x0000,0x0fee,0x0ddc,0x0cfb,
0x0000,0x0dcd,0x0eee,0x0dec,
0x0000,0x0fed,0x0ddd,0x0fef,
0x0000,0x0efa,0x0ffb,0x0ffa,
0x0000,0x0efc,0x0cfa,0x0ffb,
0x0000,0x0ddd,0x0bef,0x0fee,
0x0000,0x0eed,0x0bde,0x0ddc,
0x0000,0x0adf,0x0efe,0x0ded
};

const u8 GOTRUNK_PALIDX1[216] = {
0x00,0x00,0x00,0x00,0x01,0x01,0x01,0x01,0x01,0x01,0x01,0x01,0x01,0x01,0x01,0x01,0x01,0x01,
0x00,0x01,0x01,0x01,0x01,0x01,0x02,0x00,0x00,0x01,0x02,0x03,0x03,0x04,0x01,0x01,0x01,0x01,
0x01,0x01,0x01,0x01,0x00,0x02,0x02,0x05,0x02,0x04,0x04,0x03,0x04,0x02,0x01,0x01,0x01,0x01,
0x06,0x06,0x02,0x01,0x01,0x02,0x07,0x07,0x02,0x03,0x04,0x08,0x04,0x04,0x04,0x02,0x04,0x01,
0x09,0x0a,0x01,0x01,0x07,0x07,0x07,0x07,0x02,0x03,0x04,0x08,0x08,0x04,0x04,0x04,0x04,0x02,
0x02,0x01,0x01,0x0a,0x07,0x02,0x05,0x05,0x02,0x00,0x05,0x05,0x08,0x04,0x01,0x04,0x04,0x04,
0x02,0x03,0x08,0x09,0x01,0x08,0x07,0x05,0x00,0x05,0x05,0x05,0x07,0x08,0x04,0x04,0x02,0x01,
0x02,0x02,0x05,0x05,0x01,0x07,0x07,0x05,0x05,0x05,0x09,0x09,0x0b,0x0c,0x09,0x02,0x02,0x01,
0x01,0x02,0x02,0x02,0x01,0x05,0x00,0x09,0x09,0x09,0x0d,0x0d,0x0c,0x0e,0x0d,0x05,0x01,0x01,
0x01,0x01,0x01,0x01,0x01,0x01,0x01,0x09,0x08,0x0d,0x0d,0x0e,0x0c,0x04,0x03,0x05,0x05,0x00,
0x01,0x01,0x05,0x01,0x01,0x01,0x05,0x0b,0x0b,0x0d,0x09,0x0c,0x01,0x01,0x00,0x00,0x00,0x00,
0x05,0x01,0x01,0x05,0x05,0x05,0x09,0x0c,0x0c,0x0c,0x09,0x05,0x05,0x05,0x00,0x00,0x05,0x05
};

const u8 GOTRUNK_PALIDX2[216] = {
0x00,0x00,0x00,0x00,0x00,0x01,0x01,0x01,0x01,0x01,0x01,0x01,0x02,0x02,0x02,0x02,0x02,0x02,
0x00,0x01,0x01,0x01,0x01,0x01,0x02,0x03,0x02,0x02,0x01,0x02,0x02,0x02,0x02,0x02,0x02,0x02,
0x01,0x01,0x01,0x02,0x01,0x04,0x05,0x04,0x04,0x06,0x07,0x00,0x00,0x04,0x02,0x02,0x06,0x06,
0x01,0x02,0x02,0x02,0x02,0x08,0x08,0x09,0x06,0x0a,0x06,0x00,0x00,0x00,0x04,0x0a,0x00,0x0a,
0x02,0x00,0x02,0x02,0x04,0x08,0x08,0x08,0x04,0x04,0x0a,0x00,0x00,0x00,0x05,0x00,0x06,0x0a,
0x02,0x02,0x00,0x00,0x05,0x04,0x05,0x05,0x03,0x03,0x03,0x00,0x00,0x00,0x00,0x00,0x05,0x0b,
0x02,0x0a,0x00,0x00,0x00,0x00,0x03,0x00,0x00,0x00,0x06,0x00,0x00,0x0b,0x07,0x07,0x07,0x0c,
0x04,0x04,0x04,0x03,0x0a,0x03,0x03,0x00,0x00,0x01,0x06,0x06,0x00,0x05,0x09,0x09,0x09,0x05,
0x06,0x0a,0x0a,0x0a,0x0a,0x01,0x01,0x03,0x0b,0x08,0x00,0x00,0x00,0x00,0x05,0x0d,0x09,0x08,
0x06,0x06,0x00,0x0b,0x0b,0x0b,0x07,0x07,0x00,0x00,0x00,0x00,0x0c,0x0c,0x09,0x09,0x0d,0x0c,
0x0b,0x0b,0x0b,0x07,0x07,0x09,0x09,0x0c,0x00,0x00,0x05,0x09,0x09,0x0c,0x0c,0x05,0x0e,0x0e,
0x07,0x0c,0x09,0x09,0x0c,0x0c,0x09,0x00,0x09,0x09,0x09,0x0c,0x05,0x0e,0x0e,0x0e,0x08,0x08
};

#define GOTRUNK_ID {GOTRUNK_WIDTH,GOTRUNK_HEIGHT,GOTRUNK_TILES,(u16*)GOTRUNK_TILES1,(u16*)GOTRUNK_TILES2,GOTRUNK_NPALS1,(u16*)GOTRUNK_PALS1,GOTRUNK_NPALS2,(u16*)GOTRUNK_PALS2,(u8*)GOTRUNK_PALIDX1,(u8*)GOTRUNK_PALIDX2}

const SOD_IMG GOTRUNK_IMG = GOTRUNK_ID;

