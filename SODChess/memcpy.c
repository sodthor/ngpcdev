#include "ngpc.h"

void memcpy(void *dst, void *src, u16 n)
{
	__ASM("		ld		BC,(XSP+12)");
	__ASM("		cp		BC,0");
	__ASM("		ret		z");
	__ASM("		ld		XIX,(XSP+4)		; dst");
	__ASM("		ld		XIY,(XSP+8)		; src");
	__ASM("		cp		XIX,XIY");
	__ASM("		ret		eq");
	__ASM("		bit		0,IX			; address check");
	__ASM("		j		z,__Skip");
	__ASM("		ldib		(XIX+),(XIY+)");
	__ASM("		ret		nov			; BC == 0");
	__ASM("__Skip:");
	__ASM("		srl		1,BC			; /= 2 & set B0 to C-flag");
	__ASM("		jr		z,__ByteCopy");
	__ASM("		ldirw		(XIX+),(XIY+)");
	__ASM("__ByteCopy:");
	__ASM("		ret		nc");
	__ASM("		ldib		(XIX+),(XIY+)");
}
