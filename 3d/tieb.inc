TIEB_NBV	EQU	24

tieb_vertices
	db	-49,0,1
	db	-21,-26,15
	db	0,0,33
	db	-21,26,15
	db	-21,26,-12
	db	0,0,-39
	db	-21,-26,-12
	db	20,-26,15
	db	20,-26,-12
	db	20,26,-12
	db	48,0,1
	db	20,26,15
	db	-49,-22,46
	db	-49,22,46
	db	-14,60,1
	db	-49,22,-42
	db	-49,-22,-42
	db	-14,-60,1
	db	49,-22,-42
	db	49,22,-42
	db	14,60,1
	db	49,22,46
	db	49,-22,46
	db	14,-60,1

TIEB_NBT	EQU	28

tieb_triangles
	db	0,6,36,011h
	db	6,12,42,011h
	db	12,18,66,011h
	db	18,0,24,011h
	db	24,30,54,011h
	db	30,36,48,011h
	db	48,42,60,011h
	db	54,60,66,011h
	db	6,18,12,011h
	db	6,0,18,011h
	db	24,36,30,011h
	db	24,0,36,011h
	db	36,42,48,011h
	db	36,6,42,011h
	db	30,60,54,011h
	db	30,48,60,011h
	db	60,12,66,011h
	db	60,42,12,011h
	db	54,18,24,011h
	db	54,66,18,011h
	db	78,90,84,0c3h
	db	72,90,78,0c3h
	db	72,96,90,0c3h
	db	102,96,72,0c3h
	db	114,126,120,0c3h
	db	108,126,114,0c3h
	db	108,132,126,0c3h
	db	138,132,108,0c3h

tieb_normals
	db	-21,-22,0
	db	0,-16,25
	db	0,16,25
	db	-21,22,0
	db	0,22,-21
	db	0,-22,-21
	db	21,-22,0
	db	21,22,0
	db	-19,0,23
	db	-13,0,27
	db	-24,0,-18
	db	-13,0,-27
	db	0,-31,0
	db	0,-31,0
	db	19,8,-22
	db	19,-8,-22
	db	16,3,25
	db	16,-3,25
	db	0,31,0
	db	0,31,0
	db	-22,20,0
	db	-31,0,0
	db	-31,0,0
	db	-22,-21,0
	db	22,20,0
	db	31,0,0
	db	31,0,0
	db	22,-21,0

tieb
	dw	tieb_nbv,tieb_nbt
	dd	tieb_vertices,tieb_triangles,tieb_normals

