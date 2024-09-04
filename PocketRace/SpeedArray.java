import java.io.*;

public class SpeedArray
{
  private static final int MAX_DIR = 64;
  private static final int MAX_SPEED = 32;
  private static final int NORM_SPEED = 24;
  private static final int SHIFT_SPEED = 3;

  private static String s3(int i)
  {
    String s = String.valueOf(i);
    return ("   "+s).substring(s.length());
  }

  public static void main(String[] args) throws Exception
  {
    PrintWriter pw = new PrintWriter(new FileOutputStream("speed.h"));
    pw.println("#define MAX_SPEED "+MAX_SPEED);
    pw.println("#define NORM_SPEED "+NORM_SPEED);
    pw.println("#define SHIFT_SPEED "+SHIFT_SPEED);
    pw.println();
    double PI_DIR = (2*Math.PI)/MAX_DIR;

    pw.println("const s8 speed_x[MAX_SPEED]["+MAX_DIR+"] = {");
    for (int i=0;i<MAX_SPEED;i++)
    {
        pw.print("{");
        for (int j=0;j<MAX_DIR;j++)
            pw.print(s3((int)(Math.cos(PI_DIR*j)*i))+(j==MAX_DIR-1?"":","));
        pw.println("}"+(i==MAX_SPEED-1?"":","));
    }
    pw.println("};");

    pw.println("const s8 speed_y[MAX_SPEED]["+MAX_DIR+"] = {");
    for (int i=0;i<MAX_SPEED;i++)
    {
        pw.print("{");
        for (int j=0;j<MAX_DIR;j++)
            pw.print(s3((int)(-Math.sin(PI_DIR*j)*i))+(j==MAX_DIR-1?"":","));
        pw.println("}"+(i==MAX_SPEED-1?"":","));
    }
    pw.println("};");
    pw.close();
  }
}

