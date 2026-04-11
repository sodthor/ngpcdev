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
; d : PSG_STOPPED/PSG_PLAYING
; e : frames to skip
; b': substring length
; c': loop flag
;hl': music ptr

_start_z80:
  ; 6 bytes
  di
  ld hl, 08000h
  jr PSGStop
_stack: ; 6 bytes needed: loop, substring and rst

_noFrameSkip:
  ; 2 bytes
  exx
  rst 010h ; jr _intLoop

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
  ld a,(hl)                      ; load PSG byte directly into A!
  inc hl                         ; point to next byte
  inc b                          ; test b for 0
  dec b                          ; restoring b, sets Z if b==0
  jr z,_continue                 ; check if it is 0 (we are not in a substring)
  djnz _continue                 ; decrease len
  pop hl                         ; substring completed, get back music ptr from stack
_continue:
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
  ; 24 bytes
  cp PSGData
  jr nc,_send2PSG                ; if < $40 then it is a command else send to 04001h
;_command:
  cp PSGWait
  jr nc,_setFrameSkip            ; other commands?
;_otherCommands:
  cp PSGSubString
  jr nc,_substring
  pop de
  or a                           ; cp PSGEnd (PSGEnd is 0)
  jr nz,_setLoopPoint
  or c                           ; looping requested? (a is 0 here)
  jr z,_dontLoop                 ; No:stop it! (tail call optimization)
  ex hl,de
_setLoopPoint:
  push hl
  rst 010h ; jr _intLoop

_substring:
  ; 13 bytes
  ld e,(hl)                      ; load substring address (offset)
  inc hl
  ld d,(hl)
  inc hl
  push hl
  ld hl,_music_start_
  add hl,de                      ; make substring current
  sub PSGSubString-4             ; len is value - $08 + 4
  ld b,a                         ; save len
  rst 010h ; jr _intLoop

_dontLoop:
  ; 14 bytes
  exx
PSGStop:
  ld sp, _stack                  ; to be sure stack is clean
  ld bc,04000h
  rst 08h
  inc c
  rst 08h
  ld d, 0                        ; PSGMusicStatus in d: set status to PSG_STOPPED
_mainLoop:
  xor a
  ld (hl),a
_waitLoop:
  add a,(hl)
  jr z, _waitLoop
  dec a
  jr nz, _runCommand
;PSGFrame:
  or d               ; check if we have got to play a tune (a is 0 here)
  jr z, _mainLoop    ; no music to play : return
  and e              ; check if we have got to skip frames (a is not 0 here)
  jr z,_noFrameSkip
;_skipFrame:
  dec e
  jr _mainLoop

_runCommand: ; a is 3 (loop) or 2 (no loop) or 1 (stop)
  ; 36 bytes
  dec a
  jr z,PSGStop
;PSGPlay:
  exx
  dec a
  ld c,a                          ; loop flag
  ld hl,_music_start_             ; music ptr in hl'
  push hl                         ; default loop pointer points to begin too
  xor a
  ld b,a                          ; reset the substring len (for compression)
_setFrameSkip:
  exx
  ld d,PSG_PLAYING                ; PSGMusicStatus in d: set status to PSG_PLAYING
  and d                           ; take only the last 3 bits for skip frames (that's why PSG_PLAYING=7)
  ld e,a                          ; PSGMusicSkipFrames in e: reset the skip frames (or we got additional frames when coming from below)
  jr _mainLoop

_music_start_:

  END