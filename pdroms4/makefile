.SUFFIXES: .c .asm .rel .abs .gif .jpg .hh .class .java .mh .bin .wav

NAME = PDROMS401
JAVA_HOME=e:/jdk1.6
JAVA = $(JAVA_HOME)/bin/java
JAVAC = $(JAVA_HOME)/bin/javac

OBJS = \
	main.rel \
	hicolor.rel \
	mplayer.rel \
	flash.rel \
	music.rel \
	img.rel \
	library.rel \
	wav.rel \
	tables.rel

FLAGS =	-O3

DATA = Bin2C.class \
	   sfx/music1.mh \
	   sfx/menusnd.mh
#	   sfx/music2.mh

$(NAME).ngp: makefile mclean ngpc.lcf $(DATA) $(OBJS)
	tulink -la -o $(NAME).abs ngpc.lcf $(OBJS)
	tuconv -Fs24 $(NAME).abs
	s242ngp $(NAME).s24
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
	rm -f main.rel

clean:
	rm -f *.rel
	rm -f *.abs
	rm -f *.map
	rm -f *.s24
	rm -f $(NAME).ngp
