package LevelEditor;

import java.awt.*;
import java.awt.event.*;
import java.io.*;

public class PanelImage extends Panel {
  static Image defImg;
  Image img;
  String imgName;
  PanelImage link;
  private boolean selected = false;

  public PanelImage() {
    super();
    enableEvents(AWTEvent.MOUSE_EVENT_MASK);
    if (defImg==null) defImg=loadImage(FrameLevelEditor.DEF_IMAGE);
    setImage(defImg);
    imgName=FrameLevelEditor.DEF_IMAGE;
  }

  public PanelImage(String imageName) {
    super();
    enableEvents(AWTEvent.MOUSE_EVENT_MASK);
    img = loadImage(imageName);
    imgName=imageName;
  }

  static public Image loadImage(String imageName) {
    try {
        File f = new File(imageName);
        if (f.exists()) {
            FileInputStream fis = new FileInputStream(f);
            byte[] buf = new byte[(int)f.length()];
            fis.read(buf);
            Image img=Toolkit.getDefaultToolkit().createImage(buf);
            fis.close();
            MediaTracker mt = new MediaTracker(new Panel());
            mt.addImage(img,0);
            mt.waitForID(0);
            return img;
        } else throw new Exception("File "+imageName+" does not exists");
    } catch (Exception ex) { ex.printStackTrace(); return null; }
  }

  public void setImage(Image img) {
     this.img = img;
     repaint();
  }

  public Image getImage() { return img; }

  public String getImageName() { return imgName; }

  public void setLink(PanelImage pi) {
    link = pi;
    if (pi!=null) img = pi.getImage();
    else img = defImg;
    repaint();
  }

  public PanelImage getLink() { return link; }

  public void paint(Graphics g) {
    if ((img!=null)&&(g!=null)) {
       g.drawImage(img,0,0,this.getSize().width,this.getSize().height,0,0,img.getWidth(null),img.getHeight(null),null);
       if (selected) {
          g.setColor(Color.red);
          g.drawRect(0,0,this.getSize().width-1,this.getSize().height-1);
          g.drawRect(1,1,this.getSize().width-3,this.getSize().height-3);
       }
    }
  }

  public void processMouseEvent(MouseEvent me) {
    super.processMouseEvent(me);
    if (me.getID()==MouseEvent.MOUSE_PRESSED)
       ((PanelImageGrid)getParent()).imageSelected(this);
  }

  void setSelected(boolean sel) {
     selected = sel;
     repaint();
  }

  public boolean getSelected() { return selected; }
}
