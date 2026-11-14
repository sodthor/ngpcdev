.SUFFIXES: .c .asm .rel .abs .gif .jpg .hh .class .java .mh .bin

NAME = PDROMS3_CC900

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

$(NAME).ngp: makefile ngpc.lcf $(DATA) $(OBJS)
	tulink -la -o $(NAME).abs ngpc.lcf $(OBJS)
	tuconv -Fs24 $(NAME).abs
	s242ngp $(NAME).s24

.c.rel:
	cc900 -c $(FLAGS) $< -o $@

.asm.rel:
	asm900 $< -o $@

.bin.mh:
	$(JAVA) -classpath . Bin2C $<

.java.class:
	$(JAVAC) $<

clean:
	del $(OBJS)
	del $(NAME).abs
	del $(NAME).map 
	del $(NAME).s24 
	del $(NAME).ngp
