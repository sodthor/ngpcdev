#include "crt1.h"

/*
 * NGPC Register Defines
 *
 * by Jeff Frohwein
 *
 * v1.0 - Original release, 00-Apr-17
 * v1.1 - Converted from $xxxx to 0xxxxh format, 00-May-25
 * v1.2 - Added more regs, 00-May-27
 * v2.0 - C version from THOR (thx Jeff!)
 */

#define	rWDCR	     0x6f    // WatcDog Control Register
#define	WD_CLR       0x4e    // Value rired to be written to rWDT

#define	rBV          0x6F80  // Battery Voltage (Read Only word) (0-3ffh)
#define	rSL          0x6F82  // Sys Lever (Controller Input, read byte)
#define	rUSERB       0x6F84  // User Boot (Read only byte)
#define	rUSERS       0x6F85  // User Shutdown (Read only byte)
#define	rUSERA       0x6F86  // User Answer (Read/write byte)
#define	rLANG        0x6F87  // Language (Read Only byte: 0=Jap, 1=Eng)
#define	rOSV         0x6F91  // OS Version (Read only byte)

/* User Program Interrupt Vectors*/

#define	rSWI3        0x6FB8  // Software Interrupt (SWI 3)
#define	rSWI4        0x6FBC  // Software Interrupt (SWI 4)
#define	rSWI5        0x6FC0  // Software Interrupt (SWI 5)
#define	rSWI6        0x6FC4  // Software Interrupt (SWI 6)
#define	rRTCI        0x6FC8  // RTC Alarm Interrupt
#define	rVBI         0x6FCC  // Vertical Blanking Interrupt
#define	rZ80I        0x6FD0  // Interrupt from Z80
#define	rTI0         0x6FD4  // Timer Interrupt (8 bit timer 0)
#define	rTI1         0x6FD8  // Timer Interrupt (8 bit timer 1)
#define	rTI2         0x6FDC  // Timer Interrupt (8 bit timer 2)
#define	rTI3         0x6FE0  // Timer Interrupt (8 bit timer 3)
#define	rSTI         0x6FE4  // Serial Transmission Interrupt
#define	rSRI         0x6FE8  // Serial Reception Interrupt
#define	rEMDMA0      0x6FF0  // End Micro DMA Int (MicroDMA 0)
#define	rEMDMA1      0x6FF4  // End Micro DMA Int (MicroDMA 1)
#define	rEMDMA2      0x6FF8  // End Micro DMA Int (MicroDMA 2)
#define	rEMDMA3      0x6FFC  // End Micro DMA Int (MicroDMA 3)

/* Vint/Hint Enable/Disable */

#define	rICR         0x8000  // Interrupt Control Register

/* Window Registers */

#define	rWBAX        0x8002  // WBA.= Window Horizontal Origin
#define	rWBAY        0x8003  // WBA.V = Window Vertical Origin
#define	rWSIX        0x8004  // WSI.= Window X Size
#define	rWSIY        0x8005  // WSI.V = Window Y Size

/* Frame Rate Register */

#define	rREF         0x8006  // REF = Frame Rate Register

/* Raster Position Registers */

#define	rRASH        0x8008  // RAS.H = Raster Position Horizontal
#define	rRASV        0x8009  // RAS.V = Raster Position Vertical

/* 2D Status Register */

#define	r2DSR        0x8010  // Character Over / Vblank Status

/* 2D Control Register */

#define	r2DCR        0x8012  // NEG / OOWC Setting

/* Sprite Position Offset */

#define	rPOX         0x8020  // PO.= Position Offset Horizontal
#define	rPOY         0x8021  // PO.V = Position Offset Vertical

/* Scroll Plane Priority Function */

#define	rPF          0x8030  // Scroll plane 1 in front/back

/* Scroll Offset Registers */

#define	rS1SOX       0x8032  // S1SO.= Scroll Plane 1 X Scroll Offset
#define	rS1SOY       0x8033  // S1SO.V = Scroll Plane 1 Y Scroll Offset
#define	rS2SOX       0x8034  // S2SO.= Scroll Plane 2 X Scroll Offset
#define	rS2SOY       0x8035  // S2SO.V = Scroll Plane 2 Y Scroll Offset

/* Background Colo(u)r Register */

#define	rBCR         0x8118  // BGON / BGC Settings

/* Colo(u)r Palette RAM */

#define	_SPRPAL      0x8200  // Sprite palettes (K2GE)
#define	_SCR1PAL     0x8280  // Scroll 1 palettes (K2GE)
#define	_SCR2PAL     0x8300  // Scroll 2 palettes (K2GE)
#define	_BGCPAL      0x83E0  // Background Colo(u)r Palettes
#define	_WCPAL       0x83F0  // Window Colo(u)r Palettes

#define	rLCR         0x8400  // LED Control Register
#define	rLFC         0x8402  // LED FlasCycle

#define	r2DRES       0x87E0  // 2D software reset (WRITE)
#define	RESET2D_VAL  0x52    // 2D circuit initialization wit"52" write

/* Mode Selection Register */

#define	rMSR         0x87E2  // Mode Selection Register (K1GE/K2GE Select)

#define	rIPR         0x87FE  // Input Port Register

/* RAM ates */

#define	_MAINRAM     0x4000  // Main work RAM Start Address
#define	_SPRRAM      0x8800  // Sprite VRAM Start Address
#define	_SCR1RAM     0x9000  // Scroll 1 VRAM Start Address
#define	_SCR2RAM     0x9800  // Scroll 2 VRAM Start Address
#define	_TILERAM     0xA000  // Sprite/Scroll Tileset Start Address

#define	_INTROM      0xff0000// Internal ROM Start Address

/* Controller Defines */

#define	PADF_D               0x80
#define	PADF_OPTION          0x40
#define	PADF_B               0x20
#define	PADF_A               0x10
#define	PADF_RIGHT           0x08
#define	PADF_LEFT            0x04
#define	PADF_DOWN            0x02
#define	PADF_UP              0x01

#define	PADB_D               7
#define	PADB_OPTION          6
#define	PADB_B               5
#define	PADB_A               4
#define	PADB_RIGHT           3
#define	PADB_LEFT            2
#define	PADB_DOWN            1
#define	PADB_UP              0

/* Screen Defines */

#define	SCRN_X       160 // Widtof screen in pixels
#define	SCRN_Y       152 // Height of screen in pixels
#define	SCRN_X_B     20  // Widtof screen in bytes
#define	SCRN_Y_B     19  // Height of screen in bytes

#define	SCRN_VX      256 // Virtual widtof screen in pixels
#define	SCRN_VY      256 // Virtual height of screen in pixels
#define	SCRN_VX_B    64  // Virtual widtof screen in bytes
#define	SCRN_VY_B    64  // Virtual height of screen in bytes

void memcpy(void *,void*, u16);

#define NULL 0L
