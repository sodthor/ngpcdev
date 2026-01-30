;================================================================
; PSGlib - Programmable Sound Generator audio library - by sverx
;          https://github.com/sverx/PSGlib
;================================================================

    cpu    z80
    org    0h

PSG_STOPPED    EQU 0
PSG_PLAYING    EQU 1

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
; d : PSG_STOPPED/PSG_PLAYING
; e : frames to skip
; d': substring length
; e': loop flag
;hl': music ptr
; ix: PSGMusicLoopPoint
; iy: PSGMusicSubstringRetAddr

_start_z80:
  di
  ld sp, PSGStop

  ld d,PSG_STOPPED            ; PSGMusicStatus in d: set music status to PSG_STOPPED
  jr _mainLoop

; stack: max 4 bytes : ret PSGStop x 2

PSGStop: ; called by rst 08h
  ld hl,04000h
  rst 10h ; call PSGStop_
  inc l ; hl = 04001h
  ld d,PSG_STOPPED                                  ; set status to PSG_STOPPED
  nop                                               ; filler to align PSGStop_ to 010h
PSGStop_:; called by rst 10h
  ld (hl),PSGLatch|PSGChannel0|PSGVolumeData|00Fh   ; latch channel 0, volume=0xF (silent)
  ld (hl),PSGLatch|PSGChannel1|PSGVolumeData|00Fh   ; latch channel 1, volume=0xF (silent)
  ld (hl),PSGLatch|PSGChannel2|PSGVolumeData|00Fh   ; latch channel 2, volume=0xF (silent)
  ld (hl),PSGLatch|PSGChannel3|PSGVolumeData|00Fh   ; latch channel 3, volume=0xF (silent)
  ret

_dontLoop:
  rst 08h              ; call PSGStop
_done:
  exx

_mainLoop:
  xor a
  ld hl, 08000h
  ld (hl),a
_waitLoop:
  add a,(hl)
  jr z, _waitLoop
  xor 0ffh
  jr nz, _runCommand
;PSGFrame:
  add a,d         ; check if we have got to play a tune (a is 0 here)
  jr z, _waitLoop ; no music to play : return
  ld a,e          ; check if we have got to skip frames
  or a
  jr z,_noFrameSkip
;_skipFrame:
  dec e
  jr _mainLoop

_runCommand: ; a is 3 (loop) or 2 (no loop) or 1 (stop)
  rst 08h ; call PSGStop ; keeps 'a' safe
  dec a
  jr z,_mainLoop
  dec a
;PSGPlay:
  exx
  ld e,a                          ; loop flag
  ld hl,_music_start_             ; music ptr in hl'
  push hl                         ; default loop pointer points to begin too
  pop ix
  xor a
  ld d,a                          ; reset the substring len (for compression)
_setFrameSkip:
  exx
  ld d,PSG_PLAYING                ; PSGMusicStatus in d: set status to PSG_PLAYING
  and 007h                        ; take only the last 3 bits for skip frames
  ld e,a                          ; PSGMusicSkipFrames in e: reset the skip frames (or we got additional frames when coming from below)
  jr _mainLoop

_noFrameSkip:
  exx
_intLoop:
  ld b,(hl)                      ; load PSG byte (in B)
  inc hl                         ; point to next byte
  ld a,d                         ; read substring len
  or a
  jr z,_continue                 ; check if it is 0 (we are not in a substring)
  dec d                          ; decrease len
  jr nz,_continue
  push iy
  pop hl                         ; substring is over, retrieve return address (PSGMusicSubstringRetAddr)

_continue:
  ld a,b                         ; copy PSG byte into A
  ld bc, 04000h
  cp PSGLatch                    ; is it a latch?
  jr c,_noLatch                  ; if < $80 then it is NOT a latch
  
  ; we have got the latch PSG byte both in A and in B
  ; and we have to check if the value should pass to PSG or not
  bit 4,a                        ; test if it is a volume
  jr z,_noVolume                 ; jump if not volume data
  ; set volume
  ld (bc),a
_send2PSG:
  inc bc                         ; bc = 04001h
_send2PSG_:
  ld (bc),a                      ; output the byte to 04000h or 04001h
  jr _intLoop

_noVolume:
  cp 0E0h
  jr c,_send2PSG                 ; send data to PSG if it is for channels 0-1 or 2 (04001h)
  jr _send2PSG_                  ; output the byte in noise register (04000h)

_noLatch:
  cp PSGData
  jr nc,_send2PSG                ; if < $40 then it is a command -> send to 04001h
;_command:
  cp PSGWait
  jr z,_done                     ; no additional frames
  jr nc,_setFrameSkip            ; other commands?
;_otherCommands:
  cp PSGSubString
  jr nc,_substring
  or a                           ; cp PSGEnd (PSGEnd is 0)
  jr nz,_setLoopPoint
  add a,e                       ; looping requested? (a is 0 here)
  jr z,_dontLoop                 ; No:stop it! (tail call optimization)
  push ix
  pop hl
  jr _intLoop

_setLoopPoint:
  push hl
  pop ix
  jr _intLoop

_substring:
  ld c,(hl)                      ; load substring address (offset)
  inc hl
  ld b,(hl)
  inc hl
  push hl
  pop iy                         ; PSGMusicSubstringRetAddr
  ld hl,_music_start_
  add hl,bc                      ; make substring current
  sub PSGSubString-4             ; len is value - $08 + 4
  ld d,a                         ; save len
  jr _intLoop

_music_start_:

  END
