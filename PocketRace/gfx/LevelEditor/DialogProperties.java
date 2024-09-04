
//Titre :LevelEditor
//Version :
//Copyright :Copyright (c) 1998
//Auteur :BOIXEL
//Société :Ma société
//Description :Votre description

package LevelEditor;

import java.awt.*;
import java.awt.event.*;
import java.util.*;

public class DialogProperties extends Dialog implements ActionListener{
  Panel panelMain = new Panel();
  BorderLayout borderLayoutMain = new BorderLayout();
  Panel panelValues = new Panel();
  Panel panelCommand = new Panel();
  FlowLayout flowLayoutCommand = new FlowLayout();
  Button buttonOk = new Button();
  Button buttonCancel = new Button();
  GridLayout gridLayoutValues = new GridLayout();
  Panel panelWidth = new Panel();
  Panel panelHeight = new Panel();
  Panel panelZoom = new Panel();
  Label labelWidth = new Label();
  Label labelHeight = new Label();
  Label labelZoom = new Label();
  BorderLayout borderLayoutWidth = new BorderLayout();
  BorderLayout borderLayoutHeight = new BorderLayout();
  BorderLayout borderLayoutZoom = new BorderLayout();
  TextField textWidth = new TextField();
  TextField textHeight = new TextField();
  TextField textZoom = new TextField();
  Panel panelWalls = new Panel();
  Panel panelGrounds = new Panel();
  Panel panelImgs = new Panel();
  BorderLayout borderLayoutImgs = new BorderLayout();
  Panel panelAdd = new Panel();
  Label labelImgs = new Label();
  TextField textAdd = new TextField();
  Button buttonAdd = new Button();
  BorderLayout borderLayoutAdd = new BorderLayout();
  java.awt.List listImgs = new java.awt.List();
  BorderLayout borderLayoutWalls = new BorderLayout();
  Label labelWalls = new Label();
  TextField textWalls = new TextField();
  BorderLayout borderLayoutGrounds = new BorderLayout();
  Label labelGrounds = new Label();
  TextField textGrounds = new TextField();
  Panel panelName = new Panel();
  BorderLayout borderLayoutName = new BorderLayout();
  Label labelName = new Label();
  TextField textName = new TextField();

  int width, height, zoom;
  String walls, grounds, name;

  public DialogProperties(Frame frame, String title, boolean modal) {
    super(frame, title, modal);
    enableEvents(AWTEvent.WINDOW_EVENT_MASK);
    try  {
      jbInit();
      add(panelMain);
      buttonOk.addActionListener(this);
      buttonCancel.addActionListener(this);
      buttonAdd.addActionListener(this);
      pack();
    } catch (Exception ex) { ex.printStackTrace(); }
  }

  public DialogProperties(Frame frame, int width, int height,int zoom,String walls,String grounds,Vector imgs,String name) {
    this(frame, "Properties", true);
    this.width = width;
    this.height = height;
    this.zoom = zoom;
    this.walls = walls;
    this.grounds = grounds;
    this.name = name;
    textWidth.setText(String.valueOf(width));
    textHeight.setText(String.valueOf(height));
    textZoom.setText(String.valueOf(zoom));
    textWalls.setText(walls);
    textGrounds.setText(grounds);
    textName.setText(name);
    for (int i=0;i<imgs.size();i++) listImgs.add((String)imgs.elementAt(i));
  }

  public DialogProperties(Frame frame) {
    this(frame, "", false);
  }

  public DialogProperties(Frame frame, boolean modal) {
    this(frame, "", modal);
  }

  public DialogProperties(Frame frame, String title) {
    this(frame, title, false);
  }

  void jbInit() throws Exception {
    panelMain.setSize(new Dimension(237, 338));
    borderLayoutMain.setVgap(4);
    panelValues.setLayout(gridLayoutValues);
    buttonOk.setActionCommand("Ok");
    buttonOk.setLabel("Ok");
    buttonCancel.setActionCommand("Cancel");
    buttonCancel.setLabel("Cancel");
    gridLayoutValues.setColumns(2);
    gridLayoutValues.setVgap(4);
    panelHeight.setLayout(borderLayoutHeight);
    panelWidth.setLayout(borderLayoutWidth);
    gridLayoutValues.setRows(6);
    gridLayoutValues.setHgap(4);
    labelWidth.setText("Width");
    labelHeight.setText("Height");
    textWidth.setText("25");
    textHeight.setText("15");
    labelZoom.setText("Zoom");
    textZoom.setText("48");
    borderLayoutImgs.setVgap(4);
    panelGrounds.setLayout(borderLayoutGrounds);
    panelAdd.setLayout(borderLayoutAdd);
    labelImgs.setText("Add image");
    buttonAdd.setLabel("Add");
    labelWalls.setText("Walls img name");
    textWalls.setText("walls");
    labelGrounds.setText("Grounds img name");
    textGrounds.setText("grounds");
    labelName.setText("Level name");
    textName.setText("level");
    panelName.setLayout(borderLayoutName);
    panelWalls.setLayout(borderLayoutWalls);
    panelImgs.setLayout(borderLayoutImgs);
    panelZoom.setLayout(borderLayoutZoom);
    panelCommand.setLayout(flowLayoutCommand);
    panelMain.setLayout(borderLayoutMain);
    panelMain.add(panelValues, BorderLayout.NORTH);
    panelValues.add(panelWidth, null);
    panelWidth.add(labelWidth, BorderLayout.CENTER);
    panelWidth.add(textWidth, BorderLayout.EAST);
    panelValues.add(panelHeight, null);
    panelHeight.add(labelHeight, BorderLayout.WEST);
    panelHeight.add(textHeight, BorderLayout.EAST);
    panelValues.add(panelZoom, null);
    panelZoom.add(labelZoom, BorderLayout.WEST);
    panelZoom.add(textZoom, BorderLayout.EAST);
    panelValues.add(panelWalls, null);
    panelWalls.add(labelWalls, BorderLayout.WEST);
    panelWalls.add(textWalls, BorderLayout.EAST);
    panelValues.add(panelGrounds, null);
    panelGrounds.add(labelGrounds, BorderLayout.WEST);
    panelGrounds.add(textGrounds, BorderLayout.EAST);
    panelValues.add(panelName, null);
    panelName.add(labelName, BorderLayout.WEST);
    panelName.add(textName, BorderLayout.EAST);
    panelMain.add(panelCommand, BorderLayout.SOUTH);
    panelCommand.add(buttonOk, null);
    panelCommand.add(buttonCancel, null);
    panelMain.add(panelImgs, BorderLayout.CENTER);
    panelImgs.add(panelAdd, BorderLayout.NORTH);
    panelAdd.add(labelImgs, BorderLayout.WEST);
    panelAdd.add(textAdd, BorderLayout.CENTER);
    panelAdd.add(buttonAdd, BorderLayout.EAST);
    panelImgs.add(listImgs, BorderLayout.CENTER);
  }

  protected void processWindowEvent(WindowEvent e) {
    if (e.getID() == WindowEvent.WINDOW_CLOSING) {
      cancel();
    }
    super.processWindowEvent(e);
  }

  public void actionPerformed(ActionEvent e) {
    if (e.getActionCommand().equals("Cancel")) {
       width = -1;
       height = -1;
       zoom = -1;
       cancel();
    } else if (e.getActionCommand().equals("Ok")) {
       try {
           width = (new Integer(textWidth.getText())).intValue();
           height = (new Integer(textHeight.getText())).intValue();
           zoom = (new Integer(textZoom.getText())).intValue();
           walls = textWalls.getText();
           grounds = textGrounds.getText();
           name = textName.getText();
       } catch(Exception ex) {
           width = -1;
           height = -1;
           zoom = -1;
       }
       cancel();
    } else if (e.getActionCommand().equals("Add")) {
       String val = textAdd.getText().trim();
       if (!val.equals("")) {
          int i;
          for (i=0;i<listImgs.getItemCount();i++) if (listImgs.getItem(i).equals(val)) break;
          if (i==listImgs.getItemCount()) listImgs.add(val);
       }
    }
  }

  void cancel() {
    setVisible(false);
  }
}


