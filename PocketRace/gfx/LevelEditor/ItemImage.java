package LevelEditor;

import java.awt.*;

public class ItemImage
{
    static Image defImg;
    Image img;
    String imgName;
    private boolean selected;
    PanelImage link;
    int x=-1,y=-1;

    public ItemImage()
    {
        selected = false;
        if(defImg == null)
            defImg = PanelImage.loadImage("LevelEditor\\default.jpg");
        setImage(defImg);
        imgName = "LevelEditor\\default.jpg";
    }

    public void setImage(Image image)
    {
        img = image;
    }

    public Image getImage()
    {
        return img;
    }

    public String getImageName()
    {
        return imgName;
    }

    public void paint(Graphics g, int i, int j, int k, int l, boolean trajmode)
    {
        if(img != null && g != null)
        {
            x = i; y = j;
            g.drawImage(img, i * k, j * l, i * k + k, j * l + l, 0, 0, img.getWidth(null), img.getHeight(null), null);
            if(selected && trajmode)
            {
                int i1 = i * k + k / 2;
                int j1 = j * l + l / 2;
                g.setColor(Color.green);
                g.drawRect(i1, j1, 2, 2);
                g.setColor(Color.red);
                g.drawRect(i1 - 1, j1 - 1, 4, 4);
                g.setColor(Color.blue);
                g.drawRect(i1 - 2, j1 - 2, 6, 6);
                g.setColor(Color.red);
                g.drawRect(i1 - 3, j1 - 3, 8, 8);
                g.setColor(Color.green);
                g.drawRect(i1 - 4, j1 - 4, 10, 10);
            }
        }
    }

    public void paintTraj(Graphics g, int i, int j, int k, int l)
    {
      if (g == null || !selected)
         return;
      g.setColor(Color.white);
      g.drawString(String.valueOf(FrameLevelEditor.points.indexOf(i+" "+j)),i*k+k,j*l+l);
      g.drawString(String.valueOf(FrameLevelEditor.points.indexOf(i+" "+j)),i*k+k,j*l+l);
    }

    /* Trajectory point management */

    void unselect()
    {
      selected = false;
      FrameLevelEditor.points.removeElement(x+" "+y);
    }

    void insertAt(int pos)
    {
      selected = true;
      FrameLevelEditor.points.insertElementAt(x+" "+y,pos);
    }

    void replace(ItemImage old)
    {
      selected = true;
      int pos = FrameLevelEditor.points.indexOf(old.x+" "+old.y);
      old.unselect();
      FrameLevelEditor.points.insertElementAt(x+" "+y,pos);
    }

    void select()
    {
      selected = true;
      FrameLevelEditor.points.addElement(x+" "+y);
    }

    boolean isSelected()
    {
        return selected;
    }

    /* Image link */

    PanelImage getLink()
    {
        return link;
    }

    void clear()
    {
        setLink(null);
    }

    void setLink(PanelImage panelimage)
    {
        link = panelimage;
        if(panelimage != null)
        {
            setImage(panelimage.getImage());
            return;
        } else
        {
            setImage(defImg);
            return;
        }
    }
}