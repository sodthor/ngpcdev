import java.io.*;

class SinTabApp
{
  public static void main(String[] args) throws Exception
  {
    int precision = Integer.valueOf(args[0]).intValue();
    int bsize = Integer.valueOf(args[1]).intValue();
    int mult = (1<<bsize)-1;
    int len = bsize<8 ? 2 : 4;
    String pref = bsize<8 ? "db" : "dw";
    int[] tcos = new int[precision];
    int[] tsin = new int[precision];
    for (int i=0;i<precision;i++)
    {
        tsin[i] = (int)(Math.sin((2*Math.PI*i)/precision)*mult);
        tcos[i] = (int)(Math.cos((2*Math.PI*i)/precision)*mult);
    }
    FileOutputStream fos = new FileOutputStream("sincos.inc");
    PrintWriter pw = new PrintWriter(fos);
    pw.println("PREC\tEQU\t"+precision);
    pw.println("BSIZE\tEQU\t"+bsize);
    pw.println("MULT\tEQU\t"+mult);

    pw.print("cosinus");
    for (int i=0;i<precision;i++)
    {
        if (i%16==0)
        {
           pw.println();
           pw.print("\t"+pref+"\t");
        }
        String s = "000"+Integer.toHexString(tcos[i]);
        pw.print("0"+s.substring(s.length()-len)+"h"+((i+1<precision)&&((i+1)%16!=0)?",":""));
    }
    pw.println();
    pw.print("sinus");
    for (int i=0;i<precision;i++)
    {
        if (i%16==0)
        {
           pw.println();
           pw.print("\t"+pref+"\t");
        }
        String s = "000"+Integer.toHexString(tsin[i]);
        pw.print("0"+s.substring(s.length()-len)+"h"+((i+1<precision)&&((i+1)%16!=0)?",":""));
    }
    pw.println();
    pw.flush();
    pw.close();
    fos.close();
  }
}
