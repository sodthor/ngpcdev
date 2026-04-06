import java.io.*;

public class Bin2C
{
  public static void main(String args[]) throws Exception
  {
	File f = new File(args[0]);
    FileInputStream fis = new FileInputStream(f);
    String out = args[0].substring(0,args[0].indexOf("."));
    String ext = args[0].substring(args[0].indexOf(".") + 1);
    PrintWriter pw = new PrintWriter(new FileOutputStream(out+".h"));
    int data,i=0, l = (int) f.length();
	String name = out.toUpperCase()+"_"+ext.toUpperCase();
	pw.println("#ifndef _" + name +"_");
	pw.println("#define _" + name +"_");
    pw.println("#define "+name+"_SIZE " + l);
    pw.println("const u8 "+name+"["+name+"_SIZE] = {");
    while((data=fis.read())!=-1)
    {
        pw.print("0x"+String.format("%02x", data));
        if (i + 1 < l)
           pw.print(",");
        if (++i % 16 == 0)
			pw.println();
    }
    pw.println("};");
	pw.println("#endif");
    pw.close();
    fis.close();
  }
}