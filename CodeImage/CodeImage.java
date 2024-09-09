import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FileDialog;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.MediaTracker;
import java.awt.Panel;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.image.BufferedImage;
import java.awt.image.PixelGrabber;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.WindowConstants;
import javax.swing.border.LineBorder;

import sun.awt.image.ToolkitImage;

public class CodeImage extends JFrame implements ActionListener,ItemListener
{
	public static final int ASM  = 0;
	public static final int C    = 1;
	public static final int DATA = 2;

	public static RGB BACKGROUND = new RGB(0,0,0);

	public static final int PSIZE = 4;
	public static final int U = 500;
	
	public static final String bals[] = {"cm","cd","cl","wm","wd","wl"};
	
	/**
	 * Hexa string formating
	 * @param i number to format
	 * @param s size of hexa string
	 * @return the formated string
	 */
	public static String int2Hex(int i,int s) {
		String val = "000"+Integer.toHexString(i);
		return val.substring(val.length()-s);
	}
	
	/**
	 * Transform an image to multiple 8x8 tiles
	 * @param img image to transform
	 * @param v   ArrayList containing all tiles
	 * @return the number of tiles/line
	 */
	public static int doTiles(Image img,ArrayList v)
	{
		MediaTracker tracker = new MediaTracker(new Panel());
		tracker.addImage(img, 0);
		try
		{
			tracker.waitForID(0);
		}
		catch (InterruptedException e)
		{
		}
		int w = img.getWidth(null)/8;
		int h = img.getHeight(null)/8;
		for (int i=0;i<h;i++)
		{
			for (int j=0;j<w;j++)
			{
				BufferedImage bi = new BufferedImage(8,8,BufferedImage.TYPE_INT_ARGB);
				bi.getGraphics().drawImage(img,0,0,8,8,j*8,i*8,j*8+8,i*8+8,null);
				int pix[] = new int[64];
				bi.getRGB(0,0,8,8,pix,0,8);
				v.add(pix);
			}
		}
		return w;
	}
	
	public static void qsort(int[] a,int[] b,int[] c,int[] v,int start,int end)
	{
		int aa,i,j=start,k=end;
		int va;
		if (end<=start)
			return;
		aa = a[start];
		if (start+1==end)
		{
			c[start] = aa;
			return;
		}
		va = v[aa];
		for (i=start+1;i<end;i++)
			b[(v[a[i]]<va)?j++:--k] = a[i];
		c[j] = aa;
		qsort(b,a,c,v,start,j);
		qsort(b,a,c,v,j+1,end);
	}

	/**
	 * Find palettes in source image and separate it in two planes (if 2 planes needed)
	 */
	public static void doPals(Encoding enc,int balance)
	{
		int k;
		ArrayList v = new ArrayList();
		ArrayList w = new ArrayList();
		
		for (int i=0;i<enc.imgs0.size();i++)
		{
			int pix0[] = (int[])enc.imgs0.get(i);
			int pix1[], l = pix0.length;
			if (balance>=0)
			{
				pix1 = new int[l];
				enc.imgs1.add(pix1);
			}
			for (int j=0;j<l;j++)
			{
				RGB rgb = new RGB(pix0[j]);
				Int irgb = new Int(pix0[j]);
				if ((k = v.indexOf(irgb))<0)
				{
					v.add(irgb);
					w.add(new Int(1));
				}
				else
					((Int)w.get(k)).value += 1;
			}
		}
		int[] colors = new int[v.size()];
		int[] a = new int[v.size()];
		int[] b = new int[v.size()];
		int[] c = new int[v.size()];
		RGB col = new RGB(0);
		for (int i=0;i<colors.length;i++)
		{
			col.setRGB(((Int)v.get(i)).value);
			colors[i] = col.red+col.green+col.blue;
			a[i] = i;
		}

		qsort(a,c,b,colors,0,colors.length);
		
		ArrayList vv = new ArrayList(colors.length);
		int[] ww = new int[colors.length];
		for (int i=0;i<colors.length;i++)
		{
			vv.add((Int)v.get(b[i]));
			ww[i] = (i>0?ww[i-1]:0) + ((Int)w.get(b[i])).value;
		}
		int lim = 0;
		if (balance<0) // One plane : no balance
			lim = colors.length;
		else if (balance<3) // Color
		{
			switch(balance)
			{
			case 0 : lim = colors.length/2;     break; // middle
			case 1 : lim = colors.length/3;     break; // dark
			case 2 : lim = (2*colors.length)/3; break; // light
			}
		}
		else
		{
			int wbalance;
			switch(balance)
			{
			case 4 : wbalance = ww[ww.length-1]/3;     break; // dark
			case 5 : wbalance = (2*ww[ww.length-1])/3; break; // light
			case 3 :
			default: wbalance = ww[ww.length-1]/2;     break; // middle
			}
			while (ww[lim]<wbalance)
				lim++;
		}
		
		int[] pix0,pix1=null;
		Int irgb = new Int(0);
		
		for (int i=0;i<enc.imgs0.size();i++)
		{
			ArrayList pal0 = new ArrayList();
			ArrayList weight0 = new ArrayList();
			ArrayList pal1 = null,weight1 = null;
			
			enc.palIdx0.add(new Int(i));
			enc.pals0.add(pal0);
			enc.weights0.add(weight0);
			pal0.add(BACKGROUND);
			weight0.add(new Int(0));
			pix0 = (int[])enc.imgs0.get(i);
			
			if (balance>=0)
			{
				pal1 = new ArrayList();
				weight1 = new ArrayList();
				enc.palIdx1.add(new Int(i));
				enc.pals1.add(pal1);
				enc.weights1.add(weight1);
				pal1.add(BACKGROUND);
				weight1.add(new Int(0));
				pix1 = (int[])enc.imgs1.get(i);
			}
			
			ArrayList pal,weight;
			int[] pixa,pixb;
			
			for (int j=0;j<pix0.length;j++)
			{
				RGB rgb = new RGB(pix0[j]);
				irgb.value = pix0[j];
				if (balance<0 || vv.indexOf(irgb)<lim)
				{
					pal = pal0;
					weight = weight0;
					pixa = pix0;
					pixb = pix1;
				}
				else
				{
					pal = pal1;
					weight = weight1;
					pixa = pix1;
					pixb = pix0;
				}
				if ((k=pal.indexOf(rgb))<0)
				{
					pixa[j] = pal.size();
					if (pixb!=null)
						pixb[j] = 0;
					pal.add(rgb);
					weight.add(new Int(1));
				}
				else
				{
					((Int)weight.get(k)).value+=1;
					pixa[j] = k;
					if (pixb!=null)
						pixb[j] = 0;
				}
			}
		}
	}
	
	/**
	 * Replace a color by an other one in all affected tiles
	 */
	public static void replaceCol(ArrayList pal,ArrayList weight,ArrayList imgs,int a,int b,boolean update)
	{
		for (int k=0;k<imgs.size();k++)
		{
			int[] img = (int[])imgs.get(k);
			for (int i=0;i<img.length;i++)
			{
				if (img[i]==b)
					img[i] = a;
				if (update && img[i]>b)
					img[i] -= 1;
			}
		}
		if (update)
		{
			pal.remove(b);
			((Int)weight.get(a)).value+=((Int)weight.get(b)).value;
			weight.remove(b);
		}
	}
	
	/**
	 * Replace a double colors in a palette
	 */
	public static void removeDblColor(ArrayList pal,ArrayList weight,ArrayList imgs)
	{
		for (int i=0;i<pal.size();i++)
		{
			Object o = pal.get(i);
			for (int j=i+1;j<pal.size();) {
				if (o.equals(pal.get(j))) {
					replaceCol(pal,weight,imgs,i,j,true);
				} else {
					j += 1;
				}
			}
		}
	}
	
	/**
	 * Reduce the number of colors in a palette (and color dithering on tiles)
	 */
	public static void reducePal(ArrayList pal,ArrayList weight,ArrayList imgs,int V,int pass)
	{
		Int a = new Int(0), b = new Int(0);
		while (pal.size()>PSIZE)
		{
			a.value = 0;
			nearestColor(pal,weight,a,b,V,1);
			// compute average color
			if (a.value>0)
			{
				int wa = ((Int)weight.get(a.value)).value;
				int wb = ((Int)weight.get(b.value)).value;
				((RGB)pal.get(a.value)).merge((RGB)pal.get(b.value),wa,wb,U,V);
			}
			replaceCol(pal,weight,imgs,a.value,b.value,true);
		}
		removeDblColor(pal,weight,imgs);
	}
	
	/**
	 * Replace palette indices in tiles
	 */
	private static void replaceIdx(ArrayList pals,ArrayList palIdx,ArrayList weights,int i,int j)
	{
		int l = palIdx.size();
		for (int k=i+1;k<l;k++)
		{
			Int ii = (Int)palIdx.get(k);
			if (ii.value==j)
				ii.value = i;
			if (ii.value>j)
				ii.value -= 1;
		}
		pals.remove(j);
		weights.remove(j);
	}
	
	/**
	 * Merge 2 palettes and update tiles
	 */
	public static void mergePal(ArrayList imgs,ArrayList pals,ArrayList palIdx,ArrayList weights,int a,int b,int V,int pass)
	{
		ArrayList p0 = (ArrayList)pals.get(a);
		ArrayList p1 = (ArrayList)pals.get(b);
		ArrayList w0 = (ArrayList)weights.get(a);
		ArrayList w1 = (ArrayList)weights.get(b);
		ArrayList iv = new ArrayList();
		ArrayList iv1 = new ArrayList();
		for (int i=0;i<imgs.size();i++)
		{
			int j = ((Int)palIdx.get(i)).value;
			int[] img = (int[])imgs.get(i);
			if (j==b)
			{
				iv1.add(img);
				for (int k=0;k<img.length;k++)
					img[k]+=16;
			}
			if (j==a || j==b)
				iv.add(img);
		}
		int l = p1.size(),j;
		for (int i=0;i<l;i++)
		{
			if ((j=p0.indexOf(p1.get(i)))<0)
			{
				j = p0.size();
				p0.add(p1.get(i));
				w0.add(w1.get(i));
			}
			else
				((Int)w0.get(j)).value += ((Int)w1.get(i)).value;
			replaceCol(null,null,iv1,j,i+16,false);
		}
		reducePal(p0,w0,iv,V,pass);
		replaceIdx(pals,palIdx,weights,a,b);
	}
	
	/**
	 * Add 2 palettes
	 */
	public static void addPal(ArrayList p0,ArrayList p1,ArrayList w0,ArrayList w1)
	{
		int l = p1.size(),j;
		for (int i=0;i<l;i++)
		{
			if ((j=p0.indexOf(p1.get(i)))<0)
			{
				p0.add(p1.get(i));
				w0.add(w1.get(i));
			}
			else
				((Int)w0.get(j)).value += ((Int)w1.get(i)).value;
		}
	}
	
	/**
	 * Find the nearest color according to rgb and weight
	 */
	public static long nearestColor(ArrayList p,ArrayList w,Int a,Int b,int V,int start)
	{
		long d = Long.MAX_VALUE,dj;
		RGB ci,c = new RGB(0),cj;
		int l=p.size(),wi,wj;
		for (int i=1;i<l-1;i++)
		{
			// find nearest color
			ci = (RGB)p.get(i);
			wi = ((Int)w.get(i)).value;
			for (int j=i+1;j<l;j++)
			{
				cj = (RGB)p.get(j);
				wj = ((Int)w.get(j)).value;
				c.setRGB(ci.value);
				c.merge(cj,wi,wj,U,V);
				dj = c.dist(ci)*wi+c.dist(cj)*wj;
				if (dj<d)
				{
					d = dj;
					a.value = i;
					b.value = j;
				}
			}
		}
		return d;
	}

	public static boolean samePals(ArrayList p0,ArrayList p1) {
		int n = 0;
		int l = p0.size();
		for (int i = 0; i < l; ++i)
		{
			if (p1.indexOf(p0.get(i)) >= 0)
				n += 1;
		}
		if (n == l)
			return true;
		n = 0;
		l = p1.size();
		for (int i = 0; i < l; ++i)
		{
			if (p0.indexOf(p1.get(i)) >= 0)
				n += 1;
		}
		return n == l;
	}

	/**
	 * Compute the distance between 2 palettes
	 */
	public static long dist(ArrayList p0,ArrayList p1,ArrayList w0,ArrayList w1,int V,int pass)
	{
		if (samePals(p0, p1)) {
			return 0;
		}
		ArrayList p = (ArrayList)p0.clone();
		int l = w0.size();
		ArrayList w = new ArrayList(l);
		for (int i=0;i<l;i++)
			w.add(new Int(((Int)w0.get(i)).value));
		addPal(p,p1,w,w1);
		long v=0;
		Int a = new Int(0);
		Int b = new Int(0);
		while (p.size()>PSIZE)
		{
			nearestColor(p,w,a,b,V,1);
			RGB cb = (RGB) p.get(b.value);
			RGB ca = (RGB) p.get(a.value);
			int wb = ((Int) w.get(b.value)).value;
			int wa = ((Int) w.get(a.value)).value;
			p.remove(b.value);
			w.remove(b.value);
			if (a.value > 0) {
				p.remove(a.value);
				w.remove(a.value);
				RGB c = new RGB(ca.value);
				c.merge(cb, wa, wb, U, V);
				int pos = p.indexOf(c);
				if (pos > 0) {
					((Int) w.get(pos)).value += wa + wb;
				} else {
					p.add(c);
					w.add(new Int(wa+wb));
				}
			} else {
				((Int) w.get(0)).value += wb;
			}
		}
		v += dist(p0, w0, V, p, w);
		v += dist(p1, w1, V, p, w);
		return v;
	}

	public static long dist(ArrayList p0, ArrayList w0, int V, ArrayList p, ArrayList w) {
		RGB c = new RGB(0);
		long ret = 0;
		for (int i = 1; i < p0.size(); ++i) {
			// find nearest color
			RGB ci = (RGB)p0.get(i);
			int wi = ((Int)w0.get(i)).value;
			long d = Integer.MAX_VALUE;
			for (int j=1;j<p.size();j++)
			{
				RGB cj = (RGB)p.get(j);
				int wj = ((Int)w.get(j)).value;
				c.setRGB(ci.value);
				c.merge(cj,wi,wj,U,V);
				long dj = c.dist(ci)*wi+c.dist(cj)*wj;
				if (dj<d)
				{
					d = dj;
				}
			}
			ret += d;
		}
		return ret;
	}
	
	/**
	 * Compute a plane from source to ngpc
	 */
	public static void doPlane(ArrayList imgs,ArrayList pals,ArrayList palIdx,ArrayList weights,CodeImage frame,int pass,int V,Int cont,int psize)
	{
		ArrayList vi = new ArrayList(1);
		
		for (int i=0;i<pals.size();i++)
		{
			vi.add(imgs.get(i));
			reducePal((ArrayList)pals.get(i),(ArrayList)weights.get(i),vi,V,pass);
			vi.remove(0);
		}

		int l,l0 = pals.size();
		
		int[][] dists = new int[l0][l0];
		for (int i=0;i<l0;i++)
		{
			ArrayList pi = (ArrayList)pals.get(i);
			ArrayList wi = (ArrayList)weights.get(i);
			for (int j=i+1;j<l0;j++)
			{
				ArrayList pj = (ArrayList)pals.get(j);
				ArrayList wj = (ArrayList)weights.get(j);
				dists[i][j] = dists[j][i] = (int) dist(pi,pj,wi,wj,V,pass);
			}
		}
		
		l0-=15;
		while ((l=pals.size())>psize && (cont.value==1))
		{
			//System.err.println(pals.size());

			int a=-1,b=-1;
			long d=Long.MAX_VALUE;
			// find nearest pals
			for (int i=0;i<l;i++)
			{
				for (int j=i+1;j<l;j++)
				{
					if (dists[i][j]<d)
					{
						a = i;
						b = j;
						d = dists[i][j];
					}
				}
			}
			if (frame!=null)
				frame.setProgress("Pass "+pass+" : "+(l0-l+15)+"/"+l0);
			mergePal(imgs,pals,palIdx,weights,a,b,V,pass);
			
			for (int i=0;i<l;i++)
				for (int j=b+1;j<l;j++)
					dists[i][j-1] = dists[i][j];
			for (int i=b+1;i<l;i++)
				System.arraycopy(dists[i],0,dists[i-1],0,l-1);
			ArrayList pa = (ArrayList)pals.get(a);
			ArrayList wa = (ArrayList)weights.get(a);
			for (int i=0;i<l-1;i++)
			{
				if (i!=a)
					dists[i][a] = dists[a][i] = (int) dist((ArrayList)pals.get(i),pa,(ArrayList)weights.get(i),wa,V,pass);
			}
		}
	}
	
	private static class Encoding
	{
		ArrayList imgs0    = new ArrayList();
		ArrayList pals0    = new ArrayList();
		ArrayList palIdx0  = new ArrayList();
		ArrayList weights0 = new ArrayList();
		ArrayList imgs1    = new ArrayList();
		ArrayList pals1    = new ArrayList();
		ArrayList palIdx1  = new ArrayList();
		ArrayList weights1 = new ArrayList();
	}
	
	/**
	 * Encode an image to ngpc .hh or .inc format (main procedure)
	 */
	public static void encode(String name,String id,int contrast,int planes,int balance,int nbpals,Int nbcol,Int diff,JButton orig,JButton ngpc,CodeImage frame,OutputStream out,Int cont,int otype,boolean force,boolean reduce,boolean flip,boolean resize) throws Exception
	{
		encode(loadImage(name),id,contrast,planes,balance,nbpals,nbcol,diff,orig,ngpc,frame,out,cont,otype,force,reduce,flip, resize);
	}
	
	public static void encode(Image image,String id,int contrast,int planes,int balance,int nbpals,Int nbcol,Int diff,JButton orig,JButton ngpc,CodeImage frame,OutputStream out,Int cont,int otype,boolean force,boolean reduce,boolean flip,boolean resize) throws Exception
	{
		int wi = image.getWidth(null);
		int hi = image.getHeight(null);
		if ((wi>160 || hi > 152) && resize)
		{
			double fw = 160.0/wi,fh = 152.0/hi;
			fw = Math.min(fw,fh);
			int bw = (int)(wi*fw);
			int bh = (int)(hi*fw);
			int[] rgb = new int[wi * hi];
			((BufferedImage)image).getRGB(0, 0, wi, hi, rgb, 0, wi);
			for (int i = 0; i < rgb.length; ++i)
				rgb[i] = ((rgb[i] & 0xffffff) == (BACKGROUND.value & 0xffffff)) ? 0 : (0xff000000 | rgb[i]);
			image = new BufferedImage(wi, hi, BufferedImage.TYPE_INT_ARGB);
			((BufferedImage)image).setRGB(0, 0, wi, hi, rgb, 0, wi);
			Color background = new Color(BACKGROUND.value);
			Image scale = image.getScaledInstance(bw, bh, Image.SCALE_SMOOTH);
			rgb = new int[bw * bh];
			PixelGrabber grabber = new PixelGrabber(scale, 0, 0, bw, bh, true);
			try {
				grabber.grabPixels();
		    } catch (InterruptedException e) {
		        System.err.println(e.getMessage());
		    }
		    rgb = (int[]) grabber.getPixels();
			for (int i = 0; i < rgb.length; ++i)
				rgb[i] = (rgb[i] == 0 ? BACKGROUND.value : rgb[i]) | 0xff000000;
			BufferedImage bi = new BufferedImage(bw, bh, BufferedImage.TYPE_INT_ARGB);
			bi.setRGB(0, 0, bw, bh, rgb, 0, bw);
			image = bi;
			wi = bw;
			hi = bh;
		}

		Encoding enc = new Encoding();

		int line = doTiles(image,enc.imgs0);
		int h = enc.imgs0.size()/line;

		if ((!force) && (line*h*planes)>512)
			throw new Exception("Image too big : "+line+"*"+h+"*"+planes+"="+(line*h*planes)+" > 512");

		System.err.println((force?"Logical ":"") + "Tile Count: " + (line*h*planes) + " (" + line + "x" + h + ")");
		encode(enc,contrast,planes,balance,frame,cont,nbpals);

		if (cont.value==0)
			return;

		if (frame!=null)
			frame.setProgress("Generating...");

		generateOut(id,enc,line,h,planes,out,out,out,otype,force,reduce,flip);

		if (frame!=null)
			frame.setProgress("");

		BufferedImage bi = new BufferedImage(wi,hi,BufferedImage.TYPE_INT_ARGB);
		bi.getGraphics().drawImage(image,0,0,null);

		showResult(enc,line,h,planes,nbpals,nbcol,diff,orig,ngpc,frame,bi);
	}
	
	/**
	 *
	 */
	private static void encode(Encoding enc,int contrast,int planes,int balance,CodeImage frame,Int cont,int psize) throws Exception
	{
		
		if (frame!=null)
			frame.setProgress("Analysing...");
		
		doPals(enc,planes==1?-1:balance);

		if (cont.value==0)
			return;
		
		doPlane(enc.imgs0,enc.pals0,enc.palIdx0,enc.weights0,frame,1,contrast,cont,psize);
		
		if (cont.value==0)
			return;
		
		if (planes>1)
			doPlane(enc.imgs1,enc.pals1,enc.palIdx1,enc.weights1,frame,2,contrast,cont,psize);
		
	}
	
	/**
	 * Write a 16bits integer to output stream
	 */
    public static void write16(OutputStream os,int data) throws Exception
    {
    	os.write(data&0xff);
    	os.write(data>>8);
    }

	/**
	 * Generate output data (ASM/C/BIN)
	 */
	private static void generateOut(String id,Encoding enc,int line,int h,int planes,OutputStream out,OutputStream out1,OutputStream out2,int otype,boolean force,boolean reduce, boolean flip) throws Exception
	{
		String name = id;
		PrintWriter pw = null,pw1 = null,pw2 = null;
		
		if (otype!=DATA)
		{
			pw = new PrintWriter(out);
			pw1 = id==null&&out1!=null?new PrintWriter(out1):pw;
			pw2 = id==null&&out2!=null?new PrintWriter(out2):pw;
		}
		
		ArrayList<int[]> tiles = null;
		int emptyId = -1;
		if (reduce) {
			tiles = new ArrayList<int[]>();
			if (!flip) {
				tiles.add(new int[64]); // add empty tile at 0
				emptyId = 0;
			}
			for (int i=0;i<enc.imgs0.size();i++)
			{
				int[] img = (int[])enc.imgs0.get(i);
				if (index(tiles, img) < 0)
				{
					tiles.add(img);
					if (flip)
					{
						tiles.add(buildTile(img, true, false));
						tiles.add(buildTile(img, false, true));
						tiles.add(buildTile(img, true, true));
						if (emptyId < 0) {
							boolean empty = true;
							for (int j = 0; empty && j < img.length; ++j) {
								empty &= img[j] == 0;
							}
							if (empty) {
								emptyId = (tiles.size() - 4) >> 2;
							}
						}
					}
				}
			}
			if (planes>1)
			{
				for (int i=0;i<enc.imgs1.size();i++)
				{
					int[] img = (int[])enc.imgs1.get(i);
					if (index(tiles, img) < 0)
					{
						tiles.add(img);
						if (flip)
						{
							tiles.add(buildTile(img, true, false));
							tiles.add(buildTile(img, false, true));
							tiles.add(buildTile(img, true, true));
							if (emptyId < 0) {
								boolean empty = true;
								for (int j = 0; empty && j < img.length; ++j) {
									empty &= img[j] == 0;
								}
								if (empty) {
									emptyId = (tiles.size() - 4) >> 2;
								}
							}
						}
					}
				}
			}
		}

		if (id!=null)
		{
			name = name.toUpperCase()+"_";
			if (otype==ASM)
			{
				pw.println(name+"WIDTH\tEQU\t"+line);
				pw.println(name+"HEIGHT\tEQU\t"+h);
				pw.println(name+"TILES_COUNT\tEQU\t"+(reduce?(tiles.size()/(flip?4:1)):(line*h*planes)));
				if (reduce && emptyId >= 0) {
					pw.println(name+"EMPTY\tEQU\t"+emptyId);
				}
				pw.println();
				pw.println(name+"NPALS1\tEQU\t"+enc.pals0.size());
				if (planes>1)
					pw.println(name+"NPALS2\tEQU\t"+enc.pals1.size());
			}
			else // C
			{
				if (!force)
				{
					pw.println("#include \"img.h\"");
					pw.println();
				}
				
				pw.println("#define "+name+"WIDTH "+line);
				pw.println("#define "+name+"HEIGHT "+h);
				pw.println("#define "+name+"TILES_COUNT "+(reduce?(tiles.size()/(flip?4:1)):(line*h*planes)));
				if (reduce && emptyId >= 0) {
					pw.println("#define "+name+"EMPTY "+emptyId);
				}
				pw.println();
				pw.println("#define "+name+"NPALS1 "+enc.pals0.size());
				if (planes>1)
					pw.println("#define "+name+"NPALS2 "+enc.pals1.size());
			}
			pw.println();
		}
		else
		{
			if (otype!=DATA)
				pw.println((otype==ASM?";":"//")+" Line "+h);
		}
		
		String suffix = reduce ? "TILES" : "TILES1";
		String hexPref=null,hexSuf=null,linebPref=null,linewPref=null;
		switch(otype)
		{
			case ASM:
				if (id!=null)
					pw.println(name+suffix);
				hexPref = "0";
				hexSuf = "h";
				linewPref = "\tdw\t";
				linebPref = id==null ? "\tdw\t":"\tdb\t";
				break;
			case C:
				if (id!=null)
					pw.println("const u16 "+name+suffix+"["+(reduce?(tiles.size()*(flip?2:8)):(line*h*8))+"] = {");
				hexPref = "0x";
				hexSuf = "";
				linewPref = "";
				linebPref = "";
				break;
			case DATA:
				break;
		}

		if (!reduce)
		{
			for (int i=0;i<enc.imgs0.size();i++)
			{
				int[] img = (int[])enc.imgs0.get(i);
				if (otype!=DATA)
					pw.print(linewPref);
				for (int j=0;j<8;j++)
				{
					int v = 0;
					for (int k=0;k<8;k++)
						v = (v<<2)+img[j*8+k];
					if (otype!=DATA)
					{
						String tsep = otype==ASM?(j<7?",":""):((id==null||j<7||i+1<enc.imgs0.size())?",":"");
						pw.print(hexPref+int2Hex(v,4)+hexSuf+tsep+(j<7?"":"\n"));
					}
					else
						write16(out,v);
				}
			}
			if (id!=null)
			{
				if (otype==C)
					pw.println("};");
				pw.println();
			}
			
			if (planes>1)
			{
				if (id!=null)
				{
					if (otype==ASM)
						pw1.println(name+"TILES2");
					else
						pw1.println("const u16 "+name+"TILES2["+(line*h*8)+"] = {");
				}
				else
					pw1.println((otype==ASM?";":"//")+" Line "+h);
				for (int i=0;i<enc.imgs1.size();i++)
				{
					int[] img = (int[])enc.imgs1.get(i);
					pw1.print(linewPref);
					for (int j=0;j<8;j++)
					{
						int v = 0;
						for (int k=0;k<8;k++)
							v = (v<<2)+img[j*8+k];
						String tsep = otype==ASM?(j<7?",":""):((id==null||j<7||i+1<enc.imgs1.size())?",":"");
						pw1.print(hexPref+int2Hex(v,4)+hexSuf+tsep+(j<7?"":"\n"));
					}
				}
				if (id!=null)
				{
					if (otype==C)
						pw1.println("};");
					pw1.println();
				}
			}
		}
		else
		{
			// tileset
			for (int i=0;i<tiles.size();i+=(flip?4:1))
			{
				int[] img = tiles.get(i);
				if (otype!=DATA)
					pw.print(linewPref);
				for (int j=0;j<8;j++)
				{
					int v = 0;
					for (int k=0;k<8;k++)
						v = (v<<2)+img[j*8+k];
					if (otype!=DATA)
					{
						String tsep = otype==ASM?(j<7?",":""):((id==null||j<7||i+(flip?4:1)<tiles.size())?",":"");
						pw.print(hexPref+int2Hex(v,4)+hexSuf+tsep+(j<7?"":"\n"));
					}
					else
						write16(out,v);
				}
			}
			if (id!=null)
			{
				if (otype==C)
					pw.println("};");
				pw.println();
			}
			//tile index
			int l = enc.imgs0.size();
			if (id!=null)
			{
				if (otype==ASM)
					pw1.println(name+"IDX1");
				else
					pw1.println("const u16 "+name+"IDX1["+l+"] = {");
			}
			for (int i=0;i<l;i++)
			{
				int[] img = (int[])enc.imgs0.get(i);
				int idx = index(tiles, img);
				if (i%line == 0) {
					pw.print(otype==ASM?linewPref:"");
				}
				if (flip)
					pw1.print(idx/4);
				else
					pw1.print(idx);
				if ((i+1)%line == 0) {
					pw.println(otype==ASM?"":(i+1==l?"":","));
				} else {
					pw.print(i+1==l?"":",");
				}
			}
			if (id!=null)
			{
				if (otype==C)
					pw.println("};");
				pw.println();
			}
			if (planes>1)
			{
				if (id!=null)
				{
					if (otype==ASM)
						pw1.println(name+"IDX2");
					else
						pw1.println("const u16 "+name+"IDX2["+l+"] = {");
				}
				for (int i=0;i<l;i++)
				{
					int[] img = (int[])enc.imgs1.get(i);
					int idx = index(tiles, img);
					if ((i%line) == 0) {
						pw.print(otype==ASM?linewPref:"");
					}
					if (flip)
						pw1.print(idx/4);
					else
						pw1.print(idx);
					if (((i+1)%line) == 0) {
						pw.println(otype==ASM?"":(i+1==l?"":","));
					} else {
						pw.print(i+1==l?"":",");
					}
				}
				if (id!=null)
				{
					if (otype==C)
						pw.println("};");
					pw.println();
				}
			}
			//tile flip
			if (flip)
			{
				if (id!=null)
				{
					if (otype==ASM)
						pw1.println(name+"FLIP1");
					else
						pw1.println("const u16 "+name+"FLIP1["+l+"] = {");
				}
				for (int i=0;i<l;i++)
				{
					int[] img = (int[])enc.imgs0.get(i);
					int idx = index(tiles, img);
					if (i%line == 0) {
						pw.print(otype==ASM?linewPref:"");
					}
					pw1.print(hexPref+int2Hex((idx%4)<<14,4)+hexSuf);
					if ((i+1)%line == 0) {
						pw.println(otype==ASM?"":(i+1==l?"":","));
					} else {
						pw.print(i+1==l?"":",");
					}
				}
				if (id!=null)
				{
					if (otype==C)
						pw.println("};");
					pw.println();
				}
				if (planes>1)
				{
					if (id!=null)
					{
						if (otype==ASM)
							pw1.println(name+"FLIP2");
						else
							pw1.println("const u16 "+name+"FLIP2["+l+"] = {");
					}
					for (int i=0;i<l;i++)
					{
						int[] img = (int[])enc.imgs1.get(i);
						int idx = index(tiles, img);
						if ((i%line) == 0) {
							pw.print(otype==ASM?linewPref:"");
						}
						pw1.print(hexPref+int2Hex((idx%4)<<14,4)+hexSuf);
						if (((i+1)%line) == 0) {
							pw.println(otype==ASM?"":(i+1==l?"":","));
						} else {
							pw.print(i+1==l?"":",");
						}
					}
					if (id!=null)
					{
						if (otype==C)
							pw.println("};");
						pw.println();
					}
				}
			}
		}

		if (id!=null)
		{
			if (otype==ASM)
				pw1.println(name+"PALS1");
			else
				pw1.println("const u16 "+name+"PALS1["+(enc.pals0.size()*4)+"] = {");
		}
		
		for (int i=0;i<enc.pals0.size();i++)
		{
			ArrayList pal = (ArrayList)enc.pals0.get(i);
			if (otype!=DATA)
				pw1.print(linewPref);
			for (int j=0;j<4;j++)
			{
				int v = j<pal.size()?((RGB)pal.get(j)).ngp:0;
				if (otype!=DATA)
				{
					String tsep = otype==ASM?(j<3?",":""):((id==null||j<3||i<enc.pals0.size()-1)?",":"");
					pw1.print(hexPref+int2Hex(v,4)+hexSuf+tsep);
				}
				else
					write16(out,v);
			}
			if (otype!=DATA)
				pw1.println();
		}
		if (id!=null)
		{
			if (otype==C)
				pw1.println("};");
			pw1.println();
		}
		
		if (planes>1)
		{
			if (id!=null)
			{
				if (otype==ASM)
					pw1.println(name+"PALS2");
				else
					pw1.println("const u16 "+name+"PALS2["+(enc.pals1.size()*4)+"] = {");
			}
			for (int i=0;i<enc.pals1.size();i++)
			{
				ArrayList pal = (ArrayList)enc.pals1.get(i);
				pw1.print(linewPref);
				for (int j=0;j<4;j++)
				{
					int v = j<pal.size()?((RGB)pal.get(j)).ngp:0;
					String tsep = otype==ASM?(j<3?",":""):((id==null||j<3||i<enc.pals1.size()-1)?",":"");
					pw1.print(hexPref+int2Hex(v,4)+hexSuf+tsep);
				}
				pw1.println();
			}
			if (id!=null)
			{
				if (otype==C)
					pw1.println("};");
				pw1.println();
			}
		}
		
		int ii = otype==ASM?(id==null?10:8):line;
		int b = h%2>0?20:0;
		int c = h%2>0?8:0;
		if (enc.pals0.size() > 1) {
			if (id!=null)
			{
				if (otype==ASM)
					pw2.println(name+"PALIDX1");
				else
					pw2.println("const u8 "+name+"PALIDX1["+(line*h)+"] = {");
			}
			for (int i=0;i<enc.imgs0.size();i++)
			{
				if (otype==ASM && (i%ii)==0)
					pw2.print(linebPref);
				String lsep = ((i+1)%ii>0?"":"\n");
				String tsep = ((id==null&&(h<18||planes>1))||i<enc.imgs0.size()-1)&&((otype!=ASM)||((i+1)%ii>0))?",":"";
				int a = ((Int)enc.palIdx0.get(i)).value;
				if (id!=null)
					pw2.print(hexPref+int2Hex(a,2)+hexSuf+tsep+lsep);
				else
				{
					if (planes==2)
					{
						a+=(h%2)>0?8:0;
						int v = (a<<9)+i+(planes>1?40+(h*20):0);
						pw2.print(hexPref+int2Hex(v,4)+hexSuf+tsep+lsep);
					}
					else
					{
						int v = ((a+c)<<9)+i+b;
						if (otype!=DATA)
							pw2.print(hexPref+int2Hex(v,4)+hexSuf+tsep+lsep);
						else
							write16(out,v);
					}
				}
			}
			if (id!=null)
			{
				if (otype==C)
					pw2.println("};");
				pw2.println();
			}
		}
		
		if (planes>1 && enc.pals1.size() > 1)
		{
			if (id!=null)
			{
				if (otype==ASM)
					pw2.println(name+"PALIDX2");
				else
					pw2.println("const u8 "+name+"PALIDX2["+(line*h)+"] = {");
			}
			for (int i=0;i<enc.imgs1.size();i++)
			{
				if (otype==ASM && (i%ii)==0)
					pw2.print(linebPref);
				String lsep = ((i+1)%ii>0?"":"\n");
				String tsep = ((id==null&&h<18)||i<enc.imgs1.size()-1)&&((otype!=ASM)||((i+1)%ii>0))?",":"";
				int a = ((Int)enc.palIdx1.get(i)).value;
				if (id!=null)
					pw2.print(hexPref+int2Hex(a,2)+hexSuf+tsep+lsep);
				else
				{
					int v = ((a+c)<<9)+i+b;
					pw2.print(hexPref+int2Hex(v,4)+hexSuf+tsep+lsep);
				}
			}
			if (id!=null)
			{
				if (otype==C)
					pw2.println("};");
				pw2.println();
			}
		}
		
		if (id!=null && !force)
		{
			if (otype==ASM)
			{
				pw.println(name+"ID");
				pw.println(linebPref+name+"WIDTH"+","+name+"HEIGHT");
				pw.println(linewPref+name+"TILES");
				pw.println("\tdd\t"+name+"TILES1"+","+(planes>1?name+"TILES2":"0"));
				pw.println("\tdd\t"+name+"PALIDX1"+","+(planes>1?name+"PALIDX2":"0"));
				pw.println(linewPref+name+"NPALS1");
				pw.println(linewPref+(planes>1?name+"NPALS2":"0"));
				pw.println("\tdd\t"+name+"PALS1");
				pw.println("\tdd\t"+(planes>1?name+"PALS2":"0"));
			}
			else
			{
				pw.println("#define "+name+"ID {"+
						name+"WIDTH"+","+
						name+"HEIGHT"+","+
						name+"TILES_COUNT"+
						",(u16*)"+name+"TILES"+(reduce?"":"1")+
						",(u16*)"+(planes>1 && !reduce?name+"TILES2":"NULL")+
						","+name+"NPALS1"
						+",(u16*)"+name+"PALS1"+","+
						(planes>1?name+"NPALS2":"0")+
						",(u16*)"+(planes>1?name+"PALS2":"NULL")+
						",(u8*)"+name+"PALIDX1"+
						",(u8*)"+(planes>1?name+"PALIDX2":"NULL")+
						",(u16*)"+(reduce?name+"IDX1":"NULL")+
						",(u16*)"+(planes>1 && reduce?name+"IDX2":"NULL")+
						",(u16*)"+(reduce && flip?name+"FLIP1":"NULL")+
						",(u16*)"+(planes>1 && reduce && flip?name+"FLIP2":"NULL")+
						","+emptyId+
						"}");
				pw.println();
				pw.println("const SOD_IMG "+name+"IMG = "+name+"ID;");
			}
			pw.println();
		}
		else
		{
			if (otype!=DATA)
			{
				pw1.flush();
				pw2.flush();
			}
		}
		if (otype!=DATA)
			pw.flush();
	}
	
	/**
	 *
	 */
	private static void showResult(Encoding enc,int line,int h,int planes,int nbpal,Int nbcol,Int diff,JButton orig,JButton ngpc,CodeImage frame,BufferedImage bi)
	{
		ArrayList v = new ArrayList();
		for (int i=0;i<enc.pals0.size();i++)
		{
			ArrayList pal = (ArrayList)enc.pals0.get(i);
			for (int j=0;j<pal.size();j++)
			{
				Integer irgb = new Integer(((RGB)pal.get(j)).ngp);
				if (!v.contains(irgb))
					v.add(irgb);
			}
		}
		if (planes>1)
		{
			for (int i=0;i<enc.pals1.size();i++)
			{
				ArrayList pal = (ArrayList)enc.pals1.get(i);
				for (int j=0;j<pal.size();j++)
				{
					Integer irgb = new Integer(((RGB)pal.get(j)).ngp);
					if (!v.contains(irgb))
						v.add(irgb);
				}
			}
		}
		nbcol.value = v.size();
		
		BufferedImage img = new BufferedImage(line*8,h*8,BufferedImage.TYPE_INT_ARGB);
		
		diff.value = 0;
		
		RGB rgbi = new RGB(0);
		RGB rgbn = new RGB(0);
		int empty0 = 0, empty1 = 0;
		ArrayList<int[]> tiles = new ArrayList<int[]>();
		int duplicated = 0;
		for (int i=0;i<h;i++)
		{
			for (int j=0;j<line;j++)
			{
				int[] pix0 = (int[])enc.imgs0.get(i*line+j),pix1=null;
				if (index(tiles, pix0) < 0) {
					tiles.add(pix0);
					tiles.add(buildTile(pix0, true, false));
					tiles.add(buildTile(pix0, false, true));
					tiles.add(buildTile(pix0, true, true));
				} else {
					duplicated += 1;
				}
				ArrayList pal0 = (ArrayList)enc.pals0.get(((Int)enc.palIdx0.get(i*line+j)).value),pal1 = null;
				if (planes>1)
				{
					pal1 = (ArrayList)enc.pals1.get(((Int)enc.palIdx1.get(i*line+j)).value);
					pix1 = (int[])enc.imgs1.get(i*line+j);
					if (index(tiles, pix1) < 0) {
						tiles.add(pix1);
						tiles.add(buildTile(pix1, true, false));
						tiles.add(buildTile(pix1, false, true));
						tiles.add(buildTile(pix1, true, true));
					} else {
						duplicated += 1;
					}
				}
				int n0 = 0, n1 = 0;
				for (int k=0;k<8;k++)
				{
					for (int l=0;l<8;l++)
					{
						int p0 = pix0[k*8+l];
						int rgb0 = ((RGB)pal0.get(p0)).rgb();
						int rgb;
						if (planes>1)
						{
							int p1 = pix1[k*8+l];
							int rgb1 = ((RGB)pal1.get(p1)).rgb();
							rgb = p1==0?rgb0:rgb1;
							if (p1 == 0) {
								n1 += 1;
							}
						}
						else {
							rgb = rgb0;
						}
						if (p0 == 0) {
							n0 += 1;
						}
						img.setRGB(j*8+l,i*8+k,rgb);
						rgbn.setRGB(rgb);
						rgbi.setRGB(bi.getRGB(j*8+l,i*8+k));
						diff.value+=rgbi.dist(rgbn);
					}
				}
				if (n0 == 64) {
					empty0 += 1;
				}
				if (n1 == 64) {
					empty1 += 1;
				}
			}
		}
		System.err.println("Empty tiles plan 1: " + empty0);
		if (planes > 1)
			System.err.println("Empty tiles plan 2: " + empty1);
		System.err.println("Duplicated tiles: " + duplicated);
		System.err.println("Min tile count: " + (line*h*planes-duplicated));
		
		if (orig==null)
		{
			/**
			JFrame f = new JFrame();
			f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
			f.getContentPane().add(new JButton(new ImageIcon(bi)));
			f.getContentPane().add(new JButton(new ImageIcon(img)));
			f.pack();
			f.setVisible(true);
			**/
			return;
		}
		ImageIcon icon = new ImageIcon(bi);
		orig.setIcon(icon);
		icon = new ImageIcon(img);
		ngpc.setIcon(icon);
	}
	
	private static int index(ArrayList<int[]> tiles, int[] tile) {
		for (int i = 0; i < tiles.size(); ++i) {
			int[] t = tiles.get(i);
			if (Arrays.equals(t, tile)) {
				return i;
			}
		}
		return -1;
	}

	private static int[] buildTile(int[] tile, boolean vflip, boolean hflip) {
		int[] t = new int[64];
		for (int x = 0; x < 8; ++x) {
			for (int y = 0; y < 8; ++y) {
				t[x+y*8] = tile[(hflip?7-x:x)+(vflip?7-y:y)*8];
			}
		}
		return t;
	}

	/**
	 * Encode an image to ngpc hicolor format
	 */
	public static void encodeHC(String name,String id,int contrast,int planes,int balance,Int nbcol,Int diff,JButton orig,JButton ngpc,CodeImage frame,OutputStream out,Int cont,int otype,boolean max) throws Exception
	{
		if (frame!=null)
			frame.setProgress("Generating...");
		
		diff.value = 0;
		Image image = loadImage(name);
		int wi = image.getWidth(null);
		int hi = image.getHeight(null);
		BufferedImage bi = new BufferedImage(160,152,BufferedImage.TYPE_INT_ARGB);
		bi.getGraphics().setColor(Color.black);
		bi.getGraphics().clearRect(0,0,160,152);
		if (!max && wi<=160 && hi<=152)
			bi.getGraphics().drawImage(image,(160-wi)/2,(152-hi)/2,null);
		else
		{
			double fw = 160.0/wi,fh = 152.0/hi;
			if (max)
				fw = Math.max(fw,fh);
			else
				fw = Math.min(fw,fh);
			int w = (int)(wi*fw);
			int h = (int)(hi*fw);
			int x = (160-w)/2;
			int y = (152-h)/2;
			bi.getGraphics().drawImage(image,x,y,x+w-1,y+h-1,0,0,wi-1,hi-1,null);
		}
		
		_encodeHC(bi,id,contrast,planes,balance,nbcol,diff,out,cont,otype,true);
		
		if (frame!=null)
			frame.setProgress("Done.");
		
		if (orig==null)
			return;
		ImageIcon icon = new ImageIcon(bi);
		ngpc.setIcon(icon);
	}
	
	public static void _encodeHC(BufferedImage bi,String id,int contrast,int planes,int balance,Int nbcol,Int diff,OutputStream out,Int cont,int otype,boolean full) throws Exception
	{
		
		ArrayList vimg= new ArrayList(20*19);
		doTiles(bi,vimg);
		
		PrintWriter pw = null;
		ByteArrayOutputStream out1 = null;
		ByteArrayOutputStream out2 = null;
		if (otype!=DATA)
		{
			pw = new PrintWriter(out);
		 	out1 = planes==1?null:new ByteArrayOutputStream();
		 	out2 = planes==1?null:new ByteArrayOutputStream();
		}
		
		String name = id.toUpperCase()+"_";
		if (otype!=DATA)
		{
			if (otype==ASM)
				pw.println(name+"ID");
			else
			{
				if (full)
					pw.println("const u16 "+name+"ID["+((20*19*planes*8)+(8*4*19*planes)+(19*20*planes))+"] = {");
				else
				{
					pw.println("// "+name);
					pw.println("{");
				}
			}
			pw.flush();
		}
		
		ArrayList colors = new ArrayList();
		
		for (int i=0;i<19;i++)
		{
			Encoding enc = new Encoding();
			for (int j=0;j<20;j++)
			{
				enc.imgs0.add(vimg.get(0));
				vimg.remove(0);
			}
			encode(enc,contrast,planes,balance,null,cont,8);
			generateOut(null,enc,20,i,planes,out,out1,out2,otype,false,false,false);
			showResultHC(enc,i,planes,nbcol,diff,bi,colors);
		}
		if (planes>1)
		{
			if (otype!=DATA)
			{
				pw.println((otype==ASM?"":"// ")+name+"DYN");
				pw.print(new String(out1.toString()));
				pw.println((otype==ASM?"":"// ")+name+"IDX");
				pw.print(new String(out2.toString()));
			}
		}
		if (otype==ASM)
		{
			if (otype!=DATA)
			{
				pw.println("END_"+name+"ID");
				pw.println();
				if (planes>1)
				{
					pw.println(name+"DATA");
					pw.println("\tdd\t"+name+"ID,"+name+"DYN,"+name+"IDX");
				}
			}
		}
		else
		{
			if (otype!=DATA)
				pw.println("}"+(full?";":""));
		}
		if (otype!=DATA)
		{
			pw.flush();
			pw.close();
		}

		nbcol.value = colors.size();
	}
	
	private static void showResultHC(Encoding enc,int h,int planes,Int nbcol,Int diff,BufferedImage bi,ArrayList v)
	{
		for (int i=0;i<enc.pals0.size();i++)
		{
			ArrayList pal = (ArrayList)enc.pals0.get(i);
			for (int j=0;j<pal.size();j++)
			{
				Integer irgb = new Integer(((RGB)pal.get(j)).ngp);
				if (!v.contains(irgb))
					v.add(irgb);
			}
		}
		if (planes>1)
		{
			for (int i=0;i<enc.pals1.size();i++)
			{
				ArrayList pal = (ArrayList)enc.pals1.get(i);
				for (int j=0;j<pal.size();j++)
				{
					Integer irgb = new Integer(((RGB)pal.get(j)).ngp);
					if (!v.contains(irgb))
						v.add(irgb);
				}
			}
		}
		
		RGB rgbi = new RGB(0);
		RGB rgbn = new RGB(0);
		
		for (int j=0;j<20;j++)
		{
			int[] pix0 = (int[])enc.imgs0.get(j),pix1=null;
			ArrayList pal0 = (ArrayList)enc.pals0.get(((Int)enc.palIdx0.get(j)).value),pal1 = null;
			if (planes>1)
			{
				pal1 = (ArrayList)enc.pals1.get(((Int)enc.palIdx1.get(j)).value);
				pix1 = (int[])enc.imgs1.get(j);
			}
			for (int k=0;k<8;k++)
			{
				for (int l=0;l<8;l++)
				{
					int p0 = pix0[k*8+l];
					int rgb0 = ((RGB)pal0.get(p0)).rgb(),rgb1,rgb;
					if (planes>1)
					{
						int p1 = pix1[k*8+l];
						rgb1 = ((RGB)pal1.get(p1)).rgb();
						rgb = p1==0?rgb0:rgb1;
					}
					else
						rgb = rgb0;
					rgbi.setRGB(bi.getRGB(j*8+l,h*8+k));
					bi.setRGB(j*8+l,h*8+k,rgb);
					rgbn.setRGB(rgb);
					diff.value+=rgbi.dist(rgbn);
				}
			}
		}
	}
	
	/******/
	/* UI */
	/******/
	
	JTextField tf_name;
	JComboBox cb_contrast;
	JComboBox cb_planes;
	JComboBox cb_balance;
	JComboBox cb_nbpals;
	JButton jb_load;
	JButton jb_go;
	JButton jb_stop;
	JButton jb_original;
	JButton jb_ngpc;
	JButton jb_save;
	JButton jb_quit;
	JTextField tf_params;
	JTextField tf_colors;
	JTextField tf_diff;
	JTextField tf_best;
	JLabel jl_progress;
	JCheckBox cb_asm;
	JCheckBox cb_hicolor;
	JCheckBox cb_max;
	JCheckBox cb_resize;
	JCheckBox cb_force;
	JCheckBox cb_reduce;
	JCheckBox cb_flip;
	JComboBox cb_bgr;
	JComboBox cb_bgg;
	JComboBox cb_bgb;
	JLabel jl_bg;
	ByteArrayOutputStream output;
	Thread thread;
	Int best;
	
	public CodeImage()
	{
		super();
		JPanel main = (JPanel)getContentPane();
		main.setLayout(new BorderLayout());
		JPanel p,pp,p0;
		main.add(p = new JPanel(new BorderLayout(4,4)),BorderLayout.NORTH);
		
		p.add(pp = new JPanel(new GridLayout(14,1,4,2)),BorderLayout.WEST);
		pp.add(new JLabel("File"));
		pp.add(new JLabel("Contrast"));
		pp.add(new JLabel("Planes"));
		pp.add(new JLabel("Balance"));
		pp.add(new JLabel("Palettes"));
		pp.add(new JLabel("Asm ouput"));
		pp.add(new JLabel("HiColor"));
		pp.add(new JLabel("Max size"));
		pp.add(new JLabel("Force output"));
		pp.add(new JLabel("Resize"));
		pp.add(new JLabel("Reduce tc"));
		pp.add(new JLabel("Reduce with flip"));
		pp.add(new JLabel("Background"));
		pp.add(new JLabel(""));
		
		pp = new JPanel(new GridLayout(14,1,4,2));
		p.add(pp,BorderLayout.CENTER);
		pp.add(tf_name = new JTextField(""));
		tf_name.setEditable(false);
		pp.add(cb_contrast = new JComboBox());
		for (int i=-2;i<10;i++)
			cb_contrast.addItem(String.valueOf(i));
		cb_contrast.setSelectedIndex(3);
		pp.add(cb_planes = new JComboBox());
		cb_planes.addItem("1");
		cb_planes.addItem("2");
		cb_planes.setSelectedIndex(1);
		pp.add(cb_balance = new JComboBox());
		cb_balance.addItem("Color - middle");
		cb_balance.addItem("Color - dark");
		cb_balance.addItem("Color - light");
		cb_balance.addItem("Weight - middle");
		cb_balance.addItem("Weight - dark");
		cb_balance.addItem("Weight - light");
		cb_balance.setSelectedIndex(0);
		pp.add(cb_nbpals = new JComboBox());
		for (int i=1;i<17;i++)
			cb_nbpals.addItem(String.valueOf(i));
		cb_nbpals.setSelectedIndex(15);
		pp.add(cb_asm = new JCheckBox());
		cb_asm.setActionCommand("ASM");
		cb_asm.addActionListener(this);
		pp.add(cb_hicolor = new JCheckBox());
		cb_hicolor.setActionCommand("HICOLOR");
		cb_hicolor.addActionListener(this);
		pp.add(cb_max = new JCheckBox());
		cb_max.setActionCommand("MAX");
		cb_max.addActionListener(this);
		pp.add(cb_force = new JCheckBox());
		cb_force.setActionCommand("FORCE");
		cb_force.addActionListener(this);
		pp.add(cb_resize = new JCheckBox());
		cb_resize.setActionCommand("RESIZE");
		cb_resize.addActionListener(this);
		pp.add(cb_reduce = new JCheckBox());
		cb_reduce.setActionCommand("REDUCE");
		cb_reduce.addActionListener(this);
		pp.add(cb_flip = new JCheckBox());
		cb_flip.setActionCommand("FLIP");
		cb_flip.addActionListener(this);
		JPanel bg = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
		bg.add(new JLabel("R"));
		bg.add(cb_bgr = new JComboBox());
		cb_bgr.addItemListener(this);
		bg.add(new JLabel("G"));
		bg.add(cb_bgg = new JComboBox());
		cb_bgg.addItemListener(this);
		bg.add(new JLabel("B"));
		bg.add(cb_bgb = new JComboBox());
		cb_bgb.addItemListener(this);
		for (int i=0;i<256;i++) {
			cb_bgr.addItem(String.valueOf(i));
			cb_bgg.addItem(String.valueOf(i));
			cb_bgb.addItem(String.valueOf(i));
		}
		bg.add(jl_bg = new JLabel("               "));
		jl_bg.setBorder(new LineBorder(Color.black));
		jl_bg.setOpaque(true);
		jl_bg.setBackground(Color.black);
		pp.add(bg);
		pp.add(jl_progress = new JLabel(""));
		
		pp = new JPanel(new GridLayout(7,1,0,0));
		p.add(pp,BorderLayout.EAST);
		pp.add(p0 = new JPanel(new FlowLayout()));
		p0.add(jb_load = new JButton("Load"));
		pp.add(new JLabel(""));
		pp.add(new JLabel(""));
		pp.add(new JLabel(""));
		pp.add(new JLabel(""));
		pp.add(new JLabel(""));
		pp.add(p0 = new JPanel(new FlowLayout()));
		p0.add(jb_go = new JButton("Go"));
		jb_load.setActionCommand("LOAD");
		jb_load.addActionListener(this);
		jb_go.setEnabled(false);
		jb_go.setActionCommand("GO");
		jb_go.addActionListener(this);
		p0.add(jb_stop = new JButton("Stop"));
		jb_stop.setEnabled(false);
		jb_stop.setActionCommand("STOP");
		jb_stop.addActionListener(this);
		
		main.add(p = new JPanel(new FlowLayout()),BorderLayout.CENTER);
		p.add(jb_original = new JButton("ORIGINAL"));
		jb_original.setActionCommand("LOAD");
		jb_original.addActionListener(this);
		jb_original.setVerticalTextPosition(SwingConstants.BOTTOM);
		jb_original.setHorizontalTextPosition(SwingConstants.CENTER);
		p.add(jb_ngpc = new JButton("NGPC"));
		jb_ngpc.setActionCommand("GO");
		jb_ngpc.addActionListener(this);
		jb_ngpc.setEnabled(false);
		jb_ngpc.setVerticalTextPosition(SwingConstants.BOTTOM);
		jb_ngpc.setHorizontalTextPosition(SwingConstants.CENTER);
		
		main.add(p = new JPanel(new BorderLayout(4,4)),BorderLayout.SOUTH);
		pp = new JPanel(new GridLayout(5,1,4,4));
		p.add(pp,BorderLayout.WEST);
		pp.add(new JLabel("Cmd line"));
		pp.add(new JLabel("Colors"));
		pp.add(new JLabel("Diff"));
		pp.add(new JLabel("Best"));
		pp.add(new JLabel(""));
		pp = new JPanel(new GridLayout(5,1,4,4));
		p.add(pp,BorderLayout.CENTER);
		pp.add(tf_params = new JTextField(""));
		tf_params.setEditable(false);
		pp.add(tf_colors = new JTextField(""));
		tf_colors.setEditable(false);
		pp.add(tf_diff = new JTextField(""));
		tf_diff.setEditable(false);
		pp.add(tf_best = new JTextField(""));
		tf_best.setEditable(false);
		pp.add(new JLabel(""));
		pp = new JPanel(new GridLayout(5,1,0,0));
		p.add(pp,BorderLayout.EAST);
		pp.add(new JLabel(""));
		pp.add(new JLabel(""));
		pp.add(new JLabel(""));
		pp.add(new JLabel(""));
		pp.add(p0 = new JPanel(new FlowLayout()));
		p0.add(jb_save = new JButton("Save"));
		jb_save.setEnabled(false);
		jb_save.setActionCommand("SAVE");
		jb_save.addActionListener(this);
		p0.add(jb_quit = new JButton("Quit"));
		jb_quit.setEnabled(true);
		jb_quit.setActionCommand("QUIT");
		jb_quit.addActionListener(this);
		
		cb_contrast.addItemListener(this);
		cb_planes.addItemListener(this);
		cb_balance.addItemListener(this);
		
		setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
		setSize(new Dimension(700,840));
		center(this);
		setVisible(true);
		//setResizable(false);
		setTitle("NGPC Image converter");
	}
	
	static void center(Window w)
	{
		Dimension d = Toolkit.getDefaultToolkit().getScreenSize();
		Dimension r = w.getSize();
		w.setLocation(d.width/2-r.width/2,d.height/2-r.height/2);
	}
	
	public void itemStateChanged(ItemEvent e)
	{
		cb_balance.setEnabled(cb_planes.getSelectedIndex()>0);
		resetCtl();
	}
	
	private void resetCtl()
	{
		if (jb_ngpc != null) {
			jb_ngpc.setIcon(null);
			jb_ngpc.setDisabledIcon(null);
		}
		if (jb_save != null) {
			jb_save.setEnabled(false);
		}
		if (tf_params != null) {
			tf_params.setText("");
		}
		if (tf_colors != null) {
			tf_colors.setText("");
		}
		if (tf_diff != null) {
			tf_diff.setText("");
		}
		if (jl_bg != null) {
			jl_bg.setBackground(new Color(cb_bgr.getSelectedIndex(),cb_bgg.getSelectedIndex(),cb_bgb.getSelectedIndex()));
		}
	}
	
	public static Image loadImage(String path) throws IOException
	{
		return ImageIO.read(new File(path));
	}
	
	public void actionPerformed(ActionEvent e)
	{
		String cmd = e.getActionCommand();
		if (cmd.equals("LOAD"))
		{
			FileDialog fd = new FileDialog(this,"Load image",FileDialog.LOAD);
			fd.setVisible(true);
			if (fd.getFile()==null)
				return;
			tf_name.setText(fd.getDirectory()+fd.getFile());
			best = new Int(-1);
			try
			{
				Image img = loadImage(fd.getDirectory()+fd.getFile());
				if (img.getWidth(null)>240)
				{
					float f = ((float)img.getWidth(null))/240;
					int h = (int)(img.getHeight(null)/f);
					BufferedImage bi = new BufferedImage(240,h,BufferedImage.TYPE_INT_ARGB);
					bi.getGraphics().drawImage(img,0,0,240,h,0,0,img.getWidth(null),img.getHeight(null),null);
					img = bi;
				}
				ImageIcon icon = new ImageIcon(img);
				jb_original.setIcon(icon);
				jb_original.setDisabledIcon(null);
				jb_go.setEnabled(true);
				jb_ngpc.setEnabled(true);
				resetCtl();
			}
			catch(Exception ex)
			{
				tf_name.setText("");
				setProgress("Can't load image.");
				jb_original.setIcon(null);
				jb_original.setDisabledIcon(null);
				jb_go.setEnabled(false);
				jb_ngpc.setEnabled(false);
				jb_save.setEnabled(false);
			}
			tf_diff.setText("");
			tf_colors.setText("");
			tf_params.setText("");
			tf_best.setText("");
			jb_save.setEnabled(false);
		}
		else if (cmd.equals("GO"))
		{
			thread = new Thread()
			{
				Int cont;
				
				private void setEnabled(boolean b)
				{
					jb_go.setEnabled(b);
					jb_save.setEnabled(b&&cont.value==1);
					jb_original.setEnabled(b);
					jb_ngpc.setEnabled(b);
					jb_stop.setEnabled(!b);
					cb_contrast.setEnabled(b);
					cb_balance.setEnabled(cb_planes.getSelectedIndex()>0);
					cb_planes.setEnabled(b);
				}
				
				public void run()
				{
					setEnabled(false);
					setPriority(MIN_PRIORITY);
					try
					{
						Int nbcol = new Int(0);
						Int diff = new Int(0);
						BACKGROUND = new RGB(jl_bg.getBackground().getRGB());
						cont = new Int(1);
						output = new ByteArrayOutputStream();
						String id = fromName(tf_name.getText());
						if (cb_hicolor.isSelected())
							encodeHC(tf_name.getText(),id,
									U-Integer.valueOf((String)cb_contrast.getSelectedItem()).intValue(),
									cb_planes.getSelectedIndex()+1,
									cb_balance.getSelectedIndex(),
									nbcol,diff,jb_original,jb_ngpc,CodeImage.this,output,cont,cb_asm.isSelected()?ASM:C,cb_max.isSelected());
						else
							encode(tf_name.getText(),id,
									U-Integer.valueOf((String)cb_contrast.getSelectedItem()).intValue(),
									cb_planes.getSelectedIndex()+1,
									cb_balance.getSelectedIndex(),
									Integer.valueOf((String)cb_nbpals.getSelectedItem()).intValue(),
									nbcol,diff,jb_original,jb_ngpc,CodeImage.this,output,cont,cb_asm.isSelected()?ASM:C,
									cb_force.isSelected(),cb_reduce.isSelected(),cb_flip.isSelected(),cb_resize.isSelected());
						if (cont.value>0)
						{
							tf_colors.setText(String.valueOf(nbcol.value));
							tf_diff.setText(String.valueOf(diff.value));
							tf_params.setText("java CodeImage \""+tf_name.getText()+"\" "+id+" -p"+cb_planes.getSelectedItem()+" -c"+cb_contrast.getSelectedItem()+(cb_balance.isEnabled()?" -b"+bals[cb_balance.getSelectedIndex()]:"")+(cb_nbpals.isEnabled()?" -n"+cb_nbpals.getSelectedItem():"")+(cb_asm.isSelected()?" -a":"")+(cb_hicolor.isSelected()?" -h":""));
							if (best.value<0 || diff.value<best.value)
								tf_best.setText("Diff="+(best.value=diff.value)+" Contrast="+cb_contrast.getSelectedItem()+" Balance=["+cb_balance.getSelectedItem()+"] Planes="+cb_planes.getSelectedItem()+(cb_hicolor.isSelected()?" HiColor":" Palettes="+cb_nbpals.getSelectedItem()));
						}
						else
						{
							setProgress("Stopped");
						}
					}
					catch(Exception ex)
					{
						setProgress("Error : "+ex.getMessage());
						ex.printStackTrace();
					}
					setEnabled(true);
					thread = null;
				}
				
				public void interrupt()
				{
					cont.value = 0;
				}
			};
			thread.start();
		}
		else if (cmd.equals("STOP"))
		{
			if (thread!=null)
				thread.interrupt();
			thread = null;
		}
		else if (cmd.equals("SAVE"))
		{
			FileDialog fd = new FileDialog(this,"Save",FileDialog.SAVE);
			fd.setVisible(true);
			if (fd.getFile()==null)
				return;
			try
			{
				FileOutputStream fos = new FileOutputStream(fd.getDirectory()+fd.getFile());
				fos.write(output.toByteArray());
				fos.close();
				setProgress("Saved");
			}
			catch(Exception ex)
			{
				setProgress("Error : "+ex.getMessage());
				ex.printStackTrace();
			}
		}
		else if (cmd.equals("ASM"))
		{
			resetCtl();
		}
		else if (cmd.equals("HICOLOR"))
		{
			resetCtl();
		}
		else if (cmd.equals("QUIT"))
			System.exit(0);
	}
	
	public void setProgress(String text)
	{
		jl_progress.setText(text);
		if (!jl_progress.isShowing())
			return;
		Graphics g = jl_progress.getGraphics();
		Dimension d = jl_progress.getSize();
		g.clearRect(0,0,d.width,d.height);
		jl_progress.paint(g);
	}
	
	public static String fromName(String name)
	{
		int p0 = name.lastIndexOf("/")+1;
		if (p0==0)
			p0 = name.lastIndexOf("\\")+1;
		int p1 = name.lastIndexOf(".");
		return name.substring(p0<0?0:p0,p1<0?name.length():p1);
	}
	
	/********/
	/* MAIN */
	/********/
	
	public static void main(String[] args) throws Exception
	{
		Int nbcol = new Int(0);
		Int diff = new Int(0);
		
		if (args.length==0)
		{
			new CodeImage();
			return;
		}
		Thread.currentThread().setPriority(Thread.MIN_PRIORITY);
		int planes = 2;
		int contrast = 1;
		int balance = 0;
		int nbpals = 15;
		int otype = C;
		boolean hc = false;
		String name = null;
		String id = null;
		for (int i=0;i<args.length;i++)
		{
			if (args[i].charAt(0)!='-')
			{
				if (name==null)
					name = args[i];
				else
					id = args[i];
				continue;
			}
			String pref = args[i].substring(0,2);
			String suffix = args[i].substring(2);
			if (pref.equals("-c"))
				contrast = Integer.valueOf(suffix).intValue();
			else if (pref.equals("-b"))
			{
				int j;
				for (j=0;j<bals.length && !suffix.equals(bals[j]);j++);
				if (j<bals.length)
					balance = j;
			}
			else if (pref.equals("-p"))
				planes = Integer.valueOf(suffix).intValue();
			else if (pref.equals("-n"))
				nbpals = Integer.valueOf(suffix).intValue();
			else if (pref.equals("-a"))
				otype = ASM;
			else if (pref.equals("-h"))
				hc = true;
		}
		if (id==null)
			id = fromName(name);
		OutputStream out = new FileOutputStream(id+(otype==ASM?".inc":".hh"));
		if (hc)
			encodeHC(name,id,U-contrast,planes,balance,nbcol,diff,null,null,null,out,(new Int(1)),otype,false);
		else
			encode(name,id,U-contrast,planes,balance,nbpals,nbcol,diff,null,null,null,out,(new Int(1)),otype,false,false,false,false);
		System.err.println("Colors="+nbcol.value);
		System.err.println("Diff="+diff.value);
		System.exit(0);
	}
}
