import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.image.BufferedImage;

import javax.swing.JPanel;

public class MapPreview extends JPanel
{
	private BufferedImage m_img;

	public MapPreview(int size)
	{
		resetSize(size);
		setBackground(Color.white);
		setOpaque(true);
	}
	
	public void resetSize(int size)
	{
		Dimension d = new Dimension(32*8,size*8);
		setPreferredSize(d);
		setMinimumSize(d);
		m_img = new BufferedImage(d.width,d.height,BufferedImage.TYPE_INT_ARGB);
		Graphics g = m_img.getGraphics();
		g.setColor(Color.white);
		g.fillRect(0,0,d.width,d.height);
	}

	public void paint(Graphics g)
	{
		g.drawImage(m_img,0,0,null);
	}

	public void setTile(int x,int y,BufferedImage b,BufferedImage f,boolean repaint)
	{
		x<<=3;
		y<<=3;
		Graphics g = m_img.getGraphics();
		if (b==null)
		{
			g.setColor(Color.white);
			g.fillRect(x,y,8,8);
		}
		else
			g.drawImage(b,x,y,x+8,y+8,0,0,8,8,null);
		if (f!=null)
			g.drawImage(f,x,y,x+8,y+8,0,0,8,8,null);
		if (repaint)
			paintImmediately(x,y,8,8);
	}
}
