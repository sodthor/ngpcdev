#include "ngpc.h"
#ifdef WIN32
#ifndef GCC
#include <stdio.h>
FILE _iob[] = { NULL, NULL, NULL };
FILE * __iob_func(void)
{
    return _iob;
}
#endif
#include <SDL.h>
#include <SDL_types.h>
#include "win32.h"
#else
#include "carthdr.h"
#define NULL 0L
#define LOGGER(S)
#endif

#include "library.h"

#include "music.h"
#include "magical0.mh"

#include "blocks.h"
#include "sprites.h"
#include "sprites_prio.h"

const u8 blocks_type[544] = {
1,1,1,1,1,1,1,1,1,1,3,3,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,
1,1,1,1,1,1,1,1,1,1,3,3,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,
1,1,0,0,0,0,1,1,1,1,1,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,
1,1,0,0,0,0,1,1,1,1,1,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,
0,0,0,0,0,0,1,1,1,1,0,0,1,1,1,1,1,1,0,0,0,0,0,0,1,1,1,1,1,1,1,1,0,0,
0,0,0,0,0,0,1,1,1,1,0,0,1,1,1,1,1,1,0,0,0,0,0,0,1,1,1,1,1,1,1,1,0,0,
1,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,1,1,1,0,0,1,1,1,1,
1,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,1,1,1,0,0,1,1,1,1,
1,1,0,0,0,0,0,0,0,0,2,2,2,2,2,2,0,0,0,0,0,0,0,0,1,1,1,1,0,0,1,1,1,1,
1,1,0,0,0,0,0,0,0,0,2,2,2,2,2,2,0,0,0,0,0,0,0,0,1,1,1,1,0,0,1,1,1,1,
1,1,1,1,1,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,1,1,1,1,1,1,1,1,1,1,1,1,1,
1,1,1,1,1,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,1,1,1,1,1,1,1,1,1,1,1,1,1,
0,0,1,1,1,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,1,1,1,1,1,1,1,1,1,0,0,0,0,
0,0,1,1,1,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,1,1,1,1,1,1,1,1,1,0,0,0,0,
0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,1,1,1,1,1,0,0,0,0,0,0,0,0,
0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,1,1,1,1,1,0,0,0,0,0,0,0,0
};

typedef enum {
    CRAB = 0,
    SCARAB,
    SKULL,
    FISH,
    BAT,
    BOMB,
    ENEMY_TYPE_COUNT
} ENEMY_TYPE ;

const u8 ENEMY_ANIM[ENEMY_TYPE_COUNT][4] = {
    {8,9,10,11},
    {12,13,12,13},
    {16,17,18,19},
    {14,14,15,15},
    {20,21,22,21},
    {5,6,7,6}
};

const u8 ENEMY_DEAD[ENEMY_TYPE_COUNT][8] = {
    {25,24,25,24,0,0,0,0},
    {25,24,25,24,0,0,0,0},
    {25,24,25,24,0,0,0,0},
    {25,24,25,24,0,0,0,0},
    {25,24,25,25,24,24,23,23},
    {35,34,33,32,31,30,29,28}
};

const s8 ENEMY_SPEED[ENEMY_TYPE_COUNT] = {
    1,
    1,
    2,
    1,
    1,
    0
};

typedef enum {
    EATEN,
    IMPALED,
    EXPLODED,
    DROWNED,
    SUICIDE,
    DEATH_TYPE_COUNT
} DEATH_TYPE;

const u16 PLAYER_DEAD[DEATH_TYPE_COUNT][8] = {
    {43,43,42,42,41,41,40,40},
    {51,51,50,50,49,49,48,48},
    {63,63,62,62,61,61,60,60},
    {59,58,57,56,55,54,53,52},
    {39,39,38,38,37,37,36,36}
};

#include "animated.h"

typedef enum {
    BRIDGE = 0,
    BOX,
    LIFE,
    STONE,
    RING,
    GEMS,
    DOOR_TOP,
    DOOR_BOTTOM,
    TRAP,
    SWITCH,
    PIKES
} ANIM_TYPE;

typedef struct {
    u16 id;
    u16 x, y;
    u16 flip;
} ANIMATED_TILE;

typedef struct {
    u8 id;
    u16 x, y;
} SPRITE_POS;

#include "level0.h"

#define MAX(a,b) ((a)>(b)?(a):(b))
#define MIN(a,b) ((a)<(b)?(a):(b))

#define LEVEL_COUNT 1

#define RESERVED 396
s16 set_level[BLOCKS_TILES_COUNT];
u16 used_level[BLOCKS_TILES_COUNT];
u16 new_tiles[RESERVED];
#define SPR_RESERVED 512-RESERVED-1
s16 set_sprites[SPRITES_TILES_COUNT];
u16 used_sprites[SPRITES_TILES_COUNT];

u16 level_dx, level_dy;
u16* level; // tiles
u16* flevel; // flip flags
u16* plevel; // palettes
u16 level_used; // tiles used
u16 aclevel; // animation count

s16 followDir;
u16 followCount;
s16 followDY;
#define FOLLOW_DOWN 8
#define FOLLOW_UP 16

u8 lastSpr; // sprite count

typedef struct entity {
    s16 x, y;
    u8 id; // entity id
    u8 spr; // top left sprite
    u8 vis; // visible
    u8 anim;
    u8 dead;
    u8 active;
    s16 dx; // -1: right to left, 1: left to right, 0: inactive
    s16 dy; // <0: jump, >0: falling, 0: horizontal
} ENTITY;

ENTITY player;
#define MAX_ENEMIES 32
ENTITY enemies[MAX_ENEMIES];
u8 enemy_count;

typedef struct {
    u16 id;
    u16 x0, y0;
    u16 x, y;
    u16 flip;
    u8 active;
    u8 frame;
    u8 counter;
    u8 change;
} ANIMATION;

#define ANIMATION_COUNTER 4
#define MAX_ANIMATIONS 64
ANIMATION animations[MAX_ANIMATIONS];

void __interrupt myVBL()
{
//SetBackgroundColour(RGB(15, 0, 0));
    WATCHDOG = WATCHDOG_CLEAR;
    if (USR_SHUTDOWN) { SysShutdown(); while (1); }
    VBCounter++;
}

void preloadTiles(s16 *ref_block, u16 tiles_count, u16 tiles_start, u16 tiles_max, u16* tiles, u16* idx1, u16* idx2, u16* priority, u16 used) {
    u16 i, k = 0, t;
    u16 *tileram = TILE_RAM;

    for (i = 0; i < tiles_count; ++i) {
        ref_block[i] = -1;
    }

    // load new tiles
    for (i = 0; i < used && k < tiles_max; ++i) {
        t = idx1[priority[used-i-1]];
        if (ref_block[t] < 0) {
            ref_block[t] = k;
            // transfert tile
            memcpy16(tileram+((tiles_start+k)<<3), tiles+(t<<3), 8);
            k += 1;
        }
        t = idx2[priority[used-i-1]];
        if (k < tiles_max && ref_block[t] < 0) {
            ref_block[t] = k;
            // transfert tile
            memcpy16(tileram+((tiles_start+k)<<3), tiles+(t<<3), 8);
            k += 1;
        }
    }
}

void initTiles()
{
    u16 *tileram = TILE_RAM;
    u16 *pal1 = SCROLL_1_PALETTE;
    u16 *pal2 = SCROLL_2_PALETTE;
    u16 *pal3 = SPRITE_PALETTE;
    u16 i;

    // clear first tile
    for (i=0;i<8;++i)
        tileram[i] = 0;

    for (i=0;i<BLOCKS_NPALS1*4;++i) {
        pal1[i] = BLOCKS_PALS1[i];
        pal2[i] = BLOCKS_PALS2[i];
    }

    for (i=0;i<SPRITES_NPALS1*4;++i) {
        pal3[i] = SPRITES_PALS1[i];
        pal3[i+32] = SPRITES_PALS2[i];
    }

    preloadTiles(set_sprites, SPRITES_TILES_COUNT, 1, 512-RESERVED-1, (u16*)SPRITES_TILES, (u16*)SPRITES_IDX1, (u16*)SPRITES_IDX2, (u16*)SPRITES_PRIORITY, SPRITES_TILES_COUNT-1);
    preloadTiles(set_level, BLOCKS_TILES_COUNT, 512-RESERVED, RESERVED, (u16*)BLOCKS_TILES, (u16*)BLOCKS_IDX1, (u16*)BLOCKS_IDX2, plevel, level_used);
}

void loadTileRam(s16 *ref_block, u16 tiles_start, u16* tiles, u16* priority, u16 tiles_used, u16* used, u16* new_tiles, u16 nt) {
    u16 i, k, t, b;
    s16 last = 0;
    u16 *tileram = TILE_RAM;

    for (i = 0; i < nt; ++i) {
        // load new tiles
        b = new_tiles[i];
        if (ref_block[b] < 0) {
            // find free slot
            for (;last < tiles_used; ++last) {
                t = priority[last];
                if ((!used[t]) && (ref_block[t] >= 0)) {
                    k = ref_block[t];
                    ref_block[t] = -1;
                    ref_block[b] = k;
                    // transfert tile
                    memcpy16(tileram+((tiles_start+k)<<3), tiles+(b<<3), 8);
                    break;
                }
            }
            if (last == tiles_used)
                break;
        }
    }
}

u16 addNewTiles(u16 startX, u16 endX, u16 startY, u16 endY, u16 nt, u8 absolute) {
    u16 i, j, t, b;
    u16 k = absolute ? (startX * LEVEL0_HEIGHT) : ((level_dx + startX) * LEVEL0_HEIGHT + level_dy);

    for (i = startX; i < endX; ++i) {
        for (j = startY; j < endY; ++j) {
            t = level[k+j];
            if (t != 0xffff) {
                if (t&0x8000) {
                    t = anim_frames[animations[t&0xff].id][(t&0x300)>>8][animations[t&0xff].frame];
                    if (t == 0xffff)
                        continue;
                }
                b = BLOCKS_IDX1[t];
                if (++used_level[b] == 1)
                    new_tiles[nt++] = b;
                b = BLOCKS_IDX2[t];
                if (++used_level[b] == 1)
                    new_tiles[nt++] = b;
            }
        }
        k += LEVEL0_HEIGHT;
    }
    return nt;
}

void fillPlanes(u16 startX, u16 endX, u16 startY, u16 endY, u8 absolute) {
    u16 i, j;
    u16 k = absolute ? (startX * LEVEL0_HEIGHT) : ((level_dx + startX) * LEVEL0_HEIGHT + level_dy);
    u16 d, t;
    u16 dy = absolute ? (startY << 5) : ((level_dy + startY) << 5);
    u16 *scrp1 = SCROLL_PLANE_1;
    u16 *scrp2 = SCROLL_PLANE_2;

    for (i = startX; i < endX; ++i) {
        d = dy + (((absolute ? 0 : level_dx) + i) & 31);
        for (j = startY; j < endY; ++j, d+=32) {
            t = level[k+j];
            if (t != 0xffff) {
                if (t&0x8000) {
                    t = anim_frames[animations[t&0xff].id][(t&0x300)>>8][animations[t&0xff].frame];
                    if (t != 0xffff) {
                        // flip?
                        scrp1[d] = (flevel[k+j]^BLOCKS_FLIP1[t]) | (((u16)BLOCKS_PALIDX1[t])<<9) | (512-RESERVED+set_level[BLOCKS_IDX1[t]]);
                        scrp2[d] = (flevel[k+j]^BLOCKS_FLIP2[t]) | (((u16)BLOCKS_PALIDX2[t])<<9) | (512-RESERVED+set_level[BLOCKS_IDX2[t]]);
                    } else {
                        scrp1[d] = 0;
                        scrp2[d] = 0;
                    }
                } else {
                    scrp1[d] = (flevel[k+j]^BLOCKS_FLIP1[t]) | (((u16)BLOCKS_PALIDX1[t])<<9) | (512-RESERVED+set_level[BLOCKS_IDX1[t]]);
                    scrp2[d] = (flevel[k+j]^BLOCKS_FLIP2[t]) | (((u16)BLOCKS_PALIDX2[t])<<9) | (512-RESERVED+set_level[BLOCKS_IDX2[t]]);
                }
            } else {
                scrp1[d] = 0;
                scrp2[d] = 0;
            }
        }
        k += LEVEL0_HEIGHT;
    }
}

void clearTiles(u16 startX, u16 endX, u16 startY, u16 endY, u8 absolute) {
    u16 i, j, t;
    u16 k = absolute ? (startX * LEVEL0_HEIGHT) : ((level_dx + startX) * LEVEL0_HEIGHT + level_dy);
    for (i = startX; i < endX; ++i) {
        for (j = startY; j < endY; ++j) {
            t = level[k+j];
            if (t != 0xffff) {
                if (t&0x8000) {
                    t = anim_frames[animations[t&0xff].id][(t&0x300)>>8][animations[t&0xff].frame];
                    if (t == 0xffff)
                        continue;
                }
                used_level[BLOCKS_IDX1[t]]--;
                used_level[BLOCKS_IDX2[t]]--;
            }
        }
        k += LEVEL0_HEIGHT;
    }
}

void setLevelPos(u8 flag, u16 nt) {
    u16 i;
    u16 li=level_dx+21>LEVEL0_WIDTH?20:21;
    u16 lj=level_dy+20>LEVEL0_HEIGHT?19:20;

    if (flag) {
        // Find new tiles
        if (flag&J_LEFT)
            nt = addNewTiles(0, 1, 0, lj, nt, 0);
        else if ((flag&J_RIGHT) && li == 21)
            nt = addNewTiles(20, 21, 0, lj, nt, 0);
        if (flag&J_UP)
            nt = addNewTiles((flag&J_RIGHT) ? 1 : 0, (flag&J_LEFT) ? 20 : 21, 0, 1, nt, 0);
        else if ((flag&J_DOWN) && lj == 20)
            nt = addNewTiles((flag&J_RIGHT) ? 1 : 0, (flag&J_LEFT) ? 20 : 21, 19, 20, nt, 0);

        // Remove tiles
        if (flag&J_LEFT)
            clearTiles(20, 21, 0, 20, 0);
        else if (flag&J_RIGHT)
            clearTiles(0, 1, 0, 20, 0);
        if (flag&J_UP)
            clearTiles((flag&J_RIGHT) ? 1 : 0, (flag&J_LEFT) ? 20 : 21, 19, 20, 0);
        else if (flag&J_DOWN)
            clearTiles((flag&J_RIGHT) ? 1 : 0, (flag&J_LEFT) ? 20 : 21, 0, 1, 0);
    }

    loadTileRam(set_level, 512-RESERVED, (u16*)BLOCKS_TILES, plevel, level_used, used_level, new_tiles, nt);

    for (i = 0; i < aclevel; ++i) {
        if (animations[i].change) {
            animations[i].change = 0;
            fillPlanes(animations[i].x0, animations[i].x0 + 2,
                       animations[i].y0, animations[i].y0 + 2,
                       1);
        }
    }

    if (flag) {
        // display all tiles
        if (flag&J_LEFT)
            fillPlanes(0, 1, 0, lj, 0);
        else if ((flag&J_RIGHT) && li == 21)
            fillPlanes(20, 21, 0, lj, 0);
        if (flag&J_UP)
            fillPlanes((flag&J_RIGHT) ? 1 : 0, (flag&J_LEFT) ? 20 : 21, 0, 1, 0);
        else if ((flag&J_DOWN) && lj == 20)
            fillPlanes((flag&J_RIGHT) ? 1 : 0, (flag&J_LEFT) ? 20 : 21, 19, 20, 0);
    }
}

const u16 spr_offset[4] = {0,1,SPRITES_WIDTH,SPRITES_WIDTH+1};
const u8 spr_x_offset[2][4] = {{0,8,0,8},{8,0,8,0}};
const u8 spr_y_offset[4] = {0,0,8,8};

void clearEntity(ENTITY *e) {
    u16 i, id = e->spr;
    for (i = 0; i < 4; ++i) {
        --used_sprites[SPRITES_IDX1[id + spr_offset[i]]];
        --used_sprites[SPRITES_IDX2[id + spr_offset[i]]];
    }
    e->vis = 0;
}

u16 prepareSprite(ENTITY *e, u16 spr, u16* new_tiles, u16 nt) {
    u16 id = ((spr&0x3)<<1) + ((spr&0xffc)<<2);
    u16 i, b;
    if (e->vis) {
        if (e->spr == id)
            return nt;
        clearEntity(e);
    } else if (!e->active) {
        e->active = 1;
        e->dx = -ENEMY_SPEED[e->id];
    }
    e->spr = id;
    e->vis = 1;
    for (i = 0; i < 4; ++i) {
        b = SPRITES_IDX1[id + spr_offset[i]];
        if (b != SPRITES_EMPTY && ++used_sprites[b] == 1)
            new_tiles[nt++] = b;
        b = SPRITES_IDX2[id + spr_offset[i]];
        if (b != SPRITES_EMPTY && ++used_sprites[b] == 1)
            new_tiles[nt++] = b;
    }
    return nt;
}

u8 showSprite(ENTITY* e, u8 i, u8 x, u8 y) {
    u16 j, id, sid = e->spr;
    u8 f = e->dx < 0 ? 1 : 0;
    u16 flip = f ? H_FLIP : 0;
    for (j = 0; j < 4; ++j) {
        id = sid + spr_offset[j];
        if (SPRITES_IDX1[id] != SPRITES_EMPTY) {
            SetSprite(i++,
                      x + spr_x_offset[f][j],
                      y + spr_y_offset[j],
                      SPRITES_PALIDX1[id],
                      set_sprites[SPRITES_IDX1[id]] + 1,
                      0,
                      TOP_PRIO,
                      flip^SPRITES_FLIP1[id]);
        }
        if (SPRITES_IDX2[id] != SPRITES_EMPTY) {
            SetSprite(i++,
                      x + spr_x_offset[f][j],
                      y + spr_y_offset[j],
                      SPRITES_PALIDX2[id]+8,
                      set_sprites[SPRITES_IDX2[id]] + 1,
                      0,
                      TOP_PRIO,
                      flip^SPRITES_FLIP2[id]);
        }
    }
    return i;
}

void clearSprites(u8 i) {
    if (lastSpr>i)
        memclr16(((u16*)SPRITE_RAM) + (i << 1), ((lastSpr - i) << 1));
/*    u8* spram = SPRITE_RAM;
    u8 j;
    for (j = i; j < lastSpr; ++j) {
        spram[j<<2] = 0;
        spram[(j<<2)+1] = 0;
    }*/
    lastSpr = i;
}

#define ANIM_TILE(t) (((t!=0xffff) && (t&0x8000)) ? anim_frames[animations[t&0xff].id][(t&0x300)>>8][animations[t&0xff].frame] : t)

u8 isFreeH(ENTITY *e, s16 dx) {
    u16 t = ((e->x >> 3) + dx) * LEVEL0_HEIGHT + (e->y >> 3);
    u16 lt0 = ANIM_TILE(level[t]);
    u16 lt1 = ANIM_TILE(level[t+1]);
    u16 lt2 = ((e->y&0x7) == 0) ? 0xffff : ANIM_TILE(level[t+2]);
    return ((lt0 == 0xffff || blocks_type[lt0] != 1)
          &&(lt1 == 0xffff || blocks_type[lt1] != 1)
          &&(lt2 == 0xffff || blocks_type[lt2] != 1));
}

u8 isFreeV(ENTITY *e, s16 dy) {
    u16 t = (e->y>>3)+dy, lt0, lt1, lt2;
    if (t >= LEVEL0_HEIGHT)
        return 1;
    t += (e->x>>3)*LEVEL0_HEIGHT;
    lt0 = ANIM_TILE(level[t]);
    lt1 = ANIM_TILE(level[t+LEVEL0_HEIGHT]);
    lt2 = ((e->x&0x7) == 0) ? 0xffff : ANIM_TILE(level[t+LEVEL0_HEIGHT+LEVEL0_HEIGHT]);
    return ((lt0 == 0xffff || blocks_type[lt0] != 1)
          &&(lt1 == 0xffff || blocks_type[lt1] != 1)
          &&(lt2 == 0xffff || blocks_type[lt2] != 1));
}

u8 isLethal(ENTITY *e, u16 dx, u16 dy) {
    u16 t = ((e->y+dy)>>3), lt0;
    if (t >= LEVEL0_HEIGHT) {
        return 0;
    }
    t += ((e->x+dx)>>3)*LEVEL0_HEIGHT;
    lt0 = ANIM_TILE(level[t]);
    return lt0 != 0xffff && blocks_type[lt0] == 2;
}

void displaySprites() {
    u8 i, j, x0, y0;
    u16 new_tiles[512-RESERVED-1];
    u16 nt;
    s16 scr_y = SCR1_Y;

    // Sprites
    if (player.dead != 1) {
        if (player.dead) {
            nt = PLAYER_DEAD[player.id][((player.dead--)>>2)&0x7];
        } else {
            nt = player.dx == 0 ? 4 : ((player.x>>2)&3);
        }
        nt = prepareSprite(&player, nt, new_tiles, 0);
    } else {
        nt = 0;
    }

    for (i = 0; i < enemy_count; ++i) {
        if (enemies[i].dead) {
            if (enemies[i].dead == 1) {
                if (enemies[i].vis)
                    clearEntity(&enemies[i]);
                continue;
            }
        }
        if (((player.x <= 72 && enemies[i].x < 160)
          || (player.x > ((LEVEL0_WIDTH<<3) - 88) && enemies[i].x > (LEVEL0_WIDTH<<3) - 176)
          || (player.x >= enemies[i].x && player.x - enemies[i].x < 88)
          || (enemies[i].x >= player.x && enemies[i].x - player.x < 88))
        &&  ((enemies[i].y < scr_y + 152) && (enemies[i].y > scr_y - 168))) {
            // visible
            j = enemies[i].dead
                ? ENEMY_DEAD[enemies[i].id][((enemies[i].dead--)>>2)&0x7]
                : ENEMY_ANIM[enemies[i].id][((enemies[i].anim++)>>2)&0x3];
            nt = prepareSprite(&enemies[i], j, new_tiles, nt);
        } else if (enemies[i].vis) {
            clearEntity(&enemies[i]);
        }
    }

    loadTileRam(set_sprites, 1, (u16*)SPRITES_TILES, (u16*)SPRITES_PRIORITY, SPRITES_TILES_COUNT-1, used_sprites, new_tiles, nt);

    x0 = (u8) (player.x<=72 ? player.x
                            : (player.x>((LEVEL0_WIDTH<<3) - 88) ? 160 - ((LEVEL0_WIDTH<<3) - player.x)
                                                                 : 72));
    y0 = (u8) (player.y<=68 ? player.y
                            : (player.y>((LEVEL0_HEIGHT<<3) - 84) ? 152 - ((LEVEL0_HEIGHT<<3) - player.y)
                                                                  : 68));
    i = player.dead != 1 ? showSprite(&player, 0, x0, (u8) (y0 - followDY)) : 0;

    for (j = 0; j < enemy_count; ++j) {
        if (enemies[j].vis) {
            i = showSprite(&enemies[j],
                           i,
                           (u8) (x0 + (enemies[j].x - player.x)),
                           (u8) (enemies[j].y - scr_y));
        }
    }
    clearSprites(i);
}

void moveEnemies() {
    u8 i;
    for (i = 0; i < enemy_count; ++i) {
        if (!enemies[i].active)
            continue;
        // check collision
        if (!player.dead && !enemies[i].dead && enemies[i].x > player.x - 12 && enemies[i].x < player.x + 12
            && enemies[i].y > player.y - 12 && enemies[i].y < player.y + 12) {
            if (player.dy > 0 && player.y + 8 < enemies[i].y
                && enemies[i].id != FISH && enemies[i].id != SCARAB && enemies[i].id != BOMB) {
                enemies[i].dead = enemies[i].id == BAT ? 31 : 15;
            } else {
                player.dead = 31;
                if (enemies[i].id == BOMB) {
                    enemies[i].dead = 31;
                    player.id = EXPLODED;
                } else {
                    player.id = EATEN;
                }
            }
        }
        if (enemies[i].dy == 0 && !enemies[i].dead) {
            if ((enemies[i].x&0xf) != 0) {
                enemies[i].x += enemies[i].dx;
            } else if (enemies[i].id < FISH && isFreeV(&enemies[i], 2)) {
                enemies[i].dy = 2;
                enemies[i].y += enemies[i].dy;
            } else if (enemies[i].dx < 0) {
                if (enemies[i].x > 0 && isFreeH(&enemies[i], -1)) {
                    enemies[i].x += enemies[i].dx;
                } else {
                    enemies[i].dx = -enemies[i].dx;
                }
            } else if (enemies[i].dx > 0) {
                if (enemies[i].x < (LEVEL0_WIDTH<<3)-16 && isFreeH(&enemies[i], 2)) {
                    enemies[i].x += enemies[i].dx;
                } else {
                    enemies[i].dx = -enemies[i].dx;
                }
            }
        } else {
            if ((enemies[i].y&0xf) != 0) {
                enemies[i].y += enemies[i].dy;
            } else if (enemies[i].y >= (LEVEL0_HEIGHT<<3)) {
                enemies[i].dead = 1;
            } else if (isFreeV(&enemies[i], 2)) {
                enemies[i].y += enemies[i].dy;
            } else {
                enemies[i].dy = 0;
            }
        }
    }
}

u8 movePlayer() {
    u8 upd = 0;
    //s16 startX = player.x & 0xfff8;
    s16 i;

    if (player.dy < 0) {
        for (i = player.dy>>2; i < 0; ++i) {
            if ((player.y&0x7) == 0) {
                if (player.y > 0 && isFreeV(&player, -1)) {
                    --player.y;
                    if (player.y > 68 && player.y < ((LEVEL0_HEIGHT<<3) - 84)) {
                        level_dy--;
                        upd |= J_UP;
                    }
                } else {
                    player.dy = -1;
                    break;
                }
            } else {
                --player.y;
            }
        }
        ++player.dy;
    } else if (player.dy > 0) {
        for (i = 0; i < player.dy; ++i) {
            if ((player.y&0x7) == 0) {
                if (isFreeV(&player, 2)) {
                    if (++player.y >= (LEVEL0_HEIGHT<<3)) {
                        player.dead = 1;
                        break;
                    }
                } else {
                    player.dy = -1;
                    break;
                }
            } else {
                if (++player.y >= (LEVEL0_HEIGHT<<3)) {
                    player.dead = 1;
                    break;
                }
                if ((player.y&0x7) == 0) {
                    if (player.y > 68 && player.y < ((LEVEL0_HEIGHT<<3) - 84)) {
                        level_dy++;
                        upd |= J_DOWN;
                    }
                }
            }
        }
        if (player.dy < 4)
            ++player.dy;
    } else if ((player.y&0xf) || isFreeV(&player, 2)) {
        player.dy = 1;
    }

    if (player.dx < 0) {
        for (i = player.dx; i < 0; ++i) {
            if ((player.x&0x7) == 0) {
                if (player.x > 0 && isFreeH(&player,-1)) {
                    --player.x;
                    if (player.x > 72 && player.x < ((LEVEL0_WIDTH<<3) - 88)) {
                        level_dx--;
                        upd |= J_LEFT;
                    }
                } else {
                    break;
                }
            } else {
                --player.x;
                if (player.dy == 0 && player.dx == -1 && (player.x&0xf) == 0 && (player.y&0xf) == 0 && isFreeV(&player, 2)) {
                    player.dy = 1;
                    break;
                }
            }
        }
    } else if (player.dx > 0) {
        for (i = 0; i < player.dx; ++i) {
            if ((player.x&0x7) == 0) {
                if (player.x < (LEVEL0_WIDTH<<3)-16 && isFreeH(&player,2)) {
                    ++player.x;
                } else {
                    break;
                }
            } else {
                ++player.x;
                if ((player.x&0x7) == 0) {
                    if (player.x > 72 && player.x < ((LEVEL0_WIDTH<<3) - 88)) {
                        level_dx++;
                        upd |= J_RIGHT;
                    }
                    if (player.dy == 0 && player.dx == 1 && (player.x&0xf) == 0 && (player.y&0xf) == 0 && isFreeV(&player, 2)) {
                        player.dy = 1;
                        break;
                    }
                }
            }
        }
    }
    if (isLethal(&player, 4, 4)
     || isLethal(&player, 12, 4)
     || isLethal(&player, 4, 12)
     || isLethal(&player, 12, 12)) {
        player.dead = 31;
        player.id = IMPALED;
    }
    return upd;
}

u16 animUpdate() {
    u16 i, nt = 0;
    for (i = 0; i < aclevel; ++i) {
        if (animations[i].active == 1) {
            if (--animations[i].counter == 0) {
                if (animations[i].frame + 1 < anim_fcount[animations[i].id]) {
                    clearTiles(animations[i].x0, animations[i].x0 + 2,
                               animations[i].y0, animations[i].y0 + 2,
                               1);
                    animations[i].frame += 1;
                    nt = addNewTiles(animations[i].x0, animations[i].x0 + 2,
                                     animations[i].y0, animations[i].y0 + 2,
                                     nt, 1);
                    animations[i].change = 1;
                    animations[i].counter = ANIMATION_COUNTER;
                } else {
                    animations[i].active = 2;
                }
            }
        } else if (animations[i].active == 0) {
            switch(animations[i].id) {
                case RING:
                case GEMS:
                case LIFE:
                case TRAP:
                case SWITCH:
                case PIKES:
                    if (((player.x >= animations[i].x && player.x - animations[i].x < 16)
                         || (player.x < animations[i].x && animations[i].x - player.x < 16))
                        && ((player.y >= animations[i].y && player.y - animations[i].y < 16)
                         || (player.y < animations[i].y && animations[i].y - player.y < 16))) {
                        animations[i].active = 1;
                        animations[i].counter = ANIMATION_COUNTER;
                    }
                    break;
                case BRIDGE:
                    if (((player.x >= animations[i].x && player.x - animations[i].x < 16)
                         || (player.x < animations[i].x && animations[i].x - player.x < 16))
                        && (player.y < animations[i].y && animations[i].y - player.y <= 16)) {
                        animations[i].active = 1;
                        animations[i].counter = ANIMATION_COUNTER;
                    }
                    break;
                case BOX:
                    break;
                case STONE:
                    break;
                case DOOR_TOP:
                case DOOR_BOTTOM:
                    break;
            }
        }
    }
    return nt;
}

void gameLoop() {
    u8 i, j, joy = JOYPAD;

    if (player.dead == 0) {
        if (joy & J_RIGHT) {
            player.dx = (joy & J_DOWN) ? 1 : 2;
        }
        else if (joy & J_LEFT) {
            player.dx = (joy & J_DOWN) ? -1 : -2;
        } else {
            player.dx = 0;
        }
        if (joy & J_UP) {
            if (player.dy == 0 && !isFreeV(&player, 2)) {
                player.dy = -21;
            }
        }
    } else {
        player.dx = 0;
        player.dy = 0;
    }

    setLevelPos(player.dead == 0 ? movePlayer() : 0, animUpdate());
    moveEnemies();

    // Scroll x pos
    i = player.x <= 72 ? 0 : ((player.x >= ((LEVEL0_WIDTH<<3) - 88) ? ((LEVEL0_WIDTH<<3) - 160) : (player.x - 72)) & 0xff);
    SCR1_X = i;
    SCR2_X = i;
    // Scroll y pos
    i = player.y <= 68 ? 0 : ((player.y >= ((LEVEL0_HEIGHT<<3) - 84) ? ((LEVEL0_HEIGHT<<3) - 152) : (player.y - 68)) & 0xff);
    j = SCR1_Y;
    if (j < i) {
        if (followDir != 1 || followCount == 0) {
            followCount = FOLLOW_DOWN;
        } else if (--followCount == 0) {
            j += (i - j > 48) ? 4 : ((i - j > 24) ? 2 : 1);
            SCR1_Y = j;
            SCR2_Y = j;
            followCount = 1;
        }
        followDir = 1;
    } else if (j > i && player.dy <= 0) {
        if (followDir != 2 || followCount == 0) {
            followCount = FOLLOW_UP;
        } else if (--followCount == 0) {
            SCR1_Y = --j;
            SCR2_Y = j;
            followCount = 1;
        }
        followDir = 2;
    } else {
        followCount = 0;
        followDir = 0;
    }
    followDY = j - i;
//SetBackgroundColour(RGB(0, 0, 15));
    displaySprites();
//SetBackgroundColour(RGB(0, 0, 0));
}

void addEnemy(u8 id, u16 x, u16 y) {
    enemies[enemy_count].id = id;
    enemies[enemy_count].spr = 0;
    enemies[enemy_count].x = x;
    enemies[enemy_count].y = y;
    enemies[enemy_count].dead = 0;
    enemies[enemy_count].vis = 0;
    enemies[enemy_count].dx = 0;
    enemies[enemy_count].dy = 0;
    enemies[enemy_count].active = 0;
    enemies[enemy_count].anim = 0;
    enemy_count++;
}

void loadLevel(u8 lvl) {
    u16*  levels[LEVEL_COUNT] = { (u16*)LEVEL0 };
    u16* flevels[LEVEL_COUNT] = { (u16*)LEVEL0_FLIP };
    ANIMATED_TILE* alevels[LEVEL_COUNT] = { (ANIMATED_TILE*)LEVEL0_ANIMS };
    u16 aclevels[LEVEL_COUNT] = { LEVEL0_ANIM_COUNT };
    SPRITE_POS* slevels[LEVEL_COUNT] = { (SPRITE_POS*)LEVEL0_SPRITES };
    u16 sprcount[LEVEL_COUNT] = { LEVEL0_SPRITE_COUNT };
    u16* plevels[LEVEL_COUNT] = { (u16*)LEVEL0_PRIORITY };
    u16  ulevels[LEVEL_COUNT] = { LEVEL0_USED };
    u16 i, j, k, t;
    
    level_dx = 0;
    level_dy = 0;
    level = levels[lvl];
    flevel = flevels[lvl];
    plevel = plevels[lvl];
    level_used = ulevels[lvl];

    aclevel = aclevels[lvl];
    for (i = 0; i < aclevel; ++i) {
        animations[i].id     = alevels[lvl][i].id;
        animations[i].x0     = alevels[lvl][i].x;
        animations[i].y0     = alevels[lvl][i].y;
        animations[i].x      = animations[i].x0 << 3;
        animations[i].y      = animations[i].y0 << 3;
        animations[i].flip   = alevels[lvl][i].flip;
        animations[i].active = 0;
        animations[i].frame  = 0;
        animations[i].change = 0;
    }

    initTiles();

    memset(used_level, 0, sizeof(used_level));
    memset(used_sprites, 0, sizeof(used_sprites));

    // check used tiles
    k = level_dx * LEVEL0_HEIGHT + level_dy;
    for (i = 0; i < 21; ++i) {
        for (j = 0; j < 20; ++j) {
            t = level[k+j];
            if (t != 0xffff) {
                used_level[BLOCKS_IDX1[t]]++;
                used_level[BLOCKS_IDX2[t]]++;
            }
        }
        k += LEVEL0_HEIGHT;
    }
    // load and fill screen
    loadTileRam(set_level, 512-RESERVED, (u16*)BLOCKS_TILES, plevel, level_used, used_level, new_tiles, 0);
    fillPlanes(0, 21, 0, 20, 0);

    // find start
    player.dead = 0;
    player.x = 0;
    player.y = 0;
    player.dx = 0;
    player.dy = 0;
    player.vis = 0;
    player.active = 1;

    // enemies
    enemy_count = 0;
    for (i = 0; i < sprcount[lvl]; ++i) {
        if (slevels[lvl][i].id == 0) {
            player.y = slevels[lvl][i].y << 3;
        } else {
            addEnemy(slevels[lvl][i].id - 1,
                     slevels[lvl][i].x << 3,
                     slevels[lvl][i].y << 3);
        }
    }
    
    // Scroll x pos
    i = player.x <= 72 ? 0 : ((player.x >= ((LEVEL0_WIDTH<<3) - 88) ? ((LEVEL0_WIDTH<<3) - 160) : (player.x - 72)) & 0xff);
    SCR1_X = i;
    SCR2_X = i;
    // Scroll y position
    i = player.y <= 68 ? 0 : ((player.y >= ((LEVEL0_HEIGHT<<3) - 84) ? ((LEVEL0_HEIGHT<<3) - 152) : (player.y - 68)) & 0xff);
    SCR1_Y = i;
    SCR2_Y = i;
}

int main(
#ifdef WIN32
int argc, char*argv[]
#endif
)
{
    InitNGPC();
    ClearAll();
    SetBackgroundColour(RGB(0, 0, 0));

    SL_SoundInit();

    SL_LoadGroup(magical0,sizeof(magical0));
    SL_PlayTune(0);

    DISABLE_INTERRUPTS;
    VBL_INT = myVBL;
    ENABLE_INTERRUPTS;

    while (1) {
        loadLevel(0);

        lastSpr = 64;
        clearSprites(0);

        followCount = 0;
        followDir = 0;
        
        while (player.dead != 1)
        {
            gameLoop();
            Sleep(1);
        }
        ClearPals();
    }

    return 0;
}