#include "ngpc.h"
#include "img.h"

void showImage8(u16 offset,const SOD_IMG* img,u8 x,u8 y)
{
	u16 i,l=img->w*img->h*8,j,o=offset*8;

	for (i=0;i<16;i++)
		(SCROLL_2_PALETTE)[i] = 0;

	for (j=0;j<19;j++)
		for (i=0;i<20;i++)
			(SCROLL_PLANE_2)[j*32+i] = 0;

	for (i=0;i<l;i++)
	{
		(TILE_RAM)[o+i] = img->t1[i];
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
			u16 a = img->pi1[i*img->w+j];
			(SCROLL_PLANE_2)[(i+y)*32+j+x] = (a<<9)+(offset+i*img->w+j);
		}
	}

	for (i=0;i<img->np1*4;i++)
        	(SCROLL_2_PALETTE)[i] = img->p1[i];
}

void showImage16(u16 offset,const SOD_IMG* img,u8 x,u8 y)
{
	u16 i,l=img->w*img->h*8,j,o=offset*8;

	for (i=0;i<16;i++)
	{
		(SCROLL_1_PALETTE)[i] = 0;
		(SCROLL_2_PALETTE)[i] = 0;
	}

	for (j=0;j<19;j++)
		for (i=0;i<20;i++)
		{
			(SCROLL_PLANE_1)[j*32+i] = 0;
			(SCROLL_PLANE_2)[j*32+i] = 0;
		}

	for (i=0;i<l;i++)
	{
		(TILE_RAM)[o+i] = img->t1[i];
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
			u16 a = img->pi1[i*img->w+j];
			(SCROLL_PLANE_1)[(i+y)*32+j+x] = (a<<9)+(offset+i*img->w+j);
			a = img->pi2[i*img->w+j];
			(SCROLL_PLANE_2)[(i+y)*32+j+x] = (a<<9)+(offset+l+i*img->w+j);
		}
	}

	for (i=0;i<img->np1*4;i++)
	{
        (SCROLL_1_PALETTE)[i] = img->p1[i];
        (SCROLL_2_PALETTE)[i] = img->p2[i];
	}
}
