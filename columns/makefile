P2BIN		=	p2bin
OPTS		=	-r $$-$$
PATH		=	$(PATH);e:\temp\karim\ngpc\dev\as\bin
JAVA_HOME	=	d:\jdk1.3

all:	clean columns.ngp

clean:
	del music1.bin
	del music2.bin
	del *.p
	del *.ngp

columns.p:	columns.asm all.inc drv.inc
	set ASCMD=@e:\temp\karim\ngpc\dev\as\lib\as.rc
	as.exe columns.asm

columns.asm:	drv.inc music1.inc music2.inc

sound.p:	sound.asm
	as.exe sound.asm

drv.inc:	sound.p
	call $(P2BIN) $(OPTS) sound.p sound.bin
	$(JAVA_HOME)\bin\java Bin2Inc sound.bin drv.inc

music1.bin:
	$(JAVA_HOME)\bin\java YM2Inc ym\amber01.ym music1.bin

music1.z:	music1.bin
	$(JAVA_HOME)\bin\java Compress lc 9 music1.bin music1.z 1024

music1.inc:	music1.z
	$(JAVA_HOME)\bin\java Bin2Inc music1.z music1.inc

music2.bin:
	$(JAVA_HOME)\bin\java YM2Inc ym\lightf.bin music2.bin FB4088,18375

music2.z:	music2.bin
	$(JAVA_HOME)\bin\java Compress lc 9 music2.bin music2.z 1024

music2.inc:	music2.z
	$(JAVA_HOME)\bin\java Bin2Inc music2.z music2.inc

columns.ngp:	columns.p
	$(P2BIN) $(OPTS) columns.p columns.ngp
