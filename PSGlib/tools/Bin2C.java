import java.io.*;

public class Bin2C
{
  public static void main(String args[]) throws Exception
  {
    File file = new File(args[0]);
	try (FileInputStream fis = new FileInputStream(file)) {
    	bin2C(args[0], fis, (int) file.length());
    }
  }

  public static void bin2C(String fname, InputStream is, int l) throws FileNotFoundException, IOException {
	String out = fname.substring(fname.lastIndexOf("/") + 1,fname.lastIndexOf("."));
    String ext = fname.substring(fname.lastIndexOf(".") + 1);
    PrintWriter pw = new PrintWriter(new FileOutputStream(out+".h"));
    int data,i=0;
	String name = out.toUpperCase()+"_"+ext.toUpperCase();
	pw.println("#ifndef _" + name +"_");
	pw.println("#define _" + name +"_");
    pw.println("#define "+name+"_SIZE " + l);
    pw.println("const u8 "+name+"["+name+"_SIZE] = {");
    while(i < l && (data=is.read())!=-1)
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
  }
}