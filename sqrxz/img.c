#include "ngpc.h"
#include "img.h"
#ifdef WIN32
#include "win32.h"
#else
#define NULL 0L
#endif

void showImage(SOD_IMG* img, u8 x, u8 y, u16* plane, u16 tileOffset, u16* palette, u8 paletteOffset)
{
	u16 i, j, a, o = tileOffset<<3;
	u16 nbt = (img->t2 != NULL ? img->w * img->h : img->nbt) << 3;
	
	for (i=0;i<nbt;++i) {
		(TILE_RAM)[o+i] = img->t1[i];
		if (plane == NULL && img->t2 != NULL)
			(TILE_RAM)[o+i+nbt] = img->t2[i];
	}
	nbt >>= 3;
	if (x==CENTER)
		x = (20-img->w)/2;
	if (y==CENTER)
		y = (19-img->h)/2;

	for (i=0;i<img->h;++i)
	{
		for (j=0;j<img->w;++j)
		{
			if (plane == NULL)
			{
				a = img->pi1[i*img->w+j];
				if (img->idx1 != NULL)
					(SCROLL_PLANE_1)[(i+y)*32+j+x] = (a<<9)+(tileOffset+img->idx1[i*img->w+j])+(img->flip1!=NULL?img->flip1[i*img->w+j]:0);
				else
					(SCROLL_PLANE_1)[(i+y)*32+j+x] = (a<<9)+(tileOffset+i*img->w+j);
				if (img->pi2 != NULL)
				{
					a = img->pi2[i*img->w+j];
					if (img->idx2 != NULL)
						(SCROLL_PLANE_2)[(i+y)*32+j+x] = (a<<9)+(tileOffset+img->idx2[i*img->w+j])+(img->flip2!=NULL?img->flip2[i*img->w+j]:0);
					else
						(SCROLL_PLANE_2)[(i+y)*32+j+x] = (a<<9)+(tileOffset+nbt+i*img->w+j);
				}
			}
			else
			{
				a = img->pi1[i*img->w+j];
				plane[(i+y)*32+j+x] = (a<<9)+(tileOffset+i*img->w+j);
			}
		}
	}

	if (palette != NULL)
	{
		for (i=0;i<img->np1*4;++i)
			palette[i + paletteOffset * 4] = img->p1[i];
	}
	else 
	{
		for (i=0;i<img->np1*4;++i)
			(SCROLL_1_PALETTE)[i] = img->p1[i];
		for (i=0;i<img->np2*4;++i)
			(SCROLL_2_PALETTE)[i] = img->p2[i];
	}
}
