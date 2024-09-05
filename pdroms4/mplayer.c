#include "ngpc.h"

#define FILL0 \
	__ASM("	ld	xde,_TILERAM		"); \
	__ASM("	ld	bc,8*20     		"); \
	__ASM("	ldirw	(xde+),(xhl+)	"); \
	__ASM("	ld	xde,_SCR1PAL		"); \
	__ASM("	ld	bc,32				"); \
	__ASM("	ldirw	(xde+),(xhl+)	"); \
	__ASM("	ld	xde,xwa				"); \
	__ASM("	add	xwa,64				"); \
	__ASM("	ld	bc,20				"); \
	__ASM("	ldirw	(xde+),(xhl+)	");

#define FILL1 \
	__ASM("	ld	xde,_TILERAM+20*16	"); \
	__ASM("	ld	bc,8*20           	"); \
	__ASM("	ldirw	(xde+),(xhl+)	"); \
	__ASM("	ld	xde,_SCR1PAL+64		"); \
	__ASM("	ld	bc,32				"); \
	__ASM("	ldirw	(xde+),(xhl+)	"); \
	__ASM("	ld	xde,xwa				"); \
	__ASM("	add	xwa,64				"); \
	__ASM("	ld	bc,20				"); \
	__ASM("	ldirw	(xde+),(xhl+)	"); \

#define WAIT_H0 \
	__ASM("	db	0xc1,0x09,0x80,0x23	"); \
	__ASM("	db	0xcb,0xcc,0x07		"); \
	__ASM("	db	0x6e,0xf7			");

#define WAIT_H1 \
	__ASM("	db	0xc1,0x09,0x80,0x23	"); \
	__ASM(" db	0xcb,0xcc,0x07		"); \
	__ASM("	db	0x66,0xf7			");

void mp_load(void *movie)
{
__ASM("	_SCR1RAM	equ	0x9000	");
__ASM("	_SCR2RAM	equ	0x9800	");
__ASM("	_TILERAM	equ	0xA000	");
__ASM("	_SCR1PAL	equ	0x8280	");
__ASM("	_SCR2PAL	equ	0x8300	");
__ASM("	rRASV		equ	0x8009	");

__ASM("	ld	xwa,(xsp+4)			");
__ASM("	ld	xhl,xwa				");
__ASM("	ld	xde,xhl				");
__ASM("	add	xde,8				");
__ASM("	ld	bc,4				");
__ASM("	ldirw	(xde+),(xhl+)	");
}

void mp_stop(void *movie)
{
__ASM("	ld	xwa,(xsp+4)			");
__ASM("	xor	xde,xde				");
__ASM("	ld	(xwa+8),xde			");
}

void mp_play_emu(void* movie)
{
__ASM("	ld	xix,(xsp+4)			");
__ASM("	ld	xhl,(xix+8)			");
__ASM("	or	xhl,xhl				");
__ASM("	jp	z,mp_nodata_emu		");

__ASM("	ld	xwa,_SCR1RAM		");

FILL0

__ASM("mp_wait_0:				");
__ASM("	ld	c,(rRASV)			");
__ASM("	or	c,c					");
__ASM("	jr	nz,mp_wait_0		");

//REPT	8

WAIT_H1
FILL1
WAIT_H0
WAIT_H1
FILL0
WAIT_H0

WAIT_H1
FILL1
WAIT_H0
WAIT_H1
FILL0
WAIT_H0

WAIT_H1
FILL1
WAIT_H0
WAIT_H1
FILL0
WAIT_H0

WAIT_H1
FILL1
WAIT_H0
WAIT_H1
FILL0
WAIT_H0

WAIT_H1
FILL1
WAIT_H0
WAIT_H1
FILL0
WAIT_H0

WAIT_H1
FILL1
WAIT_H0
WAIT_H1
FILL0
WAIT_H0

WAIT_H1
FILL1
WAIT_H0
WAIT_H1
FILL0
WAIT_H0

WAIT_H1
FILL1
WAIT_H0
WAIT_H1
FILL0
WAIT_H0

//ENDM

WAIT_H1
FILL1
WAIT_H0
WAIT_H1
FILL0

__ASM("	decw	1,(xix+14)		");

__ASM("	jr	nz,mp_nodata_emu	");

__ASM("	ld	bc,(xix+6)			");
__ASM("	ld	(xix+14),bc			");

__ASM("	decw	1,(xix+12)		");
__ASM("	jr	nz,mp_notend_emu	");

__ASM("	ld	xhl,(xix)			");
__ASM("	ld	bc,(xix+4)			");
__ASM("	ld	(xix+12),bc			");

__ASM("mp_notend_emu:			");
__ASM("	ld	(xix+8),xhl			");

__ASM("mp_nodata_emu:			");
}

void mp_play_hw(void* movie)
{
__ASM("	ld	xix,(xsp+4)			");
__ASM("	ld	xhl,(xix+8)			");
__ASM("	or	xhl,xhl				");
__ASM("	jp	z,mp_nodata_hw		");

__ASM("	ld	xwa,_SCR1RAM		");

FILL0

__ASM("mp_wait_0_hw:			");
__ASM("	ld	c,(rRASV)			");
__ASM("	or	c,c					");
__ASM("	jr	nz,mp_wait_0_hw		");

// REPT	8

FILL1
WAIT_H0
FILL0
WAIT_H0

FILL1
WAIT_H0
FILL0
WAIT_H0

FILL1
WAIT_H0
FILL0
WAIT_H0

FILL1
WAIT_H0
FILL0
WAIT_H0

FILL1
WAIT_H0
FILL0
WAIT_H0

FILL1
WAIT_H0
FILL0
WAIT_H0

FILL1
WAIT_H0
FILL0
WAIT_H0

FILL1
WAIT_H0
FILL0
WAIT_H0

// ENDM

FILL1
WAIT_H0
FILL0

__ASM("mp_end_frame_hw:			");
__ASM("	decw	1,(xix+14)		");
__ASM("	jr	nz,mp_nodata_hw		");

__ASM("	ld	bc,(xix+6)			");
__ASM("	ld	(xix+14),bc			");

__ASM("	decw	1,(xix+12)		");
__ASM("	jr	nz,mp_notend_hw		");

__ASM("	ld	xhl,(xix)			");
__ASM("	ld	bc,(xix+4)			");
__ASM("	ld	(xix+12),bc			");

__ASM("	mp_notend_hw:			");
__ASM("	ld	(xix+8),xhl			");

__ASM("	mp_nodata_hw:			");
}
