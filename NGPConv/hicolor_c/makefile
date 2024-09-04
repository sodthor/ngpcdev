.SUFFIXES: .c .asm .rel .abs .gif .jpg .hh

NAME = HICOLOR

JAVA_HOME=d:/j2sdk1.4.0
JAVA = $(JAVA_HOME)/bin/java

OBJS = \
	hicolor.rel \
	main.rel \
	library.rel

FLAGS =

DATA = \
	tulipes.hh \
	gx.hh

$(NAME).ngp: makefile ngpc.lcf $(DATA) $(OBJS)
	tulink -la -o $(NAME).abs ngpc.lcf $(OBJS)
	tuconv -Fs24 $(NAME).abs
	s242ngp $(NAME).s24

.c.rel:
	cc900 -c -O3 $(FLAGS) $< -o $@

.asm.rel:
	asm900 $< -o $@

.gif.hh:
	$(JAVA) -classpath .. CodeImage -h $<

.jpg.hh:
	$(JAVA) -classpath .. CodeImage -h $<

clean:
	del *.hh
	del *.rel
	del *.abs
	del *.map 
	del *.s24 
	del $(NAME).ngp
