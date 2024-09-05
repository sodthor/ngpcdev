#ifndef _IMG_H
#define _IMG_H

#include "types.h"

#define CENTER 0xff

typedef struct sod_img
{
  u16  w;
  u16  h;
  u16  nbt;
  u16* t1;
  u16* t2;
  u8   np1;
  u16* p1;
  u8   np2;
  u16* p2;
  u8*  pi1;
  u8*  pi2;
  u16* idx1;
  u16* idx2;
  u16* flip1;
  u16* flip2;
  u16  empty;
} SOD_IMG;

void showImage(SOD_IMG* img, u8 x, u8 y, u16* plane, u16 tileOffset, u16* palette, u8 paletteOffset);

#endif
