package LevelEditor;

public class LevelEditorApp {
  boolean packFrame = false;

  //Construire l'application
  
  public LevelEditorApp() {
    FrameLevelEditor frame = new FrameLevelEditor();
    //Valide les frames ayant une taille pr�d�finies
    //Compacte les frames ayant d'utiles infos de taille pr�f�r�es - ex. depuis leur disposition
    if (packFrame)
      frame.pack();
    else
      frame.validate();
    frame.setVisible(true);
  }
//M�thode principale
  
  public static void main(String[] args) {
    new LevelEditorApp();
  }
}

 