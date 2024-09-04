import java.io.*;
import java.util.*;
import java.awt.*;
import java.awt.event.*;

public class YM2Inc
{
  public static final int YM5 = 0x594d3521;
  public static final int YM6 = 0x594d3621;

  public static final int VOICE1 = 0x80;
  public static final int VOICE2 = 0xa0;
  public static final int VOICE3 = 0xc0;

  public static int volume[] = {15,14,13,12,11,10,9,8,7,6,5,4,3,2,1,0};

  public static int read(InputStream is,int l) throws Exception
  {
    int out = 0;
    for (int i=0;i<l;i++)
        out = (out<<8)+is.read();
    return out;
  }

  public static String readString(InputStream is) throws Exception
  {
    String s = "";
    int data;
    while ((data=is.read())>0)
        s+=(char)data;
    return s;
  }

  public static int freq(int[] f,InputStream is,double fmaster) throws Exception
  {
    int low = read(is,1);
    int high = read(is,1);
    int hfreq = (((high&0xf)<<8)+low)*16;
    double ff = fmaster/hfreq;
    int outfreq = hfreq==0 ? 0 : (int)(3072000/(32*ff));
    f[0] = outfreq&0xf;
    f[1] = (outfreq>>4);
    return (int)ff;
  }

  public static void vol(int[] tv,int i,InputStream is,boolean mute) throws Exception
  {
    int v = is.read();
    if (mute)
       tv[i] = 15;
    else
       tv[i] = (v & 0x10) > 0 ? 8 : volume[v&0x0f];
  }

  public static int filter(int vol,int idx,int voice,Vector filters)
  {
    for (int i=0;i<filters.size();i+=3)
    {
        int a = ((Integer)filters.elementAt(i)).intValue();
        if (a!=voice)
           continue;
        a = ((Integer)filters.elementAt(i+1)).intValue();
        if (idx<a)
           continue;
        a = ((Integer)filters.elementAt(i+2)).intValue();
        if (idx>a)
           continue;
        return 8+vol/2;
    }
    return vol;
  }

  public static void main(String args[]) throws Exception
  {
    FileInputStream fis = new FileInputStream(args[0]);
    int version = read(fis,4); // YMx!
    read(fis,8); // LeOnArD!
    int nbf = read(fis,4); // nb frames
    read(fis,4); // attribs
    int dd = read(fis,2); // digidrum
    double fmaster = read(fis,4); // YM Master
    read(fis,2); // 50
    int loop = read(fis,4); // loop index
    read(fis,2); // xtra data
    for (int i=0;i<dd;i++) // digidrums samples
    {
        int l = read(fis,4); // length
        read(fis,l); // sample
    }
    System.out.println(readString(fis)); // song name
    System.out.println(readString(fis)); // Author name
    System.out.println(readString(fis)); // song comment
    System.out.println((dd>0?""+dd:"No ")+"Digidrums");
    InputStream is = null;
    if (version==YM5||version==YM6)
    {
       byte[] buf = new byte[nbf*16];
       byte[] rbuf = new byte[nbf];
       for (int i=0;i<16;i++)
       {
           fis.read(rbuf);
           for (int j=0;j<nbf;j++)
               buf[i+j*16] = rbuf[j];
       }
       is = new ByteArrayInputStream(buf);
    }
    else
       is = fis;

    FileOutputStream fos = new FileOutputStream(args[1]);
    int[][] freqs = new int[3][2];
    int[][] tv = new int[nbf][3];

    int[][] ff = new int[nbf][3];

    boolean analyse = false;
    Vector filters = new Vector();
    for (int i=2;i<args.length;i++)
    {
        if (args[i].equals("A"))
           analyse = true;
        else if (args[i].startsWith("F"))
        {
           int pos = args[i].indexOf(",");
           Integer voice = new Integer(args[i].substring(1,2).charAt(0)-'A');
           Integer start = Integer.valueOf(args[i].substring(2,pos));
           Integer end = Integer.valueOf(args[i].substring(pos+1));
           filters.addElement(voice);
           filters.addElement(start);
           filters.addElement(end);
        }
    }

    for (int i=0;i<nbf;i++)
    {
        ff[i][0] = freq(freqs[0],is,fmaster);
        ff[i][1] = freq(freqs[1],is,fmaster);
        ff[i][2] = freq(freqs[2],is,fmaster);

        read(is,1); // r6

        int mix = read(is,1); // r7

        vol(tv[i],0,is,(freqs[0][0]+freqs[0][1]==0)||((mix & 0x01) != 0)||((mix&0x08)==0));
        vol(tv[i],1,is,(freqs[1][0]+freqs[1][1]==0)||((mix & 0x02) != 0)||((mix&0x10)==0));
        vol(tv[i],2,is,(freqs[2][0]+freqs[2][1]==0)||((mix & 0x04) != 0)||((mix&0x20)==0));

        fos.write(VOICE1+freqs[0][0]);
        fos.write(freqs[0][1]);
        fos.write(VOICE2+freqs[1][0]);
        fos.write(freqs[1][1]);
        fos.write(VOICE3+freqs[2][0]);
        fos.write(freqs[2][1]);
	
        fos.write(VOICE1+0x10+filter(tv[i][0],i,0,filters));
        fos.write(VOICE2+0x10+filter(tv[i][1],i,1,filters));
        fos.write(VOICE3+0x10+filter(tv[i][2],i,2,filters));

        read(is,5); // other data : r11 - r15
    }
    fos.close();
    read(fis,4); // END!
    fis.close();
    if (analyse)
       (new Analyse(ff,tv)).setVisible(true);
  }

  static class Analyse extends Frame
  {
    int[][] ff;
    int[][] tv;
    ScrollPane sp;
    Analyse(int[][] f,int[][] v)
    {
      super();
      ff=f;
      tv=v;
      setLayout(new BorderLayout());
      sp = new ScrollPane();
      add(sp,BorderLayout.CENTER);
      sp.add(new APanel());
      enableEvents(-1);
      setSize(new Dimension(800,600));
    }
    public void processWindowEvent(WindowEvent e)
    {
      super.processWindowEvent(e);
      if (e.getID()==WindowEvent.WINDOW_CLOSING)
         System.exit(0);
    }
    class APanel extends Panel implements MouseListener,MouseMotionListener
    {
      Dimension d;
      int k=0,x,y;
      APanel()
      {
        super();
        setSize(d=new Dimension(ff.length,500));
        addMouseListener(this);
        addMouseMotionListener(this);
        enableEvents(-1);
      }
      public Dimension getPreferredSize()
      {
        return d;
      }
      public void paint(Graphics g)
      {
        super.paint(g);
        g.setColor(Color.black);
        for (int i=0;i<ff.length;i++)
            g.drawLine(i,0,i,(ff[i][k]*(15-tv[i][k]))/15);
        g.setColor(Color.red);
        g.drawString("Voice "+k+" "+x+"="+ff[x][k]+","+tv[x][k],sp.getHAdjustable().getValue()+10,30);
        
      }
      public void mouseClicked(MouseEvent e) {k=(k+1)%3;repaint(0);}
      public void mousePressed(MouseEvent e) {}
      public void mouseReleased(MouseEvent e) {}
      public void mouseEntered(MouseEvent e) {}
      public void mouseExited(MouseEvent e) {}
      public void mouseDragged(MouseEvent e) {}
      public void mouseMoved(MouseEvent e) {x=e.getX();y=e.getY();repaint(0);}
    }
  }
}
