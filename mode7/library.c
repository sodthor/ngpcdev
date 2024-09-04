#include "ngpc.h"
#include "library.h"


volatile u8 VBCounter;

u8 RandomNumberCounter;

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
   // Set NGP or NGPC mode
   if (OS_VERSION == 0)
   {
	  (*(u8*)0x6f83) &= ~8; // res 3
	  (*(u8*)0x6da0) = 0;
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
   __asm(" ld rw3,0"); // 0 = VECT_SHUTDOWN

   // do the system call
   __asm(" ldf 3");
   __asm(" add w,w");
   __asm(" add w,w");
   __asm(" ld xix,0xfffe00");
   __asm(" ld xix,(xix+w)");
   __asm(" call xix");
}


//////////////////////////////////////////////////////////////////////////////
// SysSetSystemFont                                                         //
//////////////////////////////////////////////////////////////////////////////
void SysSetSystemFont()
{
   __asm(" ld ra3,3"); 
   __asm(" ld rw3,5"); // VECT_SYSFONTSET

   // do the system call
   __asm(" ldf 3");
   __asm(" add w,w");
   __asm(" add w,w");
   __asm(" ld xix, 0xfffe00");
   __asm(" ld xix,(xix+w)");
   __asm(" call xix");
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
   u16 Value = TileNo + (((u16)Palette) << 9);

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
      __ASM("nop");
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
	  PutTile(Plane, PaletteNo, x+i-1, y, '0' + (u8)(Value % 10));
	  Value /= 10;
   }
}

void PrintHex(u8 Plane, u8 PaletteNo, u8 x, u8 y, u32 Value, u8 Len)
{
// Similar to PrintDecimal, but prints in Hexadecimal
// works well with the BCD values returned by GetTime

   u8 i, v;

   for (i = Len; i > 0; i--)
   {
	  v = Value & 15;
      if (v < 10)
         PutTile(Plane, PaletteNo, x+i-1, y, '0' + v);
      else
         PutTile(Plane, PaletteNo, x+i-1, y, '7' + v);

      Value >>= 4;
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

// similar to InstallTiles but allows you to
// adds tiles to a non-zero location in tile RAM
// useful if a font is installed in the lower tiles
void InstallTileSetAt(const u16 Tiles[][8], u16 Len, u16 Offset)
{
   u16 i;
   u16 * TileRam = TILE_RAM + (Offset * 8);
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
void SetSprite(u8 SpriteNo, u16 TileNo, u8 Chain, u8 XPos, u8 YPos, u8 PaletteNo)
{
   u16 SprCtrlReg;
   u8 * theSprite = SPRITE_RAM;
   u8 * theSpriteCol = SPRITE_COLOUR;
   
   theSprite += (SpriteNo * 4);
   theSpriteCol += SpriteNo;

   SprCtrlReg = 24; // topmost priority
   if (Chain)
      SprCtrlReg += 6; // v and h chaining

   *(theSprite)   = TileNo;
   *(theSprite+1) = SprCtrlReg+(TileNo>>8);
   *(theSprite+2) = XPos;
   *(theSprite+3) = YPos;

   *theSpriteCol = PaletteNo;
}



void SetSpriteTile(u8 SpriteNo, u16 TileNo, u8 Chain)
{
   u16 SprCtrlReg;
   u8 * theSprite = SPRITE_RAM;
   
   theSprite += (SpriteNo * 4);
   //theSpriteCol += SpriteNo;

   SprCtrlReg = 24; // topmost priority
   if (Chain)
      SprCtrlReg += 6; // v and h chaining

   *(theSprite)   = TileNo;
   *(theSprite+1) = SprCtrlReg+(TileNo>>8);
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

//////////////////////////////////////////////////////////////////////////////
// SetSpriteFlip
// Flips an already initialised sprite
// Inputs:
//		SpriteNo - 0-63 the sprite to flip
//      HFlip - 1 for horizontal flip 
//      VFlip - 1 for vertical flip
//////////////////////////////////////////////////////////////////////////////
void SetSpriteFlip(u8 SpriteNo, bool HFlip, bool VFlip)
{
   u8 * theSprite = SPRITE_RAM;
   theSprite += (SpriteNo * 4); //Set up pointer to tile in VRAM 
   if (HFlip){
	   *(theSprite+1) ^= 128; //Set tile's stored value for Horizontal Flip from VRAM(0=normal,1=flipped) - flips 0 to 1 and 1 to 0
   }
   if (VFlip){
	   *(theSprite+1) ^= 64; //Set tile's stored value for Vertical Flip from VRAM(0=normal,1=flipped) - flips 0 to 1 and 1 to 0
   }
}

//////////////////////////////////////////////////////////////////////////////
// SetSpriteFlipChain
// Flips a sprite chain
// Inputs:
//		SpriteNo - 0-63 the sprite to flip
//      XWidth - how many tiles wide the sprite chain is (8 pixels per tile width, so if the sprite is 16 pixels wide, the number of tile width to enter is 2) 
//      YHeight - how many tiles high the sprite chain is (8 pixels per tile height, so if the sprite is 16 pixels tall, the number of tile height to enter is 2)
//      tileCount - number of tiles in chain (if a full rectangle, then XWidth*YHeight, but other orientations with fewer tiles are supported
//      HFlip - 1 to flip horizontally
//      VFlip - 1 to flip vertically
//////////////////////////////////////////////////////////////////////////////
void SetSpriteFlipChain(u8 SpriteNo, u8 XWidth, u8 YHeight, u8 tileCount, bool HFlip, bool VFlip)
{
   u8 i, HFlipped, VFlipped, XMod, YMod, SpriteMods;
   u8 * theSprite = SPRITE_RAM;
   theSprite += (SpriteNo * 4); //Set up pointer to tile in VRAM
   SpriteMods = *(theSprite+1);
   HFlipped = SpriteMods>>7; //get the tile's stored value for Horizontal Flip from VRAM(0=normal,1=flipped)
   VFlipped = SpriteMods<<1;
   VFlipped = VFlipped>>7; //get the tile's stored value for Vertical Flip from VRAM(0=normal,1=flipped)
   XMod=1;
   YMod=1;
   if(HFlip){
       XMod=-1; //if HFlip, then set the X Modifier to -1 to invert the X offset for dependent tiles in the chain
	   if(XWidth){
		   if(HFlipped){ //Check if main tile is already Horizontally flipped and manage X position when flipping, so that the chain occupies the same space when flipped or flipped back
			   *(theSprite+2)-=(XWidth*8-8);
		   }else{
			   *(theSprite+2)+=(XWidth*8-8);
		   }
	   }
	   
   }
   if(VFlip){
	   YMod=-1; //if VFlip, then set the Y Modifier to -1 to invert the Y offset for dependent tiles in the chain
	   
	   if(YHeight){
		   if(VFlipped>0){//Check if main tile is already Verically flipped and manage Y position when flipping, so that the chain occupies the same space when flipped or flipped back
			   *(theSprite+3)-=(YHeight*8-8);
		   }else{
			   *(theSprite+3)+=(YHeight*8-8);
		   }
	   }
   }
	   
   for (i = 0; i < tileCount; i ++) //Loop through all tiles in the chain
   {
	  
	 SetSpriteFlip(SpriteNo + i, HFlip, VFlip); //Flip the tile
	 if(i>0){//Manage offset modifiers for all dependent tiles
		 theSprite += (4);
		 *(theSprite+2) *= XMod; //if XMod is -1, then offset gets inverted
		 *(theSprite+3) *= YMod; //if YMod is -1, then offset gets inverted
	 }
   }
   
}



void SetSpriteHFlipBlock(u8 SpriteNo, u8 tileNo)
{
   //SetSprite(ENEMIES[i].spriteId  , ENEMIES[i].startingTileId  , 0, 200, 200, ENEMIES[i].palletteId);
   
   u8 XWidth=16;
   u8 YHeight=16;
   u8 tileCount=4;
   bool HFlip=1;
   
   
   u8 i, HFlipped, VFlipped, SpriteMods;
   
   u8 * theSprite = SPRITE_RAM;
   
   
   theSprite += (SpriteNo * 4); //Set up pointer to tile in VRAM
   SpriteMods = *(theSprite+1);
   HFlipped = SpriteMods>>7; //get the tile's stored value for Horizontal Flip from VRAM(0=normal,1=flipped)
   VFlipped = SpriteMods<<1;
   VFlipped = VFlipped>>7; //get the tile's stored value for Vertical Flip from VRAM(0=normal,1=flipped)
   
   //PrintDecimal(SCR_2_PLANE, 0, 10, 8, HFlipped, 1);

   
   if (HFlipped)	{   
	   
	    SetSpriteTile(SpriteNo + 0, tileNo, 0);
	   SetSpriteTile(SpriteNo + 1, tileNo+1, 1);
	   SetSpriteTile(SpriteNo + 2, tileNo+2, 1);
	   SetSpriteTile(SpriteNo + 3, tileNo+3, 1);
	   
   }else{
	  
	   
	   SetSpriteTile(SpriteNo + 0, tileNo+1, 0);
	   SetSpriteTile(SpriteNo + 1, tileNo,   1);
	   SetSpriteTile(SpriteNo + 2, tileNo+3, 1);
	   SetSpriteTile(SpriteNo + 3, tileNo+2, 1);
	   
	   for (i = 0; i < tileCount; i ++) //Loop through all tiles in the chain
   {
	  
	 SetSpriteFlip(SpriteNo + i, 1, 0); //Flip the tile
   }
	   
   }
	 
	 
	 
   
   
}

//////////////////////////////////////////////////////////////////////////////
// SetSpriteFlip
// Flips an already initialised sprite
// Inputs:
//		SpriteNo - 0-63 the sprite to flip
//      HFlip - 1 for horizontal flip 
//      VFlip - 1 for vertical flip
//////////////////////////////////////////////////////////////////////////////
void SetPlanePosition(u8 Plane, u8 XPos, u8 YPos)
{
	//u8 * P1X = SCR1_X;
	//u8 * P1Y = SCR1_Y;
	
	
	switch (Plane)
   {
	  
	  case SCR_1_PLANE:
		 SCR1_X = XPos;
		 SCR1_Y = YPos;
		 break;

	  case SCR_2_PLANE:
		 SCR2_X = XPos;
         SCR2_Y = YPos;
		 break;

	  default:
		 return;
   }
}

void BlockCopy(u8 * Dest, const u8 * Source, u16 n)
{
   u8 *p1, *p2;

   p1 = Dest;
   p2 = (u8 *)Source;
   
   while (n--)
	  *p1++ = *p2++;
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
   RAND_RandomData = Multiply32bit(RAND_RandomData,20077);
   RAND_RandomData += 12345;
   
   return (Multiply32bit(((RAND_RandomData >> 16) & RAND_MAX), Value) >> 15);
}


// ----------------------------------------------
// QRandom Numbers - Chris Ahchay
// ----------------------------------------------
//Most of these aren't used... I'll get the correctly shaped tuit one of these days....
const unsigned char QuickRandomNumbers[1024]={63, 254, 102, 147, 252, 158, 83, 102, 90, 162, 89, 211, 146, 181,
33, 188, 239, 26, 52, 113, 168, 22, 140, 238, 236, 139, 163, 63, 92, 140, 49, 59, 196, 187, 155, 179, 77, 194,
203, 66, 226, 67, 239, 131, 193, 41, 115, 237, 253, 231, 230, 26, 201, 211, 37, 120, 217, 200, 147, 142, 88,
251, 156, 158, 223, 88, 255, 196, 160, 75, 19, 82, 24, 129, 212, 130, 118, 66, 20, 176, 87, 69, 79, 213, 195,
71, 109, 232, 48, 169, 93, 43, 206, 8, 52, 207, 92, 6, 47, 123, 153, 158, 256, 219, 247, 0, 48, 27, 140, 194,
74, 196, 206, 104, 78, 142, 13, 82, 65, 5, 220, 215, 72, 235, 122, 240, 166, 14, 149, 116, 233, 254, 159, 164,
52, 236, 252, 231, 84, 189, 158, 45, 1, 75, 130, 202, 89, 229, 136, 194, 61, 253, 91, 19, 1, 101, 34, 80, 74,
114, 243, 79, 172, 125, 101, 91, 42, 176, 31, 15, 171, 213, 127, 132, 256, 168, 190, 42, 100, 80, 78, 17, 104,
98, 142, 245, 158, 95, 111, 155, 131, 132, 220, 38, 158, 217, 186, 12, 29, 246, 31, 157, 19, 197, 228, 237, 248,
89, 62, 232, 247, 167, 92, 127, 100, 168, 200, 50, 113, 248, 144, 132, 91, 216, 57, 132, 140, 13, 251, 214, 30,
177, 252, 61, 1, 125, 90, 84, 161, 118, 71, 146, 74, 2, 27, 168, 158, 93, 199, 78, 201, 181, 229, 132, 226, 178,
24, 206, 158, 153, 29, 87, 93, 210, 226, 149, 206, 133, 69, 163, 53, 250, 195, 132, 5, 131, 219, 213, 35, 72, 150,
107, 26, 146, 64, 30, 39, 223, 105, 172, 109, 245, 11, 12, 20, 33, 223, 13, 40, 94, 104, 218, 238, 230, 26, 196,
255, 229, 107, 40, 155, 35, 218, 178, 115, 154, 120, 86, 163, 120, 235, 94, 44, 201, 164, 8, 80, 250, 25, 30, 256,
84, 24, 36, 177, 26, 180, 208, 190, 255, 143, 66, 34, 135, 185, 187, 248, 172, 242, 186, 177, 232, 180, 30, 206,
210, 214, 224, 152, 176, 13, 89, 30, 242, 156, 31, 138, 74, 239, 43, 151, 179, 60, 24, 186, 81, 141, 106, 133, 131,
58, 31, 221, 164, 141, 175, 57, 204, 63, 82, 52, 161, 106, 93, 124, 69, 17, 79, 113, 139, 195, 57, 103, 45, 225, 45,
51, 59, 251, 28, 30, 38, 204, 237, 198, 63, 217, 106, 109, 109, 63, 30, 114, 179, 117, 139, 102, 209, 186, 104, 202,
165, 165, 125, 222, 116, 161, 219, 179, 37, 156, 115, 4, 80, 10, 195, 214, 219, 211, 211, 49, 197, 151, 159, 35, 155,
49, 120, 157, 165, 182, 99, 189, 18, 204, 204, 218, 21, 239, 214, 90, 24, 69, 186, 218, 44, 154, 142, 149, 230, 253,
16, 98, 46, 163, 121, 25, 9, 61, 95, 225, 150, 149, 22, 199, 253, 19, 139, 246, 167, 28, 183, 238, 126, 37, 231, 66,
77, 21, 117, 40, 227, 155, 236, 62, 244, 227, 14, 244, 172, 71, 149, 174, 4, 205, 9, 18, 123, 175, 37, 147, 255, 27,
128, 228, 36, 70, 152, 60, 1, 13, 43, 7, 67, 131, 178, 8, 203, 67, 183, 66, 221, 120, 208, 48, 45, 197, 99, 159, 169,
91, 20, 219, 82, 245, 56, 127, 147, 136, 80, 201, 201, 161, 24, 212, 200, 253, 53, 141, 188, 247, 62, 147, 185, 212,
36, 253, 175, 238, 205, 147, 20, 154, 160, 74, 180, 169, 37, 85, 97, 12, 169, 195, 114, 215, 219, 83, 251, 25, 248,
140, 200, 179, 26, 175, 128, 136, 219, 182, 86, 215, 30, 71, 250, 159, 206, 7, 79, 173, 200, 194, 212, 244, 14, 49,
42, 241, 157, 239, 70, 63, 84, 85, 44, 190, 9, 18, 209, 24, 246, 169, 176, 232, 194, 54, 136, 132, 84, 148, 199, 12,
95, 155, 38, 124, 236, 88, 141, 222, 196, 221, 181, 162, 12, 154, 146, 122, 189, 138, 142, 137, 188, 48, 15, 161, 16,
52, 70, 24, 63, 56, 99, 216, 174, 95, 3, 59, 247, 235, 160, 8, 239, 57, 241, 58, 36, 163, 19, 24, 244, 80, 213, 93,
208, 223, 150, 59, 234, 212, 169, 65, 130, 250, 212, 49, 160, 114, 127, 202, 57, 244, 210, 252, 109, 191, 60, 102,
9, 51, 223, 28, 62, 30, 219, 224, 198, 37, 102, 167, 254, 111, 32, 33, 62, 212, 144, 128, 81, 97, 62, 63, 106, 48,
8, 55, 84, 134, 248,112, 211, 134, 3, 53, 35, 143, 58, 159, 219, 114, 56, 142, 100, 100, 97, 136, 216, 202, 217, 122,
233, 47, 81, 29, 10, 18, 179, 180, 44, 236, 186, 102, 4, 186, 83, 225, 130, 100, 179, 166, 190, 252, 39, 8, 85, 86,
226, 174, 158, 116, 214, 50, 187, 39, 40, 227, 176, 125, 79, 129, 85, 236, 165, 26, 97, 127, 117, 190, 161, 100, 183,
123, 27, 116, 86, 149, 226, 76, 178, 12, 154, 67, 67, 89, 228, 180, 206, 216, 74, 97, 222, 9, 159, 19, 130, 191, 120,
94, 7, 93, 233, 43, 251, 151, 148, 55, 28, 79, 43, 33, 8, 139, 243, 171, 189, 104, 19, 95, 252, 123, 130, 69, 16, 84,
191, 48, 159, 77, 196, 246, 34, 217, 153, 152, 216, 53, 36, 153, 205, 246, 52, 142, 115, 244, 45, 120, 171, 205, 219,
229, 179, 239, 93, 130, 126, 244, 19, 189, 184, 34, 247, 101, 176, 76, 94, 118, 56, 19, 240, 20, 197, 143, 219, 38,
42, 158, 9, 223, 173, 149, 211, 54, 255, 136, 11, 194, 26, 172, 73, 109, 14, 178, 9, 114, 121, 157, 117, 119, 91, 248,
29, 156, 194, 35, 156, 158, 8, 18, 170, 243, 145, 51, 93, 128, 92, 237, 192, 77, 238, 250, 173, 109, 41, 51, 43, 80,
241, 28, 172, 46, 99, 183, 35, 133, 170, 9, 120, 192, 44, 110, 182, 135, 193, 45, 161, 105, 151, 191, 170, 226, 53,
70, 31, 182, 2};

void InitialiseQRandom()
{
   RandomNumberCounter=0;
}

unsigned char QRandom()
{
    //Um. RandomNumberCounter is an unsigned char...
    //which means that this routine will never get beyond the first
    //255 elements of the random number array?
    //Oh well. Should be good enough for my uses anyway...
   return QuickRandomNumbers[RandomNumberCounter++];
}


void GetTime(TIME * pTime)
{
   __asm(" ld rw3, 2");		// VECT_RTCGET
   __asm(" ld xde, (xsp+4)");	// get ptr off stack
   __asm(" ld xhl3, xde");	// pass in this register

   // do the system call
   __asm(" ldf 3");
   __asm(" add w,w");
   __asm(" add w,w");
   __asm(" ld xix, 0xfffe00");
   __asm(" ld xix,(xix+w)");
   __asm(" call xix");
}

void SetWake(ALARM * pAlarm)
{
// sets system to wakeup if powered off
// pass in a pointer to a alarm structure

   __asm(" ld rw3, 11");	// VECT_ALARMDOWNSET
   __asm(" ld xiy, (xsp+4)");	// get ptr
   __asm(" ldf 3");		// params go in reg bank 3
                // could probably do a 32 bit copy
                // to xbc but this looks better
   __asm(" ld h, (xiy +)");	// day of month
   __asm(" ld qc, h");
   __asm(" ld b, (xiy +)");	// hour
   __asm(" ld c, (xiy +)");	// min

   // do the system call
   __asm(" add w,w");
   __asm(" add w,w");
   __asm(" ld xix,0xfffe00");
   __asm(" ld xix,(xix+w)");
   __asm(" call xix");

   // returns success in A3 someday
//   __asm(" ld (xiy), a");
}

void SetAlarm(ALARM * pAlarm)
{
// sets an alarm while powered up
// I don't see much use in this one, but...
// need to setup interrupt first to catch this one
// and do something with it
// pass in a pointer to a alarm structure

   __asm(" ld rw3, 9");		// VECT_ALARMSET
   __asm(" ld xiy, (xsp+4)");	// get ptr
   __asm(" ldf 3");		// params go in reg bank 3
                // could probably do a 32 bit copy
                // to xbc but this looks better
   __asm(" ld h, (xiy +)");	// day of month
   __asm(" ld qc, h");
   __asm(" ld b, (xiy +)");	// hour
   __asm(" ld c, (xiy +)");	// min

   // do the system call
   __asm(" add w,w");
   __asm(" add w,w");
   __asm(" ld xix,0xfffe00");
   __asm(" ld xix,(xix+w)");
   __asm(" call xix");

// return success in A3 someday
//   __asm(" ld (xiy+3), a3");
}


void CpuSpeed(u8 spd)
{
// changes system clock speed
// pass 0 (full) to 4 (slowest)


   __asm("ld rw3, 1");		// VECT_CLOCKGEARSET
   __asm("ld xde, (xsp+4)");	// get spd off stack
   __asm("ld b, e");
   __asm("ld c, 0");		// no speedup on joypad

   // do the system call
   __asm(" ldf 3");
   __asm(" add w,w");
   __asm(" add w,w");
   __asm(" ld xix,0xfffe00");
   __asm(" ld xix,(xix+w)");
   __asm(" call xix");
};

void ResumeOff(void)
{
// turns off system after setting resume bit
// this skips the intro and can be detected to
// resume the game where it left off if programmed
// properly

   USR_ANSWER |= 128;
   SysShutdown();
};
