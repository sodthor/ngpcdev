.SUFFIXES: .c .asm .rel .abs .gif .jpg .hh .class .java .mh .bin

NAME = DYNAMATE

JAVA_HOME=C:\applis\j2sdk1.4.0
JAVA = $(JAVA_HOME)\bin\java
JAVAC = $(JAVA_HOME)\bin\javac

OPTS =

OBJS = \
	main.rel \
	library.rel \
	dynabase.rel \
	hicolor.rel \
	music.rel

DATA =	CodeSprites.class \
	Bin2C.class \
	sprites.h \
	bustamove0.mh \
	bustamove1.mh \
	bustamove2.mh \
	menu.hh

all:	$(NAME).ngp

sprites.h: sprites.gif CodeSprites.class
	$(JAVA) CodeSprites sprites.gif sprites

$(NAME).ngp: makefile ngpc.lcf $(DATA) $(OBJS)
	tulink -la -o $(NAME).abs ngpc.lcf $(OBJS)
	tuconv -Fs24 $(NAME).abs
	s242ngp $(NAME).s24

.gif.hh:
	$(JAVA) -classpath . CodeImage -h $<

.jpg.hh:
	$(JAVA) -classpath . CodeImage -h $<

.bin.mh:
	$(JAVA) -classpath . Bin2C $<

.c.rel:
	cc900 -c -O3 $(OPTS) $< -o $@

.asm.rel:
	asm900 $< -o $@

.java.class:
	$(JAVAC) $<

clean:
	del *.hh
	del *.rel
	del *.abs
	del *.map 
	del *.s24 
	del $(NAME).ngp
