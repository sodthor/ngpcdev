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
PSGLoop        EQU 001h

; in registers:
; d : PSG_STOPPED/PSG_PLAYING
; e : frames to skip
; c': substring length
; d': loop flag
;hl': music ptr
; ix: PSGMusicLoopPoint
; iy: PSGMusicSubstringRetAddr

_start_z80:
  di
  ld sp,_mainLoop
		 
  xor a             ; ld a,PSG_STOPPED
  ld d,a            ; PSGMusicStatus in d: set music status to PSG_STOPPED
  ; stack: max 4 bytes : ret PSGStop x 2
																			  

_mainLoop:
  ld hl, 08000h
  ld (hl),a
_waitLoop:
  ld a,(hl)
  or a
  jr z, _waitLoop
  xor 0ffh
  jr nz, _runCommand
PSGFrame:
  ld a,d          ; check if we have got to play a tune
  or a
  jr z, _waitLoop ; no music to play : return
  ld a,e          ; check if we have got to skip frames
  or a
  jr z,_noFrameSkip
_skipFrame:
  dec e
_endLoop:
  xor a
  jr _mainLoop

_runCommand: ; a is 2 (loop) or 1 (no loop)
  call PSGStop ; keeps 'a' safe
  cp 3
  jr z,_endLoop
  dec a
;PSGPlay:
  exx
  ld d,a                          ; loop flag in d'
  ld hl,_music_start_             ; music ptr in hl'
  push hl
  pop ix  ;ld (PSGMusicLoopPoint),hl       ; loop pointer points to begin too
  xor a
  ld c,a                          ; reset the substring len (for compression)
  exx
  ld e,a                          ; PSGMusicSkipFrames in e: reset the skip frames
  ld d,PSG_PLAYING                ; PSGMusicStatus in d: set status to PSG_PLAYING
  jr _mainLoop ; a = 0

_noFrameSkip:
  exx
_intLoop:
  ld b,(hl)                      ; load PSG byte (in B)
  inc hl                         ; point to next byte
  ld a,c                         ; ld a,(PSGMusicSubstringLen)    ; read substring len
  or a
  jr z,_continue                 ; check if it is 0 (we are not in a substring)
  dec c                          ; decrease len
  jr nz,_continue
  push iy
  pop hl  ;ld hl,(PSGMusicSubstringRetAddr)  ; substring is over, retrieve return address

_continue:
  ld a,b                         ; copy PSG byte into A
  cp PSGLatch                    ; is it a latch?
  jr c,_noLatch                  ; if < $80 then it is NOT a latch
  
  ; we have got the latch PSG byte both in A and in B
  ; and we have to check if the value should pass to PSG or not
  bit 4,a                        ; test if it is a volume
  jr z,_noVolume                 ; jump if not volume data
  ; set volume
  ld (04000h),a
_send2PSG_4001h:
  ld (04001h),a                    ; output the byte
  jr _intLoop

_noVolume:
  and PSGChannel3
  xor PSGChannel3
  ld a,b
  jr nz,_send2PSG_4001h           ; send data to PSG if it is for channels 0-1 or 2
  ld (04000h),a                       ; output the byte in noise register
  jr _intLoop

_noLatch:
  cp PSGData
  jr nc,_send2PSG_4001h          ; if < $40 then it is a command
;_command:
  cp PSGWait
  jr z,_done                     ; no additional frames
  jr c,_otherCommands            ; other commands?
  and 007h                       ; take only the last 3 bits for skip frames
  exx
  ld e,a                         ; we got additional frames
  jr _endLoop                    ; frame done
_otherCommands:
  cp PSGSubString
  jr nc,_substring
  or a                           ; cp PSGEnd (PSGEnd is 0)
  jr z,_musicLoop
;  cp PSGLoop
;  ret nz                         ; 'ret' should never happen! if we do, it means the PSG file is probably corrupted, so we just RET
;_setLoopPoint:
  push hl
  pop ix  ; ld (PSGMusicLoopPoint),hl
  jr _intLoop

_substring:
  ld c,(hl)                           ; load substring address (offset)
  inc hl
  ld b,(hl)
  inc hl
  push hl
  pop iy  ;ld (PSGMusicSubstringRetAddr),hl    ; save return address
  ld hl,_music_start_
  add hl,bc                           ; make substring current
  sub PSGSubString-4                  ; len is value - $08 + 4
  ld c,a                              ; ld (PSGMusicSubstringLen),a         ; save len
  jr _intLoop

_musicLoop:
  ld a,d               ; looping requested?
  or a
  jr nz,_doLoop                     ; No:stop it! (tail call optimization)
  call PSGStop
_done:
  exx
  jr _endLoop                    ; frame done
_doLoop:
  push ix
  pop hl  ;ld hl,(PSGMusicLoopPoint)
  jr _intLoop

PSGStop:
  ld hl,04000h
  call PSGStop_
  inc l ; hl = 04001h
  ld d,PSG_STOPPED                               ; set status to PSG_STOPPED
PSGStop_:
  ld (hl),PSGLatch|PSGChannel0|PSGVolumeData|00Fh   ; latch channel 0, volume=0xF (silent)
  ld (hl),PSGLatch|PSGChannel1|PSGVolumeData|00Fh   ; latch channel 1, volume=0xF (silent)
  ld (hl),PSGLatch|PSGChannel2|PSGVolumeData|00Fh   ; latch channel 2, volume=0xF (silent)
  ld (hl),PSGLatch|PSGChannel3|PSGVolumeData|00Fh   ; latch channel 3, volume=0xF (silent)
  ret
  
_music_start_:

  END
