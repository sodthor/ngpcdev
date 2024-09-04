import java.io.*;
import java.util.*;

public class Book
{
	static final int ROOKQ   = 8;
	static final int KNIGHTQ = 9;
	static final int BISHOPQ = 10;
	static final int QUEEN   = 11;
	static final int KING    = 12;
	static final int BISHOPK = 13;
	static final int KNIGHTK = 14;
	static final int ROOKK   = 15;

	static int[] x = new int[32];
	static int[] y = new int[32];
	
	static PrintWriter pw;
	static int nbout,nbw;

	final static void initBoard()
	{
		for (int i=0;i<8;i++)
		{
			x[i] = i;
			y[i] = 1;
			x[i+8] = i;
			y[i+8] = 0;
			x[i+16] = i;
			y[i+16] = 6;
			x[i+24] = i;
			y[i+24] = 7;
		}
	}

	final static String str(int x0,int y0,int x1,int y1,int col)
	{
		for (int i=0;i<16;i++)
			if (x[i+16-col]==x1 && y[i+16-col]==y1)
				x[i+16-col] = y[i+16-col] = -255;
		return ""+((char)(x0+'a'))+((char)(y0+'1'))+((char)(x1+'a'))+((char)(y1+'1'));
	}

	private static String pgn(String move,int col) throws Exception
	{
		char p = move.charAt(0), c1 = move.charAt(1);
		int i,j=0;
		i = move.indexOf('+');
		if (i>=0)
			move = move.substring(0,i);
		i = move.indexOf('!');
		if (i>=0)
			move = move.substring(0,i);
		String ret = null;
		if (p>='a' && p<='h')
		{
			if (move.length()==2)
			{
				if (Character.isDigit(c1)) // simple pawn move
				{
					for (i=0;i<8;i++)
					{
						if (x[i+col]==p-'a') // found a candidate
						{
							if (col==0 && (y[i]+1==c1-'1' || (y[i]==1 && c1-'1'==3)))
								break;
							if (col!=0 && (y[i+col]-1==c1-'1' || (y[i+col]==6 && c1-'1'==4)))
								break;
						}
					}
					ret = str(x[i+col],y[i+col],x[i+col],c1-'1',col);
					y[i+col] = c1-'1';
				}
				else
				{
					int tx,ty;
					for (i=0;i<8;i++)
					{
						if (x[i+col]==p-'a') // found a candidate
						{
							for (j=0;j<16;j++)
							{
								if (x[j+16-col]==c1-'a' && ((col==0 && y[j+16]==y[i]+1) || (col!=0 && y[j]==y[i+col]-1)) )
									break;
							}
							if (j<16)
								break;
						}
					}
					if (i==8) // en passant
					{
						for (i=0;i<8;i++)
						{
							if (x[i+col]==p-'a') // found a candidate
							{
								for (j=0;j<8;j++)
								{
									if (x[j+16-col]==c1-'a' && y[j+16]==y[i])
										break;
								}
								if (j<8)
									break;
							}
						}
						ty = y[j+16-col]+(col==0?1:-1);
					}
					else
						ty = y[j+16-col];
					tx = x[j+16-col];
					ret = str(x[i+col],y[i+col],x[j+16-col],y[j+16-col],col);
					x[i+col] = tx;
					y[i+col] = ty;
				}
			}
			else
			{
                                if (Character.isDigit(c1))
                                        return move; // already good format
				for (i=0;i<8;i++)
					if (x[i+col]==p-'a') // found a candidate
						break;
				int xd = move.charAt(move.length()-2)-'a';
				int yd = move.charAt(move.length()-1)-'1';
				ret = str(x[i+col],y[i+col],xd,yd,col);
				x[i+col] = xd;
				y[i+col] = yd;
			}
		}
		else
		{
			int xd = move.charAt(move.length()-2)-'a';
			int yd = move.charAt(move.length()-1)-'1';
			switch(p)
			{
				case 'R':
				case 'r':
					if (move.length()>3 && c1!='x')
					{
						if (Character.isDigit(c1))
						{
							if (y[ROOKK+col]==c1-'1')
								i = ROOKK+col;
							else
								i = ROOKQ+col;
						}
						else
						{
							if (x[ROOKK+col]==c1-'a')
								i = ROOKK+col;
							else
								i = ROOKQ+col;
						}
					}
					else
					{
						if (xd==x[ROOKK+col] || yd==y[ROOKK+col])
						{
							int dx = xd==x[ROOKK+col] ? 0 : (xd>x[ROOKK+col] ? 1 : -1);
							int dy = yd==y[ROOKK+col] ? 0 : (yd>y[ROOKK+col] ? 1 : -1);
							int xx = x[ROOKK+col]+dx;
							int yy = y[ROOKK+col]+dy;
							while (xx!=xd && yy!=yd)
							{
								boolean test = true;
								for (int k=0;k<32&&test;k++)
									test = (x[k]!=xx || y[k]!=yy);
								if (!test)
									break;
								xx+=dx;
								yy+=dy;
							}
							if (xx==xd && yy==yd)
								i = ROOKK+col;
							else
								i = ROOKQ+col;
						}
						else
							i = ROOKQ+col;
					}
					ret = str(x[i],y[i],xd,yd,col);
					x[i] = xd;
					y[i] = yd;
					break;
				case 'N':
				case 'n':
					if (move.length()>3 && c1!='x')
					{
						if (Character.isDigit(c1))
						{
							if (y[KNIGHTK+col]==c1-'1')
								i = KNIGHTK+col;
							else
								i = KNIGHTQ+col;
						}
						else
						{
							if (x[KNIGHTK+col]==c1-'a')
								i = KNIGHTK+col;
							else
								i = KNIGHTQ+col;
						}
					}
					else
					{
						if (	(x[KNIGHTK+col]+2==xd && y[KNIGHTK+col]+1==yd) ||
							(x[KNIGHTK+col]+2==xd && y[KNIGHTK+col]-1==yd) ||
							(x[KNIGHTK+col]-2==xd && y[KNIGHTK+col]+1==yd) ||
							(x[KNIGHTK+col]-2==xd && y[KNIGHTK+col]-1==yd) ||
							(x[KNIGHTK+col]+1==xd && y[KNIGHTK+col]+2==yd) ||
							(x[KNIGHTK+col]+1==xd && y[KNIGHTK+col]-2==yd) ||
							(x[KNIGHTK+col]-1==xd && y[KNIGHTK+col]+2==yd) ||
							(x[KNIGHTK+col]-1==xd && y[KNIGHTK+col]-2==yd))
							i = KNIGHTK+col;
						else
							i = KNIGHTQ+col;
					}
					ret = str(x[i],y[i],xd,yd,col);
					x[i] = xd;
					y[i] = yd;
					break;
				case 'B':
				//case 'b': // impossible: pawn
					if (Math.abs(x[BISHOPK+col]-xd)==Math.abs(y[BISHOPK+col]-yd))
						i = BISHOPK+col;
					else
						i = BISHOPQ+col;
					ret = str(x[i],y[i],xd,yd,col);
					x[i] = xd;
					y[i] = yd;
					break;
				case 'Q':
				case 'q':
					ret = str(x[QUEEN+col],y[QUEEN+col],xd,yd,col);
					x[QUEEN+col] = xd;
					y[QUEEN+col] = yd;
					break;
				case 'K':
				case 'k':
					ret = str(x[KING+col],y[KING+col],xd,yd,col);
					x[KING+col] = xd;
					y[KING+col] = yd;
					break;
				case 'o':
				case 'O':
					if (move.equals("o-o")||move.equals("O-O"))
					{
						ret = col==0?"e1g1":"e8g8";
						x[KING+col] += 2;
						x[ROOKK+col] -= 2;
					}
					else
					{
						ret = col==0?"e1c1":"e8c8";
						x[KING+col] -= 2;
						x[ROOKK+col] += 3;
					}
					break;
			}
		}
		if (ret==null)
			throw new Exception(move);
		return ret;
	}

	private static void writeByte(byte b) throws Exception
	{
		String s = "0"+Integer.toHexString(b);
		nbw++;
		pw.print("0x"+s.substring(s.length()-2)+(nbw<nbout?",":""));
		if (nbw%16==0)
			pw.println();
	}

	private static void writeMove(String move) throws Exception
	{
		byte b = (byte)(((move.charAt(1)-'1')<<3)+(move.charAt(0)-'a'));
		writeByte(b);
		b = (byte)(((move.charAt(3)-'1')<<3)+(move.charAt(2)-'a'));
		writeByte(b);
	}

	private static void writeIdx(int idx) throws Exception
	{
		writeByte((byte)(idx/65536));
		writeByte((byte)((idx%65536)/256));
		writeByte((byte)(idx%256));
	}

	private static void computeSize(Vector book) throws Exception
	{
		Vector moves = (Vector)book.elementAt(0);
		Vector nodes = (Vector)book.elementAt(1);
		int cnt = moves.size();
		Vector idx = new Vector(cnt);
		book.addElement(idx);
		nbout=nbout+1+5*cnt;
		for (int i=0;i<cnt;i++)
		{
			idx.addElement(new Integer(nbout));
			computeSize((Vector)nodes.elementAt(i));
		}
	}

	private static void saveBook(Vector book) throws Exception
	{
		Vector moves = (Vector)book.elementAt(0);
		Vector nodes = (Vector)book.elementAt(1);
		Vector indexes = (Vector)book.elementAt(2);
		int cnt = nodes.size();
		writeByte((byte)cnt);
		for (int i=0;i<cnt;i++)
		{
			writeMove((String)moves.elementAt(i));
			writeIdx(((Integer)indexes.elementAt(i)).intValue());
		}
		for (int i=0;i<cnt;i++)
			saveBook((Vector)nodes.elementAt(i));
	}

	public static void main(String[] args) throws Exception
	{
		LineNumberReader lnr = new LineNumberReader(new FileReader(args.length==0?"small.txt":args[0]));
		int maxdepth = (args.length<2) ? 200 :  Integer.valueOf(args[1]).intValue();
		String line = lnr.readLine();
		Vector book = new Vector();
		book.addElement(new Vector());
		book.addElement(new Vector());
		int nb = 0;
		do
		{
			String opening = "";
			// skip header
			while (line!=null&&line.trim().length()==0||line.charAt(0)=='['||line.startsWith("    "))
			{
			        System.err.println(line);
			        line = lnr.readLine();
			}
			do
			{
				if (line==null || line.trim().length()==0 || line.charAt(0)=='[')
					break;
				if (!line.endsWith(" "))
				        line += " ";
				opening += line;
				line = lnr.readLine();
			} while (true);
			if (opening.endsWith("1-0 ")||opening.endsWith("0-1 "))
			        opening = opening.substring(0,opening.length()-4);
			else if (opening.endsWith("1/2-1/2 "))
			        opening = opening.substring(0,opening.length()-8);
			else if (opening.endsWith("end"))
			        opening = opening.substring(0,opening.length()-3);
			Vector node = book;
			Vector moves = (Vector)node.elementAt(0);
			Vector nodes = (Vector)node.elementAt(1);
			initBoard();
			int col = 0,depth=0;
			if (opening.trim().length()>0)
			        opening = opening.trim()+" ";
			else
			        opening = "";
			System.err.println("Opennig #"+(nb++)+" Line #"+lnr.getLineNumber());
			System.err.println(opening);
			while (opening.length()>0 && depth<maxdepth)
			{
				int pos = opening.indexOf(' ');
				while (pos+1<opening.length()&&opening.charAt(pos+1)==' ')
				        pos++;
				String pgn = opening.substring(0,pos).trim();
				if (pgn.indexOf('.')>=0)
				{
				        opening = opening.substring(pos+1);
				        pos = opening.indexOf(' ');
        				while (pos+1<opening.length()&&opening.charAt(pos+1)==' ')
	        			        pos++;
				        pgn = opening.substring(0,pos).trim();
				}
				String move = pgn(pgn,col);
				System.err.println(pgn+" "+move);
				col = 16-col;
				opening = opening.substring(pos+1);
				if ((pos=moves.indexOf(move))>=0)
					node = (Vector)nodes.elementAt(pos);
				else
				{
					moves.addElement(move);
					Vector n = new Vector();
					nodes.addElement(n);
					n.addElement(new Vector());
					n.addElement(new Vector());
					node = n;
				}
				moves = (Vector)node.elementAt(0);
				nodes = (Vector)node.elementAt(1);
				depth++;
			}
		} while (line!=null);
		lnr.close();
		nbout = 0;
		computeSize(book);
		System.err.println("Size = "+nbout);
		pw = new PrintWriter(new FileOutputStream("book.c"));
		pw.println("const unsigned char book[] = {");
		nbw = 0;
		saveBook(book);
		pw.println("};");
		pw.flush();
		pw.close();
	}
}
