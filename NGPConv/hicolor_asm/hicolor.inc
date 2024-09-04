	RAMDD	DYNPTR
	RAMDB	DETECT
	RAMDB	IN_EMU

	;****************************************
	; load HiColor picture
	; xwa = ptr to picture data (id,idx,dyn)
hc_load
	push	xhl
	push	xde
	push	xix
	push	xiy
	push	bc

	xor	xhl,xhl
	ld	(DYNPTR),xhl

	ld	xhl,(xwa)	; ID
	ld	xde,_TILERAM+40*16
	ld	bc,20*19*8
	ldirw	(xde+),(xhl+)

	ld	xhl,(xwa+8)	; IDX
	ld	xix,_SCR1RAM
	ld	xiy,_SCR2RAM
	REPT	19
	ld	xde,xix
	ld	bc,20
	ldirw	(xde+),(xhl+)
	add	xix,64
	ld	xde,xiy
	ld	bc,20
	ldirw	(xde+),(xhl+)
	add	xiy,64
	ENDM

	ld	xhl,(xwa+4)	; DYN
	ld	(DYNPTR),xhl

	pop	bc
	pop	xiy
	pop	xix
	pop	xhl
	pop	xde
	ret

	;****************************************
	; Show HiColor picture on emu

hc_showEmu
	push	xhl
	push	xde
	push	bc

	ld	xhl,(DYNPTR)
	or	xhl,xhl
	jp	z,hc_nodata

	ld	xde,_TILERAM
	ld	bc,8*20
	ldirw	(xde+),(xhl+)
	ld	xde,_SCR1PAL
	ld	bc,32
	ldirw	(xde+),(xhl+)
	ld	xde,_SCR2PAL
	ld	bc,32
	ldirw	(xde+),(xhl+)

hc_wait_0
	ld	c,(rRASV)
	or	c,c
	jr	nz,hc_wait_0

	ld	xde,_TILERAM+20*16
	ld	bc,8*20
	ldirw	(xde+),(xhl+)
	ld	xde,_SCR1PAL+64
	ld	bc,32
	ldirw	(xde+),(xhl+)
	ld	xde,_SCR2PAL+64
	ld	bc,32
	ldirw	(xde+),(xhl+)

hc_wait_1
	ld	c,(rRASV)
	and	c,7
	jr	nz,hc_wait_1

	ld	xde,_TILERAM
	ld	bc,8*20
	ldirw	(xde+),(xhl+)
	ld	xde,_SCR1PAL
	ld	bc,32
	ldirw	(xde+),(xhl+)
	ld	xde,_SCR2PAL
	ld	bc,32
	ldirw	(xde+),(xhl+)

hc_wait_2
	ld	c,(rRASV)
	and	c,7
	jr	nz,hc_wait_2

	ld	xde,_TILERAM+20*16
	ld	bc,8*20
	ldirw	(xde+),(xhl+)
	ld	xde,_SCR1PAL+64
	ld	bc,32
	ldirw	(xde+),(xhl+)
	ld	xde,_SCR2PAL+64
	ld	bc,32
	ldirw	(xde+),(xhl+)

hc_wait_3
	ld	c,(rRASV)
	and	c,7
	jr	nz,hc_wait_3

	ld	xde,_TILERAM
	ld	bc,8*20
	ldirw	(xde+),(xhl+)
	ld	xde,_SCR1PAL
	ld	bc,32
	ldirw	(xde+),(xhl+)
	ld	xde,_SCR2PAL
	ld	bc,32
	ldirw	(xde+),(xhl+)

hc_wait_4
	ld	c,(rRASV)
	and	c,7
	jr	nz,hc_wait_4

	ld	xde,_TILERAM+20*16
	ld	bc,8*20
	ldirw	(xde+),(xhl+)
	ld	xde,_SCR1PAL+64
	ld	bc,32
	ldirw	(xde+),(xhl+)
	ld	xde,_SCR2PAL+64
	ld	bc,32
	ldirw	(xde+),(xhl+)

hc_wait_5
	ld	c,(rRASV)
	and	c,7
	jr	nz,hc_wait_5

	ld	xde,_TILERAM
	ld	bc,8*20
	ldirw	(xde+),(xhl+)
	ld	xde,_SCR1PAL
	ld	bc,32
	ldirw	(xde+),(xhl+)
	ld	xde,_SCR2PAL
	ld	bc,32
	ldirw	(xde+),(xhl+)

hc_wait_6
	ld	c,(rRASV)
	and	c,7
	jr	nz,hc_wait_6

	ld	xde,_TILERAM+20*16
	ld	bc,8*20
	ldirw	(xde+),(xhl+)
	ld	xde,_SCR1PAL+64
	ld	bc,32
	ldirw	(xde+),(xhl+)
	ld	xde,_SCR2PAL+64
	ld	bc,32
	ldirw	(xde+),(xhl+)

hc_wait_7
	ld	c,(rRASV)
	and	c,7
	jr	nz,hc_wait_7

	ld	xde,_TILERAM
	ld	bc,8*20
	ldirw	(xde+),(xhl+)
	ld	xde,_SCR1PAL
	ld	bc,32
	ldirw	(xde+),(xhl+)
	ld	xde,_SCR2PAL
	ld	bc,32
	ldirw	(xde+),(xhl+)

hc_wait_8
	ld	c,(rRASV)
	and	c,7
	jr	nz,hc_wait_8

	ld	xde,_TILERAM+20*16
	ld	bc,8*20
	ldirw	(xde+),(xhl+)
	ld	xde,_SCR1PAL+64
	ld	bc,32
	ldirw	(xde+),(xhl+)
	ld	xde,_SCR2PAL+64
	ld	bc,32
	ldirw	(xde+),(xhl+)

hc_wait_9
	ld	c,(rRASV)
	and	c,7
	jr	nz,hc_wait_9

	ld	xde,_TILERAM
	ld	bc,8*20
	ldirw	(xde+),(xhl+)
	ld	xde,_SCR1PAL
	ld	bc,32
	ldirw	(xde+),(xhl+)
	ld	xde,_SCR2PAL
	ld	bc,32
	ldirw	(xde+),(xhl+)

hc_wait_10
	ld	c,(rRASV)
	and	c,7
	jr	nz,hc_wait_10

	ld	xde,_TILERAM+20*16
	ld	bc,8*20
	ldirw	(xde+),(xhl+)
	ld	xde,_SCR1PAL+64
	ld	bc,32
	ldirw	(xde+),(xhl+)
	ld	xde,_SCR2PAL+64
	ld	bc,32
	ldirw	(xde+),(xhl+)

hc_wait_11
	ld	c,(rRASV)
	and	c,7
	jr	nz,hc_wait_11

	ld	xde,_TILERAM
	ld	bc,8*20
	ldirw	(xde+),(xhl+)
	ld	xde,_SCR1PAL
	ld	bc,32
	ldirw	(xde+),(xhl+)
	ld	xde,_SCR2PAL
	ld	bc,32
	ldirw	(xde+),(xhl+)

hc_wait_12
	ld	c,(rRASV)
	and	c,7
	jr	nz,hc_wait_12

	ld	xde,_TILERAM+20*16
	ld	bc,8*20
	ldirw	(xde+),(xhl+)
	ld	xde,_SCR1PAL+64
	ld	bc,32
	ldirw	(xde+),(xhl+)
	ld	xde,_SCR2PAL+64
	ld	bc,32
	ldirw	(xde+),(xhl+)

hc_wait_13
	ld	c,(rRASV)
	and	c,7
	jr	nz,hc_wait_13

	ld	xde,_TILERAM
	ld	bc,8*20
	ldirw	(xde+),(xhl+)
	ld	xde,_SCR1PAL
	ld	bc,32
	ldirw	(xde+),(xhl+)
	ld	xde,_SCR2PAL
	ld	bc,32
	ldirw	(xde+),(xhl+)

hc_wait_14
	ld	c,(rRASV)
	and	c,7
	jr	nz,hc_wait_14

	ld	xde,_TILERAM+20*16
	ld	bc,8*20
	ldirw	(xde+),(xhl+)
	ld	xde,_SCR1PAL+64
	ld	bc,32
	ldirw	(xde+),(xhl+)
	ld	xde,_SCR2PAL+64
	ld	bc,32
	ldirw	(xde+),(xhl+)

hc_wait_15
	ld	c,(rRASV)
	and	c,7
	jr	nz,hc_wait_15

	ld	xde,_TILERAM
	ld	bc,8*20
	ldirw	(xde+),(xhl+)
	ld	xde,_SCR1PAL
	ld	bc,32
	ldirw	(xde+),(xhl+)
	ld	xde,_SCR2PAL
	ld	bc,32
	ldirw	(xde+),(xhl+)

hc_wait_16
	ld	c,(rRASV)
	and	c,7
	jr	nz,hc_wait_16

	ld	xde,_TILERAM+20*16
	ld	bc,8*20
	ldirw	(xde+),(xhl+)
	ld	xde,_SCR1PAL+64
	ld	bc,32
	ldirw	(xde+),(xhl+)
	ld	xde,_SCR2PAL+64
	ld	bc,32
	ldirw	(xde+),(xhl+)

hc_wait_17
	ld	c,(rRASV)
	and	c,7
	jr	nz,hc_wait_17

	ld	xde,_TILERAM
	ld	bc,8*20
	ldirw	(xde+),(xhl+)
	ld	xde,_SCR1PAL
	ld	bc,32
	ldirw	(xde+),(xhl+)
	ld	xde,_SCR2PAL
	ld	bc,32
	ldirw	(xde+),(xhl+)

hc_nodata
	pop	bc
	pop	xde
	pop	xhl

	ret

	;****************************************
	; Show HiColor picture on real HW

hc_showHW
	push	xhl
	push	xde
	push	bc

	ld	xhl,(DYNPTR)
	or	xhl,xhl
	jp	z,hc_nodata_hw

	ld	xde,_TILERAM
	ld	bc,8*20
	ldirw	(xde+),(xhl+)
	ld	xde,_SCR1PAL
	ld	bc,32
	ldirw	(xde+),(xhl+)
	ld	xde,_SCR2PAL
	ld	bc,32
	ldirw	(xde+),(xhl+)

hc_wait_0_hw
	ld	c,(rRASV)
	or	c,c
	jr	nz,hc_wait_0_hw

	ld	xde,_TILERAM+20*16
	ld	bc,8*20
	ldirw	(xde+),(xhl+)
	ld	xde,_SCR1PAL+64
	ld	bc,32
	ldirw	(xde+),(xhl+)
	ld	xde,_SCR2PAL+64
	ld	bc,32
	ldirw	(xde+),(xhl+)

	ld	xde,_TILERAM
	ld	bc,8*20
	ldirw	(xde+),(xhl+)
	ld	xde,_SCR1PAL
	ld	bc,32
	ldirw	(xde+),(xhl+)
	ld	xde,_SCR2PAL
	ld	bc,32
	ldirw	(xde+),(xhl+)

	ld	xde,_TILERAM+20*16
	ld	bc,8*20
	ldirw	(xde+),(xhl+)
	ld	xde,_SCR1PAL+64
	ld	bc,32
	ldirw	(xde+),(xhl+)
	ld	xde,_SCR2PAL+64
	ld	bc,32
	ldirw	(xde+),(xhl+)

	ld	xde,_TILERAM
	ld	bc,8*20
	ldirw	(xde+),(xhl+)
	ld	xde,_SCR1PAL
	ld	bc,32
	ldirw	(xde+),(xhl+)
	ld	xde,_SCR2PAL
	ld	bc,32
	ldirw	(xde+),(xhl+)

	ld	xde,_TILERAM+20*16
	ld	bc,8*20
	ldirw	(xde+),(xhl+)
	ld	xde,_SCR1PAL+64
	ld	bc,32
	ldirw	(xde+),(xhl+)
	ld	xde,_SCR2PAL+64
	ld	bc,32
	ldirw	(xde+),(xhl+)

	ld	xde,_TILERAM
	ld	bc,8*20
	ldirw	(xde+),(xhl+)
	ld	xde,_SCR1PAL
	ld	bc,32
	ldirw	(xde+),(xhl+)
	ld	xde,_SCR2PAL
	ld	bc,32
	ldirw	(xde+),(xhl+)

	ld	xde,_TILERAM+20*16
	ld	bc,8*20
	ldirw	(xde+),(xhl+)
	ld	xde,_SCR1PAL+64
	ld	bc,32
	ldirw	(xde+),(xhl+)
	ld	xde,_SCR2PAL+64
	ld	bc,32
	ldirw	(xde+),(xhl+)

	ld	xde,_TILERAM
	ld	bc,8*20
	ldirw	(xde+),(xhl+)
	ld	xde,_SCR1PAL
	ld	bc,32
	ldirw	(xde+),(xhl+)
	ld	xde,_SCR2PAL
	ld	bc,32
	ldirw	(xde+),(xhl+)

	ld	xde,_TILERAM+20*16
	ld	bc,8*20
	ldirw	(xde+),(xhl+)
	ld	xde,_SCR1PAL+64
	ld	bc,32
	ldirw	(xde+),(xhl+)
	ld	xde,_SCR2PAL+64
	ld	bc,32
	ldirw	(xde+),(xhl+)

	ld	xde,_TILERAM
	ld	bc,8*20
	ldirw	(xde+),(xhl+)
	ld	xde,_SCR1PAL
	ld	bc,32
	ldirw	(xde+),(xhl+)
	ld	xde,_SCR2PAL
	ld	bc,32
	ldirw	(xde+),(xhl+)

	ld	xde,_TILERAM+20*16
	ld	bc,8*20
	ldirw	(xde+),(xhl+)
	ld	xde,_SCR1PAL+64
	ld	bc,32
	ldirw	(xde+),(xhl+)
	ld	xde,_SCR2PAL+64
	ld	bc,32
	ldirw	(xde+),(xhl+)

	ld	xde,_TILERAM
	ld	bc,8*20
	ldirw	(xde+),(xhl+)
	ld	xde,_SCR1PAL
	ld	bc,32
	ldirw	(xde+),(xhl+)
	ld	xde,_SCR2PAL
	ld	bc,32
	ldirw	(xde+),(xhl+)

	ld	xde,_TILERAM+20*16
	ld	bc,8*20
	ldirw	(xde+),(xhl+)
	ld	xde,_SCR1PAL+64
	ld	bc,32
	ldirw	(xde+),(xhl+)
	ld	xde,_SCR2PAL+64
	ld	bc,32
	ldirw	(xde+),(xhl+)

	ld	xde,_TILERAM
	ld	bc,8*20
	ldirw	(xde+),(xhl+)
	ld	xde,_SCR1PAL
	ld	bc,32
	ldirw	(xde+),(xhl+)
	ld	xde,_SCR2PAL
	ld	bc,32
	ldirw	(xde+),(xhl+)

	ld	xde,_TILERAM+20*16
	ld	bc,8*20
	ldirw	(xde+),(xhl+)
	ld	xde,_SCR1PAL+64
	ld	bc,32
	ldirw	(xde+),(xhl+)
	ld	xde,_SCR2PAL+64
	ld	bc,32
	ldirw	(xde+),(xhl+)

	ld	xde,_TILERAM
	ld	bc,8*20
	ldirw	(xde+),(xhl+)
	ld	xde,_SCR1PAL
	ld	bc,32
	ldirw	(xde+),(xhl+)
	ld	xde,_SCR2PAL
	ld	bc,32
	ldirw	(xde+),(xhl+)

	ld	xde,_TILERAM+20*16
	ld	bc,8*20
	ldirw	(xde+),(xhl+)
	ld	xde,_SCR1PAL+64
	ld	bc,32
	ldirw	(xde+),(xhl+)
	ld	xde,_SCR2PAL+64
	ld	bc,32
	ldirw	(xde+),(xhl+)

	ld	xde,_TILERAM
	ld	bc,8*20
	ldirw	(xde+),(xhl+)
	ld	xde,_SCR1PAL
	ld	bc,32
	ldirw	(xde+),(xhl+)
	ld	xde,_SCR2PAL
	ld	bc,32
	ldirw	(xde+),(xhl+)

hc_nodata_hw
	pop	bc
	pop	xde
	pop	xhl

	ret

	;****************************************
	; EMU detection VBL

hc_vbl_detect
	push	bc
	ld	bc,1500
hc_detect_0
	nop
	djnz	bc,hc_detect_0
	xor	b,b
	ld	c,(rRASV)
	cp	bc,100
	jr	lt,hc_not_emu
	ldb	(IN_EMU),1
hc_not_emu
	ldb	(DETECT),1
	pop	bc
	reti

	;****************************************
	; EMU detection routine

hc_detect
	push	xwa
	push	xbc
	ld	xwa,hc_vbl_detect
	ldb	(DETECT),0
	ldb	(IN_EMU),0
	di
	ld	xbc,(06fcch)
	ld	(06fcch),xwa
	ei
hc_detect_wait
	ld	c,(DETECT)
	or	c,c
	jr	z,hc_detect_wait
	di
	ld	(06fcch),xbc
	ei
	pop	xbc
	pop	xwa
	ret
