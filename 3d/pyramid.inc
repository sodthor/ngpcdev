PYRAMID_NBV	EQU	6

pyramid_vertices
	db	1,-55,0
	db	-25,0,-26
	db	29,0,-26
	db	29,0,28
	db	-25,0,28
	db	1,0,0

PYRAMID_NBT	EQU	8

pyramid_triangles
	db	0,12,6,1
	db	0,18,12,2
	db	0,24,18,1
	db	0,6,24,2
	db	6,12,30,3
	db	12,18,30,3
	db	18,24,30,3
	db	24,6,30,3

pyramid_normals
	db	0,-13,-27
	db	27,-13,0
	db	0,-13,27
	db	-27,-13,0
	db	0,31,0
	db	0,31,0
	db	0,31,0
	db	0,31,0

pyramid
	dw	pyramid_nbv,pyramid_nbt
	dd	pyramid_vertices,pyramid_triangles,pyramid_normals

