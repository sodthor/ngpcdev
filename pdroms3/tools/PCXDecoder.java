import java.awt.Image;
import java.io.File;
import java.io.FileInputStream;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.awt.image.BufferedImage;

public class PCXDecoder
{
	
	public static Image getImage(String fileName) throws IOException
	{
		File f = new File(fileName);
		byte[] buf = new byte[(int)f.length()];
		BufferedInputStream bis = new BufferedInputStream(new FileInputStream(f));
		bis.read(buf);
		bis.close();
		return decode(buf);
	}

	private static int offset;
	
	private static int btoi(byte b)
	{
		int a = b;
		return (a<0?256+a:a);
	}
	
	private static int readInt(byte[] buf) {
		int res = btoi(buf[offset]) + (btoi(buf[offset+1])<<8) + (btoi(buf[offset+2])<<16) + (btoi(buf[offset+3])<<24);
		offset+=4;
		return res;
	}
	
	private static int readShort(byte[] buf) {
		int res = btoi(buf[offset]) + (btoi(buf[offset+1])<<8);
		offset+=2;
		return res;
	}
	
	private static int readByte(byte[] buf) {
		return btoi(buf[offset++]);
	}
	
	private static int makecol(int r,int g,int b)
	{
if ((r==115)&&(g==109)&&(b==181)) return 0;
		return (255<<24)+(r<<16)+(g<<8)+b;
	}

	public static Image decode(byte[] buf) throws IOException
	{
		offset = 4;
		int x = readShort(buf),
		y = readShort(buf),
		width = readShort(buf)-x+1,
		height = readShort(buf)-y+1;
		int bpl;
		byte nbp;
		offset+=53;
		nbp = buf[offset++];	// nb planes
		bpl = readShort(buf);	// BytesPerLine
		offset+=60;
		
		int[] line = new int[bpl*nbp];
		int[] pixels = new int[width*height];
		int i,j,k,l,b;
		for (i=0;i<height;i++)
		{
			for (j=0;j<bpl*nbp;)
			{
				b = readByte(buf);
				if ((b&0xc0)==0xc0)
				{
					l = b&0x3f;
					b = readByte(buf);
					for (k=0;k<l;k++)
						line[j++] = b;
				}
				else
					line[j++] = b;
			}
			for (j=0;j<width;j++)
			{
				if (nbp==1)
				{
					l = (buf.length-768)+line[j]*3;
					pixels[i*width+j] = makecol(buf[l],buf[l+1],buf[l+2]);
				}
				else
					pixels[i*width+j] = makecol(line[j],line[j+bpl],line[j+bpl+bpl]);
			}
		}
		BufferedImage bimg = new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB);
		bimg.setRGB(0,0,width,height,pixels,0,width);
		return bimg;
	}
}
