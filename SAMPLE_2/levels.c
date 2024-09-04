#define _LEVELS_C
#include "levels.h"

#include "level0.h"
#include "level1.h"

const LEVEL levels[NB_LEVELS] = {
	{LEVEL0_WIDTH,LEVEL0_HEIGHT,(u8*)LEVEL0_MAP},
	{LEVEL1_WIDTH,LEVEL1_HEIGHT,(u8*)LEVEL1_MAP}
};
