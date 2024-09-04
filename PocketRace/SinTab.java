import java.io.*;

class SinTab
{
	public static void main(String[] args) throws Exception
	{
		PrintWriter pw = new PrintWriter(new FileOutputStream("sintab.h"));
		pw.println("const u8 sintab[64] = {");
		for (int i=0;i<64;i++)
		{
			double d = Math.sin((2*Math.PI*i)/64);
			pw.println((int)(d*d*255)+(i<63?",":""));
		}
		pw.println("};");
		pw.close();
	}
}
