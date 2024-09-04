.SUFFIXES: .c .asm .rel .abs
NAME = POCKETRACE

OBJS = \
	ldivl.rel \
	img.rel \
	main.rel \
	library.rel	

$(NAME).ngp: makefile ngpc.lcf copy $(OBJS)
	tulink -la -o $(NAME).abs ngpc.lcf $(OBJS)
	tuconv -Fs24 $(NAME).abs
	s242ngp $(NAME).s24

.c.rel:
	cc900 -c -O3 $< -o $@

.asm.rel:
	asm900 $< -o $@

copy:
	del main.rel
	copy gfx\Pic.h .

clean:
	del *.rel
	del *.abs
	del *.map 
	del *.s24 
	del $(NAME).ngp
