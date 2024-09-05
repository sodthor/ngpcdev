import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;
import javax.swing.JComponent;
import javax.swing.JViewport;

public class ImageSet extends JComponent implements MouseListener,MouseMotionListener
{
	public static final int TILE_SIZE = 8;
	public static final int PEN_MOD = 2;

    private static String maindir;

	private BufferedImage[] m_tileset;
	private int m_w;
	private int m_h;
	private int m_tile;
	private int m_wt;
	private int m_ht;
	private BufferedImage m_img; 
    private JViewport m_view;
    private String m_setname; 
    private int m_mouse;
    private int[][] m_custom;
    private boolean hFlip;
    private boolean vFlip;

    private boolean isTiles;

	public ImageSet(boolean flipH, boolean flipV)
	{
		this("gfx/blocks.png", flipH, flipV);
        isTiles = true;
	}

	public ImageSet(String img, boolean flipH, boolean flipV)
	{
		hFlip = flipH;
		vFlip = flipV;
        setOpaque(true);
		setBackground(Color.white);
		addMouseListener(this);
		addMouseMotionListener(this);
        try { changeSet(img); } catch(Exception e) {}
        m_mouse = -1;
	}

	public void changeSet(String filename) throws Exception
	{
        if (filename!=null)
            cutImage(filename);
        else {
        	m_w = 20;
        	m_h = 12;
        }
        Dimension d = new Dimension(m_w*32,m_h*32);
        setPreferredSize(d);
        setMinimumSize(d);
        m_img = new BufferedImage(d.width,d.height,BufferedImage.TYPE_INT_ARGB);
        Graphics g = m_img.getGraphics();
        g.setColor(Color.white);
        g.fillRect(0,0,d.width,d.height);
		m_wt = m_ht = PEN_MOD;
        m_custom = null;
        doBack();
	}

    public String getSetName()
    {
        return m_setname;
    }

    public String getFlip()
    {
        return (hFlip ? "H" : "") + (vFlip ? "V" : "");
    }
    
    public BufferedImage[] getTiles()
    {
        return m_tileset;
    }
    
    public void cutImage(String filename) throws Exception
    {
        File f = new File(filename);
        maindir = f.getParent();
        if (!f.exists())
        {
            f = new File(maindir,f.getName());
            filename = f.getAbsolutePath();
        }
        m_setname = filename;
        String ucase = filename.toUpperCase();
        BufferedImage image = null;
        
        if (ucase.endsWith(".GIF")||ucase.endsWith(".JPG")||ucase.endsWith(".PNG")||ucase.endsWith(".BMP")) {
            image = ImageIO.read(f);
            int w = image.getWidth();
            int h = image.getHeight();
            if (hFlip) {
            	BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            	int[] pixels = new int[w*h];
            	image.getRGB(0, 0, w, h, pixels, 0, w);
            	for (int i = 0; i < h; ++i) {
                	for (int j = 0; j < w/2; ++j) {
	            		int t = pixels[i*w + j];
	            		pixels[i*w + j] = pixels[i*w + w-j-1];
	            		pixels[i*w + w-j-1] = t;
                	}
            	}
                img.setRGB(0, 0, w, h, pixels, 0, w);
            	image = img;
            }
            if (vFlip) {
            	BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            	int[] pixels = new int[w*h];
            	image.getRGB(0, 0, w, h, pixels, 0, w);
            	for (int i = 0; i < w; ++i) {
                	for (int j = 0; j < h/2; ++j) {
	            		int t = pixels[j*w + i];
	            		pixels[j*w + i] = pixels[(h-j-1)*w + i];
	            		pixels[(h-j-1)*w + i] = t;
                	}
            	}
                img.setRGB(0, 0, w, h, pixels, 0, w);
            	image = img;
            }
        }
        if (image==null)
            return;
        cutImage(image);
    }

    public void cutImage(BufferedImage image) {
        m_w = image.getWidth(null)/TILE_SIZE;
        m_h = image.getHeight(null)/TILE_SIZE;
        m_tileset = new BufferedImage[m_w*m_h];
        for (int j=0;j<m_h;j++)
        {
            for (int i=0;i<m_w;i++)
            {
                m_tileset[j*m_w+i] = new BufferedImage(16,16,BufferedImage.TYPE_INT_ARGB);
                m_tileset[j*m_w+i].getGraphics().drawImage(image,0,0,16,16,i*TILE_SIZE,j*TILE_SIZE,(i*TILE_SIZE)+TILE_SIZE,(j*TILE_SIZE)+TILE_SIZE,null);
            }
        }
    }

    public void setView(JViewport view)
    {
        m_view = view;
    }

	private void doBack()
	{
		if (m_tileset==null)
			return;
		Graphics g = m_img.getGraphics();
		g.setColor(Color.white);
		g.fillRect(0,0,m_img.getWidth(),m_img.getHeight());
		int xt = m_tile%m_w;
		int yt = m_tile/m_w;
		for (int i=0;i<m_tileset.length;i++)
		{
			int xi = i%m_w;
			int yi = i/m_w;
			int x = xi<<5;
			int y = yi<<5;
			g.drawImage(m_tileset[i],x,y,x+32,y+32,0,0,16,16,null);
		}
		List<Point> list = new ArrayList<Point>();
		int num = 0;
		int pw = m_w/PEN_MOD;
		int ph = m_h/PEN_MOD;
		for (int i=0;i<m_tileset.length;i++)
		{
			int xi = i%m_w;
			int yi = i/m_w;
			if ((xi%PEN_MOD)>0 || (yi%PEN_MOD)>0)
				continue;
			
			int x = xi<<5;
			int y = yi<<5;

			if (xi>=xt && xi<xt+m_wt && yi>=yt && yi<yt+m_ht)
			{
				g.setColor(Color.red);
				g.drawRect(x,y,(1<<(5+PEN_MOD-1))-1,(1<<(5+PEN_MOD-1))-1);
			}
			else
			{
				int n;
				if (vFlip)
					n = (ph - (num / pw) - 1) * pw + (hFlip ? (pw - 1 - (num % pw)) : (num % pw));
				else
					n = hFlip ? ((num / pw) * pw + pw - 1 - (num % pw)) : num;
				if (!isTiles || !LevelEditor.animMap.containsKey(n)) {
					g.setColor(Color.lightGray);
					g.drawRect(x,y,(1<<(5+PEN_MOD-1)),(1<<(5+PEN_MOD-1)));
				} else {
					list.add(new Point(x, y));
				}
			}
			num+=1;
		}
		g.setColor(Color.blue);
		for (Point p : list) {
			g.drawRect(p.x,p.y,(1<<(5+PEN_MOD-1)),(1<<(5+PEN_MOD-1)));
			g.drawRect(p.x+1,p.y+1,(1<<(5+PEN_MOD-1))-2,(1<<(5+PEN_MOD-1))-2);
		}
        m_view.repaint(0);
	}

	public int getTile()
	{
		return m_tile;
	}

    public int getTile(int i,int j)
    {
        return m_custom==null ? m_tile+i+j*m_w : m_custom[i][j];
    }

    public int getTileW()
    {
        return m_w;
    }

    public int getTileH()
    {
        return m_h;
    }

	public int getPenWidth()
	{
		return m_custom==null ? m_wt : m_custom.length;
	}

	public int getPenHeight()
	{
        return m_custom==null ? m_ht : m_custom[0].length;
	}

    public void paintComponent(Graphics g)
    {
		Rectangle r = g.getClipBounds();
		g.clearRect(r.x,r.y,r.width,r.height);
        g.drawImage(m_img,0,0,null);
    }

	public void mouseClicked(MouseEvent e)
	{
        if (m_tileset==null)
            return;
        m_custom = null;
		if ((e.getModifiers() & MouseEvent.BUTTON1_MASK)!=0)
		{
			int x = e.getX();
			int y = e.getY();
			if (x<0 || y<0)
				return;
			x>>=5;
			x-=(x%PEN_MOD);
			y>>=5;
			y-=(y%PEN_MOD);
			int num = y*m_w+x;
			if ((x>=m_w) || (num > m_tileset.length))
				return;
			setTile(num);
		}
	}

    public void setTile(int num)
    {
        m_tile = num;
        m_wt = m_ht = PEN_MOD;
        doBack();
    }

	public void mouseEntered(MouseEvent e)
	{
	}

	public void mouseExited(MouseEvent e)
	{
	}

	private int startX = -1, startY;

	public void mousePressed(MouseEvent e)
	{
        if (m_tileset==null)
            return;
        m_custom = null;
		if ((e.getModifiers() & MouseEvent.BUTTON1_MASK)!=0)
		{
			int x = e.getX();
			int y = e.getY();
			if (x<0 || y<0)
				return;
			x>>=5;
			x-=(x%PEN_MOD);
			y>>=5;
			y-=(y%PEN_MOD);
			int num = y*m_w+x;
			if ((x>=m_w) || (num > m_tileset.length))
				return;
			m_tile = num;
			m_wt = m_ht = PEN_MOD;
			startX = x;
			startY = y;
			doBack();
		}
	}

	public void mouseReleased(MouseEvent e)
	{
        if (m_tileset==null || startX<0)
            return;
		int x = e.getX();
		int y = e.getY();
		if (x<0 || y<0)
			return;
		x>>=5;
		x-=(x%PEN_MOD);
		y>>=5;
		y-=(y%PEN_MOD);
		int num1 = y*m_w+x;
		if ((x>=m_w) || (num1 > m_tileset.length))
			return;
		m_tile = Math.min(num1,m_tile);
		m_wt = Math.abs(x-startX)+1;
		m_wt += PEN_MOD-(m_wt%PEN_MOD);
		m_ht = Math.abs(y-startY)+1;
		m_ht += PEN_MOD-(m_ht%PEN_MOD);
		startX = -1;
		doBack();
	}

	public void mouseDragged(MouseEvent e)
	{
        if (m_tileset==null || startX<0)
            return;
		int x = e.getX();
		int y = e.getY();
		if (x<0 || y<0)
			return;
		x>>=5;
		x-=(x%PEN_MOD);
		y>>=5;
		y-=(y%PEN_MOD);
		int num1 = y*m_w+x;
		if ((x>=m_w) || (num1 > m_tileset.length))
			return;
		m_tile = Math.min(num1,m_tile);
		m_wt = Math.abs(x-startX)+1; 
		m_ht = Math.abs(y-startY)+1;
		doBack();
	}

	public void mouseMoved(MouseEvent e)
	{
        m_mouse = -1;
        int x = e.getX();
        int y = e.getY();
        if (x<0 || y<0)
            return;
		x>>=5;
		y>>=5;
        int num = y*m_w+x;
        if ((x>=m_w) || (y>=m_h) || (num > m_tileset.length))
            return;
        m_mouse = num;
	}
    
    public int getMouseTile()
    {
    	if (m_mouse < 0) {
    		return m_mouse;
    	}
    	int x = m_mouse % m_w;
    	int y = m_mouse / m_w;
        return (hFlip ? m_w-x-1 : x) + m_w * (vFlip ? m_h-y-1 : y);
    }
    
    public void setCustomPen(int[][] pen)
    {
        m_custom = pen;
    }
}
