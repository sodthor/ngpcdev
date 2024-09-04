import java.io.*;

public class Bin2C
{
  public static void main(String args[]) throws Exception
  {
    FileInputStream fis = new FileInputStream(args[0]);
    String out = args[0].substring(0,args[0].indexOf("."));
    PrintWriter pw = new PrintWriter(new FileOutputStream(out+".mh"));
    int data,i=0;
    pw.println("const u8 "+out+"[] = {");
    while((data=fis.read())!=-1)
    {
        pw.print("0x"+Integer.toHexString(data));
        pw.print(",");
        if (i%16==15)
           pw.println();
        i+=1;
    }
    pw.println(0);
    pw.println("};");
    pw.close();
    fis.close();
  }
}
