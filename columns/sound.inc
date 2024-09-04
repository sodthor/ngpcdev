VBL_OK	EQU	7003h
NOTE1	EQU	7004h
NOTE2	EQU	7005h
NOTE3	EQU	7006h
FREQ1   EQU	7007h
FREQ2   EQU	7009h
FREQ3   EQU	700bh
VOLUME1 EQU	700dh
VOLUME2 EQU	700eh
VOLUME3 EQU	700fh

VOL0	EQU	0Fh	; off
VOL1	EQU	0Eh
VOL2	EQU	0Dh
VOL3	EQU	0Ch
VOL4	EQU	0Bh
VOL5	EQU	0Ah
VOL6	EQU	09h
VOL7	EQU	08h
VOL8	EQU	07h
VOL9	EQU	06h
VOL10	EQU	05h
VOL11	EQU	04h
VOL12	EQU	03h
VOL13	EQU	02h
VOL14	EQU	01h
VOL15	EQU	00h	; max

MAXNOTE	EQU	50

	;**********

initsound
	ldw	(000b8h),0aaaah
        ld      xhl,SOUND_DRV
        ld      xde,07000h
        ld      xbc,END_SOUND_DRV - SOUND_DRV
	ldirb	(xde+),(xhl+)
	ldb	(MUSIC_ON),0
	ret

	;**********

initmusic
	calr	stopmusic
	ld	(MUSPTR),xwa
	ld	(ENDPTR),xbc
	calr	initall
	ret

	;**********

startmusic
	ldw	(000b8h),05555h
	ldb	(MUSIC_ON),1
	ldb	(MUSIC_CTL),1
	ret

	;**********

stopmusic
	cpb	(MUSIC_ON),0
	jr	z,musicstopped
	ldb	(MUSIC_ON),0
waitstop
	cpb	(MUSIC_CTL),0
	jr	nz,waitstop
musicstopped
	ret

	;**********

stopsound
	ldw	(000b8h),0aaaah
	ret

	;**********

playmusic
	cpb	(MUSIC_ON),0
	jr	nz,play_it
	ldb	(MUSIC_CTL),0
	ret
play_it
	push	xbc
	push	xde
	push	xhl
	ld	xhl,OUTBUF
getdata
	calr	lzwdecomp
	ld	xbc,(OUTPTR)
	sub	xbc,xhl
	cpb	(RDMASK),0ffh
	jr	nz,dataread
	cp	bc,9
	jr	ge,playdata
	jr	endplay
dataread
	cp	bc,9
	jr	lt,getdata
playdata
	ld	bc,9
	ld	xde,FREQ1
	ldirb	(xde+),(xhl+)

	ld	xde,OUTBUF
	ld	xbc,(OUTPTR)
	sub	xbc,xhl
	jr	z,nodatamove
	ldirb	(xde+),(xhl+)
nodatamove
	ld	(OUTPTR),xde

	ldb	(VBL_OK),2	; play freq
endplay
	cpb	(RDMASK),0ffh
	jr	nz,retplay
	calr	initall
retplay
	pop	xhl
	pop	xde
	pop	xbc
	ret

	;**********

reinitbuf
	calr	readCode
	ld	(CW),de
	push	xhl
	ld	xhl,(OUTPTR)
	ld	(xhl+),e
	ld	(OUTPTR),xhl
	ldw	(DSIZE),256
	ld	xhl,LZWBUF
	ld	(LZWPTR),xhl
	ld	xhl,LZWIDX
	ldw	(xhl+),0
	ld	(LZWIPTR),xhl
	pop	xhl
	ret
initall
	push	xhl
	ld	xhl,(MUSPTR)
	ld	(CURPTR),xhl
	ld	xhl,OUTBUF
	ld	(OUTPTR),xhl
	pop	xhl
	ldb	(RDMASK),0
	calr	reinitbuf
	ret

	;**********

lzwdecomp
	push	xwa
	push	xde
decomp
	ld	xde,0
	calr	readcode
	cp	de,0ffffh
	jr	z,enddecomp
	cp	de,511
	jr	nz,notclearcode
	calr	reinitbuf
	jr	decomp
notclearcode
	ld	wa,(CW)
	ld	(PW),wa
	ld	(CW),de
	cp	de,(DSIZE)
	jr	z,not_in_dict
	calr	writecode
	calr	addentry
	jr	enddecomp
not_in_dict
	ld	(CW),de
	calr	addentry
	calr	writecode
enddecomp
	pop	xde
	pop	xwa
	ret

	;**********

addentry
	push	xhl
	push	xde
	push	xbc
	push	xwa
	ld	xde,0
	ld	xhl,PW
	ld	de,(xhl)
	cp	d,0
	jr	nz,findstring
	push	xhl
	inc 	xhl
	push	xhl
	jr	addstring
findstring
	calr	getbuf
	push	xhl
	inc	de
	calr	getbuf
	push	xhl
addstring
	ld	de,(DSIZE)
	calr	getbuf
	ld	xde,xhl
	pop	xbc
	pop	xhl
	sub	xbc,xhl
	ldirb	(xde+),(xhl+)
	push	xde
	ld	xde,0
	ld	de,(CW)
	cp	d,0
	jr	nz,findstring2
	ld	xhl,CW
	jr	addstring2
findstring2
	calr	getbuf
addstring2
	pop	xde
	ld	b,(xhl+)
	ld	(xde+),b
	ld	xhl,(LZWIPTR)
	ld	xwa,LZWBUF
	sub	xde,xwa
	ld	(xhl+),de
	ld	(LZWIPTR),xhl
	incw	(DSIZE)
	pop	xwa
	pop	xbc
	pop	xde
	pop	xhl
	ret

	;**********

getbuf
	push	xwa
	push	xde
	ld	xhl,LZWIDX
	sub	de,256
	sll	1,de
	add	xde,xhl
	ld	xwa,0
	ld	wa,(xde)
	ld	xhl,LZWBUF
	add	xhl,xwa
	pop	xde
	pop	xwa
	ret

	;**********

writecode
	push	xhl
	cp	d,0
	jr	nz,writestring
	ld	xhl,(OUTPTR)
	ld	(xhl+),e
	ld	(OUTPTR),xhl
	jr	endwritecode
writestring
	push	xde
	push	xbc
	calr	getbuf
	push	xhl
	inc	de
	calr	getbuf
	ld	xbc,xhl
	pop	xhl
	sub	xbc,xhl
	ld	xde,(OUTPTR)
	ldirb	(xde+),(xhl+)
	ld	(OUTPTR),xde
	pop	xbc
	pop	xde
endwritecode
	pop	xhl
	ret

	;**********

readbyte
	push	xhl
	push	xwa
	ld	xhl,(CURPTR)
	ld	a,(xhl+)
	ld	(RDBYTE),a
	ld	xwa,(ENDPTR)
	inc	xwa
	cp	xhl,xwa
	jr	nz,dontloop
	ldb	(RDMASK),0ffh
	jr	endread
dontloop
	ld	(CURPTR),xhl
endread
	pop	xwa
	pop	xhl
	ret

	;**********

readcode
	push	wa
	push	bc
	ld	xde,0
	ld	a,(RDMASK)
	ld	bc,9
read9bits
	cp	a,0
	jr	nz,dontread
	calr	readbyte
	cpb	(RDMASK),0ffh
	jr	nz,notendfile
	ld	de,0ffffh
	jr	endreadcode
notendfile
	ld	a,080h
dontread
	sll	1,de
	ld	w,(RDBYTE)
	and	w,a
	jr	z,zeroread
	or	de,1
zeroread
	srl	1,a
	djnz	bc,read9bits
	ld	(RDMASK),a
endreadcode
	pop	bc
	pop	wa
	ret

	;**********

SOUND_DRV
	include "drv.inc"
END_SOUND_DRV

	RAMDD	MUSPTR		; music start address
	RAMDD	ENDPTR		; music end address
	RAMDD	CURPTR		; next music decoding byte
	RAMDB	MUSIC_ON	; play status ON = 1, OFF = 0
	RAMDB	MUSIC_CTL	; stop status WAIT = 1, STOPPED = 0
	RAMDD	LZWIPTR		; free index entry
	RAMDD	LZWPTR		; free dictionnary entry
	RAMDD	OUTPTR		; output buffer address
	RAMDB	RDMASK		; read mask
	RAMDB	RDBYTE		; decoding byte
	RAMDW	DSIZE		; number of dictionnary entries
	RAMDW	PW		; previous read code
	RAMDW	CW		; last read code
	RAMBUF	LZWIDX,256*2	; code indexes in dictionnary
	RAMBUF	LZWBUF,1024	; dictionnary
	RAMBUF	OUTBUF,128	; output buffer
