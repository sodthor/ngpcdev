
; Tell the AS assembler we want to code for NGPC
;
	cpu	93C141
	maxmode	on
	include	"stddef96.inc"

	include "hardware.inc"  ; Include hardware defines
	include "system.inc"
;
; Define variables
;

START_OF_RAM	EVAL	_MAINRAM

HEADER_OFS	EQU	0200000H

;
; Standard cartridge header
;
	org	HEADER_OFS

	db	" LICENSED BY SNK CORPORATION"	; 28 bytes license string
	dd	Start				; Program Counter
	dw	0				; Catalog number
	db	0				; Sub catalog number
	db	10h				; colour or b+w (10h = colour)
	db	"MODPLAYER   "			; Game name (12 bytes)
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
	dd	nada		; Timer Interrupt (8 bit Timer 0)
	dd	nada		; Timer Interrupt (8 bit Timer 1)
	dd	nada		; Timer Interrupt (8 bit Timer 2)
	dd	nada		; Timer Interrupt (8 bit Timer 3)
	dd	nada		; Serial Transmission Interrupt
	dd	nada		; Serial Reception Interrupt
	dd	nada		; (Reserved)
	dd	nada		; End Micro DMA Int (MicroDMA 0)
	dd	nada		; End Micro DMA Int (MicroDMA 1)
	dd	nada		; End Micro DMA Int (MicroDMA 2)
	dd	nada		; End Micro DMA Int (MicroDMA 3)

VBlankInt
       	; Update Watch Dog Timer to prevent CPU reset
	ld	(rWDCR),WD_CLR
nada
	reti

SYSTEM_CALL
	SystemCallCode
OS_VERSION
	OsVersionCode

; *** Start of User Code ***

Start
	calr	OS_VERSION      ; Initialize NGP or NGPC mode
	set	6,(rUSERA)      ; User Answer

; Install User Interrupt Vectors

	di

	lda	xix,(UserIntVect)
	lda	xiy,(rSWI3)
	ld	b,18
UIVloop
	ld	xwa,(xix+)
	ld	(xiy+),xwa
	djnz	b,UIVloop

	; set up screen size 160x152
	ld	(08002h),0
	ld	(08003h),0
	ld	(08004h),0a0h
	ld	(08005h),98h

	; background colour 
	; _BGCPAL = 0xBGR
	ldw	(_BGCPAL), 0000h
	ld	(8118h), 80h

	ld	xix,IMG_LIST
	ld	xde,MOD_SONG_LIST
MAIN
	ld	xwa,(xix+)
	ld	xbc,(xix+)
	call	ShowImage
	ld	xhl,(xde+)
	cp	xde,END_MOD_SONG_LIST
	jr	nz,not_end_song_list
	ld	xde,MOD_SONG_LIST
	ld	xix,IMG_LIST
not_end_song_list
	calr	start_mod
wait_next
	cp	(rUSERS),0	; User shutdown? (Power off pressed?)
	jr	z,MAIN		; no

; Power off NGP
power_off
	ld	rw3,VECT_SHUTDOWN
	calr	SYSTEM_CALL

done
	jr	done

;**************************************
;* Data
;**************************************

	include "modplay.inc"
	include "pt.inc"
	include "patega.inc"

	include "img.inc"
	include "skull.inc"
	include "jv0.inc"

MOD_SONG_LIST
	dd	PATEGA_SONG_ID
	dd	PT_SONG_ID
END_MOD_SONG_LIST

IMG_LIST
	dd	SKULL_ID,1*64+2*2
	dd	JV0_ID,2*64+3*2

	; make the cartridge size correct
;	org	HEADER_OFS + 3FFFFFh
;	db	0FFh
