#include "level1.ngpc"

typedef struct {
	u16	nbt;
	u16*	tiles;
	u16	nbp;
	u16*	pals;
	u16	w;
	u16	h;
	u16*	map_1;
	u16*	map_2;
	u8*	type_1;
	u8*	type_2;
	u8*	mark;
	u8	nbm;
} LEVEL;

#define NB_LEVELS 1

const LEVEL levels[NB_LEVELS] = {
	{LEVEL1_TILES,(u16*)level1_tiles,LEVEL1_PALS,(u16*)level1_pals,LEVEL1_WIDTH,LEVEL1_HEIGHT,(u16*)level1_map_1,(u16*)level1_map_2,(u8*)level1_type_1,(u8*)level1_type_2,(u8*)level1_mark,LEVEL1_MARK}
};
