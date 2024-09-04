package LevelEditor;

import java.awt.*;
import java.awt.event.*;
import java.util.Vector;

public class PanelImageGrid extends Panel {
  Vector lst;
  GridLayout gridLayoutMain = new GridLayout();
  Dimension prefSize;
  int width,height;
  private boolean modified;

  public PanelImageGrid() {
    try  {
        prefSize = new Dimension(100,100);
        jbInit();
        setModified(true);
    } catch (Exception ex) { ex.printStackTrace(); }
  }

  public PanelImageGrid(int w,int h) {
    this();
    this.width=w;
    this.height=h;
    gridLayoutMain.setColumns(w);
    gridLayoutMain.setRows(h);
    for (int i=0;i<w*h;i++) add(new PanelImage());
    setPreferredSize(w,h);
    setModified(true);
  }

  public void addImage(String iname) {
    PanelImage pi = new PanelImage(iname);
    if (lst==null) {
       lst = new Vector();
       gridLayoutMain.setHgap(4);
    }
    lst.addElement(pi);
    add(pi);
    gridSize();
    setModified(true);
  }

  public void remove(Component c) {
    super.remove(c);
    lst.removeElement(c);
    gridSize();
    setModified(true);
  }

  public Dimension getPreferredSize() { return prefSize; }

  public void setPreferredSize(int width,int height) {
    prefSize = new Dimension(width*FrameLevelEditor.zoom,height*FrameLevelEditor.zoom);
    setSize(prefSize);
  }

  private void gridSize() {
    gridLayoutMain.setColumns(lst.size());
    gridLayoutMain.setRows(1);
    setPreferredSize(lst.size(),1);
  }

  void jbInit() throws Exception {
    this.setLayout(gridLayoutMain);
  }

  void imageSelected(PanelImage pi) {
    if (FrameLevelEditor.tool!=null) FrameLevelEditor.tool.setSelected(false);
    FrameLevelEditor.tool = pi;
    pi.setSelected(true);
  }

  boolean isModified() { return modified; }

  void setModified(boolean mod) {
     modified = mod;
     Component parent = getParent();
     while ((parent!=null)&&!(parent instanceof FrameLevelEditor)) parent=parent.getParent();
     if (parent!=null) ((FrameLevelEditor)parent).modified();
  }
}

