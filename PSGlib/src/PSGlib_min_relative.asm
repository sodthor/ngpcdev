;================================================================
; PSGlib - Programmable Sound Generator audio library - by sverx
;          https://github.com/sverx/PSGlib
;================================================================

    cpu    z80
    org    0h

PSG_STOPPED    EQU 0
PSG_PLAYING    EQU 7

PSGLatch       EQU 080h
PSGData        EQU 040h

PSGChannel0    EQU 000000000b
PSGChannel1    EQU 000100000b
PSGChannel2    EQU 001000000b
PSGChannel3    EQU 001100000b
PSGVolumeData  EQU 000010000b

PSGWait        EQU 038h
PSGSubString   EQU 008h

; in registers:
; hl: 08000h 
; bc: 04000h
; d : PSG_STOPPED/PSG_PLAYING
; e : frames to skip
; b': substring length
; c': loop flag
;hl': music ptr

_start_z80:
  ; 8 bytes
  ld hl, 08000h
  ld bc, 04000h
  jr PSGStop
_stack: ; 6 bytes needed: loop, substring and rst

;PSGStop_: called by rst 08h
  ; 8 bytes
  ld a,PSGLatch|PSGVolumeData|00Fh ; channel volume off
_stopLoop:
  ld (bc),a
  add a,020h                       ; next channel
  jr nc,_stopLoop
  ret

; called by rst 010h
  ; 31 bytes
  pop de ; remove return address from stack
;_intLoop:
  ld a,(hl)                      ; load PSG byte directly into A
  inc b                          ; test b for 0
  dec b                          ; restoring b, sets Z if b==0
  jr z,_continue                 ; check if it is 0 (we are not in a substring)
  djnz _continue                 ; decrease len
  pop hl                         ; substring completed, get back music ptr from stack
_continue:
  inc hl                         ; point to next byte
  ld de, 04000h
  cp PSGLatch                    ; is it a latch?
  jr c,_noLatch                  ; if < $80 then it is NOT a latch
  ; we have got the latch PSG byte in A and we have to check if the value should pass to PSG or not
  bit 4,a                        ; test if it is a volume
  jr z,_noVolume                 ; jump if not volume data
;_setVolume
  ld (de),a
_send2PSG:
  inc e                          ; de = 04001h
_send2PSG_:
  ld (de),a                      ; output the byte to 04000h or 04001h
  rst 010h ; jr _intLoop
_noVolume:
  cp 0E0h
  jr c,_send2PSG                 ; send data to PSG if it is for channels 0-1 or 2 (04001h)
  jr _send2PSG_                  ; output the byte in noise register (04000h)

_noLatch:
  ; 21 bytes
  cp d                           ; d is 040h here
  jr nc,_send2PSG                ; if < $40 then it is a command else send to 04001h
;_command:
  cp PSGWait
  jr nc,_setFrameSkip            ; other commands?
;_otherCommands:
  cp PSGSubString
  jr nc,_substring
  pop de                         ; get current loop point
  or a                           ; cp PSGEnd (PSGEnd is 0)
  jr nz,_setLoopPoint
  or c                           ; looping requested? (a is 0 here)
  jr z,_dontLoop                 ; No:stop it! (tail call optimization)
  ex hl,de                       ; reset hl to loop point
_setLoopPoint:
  push hl                        ; save loop point in stack
  rst 010h ; jr _intLoop

_substring:
  ; 24 bytes
  sub PSGSubString-3             ; len is value - $08 + MIN_LEN (MIN_LEN = 3)
  ld b,a                         ; save len
  ld d,(hl)                      ; load substring address (offset)
  bit 7,d                        ; check if relative address
  jr z, _dbl                     ; if not get second byte
  push hl                        ; do not increment hl: will be done on "_continue"
  ld a, d
  sub b
  ld e, a
  ld d, 0ffh                     ; negative relative
  jr _next
_dbl:
  inc hl
  ld e,(hl)
  push hl                        ; do not increment hl: will be done on "_continue"
  ld hl,_music_start_
_next:
  add hl,de                      ; make substring current
  rst 010h ; jr _intLoop

_dontLoop:
  ; 26 bytes
  exx
PSGStop:
  ld sp, _stack                  ; to be sure stack is clean
  rst 08h
  inc c                          ; bc = 04001h
  rst 08h
  dec c                          ; bc = 04000h
  ld d,c                         ; PSGMusicStatus in d: set status to PSG_STOPPED (0)
_skipFrame:
  dec e
_mainLoop:
  xor a
  ld (hl),a                      ; clear comm
_waitLoop:
  or (hl)                        ; check comm
  jr z, _waitLoop
  dec a
  jr nz, _runCommand
;PSGFrame:
  or d               ; check if we have got to play a tune (a is 0 here)
  jr z, _mainLoop    ; no music to play : return
  and e              ; check if we have got to skip frames (a is 7 here)
  jr nz, _skipFrame
;_noFrameSkip:
  exx
  rst 010h ; jr _intLoop

_runCommand: ; a is 3 (loop) or 2 (no loop) or 1 (stop)
  ; 17 bytes
  dec a
  jr z,PSGStop
;PSGPlay:
  ld d,PSG_PLAYING                ; PSGMusicStatus in d: set status to PSG_PLAYING
  exx
  dec a
  ld c,a                          ; loop flag in c'
  ld hl,_music_start_             ; music ptr in hl'
  push hl                         ; default loop pointer points to _music_start_
  ld b,h                          ; reset the substring len in b' (for compression), here h' is 0 as _music_start_ is < 256
  ; here a is 0 or 1 and will be used as initial frameskip in e
_setFrameSkip:
  exx
  ld e,a                          ; PSGMusicSkipFrames in e: reset the skip frames (higher bits ignored)
  jr _mainLoop

_music_start_:

  END