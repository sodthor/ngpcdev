#include "ngpc.h"
#include "PSGlib.h"

#define SAMPLE_FREQ (48000/8000) // 8khz 8bit Stereo PCM

/*
 * PSG
 */

void SL_SoundCPUStop()
{
    SOUNDCPU_CTRL = 0xaaaa;
}

void SL_SoundCPUStart()
{
    SOUNDCPU_CTRL = 0x5555;
}

void SL_WaitZ80()
{
    __ASM("wait_z80:");
    __ASM("ld a,(0xbc)");
    __ASM("or a,a");
    __ASM("jr nz,wait_z80");
}

void __interrupt dma0Int();

void SL_SoundInit()
{
    u16 i;
    u8 *ram = Z80_RAM;
    SL_SoundCPUStop();
    
    // Copy PSG lib driver to z80
    for (i = 0; i < PSGLIB_SIZE; i++)
        ram[i] = PSGLIB[i];

    // Config timer 2 for PCM playback
    TRUN &= (TIMER2_ON | TIMER3_ON) ^ 0xff;
    T23MOD = TIMER0_SRC_T1 | TIMER01_8BIT;
    TREG2 = SAMPLE_FREQ;
    TFFCR = 0; // no flip-flop
    TRDC = 0; // no double buffering

    // Set DMA0 interrupt
    __asm("ldb rw3,0x04"); // VECT_INTLVSET
    __asm("ldb rb3,0x01"); // LOW priority
    __asm("ldb rc3,0x06"); // DMA0
    __asm("swi 1");

    __asm("di");
    DMA0_INT = dma0Int;
    __asm("ei");

    Z80_COMM = 0xff;
    SL_SoundCPUStart();
    SL_WaitZ80();
}

typedef enum {
    PSGStop = 1,
    PSGPlayNoRepeat,
    PSGPlay
} PSG_COMMAND;

void SL_LoadData(const u8 *data, u16 len)
{
    u8 *ram = Z80_RAM;
    u16 i;
    for (i = 0; i < len && i + PSGLIB_SIZE < 4096; ++i)
        ram[i + PSGLIB_SIZE] = data[i];
}

void SL_StopBGM(u8 sync)
{
    Z80_COMM = PSGStop ^ 0xff;
    SL_WaitZ80();
}

void SL_PlayBGM(u8 noRepeat)
{
    Z80_COMM = (noRepeat ? PSGPlayNoRepeat : PSGPlay) ^ 0xff;
    SL_WaitZ80();
}

/*
 * PCM
 */

u8* pcmData;
u16 pcmCount;
u8  pcmPlaying = 0;

void SL_PlayPCM(u8* pcmPtr, u16 pcmLen)
{
    if (pcmPlaying) // one at a time
        return;

    pcmData = pcmPtr;
    pcmCount = pcmLen >> 1;
    __asm("ldl xwa, (_pcmData)");
    __asm("ldc DMAS0,xwa");
    __asm("ldl xwa, 0xA2"); // DAC
    __asm("ldc DMAD0,xwa");
    __asm("ldb w, 0x09"); // Word I/O mode
    __asm("ldc DMAM0,w");
    __asm("ldw wa,(_pcmCount)");
    __asm("ldc DMAC0,wa");
    DMA0V = DMAV_TIMER2;

    pcmPlaying = 1;

    SOUNDCPU_CTRL = 0x55aa; // enable dac

    // Start timer 2
    TRUN |= TIMER2_ON | PRESCALER_ON;
}

void __interrupt dma0Int()
{
    // Stop timer 2
    TRUN &= TIMER2_ON ^ 0xff;
    pcmPlaying = 0;
    SOUNDCPU_CTRL = 0x5555; // enable z80
}
