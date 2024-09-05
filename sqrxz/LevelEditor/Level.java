import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.LineNumberReader;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;

import javax.swing.JPanel;
import javax.swing.JScrollPane;

public class Level extends JPanel implements MouseListener,MouseMotionListener,KeyListener
{
	private int m_width;
	private int m_height;
	private int[][] m_lvl;
	private LevelPreview m_preview;
	private ImageSet[] m_imgset;
	private int m_curset;
	private JScrollPane m_view;
    private int m_zoom;
    private String m_tool;

	public Level(int width,int height,LevelPreview lp,ImageSet[] is)
	{
		m_width = width;
		m_height = height;
        m_zoom = 5;
		m_lvl = new int[width][height];
		addMouseListener(this);
		addMouseMotionListener(this);
		resetSize();
		setBackground(Color.white);
		setOpaque(true);
		m_preview = lp;
		m_imgset = is;
		m_curset = 0;
        for (int i=0;i<width;i++)
            for (int j=0;j<height;j++)
                setTileAt(i,j,-1,false);
        lp.paint(lp.getGraphics());
	}

	public int getZoom()
	{
		return m_zoom;
	}

	private void resetSize()
	{
		Dimension d = new Dimension((m_width<<m_zoom)+8,m_height<<m_zoom);
		setSize(d);
		setPreferredSize(d);
		setMinimumSize(d);
	}

	public void setView(JScrollPane view)
	{
		m_view = view;
	}

    public void zoomIn()
    {
        if (m_zoom<7)
        {
            m_zoom+=1;
            resetSize();
            repaint(0);
        }
    }

    public void zoomOut()
    {
        if (m_zoom>3)
        {
            m_zoom-=1;
            resetSize();
            repaint(0);
        }
    }

	private void setValueAt(int i,int j,int v)
	{
		m_lvl[i][j] = v;
	}

	public static Level load(String filename,LevelPreview lp,ImageSet[] is) throws Exception
	{
		LineNumberReader lnr = new LineNumberReader(new FileReader(filename));
		int width = Integer.valueOf(lnr.readLine()).intValue();
		int height = Integer.valueOf(lnr.readLine()).intValue();
		Level lvl = new Level(width,height,lp,is);
		String tileset = lnr.readLine();
		for (int i = 0; i < 4; ++i) {
			is[i].changeSet(tileset);
		}
		for (int i=0;i<width;i++)
		{
			StringTokenizer st = new StringTokenizer(lnr.readLine());
			for (int j=0;j<height;j++)
                lvl.setValueAt(i,j,Integer.valueOf(st.nextToken()).intValue());
		}
		lnr.close();
		return lvl;
	}

	private String string(int v,int l)
	{
		String s = "        "+v;
		return s.substring(s.length()-l);
	}

	private static class Pair implements Comparable<Pair> {
		int k;
		Integer v;
		Pair(int i, int j) {
			k = i;
			v = j;
		}
		@Override
		public int compareTo(Pair o) {
			return v.compareTo(o.v);
		}
	}

	private static class Sprite {
		private int x, y;
		private int id;
	}
	
	private static class Animation {
		private int x, y;
		private int flip;
		private int id;
	}
	
	private int getAnimId(List<Animation> list, int x, int y) {
		for (int i = 0; i < list.size(); ++i) {
			Animation a = list.get(i);
			if (a.x == x && a.y == y) {
				return i;
			}
		}
		return -1;
	}

	public void save(String filename) throws Exception
	{
		File f = new File(filename);
		PrintWriter pw = new PrintWriter(new FileOutputStream(f));
		if (filename.endsWith(".h"))
		{
			String name = f.getName();
			name = name.substring(0, name.length() - 2).toUpperCase();
			pw.println("#ifndef _" + name + "_");
			pw.println("#define _" + name + "_");
			pw.println();
			pw.println("#define "  + name + "_WIDTH " + m_width);
			pw.println("#define "  + name + "_HEIGHT " + m_height);
			pw.println();
			pw.println("const u16 "  + name + "[" + name + "_WIDTH][" + name + "_HEIGHT] = {");
			int tw = m_imgset[0].getTileW();
			int th = m_imgset[0].getTileH();
			int tws = m_imgset[4].getTileW();
			Map<Integer, Pair> map = new HashMap<Integer, Pair>();
			List<Animation> animList = new ArrayList<Animation>(); 
			List<Sprite> sprList = new ArrayList<Sprite>();
			// find animations
			for (int i = 0; i < m_width; ++i)
			{
				for (int j = 0; j < m_height; ++j)
				{
					int t = m_lvl[i][j];
					int ts = t >> 12;
					if (t >= 0 && ts < 4) {
						t &= 0xfff;
						switch(ts) {
							case 1: // v flip
								t = (th - (t / tw) - 1) * tw + (t % tw); 
								break;
							case 2: // h flip
								t = (t / tw) * tw + (tw - (t % tw) - 1); 
								break;
							case 3: // hv flip
								t = (th - (t / tw) - 1) * tw + (tw - (t % tw) - 1); 
								break;
						}
						int nt = ((t / (tw * ImageSet.PEN_MOD)) * (tw / ImageSet.PEN_MOD)) + ((t % tw) / ImageSet.PEN_MOD);
						if (LevelEditor.animMap.containsKey(nt)) {
							int animIdx = ((t % ImageSet.PEN_MOD) + (2 * ((t / tw) % ImageSet.PEN_MOD)));
							if (animIdx == 0) {
								Animation anim = new Animation();
								anim.id = LevelEditor.animMap.get(nt);
								anim.x = i - (i % ImageSet.PEN_MOD);
								anim.y = j - (j % ImageSet.PEN_MOD);
								anim.flip = ((m_lvl[i][j]&0x3000)<<2);
								animList.add(anim);
							}
						}
					}
				}
			}
			// save
			for (int i = 0; i < m_width; ++i)
			{
				pw.print("{");
				for (int j = 0; j < m_height; ++j)
				{
					int t = m_lvl[i][j];
					int ts = t >> 12;
					if (t >= 0 && ts < 4) {
						t &= 0xfff;
						switch(ts) {
							case 1: // v flip
								t = (th - (t / tw) - 1) * tw + (t % tw);
								break;
							case 2: // h flip
								t = (t / tw) * tw + (tw - (t % tw) - 1);
								break;
							case 3: // hv flip
								t = (th - (t / tw) - 1) * tw + (tw - (t % tw) - 1); 
								break;
						}
						Pair p = map.get(t);
						if (p == null) {
							map.put(t, new Pair(t, 1));
						} else {
							p.v = p.v.intValue() + 1;
						}
						int nt = ((t / (tw * ImageSet.PEN_MOD)) * (tw / ImageSet.PEN_MOD)) + ((t % tw) / ImageSet.PEN_MOD);
						if (LevelEditor.animMap.containsKey(nt)) {
							int animIdx = ((t % ImageSet.PEN_MOD) + (2 * ((t / tw) % ImageSet.PEN_MOD)));
							int animId = getAnimId(animList, i - (i % ImageSet.PEN_MOD), j - (j % ImageSet.PEN_MOD));
							if (animId != -1 && animList.get(animId).id == LevelEditor.animMap.get(nt)) {
								t = 0xfc00 | (animIdx<<8) | animId;
							}
						}
					} else if (t >= 0) {
						t &= 0xfff;
						int sprIdx = ((t % ImageSet.PEN_MOD) + (2 * ((t / tws) % ImageSet.PEN_MOD)));
						int nt = ((t / (tws * ImageSet.PEN_MOD)) * (tws / ImageSet.PEN_MOD)) + ((t % tws) / ImageSet.PEN_MOD);
						int sprId = LevelEditor.sprMap != null && LevelEditor.sprMap.containsKey(nt) ? LevelEditor.sprMap.get(nt) : -1;
						if (sprId >= 0) {
							if (sprIdx == 0) {
								Sprite spr = new Sprite();
								spr.id = sprId;
								spr.x = i;
								spr.y = j;
								sprList.add(spr);
							}
							t = LevelEditor.sprTile.get(sprId);
							if (t != -1) {
								t = ((t / tw) * tw * ImageSet.PEN_MOD * ImageSet.PEN_MOD) + ((t % tw) * ImageSet.PEN_MOD);
								t += ((sprIdx & 0x2) != 0 ? tw : 0) + (sprIdx & 0x1);
							}
						} else {
							t = -1;
						}
						
					}
					pw.print(t + (j + 1 < m_height ? ","  : "}"));
				}
				pw.println((i + 1 < m_width ? "," : ""));
			}
			pw.println("};");
			pw.println();
			pw.println("#define " + name + "_ANIM_COUNT " + animList.size());
			pw.println();
			pw.println("const ANIMATED_TILE "  + name + "_ANIMS[" + name + "_ANIM_COUNT] = {");
			for (int i = 0; i < animList.size(); ++i)
			{
				Animation a = animList.get(i);
				pw.println("{" + a.id + "," + a.x + "," + a.y + "," + a.flip + "}" + (i + 1 < animList.size() ? ",": ""));
			}
			pw.println("};");
			pw.println();
			pw.println("#define " + name + "_SPRITE_COUNT " + sprList.size());
			pw.println();
			pw.println("const SPRITE_POS "  + name + "_SPRITES[" + name + "_SPRITE_COUNT] = {");
			Set<Integer> usedAnim = new HashSet<Integer>();
			for (int i = 0; i < sprList.size(); ++i)
			{
				Sprite a = sprList.get(i);
				usedAnim.add(a.id);
				pw.println("{" + a.id + "," + a.x + "," + a.y + "}" + (i + 1 < sprList.size() ? ",": ""));
			}
			pw.println("};");
			pw.println();
			pw.println("const u16 "  + name + "_FLIP[" + name + "_WIDTH][" + name + "_HEIGHT] = {");
			for (int i = 0; i < m_width; ++i)
			{
				pw.print("{");
				for (int j = 0; j < m_height; ++j) {
					int t = m_lvl[i][j];
					t = t >= 0 ? ((t&0x3000)<<2) : 0;
					pw.print((t == 0 ? "0" : ("0x"+ Integer.toHexString(t))) + (j + 1 < m_height ? ","  : "}"));
				}
				pw.println((i + 1 < m_width ? "," : ""));
			}
			pw.println("};");
			pw.println();
			// add animation tiles with low priority (0)
			for (int i = 0; i < LevelEditor.animList.size(); ++i) {
				if (!usedAnim.contains(i)) // only used animations
					continue;
				List<Integer> list = LevelEditor.animList.get(i);
				for (int j = 0; j < list.size(); ++j) {
					int id = list.get(j);
					if (id < 0)
						break;
					id = ((id / tw) * tw * 4) + (id % tw) * 2;
					if (!map.containsKey(id))
						map.put(id, new Pair(id, 0));
					if (!map.containsKey(id + 1))
						map.put(id + 1, new Pair(id + 1, 0));
					if (!map.containsKey(id + tw * 2))
						map.put(id + tw * 2, new Pair(id + tw * 2, 0));
					if (!map.containsKey(id + tw * 2 + 1))
						map.put(id + tw * 2 + 1, new Pair(id + tw * 2 + 1, 0));
				}
			}
			// tiles priority
			Pair[] pairs = map.values().toArray(new Pair[0]);
			Arrays.sort(pairs);
			pw.println("#define " + name + "_USED " + pairs.length);
			pw.println();
			pw.println("const u16 "  + name + "_PRIORITY[" + name + "_USED] = {");
			for (int i = 0; i < pairs.length; ++i)
			{
				pw.print(pairs[i].k + (i+1 < pairs.length ? "," : ""));
				if ((i+1)%16 == 0)
					pw.println();
			}
			pw.println("};");
			pw.println();
			pw.println("#endif");
		}
		else
		{
			pw.println(m_width);
			pw.println(m_height);
			pw.println(m_imgset[0].getSetName());
			for (int i=0;i<m_width;i++)
			{
				for (int j=0;j<m_height;j++)
					pw.print(string(m_lvl[i][j],5)+" ");
				pw.println();
			}
		}
		pw.close();
	}

	public static int btoi(byte b)
	{
		return b<0 ? 256+b : b;
	}

    public void resetPreview()
    {
        m_preview.resetSize(m_width,m_height);
        for (int i=0;i<m_width;i++)
            for (int j=0;j<m_height;j++)
                showTile(i,j,false);
        m_preview.paint(m_preview.getGraphics());
    }

    public void changeSet(String filename) throws Exception
    {
    	m_hm.clear();
		for (int i = 0; i < 4; ++i) {
			m_imgset[i].changeSet(filename);
		}
        resetPreview();
        repaint(0);
    }
  
    public String getSetName()
    {
        return m_imgset[0].getSetName();
    }
  
    public void changeBack(String filename) throws Exception
    {
        m_preview.setBack(filename);
        resetPreview();
    }

    public void setCurrentSet(int set) {
    	m_curset = set;
    }

    public void importBin(String filename) throws Exception
    {
        FileInputStream fis = new FileInputStream(filename);
		for (int i = 0; i < 4; ++i) {
			m_imgset[i].changeSet("gfx/tiles0.png");
		}
        m_width = fis.read() + 1;
        m_height = fis.read() + 1;
        m_lvl = new int[m_width][m_height];
        for (int i=0;i<m_width;i++)
            for (int j=0;j<m_height;j++)
                m_lvl[i][j] = fis.read();
	}

	public void export(String filename) throws Exception
	{
		FileOutputStream fos = new FileOutputStream(filename);
		fos.write(m_width - 1);
		fos.write(m_height - 1);
		for (int i=0;i<m_width;i++)
			for (int j=0;j<m_height;j++)
				fos.write(m_lvl[i][j]);
		fos.flush();
		fos.close();
	}

	private HashMap<String, BufferedImage> m_hm = new HashMap<String, BufferedImage>();

	public BufferedImage transImage(int t,int wt,int ht)
	{
		String key = t+"|"+wt+"|"+ht;
		BufferedImage ret = m_hm.get(key);
		if (ret!=null)
			return ret;
        BufferedImage[] tileset;
		ret = new BufferedImage(16*wt,16*ht,BufferedImage.TYPE_INT_ARGB);
		BufferedImage img = new BufferedImage(16,16,BufferedImage.TYPE_INT_ARGB);
		Graphics g = ret.getGraphics();
		int[] rgb = new int[256];
		for (int i=0;i<wt;i++)
		{
			for (int j=0;j<ht;j++)
			{
                int tij;
                if (t >= 0) {
                	int set = t >> 12; 
					tij = (t & 0xfff) + j * m_imgset[set].getTileW() + i;  
					tileset = m_imgset[set].getTiles();
                } else {
                	tij = m_imgset[m_curset].getTile(i,j);
					tileset = m_imgset[m_curset].getTiles();
                }
				if (tij < 0 || tij>=tileset.length)
					continue;
				tileset[tij].getRGB(0,0,16,16,rgb,0,16);
				img.setRGB(0,0,16,16,rgb,0,16);
				g.drawImage(img,i*16,j*16,null);
			}
		}
		if (t>=0)
			m_hm.put(key,ret);
		return ret;
	}
	
	public void paintComponent(Graphics g)
	{
		Rectangle r = g.getClipBounds();
		g.setColor(Color.black);
		g.fillRect(r.x,r.y,r.width,r.height);
		r = m_view.getViewport().getViewRect();
		for (int i=0;i<m_width;i++)
		{
			int xi = i<<m_zoom;
			if (xi+(1<<m_zoom)<r.x || xi>=r.x+r.width)
				continue;
			for (int j=0;j<m_height;j++)
			{
				int yi = j<<m_zoom;
				if (yi+(1<<m_zoom)<r.y || yi>=r.y+r.height)
					continue;
				if (m_lvl[i][j] >= 0) {
					BufferedImage img = transImage(m_lvl[i][j],1,1);
					g.drawImage(img,xi,yi,xi+(1<<m_zoom),yi+(1<<m_zoom),0,0,16,16,null);
				}
				if ((i%ImageSet.PEN_MOD) == 0 && (j%ImageSet.PEN_MOD) == 0) {
					g.setColor(Color.lightGray);
					g.drawRect(xi,yi,(1<<(m_zoom+ImageSet.PEN_MOD-1)),(1<<(m_zoom+ImageSet.PEN_MOD-1)));
				}
			}
		}
		g.setColor(Color.red);
		if ((m_width&1)==0)
			g.fillRect((m_width<<(m_zoom-1))-1,0,3,(m_height<<m_zoom));
		else
		{
			g.fillRect(((m_width-1)<<(m_zoom-1)),0,3,(m_height<<m_zoom));
			g.fillRect(((m_width+1)<<(m_zoom-1)),0,3,(m_height<<m_zoom));
		}
		g.setColor(Color.red);
		g.fillRect((m_width<<m_zoom),0,8,(1<<m_zoom));
		g.setColor(Color.blue);
		g.fillRect((m_width<<m_zoom),(1<<m_zoom),8,((m_height-1)<<m_zoom));
	}

    public int getTileAt(int x,int y)
    {
        return m_lvl[x][y];
    }

	private void setTileAt(int x,int y,int tile,boolean repaint)
	{
		int nt = tile>= 0 ? (m_curset<<12) + tile : -1;
		if (x>=m_width || y>=m_height || m_lvl[x][y]==nt)
			return;
		m_lvl[x][y] = nt;
		if (repaint)
			paintImmediately(x<<m_zoom,y<<m_zoom,1<<m_zoom,1<<m_zoom);
		showTile(x,y,repaint);
	}

	private void showTile(int x,int y,boolean repaint)
	{
		if (m_imgset[m_curset].getTiles()==null)
			return;
		m_preview.setTile(x,y,m_lvl[x][y] < 0 ? null : transImage(m_lvl[x][y],1,1),repaint);
	}

	private void fill(int x,int y,int d)
	{
		if (x<0 || y<0 || x>=m_width || y>=m_height || d==0)
			return;
		for (int i=0;i<ImageSet.PEN_MOD;i++)
			for (int j=0;j<ImageSet.PEN_MOD;j++)
				setTileAt(x,y,m_imgset[m_curset].getTile(i, j),false);
		fill(x-ImageSet.PEN_MOD,y,d-1);
        fill(x+ImageSet.PEN_MOD,y,d-1);
		fill(x,y-ImageSet.PEN_MOD,d-1);
		fill(x,y+ImageSet.PEN_MOD,d-1);
	}

	private void putTile(int x,int y)
	{
		int wt = m_imgset[m_curset].getPenWidth();
		int ht = m_imgset[m_curset].getPenHeight();
		for (int i=0;i<wt;i++)
			for (int j=0;j<ht;j++)
				setTileAt(x+i,y+j,m_imgset[m_curset].getTile(i,j),false);
		repaint(0);
		m_preview.repaint(0);
	}


	private void clearTile(int x,int y)
	{
		for (int i=0;i<ImageSet.PEN_MOD;i++)
			for (int j=0;j<ImageSet.PEN_MOD;j++)
				setTileAt(x+i,y+j,-1,false);
		repaint(0);
		m_preview.repaint(0);
	}

	public void mouseClicked(MouseEvent e)
	{
		int x = e.getX();
		int y = e.getY();
		if (x<0 || y<0)
			return;
		x>>=m_zoom;
		y>>=m_zoom;
		x -= (x%ImageSet.PEN_MOD);
		y -= (y%ImageSet.PEN_MOD);
		if (x>m_width || y>m_height-1)
			return;
		if (m_tool.equals(ToolBox.PEN))
		{
			if ((e.getModifiers() & MouseEvent.BUTTON1_MASK)!=0)
			{
				putTile(x,y);
			}
			else if ((e.getModifiers() & MouseEvent.BUTTON3_MASK)!=0)
			{
				clearTile(x,y);
			}
			else
			{
				fill(x,y,16);
				paint(getGraphics());
				m_preview.paint(m_preview.getGraphics());
			}
        }
	}

	public void mouseEntered(MouseEvent e)
	{
		if (m_imgset[m_curset].getTiles()==null)
		{
			setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
			return;
		}
		if (m_tool.equals(ToolBox.PEN))
		{
			int wt = m_imgset[m_curset].getPenWidth();
			int ht = m_imgset[m_curset].getPenHeight();
			Image img = transImage(-1,wt,ht);
			setCursor(Toolkit.getDefaultToolkit().createCustomCursor(img,new Point(2,2),"no_name"));
		}
		else
			setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
	}
	
	public void mouseExited(MouseEvent e)
	{
        Graphics g = getGraphics();
        if (m_lastr != null)
        {
            g.setColor(Color.lightGray);
            g.drawRect(m_lastr.x,m_lastr.y,m_lastr.width,m_lastr.height);
            m_lastr = null;
        }
	}
	
	public void mousePressed(MouseEvent e)
	{
	}

	private transient boolean m_drag = false;
	private transient boolean m_dragok = false;
	private transient int m_dragx = 0;
	private transient int m_dragy = 0;

    private transient boolean m_select = false;
    private transient int m_selx = 0;
    private transient int m_sely = 0;
    private transient Rectangle m_selr = null;
    private transient int[][] m_clip = null;

    public void mouseReleased(MouseEvent e)
	{
        if (m_tool.equals(ToolBox.PEN))
        {
    		m_drag = false;
    		if (!m_dragok)
    		{
    			if (m_dragx>0 && m_tool.equals(ToolBox.PEN))
    			{
    				int x = m_dragx>>m_zoom;
					int y = m_dragy>>m_zoom;
   					x -= (x%ImageSet.PEN_MOD);
    				y -= (y%ImageSet.PEN_MOD);
    				if ((e.getModifiers() & MouseEvent.BUTTON1_MASK)!=0)
    					putTile(x,y);
   					else if ((e.getModifiers() & MouseEvent.BUTTON3_MASK)!=0)
   						clearTile(x,y);
    			}
    			m_dragx=-1;
    		}
        }
        else if (m_tool.equals(ToolBox.SELECT))
        {
            m_select = false;
        }
        else if (m_tool.equals(ToolBox.PICKER))
        {
            int x = e.getX();
            int y = e.getY();
            if (x<0 || y<0)
                return;
            x>>=m_zoom;
            y>>=m_zoom;
			x -= (x%ImageSet.PEN_MOD);
			y -= (y%ImageSet.PEN_MOD);
            m_imgset[m_curset].setTile(getTileAt(x, y));
        }
	}

	public void mouseDragged(MouseEvent e)
	{
		int x = e.getX();
		int y = e.getY();
		if (x<0 || y<0)
			return;
		x>>=m_zoom;
		y>>=m_zoom;
		x -= (x%ImageSet.PEN_MOD);
		y -= (y%ImageSet.PEN_MOD);
		if (x>m_width || y>m_height-1)
			return;
		if (m_tool.equals(ToolBox.PEN))
		{
			if (m_drag)
			{
				if (Math.abs(e.getX()-m_dragx)>1 || Math.abs(e.getY()-m_dragy)>1)
				{
					if ((e.getModifiers() & MouseEvent.BUTTON1_MASK)!=0)
						putTile(x,y);
					else if ((e.getModifiers() & MouseEvent.BUTTON3_MASK)!=0)
						clearTile(x,y);
					m_dragok = true;
				}
			}
			else
			{
				m_drag = true;
				m_dragok = false;
			}
			m_dragx = e.getX();
			m_dragy = e.getY();
        	Rectangle rect = new Rectangle(x<<m_zoom,y<<m_zoom,m_imgset[m_curset].getPenWidth()<<m_zoom,m_imgset[m_curset].getPenHeight()<<m_zoom);
        	Graphics g = getGraphics();
            if (m_lastr!=null && !m_lastr.equals(rect))
            {
                g.setColor(Color.lightGray);
                g.drawRect(m_lastr.x,m_lastr.y,m_lastr.width,m_lastr.height);
            }
            if (m_lastr==null || !m_lastr.equals(rect))
            {
                g.setColor(Color.red);
                m_lastr = rect;
                g.drawRect(m_lastr.x,m_lastr.y,m_lastr.width,m_lastr.height);
            }
		}
		else if (m_tool.equals(ToolBox.ERASER))
		{
			if ((e.getModifiers() & MouseEvent.BUTTON1_MASK)!=0)
				clearTile(x,y);
		}
        else if (m_tool.equals(ToolBox.SELECT))
        {
            Graphics g = getGraphics();
            if (m_selr != null)
            {
                g.setColor(Color.lightGray);
                g.drawRect(m_selr.x,m_selr.y,m_selr.width,m_selr.height);
            }
            if (m_select)
            {
                int x0 = Math.min(x, m_selx);
                int y0 = Math.min(y, m_sely);
                m_selr.x = x0<<m_zoom;
                m_selr.y = y0<<m_zoom;
                m_selr.width = (ImageSet.PEN_MOD+Math.abs(x0-Math.max(x, m_selx)))<<m_zoom;
                m_selr.height = (ImageSet.PEN_MOD+Math.abs(y0-Math.max(y, m_sely)))<<m_zoom;
            }
            else
            {
                m_selx = x;
                m_sely = y;
                m_selr = new Rectangle(x<<m_zoom,y<<m_zoom,1<<(m_zoom+ImageSet.PEN_MOD-1),1<<(m_zoom+ImageSet.PEN_MOD-1));
                m_select = true;
            }
            g.setColor(Color.blue);
            g.drawRect(m_selr.x,m_selr.y,m_selr.width,m_selr.height);
        }
	}

    private transient Rectangle m_lastr;

	public void mouseMoved(MouseEvent e)
	{
        Graphics g = getGraphics();
        int x = e.getX();
        int y = e.getY();
        x>>=m_zoom;
        x-=(x%ImageSet.PEN_MOD);
        y>>=m_zoom;
        y-=(y%ImageSet.PEN_MOD);
        Rectangle rect = null;
        if (m_tool.equals(ToolBox.PEN))
        	rect = new Rectangle(x<<m_zoom,y<<m_zoom,m_imgset[m_curset].getPenWidth()<<m_zoom,m_imgset[m_curset].getPenHeight()<<m_zoom);
        if (m_lastr!=null && !m_lastr.equals(rect))
        {
            g.setColor(Color.lightGray);
            g.drawRect(m_lastr.x,m_lastr.y,m_lastr.width,m_lastr.height);
        }
        if (x<0 || y<0)
        {
            m_lastr = null;
            return;
        }
        if (m_tool.equals(ToolBox.PEN) && (m_lastr==null || !m_lastr.equals(rect)))
        {
            g.setColor(Color.red);
            m_lastr = rect;
            g.drawRect(m_lastr.x,m_lastr.y,m_lastr.width,m_lastr.height);
        }
        else if (m_lastr!=null && !m_lastr.equals(rect))
            m_lastr = null;
	}

    public void keyPressed(KeyEvent e)
    {
    }

    public void keyReleased(KeyEvent e)
    {
    }

    public void keyTyped(KeyEvent e)
    {
        if (e.getKeyChar()=='+')
            zoomIn();
        else if (e.getKeyChar()=='-')
            zoomOut();
    }
    
    public void setTool(String tool)
    {
    	m_tool = tool;
        if (m_selr != null)
        {
            Graphics g = getGraphics();
            g.setColor(Color.lightGray);
            g.drawRect(m_selr.x,m_selr.y,m_selr.width,m_selr.height);
        }
        if (tool.equals(ToolBox.COPY)||tool.equals(ToolBox.CUT))
        {
            if (m_selr != null)
            {
                m_selr.x>>=m_zoom;
                m_selr.y>>=m_zoom;
                m_selr.width>>=m_zoom;
                m_selr.height>>=m_zoom;
                m_clip = new int[m_selr.width][m_selr.height];
                for (int i=0;i<m_selr.width;++i)
                    for (int j=0;j<m_selr.height;++j)
                        m_clip[i][j] = getTileAt(m_selr.x+i,m_selr.y+j);
                m_imgset[m_curset].setCustomPen(m_clip);
                if (tool.equals(ToolBox.CUT))
                {
                    for (int i=0;i<m_selr.width;++i)
                        for (int j=0;j<m_selr.height;++j)
                            setTileAt(m_selr.x+i,m_selr.y+j,-1,false);
                    paint(getGraphics());
                    m_preview.paint(m_preview.getGraphics());
                }
            }
        }
        else if (tool.equals(ToolBox.PASTE))
        {
            m_imgset[m_curset].setCustomPen(m_clip);
        }
        m_selr = null;
    }
}
