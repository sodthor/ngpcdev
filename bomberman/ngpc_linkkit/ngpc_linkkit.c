/*
 * ngpc_linkkit -- blank two-console link layer. See the header for the API and
 * for the two traps (symmetric tie-break, bandwidth budget).
 *
 * ---------------------------------------------------------------------------
 * WIRE FORMAT
 * ---------------------------------------------------------------------------
 *   byte 0   0xA5      start marker
 *   byte 1   type      HELLO or DATA
 *   byte 2   seq       wraps at 256; only used to spot losses
 *   byte 3.. body      HELLO: version, payload size, token hi, token lo, have
 *                      DATA : your payload, NGPC_LINKKIT_PAYLOAD bytes
 *   last     sum       bytes 1..n-2 added up, xor 0x5A
 *
 * Why each piece exists, because every one of them is a bug someone shipped:
 *
 *   The start marker is what lets a receiver that missed a byte find its feet
 *   again. Without it, one dropped byte shifts the stream by one FOREVER and
 *   every frame after that is garbage.
 *
 *   The checksum is what stops a false marker inside the data from being taken
 *   for a real packet. Marker alone is not enough.
 *
 *   The type byte is what stops a handshake message being parsed as game data,
 *   and vice versa. A protocol built as a sequence of phases -- send this, then
 *   wait for that -- breaks the moment one console is a phase ahead, because
 *   the bytes carry no clue about which phase they belong to. Self-describing
 *   messages have no phases to get out of step.
 *
 *   The version and payload size in HELLO are what turn "my opponent's screen
 *   is nonsense" into a clean MISMATCH you can put on screen.
 *
 * The parser below is a two-state machine: hunting for the marker, then filling
 * a buffer. Failure always costs exactly one packet, then it resynchronises by
 * itself on the next marker. That property is the whole point.
 * ---------------------------------------------------------------------------
 */
#include "ngpc.h"

#include "ngpc_linkkit/ngpc_linkkit.h"
#include "ngpc_linkkit/ngpc_linkkit_com.h"

#define LK_MARKER      0xA5u
#define LK_SUM_XOR     0x5Au

#define LK_TYPE_HELLO  0x01u
#define LK_TYPE_DATA   0x02u

#define LK_HELLO_BODY  5u    /* version, size, token hi, token lo, have  */
#define LK_HELLO_LEN   (3u + LK_HELLO_BODY + 1u)
#define LK_DATA_LEN    (3u + (u8)NGPC_LINKKIT_PAYLOAD + 1u)

#define LK_MAX_LEN     ((LK_HELLO_LEN > LK_DATA_LEN) ? LK_HELLO_LEN : LK_DATA_LEN)

/* How often to repeat the announcement while looking for a peer. A console
 * that joins late has to be able to catch one. */
#define LK_HELLO_PERIOD 16u

/* Ties tolerated before connecting anyway with an ambiguous role. */
#define LK_MAX_TIES     8u

/* Bytes drained per update(). One frame can only deliver ~32, so this is a
 * safety rail, not a throttle. */
#define LK_RX_BUDGET    48u

/* ---- state ---- */

static u8  lk_state;
static u8  lk_role_wanted;      /* what set_role() asked for      */
static u8  lk_role;             /* what we ended up as            */
static u8  lk_ambiguous;

static u16 lk_token;
static u8  lk_ties;
static u16 lk_frames;           /* in CONNECTING: time spent      */
static u16 lk_silence;          /* in READY: frames since a packet */

static u8  lk_tx_payload[NGPC_LINKKIT_PAYLOAD];
static u8  lk_rx_payload[NGPC_LINKKIT_PAYLOAD];
static u8  lk_fresh;
static u8  lk_tx_seq;

static u8  lk_buf[LK_MAX_LEN];
static u8  lk_len;              /* 0 = hunting for the marker     */

static u16 lk_c_tx, lk_c_rx, lk_c_skip, lk_c_sum, lk_c_drop;

/* --------------------------------------------------------------------------
 * helpers
 * ------------------------------------------------------------------------ */

static u8 lk_checksum(const u8 *p, u8 len)
{
    u8 i, sum;
    sum = 0;
    for (i = 1; i < (u8)(len - 1); i++)
        sum = (u8)(sum + p[i]);
    return (u8)(sum ^ LK_SUM_XOR);
}

/* Expected length for a type byte, 0 if we do not know it. */
static u8 lk_len_for(u8 type)
{
    if (type == LK_TYPE_HELLO) return LK_HELLO_LEN;
    if (type == LK_TYPE_DATA)  return LK_DATA_LEN;
    return 0;
}

/* Draw a tie-break token. Mixes everything that might differ between two
 * consoles -- including the controller, because two players holding exactly
 * the same buttons on the same frame is the one thing that rarely happens.
 * It is still not a guarantee: see the header. */
static u16 lk_draw_token(void)
{
    u16 t;
    t  = (u16)(lk_frames * 2654u);
    t ^= GetRandom(65535);
    t ^= (u16)(((u16)LKCOM_JOYPAD) << 7);
    t ^= (u16)(((u16)lk_ties) * 40503u);
    t ^= (u16)(((u16)LKCOM_RX_COUNT) << 3);
    t ^= (u16)LKCOM_SC0BUF;
    if (t == 0) t = 1;
    return t;
}

static void lk_reset_parser(void)
{
    lk_len = 0;
}

static void lk_purge_rx(void)
{
    u8 b, guard;
    guard = 0;
    while (lkcom_rx_pending() && guard < 255) {
        b = lkcom_get_data();
        guard++;
    }
    lk_reset_parser();
}

/* Queue a whole packet, or none of it.
 *
 * The free-space check is not an optimisation. Queueing half a packet because
 * the ring filled up mid-way leaves the peer's parser staring at a truncated
 * message, and that is how a link desynchronises for good. Dropping the frame
 * costs one frame; truncating costs the session. */
static void lk_send_packet(const u8 *p, u8 len)
{
    if (lkcom_tx_free() < len) {
        lk_c_skip++;
        return;
    }
    lkcom_send_block(p, len);
    lkcom_send_start();
    lk_c_tx++;
}

static void lk_send_hello(void)
{
    u8 pkt[LK_HELLO_LEN];

    pkt[0] = LK_MARKER;
    pkt[1] = LK_TYPE_HELLO;
    pkt[2] = lk_tx_seq;
    pkt[3] = (u8)NGPC_LINKKIT_VERSION;
    pkt[4] = (u8)NGPC_LINKKIT_PAYLOAD;
    pkt[5] = (u8)(lk_token >> 8);
    pkt[6] = (u8)(lk_token & 0x00FFu);
    /* "I already have a session." A peer that is READY answers a HELLO from
     * someone who does not, which is how a console that dropped out gets back
     * in; the flag stops the two of them announcing at each other forever. */
    pkt[7] = (u8)((lk_state == LINKKIT_READY) ? 1 : 0);
    pkt[LK_HELLO_LEN - 1] = lk_checksum(pkt, LK_HELLO_LEN);

    lk_send_packet(pkt, LK_HELLO_LEN);
}

static void lk_send_data(void)
{
    u8 pkt[LK_DATA_LEN];
    u8 i;

    pkt[0] = LK_MARKER;
    pkt[1] = LK_TYPE_DATA;
    pkt[2] = lk_tx_seq;
    for (i = 0; i < (u8)NGPC_LINKKIT_PAYLOAD; i++)
        pkt[3 + i] = lk_tx_payload[i];
    pkt[LK_DATA_LEN - 1] = lk_checksum(pkt, LK_DATA_LEN);

    lk_send_packet(pkt, LK_DATA_LEN);
    lk_tx_seq++;
}

/* --------------------------------------------------------------------------
 * receive
 * ------------------------------------------------------------------------ */

static void lk_on_hello(void)
{
    u16 peer;
    u8  peer_has_session;

    if (lk_buf[3] != (u8)NGPC_LINKKIT_VERSION ||
        lk_buf[4] != (u8)NGPC_LINKKIT_PAYLOAD) {
        /* Different build on the other side. Say so instead of decoding
         * bytes that mean something else. */
        lk_state = LINKKIT_MISMATCH;
        return;
    }

    peer             = (u16)(((u16)lk_buf[5] << 8) | (u16)lk_buf[6]);
    peer_has_session = lk_buf[7];

    /* Already connected: this is a peer trying to come back. Answer once so it
     * can complete, and keep our own session as it is. */
    if (lk_state == LINKKIT_READY) {
        if (!peer_has_session)
            lk_send_hello();
        return;
    }

    /* The player chose. No negotiation needed. */
    if (lk_role_wanted != LINKKIT_ROLE_AUTO) {
        lk_role      = lk_role_wanted;
        lk_ambiguous = 0;
        lk_state     = LINKKIT_READY;
        lk_silence   = 0;
        lk_reset_parser();
        return;
    }

    if (peer == lk_token) {
        /* Both drew the same number. Redraw a few times -- on real consoles
         * the controller state usually differs and that is enough. */
        if (lk_ties < LK_MAX_TIES) {
            lk_ties++;
            lk_token = lk_draw_token();
            lk_purge_rx();
            return;
        }
        /* Truly symmetric. Connect anyway rather than refuse the link over a
         * coin flip; flag it so a game that needs real roles can react. */
        lk_ambiguous = 1;
        lk_role      = LINKKIT_ROLE_HOST;
    } else {
        lk_ambiguous = 0;
        /* Both sides compute this from the same pair of numbers, so the two
         * answers are always opposite. */
        lk_role = (peer > lk_token) ? LINKKIT_ROLE_GUEST : LINKKIT_ROLE_HOST;
    }

    lk_state   = LINKKIT_READY;
    lk_silence = 0;
    lk_reset_parser();
}

static void lk_on_data(void)
{
    u8 i;

    if (lk_state != LINKKIT_READY)
        return;

    for (i = 0; i < (u8)NGPC_LINKKIT_PAYLOAD; i++)
        lk_rx_payload[i] = lk_buf[3 + i];

    if (lk_fresh)
        lk_c_drop++;    /* previous one never read: update() is being starved */
    lk_fresh   = 1;
    lk_silence = 0;
    lk_c_rx++;
}

/* Drain the ring and act on whatever came out whole. */
static void lk_poll(void)
{
    u8 b, budget, want;

    budget = LK_RX_BUDGET;
    while (budget && lkcom_rx_pending()) {
        b = lkcom_get_data();
        budget--;

        if (lk_len == 0) {
            /* Hunting: anything that is not the marker is debris from a lost
             * byte and is thrown away. This is what makes recovery automatic. */
            if (b == LK_MARKER) {
                lk_buf[0] = b;
                lk_len    = 1;
            }
            continue;
        }

        lk_buf[lk_len] = b;
        lk_len++;

        if (lk_len == 2 && lk_len_for(b) == 0) {
            /* Unknown type: that marker was a false alarm. Start hunting from
             * scratch rather than swallow a fixed number of bytes. */
            lk_len = 0;
            continue;
        }

        want = lk_len_for(lk_buf[1]);
        if (want && lk_len >= want) {
            lk_len = 0;
            if (lk_checksum(lk_buf, want) == lk_buf[want - 1]) {
                if (lk_buf[1] == LK_TYPE_HELLO) lk_on_hello();
                else                            lk_on_data();
            } else {
                lk_c_sum++;
            }
        }
    }
}

/* --------------------------------------------------------------------------
 * public
 * ------------------------------------------------------------------------ */

void ngpc_linkkit_set_role(u8 role)
{
    lk_role_wanted = role;
}

void ngpc_linkkit_init(void)
{
    u8 i;

    lkcom_init();
    lkcom_recv_start();   /* both consoles must do this */
    lkcom_rts_on();       /* let the peer transmit      */

    lk_state     = LINKKIT_CONNECTING;
    lk_role      = (lk_role_wanted == LINKKIT_ROLE_AUTO)
                        ? LINKKIT_ROLE_HOST : lk_role_wanted;
    lk_ambiguous = 0;
    lk_ties      = 0;
    lk_frames    = 0;
    lk_silence   = 0;
    lk_fresh     = 0;
    lk_tx_seq    = 0;

    for (i = 0; i < (u8)NGPC_LINKKIT_PAYLOAD; i++) {
        lk_tx_payload[i] = 0;
        lk_rx_payload[i] = 0;
    }

    lk_c_tx = lk_c_rx = lk_c_skip = lk_c_sum = lk_c_drop = 0;

    lk_purge_rx();
    lk_token = lk_draw_token();
}

void ngpc_linkkit_stage(const u8 *payload)
{
    u8 i;
    for (i = 0; i < (u8)NGPC_LINKKIT_PAYLOAD; i++)
        lk_tx_payload[i] = payload[i];
}

void ngpc_linkkit_update(void)
{
    u16 status;

    if (lk_state == LINKKIT_IDLE || lk_state == LINKKIT_MISMATCH)
        return;

    /* A line error means the BIOS receiver needs re-arming. Reading the status
     * and doing nothing about it -- which is easy to write by accident -- just
     * leaves the port dead. */
    status = lkcom_recv_status();
    if (status & LKCOM_RX_ERR_MASK) {
        lkcom_recv_start();
        lkcom_rts_on();
        lk_purge_rx();
    }

    lk_poll();

    if (lk_state == LINKKIT_READY) {
        lk_send_data();
        if (lk_silence < (u16)NGPC_LINKKIT_LOST_FRAMES) {
            lk_silence++;
        } else {
            lk_state  = LINKKIT_LOST;
            lk_frames = 0;
            lk_token  = lk_draw_token();
        }
        return;
    }

    /* CONNECTING or LOST: announce ourselves periodically and keep listening.
     * A power-of-two period so this is a mask, not a modulo. */
    if ((lk_frames & (LK_HELLO_PERIOD - 1u)) == 0)
        lk_send_hello();

    lk_frames++;

    /* CONNECTING gives up so the caller can show "no link" and offer a way
     * out. LOST keeps trying forever: the cable may simply have been unplugged
     * for a moment, and coming back on its own is worth more than a timeout. */
    if (lk_state == LINKKIT_CONNECTING &&
        lk_frames >= (u16)NGPC_LINKKIT_CONNECT_FRAMES)
        lk_state = LINKKIT_IDLE;
}

u8 ngpc_linkkit_fresh(void) { return lk_fresh; }

void ngpc_linkkit_peek(u8 *payload)
{
    u8 i;
    for (i = 0; i < (u8)NGPC_LINKKIT_PAYLOAD; i++)
        payload[i] = lk_rx_payload[i];
    lk_fresh = 0;
}

u8 ngpc_linkkit_state(void)           { return lk_state; }
u8 ngpc_linkkit_role(void)            { return lk_role; }
u8 ngpc_linkkit_role_ambiguous(void)  { return lk_ambiguous; }

u16 ngpc_linkkit_tx_packets(void)  { return lk_c_tx; }
u16 ngpc_linkkit_rx_packets(void)  { return lk_c_rx; }
u16 ngpc_linkkit_tx_skipped(void)  { return lk_c_skip; }
u16 ngpc_linkkit_rx_bad_sum(void)  { return lk_c_sum; }
u16 ngpc_linkkit_rx_dropped(void)  { return lk_c_drop; }
