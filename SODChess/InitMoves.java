import java.util.*;
import java.io.*;

public class InitMoves
{
	public static void main(String args[]) throws Exception
	{
		int i,j,k,l,x,y,n;
		Vector v = new Vector();
		// Rook
		PrintWriter pw = new PrintWriter(new FileOutputStream("moves.h"));
		pw.println("const u16 moves[7][64][8][2] = {");
		pw.println("{ // W PAWNS");
		n = 6;
		for (i=0;i<64;i++)
		{
			x = i%8;
			y = i/8;
			pw.print("\t{");
			l = v.size();
			if (x>0 && y<7)
				v.addElement(new Integer(x-1+(y+1)*8));
			if (x<7 && y<7)
				v.addElement(new Integer(x+1+(y+1)*8));
			pw.print("{"+l+","+(v.size()-l)+"},");
			for (j=0;j<n;j++)
				pw.print("{0,0}"+(j+1<n?",":""));
			pw.println("}"+(i<63?",":""));
		}
		pw.println("},");
		pw.println("{ // B PAWNS");
		n = 6;
		for (i=0;i<64;i++)
		{
			x = i%8;
			y = i/8;
			pw.print("\t{");
			l = v.size();
			if (x>0 && y>0)
				v.addElement(new Integer(x-1+(y-1)*8));
			if (x<7 && y>0)
				v.addElement(new Integer(x+1+(y-1)*8));
			pw.print("{"+l+","+(v.size()-l)+"},");
			for (j=0;j<n;j++)
				pw.print("{0,0}"+(j+1<n?",":""));
			pw.println("}"+(i<63?",":""));
		}
		pw.println("},");
		pw.println("{ // ROOK");
		for (i=0;i<64;i++)
		{
			x = i%8;
			y = i/8;
			pw.print("\t{");
			l = v.size();
			n = 4;

			for (j=x-1;j>0;j--)
				v.addElement(new Integer(j+y*8));
			if (v.size()>l)
			{
				pw.print("{"+l+","+(v.size()-l)+"},");
				l = v.size();
			}
			else
				n++;

			for (j=x+1;j<8;j++)
				v.addElement(new Integer(j+y*8));
			if (v.size()>l)
			{
				pw.print("{"+l+","+(v.size()-l)+"},");
				l = v.size();
			}
			else
				n++;

			for (j=y-1;j>0;j--)
				v.addElement(new Integer(x+j*8));
			if (v.size()>l)
			{
				pw.print("{"+l+","+(v.size()-l)+"},");
				l = v.size();
			}
			else
				n++;

			for (j=y+1;j<8;j++)
				v.addElement(new Integer(x+j*8));
			if (v.size()>l)
				pw.print("{"+l+","+(v.size()-l)+"},");
			else
				n++;

			for (j=0;j<n;j++)
				pw.print("{0,0}"+(j+1<n?",":""));
			pw.println("}"+(i<63?",":""));
		}
		pw.println("},");
    		// Bishop
		pw.println("{ // BISHOP");
		for (i=0;i<64;i++)
		{
			x = i%8;
			y = i/8;
			pw.print("\t{");
			l = v.size();
			n = 4;

                        j = x-1;
                        k = y-1;
			while (j>=0 && k>=0)
				v.addElement(new Integer((j--)+(k--)*8));
			if (v.size()>l)
			{
				pw.print("{"+l+","+(v.size()-l)+"},");
				l = v.size();
			}
			else
				n++;

                        j = x+1;
                        k = y-1;
			while (j<8 && k>=0)
				v.addElement(new Integer((j++)+(k--)*8));
			if (v.size()>l)
			{
				pw.print("{"+l+","+(v.size()-l)+"},");
				l = v.size();
			}
			else
				n++;

                        j = x+1;
                        k = y+1;
			while (j<8 && k<8)
				v.addElement(new Integer((j++)+(k++)*8));
			if (v.size()>l)
			{
				pw.print("{"+l+","+(v.size()-l)+"},");
				l = v.size();
			}
			else
				n++;

                        j = x-1;
                        k = y+1;
			while (j>=0 && k<8)
				v.addElement(new Integer((j--)+(k++)*8));
			if (v.size()>l)
				pw.print("{"+l+","+(v.size()-l)+"},");
			else
				n++;

			for (j=0;j<n;j++)
				pw.print("{0,0}"+(j+1<n?",":""));
			pw.println("}"+(i<63?",":""));
		}
		pw.println("},");
    		// Queen
		pw.println("{ // QUEEN");
		for (i=0;i<64;i++)
		{
			x = i%8;
			y = i/8;
			pw.print("\t{");
			l = v.size();
			n = 0;

			for (j=x-1;j>0;j--)
				v.addElement(new Integer(j+y*8));
			if (v.size()>l)
			{
				pw.print("{"+l+","+(v.size()-l)+"},");
				l = v.size();
			}
			else
				n++;

			for (j=x+1;j<8;j++)
				v.addElement(new Integer(j+y*8));
			if (v.size()>l)
			{
				pw.print("{"+l+","+(v.size()-l)+"},");
				l = v.size();
			}
			else
				n++;

			for (j=y-1;j>0;j--)
				v.addElement(new Integer(x+j*8));
			if (v.size()>l)
			{
				pw.print("{"+l+","+(v.size()-l)+"},");
				l = v.size();
			}
			else
				n++;

			for (j=y+1;j<8;j++)
				v.addElement(new Integer(x+j*8));
			if (v.size()>l)
			{
				pw.print("{"+l+","+(v.size()-l)+"},");
				l = v.size();
			}
			else
				n++;

                        j = x-1;
                        k = y-1;
			while (j>=0 && k>=0)
				v.addElement(new Integer((j--)+(k--)*8));
			if (v.size()>l)
			{
				pw.print("{"+l+","+(v.size()-l)+"},");
				l = v.size();
			}
			else
				n++;

                        j = x+1;
                        k = y-1;
			while (j<8 && k>=0)
				v.addElement(new Integer((j++)+(k--)*8));
			if (v.size()>l)
			{
				pw.print("{"+l+","+(v.size()-l)+"},");
				l = v.size();
			}
			else
				n++;

                        j = x+1;
                        k = y+1;
			while (j<8 && k<8)
				v.addElement(new Integer((j++)+(k++)*8));
			pw.print("{"+l+","+(v.size()-l)+"},");
			l = v.size();
                        j = x-1;
                        k = y+1;
			while (j>=0 && k<8)
				v.addElement(new Integer((j--)+(k++)*8));
			if (v.size()>l)
				pw.print("{"+l+","+(v.size()-l)+"}"+(n==0?"":","));
			else
				n++;

			for (j=0;j<n;j++)
				pw.print("{0,0}"+(j+1<n?",":""));
			pw.println("}"+(i<63?",":""));
		}
		pw.println("},");
    		// Knight
		pw.println("{ // KNIGHT");
		for (i=0;i<64;i++)
		{
			x = i%8;
			y = i/8;
			pw.print("\t{");
			l = v.size();
			n = 0;

			if (x-1>=0&&y-2>=0)
                           v.addElement(new Integer((x-1)+(y-2)*8));
			if (v.size()>l)
			{
				pw.print("{"+l+","+(v.size()-l)+"},");
				l = v.size();
			}
			else
				n++;

			if (x-1>=0&&y+2<8)
                           v.addElement(new Integer((x-1)+(y+2)*8));
			if (v.size()>l)
			{
				pw.print("{"+l+","+(v.size()-l)+"},");
				l = v.size();
			}
			else
				n++;

			if (x-2>=0&&y-1>=0)
                           v.addElement(new Integer((x-2)+(y-1)*8));
			if (v.size()>l)
			{
				pw.print("{"+l+","+(v.size()-l)+"},");
				l = v.size();
			}
			else
				n++;

			if (x-2>=0&&y+1<8)
                           v.addElement(new Integer((x-2)+(y+1)*8));
			if (v.size()>l)
			{
				pw.print("{"+l+","+(v.size()-l)+"},");
				l = v.size();
			}
			else
				n++;

			if (x+1<8&&y-2>=0)
                           v.addElement(new Integer((x+1)+(y-2)*8));
			if (v.size()>l)
			{
				pw.print("{"+l+","+(v.size()-l)+"},");
				l = v.size();
			}
			else
				n++;

			if (x+1<8&&y+2<8)
                           v.addElement(new Integer((x+1)+(y+2)*8));
			if (v.size()>l)
			{
				pw.print("{"+l+","+(v.size()-l)+"},");
				l = v.size();
			}
			else
				n++;

			if (x+2<8&&y-1>=0)
                           v.addElement(new Integer((x+2)+(y-1)*8));
			if (v.size()>l)
			{
				pw.print("{"+l+","+(v.size()-l)+"},");
				l = v.size();
			}
			else
				n++;

			if (x+2<8&&y+1<8)
                           v.addElement(new Integer((x+2)+(y+1)*8));
			if (v.size()>l)
				pw.print("{"+l+","+(v.size()-l)+"}"+(n==0?"":","));
			else
				n++;

			for (j=0;j<n;j++)
				pw.print("{0,0}"+(j+1<n?",":""));
			pw.println("}"+(i<63?",":""));
		}
		pw.println("},");
    		// King
		pw.println("{ // KING");
		for (i=0;i<64;i++)
		{
			x = i%8;
			y = i/8;
			pw.print("\t{");
			l = v.size();
			n = 0;

			if (x-1>=0&&y-1>=0)
                           v.addElement(new Integer((x-1)+(y-1)*8));
			if (v.size()>l)
			{
				pw.print("{"+l+","+(v.size()-l)+"},");
				l = v.size();
			}
			else
				n++;

			if (x-1>=0&&y+1<8)
                           v.addElement(new Integer((x-1)+(y+1)*8));
			if (v.size()>l)
			{
				pw.print("{"+l+","+(v.size()-l)+"},");
				l = v.size();
			}
			else
				n++;

			if (x+1<8&&y+1<8)
                           v.addElement(new Integer((x+1)+(y+1)*8));
			if (v.size()>l)
			{
				pw.print("{"+l+","+(v.size()-l)+"},");
				l = v.size();
			}
			else
				n++;

			if (x+1<8&&y-1>=0)
                           v.addElement(new Integer((x+1)+(y-1)*8));
			if (v.size()>l)
			{
				pw.print("{"+l+","+(v.size()-l)+"},");
				l = v.size();
			}
			else
				n++;

			if (x-1>=0)
                           v.addElement(new Integer((x-1)+y*8));
			if (v.size()>l)
			{
				pw.print("{"+l+","+(v.size()-l)+"},");
				l = v.size();
			}
			else
				n++;

			if (x+1<8)
                           v.addElement(new Integer((x+1)+y*8));
			if (v.size()>l)
			{
				pw.print("{"+l+","+(v.size()-l)+"},");
				l = v.size();
			}
			else
				n++;

			if (y-1>=0)
                           v.addElement(new Integer(x+(y-1)*8));
			if (v.size()>l)
			{
				pw.print("{"+l+","+(v.size()-l)+"},");
				l = v.size();
			}
			else
				n++;

			if (y+1<8)
                           v.addElement(new Integer(x+(y+1)*8));
			if (v.size()>l)
				pw.print("{"+l+","+(v.size()-l)+"}"+(n==0?"":","));
			else
				n++;

			for (j=0;j<n;j++)
				pw.print("{0,0}"+(j+1<n?",":""));
			pw.println("}"+(i<63?",":""));
		}
		pw.println("}");
		pw.println("};");
		pw.println();
		l = v.size();
		pw.print("const u8 moves_dst["+l+"] = {");
		String s;
		for (i=0;i<l;i++)
		{
			if (i%16==0)
				pw.println();
			s = " "+((Integer)v.elementAt(i)).intValue();
			pw.print(s.substring(s.length()-2)+(i<l-1?",":""));
		}
		pw.println("};");
		pw.close();
	}
}