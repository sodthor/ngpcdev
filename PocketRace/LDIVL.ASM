;TLCS-900 C Compiler Runtime Routine
; Copyright(C) 1994 TOSHIBA CORPORATION ALL RIGHT RESERVED
;
;#SDivMod --- 32x32 division
;		D==0 then mod , D==1 then div
$maximum	
f_code		section	code	large
		public	SDivMod
SDivMod:
		LD		E,0
		BIT		15,QWA
		J		Z,PLUS_A
		LD		E,1
		CPL		QWA		; change dividend sign to plus
		CPL		WA
		INC		1,XWA
PLUS_A:
		BIT		15,QBC
		J		Z,PLUS_B
		OR		E,2
		CPL		QBC		; change divisor sign to plus
		CPL		BC
		INC		1,XBC
PLUS_B:
		PUSH	DE
		CAL		udiv32	; XHL -- quotient   XDE -- remainder
		POP		WA
		CP		W,1		; quotient ?
		J		Z,DIVI
		LD		XHL,XDE
		BIT		0,A
		SCC		NZ,A
		J		FIN
DIVI:
		CP		A,3		; MINUS div MINUS
		RET		Z
FIN:
		OR		XHL,XHL
		RET		Z
		CP		A,0		; check dividend and divisor sign
		RET		Z
UMINUS:
		CPL		QHL
		CPL		HL
		INC		1,XHL
		RET

;#C9H_remls --- remainder (signed)
;		XHL = XWA mod XBC
;		The remainder sign is equal to the dividend sign.
;		-5 mod  3 --> -2 
;		 5 mod -3 -->  2
f_code		section	code	large
		public	C9H_remls
C9H_remls:
		LD		D,0
		J		SDivMod
;
;#C9H_divls -- quotient (signed)
;		XHL = XWA / XBC
;		The remainder sign is equal to the dividend sign.
f_code		section code	large
		public	C9H_divls
C9H_divls:
		LD		D,1
		J		SDivMod

;#C9H_remlu --- remainder (unsigned)
f_code		section	code	large
		public	C9H_remlu
C9H_remlu:
		CAL		udiv32
		LD		XHL,XDE
		RET
;
;#C9H_divu --- quotient (unsigned)
;#udiv32 
;		XWA / XBC = XHL ... XDE
f_code		section code	large
		public	udiv32
		public	C9H_divlu
C9H_divlu:
udiv32:
		cp		XBC,1
		j		eq,DIV_1		;		XWA / 1
		j		ult,DIV_0		;		XWA / 0
		cp		XWA,XBC
		j		ule,DIV_leq		;		XWA <= XBC
		; QBC==0 ?
		cp		QBC,0
		j		ne,DIV_shift
		ld		XDE,XWA			; save XWA
		div		XWA,BC
		j		ov,DIV_ov
		; result is not overflow
		ld		XHL,0
		ld		XDE,XHL
		ld		HL,WA
		ld		DE,QWA
		ret
DIV_ov:	; overflow
		ld		WA,QDE	
		extz	XWA
		div		XWA,BC
		ld		QHL,WA
		ld		WA,DE
		div		XWA,BC
		ld		HL,WA
		ld		DE,QWA
		extz	XDE
		ret
DIV_1:
		ld		XHL,XWA
		ld		XDE,0
		ret
DIV_0:
		ld		XHL,0
		ld		XDE,XHL
		dec		1,XHL
		ret
DIV_leq:
		ld		XHL,1
		ld		XDE,0
		ret		eq				;		XWA == XBC
		dec		1,XHL
		ld		XDE,XWA
		ret						;		XWA < XBC
		; division by shifting
DIV_shift:
		; adjust bit position
ADJUST:
		ld		D,0
loop:
		cp		XWA,XBC
;;; 	j		eq,skip
		j		ult,next2
		inc		1,D				;		count bit
		add		XBC,XBC			;		xbc << 1
		jr		nc,loop			;		xbc overflow
next:
;		sra		1,XBC			;		XBC /= 2
		rr		1,XBC			;		XBC /= 2
		j		skip
next2:
		srl		1,XBC
skip:
		ld		XHL,0
loop2:
		add		XHL,XHL			;		XHL << 1
		cp		XWA,XBC
		j		ult,count		;		? XWA < XBC
		set		0,L
		sub		XWA,XBC
count:
		srl		1,XBC			;		XBC >> 1
		djnz	D,loop2
		ld		XDE,XWA
		ret
		end
