.SUFFIXES: .c .asm .rel .abs .gif .jpg .hh

NAME = SLIDESHOW

JAVA_HOME=d:/j2sdk1.4.0
JAVA = $(JAVA_HOME)/bin/java

OBJS = \
	img.rel \
	main.rel \
	library.rel

DATA = \
	skull.hh \
	ff0.hh \
	ff1.hh \
	ranma.hh \
	goku.hh \
	gotrunk.hh \
	jv0.hh \
	jv1.hh \
	jv2.hh \
	gekka.hh \
	kof2k1.hh \
        gk1.hh \
        gk2.hh \
        gk3.hh \
        gk4.hh \
        gk5.hh \
        gk6.hh \
        gk7.hh \
        gk8.hh \
        gk9.hh \
        gka.hh \
        gkb.hh \
        gkc.hh \
	hinako.hh \
	hotaru.hh \
	leona.hh \
	chess.hh \
	scene.hh

$(NAME).ngp: makefile ngpc.lcf $(DATA) $(OBJS)
	tulink -la -o $(NAME).abs ngpc.lcf $(OBJS)
	tuconv -Fs24 $(NAME).abs
	s242ngp $(NAME).s24

.gif.hh:
	$(JAVA) -classpath .. CodeImage $<

.jpg.hh:
	$(JAVA) -classpath .. CodeImage $<

.c.rel:
	cc900 -c -O3 $< -o $@

.asm.rel:
	asm900 $< -o $@

clean:
	del *.hh
	del *.rel
	del *.abs
	del *.map 
	del *.s24 
	del $(NAME).ngp
