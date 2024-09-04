*****************************************
*                                       *
* CodeImage                             *
*                                       *
* GIF/JPG image converter for NGPC      *
*                                       *
* CopyLeft 04/2003 THOR/Shadow Of Dawn  *
* Modify, distribute, do what you want  *
* with this sources...                  *
*                                       *
*****************************************

What you need :

	. JDK (or JRE) 1.4
	. Ivan's C Framework for C ouput
	. C compiler or ASM of course

This release includes a sample (the famous SLIDESHOW :-) with a makefile containing rules
to generate NGP image from gif and jpg with default options (modify makefile to set JAVA_HOME).

The sources img.c and img.h contains data structure and a simple function to display image
on the screen using Ivan Mackintosh C framework. hicolor.c and hicolor.inc contain routines
to display hiclor pictures.

Description of the java app :

Command Line :
--------------

<javapath/bin>/java CodeImage [-c<contrast>] [-p<nb_of_planes>] [-b<balance_mode>] [-a] [-h]  <image_filename> [<image_id>]

filename :	A gif or jpg image

image_id :	an ID for the image, it will be used in the generated header as prefix and the generated file will be : <image_id>.hh

contrast:	from -2 to 9 in the graphic app, to obtain darker or lighter result, default is 1

nb of planes :	1 or 2(default)
		1 plane  : up to 45 colors + black, but only 3colors + black per tiles but big size image (limit is 512 tiles of course)
		2 planes : up to 90 colors + black, 6 colors + black per tiles, max 256 tiles per plane, 128*128 for example

balance mode :	How the encoder dispatch colors on the two planes (not available for 1 plane)
		cm : Color Middle, same number repartition on the two planes, darkest colors on the plane 1, the other on the second plane
		cd : Color Dark, 1/3 - 2/3 repartition, more nuances for darkest colors
		cl : Color Light, 2/3 - 1/3, more nuances for lightest colors
		wm : Weight Middle, same weight repartition (number of pixels/color) on the two planes, darkest colors on the plane 1, the other on the second plane
		wd : Weight Dark, 1/3 - 2/3 weight repartition, more nuances for darkest colors
		wl : Weight Light, 2/3 - 1/3, more nuances for lightest colors
		default is cm

asm output :	(-a) Saved file contains asm code instead of C

High color :	(-h) hundreds of colors (with less CPU!)

GUI :
-----

<javapath/bin>/javaw CodeImage

You will recognize all the cmd line parameters.


Note:
-----

"Diff" is the sum of the distance of each pixel from source image to ngpc version.
in theory, smaller is this value, better is the conversion. You have to modify parameters
to obtain the best result, but the one to keep is not always the one with the smaller "Diff"
you have to judge by yourself the result to keep the one which looks better.


Have fun.

THOR

http://www.geocities.com/rtb7
rtb7@yahoo.com
