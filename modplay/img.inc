;**************************************
;* Show an image encoded with CodeImage
;* xwa : image ID
;* xbc : offset in scrollplane 64*Y+X*2
;**************************************

ShowImage
	push	xwa
	push	xbc
	push	xde
	push	xhl
	push	xix
	push	xiy

	push	xbc	; keep offset in stack
	ld	xix,_SCR1PAL
	calr	img_clearpal
	ld	xix,_SCR1RAM
	calr	img_clearplane
	pop	xix
	push	xix
	add	xix,_SCR1RAM
	ld	xiy,(xwa+12)
	ld	bc,(xwa)
	xor	hl,hl
	calr	img_settiles
	ld	xde,_SCR1PAL
	xor	xhl,xhl
	ld	(xde+),xhl
	ld	(xde+),xhl
	ld	xhl,(xwa+24)
	ld	bc,(xwa+20)
	sll	2,bc
	ldirw	(xde+),(xhl+)

	ld	xix,_SCR2PAL
	calr	img_clearpal
	ld	xix,_SCR2RAM
	calr	img_clearplane

	ld	xix,(xwa+8)
	or	xix,xix
	jr	z,img_nopal2

	pop	xix
	push	xix
	add	xix,_SCR2RAM
	ld	xiy,(xwa+16)
	ld	bc,(xwa)
	ld	hl,(xwa+2)
	srl	1,hl	
	calr	img_settiles
	ld	xde,_SCR2PAL
	xor	xhl,xhl
	ld	(xde+),xhl
	ld	(xde+),xhl
	ld	xhl,(xwa+28)
	ld	bc,(xwa+22)
	sll	2,bc
	ldirw	(xde+),(xhl+)

img_nopal2
	ld	xde,_TILERAM
	ld	xhl,(xwa+4)
	ld	bc,(xwa+2)
	sll	3,bc
	ldirw	(xde+),(xhl+)

	pop	xbc	; remove offset

	pop	xiy
	pop	xix
	pop	xhl
	pop	xde
	pop	xbc
	pop	xwa
	ret

img_clearpal
	push	xhl
	push	b
	xor	xhl,xhl
	ld	b,8
img_clearpals
	ld	(xix+),xhl
	djnz	b,img_clearpals
	pop	b
	pop	xhl
	ret

img_clearplane
	push	xhl
	push	bc
	xor	xhl,xhl
	ld	bc,(32*32)/2
img_clearplanes
	ld	(xix+),xhl
	djnz	bc,img_clearplanes
	pop	bc
	pop	xhl
	ret

img_settiles
	push	xix
	push	bc
img_cpcols
	ld	b,(xiy+)
	inc	1,b
	sll	1,b
	and	h,001h
	add	h,b
	ld	(xix+),hl
	inc	1,hl
	djnz	c,img_cpcols
	pop	bc
	pop	xix
	add	xix,64
	djnz	b,img_settiles
	ret
