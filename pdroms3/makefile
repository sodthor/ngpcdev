.SUFFIXES: .c .asm .rel .abs .gif .jpg .hh .class .java .mh .bin

NAME = PDROMS3

JAVA_HOME=c:\temp\jdk1.4.1
JAVA = $(JAVA_HOME)\bin\java
JAVAC = $(JAVA_HOME)\bin\javac

OBJS = \
	main.rel \
	hicolor.rel \
	mplayer.rel \
	flash.rel \
	music.rel \
	img.rel \
	library.rel \
	earth0.rel

FLAGS =	-O3

DATA = \
	Bin2C.class

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

.java.class:
	$(JAVAC) $<

mclean:
	touch main.rel
	del main.rel

clean:
	rm *.rel
	rm *.abs
	rm *.map 
	rm *.s24 
	rm $(NAME).ngp
