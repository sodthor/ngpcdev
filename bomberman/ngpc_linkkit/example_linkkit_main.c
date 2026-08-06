/*
 * example_linkkit_main.c -- smallest useful two-console loop.
 *
 * Not part of the build: copy the shape into your own state, or add this file
 * to OBJS in place of main.rel to try the module on its own.
 *
 * What it shows, in order of importance:
 *   - stage / update / peek, once per frame, in that order
 *   - the link never blocks the game: a frame with no packet still runs
 *   - the counters are on screen, because "it does not work" is not a
 *     diagnosis and those five numbers usually are one
 */

#include "ngpc.h"
#include "library.h"
#include "ngpc_linkkit/ngpc_linkkit.h"

/* Whatever you want the peer to know each frame. Keep it small -- the cable
 * carries about 32 bytes per frame in total, overhead included. Here: the
 * player's position and the buttons held. */
typedef struct {
    u8 x;
    u8 y;
    u8 buttons;
    u8 spare;
} MyState;

static MyState  gMine;
static MyState  gTheirs;
static u8       gTx[NGPC_LINKKIT_PAYLOAD];
static u8       gRx[NGPC_LINKKIT_PAYLOAD];

static void PackMine(void)
{
    gTx[0] = gMine.x;
    gTx[1] = gMine.y;
    gTx[2] = gMine.buttons;
    gTx[3] = gMine.spare;
    /* Bytes 4.. are yours; they are sent whether you fill them or not, so if
     * you are not using them, shrink NGPC_LINKKIT_PAYLOAD instead. */
}

static void UnpackTheirs(void)
{
    gTheirs.x       = gRx[0];
    gTheirs.y       = gRx[1];
    gTheirs.buttons = gRx[2];
    gTheirs.spare   = gRx[3];
}

void ExampleLinkLoop(void)
{
    /* If your menu asked the players "HOST or JOIN", say so here and the two
     * consoles never have to draw lots. Skip it for a symmetric game. */
    /* ngpc_linkkit_set_role(LINKKIT_ROLE_HOST); */

    ngpc_linkkit_init();

    while (1) {
        /* ---- your game logic, unchanged ---- */
        gMine.buttons = JOYPAD;
        if (gMine.buttons & J_RIGHT) gMine.x++;
        if (gMine.buttons & J_LEFT)  gMine.x--;

        /* ---- the link, three calls ---- */
        PackMine(); // Builds up the exchange struct from this console
        ngpc_linkkit_stage(gTx); // stages the send
        ngpc_linkkit_update(); // sends and receives data
        if (ngpc_linkkit_fresh()) // Checks that the link is active
        { 
            ngpc_linkkit_peek(gRx); // Retrieves the data into gRx[]
            UnpackTheirs();
        }
        /* No fresh packet this frame is NORMAL. Keep drawing the peer where it
         * was; do not freeze and do not wait for it. */

        /* ---- feedback ---- */
        switch (ngpc_linkkit_state()) {
        case LINKKIT_CONNECTING:
            PrintString(SCR_FORE_PLANE, 0, 1, 2, "LOOKING FOR PLAYER 2");
            break;
        case LINKKIT_READY:
            PrintString(SCR_FORE_PLANE, 0, 1, 2,
                        ngpc_linkkit_role() == LINKKIT_ROLE_HOST
                            ? "CONNECTED - HOST " : "CONNECTED - GUEST");
            break;
        case LINKKIT_LOST:
            PrintString(SCR_FORE_PLANE, 0, 1, 2, "LINK LOST - RETRYING");
            break;
        case LINKKIT_MISMATCH:
            PrintString(SCR_FORE_PLANE, 0, 1, 2, "OTHER CART DIFFERS  ");
            break;
        default:
            PrintString(SCR_FORE_PLANE, 0, 1, 2, "NO LINK             ");
            break;
        }

        /* The five numbers that tell you what is actually happening.
         * tx climbing + rx flat        -> nothing arrives (cable? recv_start?)
         * skip climbing                -> over budget, shrink the payload
         * bad climbing                 -> bytes lost or formats disagree
         * drop climbing                -> update() is not called often enough */
        PrintString (SCR_FORE_PLANE, 0, 1, 16, "TX   RX   SKIP BAD  DROP");
        PrintDecimal(SCR_FORE_PLANE, 0, 1, 17, ngpc_linkkit_tx_packets(), 4);
        PrintDecimal(SCR_FORE_PLANE, 0, 6, 17, ngpc_linkkit_rx_packets(), 4);
        PrintDecimal(SCR_FORE_PLANE, 0,11, 17, ngpc_linkkit_tx_skipped(), 4);
        PrintDecimal(SCR_FORE_PLANE, 0,16, 17, ngpc_linkkit_rx_bad_sum(), 4);
        PrintDecimal(SCR_FORE_PLANE, 0,21, 17, ngpc_linkkit_rx_dropped(), 4);

        Sleep(1);
    }
}
