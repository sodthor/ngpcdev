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

START_OF_RAM	EVAL	_MAINRAM
		RAMDB	VBCOUNTER

	IFDEF RUNFROMMENU
HEADER_OFS      EQU     0200000H
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
    	db	"3D ENGINE   "			; Game name (12 bytes)
	dd	0,0,0,0				; padding - reserve for future use

; User Interrupt Vectors

UserIntVect
	dd	nada		; Software Interrupt (SWI 3)
	dd	nada		; Software Interrupt (SWI 4)
	dd	nada		; Software Interrupt (SWI 5)
	dd	nada		; Software Interrupt (SWI 6)
	dd	nada		; RTC Alarm Interrupt
	dd	VBLInt_3D	; Vertical Blanking Interrupt
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

; enable interrupts
	ei

;seems to be BGR format?
	ldw	(_BGCPAL),0; 0311h	;BACKGROUND COLOUR
	ld	(8118h),80h

; copy internal charset to character RAM
;	ld	ra3,3
;	ld	rw3,VECT_SYSFONTSET
;	calr	SYSTEM_CALL

	calr	InitialiseMemory
	calr	init_3D
	ld	xwa,scenes
	ld	(SCENEPTR),xwa

_load_scene
	ld	a,(rSL)
	cp	a,0
	jr	nz,_load_scene

	ld	xde,(SCENEPTR)
	xor	w,w
	ld	(NB_OBJ),w
	ld	a,(xde+)
	ld	iz,wa
_loop_scene
	ld	xwa,(xde+)
	ld	xbc,(xde+)
	ld	xhl,(xde+)
	calr	add_object
	djnz	iz,_loop_scene
	cp	xde,endscenes
	jr	nz,_cont_scene
	ld	xde,scenes
_cont_scene
	ld	(SCENEPTR),xde

;*********
;Main loop
;*********

_main

	calr	compute_scene

	ld	xhl,SCENE
	incb	(xhl+16)
	incb	(xhl+17)
	incb	(xhl+18)
	cp	(NB_OBJ),1
	jr	le,_flip
	add	xhl,ELT_SIZE
	decb	(xhl+16)
	decb	(xhl+17)
	decb	(xhl+18)
	cp	(NB_OBJ),2
	jr	le,_flip
	add	xhl,ELT_SIZE
	incb	(xhl+16)
	decb	(xhl+17)
	incb	(xhl+18)

_flip
	calr	flip_buf

	ld	a,(rSL)
	bit	PADB_A,a
	jp	nz,_load_scene
_no_change

; User shutdown? (Power off pressed?)
	cp	(rUSERS),0
	jp	z,_main		;no,jump to _main and loop

	andb	(rPF),07fh	; scroll plane 1 to front

; Power off NGP
	ld	rw3,VECT_SHUTDOWN
	calr	SYSTEM_CALL
_end
	jr	_end

;*************************************
; VBWait
; Waits for n vertical blank interrupts
; inputs w = number of VBIs to wait for
;*************************************

VBWait
	push	wa
	ld	(VBCOUNTER),0
vbw1
	ld	a,(VBCOUNTER)
	cp	w,a
	jr	lt,vbw1
	pop	wa
	ret

;*************************************
; InitialiseMemory
; Initialise Memory before game starts
;*************************************

InitialiseMemory
	ld	xwa,0
	calr	ClearSprite

	; Clear Scroll 1 & 2 VRAM with 0
ClearScreen
	ld	xhl,_SCR1RAM
	ld	xwa,0
	ld	bc,1024
_clear_1
	ld	(xhl+),xwa
	djnz	bc,_clear_1

	ld	xhl,_TILERAM
	ld	xwa,0
	ld	bc,2048
_clear_2
	ld	(xhl+),xwa
	djnz	bc,_clear_2
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

;*********
; includes
;*********

	include	"3d.inc"
	include	"cube.inc"
	include	"pyramid.inc"
	include	"tie.inc"
	include	"tieb.inc"

scenes
;scene -2
	db	1		; nb objs
	dd	0		; rots
	dd	0		; translations
	dd	tie
;scene -1
	db	1		; nb objs
	dd	0		; rots
	dd	0		; translations
	dd	tieb
;scene 0
	db	2		; nb objs
	dd	010000000h	; rots
	dd	0000000e0h	; translations
	dd	cube
	dd	000000000h	; rots
	dd	000000010h	; translations
	dd	cube
;scene 1
	db	2		; nb objs
	dd	0		; rots
	dd	0000000e0h	; translations
	dd	cube
	dd	0		; rots
	dd	000000010h	; translations
	dd	pyramid
;scene 2
	db	2		; nb objs
	dd	010000000h	; rots & scale
	dd	0000000e0h	; translations
	dd	pyramid
	dd	0		; rots
	dd	000000010h	; translations
	dd	pyramid
;scene 3
	db	3		; nb objs
	dd	0		; rots
	dd	00000e0e0h	; translations
	dd	cube
	dd	0		; rots
	dd	00000e010h	; translations
	dd	pyramid
	dd	010000000h	; rots
	dd	000001000h	; translations
	dd	cube
endscenes

PALETTE
	dw	00fffh, 00fffh, 0fffh, 00fffh	;black
PALETTE_COL
	dw	00000h, 00f00h, 00f0h, 0000fh
ENDPALETTE

SYSTEM_CALL
	SystemCallCode
OS_VERSION
	OsVersionCode

	IFNDEF RUNFROMMENU
	; make the cartridge size correct
	org HEADER_OFS + 07fffh
	db 0FFh
	ENDIF
