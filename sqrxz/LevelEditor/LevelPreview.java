import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;
import javax.swing.JComponent;

public class LevelPreview extends JComponent
{
	private BufferedImage m_img;
    private BufferedImage m_back;

	public LevelPreview(int w, int h)
	{
		resetSize(w, h);
		setBackground(Color.white);
		setOpaque(true);
	}

    public void setBack(String filename) throws IOException
    {
        File f = new File(filename);
        String maindir = f.getParent();
        if (!f.exists())
        {
            f = new File(maindir,f.getName());
            filename = f.getAbsolutePath();
        }
        String ucase = filename.toUpperCase();
        m_back = null;
        if (ucase.endsWith(".GIF")||ucase.endsWith(".JPG")||ucase.endsWith(".PNG")||ucase.endsWith(".BMP"))
            m_back = ImageIO.read(f);
    }

	public void resetSize(int w, int h)
	{
		Dimension d = new Dimension(w*16,h*16);
		setPreferredSize(d);
		setMinimumSize(d);
		m_img = new BufferedImage(d.width,d.height,BufferedImage.TYPE_INT_ARGB);
	}

	public void paintComponent(Graphics g)
	{
		Rectangle r = g.getClipBounds();
        if (m_back!=null)
        {
            for (int i=0;i<m_img.getWidth();i+=m_back.getWidth())
                g.drawImage(m_back,i,200-m_back.getHeight(),null);
        }
        else
        {
            g.setColor(Color.white);
            g.fillRect(r.x,r.y,r.width,r.height);
        }
        g.drawImage(m_img,0,0,null);
	}

    public static final int[] eraser = new int[256];

	public void setTile(int x,int y,BufferedImage b,boolean repaint)
	{
		x<<=4;
		y<<=4;
        m_img.setRGB(x, y, 16, 16, eraser, 0, 16);
		Graphics g = m_img.getGraphics();
		if (b != null) {
			g.drawImage(b,x,y,x+16,y+16,0,0,16,16,null);
		} else {
			g.setColor(Color.black);
			g.fillRect(x, y, 16, 16);
		}
		if (repaint)
			paintImmediately(x,y,16,16);
	}
}
