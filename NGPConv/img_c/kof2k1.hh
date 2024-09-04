#include "img.h"

#define KOF2K1_WIDTH 16
#define KOF2K1_HEIGHT 16
#define KOF2K1_TILES 512

#define KOF2K1_NPALS1 15
#define KOF2K1_NPALS2 15

const u16 KOF2K1_TILES1[2048] = {
0x5625,0x5519,0x5929,0x5525,0x5525,0x5525,0x5526,0x5525,
0x6aaa,0xd6aa,0x76aa,0x676a,0x56da,0x6575,0x5569,0x6555,
0x5566,0x555a,0x5556,0x555a,0x5556,0x555a,0xe566,0xfd5a,
0x5595,0x55b5,0x5595,0x55fd,0x55fd,0x55fd,0x55f5,0x75fd,
0x5555,0x5555,0x5555,0x5555,0x5555,0x5555,0x5555,0x5555,
0xaaaa,0xaaaa,0xaaaa,0x75d7,0x7a6a,0xf966,0xf555,0xf555,
0x5555,0x5555,0x5555,0x5556,0x5556,0x5554,0x5554,0x5554,
0xaaa6,0x9995,0x5965,0x9555,0x7555,0x7555,0x57ff,0x77f7,
0x5555,0x5555,0x555d,0x77dd,0xf7f7,0xff7f,0xffff,0xffff,
0x5555,0x5555,0x5577,0x7dd5,0x7fff,0xfff5,0xffff,0xffff,
0x5555,0x5555,0x5555,0x5555,0x5555,0x5555,0x5555,0x5555,
0x5aaa,0x5aaa,0x5aaa,0x5aa9,0x5aa5,0x5a96,0x5a99,0x5699,
0x5aff,0x7900,0x6800,0x9400,0x5400,0x5400,0x7400,0x5400,
0x9aaa,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x5555,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0xdf40,0x0240,0x0240,0x0240,0x0240,0x0240,0x0240,0x0240,
0x551d,0x551d,0x551d,0x551d,0x551d,0x5535,0x551d,0x5535,
0x5595,0x5956,0x555a,0x555a,0x555a,0x5555,0x5555,0x5555,
0xff55,0xdd55,0xfd55,0xfd55,0xfd55,0xff75,0xffd5,0xfdd5,
0x55a5,0x59a5,0x55b5,0x55b5,0x55b6,0x55b5,0x55b5,0x5575,
0x5555,0x5555,0x5555,0x5555,0x5555,0x5555,0x5555,0x5555,
0xe555,0xed55,0xe5dd,0xef7f,0xefff,0xafff,0xaff7,0xa55d,
0xaaa8,0xaaa8,0xaaa8,0xaaa4,0xaa54,0x95df,0x9654,0xaaa7,
0x5555,0x5555,0x5555,0x5557,0x75ff,0xdfff,0xffff,0x5ddf,
0x5555,0x5555,0x5555,0x6555,0xa6aa,0xefff,0xbbbf,0xffff,
0x55a6,0x65aa,0x5555,0x5565,0xd559,0xffff,0xffff,0xffff,
0xaaaa,0xaaaa,0x595a,0x559a,0x595a,0x5d55,0xdd55,0xf555,
0xf999,0xfa99,0xf695,0xf996,0xf595,0xf555,0xf557,0xf555,
0xa700,0xaa00,0xa900,0xa600,0xa580,0xa580,0xa580,0xa560,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0380,0x0380,0x0380,0x0340,0x0380,0x0340,0x0340,0x0340,
0x5519,0x9ae5,0xa6e5,0x9ae5,0xaaf6,0xaae6,0xaaea,0xaaf6,
0x5555,0x5555,0xdd55,0xd555,0xfff7,0xfff5,0xfff5,0xfff5,
0xf56c,0xfd5a,0xfd5a,0xfd5a,0xfd5a,0xf55a,0xfd5a,0xfd5a,
0x0000,0x5555,0x5555,0x5555,0x5555,0x5555,0x5555,0x5555,
0x000f,0xaa95,0xaaa5,0xaa55,0xa595,0xa555,0x9955,0xa955,
0xfabb,0xaaaa,0xaaaa,0xaaab,0xaabe,0xaabb,0xaabf,0xaeff,
0x5556,0xdfff,0x5fff,0x5fff,0xfbff,0xfeff,0xeaff,0xfaff,
0xffff,0xffbf,0xffef,0xfeba,0xbaaa,0xbaaa,0xbaaa,0xbaea,
0xffff,0xffff,0xefff,0xafbf,0xbaaf,0xaabb,0xaaac,0xaaa0,
0xbb5d,0xeddb,0xffd5,0xee00,0xb000,0x0000,0x0000,0x0000,
0xa995,0xa999,0xaaa9,0x00ea,0x000a,0x0002,0x0000,0x0000,
0x5aaa,0x5aaa,0x5aaa,0x5aaa,0x5aaa,0x5aaa,0xdaaa,0x0aaa,
0xa798,0x57a9,0x9796,0x579a,0x5799,0x9766,0x9799,0x5766,
0x0080,0x0000,0x0000,0x8000,0x6000,0x5800,0x6940,0xe598,
0x8211,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x02c0,0x02c0,0x03c0,0x02c0,0x02c0,0x02c0,0x02c0,0x02c0,
0x559d,0x55b5,0x55a5,0x75ad,0x5ded,0x75ad,0x55ad,0xab89,
0x5555,0xdd55,0xdf55,0xff7d,0xffdd,0xfdfd,0xffdf,0xfffd,
0xf5aa,0x75aa,0xd5aa,0xfd6a,0xfdaa,0xfd6a,0xfd6a,0xfd6a,
0xaaa5,0xaaa5,0xaaa5,0xaa55,0xa595,0xaa95,0xa955,0xa655,
0x556a,0x59af,0x66af,0x66af,0x6a65,0xa2eb,0xa3ff,0xb3ff,
0x5ffe,0x7ffb,0xfffb,0xeeea,0x5555,0xdfff,0xbabb,0xeaaa,
0xbfef,0xffef,0xe3bb,0xcfff,0xaaaa,0xbfff,0xfffc,0xccc0,
0xffff,0xff3f,0xfff3,0xf000,0xaabb,0xcc00,0x3000,0x0000,
0xff00,0xf300,0x3c00,0x0c00,0xb000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0277,0x00dd,0x003f,0x0007,0x0003,0x0000,0x0000,0x0000,
0x5655,0x5655,0x5655,0x5655,0x5655,0x5658,0x1602,0x0a91,
0xeaa9,0xeaa6,0xea66,0xe650,0x670f,0x3d5a,0xa965,0xa955,
0x4000,0x5580,0xa03f,0x0faa,0xeaaa,0x66aa,0x5aaa,0x5aaa,
0x0000,0x0b80,0xd7c0,0x7fc0,0x7fc0,0x7fc0,0x7bc0,0x7f80,
0x0002,0xff80,0xffff,0xffff,0xffff,0xffff,0xffff,0xffff,
0xabff,0x02af,0xfffe,0xffff,0xffff,0xffff,0xffef,0xffab,
0xd56a,0xf56a,0xf56a,0xf56a,0x756a,0xf56a,0xf5aa,0xfd56,
0xaa95,0xaaa5,0xaa57,0xaa57,0xaa57,0xaa55,0xaa57,0xa900,
0xf3fb,0xf3fb,0xf2aa,0xf2aa,0xf2a0,0xf2a8,0x828a,0x0000,
0xffff,0xfff3,0xffcc,0x3300,0x0000,0xf00c,0x3000,0xc000,
0x3000,0x0300,0xc000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x00c0,0x0e00,0x4000,
0x8000,0x0000,0x0000,0x0000,0x0000,0x0800,0x0000,0x0000,
0x01a1,0x00a2,0x0012,0x000e,0x0000,0x0000,0x0000,0x0000,
0xaaa5,0xaaa5,0x5665,0x6a65,0x9595,0x16a5,0x0555,0x0395,
0x9bbb,0x5eef,0x5bbb,0x6bbe,0x5bbb,0x9ebb,0xaefe,0xaefe,
0xffc0,0xffc0,0xfe80,0xef80,0xfec0,0xff80,0xef80,0xef80,
0xffff,0xffff,0xffff,0xffff,0xffff,0xfbff,0xbbbb,0xaea8,
0x5595,0x5655,0x5655,0x5655,0x5655,0x5656,0x6664,0xa083,
0xfeaa,0xfaa0,0xfa89,0xfac6,0xfe2a,0x2e1b,0x4ca8,0x8280,
0x0300,0x9000,0x0000,0x0000,0x0030,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0027,0x0000,0x0304,0x0002,0x00a0,0x0000,
0x2000,0x0a00,0x0000,0x1000,0x0280,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0080,0x0130,0x0b00,0x0000,0x0000,
0x4000,0x3000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0080,0x0000,0x000f,0x0000,0x003f,
0x0000,0x0000,0x0000,0x0000,0x09cb,0xc220,0x0820,0x6802,
0x0015,0x0005,0x0001,0x0000,0x0000,0x4000,0x0000,0x6000,
0x5f7f,0xddf7,0x5f7f,0x7df7,0x377f,0x2fff,0x017f,0x00ff,
0xfe80,0xef80,0xfb80,0xff80,0xfec0,0xff80,0xff80,0xffc0,
0x9a00,0xa642,0x9a00,0x9a04,0xa40c,0xa800,0xaa08,0xaaaa,
0x000d,0x0c00,0x00f0,0x0040,0x004c,0x0015,0x0814,0xa814,
0x4180,0x8800,0x4800,0x8000,0x0000,0x0000,0x0080,0xd600,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0020,0x0000,0x0000,0x0080,0x0001,0x0001,
0x0000,0x8800,0x044c,0x0cc4,0x1300,0x01c0,0x1400,0x7080,
0x0000,0x0410,0x0000,0x0000,0x3080,0x0002,0x0080,0x0088,
0x0000,0x0003,0x003c,0x00d5,0x0099,0x0233,0x0809,0x1573,
0x000c,0x0000,0x0000,0x9000,0xfa40,0x76ba,0x18d3,0xc030,
0x0008,0x0005,0x0005,0x0005,0x00f0,0x7000,0x0000,0x0002,
0x0000,0x0010,0xbfc0,0xac33,0x0000,0x0000,0x0000,0x0000,
0x00c0,0x001f,0x1020,0x4001,0x2008,0x003d,0x003f,0x9c08,
0x099f,0xf50b,0x0758,0x0000,0x981f,0x3603,0xffc0,0x25ff,
0x6400,0xacc0,0x6480,0xaca0,0xaca0,0xaca0,0x9fa4,0xfea4,
0x007b,0x001b,0x0007,0x0000,0x0003,0x0000,0x0000,0x0000,
0xffc0,0xffc0,0xffc0,0xbfc0,0xafc0,0xfbc0,0x2bc0,0x3ac0,
0xffff,0xffff,0xffff,0xffff,0xffff,0xffff,0xffff,0xffff,
0xaa14,0xaa14,0xa815,0xaa85,0xaa82,0xaa88,0xaa8a,0xaa8a,
0x4000,0x0000,0xff00,0xdc00,0xfc01,0x1c09,0x8000,0x002a,
0x0000,0x0000,0x0000,0xa000,0xe028,0x5580,0x0000,0x5555,
0x008b,0x0080,0x0940,0x2015,0x026a,0x1955,0x0000,0x5566,
0x4200,0x0200,0x0200,0xfa8a,0xbaa0,0xea88,0x0000,0xeaaa,
0x0820,0x00a0,0x0003,0xaa03,0x8a03,0xa803,0x0000,0xa803,
0x2f4b,0xe723,0xbce3,0x5d64,0xd5a7,0x5565,0x5c6a,0xafea,
0x0c03,0x3401,0x7504,0xb103,0x93d0,0x930f,0xa4c0,0xa9fc,
0x0030,0x7c03,0xddc0,0xffff,0x3ffd,0x0000,0x3faa,0x0daa,
0x03c0,0xfcef,0x3955,0x003a,0xfffe,0xfe55,0x56c0,0xb000,
0x000f,0xc000,0x5809,0xab0a,0xab00,0x8000,0x0000,0xc000,
0xfffe,0x3955,0x5555,0xff26,0x3fc9,0x03bd,0x0ffe,0x3fff,
0xad8f,0xbb02,0xb702,0xa963,0xaef3,0x6cf3,0x9bcf,0xf7c3,
0x0000,0x0000,0x0000,0xc000,0x8000,0x8000,0x8000,0x8000,
0x0ac0,0x0bc0,0x0b80,0x0240,0x01c0,0x03c0,0x0040,0x0040,
0xffff,0xffff,0xffff,0xffff,0xffff,0xeeee,0xbbbb,0xeeee,
0xfecf,0xffce,0xffcb,0xffca,0xffce,0xeeba,0xbbba,0xeeba,
0x00f7,0xabd7,0xab57,0xab57,0xaf57,0xad57,0xaf57,0xad57,
0x575f,0x5577,0x5557,0x5577,0x5557,0x5557,0x5557,0x5557,
0xefef,0x77ff,0xfdef,0x57fe,0x57ee,0x55df,0x557f,0x55f6,
0xffaa,0xffee,0xfbee,0xffbb,0xffee,0xffff,0xffff,0xffff,
0xa40b,0x565c,0x657c,0x95f8,0x55f0,0x97c0,0x5f80,0x5f00,
0x5fff,0x00bf,0x0007,0x0001,0x0002,0x0000,0x0000,0x0008,
0xffa9,0x65ff,0x567f,0x9a7f,0x9aaa,0xa4a9,0xa849,0xa6e6,
0xa56c,0x5abc,0x6abf,0x7fff,0xb03f,0x0c0f,0x000f,0xc000,
0x2000,0x2800,0x2400,0x06aa,0x89ff,0xa2f5,0xa07f,0x681f,
0xc000,0xc003,0xc003,0x7700,0xb400,0xdc00,0xab00,0xa800,
0x3fe8,0xeab8,0xaaa2,0xaa96,0xaa8a,0xa83f,0x92f3,0x2bc0,
0x4e01,0x1b6d,0xfe76,0xffa6,0xafea,0x6aaa,0x5aaa,0x3eaa,
0x8000,0x8000,0x4000,0x4000,0x4000,0xc000,0xc000,0xc000,
0x0040,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0xeefb,0xbbbb,0xeafb,0xaeef,0xffff,0xffff,0xffff,0xffff,
0xfefa,0xfefa,0xfefa,0xfef9,0xfae5,0xbaa5,0xa9a5,0xaa59,
0xf597,0xf597,0xf595,0xf567,0xd699,0xd667,0xd9a9,0x5659,
0x6a55,0xa655,0x6a55,0x9955,0xaaa5,0x99a5,0xaa69,0xaa55,
0x56aa,0x66aa,0x9aaa,0x96aa,0x565a,0x5595,0x5665,0x5559,
0x5757,0x5577,0x5551,0x557d,0x55c6,0x57ca,0x5748,0x91cc,
0x7c00,0xf400,0x7000,0xc000,0xc000,0x0000,0x0008,0x0028,
0x0302,0x0002,0x0002,0x0c03,0x0000,0x030c,0x03cb,0x30ce,
0xaee6,0x95ef,0xb5f9,0xbfde,0xbfdb,0xbfff,0x75f5,0xd577,
0x7300,0xc300,0x0b00,0x6800,0x6300,0xbd00,0x58c0,0x5b00,
0xfa30,0x3800,0x3e30,0x0ec0,0x03bc,0x02e3,0x00ee,0x003a,
0x0d30,0x0e10,0x01d4,0x00eb,0x0069,0x00a9,0x00a9,0xc0e8,
0x3ff0,0x3f00,0x2abf,0x26aa,0x096b,0x03cc,0x35af,0x3cac,
0x06aa,0x0d6a,0x03fa,0xfd5a,0x6aaa,0x57da,0xda6a,0x003a,
0x8000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0xffff,0xfffe,0xffff,0xaaaa,0xaaaa,0xaaaa,0x5599,0x6659,
0xaaff,0xabfb,0xabff,0xbff9,0xbff9,0xbff9,0xfffa,0xfff5,
0xf557,0xf55f,0xf77f,0xfdd7,0xf555,0x755a,0xf56a,0x7d6a,
0xeaaa,0xffff,0xaaaa,0xaaaa,0x5555,0x5555,0x5555,0x5555,
0xaaae,0xfffe,0xaaab,0xaaa8,0x556c,0x56ef,0x5af2,0x5f32,
0x929c,0x51a0,0x7690,0x6a40,0xaa40,0x5900,0x6800,0x6c00,
0x0028,0x0022,0x0020,0x0020,0x00a0,0x0020,0x0030,0x0032,
0x0c02,0x0f01,0x0eba,0x00ea,0x0015,0x0395,0x0e55,0xa955,
0x955a,0xf559,0x7565,0x5f55,0x6755,0xa9d5,0xaad5,0xaa56,
0x7620,0xfd80,0xff40,0x7fe0,0x7f58,0x5ffa,0xd7f7,0x57f5,
0x003a,0x000a,0x0003,0x0000,0x0000,0x0000,0x0000,0xc000,
0xfce9,0x546a,0x54aa,0x57aa,0xd701,0xd546,0x354a,0x356a,
0x056a,0x6295,0x9a56,0xaa5a,0xab39,0xa000,0x6300,0x5f00,
0xfdab,0xff6b,0x55ab,0x655b,0xa6ab,0x1da7,0x1c3f,0x1c0f,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x2000,
0x5555,0x5555,0x5555,0x5565,0x5555,0x6566,0x5965,0x6566,
0x557d,0x55bd,0x5eaf,0x5d87,0x5f83,0x5fc2,0x5fe0,0x5fd0,
0xf955,0xf955,0xcd55,0xb355,0x62de,0x5b90,0x575f,0x1573,
0xaaaa,0xaaaa,0x6665,0x5595,0x5555,0xffa5,0xc3e9,0xd679,
0x76cd,0x75b9,0xbe69,0x03d5,0xf399,0xfb55,0x7d56,0x6b55,
0x6000,0x7000,0x4300,0x4000,0x4000,0x63fc,0x003f,0x556c,
0x0cd0,0x0c40,0x0dcf,0x01ce,0x0162,0x07ee,0x546e,0x16ae,
0x075d,0x2f55,0x5f75,0x9d55,0x7555,0xf555,0xd595,0x5aaa,
0xa6b6,0xfdbe,0xaaad,0xaa9b,0x9aab,0x5655,0xd555,0xfffd,
0xa5a5,0xa5a5,0x67a9,0xd9e9,0xdae9,0xf9f9,0xfab9,0x7ebe,
0x4000,0x8000,0x9000,0xa400,0xa400,0xa9c0,0xabc0,0xa9f0,
0x0f76,0x0d77,0x0fa6,0x0366,0x03ea,0x0029,0x002a,0x0019,
0xaaaa,0x9a65,0x175d,0x89fd,0x9180,0x712d,0x94b9,0xad12,
0x68cb,0x56c4,0xaa6b,0xaa9f,0xfa9a,0xef9a,0x825a,0xba55,
0x0000,0x0000,0xc000,0xc0c0,0xc0c0,0x07c3,0x0a00,0x8905,
0x20c0,0x0040,0x0840,0x2d80,0x3580,0x15c0,0x1540,0x9540,
0xffff,0xffff,0xffff,0xffff,0xffff,0xffff,0xfffd,0xfffd,
0x5fd6,0x5ffd,0x5fff,0x5fff,0x57ff,0x555f,0x5555,0x5555,
0x8fd5,0x0bff,0xda7f,0xf55f,0xfd55,0xffd5,0xfff6,0x57f5,
0x8aa7,0x5555,0xffff,0xffff,0x97fe,0xaad0,0x0000,0xa00a,
0x5555,0x5556,0x55ff,0x5fc0,0xf0c3,0x3080,0xe42a,0x2955,
0xaa96,0xda8a,0xff58,0x27d5,0xe2ff,0x825d,0x5ddd,0xffeb,
0x295b,0x62ab,0xaa5d,0xa7fd,0xd5de,0x7ffe,0xfff6,0xfff6,
0xd555,0x5699,0xaaaa,0x828a,0x2a08,0xaa2a,0x55a5,0x5595,
0x5555,0xeffd,0xaeff,0xaaaf,0x2abb,0xabfd,0xff55,0xf555,
0x69db,0xaaf6,0xaabe,0xaabd,0xaaaf,0xaa6b,0xa569,0x555a,
0x56a0,0xd5a0,0x756a,0x7d5a,0x9d56,0x67d5,0xe7ff,0xdbff,
0x001d,0xa08f,0x204f,0x104f,0x624f,0xd9cf,0xf7cf,0xf7ef,
0xaf3f,0xacff,0x9dff,0xadff,0x6d7d,0xe6d5,0xea65,0xe996,
0x6675,0xa5f7,0xa5f7,0x57f7,0x57fd,0x77f7,0xd7f7,0xf7f5,
0xd7ad,0xbf66,0xbff5,0xbf55,0xdd55,0xf555,0xf555,0xd555,
0x5540,0x9580,0x5580,0x9580,0x9580,0x9580,0x9580,0x9580,
0xaa95,0xaaab,0xaa55,0xa955,0xa555,0x9555,0x9555,0x5556,
0x5555,0xaaaa,0x5555,0x5555,0x5555,0x5555,0x5555,0x5555,
0x55ff,0xa289,0x55a0,0x55a0,0x5550,0x5557,0x555a,0x5565,
0xffd5,0xffff,0x09ff,0x8002,0x8002,0xd602,0x7fff,0x5555,
0xffff,0xf7ff,0xffff,0x2555,0x05a0,0x0d5a,0xffff,0x5555,
0xffff,0xffff,0xffff,0xffff,0x0000,0x0000,0xe882,0x57ff,
0xfff5,0xfff4,0xfff5,0xff55,0x0095,0x2a95,0xaa56,0xd5aa,
0xaa69,0x00ff,0xaaaa,0xaaaa,0xa555,0x5fff,0xffff,0x3003,
0xaaaa,0xffff,0x5555,0xaa95,0xfea9,0xffea,0xffff,0x0fff,
0x5576,0xaaff,0xffff,0x7fff,0x57ff,0x5555,0x9555,0xaaaa,
0xf5ff,0x6ebf,0xd5af,0xfd6b,0xfff6,0xffff,0xffff,0x5557,
0x5555,0x5555,0x5555,0x5555,0xa555,0x6555,0x55aa,0x5555,
0x9ab6,0xaaaa,0xa665,0xa5be,0x9fea,0xfdaa,0xdaaa,0x5566,
0x9aa6,0xaaa9,0xaaa5,0xaaa5,0xaaa5,0xaaa5,0xaaa5,0x6695,
0x55dd,0x55ff,0x55ff,0x5fff,0x5fff,0x5fff,0x5fff,0x5fff,
0x9540,0x9540,0x9540,0x5540,0x9540,0x5540,0x5540,0x5580,
0xfff5,0xffdd,0x7f7d,0x5fd7,0xdd75,0xdc29,0xfc2b,0xdc28,
0x5555,0x5555,0x5557,0x775d,0xe5f5,0x0000,0x0083,0x00b0,
0x5595,0x5695,0x5455,0x5d55,0xab55,0x0000,0xa4cf,0x0020,
0x6655,0x6655,0x9a65,0xaa99,0xeaa9,0xeaa9,0xaaaa,0xaaaa,
0x5555,0x5555,0x5555,0x5555,0x5555,0x9955,0xaaaa,0xaaaa,
0xffff,0xdfff,0x777f,0x557f,0x5555,0x6555,0xaaaa,0xaaaa,
0x55a2,0x6aaa,0x5556,0x5555,0xd555,0xd755,0xfffd,0xffff,
0x0000,0xa008,0xaaaa,0x5669,0x5555,0x5555,0xdd5d,0xffff,
0x22a9,0x8aa9,0xa655,0xa555,0x5555,0x555d,0xdfff,0xffff,
0xffff,0xffff,0xffff,0xffff,0xdd55,0xd555,0xaaaa,0xaaaa,
0x66aa,0x565a,0x555a,0x6aaa,0x4c0f,0x7aaa,0x8aaa,0xbaaa,
0x5555,0x5555,0x5555,0x5555,0xaaaa,0x5555,0x5555,0x5555,
0x659a,0x65aa,0xa6aa,0x9a66,0xffff,0xaaaa,0x66a9,0xa66a,
0x9aa5,0x6665,0xaa96,0x6aa5,0x56a5,0x56a5,0x5aaa,0x666a,
0x5fff,0x57ff,0x57ff,0x55ff,0x557f,0x557f,0x557f,0x557f,
0x5540,0x5580,0x5580,0x5580,0x5580,0x9580,0x5580,0x9540,
0x54e0,0xf755,0xfd55,0xf555,0x0000,0x0000,0x0000,0x0000,
0x8fe0,0x5556,0x5556,0x555a,0x0000,0x0000,0x0000,0x0000,
0x0020,0x9555,0x5555,0x6659,0x0000,0x0000,0x0000,0x0000,
0x5555,0x5555,0x5555,0x5555,0x0000,0x0000,0x0000,0x0000,
0x5555,0x5555,0x5555,0x5555,0x0000,0x0000,0x0000,0x0000,
0x5555,0x5555,0x5555,0x5555,0x0000,0x0000,0x0000,0x0000,
0x5555,0x5555,0x5555,0x5555,0x0000,0x0000,0x0000,0x0000,
0x5555,0x5555,0x5555,0x5555,0x0000,0x0000,0x0000,0x0000,
0x5555,0x5555,0x5555,0x5555,0x0000,0x0000,0x0000,0x0000,
0x5555,0x5555,0x5555,0x5555,0x0000,0x0000,0x0000,0x0000,
0x6555,0x5955,0x5155,0x5955,0x0000,0x0000,0x0000,0x0000,
0x5555,0x5555,0x5555,0x5555,0x0000,0x0000,0x0000,0x0000,
0x5555,0x5566,0x5999,0x59aa,0x0000,0x0000,0x0000,0x0000,
0x65aa,0x665a,0x99aa,0xaa9a,0x0000,0x0000,0x0000,0x0000,
0x957f,0xa7df,0xa577,0xa65f,0x0000,0x0000,0x0000,0x0000,
0x9540,0x9580,0x9540,0xaa80,0x0000,0x0000,0x0000,0x0000
};

const u16 KOF2K1_TILES2[2048] = {
0x0040,0x0040,0x0040,0x0040,0x0040,0x0040,0x0040,0x0040,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0001,0x0001,0x0001,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0055,0x02bf,0x02ff,0x02ff,0x02ff,0x02bf,0x02bf,
0x0000,0x5555,0xaaaa,0xaaaa,0xaaaa,0xaaaa,0xaaa9,0xaaa6,
0x0000,0x5555,0xaaaa,0xaaaa,0xaaaa,0xaaaa,0xfdea,0xde5a,
0x0015,0x5425,0xf429,0xf429,0xf429,0xf429,0xf429,0xf429,
0x0040,0x0040,0x0040,0x0040,0x0040,0x0040,0x0040,0x0040,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0001,0x0001,0x0001,0x0001,0x0001,0x0000,0x0001,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x007f,0x007f,0x007f,0x00bf,0x001f,0x001f,0x0027,0x0007,
0xaab9,0xaa9f,0xaa96,0xaa9f,0xaab9,0xaaa5,0xaaaa,0xaaaa,
0xed9a,0xdef6,0x7f6e,0xbf6a,0x566a,0xe5aa,0xfeaa,0xaaaa,
0xf429,0xf429,0xf429,0xf429,0xf429,0xf429,0xf429,0xf429,
0x0040,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0001,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x5555,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x5550,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0001,0x0009,
0x0000,0x0000,0x0000,0x0056,0x06aa,0x5a55,0x956a,0xafff,
0x0000,0x0000,0x0000,0xa500,0xff90,0x6bf4,0xaaff,0xffff,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0xd000,
0x0001,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0xff3f,0x7fef,0x6fff,0x27ff,0x09ff,0x027f,0x001b,0x0001,
0x2c8c,0xebff,0xaaaa,0xaaaa,0xaaaa,0xaaaa,0xaaaa,0x6aaa,
0xf429,0xf429,0xf429,0xf429,0xf429,0xf429,0xf429,0xf429,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0010,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0400,0x0400,0x0400,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0400,0x1000,0x0000,0x0000,0x0001,0x1115,
0x0000,0x0040,0x0004,0x0555,0x0000,0x1155,0x4555,0x5555,
0x007f,0x04fe,0x41fa,0x51aa,0x07aa,0x5eaa,0x5eaa,0x5eaa,
0x5555,0x5555,0x555a,0x559a,0x59aa,0x9aaa,0x9aaa,0xaaaa,
0x5555,0x6955,0x6a95,0x6a95,0xaa65,0xaa55,0xa595,0x6555,
0x5800,0x5e00,0x7580,0x5560,0x5578,0xf576,0xf55d,0xed5d,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0001,0xc054,0xb004,
0x0000,0x0000,0x0000,0x0005,0x0050,0x4000,0x0000,0x0000,
0x1abf,0x0015,0x0540,0x5000,0x0000,0x0000,0x0000,0x0000,
0x5bf6,0xf036,0x0036,0x0036,0x0036,0x0036,0x0036,0x0036,
0x5554,0x0015,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x5400,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x006f,
0x0400,0x0400,0x0400,0x0400,0x0405,0x0401,0x1410,0xfff9,
0x0000,0x0004,0x0011,0x4455,0x5555,0x0551,0x4555,0x1555,
0x4555,0x5455,0x1555,0x5555,0x5555,0x5555,0x5555,0x5555,
0x5555,0x5555,0x5555,0x5555,0x5555,0x5555,0x5555,0x5555,
0x5aaf,0x5aef,0x5aee,0x5aef,0x5aaa,0x5abf,0x5abe,0x56af,
0xaa6a,0x6aaa,0xa9a6,0x9aa5,0x6a65,0x6a95,0x5555,0x5657,
0xffff,0xffef,0xffee,0xfe9b,0xf95f,0xe527,0x507b,0x15fe,
0x3f55,0xff65,0x5759,0x5f65,0xdf59,0xf37a,0xdd5e,0x6f75,
0xb804,0xee04,0xfbc4,0xfee0,0xffbe,0xffef,0xfffb,0xffff,
0x0000,0x0000,0x0000,0x0000,0x0000,0x4000,0x9000,0xb800,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0036,0x0036,0x0036,0x0036,0x0036,0x0036,0x0036,0x0036,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0001,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0001,0x0a24,
0x0000,0x0006,0x0010,0x0020,0x0040,0x8080,0x2202,0x241f,
0x54bf,0x05bf,0x96ff,0xabff,0x7fc5,0xbfe9,0xffae,0xffae,
0xffd5,0xffd5,0xffc0,0xff55,0x5451,0x5554,0x5505,0x5555,
0x8aaa,0xa0aa,0x9aaa,0x8aaa,0x982a,0xaaaa,0xaaaa,0xaaaa,
0x5555,0x5555,0x5555,0x5555,0x5555,0x5555,0x5555,0x5555,
0x5555,0x5555,0x5555,0x5555,0x5555,0x5555,0x5555,0x5555,
0x5eaa,0x5eaa,0x57aa,0x55aa,0x55ea,0x557a,0x5557,0x5557,
0xfae9,0xfa95,0xa65f,0xa91b,0xa447,0x909a,0xa56d,0xa999,
0x3aff,0x4bff,0xffff,0xffff,0xffff,0xffff,0xffff,0xaabf,
0xfafb,0xadfa,0xad5a,0xfd1b,0xfe59,0xfe50,0xee55,0x6f40,
0xffff,0xbb5b,0x6ff9,0xe5af,0x9010,0x1445,0x9145,0x0154,
0xae40,0xeb90,0xbef4,0xaeb9,0x6baf,0x1bff,0x55eb,0x05be,
0x0000,0x0000,0x0000,0x0000,0x4000,0xc000,0xf800,0xfd00,
0x0036,0x0036,0x0036,0x0036,0x0036,0x0036,0x0036,0x0036,
0x00aa,0x0028,0x00ab,0x0073,0x0173,0x01aa,0x0051,0x0000,
0xaaa0,0x5259,0xfb0f,0x7e2f,0xbf33,0xaa40,0x5141,0x0142,
0x142f,0x115a,0x2299,0x269a,0xa9aa,0xaaa6,0x5519,0x0069,
0x55a5,0xaafa,0xafbe,0xbffa,0xbffb,0xffeb,0xaafa,0xeafa,
0xf7ff,0x5df7,0x574d,0xbbae,0x799d,0x5515,0x5ffc,0xfdfc,
0xaaaa,0x2265,0xf333,0xf233,0xccf7,0xd82a,0xc195,0x0915,
0xaaa5,0xa289,0xab55,0x9aa5,0x8f25,0xaa94,0x5515,0x5512,
0xaaaa,0xaaa8,0xaa83,0xaa00,0xae00,0xa8cc,0x92e0,0x4004,
0xaaa1,0xaa95,0x5575,0x0a55,0x002b,0x0000,0x8208,0x3bcb,
0xd762,0xd770,0xaff0,0x6aa0,0xaa0a,0x0bbb,0x55ed,0xaea8,
0x57ab,0x5545,0x0015,0x0144,0xffff,0x57ff,0xdd55,0x5555,
0x5a17,0xfa40,0x4a45,0x1af4,0x4ed1,0xde80,0x5680,0x0291,
0x5000,0x00a0,0x9002,0xa6aa,0x0240,0x8098,0x002a,0x8000,
0x027f,0x012f,0x011e,0x010b,0x010e,0x0106,0x0002,0x0002,
0x5500,0x5540,0x5550,0x555a,0x75d4,0x5556,0xed75,0x9f5f,
0x0036,0x0036,0x0036,0x0036,0x0036,0x0036,0xc036,0x4036,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x00c3,0x00c2,0x0140,0x0030,0x001c,0x0011,0x0010,0x0010,
0x2aa5,0xaa9a,0x005d,0x02ff,0x02fc,0x43f0,0x1be6,0x6e40,
0xfdf7,0xffbf,0x9555,0x0555,0x0541,0x0019,0xa955,0x0000,
0xa630,0xa92a,0x5037,0x4fc0,0xf400,0x4000,0x5555,0x0000,
0x18aa,0xf8aa,0xe8aa,0x0020,0x000a,0x0022,0xaaaa,0x0000,
0xa289,0xaa0f,0xaaa4,0x0094,0x20d4,0x02fc,0xaafa,0x02f8,
0x8020,0x0084,0x0104,0x0001,0x0000,0x0000,0x0100,0x0000,
0xe368,0x8268,0x00e1,0x08b4,0x0809,0x04a0,0x022a,0x0002,
0xf549,0x0258,0x0015,0x0000,0x4000,0x56aa,0x8000,0xa000,
0xa82a,0x0200,0x8000,0xa980,0x0000,0x0000,0x0027,0x0957,
0x57b0,0x17a5,0x01a0,0x00a0,0x00ad,0x1fa9,0x7fa9,0x3fa9,
0x0000,0x4000,0x0000,0x0040,0x4010,0x5400,0x5000,0x4000,
0x0020,0x00a8,0x00a4,0x0004,0x0008,0x0208,0x0020,0x0018,
0x9bdb,0xbfdf,0xb67f,0x2ff6,0x25fe,0x2dff,0x2bbf,0x2bfb,
0x901b,0xa01b,0x601b,0xa41b,0xa81b,0xa81b,0xa91b,0xaa1b,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0010,0x0010,0x0010,0x0010,0x0010,0x0000,0x0000,0x0000,
0x6900,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x02a0,0x0001,0x0001,0x0001,0x000d,0x001d,0x003e,0x007e,
0x0000,0x5600,0x55a0,0x6598,0x57ac,0xa766,0x9be9,0xa952,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0100,0x0110,0x0000,
0x0002,0x0002,0x0000,0x0000,0x0980,0xa1a0,0xa9a0,0x26aa,
0x4957,0x41ff,0xc259,0x6000,0x2000,0x0800,0x0900,0x02c0,
0x15f9,0x15e8,0x15e8,0x0099,0x019e,0x019f,0x005b,0x015a,
0x4002,0x0002,0x0008,0x0000,0x0020,0x0280,0x0804,0x8015,
0x2098,0x8000,0x0000,0x0000,0x0000,0x0000,0x0000,0x8000,
0x2d7d,0x2b65,0x2b5f,0x3b5a,0x3b5a,0x3f5a,0x3b7f,0x3b77,
0xbb2d,0xebad,0xeaad,0xeead,0xeeed,0xeeed,0xfeed,0xbeed,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0004,0x0000,0x0010,0x0010,0x0011,0x0412,
0x02f5,0x01f5,0x05f6,0x15fe,0x15d6,0x95ea,0x5762,0x5542,
0xa858,0xaa58,0xaa68,0x6298,0x5556,0x9891,0x6410,0x8920,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x049a,0x289a,0xa095,0x02b5,0x08b5,0x00b5,0x0215,0x0067,
0x004d,0x82ad,0x808f,0xa02b,0xa801,0xa808,0xaa00,0xaa80,
0x608b,0xd08b,0xd401,0xfd00,0xf700,0xf700,0xbb00,0x1b02,
0x400a,0x40aa,0x4000,0x4000,0xe000,0xe822,0x8000,0x8202,
0x5000,0x5000,0x5400,0x0000,0x0000,0x0000,0x0000,0xaa80,
0x2aae,0x6aae,0x6aae,0x6aae,0x5aea,0x5aea,0x59ea,0x5aea,
0x7a9b,0x769b,0xba9b,0xb65b,0x7a5b,0x7a5b,0x7a5b,0x7a5b,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0001,0x0001,0x0000,0x0004,0x0044,
0x0801,0x080b,0x000f,0x002f,0x002d,0x0096,0x029a,0x029a,
0x5b42,0x9b88,0x9b86,0x5b85,0x590b,0x6989,0xaa8b,0xaa48,
0x9168,0xa0a8,0xa000,0x9e00,0x6580,0xf800,0x5000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0087,0x0029,0x0029,0x0009,0x0001,0x0000,0x0000,0x0000,
0x6a80,0x6aa0,0x5aa8,0x5aaa,0x5aaa,0x5a6a,0xa9aa,0x19aa,
0x0200,0x0200,0x0200,0x0000,0x00a8,0x0020,0x4020,0x4000,
0xa000,0x0800,0x0000,0x0000,0x0080,0x0bbf,0x08ff,0x00bf,
0x0000,0x0000,0x0000,0x0000,0x0000,0xc000,0xc280,0x83e0,
0x5af6,0x5af9,0x5ae6,0x5af6,0x5ae6,0x5a9a,0x5ada,0x5a99,
0xaa5b,0xa95b,0x695b,0x995b,0x955b,0x555b,0x555b,0x455b,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0020,0x0028,0x002c,0x000e,0x000a,
0x0000,0x0000,0x1000,0x0400,0x0400,0x000a,0x0000,0x8004,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x2800,0x0000,
0x0010,0x0000,0x0000,0x9800,0x0400,0x0000,0x0000,0x0000,
0x06b6,0x0695,0x2455,0x1d55,0x2ff7,0x0402,0x9e80,0x0002,
0x920a,0x9229,0x9020,0xa420,0x9808,0x9000,0x0200,0x8000,
0x5000,0x4000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x159a,0x25aa,0x0aaa,0x02a6,0x02aa,0x002a,0x002a,0x000a,
0x5000,0x5000,0x5000,0x5400,0x5400,0x5540,0x5540,0x5540,
0x0000,0x0000,0x4000,0x2000,0x0416,0x0840,0x0200,0x0048,
0x0110,0x0011,0x0000,0x0000,0x0000,0x0000,0x1400,0x0000,
0x957f,0x95ff,0x1d99,0x1f2b,0x1f2b,0xd028,0x90aa,0x20b0,
0x451b,0x551b,0x511b,0x401b,0x401b,0x401b,0x401b,0x001b,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x2000,0xa000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x1000,0x0000,0x0000,0x0000,0x0000,0x0005,0x5555,0x0550,
0x0000,0x0000,0x0000,0x003e,0x0b3c,0xcf2a,0x0280,0x8000,
0x0000,0x0010,0x0001,0x8000,0x0400,0x1400,0x0000,0x0000,
0x4000,0x0400,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x1410,0x4051,0x0040,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x4000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0005,0x0005,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x5540,0x0510,0x4510,0x4510,0x0410,0x0010,0x0010,0x0000,
0x0040,0x0200,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0036,0x0036,0x0036,0x0036,0x0036,0x0036,0x0036,0x0036,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0420,0x000a,0x000a,0x000a,0x0000,0x0000,0x0000,
0x0000,0x0000,0xa000,0x3bec,0x3bec,0x00ac,0x0000,0x0000,
0x0000,0x0000,0x0000,0x8000,0xa00a,0xa000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0xbfbf,0xbfbe,0x0228,0x0000,
0x0000,0x0001,0x0000,0x0000,0xaa00,0x8000,0x0000,0x0000,
0x0000,0x5500,0x0000,0x0000,0x0000,0x0000,0x0000,0x4554,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x5000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0036,0x0036,0x0036,0x0036,0x0036,0x0036,0x0036,0x0036,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0280,0x02c0,0x0283,
0x0000,0x0000,0x0000,0x0000,0x0000,0xe5fd,0x6f1c,0xe50d,
0x0000,0x0000,0x0100,0x0000,0x0000,0xbfa9,0x0320,0x57c6,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0004,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x5555,0x0551,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x4400,0x1000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x1150,0x0000,0x1000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
0x0036,0x0036,0x0036,0x0036,0x0036,0x0036,0x0036,0x0036,
0x010b,0x0000,0x0000,0x0000,0xa5ff,0xa955,0xaaaa,0xaaaa,
0x200f,0x0000,0x0000,0x0000,0xffff,0x5555,0xaaaa,0xaaaa,
0xaeca,0x0000,0x0000,0x0000,0xffff,0x5555,0xaaaa,0xaaaa,
0x0000,0x0000,0x0000,0x0000,0xffff,0x5555,0xaaaa,0xaaaa,
0x0000,0x0000,0x0000,0x0000,0xffff,0x5555,0xaaaa,0xaaaa,
0x0000,0x0000,0x0000,0x0000,0xffff,0x5555,0xaaaa,0xaaaa,
0x0000,0x0000,0x0000,0x0000,0xffff,0x5555,0xaaaa,0xaaaa,
0x0000,0x0000,0x0000,0x0000,0xffff,0x5555,0xaaaa,0xaaaa,
0x0000,0x0000,0x0000,0x0000,0xffff,0x5555,0xaaaa,0xaaaa,
0x0000,0x0000,0x0000,0x0000,0xffff,0x5555,0xaaaa,0xaaaa,
0x0000,0x0000,0x0c00,0x0000,0xffff,0x5555,0xaaaa,0xaaaa,
0x0000,0x0000,0x0000,0x0000,0xffff,0x5555,0xaaaa,0xaaaa,
0x0000,0x0000,0x0000,0x0000,0xffff,0x5555,0xaaaa,0xaaaa,
0x0000,0x0000,0x0000,0x0000,0xffff,0x5555,0xaaaa,0xaaaa,
0x0000,0x0000,0x0000,0x0000,0xffff,0x5555,0xaaaa,0xaaaa,
0x0036,0x0036,0x0036,0x0036,0xffd6,0x555a,0xaaaa,0xaaaa
};

const u16 KOF2K1_PALS1[60] = {
0x0000,0x0422,0x0322,0x0522,
0x0000,0x0422,0x0322,0x0623,
0x0000,0x0322,0x0211,0x0422,
0x0000,0x0222,0x0522,0x0322,
0x0000,0x0222,0x0623,0x0723,
0x0000,0x0212,0x0111,0x0322,
0x0000,0x0222,0x0433,0x0111,
0x0000,0x0211,0x0623,0x0623,
0x0000,0x0211,0x0322,0x0523,
0x0000,0x0422,0x0522,0x0623,
0x0000,0x0522,0x0623,0x0523,
0x0000,0x0423,0x0222,0x0623,
0x0000,0x0523,0x0723,0x0322,
0x0000,0x0322,0x0523,0x0111,
0x0000,0x0212,0x0423,0x0345
};

const u16 KOF2K1_PALS2[60] = {
0x0000,0x0834,0x0655,0x0677,
0x0000,0x0dde,0x0889,0x025e,
0x0000,0x0fff,0x025e,0x014b,
0x0000,0x0eef,0x025e,0x07ad,
0x0000,0x0834,0x0caa,0x0a66,
0x0000,0x0845,0x0b88,0x0dbb,
0x0000,0x0dbb,0x0edd,0x0a77,
0x0000,0x0dbb,0x0a77,0x0c9a,
0x0000,0x0855,0x0cbb,0x05ad,
0x0000,0x0aaa,0x0eee,0x0777,
0x0000,0x0855,0x0b99,0x0dcd,
0x0000,0x0b88,0x0834,0x0956,
0x0000,0x0965,0x0dbb,0x023e,
0x0000,0x025e,0x0ca8,0x0fec,
0x0000,0x0945,0x0834,0x0a56
};

const u8 KOF2K1_PALIDX1[256] = {
0x00,0x01,0x02,0x03,0x04,0x02,0x04,0x02,0x03,0x03,0x04,0x05,0x06,0x05,0x07,0x06,
0x00,0x00,0x03,0x08,0x04,0x03,0x01,0x00,0x09,0x00,0x00,0x05,0x02,0x0a,0x0a,0x01,
0x09,0x00,0x02,0x04,0x01,0x09,0x0a,0x07,0x07,0x0a,0x09,0x0b,0x02,0x0b,0x0b,0x0a,
0x0a,0x0a,0x00,0x00,0x09,0x0a,0x04,0x04,0x04,0x0a,0x0a,0x05,0x04,0x01,0x09,0x0a,
0x07,0x07,0x01,0x01,0x07,0x04,0x04,0x0a,0x0a,0x0a,0x01,0x0a,0x08,0x01,0x09,0x07,
0x07,0x0c,0x08,0x01,0x0c,0x07,0x0a,0x0a,0x0a,0x08,0x01,0x01,0x0a,0x01,0x0a,0x07,
0x0c,0x07,0x0d,0x0a,0x07,0x07,0x0a,0x0b,0x0b,0x0a,0x01,0x0c,0x0c,0x01,0x0a,0x07,
0x04,0x07,0x06,0x0a,0x0c,0x0a,0x07,0x0b,0x0b,0x01,0x08,0x08,0x08,0x08,0x01,0x0a,
0x04,0x04,0x03,0x00,0x0a,0x07,0x0c,0x0c,0x0d,0x08,0x0c,0x0b,0x0b,0x0b,0x01,0x0a,
0x07,0x09,0x02,0x01,0x09,0x01,0x0c,0x04,0x0d,0x08,0x04,0x0b,0x08,0x0b,0x0a,0x0a,
0x09,0x03,0x05,0x08,0x08,0x01,0x0a,0x08,0x05,0x0d,0x04,0x0b,0x0e,0x02,0x0a,0x0a,
0x00,0x06,0x08,0x02,0x08,0x08,0x0b,0x0d,0x02,0x05,0x01,0x01,0x0b,0x08,0x08,0x0a,
0x05,0x06,0x06,0x0d,0x0e,0x0d,0x0d,0x0c,0x0a,0x0d,0x0d,0x0c,0x02,0x06,0x06,0x00,
0x08,0x04,0x06,0x06,0x06,0x06,0x06,0x01,0x08,0x0d,0x0d,0x07,0x05,0x05,0x05,0x00,
0x06,0x06,0x06,0x01,0x00,0x00,0x0c,0x0c,0x0c,0x00,0x01,0x04,0x02,0x05,0x03,0x00,
0x06,0x08,0x05,0x04,0x04,0x04,0x04,0x04,0x04,0x04,0x04,0x04,0x05,0x05,0x05,0x00
};

const u8 KOF2K1_PALIDX2[256] = {
0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x01,0x02,0x03,0x01,
0x04,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x01,0x03,0x03,0x01,
0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x05,0x05,0x05,0x05,0x02,0x01,0x02,0x01,
0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x04,0x06,0x06,0x07,0x04,0x00,0x08,0x09,
0x00,0x00,0x00,0x0a,0x05,0x00,0x00,0x00,0x0a,0x06,0x05,0x06,0x05,0x0a,0x00,0x09,
0x00,0x0b,0x0a,0x0c,0x0c,0x0b,0x00,0x00,0x04,0x05,0x0a,0x05,0x05,0x0a,0x05,0x09,
0x05,0x05,0x0c,0x0d,0x06,0x05,0x05,0x0b,0x0b,0x0b,0x04,0x04,0x0e,0x05,0x07,0x09,
0x00,0x04,0x08,0x05,0x04,0x0b,0x0b,0x00,0x0e,0x0e,0x0e,0x04,0x00,0x0e,0x07,0x0a,
0x00,0x00,0x05,0x00,0x00,0x00,0x0e,0x0e,0x00,0x0e,0x0e,0x05,0x00,0x0e,0x07,0x07,
0x00,0x00,0x00,0x00,0x00,0x00,0x0e,0x0e,0x00,0x0e,0x0e,0x0e,0x0b,0x00,0x05,0x0a,
0x00,0x00,0x00,0x00,0x00,0x0e,0x0e,0x0e,0x00,0x0e,0x0e,0x00,0x00,0x00,0x05,0x0a,
0x00,0x00,0x00,0x00,0x0e,0x0e,0x0e,0x00,0x00,0x00,0x0e,0x00,0x00,0x0e,0x0b,0x0a,
0x00,0x00,0x00,0x0e,0x00,0x04,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x09,
0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x09,
0x04,0x09,0x0a,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x09,
0x09,0x09,0x09,0x09,0x09,0x09,0x09,0x09,0x09,0x09,0x09,0x09,0x09,0x09,0x09,0x09
};

#define KOF2K1_ID {KOF2K1_WIDTH,KOF2K1_HEIGHT,KOF2K1_TILES,(u16*)KOF2K1_TILES1,(u16*)KOF2K1_TILES2,KOF2K1_NPALS1,(u16*)KOF2K1_PALS1,KOF2K1_NPALS2,(u16*)KOF2K1_PALS2,(u8*)KOF2K1_PALIDX1,(u8*)KOF2K1_PALIDX2}

const SOD_IMG KOF2K1_IMG = KOF2K1_ID;

