import java.awt.*;
import java.awt.image.*;
import java.io.*;
import java.util.*;

public class CodeSprites
{
  public static String int2Hex(int i,int s) {
    String val = "000"+Integer.toHexString(i);
    return val.substring(val.length()-s);
  }

  private static boolean compPals(Vector a,Vector b)
  {
    int i = 0;
    while (i<4 && ((Integer)a.elementAt(i)).compareTo((Integer)b.elementAt(i))==0)
        i++;
    return i==4;
  }

  private static int rgb2ngp(int col)
  {
    return ((col&0xf0)<<4)+((col&0xf000)>>8)+((col&0xf00000)>>20);
  }

  public static void saveSprites(Vector imgs,Vector pals,Vector palIdx,PrintWriter pw) throws Exception
  {
    int pix[] = new int[64];
    for (int i=0;i<imgs.size();i++)
    {
        ((BufferedImage)imgs.elementAt(i)).getRGB(0,0,8,8,pix,0,8);
        Vector pal = new Vector(4);
        /* Compute palette */
        pal.addElement(new Integer(rgb2ngp(pix[0])));
        for (int j=1;j<64 && pal.size()<4;j++)
        {
            int k=0;
            int pj = rgb2ngp(pix[j]);
            while (k<pal.size())
            {
                int pk = ((Integer)pal.elementAt(k)).intValue();
                if (pk>=pj)
                {
                   if (pk>pj)
                      pal.insertElementAt(new Integer(pj),k);
                   pj = -1;
                   break;
                }
                else
                   k++;
            }
            if (pj>=0)
               pal.addElement(new Integer(pj));
        }
        while (pal.size()<4)
            pal.insertElementAt(new Integer(0),0);
        /* Find palette in known list */
        boolean b = true;
        for (int j=0;j<pals.size()&&b;j++)
        {
            if (compPals(pal,(Vector)pals.elementAt(j)))
            {
               palIdx.addElement(new Integer(j));
               b = false;
            }
        }
        if (b)
        {
           palIdx.addElement(new Integer(pals.size()));
           pals.addElement(pal);
        }
        /* ngp tile encoding */
        for (int j=0;j<8;j++)
        {
            int v = 0;
            for (int k=0;k<8;k++)
                v = (v<<2)+pal.indexOf(new Integer(rgb2ngp(pix[j*8+k])));
            pw.print("0x"+int2Hex(v,4)+(j<7||i<imgs.size()-1?",":"")+(j<7?"":"\n"));
        }
    }
  }

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
            v.addElement(bi);
        }
    }
    return w;
  }

  public static void main(String[] args) throws Exception
  {
    Vector imgs = new Vector();
    Vector pals = new Vector();
    Vector palIdx = new Vector();

    int line = CodeSprites.doTiles(Toolkit.getDefaultToolkit().createImage(args[0]),imgs);

    PrintWriter pw = new PrintWriter(new FileOutputStream(args[1]+".h"));

    String id = args[1].toUpperCase();

    pw.println("#define "+id+"_TILES "+imgs.size());
    pw.println("#define "+id+"_LINE "+line);
    pw.println();
    pw.println("const u16 "+args[1]+"_tiles["+id+"_TILES*8] = {");
    CodeSprites.saveSprites(imgs,pals,palIdx,pw);
    pw.println("};");
    pw.println();

    pw.println("#define "+id+"_PALS "+pals.size());
    pw.println();
    pw.println("const u16 "+args[1]+"_pals["+id+"_PALS*4]= {");
    String s = null;
    for (int i=0;i<pals.size();i++)
    {
        Vector pal = (Vector)pals.elementAt(i);
        s="";
        for (int j=0;j<4;j++)
            s+="0x"+int2Hex(((Integer)pal.elementAt(j)).intValue(),4)+(j<3?",":"");
        pw.println(s+(i+1<pals.size()?",":""));
    }
    pw.println("};");
    pw.println();

    pw.println("const u8 "+args[1]+"_palidx["+id+"_TILES] = {");
    for (int i=0;i<palIdx.size();i++)
        pw.print(((Integer)palIdx.elementAt(i)).intValue()+(i+1<palIdx.size()?",":"")+((i+1)%16==0?"\n":""));
    pw.println("};");
    pw.close();
    System.exit(0);
  }
}
