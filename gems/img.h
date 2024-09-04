#ifndef _IMG_LIB
#define _IMG_LIB

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
} SOD_IMG;

void showImage(u16,SOD_IMG*,u8,u8);

#endif
