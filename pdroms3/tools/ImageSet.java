import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.image.BufferedImage;
import javax.swing.JPanel;

public class ImageSet extends JPanel implements MouseListener
{
	private BufferedImage[] m_set;
	private Map m_map;
	private int m_w;
	private int m_num;
	private boolean m_spr;

	public ImageSet(Map map,int num,boolean spr)
	{
		m_map = map;
		m_num = num;
		m_spr = spr;
		changeSet(null);
		addMouseListener(this);
	}

	public void changeSet(BufferedImage[] set)
	{
		if (set==null)
			set = new BufferedImage[0];
		m_set = set;
		m_w = set.length==0?1:set.length;
		m_map.setDX(m_w);
		setBackground(Color.white);
		setOpaque(true);
		resetSize();
	}

	private void resetSize()
	{
		setPreferredSize(new Dimension(m_w<<5,(m_set.length/m_w)<<5));
		setMinimumSize(getPreferredSize());
		setSize(getPreferredSize());
	}
	
	public void paint(Graphics g)
	{
		for (int i=0;i<m_set.length;i++)
		{
			if (m_spr)
			{
				g.setColor(Color.white);
				g.fillRect((i%m_w)<<5,(i/m_w)<<5,32,32);
			}
			int x = (i%m_w)<<5;
			int y = (i/m_w)<<5;
			g.drawImage(m_set[i],x,y,x+32,y+32,0,0,8,8,null);
			g.setColor(Color.lightGray);
			g.drawRect(x,y,32,32);
		}
	}

	public void mouseClicked(MouseEvent e)
	{
		if ((e.getModifiers() & MouseEvent.BUTTON1_MASK)!=0)
		{
			int x = e.getX();
			int y = e.getY();
			if (x<0 || y<0)
				return;
			x>>=5;
			y>>=5;
			int num = y*m_w+x;
			if ((x>=m_w) || (num > m_set.length))
				return;
			if (m_spr)
				m_map.setSprite(m_num,num);
			else
				m_map.setTile(m_num,num);
			m_map.setDX(m_w);
		}
		else
		{
			m_w-=1;
			while(m_w>0 && (m_set.length/m_w)*m_w!=m_set.length)
				m_w--;
			if (m_w==0)
				m_w = m_set.length;
			m_map.setDX(m_w);
			resetSize();
			paint(getGraphics());
			MapEditor.refresh(this);
		}
	}

	public void mouseEntered(MouseEvent e)
	{
	}

	public void mouseExited(MouseEvent e)
	{
	}

	public void mousePressed(MouseEvent e)
	{
		if ((e.getModifiers() & MouseEvent.BUTTON1_MASK)!=0)
			mouseClicked(e);
	}

	public void mouseReleased(MouseEvent e)
	{
	}
}
