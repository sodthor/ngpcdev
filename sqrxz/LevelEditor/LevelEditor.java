import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.LineNumberReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;

import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

public class LevelEditor extends JFrame implements ActionListener,ChangeListener,MouseListener, MouseMotionListener
{
	private static final String NEW = "NEW";
	private static final String OPEN = "OPEN";
	private static final String SAVE = "SAVE";
	private static final String EXPORT = "EXPORT";
	private static final String IMPORT = "IMPORT";
	private static final String TILESET = "TILESET";
    private static final String BACKGROUND = "BACKGROUND";
	private static final String EXIT = "EXIT";
    private static final String ZOOM_IN = "ZOOM_IN";
    private static final String ZOOM_OUT = "ZOOM_OUT";
    private static final String TEST_IT = "TEST_IT";
    private static final String TOOLBOX = "TOOLBOX";
	
	private static final int LEVEL_WIDTH = 256;
	private static final int LEVEL_HEIGHT = 32;

	private JScrollPane m_sp,m_psp;
	private JScrollPane[] m_isp;
	private Level m_lvl;
	private LevelPreview m_preview;
	private ImageSet[] m_imgset;
	private JTabbedPane m_tab;
    private String m_gamedir;
    private ToolBox m_toolbox;
    private JLabel m_status;

	public static Map<Integer, Integer> animMap;
	public static List<List<Integer>> animList;
	public static Map<Integer, Integer> sprMap;
	public static Map<Integer, Integer> sprTile;

	public LevelEditor()
	{
		super("Map Editor");

        File f = new File("animated.txt");
        if (f.exists()) {
        	readAnimated(f);
        }
        f = new File("sprites.txt");
        if (f.exists()) {
        	readSprites(f);
        }

		JMenuBar mb = new JMenuBar();
		JMenu m = new JMenu("File");
		m.setMnemonic('F');
		addMenuItem(m,"New",'N',NEW);
		addMenuItem(m,"Open",'O',OPEN);
		addMenuItem(m,"Save",'S',SAVE);
		m.addSeparator();
		addMenuItem(m,"Export",'E',EXPORT);
		addMenuItem(m,"Import",'I',IMPORT);
		m.addSeparator();
		addMenuItem(m,"TileSet",'T',TILESET);
        addMenuItem(m,"Background",'B',BACKGROUND);
		m.addSeparator();
		addMenuItem(m,"Exit",'x',EXIT);
		mb.add(m);
        m = new JMenu("Tools");
        m.setMnemonic('T');
        addMenuItem(m,"Zoom in",'i',ZOOM_IN);
        addMenuItem(m,"Zoom out",'o',ZOOM_OUT);
        addMenuItem(m,"Test it!",'s',TEST_IT);
        mb.add(m);
        m = new JMenu("View");
        m.setMnemonic('V');
        mb.add(m);
		setJMenuBar(mb);
        addMenuItem(m,"ToolBox",'T',TOOLBOX);
		getContentPane().setLayout(new BorderLayout());
		JSplitPane jsp = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
		getContentPane().add(jsp,BorderLayout.CENTER);
		JPanel p = new JPanel(new BorderLayout());
		JSplitPane jsp2 = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
		p.add(jsp2,BorderLayout.CENTER);
		jsp2.setLeftComponent(m_psp = new JScrollPane(m_preview = new LevelPreview(LEVEL_WIDTH, LEVEL_HEIGHT)));

		m_tab = new JTabbedPane();
		m_isp = new JScrollPane[5];
		m_imgset = new ImageSet[5];
		m_isp[0] = new JScrollPane(m_imgset[0] = new ImageSet(false, false));
        m_imgset[0].setView(m_isp[0].getViewport());
        m_isp[0].getVerticalScrollBar().setUnitIncrement(8);
        m_imgset[0].addMouseMotionListener(this);
        m_tab.add("Normal", m_isp[0]);
		m_isp[1] = new JScrollPane(m_imgset[1] = new ImageSet(false, true));
        m_imgset[1].setView(m_isp[1].getViewport());
        m_isp[1].getVerticalScrollBar().setUnitIncrement(8);
        m_imgset[1].addMouseMotionListener(this);
        m_tab.add("V Flip", m_isp[1]);
		m_isp[2] = new JScrollPane(m_imgset[2] = new ImageSet(true, false));
        m_imgset[2].setView(m_isp[2].getViewport());
        m_isp[2].getVerticalScrollBar().setUnitIncrement(8);
        m_imgset[2].addMouseMotionListener(this);
        m_tab.add("H Flip", m_isp[2]);
		m_isp[3] = new JScrollPane(m_imgset[3] = new ImageSet(true, true));
        m_imgset[3].setView(m_isp[3].getViewport());
        m_isp[3].getVerticalScrollBar().setUnitIncrement(8);
        m_imgset[3].addMouseMotionListener(this);
        m_tab.add("HV Flip", m_isp[3]);
        m_tab.addChangeListener(this);
		m_isp[4] = new JScrollPane(m_imgset[4] = new ImageSet("gfx/sprites.png", false, false));
        m_imgset[4].setView(m_isp[4].getViewport());
        m_isp[4].getVerticalScrollBar().setUnitIncrement(8);
        m_imgset[4].addMouseMotionListener(this);
        m_tab.add("Sprites", m_isp[4]);
        m_tab.addChangeListener(this);
        jsp2.setRightComponent(m_tab);
		jsp.setTopComponent(m_sp = new JScrollPane(m_lvl = new Level(LEVEL_WIDTH,LEVEL_HEIGHT,m_preview,m_imgset)));
		m_lvl.setView(m_sp);
        m_lvl.addMouseMotionListener(this);
		m_preview.addMouseListener(this);
		m_sp.getVerticalScrollBar().setUnitIncrement(16);
		m_sp.getViewport().addChangeListener(this);
        m_psp.getVerticalScrollBar().setUnitIncrement(4);
		jsp.setBottomComponent(p);
		jsp.setDividerLocation(408);
		jsp2.setDividerLocation(250);
        getContentPane().add(m_status = new JLabel(" "),BorderLayout.SOUTH);
        m_status.setHorizontalAlignment(JLabel.RIGHT);
		setSize(800,600);
		center(this,null);
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        addKeyListener(m_lvl);
        m_gamedir = System.getProperty("sqrxz.dir");
        if (m_gamedir==null)
            m_gamedir = "..";
        m_toolbox = new ToolBox(this);
        m_toolbox.setLevel(m_lvl);
	}
	
	private void readAnimated(File f) {
		try {
			LineNumberReader lnr = new LineNumberReader(new FileReader(f));
			String line;
			animMap = new HashMap<Integer, Integer>();
			animList = new ArrayList<List<Integer>>();
			while ((line = lnr.readLine()) != null) {
				line = line.trim();
				if (line.length() == 0) continue;
				List<Integer> list = new ArrayList<Integer>();
				animList.add(list);
				StringTokenizer st = new StringTokenizer(line,",");
				while(st.hasMoreTokens()) {
					String token = st.nextToken();
					try {
						Integer i = Integer.decode(token);
						if (i >= 0) {
							list.add(i);
							if (!animMap.containsKey(i))
								animMap.put(i, animList.size() - 1);
						}
					} catch(NumberFormatException nfe) {
						// ignore
					}
				}
			}
			lnr.close();
		} catch(IOException e) {
			//ignore
		}
	}

	
	private void readSprites(File f) {
		try {
			LineNumberReader lnr = new LineNumberReader(new FileReader(f));
			String line;
			sprMap = new HashMap<Integer, Integer>();
			sprTile = new HashMap<Integer, Integer>();
			int id = 0;
			while ((line = lnr.readLine()) != null) {
				line = line.trim();
				if (line.length() == 0 || line.startsWith(";")) continue;
				StringTokenizer st = new StringTokenizer(line,",");
				boolean first = true;
				while(st.hasMoreTokens()) {
					String token = st.nextToken().trim();
					try {
						Integer i = Integer.decode(token);
						if (first) {
							sprTile.put(id, i);
							first = false;
						} else if (i >= 0) {
							sprMap.put(i, id);
						}
					} catch(NumberFormatException nfe) {
						// ignore
					}
				}
				id += 1;
			}
			lnr.close();
		} catch(IOException e) {
			//ignore
		}
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

	private void newLevel(Level lvl)
	{
		m_lvl = lvl;
        lvl.resetPreview();
		m_sp.getViewport().setView(m_lvl);
		m_lvl.setView(m_sp);
        addKeyListener(m_lvl);
        m_lvl.addMouseMotionListener(this);
        m_toolbox.setLevel(m_lvl);
	}

	public void actionPerformed(ActionEvent e)
	{
		String cmd = e.getActionCommand();
		if (cmd.equals(NEW))
		{
			newLevel(new Level(LEVEL_WIDTH,LEVEL_HEIGHT,m_preview,m_imgset));
		}
		else if (cmd.equals(OPEN))
		{
			String filename = getFileName("Open level",false);
			if (filename==null)
				return;
			try
			{
				newLevel(Level.load(filename,m_preview,m_imgset));
			}
			catch(Exception ex)
			{
				ex.printStackTrace();
			}
		}
		else if (cmd.equals(SAVE))
		{
			String filename = getFileName("Save level",true);
			if (filename==null)
				return;
			try
			{
				m_lvl.save(filename);
			}
			catch(Exception ex)
			{
				ex.printStackTrace();
			}
		}
		else if (cmd.equals(EXPORT))
		{
			String filename = getFileName("Export level as binary",true);
			if (filename==null)
				return;
			try
			{
				m_lvl.export(filename);
			}
			catch(Exception ex)
			{
				ex.printStackTrace();
			}
		}
		else if (cmd.equals(IMPORT))
		{
			String filename = getFileName("Import level from binary",false);
			if (filename==null)
				return;
			try
			{
			    Level lvl = new Level(LEVEL_WIDTH,LEVEL_HEIGHT,m_preview,m_imgset);
                lvl.importBin(filename);
				newLevel(lvl);
			}
			catch(Exception ex)
			{
				ex.printStackTrace();
			}
		}
		else if (cmd.equals(TILESET))
		{
			String filename = getFileName("Change tileset",false);
			if (filename==null)
				return;
			try
			{
				m_lvl.changeSet(filename);
			}
			catch(Exception ex)
			{
				ex.printStackTrace();
			}
		} 
        else if (cmd.equals(BACKGROUND))
        {
            String filename = getFileName("Change background",false);
            if (filename==null)
                return;
            try
            {
                m_lvl.changeBack(filename);
            }
            catch(Exception ex)
            {
                ex.printStackTrace();
            }
        } 
		else if (cmd.equals(EXIT))
		{
			System.exit(0);
		}
        else if (cmd.equals(ZOOM_IN))
        {
            m_lvl.zoomIn();
        }
        else if (cmd.equals(ZOOM_OUT))
        {
            m_lvl.zoomOut();
        }
        else if (cmd.equals(TEST_IT))
        {
            if (m_gamedir==null)
                return;
            try
            {
                m_lvl.export(m_gamedir+"/maps/test.bin");
                /*
                FileInputStream fis = new FileInputStream(m_lvl.getSetName());
                FileOutputStream fos = new FileOutputStream(m_gamedir+"/test.bmp");
                byte[] buf = new byte[8192];
                int read;
                while ((read = fis.read(buf))>0)
                	fos.write(buf,0,read);
                fos.close();
                fis.close();
                */
                Runtime.getRuntime().exec(m_gamedir+"/sqrxz_win32 -t",null,new File(m_gamedir));
            }
            catch(Exception ex)
            {
                ex.printStackTrace();
            }
        }
        else if (cmd.equals(TOOLBOX))
        {
            m_toolbox.setVisible(!m_toolbox.isVisible());
        }
	}

	public static void refresh(Component c)
	{
		SwingUtilities.windowForComponent(c).dispatchEvent(new ComponentEvent(c,ComponentEvent.COMPONENT_RESIZED));
	}

	public static void main(String[] args)
	{
		(new LevelEditor()).setVisible(true);
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
		if (e.getSource() == m_tab) {
			m_lvl.setCurrentSet(m_tab.getSelectedIndex());
		} else {
			Rectangle r = m_sp.getViewport().getViewRect();
			int x = m_sp.getHorizontalScrollBar().getValue()+(r.width>>1);
			int y = m_sp.getVerticalScrollBar().getValue()+(r.height>>1);
			moveScrollPane(m_psp,x>>2,y>>2);
		}
	}

	public void mouseClicked(MouseEvent e)
	{
		moveScrollPane(m_sp,e.getX()<<(m_lvl.getZoom()-3),e.getY()<<(m_lvl.getZoom()-3));
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

    public void mouseDragged(MouseEvent e)
    {
    }

    public void mouseMoved(MouseEvent e)
    {
        if (e.getSource()==m_lvl)
        {
            int x = e.getX()>>m_lvl.getZoom();
            int y = e.getY()>>m_lvl.getZoom();
		    if (x<LEVEL_WIDTH && y<LEVEL_HEIGHT) {
		    	int t = m_lvl.getTileAt(x, y);
		    	if (t >= 0) {
		    		m_status.setText("x="+x+" y="+y+ " t="+(t&0xfff) + " f="+m_imgset[t>>12].getFlip());
		    	} else {
		    		m_status.setText("x="+x+" y="+y);
		    	}
		    }
        }
        else if (e.getSource() instanceof ImageSet)
        {
            int tile = ((ImageSet)e.getSource()).getMouseTile();
            m_status.setText("t=" + (tile>=0?tile:""));
        }
    }
}
