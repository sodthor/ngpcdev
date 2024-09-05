#ifndef _TABLES_H_
#define _TABLES_H_

#define PRECISION 512
#define PRECISION2 256
#define MULT 128
#define SHIFT 7
#define PMASK 511
#define DIV_MULT 65536
#define DIV_SHIFT 16
#define MAX_DIV 32767

#define DIVIDE(a,b) (DIVIDE_NS(a,b)>>DIV_SHIFT)
#define DIVIDE_NS(a,b) ((b)<0 ? Multiply32bit(-(a),tdiv[-(b)]) : Multiply32bit((a),tdiv[b]))

#include "ngpc.h"

#ifndef _TABLES_C_
extern const s16 tsin[PRECISION];
extern const s16 tcos[PRECISION];
extern const s32 tdiv[MAX_DIV+1];
#endif

#endif
