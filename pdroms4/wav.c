#include "ngpc.h"

void __interrupt wavLoopTimer0(void)
{
__ASM("	cpb	(_WD),0");
__ASM("	jr	z,wav_loopcont");
__ASM("	reti");
__ASM("wav_loopcont:");
__ASM(" push    xwa");
__ASM(" push    bc");
__ASM("	ldb	(_WD),1");
__ASM("	ld	xwa,(_SMPPTR)");
__ASM("	ld	b,(xwa)");
__ASM("	ld	c,b");
__ASM("	inc	1,xwa");
__ASM("	cp	xwa,(_END_SAMPLE)");
__ASM("	jr	nz,wav_loopnotend");
__ASM("	ld	xwa,(_START_SAMPLE)");
__ASM("wav_loopnotend:");
__ASM("	ld	(_SMPPTR),xwa");
__ASM("	ld	(0xa2),bc");
__ASM("wav_loopout:");
__ASM("	pop     bc");
__ASM("	pop     xwa");
__ASM("	ldb	(_WD),0");
}

void __interrupt wavTimer0(void)
{
__ASM("	cpb	(_WD),0");
__ASM("	jr	z,wav_cont");
__ASM("	reti");
__ASM("wav_cont:");
__ASM("	ldb	(_WD),1");
__ASM(" push    xwa");
__ASM(" push    bc");
__ASM("	ld	xwa,(_SMPPTR)");
__ASM("	or	xwa,xwa");
__ASM("	jr	z,wav_out");
__ASM("	ld	b,(xwa)");
__ASM("	ld	c,b");
__ASM("	inc	1,xwa");
__ASM("	cp	xwa,(_END_SAMPLE)");
__ASM("	jr	nz,wav_notend");
__ASM("	xor	xwa,xwa");
__ASM("	ld	(_SMPPTR),xwa");
__ASM("	jr	wav_out");
__ASM("wav_notend:");
__ASM("	ld	(_SMPPTR),xwa");
__ASM("	ld	(0xa2),bc");
__ASM("wav_out:");
__ASM("	pop     bc");
__ASM("	pop     xwa");
__ASM("	ldb	(_WD),0");
}

u8 WD;
u32 SMPPTR;
u32 START_SAMPLE;
u32 END_SAMPLE;

void wavPlayLoop(u8 *smp)
{
    u32 length = *((u32*)(smp+40));
    WD = 0;
    SMPPTR=(u32)smp;
    START_SAMPLE=(u32)(smp+44);
    END_SAMPLE=(u32)(smp+44+length);
__ASM("	andb	(0x20),0x8e");
__ASM("	ldb	(0x24),0x2");
    TI0_INT=wavLoopTimer0;
__ASM("	orb	(0x20),0x01");
}

extern void __interrupt DummyFunction(void);

void wavStop()
{
__ASM("	andb	(0x20),0x8e");
    TI0_INT=DummyFunction;
    SMPPTR=0;
    START_SAMPLE=0;
    END_SAMPLE=0;
}

void wavPlay(u8 *smp)
{
    u32 length = *((u32*)(smp+40));
    WD=0;
    SMPPTR=(u32)(smp+44);
    START_SAMPLE=0;
    END_SAMPLE=(u32)(smp+44+length);
__ASM("	andb	(0x20),0x8e");
__ASM("	ldb	(0x24),0x2");
    TI0_INT=wavTimer0;
__ASM("	orb	(0x20),0x01");
}

void wavCheck()
{
    if (START_SAMPLE==0 && SMPPTR==0 && END_SAMPLE!=0)
        wavStop();
}
