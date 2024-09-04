import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.GridLayout;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.FileOutputStream;
import java.io.PrintWriter;

import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

public class CurveGenerator extends JFrame implements ChangeListener, ActionListener
{
	public static class WhiteBoard extends JPanel
	{
		double start,delta;
		int x0,y0;
		int nb,speed;
		Point[] crv;
		double ax0,ay0,dx0,dy0;

		public WhiteBoard()
		{
			this(0.0,0.4,80,0,256,4,0.0,0.01,0.0,0.1);
		}
		
		public WhiteBoard(double a,double d,int x,int y,int n,int s,double ax,double dx,double ay,double dy)
		{
			setParams(a,d,x,y,n,s,ax,dy,ay,dy);
		}

		public void setParams(double a,double d,int x,int y,int n,int s,double ax,double dx,double ay,double dy)
		{
			start = a;
			delta = d;
			float xx = x;
			float yy = y;
			x0 = x;
			y0 = y;
			nb = n;
			ax0 = ax;
			ay0 = ay;
			dx0 = dx;
			dy0 = dy;
			speed = s;
			crv = new Point[nb];
			for (int i=0;i<nb;i++)
			{
				crv[i] = new Point();
				crv[i].x = (int)xx;
				crv[i].y = (int)yy;
				xx+=Math.cos(ax)+Math.cos(a)*speed;
				yy+=Math.cos(ay)+Math.sin(a)*speed;
				a+=delta;
				ax+=dx;
				ay+=dy;
			}
		}
		
		public void paint(Graphics g)
		{
			g.setColor(Color.LIGHT_GRAY);
			g.fillRect(0,0,320,256);
			g.setColor(Color.WHITE);
			g.fillRect(32,52,256,153);
			g.setColor(Color.BLACK);
			g.drawRect(80,52,160,152);
			for (int i=0;i<nb;i++)
				g.drawLine(crv[i].x+32,crv[i].y+52,crv[i].x+32,crv[i].y+52);
			g.setColor(Color.GREEN);
			g.fillOval(crv[0].x+32,crv[0].y+52,4,4);
			g.setColor(Color.RED);
			g.fillOval(crv[nb-1].x+32,crv[nb-1].y+52,4,4);
		}
	}

	JSlider[] s = new JSlider[10];
	JLabel[] values = new JLabel[10];
	int[] min = new int[] {    0,    1, -255, -255,    1,    0,    0,    1,    0,    1};
	int[] max = new int[] {  360, 1000,  255,  255, 1024,   32,  360, 1000,  360, 1000};
	int[] ini = new int[] {    0,  100,   50,   45,  500,    2,  276,  500,  180,  250};
	String[] labels = new String[] {"start","delta","x","y","count","speed","ax","dx","ay","dy"};

	WhiteBoard b;

	public CurveGenerator()
	{
		super();
		Container c = getContentPane();
		c.setLayout(new BorderLayout());
		b = new WhiteBoard();
		c.add(b,BorderLayout.CENTER);
		b.setPreferredSize(new Dimension(320,256));
		JPanel p = new JPanel(new BorderLayout());
		JPanel pl = new JPanel(new GridLayout(10,1));
		JPanel pv = new JPanel(new GridLayout(10,1));
		JPanel ps = new JPanel(new GridLayout(10,1));
		p.add(pl,BorderLayout.WEST);
		p.add(pv,BorderLayout.CENTER);
		p.add(ps,BorderLayout.EAST);
		for (int i=0;i<10;i++)
		{
			pl.add(new JLabel(labels[i]));
			pv.add(s[i] = new JSlider(min[i],max[i]));
			s[i].setValue(ini[i]);
			s[i].addChangeListener(this);
			ps.add(values[i] = new JLabel());
		}
		JPanel pb = new JPanel();
		JButton jb = new JButton("Save");
		jb.addActionListener(this);
		pb.add(jb);
		p.add(pb,BorderLayout.SOUTH);
		stateChanged(null);
		c.add(p,BorderLayout.SOUTH);
		pack();
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
	}

	public void stateChanged(ChangeEvent e)
	{
		for (int i=0;i<10;i++)
			values[i].setText(String.valueOf(s[i].getValue()));
		b.setParams
			(
				(s[0].getValue()*Math.PI)/180,
				2*Math.PI/s[1].getValue(),
				s[2].getValue(),
				s[3].getValue(),
				s[4].getValue(),
				s[5].getValue(),
				(s[6].getValue()*Math.PI)/180,
				2*Math.PI/s[7].getValue(),
				(s[8].getValue()*Math.PI)/180,
				2*Math.PI/s[9].getValue()
			);
		b.repaint(0);
	}

	private int u8(int v)
	{
		return v<0?v+256:v;
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

	public void actionPerformed(ActionEvent e)
	{
		String fname = getFileName("Save curve",true);
		if (fname==null)
			return;
		try
		{
			PrintWriter pw = new PrintWriter(new FileOutputStream(fname));
			for (int i=0;i<10;i++)
				pw.println("// "+labels[i]+" = "+s[i].getValue());
			pw.println();
			pw.println("{"+(b.nb*2)+",{");
			int i;
			for (i=0;i<b.nb;i++)
			{
				pw.print(u8(b.crv[i].x)+","+u8(b.crv[i].y)+(i+1<2048?",":""));
				if ((i+1)%16==0)
					pw.println();
			}
			for (;i<1024;i++)
			{
				pw.print(0+","+0+(i+1<1024?",":""));
				if ((i+1)%16==0)
					pw.println();
			}
			pw.println("}}");
			pw.close();
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
		}
	}

	public static void main(String[] args)
	{
		CurveGenerator cg = new CurveGenerator();
		Dimension d = cg.getSize();
		Dimension ds = Toolkit.getDefaultToolkit().getScreenSize();
		cg.setLocation((ds.width-d.width)/2,(ds.height-d.height)/2);
		cg.setVisible(true);
	}
}
