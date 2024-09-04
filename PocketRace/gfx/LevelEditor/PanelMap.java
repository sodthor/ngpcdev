package LevelEditor;

import java.awt.*;
import java.awt.event.*;
import java.util.Vector;

public class PanelMap extends Panel implements ActionListener {

  private static final Color GREY = new Color(63,63,63,127);
  Dimension prefSize;
  int width,height,cur,mx,my,_mx,_my;
  ItemImage[][][] arrays;
  boolean trajmode;
  PopupMenu menu;

  private boolean modified;
  Image img = null;

  public PanelMap(int w,int h) {
    this.width=w;
    this.height=h;
    this.cur = 0;
    this.trajmode = false;
    this.mx = this.my = -1;
    this.arrays = new ItemImage[2][w][h];
    this._mx = -1;
    for (int i=0;i<w;i++)
        for (int j=0;j<h;j++)
        {
            this.arrays[0][i][j] = new ItemImage();
            this.arrays[1][i][j] = new ItemImage();
        }
    setPreferredSize(w,h);
    enableEvents(AWTEvent.MOUSE_EVENT_MASK|AWTEvent.MOUSE_MOTION_EVENT_MASK);
    add(menu = new PopupMenu());
    MenuItem mi = new MenuItem("Remove");
    mi.setActionCommand("DEL");
    mi.addActionListener(this);
    menu.add(mi);
    mi = new MenuItem("Insert after");
    mi.setActionCommand("AFTER");
    mi.addActionListener(this);
    menu.add(mi);
    mi = new MenuItem("Insert before");
    mi.setActionCommand("BEFORE");
    mi.addActionListener(this);
    menu.add(mi);
  }

  public Dimension getPreferredSize() { return prefSize; }

  public void setPreferredSize(int width,int height) {
    prefSize = new Dimension(width*FrameLevelEditor.zoom,height*FrameLevelEditor.zoom);
    img = createImage(getSize().width,getSize().height);
    setSize(prefSize);
  }

  boolean isModified() { return modified; }

  void setModified(boolean mod) {
     modified = mod;
     Component parent = getParent();
     while ((parent!=null)&&!(parent instanceof FrameLevelEditor)) parent=parent.getParent();
     if (parent!=null) ((FrameLevelEditor)parent).modified();
  }

  public void processMouseEvent(MouseEvent me) {
    super.processMouseEvent(me);
    if (me.getID()==MouseEvent.MOUSE_PRESSED)
    {
       if (trajmode)
       {
          if ((me.getModifiers()&MouseEvent.BUTTON3_MASK)!=0)
          {
             _mx = me.getX()/FrameLevelEditor.zoom;
             _my = me.getY()/FrameLevelEditor.zoom;
             menu.show(this,me.getX(),me.getY());
          }
          else
          {
             mx = me.getX()/FrameLevelEditor.zoom;
             my = me.getY()/FrameLevelEditor.zoom;
             if (arrays[cur][mx][my].isSelected())
             {
                _mx = mx;
                _my = my;
             }
             else
                _mx = -1;
          }
       }
       else
       {
          mx = me.getX()/FrameLevelEditor.zoom;
          my = me.getY()/FrameLevelEditor.zoom;
          if ((me.getModifiers()&me.BUTTON3_MASK)!=0)
             arrays[cur][mx][my].clear();
          else if ((me.getModifiers()&me.BUTTON1_MASK)!=0)
             arrays[cur][mx][my].setLink(FrameLevelEditor.tool);
          setModified(true);
          doPaint(getGraphics(),false);
       }
    }
    else if (me.getID()==MouseEvent.MOUSE_RELEASED)
    {
       if (trajmode)
       {
          if (_mx>=0)
          {
             if (_mx!=mx || _my!=my)
             {
                if (!arrays[cur][mx][my].isSelected())
                   arrays[cur][mx][my].replace(arrays[cur][_mx][_my]);
                else
                   arrays[cur][_mx][_my].unselect();
                setModified(true);
                mx = -1;
                forcePaint();
             }
          }
          else
          {
             arrays[cur][mx][my].select();
             mx = -1;
             setModified(true);
             forcePaint();
          }
       }
       _mx = -1;
    }
  }

  public void actionPerformed(ActionEvent e)
  {
    String cmd = e.getActionCommand();
    int pos = FrameLevelEditor.points.indexOf(_mx+" "+_my);
    int pos1 = -1,posi = -1;
    if (cmd.equals("DEL"))
    {
       arrays[cur][_mx][_my].unselect();
       forcePaint();
       return;
    }
    else if (cmd.equals("AFTER"))
    {
       pos1 = pos+1 < FrameLevelEditor.points.size() ? pos+1 : 0;
       posi = pos+1;
    }
    else if (cmd.equals("BEFORE"))
    {
       pos1 = (pos > 0 ? pos : FrameLevelEditor.points.size())-1;
       posi = pos1+1;
    }
    String s = (String)FrameLevelEditor.points.elementAt(pos1);
    int px = s.indexOf(" ");
    int xx = (_mx + Integer.valueOf(s.substring(0,px)).intValue())/2;
    int yy = (_my + Integer.valueOf(s.substring(px+1)).intValue())/2;
    if (!arrays[cur][xx][yy].isSelected())
       arrays[cur][xx][yy].insertAt(posi);
    forcePaint();
  }

  public void processMouseMotionEvent(MouseEvent me) {
    super.processMouseMotionEvent(me);
    if (me.getID()==MouseEvent.MOUSE_DRAGGED)
    {
       mx = me.getX()/FrameLevelEditor.zoom;
       my = me.getY()/FrameLevelEditor.zoom;
       if (!trajmode)
       {
          if ((me.getModifiers()&me.BUTTON1_MASK)!=0)
             arrays[cur][mx][my].setLink(FrameLevelEditor.tool);
          else if ((me.getModifiers()&me.BUTTON3_MASK)!=0)
             arrays[cur][mx][my].clear();
          setModified(true);
          doPaint(getGraphics(),false);
       }
    }
  }

  public PanelImage getLink(int x,int y) {
    return arrays[y<height?cur:1][x][y%height].getLink();
  }

  public void setLink(PanelImage pi,int x,int y) {
    arrays[y<height?cur:1][x][y%height].setLink(pi);
  }

  public boolean isSelected(int x,int y) {
    return arrays[y<height?cur:1][x][y%height].isSelected();
  }

  public void select(int x,int y) {
    arrays[y<height?cur:1][x][y%height].select();
  }

  public void unselect(int x,int y) {
    arrays[y<height?cur:1][x][y%height].unselect();
  }

  public void paint(Graphics g) {
    doPaint(g,false);
  }

  public void doPaint(Graphics g,boolean force)
  {
    if (img==null)
       img = createImage(getSize().width,getSize().height);
    Graphics ig = img.getGraphics();
    if (mx<0||force)
    {
       ig.clearRect(0,0,getSize().width,getSize().height);
       for (int i=0;i<width;i++)
           for (int j=0;j<height;j++)
               updateTile(ig,i,j);
       if (trajmode && FrameLevelEditor.points.size()>0)
       {
          for (int i=0;i<width;i++)
              for (int j=0;j<height;j++)
                  arrays[0][i][j].paintTraj(ig,i,j,FrameLevelEditor.zoom,FrameLevelEditor.zoom);
       }
    }
    else
       updateTile(ig,mx,my);
    g.drawImage(img,0,0,null);
  }

  private void updateTile(Graphics ig,int i,int j)
  {
    boolean b = false;
    arrays[0][i][j].paint(ig,i,j,FrameLevelEditor.zoom,FrameLevelEditor.zoom,trajmode);
    if ((cur==0&&arrays[1][i][j].getLink()!=null)||(cur==1&&arrays[1][i][j].getLink()==null))
    {
       ig.setColor(GREY);
       ig.fillRect(i*FrameLevelEditor.zoom,j*FrameLevelEditor.zoom,FrameLevelEditor.zoom,FrameLevelEditor.zoom);
    }
    if (cur==1&&arrays[1][i][j].getLink()!=null)
       arrays[1][i][j].paint(ig,i,j,FrameLevelEditor.zoom,FrameLevelEditor.zoom,trajmode);
    if (trajmode)
    {
       ig.setColor(GREY);
       ig.fillRect(i*FrameLevelEditor.zoom,j*FrameLevelEditor.zoom,FrameLevelEditor.zoom,FrameLevelEditor.zoom);
    }
  }

  public void forcePaint()
  {
    mx = -1;
    doPaint(getGraphics(),true);
  }

  public void setPlane(int p)
  {
    cur = p;
    forcePaint();
  }

  public void setTrajMode(boolean b)
  {
    trajmode = b;
    forcePaint();
  }
}
