import java.io.*;

class ModConverter
{

  static final int DEF_SPEED = 4096/6;

  static int periods[] =
  {
    1712,1616,1524,1440,1356,1280,1208,1140,1076,1016,960,906,	// octave 0
    856,808,762,720,678,640,604,570,538,508,480,453,
    428,404,381,360,339,320,302,285,269,254,240,226,
    214,202,190,180,170,160,151,143,135,127,120,113,
    107,101,95,90,85,80,75,71,67,63,60,56			// octave 5
  };

  static class SampData
  {
    byte[] name = new byte[22];
    int sampllen;
    int finetune;
    int sampvol;
    int repeatpoint;
    int repeatlen;
  }

  static int getByte(byte b)
  {
    return b<0?256+b:b;
  }

  static int getShort(byte l,byte h)
  {
    return getByte(l)+256*getByte(h);
  }

  static int readShort(InputStream is) throws Exception
  {
    return is.read()*256+is.read();
  }

  static void copy(int[] dst,int[] src)
  {
    for (int i=0;i<dst.length;i++)
        dst[i] = src[i];
  }

  static int readPattern(byte[] pattern,int[][][] result,int[] corr,int[] defvol) throws Exception
  {
    int i = 0,speed = DEF_SPEED,jump=-1;
    int[] vols = new int[] {0,0,0,0};
    int[] lastSample = new int[] {99,99,99,99};

    for (int Row = 0; Row < 64; Row++)
    {
        for (int Chan = 0; Chan < 4; Chan++)
        {
            int command,note,octave,period,sample,data0,data1;
            period = getShort(pattern[i+1],pattern[i])&0xfff;
	    if (period>0)
            {
               for (note=0;note<60 && period<periods[note];note++);
               octave = note/12;
               note = note%12;
               sample = (getByte(pattern[i]) & 0xf0)|(getByte(pattern[i+2])>>4);
               if (sample==0)
                  sample=lastSample[Chan];
               else
                  lastSample[Chan] = sample;
               vols[Chan] = defvol[corr[sample-1]];
            }
            else
            {
               note=99;
               octave=99;
               sample=99;
            }
            command = getByte(pattern[i+2])&0x0f;
            data0 = getByte(pattern[i+3])>>4;
            data1 = getByte(pattern[i+3])&0x0f;
            switch(command)
            {
              case 0: // Arpegio
                break;
              case 1: // Slide up
              case 2: // Slide down
              case 3: // Slide to note
              case 4: // Vibrato
              case 5: // Continue slide to note  but also volume slide
              case 6: // Continue vibrato  but also volume slide
              case 7: // Tremolo
              case 8: // unused
              case 9: // Set sample offset
              case 10: // Volume slide
              case 11: // Position jump
                jump=data0*16+data1;
                break;
              case 12: // Set volume
                vols[Chan] = data0*16+data1;
                break;
              case 13: // Pattern break
                break;
              case 14:
                switch(data0)
                {
                  case 0: // Set filter on/off
                  case 1: // Fine slide up
                  case 2: // Fine slide down
                  case 3: // Set glissando on/off
                  case 4: // Set vibrato waveform
                  case 5: // Set fine tune value
                  case 6: // Loop pattern
                  case 7: // Set tremolo waveform
                  case 8: // Unused
                  case 9: // Retrigger sample
                  case 10: // Fine volume slide up
                  case 11: // Fine volume slide down
                  case 12: // Cut sample
                  case 13: // Delay sample
                  case 14: // Delay pattern
                  case 15: // Invert loop
                    break;
                }
                break;
              case 15: // Set Speed
                speed = data0*16+data1;
		speed = 4096/(speed>0?speed:1);
System.err.println("speed="+speed);
                break;
            }
            i+=4;
            result[Row][Chan][0] = sample<99?corr[sample-1]:99;
            result[Row][Chan][1] = note<99?note+octave*12 : 99;
            result[Row][Chan][2] = (vols[Chan]-1)/8;
            result[Row][Chan][3] = speed;
         }
         if (result[Row][0][0]==99)
            copy(result[Row][0],result[Row][3]);
         else if (result[Row][3][0]==99)
            copy(result[Row][3],result[Row][0]);
         if (result[Row][1][0]==99)
            copy(result[Row][1],result[Row][2]);
         else if (result[Row][2][0]==99)
            copy(result[Row][2],result[Row][1]);
      }
      return jump;
  }

  static String s2(int i)
  {
    String s = String.valueOf(i);
    return s.length()<2 ? " "+s : s;
  }

  static String hex(int i)
  {
    String s = Integer.toHexString(i);
    return "0"+(s.length()<2 ? "0"+s : s)+"h";
  }

  public static void main(String[] args) throws Exception
  {
    FileInputStream fis = new FileInputStream(args[0]);
    // Read MOD header
    byte[] name = new byte[20];
    fis.read(name);
    SampData[] data = new SampData[31];
    int nbi = -1;
    int[] corr = new int[31];
    int[] defvol = new int[31];
    for (int i=0;i<31;i++)
    {
        data[i] = new SampData();
        fis.read(data[i].name);
        data[i].sampllen = readShort(fis)*2;
        data[i].finetune = fis.read();
        data[i].sampvol = fis.read();
        data[i].repeatpoint = readShort(fis);
        data[i].repeatlen = readShort(fis);
        if (data[i].sampllen>1)
        {
           nbi+=1;
           defvol[nbi] = data[i].sampvol;
        }
        corr[i] = nbi;
System.err.println(i+" "+corr[i]+" "+new String(data[i].name)+" "+data[i].sampllen+" "+data[i].sampvol+" "+" "+data[i].repeatpoint+" "+data[i].repeatlen);
    }
    int songlen = fis.read();
    int magic = fis.read();
    byte[] songpos = new byte[128];
    fis.read(songpos);
    byte[] id = new byte[4];
    fis.read(id);

    // Patterns
    int nbp = -1;
    for (int i=0;i<songlen;i++)
    {
        if (songpos[i]>nbp)
	    nbp = songpos[i];
    }
    nbp+=1;
    byte[][] patterns = new byte[nbp][1024];
    for (int i=0;i<nbp;i++)
        fis.read(patterns[i]);
    int[][][][] results = new int[nbp][64][4][4];
    int jump = 0;
    for (int i=0;i<nbp;i++)
    {
        int j = readPattern(patterns[i],results[i],corr,defvol);
        if (j>=0)
           jump = j;
    }

    // Save
    String out = args[1].toUpperCase()+"_";
    PrintWriter pw = new PrintWriter(new FileOutputStream(args[1]+".inc"));
    pw.println(out+"SONG_START");
    for (int i=0;i<songlen;i++)
    {
        int[][][] r = results[songpos[i]];
System.err.println(songpos[i]);
        for (int j=0;j<64;j++)
        {
            pw.print("\tdb\t");
            for (int k=0;k<4;k++)
            {
                for (int l=0;l<3;l++)
                    pw.print(s2(r[j][k][l])+",");
                pw.print(" ");
            }
            int s = r[j][3][3];
            pw.println(s2(s%256)+","+s2(s/256));
        }
    }
    pw.println(out+"SONG_END");
    pw.println();
    pw.println(out+"SONG_RESTART\tEQU\t"+out+"SONG_START+"+(14*64*jump));
    pw.println();
    byte[] smp = new byte[65535];
    int n=0;
    for (int i=0;i<31;i++)
    {
        if (data[i].sampllen<2)
           continue;
        String s = out+"INSTR"+n,se = s+"_END";
        n+=1;
        pw.print(s);
        fis.read(smp,0,data[i].sampllen);
        int k = 0,l=data[i].sampllen-2,vol = data[i].sampvol;
        for (int j=0;j<l;j++)
        {
            if (k%16==0)
            {
               pw.println();
               pw.print("\tdb\t");
            }
            pw.print(hex(128+smp[j+2])+((k+1)%16>0 && (j+1)<l? ",":""));
            k+=1;
        }
        pw.println();
        pw.println(se);
        pw.println();
    }

    pw.println(out+"INSTR_TABLE");
    n=0;
    for (int i=0;i<31;i++)
        if (data[i].sampllen>2)
           pw.println("\tdd\t"+(out+"INSTR"+(n++)));
    pw.println();

    pw.println(out+"INSTR_END_TABLE");
    n=0;
    for (int i=0;i<31;i++)
        if (data[i].sampllen>2)
           pw.println("\tdd\t"+(out+"INSTR"+(n++)+"_END"));
    pw.println();

    pw.println(out+"INSTR_LOOP_TABLE");
    n=0;
    for (int i=0;i<31;i++)
        if (data[i].sampllen>2)
        {
           if (data[i].repeatpoint>=1)
               pw.println("\tdd\t"+(out+"INSTR"+(n++))+"+"+((data[i].repeatpoint-1)*2));
           else
               pw.println("\tdd\tEMPTY_SAMPLE");
        }
    pw.println();

    pw.println(out+"SONG_ID");
    pw.println("\tdd\t"+out+"SONG_START");
    pw.println("\tdd\t"+out+"SONG_END");
    pw.println("\tdd\t"+out+"SONG_RESTART");
    pw.println("\tdd\t"+out+"INSTR_TABLE");
    pw.println("\tdd\t"+out+"INSTR_END_TABLE");
    pw.println("\tdd\t"+out+"INSTR_LOOP_TABLE");
    pw.close();
    fis.close();
  }
}
