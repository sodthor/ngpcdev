import java.io.*;

class FreqConverter
{
  private static String[] notes = {"C","C#","D","D#","E","F","F#","G","G#","A","A#","B"};

  static int periods[] =
  {
    1712,1616,1524,1440,1356,1280,1208,1140,1076,1016,960,906,	// octave 0
    856,808,762,720,678,640,604,570,538,508,480,453,
    428,404,381,360,339,320,302,285,269,254,240,226,
    214,202,190,180,170,160,151,143,135,127,120,113,
    107,101,95,90,85,80,75,71,67,63,60,56			// octave 5
  };

  static final int NTSC = 7159090;
  static final int PAL = 7093789;

  public static void main(String[] args) throws Exception
  {
    PrintWriter pw = new PrintWriter(new FileOutputStream("freq.inc"));
    pw.println("FREQ_TABLE");
    for (int i=0;i<periods.length;i++)
    {
        int p = ((NTSC/(periods[i]*2))*128)/8000;
        pw.println("\tdd\t"+p+"\t; "+notes[i%12]+" "+(i/12));
    }
    pw.println();
    pw.println("VOL_TABLE");
    for (int i=1;i<=8;i++)
    {
        pw.println("; VOL "+i);
        for (int j=0;j<256;j++)
        {
            if ((j%16)==0)
               pw.print("\tdb\t");
            pw.print(((((j-128)*i)/8)+128)+((j+1)%16==0?"":","));
            if (((j+1)%16)==0)
               pw.println();
        }
    }
    pw.println();
    pw.println("EMPTY_SAMPLE");
    pw.println("\tdb\t0080h");
    pw.println("END_EMPTY_SAMPLE");
    pw.println();
    pw.println("EMPTY_SAMPLE_BIS\tEQU\tEMPTY_SAMPLE*128");
    pw.println();
    pw.println("END_EMPTY_SAMPLE_BIS\tEQU\tEND_EMPTY_SAMPLE*128");
    pw.close();
  }
}
