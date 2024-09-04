import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.MediaTracker;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.LineNumberReader;
import java.io.PrintWriter;
import java.util.StringTokenizer;
import java.util.Vector;

import javax.swing.JPanel;
import javax.swing.JScrollPane;

public class Map extends JPanel implements MouseListener,MouseMotionListener
{
	private int m_size;
	private int m_tset;
	private int m_tile;
	private int m_sprset;
	private int m_sprite;
	private int[][][] m_map;
	private boolean[][][] m_vf;
	private boolean[][][] m_hf;
	private Vector m_tilesets, m_ts_names;
	private Vector m_sprites,m_spr_names;
	private MapPreview m_preview;
	private int m_mode;
	private boolean m_vflip;
	private boolean m_hflip;
	private int m_wt,m_ht,m_dx;
	private JScrollPane m_view;
	private boolean m_front;
	
	private static String maindir;

	public Map(int size,MapPreview mp)
	{
		m_size = size;
		m_map = new int[32][size][4];
		for (int i=0;i<32;i++)
			for (int j=0;j<size;j++)
				for (int k=0;k<4;k++)
					m_map[i][j][k] = -1;
		m_vf = new boolean[2][32][size];
		m_hf = new boolean[2][32][size];
		addMouseListener(this);
		addMouseMotionListener(this);
		Dimension d = new Dimension(32<<5,size<<5);
		setSize(d);
		setPreferredSize(d);
		setMinimumSize(d);
		setBackground(Color.white);
		setOpaque(true);
		m_preview = mp;
		m_tilesets = new Vector();
		m_sprites = new Vector();
		m_ts_names = new Vector();
		m_spr_names = new Vector();
		m_mode = 0;
		m_vflip = m_hflip = false;
		m_front = true;
		m_wt = m_ht = 1;
		m_dx = 0;
	}

	public void setView(JScrollPane view)
	{
		m_view = view;
	}

	public void setGrid(int wt,int ht)
	{
		m_wt = wt;
		m_ht = ht;
	}

	private void setValueAt(int i,int j,int k,int v)
	{
		m_map[i][j][k] = v;
		if (k==3)
			showTile(i,j,false);
	}

	public void setVFlip(int k,int i,int j,boolean b)
	{
		m_vf[k][i][j] = b;
	}
	
	public void setHFlip(int k,int i,int j,boolean b)
	{
		m_hf[k][i][j] = b;
	}
	
	public static Map load(String filename,MapPreview mp) throws Exception
	{
		maindir = (new File(filename)).getParent();
		LineNumberReader lnr = new LineNumberReader(new FileReader(filename));
		int size = Integer.valueOf(lnr.readLine()).intValue();
		Map map = new Map(size,mp);
		int n = Integer.valueOf(lnr.readLine()).intValue();
		for (int i=0;i<n;i++)
			map.addTileset(lnr.readLine());
		n = Integer.valueOf(lnr.readLine()).intValue();
		for (int i=0;i<n;i++)
			map.addSprites(lnr.readLine());
		int [][][] m = new int[32][size][4];
		for (int i=0;i<32;i++)
		{
			StringTokenizer st = new StringTokenizer(lnr.readLine());
			for (int j=0;j<size;j++)
				for (int k=0;k<4;k++)
					m[i][j][k] = Integer.valueOf(st.nextToken()).intValue();
		}
		for (int k=0;k<2;k++)
		{
			for (int i=0;i<32;i++)
			{
				StringTokenizer st = new StringTokenizer(lnr.readLine());
				for (int j=0;j<size;j++)
					map.setVFlip(k,i,j,Integer.valueOf(st.nextToken()).intValue()==1);
			}
		}
		for (int k=0;k<2;k++)
		{
			for (int i=0;i<32;i++)
			{
				StringTokenizer st = new StringTokenizer(lnr.readLine());
				for (int j=0;j<size;j++)
				{
					map.setHFlip(k,i,j,Integer.valueOf(st.nextToken()).intValue()==1);
				}
			}
		}
		for (int i=0;i<32;i++)
			for (int j=0;j<size;j++)
				for (int k=0;k<4;k++)
					map.setValueAt(i,j,k,m[i][j][k]);
		lnr.close();
		mp.paint(mp.getGraphics());
		return map;
	}

	public void save(String filename) throws Exception
	{
		PrintWriter pw = new PrintWriter(new FileOutputStream(filename));
		pw.println(m_size);
		pw.println(m_ts_names.size());
		for (int i=0;i<m_ts_names.size();i++)
			pw.println((String)m_ts_names.elementAt(i));
		pw.println(m_spr_names.size());
		for (int i=0;i<m_spr_names.size();i++)
			pw.println((String)m_spr_names.elementAt(i));
		for (int i=0;i<32;i++)
		{
			for (int j=0;j<m_size;j++)
			{
				for (int k=0;k<4;k++)
					pw.print(m_map[i][j][k]+" ");
			}
			pw.println();
		}
		for (int i=0;i<32;i++)
		{
			for (int j=0;j<m_size;j++)
				pw.print((m_vf[0][i][j]?"1":"0")+" ");
			pw.println();
		}
		for (int i=0;i<32;i++)
		{
			for (int j=0;j<m_size;j++)
				pw.print((m_vf[1][i][j]?"1":"0")+" ");
			pw.println();
		}
		for (int i=0;i<32;i++)
		{
			for (int j=0;j<m_size;j++)
				pw.print((m_hf[0][i][j]?"1":"0")+" ");
			pw.println();
		}
		for (int i=0;i<32;i++)
		{
			for (int j=0;j<m_size;j++)
				pw.print((m_hf[1][i][j]?"1":"0")+" ");
			pw.println();
		}
		pw.close();
	}

	public String transC(int i,int j,int k)
	{
		int t = m_map[i][j][k+1];
		if (t>0)
		{
			if (m_hf[k>>1][i][j])
				t+=0x8000;
			if (m_vf[k>>1][i][j])
				t+=0x4000;
		}
		return "0x"+Integer.toHexString(t+1);
	}

	public void saveAsC(String filename) throws Exception
	{
		PrintWriter pw = new PrintWriter(new FileOutputStream(filename));
		pw.println("#include \"ngpc.h\"");
		pw.println();
		pw.println("const u16 lvl0_0["+m_size+"][32] = {");
		for (int k=0;k<4;k+=2)
		{
			if (k==2)
				pw.println("const u16 lvl0_1["+m_size+"][32] = {");
			for (int i=0;i<m_size;i++)
			{
				pw.print("{");
				for (int j=0;j<32;j++)
					pw.print(transC(j,m_size-i-1,k)+(j<31?",":"}"));
				pw.println(i<m_size-1?",":"");
			}
			pw.println("};");
		}
		pw.close();
	}

	public int addTileset(String filename) throws Exception
	{
		m_ts_names.addElement(filename);
		m_tilesets.addElement(cutImage(filename));
		return m_ts_names.size()-1;
	}

	public BufferedImage[] getTileset(int set)
	{
		return (BufferedImage[])m_tilesets.elementAt(set);
	}

	public int getTilesetCount()
	{
		return m_tilesets.size();
	}

	public String getTilesetName(int set)
	{
		return (String)m_ts_names.elementAt(set);
	}

	public int addSprites(String filename) throws Exception
	{
		m_spr_names.add(filename);
		m_sprites.add(cutImage(filename));
		return m_spr_names.size()-1;
	}

	public BufferedImage[] getSprites(int set)
	{
		return (BufferedImage[])m_sprites.elementAt(set);
	}

	public int getSpritesCount()
	{
		return m_sprites.size();
	}

	public String getSpritesName(int set)
	{
		return (String)m_spr_names.elementAt(set);
	}
	
	private void blackAlpha(BufferedImage img)
	{
		int[] rgb  = new int[64];
		img.getRGB(0,0,8,8,rgb,0,8);
		for (int i=0;i<64;i++)
			if ((rgb[i]&0xffffff)==0)
				rgb[i] = 0;
		img.setRGB(0,0,8,8,rgb,0,8);
	}

	private BufferedImage[] cutImage(String filename) throws Exception
	{
		File f = new File(filename);
		if (!f.exists())
		{
			f = new File(maindir,f.getName());
			filename = f.getAbsolutePath();
		}
		String ucase = filename.toUpperCase();
		Image image = null;
		if (ucase.endsWith(".GIF")||ucase.endsWith(".JPG")||ucase.endsWith(".PNG"))
		{
			image = Toolkit.getDefaultToolkit().getImage(filename);
			MediaTracker mt = new MediaTracker(this);
			mt.addImage(image,0);
			mt.waitForAll();
		}
		else if (ucase.endsWith(".PCX"))
		{
			image = PCXDecoder.getImage(filename);
		}
		int w = image.getWidth(null)>>3;
		int h = image.getHeight(null)>>3;
		BufferedImage[] set = new BufferedImage[w*h];
		for (int j=0;j<h;j++)
		{
			for (int i=0;i<w;i++)
			{
				set[j*w+i] = new BufferedImage(8,8,BufferedImage.TYPE_INT_ARGB);
				set[j*w+i].getGraphics().drawImage(image,0,0,8,8,i<<3,j<<3,(i<<3)+8,(j<<3)+8,null);
				blackAlpha(set[j*w+i]);
			}
		}
		return set;
	}

	public void setTile(int set,int tile)
	{
		m_tset = set;
		m_tile = tile;
		m_mode = 0;
	}

	public void setSprite(int set,int spr)
	{
		m_sprset = set;
		m_sprite = spr;
		m_mode = 2;
	}

	private void hflip(int[] rgb)
	{
		for (int i=0;i<8;i++)
			for (int j=0;j<4;j++)
			{
				int t = rgb[i*8+j];
				rgb[i*8+j] = rgb[i*8+7-j];
				rgb[i*8+7-j] = t;
			}
	}
	
	private void vflip(int[] rgb)
	{
		for (int i=0;i<8;i++)
			for (int j=0;j<4;j++)
			{
				int t = rgb[j*8+i];
				rgb[j*8+i] = rgb[(7-j)*8+i];
				rgb[(7-j)*8+i] = t;
			}
	}
	
	public BufferedImage transImage(BufferedImage[] tile,int t,int wt,int ht,int dx,boolean vf,boolean hf)
	{
		if (tile==null)
			return null;
		BufferedImage ret = new BufferedImage(8*wt,8*ht,BufferedImage.TYPE_INT_ARGB);
		BufferedImage img = new BufferedImage(8,8,BufferedImage.TYPE_INT_ARGB);
		Graphics g = ret.getGraphics();
		int[] rgb = new int[64];
		for (int i=0;i<wt;i++)
		{
			for (int j=0;j<ht;j++)
			{
				if (t+j*dx+i>=tile.length)
					break;
				tile[t+j*dx+i].getRGB(0,0,8,8,rgb,0,8);
				if (vf)
					vflip(rgb);
				if (hf)
					hflip(rgb);
				img.setRGB(0,0,8,8,rgb,0,8);
				g.drawImage(img,hf?(wt*8)-((i+1)*8):(i*8),vf?(ht*8)-((j+1)*8):(j*8),null);
			}
		}
		return ret;
	}
	
	public void paint(Graphics g)
	{
		Rectangle r = m_view.getViewport().getViewRect();
		for (int i=0;i<32;i++)
		{
			int xi = i<<5;
			if (xi+32<r.x || xi>=r.x+r.width)
				continue;
			for (int j=0;j<m_size;j++)
			{
				int yi = j<<5;
				if (yi+32<r.y || yi>=r.y+r.height)
					continue;
				if (m_map[i][j][1]>=0 && m_tilesets.size()>0 && m_map[i][j][1]>=0)
				{
					BufferedImage[] ts = (BufferedImage[])m_tilesets.elementAt(m_map[i][j][0]);
					BufferedImage img = transImage(ts,m_map[i][j][1],1,1,m_dx,m_vf[0][i][j],m_hf[0][i][j]);
					g.drawImage(img,xi,yi,xi+32,yi+32,0,0,8,8,null);
				}
				else
				{
					g.setColor(Color.white);
					g.fillRect(xi,yi,32,32);
				}
				if (m_front && m_map[i][j][2]>=0 && m_sprites.size()>0 && m_map[i][j][3]>=0)
				{
					BufferedImage[] ts = (BufferedImage[])m_sprites.elementAt(m_map[i][j][2]);
					BufferedImage img = transImage(ts,m_map[i][j][3],1,1,m_dx,m_vf[1][i][j],m_hf[1][i][j]);
					g.drawImage(img,xi,yi,xi+32,yi+32,0,0,8,8,null);
				}
				g.setColor(Color.lightGray);
				g.drawRect(xi,yi,32,32);
			}
		}
	}

	private void setTileAt(int x,int y,int set,int tile,boolean repaint)
	{
		if (x>=32 || y>=m_size || (set==m_map[x][y][m_mode]&&tile==m_map[x][y][m_mode+1]))
			return;
		m_map[x][y][m_mode] = set;
		m_map[x][y][m_mode+1] = tile;
		m_vf[m_mode>>1][x][y] = m_vflip;
		m_hf[m_mode>>1][x][y] = m_hflip;
		if (repaint)
			paintImmediately(x<<5,y<<5,32,32);
		showTile(x,y,repaint);
	}
	
	private void showTile(int x,int y,boolean repaint)
	{
		BufferedImage[] ts0 = null;
		BufferedImage[] ts1 = null;
		int t0 = -1;
		if (m_map[x][y][0]>=0 && m_tilesets.size()>0 && m_map[x][y][1]>=0)
		{
			ts0 = (BufferedImage[])m_tilesets.elementAt(m_map[x][y][0]);
			t0 = m_map[x][y][1];
		}
		int t1 = -1;
		if (m_map[x][y][2]>=0 && m_sprites.size()>0 && m_map[x][y][3]>=0)
		{
			ts1 = (BufferedImage[])m_sprites.elementAt(m_map[x][y][2]);
			t1 = m_map[x][y][3];
		}
		m_preview.setTile(	x,y,
							transImage(ts0,t0,1,1,m_dx,m_vf[0][x][y],m_hf[0][x][y]),
							transImage(ts1,t1,1,1,m_dx,m_vf[1][x][y],m_hf[1][x][y]),
							repaint);
	}

	private void fill(int x,int y,int d)
	{
		if (x<0 || y<0 || x>=32 || y>=m_size || m_map[x][y][0]!=-1 || d==0)
			return;
		setTileAt(x,y,m_tset,m_tile,false);
		fill(x-1,y,d-1);
		fill(x,y-1,d-1);
		fill(x+1,y,d-1);
		fill(x,y+1,d-1);
	}

	public void setDX(int dx)
	{
		m_dx = dx;
	}

	public void setVFlip(boolean b)
	{
		m_vflip = b;
	}

	public void setHFlip(boolean b)
	{
		m_hflip = b;
	}

	public void setFront(boolean b)
	{
		m_front = b;
	}

	public void mouseClicked(MouseEvent e)
	{
		int x = e.getX();
		int y = e.getY();
		if (x<0 || y<0)
			return;
		x>>=5;
		y>>=5;
		if ((e.getModifiers() & MouseEvent.BUTTON1_MASK)!=0)
		{
			for (int i=0;i<m_wt;i++)
				for (int j=0;j<m_ht;j++)
					setTileAt(x+i,y+j,m_mode==0?m_tset:m_sprset,(m_mode==0?m_tile:m_sprite)+(m_hflip?m_wt-i-1:i)+(m_vflip?(m_ht-j-1):j)*m_dx,false);
			repaint(0);
			m_preview.repaint(0);
		}
		else if ((e.getModifiers() & MouseEvent.BUTTON3_MASK)!=0)
		{
			for (int i=0;i<m_wt;i++)
				for (int j=0;j<m_ht;j++)
					setTileAt(x+i,y+j,-1,-1,false);
			repaint(0);
			m_preview.repaint(0);
		}
		else if (m_mode==0)
		{
			fill(x,y,16);
			paint(getGraphics());
			m_preview.paint(m_preview.getGraphics());
		}
	}

	public void mouseEntered(MouseEvent e)
	{
		if (m_tile<0 || m_tilesets.size()==0)
			return;
		Image img = null;
		if (m_mode==0)
		{
			BufferedImage[] ts = (BufferedImage[])m_tilesets.elementAt(m_tset);
			img = transImage(ts,m_tile,m_wt,m_ht,m_dx,m_vflip,m_hflip);
		}
		else
		{
			BufferedImage[] ts = (BufferedImage[])m_sprites.elementAt(m_sprset);
			img = transImage(ts,m_sprite,m_wt,m_ht,m_dx,m_vflip,m_hflip);
		}
		setCursor(Toolkit.getDefaultToolkit().createCustomCursor(img,new Point(3,3),"no_name"));
	}
	
	public void mouseExited(MouseEvent e)
	{
	}
	
	public void mousePressed(MouseEvent e)
	{
	}
	
	public void mouseReleased(MouseEvent e)
	{
	}
	
	public void mouseDragged(MouseEvent e)
	{
		int x = e.getX();
		int y = e.getY();
		if (x<0 || y<0)
			return;
		x>>=5;
		y>>=5;
		if ((e.getModifiers() & MouseEvent.BUTTON1_MASK)!=0)
			setTileAt(x,y,m_mode==0?m_tset:m_sprset,m_mode==0?m_tile:m_sprite,true);
		else if ((e.getModifiers() & MouseEvent.BUTTON3_MASK)!=0)
			setTileAt(x,y,-1,-1,true);
	}

	public void mouseMoved(MouseEvent e)
	{
	}
}
