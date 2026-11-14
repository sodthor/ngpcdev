; function-abi: return=void clobbers=xwa,xhl
	push xbc
	push xde
	push xix
	push xiy
	push xiz
	push xbc1
	push xde1
	push xhl1
	push xwa1
	push xde2
	push xwa2
	push xbc2
	push xhl2
	add xsp, -44	; frame: 44 bytes
	ld xwa, (_worldX)
	ld (xsp+16), xwa
	ld wa, (_teta)
	extz xwa
	ld xde1, xwa
	add xwa, 01c0h
	and xwa, 01ffh
	ld xde2, xwa
	sla 1, xwa
	lda xhl, _tcos
	ld wa, (xhl+wa)
	exts xwa
	ld xhl2, xwa
	ld xhl, xwa
	sla 5, xwa
	sub xwa, xhl
	sra 7, xwa
	ld xbc2, xwa
	ld xwa, xhl2
	ld xhl, xwa
	sla 2, xwa
	add xwa, xhl
	sra 7, xwa
	ld xhl2, xwa
	add xwa, (xsp+16)
	ld (xsp+20), xwa
	ld xwa, xbc2
	sub xwa, xhl2
	sla 6, xwa
	ld xhl2, xwa
	ld xwa, (_worldY)
	ld xbc2, xwa
	ld xwa, xde2
	sla 1, xwa
	lda xhl, _tsin
	ld wa, (xhl+wa)
	exts xwa
	ld xde2, xwa
	ld xhl, xwa
	sla 5, xwa
	sub xwa, xhl
	sra 7, xwa
	ld (xsp+24), xwa
	ld xwa, xde2
	ld xhl, xwa
	sla 2, xwa
	add xwa, xhl
	sra 7, xwa
	ld xde2, xwa
	add xwa, xbc2
	ld (xsp+28), xwa
	ld xwa, (xsp+24)
	sub xwa, xde2
	sla 6, xwa
	ld (xsp+24), xwa
	ld xwa, xde1
	add xwa, 040h
	and xwa, 01ffh
	ld xde1, xwa
	sla 1, xwa
	lda xhl, _tcos
	ld wa, (xhl+wa)
	exts xwa
	ld xde2, xwa
	ld xhl, xwa
	sla 5, xwa
	sub xwa, xhl
	sra 7, xwa
	ld xwa2, xwa
	ld xwa, xde2
	ld xhl, xwa
	sla 2, xwa
	add xwa, xhl
	sra 7, xwa
	ld xde2, xwa
	add xwa, (xsp+16)
	ld (xsp+32), xwa
	ld xwa, xwa2
	sub xwa, xde2
	sla 6, xwa
	ld (xsp+16), xwa
	ld xwa, xde1
	sla 1, xwa
	lda xhl, _tsin
	ld wa, (xhl+wa)
	exts xwa
	ld xde1, xwa
	ld xhl, xwa
	sla 5, xwa
	sub xwa, xhl
	sra 7, xwa
	ld xwa2, xwa
	ld xwa, xde1
	ld xhl, xwa
	sla 2, xwa
	add xwa, xhl
	sra 7, xwa
	ld xde1, xwa
	add xwa, xbc2
	ld (xsp+36), xwa
	ld xwa, xwa2
	sub xwa, xde1
	sla 6, xwa
	ld xbc2, xwa
	ld xbc, (_track)
	; phi copies for block 0
	ld xiy, 1
.Lbb_update_50:
	; phi (materialized by predecessors): %51 = phi i32 [ 1, %0 ], [ %276, %275 ]
	ld xwa, xhl2
	ld xhl, xiy
	divs xwa, hl
	jr OV, .Ldiv_fb_update_0
	exts xwa
	jr .Ldiv_dn_update_0
	.Ldiv_fb_update_0:
	ld xwa, xhl2
	push xde
	push xwa
	push xhl
	call _C9H_divls
	inc 8, xsp
	ld xwa, xhl
	pop xde
	.Ldiv_dn_update_0:
	ld xhl, xwa
	ld xwa, (xsp+20)
	add xhl, xwa
	ld xwa, xhl
	ld xde1, xwa
	ld xwa, (xsp+24)
	ld xhl, xiy
	divs xwa, hl
	jr OV, .Ldiv_fb_update_1
	exts xwa
	jr .Ldiv_dn_update_1
	.Ldiv_fb_update_1:
	ld xwa, (xsp+24)
	push xde
	push xwa
	push xhl
	call _C9H_divls
	inc 8, xsp
	ld xwa, xhl
	pop xde
	.Ldiv_dn_update_1:
	ld xhl, xwa
	ld xwa, (xsp+28)
	add xhl, xwa
	ld xwa, xhl
	ld xde2, xwa
	ld xwa, (xsp+16)
	ld xhl, xiy
	divs xwa, hl
	jr OV, .Ldiv_fb_update_2
	exts xwa
	jr .Ldiv_dn_update_2
	.Ldiv_fb_update_2:
	ld xwa, (xsp+16)
	push xde
	push xwa
	push xhl
	call _C9H_divls
	inc 8, xsp
	ld xwa, xhl
	pop xde
	.Ldiv_dn_update_2:
	ld xwa2, xwa
	ld xwa, (xsp+32)
	sub xwa, xde1
	add xwa, xwa2
	ld xde, xwa
	ld xwa, xbc2
	ld xhl, xiy
	divs xwa, hl
	jr OV, .Ldiv_fb_update_3
	exts xwa
	jr .Ldiv_dn_update_3
	.Ldiv_fb_update_3:
	ld xwa, xbc2
	push xde
	push xwa
	push xhl
	call _C9H_divls
	inc 8, xsp
	ld xwa, xhl
	pop xde
	.Ldiv_dn_update_3:
	ld xwa2, xwa
	ld xwa, (xsp+36)
	sub xwa, xde2
	add xwa, xwa2
	ld xix, xwa
	ld xwa, xiy
	sla 4, xwa
	and xwa, 0380h
	sla 1, xwa
	add xwa, 0a000h
	ld (xsp+40), xwa
	ld wa, iy
	and xwa, 7
	ld xwa2, xwa
	ld xhl, (xsp+40)
	sla 1, xwa
	add xhl, xwa
	ld (xsp+0), xhl
	ld xwa, xde1
	sla 7, xwa
	ld (xsp+4), xwa
	ld xwa, xde2
	sla 7, xwa
	ld (xsp+8), xwa
.Lbb_update_69:
	push xbc
	push xiy
	; experimental loop frame: 0 bytes
	ld xiy, (xsp+8)
	ld xwa1, 0
	ld xhl, (xsp+12)
	ld xde2, xbc
	ld xbc, (xsp+16)
	ld xbc1, xde
	ld xhl1, xix
.Lbb_update_alloc_69:
	ld de, qbc
	or de, qhl
	cp de, 2
	jr C, .Lbb_update_alloc_77
	ld xde, 3
	jr .Lbb_update_alloc_90
.Lbb_update_alloc_77:
	ld xde, xhl
	srl 8, xde
	ld xix, xbc
	srl 8, xix
	sla 9, xix
	add xix, xde2
	ld xwa, 0
	ld a, (xix+de)
	ld xde, xwa
	bit 2, de
	jr Z, .Lbb_update_alloc_90
.Lbb_update_alloc_87:
	sub de, 3
	sla 16, xde
.Lbb_update_alloc_90:
	add xhl, xbc1
	add xbc, xhl1
	sla 2, xde
	ld ix, qbc
	or ix, qhl
	cp ix, 2
	jr C, .Lbb_update_alloc_99
.Lbb_update_alloc_97:
	or xde, 3
	jr .Lbb_update_alloc_115
.Lbb_update_alloc_99:
	ld xix, xhl
	srl 8, xix
	ld xiz, xbc
	srl 8, xiz
	sla 9, xiz
	add xiz, xde2
	ld a, (xiz+ix)
	bit 2, a
	jr Z, .Lbb_update_alloc_113
.Lbb_update_alloc_109:
	ld w, 0
	sub wa, 3
	sla 16, xwa
	or xde, xwa
.Lbb_update_alloc_113:
	or e, a
.Lbb_update_alloc_115:
	add xhl, xbc1
	add xbc, xhl1
	sla 2, xde
	ld ix, qbc
	or ix, qhl
	cp ix, 2
	jr C, .Lbb_update_alloc_124
.Lbb_update_alloc_122:
	or xde, 3
	jr .Lbb_update_alloc_140
.Lbb_update_alloc_124:
	ld xix, xhl
	srl 8, xix
	ld xiz, xbc
	srl 8, xiz
	sla 9, xiz
	add xiz, xde2
	ld a, (xiz+ix)
	bit 2, a
	jr Z, .Lbb_update_alloc_138
.Lbb_update_alloc_134:
	ld w, 0
	sub wa, 3
	sla 16, xwa
	or xde, xwa
.Lbb_update_alloc_138:
	or e, a
.Lbb_update_alloc_140:
	add xhl, xbc1
	add xbc, xhl1
	sla 2, xde
	ld ix, qbc
	or ix, qhl
	cp ix, 2
	jr C, .Lbb_update_alloc_149
.Lbb_update_alloc_147:
	or xde, 3
	jr .Lbb_update_alloc_165
.Lbb_update_alloc_149:
	ld xix, xhl
	srl 8, xix
	ld xiz, xbc
	srl 8, xiz
	sla 9, xiz
	add xiz, xde2
	ld a, (xiz+ix)
	bit 2, a
	jr Z, .Lbb_update_alloc_163
.Lbb_update_alloc_159:
	ld w, 0
	sub wa, 3
	sla 16, xwa
	or xde, xwa
.Lbb_update_alloc_163:
	or e, a
.Lbb_update_alloc_165:
	add xhl, xbc1
	add xbc, xhl1
	sla 2, xde
	ld ix, qbc
	or ix, qhl
	cp ix, 2
	jr C, .Lbb_update_alloc_174
.Lbb_update_alloc_172:
	or xde, 3
	jr .Lbb_update_alloc_190
.Lbb_update_alloc_174:
	ld xix, xhl
	srl 8, xix
	ld xiz, xbc
	srl 8, xiz
	sla 9, xiz
	add xiz, xde2
	ld a, (xiz+ix)
	bit 2, a
	jr Z, .Lbb_update_alloc_188
.Lbb_update_alloc_184:
	ld w, 0
	sub wa, 3
	sla 16, xwa
	or xde, xwa
.Lbb_update_alloc_188:
	or e, a
.Lbb_update_alloc_190:
	add xhl, xbc1
	add xbc, xhl1
	sla 2, xde
	ld ix, qbc
	or ix, qhl
	cp ix, 2
	jr C, .Lbb_update_alloc_199
.Lbb_update_alloc_197:
	or xde, 3
	jr .Lbb_update_alloc_215
.Lbb_update_alloc_199:
	ld xix, xhl
	srl 8, xix
	ld xiz, xbc
	srl 8, xiz
	sla 9, xiz
	add xiz, xde2
	ld a, (xiz+ix)
	bit 2, a
	jr Z, .Lbb_update_alloc_213
.Lbb_update_alloc_209:
	ld w, 0
	sub wa, 3
	sla 16, xwa
	or xde, xwa
.Lbb_update_alloc_213:
	or e, a
.Lbb_update_alloc_215:
	add xhl, xbc1
	add xbc, xhl1
	sla 2, xde
	ld ix, qbc
	or ix, qhl
	cp ix, 2
	jr C, .Lbb_update_alloc_224
.Lbb_update_alloc_222:
	or xde, 3
	jr .Lbb_update_alloc_240
.Lbb_update_alloc_224:
	ld xix, xhl
	srl 8, xix
	ld xiz, xbc
	srl 8, xiz
	sla 9, xiz
	add xiz, xde2
	ld a, (xiz+ix)
	bit 2, a
	jr Z, .Lbb_update_alloc_238
.Lbb_update_alloc_234:
	ld w, 0
	sub wa, 3
	sla 16, xwa
	or xde, xwa
.Lbb_update_alloc_238:
	or e, a
.Lbb_update_alloc_240:
	add xhl, xbc1
	add xbc, xhl1
	sla 2, xde
	ld ix, qbc
	or ix, qhl
	cp ix, 2
	jr C, .Lbb_update_alloc_249
.Lbb_update_alloc_247:
	or xde, 3
	jr .Lbb_update_alloc_265
.Lbb_update_alloc_249:
	ld xix, xhl
	srl 8, xix
	ld xiz, xbc
	srl 8, xiz
	sla 9, xiz
	add xiz, xde2
	ld a, (xiz+ix)
	bit 2, a
	jr Z, .Lbb_update_alloc_263
.Lbb_update_alloc_259:
	ld w, 0
	sub wa, 3
	sla 16, xwa
	or xde, xwa
.Lbb_update_alloc_263:
	or e, a
.Lbb_update_alloc_265:
	add xhl, xbc1
	add xbc, xhl1
	ld (xiy+16), de
	srl 16, xde
	ld (xiy+2064), de
	inc 1, rwa1
	add xiy, 010h
	cp rwa1, 010h
	jrl C, .Lbb_update_alloc_69
.Lbb_update_alloc_exit:
	pop xiy
	pop xbc
.Lbb_update_275:
	ld xwa, xiy
	add xwa, 1
	ld (xsp+12), xwa
	cp xwa, 040h
	jr Z, .Lbb_update_278
	; phi copies for edge 275 -> 50
	ld xiy, xwa
	jrl .Lbb_update_50
.Lbb_update_278:
	add xsp, 44	; free frame
	pop xhl2
	pop xbc2
	pop xwa2
	pop xde2
	pop xwa1
	pop xhl1
	pop xde1
	pop xbc1
	pop xiz
	pop xiy
	pop xix
	pop xde
	pop xbc
	ret
; experimental-register-function-end update
