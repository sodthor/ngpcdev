MAX_NBOBJ	EQU	32
MAX_NBV		EQU	200
MAX_NBT		EQU	200
ELT_SIZE	EQU	55

ZMIN		EQU	127
ZMAX		EQU	63

		; 3D Engine vars
		RAMDW	COLOR
		RAMDD	OFFSET
		RAMDD	PALDST
		RAMBUF	BACKBUF,4096
		RAMBUF	LINE1,128
		RAMBUF	LINE2,128
		RAMBUF	TRANS,MAX_NBT*3*2
		RAMDB	NB_VIS
		RAMBUF	VIS,MAX_NBT*2
		RAMBUF	ZBUF,MAX_NBT*2
		RAMBUF	SORT,MAX_NBT
		RAMBUF	VSTACK,MAX_NBOBJ*4	; transformed scene vertices stack
		RAMBUF	TSTACK,MAX_NBOBJ*4	; scene triangles stack
		RAMDB	TMPB
		RAMDD	TMPD
		RAMDB	FLIP
		RAMBUF	SAVE_TMP,6
		RAMDD	SCENEPTR
		RAMBUF	MAT0,32
		RAMBUF	MAT1,32
		RAMDB	NB_OBJ
		RAMDW	INCX
		RAMDW	INCY
		; 3D Scene def
		RAMBUF	SCENE,ELT_SIZE*MAX_NBOBJ	; nbv(w),nbt(w),v(d),t(d),n(d),rotx,roty,rotz,scale,mat(32)

;************************
; init_3D
; Init palettes & buffers
; and center used screen
;************************

init_3D
	ld	bc,16
	ld	xde,_SCR1PAL
	ld	xhl,_SCR2PAL
	xor	xwa,xwa
_erase_pal
	ld	(xhl+),xwa
	ld	(xhl+),xwa
	ld	(xde+),xwa
	ld	(xde+),xwa
	djnz	bc,_erase_pal

	ld	xhl,_SCR1RAM
	ld	xix,_SCR2RAM
	xor	wa,wa
	ld	de,0100h
	ld	b,16
	ld	xiy,002000200h
fill_rows
	ld	c,16
fill_cols
	ld	(xhl+),wa
	ld	(xix+),de
	inc	wa
	inc	de
	djnz	c,fill_cols
	ld	c,8
fill_cols2
	ld	(xhl+),xiy
	ld	(xix+),xiy
	djnz	c,fill_cols2
	djnz	b,fill_rows

_init
	calr	clear_buf
	xor	xwa,xwa
	ld	(OFFSET),xwa
	ld	xwa,_SCR1PAL
	ld	(PALDST),xwa
	ldb	(rS1SOX),-16
	ldb	(rS2SOX),-16
	ldb	(rS1SOY),-12
	ldb	(rS2SOY),-12
	orb	(rPF),080h	; scroll plane 2 to front
	ret

;*****************************
; compute_scene
; compute 3D trans & back buff
;*****************************

compute_scene
	;***************************
	; Hidden faces determination
	;***************************
	ld	xhl,TRANS
	ld	(TMPD),xhl
	ld	a,(NB_OBJ)
	ld	(TMPB),a
	ld	xhl,SCENE
compute_normals
	push	xhl
	call	rotmatrix

	ld	xiy,(xhl+12)	; normals
	ld	iz,(xhl+2)	; nbt
	ld	xix,xhl
	add	xix,23		; matrix
	ld	xhl,(TMPD)
	calr	transform
	ld	(TMPD),xhl

	pop	xhl
	add	xhl,ELT_SIZE
	decb	(TMPB)
	jr	nz,compute_normals

	xor	w,w
	ld	xiy,SCENE+2
	ld	xhl,TRANS+5	; z trans second byte for sign test
	ld	xix,VIS
	ld	d,(NB_OBJ)
	ld	(TMPB),d
	xor	bc,bc
compute_visibility
	ld	de,(xiy)	; nbt
	ld	xiz,(xiy+6)	; triangles
	add	xiz,3
	add	xiy,ELT_SIZE
_test_vis
	bit	6,(xiz)		; visibility bit on for triangle ? ok = visible
	jr	nz,_always_vis
	bit	7,(xhl)		; normal positive ? ok = visible
	jr	nz,_not_vis
_always_vis
	ld	(xix+),bc
	inc	w
_not_vis
	inc	4,xiz
	inc	4,bc
	inc	6,xhl
	djnz	de,_test_vis
	and	bc,0f800h
	add	bc,00800h
	decb	(TMPB)
	jr	nz,compute_visibility
	ld	(NB_VIS),w

	;***************************
	; Vertices transformation
	;***************************

	ld	xhl,TRANS
	ld	xwa,SCENE
	ld	xde,VSTACK
	ld	c,(NB_OBJ)
	ld	(TMPB),c
	ld	xbc,TSTACK
compute_vertices
	calr	transmatrix
	ld	xiy,(xwa+4)	; vertices
	ld	iz,(xwa)	; nbv
	ld	xix,xwa
	add	xix,23		; matrix
	ld	(xde+),xhl
	calr	transform
	ld	xix,(xwa+8)
	ld	(xbc+),xix
	add	xwa,ELT_SIZE
	decb	(TMPB)
	jr	nz,compute_vertices

	;***************************
	; Depth sort
	;***************************

	; fill zbuff
	ld	xiy,VIS
	ld	xiz,ZBUF
	ld	a,(NB_VIS)
	ld	(TMPB),a
_zbuf
	xor	xhl,xhl
	xor	xde,xde
	ld	de,(xiy+)
	xor	xwa,xwa
	ld	wa,de
	and	w,0f8h
	ld	a,w
	xor	w,w
	srl	1,a
	ld	xix,xwa
	add	xix,TSTACK
	and	de,007ffh
	add	xde,(xix)
	add	xwa,VSTACK
	xor	xbc,xbc
	ld	c,(xde+)	;point 1
	add	xbc,(xwa)
	ld	hl,(xbc+4)
	xor	xbc,xbc
	ld	c,(xde+)	;point 2
	add	xbc,(xwa)
	ld	bc,(xbc+4)
	add	hl,bc
	xor	xbc,xbc
	ld	c,(xde)		;point 3
	add	xbc,(xwa)
	add	hl,(xbc+4)
	ld	(xiz+),hl
	decb	(TMPB)
	jr	nz,_zbuf

	; sort
	ld	c,(NB_VIS)
	ld	xiy,SORT
_sort
	ld	de,07fffh	; max
	xor	xhl,xhl
	ld	hl,-1		; idx
	ld	xix,ZBUF
	ld	b,(NB_VIS)
	xor	xiz,xiz
	push	xix
_sort_0
	ld	wa,(xix)
	cp	de,wa
	jr	le,_sort_not_inf
	ld	de,wa
	ld	hl,iz
	add	sp,4
	push	xix
_sort_not_inf
	inc	2,iz
	inc	2,xix
	djnz	b,_sort_0

	add	xhl,VIS
	ld	iz,(xhl)
	ld	(xiy+),iz
	pop	xix
	ldw	(xix),07fffh
	djnz	c,_sort

	;***************************
	; Draw triangles
	;***************************

	ld	xix,SORT
_draw_all
	xor	xhl,xhl
	ld	hl,(xix+)
	xor	xwa,xwa
	ld	wa,hl
	and	w,0f8h
	ld	a,w
	xor	w,w
	srl	1,a
	ld	xde,xwa
	add	xwa,VSTACK
	ld	xiz,(xwa)
	add	xde,TSTACK
	ld	xiy,(xde)
	and	hl,007ffh
	add	xhl,xiy

	; point 1
	xor	xde,xde
	ld	e,(xhl+)
	add	xde,xiz
	ld	wa,(xde+)
	ld	bc,(xde+)
	ld	de,(xde)
	calr	perspective

	push	wa
	push	bc

	; point 2
	xor	xde,xde
	ld	e,(xhl+)
	add	xde,xiz
	ld	wa,(xde+)
	ld	bc,(xde+)
	ld	de,(xde)
	calr	perspective
	push	wa
	push	bc
	
	; point 3
	xor	xde,xde
	ld	e,(xhl+)
	add	xde,xiz
	ld	wa,(xde+)
	ld	bc,(xde+)
	ld	de,(xde)
	calr	perspective
	push	wa
	push	bc

	xor	wa,wa
	ld	a,(xhl+)
	ld	(COLOR),wa

	pop	xde
	pop	xbc
	pop	xwa

	calr	draw_triangle

	decb	(NB_VIS)
	jr	nz,_draw_all

	ret

;***************************************************
; flip_buf
; Fill Tileram with back buffer & flip scroll planes 
; Level 1
;***************************************************

flip_buf
	incf

	ld	xbc,2048
	ld	xde,_TILERAM
	ld	xwa,(OFFSET)
	add	xde,xwa
	xor	xwa,01000h
	ld	(OFFSET),xwa
	ld	xhl,BACKBUF
	ldirw	(xde+),(xhl+)

	calr	clear_buf

	decf

	ldb	(FLIP),1
_wait_raster
	cp	(FLIP),0
	jr	nz,_wait_raster

	ret

;*************************************
; clear_buf
; Clear back buffer
;*************************************

clear_buf
	ld	xhl,BACKBUF
	xor	xwa,xwa
	ld	bc,4096/32
_clear_buf
	ld	(xhl+),xwa
	ld	(xhl+),xwa
	ld	(xhl+),xwa
	ld	(xhl+),xwa
	ld	(xhl+),xwa
	ld	(xhl+),xwa
	ld	(xhl+),xwa
	ld	(xhl+),xwa
	djnz	bc,_clear_buf
	ret

;*************************
; VBLInt_3D
; Vertical blank interrupt
;*************************

VBLInt_3D
	; Update Watch Dog Timer to prevent CPU reset
	ld	(rWDCR),WD_CLR

	cpb	(FLIP),1
	jr	nz,vb_exit

	xorb	(rPF),080h	; switch scroll planes

	; flip palettes
	push	xwa
	push	xbc
	push	xde
	push	xhl
	ld	xwa,(PALDST)
	ld	xde,xwa
	xor	xwa,0180h
	ld	(PALDST),xwa

	ld	xhl,PALETTE_COL					
	ld	xbc,(xhl+)
	ld	(xde+),xbc
	ld	xbc,(xhl)
	ld	(xde),xbc

	xor	xbc,xbc
	ld	(xwa+),xbc
	ld	(xwa),xbc
	pop	xhl
	pop	xde
	pop	xbc
	pop	xwa

	ldb	(FLIP),0
vb_exit
	reti

;*************************************
; draw_line
; Level 1
;	xwa0 h	x0
;	xwa0 l	y0
;	xbc0 h	x1
;	xbc0 l	y1
;	xde0	pixel management routine
;*************************************

draw_line
	push	ix
	push	iy
	push	iz
	push	xwa
	push	xbc
	incf
	pop	iy	; y1
	pop	ix	; x1
	pop	bc	; y0
	pop	wa	; x0

	ld	hl,1
	sub	ix,wa	; dx
	bit	15,ix
	jr	z,_draw_line_1
	ld	hl,-1
	neg	ix
_draw_line_1
	ld	(INCX),hl
	ld	hl,1
	sub	iy,bc	; dy
	bit	15,iy
	jr	z,_draw_line_2
	ld	hl,-1
	neg	iy
_draw_line_2
	ld	(INCY),hl

	call	(xde0)

	cp	ix,iy	; dx > dy
	jr	lt,_draw_line_b_
	jr	nz,_draw_line_a_
	cp	ix,0
	jr	z,_draw_line_exit

_draw_line_a_
	ld	de,iy
	sll	1,de	; inc1
	ld	hl,iy
	sub	hl,ix
	sll	1,hl	; inc2
	ld	iz,de
	sub	iz,ix	; E
_draw_line_a
	bit	15,iz
	jr	z,_draw_line_a0
	add	iz,de
	jr	_draw_line_a1
_draw_line_a0
	add	bc,(INCY)
	add	iz,hl
_draw_line_a1
	add	wa,(INCX)
	call	(xde0)
	djnz	ix,_draw_line_a
_draw_line_exit
	decf
	pop	iz
	pop	iy
	pop	ix
	ret

_draw_line_b_
	ld	de,ix
	sll	1,de	; inc1
	ld	hl,ix
	sub	hl,iy
	sll	1,hl	; inc2
	ld	iz,de
	sub	iz,iy	; E
_draw_line_b
	bit	15,iz
	jr	z,_draw_line_b0
	add	iz,de
	jr	_draw_line_b1
_draw_line_b0
	add	wa,(INCX)
	add	iz,hl
_draw_line_b1
	add	bc,(INCY)
	call	(xde0)
	djnz	iy,_draw_line_b
	decf
	pop	iz
	pop	iy
	pop	ix
	ret

;*************************************
; draw_pixel
; Level 2
;	wa1	x
;	bc1	y
;*************************************

draw_pixel
	incf

	ld	wa,rwa1
	bit	7,w
	jr	z,_x_pos
	xor	w,w
	jr	_x_ok
_x_pos
	cp	wa,127
	jr	lt,_x_inf
	ld	w,127
	jr	_x_ok
_x_inf
	ld	w,a
_x_ok
	ld	bc,rbc1
	bit	7,b
	jr	z,_y_pos
	xor	a,a
	jr	_y_ok
_y_pos
	cp	bc,127
	jr	lt,_y_inf
	ld	a,127
	jr	_y_ok
_y_inf
	ld	a,c
_y_ok
	ld	xhl,0
	ld	xbc,0
	ld	l,w
	ld	c,w
	srl	3,c
	sll	4,c
	ld	b,a
	srl	3,b
	and	a,7
	sll	1,a
	ld	w,0
	add	bc,wa
	and	l,7
	sll	1,hl
	add	xhl,mask_pixel
	ld	hl,(xhl)
	xor	xde,xde
	ld	de,(COLOR)
	add	xde,set_pixel
	ld	wa,(xde)
	add	xbc,BACKBUF
	and	(xbc),hl
	xor	hl,0ffffh
	and	wa,hl
	or	(xbc),wa

	decf
	ret

;*************************************
; draw_hline
; Draw horizontal line
; Level 1
;	w0	x0
;	a0	x1
;	b0	y
;*************************************

draw_hline
	incf

	ld	wa,rwa0
	cp	w,a
	jr	le,_draw_hline
	ex	w,a
_draw_hline
	xor	xbc,xbc
	xor	xde,xde
	ld	c,rb0
	ld	d,c
	srl	3,d
	and	c,7
	sll	1,c
	add	de,bc
	xor	bc,bc
	ld	c,w
	and	c,0f8h
	sll	1,c
	add	de,bc

	xor	hl,hl
	ld	l,a
	and	l,0f8h
	sll	1,l

	add	xde,BACKBUF
	and	w,7
	and	a,7

	incf
	xor	xwa,xwa
	ld	xhl,xwa
	ld	wa,(COLOR)
	ld	hl,wa
	srl	3,hl
	and	hl,7
	sll	1,hl
	jr	z,_no_border

	push	xix
	push	xiy
	push	xiz
	ld	xix,set_pixel
	add	xix,xhl
	xor	xiy,xiy
	ld	iy,rwa1
	ld	iz,iy
	and	iy,0ff00h
	srl	7,iy
	and	iz,0ffh
	sll	1,iz
	add	xiy,mask_pixel
	add	xiz,mask_pixel

_no_border
	xor	bc,bc
	bit	7,a
	jr	z,_no_trans
	ld	bc,0cccch
	bit	0,rb0
	jr	z,_no_trans
	srl	2,bc
_no_trans
	and	a,7	; keep only main color
	sll	1,a
	add	xwa,set_pixel
	ld	wa,(xwa)
	bit	0,rb0
	jr	z,_no_dec
	rlc	2,wa
_no_dec
	decf

	sub	hl,bc
	jp	z,_same_addr

	; not same addr : loop from left to right

	ld	rde2,hl

	; draw left side
	xor	xhl,xhl
	ld	l,w
	sll	1,l
	add	xhl,mask_left
	ld	bc,(xhl)
	or	bc,rbc2
	ld	hl,(xde)
	and	hl,bc
	xor	bc,0ffffh
	and	bc,rwa2
	or	hl,bc
	cp	rhl2,0
	jr	z,_no_border_left

	ld	bc,(xiy)
	and	hl,bc
	xor	bc,0ffffh
	and	bc,(xix)
	or	hl,bc

_no_border_left
	ld	(xde),hl
	add	xde,16

	; draw middle
_draw_middle
	cp	rde2,16
	jr	le,_draw_right
	ld	bc,rbc2
	and	(xde),bc
	xor	bc,0ffffh
	and	bc,rwa2
	or	(xde),bc
	add	xde,16
	sub	rde2,16
	jr	_draw_middle

	; draw right side
_draw_right
	xor	xhl,xhl
	ld	l,a
	sll	1,l
	add	xhl,mask_right
	ld	bc,(xhl)
	or	bc,rbc2
	ld	hl,(xde)
	and	hl,bc
	xor	bc,0ffffh
	and	bc,rwa2
	or	hl,bc
	cp	rhl2,0
	jr	z,_no_border_right

	ld	bc,(xiz)
	and	hl,bc
	xor	bc,0ffffh
	and	bc,(xix)
	or	hl,bc

_no_border_right
	ld	(xde),hl

	cp	rhl2,0
	jr	z,_exit_hline
	pop	xiz
	pop	xiy
	pop	xix
_exit_hline
	decf
	ret

_same_addr
	xor	xhl,xhl
	ld	l,w
	sll	1,l
	add	xhl,mask_left
	ld	bc,(xhl)
	xor	xhl,xhl
	ld	l,a
	sll	1,l
	add	xhl,mask_right
	or	bc,(xhl)
	or	bc,rbc2
	ld	hl,(xde)
	and	hl,bc
	xor	bc,0ffffh
	and	bc,rwa2
	or	hl,bc
	cp	rhl2,0
	jr	z,_no_border_right

	ld	bc,(xiy)
	and	hl,bc
	xor	bc,0ffffh
	and	bc,(xix)
	or	hl,bc

	ld	bc,(xiz)
	and	hl,bc
	xor	bc,0ffffh
	and	bc,(xix)
	or	hl,bc

	jr	_no_border_right

set_pixel
	dw	00000h,05555h,0aaaah,0ffffh,06666h,07777h,0bbbbh,01111h
mask_pixel
	dw	03fffh,0cfffh,0f3ffh,0fcffh,0ff3fh,0ffcfh,0fff3h,0fffch
mask_left
	dw	00000h,0c000h,0f000h,0fc00h,0ff00h,0ffc0h,0fff0h,0fffch
mask_right
	dw	03fffh,00fffh,003ffh,000ffh,0003fh,0000fh,00003h,00000h


;*************************************
; draw_triangle
; Draw triangle
; Level 0
;	xwa0	x0y0
;	xbc0	x1y1
;	xde0	x2y2
;*************************************

draw_triangle
	push	xwa
	push	xbc
	push	xde
	push	xhl

	cp	wa,bc
	jr	le,_a_inf_c
	EXCHG	xwa,xbc
_a_inf_c
	cp	wa,de
	jr	le,_a_inf_e
	EXCHG	xwa,xde
_a_inf_e
	cp	bc,de
	jr	le,_c_inf_e
	EXCHG	xde,xbc
_c_inf_e
	; xwa : higher point
	; xbc : mid point
	; xde : lower point

	; fill triangle top
	incf
	ld	xwa,xwa0
	ld	xbc,xbc0
	ld	xde,xde0
	ld	xhl,SAVE_TMP
	srl	16,xwa
	ld	(xhl+),wa
	srl	16,xbc
	ld	(xhl+),bc
	srl	16,xde
	ld	(xhl),de
	cp	bc,de
	jr	gt,_inv_1
	jr	nz,_no_inv_1
	cp	bc,wa		; when bc==de
	jr	gt,_inv_1
_no_inv_1
	decf
	calr	_comp_line_left
	EXCHG	xbc,xde
	calr	_comp_line_right
	EXCHG	xbc,xde
	jr	_draw_first
_inv_1
	decf
	calr	_comp_line_right
	EXCHG	xbc,xde
	calr	_comp_line_left
	EXCHG	xbc,xde

_draw_first
	push	xwa
	push	xbc
	push	xde
	calr	_horiz_fill
	pop	xde
	pop	xbc
	pop	xwa

	; fill triangle bottom
	ld	xwa,xde
	incf
	ld	xhl,SAVE_TMP
	ld	de,(xhl+)
	ld	bc,(xhl+)
	ld	wa,(xhl)
	cp	bc,wa
	jr	le,_inv_2
_do_right
	decf
	calr	_comp_line_right
	jr	_draw_second
_inv_2
	jr	nz,_inv_3
	cp	bc,de
	jr	gt,_do_right
_inv_3	
	decf
	calr	_comp_line_left

_draw_second
	ex	wa,bc
	calr	_horiz_fill

	pop	xhl
	pop	xde
	pop	xbc
	pop	xwa
	ret

	;*******************
	; Fill triangle part
	;*******************
_horiz_fill
	bit	7,b
	jr	nz,_no_fill
	cp	wa,127
	jr	gt,_no_fill
	bit	7,w
	jr	z,_fill_a
	xor	a,a
_fill_a
	cp	bc,127
	jr	lt,_fill_b
	ld	c,127
_fill_b
	sub	c,a
	inc	c
	xor	xde,xde
	ld	e,a
	add	xde,LINE1
	xor	xhl,xhl
	ld	l,a
	add	xhl,LINE2
	ld	b,a
_fill_c
	ld	w,(xde+)
	ld	a,(xhl+)
	calr	draw_hline
	inc	b
	djnz	c,_fill_c
_no_fill
	ret

	;***********
	; Left line
	;***********
_comp_line_left
	push	xde
	push	xhl
	push	c
	ld	xde,LINE1
	ld	xhl,07f7f7f7fh
	ld	c,128/16
_fill_line1
	ld	(xde+),xhl
	ld	(xde+),xhl
	ld	(xde+),xhl
	ld	(xde+),xhl
	djnz	c,_fill_line1
	pop	c
	ld	xde,draw_left
	calr	draw_line
	pop	xhl
	pop	xde
	ret

	;************
	; Right line
	;************
_comp_line_right
	push	xde
	push	xhl
	push	c
	ld	xde,LINE2
	xor	xhl,xhl
	ld	c,128/16
_fill_line2
	ld	(xde+),xhl
	ld	(xde+),xhl
	ld	(xde+),xhl
	ld	(xde+),xhl
	djnz	c,_fill_line2
	pop	c
	ld	xde,draw_right
	calr	draw_line
	pop	xhl
	pop	xde
	ret

;**************************
; Draw left pixel on LINE1
;**************************
draw_left
	bit	7,b
	jr	nz,_end_draw_left_
	cp	bc,127
	jr	gt,_end_draw_left_

	incf
	ld	wa,rwa1
	bit	7,w
	jr	z,_draw_left_0
	xor	a,a
	jr	_draw_left_1
_draw_left_0
	cp	wa,127
	jr	le,_draw_left_1
	ld	a,127
_draw_left_1
	xor	xhl,xhl
	ld	hl,rbc1
	add	xhl,LINE1
	cp	a,(xhl)
	jr	ge,_end_draw_left
	ld	(xhl),a
_end_draw_left
	decf
_end_draw_left_
	ret

;***************************
; Draw right pixel on LINE2
;***************************

draw_right
	bit	7,b
	jr	nz,_end_draw_right_
	cp	bc,127
	jr	gt,_end_draw_right_

	incf
	ld	wa,rwa1
	bit	7,w
	jr	z,_draw_right_0
	xor	a,a
	jr	_draw_right_1
_draw_right_0
	cp	wa,127
	jr	le,_draw_right_1
	ld	a,127
_draw_right_1
	xor	xhl,xhl
	ld	hl,rbc1
	add	xhl,LINE2
	cp	a,(xhl)
	jr	le,_end_draw_right
	ld	(xhl),a
_end_draw_right
	decf
_end_draw_right_
	ret

;***************************
; add_object
; Add an object to the scene
; Level 0
;	xhl	object to add
;	xwa	scale,rotz,roty,rotx
;	xbc	unused,transz,transy,transx
;***************************

add_object
	push	xde
	push	xwa
	push	xhl
	push	xbc

	push	xbc
	xor	xde,xde
	ld	e,(NB_OBJ)
	ld	c,ELT_SIZE
	mul	de,c
	add	xde,SCENE

	ld	bc,8
	ldirw	(xde+),(xhl+)
	ld	(xde+),a	; rot x
	srl	8,xwa
	ld	(xde+),a	; rot y
	srl	8,xwa
	ld	(xde+),a	; rot z
	srl	8,xwa
	ld	(xde+),a	; scale

	pop	xwa
	ld	(xde+),a	; trans x
	srl	8,xwa
	ld	(xde+),a	; trans y
	srl	8,xwa
	ld	(xde+),a	; trans z
	ld	c,8
	xor	xwa,xwa
_clear_matrix
	ld	(xde+),xwa
	djnz	c,_clear_matrix
	incb	(NB_OBJ)

	pop	xbc
	pop	xhl
	pop	xwa
	pop	xde
	ret

;******************************
; rotmatrix
; Compute Rotation  Matrix
;	xhl	object in scene
;******************************

rotmatrix
	push	xwa
	push	xbc
	push	xde
	push	xhl
	push	xix
	push	xiy
	push	xiz

	ld	xde,xhl
	add	xde,23
	cpb	(xhl+19),0
	jr	z,_no_scale
	ld	xde,MAT1
_no_scale

	ld	xix,cosinus
	ld	xiy,sinus
	incf
	xor	xhl,xhl
	; cos/sin x
	ld	l,(xhl0+16)
	ld	xiz,xix
	add	xiz,xhl
	ld	w,(xiz)
	ld	xiz,xiy
	add	xiz,xhl
	ld	a,(xiz)
	; cos/sin y
	ld	l,(xhl0+17)
	ld	xiz,xix
	add	xiz,xhl
	ld	b,(xiz)
	ld	xiz,xiy
	add	xiz,xhl
	ld	c,(xiz)
	; cos/sin z
	ld	l,(xhl0+18)
	ld	xiz,xix
	add	xiz,xhl
	ld	d,(xiz)
	ld	xiz,xiy
	add	xiz,xhl
	ld	e,(xiz)
	decf

	;******************************
	; line 0 : cz*cy, cy*sz, -sy, 0
	;******************************

	ld	a,rd1
	muls	wa,rb1
	sra	BSIZE,wa
	ld	(xde+),wa

	ld	a,rb1
	muls	wa,re1
	sra	BSIZE,wa
	ld	(xde+),wa

	xor	a,a
	sub	a,rc1
	exts	wa
	ld	(xde+),wa

	ldw	(xde+),0

	;**************************************************
	; line 1 : sx*sy*cz-cx*sz, sx*sy*sz+cx*cz, sx*cy, 0
	;**************************************************

	ld	a,ra1
	muls	wa,rc1
	sra	BSIZE,wa
	muls	wa,rd1
	sra	BSIZE,wa
	ld	c,rw1
	muls	bc,re1
	sra	BSIZE,bc
	sub	wa,bc
	ld	(xde+),wa
	
	ld	a,ra1
	muls	wa,rc1
	sra	BSIZE,wa
	muls	wa,re1
	sra	BSIZE,wa
	ld	c,rw1
	muls	bc,rd1
	sra	BSIZE,bc
	add	wa,bc
	ld	(xde+),wa

	ld	a,ra1
	muls	wa,rb1
	sra	BSIZE,wa
	ld	(xde+),wa

	ldw	(xde+),0

	;***************************************************
	; line 2 : cx*sy*cz+sx*sz, cx*sy*sz-sx*cz, cx*cy, 0
	;***************************************************

	ld	a,rw1
	muls	wa,rc1
	sra	BSIZE,wa
	muls	wa,rd1
	sra	BSIZE,wa
	ld	c,ra1
	muls	bc,re1
	sra	BSIZE,bc
	add	wa,bc
	ld	(xde+),wa
	
	ld	a,rw1
	muls	wa,rc1
	sra	BSIZE,wa
	muls	wa,re1
	sra	BSIZE,wa
	ld	c,ra1
	muls	bc,rd1
	sra	BSIZE,bc
	sub	wa,bc
	ld	(xde+),wa

	ld	a,rw1
	muls	wa,rb1
	sra	BSIZE,wa
	ld	(xde+),wa

	xor	wa,wa
	ldw	(xde+),wa

	;***********************
	; line 3 : 0, 0, 0, 1
	;***********************

	ld	(xde+),wa
	ld	(xde+),wa
	ld	(xde+),wa
	ldw	(xde),MULT

	ld	a,(xhl+19)
	cp	a,0
	jr	z,_no_scale_bis
	xor	w,w
	xor	xbc,xbc
	ld	xde,MAT0
	ld	xiy,xde
	ld	(xde+),wa
	ld	(xde+),bc
	ld	(xde+),xbc
	ld	(xde+),bc
	ld	(xde+),wa
	ld	(xde+),xbc
	ld	(xde+),xbc
	ld	(xde+),wa
	ld	(xde+),bc
	ld	(xde+),xbc
	ld	(xde+),bc
	ldw	(xde),MULT
	ld	xix,MAT1
	ld	xiz,xhl
	add	xiz,23
	calr	composition
_no_scale_bis
	pop	xiz
	pop	xiy
	pop	xix
	pop	xhl
	pop	xde
	pop	xbc
	pop	xwa
	ret

;***************************
; transmatrix
; Add translation to the matrix
;	xhl	object in scene
;***************************

transmatrix
	push	hl
	push	b
	ld	b,MULT
	ld	l,(xwa+20)	; trans x
	muls	hl,b
	ld	(xwa+47),hl
	ld	l,(xwa+21)	; trans y
	muls	hl,b
	ld	(xwa+49),hl
	ld	l,(xwa+22)	; trans z
	muls	hl,b
	ld	(xwa+51),hl
	pop	b
	pop	hl
	ret

;*************************************
; transform
; Transform point with matrix
; Level 1
;	xix	transformation matrix
;	xiy	points to transform
;	xhl	destination buffer
;	iz	number of transformations
;*************************************
transform
	push	xiy
	push	iz
	incf
	ld	xhl,xhl0

_transform
	ld	a,(xiy)
	exts	wa
	ld	bc,(xix)
	muls	xbc,wa
	ld	de,bc

	ld	a,(xiy+1)
	exts	wa
	ld	bc,(xix+8)
	muls	xbc,wa
	add	de,bc

	ld	a,(xiy+2)
	exts	wa
	ld	bc,(xix+16)
	muls	xbc,wa
	add	de,bc

	add	de,(xix+24)

	sra	BSIZE,de
	ld	(xhl+),de
	
	ld	a,(xiy)
	exts	wa
	ld	bc,(xix+2)
	muls	xbc,wa
	ld	de,bc

	ld	a,(xiy+1)
	exts	wa
	ld	bc,(xix+10)
	muls	xbc,wa
	add	de,bc

	ld	a,(xiy+2)
	exts	wa
	ld	bc,(xix+18)
	muls	xbc,wa
	add	de,bc

	add	de,(xix+26)

	sra	BSIZE,de
	ld	(xhl+),de
	
	ld	a,(xiy+)
	exts	wa
	ld	bc,(xix+4)
	muls	xbc,wa
	ld	de,bc

	ld	a,(xiy+)
	exts	wa
	ld	bc,(xix+12)
	muls	xbc,wa
	add	de,bc

	ld	a,(xiy+)
	exts	wa
	ld	bc,(xix+20)
	muls	xbc,wa
	add	de,bc

	add	de,(xix+28)

	sra	BSIZE,de
	ld	(xhl+),de

	dec	iz
	cp	iz,0
	jp	nz,_transform

	decf
	pop	iz
	pop	xiy
	ld	xhl,xhl1
	ret

;*******************************
; perspective
; Compute perspective correction
;*******************************

perspective
	incf

	ld	bc,rde0
	add	bc,ZMIN
	ld	de,rwa0
	muls	xde,bc
	ld	hl,ZMIN+ZMAX
	divs	xde,hl
	add	de,64
	ld	rwa0,de

	ld	bc,rde0
	add	bc,ZMIN
	ld	de,rbc0
	muls	xde,bc
	divs	xde,hl
	add	de,64
	ld	rbc0,de

	decf
	ret

;*******************************
; composition
; Matrix composition
; Level 1
;	xix	matrix b
;	xiy	matrix a
;	xiz	matrix res = a*b
;*******************************
composition
	incf

	ld	w,4
_for_i
	dec	w
	ld	a,4
_for_j
	dec	a
	xor	xde,xde
	ld	e,w
	sll	2,e
	add	e,a
	sll	1,e
	add	xde,xiz
	ldw	(xde),0
	ld	c,4
_for_k
	dec	c
	push	xde
	xor	xhl,xhl
	xor	xde,xde
	ld	e,c
	sll	2,e
	add	e,a
	sll	1,e
	add	xde,xix
	ld	hl,(xde)

	xor	xde,xde
	ld	e,w
	sll	2,e
	add	e,c
	sll	1,e
	add	xde,xiy
	ld	de,(xde)
	muls	xhl,de
	sra	BSIZE,hl

	pop	xde
	add	(xde),hl

	or	c,c
	jr	nz,_for_k
	or	a,a
	jr	nz,_for_j
	or	w,w
	jr	nz,_for_i

	decf	
	ret

;*********
; includes
;*********

	include	"sincos.inc"
