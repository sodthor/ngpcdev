import java.awt.Container;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.HashMap;

import javax.swing.ButtonGroup;
import javax.swing.ImageIcon;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JSpinner;
import javax.swing.JToggleButton;

public class ToolBox extends JDialog implements ActionListener
{
    public static String POINTER = "POINTER";
    public static String SELECT  = "SELECT";
    public static String PEN     = "PEN";
    public static String COPY    = "COPY";
    public static String CUT     = "CUT";
    public static String PASTE   = "PASTE";
    public static String ERASER  = "ERASER";
    public static String PICKER  = "PICKER";

    private HashMap m_map = new HashMap();
    private String m_curtool;
    private Level m_lvl;
    private JSpinner m_spin;
    
    private JToggleButton getButton(ButtonGroup bg, String type)
    {
        JToggleButton jtb = new JToggleButton();
        jtb.setActionCommand(type);
        jtb.setIcon(new ImageIcon("icons/"+type+".GIF"));
        jtb.addActionListener(this);
        bg.add(jtb);
        m_map.put(type,jtb);
        return jtb;
    }
    
    public ToolBox(JFrame parent)
    {
        super(parent,"ToolBox",false);
        Container c = getContentPane();
        c.setLayout(new GridLayout(4,2));
        m_map = new HashMap();
        ButtonGroup bg = new ButtonGroup();
        c.add(getButton(bg,POINTER));
        c.add(getButton(bg,PEN));
        c.add(getButton(bg,SELECT));
        c.add(getButton(bg,COPY));
        c.add(getButton(bg,CUT));
        c.add(getButton(bg,PASTE));
        c.add(getButton(bg,ERASER));
        c.add(getButton(bg,PICKER));
        ((JToggleButton)m_map.get(PEN)).setSelected(true);
        setDefaultCloseOperation(JDialog.HIDE_ON_CLOSE);
        pack();
        setLocation(parent.getLocation().x+20, parent.getLocation().y+60);
        setVisible(true);
        m_curtool = PEN;
    }

    public void setLevel(Level l)
    {
    	m_lvl = l;
    	m_lvl.setTool(m_curtool);
    }

    public void actionPerformed(ActionEvent e)
    {
        m_curtool = e.getActionCommand();
        m_lvl.setTool(m_curtool);
        if (m_curtool.equals(COPY)||m_curtool.equals(CUT)||m_curtool.equals(PASTE))
        {
            ((JToggleButton)m_map.get(PEN)).setSelected(true);
            m_lvl.setTool(PEN);
        }
    }
}
