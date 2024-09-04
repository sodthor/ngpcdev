;RUNFROMMENU EQU 1

; Tell the AS assembler we want to code for NGPC
;
	cpu	93C141
	maxmode	on
	include	"stddef96.inc"

	include	"hardware.inc"  ; Include hardware defines
	include	"system.inc"    ; Include System Call defines

;
; Define variables
;
HEIGHT		EQU	17
WIDTH		EQU	7
LVLADD		EQU	100
SIZE		EVAL	WIDTH*HEIGHT
DX		EQU	3
WALL		EQU	8	; white/background palette
MSGTIME		EQU	30
MAGIC_NB	EQU	0cafebabeh	; java rules !

SAVEOFFSET	EQU	01e0000h
BLOCK_NB	EQU	30

START_OF_RAM	EVAL	_MAINRAM

		RAMBUF	ARRAY,SIZE
		RAMBUF	MARK,SIZE

		RAMDD	ADDLVL
		RAMDD	NXTLVL
		RAMDD	BLOCKS
		RAMDD	SCORE
		RAMDD	STRLVL
		RAMDD	TXTSCORE
		RAMDD	TXTSCORE2
		RAMDB	VBCOUNTER	;used for frame rate
		RAMDB	CURX
		RAMDB	CURY
		RAMDB	AA
		RAMDB	BB
		RAMDB	CC
		RAMDB	NA
		RAMDB	NB
		RAMDB	NC
		RAMDB	MSG
		RAMDB	MSGCPT
		RAMDB	SPECIAL
		RAMDB	EXIST
		RAMDB	CPT
		RAMDB	STOP
		RAMDB	ANIM
		RAMDB	LVL
		RAMDB	NBWAIT
		RAMDB	SCRL
		RAMDB	SET
		RAMDB	RND
		RAMDB	CHAIN
		RAMDB	SPD
		RAMDB	READY
		
		RAMBUF	FADE0,128
		RAMBUF	FADE1,192
		RAMBUF	FADE2,192

		RAMDD	SAVESND
		
		RAMDD	MAGIC
		RAMDD	EASYNAME
		RAMDD	BESTEASY
		RAMDD	NORMNAME
		RAMDD	BESTNORM
		RAMDD	HARDNAME
		RAMDD	BESTHARD

START_OF_RAM	EVAL	MAGIC+256

   IFDEF RUNFROMMENU
HEADER_OFS      EQU     025a000H
   ELSE
HEADER_OFS      EQU     0200000H
   ENDIF


;
; Standard cartridge header
;
	org	HEADER_OFS
	db	" LICENSED BY SNK CORPORATION"	; 28 bytes license string
	dd	_start				; Program Counter
	dw	0				; Catalog number
	db	0				; Sub catalog number
	db	10h				; colour or b+w (10h = colour)
	;	 123456789012
    	db	"COLUMNS     "			; Game name (12 bytes)
	dd	0,0,0,0				; padding - reserve for future use

; User Interrupt Vectors

UserIntVect
	dd	nada		; Software Interrupt (SWI 3)
	dd	nada		; Software Interrupt (SWI 4)
	dd	nada		; Software Interrupt (SWI 5)
	dd	nada		; Software Interrupt (SWI 6)
	dd	nada		; RTC Alarm Interrupt
	dd	VBlankInt	; Vertical Blanking Interrupt
	dd	nada		; Interrupt from Z80
	dd	nada		; Timer Interrupt (8 bit timer 0)
	dd	nada		; Timer Interrupt (8 bit timer 1)
	dd	nada		; Timer Interrupt (8 bit timer 2)
	dd	nada		; Timer Interrupt (8 bit timer 3)
	dd	nada		; Serial Transmission Interrupt
	dd	nada		; Serial Reception Interrupt
	dd	nada		; (Reserved)
	dd	nada		; End Micro DMA Int (MicroDMA 0)
	dd	nada		; End Micro DMA Int (MicroDMA 1)
	dd	nada		; End Micro DMA Int (MicroDMA 2)
	dd	nada		; End Micro DMA Int (MicroDMA 3)
nada	reti

;************************************************
; *** Start of User Code ***

_start
	calr	OS_VERSION      ; Initialize NGP or NGPC mode
	set	6,(rUSERA)      ; User Answer

; Install User Interrupt Vectors

	lda	xix,(UserIntVect)
	lda	xiy,(rSWI3)
	ld	b,18
UIVloop
        ld	xwa,(xix+)
        ld	(xiy+),xwa
        djnz	b,UIVloop

;set up screen size 160x152
	ld	(08002h),0
	ld	(08003h),0
	ld	(08004h),0a0h
	ld	(08005h),98h

	call	initsound

; enable interrupts
	ei

	call	InitialiseMemory
	ld	xwa,64+4	; at (x=8,y=16)
	ld	xde,0
	ld	b,0		; no pal
	ld	xhl,img3	; skull
	call	drawpic
	call	fade

;seems to be BGR format?
	ldw	(_BGCPAL),0; 0311h	;BACKGROUND COLOUR
	ld	(8118h),80h

; copy internal charset to character RAM
	ld	ra3,3
	ld	rw3,VECT_SYSFONTSET
	calr	SYSTEM_CALL

; copy charset to character RAM	
; characters found in included file
	ld	bc,(ENDCHARSET - CHARSET)/2	;in this case 'first.inc'
	ld	xde,_TILERAM
	ld	xhl,CHARSET
	ldirw	(xde+),(xhl+)

	calr	InitialiseMemory

;*********
;Main loop
;*********

	;*****************
	; best scores init
	
	ld	xhl,0200000h+SAVEOFFSET
	ld	xwa,MAGIC_NB
	cp	xwa,(xhl)
	jr	nz,initscores
	ld	xde,MAGIC
	ld	xbc,14
	ldirw	(xde+),(xhl+)
	jr	_begin
initscores
	ld	(MAGIC),xwa
	ld	xwa,(COMPUTR)
	ld	(EASYNAME),xwa
	ld	(NORMNAME),xwa
	ld	(HARDNAME),xwa
	ld	xwa,1000
	ld	(BESTEASY),xwa
	ld	xwa,500
	ld	(BESTNORM),xwa
	ld	xwa,200
	ld	(BESTHARD),xwa

_begin
	call	clearscreen
	call	menu

	ld	xwa,MUSIC2
	ld	xbc,ENDMUSIC2
	call	initmusic
	call	startmusic

	;*****************
	
	ld	xwa,ARRAY
	ld	xbc,SIZE
	ld	l,0
cleararray
	ld	(xwa+),l
	djnz	bc,cleararray

	call	setpal

	ld	xwa,BACKDRAW
	call	drawback

	ld	xwa,0
	ld	(BLOCKS),xwa
	ld	(SCORE),xwa
	ld	(CHAIN),0
	ld	(STOP),0
	ld	(SET),0
	ld	(SPECIAL),0
	ld	(MSG),0
	ld	(MSGCPT),0
	ld	(READY),0
	ld	(NBWAIT),2
	ld	(SPD),2
	ld	xwa,LVLADD
	ld	(ADDLVL),xwa
	ld	(NXTLVL),xwa
_not3
	calr	_newrand
	ld	a,(NA)
	cp	a,(NB)
	jr	nz,_new
	cp	a,(NC)
	jr	nz,_new
	jr	_not3

_main
	cp	(STOP),0
	jp	z,_cont
	cp	(STOP),1
	jp	z,_set
	cp	(STOP),2
	jp	z,_remove
	cp	(STOP),3
	jp	z,_anim
	cp	(STOP),4
	jp	z,_scroll

	;******************
	; new falling piece
	;******************
_new
	ld	a,(NA)
	ld	(AA),a
	ld	a,(NB)
	ld	(BB),a
	ld	a,(NC)
	ld	(CC),a
	calr	_newrand
	ld	(CURX),WIDTH/2
	ld	(CURY),-24
	ld	(CPT),0
	ld	(STOP),0
	ld	(CHAIN),0
	jp	_cont
_newrand
	ld	xwa,(BLOCKS)
	cp	xwa,0
	jr	z,_nrand0
	and	xwa,03fh
	cp	xwa,0
	jr	nz,_nrand0
	cp	(SPECIAL),0
	jr	z,_special
_nrand0
	calr	random
	ld	(NA),a
	calr	random
	ld	(NB),a
	calr	random
	ld	(NC),a
	cp	(SPECIAL),0
	jr	z,_endrand
	inc	(SPECIAL)
_endrand
	ret
_special
	ld	(NA),WALL-1
	ld	(NB),WALL-1
	ld	(NC),WALL-1
	ld	(SPECIAL),1
	ld	(MSG),5
	ld	(MSGCPT),MSGTIME
	ret
random
	push	xbc
	push	xde
	ld	xwa,RND_LOOKUP
	ld	e,(LVL)
_rand
	inc	(RND)
	ld	xbc,0
	ld	c,(RND)
	add	xbc,xwa
	ld	d,(xbc)
	and	d,7
	add	d,1
	cp	d,e
	jr	gt,_rand
	ld	a,d
	pop	xde
	pop	xbc
	ret

	;************************
	; mark removable elements
	;************************
_remove
	ld	(ANIM),0
	calr	clearmark

	cp	(SPECIAL),2
	jr	nz,_scan

	ld	(SPECIAL),0
	ld	h,(CURX)
	ld	l,(CURY)
	srl	3,l
	inc	2,l
	cp	l,HEIGHT
	jr	z,_scan
	ld	xix,ARRAY
	ld	xiy,MARK
	call	GetXY
	ld	bc,SIZE
_replace
	ld	e,(xix+)
	cp	e,a
	jr	nz,_rep0
	ld	(xiy),1
_rep0
	inc	xiy
	djnz	bc,_replace

_scan:
	;***********
	; width scan
	;***********

	ld	xiy,ARRAY
	ld	xiz,MARK
        ld	l,HEIGHT
_mark0
	dec	l
	ld	h,WIDTH-1
_mark1
	ld	xix,xiy
	calr	GetXY
_mark2
	ld	b,a
	ld	c,0
_mark3
	inc	c
	dec	h
	cp	h,-1
	jr	z,_mark4b
	calr	GetXY
	cp	b,a
	jr	z,_mark3
_mark4
	cp	b,0
	jr	z,_mark2
_mark4b
	cp	b,0
	jr	z,_mark7
	cp	c,2
	jr	gt,_mark5
	cp	h,-1
	jr	nz,_mark2
	jr	_mark7
_mark5
	ld	(ANIM),9
	ld	b,h
	ld	xix,xiz
	ld	a,1
_mark6
	inc	h
	calr	SetXY
	djnz	c,_mark6
	ld	h,b
	cp	h,-1
	jr	nz,_mark1
_mark7
	cp	l,0
	jr	nz,_mark0

	;************
	; height scan
	;************

        ld	h,WIDTH
_marka
	dec	h
	ld	l,HEIGHT-1
_markb
	ld	xix,xiy
	calr	GetXY
_markc
	ld	b,a
	ld	c,0
_markd
	inc	c
	dec	l
	cp	l,-1
	jr	z,_markeb
	calr	GetXY
	cp	b,a
	jr	z,_markd
_marke
	cp	b,0
	jr	z,_markc
_markeb
	cp	b,0
	jr	z,_markh
	cp	c,2
	jr	gt,_markf
	cp	l,-1
	jr	nz,_markc
	jr	_markh
_markf
	ld	(ANIM),9
	ld	b,l
	ld	xix,xiz
	ld	a,1
_markg
	inc	l
	calr	SetXY
	djnz	c,_markg
	ld	l,b
	cp	l,-1
	jr	nz,_markb
_markh
	cp	h,0
	jr	nz,_marka

	;***************
	; diag left scan
	;***************

	ld	d,HEIGHT-1
	ld	e,2
_marki0
	push	de
_marki
	ld	l,d
	ld	h,e
_markj
	ld	xix,xiy
	calr	GetXY
	ld	b,a
	ld	c,1
_markk
	dec	l
	dec	h
	ld	xix,xiy
	calr	GetXY
	cp	b,a
	jr	nz,_markl
	inc	c
	cp	h,0
	jr	z,_markl
	cp	l,0
	jr	nz,_markk
_markl
	cp	c,2
	jr	le,_markm
	cp	b,0
	jr	z,_markm
	ld	xix,xiz
	ld	c,a
	ld	a,1
	ld	(ANIM),9
	push	hl
	cp	b,c
	jr	z,_marklc
_marklb
	inc	l
	inc	h
_marklc
	calr	SetXY
	cp	h,e
	jr	nz,_marklb
	pop	hl
_markm
	cp	h,0
	jr	z,_markn
	cp	l,0
	jr	z,_markn
	ld	e,h
	cp	e,1
	jr	z,_markn
	ld	d,l
	cp	d,1
	jr	nz,_marki
_markn
	pop	de
	inc	e
	cp	e,WIDTH
	jr	nz,_marki0
	dec	e
	dec	d
	cp	d,1
	jr	nz,_marki0

	;****************
	; diag right scan
	;****************

	ld	d,HEIGHT-1
	ld	e,WIDTH-3
_marko0
	push	de
_marko
	ld	l,d
	ld	h,e
_markp
	ld	xix,xiy
	calr	GetXY
	ld	b,a
	ld	c,1
_markq
	dec	l
	inc	h
	ld	xix,xiy
	calr	GetXY
	cp	b,a
	jr	nz,_markr
	inc	c
	cp	h,WIDTH-1
	jr	z,_markr
	cp	l,0
	jr	nz,_markq
_markr
	cp	c,2
	jr	le,_marks
	cp	b,0
	jr	z,_marks
	ld	xix,xiz
	ld	c,a
	ld	a,1
	ld	(ANIM),9
	push	hl
	cp	b,c
	jr	z,_markrc
_markrb
	inc	l
	dec	h
_markrc
	calr	SetXY
	cp	h,e
	jr	nz,_markrb
	pop	hl
_marks
	cp	h,WIDTH-1
	jr	z,_markt
	cp	l,0
	jr	z,_markt
	ld	e,h
	cp	e,WIDTH-2
	jr	z,_markt
	ld	d,l
	cp	d,1
	jr	nz,_marko
_markt
	pop	de
	dec	e
	cp	e,-1
	jr	nz,_marko0
	inc	e
	dec	d
	cp	d,1
	jr	nz,_marko0

	;*********
	; end scan
	;*********

	ld	(STOP),5
	cp	(ANIM),0
	jp	z,_cont
	ld	(STOP),3
	inc	(CHAIN)
	jp	_cont

	;*********************************
	;wait while showing removable elts
	;*********************************
_anim
	dec	(ANIM)
	jp	nz,_cont

	;**********************
	;remove marked elements
	;**********************
_down
	ld	xix,ARRAY
	ld	xiy,MARK
	ld	bc,SIZE
	ld	xiz,0
	ld	e,0
_rem0
	ld	a,(xiy+)
	cp	a,0
	jr	z,_rem1
	ld	(xix),e
	inc	xiz
_rem1
	inc	xix
	djnz	bc,_rem0

	add	(BLOCKS),xiz
	ld	xwa,(NXTLVL)
	cp	(BLOCKS),xwa
	jr	lt,_rem1b
	ld	a,(NBWAIT)
	cp	a,0
	jr	z,_rem1a
	dec	a
	jr	nz,_rem0a
	ld	(SPD),4
_rem0a
	ld	(NBWAIT),a
	ld	xwa,(ADDLVL)
	sll	1,xwa
	ld	(ADDLVL),xwa
	add	(NXTLVL),xwa
	jr	_rem1b
_rem1a
	ld	(SPD),8
_rem1b
	ld	xwa,0
	ld	a,(CHAIN)
_rem2
	dec	a
	jr	z,_rem3
	sll	1,xiz
	jr	_rem2
_rem3
	add	(SCORE),xiz
	ld	a,(CHAIN)
	cp	a,5
	jr	lt,_rem4
	ld	a,4
_rem4
	ld	(MSG),a
	ld	(MSGCPT),MSGTIME
	calr	clearmark
	ld	(SCRL),0
	ld	(STOP),4

	;***************************
	;move elements to the bottom
	;***************************
_scroll
	cp	(SCRL),0
	jr	z,_scr1
	dec	2,(SCRL)
	jp	_cont
_scr1

	;move MARK to ARRAY
	ld	xiy,ARRAY
	ld	xiz,MARK
	ld	l,HEIGHT
_trans0
	dec	l
	ld	h,WIDTH
_trans1
	dec	h
	ld	xix,xiz
	calr	GetXY
	cp	a,0
	jr	z,_trans2
	ld	xix,xiy
	calr	SetXY
_trans2
	cp	h,0
	jr	nz,_trans1
	cp	l,0
	jr	nz,_trans0

	calr	clearmark
	ld	xbc,0
	ld	h,WIDTH
_evalx
	dec	h
	ld	l,HEIGHT-1
_evaly
	ld	xix,xiy
	calr	GetXY
	cp	a,0
	jr	z,_marky
	djnz	l,_evaly
	jr	_contx
_marky
	cp	l,0
	jr	z,_contx
	dec	l
	ld	xix,xiy
	call	GetXY
	cp	a,0
	jr	z,_marky
	ld	xix,xiz
	inc	l
	calr	SetXY		; save value in MARK(Y+1)
	dec	l
	ld	xix,xiy
	ld	a,0
	calr	SetXY		; erase in ARRAY
	inc	bc
	jr	_marky
_contx
	cp	h,0
	jr	nz,_evalx
_contscr

	cp	bc,0
	jr	z,_stopscr

	ld	(SCRL),8
	jr	_cont
_stopscr
	ld	(STOP),2
	jr	_cont

	;*************************
	;set elements in the array
	;*************************
_set
	dec	(SET)
	jp	nz,_cont
	ld	xix,ARRAY
	ld	h,(CURX)
	ld	l,(CURY)
	cp	l,8
	jr	lt,_lost
	srl	3,l
	inc	l
	ld	a,(CC)
	calr	SetXY
	dec	l
	ld	a,(BB)
	calr	SetXY
	dec	l
	ld	a,(AA)
	calr	SetXY
	ld	(STOP),2

_cont
	calr	Process		;move them about
	ld	(READY),1	; ready to draw (in vbl)
	calr	test
	ld	w,1
	cp	(STOP),4
	jr	z,_wait
	ld	w,(NBWAIT)
	cp	w,0
	jr	nz,_wait
	ld	w,1
_wait
	calr	VBWait		;wait until 1 full screendraw before looping (else screen tears)
_nowait
; User shutdown? (Power off pressed?)
	cp	(rUSERS),0
	jp	z,_main		;no,jump to _main and loop
; Power off NGP
	ld	rw3,VECT_SHUTDOWN
	calr	SYSTEM_CALL
_end
	jr	_end

_lost
	ld	(READY),0
	ld	hl,0101h
	ld	c,WALL
	ld	xwa,loose
	calr	vprint
_lost0
	ld	a,(rSL)
	bit	PADB_A,a
	jr	z,_lost0
	calr	released
	
	call	bestscores
	
	jp	_begin

;***********************
;Clear MARK array
;***********************

clearmark
	push	xwa
	push	xbc
	push	hl
	ld	xwa,MARK
	ld	xbc,SIZE
	ld	l,0
_clearmark
	ld	(xwa+),l
	djnz	bc,_clearmark
	pop	hl
	pop	xbc
	pop	xwa
	ret

;***********************
;Test if piece must stop
;***********************

test
	cp	(STOP),1
	jr	le,ctest
	ret
ctest
	ld	(STOP),0
	ld	l,(CURY)
	cp	l,-16
	jr	lt,retstop
	cp	l,(HEIGHT-2)*8
	jr	z,dostop
	and	l,7
	jr	nz,retstop
stop0
	ld	l,(CURY)
	add	l,16
	srl	3,l
	ld	h,(CURX)
	ld	xix,ARRAY
	calr	GetXY
	cp	a,0
	jr	nz,dostop
	ret
dostop
	ld	(STOP),1
	cp	(SET),0
	jr	nz,retstop
	ld	(SET),8
retstop
	ret

;***********************************************
; Get value at (x,y) in an array of WIDTH*HEIGHT
; xix  array address
; h = x, l = y
; return in a
;***********************************************

GetXY
	push	xde
	push	xbc
	ld	xde,0
	ld	e,l
	ld	xbc,WIDTH
	mul	bc,e
	add	c,h
	add	xbc,xix
	ld	a,(xbc)
	pop	xbc
	pop	xde
	ret

;***********************************************
; Set value at (x,y) in an array of WIDTH*HEIGHT
; xix  array address
; h = x, l = y, v = a
;***********************************************

SetXY
	push	xde
	push	xbc
	ld	xde,0
	ld	e,l
	ld	xbc,WIDTH
	mul	bc,e
	add	c,h
	add	xbc,xix
	ld	(xbc),a
	pop	xbc
	pop	xde
	ret

;************************************************
DrawLine

	; draw limite line
	ld	h,(DX-1)*8
	ld	l,8
	ld	wa,WALL+9
	ld	b,WALL
	ld	xix,WIDTH
	ld	c,-1
_drawline
	add	h,8
	inc	c
	calr	DrawSprite
	djnz	ix,_drawline
	ret

;************************************************
DrawScreen
	ld	xbc1,MARK
	ld	xhl,ARRAY
	ld	xix,_SCR2RAM+64+DX*2
	ld	xwa1,_SCR1RAM+64+DX*2
	ld	iy,HEIGHT
	ld	(EXIST),0
	;draw rows
rows
	ld	iz,WIDTH
	ld	xde,xwa1
	ld	xde1,xde
	ld	xde,xix

	; Array state
cols
	cp	(STOP),3
	jr	nz,showit0
	ld	c,(xbc1+)
	cp	c,0
	jr	z,showit
	ld	c,(ANIM)
	and	c,1
	jr	z,showit
	inc	xhl
	ld	bc,0
	jr	dtshow
showit0
	ld	bc,0
	cp	(STOP),4
	jr	nz,showit1
	ld	c,(xbc1+)
	ld	b,c
	sll	1,b
showit1
	ld	(xde1+),bc
showit
	ld	c,(xhl+)
	ld	b,c
	sll	1,b
	cp	bc,0
	jr	z,dtshow
	ld	(EXIST),1
dtshow
	ld	(xde+),bc
	djnz	iz,cols

	add	xix,64		;nextline
	add	xwa1,64
	djnz	iy,rows

	cp	(STOP),5
	jr	nz,_contshow0
	cp	(EXIST),0
	jr	nz,_contshow0
	ld	xwa,500		; all clear : bonus
	add	(SCORE),xwa
	ld	(MSG),6
	ld	(MSGCPT),MSGTIME
_contshow0
	ld	a,(SCRL)
	ld	(rS1SOY),a
	ld	xbc,0
	ld	c,(MSG)
	mul	bc,18
	ld	xwa,msgs
	add	xwa,xbc
	ld	hl,00101h
	ld	c,WALL
	calr	vprint

	ld	a,(MSGCPT)
	cp	a,0
	jr	z,contshow
	dec	a
	ld	(MSGCPT),a
	cp	a,0
	jr	nz,contshow
	ld	(MSG),0
contshow
	cp	(STOP),1
	jr	le,drawspr
	ld	xwa,WIDTH
	calr	ClearSprite
	ret
drawspr
	; current falling sprites
	ld	h,(CURX)	;set up registers h,l,wa,b,c for drawsprite inputs
	add	h,DX
	sll	3,h
	ld	l,(CURY)	;see drawsprite for input meanings
	ld	wa,0
	ld	a,(AA)
	ld	b,a
	ld	c,WIDTH
	calr	DrawSprite

	add	l,8
	ld	wa,0
	ld	a,(BB)
	ld	b,a
	inc	c
	calr	DrawSprite

	add	l,8
	ld	wa,0
	ld	a,(CC)
	ld	b,a
	inc	c
	calr	DrawSprite

	; draw next
	ld	xix,_SCR2RAM+64+(DX+WIDTH+7)*2
	ld	a,(NA)
	ld	w,a
	sll	1,w
	ld	(xix),wa
	add	xix,64
	ld	a,(NB)
	ld	w,a
	sll	1,w
	ld	(xix),wa
	add	xix,64
	ld	a,(NC)
	ld	w,a
	sll	1,w
	ld	(xix),wa

	; draw score
	ld	c,WALL
	ld	h,DX+WIDTH+2
	ld	xwa,(BLOCKS)
	calr	int2str
	ld	l,7
	ld	xwa,TXTSCORE
	call	print

	ld	xwa,(SCORE)
	calr	int2str
	ld	l,11
	ld	xwa,TXTSCORE
	call	print

	ld	l,13
	ld	xwa,(STRLVL)
	call	print
	ld	a,4+48
	cp	(SPD),8
	jr	z,drawspd
	dec	a
	cp	(SPD),4
	jr	z,drawspd
	ld	a,3+48
	sub	a,(NBWAIT)
drawspd
	ld	xde,TXTSCORE
	ld	(xde+),a
	ld	(xde),0
	add	h,5
	ld	l,17
	ld	xwa,TXTSCORE
	call	print

	ret

int2str
	push	xbc
	push	xde
	push	xhl
	ld	xde,TXTSCORE
	ld	(xde+),32
	ld	(xde+),32
	ld	(xde+),32
	ld	(xde+),32
	ld	(xde+),32
	ld	(xde+),48
	ld	(xde),0
loop0
	dec	xde
	cp	xwa,0
	jr	z,endsco
	ld	xbc,xwa
	div	xbc,10
	and	xbc,0ffffh
	push	xbc
	mul	bc,10
	ld	xhl,xwa
	sub	xhl,xbc
	add	l,030h
	ld	(xde),l
	pop	xwa
	jr	loop0
endsco
	pop	xhl
	pop	xde
	pop	xbc
	ret

;************************************************

Process
	cp	(STOP),1
	jr	gt,outp
	cp	(STOP),0
	jr	nz,nomove
	ld	a,(SPD)
	add	(CURY),a
nomove
	cp	(CPT),0
	jr	z,contp
	dec	(CPT)
	ret
contp
	ld	xix,ARRAY	; used in GetXY
	ld	a,(rSL)		;get joystick movement into a
	bit	PADB_RIGHT,a
	jp	z,label0
	cp	(CURX),WIDTH-1
	jr	z,outp
	; left test
	ld	l,(CURY)
	ld	h,(CURX)
	inc	h
	calr	tstxy
	cp	a,0
	jr	nz,outp
	; ok go right
	inc	(CURX)
	jr	exitp
exitp
	ld	(CPT),2
outp
	ret

	;******************
	;test xy after move
	;******************
tstxy
	ld	e,2
	ld	d,l
	and	d,7
	cp	d,0
	jr	nz,contp0
	dec	e
contp0
	ld	d,l
	cp	d,0
	jr	ge,zero
	cp	d,-16
	jr	gt,zeroa
	ld	a,0
	ret
zeroa
	ld	l,-1
	cp	d,-8
	jr	le,zerob
	ld	l,0
	cp	d,0
	jr	lt,zerob
zero
	srl	3,l
zerob
	add	l,e
	calr	GetXY
	ret

	;***********
	;other moves
	;***********
label0
	bit	PADB_LEFT,a
	jr	z,label1

	cp	(CURX),0
	jr	z,outp
	; left test
	ld	l,(CURY)
	ld	h,(CURX)
	dec	h
	calr	tstxy
	cp	a,0
	jp	nz,outp
	; ok go left
	dec	(CURX)
	jp	exitp

label1
	cp	(STOP),0
	jr	nz,label2
	bit	PADB_DOWN,a
	jr	z,label2
	ld	l,(CURY)
	srl	3,l
	add	l,2
	cp	l,HEIGHT
	jp	ge,outp
	ld	h,(CURX)
	calr	GetXY
	cp	a,0
	jp	nz,outp
	ld	a,(SPD)
	add	(CURY),a
	jp	exitp
label2
	bit	PADB_A,a
	jr	z,label4
	ld	b,(AA)
	ld	c,(BB)
	ld	(AA),c
	ld	c,(CC)
	ld	(BB),c
	ld	(CC),b
	jp	exitp
label4
	bit	PADB_B,a
	jr	z,label7
	ld	h,(CURX)
	ld	l,0
	ld	xix,ARRAY
label5
	calr	GetXY
	cp	a,0
	jr	nz,label6
	cp	l,HEIGHT-1
	jr	z,label6
	inc	l
	jr	label5
label6
	sll	3,l
	sub	l,16
	cp	(CURY),l
	jp	ge,exitp
	ld	(CURY),l
	jp	exitp
label7
	bit	PADB_OPTION,a	; pause
	jr	z,labela
	calr	released
	ld	hl,00101h
	ld	xwa,pause
	ld	c,WALL
	calr	vprint
label8
	ld	a,(rSL)
	bit	PADB_B,a
	jr	z,label9
	jp	_begin		; stack memory leak, i know it
label9
	bit	PADB_OPTION,a
	jr	z,label8
	calr	released
	ld	hl,00101h
	ld	xwa,blank
	ld	c,WALL
	calr	vprint
labela
	ret

;-------------------------------------

;--------------------------------------------
; VBWait
; Waits for n vertical blank interrupts
; inputs w = number of VBIs to wait for
;--------------------------------------------

VBWait
	push	xwa
	push	xbc
	ld	(VBCOUNTER),0
	ld	c,w
vbw1
	ld	w,c
	ld	a,(VBCOUNTER)
	sub	w,a
	jr	nc,vbw1
	pop	xbc
	pop	xwa
	ret

; ---------------------------------------------------------------
; Initialise Memory before game starts
;
; ---------------------------------------------------------------
InitialiseMemory
	ld	xwa,0
	calr	ClearSprite
; Clear Scroll 1 & 2 VRAM with 0
ClearScreen
	ld	xhl,_SCR1RAM
	ld	wa,0
	ld	(xhl),wa
	ld	xde,xhl
	inc	xde
	inc	xde
	ld	bc,800h-1
	ldirw
	ret
; Clear Sprite Attribute Memory
ClearSprite
	push	xbc
	push	xde
	push	xix
	ld	xde,0
	ld	b,64
	sll	2,wa
	ld	xix,_SPRRAM
	add	xix,xwa
SAMloop
	ld	(xix+),xde
	djnz	b,SAMloop
	pop	xix
	pop	xde
	pop	xbc
	ret
;
; DrawSprite
;
; Draws a Sprite
; inputs wa = tile (0-511),hl = xy (in pixels)
; c = sprite number (0-63) ,b = palette number (0-15)
;

DrawSprite
	push	xde
	push	xhl
	push	xwa

	; sprite registers for specified sprite
	ld	xde,0
	ld	e,c
	sll	2,xde
	add	xde,_SPRRAM

	add	w,16	; set priority - 24 is above both character planes
	ldw	(xde+),wa
	ld	(xde+),h	; store x
	ld	(xde),l	; store y

	; set palette
	ld	xde,0
	ld	e,c
	add	xde,8c00h
	ld	(xde),b

	pop	xwa
	pop	xhl
	pop	xde
	ret

;************

VBlankInt
	; Update Watch Dog Timer to prevent CPU reset
	ld	(rWDCR),WD_CLR
	; sound !
	call	playmusic
	; increment a counter
	inc	(VBCOUNTER)
	cp	(READY),0
	jr	z,notready
	push	xwa
	push	xbc
	push	xde
	push	xhl
	push	xix
	push	xiy
	push	xiz
	push	xwa1
	push	xbc1
	push	xde1
	call	DrawScreen
	calr	DrawLine
	ld	(READY),0
	pop	xde1
	pop	xbc1
	pop	xwa1
	pop	xiz
	pop	xiy
	pop	xix
	pop	xhl
	pop	xde
	pop	xbc
	pop	xwa
notready
	reti

;******
; Menu
;******

released
	push	a
rel1
	ld	a,(rSL)
	cp	a,0
	jr	nz,rel1
	pop	a
	ret

pal0
	ld	bc,(ENDPALETTE0-PALETTE0)/2
	ld	xde,_SCR2PAL
	ld	xhl,PALETTE0
	ldirw	(xde+),(xhl+)
	ret

;**********************
;* Show scores & wait A
;**********************

showgreetz
	call	clearscreen
	ld	xwa,GREETZTABLE1
	call	drawback
	calr	waitscores
	calr	released
	ld	xwa,GREETZTABLE2
	call	drawback
	jr	waitscores

showscores
	call	clearscreen
	ld	xwa,SCORETABLE
	call	drawback
	calr	drawscores

	ld	hl,020eh
	ld	c,WALL
	ld	xwa,goplay1
	calr	print
	inc	l
	ld	xwa,goplay2
	calr	print

waitscores
	ld	a,(rSL)
	bit	PADB_A,a
	jr	nz,endwaitscores
	bit	PADB_B,a
	jr	z,waitscores
endwaitscores
	ret

menu
	call	InitialiseMemory
	or	(rPF),080h
	ld	(rS1SOX),3
	calr	setpal
	calr	pal0

	ld	xwa,MUSIC1
	ld	xbc,ENDMUSIC1
	call	initmusic
	call	startmusic
menu0
	call	clearscreen

	ld	xwa,0		; at(0,0)
	ld	xde,'Z'+1
	ld	b,1		; use pal
	ld	c,1		; plan 1
	ld	xhl,img1	; pres picture (1 plan)
	call	drawpic
	ld	xwa,MENUTABLE
	call	drawback
	ld	xwa,menu1
	calr	select

	cp	(LVL),-1
	jr	z,menu0
	cp	(LVL),0
	jr	z,letsplay
	cp	(LVL),1
	jr	nz,notscore
	calr	showscores
	jr	menu0
notscore
	calr	showgreetz
	jr	menu0

letsplay	
	call	clearscreen
	ld	xwa,0		; at(0,0)
	ld	xde,'Z'+1
	ld	b,1		; use pal
	ld	c,1		; plan 1
	ld	xhl,img1	; pres picture (1 plan)
	call	drawpic
	ld	xwa,MENUTABLE
	call	drawback
	ld	xwa,menu2
	calr	select

	cp	(LVL),-1
	jp	z,menu0

	ld	(rS1SOX),0
	and	(rPF),07fh

	ld	xde,(STRLVL)
ltrim
	ld	a,(xde+)
	cp	a,32
	jr	z,ltrim
	dec	xde
	ld	(STRLVL),xde
	add	(LVL),4

	call	InitialiseMemory
	call	stopmusic

	ret

select
	ld	(LVL),0
	push	xwa
wait2

	inc	(RND)
	ld	a,(LVL)
	cp	a,3
	jr	nz,_w0
	ld	a,0
	jr	_w1
_w0
	cp	a,-1
	jr	nz,_w1
	ld	a,2
_w1
	ld	(LVL),a
	calr	released
	
	ld	hl,040dh
	ld	c,WALL
	pop	xwa
	push	xwa
	calr	print
	ld	hl,040fh
	ld	c,1
	add	xwa,13
	ld	(STRLVL),xwa
	cp	(LVL),0
	jr	nz,wait2a
	inc	c
wait2a
	calr	print
	ld	hl,0410h
	add	xwa,13
	ld	c,1
	cp	(LVL),1
	jr	nz,wait2b
	inc	c
	ld	(STRLVL),xwa
wait2b
	calr	print
	ld	hl,0411h
	ld	c,1
	add	xwa,13
	cp	(LVL),2
	jr	nz,wait2c
	inc	c
	ld	(STRLVL),xwa
wait2c
	calr	print
	ld	a,(rSL)
	bit	PADB_UP,a
	jr	z,wait20
	dec	(LVL)
	jp	wait2
wait20
	bit	PADB_DOWN,a
	jr	z,wait21
	inc	(LVL)
	jp	wait2
wait21
	bit	PADB_B,a
	jr	z,wait3
	pop	xwa
	ld	(LVL),-1
	ret

wait3
	IFDEF RUNFROMMENU
	bit	PADB_OPTION,a
	jp	z,wait4
	call	stopmusic
	call	stopsound
	ld	(rS1SOX),0
	and	(rPF),07fh
	jp	200089h		; return to menu system
	ENDIF
wait4
	bit	PADB_A,a
	jp	z,wait2

	pop	xwa
	calr	released
	ret

;****************
;Best scores pres
;****************

bestscores
	ld	xwa,0
	call	clearsprite
	ld	xwa,SCORETABLE
	call	drawback
	calr	drawscores

	ld	hl,020ch
	ld	c,1
	ld	xwa,yscr
	calr	print
	ld	xwa,(SCORE)
	calr	int2str
	ld	c,1
	ld	hl,00c0ch
	ld	xwa,TXTSCORE
	call	print

	ld	xwa,(SCORE)
	ld	xbc,BESTEASY
	ld	xde,EASYNAME
	cp	(LVL),4		; easy
	jr	nz,best0
	cp	(xbc),xwa
	jr	lt,best3
	jr	less
best0
	ld	xbc,BESTNORM
	ld	xde,NORMNAME
	cp	(LVL),5		; normal
	jr	nz,best1
	cp	(xbc),xwa
	jr	lt,best3
	jr	less
best1				; hard
	ld	xbc,BESTHARD
	ld	xde,HARDNAME
	cp	(xbc),xwa
	jr	lt,best3
less
	ld	hl,020eh
	ld	c,1
	ld	xwa,sorry0
	calr	print

	ld	hl,0d0eh
	ld	c,1
	ld	xwa,xde
	calr	print

	ld	hl,020fh
	ld	c,1
	ld	xwa,sorry1
	calr	print

	ld	hl,0210h
	ld	c,1
	ld	xwa,sorry2
	calr	print
best2
	ld	a,(rSL)
	bit	PADB_A,a
	jr	z,best2
	calr	released
	ret
best3				; new hiscore !
	push	xde
	push	xbc
	ld	hl,020eh
	ld	c,1
	ld	xwa,great0
	calr	print

	ld	hl,020fh
	ld	c,1
	ld	xwa,great1
	calr	print
	
	ld	xde,_SCR2RAM+16*64+16
	ld	xbc,3
	ld	hl,0241h
	ld	xwa,TXTSCORE
	push	xwa
getname
	ld	(xde),hl
	ld	w,4
	calr	VBWait
	ld	a,(rSL)
	bit	PADB_A,a
	jr	z,name0
	calr	released
	pop	xwa
	inc	2,xde
	ld	(xwa+),l
	push	xwa
	djnz	bc,getname
	pop	xwa
	ld	(xwa),0
	jr	best3b
name0
	bit	PADB_UP,a
	jr	z,name1
	cp	l,'Z'
	jr	nz,name0a
	ld	l,'A'
	jr	getname
name0a
	inc	l
	jr	getname
name1
	bit	PADB_DOWN,a
	jr	z,getname
	cp	l,'A'
	jr	nz,name1a
	ld	l,'Z'
	jr	getname
name1a
	dec	l
	jr	getname

best3b
	pop	xbc
	pop	xde
	ld	xwa,(SCORE)
	ld	(xbc),xwa
	ld	xwa,(TXTSCORE)
	ld	(xde),xwa
	calr	drawscores

flash
	; Erase block first (mandatory) : 64kb for only 256 bytes (for my game)
	ld	ra3,0
	ld	rb3,BLOCK_NB
	ld	rw3,VECT_FLASHERS
	ld	(rWDCR),WD_CLR
	swi	1

	; Then write data
	ld	ra3,0
	ld	rbc3,1	; 256 bytes
	ld	xhl3,MAGIC
	ld	xde3,SAVEOFFSET
	ld	rw3,VECT_FLASHWRITE
	ld	(rWDCR),WD_CLR
	swi	1

	ld	(rWDCR),WD_CLR
	
	ld	w,50
	calr	VBWait
	ret

drawscores
	ld	hl,0805h
	ld	c,WALL
	ld	xwa,EASYNAME
	calr	print
	ld	xwa,(BESTEASY)
	calr	int2str
	ld	c,WALL
	ld	hl,00d05h
	ld	xwa,TXTSCORE
	call	print

	ld	hl,0807h
	ld	c,WALL
	ld	xwa,NORMNAME
	calr	print
	ld	xwa,(BESTNORM)
	calr	int2str
	ld	c,WALL
	ld	hl,00d07h
	ld	xwa,TXTSCORE
	call	print

	ld	hl,0809h
	ld	c,WALL
	ld	xwa,HARDNAME
	calr	print
	ld	xwa,(BESTHARD)
	calr	int2str
	ld	c,WALL
	ld	hl,00d09h
	ld	xwa,TXTSCORE
	call	print
	ret

;****************
;SetPal
;****************
setpal
; copy palette to character palette RAM
; same as above
	ldw	(_BGCPAL),00000h;

	ld	bc,(ENDPALETTE-PALETTE)/2
	ld	xde,_SCR2PAL
	ld	xhl,PALETTE
	ldirw	(xde+),(xhl+)
	ld	bc,(ENDPALETTE-PALETTE)/2
	ld	xde,_SCR1PAL
	ld	xhl,PALETTE
	ldirw	(xde+),(xhl+)

; copy sprite palette
; sprites can have seperate palettes
	ld	bc,(ENDPALETTE-PALETTE)/2	;but i just use the same ones
	ld	xde,_SPRPAL
	ld	xhl,PALETTE
	ldirw	(xde+),(xhl+)
	ret

;****************
;Draw back screen
;****************
drawback
	ld	xhl,xwa
	ld	xde,_SCR2RAM
	ld	xix,19
	ld	w,WALL*2
_showback
	ld	xbc,20
_show0:
	ld	a,(xhl+)
	ld	(xde+),wa
	djnz	bc,_show0
	add	xde,24
	djnz	ix,_showback
	ret

;****************
;Horizontal print
;****************

print
	push	xwa
	push	xbc
	push	xde
	push	c
	ld	xbc,0
	ld	xde,0
	ld	c,l
	ld	e,h
	sll	1,e
	sll	3,bc
	sll	3,bc
	add	bc,de
	add	xbc,_SCR2RAM
	pop	d
	sll	1,d
contprint
	ld	e,(xwa+)
	cp	e,0
	jr	z,endprint
	cp	e,32
	jr	z,space
	ld	(xbc),de
space
	inc	2,xbc
	jr	contprint
endprint
	pop	xde
	pop	xbc
	pop	xwa
	ret

;**************
;Vertical print
;**************

vprint
	push	xbc
	push	xde
	push	c
	ld	xbc,0
	ld	xde,0
	ld	c,l
	ld	e,h
	sll	1,e
	sll	3,bc
	sll	3,bc
	add	bc,de
	add	xbc,_SCR2RAM
	pop	d
	sll	1,d
contvprint
	ld	e,(xwa+)
	cp	e,0
	jr	z,endvprint
	ld	(xbc),de
	add	xbc,64
	jr	contvprint
endvprint
	pop	xde
	pop	xbc
	ret

;****
;drawpic
;
; in :
;     xwa = dest offset in video ram
;     xde = tile number offset ex : 'Z'+1
;     b   = 0 : dont use pals, 1 : use pals
;     c   = plan to use for type 1 pictures
;     xhl = img addr
; out :
;     xwa1 = pal1
;     xwa2 = pal2
;****
drawpic
	ld	rde1,bc
	ld	b,(xhl+)
	ld	ra1,b		;type
	ld	xbc1,0
	ld	b,(xhl+)
	ld	rb1,b		;width
	ld	b,(xhl+)
	ld	rc1,b		;height
	ld	xbc,0
	ld	bc,(xhl+)	;size (width*height)
	ld	xhl1,xbc
	ld	xiz,xde		;save tile offset
	sll	3,bc
	cp	ra1,1
	jr	z,contpic
	sll	1,bc		; 2 plans
contpic
	sll	4,xde
	add	xde,_TILERAM
	ldirw	(xde+),(xhl+)

	push	xhl	; pal1
	cp	rd1,0
	jr	z,nopal1

	ld	bc,64
	ld	xde,_SCR1PAL
	cp	ra1,2
	jr	z,okpal1
	cp	re1,1
	jr	z,okpal1
	ld	xde,_SCR2PAL
okpal1
	ldirw	(xde+),(xhl+)
nopal1
	pop	xhl
	push	xhl
	add	xhl,128

	cp	ra1,1
	jr	z,not2

	push	xhl	; pal2
	cp	rd1,0
	jr	z,nopal2
	ld	bc,64
	ld	xde,_SCR2PAL
	ldirw	(xde+),(xhl+)
nopal2
	pop	xhl
	push	xhl
	add	xhl,128

not2
	ldw	(_BGCPAL),0

	ld	xix,_SCR1RAM
	cp	ra1,1
	jr	nz,okadd
	cp	re1,1
	jr	z,okadd
	ld	xix,_SCR2RAM
okadd
	ld	xiy,_SCR2RAM
	add	xix,xwa
	add	xiy,xwa
	ld	xwa,xhl1
	ld	xde1,xwa
	ld	xhl1,xiz	;saved tile offset
	ld	xiz,xhl
	ld	xbc,0
	ld	c,rc1	; h
imgh
	ld	xwa,0
	ld	a,rb1
	push	xix
	push	xiy
imgw
	ld	xhl,0
	ld	h,(xiz+)
	sll	1,h
	add	hl,rhl1
	ld	(xix+),hl
	cp	ra1,1
	jr	z,not2a
	ld	de,hl
	add	de,rde1
	ld	(xiy+),de
not2a
	inc	xhl1
	djnz	wa,imgw
	pop	xiy
	pop	xix
	add	xix,64
	add	xiy,64
	djnz	bc,imgh
	pop	xwa	; pal1 out
	ld	xwa2,0
	cp	ra1,1
	jr	z,not2b
	ld	xwa2,xwa
	pop	xwa1
	ret
not2b
	ld	xwa1,xwa
	ret

;****
;fade
;
; in :
;     xwa1 = pal1
;     xwa2 = pal2
;****
fade
	; Fade in

	ld	xwa,FADE1
	calr	init_fadein
	cp	xwa2,0
	jr	z,notfade2
	ld	xwa,FADE2
	calr	init_fadein
notfade2
	ld	xix,16
img_fi
	ld	xwa,FADE1
	ld	xhl,xwa1
	calr	fadein
	ld	bc,64
	ld	xde,_SCR1PAL
	ld	xhl,FADE0
	ldirw	(xde+),(xhl+)
	
	cp	xwa2,0
	jr	z,notfade2a
	ld	xwa,FADE2
	ld	xhl,xwa2
	calr	fadein
	ld	bc,64
	ld	xde,_SCR2PAL
	ld	xhl,FADE0
	ldirw	(xde+),(xhl+)
notfade2a

	ld	w,5
	calr	VBWait
	djnz	ix,img_fi

	; Wait a while

	ld	w,100
	calr	VBWait

	; Fade out

	ld	xwa,FADE1
	ld	xhl,xwa1
	calr	init_fadeout
	cp	xwa2,0
	jr	z,notfade2b
	ld	xwa,FADE2
	ld	xhl,xwa2
	calr	init_fadeout
notfade2b

	ld	xix,16
img_fo
	ld	xwa,FADE1
	calr	fadeout
	ld	bc,64
	ld	xde,_SCR1PAL
	ld	xhl,FADE0
	ldirw	(xde+),(xhl+)

	cp	xwa2,0
	jr	z,notfade2c	
	ld	xwa,FADE2
	calr	fadeout
	ld	bc,64
	ld	xde,_SCR2PAL
	ld	xhl,FADE0
	ldirw	(xde+),(xhl+)
notfade2c	

	ld	w,5
	calr	VBWait
	djnz	ix,img_fo
	ret

;************
; init_fadein
;
; xwa : fade buffer
;************

init_fadein
        push	xwa
        push	xbc
        push	xde
        ld	xde,0
        ld	xbc,48
buf0
	ld	(xwa+),xde
	djnz	bc,buf0
        pop	xde
        pop	xbc
        pop	xwa
	ret
	
;************
; init_fadeout
;
; xwa : fade buffer
; xhl : palettes (16*2 bytes)
;************

init_fadeout
        push	xwa
        push	xbc
        push	xde
        push	xhl
        ld	xbc,64
        ld	xde,0
bufi
	push	xbc
	ld	xbc,0
	ld	de,(xhl+)
	ld	bc,de
	ld	(xwa+),b
	srl	4,c
	and	c,0fh
	ld	(xwa+),c
	and	e,0fh
	ld	(xwa+),e
	pop	xbc
	djnz	bc,bufi
        pop	xhl
	pop	xde
	pop	xbc
	pop	xwa
	ret

;************
; fadein
;
; xwa : fade buffer
; xhl : palette
;************

fadein
        push	xwa
        push	xbc
        push	xde
        push	xhl
        push	xix
        push	xiy
        ld	xix,FADE0
	ld	xiy,64
        ld	xde,0
fadein0:
	ld	xbc,0
	ld	de,(xhl+)
	ld	bc,de
	cp	b,(xwa)
	jr	z,fadein1
	inc	(xwa)
fadein1
	inc	xwa
	srl	4,c
	and	c,0fh
	cp	c,(xwa)
	jr	z,fadein2
	inc	(xwa)
fadein2
	inc	xwa
	and	e,0fh
	cp	e,(xwa)
	jr	z,fadein3
	inc	(xwa)
fadein3
	dec	2,xwa
	ld	bc,0
	ld	b,(xwa+)
	ld	c,(xwa+)
	sll	4,c
	add	c,(xwa+)
	ld	(xix+),bc
	djnz	iy,fadein0
	pop	xiy
	pop	xix
	pop	xhl
	pop	xde
	pop	xbc
	pop	xwa
	ret

;************
; fadeout
;
; xwa : fade buffer
;************

fadeout
        push	xwa
        push	xbc
        push	xde
        push	xhl
        push	xix
        ld	xix,FADE0
	ld	xbc,64
fadeout0
	ld	d,(xwa)
	cp	d,0
	jr	z,fadeout1
	dec	(xwa)
fadeout1
	inc	xwa
	ld	e,(xwa)
	cp	e,0
	jr	z,fadeout2
	dec	(xwa)
fadeout2
	inc	xwa
	ld	l,(xwa)
	cp	l,0
	jr	z,fadeout3
	dec	(xwa)
fadeout3
	inc	xwa
	sll	4,e
	add	e,l
	ld	(xix+),de
	djnz	bc,fadeout0
	pop	xix
	pop	xhl
	pop	xde
	pop	xbc
	pop	xwa
	ret

; data
img1
	include	"menu.inc"
img3
	include	"skull.inc"

menu1
title	db	" MAIN  MENU ",0
play	db	"    PLAY    ",0
scores	db	"  HISCORES  ",0
credits	db	"   GREETZ",0

menu2
choice	db	"SELECT LEVEL",0
easy	db	"    EASY    ",0
normal	db	"   NORMAL   ",0
hard	db	"    HARD    ",0

goplay1	db	"TRY TO BEAT THIS",0
goplay2	db	"    - THOR -",0

yscr	db	"YOUR SCORE",0
sorry0	db	"SORRY, BUT",0
sorry1	db	"IS THE BEST.",0
sorry2	db	"TRY ONCE AGAIN.",0
great0	db	"CONGRATULATIONS!",0
great1	db	"ENTER YOUR NAME:",0

loose	db	"    YOU LOSE     ",0
pause	db	"PAUSE   B TO EXIT",0

computr	db	"COM",0

msgs
blank	db	"                 ",0
good	db	"      GOOD       ",0
cool	db	"      COOL       ",0
great	db	"      GREAT      ",0
marvel	db	"    MARVELOUS    ",0
spec	db	"     SPECIAL     ",0
bonus	db	"   CLEAR BONUS   ",0

SYSTEM_CALL
	SystemCallCode
OS_VERSION
	OsVersionCode
	
	include "all.inc"
	include "sound.inc"
MUSIC1
	include "music1.inc"
ENDMUSIC1
MUSIC2
	include "music2.inc"
ENDMUSIC2

	IFNDEF RUNFROMMENU
	; make the cartridge size correct
	org HEADER_OFS + 2FFFFh
	db 0FFh
	ENDIF
