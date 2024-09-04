CUBE_NBV	EQU	8

cube_vertices
	db	-22,0,-22
	db	23,0,-22
	db	-22,0,23
	db	23,0,23
	db	-22,-46,-22
	db	23,-46,-22
	db	-22,-46,23
	db	23,-46,23

CUBE_NBT	EQU	12

cube_triangles
	db	0,18,12,1
	db	18,0,6,1
	db	24,42,30,2
	db	42,24,36,2
	db	0,30,6,3
	db	30,0,24,3
	db	6,42,18,4
	db	42,6,30,4
	db	18,36,12,5
	db	36,18,42,5
	db	12,24,0,6
	db	24,12,36,6

cube_normals
	db	0,31,0
	db	0,31,0
	db	0,-31,0
	db	0,-31,0
	db	0,0,-31
	db	0,0,-31
	db	31,0,0
	db	31,0,0
	db	0,0,31
	db	0,0,31
	db	-31,0,0
	db	-31,0,0

cube
	dw	cube_nbv,cube_nbt
	dd	cube_vertices,cube_triangles,cube_normals

