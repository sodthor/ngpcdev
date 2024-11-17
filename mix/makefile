.SUFFIXES: .c .asm .rel .abs .gif .jpg .hh .class .java .mh .bin

NAME = MIX!

JAVA_HOME=c:\jdk1.7.0
#c:\applis\j2sdk1.4.0
JAVA = $(JAVA_HOME)\bin\java
JAVAC = $(JAVA_HOME)\bin\javac

OBJS = \
	main.rel \
	menu.rel \
	gems\gems.rel \
	dynamate\dynabase.rel \
	dynamate\dynamate.rel \
	vexed\vexed.rel \
	hicolor.rel \
	flash.rel \
	music.rel \
	img.rel \
	mplayer.rel \
	library.rel

FLAGS =	-O -I. -Imusix

DATA =	CodeSprites.class \
	Bin2C.class \
	musix/cotton1.mh \
	musix/bustamove0.mh \
	musix/bustamove1.mh \
	musix/bustamove2.mh \
	gems/gems.h \
	gems/title.h \
	musix/magical0.mh \
	musix/magical1.mh \
	musix/magical2.mh \
	gems/gems_menu.hh \
	gems/back.hh \
	musix/metalslug0.mh \
	musix/metalslug1.mh \
	musix/metalslug2.mh

$(NAME).ngp: makefile ngpc.lcf $(DATA) $(OBJS)
	tulink -la -o $(NAME).abs ngpc.lcf $(OBJS) C900ML.LIB
	tuconv -Fs24 $(NAME).abs
	s242ngp $(NAME).s24
	..\..\neopop\NeoPop-Win32.exe $(NAME).ngp

gems.h: gems.gif CodeSprites.class
	$(JAVA) -classpath . CodeSprites gems.gif gems

title.h: title.gif CodeSprites.class
	$(JAVA) -classpath . CodeSprites title.gif title

.c.rel:
	cc900 -c $(FLAGS) $< -o $@

.asm.rel:
	asm900 $< -o $@

.gif.hh:
	$(JAVA) -classpath . CodeImage -h $<

.jpg.hh:
	$(JAVA) -classpath . CodeImage -h $<

.bin.mh:
	$(JAVA) -classpath . Bin2C $<

.java.class:
	$(JAVAC) $<

clean:
	del /F $(OBJS)
	del *.abs
	del *.map 
	del *.s24 
	del $(NAME).ngp
