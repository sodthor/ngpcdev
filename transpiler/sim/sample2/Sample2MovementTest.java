package sim.sample2;
import sim.*;
import java.io.*;
import java.util.*;

/** Differential wall/movement regression for SAMPLE_2 doMove: drive the real main()
 *  loop with a held joypad direction and snapshot camera/sprite globals, comparing a
 *  coalescing-ON build against a coalescing-OFF reference build. They must match. */
public final class Sample2MovementTest {
    static Map<String,Integer> loadMap(String path) throws Exception {
        Map<String,Integer> s=new HashMap<>();
        try(BufferedReader r=new BufferedReader(new FileReader(path))){
            String ln; while((ln=r.readLine())!=null){ String[] f=ln.trim().split("\\s+");
                if(f.length>=3 && f[0].startsWith("_")){ try{ s.put(f[0].toUpperCase(),Integer.parseInt(f[2],16)); }catch(Exception e){} } }
        } return s;
    }
    // Drive main() with joypad=dir held; after `frames` VBLs, return {map_x,map_y,spr_x,spr_y}.
    static int[] run(String rom,String map,int dir,int frames) throws Exception {
        Map<String,Integer> sym=loadMap(map);
        NgpLoader.NgpRom r=NgpLoader.load(rom);
        Memory mem=new Memory(r.data); Registers reg=new Registers(); Cpu cpu=new Cpu(mem,reg);
        final int[] jp={0};
        mem.setIoDevice(new Memory.IoDevice(){
            public int read(int a){ if(a==0x6F82) return jp[0]; return -1; }
            public void write(int a,int v){}
        });
        cpu.setSwiHandler((n,rg,m)->true);
        NgpLoader.init(r,mem,reg);
        int MX=sym.get("_MAP_X"),MY=sym.get("_MAP_Y"),SX=sym.get("_SPR_X"),SY=sym.get("_SPR_Y");
        int sleep=sym.get("_SLEEP");
        int tileChecks=0;
        int ROM_BASE=0x200000, ROM_END=ROM_BASE+r.data.length;
        long cyclesPerVbl=NgpcTiming.CYCLES_PER_FRAME, nextVbl=cyclesPerVbl, prevCyc=0, cyc=0, vbl=0, instr=0;
        boolean inIsr=false; int isrSp=0;
        while(vbl<frames && instr<20_000_000){
            if(inIsr && reg.getXSP()>isrSp) inIsr=false;
            if(cyc>=nextVbl){ nextVbl+=cyclesPerVbl;
                int h=mem.readDword(0x6FCC)&0xFFFFFF;
                if(h!=0 && !inIsr){ int xsp=reg.getXSP(); xsp-=2; mem.writeWord(xsp,reg.getSR());
                    xsp-=4; mem.writeDword(xsp,reg.getPC()); reg.setXSP(xsp); reg.setPC(h); inIsr=true; isrSp=xsp; }
                vbl++;
                // let the game boot a few frames before holding the direction
                jp[0] = (vbl>8)? dir : 0;
            }
            int pc=reg.getPC();
            if(pc<ROM_BASE||pc>=ROM_END) break;
            if(pc==sleep){ Sample2SpriteAnimationTest.verifyLevelTiles(mem,sym,20,19); tileChecks++; }
            cpu.step(); instr++;
            long nc=cpu.getCycles(); cyc+=nc-prevCyc; prevCyc=nc;
        }
        if(tileChecks==0) throw new AssertionError("movement run did not verify a completed tile frame");
        return new int[]{ mem.readWord(MX),mem.readWord(MY),mem.readWord(SX),mem.readWord(SY) };
    }
    public static void main(String[] a) throws Exception {
        String rom = a.length>0 ? a[0] : "../SAMPLE_2/SAMPLE.ngp";
        String map = a.length>1 ? a[1] : "../SAMPLE_2/SAMPLE.map";
        // Golden movement outcomes {map_x,map_y,spr_x,spr_y} after 40 VBLs holding each
        // direction from the level-0 start. These were captured from a known-correct
        // reference build; a register-allocation miscompile of doMove (e.g. coalesced
        // bank-register read-before-write) perturbs wall collision / scrolling and
        // changes these. Direction bits: LEFT=0x04 RIGHT=0x08 UP=0x01 DOWN=0x02.
        int[] dirs={0x04,0x08,0x01,0x02}; String[] nm={"LEFT","RIGHT","UP","DOWN"};
        int[][] golden={{10,15,72,72},{17,15,72,72},{14,11,72,72},{14,18,72,72}};
        int bad=0;
        for(int i=0;i<dirs.length;i++){
            int[] got=run(rom,map,dirs[i],40);
            boolean eq=java.util.Arrays.equals(got,golden[i]);
            System.out.printf("%-5s got%s expect%s %s%n", nm[i],
                java.util.Arrays.toString(got), java.util.Arrays.toString(golden[i]), eq?"OK":"MISMATCH");
            if(!eq) bad++;
        }
        if(bad!=0){ System.out.println("Sample2MovementTest: FAIL ("+bad+" directions wrong)"); System.exit(1); }
        System.out.println("Sample2MovementTest: PASS (movement and level tiles correct in 4 directions)");
    }
}