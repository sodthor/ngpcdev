	cpu	z80
	org	0h

VOICE1	EQU	080h
VOICE2	EQU	0A0h
VOICE3	EQU	0C0h


	jp	start

vbl_ok	db	1
note1	db	0
note2	db	0
note3	db	0
freq1	db	0,0
freq2	db	0,0
freq3	db	0,0
volume1	db	09Fh
volume2	db	0BFh
volume3	db	0DFh

start
	ld	sp,1000h
loop
	ld	a,(vbl_ok)
	or	a
	jr	z,loop
	sub	a,1
	jr	z,playnotes
;play freq
	xor	a
	ld	(vbl_ok),a
	ld	hl,04001h
	ld	a,(freq1)
	ld	(hl),a
	ld	a,(freq1+1)
	ld	(hl),a
	ld	a,(volume1)
	ld	(hl),a
	dec	hl
	ld	(hl),a
        inc	hl

	ld	a,(freq2)
	ld	(hl),a
	ld	a,(freq2+1)
	ld	(hl),a
	ld	a,(volume2)
	ld	(hl),a
	dec	hl
	ld	(hl),a
        inc	hl

	ld	a,(freq3)
	ld	(hl),a
	ld	a,(freq3+1)
	ld	(hl),a
	ld	a,(volume3)
	ld	(hl),a
	dec	hl
	ld	(hl),a

	jp	loop

playnotes
	ld	(vbl_ok),a

	ld	hl,volume1
	push	hl
	ld	a,VOICE1
	push	af
	push	af
	ld	hl,note1
	push	hl
	call	play

	jp	loop

	ld	hl,volume2
	push	hl
	ld	a,VOICE2
	push	af
	push	af
	ld	hl,note2
	push	hl
	call	play

	ld	hl,volume3
	push	hl
	ld	a,VOICE3
	push	af
	push	af
	ld	hl,note3
	push	hl
	call	play

	jp	loop

play
	pop	de
	ld	b,0
	pop	hl		; note
	ld	c,(hl)
	sla	c
	ld	hl,notes
	add	hl,bc
	pop	af		; voice
	or	a,(hl)
	inc	hl
	ld	b,(hl)
	ld	hl,4000h
	ld	(hl),a
	ld	(hl),b
	pop	af		; voice
	ld	b,a
	pop	af		; volume
	or	a,10h
	add	a,b
	ld	(hl),a
	inc	hl
	ld	(hl),a
	push	de
	ret

notes
	db	08h,36h	; A	note 0
	db	07h,33h	; A#
	db	09h,30h	; B
	db	0Dh,2Dh	; C
	db	04h,2Bh	; C#
	db	0Dh,28h	; D
	db	09h,26h	; D#
	db	06h,24h	; E
	db	05h,22h	; F
	db	06h,20h	; F#
	db	09h,1Eh	; G
	db	0Eh,1Ch	; G#

	db	04h,1Bh	; A	note 12
	db	0Bh,19h	; A#
	db	04h,18h	; B
	db	0Eh,16h	; C
	db	09h,15h	; C#
	db	06h,14h	; D
	db	04h,13h	; D#
	db	03h,12h	; E
	db	02h,11h	; F
	db	03h,10h	; F#
	db	04h,0Fh	; G
	db	07h,0Eh	; G#

	db	0Ah,0Dh	; A	note 24
	db	0Dh,0Ch	; A#
	db	02h,0Ch	; B
	db	07h,0Bh	; C
	db	0Dh,0Ah	; C#
	db	03h,0Ah	; D
	db	0Ah,09h	; D#
	db	01h,09h	; E
	db	09h,08h	; F
	db	01h,08h	; F#
	db	0Ah,07h	; G
	db	03h,07h	; G#

	db	0Dh,06h	; A	note 36
	db	06h,06h	; A#
	db	01h,06h	; B
	db	0Bh,05h	; C
	db	06h,05h	; C#
	db	01h,05h	; D
	db	0Dh,04h	; D#
	db	08h,04h	; E
	db	04h,04h	; F
	db	00h,04h	; F#
	db	0Dh,03h	; G
	db	09h,03h	; G#

	db	06h,03h	; A	note 48
	db	03h,03h	; A#
	db	00h,03h	; B

	END
