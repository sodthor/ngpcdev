#include "ngpc.h"
#include "library.h"

#ifdef WIN32
void exit(int);
#include "win32.h"
#endif

volatile u8 VBCounter;

// dummy Interrupt function used for interrupt that are currently
// unused. To add an interrupt simply supply your own routine in the
// InitNGPC routine in the interrupt vector table initialisation
void __interrupt DummyFunction(void) {}


//////////////////////////////////////////////////////////////////////////////
// VBInterrupt
// Handles the default work required by the VBInterrupt (watchdog,
// shutdown and a standard counter)
//////////////////////////////////////////////////////////////////////////////
void SysShutdown(); // forward reference
void __interrupt VBInterrupt(void)
{
   // clear the watchdog
   WATCHDOG = WATCHDOG_CLEAR;

   // check for power down
   if (USR_SHUTDOWN)
   {
	  SysShutdown();
	  while (1);
   }
   
   // increment a counter
   VBCounter++;

   // TODO: add any other VBI code here.
}

//////////////////////////////////////////////////////////////////////////////
// InitNGPC
// This should be the first function called in main() it initialises the
// NGPC hardware (interrupt vector tables etc)
//////////////////////////////////////////////////////////////////////////////
void InitNGPC(void)
{
#ifdef WIN32
	initVideo();
#endif
   // Set NGP or NGPC mode
   if (OS_VERSION == 0)
   {
	  MEM_6f83 &= ~8; // res 3
	  MEM_6da0 = 0;
   }

   // set user answer
   USR_ANSWER |= 64; // set 6

   // install user interrupt vectors
   SWI3_INT = DummyFunction;
   SWI4_INT = DummyFunction;
   SWI5_INT = DummyFunction;
   SWI6_INT = DummyFunction;
   RTCI_INT = DummyFunction;
   VBL_INT = VBInterrupt;
   Z80_INT = DummyFunction;
   HBL_INT = DummyFunction;
   TI0_INT = DummyFunction;
   TI1_INT = DummyFunction;
   TI2_INT = DummyFunction;
   TI3_INT = DummyFunction;
   STX_INT = DummyFunction;
   SRX_INT = DummyFunction;
   DMA0_INT = DummyFunction;
   DMA1_INT = DummyFunction;
   DMA2_INT = DummyFunction;
   DMA3_INT = DummyFunction;

   ENABLE_INTERRUPTS;
   
   // set screen size
   WIN_X = 0;
   WIN_Y = 0;
   WIN_W = 160;
   WIN_H = 152;
}


//////////////////////////////////////////////////////////////////////////////
// SysShutdown                                                              //
//////////////////////////////////////////////////////////////////////////////
void SysShutdown()
{
#ifndef WIN32
   __asm(" ld rw3,0"); // 0 = VECT_SHUTDOWN

   // do the system call
   __asm(" ldf 3");
   __asm(" add w,w");
   __asm(" add w,w");
   __asm(" ld xix,0xfffe00");
   __asm(" ld xix,(xix+w)");
#ifndef GCC
   __asm(" call xix");
#else
   __asm(" call (xix)");
#endif
#else
	exit(0);
#endif
}


//////////////////////////////////////////////////////////////////////////////
// SysSetSystemFont                                                         //
//////////////////////////////////////////////////////////////////////////////
void SysSetSystemFont()
{
#ifndef WIN32
   __asm(" ld ra3,3"); 
   __asm(" ld rw3,5"); // VECT_SYSFONTSET

   // do the system call
   __asm(" ldf 3");
   __asm(" add w,w");
   __asm(" add w,w");
   __asm(" ld xix, 0xfffe00");
   __asm(" ld xix,(xix+w)");
   __asm(" call xix");
#endif
}

//////////////////////////////////////////////////////////////////////////////
// ClearScreen
// Inputs:
//		Plane - Scroll Plane to clear SCR_1_PLANE or SCR_2_PLANE
//////////////////////////////////////////////////////////////////////////////
void ClearScreen(u8 Plane)
{
   int i;
   u16 * Screen;

   switch(Plane)
   {
	  case SCR_1_PLANE:
		 Screen = SCROLL_PLANE_1;
		 break;
	  case SCR_2_PLANE:
		 Screen = SCROLL_PLANE_2;
		 break;
	  default:
		 return;
   }

   for (i = 0; i < 32*32; i ++)
	  Screen[i] = 0;
}

//////////////////////////////////////////////////////////////////////////////
// ClearSprites
//////////////////////////////////////////////////////////////////////////////
void ClearSprites()
{
    u8* sprites = SPRITE_RAM;
    u16 i;
    for (i = 0; i < 256; ++i)
        sprites[i] = 0;
}

//////////////////////////////////////////////////////////////////////////////
// ClearPals
//////////////////////////////////////////////////////////////////////////////
void ClearPals()
{
	u16 *p1 = SCROLL_1_PALETTE;
	u16 *p2 = SCROLL_2_PALETTE;
	u16 *ps = SPRITE_PALETTE;
	u16 i;
	for (i=0;i<64;++i)
		p1[i] = p2[i] = ps[i] = 0;
}

//////////////////////////////////////////////////////////////////////////////
// ClearAll
//////////////////////////////////////////////////////////////////////////////
void ClearAll()
{
    ClearPals();
    ClearScreen(SCR_1_PLANE);
    ClearScreen(SCR_2_PLANE);
    ClearSprites();
}

//////////////////////////////////////////////////////////////////////////////
// SetPalette
// Inputs:
//		Plane - Scroll Plane to clear SCR_1_PLANE or SCR_2_PLANE
//		PalleteNo - 0-15 the palette no to set
//      Col0-Col3 - The RGB values for the 4 colours within the palette
//////////////////////////////////////////////////////////////////////////////
void SetPalette(u8 Plane, u8 PaletteNo, u16 Col0, u16 Col1, u16 Col2, u16 Col3)
{
   u16 * thePalette;
   u8 Offset = PaletteNo * 4;

   switch (Plane)
   {
	  case SCR_1_PLANE:
		 thePalette = SCROLL_1_PALETTE;
		 break;
	  case SCR_2_PLANE:
		 thePalette = SCROLL_2_PALETTE;
		 break;
      case SPRITE_PLANE:
         thePalette = SPRITE_PALETTE;
         break;
	  default:
		 return;
   }
   
   thePalette[Offset] = Col0;
   thePalette[Offset+1] = Col1;
   thePalette[Offset+2] = Col2;
   thePalette[Offset+3] = Col3;
}


//////////////////////////////////////////////////////////////////////////////
// PutTile
// Inputs:
//		Plane - Scroll Plane to clear SCR_1_PLANE or SCR_2_PLANE
//		PalleteNo - 0-15 the palette number to set
//      XPos - X Position (0 to 19)
//      YPos - Y Position (0 to 18)
//      TileNo - Tile Number (0 to 511)
//////////////////////////////////////////////////////////////////////////////
void PutTile(u8 Plane, u8 Palette, u8 XPos, u8 YPos, u16 TileNo)
{
   u16 * ScreenPlane1 = SCROLL_PLANE_1;
   u16 * ScreenPlane2 = SCROLL_PLANE_2;

   u16 Offset = ((u16)YPos * 32) + XPos;
   u16 Value = TileNo + ((u16)Palette << 9);

   switch(Plane)
   {
	  case SCR_1_PLANE:
		 ScreenPlane1[Offset] = Value;
		 break;
	  case SCR_2_PLANE:
		 ScreenPlane2[Offset] = Value;
		 break;
	  default:
		 break;
   }
}


//////////////////////////////////////////////////////////////////////////////
// GetTile
// Inputs:
//		Plane - Scroll Plane to clear SCR_1_PLANE or SCR_2_PLANE
//      XPos - X Position (0 to 19)
//      YPos - Y Position (0 to 18)
// Outputs:
//		PalleteNo - 0-15 the palette number to set
//      TileNo - Tile Number (0 to 511)
//////////////////////////////////////////////////////////////////////////////
void GetTile(u8 Plane, u8 *Palette, u8 XPos, u8 YPos, u16 *TileNo)
{
   u16 * ScreenPlane1 = SCROLL_PLANE_1;
   u16 * ScreenPlane2 = SCROLL_PLANE_2;

   u16 Offset = ((u16)YPos * 32) + XPos;
  
   switch(Plane)
   {
      case SCR_1_PLANE:
	 *Palette = (u8)(ScreenPlane1[Offset] >> 9);
	 *TileNo = (ScreenPlane1[Offset] & 511);
	 break;
      case SCR_2_PLANE:
	 *Palette = (u8)(ScreenPlane2[Offset] >> 9);
	 *TileNo = (ScreenPlane2[Offset] & 511);
	 break;
      default:
	 break;
   }
}


//////////////////////////////////////////////////////////////////////////////
// Sleep
// Waits for specified number of VBlanks (60ths of a second)
//////////////////////////////////////////////////////////////////////////////
void Sleep(u8 VBlanks)
{
   VBCounter = 0;
   while (VBCounter < VBlanks)
   {
#ifdef WIN32
		checkInput();
		(*VBL_INT)();
		flipScreen();
#else
		__ASM("  NOP");
#endif
   }
}


//////////////////////////////////////////////////////////////////////////////
// PrintDecimal
// Displays a decimal number of the screen padded with leading zeros
// Inputs:
//		Plane - Scroll Plane to clear SCR_1_PLANE or SCR_2_PLANE
//		PalleteNo - 0-15 the palette number to set
//      x - X Position (0 to 19)
//      y - Y Position (0 to 18)
//      Value - The number to display
//      Len - The number of tiles to fill
//            e.g. Value = 15, Len = 4 will display 0015
//////////////////////////////////////////////////////////////////////////////
void PrintDecimal(u8 Plane, u8 PaletteNo, u8 x, u8 y, u16 Value, u8 Len)
{
   u8 i;

   for (i = Len; i > 0; i--)
   {
	  PutTile(Plane, PaletteNo, x+i-1, y, '0' + (Value % 10));
	  Value /= 10;
   }
}

//////////////////////////////////////////////////////////////////////////////
// InstallTileSet
// Copies your tile data to the NGPC tileram. Takes a 2D array and a
// length. The length field will equal "sizeof(Tiles)/2" which is the
// number of words in the 2D array.
// This style array can be exported directly from Steve Robb's NeoTile
// NGPC Tile Editing tool
//////////////////////////////////////////////////////////////////////////////
void InstallTileSet(const unsigned short Tiles[][8], u16 Len)
{
   u16 i;
   u16 * TileRam = TILE_RAM;
   u16 * theTiles = (u16 *)Tiles;

   for (i = 0; i < Len; i ++)
   {
	  TileRam[i] = *theTiles++;
   }
}

//////////////////////////////////////////////////////////////////////////////
// SetBackgroundColour
// Changes the background colour of NGPC screen.
// The macro RGB() can be used to specify Col.
// e.g SetBackgroundColour(RGB(15,0,0)); will set the background red
//////////////////////////////////////////////////////////////////////////////
void SetBackgroundColour(u16 Col)
{
   BG_PAL = Col;
   BG_COL = 0x80;
}


//////////////////////////////////////////////////////////////////////////////
// PrintString
// Displays a string on the screen at the specified location
// Inputs:
//		Plane - Scroll Plane to clear SCR_1_PLANE or SCR_2_PLANE
//		PalleteNo - 0-15 the palette number to set
//      XPos - X Position (0 to 19)
//      YPos - Y Position (0 to 18)
//      theString - The string to be displayed
//////////////////////////////////////////////////////////////////////////////
void PrintString(u8 Plane, u8 Palette, u8 XPos, u8 YPos, const char * theString)
{
   u16 * Screen;

   switch (Plane)
   {
	  case SCR_1_PLANE:
		 Screen = SCROLL_PLANE_1;
		 break;

	  case SCR_2_PLANE:
		 Screen = SCROLL_PLANE_2;
		 break;

	  default:
		 return;
   }

   while (*theString)
   {
	  u16 Offset = ((u16)YPos * 32) + XPos;
	  u16 Value = *theString + ((u16)Palette << 9);
	  Screen[Offset] = Value;

	  theString++;
	  XPos++;
   }
}



//////////////////////////////////////////////////////////////////////////////
// SetSprite
// Initialise a sprite by mapping a tile number and sprite palette no
// to a sprite number and allowing chaining to be set up.
// Inputs:
//		SpriteNo - 0-63 the sprite to move
//      XPos - X Position (0 to 255)
//      YPos - Y Position (0 to 255)
//////////////////////////////////////////////////////////////////////////////
void SetSprite(u8 SpriteNo, u8 XPos, u8 YPos, u8 PaletteNo, u16 TileNo, u16 Chain, u16 Prio, u16 Flip)
{
   u16* theSprite = (u16*)(SPRITE_RAM + (SpriteNo<<2));
   u8*  theSpriteCol = SPRITE_COLOUR;
   
   if (SpriteNo > 63)
      return;

   *(theSprite++) = Flip|Chain|Prio|TileNo;
   *theSprite = (((u16)YPos)<<8)|XPos;

   theSpriteCol[SpriteNo] = PaletteNo;
}

//////////////////////////////////////////////////////////////////////////////
// SetSpritePosition
// Moves a already initialise sprite
// Inputs:
//		SpriteNo - 0-63 the sprite to move
//      XPos - X Position (0 to 255)
//      YPos - Y Position (0 to 255)
//////////////////////////////////////////////////////////////////////////////
void SetSpritePosition(u8 SpriteNo, u8 XPos, u8 YPos)
{
   u8 * theSprite = SPRITE_RAM;
   theSprite += (SpriteNo * 4);

   *(theSprite+2) = XPos;
   *(theSprite+3) = YPos;
}

#define RAND_MAX 32767
volatile u32 RAND_RandomData;

//////////////////////////////////////////////////////////////////////////////
// SeedRandom
// Unfortunately we need a pseudo random number in order to seed our
// pseudo random number generator otherwise the numbers would always
// come out the same.
// To solve this I use VBCounter that is incremented every VB
// Interrupt. If this function was called at initialisation the same
// problem would arrise as VBCounter will have always hit the same
// value.
// Call SeedRandom just after the user has pressed fire/start from the
// title screen of your game.
//////////////////////////////////////////////////////////////////////////////
void SeedRandom(void)
{
   RAND_RandomData = VBCounter;
   GetRandom(100);
}



// helper function as there is no 32bit multiply natively supported by
// the compiler :-0
s32 Multiply32bit(s32 Value1, s32 Value2)
{
#ifndef WIN32
   __ASM("  LD      XWA, (XSP+4)");
   __ASM("  LD      XBC, (XSP+8)");
   __ASM("  LD		HL,QWA");
   __ASM("  MUL		XHL,BC");
   __ASM("  LD		DE,QBC");
   __ASM("  MUL		XDE,WA");
   __ASM("  ADD		XHL,XDE");
   __ASM("  LD		QHL,HL");
   __ASM("  LD		HL,0");
   __ASM("  MUL		XWA,BC");
   __ASM("  ADD		XHL,XWA");

   return __XHL;
#else
	return Value1 * Value2;
#endif
}

void memcpy16(const void *dst, const void *src, u16 len) {
#ifndef WIN32
	__ASM("  LD		XIX,(XSP+4)");
	__ASM("  LD		XIY,(XSP+8)");
	__ASM("  LD		BC,(XSP+12)");
	__ASM("  LDIRW	(XIX+),(XIY+)");
#else
	int i;
	for (i = 0; i < len; ++i)
		((u16*)dst)[i] = ((u16*)src)[i];
#endif
}

void memclr16(const void *dst, u16 len) {
#ifndef WIN32
	__ASM("  LD		XIX,(XSP+4)");
	__ASM("  LD		BC,(XSP+8)");
	__ASM("  XOR	WA,WA");
	__ASM("_memclr16_loop:");
	__ASM("  LD		(XIX+),WA");
	__ASM("  DJNZ	BC,_memclr16_loop");
#else
	int i;
	for (i = 0; i < len; ++i)
		((u16*)dst)[i] = 0;
#endif
}

void enableHBL(u8 p) {
#ifndef WIN32
	__ASM("  ANDB	(0x20),0x8e"); // TRUN
	__ASM("  LDB	(0x24),0x00"); // T01MOD
	__ASM("  LDB	(0x22),(XSP+4)"); // TREG0
	__ASM("  LDB	RW3,4"); // VECT_INTLVSET
	__ASM("  LDB	RB3,3");
	__ASM("  LDB	RC3,2");
	__ASM("  SWI	1");
	__ASM("  ORB	(0x20),0x01"); // TRUN
#endif
}

//////////////////////////////////////////////////////////////////////////////
// GetRandom
// Returns a pseudo random number in the range of 0 to the specified
// input value.
// Inputs:
//     Value = the upper limit for the random number (must be less than 32767)
//////////////////////////////////////////////////////////////////////////////
u16 GetRandom(u16 Value)
{
#if !defined(GCC) && !defined(WIN32)
   RAND_RandomData = Multiply32bit(RAND_RandomData,20077);
   RAND_RandomData += 12345;
   
   return (Multiply32bit(((RAND_RandomData >> 16) & RAND_MAX), Value) >> 15);
#else
   return (u16)(RAND_RandomData * VBCounter * 17);
#endif
}

