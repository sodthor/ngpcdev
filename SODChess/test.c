#include "stdio.h"

extern unsigned char eatdot[]; // Thanks to Dark Fader

void main()
{
	*((u8*)rWBAX) = 0;
	*((u8*)rWBAY) = 0;
	*((u8*)rWSIX) = 160;
	*((u8*)rWSIY) = 152;
	*((u16*)_BGCPAL) = 0;

	SysPlayWave(eatdot);

	while (1)
	{
	}
}
