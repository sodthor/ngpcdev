P2BIN		=	p2bin
OPTS		=	-r $$-$$
#ASDIR           =       z:\rb\ngpc\dev\as
ASDIR           =       e:\temp\karim\ngpc\dev\as
PATH		=	$(PATH);$(ASDIR)\bin
JAVA_HOME	=	d:\jdk1.3

all:	modplay.ngp

clean:
	del *.p
	del *.ngp

modplay.asm:	pt.inc freq.inc modplay.inc img.inc makefile

modplay.p:	modplay.asm
	as.exe -i $(ASDIR)\include modplay.asm

modplay.ngp:	modplay.p
	$(P2BIN) $(OPTS) modplay.p modplay.ngp
