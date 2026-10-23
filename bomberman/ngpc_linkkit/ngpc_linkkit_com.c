/*
 * ngpc_linkkit_com -- BIOS COM calls. See the header for the four rules that
 * shape every function below; the call sequence is always the same:
 *
 *     ld rw3, <vector>          ; bank-3 W, explicit
 *     <read arguments off xsp>  ; BEFORE the pushes -- they move xsp
 *     push sr / push xix        ; save bank + interrupt level, save XIX
 *     ldf 3                     ; enter bank 3 for the BIOS
 *     add w,w / add w,w         ; vector * 4
 *     ld xix, 0xfffe00 / ld xix,(xix+w) / call xix
 *     pop xix / pop sr          ; bank and interrupt level back as they were
 *     <write results>           ; xsp is balanced again here
 *
 * XIX is saved because the compiler uses it for stack frames. XSP/XIX/XIY/XIZ
 * are not banked on the TLCS-900, only WA/BC/DE/HL are -- which is exactly why
 * arguments have to be moved into rb3/xhl3 explicitly.
 */
#include "ngpc.h"
#include "ngpc_linkkit/ngpc_linkkit_com.h"

#if NGPC_LINKKIT_FORCE_EI0
#define COM_EXTRA_EI()  __asm("    ei 0")
#else
#define COM_EXTRA_EI()
#endif

#ifdef CLANG
#define COM_VEC 0fffe00h
#else
#define COM_VEC 0xfffe00
#endif
#define COM_STR(x) #x
#define COM_XSTR(x) COM_STR(x)

/* BIOS COM vector select: cc900/asm900 wants C-style 0xNN, asl wants NNh. */
#ifdef CLANG
#define LDRW3(h) __asm("    ld rw3, " #h "h")
#else
#define LDRW3(h) __asm("    ld rw3, 0x" #h)
#endif

#define COM_ENTER()                     \
    __asm("    push sr");                  \
    __asm("    push xix");                 \
    __asm("    ldf 3");                    \
    __asm("    add w, w");                 \
    __asm("    add w, w");                 \
    __asm("    ld xix, " COM_XSTR(COM_VEC)); \
    __asm("    ld xix, (xix+w)");          \
    __asm("    call xix");                 \
    __asm("    pop xix");                  \
    __asm("    pop sr");                   \
    COM_EXTRA_EI()

/* ---- No-argument calls ---- */

void lkcom_init(void)       { LDRW3(10); COM_ENTER(); }
void lkcom_send_start(void) { LDRW3(11); COM_ENTER(); }
void lkcom_recv_start(void) { LDRW3(12); COM_ENTER(); }
void lkcom_rts_on(void)     { LDRW3(15); COM_ENTER(); }

void lkcom_rts_off(void)
{
    /* The BIOS does `ei 6` in here, masking VBlank. The `pop sr` in COM_ENTER
     * is what puts your interrupt level back -- do not drop it. */
    LDRW3(16);
    COM_ENTER();
}

/* ---- One byte out: argument goes to rb3 ---- */

void lkcom_create_data(u8 b) // equivalent to Link_SendByte()
{
    LDRW3(13);
    __asm("    ld de, (xsp+4)");     /* argument, read before the pushes */
    __asm("    ld rb3, e");          /* rb3 = byte to send               */
    COM_ENTER();
}

/* ---- One byte in: the BIOS leaves it in rb3 ---- */

u8 lkcom_get_data() // Equivalent to Link_ReceiveByte()
{
    LDRW3(14);
    COM_ENTER();
    /* A store straight from a bank-3 register to memory is not encodable, so
     * the value has to transit through a register of the current bank. */
    __asm("    ld l, rb3");
#ifdef CLANG
    /* cc900's `return __L` reads L directly (the CC900 8-bit return register).
     * clang can't express that, so return via an explicit asm ret with the
     * result already in L; the C return below is unreachable. */
    __asm("    ret");
    return 0;
#else
    return __L;
#endif
}

/* ---- Blocks: xhl3 = pointer, rb3 = size ---- */

void lkcom_send_block(const u8 *p, u8 n) // equivalent to Link_SendBuffer()
{
    LDRW3(19);
    __asm("    ld xde, (xsp+4)");    /* a pointer is 4 bytes, so the second */
    __asm("    ld xhl3, xde");
    __asm("    ld de, (xsp+8)");     /* argument sits at +8         */
    __asm("    ld rb3, e");
    COM_ENTER();
}

void lkcom_get_block(u8 *p, u8 n) // equivalent to Link_ReceiveBuffer()
{
    LDRW3(1a);
    __asm("    ld xde, (xsp+4)");
    __asm("    ld xhl3, xde");
    __asm("    ld de, (xsp+8)");
    __asm("    ld rb3, e");
    COM_ENTER();
}

/* ---- Status words: the BIOS returns them in rwa3 (W = high, A = low) ---- */

u16 lkcom_send_status()
{
    LDRW3(17);
    COM_ENTER();
    __asm("    ld hl, rwa3");         /* low byte  = count      */
#ifdef CLANG
    __asm("    ret");
    return 0;
#else
    return __HL;
#endif
}

u16 lkcom_recv_status()
{
    LDRW3(18);
    COM_ENTER();
    __asm("    ld hl, rwa3");
#ifdef CLANG
    __asm("    ret");
    return 0;
#else
    return __HL;
#endif
}

/* ---- Cable detect (advisory, see the header) ---- */

u8 lkcom_cable_present(void)
{
    return (u8)((LKCOM_PORT_B1 & LKCOM_DETECT_BIT) ? 0 : 1);
}
