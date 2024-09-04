package  LevelEditor;

import java.awt.*;
import java.awt.event.*;

public class StandardMenu extends MenuBar {

  Menu FileMenu = new Menu();
  Menu EditMenu = new Menu();
  Menu HelpMenu = new Menu();
  MenuItem menuItem1 = new MenuItem();
  MenuItem menuItem2 = new MenuItem();
  MenuItem menuItem3 = new MenuItem();
  MenuItem menuItem4 = new MenuItem();
  MenuItem menuItem5 = new MenuItem();
  MenuItem menuItem6 = new MenuItem();
  MenuItem menuItem7 = new MenuItem();
  MenuItem menuItem8 = new MenuItem();
  MenuItem menuItem9 = new MenuItem();
  MenuItem menuItem10 = new MenuItem();
  MenuItem menuItem11 = new MenuItem();
  MenuItem menuItem12 = new MenuItem();
  MenuItem menuItem13 = new MenuItem();
  MenuItem menuItem14 = new MenuItem();
  MenuItem menuItem15 = new MenuItem();
  MenuItem menuItem16 = new MenuItem();
  MenuItem menuItem17 = new MenuItem();
  MenuItem menuItem18 = new MenuItem();
  MenuItem menuItem19 = new MenuItem();

  public StandardMenu() {
    try {
      jbInit();
    }
    catch (Exception e) {
      e.printStackTrace();
    }
  }

  private void jbInit() throws Exception {
    FileMenu.setLabel("Fichier");
    EditMenu.setLabel("Edition");
    HelpMenu.setLabel("Aide");
    this.add(FileMenu);
    this.add(EditMenu);
    this.add(HelpMenu);
    menuItem1.setLabel("Nouveau");
    menuItem2.setLabel("Ouvrir");
    menuItem3.setLabel("Enregistrer");
    menuItem4.setLabel("Enregistrer sous");
    menuItem5.setLabel("Imprimer");
    menuItem6.setLabel("Configuration impression");
    menuItem7.setLabel("Quitter");
    menuItem8.setLabel("Défaire");
    menuItem9.setLabel("Refaire");
    menuItem10.setLabel("Couper");
    menuItem11.setLabel("Copier");
    menuItem12.setLabel("Coller");
    menuItem13.setLabel("Chercher");
    menuItem14.setLabel("Remplacer");
    menuItem15.setLabel("Aller");
    menuItem16.setLabel("Rubriques d'aide");
    menuItem17.setLabel("Rechercher dans l'aide");
    menuItem18.setLabel("Utilisation de l'aide");
    menuItem19.setLabel("A propos");

    FileMenu.add(menuItem1);
    FileMenu.add(menuItem2);
    FileMenu.add(menuItem3);
    FileMenu.add(menuItem4);
    FileMenu.addSeparator();
    FileMenu.add(menuItem5);
    FileMenu.add(menuItem6);
    FileMenu.addSeparator();
    FileMenu.add(menuItem7);
    EditMenu.add(menuItem8);
    EditMenu.add(menuItem9);
    EditMenu.addSeparator();
    EditMenu.add(menuItem10);
    EditMenu.add(menuItem11);
    EditMenu.add(menuItem12);
    EditMenu.addSeparator();
    EditMenu.add(menuItem13);
    EditMenu.add(menuItem14);
    EditMenu.add(menuItem15);
    HelpMenu.add(menuItem16);
    HelpMenu.add(menuItem17);
    HelpMenu.add(menuItem18);
    HelpMenu.add(menuItem19);
  }

}
 