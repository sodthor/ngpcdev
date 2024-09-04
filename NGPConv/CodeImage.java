import java.io.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.*;
import java.util.*;
import javax.swing.*;

public class CodeImage extends JFrame implements ActionListener,ItemListener
{
  public static RGB BLACK = new RGB(0,0,0);

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
   * @param v   vector containing all tiles
   * @return the number of tiles/line
   */
  public static int doTiles(Image img,Vector v)
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
            v.addElement(pix);
        }
    }
    return w;
  }

  /**
   * Heap sort
   */
  public static void hsort(int[] a,int[] b,int[] c,int N) {
    for (int k = N/2; k > 0; k--)
        downheap(a,c,k,N);
    do {
        b[--N] = a[0];
        a[0] = a[N];
        downheap(a,c,1, N);
    } while (N > 1);
  }

  /**
   * Heap sort tool
   */
  private static void downheap(int[] a,int[] c,int k, int N) {
    int T = a[k - 1],N2 = N/2;
    float cT = c[T];
    while (k <= N2) {
        int j = k + k;
        if ((j < N) && (c[a[j - 1]] < c[a[j]])) j++;
        if (cT >= c[a[j - 1]]) break;
        else {
           a[k - 1] = a[j - 1];
           k = j;
        }
    }
    a[k - 1] = T;
  }

  /**
   * Find palettes in source image and separate it in two planes (if 2 planes needed)
   */
  public static void doPals(Encoding enc,int balance)
  {
    int k;
    Vector v = new Vector();
    Vector w = new Vector();

    for (int i=0;i<enc.imgs0.size();i++)
    {
        int pix0[] = (int[])enc.imgs0.elementAt(i);
        int pix1[], l = pix0.length;
        if (balance>=0)
        {
           pix1 = new int[l];
	   enc.imgs1.addElement(pix1);
        }
        for (int j=0;j<l;j++)
        {
            RGB rgb = new RGB(pix0[j]);
            Int irgb = new Int(pix0[j]);
            if ((k = v.indexOf(irgb))<0)
            {
               v.addElement(irgb);
               w.addElement(new Int(1));
            }
            else
               ((Int)w.elementAt(k)).value += 1;
        }
    }
    int[] colors = new int[v.size()];
    int[] a = new int[v.size()];
    int[] b = new int[v.size()];
    RGB col = new RGB(0);
    for (int i=0;i<colors.length;i++)
    {
        col.setRGB(((Int)v.elementAt(i)).value);
        colors[i] = col.red+col.green+col.blue;
        a[i] = i;
    }

    hsort(a,b,colors,colors.length);

    Vector vv = new Vector(colors.length);
    int[] ww = new int[colors.length];
    for (int i=0;i<colors.length;i++)
    {
        vv.addElement((Int)v.elementAt(b[i]));
        ww[i] = (i>0?ww[i-1]:0) + ((Int)w.elementAt(b[i])).value;
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
        Vector pal0 = new Vector();
        Vector weight0 = new Vector();
        Vector pal1 = null,weight1 = null;

        enc.palIdx0.addElement(new Int(i));
        enc.pals0.addElement(pal0);
        enc.weights0.addElement(weight0);
        pal0.addElement(BLACK);
        weight0.addElement(new Int(0));
        pix0 = (int[])enc.imgs0.elementAt(i);

        if (balance>=0)
        {
           pal1 = new Vector();
           weight1 = new Vector();
           enc.palIdx1.addElement(new Int(i));
           enc.pals1.addElement(pal1);
           enc.weights1.addElement(weight1);
           pal1.addElement(BLACK);
           weight1.addElement(new Int(0));
           pix1 = (int[])enc.imgs1.elementAt(i);
        }

        Vector pal,weight;
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
               pal.addElement(rgb);
               weight.addElement(new Int(1));
            }
            else
            {
               if (k>0)
                  ((Int)weight.elementAt(k)).value+=1;
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
  public static void replaceCol(Vector pal,Vector weight,Vector imgs,int a,int b,boolean update)
  {
    for (int k=0;k<imgs.size();k++)
    {
        int[] img = (int[])imgs.elementAt(k);
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
       pal.removeElementAt(b);
       if (a>0)
          ((Int)weight.elementAt(a)).value+=((Int)weight.elementAt(b)).value;
       weight.removeElementAt(b);
    }
  }

  /**
   * Replace a double colors in a palette
   */
  public static void removeDblColor(Vector pal,Vector weight,Vector imgs)
  {
    for (int i=0;i<pal.size();i++)
    {
        Object o = pal.elementAt(i);
        for (int j=i+1;j<pal.size();j++)
            if (o.equals(pal.elementAt(j)))
               replaceCol(pal,weight,imgs,i,j,true);
    }
  }

  /**
   * Reduce the number of colors in a palette (and color dithering on tiles)
   */
  public static void reducePal(Vector pal,Vector weight,Vector imgs,int V)
  {
    while (pal.size()>PSIZE)
    {
        int l = pal.size();
        RGB ca = null, cb = null;
        long d = Long.MAX_VALUE;
        Int a = new Int(0), b = new Int(0);
        nearestColor(pal,weight,a,b,V);
        // compute average color
        int wa = ((Int)weight.elementAt(a.value)).value;
        int wb = ((Int)weight.elementAt(b.value)).value;
        if (a.value>0)
           ((RGB)pal.elementAt(a.value)).merge((RGB)pal.elementAt(b.value),wa,wb,U,V);
        replaceCol(pal,weight,imgs,a.value,b.value,true);
    }
    removeDblColor(pal,weight,imgs);
  }

  /**
   * Replace palette indices in tiles
   */
  private static void replaceIdx(Vector pals,Vector palIdx,Vector weights,int i,int j)
  {
    int l = palIdx.size();
    for (int k=i+1;k<l;k++)
    {
        Int ii = (Int)palIdx.elementAt(k);
        if (ii.value==j)
           ii.value = i;
        if (ii.value>j)
           ii.value -= 1;
    }
    pals.removeElementAt(j);
    weights.removeElementAt(j);
  }

  /**
   * Merge 2 palettes and update tiles
   */
  public static void mergePal(Vector imgs,Vector pals,Vector palIdx,Vector weights,int a,int b,int V)
  {
    Vector p0 = (Vector)pals.elementAt(a);
    Vector p1 = (Vector)pals.elementAt(b);
    Vector w0 = (Vector)weights.elementAt(a);
    Vector w1 = (Vector)weights.elementAt(b);
    Vector iv = new Vector();
    Vector iv1 = new Vector();
    for (int i=0;i<imgs.size();i++)
    {
        int j = ((Int)palIdx.elementAt(i)).value;
        int[] img = (int[])imgs.elementAt(i);
        if (j==b)
        {
           iv1.addElement(img);
           for (int k=0;k<img.length;k++)
               img[k]+=16;
        }
        if (j==a || j==b)
           iv.addElement(img);
    }
    int l = p1.size(),j;
    for (int i=0;i<l;i++)
    {
        if ((j=p0.indexOf(p1.elementAt(i)))<0)
        {
           j = p0.size();
           p0.addElement(p1.elementAt(i));
           w0.addElement(w1.elementAt(i));
        }
        else
           ((Int)w0.elementAt(j)).value += ((Int)w1.elementAt(i)).value;
        replaceCol(null,null,iv1,j,i+16,false);
    }
    reducePal(p0,w0,iv,V);
    replaceIdx(pals,palIdx,weights,a,b);
  }

  /**
   * Add 2 palettes
   */
  public static void addPal(Vector p0,Vector p1,Vector w0,Vector w1)
  {
    int l = p1.size(),j;
    for (int i=0;i<l;i++)
    {
        if ((j=p0.indexOf(p1.elementAt(i)))<0)
        {
           p0.addElement(p1.elementAt(i));
           w0.addElement(w1.elementAt(i));
        }
        else
           ((Int)w0.elementAt(j)).value += ((Int)w1.elementAt(i)).value;
    }
  }

  /**
   * Find the nearest color according to rgb and weight
   */
  public static long nearestColor(Vector p,Vector w,Int a,Int b,int V)
  {
    long d = Long.MAX_VALUE,dj;
    RGB ci,c = new RGB(0),cj;
    int l=p.size(),wi,wj;
    for (int i=1;i<l-1;i++)
    {
        // find nearest color
        ci = (RGB)p.elementAt(i);
        wi = ((Int)w.elementAt(i)).value;
        for (int j=i+1;j<l;j++)
        {
            cj = (RGB)p.elementAt(j);
            wj = ((Int)w.elementAt(j)).value;
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

  /**
   * Compute the distance between 2 palettes
   */
  public static long dist(Vector p0,Vector p1,Vector w0,Vector w1,int V)
  {
    Vector p = (Vector)p0.clone();
    int l = w0.size();
    Vector w = new Vector(l);
    for (int i=0;i<l;i++)
        w.addElement(new Int(((Int)w0.elementAt(i)).value));
    addPal(p,p1,w,w1);
    long v=0;
    Int a = new Int(0);
    Int b = new Int(0);
    while (p.size()>2)
    {
        v+=nearestColor(p,w,a,b,V);
        p.removeElementAt(b.value);
        p.removeElementAt(a.value);
        w.removeElementAt(b.value);
        w.removeElementAt(a.value);
    }
    return v;
  }

  /**
   * Compute a plane from source to ngpc
   */
  public static void doPlane(Vector imgs,Vector pals,Vector palIdx,Vector weights,CodeImage frame,int pass,int V,Int cont,int psize)
  {
    Vector vi = new Vector(1);

    for (int i=0;i<pals.size();i++)
    {
        vi.addElement(imgs.elementAt(i));
        reducePal((Vector)pals.elementAt(i),(Vector)weights.elementAt(i),vi,V);
        vi.removeElementAt(0);
    }

    int l,l0 = pals.size();

    long[][] dists = new long[l0][l0];
    for (int i=0;i<l0;i++)
    {
        Vector pi = (Vector)pals.elementAt(i);
        Vector wi = (Vector)weights.elementAt(i);
        for (int j=i+1;j<l0;j++)
        {
            Vector pj = (Vector)pals.elementAt(j);
            Vector wj = (Vector)weights.elementAt(j);
            dists[i][j] = dists[j][i] = dist(pi,pj,wi,wj,V);
        }
    }

    l0-=15;
    while ((l=pals.size())>psize && (cont.value==1))
    {
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
        mergePal(imgs,pals,palIdx,weights,a,b,V);

        for (int i=0;i<l;i++)
            for (int j=b+1;j<l;j++) dists[i][j-1] = dists[i][j];
        for (int i=b+1;i<l;i++)
            System.arraycopy(dists[i],0,dists[i-1],0,l-1);
        Vector pa = (Vector)pals.elementAt(a);
        Vector wa = (Vector)weights.elementAt(a);
	for (int i=0;i<l-1;i++)
        {
            if (i!=a)
               dists[i][a] = dists[a][i] = dist((Vector)pals.elementAt(i),pa,(Vector)weights.elementAt(i),wa,V);
        }
    }
  }

  private static class Encoding
  {
    Vector imgs0    = new Vector();
    Vector pals0    = new Vector();
    Vector palIdx0  = new Vector();
    Vector weights0 = new Vector();
    Vector imgs1    = new Vector();
    Vector pals1    = new Vector();
    Vector palIdx1  = new Vector();
    Vector weights1 = new Vector();
  }

  /**
   * Encode an image to ngpc .hh or .inc format (main procedure)
   */
  public static void encode(String name,String id,int contrast,int planes,int balance,Int nbcol,Int diff,JButton orig,JButton ngpc,CodeImage frame,OutputStream out,Int cont,boolean asm) throws Exception
  {
    Image image = loadImage(name);
    Encoding enc = new Encoding();

    int line = doTiles(image,enc.imgs0);
    int h = enc.imgs0.size()/line;

    if (line*h*planes>512)
       throw new Exception("Image too big : "+line+"*"+h+"*"+planes+"="+(line*h*planes)+" > 512");
 
    encode(enc,contrast,planes,balance,frame,cont,15);

    if (cont.value==0)
       return;

    if (frame!=null)
       frame.setProgress("Generating...");

    generateOut(id,enc,line,h,planes,out,out,out,asm);

    if (frame!=null)
       frame.setProgress("");

    int wi = image.getWidth(null);
    int hi = image.getHeight(null);
    BufferedImage bi = new BufferedImage(wi,hi,BufferedImage.TYPE_INT_ARGB);
    bi.getGraphics().drawImage(image,0,0,null);

    showResult(enc,line,h,planes,nbcol,diff,orig,ngpc,frame,bi);
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
   * Generate C or ASM
   */
  private static void generateOut(String id,Encoding enc,int line,int h,int planes,OutputStream out,OutputStream out1,OutputStream out2,boolean asm) throws Exception
  {
    String name = id;
    PrintWriter pw = new PrintWriter(out);
    PrintWriter pw1 = id==null?new PrintWriter(out1):pw;
    PrintWriter pw2 = id==null?new PrintWriter(out2):pw;

    if (id!=null)
    {
       name = name.toUpperCase()+"_";
       if (asm)
       {
          pw.println(name+"WIDTH\tEQU\t"+line);
          pw.println(name+"HEIGHT\tEQU\t"+h);
          pw.println(name+"TILES\tEQU\t"+(line*h*planes));
          pw.println();
          pw.println(name+"NPALS1\tEQU\t"+enc.pals0.size());
          if (planes>1)
             pw.println(name+"NPALS2\tEQU\t"+enc.pals1.size());
       }
       else
       {
          pw.println("#include \"img.h\"");
          pw.println();

          pw.println("#define "+name+"WIDTH "+line);
          pw.println("#define "+name+"HEIGHT "+h);
          pw.println("#define "+name+"TILES "+(line*h*planes));
          pw.println();
          pw.println("#define "+name+"NPALS1 "+enc.pals0.size());
          if (planes>1)
             pw.println("#define "+name+"NPALS2 "+enc.pals1.size());
       }
       pw.println();
    }
    else
       pw.println((asm?";":"//")+" Line "+h);

    String hexPref=null,hexSuf=null,linebPref=null,linewPref=null;
    if (asm)
    {
       if (id!=null)
          pw.println(name+"TILES1");
       hexPref = "0";
       hexSuf = "h";
       linewPref = "\tdw\t";
       linebPref = id==null ? "\tdw\t":"\tdb\t";
    }
    else
    {
       if (id!=null)
          pw.println("const u16 "+name+"TILES1["+(line*h*8)+"] = {");
       hexPref = "0x";
       hexSuf = "";
       linewPref = "";
       linebPref = "";
    }
    for (int i=0;i<enc.imgs0.size();i++)
    {
        int[] img = (int[])enc.imgs0.elementAt(i);
        pw.print(linewPref);
        for (int j=0;j<8;j++)
        {
            int v = 0;
            for (int k=0;k<8;k++)
                 v = (v<<2)+img[j*8+k];
            String tsep = asm?(j<7?",":""):((id==null||j<7||i+1<enc.imgs0.size())?",":"");
            pw.print(hexPref+int2Hex(v,4)+hexSuf+tsep+(j<7?"":"\n"));
        }
    }
    if (id!=null)
    {
       if (!asm)
          pw.println("};");
       pw.println();
    }

    if (planes>1)
    {
       if (id!=null)
       {
          if (asm)
             pw1.println(name+"TILES2");
          else
             pw1.println("const u16 "+name+"TILES2["+(line*h*8)+"] = {");
       }
       else
          pw1.println((asm?";":"//")+" Line "+h);
       for (int i=0;i<enc.imgs1.size();i++)
       {
           int[] img = (int[])enc.imgs1.elementAt(i);
           pw1.print(linewPref);
           for (int j=0;j<8;j++)
           {
               int v = 0;
               for (int k=0;k<8;k++)
                   v = (v<<2)+img[j*8+k];
               String tsep = asm?(j<7?",":""):((id==null||j<7||i+1<enc.imgs1.size())?",":"");
               pw1.print(hexPref+int2Hex(v,4)+hexSuf+tsep+(j<7?"":"\n"));
           }
       }
       if (id!=null)
       {
          if (!asm)
             pw1.println("};");
          pw1.println();
       }
    }

    if (id!=null)
    {
       if (asm)
          pw1.println(name+"PALS1");
       else
          pw1.println("const u16 "+name+"PALS1["+(enc.pals0.size()*4)+"] = {");
    }
    for (int i=0;i<enc.pals0.size();i++)
    {
        Vector pal = (Vector)enc.pals0.elementAt(i);
        pw1.print(linewPref);
        for (int j=0;j<4;j++)
        {
            String tsep = asm?(j<3?",":""):((id==null||j<3||i<enc.pals0.size()-1)?",":"");
            pw1.print(hexPref+int2Hex(j<pal.size()?((RGB)pal.elementAt(j)).ngp:0,4)+hexSuf+tsep);
        }
        pw1.println();
    }
    if (id!=null)
    {
       if (!asm)
          pw1.println("};");
       pw1.println();
    }

    if (planes>1)
    {
       if (id!=null)
       {
          if (asm)
             pw1.println(name+"PALS2");
          else
             pw1.println("const u16 "+name+"PALS2["+(enc.pals1.size()*4)+"] = {");
       }
       for (int i=0;i<enc.pals1.size();i++)
       {
           Vector pal = (Vector)enc.pals1.elementAt(i);
           pw1.print(linewPref);
           for (int j=0;j<4;j++)
           {
               String tsep = asm?(j<3?",":""):((id==null||j<3||i<enc.pals1.size()-1)?",":"");
               pw1.print(hexPref+int2Hex(j<pal.size()?((RGB)pal.elementAt(j)).ngp:0,4)+hexSuf+tsep);
           }
           pw1.println();
       }
       if (id!=null)
       {
          if (!asm)
             pw1.println("};");
          pw1.println();
       }
    }

    if (id!=null)
    {
       if (asm)
          pw2.println(name+"PALIDX1");
       else
          pw2.println("const u8 "+name+"PALIDX1["+(line*h)+"] = {");
    }
    int ii = asm?(id==null?10:8):line;
    for (int i=0;i<enc.imgs0.size();i++)
    {
        if (asm && (i%ii)==0)
           pw2.print(linebPref);
        String lsep = ((i+1)%ii>0?"":"\n");
        String tsep = ((id==null&&(h<18||planes>1))||i<enc.imgs0.size()-1)&&((!asm)||((i+1)%ii>0))?",":"";
        int a = ((Int)enc.palIdx0.elementAt(i)).value;
        if (id!=null)
           pw2.print(hexPref+int2Hex(a,2)+hexSuf+tsep+lsep);
        else
        {
           a+=(h%2)>0?8:0;
           pw2.print(hexPref+int2Hex((a<<9)+(h*20)+i+40,4)+hexSuf+tsep+lsep);
        }
    }
    if (id!=null)
    {
       if (!asm)
          pw2.println("};");
       pw2.println();
    }

    if (planes>1)
    {
       if (id!=null)
       {
          if (asm)
             pw2.println(name+"PALIDX2");
          else
             pw2.println("const u8 "+name+"PALIDX2["+(line*h)+"] = {");
       }
       int b = h%2>0?20:0;
       int c = h%2>0?8:0;
       for (int i=0;i<enc.imgs1.size();i++)
       {
           if (asm && (i%ii)==0)
              pw2.print(linebPref);
           String lsep = ((i+1)%ii>0?"":"\n");
           String tsep = ((id==null&&h<18)||i<enc.imgs1.size()-1)&&((!asm)||((i+1)%ii>0))?",":"";
           int a = ((Int)enc.palIdx1.elementAt(i)).value;
           if (id!=null)
              pw2.print(hexPref+int2Hex(a,2)+hexSuf+tsep+lsep);
           else
              pw2.print(hexPref+int2Hex(((a+c)<<9)+i+b,4)+hexSuf+tsep+lsep);
       }
       if (id!=null)
       {
          if (!asm)
             pw2.println("};");
          pw2.println();
       }
    }

    if (id!=null)
    {
       if (asm)
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
          pw.println("#define "+name+"ID {"+name+"WIDTH"+","+name+"HEIGHT"+","+name+"TILES"+",(u16*)"+name+"TILES1"+",(u16*)"+(planes>1?name+"TILES2":"0")+","+name+"NPALS1"+",(u16*)"+name+"PALS1"+","+(planes>1?name+"NPALS2":"0")+",(u16*)"+(planes>1?name+"PALS2":"0")+",(u8*)"+name+"PALIDX1"+",(u8*)"+(planes>1?name+"PALIDX2":"0")+"}");
          pw.println();
          pw.println("const SOD_IMG "+name+"IMG = "+name+"ID;");
       }
       pw.println();
    }
    else
    {
       pw1.flush();
       pw2.flush();
    }
    pw.flush();
  }

  /**
   *
   */
  private static void showResult(Encoding enc,int line,int h,int planes,Int nbcol,Int diff,JButton orig,JButton ngpc,CodeImage frame,BufferedImage bi)
  {
    Vector v = new Vector();
    for (int i=0;i<enc.pals0.size();i++)
    {
        Vector pal = (Vector)enc.pals0.elementAt(i);
        for (int j=0;j<pal.size();j++)
        {
            Integer irgb = new Integer(((RGB)pal.elementAt(j)).ngp);
            if (!v.contains(irgb))
               v.addElement(irgb);
        }
    }
    if (planes>1)
    {
       for (int i=0;i<enc.pals1.size();i++)
       {
           Vector pal = (Vector)enc.pals1.elementAt(i);
           for (int j=0;j<pal.size();j++)
           {
               Integer irgb = new Integer(((RGB)pal.elementAt(j)).ngp);
               if (!v.contains(irgb))
                  v.addElement(irgb);
           }
       }
    }
    nbcol.value = v.size();

    BufferedImage img = new BufferedImage(line*8,h*8,BufferedImage.TYPE_INT_ARGB);

    diff.value = 0;

    RGB rgbi = new RGB(0);
    RGB rgbn = new RGB(0);

    for (int i=0;i<h;i++)
    {
        for (int j=0;j<line;j++)
        {
            int[] pix0 = (int[])enc.imgs0.elementAt(i*line+j),pix1=null;
            Vector pal0 = (Vector)enc.pals0.elementAt(((Int)enc.palIdx0.elementAt(i*line+j)).value),pal1 = null;
            if (planes>1)
            {
               pal1 = (Vector)enc.pals1.elementAt(((Int)enc.palIdx1.elementAt(i*line+j)).value);
               pix1 = (int[])enc.imgs1.elementAt(i*line+j);
            }
            for (int k=0;k<8;k++)
            {
                for (int l=0;l<8;l++)
                {
                    int rgb0 = ((RGB)pal0.elementAt(pix0[k*8+l])).rgb(),rgb1,rgb;
                    if (planes>1)
                    {
                       rgb1 = ((RGB)pal1.elementAt(pix1[k*8+l])).rgb();
                       rgb = rgb1==0xff000000?rgb0:rgb1;
                    }
                    else
                       rgb = rgb0;
                    img.setRGB(j*8+l,i*8+k,rgb);
                    rgbn.setRGB(rgb);
                    rgbi.setRGB(bi.getRGB(j*8+l,i*8+k));
                    diff.value+=rgbi.dist(rgbn);
                }
            }
        }
    }
    if (orig==null)
       return;
    ImageIcon icon = new ImageIcon(bi);
    orig.setIcon(icon);
    icon = new ImageIcon(img);
    ngpc.setIcon(icon);
  }

  /**
   * Encode an image to ngpc hicolor format
   */
  public static void encodeHC(String name,String id,int contrast,int planes,int balance,Int nbcol,Int diff,JButton orig,JButton ngpc,CodeImage frame,OutputStream out,Int cont,boolean asm) throws Exception
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
    bi.getGraphics().drawImage(image,(160-wi)/2,(152-hi)/2,null);
    Vector vimg= new Vector(20*19);
    doTiles(bi,vimg);

    PrintWriter pw = new PrintWriter(out);
    ByteArrayOutputStream out1 = new ByteArrayOutputStream();
    ByteArrayOutputStream out2 = new ByteArrayOutputStream();

    name = id.toUpperCase()+"_";
    if (asm)
       pw.println(name+"ID");
    else
       pw.println("const u16 "+name+"ID["+((20*19*planes*8)+(8*4*19*planes)+(19*20*planes))+"] = {");
    pw.flush();

    Vector colors = new Vector();

    for (int i=0;i<19;i++)
    {
        Encoding enc = new Encoding();
        for (int j=0;j<20;j++)
        {
            enc.imgs0.addElement(vimg.elementAt(0));
            vimg.removeElementAt(0);
        }
        encode(enc,contrast,planes,balance,null,cont,8);
        generateOut(null,enc,20,i,planes,out,out1,out2,asm);
        showResultHC(enc,i,planes,nbcol,diff,bi,colors);
    }
    pw.println((asm?"":"// ")+name+"DYN");
    pw.print(new String(out1.toString()));
    pw.println((asm?"":"// ")+name+"IDX");
    pw.print(new String(out2.toString()));
    if (asm)
    {
       pw.println("END_"+name+"ID");
       pw.println();
       pw.println(name+"DATA");
       pw.println("\tdd\t"+name+"ID,"+name+"DYN,"+name+"IDX");
    }
    else
       pw.println("};");
    pw.flush();
    pw.close();

    nbcol.value = colors.size();

    if (frame!=null)
       frame.setProgress("Done.");

    if (orig==null)
       return;
    ImageIcon icon = new ImageIcon(bi);
    ngpc.setIcon(icon);
  }

  private static void showResultHC(Encoding enc,int h,int planes,Int nbcol,Int diff,BufferedImage bi,Vector v)
  {
    for (int i=0;i<enc.pals0.size();i++)
    {
        Vector pal = (Vector)enc.pals0.elementAt(i);
        for (int j=0;j<pal.size();j++)
        {
            Integer irgb = new Integer(((RGB)pal.elementAt(j)).ngp);
            if (!v.contains(irgb))
               v.addElement(irgb);
        }
    }
    if (planes>1)
    {
       for (int i=0;i<enc.pals1.size();i++)
       {
           Vector pal = (Vector)enc.pals1.elementAt(i);
           for (int j=0;j<pal.size();j++)
           {
               Integer irgb = new Integer(((RGB)pal.elementAt(j)).ngp);
               if (!v.contains(irgb))
                  v.addElement(irgb);
           }
       }
    }

    RGB rgbi = new RGB(0);
    RGB rgbn = new RGB(0);

    for (int j=0;j<20;j++)
    {
        int[] pix0 = (int[])enc.imgs0.elementAt(j),pix1=null;
        Vector pal0 = (Vector)enc.pals0.elementAt(((Int)enc.palIdx0.elementAt(j)).value),pal1 = null;
        if (planes>1)
        {
           pal1 = (Vector)enc.pals1.elementAt(((Int)enc.palIdx1.elementAt(j)).value);
           pix1 = (int[])enc.imgs1.elementAt(j);
        }
        for (int k=0;k<8;k++)
        {
            for (int l=0;l<8;l++)
            {
                int rgb0 = ((RGB)pal0.elementAt(pix0[k*8+l])).rgb(),rgb1,rgb;
                if (planes>1)
                {
                   rgb1 = ((RGB)pal1.elementAt(pix1[k*8+l])).rgb();
                   rgb = rgb1==0xff000000?rgb0:rgb1;
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

    p.add(pp = new JPanel(new GridLayout(7,1,4,4)),BorderLayout.WEST);
    pp.add(new JLabel("File"));
    pp.add(new JLabel("Contrast"));
    pp.add(new JLabel("Planes"));
    pp.add(new JLabel("Balance"));
    pp.add(new JLabel("Asm ouput"));
    pp.add(new JLabel("HiColor"));
    pp.add(new JLabel(""));

    pp = new JPanel(new GridLayout(7,1,4,4));
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
    pp.add(cb_asm = new JCheckBox());
    cb_asm.setActionCommand("ASM");
    cb_asm.addActionListener(this);
    pp.add(cb_hicolor = new JCheckBox());
    cb_hicolor.setActionCommand("HICOLOR");
    cb_hicolor.addActionListener(this);
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
    setSize(new Dimension(700,700));
    center(this);
    setVisible(true);
    setResizable(false);
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
    jb_ngpc.setIcon(null);
    jb_ngpc.setDisabledIcon(null);
    jb_save.setEnabled(false);
    tf_params.setText("");
    tf_colors.setText("");
    tf_diff.setText("");
  }

  public static Image loadImage(String path)
  {
    Image img = Toolkit.getDefaultToolkit().createImage(path);
    MediaTracker tracker = new MediaTracker(new Panel());
    tracker.addImage(img, 0);
    try
    {
        tracker.waitForID(0);
    }
    catch (InterruptedException ex)
    {
    }
    return img;
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
                 cont = new Int(1);
                 output = new ByteArrayOutputStream();
                 String id = fromName(tf_name.getText());
                 if (cb_hicolor.isSelected())
                    encodeHC(tf_name.getText(),id,
                           U-Integer.valueOf((String)cb_contrast.getSelectedItem()).intValue(),
                           cb_planes.getSelectedIndex()+1,
                           cb_balance.getSelectedIndex(),
                           nbcol,diff,jb_original,jb_ngpc,CodeImage.this,output,cont,cb_asm.isSelected());
                 else
                    encode(tf_name.getText(),id,
                           U-Integer.valueOf((String)cb_contrast.getSelectedItem()).intValue(),
                           cb_planes.getSelectedIndex()+1,
                           cb_balance.getSelectedIndex(),
                           nbcol,diff,jb_original,jb_ngpc,CodeImage.this,output,cont,cb_asm.isSelected());
                 if (cont.value>0)
                 {
                    tf_colors.setText(String.valueOf(nbcol.value));
                    tf_diff.setText(String.valueOf(diff.value));
                    tf_params.setText("java CodeImage \""+tf_name.getText()+"\" "+id+" -p"+cb_planes.getSelectedItem()+" -c"+cb_contrast.getSelectedItem()+(cb_balance.isEnabled()?" -b"+bals[cb_balance.getSelectedIndex()]:"")+(cb_asm.isSelected()?" -a":"")+(cb_hicolor.isSelected()?" -h":""));
                    if (best.value<0 || diff.value<best.value)
                       tf_best.setText("Diff="+(best.value=diff.value)+" Contrast="+cb_contrast.getSelectedItem()+" Balance=["+cb_balance.getSelectedItem()+"] Planes="+cb_planes.getSelectedItem()+(cb_hicolor.isSelected()?" HiColor":""));
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
    boolean asm = false;
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
        else if (pref.equals("-a"))
           asm = true;
        else if (pref.equals("-h"))
           hc = true;
    }
    if (id==null)
       id = fromName(name);
    OutputStream out = new FileOutputStream(id+(asm?".inc":".hh"));
    if (hc)
       encodeHC(name,id,U-contrast,planes,balance,nbcol,diff,null,null,null,out,(new Int(1)),asm);
    else
       encode(name,id,U-contrast,planes,balance,nbcol,diff,null,null,null,out,(new Int(1)),asm);
    System.err.println("Colors="+nbcol.value);
    System.err.println("Diff="+diff.value);
    System.exit(0);
  }
}
