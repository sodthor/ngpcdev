package LevelEditor;

import java.awt.*;
import java.awt.image.*;
import java.awt.event.*;
import java.io.*;
import java.util.*;

public class FrameLevelEditor extends Frame implements ActionListener,ItemListener{
  BorderLayout borderLayoutMain = new BorderLayout();
  Panel panelTools = new Panel();
  GridLayout gridLayoutTools = new GridLayout();
  ScrollPane scrollPaneWall = new ScrollPane(ScrollPane.SCROLLBARS_AS_NEEDED);
  ScrollPane scrollPaneGround = new ScrollPane(ScrollPane.SCROLLBARS_AS_NEEDED);
  MenuBar menuBarMain = new MenuBar();
  ScrollPane scrollPaneMap = new ScrollPane(ScrollPane.SCROLLBARS_AS_NEEDED);
  PanelMap panelMap = new PanelMap(1,1);
  PanelImageGrid panelGridWall = new PanelImageGrid();
  PanelImageGrid panelGridGround = new PanelImageGrid();
  Menu menuFile = new Menu();
  MenuItem menuItemOpen = new MenuItem();
  MenuItem menuItemClose = new MenuItem();
  MenuItem menuItemNew = new MenuItem();
  MenuItem menuItemQuit = new MenuItem();
  MenuItem menuItemSaveAs = new MenuItem();
  MenuItem menuItemSave = new MenuItem();
  Menu menuIInsert = new Menu();
  MenuItem menuItemWall = new MenuItem();
  MenuItem menuItemGround = new MenuItem();
  MenuItem menuItemActor = new MenuItem();
  Menu menuEdit = new Menu();
  MenuItem menuItemProperties = new MenuItem();
  CheckboxMenuItem menuItemPlane = new CheckboxMenuItem();
  Panel panelPageMap = new Panel();
  Panel panelPageWall = new Panel();
  Panel panelPageGround = new Panel();
  ScrollPane scrollPaneActor = new ScrollPane(ScrollPane.SCROLLBARS_AS_NEEDED);
  Panel panelPageActor = new Panel();
  PanelImageGrid panelGridActor = new PanelImageGrid();
  Panel panelMain = new Panel();
  BorderLayout borderLayoutPanelMain = new BorderLayout();
  Panel panelLabels = new Panel();
  GridLayout gridLayoutLabels = new GridLayout();
  Label labelWall = new Label();
  Label labelActor = new Label();
  Label labelGround = new Label();
  CheckboxMenuItem menuItemTraj = new CheckboxMenuItem();

  static final String DEF_IMAGE = "LevelEditor\\default.jpg";
  static public int zoom = 48;
  String mapFileName = null, walls = "walls", grounds = "grounds", name="level";
  MenuItem menuItemGenerateNGPC = new MenuItem();
  MenuItem manuItemLoadTraj = new MenuItem();
  MenuItem manuItemSaveTraj = new MenuItem();
  Vector imgs = new Vector();
  static Vector points;
  static PanelImage tool = null;

  public FrameLevelEditor() {
    try  {
        enableEvents(AWTEvent.WINDOW_EVENT_MASK);
        jbInit();
        menuItemOpen.addActionListener(this);
        menuItemClose.addActionListener(this);
        menuItemClose.setEnabled(false);
        menuItemNew.addActionListener(this);
        menuItemQuit.addActionListener(this);
        menuItemSaveAs.addActionListener(this);
        menuItemSaveAs.setEnabled(false);
        menuItemSave.addActionListener(this);
        menuItemSave.setEnabled(false);
        menuItemWall.addActionListener(this);
        menuItemWall.setEnabled(false);
        menuItemGround.addActionListener(this);
        menuItemGround.setEnabled(false);
        menuItemActor.addActionListener(this);
        menuItemActor.setEnabled(false);
        menuItemProperties.addActionListener(this);
        menuItemProperties.setEnabled(false);
        menuItemPlane.addItemListener(this);
        menuItemPlane.setEnabled(false);
        menuItemTraj.addItemListener(this);
        menuItemTraj.setEnabled(false);
        menuItemGenerateNGPC.addActionListener(this);
        menuItemGenerateNGPC.setEnabled(false);
        manuItemLoadTraj.addActionListener(this);
        manuItemLoadTraj.setEnabled(false);
        manuItemSaveTraj.addActionListener(this);
        manuItemSaveTraj.setEnabled(false);
    } catch (Exception e) { e.printStackTrace(); }
  }

  private void jbInit() throws Exception {
    this.setSize(new Dimension(641, 445));
    gridLayoutTools.setColumns(3);
    menuFile.setLabel("File");
    menuFile.setActionCommand("");
    menuItemOpen.setLabel("Open");
    menuItemOpen.setActionCommand("Open");
    menuItemClose.setLabel("Close");
    menuItemClose.setActionCommand("Close");
    menuItemNew.setLabel("New");
    menuItemNew.setActionCommand("New");
    menuItemQuit.setLabel("Quit");
    menuItemQuit.setActionCommand("Quit");
    menuItemSaveAs.setLabel("Save as");
    menuItemSaveAs.setActionCommand("Save as");
    menuItemSave.setLabel("Save");
    menuItemSave.setActionCommand("Save");
    menuIInsert.setLabel("Insert");
    menuIInsert.setActionCommand("");
    menuItemWall.setLabel("Wall");
    menuItemWall.setActionCommand("Wall");
    menuItemGround.setLabel("Ground");
    menuItemGround.setActionCommand("Ground");
    menuItemActor.setLabel("Actor");
    menuItemActor.setActionCommand("Actor");
    menuEdit.setLabel("Edit");
    menuEdit.setActionCommand("");
    menuItemProperties.setLabel("Properties");
    menuItemProperties.setActionCommand("Properties");
    menuItemPlane.setLabel("Plane 2");
    menuItemTraj.setLabel("Trajectory");
    panelPageMap.setBounds(new Rectangle(0, 0, 610, 170));
    panelPageActor.setBounds(new Rectangle(2, 1, 310, 112));
    gridLayoutLabels.setColumns(3);
    labelWall.setText("Walls");
    labelActor.setText("Actor");
    labelGround.setText("Grounds");
    menuItemGenerateNGPC.setLabel("Generate NGPC");
    manuItemLoadTraj.setLabel("Load Trajectory");
    manuItemSaveTraj.setLabel("Save Trajectory");
    panelLabels.setLayout(gridLayoutLabels);
    panelMain.setLayout(borderLayoutPanelMain);
    panelTools.setLayout(gridLayoutTools);
    this.setLayout(borderLayoutMain);
    this.add(panelTools, BorderLayout.SOUTH);
    panelTools.add(scrollPaneWall);
    scrollPaneWall.add(panelPageWall);
    panelPageWall.add(panelGridWall);
    panelTools.add(scrollPaneGround);
    scrollPaneGround.add(panelPageGround);
    panelPageGround.add(panelGridGround);
    panelTools.add(scrollPaneActor, null);
    scrollPaneActor.add(panelPageActor, null);
    panelPageActor.add(panelGridActor, null);
    this.add(panelMain, BorderLayout.CENTER);
    panelMain.add(scrollPaneMap, BorderLayout.CENTER);
    scrollPaneMap.add(panelPageMap, null);
    panelPageMap.add(panelMap);
    panelMain.add(panelLabels, BorderLayout.SOUTH);
    panelLabels.add(labelWall, null);
    panelLabels.add(labelGround, null);
    panelLabels.add(labelActor, null);
    menuBarMain.add(menuFile);
    menuBarMain.add(menuIInsert);
    menuBarMain.add(menuEdit);
    menuFile.add(menuItemNew);
    menuFile.add(menuItemOpen);
    menuFile.add(menuItemSaveAs);
    menuFile.add(menuItemSave);
    menuFile.add(menuItemClose);
    menuFile.addSeparator();
    menuFile.add(menuItemGenerateNGPC);
    menuFile.addSeparator();
    menuEdit.add(menuItemTraj);
    menuFile.add(manuItemLoadTraj);
    menuFile.add(manuItemSaveTraj);
    menuFile.addSeparator();
    menuFile.add(menuItemQuit);
    menuIInsert.add(menuItemWall);
    menuIInsert.add(menuItemGround);
    menuIInsert.add(menuItemActor);
    menuEdit.add(menuItemProperties);
    menuEdit.add(menuItemPlane);
    this.setMenuBar(menuBarMain);
  }

  protected void processWindowEvent(WindowEvent e) {
    if (e.getID() == WindowEvent.WINDOW_CLOSING) {
       cancel();
    }
    super.processWindowEvent(e);
  }

  public void actionPerformed(ActionEvent e) {
    String cmd = e.getActionCommand();
    System.err.println(cmd);
    if (cmd.equals("New")) {
       DialogProperties dp = new DialogProperties(this,panelMap.width,panelMap.height,zoom,walls,grounds,imgs,name);
       dp.setVisible(true);
       if ((dp.width>0)&&(dp.height>0)) {
          panelPageMap.remove(panelMap);
          panelMap = new PanelMap(dp.width,dp.height);
          zoom = dp.zoom;
          panelPageMap.add(panelMap);
          recupProperties(dp);
          dp.dispose();
          menuItemSaveAs.setEnabled(true);
          menuItemGenerateNGPC.setEnabled(true);
          manuItemLoadTraj.setEnabled(true);
          manuItemSaveTraj.setEnabled(true);
          menuItemSave.setEnabled(true);
          menuItemWall.setEnabled(true);
          menuItemGround.setEnabled(true);
          menuItemActor.setEnabled(true);
          menuItemProperties.setEnabled(true);
          menuItemPlane.setEnabled(true);
          menuItemTraj.setEnabled(true);
       }
    } else if (cmd.equals("Open")) {
       readMap();
       menuItemSaveAs.setEnabled(true);
       menuItemGenerateNGPC.setEnabled(true);
       manuItemLoadTraj.setEnabled(true);
       manuItemSaveTraj.setEnabled(true);
       menuItemSave.setEnabled(false);
       menuItemClose.setEnabled(true);
       menuItemWall.setEnabled(true);
       menuItemGround.setEnabled(true);
       menuItemActor.setEnabled(true);
       menuItemProperties.setEnabled(true);
       menuItemPlane.setEnabled(true);
       menuItemTraj.setEnabled(true);
    } else if (cmd.equals("Close")) {
    } else if (cmd.equals("Save as")) {
       saveMapAs();
    } else if (cmd.equals("Save")) {
       saveMap();
    } else if (cmd.equals("Generate NGPC")) {
       generateNGPC();
    } else if (cmd.equals("Load Trajectory")) {
       FileDialog fd = new FileDialog(this,"Load Trajectory File",FileDialog.LOAD);
       fd.setVisible(true);
       if (fd.getFile()!=null)
       {
          try
          {
              loadTraj(fd.getDirectory()+fd.getFile());
          }
          catch(Exception ex)
          {
              ex.printStackTrace();
          }
          dispatchEvent(new ComponentEvent(this,ComponentEvent.COMPONENT_RESIZED));
          panelMap.doPaint(panelMap.getGraphics(),true);
       }
    } else if (cmd.equals("Save Trajectory")) {
       FileDialog fd = new FileDialog(this,"Save Trajectory File",FileDialog.SAVE);
       fd.setVisible(true);
       if (fd.getFile()!=null)
       {
          try
          {
              saveTraj(fd.getDirectory()+fd.getFile());
          }
          catch(Exception ex)
          {
              ex.printStackTrace();
          }
       }
    } else if (cmd.equals("Quit")) {
       cancel();
    } else if (cmd.equals("Wall")) {
       addItem(panelGridWall,"Load Wall item");
    } else if (cmd.equals("Ground")) {
       addItem(panelGridGround,"Load Ground item");
    } else if (cmd.equals("Actor")) {
       addItem(panelGridActor,"Load Actor item");
    } else if (cmd.equals("Properties")) {
       DialogProperties dp = new DialogProperties(this,panelMap.width,panelMap.height,zoom,walls,grounds,imgs,name);
       dp.textWidth.setEnabled(false);
       dp.textHeight.setEnabled(false);
       dp.buttonCancel.setVisible(false);
       dp.setVisible(true);
       if (dp.zoom!=-1) recupProperties(dp);
       dp.dispose();
    }
    dispatchEvent(new ComponentEvent(this,ComponentEvent.COMPONENT_RESIZED));
  }

  public void itemStateChanged(ItemEvent e)
  {
     if (e.getSource()==menuItemPlane)
     	panelMap.setPlane(menuItemPlane.getState()?1:0);
     else
     {
     	panelMap.setTrajMode(menuItemTraj.getState());
     }
  }

  private void cancel() {
    if (panelMap.isModified()||panelGridGround.isModified()||panelGridWall.isModified()||panelGridActor.isModified()) {
       AskDialog d = new AskDialog(this,"Note :","Map modified : Saving before leaving ?",true);
       d.setVisible(true);
       if (d.result==AskDialog.YES) {
          if (mapFileName!=null) saveMapAs();
          else saveMap();
       }
    }
    dispose();
    System.exit(0);
  }

  private void recupProperties(DialogProperties dp) {
     zoom = dp.zoom;
     panelMap.setPreferredSize(panelMap.width,panelMap.height);
     if (panelGridWall.lst!=null) panelGridWall.setPreferredSize(panelGridWall.lst.size(),1);
     if (panelGridGround.lst!=null) panelGridGround.setPreferredSize(panelGridGround.lst.size(),1);
     if (panelGridActor.lst!=null) panelGridActor.setPreferredSize(panelGridActor.lst.size(),1);
     walls = dp.walls;
     grounds = dp.grounds;
     name = dp.name;
     imgs.removeAllElements();
     for (int i=0;i<dp.listImgs.getItemCount();i++) imgs.addElement(dp.listImgs.getItem(i));
     modified();
  }

  private void addItem(PanelImageGrid pig, String title) {
    FileDialog fd = new FileDialog(this,"Load Wall image",FileDialog.LOAD);
    fd.setVisible(true);
    if (fd.getFile()!=null) {
       File f = new File(fd.getDirectory()+(fd.getFile().equals("#")?"":fd.getFile()));
       if (f.exists()) {
          if (f.isDirectory()) {
             String[] lst = f.list();
             for (int i=0;i<lst.length;i++) {
                 if (lst[i].endsWith(".gif")||lst[i].endsWith(".jpg")||
                     lst[i].endsWith(".GIF")||lst[i].endsWith(".JPG"))
                    pig.addImage(f.getAbsolutePath()+lst[i]);
             }
          } else pig.addImage(f.getAbsolutePath());
       }
    }
    fd.dispose();
  }

  public void readMap() {
    FileDialog fd = new FileDialog(this,"Load Map File",FileDialog.LOAD);
    fd.setVisible(true);
    if (fd.getFile()!=null) {
       File f = new File(fd.getDirectory()+fd.getFile());
       try {
           LineNumberReader lnr = new LineNumberReader(new FileReader(f));
           panelPageWall.remove(panelGridWall);
           panelPageGround.remove(panelGridGround);
           panelPageActor.remove(panelGridActor);
           panelPageMap.remove(panelMap);
           panelGridWall = new PanelImageGrid();
           panelGridGround = new PanelImageGrid();
           panelGridActor = new PanelImageGrid();
           panelPageWall.add(panelGridWall);
           panelPageGround.add(panelGridGround);
           panelPageActor.add(panelGridActor);
           panelMap = null;
           imgs.removeAllElements();
           loadMap(lnr);
           mapFileName=f.getAbsolutePath();
           setTitle(mapFileName);
           loadTraj(mapFileName+".p");
           panelGridWall.setModified(false);
           panelGridGround.setModified(false);
           panelGridActor.setModified(false);
           panelMap.setModified(false);
           dispatchEvent(new ComponentEvent(this,ComponentEvent.COMPONENT_RESIZED));
       } catch(Exception e) { e.printStackTrace(); }
    }
  }

  private void loadTraj(String fname) throws Exception
  {
    File fp = new File(fname);
    if (!fp.exists())
       return;
    for (int i=0;i<panelMap.height;i++)
        for (int j=0;j<panelMap.width;j++)
            if (panelMap.isSelected(j,i))
               panelMap.unselect(j,i);
    points = new Vector();
    panelMap.setModified(true);
    panelMap.forcePaint();
    loadMap(new LineNumberReader(new FileReader(fp)));
    Vector p = points;
    points = new Vector();
    for (int i=0;i<p.size();i++)
    {
        String pt = (String)p.elementAt(i);
        int x = Integer.valueOf(pt.substring(0,pt.indexOf(" "))).intValue();
        int y = Integer.valueOf(pt.substring(pt.indexOf(" ")+1)).intValue();
        panelMap.select(x,y);
    }
    panelMap.forcePaint();
    p.removeAllElements();
  }

  private void saveTraj(String fname) throws Exception
  {
    PrintWriter pw = new PrintWriter(new FileOutputStream(new File(fname)));
    pw.println("[POINT]");
    for (int i=0;i<points.size();i++) pw.println((String)points.elementAt(i));
    pw.close();
  }

  private void loadMap(LineNumberReader lnr) throws Exception
  {
    int w=-1,h=-1;
    String line = lnr.readLine();
    String com = "";
    while (line!=null) {
        if (line.equals("")) {
           line = lnr.readLine();
           continue;
        }
        if (line.charAt(0)=='[') com = line;
        else {
           if (com.equals("[MAP]")) {
              if (panelMap == null) {
                 panelMap = new PanelMap(w,h);
                 panelPageMap.add(panelMap);
                 //dispatchEvent(new ComponentEvent(this,ComponentEvent.COMPONENT_RESIZED));
                 h=-1;
              }
              w=0;h++;
              while (!line.equals("")) {
                  int i = line.indexOf(" ");
                  if (i>1) {
                     int j = Integer.valueOf(line.substring(1,i)).intValue();
                     if (line.charAt(0)=='W') panelMap.setLink((PanelImage)panelGridWall.lst.elementAt(j),w,h);
                     else if (line.charAt(0)=='G') panelMap.setLink((PanelImage)panelGridGround.lst.elementAt(j),w,h);
                     else if (line.charAt(0)=='A') panelMap.setLink((PanelImage)panelGridActor.lst.elementAt(j),w,h);
                  }
                  line = line.substring(i+1);
                  w++;
              }
           } else if (com.equals("[WIDTH]")) {
              w = Integer.valueOf(line).intValue();
           } else if (com.equals("[HEIGHT]")) {
              h = Integer.valueOf(line).intValue();
           } else if (com.equals("[ZOOM]")) {
              zoom = Integer.valueOf(line).intValue();
           } else if (com.equals("[NAME]")) {
              name = line;
           } else if (com.equals("[WALL]")) {
              panelGridWall.addImage(line);
           } else if (com.equals("[GROUND]")) {
              panelGridGround.addImage(line);
           } else if (com.equals("[ACTOR]")) {
              panelGridActor.addImage(line);
           } else if (com.equals("[WALLVAR]")) {
              walls = line;
           } else if (com.equals("[GROUNDVAR]")) {
              grounds = line;
           } else if (com.equals("[IMGVAR]")) {
              imgs.addElement(line);
           } else if (com.equals("[POINT]")) {
              points.addElement(line);
           }
        }
        line = lnr.readLine();
    }
    lnr.close();
  }

  private void saveMap() {
    menuItemTraj.setState(false);
    panelMap.setTrajMode(false);
    menuItemPlane.setState(false);
    panelMap.setPlane(0);
    try {
        File f = new File(mapFileName);
        PrintWriter pw = new PrintWriter(new FileOutputStream(f));
        pw.println("[WIDTH]");
        pw.println(panelMap.width);
        pw.println("[HEIGHT]");
        pw.println(panelMap.height);
        pw.println("[ZOOM]");
        pw.println(zoom);
        pw.println("[NAME]");
        pw.println(name);
        pw.println("[WALL]");
        if (panelGridWall.lst!=null) {
           for (int i=0;i<panelGridWall.lst.size();i++)
               pw.println(((PanelImage)panelGridWall.lst.elementAt(i)).getImageName());
        }
        pw.println("[GROUND]");
        if (panelGridGround.lst!=null) {
           for (int i=0;i<panelGridGround.lst.size();i++)
               pw.println(((PanelImage)panelGridGround.lst.elementAt(i)).getImageName());
        }
        pw.println("[ACTOR]");
        if (panelGridActor.lst!=null) {
           for (int i=0;i<panelGridActor.lst.size();i++)
               pw.println(((PanelImage)panelGridActor.lst.elementAt(i)).getImageName());
        }
        pw.println("[WALLVAR]");
        pw.println(walls);
        pw.println("[GROUNDVAR]");
        pw.println(grounds);
        pw.println("[IMGVAR]");
        for (int i=0;i<imgs.size();i++) pw.println((String)imgs.elementAt(i));
        pw.println("[MAP]");
        for (int i=0;i<panelMap.height*2;i++) {
            for (int j=0;j<panelMap.width;j++) {
                PanelImage link = panelMap.getLink(j,i);
                if (link!=null) {
                   int k = panelGridWall.lst.indexOf(link);
                   if (k>=0) pw.print("W"+k+" ");
                   else {
                      k = panelGridGround.lst.indexOf(link);
                      if (k>=0) pw.print("G"+k+" ");
                      else pw.print("A"+panelGridActor.lst.indexOf(link)+" ");
                   }
                } else pw.print("B ");
            }
            pw.println();
        }
        pw.close();
        saveTraj(mapFileName+".p");
        panelMap.setModified(false);
        panelGridWall.setModified(false);
        panelGridGround.setModified(false);
        panelGridActor.setModified(false);
        menuItemSave.setEnabled(false);
        menuItemClose.setEnabled(true);
    } catch (Exception e) { e.printStackTrace(); }
  }

  private void saveMapAs() {
    FileDialog fd = new FileDialog(this,"Save Map File as...",FileDialog.SAVE);
    fd.setVisible(true);
    if (fd.getFile()!=null) {
       mapFileName = fd.getDirectory()+fd.getFile();
       setTitle(mapFileName);
       saveMap();
    }
  }

  void modified() {
    menuItemSave.setEnabled(true);
  }

  private String int2Hex(int i,int s) {
    String val = "000"+Integer.toHexString(i);
    return val.substring(val.length()-s);
  }

  private boolean compPals(Vector a,Vector b)
  {
    int i = 0;
    while (i<4 && ((Integer)a.elementAt(i)).compareTo((Integer)b.elementAt(i))==0)
        i++;
    return i==4;
  }

  private int rgb2ngp(int col)
  {
    return ((col&0xf0)<<4)+((col&0xf000)>>8)+((col&0xf00000)>>20);
  }

  private void saveTiles(Vector imgs,Vector pals,Vector palIdx,PrintWriter pw) throws Exception
  {
    int pix[] = new int[64],a=0,end=imgs.size();
    if (palIdx.size()==0)
    {
       Arrays.fill(pix,0); // empty tile
       end+=1;
       a=1;
    }
    for (int i=0;i<end;i++)
    {
        if (a==0||i>0)
        {
           PanelImage pi = (PanelImage)imgs.elementAt(i-a);
           PixelGrabber pg = new PixelGrabber(pi.getImage(),0,0,8,8,pix,0,8);
           pg.grabPixels();
        }
        Vector pal = new Vector(4);
        /* Compute palette */
        pal.addElement(new Integer(rgb2ngp(pix[0])));
        for (int j=1;j<64 && pal.size()<4;j++)
        {
            int k=0;
            int pj = rgb2ngp(pix[j]);
            while (k<pal.size())
            {
                int pk = ((Integer)pal.elementAt(k)).intValue();
                if (pk>=pj)
                {
                   if (pk>pj)
                      pal.insertElementAt(new Integer(pj),k);
                   pj = -1;
                   break;
                }
                else
                   k++;
            }
            if (pj>=0)
               pal.addElement(new Integer(pj));
        }
        while (pal.size()<4)
            pal.insertElementAt(new Integer(0),0);
        /* Find palette in known list */
        boolean b = true;
        for (int j=0;j<pals.size()&&b;j++)
        {
            if (compPals(pal,(Vector)pals.elementAt(j)))
            {
               palIdx.addElement(new Integer(j));
               b = false;
            }
        }
        if (b)
        {
           palIdx.addElement(new Integer(pals.size()));
           pals.addElement(pal);
        }
        /* ngp tile encoding */
        for (int j=0;j<8;j++)
        {
            int v = 0;
            for (int k=0;k<8;k++)
                v = (v<<2)+pal.indexOf(new Integer(rgb2ngp(pix[j*8+k])));
            pw.print("0x"+int2Hex(v,4)+(j<7?",":",\n"));
        }
    }
  }

  private int tileCode(int num,Vector palIdx)
  {
    num+=1;
    return num+(((Integer)palIdx.elementAt(num)).intValue()<<9);
  }

  private String savePlane(int plane,PrintWriter pw,Vector palIdx)
  {
    String s = "";
    int dg  = panelGridWall.lst.size(), da = panelGridWall.lst.size()+panelGridGround.lst.size();
    int dp = plane==0?0:panelMap.height;
    for (int i=0;i<panelMap.width;i++)
    {
        for (int j=0;j<panelMap.height;j++)
        {
            PanelImage link = panelMap.getLink(i,j+dp);
            if (link!=null) {
               int k = panelGridWall.lst.indexOf(link);
               if (k>=0) {
                  pw.print("0x"+int2Hex(tileCode(k,palIdx),4));
                  s += "0";
               } else {
                  k = panelGridGround.lst.indexOf(link);
                  if (k>=0) {
                     pw.print("0x"+int2Hex(tileCode(k+dg,palIdx),4));
                     s += "1";
                  } else {
                     k = panelGridActor.lst.indexOf(link);
                     pw.print("0x"+int2Hex(tileCode(k+da,palIdx),4));
                     s += "2";
                  }
               }
            } else {
               pw.print("0x0000");
               s += "3";
            }
            String sep = j==panelMap.height-1&&i==panelMap.width-1?"":",";
            pw.print(sep);
            s+= sep;
        }
        pw.println((i==panelMap.width-1?"};":""));
        s+= (i==panelMap.width-1?"}":"")+System.getProperty("line.separator");
    }
    pw.println();
    return s;
  }

  private void generateNGPC() {
    FileDialog fd = new FileDialog(this,"Save NGPC File as...",FileDialog.SAVE);
    fd.setVisible(true);
    if (fd.getFile()!=null) {
       try {
           File f = new File(fd.getDirectory()+fd.getFile()+(fd.getFile().endsWith(".ngpc")?"":".ngpc"));
           PrintWriter pw = new PrintWriter(new FileOutputStream(f));

           /* Tiles */
           Vector pals = new Vector();
           Vector palIdx = new Vector();
           String defTiles = name.toUpperCase()+"_TILES";
           pw.println("#define "+defTiles+" "+(panelGridWall.lst.size()+panelGridGround.lst.size()+panelGridActor.lst.size()+1));
           pw.println("const u16 "+name+"_tiles["+defTiles+"*8+1]= {");
           pw.println("/* Wall */");
           saveTiles(panelGridWall.lst,pals,palIdx,pw);
           pw.println("/* Ground */");
           saveTiles(panelGridGround.lst,pals,palIdx,pw);
           pw.println("/* Actor */");
           saveTiles(panelGridActor.lst,pals,palIdx,pw);
           pw.println("0x0000};");
           pw.println();

           String defPals = name.toUpperCase()+"_PALS";
           pw.println("#define "+defPals+" "+pals.size());
           pw.println("const u16 "+name+"_pals["+defPals+"*4]= {");
           String s = null;
           for (int i=0;i<pals.size();i++)
           {
               Vector pal = (Vector)pals.elementAt(i);
               s="";
               for (int j=0;j<4;j++)
               {
                   s+="0x"+int2Hex(((Integer)pal.elementAt(j)).intValue(),4)+(j<3?",":"");
               }
               pw.println(s+(i+1<pals.size()?",":""));
           }
           pw.println("};");
           pw.println();

           String defWidth = name.toUpperCase()+"_WIDTH";
           String defHeight = name.toUpperCase()+"_HEIGHT";
           pw.println("#define "+defWidth+" "+panelMap.width);
           pw.println("#define "+defHeight+" "+panelMap.height);
           pw.println();
           pw.println("const u16 "+name+"_map_1["+defWidth+"*"+defHeight+"] = {");
           String t1 = savePlane(0,pw,palIdx);
           pw.println("const u16 "+name+"_map_2["+defWidth+"*"+defHeight+"] = {");
           String t2 = savePlane(1,pw,palIdx);
           pw.println("const u8 "+name+"_type_1["+defWidth+"*"+defHeight+"] = {");
           pw.println(t1+";");
           pw.println();
           pw.println("const u8 "+name+"_type_2["+defWidth+"*"+defHeight+"] = {");
           pw.println(t2+";");
           pw.println();
           String defMark = name.toUpperCase()+"_MARK";
           pw.println("#define "+defMark+" "+points.size()*2);
           pw.println();
           pw.println("const u8 "+name+"_mark["+defMark+"]= {");
           for (int i=0;i<points.size();i++) pw.println(((String)points.elementAt(i)).replace(' ',',')+(i+1<points.size()?",":""));
           pw.println("};");
           pw.println();
           pw.close();
       } catch (Exception e) { e.printStackTrace(); }
    }
    fd.dispose();
  }
}

