NGPC C Framework v0.2 - 2 April 2002
ivan@rcp.co.uk
---------------------------------

0) Whats New
Version 0.2 - 2Apr2002

  InstallSoundDriver - installs the z80 sound effects driver
  InstallSounds - Copies user defined sounds to the z80 driver
  PlaySound - plays a specified predefined sound
  StopAllSounds - stops playing on all sound channels
  SOUNDEFFECT structure - the parameters for a user defined sound
  SetSprite - initialise the sprite
  SetSpritePositon - move it
  SPRITE_PLANE definition to be used with functions like SetPalette()
  SeedRandom - Seeds the pseudo random number generator
  GetRandom - gets a random number in a specified range

Version 0.1 - 27Nov2001

  Initial Revision


1) Overview
This archive provides all of the files required for a basic framework for
you to build your own game for the Neo Geo Pocket Color using the C 
programming language.


2) Whats in this Package
The files are as follows:
NGPC.H - The NGPC register definitions
CARTHDR.H - The standard NGPC cartridge header
LIBRARY.H - Definitions for library of useful functions like PrintString etc
LIBRARY.C - The implementation of the helper library
MAIN.C - Your code goes here
NGPC.LCF - No need to touch this file. It tells the linker where to link to.
MAKEFILE - The makefile for your project.


3) What to do first
First things first you need to install the compiler. Roger Bacon's website
http://www.btinternet.com/~rog666/ has this available for download and provides
installation instructions. 

Once you have done this try building the framework as it. If successful you
will have a framewrk.ngp file that if run under an emulator will display 
"Hello World!"

The library provides a function called InstallTileSet() this takes the 
exported Tileset created with Steve Robb's "NeoTile" tile editor and installs
it into tile ram.


4) Starting your own game
There are a few steps you need to do to the framework to customise it for you
own game:
* Change the NAME variable in Makefile. This is the resultant name of the .ngp
output file that you want.

* In Carthdr.h change the CARTTITLE constant to the name of your game. This
must be a 12 character string. Pad it with spaces if your game name is not
12 characters

* Remove the SetPalette and PrintString "Hello World" from main.c and add your
own game code!

* If you need anything to be done on the VBInterrupt then modify the interrupt
handler in library.c

