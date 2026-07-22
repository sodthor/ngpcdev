#include "ngpc.h"

void memcpy(void *dst, void *src, u16 n)
{
 __asm("  ld  BC,(XSP+12)");
 __asm("  cp  BC,0");
 __asm("  ret  z");
 __asm("  ld  XIX,(XSP+4)  ; dst");
 __asm("  ld  XIY,(XSP+8)  ; src");
 __asm("  cp  XIX,XIY");
 __asm("  ret  eq");
 __asm("  bit  0,IX   ; address check");
 __asm("  jrl  z,__Skip");
 __asm("  ldib (XIX+),(XIY+)");
 __asm("  ret  nov    ; BC == 0");
 __asm("__Skip:");
 __asm("  srl  1,BC   ; /= 2 & set B0 to C-flag");
 __asm("  jrl  z,__ByteCopy");
 __asm("  ldirw (XIX+),(XIY+)");
 __asm("__ByteCopy:");
 __asm("  ret  nc");
 __asm("  ldib (XIX+),(XIY+)");
}
