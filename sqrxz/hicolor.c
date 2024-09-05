#include "ngpc.h"

void hc_load(void *img)
{
#ifndef WIN32
__ASM("	_SCR1RAM	equ	0x9000	");
__ASM("	_SCR2RAM	equ	0x9800	");
__ASM("	_TILERAM	equ	0xA000	");
__ASM("	_SCR1PAL	equ	0x8280	");
__ASM("	_SCR2PAL	equ	0x8300	");
__ASM("	rRASV		equ	0x8009	");
__ASM("	DYN		equ	6080	");
__ASM("	IDX		equ	14592	");

__ASM("	ld	xwa,(xsp+4)		");
__ASM("	ld	xhl,xwa			");
__ASM("	ld	xde,_TILERAM+40*16	");

__ASM("	ld	bc,20*19*8		");
__ASM("	ldirw	(xde+),(xhl+)		");

__ASM("	ld	xhl,xwa			");
__ASM("	add	xhl,IDX			");

__ASM("	ld	xix,_SCR1RAM		");
__ASM("	ld	xiy,_SCR2RAM		");

__ASM("	ld	a,19			");
__ASM("_hc_load_:			");
__ASM("	ld	xde,xix			");
__ASM("	ld	bc,20			");
__ASM("	ldirw	(xde+),(xhl+)		");
__ASM("	add	xix,64			");
__ASM("	ld	xde,xiy			");
__ASM("	ld	bc,20			");
__ASM("	ldirw	(xde+),(xhl+)		");
__ASM("	add	xiy,64			");
__ASM("	djnz	a,_hc_load_		");
#endif
}

/************************************
 * Show HiColor picture on emulators
 */

void hc_showEmu(void *img)
{
#ifndef WIN32
__ASM("	ld	xhl,(xsp+4)		");
__ASM("	or	xhl,xhl			");
__ASM("	ret	z			");

__ASM("	add	xhl,DYN			");

__ASM("	ld	xde,_TILERAM		");
__ASM("	ld	bc,8*20			");
__ASM("	ldirw	(xde+),(xhl+)		");
__ASM("	ld	xde,_SCR1PAL		");
__ASM("	ld	bc,32			");
__ASM("	ldirw	(xde+),(xhl+)		");
__ASM("	ld	xde,_SCR2PAL		");
__ASM("	ld	bc,32			");
__ASM("	ldirw	(xde+),(xhl+)		");

__ASM("hc_wait_0:			");
__ASM("	ld	c,(rRASV)		");
__ASM("	or	c,c			");
__ASM("	jr	nz,hc_wait_0		");

__ASM("	ld	xde,_TILERAM+20*16	");
__ASM("	ld	bc,8*20			");
__ASM("	ldirw	(xde+),(xhl+)		");
__ASM("	ld	xde,_SCR1PAL+64		");
__ASM("	ld	bc,32			");
__ASM("	ldirw	(xde+),(xhl+)		");
__ASM("	ld	xde,_SCR2PAL+64		");
__ASM("	ld	bc,32			");
__ASM("	ldirw	(xde+),(xhl+)		");

__ASM("hc_wait_1:			");
__ASM("	ld	c,(rRASV)		");
__ASM("	and	c,7			");
__ASM("	jr	nz,hc_wait_1		");

__ASM("	ld	xde,_TILERAM		");
__ASM("	ld	bc,8*20     		");
__ASM("	ldirw	(xde+),(xhl+)		");
__ASM("	ld	xde,_SCR1PAL		");
__ASM("	ld	bc,32       		");
__ASM("	ldirw	(xde+),(xhl+)		");
__ASM("	ld	xde,_SCR2PAL		");
__ASM("	ld	bc,32       		");
__ASM("	ldirw	(xde+),(xhl+)		");

__ASM("hc_wait_2:			");
__ASM("	ld	c,(rRASV)		");
__ASM("	and	c,7			");
__ASM("	jr	nz,hc_wait_2		");

__ASM("	ld	xde,_TILERAM+20*16	");
__ASM("	ld	bc,8*20			");
__ASM("	ldirw	(xde+),(xhl+)		");
__ASM("	ld	xde,_SCR1PAL+64		");
__ASM("	ld	bc,32			");
__ASM("	ldirw	(xde+),(xhl+)		");
__ASM("	ld	xde,_SCR2PAL+64		");
__ASM("	ld	bc,32			");
__ASM("	ldirw	(xde+),(xhl+)		");

__ASM("hc_wait_3:			");
__ASM("	ld	c,(rRASV)		");
__ASM("	and	c,7			");
__ASM("	jr	nz,hc_wait_3		");

__ASM("	ld	xde,_TILERAM		");
__ASM("	ld	bc,8*20     		");
__ASM("	ldirw	(xde+),(xhl+)		");
__ASM("	ld	xde,_SCR1PAL		");
__ASM("	ld	bc,32       		");
__ASM("	ldirw	(xde+),(xhl+)		");
__ASM("	ld	xde,_SCR2PAL		");
__ASM("	ld	bc,32       		");
__ASM("	ldirw	(xde+),(xhl+)		");

__ASM("hc_wait_4:			");
__ASM("	ld	c,(rRASV)		");
__ASM("	and	c,7			");
__ASM("	jr	nz,hc_wait_4		");

__ASM("	ld	xde,_TILERAM+20*16	");
__ASM("	ld	bc,8*20			");
__ASM("	ldirw	(xde+),(xhl+)		");
__ASM("	ld	xde,_SCR1PAL+64		");
__ASM("	ld	bc,32			");
__ASM("	ldirw	(xde+),(xhl+)		");
__ASM("	ld	xde,_SCR2PAL+64		");
__ASM("	ld	bc,32			");
__ASM("	ldirw	(xde+),(xhl+)		");

__ASM("hc_wait_5:			");
__ASM("	ld	c,(rRASV)		");
__ASM("	and	c,7			");
__ASM("	jr	nz,hc_wait_5		");

__ASM("	ld	xde,_TILERAM		");
__ASM("	ld	bc,8*20     		");
__ASM("	ldirw	(xde+),(xhl+)		");
__ASM("	ld	xde,_SCR1PAL		");
__ASM("	ld	bc,32       		");
__ASM("	ldirw	(xde+),(xhl+)		");
__ASM("	ld	xde,_SCR2PAL		");
__ASM("	ld	bc,32       		");
__ASM("	ldirw	(xde+),(xhl+)		");

__ASM("hc_wait_6:			");
__ASM("	ld	c,(rRASV)		");
__ASM("	and	c,7			");
__ASM("	jr	nz,hc_wait_6		");

__ASM("	ld	xde,_TILERAM+20*16	");
__ASM("	ld	bc,8*20			");
__ASM("	ldirw	(xde+),(xhl+)		");
__ASM("	ld	xde,_SCR1PAL+64		");
__ASM("	ld	bc,32			");
__ASM("	ldirw	(xde+),(xhl+)		");
__ASM("	ld	xde,_SCR2PAL+64		");
__ASM("	ld	bc,32			");
__ASM("	ldirw	(xde+),(xhl+)		");

__ASM("hc_wait_7:			");
__ASM("	ld	c,(rRASV)		");
__ASM("	and	c,7			");
__ASM("	jr	nz,hc_wait_7		");

__ASM("	ld	xde,_TILERAM		");
__ASM("	ld	bc,8*20     		");
__ASM("	ldirw	(xde+),(xhl+)		");
__ASM("	ld	xde,_SCR1PAL		");
__ASM("	ld	bc,32       		");
__ASM("	ldirw	(xde+),(xhl+)		");
__ASM("	ld	xde,_SCR2PAL		");
__ASM("	ld	bc,32       		");
__ASM("	ldirw	(xde+),(xhl+)		");

__ASM("hc_wait_8:			");
__ASM("	ld	c,(rRASV)		");
__ASM("	and	c,7			");
__ASM("	jr	nz,hc_wait_8		");

__ASM("	ld	xde,_TILERAM+20*16	");
__ASM("	ld	bc,8*20			");
__ASM("	ldirw	(xde+),(xhl+)		");
__ASM("	ld	xde,_SCR1PAL+64		");
__ASM("	ld	bc,32			");
__ASM("	ldirw	(xde+),(xhl+)		");
__ASM("	ld	xde,_SCR2PAL+64		");
__ASM("	ld	bc,32			");
__ASM("	ldirw	(xde+),(xhl+)		");

__ASM("hc_wait_9:			");
__ASM("	ld	c,(rRASV)		");
__ASM("	and	c,7			");
__ASM("	jr	nz,hc_wait_9		");

__ASM("	ld	xde,_TILERAM		");
__ASM("	ld	bc,8*20     		");
__ASM("	ldirw	(xde+),(xhl+)		");
__ASM("	ld	xde,_SCR1PAL		");
__ASM("	ld	bc,32       		");
__ASM("	ldirw	(xde+),(xhl+)		");
__ASM("	ld	xde,_SCR2PAL		");
__ASM("	ld	bc,32       		");
__ASM("	ldirw	(xde+),(xhl+)		");

__ASM("hc_wait_10:			");
__ASM("	ld	c,(rRASV)		");
__ASM("	and	c,7			");
__ASM("	jr	nz,hc_wait_10		");

__ASM("	ld	xde,_TILERAM+20*16	");
__ASM("	ld	bc,8*20			");
__ASM("	ldirw	(xde+),(xhl+)		");
__ASM("	ld	xde,_SCR1PAL+64		");
__ASM("	ld	bc,32			");
__ASM("	ldirw	(xde+),(xhl+)		");
__ASM("	ld	xde,_SCR2PAL+64		");
__ASM("	ld	bc,32			");
__ASM("	ldirw	(xde+),(xhl+)		");

__ASM("hc_wait_11:			");
__ASM("	ld	c,(rRASV)		");
__ASM("	and	c,7			");
__ASM("	jr	nz,hc_wait_11		");

__ASM("	ld	xde,_TILERAM		");
__ASM("	ld	bc,8*20     		");
__ASM("	ldirw	(xde+),(xhl+)		");
__ASM("	ld	xde,_SCR1PAL		");
__ASM("	ld	bc,32       		");
__ASM("	ldirw	(xde+),(xhl+)		");
__ASM("	ld	xde,_SCR2PAL		");
__ASM("	ld	bc,32       		");
__ASM("	ldirw	(xde+),(xhl+)		");

__ASM("hc_wait_12:			");
__ASM("	ld	c,(rRASV)		");
__ASM("	and	c,7			");
__ASM("	jr	nz,hc_wait_12		");

__ASM("	ld	xde,_TILERAM+20*16	");
__ASM("	ld	bc,8*20			");
__ASM("	ldirw	(xde+),(xhl+)		");
__ASM("	ld	xde,_SCR1PAL+64		");
__ASM("	ld	bc,32			");
__ASM("	ldirw	(xde+),(xhl+)		");
__ASM("	ld	xde,_SCR2PAL+64		");
__ASM("	ld	bc,32			");
__ASM("	ldirw	(xde+),(xhl+)		");

__ASM("hc_wait_13:			");
__ASM("	ld	c,(rRASV)		");
__ASM("	and	c,7			");
__ASM("	jr	nz,hc_wait_13		");

__ASM("	ld	xde,_TILERAM		");
__ASM("	ld	bc,8*20     		");
__ASM("	ldirw	(xde+),(xhl+)		");
__ASM("	ld	xde,_SCR1PAL		");
__ASM("	ld	bc,32       		");
__ASM("	ldirw	(xde+),(xhl+)		");
__ASM("	ld	xde,_SCR2PAL		");
__ASM("	ld	bc,32       		");
__ASM("	ldirw	(xde+),(xhl+)		");

__ASM("hc_wait_14:			");
__ASM("	ld	c,(rRASV)		");
__ASM("	and	c,7			");
__ASM("	jr	nz,hc_wait_14		");

__ASM("	ld	xde,_TILERAM+20*16	");
__ASM("	ld	bc,8*20			");
__ASM("	ldirw	(xde+),(xhl+)		");
__ASM("	ld	xde,_SCR1PAL+64		");
__ASM("	ld	bc,32			");
__ASM("	ldirw	(xde+),(xhl+)		");
__ASM("	ld	xde,_SCR2PAL+64		");
__ASM("	ld	bc,32			");
__ASM("	ldirw	(xde+),(xhl+)		");

__ASM("hc_wait_15:			");
__ASM("	ld	c,(rRASV)		");
__ASM("	and	c,7			");
__ASM("	jr	nz,hc_wait_15		");

__ASM("	ld	xde,_TILERAM		");
__ASM("	ld	bc,8*20     		");
__ASM("	ldirw	(xde+),(xhl+)		");
__ASM("	ld	xde,_SCR1PAL		");
__ASM("	ld	bc,32       		");
__ASM("	ldirw	(xde+),(xhl+)		");
__ASM("	ld	xde,_SCR2PAL		");
__ASM("	ld	bc,32       		");
__ASM("	ldirw	(xde+),(xhl+)		");

__ASM("hc_wait_16:			");
__ASM("	ld	c,(rRASV)		");
__ASM("	and	c,7			");
__ASM("	jr	nz,hc_wait_16		");

__ASM("	ld	xde,_TILERAM+20*16	");
__ASM("	ld	bc,8*20			");
__ASM("	ldirw	(xde+),(xhl+)		");
__ASM("	ld	xde,_SCR1PAL+64		");
__ASM("	ld	bc,32			");
__ASM("	ldirw	(xde+),(xhl+)		");
__ASM("	ld	xde,_SCR2PAL+64		");
__ASM("	ld	bc,32			");
__ASM("	ldirw	(xde+),(xhl+)		");

__ASM("hc_wait_17:			");
__ASM("	ld	c,(rRASV)		");
__ASM("	and	c,7			");
__ASM("	jr	nz,hc_wait_17		");

__ASM("	ld	xde,_TILERAM		");
__ASM("	ld	bc,8*20     		");
__ASM("	ldirw	(xde+),(xhl+)		");
__ASM("	ld	xde,_SCR1PAL		");
__ASM("	ld	bc,32       		");
__ASM("	ldirw	(xde+),(xhl+)		");
__ASM("	ld	xde,_SCR2PAL		");
__ASM("	ld	bc,32       		");
__ASM("	ldirw	(xde+),(xhl+)		");
#endif
}

/************************************
 * Show HiColor picture on real HW
 */

void hc_showHW(void *img)
{
#ifndef WIN32
__ASM("	ld	xhl,(xsp+4)		");
__ASM("	or	xhl,xhl			");
__ASM("	ret	z			");

__ASM("	add	xhl,DYN			");

__ASM("	ld	xde,_TILERAM		");
__ASM("	ld	bc,8*20			");
__ASM("	ldirw	(xde+),(xhl+)		");
__ASM("	ld	xde,_SCR1PAL		");
__ASM("	ld	bc,32			");
__ASM("	ldirw	(xde+),(xhl+)		");
__ASM("	ld	xde,_SCR2PAL		");
__ASM("	ld	bc,32			");
__ASM("	ldirw	(xde+),(xhl+)		");

__ASM("hc_wait_0_hw:			");
__ASM("	ld	c,(rRASV)		");
__ASM("	or	c,c			");
__ASM("	jr	nz,hc_wait_0_hw		");

__ASM("	ld	xde,_TILERAM+20*16	");
__ASM("	ld	bc,8*20			");
__ASM("	ldirw	(xde+),(xhl+)		");
__ASM("	ld	xde,_SCR1PAL+64		");
__ASM("	ld	bc,32			");
__ASM("	ldirw	(xde+),(xhl+)		");
__ASM("	ld	xde,_SCR2PAL+64		");
__ASM("	ld	bc,32			");
__ASM("	ldirw	(xde+),(xhl+)		");

__ASM("	ld	xde,_TILERAM		");
__ASM("	ld	bc,8*20     		");
__ASM("	ldirw	(xde+),(xhl+)		");
__ASM("	ld	xde,_SCR1PAL		");
__ASM("	ld	bc,32       		");
__ASM("	ldirw	(xde+),(xhl+)		");
__ASM("	ld	xde,_SCR2PAL		");
__ASM("	ld	bc,32       		");
__ASM("	ldirw	(xde+),(xhl+)		");

__ASM("	ld	xde,_TILERAM+20*16	");
__ASM("	ld	bc,8*20			");
__ASM("	ldirw	(xde+),(xhl+)		");
__ASM("	ld	xde,_SCR1PAL+64		");
__ASM("	ld	bc,32			");
__ASM("	ldirw	(xde+),(xhl+)		");
__ASM("	ld	xde,_SCR2PAL+64		");
__ASM("	ld	bc,32			");
__ASM("	ldirw	(xde+),(xhl+)		");

__ASM("	ld	xde,_TILERAM		");
__ASM("	ld	bc,8*20     		");
__ASM("	ldirw	(xde+),(xhl+)		");
__ASM("	ld	xde,_SCR1PAL		");
__ASM("	ld	bc,32       		");
__ASM("	ldirw	(xde+),(xhl+)		");
__ASM("	ld	xde,_SCR2PAL		");
__ASM("	ld	bc,32       		");
__ASM("	ldirw	(xde+),(xhl+)		");

__ASM("	ld	xde,_TILERAM+20*16	");
__ASM("	ld	bc,8*20			");
__ASM("	ldirw	(xde+),(xhl+)		");
__ASM("	ld	xde,_SCR1PAL+64		");
__ASM("	ld	bc,32			");
__ASM("	ldirw	(xde+),(xhl+)		");
__ASM("	ld	xde,_SCR2PAL+64		");
__ASM("	ld	bc,32			");
__ASM("	ldirw	(xde+),(xhl+)		");

__ASM("	ld	xde,_TILERAM		");
__ASM("	ld	bc,8*20     		");
__ASM("	ldirw	(xde+),(xhl+)		");
__ASM("	ld	xde,_SCR1PAL		");
__ASM("	ld	bc,32       		");
__ASM("	ldirw	(xde+),(xhl+)		");
__ASM("	ld	xde,_SCR2PAL		");
__ASM("	ld	bc,32       		");
__ASM("	ldirw	(xde+),(xhl+)		");

__ASM("	ld	xde,_TILERAM+20*16	");
__ASM("	ld	bc,8*20			");
__ASM("	ldirw	(xde+),(xhl+)		");
__ASM("	ld	xde,_SCR1PAL+64		");
__ASM("	ld	bc,32			");
__ASM("	ldirw	(xde+),(xhl+)		");
__ASM("	ld	xde,_SCR2PAL+64		");
__ASM("	ld	bc,32			");
__ASM("	ldirw	(xde+),(xhl+)		");

__ASM("	ld	xde,_TILERAM		");
__ASM("	ld	bc,8*20     		");
__ASM("	ldirw	(xde+),(xhl+)		");
__ASM("	ld	xde,_SCR1PAL		");
__ASM("	ld	bc,32       		");
__ASM("	ldirw	(xde+),(xhl+)		");
__ASM("	ld	xde,_SCR2PAL		");
__ASM("	ld	bc,32       		");
__ASM("	ldirw	(xde+),(xhl+)		");

__ASM("	ld	xde,_TILERAM+20*16	");
__ASM("	ld	bc,8*20			");
__ASM("	ldirw	(xde+),(xhl+)		");
__ASM("	ld	xde,_SCR1PAL+64		");
__ASM("	ld	bc,32			");
__ASM("	ldirw	(xde+),(xhl+)		");
__ASM("	ld	xde,_SCR2PAL+64		");
__ASM("	ld	bc,32			");
__ASM("	ldirw	(xde+),(xhl+)		");

__ASM("	ld	xde,_TILERAM		");
__ASM("	ld	bc,8*20     		");
__ASM("	ldirw	(xde+),(xhl+)		");
__ASM("	ld	xde,_SCR1PAL		");
__ASM("	ld	bc,32       		");
__ASM("	ldirw	(xde+),(xhl+)		");
__ASM("	ld	xde,_SCR2PAL		");
__ASM("	ld	bc,32       		");
__ASM("	ldirw	(xde+),(xhl+)		");

__ASM("	ld	xde,_TILERAM+20*16	");
__ASM("	ld	bc,8*20			");
__ASM("	ldirw	(xde+),(xhl+)		");
__ASM("	ld	xde,_SCR1PAL+64		");
__ASM("	ld	bc,32			");
__ASM("	ldirw	(xde+),(xhl+)		");
__ASM("	ld	xde,_SCR2PAL+64		");
__ASM("	ld	bc,32			");
__ASM("	ldirw	(xde+),(xhl+)		");

__ASM("	ld	xde,_TILERAM		");
__ASM("	ld	bc,8*20     		");
__ASM("	ldirw	(xde+),(xhl+)		");
__ASM("	ld	xde,_SCR1PAL		");
__ASM("	ld	bc,32       		");
__ASM("	ldirw	(xde+),(xhl+)		");
__ASM("	ld	xde,_SCR2PAL		");
__ASM("	ld	bc,32       		");
__ASM("	ldirw	(xde+),(xhl+)		");

__ASM("	ld	xde,_TILERAM+20*16	");
__ASM("	ld	bc,8*20			");
__ASM("	ldirw	(xde+),(xhl+)		");
__ASM("	ld	xde,_SCR1PAL+64		");
__ASM("	ld	bc,32			");
__ASM("	ldirw	(xde+),(xhl+)		");
__ASM("	ld	xde,_SCR2PAL+64		");
__ASM("	ld	bc,32			");
__ASM("	ldirw	(xde+),(xhl+)		");

__ASM("	ld	xde,_TILERAM		");
__ASM("	ld	bc,8*20     		");
__ASM("	ldirw	(xde+),(xhl+)		");
__ASM("	ld	xde,_SCR1PAL		");
__ASM("	ld	bc,32       		");
__ASM("	ldirw	(xde+),(xhl+)		");
__ASM("	ld	xde,_SCR2PAL		");
__ASM("	ld	bc,32       		");
__ASM("	ldirw	(xde+),(xhl+)		");

__ASM("	ld	xde,_TILERAM+20*16	");
__ASM("	ld	bc,8*20			");
__ASM("	ldirw	(xde+),(xhl+)		");
__ASM("	ld	xde,_SCR1PAL+64		");
__ASM("	ld	bc,32			");
__ASM("	ldirw	(xde+),(xhl+)		");
__ASM("	ld	xde,_SCR2PAL+64		");
__ASM("	ld	bc,32			");
__ASM("	ldirw	(xde+),(xhl+)		");

__ASM("	ld	xde,_TILERAM		");
__ASM("	ld	bc,8*20     		");
__ASM("	ldirw	(xde+),(xhl+)		");
__ASM("	ld	xde,_SCR1PAL		");
__ASM("	ld	bc,32       		");
__ASM("	ldirw	(xde+),(xhl+)		");
__ASM("	ld	xde,_SCR2PAL		");
__ASM("	ld	bc,32       		");
__ASM("	ldirw	(xde+),(xhl+)		");

__ASM("	ld	xde,_TILERAM+20*16	");
__ASM("	ld	bc,8*20			");
__ASM("	ldirw	(xde+),(xhl+)		");
__ASM("	ld	xde,_SCR1PAL+64		");
__ASM("	ld	bc,32			");
__ASM("	ldirw	(xde+),(xhl+)		");
__ASM("	ld	xde,_SCR2PAL+64		");
__ASM("	ld	bc,32			");
__ASM("	ldirw	(xde+),(xhl+)		");

__ASM("	ld	xde,_TILERAM		");
__ASM("	ld	bc,8*20     		");
__ASM("	ldirw	(xde+),(xhl+)		");
__ASM("	ld	xde,_SCR1PAL		");
__ASM("	ld	bc,32       		");
__ASM("	ldirw	(xde+),(xhl+)		");
__ASM("	ld	xde,_SCR2PAL		");
__ASM("	ld	bc,32       		");
__ASM("	ldirw	(xde+),(xhl+)		");
#endif
}

volatile u8 in_emu;
volatile u8 detect_done;

void __interrupt detectVBL()
{
#ifndef WIN32
	WATCHDOG = WATCHDOG_CLEAR;
	if (USR_SHUTDOWN) { SysShutdown(); while (1); }
	__ASM("	ld	bc,1500			");
	__ASM("hc_detect:			");
	__ASM("	nop				");
	__ASM("	djnz	bc,hc_detect		");
	__ASM("	xor	b,b");
	__ASM("	ld	c,(rRASV)		");
	__ASM("	cp	bc,100			");
	__ASM("	jr	lt,hc_not_emu		");
	in_emu = 1;
	__ASM("hc_not_emu:			");
	detect_done = 0;
#endif
}

extern void __interrupt VBInterrupt();

void hc_detect()
{
#ifndef WIN32
	detect_done = 1;
	in_emu = 0;
	__ASM(" di");
	VBL_INT = detectVBL;
	__ASM(" ei");
	while (detect_done);
	__ASM(" di");
	VBL_INT = VBInterrupt;
	__ASM(" ei");
#endif
}

u8 hc_emu()
{
	return in_emu;
}
