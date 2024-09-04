.SUFFIXES: .gif .jpg .inc

OPTS		=	-r $$-$$
ASDIR           =       z:\rb\ngpc\dev\as
P2BIN		=	$(ASDIR)\bin\p2bin
JAVA_HOME	=	d:/j2sdk1.4.0
JAVA		=	$(JAVA_HOME)/bin/java

FLAGS		=	#-D NEOPOP

all:	hicolor.ngp

clean:
	del *.p
	del *.ngp

DATA =	ff1.inc \
	sunset.inc \
	nenu.inc \
	gk8.inc \
	ss_gt4.inc \
	game3.inc

hicolor.p:	hicolor.asm makefile hicolor.inc $(DATA)
	$(ASDIR)\bin\as.exe -i $(ASDIR)\include $(FLAGS) hicolor.asm

hicolor.ngp:	hicolor.p
	$(P2BIN) $(OPTS) hicolor.p hicolor.ngp

.gif.inc:
	$(JAVA) -classpath .. CodeImage -a -h $<

.jpg.inc:
	$(JAVA) -classpath .. CodeImage -a -h $<

