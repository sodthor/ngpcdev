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
void InstallTileSetAt(const u16 Tiles[][8], u16 Len, u16 Offset);

void ClearScreen(u8 ScrollPlane);
void SetBackgroundColour(u16 Col);
void SysSetSystemFont(void);
void SetPalette(u8 ScrollPlane, u8 PaletteNo, u16 Col0, u16 Col1, u16 Col2, u16 Col3);
void Sleep(u8 VBLanks);

void GetTile(u8 ScrollPlane, u8 *PaletteNo, u8 XPos, u8 YPos, u16 *TileNo);
void PutTile(u8 ScrollPlane, u8 PaletteNo, u8 XPos, u8 YPos, u16 TileNo);

void PrintDecimal(u8 Plane, u8 PaletteNo, u8 x, u8 y, u16 Value, u8 Len);
void PrintHex(u8 Plane, u8 PaletteNo, u8 x, u8 y, u32 Value, u8 Len);
void PrintString(u8 Plane, u8 Palette, u8 XPos, u8 YPos, const char * theString);

void SetSprite(u8 SpriteNo, u16 TileNo, u8 Chain, u8 XPos, u8 YPos, u8 PaletteNo);
void SetSpritePosition(u8 SpriteNo, u8 XPos, u8 YPos);
void SetSpriteFlip(u8 SpriteNo, bool HFlip, bool VFlip);
void SetSpriteFlipChain(u8 SpriteNo, u8 XWidth, u8 YHeight, u8 tileCount, bool HFlip, bool VFlip);
void SetPlanePosition(u8 Plane, u8 XPos, u8 YPos);

void SetSpriteTile(u8 SpriteNo, u16 TileNo, u8 Chain);
void SetSpriteHFlipBlock(u8 SpriteNo, u8 tileNo);

s32 Multiply32bit(s32 Value1, s32 Value2);

u16 GetRandom(u16 Value);

typedef struct tagTIME
{
   u8 Year;
   u8 Month;
   u8 Day;
   u8 Hour;
   u8 Minute;
   u8 Second;
   u8 LeapYear:4;
   u8 Weekday:4;
} TIME;

void GetTime(TIME * pTime);

typedef struct tagALARM
{
   u8 Day;
   u8 Hour;
   u8 Min;
   u8 Code;
} ALARM;

void SetWake(ALARM * pAlarm);
void SetAlarm(ALARM * pAlarm);

void CpuSpeed(u8 spd);
void ResumeOff(void);

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

