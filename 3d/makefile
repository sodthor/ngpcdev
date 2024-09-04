P2BIN		=	p2bin
OPTS		=	-r $$-$$
PATH		=	$(PATH);e:\temp\karim\ngpc\dev\as\bin
JAVA_HOME	=	d:\jbuilder4\jdk1.3

all:	clean engine.ngp

clean:
	del *.p
	del *.ngp

engine.p:	engine.asm cube.inc pyramid.inc tie.inc tieb.inc
	set ASCMD=@e:\temp\karim\ngpc\dev\as\lib\as.rc
	as.exe engine.asm

engine.ngp:	engine.p
	$(P2BIN) $(OPTS) engine.p engine.ngp

TransObj.class: TransObj.java
	del cube.inc pyramid.inc TransObj*.class
	$(JAVA_HOME)\bin\javac TransObj.java

cube.inc:	TransObj.class
	$(JAVA_HOME)\bin\java TransObj cube\Neocube.rsd cube

pyramid.inc:	TransObj.class
	$(JAVA_HOME)\bin\java TransObj pyramid\Neopyra.rsd pyramid

tie.inc:	TransObj.class
	$(JAVA_HOME)\bin\java TransObj tie\tie.rsd tie 0.7 019h 0c2h 0c2h

tieb.inc:	TransObj.class
	$(JAVA_HOME)\bin\java TransObj tieb\tieb.rsd tieb 0.7 011h 0c3h 0c3h
