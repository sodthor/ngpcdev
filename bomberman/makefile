.SUFFIXES: .c .asm .rel .abs .gif .jpg .hh .class .java .mh .bin

NAME = BOMBERMAN

OBJS = \
	main.rel \
	hicolor.rel \
	mplayer.rel \
	flash.rel \
	music.rel \
	img.rel \
	library.rel \
	ngpc_linkkit/ngpc_linkkit.rel \
	ngpc_linkkit/ngpc_linkkit_com.rel

FLAGS =	-O3

DATA = \
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
	del *.rel
	del *.abs
	del *.map 
	del *.s24 
	del $(NAME).ngp
