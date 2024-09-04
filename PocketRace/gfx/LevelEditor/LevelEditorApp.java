package LevelEditor;

public class LevelEditorApp {
  boolean packFrame = false;

  //Construire l'application
  
  public LevelEditorApp() {
    FrameLevelEditor frame = new FrameLevelEditor();
    //Valide les frames ayant une taille prédéfinies
    //Compacte les frames ayant d'utiles infos de taille préférées - ex. depuis leur disposition
    if (packFrame)
      frame.pack();
    else
      frame.validate();
    frame.setVisible(true);
  }
//Méthode principale
  
  public static void main(String[] args) {
    new LevelEditorApp();
  }
}

 