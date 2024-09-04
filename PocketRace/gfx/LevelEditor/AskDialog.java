
//Titre :LevelEditor
//Version :
//Copyright :Copyright (c) 1998
//Auteur :BOIXEL
//Société :Ma société
//Description :Votre description

package LevelEditor;

import java.awt.*;
import java.awt.event.*;

public class AskDialog extends Dialog implements ActionListener {
  Panel panelMain = new Panel();
  BorderLayout borderLayoutMain = new BorderLayout();
  Label labelQuestion = new Label();
  Panel panelButtons = new Panel();
  Button buttonYes = new Button();
  Button buttonNo = new Button();
  int result;
  static final int NO = 0;
  static final int YES = 1;

  public AskDialog(Frame frame, String title, String question, boolean modal) {
    super(frame, title, modal);
    result = NO;
    try  {
      jbInit();
      labelQuestion.setText(question);
      add(panelMain);
      buttonYes.addActionListener(this);
      buttonNo.addActionListener(this);
      pack();
    }
    catch (Exception ex) {
      ex.printStackTrace();
    }
  }

  void jbInit() throws Exception {
    panelMain.setSize(new Dimension(270, 63));
    labelQuestion.setAlignment(1);
    labelQuestion.setText("Yes or No ?");
    buttonYes.setLabel("Yes");
    buttonNo.setLabel("No");
    panelMain.setLayout(borderLayoutMain);
    panelMain.add(labelQuestion, BorderLayout.NORTH);
    panelMain.add(panelButtons, BorderLayout.SOUTH);
    panelButtons.add(buttonYes, null);
    panelButtons.add(buttonNo, null);
  }

  public void actionPerformed(ActionEvent e) {
    if (e.getActionCommand().equals("Yes")) result = YES;
    setVisible(false);
  }
}

                    
