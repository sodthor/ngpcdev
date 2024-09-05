.SUFFIXES: .c .asm .rel .abs .gif .jpg .hh .class .java .mh .bin .wav

NAME = SQRXZ
JAVA_HOME=e:/jdk1.6
JAVA = $(JAVA_HOME)/bin/java
JAVAC = $(JAVA_HOME)/bin/javac

OBJS = \
	main.rel \
	flash.rel \
	music.rel \
	img.rel \
	hicolor.rel \
	library.rel

FLAGS =	-O3

$(NAME).ngp: makefile mclean ngpc.lcf $(DATA) $(OBJS)
	tulink -la -o $(NAME).abs ngpc.lcf C900ML.LIB $(OBJS)
	tuconv -Fs24 $(NAME).abs
	s242ngp $(NAME).s24
#	../../neopop/neopopDebugger
	../../neopop/neopop-win32

.c.rel:
	cc900 -c $(FLAGS) $< -o $@

.asm.rel:
	asm900 $< -o $@

.bin.mh:
	$(JAVA) -classpath . Bin2C $<

.wav.mh:
	$(JAVA) -classpath . Bin2C $<

.java.class:
	$(JAVAC) $<

mclean:
	del main.rel

clean:
	del *.rel
	del *.abs
	del *.map
	del *.s24
	del $(NAME).ngp
