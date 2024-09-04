	RAMDW	COUNT

	RAMDD	MOD_SAVE_TIMER

	RAMDD	MOD_SONG_PTR
	RAMDD	MOD_SONG_END
	RAMDD	MOD_SONG_RESTART
	RAMDD	MOD_INSTR_TABLE
	RAMDD	MOD_INSTR_END_TABLE
	RAMDD	MOD_INSTR_LOOP_TABLE

	RAMBUF	Timer,end_timer_src-timer_src

;**************************************
;* Init & start MOD play
;* param : xhl : mod song id
;**************************************

start_mod
	push	bc
	push	xde
	push	xhl

	ldw	(000b8h),0aaaah		; stop sound
	andb	(TRUN),10001110b	; stop count for Timer 0
	di

	; init song
	ld	xde,MOD_SONG_PTR
	ld	bc,12
	ldirw	(xde+),(xhl+)

	; transfert auto mod timer code
	ld	xhl,timer_src
	ld	xde,Timer
	ld	bc,end_timer_src-timer_src
	ldir	(xde+),(xhl+)

	ldw	(COUNT),1

	ld	xde,rSWI3
	add	xde,28
	ld	xhl,(xde)
	ld	(MOD_SAVE_TIMER),xhl
	ld	xhl,Timer
	ld	(xde),xhl		; set Timer 0

	; enable sampling
	ldw	(000b8h),055aah

	; set up Timer0
	ldb	(TMOD),02h
	ldb	rw3,VECT_INTLVSET
	ldb	rb3,3
	ldb	rc3,2
	swi	1

	ldf	0

	orb	(TRUN),00000001b	; start count for Timer 0

	ei	0

mod_play
	cpb	(Timer),007h
	jr	nz,mod_play

	; Stop MOD play
	andb	(TRUN),10001110b	; stop count for Timer 0

	ld	xde,rSWI3
	add	xde,28
	lda	xhl,(MOD_SAVE_TIMER)
	ld	(xde),xhl		; restore Timer 0

mod_wait
	ld	l,(rSL)
	bit	PADB_A,l
	jr	nz,mod_wait

	pop	xhl
	pop	xde
	pop	bc
	ret

;**************************************
;* Timer source
;**************************************

timer_src
	decw	(COUNT)
	jp	z,mod_seq

	push	wa
	push	bc
	push	de
	push	xhl
	push	xix
	push	xiy

	xor	wa,wa
	xor	xhl,xhl
start_smp1_src
	ld	xix,EMPTY_SAMPLE_BIS
	ld	xiy,xix
	srl	7,xiy
vol_smp1_src
	db	026h,007h	; ld h,7
	ld	l,(xiy)
	ld	xiy,VOL_TABLE
	add	xiy,xhl
	ld	a,(xiy)
freq_smp1_src
	add	xix,0
end_smp1_src
	cp	xix,END_EMPTY_SAMPLE_BIS
	jr	lt,not_end_smp1_src
loop_smp1_src
	ld	xix,EMPTY_SAMPLE_BIS
	cp	xix,EMPTY_SAMPLE_BIS
	jr	nz,not_end_smp1_src
 	xor	xiy,xiy
	ld	(Timer+freq_smp1_src-timer_src+2),xiy
not_end_smp1_src
	ld	(Timer+start_smp1_src-timer_src+1),xix

	xor	bc,bc
start_smp2_src
	ld	xix,EMPTY_SAMPLE_BIS
	ld	xiy,xix
	srl	7,xiy
vol_smp2_src
	db	026h,007h	; ld h,7
	ld	l,(xiy)
	ld	xiy,VOL_TABLE
	add	xiy,xhl
	ld	c,(xiy)
freq_smp2_src
	add	xix,0
end_smp2_src
	cp	xix,END_EMPTY_SAMPLE_BIS
	jr	lt,not_end_smp2_src
loop_smp2_src
	ld	xix,EMPTY_SAMPLE_BIS
	cp	xix,EMPTY_SAMPLE_BIS
	jr	nz,not_end_smp2_src
	xor	xiy,xiy
	ld	(Timer+freq_smp2_src-timer_src+2),xiy
not_end_smp2_src
	ld	(Timer+start_smp2_src-timer_src+1),xix

	xor	de,de
start_smp3_src
	ld	xix,EMPTY_SAMPLE_BIS
	ld	xiy,xix
	srl	7,xiy
vol_smp3_src
	db	026h,007h	; ld h,7
	ld	l,(xiy)
	ld	xiy,VOL_TABLE
	add	xiy,xhl
	ld	e,(xiy)
freq_smp3_src
	add	xix,0
end_smp3_src
	cp	xix,END_EMPTY_SAMPLE_BIS
	jr	lt,not_end_smp3_src
loop_smp3_src
	ld	xix,EMPTY_SAMPLE_BIS
	cp	xix,EMPTY_SAMPLE_BIS
	jr	nz,not_end_smp3_src
	xor	xiy,xiy
	ld	(Timer+freq_smp3_src-timer_src+2),xiy
not_end_smp3_src
	ld	(Timer+start_smp3_src-timer_src+1),xix

	add	bc,de
	srl	1,bc

	xor	de,de
start_smp4_src
	ld	xix,EMPTY_SAMPLE_BIS
	ld	xiy,xix
	srl	7,xiy
vol_smp4_src
	db	026h,007h	; ld h,7
	ld	l,(xiy)
	ld	xiy,VOL_TABLE
	add	xiy,xhl
	ld	e,(xiy)
freq_smp4_src
	add	xix,0
end_smp4_src
	cp	xix,END_EMPTY_SAMPLE_BIS
	jr	lt,not_end_smp4_src
loop_smp4_src
	ld	xix,EMPTY_SAMPLE_BIS
	cp	xix,EMPTY_SAMPLE_BIS
	jr	nz,not_end_smp4_src
	xor	xiy,xiy
	ld	(Timer+freq_smp4_src-timer_src+2),xiy
not_end_smp4_src
	ld	(Timer+start_smp4_src-timer_src+1),xix

	add	wa,de
	srl	1,wa

	ld	w,c
	;ex	w,a
	ld	(0a2h),wa

	pop	xiy
	pop	xix
	pop	xhl
	pop	de
	pop	bc
	pop	wa
	reti

end_timer_src


;**************************************
;* Next mod sequence initialization
;**************************************

mod_seq
	push	xhl
	ld	l,(rSL)
	bit	PADB_A,l
	jr	z,mod_dont_stop
	ldb	(Timer),007h
	pop	xhl
	reti
mod_dont_stop
	push	xix
	push	xde
	ld	xix,(MOD_SONG_PTR)
	xor	xhl,xhl
	ld	l,(xix+)
	cp	l,99
	jr	nz,do_instr1
	inc	xix	
	jr	go_instr2
do_instr1
	sll	2,l
	ld	xde,(MOD_INSTR_TABLE)
	add	xde,xhl
	ld	xde,(xde)
	sll	7,xde
	ld	(Timer+start_smp1_src-timer_src+1),xde
	ld	xde,(MOD_INSTR_END_TABLE)
	add	xde,xhl
	ld	xde,(xde)
	sll	7,xde
	ld	(Timer+end_smp1_src-timer_src+2),xde
	add	xhl,(MOD_INSTR_LOOP_TABLE)
	ld	xde,(xhl)
	sll	7,xde
	ld	(Timer+loop_smp1_src-timer_src+1),xde
	xor	xhl,xhl
	ld	l,(xix+)
	sll	2,l
	add	xhl,FREQ_TABLE
	ld	xde,(xhl)
	ld	(Timer+freq_smp1_src-timer_src+2),xde

go_instr2
	ld	l,(xix+)	; volume
	ld	(Timer+vol_smp1_src-timer_src+1),l

	xor	xhl,xhl
	ld	l,(xix+)
	cp	l,99
	jr	nz,do_instr2
	inc	xix
	jr	go_instr3
do_instr2
	sll	2,l
	ld	xde,(MOD_INSTR_TABLE)
	add	xde,xhl
	ld	xde,(xde)
	sll	7,xde
	ld	(Timer+start_smp2_src-timer_src+1),xde
	ld	xde,(MOD_INSTR_END_TABLE)
	add	xde,xhl
	ld	xde,(xde)
	sll	7,xde
	ld	(Timer+end_smp2_src-timer_src+2),xde
	add	xhl,(MOD_INSTR_LOOP_TABLE)
	ld	xde,(xhl)
	sll	7,xde
	ld	(Timer+loop_smp2_src-timer_src+1),xde
	xor	xhl,xhl
	ld	l,(xix+)
	sll	2,l
	add	xhl,FREQ_TABLE
	ld	xde,(xhl)
	ld	(Timer+freq_smp2_src-timer_src+2),xde
go_instr3
	ld	l,(xix+)
	ld	(Timer+vol_smp2_src-timer_src+1),l

	xor	xhl,xhl
	ld	l,(xix+)
	cp	l,99
	jr	nz,do_instr3
	inc	xix
	jr	go_instr4
do_instr3
	sll	2,l
	ld	xde,(MOD_INSTR_TABLE)
	add	xde,xhl
	ld	xde,(xde)
	sll	7,xde
	ld	(Timer+start_smp3_src-timer_src+1),xde
	ld	xde,(MOD_INSTR_END_TABLE)
	add	xde,xhl
	ld	xde,(xde)
	sll	7,xde
	ld	(Timer+end_smp3_src-timer_src+2),xde
	add	xhl,(MOD_INSTR_LOOP_TABLE)
	ld	xde,(xhl)
	sll	7,xde
	ld	(Timer+loop_smp3_src-timer_src+1),xde
	xor	xhl,xhl
	ld	l,(xix+)
	sll	2,l
	add	xhl,FREQ_TABLE
	ld	xde,(xhl)
	ld	(Timer+freq_smp3_src-timer_src+2),xde
go_instr4
	ld	l,(xix+)
	ld	(Timer+vol_smp3_src-timer_src+1),l

	xor	xhl,xhl
	ld	l,(xix+)
	cp	l,99
	jr	nz,do_instr4
	inc	xix
	jr	end_line
do_instr4
	sll	2,l
	ld	xde,PT_INSTR_TABLE
	add	xde,xhl
	ld	xde,(xde)
	sll	7,xde
	ld	(Timer+start_smp4_src-timer_src+1),xde
	ld	xde,(MOD_INSTR_END_TABLE)
	add	xde,xhl
	ld	xde,(xde)
	sll	7,xde
	ld	(Timer+end_smp4_src-timer_src+2),xde
	add	xhl,(MOD_INSTR_LOOP_TABLE)
	ld	xde,(xhl)
	sll	7,xde
	ld	(Timer+loop_smp4_src-timer_src+1),xde
	xor	xhl,xhl
	ld	l,(xix+)
	sll	2,l
	add	xhl,FREQ_TABLE
	ld	xde,(xhl)
	ld	(Timer+freq_smp4_src-timer_src+2),xde
end_line
	ld	l,(xix+)
	ld	(Timer+vol_smp4_src-timer_src+1),l

	ld	de,(xix+)		; speed
	ld	(COUNT),de

	; test end of song
	cp	xix,(MOD_SONG_END)
	jr	nz,not_song_end
	ld	xix,(MOD_SONG_RESTART)
not_song_end
	ld	(MOD_SONG_PTR),xix

	pop	xde
	pop	xix
	pop	xhl
	reti

	include "freq.inc"
