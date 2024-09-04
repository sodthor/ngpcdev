#ifndef _LEVELS_H
#define _LEVELS_H

#include "ngpc.h"

typedef struct {
	u8		w;
	u8		h;
	u8*		map;
} LEVEL;

#define NB_LEVELS 2

#ifndef _LEVELS_C
extern const LEVEL levels[NB_LEVELS];
#endif

#endif
