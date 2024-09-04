.SUFFIXES: .gif .jpg .inc

OPTS		=	-r $$-$$
ASDIR           =       e:/ngpcdev/as
P2BIN		=	$(ASDIR)/bin/p2bin
JAVA_HOME	=	d:/j2sdk1.4.0
JAVA		=	$(JAVA_HOME)/bin/java

all:	main.ngp

clean:
	del *.p
	del *.ngp

DATA =	\
	skull.inc \
	jv0.inc

main.asm:	img.inc $(DATA) makefile

main.p:	main.asm
	$(ASDIR)/bin/as.exe -i $(ASDIR)/include main.asm

main.ngp:	main.p
	$(P2BIN) $(OPTS) main.p main.ngp

skull.inc:	skull.gif
	$(JAVA) -classpath .. CodeImage skull.gif skull -a

jv0.inc:	jv0.jpg
	$(JAVA) -classpath .. CodeImage jv0.jpg jv0 -a
