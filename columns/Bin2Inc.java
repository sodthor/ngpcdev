import java.io.*;

public class Bin2Inc
{
  public static void main(String args[]) throws Exception
  {
    FileInputStream fis = new FileInputStream(args[0]);
    PrintWriter pw = new PrintWriter(new FileOutputStream(args[1]));
    int data,i=0;
    boolean head = true;
    while((data=fis.read())!=-1)
    {
        if (head)
           pw.print("\tdb\t");
        head = false;
        pw.print("0"+Integer.toHexString(data)+"h");
        if (i%16==15)
        {
           pw.println();
           head = true;
        }
        else
           pw.print(",");
        i+=1;
    }
    pw.println();
    pw.close();
    fis.close();
  }
}
