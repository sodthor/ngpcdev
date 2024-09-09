import java.io.*;
import java.awt.*;
import java.awt.image.*;

public class GIF2NGP
{
	static GifDecoder gd = null;
	static String id = null;
	static String ext = null;
	static int delay = 0;
	static int fill = 0;

	public static String getName(int i)
	{
		String idx = "00"+i;
		idx = idx.substring(idx.length()-fill);
		return id+"/"+id+idx+"."+ext;
	}

	public static int getFrameCount()
	{
		if (gd!=null)
			return gd.getFrameCount();
		fill = 3;
		File f = new File(getName(0));
		if (!f.exists())
		{
			fill = 2;
			f = new File(getName(0));
			if (!f.exists())
				fill = 1;
		}
		int i;
		for (i=0;;i++)
		{
			f = new File(getName(i));
			if (!f.exists())
				break;
		}
		return i;
	}

	public static int getDelay()
	{
		if (gd!=null)
			return 1000/gd.getDelay(0);
		return delay;
	}

	public static Image getFrame(int i) throws Exception
	{
		if (gd!=null)
			return gd.getFrame(i);
		Image img = Toolkit.getDefaultToolkit().createImage(getName(i));
		MediaTracker tracker = new MediaTracker(new Panel());
		tracker.addImage(img, 0);
		tracker.waitForID(0);
		return img;
	}

	public static void main(String[] args) throws Exception
	{
		int otype,okind;
		String stype = "asm";
		String musName = "bustamove0.bin";
		String skind = "hc";
		if (args[0].endsWith(".gif"))
		{
			gd = new GifDecoder();
			gd.read(args[0]);
			id = (new File(args[0])).getName();
			id = id.substring(0,id.length()-4);
			if (args.length>=2)
				stype = args[1]; 
			if (args.length>=3)
				skind = args[2]; 
			if (args.length>=4)
				musName = args[3]; 
		}
		else
		{
			id = args[0];
			ext = args[1];
			delay = Integer.valueOf(args[2]).intValue();
			if (args.length>=4)
				stype = args[3]; 
			if (args.length>=5)
				skind = args[4]; 
			if (args.length>=6)
				musName = args[5]; 
		}

		if (stype.equals("asm"))
			otype = CodeImage.ASM;
		else if (stype.equals("c"))
			otype = CodeImage.C;
		else if (stype.equals("bin"))
			otype = CodeImage.DATA;
		else
			throw new Exception("Bad output format:"+stype);

		if (skind.equals("hc"))
			okind = 0;
		else if (skind.equals("lc"))
			okind = 1;
		else
			throw new Exception("Bad output kind:"+skind);

		int count = getFrameCount();
		Image frame = getFrame(0);
		int wi = frame.getWidth(null);
		int hi = frame.getHeight(null);
		int W = okind==0?160:wi&0xf8;
		int H = okind==0?152:hi&0xf8;
		double fw = ((double)W)/wi,fh = ((double)H)/hi;
		if (fw>fh)
			fw = fh;
		int w = (int)(wi*fw);
		int h = (int)(hi*fw);
		int x = (W-w)/2;
		int y = (H-h)/2;
		BufferedImage bi = new BufferedImage(W,okind==0?152:H*count,BufferedImage.TYPE_INT_ARGB);

		String name = id.toUpperCase();
		File f = new File(id);
		if (!f.exists())
			f.mkdir();

		PrintWriter pw = null;
		OutputStream os = null;
		if (otype!=CodeImage.DATA)
			pw = new PrintWriter(new FileOutputStream(id+(otype==CodeImage.ASM?".inc":".h")));
		else
			os = new ByteArrayOutputStream();
			
		switch(otype)
		{
			case CodeImage.ASM:
				if (okind==0)
				{
					pw.println(name+"_DATA");
					pw.println("\tdd\t"+id+"0_ID");
					pw.println(name+"_COUNT");
					pw.println("\tdw\t"+count);
					pw.println(name+"_WIDTH");
					pw.println("\tdw\t"+(W>>3));
					pw.println(name+"_HEIGHT");
					pw.println("\tdw\t"+(H>>3));
					pw.println(name+"_DELAY");
					pw.println("\tdw\t"+(60/getDelay()));
				}
				else
				{
					pw.println(name+"_COUNT EQU "+count);
					pw.println(name+"_WIDTH EQU "+(W>>3));
					pw.println(name+"_FH EQU "+(H>>3));
					pw.println(name+"_DELAY EQU "+(60/getDelay()));
				}
				pw.println();
				break;
			case CodeImage.C:
				pw.println("#define "+name+"_COUNT "+count);
				pw.println("#define "+name+"_DELAY "+(60/getDelay()));
				pw.println();
				if (okind==0)
					pw.println("const u16 "+name+"_DATA["+name+"_COUNT]["+(okind==0?4028:0)+"] = {");
				else
				{
					pw.println("#define "+name+"_FRAME "+(8*(W>>3)*(H>>3)));
					pw.println("#define "+name+"_FT "+((W*H)>>6));
					pw.println("#define "+name+"_FH "+(H>>3));
					pw.println();
				}
				break;
			case CodeImage.DATA:
				CodeImage.write16(os,count);
				CodeImage.write16(os,(60/getDelay()));
				break;
		}
		Int col = new Int(0);
		Int diff = new Int(0);
		long cols = 0,diffs = 0;

		if (okind==0)
		{
			for (int i=0;i<count;i++)
			{
				System.err.println("frame "+(i+1)+"/"+count);
				if (otype==CodeImage.ASM)
					pw.println("\tinclude\t\""+id+"/"+id+i+".inc\"");
				frame = getFrame(i);
				bi.getGraphics().setColor(Color.black);
				bi.getGraphics().clearRect(0,0,160,152);
				bi.getGraphics().drawImage(frame,x,y,x+w,y+h,0,0,wi,hi,null);
				if (otype==CodeImage.ASM)
					os = new FileOutputStream(id+"/"+id+i+".inc");
				else if (otype==CodeImage.C)
					os = new ByteArrayOutputStream();
				col.value = 0;
				diff.value = 0;
				CodeImage._encodeHC(bi,id+i,CodeImage.U-1,1,0,col,diff,os,new Int(1),otype,otype==CodeImage.ASM);
				cols+=col.value;
				diffs+=diff.value;
				if (otype==CodeImage.C)
					pw.println(new String(((ByteArrayOutputStream)os).toByteArray())+(i+1==count?"":","));
				else if (otype!=CodeImage.DATA)
					os.close();
			}
		}
		else
		{
			bi.getGraphics().setColor(Color.black);
			bi.getGraphics().clearRect(0,0,W,H*count);
			os = new ByteArrayOutputStream();
			for (int i=0;i<count;i++)
			{
				frame = getFrame(i);
				bi.getGraphics().drawImage(frame,x,y+i*H,x+w,y+h+i*H,0,0,wi,hi,null);
			}
			CodeImage.encode(bi,id,CodeImage.U-1,1,0,16,col,diff,null,null,null,os,new Int(1),otype,true,false,false,false);
			cols=col.value;
			diffs=diff.value;
			pw.println(new String(((ByteArrayOutputStream)os).toByteArray()));
		}
		if (otype!=CodeImage.DATA)
		{
			if (okind==0 && otype==CodeImage.C)
				pw.println("};");
			pw.println();
			pw.flush();
			pw.close();
		}
		else
		{
			File ngp = new File("mplayerc.ngp");
			if (!ngp.exists())
				throw new Exception("Can not open : mplayerc.ngp. File is missing.");
			byte[] buf = new byte[(int)ngp.length()];
			InputStream is = new BufferedInputStream(new FileInputStream(ngp));
			is.read(buf);
			is.close();
			
			byte[] data = ((ByteArrayOutputStream)os).toByteArray();
			if (data.length>2000000)
				throw new Exception("Movie too long : "+data.length+">2000000");
			int i,m;
			// find video offset
			for (i=0;i<buf.length;i++)
			{
				if (   buf[i]==0x11 && buf[i+1]==0x22 && buf[i+2]==0x33 && buf[i+3]==0x44
					&& buf[i+4]==0x55 && buf[i+5]==0x66 && buf[i+6]==0x77 && buf[i+7]==(byte)0x88)
					 break;
			}
			// find music offset
			for (m=0;m<buf.length;m++)
			{
				if (   buf[m]==(byte)0xaa && buf[m+1]==(byte)0xbb && buf[m+2]==(byte)0xcc &&
					   buf[m+3]==(byte)0xdd && buf[m+4]==(byte)0xee && buf[m+5]==(byte)0xff)
					 break;
			}
			// replace video data
			for (int j=0;j<data.length;j++)
				buf[i++] = data[j];
			// replace music data
			File mus = new File(musName);
			if (!mus.exists())
				throw new Exception("Can not open : "+mus.getCanonicalPath()+". File is missing.");
			byte[] music = new byte[(int)mus.length()];
			if (music.length>1355)
				throw new Exception("Music too long : "+music.length+">1355");
			is = new BufferedInputStream(new FileInputStream(mus));
			is.read(music);
			is.close();
			for (int j=0;j<music.length;j++)
				buf[m++] = music[j];

			// Save new rom
			OutputStream fos = new BufferedOutputStream(new FileOutputStream("mymovie.ngp"));
			fos.write(buf);
			fos.close();
		}
		if (okind==0)
			System.err.println("AVG colors = "+(cols/count)+" AVG diff = "+(diffs/count));
		else
			System.err.println("Colors = "+cols+" AVG diff = "+(diffs/count));
	}
}
