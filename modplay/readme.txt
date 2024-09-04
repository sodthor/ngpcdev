*****************************************
*                                       *
* MOD Tools                             *
*                                       *
* MOD player for NGPC                   *
*                                       *
* CopyLeft 02/2003 THOR/Shadow Of Dawn  *
* Modify, distribute, do what you want  *
* with this sources...                  *
*                                       *
*****************************************

What you need :

	. JDK (or JRE) 1.4
	. AS assembler
	. A real NGPC (Standard Neopop don't manage stereo on DAC, the version on my site give only an idea of how it sounds)
	. Headphones ! NGPC speaker is not the best way to hear this kind of musix

This release includes a sample of the first version of my MOD player.
No fx managed (except volume variations). Simple songs sound better.
It includes the asm viewer of pictures created with "NGP image converter" : img.inc
CPU is locked when MOD is playing (press A to interrupt song), sorry.
MOD player is only ASM for the moment, sorry once again.

Description of the java app :

Command Line :
--------------

<javapath/bin>/java ModConverter <song_filename> <song_id>

filename :	A MOD file

song_id :	an ID for the song, it will be used in the generated header as prefix and the generated file will be : <song_id>.inc

Have fun.

THOR

http://www.geocities.com/rtb7
rtb7@yahoo.com
