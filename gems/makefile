.SUFFIXES: .c .asm .rel .abs .gif .jpg .hh .class .java .mh .bin

NAME = GEMS

JAVA_HOME=d:\jdk1.4
#c:\applis\j2sdk1.4.0
JAVA = $(JAVA_HOME)\bin\java
JAVAC = $(JAVA_HOME)\bin\javac

OBJS = \
	main.rel \
	hicolor.rel \
	flash.rel \
	music.rel \
	img.rel \
	library.rel

FLAGS =	-O3

DATA =	CodeSprites.class \
	Bin2C.class \
	gems.h \
	title.h \
	magical0.mh \
	magical1.mh \
	magical2.mh \
	menu.hh

$(NAME).ngp: makefile ngpc.lcf $(DATA) $(OBJS)
	tulink -la -o $(NAME).abs ngpc.lcf $(OBJS)
	tuconv -Fs24 $(NAME).abs
	s242ngp $(NAME).s24

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
	del *.hh
	del *.rel
	del *.abs
	del *.map 
	del *.s24 
	del $(NAME).ngp
