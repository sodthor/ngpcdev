import java.io.*;

class Tables
{
	static final int PRECISION = 512;
	static final int SHIFT = 7;
	static final int MULT = 1<<SHIFT;
	static final int DIV_SHIFT = 16;
	static final int DIV_MULT = 1<<DIV_SHIFT;
	static final int MAX_DIV = 32767;

	public static void main(String args[]) throws Exception
	{
		PrintWriter pw = new PrintWriter(new FileOutputStream("../tables.h"));
		pw.println("#ifndef _TABLES_H_");
		pw.println("#define _TABLES_H_");
		pw.println();
		pw.println("#define PRECISION "+PRECISION);
		pw.println("#define PRECISION2 "+(PRECISION>>1));
		pw.println("#define MULT "+MULT);
		pw.println("#define SHIFT "+SHIFT);
		pw.println("#define PMASK "+(PRECISION-1));
		pw.println("#define DIV_MULT "+DIV_MULT);
		pw.println("#define DIV_SHIFT "+DIV_SHIFT);
		pw.println("#define MAX_DIV "+MAX_DIV);

		pw.println();
		pw.println("#define DIVIDE(a,b) (DIVIDE_NS(a,b)>>DIV_SHIFT)");
		pw.println("#define DIVIDE_NS(a,b) ((b)<0 ? Multiply32bit(-(a),tdiv[-(b)]) : Multiply32bit((a),tdiv[b]))");
		pw.println();
        pw.println("#include \"ngpc.h\"");
		pw.println();
		pw.println("#ifndef _TABLES_C_");
		pw.println("extern const s16 tsin[PRECISION];");
		pw.println("extern const s16 tcos[PRECISION];");
		pw.println("extern const s32 tdiv[MAX_DIV+1];");
		pw.println("#endif");
		pw.println();
		pw.println("#endif");
		pw.close();

		pw = new PrintWriter(new FileOutputStream("../tables.c"));
		pw.println("#define _TABLES_C_");
		pw.println();
		pw.println("#include \"tables.h\"");
		pw.println();
		pw.println("const s16 tsin[PRECISION] = {");
		for (int i=0;i<PRECISION;i++)
		{
			pw.print(((int)(Math.sin((2*Math.PI*i)/PRECISION)*MULT))+(i<PRECISION-1?",":"};"));
			if ((i+1)%16==0)
				pw.println();
		}
		pw.println();
		pw.println("const s16 tcos[PRECISION] = {");
		for (int i=0;i<PRECISION;i++)
		{
			pw.print(((int)(Math.cos((2*Math.PI*i)/PRECISION)*MULT))+(i<PRECISION-1?",":"};"));
			if ((i+1)%16==0)
				pw.println();
		}
		pw.println();
		pw.println("const s32 tdiv[MAX_DIV+1] = {");
		for (int i=0;i<=MAX_DIV;i++)
		{
			pw.print((i==0?0:((int)(DIV_MULT/i)))+(i<MAX_DIV?",":"};"));
			if ((i+1)%16==0)
				pw.println();
		}
		pw.close();

	}
}
