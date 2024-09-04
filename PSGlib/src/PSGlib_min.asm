;================================================================
; PSGlib - Programmable Sound Generator audio library - by sverx
;          https://github.com/sverx/PSGlib
;================================================================

; NOTE: this uses a WLA-DX 'ramsection' at slot 3
;       If you want to change or remove that,
;       see the note at the end of this file

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
PSGEnd         EQU 000h

_start_z80:
  di
  ld sp, 01000h
  jr PSGInit

  org 06h

  ; fundamental vars
PSGMusicStart              dw 0   ; the pointer to the beginning of music (written by tlcs: where music has been stored in z80 ram)
PSGMusicPointer            dw 0   ; the pointer to the current
PSGMusicLoopPoint          dw 0   ; the pointer to the loop begin
PSGLoopFlag                db 0   ; the tune should loop or not (flag)

  ; decompression vars
PSGMusicSubstringLen       db 0   ; length of the substring we are playing
PSGMusicSubstringRetAddr   dw 0   ; return to this address when substring is over

; ************************************************************************************
; initializes the PSG 'engine'
PSGInit:
  xor a             ; ld a,PSG_STOPPED
  ld d,a            ; PSGMusicStatus in d: set music status to PSG_STOPPED
  ld ix,04000h
_mainLoop:
  ld hl, 08000h
  ld (hl),a
_waitLoop:
  ld a,(hl)
  or a
  jr z, _waitLoop
  xor 0ffh
  jr nz, _runCommand
  call PSGFrame
_endLoop:
  xor a
  jr _mainLoop

_runCommand: ; a is 2 (loop) or 1 (no loop)
  call PSGStop ; keeps 'a' safe
  cp 3
  jr z,_endLoop
  dec a
;PSGPlay:
  ld (PSGLoopFlag),a
  ld hl,(PSGMusicStart)
  ld (PSGMusicPointer),hl         ; set music pointer to begin of music
  ld (PSGMusicLoopPoint),hl       ; loop pointer points to begin too
  xor a
  ld e,a                          ; PSGMusicSkipFrames in e: reset the skip frames
  ld (PSGMusicSubstringLen),a     ; reset the substring len (for compression)
  ld d,PSG_PLAYING                ; PSGMusicStatus in d: set status to PSG_PLAYING
  jr _mainLoop ; a = 0

PSGStop:
  ld b,PSGLatch|PSGChannel0|PSGVolumeData|00Fh   ; latch channel 0, volume=0xF (silent)
  ld (ix),b
  ld (ix+1),b
  ld b,PSGLatch|PSGChannel1|PSGVolumeData|00Fh   ; latch channel 1, volume=0xF (silent)
  ld (ix),b
  ld (ix+1),b
  ld b,PSGLatch|PSGChannel2|PSGVolumeData|00Fh   ; latch channel 2, volume=0xF (silent)
  ld (ix),b
  ld (ix+1),b
  ld b,PSGLatch|PSGChannel3|PSGVolumeData|00Fh   ; latch channel 3, volume=0xF (silent)
  ld (ix),b
  ld (ix+1),b
  ld d,PSG_STOPPED                               ; set status to PSG_STOPPED
  ret

PSGFrame:
  ld a,d      ; check if we have got to play a tune
  or a
  ret z       ; no music to play : return
  ld a,e      ; check if we havve got to skip frames
  or a
  jr z,_noFrameSkip
;_skipFrame:
  dec e
  ret

_noFrameSkip:
  ld hl,(PSGMusicPointer)        ; read current address

_intLoop:
  ld b,(hl)                      ; load PSG byte (in B)
  inc hl                         ; point to next byte
  ld a,(PSGMusicSubstringLen)    ; read substring len
  or a
  jr z,_continue                 ; check if it is 0 (we are not in a substring)
  dec a                          ; decrease len
  ld (PSGMusicSubstringLen),a    ; save len
  jr nz,_continue
  ld hl,(PSGMusicSubstringRetAddr)  ; substring is over, retrieve return address

_continue:
  ld a,b                         ; copy PSG byte into A
  cp PSGLatch                    ; is it a latch?
  jr c,_noLatch                  ; if < $80 then it is NOT a latch
  
  ; we have got the latch PSG byte both in A and in B
  ; and we have to check if the value should pass to PSG or not
  bit 4,a                        ; test if it is a volume
  jr z,_noVolume                 ; jump if not volume data
  ; set volume
  ld (ix),a
  ld (ix+1),a
  jr _intLoop

_noVolume:
  and PSGChannel3
  xor PSGChannel3
  jr nz,_send2PSG_4001h           ; send data to PSG if it is for channels 0-1 or 2

  ld (ix),b                       ; output the byte in noise register
  jr _intLoop

_noLatch:
  cp PSGData
  jr c,_command                  ; if < $40 then it is a command
  ; it's a data
_send2PSG_4001h:
  ld (ix+1),b                    ; output the byte
  jr _intLoop

_musicLoop:
  ld a,(PSGLoopFlag)               ; looping requested?
  or a
  jr z,PSGStop                     ; No:stop it! (tail call optimization)
  ld hl,(PSGMusicLoopPoint)
  jr _intLoop

_command:
  cp PSGWait
  jr z,_done                     ; no additional frames
  jr c,_otherCommands            ; other commands?
  and 007h                       ; take only the last 3 bits for skip frames
  ld e,a                         ; we got additional frames
_done:
  ld (PSGMusicPointer),hl        ; save current address
  ret                            ; frame done

_otherCommands:
  cp PSGSubString
  jr nc,_substring
  cp PSGEnd
  jr z,_musicLoop
  cp PSGLoop
  ret nz                         ; 'ret' should never happen! if we do, it means the PSG file is probably corrupted, so we just RET

_setLoopPoint:
  ld (PSGMusicLoopPoint),hl
  jr _intLoop

_substring:
  sub PSGSubString-4                  ; len is value - $08 + 4
  ld (PSGMusicSubstringLen),a         ; save len
  ld c,(hl)                           ; load substring address (offset)
  inc hl
  ld b,(hl)
  inc hl
  ld (PSGMusicSubstringRetAddr),hl    ; save return address
  ld hl,(PSGMusicStart)
  add hl,bc                           ; make substring current
  jr _intLoop

  END
