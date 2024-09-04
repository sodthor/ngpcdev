import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.Vector;

import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

public class MapEditor extends JFrame implements ActionListener,ChangeListener,MouseListener
{
	private static final String NEW = "NEW";
	private static final String OPEN = "OPEN";
	private static final String SAVE = "SAVE";
	private static final String EXPORT = "EXPORT";
	private static final String TILESET = "TILESET";
	private static final String SPRITES = "SPRITES";
	private static final String EXIT = "EXIT";
	private static final String VFLIP = "VFLIP";
	private static final String HFLIP = "HFLIP";
	private static final String FRONT = "FRONT";
	
	private static final int MAPSIZE = 512;

	private JScrollPane m_sp,m_psp;
	private Map m_map;
	private Vector m_tset,m_sprset;
	private MapPreview m_preview;
	private JTabbedPane m_panel_tset;
	private JTabbedPane m_panel_sprset;
	private JTabbedPane m_main_tp;
	SpinnerNumberModel m_spinw,m_spinh;

	public MapEditor()
	{
		super("Map Editor");
		JMenuBar mb = new JMenuBar();
		JMenu m = new JMenu("File");
		m.setMnemonic('F');
		addMenuItem(m,"New",'N',NEW);
		addMenuItem(m,"Open",'O',OPEN);
		addMenuItem(m,"Save",'S',SAVE);
		m.addSeparator();
		addMenuItem(m,"Export",'E',EXPORT);
		m.addSeparator();
		addMenuItem(m,"Background",'B',TILESET);
		addMenuItem(m,"Foreground",'F',SPRITES);
		m.addSeparator();
		addMenuItem(m,"Exit",'x',EXIT);
		mb.add(m);
		setJMenuBar(mb);
		getContentPane().setLayout(new BorderLayout());
		JSplitPane jsp = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
		getContentPane().add(jsp,BorderLayout.CENTER);
		JPanel p = new JPanel(new BorderLayout());
		JSplitPane jsp2 = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
		p.add(jsp2,BorderLayout.CENTER);
		jsp2.setLeftComponent(m_psp = new JScrollPane(m_preview = new MapPreview(MAPSIZE)));
		jsp.setTopComponent(m_sp = new JScrollPane(m_map = new Map(MAPSIZE,m_preview)));
		m_map.setView(m_sp);
		m_preview.addMouseListener(this);
		m_sp.getVerticalScrollBar().setUnitIncrement(16);
		m_sp.getViewport().addChangeListener(this);
		m_main_tp = new JTabbedPane();
		m_main_tp.add("Background",m_panel_tset = new JTabbedPane());
		m_main_tp.add("Foreground",m_panel_sprset = new JTabbedPane());
		JPanel p0 = new JPanel(new BorderLayout());
		p0.add(m_main_tp,BorderLayout.CENTER);
		JPanel p1 = new JPanel(new GridLayout(5,1));
		JCheckBox cb;
		p1.add(cb = new JCheckBox("V Flip"));
		cb.setActionCommand(VFLIP);
		cb.addActionListener(this);
		p1.add(cb = new JCheckBox("H Flip"));
		cb.setActionCommand(HFLIP);
		cb.addActionListener(this);
		p1.add(cb = new JCheckBox("Show Front"));
		cb.setSelected(true);
		cb.setActionCommand(FRONT);
		cb.addActionListener(this);
		JPanel p2 = new JPanel(new BorderLayout());
		p2.add(new JLabel("Width"),BorderLayout.WEST);
		p2.add(new JSpinner(m_spinw = new SpinnerNumberModel(1,1,64,1)),BorderLayout.CENTER);
		m_spinw.addChangeListener(this);
		p1.add(p2);
		p2 = new JPanel(new BorderLayout());
		p2.add(new JLabel("Height"),BorderLayout.WEST);
		p2.add(new JSpinner(m_spinh = new SpinnerNumberModel(1,1,64,1)),BorderLayout.CENTER);
		m_spinh.addChangeListener(this);
		p1.add(p2);
		p2 = new JPanel(new BorderLayout());
		p2.add(p1,BorderLayout.NORTH);
		p0.add(p2,BorderLayout.EAST);
		jsp2.setRightComponent(p0);
		jsp.setBottomComponent(p);
		jsp.setDividerLocation(400);
		jsp2.setDividerLocation(250);
		setSize(800,600);
		center(this,null);
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		m_tset = new Vector();
		m_sprset = new Vector();
	}

	public void addMenuItem(JMenu m,String label,char mnemo,String cmd)
	{
		JMenuItem mi = new JMenuItem(label,mnemo);
		mi.setActionCommand(cmd);
		mi.addActionListener(this);
		m.add(mi);
	}

	public static void center(Window child,Window parent)
	{
		Dimension dp,dc;
		Point p;
		if (parent==null)
		{
			dp = Toolkit.getDefaultToolkit().getScreenSize();
			p = new Point(0,0);
		}
		else
		{
			dp = parent.getSize();
			p = parent.getLocationOnScreen();
		}
		dc = child.getSize();
		p.x += (dp.width - dc.width)>>1;
		p.y += (dp.height - dc.height)>>1;
		child.setLocation(p);
	}

	private String getFileName(String title,boolean save)
	{
		JFileChooser jfc = new JFileChooser(".");
		jfc.setDialogTitle(title);
		if (save)
			jfc.showSaveDialog(this);
		else
			jfc.showOpenDialog(this);
		return jfc.getSelectedFile()!=null ? jfc.getSelectedFile().getAbsolutePath():null;
	}

	private void addTileset(int num,boolean spr)
	{
		int i = spr?1:0;
		JTabbedPane parent = spr?m_panel_sprset:m_panel_tset;
		if (spr)
			m_map.setSprite(num,0);
		else
			m_map.setTile(num,0);
		ImageSet is = new ImageSet(m_map,num,spr);
		(spr?m_sprset:m_tset).addElement(is);
		JScrollPane sp = new JScrollPane(is);
		sp.getVerticalScrollBar().setUnitIncrement(8);
		m_main_tp.setSelectedIndex(i);
		parent.add(spr?m_map.getSpritesName(num):m_map.getTilesetName(num),sp);
		parent.setSelectedIndex(num);
		is.changeSet(spr?m_map.getSprites(num):m_map.getTileset(num));
	}

	private void newMap(Map map)
	{
		m_sp.getViewport().setView(m_map = map);
		m_map.setView(m_sp);
		for (int i=0;i<m_map.getTilesetCount();i++)
			addTileset(i,false);
		for (int i=0;i<m_map.getSpritesCount();i++)
			addTileset(i,true);
		m_panel_tset.setSelectedIndex(0);
		m_map.setTile(0,0);
	}

	public void actionPerformed(ActionEvent e)
	{
		String cmd = e.getActionCommand();
		if (cmd.equals(NEW))
		{
			m_sp.getViewport().setView(m_map = new Map(MAPSIZE,m_preview));
		}
		else if (cmd.equals(OPEN))
		{
			String filename = getFileName("Open map",false);
			if (filename==null)
				return;
			try
			{
				newMap(Map.load(filename,m_preview));
			}
			catch(Exception ex)
			{
				ex.printStackTrace();
			}
		}
		else if (cmd.equals(SAVE))
		{
			String filename = getFileName("Save map",true);
			if (filename==null)
				return;
			try
			{
				m_map.save(filename);
			}
			catch(Exception ex)
			{
				ex.printStackTrace();
			}
		}
		else if (cmd.equals(EXPORT))
		{
			String filename = getFileName("Export map as C",true);
			if (filename==null)
				return;
			try
			{
				m_map.saveAsC(filename);
			}
			catch(Exception ex)
			{
				ex.printStackTrace();
			}
		}
		else if (cmd.equals(TILESET))
		{
			String filename = getFileName("Import tileset",false);
			if (filename==null)
				return;
			try
			{
				addTileset(m_map.addTileset(filename),false);
			}
			catch(Exception ex)
			{
				ex.printStackTrace();
			}
		} 
		else if (cmd.equals(SPRITES))
		{
			String filename = getFileName("Import sprites",false);
			if (filename==null)
				return;
			try
			{
				addTileset(m_map.addSprites(filename),true);
			}
			catch(Exception ex)
			{
				ex.printStackTrace();
			}
		}
		else if (cmd.equals(VFLIP))
		{
			m_map.setVFlip(((JCheckBox)e.getSource()).isSelected());
		}
		else if (cmd.equals(HFLIP))
		{
			m_map.setHFlip(((JCheckBox)e.getSource()).isSelected());
		}
		else if (cmd.equals(FRONT))
		{
			m_map.setFront(((JCheckBox)e.getSource()).isSelected());
			m_map.paint(m_map.getGraphics());
		}
		else if (cmd.equals(EXIT))
		{
			System.exit(0);
		}
	}

	public static void refresh(Component c)
	{
		SwingUtilities.windowForComponent(c).dispatchEvent(new ComponentEvent(c,ComponentEvent.COMPONENT_RESIZED));
	}

	public static void main(String[] args)
	{
		(new MapEditor()).setVisible(true);
	}

	private boolean m_move = false;

	private void moveScrollPane(JScrollPane sp,int x,int y)
	{
		if (m_move)
			return;
		m_move = true;
		Dimension d = sp.getViewport().getViewSize();
		Rectangle r = sp.getViewport().getViewRect();
		x -= (r.width>>1);
		y -= (r.height>>1);
		if (x+r.width>=d.width)
			x = d.width-r.width;
		else if (x<0)
			x = 0;
		if (y+r.height>=d.height)
			y = d.height-r.height;
		else if (y<0)
			y = 0;
		sp.getViewport().setViewPosition(new Point(x,y));
		m_move = false;
	}

	public void stateChanged(ChangeEvent e)
	{
		if (e.getSource()==m_spinw || e.getSource()==m_spinh)
		{
			m_map.setGrid(m_spinw.getNumber().intValue(),m_spinh.getNumber().intValue());
		}
		else
		{
			Rectangle r = m_sp.getViewport().getViewRect();
			int x = m_sp.getHorizontalScrollBar().getValue()+(r.width>>1);
			int y = m_sp.getVerticalScrollBar().getValue()+(r.height>>1);
			moveScrollPane(m_psp,x>>2,y>>2);
		}
	}

	public void mouseClicked(MouseEvent e)
	{
		moveScrollPane(m_sp,e.getX()<<2,e.getY()<<2);
	}

	public void mousePressed(MouseEvent e)
	{
		mouseClicked(e);
	}

	public void mouseReleased(MouseEvent e)
	{
	}

	public void mouseEntered(MouseEvent e)
	{
	}

	public void mouseExited(MouseEvent e)
	{
	}
}
