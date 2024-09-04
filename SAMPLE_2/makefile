.SUFFIXES: .c .asm .rel .abs
NAME = SAMPLE

OBJS = \
	main.rel \
	memcpy.rel \
	library.rel \
	levels.rel

$(NAME).ngp: makefile ngpc.lcf gfx $(OBJS)
	tulink -la -o $(NAME).abs ngpc.lcf $(OBJS)
	tuconv -Fs24 $(NAME).abs
	s242ngp $(NAME).s24

.c.rel:
	cc900 -c -O3 $< -o $@

.asm.rel:
	asm900 $< -o $@

gfx:
	java -classpath . CodeSprites test.gif gfx.h GFX
	del levels.rel

clean:
	del *.rel
	del *.abs
	del *.map 
	del *.s24 
	del $(NAME).ngp
