#ifndef NGPC_LINKKIT_COM_H
#define NGPC_LINKKIT_COM_H

/*
 * ngpc_linkkit_com -- BIOS COM calls, the low layer of the NGPC link cable.
 *
 * The cable is the TLCS-900 SC0 serial channel, driven through BIOS system
 * calls: call [0xFFFE00 + vect*4], in register bank 3. Line settings are done
 * by COMINIT: UART 8N1, CTS/RTS handshake, 19200 bps. Vector numbers 0x10..0x1A
 * were read out of a real BIOS dump -- most public headers stop at 0x0E.
 *
 * ---------------------------------------------------------------------------
 * FOUR RULES THAT COST REAL DEBUGGING TIME. Do not "simplify" them away.
 * ---------------------------------------------------------------------------
 *
 * 1. NO WRAPPER RETURNS A VALUE.
 *    A cc900 function whose body is nothing but inline asm has no C return, so
 *    what the caller gets is whatever happened to be in the return register.
 *    Every result here is written through a pointer instead. Buffer levels are
 *    read straight from the BIOS counters in work RAM (documented ABI, the
 *    official SDK reads them too), which is more reliable than any status word.
 *
 * 2. NAME THE BANK-3 REGISTERS EXPLICITLY.
 *    The BIOS reads its arguments from rb3 / xhl3 / rw3. Whether the C runtime
 *    happens to be running in bank 3 depends on the startup code of the project
 *    you drop this into -- it is NOT a given. Bare names (b, hl, a) mean "the
 *    current bank", so on a project whose runtime is not in bank 3 they never
 *    reach the BIOS and the cable stays silent while everything still compiles.
 *    Writing rb3 / xhl3 / ra3 is correct either way, so always write them.
 *
 * 3. SAVE AND RESTORE SR AROUND THE CALL.
 *    `push sr` / `pop sr` does two jobs at once. It puts the register bank back
 *    the way the compiler left it, and it restores the interrupt level -- which
 *    matters because COMOFFRTS starts with `ei 6`, masking VBlank. Without the
 *    restore, `com_rts_off(); wait_vblank();` hangs forever and a real console
 *    powers itself off through the watchdog after about a second.
 *    Consequence: never call these from inside an ISR.
 *
 * 4. THE WIRE IS SLOW AND ASYNCHRONOUS.
 *    19200 bps 8N1 is 1920 bytes/s, i.e. ~32 bytes per frame at 60 Hz, and the
 *    BIOS rings hold 64 bytes each. Never block waiting for a byte, and always
 *    check the free space before queueing a packet -- a half-queued packet is
 *    worse than no packet, because it desynchronises the peer's parser.
 * ---------------------------------------------------------------------------
 */

/* u8/u16 come from the host project. Override with
 *   -DNGPC_LINKKIT_TYPES_HEADER="ngpc.h"
 * if your project names its types header differently. Nothing else is needed. */
// #ifndef NGPC_LINKKIT_TYPES_HEADER
// #define NGPC_LINKKIT_TYPES_HEADER "ngpc_types.h"
// #endif
// #include NGPC_LINKKIT_TYPES_HEADER

/* Force `ei 0` at the end of every wrapper, on top of the `pop sr` restore.
 * Leave it off unless your project runs at interrupt level 0 and you would
 * rather pin it there than trust the saved status register. */
#ifndef NGPC_LINKKIT_FORCE_EI0
#define NGPC_LINKKIT_FORCE_EI0 0
#endif

/* ---- BIOS vector numbers (verified against a real BIOS dump) ---- */
#define LKCOM_BIOS_INIT          0x10  /* ports, baud, serial IRQs      */
#define LKCOM_BIOS_SENDSTART     0x11  /* start transmitting the queue  */
#define LKCOM_BIOS_RECIVESTART   0x12  /* allow reception               */
#define LKCOM_BIOS_CREATEDATA    0x13  /* queue one byte    (rb3)       */
#define LKCOM_BIOS_GETDATA       0x14  /* pop one byte      (-> rb3)    */
#define LKCOM_BIOS_ONRTS         0x15  /* RTS low  = peer may send      */
#define LKCOM_BIOS_OFFRTS        0x16  /* RTS high = peer must wait     */
#define LKCOM_BIOS_SENDSTATUS    0x17  /* -> rwa3                       */
#define LKCOM_BIOS_RECIVESTATUS  0x18  /* -> rwa3                       */
#define LKCOM_BIOS_CREATEBUFDATA 0x19  /* queue n bytes (xhl3, rb3)     */
#define LKCOM_BIOS_GETBUFDATA    0x1A  /* pop n bytes   (xhl3, rb3)     */

/* ---- BIOS COM state in work RAM ---- */
#define LKCOM_TX_RING   0x006C80u  /* 64 bytes, wraps on & 0x3F */
#define LKCOM_RX_RING   0x006CC0u  /* 64 bytes                  */
#define LKCOM_RING_SIZE 64u

/* Bytes pending in each ring, maintained by the BIOS serial ISRs.
 * Read these rather than the status words: they are plain counters. */
#define LKCOM_TX_COUNT  (*(volatile u8 *)0x006D00u)
#define LKCOM_RX_COUNT  (*(volatile u8 *)0x006D01u)

/* Controller state kept by the BIOS. Handy as entropy when two consoles have
 * to break a tie: two players never hold exactly the same buttons. */
#define LKCOM_JOYPAD    (*(volatile u8 *)0x006F82u)

/* Link connector ports. */
#define LKCOM_PORT_B1   (*(volatile u8 *)0x0000B1u)  /* bit 2: cable detect */
#define LKCOM_PORT_B2   (*(volatile u8 *)0x0000B2u)  /* bit 0: RTS          */
#define LKCOM_DETECT_BIT 0x04u

/* SC0 registers -- read-only, for diagnostics. */
#define LKCOM_SC0BUF    (*(volatile u8 *)0x000050u)
#define LKCOM_SC0CR     (*(volatile u8 *)0x000051u)
#define LKCOM_SC0MOD    (*(volatile u8 *)0x000052u)  /* 0x49 after COMINIT */
#define LKCOM_BR0CR     (*(volatile u8 *)0x000053u)  /* 0x05 = 19200 bps   */

/* ---- Status word layout (bits, not bytes: the count is the LOW byte) ---- */
#define LKCOM_COUNT_MASK   0x00FFu  /* bytes in the buffer        */
#define LKCOM_BUFOVERERROR 0x0100u
#define LKCOM_FRAMEERROR   0x0200u  /* RX only */
#define LKCOM_PARITYERROR  0x0400u  /* RX only */
#define LKCOM_OVERRUNERROR 0x0800u  /* RX only */
#define LKCOM_RX_ERR_MASK  (LKCOM_FRAMEERROR | LKCOM_PARITYERROR | \
                               LKCOM_OVERRUNERROR)

/* ---- API ---- */

void lkcom_init(void);         /* once, before anything else            */
void lkcom_send_start(void);   /* flush what has been queued            */
void lkcom_recv_start(void);   /* both consoles must call it            */
void lkcom_rts_on(void);       /* let the peer transmit                 */
void lkcom_rts_off(void);      /* block it -- see rule 3 above          */

void lkcom_create_data(u8 b);              /* queue one byte            */
void lkcom_send_block(const u8 *p, u8 n);  /* queue n bytes, n <= 64    */
void lkcom_get_data(u8 *out);              /* pop one byte into *out    */
void lkcom_get_block(u8 *p, u8 n);

void lkcom_send_status(u16 *out);
void lkcom_recv_status(u16 *out);

#define lkcom_tx_pending()  ((u8)LKCOM_TX_COUNT)
#define lkcom_rx_pending()  ((u8)LKCOM_RX_COUNT)
#define lkcom_tx_free()     ((u8)(LKCOM_RING_SIZE - LKCOM_TX_COUNT))

/* 1 when a peer appears to be plugged in. ADVISORY ONLY: commercial games gate
 * on this bit but it has never been checked against silicon, so use it to draw
 * an icon, never to refuse an exchange. */
u8 lkcom_cable_present(void);

#endif /* NGPC_LINKKIT_COM_H */
