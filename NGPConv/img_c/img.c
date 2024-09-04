#include "ngpc.h"
#include "img.h"

void showImage(u16 offset,SOD_IMG* img,u8 x,u8 y)
{
  u16 i,l=img->w*img->h*8,j,o=offset*8;

  SCR1_X = SCR2_X = 0;
  SCR1_Y = SCR2_Y = 0;

  for (i=0;i<16;i++)
  {
      (SCROLL_1_PALETTE)[i] = 0;
      if (img->np2>0)
         (SCROLL_2_PALETTE)[i] = 0;
  }

  for (j=0;j<19;j++)
  {
      for (i=0;i<20;i++)
      {
          (SCROLL_PLANE_1)[j*32+i] = 0;
          if (img->np2>0)
             (SCROLL_PLANE_2)[j*32+i] = 0;
      }
  }

  for (i=0;i<l;i++)
  {
        (TILE_RAM)[o+i] = img->t1[i];
        if (img->np2>0)
           (TILE_RAM)[o+i+l] = img->t2[i];
  }

  if (x==CENTER)
     x = (20-img->w)/2;
  if (y==CENTER)
     y = (19-img->h)/2;

  l>>=3;
  for (i=0;i<img->h;i++)
  {
      for (j=0;j<img->w;j++)
      {
          u16 a = img->pi1[i*img->w+j]+1;
          (SCROLL_PLANE_1)[(i+y)*32+j+x] = (a<<9)+(offset+i*img->w+j);
          if (img->np2==0)
             continue;
          a = img->pi2[i*img->w+j]+1;
          (SCROLL_PLANE_2)[(i+y)*32+j+x] = (a<<9)+(offset+i*img->w+j+l);
      }
  }

  for (i=0;i<img->np1*4;i++)
        (SCROLL_1_PALETTE)[i+4] = img->p1[i];
  for (i=0;i<img->np2*4;i++)
        (SCROLL_2_PALETTE)[i+4] = img->p2[i];

}
