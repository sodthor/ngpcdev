package sim;

/**
 * TLCS-900/H CPU (Neo Geo Pocket Color, 93C141 variant).
 * All encodings verified from binary analysis of SAMPLE.ngp.
 *
 * STANDALONE OPCODE MAP (verified):
 *   0x00        NOP
 *   0x06 n      EI n  (DI = EI 7 = 0x06 0x07)
 *   0x07        RETI
 *   0x0E        RET
 *   0x17 n      LDF n  (set register bank = n & 3)
 *   0x1E d16    CALR d16  (unconditional relative call)
 *   0x20+r n    LD byte_reg[r], #n      (r=0..7: W,A,B,C,D,E,H,L)
 *   0x28        PUSH WA
 *   0x29        PUSH BC
 *   0x2A        PUSH DE
 *   0x2B        PUSH HL
 *   0x2C        PUSH IX
 *   0x2D        PUSH IY
 *   0x2E        PUSH IZ
 *   0x2F        PUSH SP
 *   0x30+r d16  LD word_reg[r], #d16    (r=0..7: WA,BC,DE,HL,IX,IY,IZ,SP)
 *   0x38        PUSH XWA
 *   0x39        PUSH XBC
 *   0x3A        PUSH XDE
 *   0x3B        PUSH XHL
 *   0x3C        PUSH XIX
 *   0x3D        PUSH XIY
 *   0x3E        PUSH XIZ
 *   0x3F        PUSH XSP
 *   0x40+r d32  LD dword_reg[r], #d32   (r=0..7: XWA,XBC,XDE,XHL,XIX,XIY,XIZ,XSP)
 *   0x48+r      POP word_reg[r]          (r=0..7)
 *   0x58+r      POP dword_reg[r]         (r=0..7: 0=XWA..7=XSP)
 *   0x60+r d16  JRL cc[r], d16           (cc table, r=0..7 short set)
 *   0x66 d8     JR Z, d8    (confirmed)
 *   0x67 d8     JR C, d8    (confirmed)
 *   0x6E d8     JR NZ, d8   (confirmed)
 *   0x6F d8     JR NC, d8   (confirmed)
 *   0x70+r d16  JRL cc[r], d16           (full 16-condition set)
 *   0x76 d16    JRL Z, d16   (confirmed)
 *   0x78 d16    JRL T, d16   (always, confirmed)
 *   0xB0 cc     RET cc  (conditional return, cc byte uses same encoding)
 *   0xB4 cc16   CALL (XIX)  (call register indirect)
 *
 * MEMORY PREFIX BYTE: 0x80 + (nibble<<4) + mode
 *   nibble 0 (0x80-0x8F): byte ops
 *   nibble 1 (0x90-0x9F): word ops
 *   nibble 2 (0xA0-0xAF): dword ops
 *   nibble 3 (0xB0-0xBF): byte ops (store direction in some contexts)
 *   nibble 4 (0xC0-0xC7): byte load/store ops (absolute-addr group)
 *   nibble 5 (0xD0-0xD7): word load/store ops (absolute-addr group)
 *   nibble 6 (0xE0-0xE7): dword load/store ops (absolute-addr group)
 *   nibble 7 (0xF0-0xFF): byte store ops (absolute-addr group)
 *
 *   Modes (bits[3:0]): 0=(d8), 1=(d16), 2=(d24), 3=(XIX), 4=(XIY), 5=(XIZ),
 *     6=(XSP), 7=(XSP+0 compact), 8=(XIX+d8), 9=(XIY+d8), A=(XIZ+d8), B=(XSP+d8),
 *     C=(XIX+d16), D=(XIY+d16), E=(XIZ+d16), F=(XSP+d8 explicit)
 *
 *   After mem prefix, op2:
 *     0x10+zz+r: LDA R, ea  (load effective address; zz=0x00/0x10/0x20 for byte/word/dword)
 *     0x20+r: LD reg[r], (ea)   load; size from prefix
 *     0x40+r: LD (ea), byte_reg[r]   byte store
 *     0x60+r: LD (ea), dword_reg[r]  dword store
 *     0x80+s: ADD (ea), reg[s]
 *     0xA0+s: SUB (ea), reg[s]
 *     0xB0:   JP (ea)
 *     0xB2:   CALL (ea)
 *
 * REGISTER PREFIX: C8+r=byte, D8+r=word, E8+r=dword (current bank, r=0..7)
 *
 *   After register prefix, op2:
 *     0xA8+n: LD reg, n   (n=0..15)
 *     0x88+d: LD dst[d], src_reg
 *     0xC8..0xCF: ADD/ADC/SUB/SBC/AND/XOR/OR/CP reg, imm
 *     0x80+s: ADD/ADC/SUB/SBC/... reg, reg[s]  (op varies by which group)
 *     0x40..0x47: INC reg, #(n+1)
 *     0x48..0x4F: DEC reg, #(n+1)
 *     0xC0..0xC7: shift/rotate with count byte
 *     0xE0..0xE3: BIT/RES/SET/CHG with bit-number byte
 *     0x70+cc: SCC cc, reg
 *     0x07/0x08/0x09: LD reg, #byte/word/dword
 *     0x12: EXTZ  0x13: EXTS  0x10: DAA  0x04: NEG  0x05: CPL
 *     0x1A: PUSH reg  0x1B: POP reg
 *     0x18: JP (reg)  0x1C: CALL (reg)
 *
 * CC table (nibble index, confirmed from binary):
 *   0=F, 1=LT(S≠V), 2=GE(S=V), 3=ULE(C|Z), 4=UGT(!C&!Z), 5=OV(V),
 *   6=Z(Z=1), 7=NZ(Z=0), 8=T(always), 9=MI(S=1), A=PL(S=0),
 *   B=C(C=1), C=NC(C=0), D=SGT(!Z&S=V), E=NZ(Z=0), F=T(always)
 *
 * JR cc encoding (short 8-bit displacement, 0x60..0x6F range):
 *   Confirmed: 0x66=JR Z, 0x67=JR C, 0x6E=JR NZ, 0x6F=JR NC
 *   Pattern: 0x60+cc where cc uses same table as above (nibble 0..F)
 */
public class Cpu {

    private final Memory    mem;
    private final Registers reg;

    private boolean halted = false;
    private long    cycles = 0;
    private boolean trace  = false;
    /** When set, logs every (base+index) register-offset memory read (the map
     *  collision reads in doMove use this addressing mode). */
    public static boolean traceIndexLoads = false;

    public interface SwiHandler {
        boolean handle(int n, Registers reg, Memory mem);
    }
    private SwiHandler swiHandler;

    public Cpu(Memory mem, Registers reg) { this.mem = mem; this.reg = reg; }

    public void setTrace(boolean t)         { trace = t; }
    public void setSwiHandler(SwiHandler h) { swiHandler = h; }
    public boolean isHalted()               { return halted; }
    public long    getCycles()              { return cycles; }
    public String  getRamReport()           { return mem.getRamReport(); }

    // ── Fetch ────────────────────────────────────────────────────────────────
    private int fetch8()  { int v=mem.readFetchByte(reg.getPC()); reg.addPC(1); return v; }
    private int fetch16() {
        int v=mem.readFetchByte(reg.getPC())|(mem.readFetchByte(reg.getPC()+1)<<8);
        reg.addPC(2); return v;
    }
    private int fetch24() {
        int v=mem.readFetchByte(reg.getPC())|(mem.readFetchByte(reg.getPC()+1)<<8)|(mem.readFetchByte(reg.getPC()+2)<<16);
        reg.addPC(3); return v;
    }
    private int fetch32() {
        int v=mem.readFetchByte(reg.getPC())|(mem.readFetchByte(reg.getPC()+1)<<8)
            |(mem.readFetchByte(reg.getPC()+2)<<16)|(mem.readFetchByte(reg.getPC()+3)<<24);
        reg.addPC(4); return v;
    }
    private static int se8(int v)  { return (byte) v; }
    private static int se16(int v) { return (short)v; }

    // ── Stack ─────────────────────────────────────────────────────────────────
    private void push16(int v) { reg.setXSP(reg.getXSP()-2); mem.trackStackAccess(reg.getXSP()); mem.writeWord(reg.getXSP(),v); }
    private void push32(int v) { reg.setXSP(reg.getXSP()-4); mem.trackStackAccess(reg.getXSP()); mem.writeDword(reg.getXSP(),v); }
    private int  pop8()  { int v=mem.readByte(reg.getXSP());  reg.setXSP(reg.getXSP()+1); return v; }
    private int  pop16() { int v=mem.readWord(reg.getXSP());  reg.setXSP(reg.getXSP()+2); return v; }
    private int  pop32() { int v=mem.readDword(reg.getXSP()); reg.setXSP(reg.getXSP()+4); return v; }
    private void pushPC() { push32(reg.getPC()); }
    private int  popPC()  { return pop32() & 0xFFFFFF; }
    // Push/pop at a given operand size (byte pushes round to a word — TLCS-900 keeps the
    // stack word-aligned; only 2-/4-byte GP pushes occur in practice).
    private void pushSized(int v, int sz) { if (sz >= 4) push32(v); else push16(v); }
    private int  popSized(int sz) { return sz >= 4 ? pop32() : pop16(); }

    // ── Flags ─────────────────────────────────────────────────────────────────
    private int mask(int sz) { return sz==1?0xFF:sz==2?0xFFFF:0xFFFFFFFF; }

    private void setSZ(int r, int sz) {
        if(sz==1){ reg.setFlagS((r&0x80)!=0);       reg.setFlagZ((r&0xFF)==0); }
        else if(sz==2){ reg.setFlagS((r&0x8000)!=0); reg.setFlagZ((r&0xFFFF)==0); }
        else{ reg.setFlagS((r&0x80000000)!=0);        reg.setFlagZ(r==0); }
    }
    private void addFlags(int a, int b, int r, int sz) {
        setSZ(r,sz); reg.setFlagN(false);
        if(sz==1){ a&=0xFF;b&=0xFF;r&=0xFF; reg.setFlagH(((a&0xF)+(b&0xF))>0xF); reg.setFlagV(((a^r)&(b^r)&0x80)!=0); reg.setFlagC((a+b)>0xFF); }
        else if(sz==2){ a&=0xFFFF;b&=0xFFFF;r&=0xFFFF; reg.setFlagH(((a&0xFFF)+(b&0xFFF))>0xFFF); reg.setFlagV(((a^r)&(b^r)&0x8000)!=0); reg.setFlagC((a+b)>0xFFFF); }
        else{ long la=a&0xFFFFFFFFL,lb=b&0xFFFFFFFFL,lr=r&0xFFFFFFFFL; reg.setFlagH((la&0xFFFFFFFL)+(lb&0xFFFFFFFL)>0xFFFFFFFL); reg.setFlagV(((la^lr)&(lb^lr)&0x80000000L)!=0); reg.setFlagC(la+lb>0xFFFFFFFFL); }
    }
    private void subFlags(int a, int b, int r, int sz) {
        setSZ(r,sz); reg.setFlagN(true);
        if(sz==1){ a&=0xFF;b&=0xFF;r&=0xFF; reg.setFlagH((a&0xF)<(b&0xF)); reg.setFlagV(((a^b)&(a^r)&0x80)!=0); reg.setFlagC(a<b); }
        else if(sz==2){ a&=0xFFFF;b&=0xFFFF;r&=0xFFFF; reg.setFlagH((a&0xFFF)<(b&0xFFF)); reg.setFlagV(((a^b)&(a^r)&0x8000)!=0); reg.setFlagC(a<b); }
        else{ long la=a&0xFFFFFFFFL,lb=b&0xFFFFFFFFL; reg.setFlagH((la&0xFFFFFFFL)<(lb&0xFFFFFFFL)); reg.setFlagV(((la^lb)&(la^(r&0xFFFFFFFFL))&0x80000000L)!=0); reg.setFlagC(la<lb); }
    }
    private void logicFlags(int r, int sz) {
        setSZ(r,sz); reg.setFlagH(false); reg.setFlagN(false); reg.setFlagC(false);
        reg.setFlagV((Integer.bitCount(r&mask(sz))&1)==0);
    }

    // ── Condition codes ───────────────────────────────────────────────────────
    // TLCS-900/H 4-bit condition-code table (matches the encoding asl produces and
    // the confirmed pairs 0x6=Z, 0x7=C, 0xE=NZ, 0xF=NC in the opcode notes above):
    //   0=F 1=LT 2=LE 3=ULE 4=OV 5=MI 6=Z 7=C 8=T 9=GE A=GT B=UGT C=NOV D=PL E=NZ F=NC
    private boolean cc(int c) {
        return switch(c&0xF) {
            case 0x0 -> false;                              // F (never)
            case 0x1 -> reg.flagS()!=reg.flagV();           // LT
            case 0x2 -> reg.flagZ()||(reg.flagS()!=reg.flagV()); // LE
            case 0x3 -> reg.flagC()||reg.flagZ();           // ULE
            case 0x4 -> reg.flagV();                        // OV
            case 0x5 -> reg.flagS();                        // MI
            case 0x6 -> reg.flagZ();                        // Z
            case 0x7 -> reg.flagC();                        // C
            case 0x8 -> true;                               // T (always)
            case 0x9 -> reg.flagS()==reg.flagV();           // GE
            case 0xA -> !reg.flagZ()&&(reg.flagS()==reg.flagV()); // GT
            case 0xB -> !reg.flagC()&&!reg.flagZ();         // UGT
            case 0xC -> !reg.flagV();                       // NOV
            case 0xD -> !reg.flagS();                       // PL
            case 0xE -> !reg.flagZ();                       // NZ
            case 0xF -> !reg.flagC();                       // NC
            default  -> false;
        };
    }

    // ── Memory prefix helpers ─────────────────────────────────────────────────
    private static final int[] SIZE_FROM_NIBBLE = {1,2,4,1, 1,2,4,1};

    // Effective-address decode for a memory-prefix byte.
    //
    // The TLCS-900/H memory prefix packs an addressing MODE and a data SIZE into one byte:
    //   mode = ((pfx & 0x40) >> 2) | (pfx & 0x0F)   (5-bit: bit6 -> bit4, plus low nibble)
    //   size = (pfx & 0x30) >> 4                     (0=byte, 1=word, 2=long)
    // Modes:  0..7 = (Rn) indirect (Rn = WA,BC,DE,HL,IX,IY,IZ,SP);  8..15 = (Rn+d8);
    //         16 = (n8);  17 = (n16);  18 = (n24);  19 = extended (index / Rn+d16);
    //         20 = (-Rn) pre-decrement;  21 = (Rn+) post-increment.
    // (Verified against asl -L output and ngpc_disasm.py's _getmem/_getzz_mem.)
    //
    // Pre-dec/post-inc adjust the register by the access size; the amount is taken from
    // the sub-byte (0xE0 + pair*4 + zz), not the prefix's own size field, and the register
    // update is applied here so execMemOp sees the final address.
    private int eaMode(int pfx){ return ((pfx & 0x40) >> 2) | (pfx & 0x0F); }

    private int ea(int pfx) {
        int mode = eaMode(pfx);
        if (mode <= 7)  return reg.getDword32(mode) & 0xFFFFFF;                 // (Rn)
        if (mode <= 15) return (reg.getDword32(mode-8) + se8(fetch8())) & 0xFFFFFF; // (Rn+d8)
        switch (mode) {
            case 16: return fetch8()  & 0xFF;      // (n8)
            case 17: return fetch16() & 0xFFFF;    // (n16)
            case 18: return fetch24();             // (n24)
            case 20: {                             // (-Rn) pre-decrement
                int sub  = fetch8() & 0xFF;
                int pair = (sub - 0xE0) >> 2;
                int inc  = 1 << (sub & 3);          // zz: 0->1, 1->2, 2->4
                int v    = (reg.getDword32(pair) - inc) & 0xFFFFFF;
                reg.setDword32(pair, v);
                return v;
            }
            case 21: {                             // (Rn+) post-increment
                int sub  = fetch8() & 0xFF;
                int pair = (sub - 0xE0) >> 2;
                int inc  = 1 << (sub & 3);
                int v    = reg.getDword32(pair) & 0xFFFFFF;
                reg.setDword32(pair, (v + inc) & 0xFFFFFF);
                return v;
            }
            case 19: {                             // extended: (Rn+d16) or (base+index)
                // Sub-byte 0xE1 + pair*4 (E1=WA E5=BC E9=DE ED=HL F1=IX F5=IY F9=IZ FD=SP)
                // => (Rn+d16), signed 16-bit displacement.  asl: `lda xwa,(xbc+256)`=F3 E5 00 01 30.
                int peek = fetch8();
                if (peek >= 0xE1 && peek <= 0xFD && (peek & 0x03) == 1) {
                    int pair = (peek - 0xE1) >> 2;
                    return (reg.getDword32(pair) + se16(fetch16())) & 0xFFFFFF;
                }
                // Register-index: 0x07 = 16-bit index, 0x03 = 8-bit index, each followed by
                // base + index register-code bytes (0xE0 + code*4). Index zero-extended.
                if (peek == 0x07 || peek == 0x03) {
                    int baseByte = fetch8() & 0xFF;
                    int idxByte  = fetch8() & 0xFF;
                    int baseCode = (baseByte - 0xE0) >> 2;
                    int idxCode  = (idxByte  - 0xE0) >> 2;
                    int base  = reg.getDword32(baseCode);
                    int index = (peek == 0x07)
                        ? (reg.getWord16(idxCode) & 0xFFFF)   // 16-bit index, zero-extended
                        : (reg.getByteReg(idxCode) & 0xFF);   // 8-bit index, zero-extended
                    int addr = (base + index) & 0xFFFFFF;
                    if (traceIndexLoads) {
                        System.out.printf("  [idx-load @PC~0x%06X] base=0x%06X index=%d addr=0x%06X tile=%d spr_y=%d%n",
                            reg.getPC(), base, index, addr, mem.readByte(addr) & 0xFF, mem.readWord(0x400E)&0xFFFF);
                    }
                    return addr;
                }
                // Unknown extended sub-byte: rewind it and fall back to (XHL) so the op2
                // stream stays aligned (matches prior conservative behaviour).
                reg.addPC(-1); return reg.getXHL();
            }
            default: return 0;
        }
    }
    private int memSize(int pfx) { return SIZE_FROM_NIBBLE[(pfx & 0x30) >> 4]; }
    private int readMem(int a,int sz){
        return switch(sz){ case 1->mem.readByte(a); case 2->mem.readWord(a); default->mem.readDword(a); };
    }
    private void writeMem(int a,int v,int sz){
        switch(sz){ case 1->mem.writeByte(a,v); case 2->mem.writeWord(a,v); default->mem.writeDword(a,v); }
    }

    // ── Register access ───────────────────────────────────────────────────────
    private int  readReg(int r,int sz){
        return switch(sz){ case 1->reg.getByteReg(r); case 2->reg.getWord16(r); default->reg.getDword32(r); };
    }
    private void writeReg(int r,int v,int sz){
        switch(sz){ case 1->reg.setByteReg(r,v); case 2->reg.setWord16(r,v); default->reg.setDword32(r,v); }
    }
    private int fetchImm(int sz){
        return switch(sz){ case 1->(byte)fetch8(); case 2->(short)fetch16(); default->fetch32(); };
    }

    // ── Arithmetic ────────────────────────────────────────────────────────────
    private int doArith(int op, int a, int b, int sz) {
        int r;
        switch(op&7) {
            case 0: r=(a+b)&mask(sz);            addFlags(a,b,r,sz); return r;
            case 1:{ int c=reg.flagC()?1:0; r=(a+b+c)&mask(sz); addFlags(a,b+c,r,sz); return r; }
            case 2: r=(a-b)&mask(sz);            subFlags(a,b,r,sz); return r;
            case 3:{ int c=reg.flagC()?1:0; r=(a-b-c)&mask(sz); subFlags(a,b+c,r,sz); return r; }
            case 4: r=(a&b)&mask(sz);            logicFlags(r,sz); return r;
            case 5: r=(a^b)&mask(sz);            logicFlags(r,sz); return r;
            case 6: r=(a|b)&mask(sz);            logicFlags(r,sz); return r;
            case 7: subFlags(a,b,(a-b)&mask(sz),sz); return a; // CP: no store
            default: return a;
        }
    }

    // ── Shift/Rotate ──────────────────────────────────────────────────────────
    private int doShift(int op, int v, int n, int sz) {
        // Callers pass the already-resolved count (1..16; the immediate-0 => 16 mapping is
        // done at decode). Guard against a stray 0 only as defence; real shifts never pass 0.
        if(n==0)n=1;
        int m=mask(sz); int bits=sz*8; int top=1<<(bits-1);
        boolean lc=reg.flagC();
        for(int i=0;i<n;i++) switch(op&7) {
            case 0:{ lc=(v&top)!=0; v=(v<<1)&m; break; }   // SLA
            case 1:{ lc=(v&1)!=0;   v=((v&m)>>1)|((v&top)!=0?top:0); break; } // SRA
            case 2:{ lc=(v&1)!=0;   v=((v&m)>>>1); break; } // SRL
            case 3:{ boolean co=(v&top)!=0; v=((v<<1)|(co?1:0))&m; lc=co; break; } // RLC
            case 4:{ boolean co=(v&1)!=0; v=(((v&m)>>>1)|(co?top:0))&m; lc=co; break; } // RRC
            case 5:{ boolean ci=lc; boolean co=(v&top)!=0; v=((v<<1)|(ci?1:0))&m; lc=co; break; } // RL
            case 6:{ boolean ci=lc; boolean co=(v&1)!=0; v=(((v&m)>>>1)|(ci?top:0))&m; lc=co; break; } // RR
            case 7:{ lc=(v&top)!=0; v=(v<<1)&m; break; }   // SLL
        }
        // Flags per datasheet (SLA/SRL pages): S,Z from result; H=0; N=0; C=last bit out.
        // V = parity (number of 1s even) for byte/word; UNDEFINED for 32-bit operand — we
        // compute the parity deterministically so both merged/split shift forms still agree.
        reg.setFlagC(lc); setSZ(v&m,sz); reg.setFlagN(false); reg.setFlagH(false);
        int vv=v&m, ones=Integer.bitCount(vv);
        reg.setFlagV((ones & 1)==0);
        return v&m;
    }

    private int shiftOperation(int opcode) {
        return switch (opcode & 7) {
            case 0 -> 3; case 1 -> 4; case 2 -> 5; case 3 -> 6;
            case 4 -> 0; case 5 -> 1; case 6 -> 7; default -> 2;
        };
    }

    private int shiftCount(int opcode) {
        int count = ((opcode & 0x10) == 0 ? fetch8() : reg.getA()) & 15;
        return count == 0 ? 16 : count;
    }

    // ── Register-prefix operations ────────────────────────────────────────────
    private void execRegOp(int r, int sz) {
        int op2=fetch8();

        // DJNZ r, $disp8  (op2 = 0x1C): decrement r (by the reg's size), and if the
        // result is non-zero branch by the signed 8-bit displacement that follows.
        // The displacement is relative to the address just past the disp byte.
        if(op2==0x1C){
            int v = (readReg(r,sz) - 1) & mask(sz);
            writeReg(r, v, sz);
            int disp = se8(fetch8());
            if(v != 0) reg.setPC((reg.getPC() + disp) & 0xFFFFFF);
            cycles += (v!=0)?11:7;
            return;
        }

        // Quick LD reg, n  (A8..B7)
        if(op2>=0xA8&&op2<=0xB7){ writeReg(r,op2-0xA8,sz); cycles+=4; return; }

        // LD dst, reg  (88..8F)
        if(op2>=0x88&&op2<=0x8F){ writeReg(op2-0x88, readReg(r,sz), sz); cycles+=4; return; }

        // CP reg, #n  quick-immediate form (op2 = 0xD8+n, n=0..7): a TWO-byte
        // instruction with NO trailing immediate. asl encodes `cp <reg>,#n` for
        // small n=0..7 as prefix + (0xD8+n): 0xD8=CP#0, 0xD9=CP#1, ... 0xDF=CP#7.
        // (For n>=8 asl falls back to the 0xCF immediate form, handled below.)
        // Sets flags from (reg - n); does NOT write the register.
        if(op2>=0xD8&&op2<=0xDF){ int v=readReg(r,sz); int n=op2-0xD8; subFlags(v, n, (v-n)&mask(sz), sz); cycles+=4; return; }

        // Arithmetic reg, imm (C8..CF): ADD/ADC/SUB/SBC/AND/XOR/OR/CP reg, imm_n
        if(op2>=0xC8&&op2<=0xCF){
            int imm = (sz==1)?se8(fetch8()):((sz==2)?se16(fetch16()):fetch32());
            int aop = op2-0xC8;   // 0=ADD,1=ADC,2=SUB,3=SBC,4=AND,5=XOR,6=OR,7=CP
            int a   = readReg(r, sz);
            int res = doArith(aop, a, imm, sz);
            if(aop != 7) writeReg(r, res, sz);
            cycles += 6; return;
        }

        // Encoding: op2 = base + dst_reg_idx
        // base: ADD=0x80, ADC=0x90, SUB=0xA0, SBC=0xB0, AND=0xC0, XOR=0xD0, OR=0xE0, CP=0xF0
        // src = this register (from prefix), dst = reg[op2 & 7]
        if(op2>=0x80){
            // Skip ranges handled by other cases above (A8-B7=quick LD, 88-8F=LD dst,src, C8-CF=arith imm)
            // Skip E0-E3 (bit ops), 70-7F (SCC) -- handled above
            // This handles: 80-87, 90-97, A0-A7, B0-B7, C0-C7 (shift), D0-DF, E0-EF, F0-FF
            // But shifts (C0-C7) are already handled above, bit ops (E0-E3) already handled
            // Let's just handle the arith ones: base in {0x80,0x90,0xA0,0xB0,0xC0,0xD0,0xE0,0xF0}
            int base = op2 & 0xF8;
            int dst  = op2 & 0x07;
            int aop_table[] = {0,1,2,3,4,5,6,7}; // ADD,ADC,SUB,SBC,AND,XOR,OR,CP
            int aop = switch(base) {
                case 0x80 -> 0; // ADD
                case 0x90 -> 1; // ADC
                case 0xA0 -> 2; // SUB
                case 0xB0 -> 3; // SBC
                case 0xC0 -> 4; // AND
                case 0xD0 -> 5; // XOR
                case 0xE0 -> 6; // OR
                case 0xF0 -> 7; // CP
                default   -> -1;
            };
            if(aop >= 0) {
                int a = readReg(dst, sz);  // dst is first operand
                int b = readReg(r,   sz);  // src (this register from prefix) is second operand
                int res = doArith(aop, a, b, sz);
                if(aop != 7) writeReg(dst, res, sz);
                cycles += 4; return;
            }
        }

        // Arithmetic reg op imm  (C8..CF)
        if(op2>=0xC8&&op2<=0xCF){
            int aop=op2-0xC8, a=readReg(r,sz), b=fetchImm(sz)&mask(sz), res=doArith(aop,a,b,sz);
            if(aop!=7) writeReg(r,res,sz);
            cycles+=6; return;
        }

        // NOTE: op2=0x40-0x5F in register context is MUL/MULS/DIV/DIVS (handled
        // below), NOT INC/DEC. The canonical INC #3 / DEC #3 forms are 0x60/0x68
        // (per the TLCS-900 opcode map and confirmed against CC900 ROMs).
        // INC #3, reg: op2 = 0x60+#3. The #3 field encodes the amount as
        // field 1..7 -> 1..7 and field 0 -> 8 (asl: `inc 1,a`=..61, `inc 8,a`=..60).
        // Flags (Toshiba TLCS-900 table): BYTE reg sets S,Z,H,V,N but PRESERVES C;
        // WORD/LONG reg change NO flags at all (`- - - - - -`). DEC is identical
        // with N=1. (Both cores historically set S/Z at every size and were wrong.)
        if(op2>=0x60&&op2<=0x67){ int n=(op2&7)==0?8:(op2&7); int v=readReg(r,sz); int res=(v+n)&mask(sz); writeReg(r,res,sz);
            if(sz==1){ boolean c=reg.flagC(); addFlags(v,n,res,sz); reg.setFlagC(c); } cycles+=4; return; }
        // DEC #3, reg: op2 = 0x68+#3 (field 0 -> 8, field 1..7 -> 1..7)
        if(op2>=0x68&&op2<=0x6F){ int n=(op2&7)==0?8:(op2&7); int v=readReg(r,sz); int res=(v-n)&mask(sz); writeReg(r,res,sz);
            if(sz==1){ boolean c=reg.flagC(); subFlags(v,n,res,sz); reg.setFlagC(c); } cycles+=4; return; }

        // E8..EF use an immediate count; F8..FF use A. Zero encodes 16.
        // Keep the existing flat cycle estimate for shifts and rotates.
        if ((op2 >= 0xE8 && op2 <= 0xEF) || op2 >= 0xF8) {
            int n = shiftCount(op2);
            int res = doShift(shiftOperation(op2), readReg(r, sz), n, sz);
            writeReg(r, res, sz); cycles+=4; return;
        }
        if(op2>=0xC0&&op2<=0xC7){ int n=(fetch8()&0x0F); if(n==0)n=16; writeReg(r, doShift(op2-0xC0, readReg(r,sz), n, sz), sz); cycles+=4; return; }

        // Bit ops: 0x30=RES, 0x31=SET, 0x32=CHG, 0x33=BIT (bit-number byte follows)
        if(op2>=0x30&&op2<=0x33){
            int b=fetch8()&31; int v=readReg(r,sz);
            switch(op2){
                case 0x30->writeReg(r,v&~(1<<b),sz);
                case 0x31->writeReg(r,v|(1<<b),sz);
                case 0x32->writeReg(r,v^(1<<b),sz);
                case 0x33->{ reg.setFlagZ(((v>>b)&1)==0); reg.setFlagH(true); reg.setFlagN(false); }
            }
            cycles+=4; return;
        }
        // 0x34-0x3F: extended bit/flag ops with bit-number byte (TSET, LDCF, etc.)
        // 0x3F is dummy per TLCS opcode map; others treated as BIT-test + TSET
        if(op2>=0x34&&op2<=0x3F){
            if(op2==0x3F){ cycles+=4; return; }
            int b=fetch8()&31; int v=readReg(r,sz);
            reg.setFlagZ(((v>>b)&1)==0); reg.setFlagH(true); reg.setFlagN(false);
            if(op2==0x34){ writeReg(r,v|(1<<b),sz); } // TSET: test and set
            cycles+=4; return;
        }

        // Bit ops old mapping kept for safety (E0..E3 with bit-number byte)
        if(op2>=0xE0&&op2<=0xE3){
            int b=fetch8()&31; int v=readReg(r,sz);
            switch(op2){
                case 0xE0->{ reg.setFlagZ(((v>>b)&1)==0); reg.setFlagH(true); reg.setFlagN(false); }
                case 0xE1->writeReg(r,v&~(1<<b),sz);
                case 0xE2->writeReg(r,v|(1<<b),sz);
                case 0xE3->writeReg(r,v^(1<<b),sz);
            }
            cycles+=4; return;
        }

        // SCC cc, reg  (70..7F)
        if(op2>=0x70&&op2<=0x7F){ writeReg(r,cc(op2-0x70)?1:0,sz); cycles+=6; return; }

        // MUL/MULS/DIV/DIVS reg, #imm : op2 0x08/0x09/0x0A/0x0B. The immediate width is
        // the PREFIX size (byte prefix -> imm8, word prefix -> imm16); the result is
        // double-width in the same register pair.  (asl: `muls xwa,#`=D8 09 imm16,
        // `divs wa,#`=C9 0B imm8.)  This replaces the old LD reg,#imm decode here — LD
        // reg,#imm actually uses the standalone opcodes 0x20/0x30/0x40, not a reg prefix.
        if(op2==0x08||op2==0x09){                       // MUL / MULS (immediate)
            boolean signed=(op2==0x09);
            int rsz=sz*2;
            long a=readReg(r,sz)&mask(sz);
            long b=(sz==1)?(fetch8()&0xFF):(fetch16()&0xFFFF);
            long res;
            if(!signed){ res=a*b; }
            else{ int sh=32-sz*8; long as=(int)((readReg(r,sz)<<sh)>>sh); long bs=(int)((b<<sh)>>sh); res=as*bs; }
            writeReg(r,(int)(res&mask(rsz)),rsz); cycles+=14; return;
        }
        if(op2==0x0A||op2==0x0B){                       // DIV / DIVS (immediate)
            boolean signed=(op2==0x0B);
            long b=(sz==1)?(fetch8()&0xFF):(fetch16()&0xFFFF);
            execDiv(r, b, sz, signed); cycles+=18; return;
        }

        // LD r, #imm32 : op2=0x03 (a 4-byte immediate always follows, result is 32-bit).
        // ngpc_disasm decode_zz_r: reg-prefix op2 0x03 = `ld r32, imm32`.
        if(op2==0x03){ writeReg(r, fetch32(), 4); cycles+=8; return; }

        // DAA / EXTZ / EXTS / NEG / CPL
        if(op2==0x10){ execDAA(); return; }
        // EXTZ: zero-extend lower half into full register (byte→word, word→dword)
        // For sz=2 (extz wa): read A (sz/2=1), zero-extend into WA
        // For sz=4 (extz xwa): read WA (sz/2=2), zero-extend into XWA
        // EXTZ: zero-extend the LOW half of the register into the full width.
        // The low half is the low hsz bytes of the register's current value — NOT
        // readReg(r,hsz), which for a word target (hsz=1) would read byte-register
        // code r (=W, the HIGH byte of WA) instead of the low byte A.
        if(op2==0x12){ int hsz=sz/2; int v=readReg(r,sz)&mask(hsz); writeReg(r,v,sz); cycles+=4; return; } // EXTZ
        // EXTS: sign-extend the LOW half (byte→word, word→dword)
        if(op2==0x13){ int hsz=sz/2; int v=readReg(r,sz)&mask(hsz); int sv=(v<<((32-hsz*8)))>>(32-hsz*8); writeReg(r,sv&mask(sz),sz); cycles+=4; return; } // EXTS
        if(op2==0x07){ int v=readReg(r,sz); int res=(-v)&mask(sz); writeReg(r,res,sz); subFlags(0,v,res,sz); cycles+=4; return; } // NEG
        if(op2==0x06){ writeReg(r,(~readReg(r,sz))&mask(sz),sz); reg.setFlagH(true); reg.setFlagN(true); cycles+=4; return; } // CPL

        // PUSH / POP
        if(op2==0x1A){ push32(readReg(r,sz)); cycles+=6; return; }
        if(op2==0x1B){ writeReg(r,pop32(),sz); cycles+=6; return; }

        // JP / CALL via register
        if(op2==0x18){ reg.setPC(readReg(r,sz)&0xFFFFFF); cycles+=6; return; }
        if(op2==0x1C){ pushPC(); reg.setPC(readReg(r,sz)&0xFFFFFF); cycles+=12; return; }

        // LDA R, mem  (load effective address into register; mem prefix follows)
        if(op2==0x16){ int pfx2=fetch8(); writeReg(r,ea(pfx2),sz); cycles+=8; return; }

        // SWAP
        if(op2==0x0D){
            int v=readReg(r,sz);
            int res=(sz==1)?((v<<4)|(v>>4))&0xFF:(sz==2)?((v<<8)|(v>>8))&0xFFFF:Integer.rotateLeft(v,16);
            writeReg(r,res,sz); cycles+=6; return;
        }

        // MUL RR, r  (op2=0x40+R) / MULS RR, r (op2=0x48+R): register form.
        // asl encoding: the PREFIX register (r, passed in) is the SOURCE, and the low 3
        // bits of op2 select the DESTINATION RR. Result is double width and lands in the
        // dst: word form = 32(dst) <- 16(dst lower half) x 16(src). Per TLCS-900 spec
        // p80-84 (example MUL XIX,IY: dst XIX gets the product; asl `mul xwa,hl`=DB40 has
        // prefix DB=src HL, op2 40=dst XWA). For byte MUL, op2 encodes the
        // destination pair by its low byte (A/C/E/L = 1/3/5/7).
        if(op2>=0x40&&op2<=0x4F){
            boolean signed=op2>=0x48; int encodedDst=op2&7; int s=r;
            int d=(sz==1) ? encodedDst>>1 : encodedDst;
            int rsz=sz*2;
            long res;
            int dstOperand = (sz==1) ? readReg(encodedDst,1) : readReg(d,sz);
            if(!signed){
                res=(dstOperand&mask(sz)) * (readReg(s,sz)&mask(sz));
            }else{
                int sh=32-sz*8;
                int as=(int)((dstOperand<<sh)>>sh);
                int bs=(int)((readReg(s,sz)<<sh)>>sh);
                res=(long)as*(long)bs;
            }
            writeReg(d,(int)(res&mask(rsz)),rsz); cycles+=14; return;
        }
        // DIV RR, r (op2=0x50+R) / DIVS RR, r (op2=0x58+R): register form.
        // Same operand convention as MUL: dst = op2&7 (the dividend, 2*sz wide),
        // src = r (the prefix register, the divisor, sz wide). Quotient -> dst lower
        // half, remainder -> dst upper half. Per TLCS-900 spec p85-88 (DIV XIX,IY:
        // XIX(32)/IY(16) -> XIX = rem:quot). On divide-by-zero V is set, dst unchanged.
        if(op2>=0x50&&op2<=0x5F){
            boolean signed=op2>=0x58; int d=op2&7; int s=r;
            execDiv(d, readReg(s,sz)&mask(sz), sz, signed); cycles+=18; return;
        }
        // NOTE on immediate-source MUL/DIV (`MUL rr,#`, op2 0x08-0x0B per some
        // opcode tables): NOT decoded here because op2=0x08/0x09 are already used
        // by this core as LD reg,#16 / LD reg,#32 (see above), a collision we have
        // no ROM evidence to resolve. The register-source forms below (0x40-0x5F)
        // are the ones the real CC900 toolchain emits and are verified against the
        // TLCS-900 manual's worked examples. Add the immediate forms only with a
        // real ROM that exercises them to disambiguate.
        // MUL / MULS (legacy op2=0x28/0x29 with a following source byte — kept for
        // any ROM that used this speculative encoding).
        if(op2==0x28||op2==0x29){
            int s=fetch8()&7;
            int rsz=sz*2;                       // result width (2 or 4 bytes)
            long a=readReg(r,sz)&mask(sz);
            long b=readReg(s,sz)&mask(sz);
            long res;
            if(op2==0x28){                       // MUL (unsigned)
                res=a*b;
            }else{                               // MULS (signed): sign-extend operands
                int sh=32-sz*8;
                int as=(int)((readReg(r,sz)<<sh)>>sh);
                int bs=(int)((readReg(s,sz)<<sh)>>sh);
                res=(long)as*(long)bs;
            }
            writeReg(r,(int)(res&mask(rsz)),rsz); cycles+=14; return;
        }

        throw new CpuException(String.format("Unhandled reg-op2=0x%02X (r=%d sz=%d) at PC=%06X",op2,r,sz,reg.getPC()));
    }

    // DIV/DIVS shared: dividend = dst register at 2*sz, divisor sz-wide (already
    // masked for unsigned). Quotient -> dst lower half, remainder -> upper half.
    // On divide-by-zero: set V, leave dst unchanged (no trap). V is also set if
    // the quotient does not fit the dst lower half.  Per TLCS-900 spec p85-88.
    private void execDiv(int r, long divisorRaw, int sz, boolean signed) {
        int dsz = sz * 2;
        if((divisorRaw & mask(sz)) == 0){ reg.setFlagV(true); return; }
        long q, rem;
        if(!signed){
            long dividend = Integer.toUnsignedLong(readReg(r,dsz)) & Integer.toUnsignedLong(mask(dsz));
            long divisor  = divisorRaw & mask(sz);
            q = dividend/divisor; rem = dividend%divisor;
        }else{
            int shd=32-dsz*8, shs=32-sz*8;
            long dividend = (int)(readReg(r,dsz)<<shd)>>shd;      // sign-extend dividend
            long divisor  = (int)(divisorRaw<<shs)>>shs;          // sign-extend divisor
            q = dividend/divisor; rem = dividend%divisor;          // Java: truncate toward zero
        }
        // V set if quotient overflows the dst lower half (sz bits).
        boolean ovf = signed
            ? (q < -(1L<<(sz*8-1)) || q >= (1L<<(sz*8-1)))
            : ((q & ~mask(sz)) != 0);
        reg.setFlagV(ovf);
        int packed = (int)(((rem & mask(sz)) << (sz*8)) | (q & mask(sz)));
        writeReg(r, packed, dsz);
    }

    // ── Register-file-direct operations ───────────────────────────────────────
    // "R" (register-file-direct) operand: [C7|D7|E7] <regfile-addr> <op2> [imm...]
    // 00..3F select absolute GP banks 0..3; D0..DF / E0..EF select previous/current
    // banks, and F0..FF select shared index/SP registers. Low address bits select
    // the byte lane or low/upper word (e.g. E2 is current-bank QWA).
    private void execRegFileDirectOp(int sz) {
        int rfa = fetch8() & 0xFF;   // register-file address byte
        int bank = (rfa & 0xF0) == 0xE0 ? reg.bank()
            : (rfa & 0xF0) == 0xD0 ? (reg.bank() + 3) & 3 : (rfa >> 4) & 3;
        int pair = (rfa & 0x0F) >> 2;
        int shift = (rfa & (4 - sz)) * 8;

        // Index/SP register-file addresses (0xF0-0xFF): 0xF0=XIX 0xF4=XIY 0xF8=XIZ 0xFC=XSP.
        // asl/cc900 name these rW15/rC15/etc. but they are just the fixed index registers,
        // NOT hardware control regs. cc900 title/menu wait loops read IZ this way
        // (ld A,rW15 = C7 F8 99 = ld A, IZ-byte0), so decoding them as real register
        // accesses (not NOP) lets those loops observe an ISR-updated IZ and exit.
        if (rfa >= 0xF0) {
            int ir  = (rfa - 0xF0) >> 2;      // 0=XIX,1=XIY,2=XIZ,3=XSP
            int off = (rfa & 3);              // byte offset within the 32-bit reg (byte size)
            int op2 = fetch8() & 0xFF;
            java.util.function.IntSupplier readR = () -> switch (sz) {
                case 1  -> (reg.getDword32(4 + ir) >>> (off * 8)) & 0xFF;
                case 2  -> (reg.getDword32(4 + ir) >>> shift) & 0xFFFF;
                default -> reg.getDword32(4 + ir);
            };
            java.util.function.IntConsumer writeR = v -> {
                switch (sz) {
                    case 1  -> { int cur = reg.getDword32(4 + ir); int sh = off * 8;
                                 reg.setDword32(4 + ir, (cur & ~(0xFF << sh)) | ((v & 0xFF) << sh)); }
                    case 2  -> { int cur = reg.getDword32(4 + ir);
                                 reg.setDword32(4 + ir, (cur & ~(0xFFFF << shift)) | ((v & 0xFFFF) << shift)); }
                    default -> reg.setDword32(4 + ir, v);
                }
            };
            // LD reg[op2-0x88], r  (0x88..0x8F): index reg is SOURCE.
            if (op2 >= 0x88 && op2 <= 0x8F) { writeReg(op2 - 0x88, readR.getAsInt(), sz); cycles += 4; return; }
            // LD r, reg[op2-0x98]  (0x98..0x9F): index reg is DEST.
            if (op2 >= 0x98 && op2 <= 0x9F) { writeR.accept(readReg(op2 - 0x98, sz)); cycles += 4; return; }
            // Bit ops (0x30=RES,0x31=SET,0x32=CHG,0x33=BIT) + bit-number byte.
            if (op2 >= 0x30 && op2 <= 0x33) {
                int b = fetch8() & 31; int v = readR.getAsInt();
                switch (op2) {
                    case 0x30 -> writeR.accept(v & ~(1 << b));
                    case 0x31 -> writeR.accept(v |  (1 << b));
                    case 0x32 -> writeR.accept(v ^  (1 << b));
                    case 0x33 -> { reg.setFlagZ(((v >> b) & 1) == 0); reg.setFlagH(true); reg.setFlagN(false); }
                }
                cycles += 4; return;
            }
            // Arith reg[op2&7], r  (0x80 ADD..0xF0 CP): index reg is SECOND operand.
            int abase = op2 & 0xF8;
            int iaop = switch (abase) {
                case 0x80 -> 0; case 0x90 -> 1; case 0xA0 -> 2; case 0xB0 -> 3;
                case 0xC0 -> 4; case 0xD0 -> 5; case 0xE0 -> 6; case 0xF0 -> 7; default -> -1;
            };
            if (iaop >= 0) {
                int dst = op2 & 0x07; int a = readReg(dst, sz); int b = readR.getAsInt();
                int res = doArith(iaop, a, b, sz);
                if (iaop != 7) writeReg(dst, res, sz);
                cycles += 4; return;
            }
            // r op= #imm (0xC8..0xCF).
            if (op2 >= 0xC8 && op2 <= 0xCF) {
                int imm = (sz == 1) ? se8(fetch8()) : (sz == 2 ? se16(fetch16()) : fetch32());
                int a2 = readR.getAsInt(); int res = doArith(op2 - 0xC8, a2, imm, sz);
                if ((op2 - 0xC8) != 7) writeR.accept(res);
                cycles += 6; return;
            }
            // CP r,#quick (0xD8..0xDF).
            if (op2 >= 0xD8 && op2 <= 0xDF) {
                int v = readR.getAsInt(); int n = op2 - 0xD8;
                subFlags(v, n, (v - n) & mask(sz), sz); cycles += 4; return;
            }
            // Quick LD r,#n (0xA8..0xB7).
            if (op2 >= 0xA8 && op2 <= 0xB7) { writeR.accept(op2 - 0xA8); cycles += 4; return; }
            // INC/DEC #n,r (0x60..0x6F).
            if (op2 >= 0x60 && op2 <= 0x6F) {
                boolean dec = (op2 & 0x08) != 0; int n = (op2 & 7) == 0 ? 8 : (op2 & 7);
                int v = readR.getAsInt(); int res = (dec ? (v - n) : (v + n)) & mask(sz);
                writeR.accept(res);
                if (sz == 1) { boolean c = reg.flagC(); if (dec) subFlags(v, n, res, sz); else addFlags(v, n, res, sz); reg.setFlagC(c); }
                cycles += 4; return;
            }
            if (op2 == 0x07) { int v = readR.getAsInt(); int res = (-v) & mask(sz); writeR.accept(res); subFlags(0, v, res, sz); cycles += 4; return; } // NEG
            if (op2 == 0x06) { writeR.accept((~readR.getAsInt()) & mask(sz)); reg.setFlagH(true); reg.setFlagN(true); cycles += 4; return; }             // CPL
            if (op2 == 0x12) { int hsz = sz / 2; writeR.accept(readR.getAsInt() & mask(hsz)); cycles += 4; return; }                                     // EXTZ
            if (op2 == 0x13) { int hsz = sz / 2; int v = readR.getAsInt() & mask(hsz); int sv = (v << (32 - hsz * 8)) >> (32 - hsz * 8); writeR.accept(sv & mask(sz)); cycles += 4; return; } // EXTS
            if (op2 == 0x04) { pushSized(readR.getAsInt(), sz); cycles += 8; return; } // PUSH r
            if (op2 == 0x05) { writeR.accept(popSized(sz)); cycles += 8; return; }      // POP r
            if ((op2 >= 0xE8 && op2 <= 0xEF) || op2 >= 0xF8) {
                int n = shiftCount(op2);
                writeR.accept(doShift(shiftOperation(op2), readR.getAsInt(), n, sz)); cycles+=4; return;
            }
            // LD r, #imm (0x03): immediate width follows the operand size (sz).
            if (op2 == 0x03) {
                int imm = (sz == 1) ? fetch8() : (sz == 2 ? fetch16() : fetch32());
                writeR.accept(imm); cycles += 4; return;
            }
            throw new CpuException(String.format(
                "Unhandled idx-reg op2=0x%02X (rfa=0x%02X sz=%d) at PC=%06X", op2, rfa, sz, reg.getPC()));
        }

        // 00..3F select absolute banks; D0/ E0 select previous/current bank.
        if (rfa < 0x40 || (rfa >= 0xD0 && rfa < 0xF0)) {
            int op2 = fetch8() & 0xFF;

            // R read/write helpers at this size.
            java.util.function.IntSupplier readR = () ->
                (reg.getBankGP32(bank, pair) >>> shift) & mask(sz);
            java.util.function.IntConsumer writeR = v -> {
                int field = mask(sz) << shift;
                reg.setBankGP32(bank, pair,
                    (reg.getBankGP32(bank, pair) & ~field) | ((v & mask(sz)) << shift));
            };

            // PUSH R (0x04) / POP R (0x05): stack width follows the operand size.
            if (op2 == 0x04) { pushSized(readR.getAsInt(), sz); cycles += 8; return; }
            if (op2 == 0x05) { writeR.accept(popSized(sz)); cycles += 8; return; }

            // Bit ops on R (0x30=RES,0x31=SET,0x32=CHG,0x33=BIT), bit-number byte follows.
            if (op2 >= 0x30 && op2 <= 0x33) {
                int b = fetch8() & 31; int v = readR.getAsInt();
                switch (op2) {
                    case 0x30 -> writeR.accept(v & ~(1 << b));
                    case 0x31 -> writeR.accept(v |  (1 << b));
                    case 0x32 -> writeR.accept(v ^  (1 << b));
                    case 0x33 -> { reg.setFlagZ(((v >> b) & 1) == 0); reg.setFlagH(true); reg.setFlagN(false); }
                }
                cycles += 4; return;
            }

            // LD reg[op2-0x88], R  (0x88..0x8F): R is the SOURCE.
            if (op2 >= 0x88 && op2 <= 0x8F) { writeReg(op2 - 0x88, readR.getAsInt(), sz); cycles += 4; return; }

            // LD R, reg[op2-0x98]  (0x98..0x9F): R is the DESTINATION.
            if (op2 >= 0x98 && op2 <= 0x9F) { writeR.accept(readReg(op2 - 0x98, sz)); cycles += 4; return; }

            // Arith reg[op2&7], R  (0x80..0x87 ADD, 0x90.. ADC, 0xA0.. SUB, 0xB0.. SBC,
            // 0xC0.. AND, 0xD0.. XOR, 0xE0.. OR, 0xF0.. CP). R is the SECOND operand; the
            // register in the low 3 bits is the destination (asl forbids R as arith dest).
            int base = op2 & 0xF8;
            int aop = switch (base) {
                case 0x80 -> 0; case 0x90 -> 1; case 0xA0 -> 2; case 0xB0 -> 3;
                case 0xC0 -> 4; case 0xD0 -> 5; case 0xE0 -> 6; case 0xF0 -> 7;
                default   -> -1;
            };
            if (aop >= 0) {
                int dst = op2 & 0x07;
                int a = readReg(dst, sz);
                int b = readR.getAsInt();
                int res = doArith(aop, a, b, sz);
                if (aop != 7) writeReg(dst, res, sz);
                cycles += 4; return;
            }

            // Arith R, #imm  (0xC8..0xCF): R op= immediate. Rare, but supported.
            if (op2 >= 0xC8 && op2 <= 0xCF) {
                int imm = (sz == 1) ? se8(fetch8()) : (sz == 2 ? se16(fetch16()) : fetch32());
                int a2 = readR.getAsInt();
                int res = doArith(op2 - 0xC8, a2, imm, sz);
                if ((op2 - 0xC8) != 7) writeR.accept(res);
                cycles += 6; return;
            }
            // CP R,#quick (0xD8..0xDF, n=0..7): flags only, 2 bytes, no trailing imm.
            if (op2 >= 0xD8 && op2 <= 0xDF) {
                int v = readR.getAsInt(); int n = op2 - 0xD8;
                subFlags(v, n, (v - n) & mask(sz), sz); cycles += 4; return;
            }
            // Quick LD R,#n (0xA8..0xB7): n = op2-0xA8 (0..15).
            if (op2 >= 0xA8 && op2 <= 0xB7) { writeR.accept(op2 - 0xA8); cycles += 4; return; }
            // INC/DEC #n,R (0x60..0x6F): byte sets S,Z,H,V,N (C preserved); word/long no flags.
            if (op2 >= 0x60 && op2 <= 0x6F) {
                boolean dec = (op2 & 0x08) != 0; int n = (op2 & 7) == 0 ? 8 : (op2 & 7);
                int v = readR.getAsInt(); int res = (dec ? (v - n) : (v + n)) & mask(sz);
                writeR.accept(res);
                if (sz == 1) { boolean c = reg.flagC(); if (dec) subFlags(v, n, res, sz); else addFlags(v, n, res, sz); reg.setFlagC(c); }
                cycles += 4; return;
            }
            // NEG/CPL/EXTZ/EXTS on R (0x06 CPL, 0x07 NEG, 0x12 EXTZ, 0x13 EXTS).
            if (op2 == 0x07) { int v = readR.getAsInt(); int res = (-v) & mask(sz); writeR.accept(res); subFlags(0, v, res, sz); cycles += 4; return; }
            if (op2 == 0x06) { writeR.accept((~readR.getAsInt()) & mask(sz)); reg.setFlagH(true); reg.setFlagN(true); cycles += 4; return; }
            if (op2 == 0x12) { int hsz = sz / 2; writeR.accept(readR.getAsInt() & mask(hsz)); cycles += 4; return; }              // EXTZ
            if (op2 == 0x13) { int hsz = sz / 2; int v = readR.getAsInt() & mask(hsz); int sv = (v << (32 - hsz * 8)) >> (32 - hsz * 8); writeR.accept(sv & mask(sz)); cycles += 4; return; } // EXTS

            if ((op2 >= 0xE8 && op2 <= 0xEF) || op2 >= 0xF8) {
                int n = shiftCount(op2);
                writeR.accept(doShift(shiftOperation(op2), readR.getAsInt(), n, sz)); cycles+=4; return;
            }
            // LD r, #imm (0x03): immediate width follows the operand size (sz).
            if (op2 == 0x03) {
                int imm = (sz == 1) ? fetch8() : (sz == 2 ? fetch16() : fetch32());
                writeR.accept(imm); cycles += 4; return;
            }
            throw new CpuException(String.format(
                "Unhandled R-op2=0x%02X (bank%d pair%d sz=%d) at PC=%06X", op2, bank, pair, sz, reg.getPC()));
        }

        throw new CpuException(String.format("Invalid register-file address 0x%02X at PC=%06X",
            rfa, reg.getPC()));
    }

    private void execMemOp(int pfx) {
        int addr=ea(pfx);
        int msz=memSize(pfx);
        int op2=fetch8();

        // The op2 sub-opcode map differs between the two prefix families, keyed by the
        // prefix's zz field (bits[5:4]):
        //   zz 0/1/2 = byte/word/long "source-mem" ops (LD R,(mem); ALU R,(mem); ...).
        //   zz 3     = the (0xB0-0xBF / 0xF0-0xFF) group: stores, LD(mem),#imm, LDA,
        //              carry-flag ops, bit ops (RES/SET/CHG/BIT/TSET), and JP/CALL/RET cc.
        // (Both maps verified against asl -L and ngpc_disasm.py's decode_zz_mem/decode_B0_mem.)
        int zz = (pfx & 0x30) >> 4;
        if (zz == 3) { execMemOpZ3(pfx, addr, op2); return; }

        // ── zz 0/1/2 : source-mem group ──────────────────────────────────────────
        if(op2>=0x10 && op2<=0x13){
            int source = (pfx & 15) == 5 ? 5 : 3;
            int destination = source - 1;
            int stride = op2 >= 0x12 ? -msz : msz;
            boolean repeat = (op2 & 1) != 0;
            int transfers = 0;
            // The existing block cost is all-in. Like the C++ core, do not also
            // add ordinary data or active-display waits for its internal accesses.
            boolean oldTimingSuppressed = mem.suppressTiming(true);
            try {
                do {
                    int value = readMem(reg.getDword32(source), msz);
                    writeMem(reg.getDword32(destination), value, msz);
                    reg.setDword32(source, reg.getDword32(source) + stride);
                    reg.setDword32(destination, reg.getDword32(destination) + stride);
                    reg.setBC(reg.getBC() - 1);
                    transfers++;
                } while (repeat && reg.getBC() != 0);
            } finally {
                mem.suppressTiming(oldTimingSuppressed);
            }
            reg.setFlagV(reg.getBC() != 0);
            reg.setFlagH(false);
            reg.setFlagN(false);
            cycles += repeat ? 10 + 14L * transfers : 10;
            return;
        }

        // PUSH (mem): op2=0x04 — push the value at (mem), width from prefix size.
        if(op2==0x04){ pushSized(readMem(addr,msz), msz); cycles+=8; return; }
        // RLD/RRD [A],(mem): op2=0x06/0x07 — nibble rotate through A (byte memory).
        if(op2==0x06){ int m=readMem(addr,1)&0xFF, a=reg.getA()&0xFF;
            int nm=((m<<4)&0xF0)|(a&0x0F); reg.setA((a&0xF0)|((m>>4)&0x0F)); writeMem(addr,nm,1);
            setSZ(reg.getA()&0xFF,1); reg.setFlagH(false); reg.setFlagN(false); cycles+=12; return; }   // RLD
        if(op2==0x07){ int m=readMem(addr,1)&0xFF, a=reg.getA()&0xFF;
            int nm=((a<<4)&0xF0)|((m>>4)&0x0F); reg.setA((a&0xF0)|(m&0x0F)); writeMem(addr,nm,1);
            setSZ(reg.getA()&0xFF,1); reg.setFlagH(false); reg.setFlagN(false); cycles+=12; return; }   // RRD

        // LD reg[r], (ea): 0x20+r  (load value from memory into register; size from prefix)
        if(op2>=0x20&&op2<=0x27){ writeReg(op2-0x20, readMem(addr,msz), msz); cycles+=8; return; }
        // EX (mem), R : 0x30+r  (swap register and memory)
        if(op2>=0x30&&op2<=0x37){ int r2=op2&7; int mv=readMem(addr,msz); writeMem(addr, readReg(r2,msz), msz); writeReg(r2, mv, msz); cycles+=8; return; }

        // op2 0x3C/0x3D/0x3E: logic op on (ea) with a trailing 8-bit immediate.
        //   0x3C: AND (ea), #n    0x3D: XOR (ea), #n    0x3E: OR (ea), #n
        // ALU (mem), #imm : op2 0x38-0x3F = ADD/ADC/SUB/SBC/AND/XOR/OR/CP (mem),#imm.
        // Immediate width follows the prefix size (byte/word/long). CP writes flags only.
        // (asl/ngpc_disasm: 0x38 add … 0x3F cp; e.g. `cp (0x6F85),0` = C1 85 6F 3F 00.)
        if(op2>=0x38&&op2<=0x3F){
            int aop=op2-0x38;                       // 0=ADD..7=CP
            int imm=fetchImm(msz)&mask(msz);
            int v=readMem(addr,msz)&mask(msz);
            int res=doArith(aop, v, imm, msz);
            if(aop!=7) writeMem(addr, res&mask(msz), msz);
            cycles+=8; return;
        }

        // INC #n,(mem) / DEC #n,(mem): op2 0x60-0x67 / 0x68-0x6F, n = (op2&7) or 8.
        // Flags follow the reg-form rule: byte sets S,Z,H,V,N with C preserved; word/long
        // set no flags. (Same classification as reg-form INC/DEC — see memory note.)
        if(op2>=0x60&&op2<=0x6F){
            boolean dec=(op2&0x08)!=0; int n=(op2&7)==0?8:(op2&7);
            int v=readMem(addr,msz); int res=(dec?(v-n):(v+n))&mask(msz);
            writeMem(addr,res,msz);
            if(msz==1){ boolean c=reg.flagC(); if(dec) subFlags(v,n,res,msz); else addFlags(v,n,res,msz); reg.setFlagC(c); }
            cycles+=8; return;
        }

        // Rotate/shift (mem) by 1: op2 0x78-0x7F = RLC/RRC/RL/RR/SLA/SRA/SLL/SRL, count 1.
        if(op2>=0x78&&op2<=0x7F){
            int[] map={3,4,5,6,0,1,7,2}; // 78=RLC,79=RRC,7A=RL,7B=RR,7C=SLA,7D=SRA,7E=SLL,7F=SRL
            writeMem(addr, doShift(map[op2-0x78], readMem(addr,msz), 1, msz), msz); cycles+=8; return;
        }

        // Arithmetic R[r], (ea): op2 = 0x80/0x90/0xA0/0xB0/0xC0/0xD0/0xE0/0xF0 + r.
        // R,(mem) SOURCE direction (reg = dst, (mem) = 2nd operand); all sizes.
        //   0x80=ADD 0x90=ADC 0xA0=SUB 0xB0=SBC 0xC0=AND 0xD0=XOR 0xE0=OR 0xF0=CP.
        // (asl: `add wa,(xhl)`=93 80, `or xwa,(xhl)`=A3 E0, `cp xwa,(xhl)`=A3 F0.)
        if(op2>=0x80 && (op2&0x08)==0){
            int aop=(op2>>4)-0x8; int r2=op2&7;
            int a=readReg(r2,msz), b=readMem(addr,msz), res=doArith(aop,a,b,msz);
            if(aop!=7) writeReg(r2,res,msz);
            cycles+=10; return;
        }
        // Arithmetic (mem) op= R : op2 = 0x88/0x98/.../0xF8 + r  (mem = dst, R = src).
        // (asl: `add (xsp+4),wa`=9F 04 88, `cp (xsp+4),wa`=..F8.)
        if(op2>=0x88 && (op2&0x08)!=0 && op2<=0xFF){
            int aop=(op2>>4)-0x8; int r2=op2&7;
            int a=readMem(addr,msz), b=readReg(r2,msz), res=doArith(aop,a,b,msz);
            if(aop!=7) writeMem(addr,res,msz);
            cycles+=10; return;
        }

        throw new CpuException(String.format("Unhandled mem-op2=0x%02X (pfx=0x%02X zz=%d addr=0x%06X) at PC=%06X",op2,pfx,zz,addr,reg.getPC()));
    }

    // zz=3 memory group: prefixes 0xB0-0xBF and 0xF0-0xFF. addr already computed.
    // op2 map (verified against asl -L and ngpc_disasm.py decode_B0_mem):
    //   0x00 LD(mem),#imm8   0x02 LDW(mem),#imm16   0x04 POP(mem)   0x06 POPW(mem)
    //   0x20-0x27 LDA R16,(mem)   0x30-0x37 LDA R32,(mem)
    //   0x28-0x2C ANDCF/ORCF/XORCF/LDCF/STCF A,(mem)
    //   0x40-0x47 LD(mem),R8   0x50-0x57 LDW(mem),R16   0x60-0x67 LD(mem),R32
    //   0x80-0xA7 ANDCF/ORCF/XORCF/LDCF/STCF #b,(mem)   0xA8-0xAF TSET #b,(mem)
    //   0xB0-0xB7 RES   0xB8-0xBF SET   0xC0-0xC7 CHG   0xC8-0xCF BIT
    //   0xD0-0xDF JP cc,(mem)   0xE0-0xEF CALL cc,(mem)   0xF0-0xFF RET cc (B0 form)
    private void execMemOpZ3(int pfx, int addr, int op2) {
        // RET cc — encoded only under prefix 0xB0, op2 0xF0+cc (addr is unused).
        if(pfx==0xB0 && op2>=0xF0){
            int cci=op2&0xF;
            if(cci==8 || cc(cci)) reg.setPC(popPC());
            cycles+=10; return;
        }
        // Stores of immediates
        if(op2==0x00){ mem.writeByte(addr, fetch8()&0xFF); cycles+=8; return; }       // LD (mem),#imm8
        if(op2==0x02){ mem.writeWord(addr, fetch16()&0xFFFF); cycles+=10; return; }    // LDW (mem),#imm16
        if(op2==0x04){ mem.writeWord(addr, pop16()); cycles+=8; return; }             // POP (mem)
        if(op2==0x06){ mem.writeWord(addr, pop16()); cycles+=8; return; }             // POPW (mem)

        // LDA R,(mem) — load effective address (not the value)
        if(op2>=0x20&&op2<=0x27){ reg.setWord16(op2-0x20, addr&0xFFFF); cycles+=8; return; }   // LDA R16
        if(op2>=0x30&&op2<=0x37){ reg.setDword32(op2-0x30, addr); cycles+=8; return; }         // LDA R32

        // Carry-flag ops on A / immediate bit
        if(op2>=0x28&&op2<=0x2C){ carryOp(op2-0x28, addr, reg.getA()&7, memSize(pfx)); cycles+=6; return; }
        if(op2>=0x80&&op2<=0xA7){ carryOp((op2>>3)-0x10, addr, op2&7, memSize(pfx)); cycles+=6; return; }

        // Stores of registers to (mem)
        if(op2>=0x40&&op2<=0x47){ mem.writeByte(addr, readReg(op2-0x40,1)); cycles+=8; return; }        // LD (mem),R8
        if(op2>=0x50&&op2<=0x57){ mem.writeWord(addr, readReg(op2-0x50,2)); cycles+=8; return; }        // LDW (mem),R16
        if(op2>=0x60&&op2<=0x67){ mem.writeDword(addr, readReg(op2-0x60,4)); cycles+=10; return; }      // LD (mem),R32

        int msz=memSize(pfx);
        // TSET #b,(mem): test bit then set it (Z = !bit)
        if(op2>=0xA8&&op2<=0xAF){ int b=op2&7; int v=readMem(addr,msz);
            reg.setFlagZ(((v>>b)&1)==0); reg.setFlagH(true); reg.setFlagN(false);
            writeMem(addr, v|(1<<b), msz); cycles+=8; return; }
        // Bit ops #b,(mem)
        if(op2>=0xB0&&op2<=0xB7){ int b=op2&7; writeMem(addr, readMem(addr,msz)&~(1<<b), msz); cycles+=8; return; } // RES
        if(op2>=0xB8&&op2<=0xBF){ int b=op2&7; writeMem(addr, readMem(addr,msz)| (1<<b), msz); cycles+=8; return; } // SET
        if(op2>=0xC0&&op2<=0xC7){ int b=op2&7; writeMem(addr, readMem(addr,msz)^ (1<<b), msz); cycles+=8; return; } // CHG
        if(op2>=0xC8&&op2<=0xCF){ int b=op2&7; int v=readMem(addr,msz);                                             // BIT
            reg.setFlagZ(((v>>b)&1)==0); reg.setFlagH(true); reg.setFlagN(false); cycles+=8; return; }

        // JP cc,(mem): addr is the branch target
        if(op2>=0xD0&&op2<=0xDF){ int cci=op2&0xF; if(cci==8||cc(cci)) reg.setPC(addr&0xFFFFFF); cycles+=8; return; }
        // CALL cc,(mem): addr is the target
        if(op2>=0xE0&&op2<=0xEF){ int cci=op2&0xF; if(cci==8||cc(cci)){ pushPC(); reg.setPC(addr&0xFFFFFF); } cycles+=12; return; }

        throw new CpuException(String.format("Unhandled zz3 mem-op2=0x%02X (pfx=0x%02X addr=0x%06X) at PC=%06X",op2,pfx,addr,reg.getPC()));
    }

    // Carry-flag bit operations. op: 0=ANDCF,1=ORCF,2=XORCF,3=LDCF,4=STCF. bit b of (mem).
    private void carryOp(int op, int addr, int b, int msz){
        int v=readMem(addr,msz); int bit=(v>>b)&1;
        switch(op){
            case 0 -> reg.setFlagC(reg.flagC() && bit!=0);      // ANDCF
            case 1 -> reg.setFlagC(reg.flagC() || bit!=0);      // ORCF
            case 2 -> reg.setFlagC(reg.flagC() ^ (bit!=0));     // XORCF
            case 3 -> reg.setFlagC(bit!=0);                     // LDCF: C <- bit
            case 4 -> writeMem(addr, reg.flagC()?(v|(1<<b)):(v&~(1<<b)), msz); // STCF: bit <- C
        }
    }

    // ── DAA ───────────────────────────────────────────────────────────────────
    private void execDAA() {
        int a=reg.getA();
        if(!reg.flagN()){
            if(reg.flagH()||(a&0xF)>9) a+=6;
            if(reg.flagC()||a>0x99){ a+=0x60; reg.setFlagC(true); }
        } else {
            if(reg.flagH()) a-=6;
            if(reg.flagC()) a-=0x60;
        }
        a&=0xFF; reg.setA(a); setSZ(a,1); reg.setFlagV((Integer.bitCount(a)&1)==0);
        cycles+=4;
    }

    // ── NORMAL prefix (0x01) ──────────────────────────────────────────────────
    private void execNormal() {
        int op2=fetch8();
        // LDAR xrr, PC+d16 (0x00..0x07 = XWA..XSP)
        if(op2<=0x07){ int d=se16(fetch16()); reg.setDword32(op2,(reg.getPC()+d)&0xFFFFFF); cycles+=8; return; }
        // DAA / EXTZ / EXTS
        if(op2==0x10){ execDAA(); return; }
        if(op2>=0x12&&op2<=0x13){
            // EXTZ/EXTS for special registers
            cycles+=4; return;
        }
        // SWI via NORMAL prefix: 0x01 (F8+n) = SWI n
        if(op2>=0xF8){ execSWI(op2-0xF8); return; }
        // Unknown NORMAL sub-opcodes: treat as NOP
        cycles+=4;
    }

    // ── SWI ───────────────────────────────────────────────────────────────────
    private void execSWI(int n) {
        if(swiHandler!=null) swiHandler.handle(n,reg,mem);
        cycles+=16;
    }

    // ── Main decode ───────────────────────────────────────────────────────────
    public void step() {
        if(halted) return;
        long cyclesBefore = cycles;
        mem.beginInstruction(cyclesBefore);
        try {
        int startPC=reg.getPC();
        mem.setInstructionAddress(startPC);
        int op=fetch8();
        if(trace) System.out.printf("[%06X] op=%02X  %s%n",startPC,op,reg);

        // Memory prefix: 0x80-0xE7 (excl C8-CF=byte_reg, D8-DF=word_reg, E8-EF=dword_reg) + 0xF0-0xFF
        // NOTE: 0xB0 = (XWA) byte-indirect prefix and 0xB4 = (XIX) byte-indirect prefix are
        // ordinary memory prefixes routed through execMemOp; RET cc / CALL cc are op2
        // sub-opcodes there (RET cc = op2 0xF0+cc, CALL cc = op2 0xE0+cc), not top-level
        // opcodes. The earlier B0=RET-cc / B4=CALL-reg special cases were wrong and broke
        // every real (XWA)/(XIX) memory access cc900 emits.
        // "R" register-file-direct prefix, byte/word/dword = C7/D7/E7 <regfile-addr> <op2>.
        // The operand selects a GP/index register and lane, NOT a memory address.
        if(op==0xC7){ execRegFileDirectOp(1); return; }
        if(op==0xD7){ execRegFileDirectOp(2); return; }
        if(op==0xE7){ execRegFileDirectOp(4); return; }
        // SWI n: 0xF8..0xFF (1 byte). Must be tested before the 0xF0+ memory-prefix
        // range below, which only covers 0xF0..0xF7 (SP/index address prefixes).
        if(op>=0xF8){ execSWI(op-0xF8); return; }
        if((op>=0x80&&op<0xC8)||(op>=0xD0&&op<0xD8)||(op>=0xE0&&op<0xE8)||(op>=0xF0&&op<0xF8)){
            execMemOp(op); return;
        }

        // Register prefix: C8-EF
        if(op>=0xC8&&op<=0xEF){ execRegOp(op&7, op<=0xCF?1:op<=0xDF?2:4); return; }

        // Standalone opcodes
        switch(op) {
            case 0x00 -> cycles+=4;                                               // NOP
            case 0x01 -> execNormal();                                            // NORMAL prefix
            case 0x02 -> { push16(reg.getSR()); cycles+=8; }                      // PUSH SR
            case 0x03 -> { reg.setSR(pop16()); cycles+=8; }                       // POP SR
            case 0x04 -> cycles+=4;                                               // MIN (ignored, we run in MAX mode)
            case 0x05 -> { halted=true; cycles+=4; }                              // HALT
            case 0x06 -> { fetch8(); cycles+=8; }                                 // EI n / DI
            case 0x07 -> { reg.setPC(popPC()); reg.setSR(pop16()); cycles+=12; }  // RETI
            // 0x08-0x0D: LD (n8),imm / PUSH imm / INCF / DECF. Verified against asl -L
            // listings (as/bin/asl.exe) and ngpc_disasm.py: the transpiler never emits
            // these forms (it assembles `add xsp,N` as EF C8 <imm32>, and uses no raw
            // push-imm), so the earlier ADD-XSP/PUSH-(n16) decodes were dead guesses.
            // cc900 emits pushw/incf/decf heavily.
            case 0x08 -> { int a=fetch8()&0xFF; int v=fetch8(); mem.writeByte(a,v); cycles+=8; } // LDB (n8), i8
            case 0x09 -> { int v=fetch8()&0xFF; push16(v); cycles+=6; }           // PUSH #imm8: WORD push (XSP-=2), imm zero-extended. cc900's stack discipline (bomberman) requires SP-=2; 09/0B differ only in immediate width, both push a word.
            case 0x0A -> { int a=fetch8()&0xFF; int v=fetch16()&0xFFFF; mem.writeWord(a,v); cycles+=10; } // LDW (n8), i16
            case 0x0B -> { int v=fetch16()&0xFFFF; push16(v); cycles+=8; }        // PUSHW #imm16
            case 0x0C -> { reg.setBank((reg.bank()+1)&3); cycles+=4; }            // INCF: RFP+1 (RFP2 fixed 0)
            case 0x0D -> { reg.setBank((reg.bank()-1)&3); cycles+=4; }            // DECF: RFP-1
            case 0x0E -> { reg.setPC(popPC()); cycles+=12; }                      // RET
            case 0x0F -> { int d=se16(fetch16()); int ret=popPC(); reg.setXSP(reg.getXSP()+d); reg.setPC(ret); cycles+=12; } // RETD d16
            // 0x10-0x1F: misc CPU control (confirmed + PDF-derived)
            case 0x10 -> cycles+=4;                                               // NOP alt / reserved
            case 0x11 -> cycles+=4;                                               // NOP alt / reserved
            case 0x12 -> { reg.setFlagC(false); cycles+=4; }                     // RCF
            case 0x13 -> { reg.setFlagC(true);  cycles+=4; }                     // SCF
            case 0x14 -> { reg.setFlagC(!reg.flagC()); cycles+=4; }              // CCF
            case 0x15 -> { reg.setFlagC(reg.flagZ());  cycles+=4; }              // ZCF
            case 0x16 -> { push16(reg.getA() | (reg.getF() << 8)); cycles+=6; } // PUSH A (or PUSH AF)
            case 0x17 -> { reg.setBank(fetch8()&3); cycles+=4; }                  // LDF n
            case 0x18 -> cycles+=4;                                               // EX F,F' (no shadow regs — NOP)
            case 0x19 -> { reg.setF(pop8()); cycles+=6; }                         // POP F (or alternate)
            case 0x1A -> { reg.setPC(fetch16()&0xFFFF); cycles+=8; }              // JP #16
            case 0x1B -> { reg.setPC(fetch24()); cycles+=8; }                     // JP #24
            case 0x1C -> { int a=fetch16()&0xFFFF; pushPC(); reg.setPC(a); cycles+=14; } // CALL #16
            case 0x1D -> { int a=fetch24(); pushPC(); reg.setPC(a); cycles+=14; }  // CALL #24
            case 0x1E -> { int d=se16(fetch16()); pushPC(); reg.addPC(d); cycles+=14; } // CALR d16
            case 0x1F -> { reg.setPC(reg.getXSP()&0xFFFFFF); cycles+=6; }        // JP (XSP)
            default   -> {
                // LD byte_reg[r], #n  (0x20..0x27)
                if(op>=0x20&&op<=0x27){ reg.setByteReg(op-0x20, fetch8()); cycles+=6; return; }
                // PUSH word_reg[r] (0x28..0x2F)
                if(op>=0x28&&op<=0x2F){ push16(reg.getWord16(op-0x28)); cycles+=6; return; }
                // LD word_reg[r], #d16 (0x30..0x37)
                if(op>=0x30&&op<=0x37){ reg.setWord16(op-0x30, fetch16()); cycles+=8; return; }
                // PUSH dword_reg[r] (0x38..0x3F)
                if(op>=0x38&&op<=0x3F){ push32(reg.getDword32(op-0x38)); cycles+=8; return; }
                // LD dword_reg[r], #d32 (0x40..0x47)
                if(op>=0x40&&op<=0x47){ reg.setDword32(op-0x40, fetch32()); cycles+=12; return; }
                // POP word_reg[r] (0x48..0x4F)
                if(op>=0x48&&op<=0x4F){ reg.setWord16(op-0x48, pop16()); cycles+=6; return; }
                // SWI n (0x50..0x57)
                if(op>=0x50&&op<=0x57){ execSWI(op-0x50); return; }
                // POP dword_reg[r] (0x58..0x5F)
                if(op>=0x58&&op<=0x5F){ reg.setDword32(op-0x58, pop32()); cycles+=8; return; }
                // JR cc, d8 (0x60..0x6F)
                if(op>=0x60&&op<=0x6F){
                    int d=se8(fetch8());
                    if(cc(op-0x60)){ reg.addPC(d); cycles+=8; } else cycles+=4;
                    return;
                }
                // JRL cc, d16 (0x70..0x7F)
                if(op>=0x70&&op<=0x7F){
                    int d=se16(fetch16());
                    if(cc(op-0x70)){ reg.addPC(d); cycles+=10; } else cycles+=6;
                    return;
                }
                throw new CpuException(String.format("Unknown opcode 0x%02X at PC=0x%06X",op,startPC));
            }
        }
        } finally {
            long instructionStates = cycles - cyclesBefore;
            cycles = cyclesBefore
                + instructionStates * NgpcTiming.INSTRUCTION_STATE_SCALE
                + mem.endInstruction();
        }
    }

    public static class CpuException extends RuntimeException {
        public CpuException(String m) { super(m); }
    }
}
