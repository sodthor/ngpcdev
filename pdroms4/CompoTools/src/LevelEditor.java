import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JSeparator;

public class LevelEditor extends JFrame implements ActionListener
{
    private static final String NEW = "NEW";
    private static final String LOAD = "LOAD";
    private static final String SAVE = "SAVE";
    private static final String EXIT = "EXIT";
    private static final String SET = "SET_";
    private static final String ROT_A = "ROT_A";
    private static final String ROT_B = "ROT_B";
    private static final String PUSH_A = "PUSH_A";
    private static final String PUSH_B = "PUSH_B";
    private static final String SOLVE = "SOLVE";
    private static final String UP = "UP";
    private static final String DOWN = "DOWN";
    private static final String LEFT = "LEFT";
    private static final String RIGHT = "RIGHT";

    private LevelPanel level;
    private JFileChooser jfc;

    private LevelEditor()
    {
        super("Level Editor");
        JMenuBar mb = new JMenuBar();
        JMenu m = new JMenu("File");
        m.setMnemonic('F');
        addMenutItem(m, "New", 'N', NEW);
        addMenutItem(m, "Load", 'L', LOAD);
        addMenutItem(m, "Save", 'S', SAVE);
        m.add(new JSeparator());
        addMenutItem(m, "Exit", 'x', EXIT);
        mb.add(m);
        m = new JMenu("Actions");
        m.setMnemonic('c');
        addMenutItem(m, "Rotate A", 'o', ROT_A);
        addMenutItem(m, "Rotate B", 't', ROT_B);
        m.add(new JSeparator());
        addMenutItem(m, "Push A", 'A', PUSH_A);
        addMenutItem(m, "Push B", 'B', PUSH_B);
        m.add(new JSeparator());
        addMenutItem(m, "Solve", 'S', SOLVE);
        mb.add(m);
        setJMenuBar(mb);
        level = new LevelPanel();
        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(level, BorderLayout.CENTER);
        level.setSize((LevelPanel.ZOOM*160)>>3, (LevelPanel.ZOOM*152)>>3);
        level.setPreferredSize(level.getSize());
        JPanel tp = new JPanel(new BorderLayout());
        JPanel p = new JPanel(new GridLayout(7, 2, 4, 4));
        ButtonGroup bg = new ButtonGroup();
        addRadioButton(p,"wall",1,bg);
        addRadioButton(p,"start",2,bg);
        addRadioButton(p,"target",3,bg);
        addRadioButton(p,"tile orange",4,bg);
        addRadioButton(p,"tile purple",5,bg);
        addRadioButton(p,"tile blue",6,bg);
        addRadioButton(p,"wall orange",7,bg);
        addRadioButton(p,"wall purple",8,bg);
        addRadioButton(p,"wall blue",9,bg);
        addRadioButton(p,"tile grey",10,bg);
        addButton(p, "Up", 'U', UP);
        addButton(p, "Down", 'D', DOWN);
        addButton(p, "Left", 'L', LEFT);
        addButton(p, "Right", 'R', RIGHT);
        tp.add(p, BorderLayout.NORTH);
        getContentPane().add(tp, BorderLayout.EAST);
        p = new JPanel(new FlowLayout());
        addButton(p, "R", 'R', ROT_A);
        addButton(p, "L", 'L', ROT_B);
        addButton(p, "A", 'A', PUSH_A);
        addButton(p, "B", 'B', PUSH_B);
        addButton(p, "Solve", 'S', SOLVE);
        getContentPane().add(p, BorderLayout.SOUTH);
        pack();
        setResizable(false);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
    }

    private void addMenutItem(JMenu m, String label, char mnemo, String cmd)
    {
        JMenuItem mi = new JMenuItem(label, mnemo);
        mi.setActionCommand(cmd);
        mi.addActionListener(this);
        m.add(mi);
    }

    private void addRadioButton(JPanel p, String label, int idx, ButtonGroup bg)
    {
        JRadioButton rb = new JRadioButton(label, idx==1);
        rb.addActionListener(this);
        rb.setActionCommand(SET + idx);
        bg.add(rb);
        p.add(rb);
    }

    private void addButton(JPanel p, String label, char mnemo, String cmd)
    {
        JButton b = new JButton(label);
        b.setMnemonic(mnemo);
        b.setActionCommand(cmd);
        b.addActionListener(this);
        p.add(b);
    }

    public void actionPerformed(ActionEvent e)
    {
        String cmd = e.getActionCommand();
        if (cmd.equals(NEW))
        {
            setTitle("Level Editor");
            level.clearLevel();
        }
        else if (cmd.equals(LOAD))
        {
            if (jfc == null)
                jfc = new JFileChooser(".");
            jfc.showOpenDialog(this);
            File f = jfc.getSelectedFile();
            if (f != null && f.canRead())
            {
                try
                {
                    level.loadLevel(new FileInputStream(f));
                    setTitle("Level Editor: "+f.getName());
                }
                catch(IOException ioe)
                {
                    ioe.printStackTrace();
                }
            }
        }
        else if (cmd.equals(SAVE))
        {
            if (jfc == null)
                jfc = new JFileChooser();
            jfc.showSaveDialog(this);
            File f = jfc.getSelectedFile();
            if (f != null && (!f.exists() || f.canWrite()))
            {
                try
                {
                    level.saveLevel(new FileOutputStream(f));
                    String ln = f.getAbsolutePath();
                    ln = ln.substring(0, ln.indexOf(".h")) + "_lines.h";
                    level.saveLines(new FileOutputStream(ln));
                    setTitle("Level Editor: "+f.getName());
                }
                catch(IOException ioe)
                {
                    ioe.printStackTrace();
                }
            }
        }
        else if (cmd.equals(EXIT))
        {
            System.exit(0);
        }
        else if (cmd.startsWith(SET))
        {
            level.setPen(Integer.valueOf(cmd.substring(SET.length())));
        }
        else if (cmd.equals(ROT_A))
        {
            level.rotateA();
        }
        else if (cmd.equals(ROT_B))
        {
            level.rotateB();
        }
        else if (cmd.equals(PUSH_A))
        {
            level.pushA();
        }
        else if (cmd.equals(PUSH_B))
        {
            level.pushB();
        }
        else if (cmd.equals(SOLVE))
        {
            level.solveIt();
        }
        else if (cmd.equals(UP))
        {
            level.scroll(0,-1);
        }
        else if (cmd.equals(DOWN))
        {
            level.scroll(0,1);
        }
        else if (cmd.equals(LEFT))
        {
            level.scroll(-1,0);
        }
        else if (cmd.equals(RIGHT))
        {
            level.scroll(1,0);
        }
    }

    public static void main(String[] args)
    {
        (new LevelEditor()).setVisible(true);
    }
}
