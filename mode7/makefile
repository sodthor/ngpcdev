.SUFFIXES: .c .asm .rel .abs
NAME = MODE7
OBJS = \
	main.rel \
	music.rel \
	library.rel

$(NAME).ngp: makefile ngpc.lcf $(OBJS)
	tulink -la -o $(NAME).abs ngpc.lcf C900ML.LIB $(OBJS)
	tuconv -Fs24 $(NAME).abs
	s242ngp $(NAME).s24
	\tools\trunc $(NAME).ngp 524288
	..\..\vdmgr\vdmgr64

.c.rel:
	cc900 -Nb2 -c -O3 $< -o $@

clean:
	del *.rel
	del *.abs
	del *.map 
	del *.s24 
	del *.ngp
