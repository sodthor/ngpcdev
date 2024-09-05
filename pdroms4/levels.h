#ifndef _LEVELS_H
#define	_LEVELS_H

#define LEVEL_COUNT 8

const u8 levels[LEVEL_COUNT][16][16] =
{
#include "levels/level0.h"
    ,
#include "levels/level1.h"
    ,
#include "levels/level2.h"
    ,
#include "levels/level3.h"
    ,
#include "levels/level4.h"
    ,
#include "levels/level5.h"
    ,
#include "levels/level6.h"
    ,
#include "levels/level7.h"
};

#define MAX_LINES 64
#define LINE_END 0xf000

const s16 lines[LEVEL_COUNT][4][MAX_LINES] =
{
#include "levels/level0_lines.h"
    ,
#include "levels/level1_lines.h"
    ,
#include "levels/level2_lines.h"
    ,
#include "levels/level3_lines.h"
    ,
#include "levels/level4_lines.h"
    ,
#include "levels/level5_lines.h"
    ,
#include "levels/level6_lines.h"
    ,
#include "levels/level7_lines.h"
};

#endif	/* _LEVELS_H */
