package sim;

/**
 * TLCS-900/H register file (maximum mode, 4 banks of 32-bit registers).
 *
 * Register naming (current bank):
 *   XWA (32-bit) -> WA (16-bit low word) -> W (bits 15-8), A (bits 7-0)
 *   XBC           -> BC                   -> B (bits 15-8), C (bits 7-0)
 *   XDE           -> DE                   -> D (bits 15-8), E (bits 7-0)
 *   XHL           -> HL                   -> H (bits 15-8), L (bits 7-0)
 *   XIX, XIY, XIZ, XSP  (index / stack pointer, 32-bit each)
 *
 * Register prefix byte encoding (C8+zz+r):
 *   zz=0x00: byte registers  -> C8..CF: W,A,B,C,D,E,H,L
 *   zz=0x10: word registers  -> D8..DF: WA,BC,DE,HL,IX,IY,IZ,SP
 *   zz=0x20: dword registers -> E8..EF: XWA,XBC,XDE,XHL,XIX,XIY,XIZ,XSP
 *
 * SR bits: [15:11]=unused, [10:8]=RFP (register file pointer = current bank 0-3),
 *          [7:0]=F (flags: S Z - H - V N C)
 */
public class Registers {

    // 4 banks × 4 general-purpose dword registers
    // index: 0=XWA, 1=XBC, 2=XDE, 3=XHL
    private final int[][] gp = new int[4][4];

    // Index registers (shared across banks)
    private int xix, xiy, xiz, xsp;

    private int pc;  // 24-bit
    private int sr;  // 16-bit: [10:8]=bank [7:0]=F

    // ---- current bank (from SR bits 9:8) ----
    public int bank() { return (sr >> 8) & 3; }
    public void setBank(int b) { sr = (sr & 0xFCFF) | ((b & 3) << 8); }

    // ---- 32-bit general registers (current bank) ----
    public int  getXWA() { return gp[bank()][0]; }
    public int  getXBC() { return gp[bank()][1]; }
    public int  getXDE() { return gp[bank()][2]; }
    public int  getXHL() { return gp[bank()][3]; }
    public void setXWA(int v) { gp[bank()][0] = v; }
    public void setXBC(int v) { gp[bank()][1] = v; }
    public void setXDE(int v) { gp[bank()][2] = v; }
    public void setXHL(int v) { gp[bank()][3] = v; }

    // 32-bit by index (r=0..3)
    public int  getGP32(int r) { return gp[bank()][r & 3]; }
    public void setGP32(int r, int v) { gp[bank()][r & 3] = v; }

    // ---- Absolute-bank general registers (independent of current bank) ----
    // Used by the "R" register-file-direct addressing mode, where asl names a
    // register in a *specific* bank (e.g. xwa1 = bank1 WA, xbc2 = bank2 BC).
    // The register-file address byte encodes bank*0x10 + pair*4; pair 0..3 = WA/BC/DE/HL.
    public int  getBankGP32(int bank, int pair) { return gp[bank & 3][pair & 3]; }
    public void setBankGP32(int bank, int pair, int v) { gp[bank & 3][pair & 3] = v; }

    public int  getBankGP16(int bank, int pair) { return gp[bank & 3][pair & 3] & 0xFFFF; }
    public void setBankGP16(int bank, int pair, int v) {
        gp[bank & 3][pair & 3] = (gp[bank & 3][pair & 3] & 0xFFFF0000) | (v & 0xFFFF);
    }

    // 8-bit view: byteSel 0 = low byte (A/C/E/L), 1 = high byte (W/B/D/H).
    public int getBankGP8(int bank, int pair, boolean hi) {
        int word = gp[bank & 3][pair & 3];
        return hi ? (word >> 8) & 0xFF : word & 0xFF;
    }
    public void setBankGP8(int bank, int pair, boolean hi, int v) {
        v &= 0xFF;
        if (hi) gp[bank & 3][pair & 3] = (gp[bank & 3][pair & 3] & 0xFFFF00FF) | (v << 8);
        else    gp[bank & 3][pair & 3] = (gp[bank & 3][pair & 3] & 0xFFFFFF00) | v;
    }

    // ---- 16-bit (low word) ----
    public int  getWA() { return gp[bank()][0] & 0xFFFF; }
    public int  getBC() { return gp[bank()][1] & 0xFFFF; }
    public int  getDE() { return gp[bank()][2] & 0xFFFF; }
    public int  getHL() { return gp[bank()][3] & 0xFFFF; }
    public void setWA(int v) { gp[bank()][0] = (gp[bank()][0] & 0xFFFF0000) | (v & 0xFFFF); }
    public void setBC(int v) { gp[bank()][1] = (gp[bank()][1] & 0xFFFF0000) | (v & 0xFFFF); }
    public void setDE(int v) { gp[bank()][2] = (gp[bank()][2] & 0xFFFF0000) | (v & 0xFFFF); }
    public void setHL(int v) { gp[bank()][3] = (gp[bank()][3] & 0xFFFF0000) | (v & 0xFFFF); }

    public int  getGP16(int r) { return gp[bank()][r & 3] & 0xFFFF; }
    public void setGP16(int r, int v) {
        gp[bank()][r & 3] = (gp[bank()][r & 3] & 0xFFFF0000) | (v & 0xFFFF);
    }

    // ---- 8-bit (A=lo,W=hi pattern; E=lo of DE, etc.) ----
    // Byte register map for prefix C8+r (r=0..7): W,A,B,C,D,E,H,L
    // W=hi of XWA, A=lo of XWA, B=hi of XBC, C=lo, D=hi of XDE, E=lo, H=hi of XHL, L=lo
    public int getByteReg(int r) {
        r &= 7;
        int pair = r >> 1;       // 0=WA, 1=BC, 2=DE, 3=HL
        boolean hi = (r & 1) == 0; // even index = high byte
        int word = gp[bank()][pair];
        return hi ? (word >> 8) & 0xFF : word & 0xFF;
    }
    public void setByteReg(int r, int v) {
        r &= 7; v &= 0xFF;
        int pair = r >> 1;
        boolean hi = (r & 1) == 0;
        if (hi) gp[bank()][pair] = (gp[bank()][pair] & 0xFFFF00FF) | (v << 8);
        else    gp[bank()][pair] = (gp[bank()][pair] & 0xFFFFFF00) | v;
    }

    // Convenience single-register accessors
    public int  getA() { return gp[bank()][0] & 0xFF; }
    public int  getW() { return (gp[bank()][0] >> 8) & 0xFF; }
    public int  getB() { return (gp[bank()][1] >> 8) & 0xFF; }
    public int  getC() { return gp[bank()][1] & 0xFF; }
    public int  getD() { return (gp[bank()][2] >> 8) & 0xFF; }
    public int  getE() { return gp[bank()][2] & 0xFF; }
    public int  getH() { return (gp[bank()][3] >> 8) & 0xFF; }
    public int  getL() { return gp[bank()][3] & 0xFF; }
    public void setA(int v) { setByteReg(1, v); }
    public void setW(int v) { setByteReg(0, v); }
    public void setB(int v) { setByteReg(2, v); }
    public void setC(int v) { setByteReg(3, v); }
    public void setD(int v) { setByteReg(4, v); }
    public void setE(int v) { setByteReg(5, v); }
    public void setH(int v) { setByteReg(6, v); }
    public void setL(int v) { setByteReg(7, v); }

    // ---- Index / SP ----
    public int  getXIX() { return xix; }
    public int  getXIY() { return xiy; }
    public int  getXIZ() { return xiz; }
    public int  getXSP() { return xsp; }
    public void setXIX(int v) { xix = v; }
    public void setXIY(int v) { xiy = v; }
    public void setXIZ(int v) { xiz = v; }
    public void setXSP(int v) { xsp = v; }

    // 16-bit view of index regs (low word)
    public int getIX() { return xix & 0xFFFF; }
    public int getIY() { return xiy & 0xFFFF; }
    public int getIZ() { return xiz & 0xFFFF; }
    public int getSP() { return xsp & 0xFFFF; }

    // Get/set 16/32-bit word/dword register by r=0..7
    // r=0..3 = WA/BC/DE/HL (gp), r=4..7 = IX/IY/IZ/SP
    public int getWord16(int r) {
        r &= 7;
        if (r < 4) return gp[bank()][r] & 0xFFFF;
        return switch (r) { case 4->getIX(); case 5->getIY(); case 6->getIZ(); default->getSP(); };
    }
    public void setWord16(int r, int v) {
        r &= 7; v &= 0xFFFF;
        if (r < 4) { gp[bank()][r] = (gp[bank()][r] & 0xFFFF0000) | v; return; }
        switch (r) {
            case 4: xix = (xix & 0xFFFF0000) | v; break;
            case 5: xiy = (xiy & 0xFFFF0000) | v; break;
            case 6: xiz = (xiz & 0xFFFF0000) | v; break;
            case 7: xsp = (xsp & 0xFFFF0000) | v; break;
        }
    }
    public int getDword32(int r) {
        r &= 7;
        if (r < 4) return gp[bank()][r];
        return switch (r) { case 4->xix; case 5->xiy; case 6->xiz; default->xsp; };
    }
    public void setDword32(int r, int v) {
        r &= 7;
        if (r < 4) { gp[bank()][r] = v; return; }
        switch (r) {
            case 4: xix = v; break; case 5: xiy = v; break;
            case 6: xiz = v; break; case 7: xsp = v; break;
        }
    }

    // ---- PC ----
    public int  getPC()    { return pc & 0xFFFFFF; }
    public void setPC(int v) { pc = v & 0xFFFFFF; }
    public void addPC(int d) { pc = (pc + d) & 0xFFFFFF; }

    // ---- SR ----
    public int  getSR()     { return sr & 0xFFFF; }
    public void setSR(int v) { sr = v & 0xFFFF; }

    // ---- Flags (low byte of SR) ----
    public int  getF()       { return sr & 0xFF; }
    public void setF(int v)  { sr = (sr & 0xFF00) | (v & 0xFF); }

    public boolean flagS() { return (sr & 0x80) != 0; }
    public boolean flagZ() { return (sr & 0x40) != 0; }
    public boolean flagH() { return (sr & 0x10) != 0; }
    public boolean flagV() { return (sr & 0x04) != 0; }
    public boolean flagN() { return (sr & 0x02) != 0; }
    public boolean flagC() { return (sr & 0x01) != 0; }

    public void setFlagS(boolean b) { if(b) sr|=0x80; else sr&=~0x80; }
    public void setFlagZ(boolean b) { if(b) sr|=0x40; else sr&=~0x40; }
    public void setFlagH(boolean b) { if(b) sr|=0x10; else sr&=~0x10; }
    public void setFlagV(boolean b) { if(b) sr|=0x04; else sr&=~0x04; }
    public void setFlagN(boolean b) { if(b) sr|=0x02; else sr&=~0x02; }
    public void setFlagC(boolean b) { if(b) sr|=0x01; else sr&=~0x01; }

    @Override
    public String toString() {
        return String.format("PC=%06X SP=%08X bank=%d F=%02X  XWA=%08X XBC=%08X XDE=%08X XHL=%08X  XIX=%08X",
            pc, xsp, bank(), getF(),
            gp[bank()][0], gp[bank()][1], gp[bank()][2], gp[bank()][3], xix);
    }
}
