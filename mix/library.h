/*****************************************************************
 *
 * C Library of useful, generic NGPC routines
 *
 *****************************************************************/
#ifndef _LIBRARY_H
#define _LIBRARY_H

/*
 * Function Prototypes contained within the library
 */
void InitNGPC(void);
void InstallTileSet(const unsigned short Tiles[][8], u16 Len);

void ClearScreen(u8 ScrollPlane);
void ClearSprites();
void ClearPals();
void ClearAll();
void SetBackgroundColour(u16 Col);
void SysSetSystemFont(void);
void SetPalette(u8 ScrollPlane, u8 PaletteNo, u16 Col0, u16 Col1, u16 Col2, u16 Col3);
void Sleep(u8 VBLanks);

void GetTile(u8 ScrollPlane, u8 *PaletteNo, u8 XPos, u8 YPos, u16 *TileNo);
void PutTile(u8 ScrollPlane, u8 PaletteNo, u8 XPos, u8 YPos, u16 TileNo);

void PrintDecimal(u8 Plane, u8 PaletteNo, u8 x, u8 y, u16 Value, u8 Len);
void PrintString(u8 Plane, u8 Palette, u8 XPos, u8 YPos, const char * theString);

#define TOP_PRIO 24
#define MIDDLE_PRIO 16
#define LOW_PRIO 8

void SetSprite(u8 SpriteNo, u16 TileNo, u8 Chain, u8 XPos, u8 YPos, u8 PaletteNo, u8 Prio);
void SetSpritePosition(u8 SpriteNo, u8 XPos, u8 YPos);

void SeedRandom(void);
u16 GetRandom(u16 Value);

/*
 * Defines used by the library
 */
#define SCR_1_PLANE	(1)
#define SCR_2_PLANE	(2)
#define SPRITE_PLANE (3)

/*
 * Public variables
 */
extern volatile u8 VBCounter;


#endif

