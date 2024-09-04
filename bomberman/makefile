.SUFFIXES: .c .asm .rel .abs .gif .jpg .hh .class .java .mh .bin

NAME = BOMBERMAN

JAVA_HOME=c:\temp\jdk1.4
JAVA = $(JAVA_HOME)\bin\java
JAVAC = $(JAVA_HOME)\bin\javac

OBJS = \
	main.s \
	hicolor.s \
	mplayer.s \
	flash.s \
	music.s \
	img.s \
	library.s

FLAGS =	-O3

DATA = \
	Bin2C.class \
	cotton0.mh \
	metalslug0.mh \
	metalslug1.mh \
	metalslug2.mh \
	metalslug3.mh \
	metalslug4.mh \
	metalslug5.mh \
	metalslug6.mh \
	metalslug7.mh \
	metalslug8.mh \
	metalslug9.mh \
	metalslug10.mh \
	magical0.mh \
	magical1.mh \
	magical2.mh

$(NAME).ngp: makefile mclean ngpc.lcf $(DATA) $(OBJS)
#	tulink -la -o $(NAME).abs ngpc.lcf $(OBJS)
#	tuconv -Fs24 $(NAME).abs
#	s242ngp $(NAME).s24
#	../../neopop/neopop-win32

.c.s:
	D:/dev/ngpcdev/llvm-2.9/bin/llvm-gcc -I . -DGCC -emit-llvm -O3 -S $< -o $@

.c.rel:
	cc900 -c $(FLAGS) $< -o $@

.asm.rel:
	asm900 $< -o $@

.bin.mh:
	$(JAVA) -classpath . Bin2C $<

.java.class:
	$(JAVAC) $<

mclean:
	touch main.rel
	rm main.rel

clean:
	rm *.rel
	rm *.abs
	rm *.map 
	rm *.s24 
	rm $(NAME).ngp
