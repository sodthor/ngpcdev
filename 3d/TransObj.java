import java.io.*;
import java.util.*;
import java.awt.*;

public class TransObj {

  public static class Point3D {
	float x,y,z;
	Point3D(float x,float y,float z) {
		this.x = x;
		this.y = y;
		this.z = z;
	}
	public void normalize() {
		float l = length();
		x /= l;
		y /= l;
		z /= l;
	}
	public float length() {
		return (float)Math.sqrt(x*x+y*y+z*z);
	}
  }

  public static Vector faces = new Vector();
  public static Vector vertices = new Vector();
  public static Vector normals = new Vector();
  public static Vector colors = new Vector();
  public static Vector colset = new Vector();
  public static String path;

  public static void main(String args[]) throws Exception {
    colset.addElement(Color.black);
    if (args[0].endsWith(".obj")) fromWaveFront(args[0]);
    if (args[0].endsWith(".rsd")) fromRSD(args[0]);
    float zoom = args.length>2 ? Float.valueOf(args[2]).floatValue():1f;
    save(args[1],zoom,args);
  }

  public static void fromWaveFront(String fname) throws Exception {
      colset.addElement(Color.red);
      colset.addElement(Color.green);
      colset.addElement(Color.blue);
      Reader r = new BufferedReader(new FileReader(fname));
      StreamTokenizer st = new StreamTokenizer(r);
      st.eolIsSignificant(true);
      st.commentChar('#');
      Integer curColor = new Integer(0);
scan:
      while (true) {
          switch (st.nextToken()) {
            default:
              break scan;
            case StreamTokenizer.TT_EOL:
              break;
            case StreamTokenizer.TT_WORD:
              if ("v".equals(st.sval)) {
                 double x = 0, y = 0, z = 0;
                 if (st.nextToken() == StreamTokenizer.TT_NUMBER) {
                    x = st.nval;
                    if (st.nextToken() == StreamTokenizer.TT_NUMBER) {
                       y = st.nval;
                       if (st.nextToken() == StreamTokenizer.TT_NUMBER)
                          z = st.nval;
                    }
                 }
                 vertices.add(new TransObj.Point3D((float)x,(float)y,(float)z));
                 while (st.ttype != StreamTokenizer.TT_EOL && st.ttype != StreamTokenizer.TT_EOF)
                   st.nextToken();
              } else if ("f".equals(st.sval) || "fo".equals(st.sval) || "l".equals(st.sval)) {
                 Vector face = new Vector();
                 while (true) {
                     if (st.nextToken() == StreamTokenizer.TT_NUMBER) {
                        face.insertElementAt(new Integer(((int) st.nval)-1),0);
                     } else if (st.ttype == '/') st.nextToken();
                     else break;
                 }
                 face.addElement(curColor);
                 faces.addElement(face);
                 if (st.ttype != StreamTokenizer.TT_EOL)
                    break scan;
                 } else if ("g".equals(st.sval)) {
                     while (st.nextToken() != StreamTokenizer.TT_EOL && st.ttype != StreamTokenizer.TT_EOF && st.ttype != StreamTokenizer.TT_NUMBER) ;
                     if (st.ttype == StreamTokenizer.TT_NUMBER) {
                        curColor=new Integer((int)st.nval);
                     }
                 } else {
                    while (st.nextToken() != StreamTokenizer.TT_EOL && st.ttype != StreamTokenizer.TT_EOF);
                 }
          }
      }
      r.close();
      if (st.ttype != StreamTokenizer.TT_EOF) throw new IOException(st.toString());
  }

  public static void fromRSD(String fileName) {
    fromRSD(new File(fileName));
  }

  public static void fromRSD(File file) {
    try {
       String s = file.getAbsolutePath();
       setWorkDir(s.substring(0,s.lastIndexOf(File.separator)+1));
       fromRSD(new FileInputStream(file));
    } catch(Exception e) { e.printStackTrace(); }
  }

  public static void setWorkDir(String path) {
    TransObj.path = path;
  }

  public static void fromRSD(InputStream is) throws Exception {
    LineNumberReader lnr = new LineNumberReader(new InputStreamReader(is));
    String l,ply=null,mat = null;
    while ((l=lnr.readLine())!=null) {
        l = l.trim();
        if (l.startsWith("PLY=")) {
           ply = l.substring(4);
        } else if (l.startsWith("MAT=")) {
           mat = l.substring(4);
        } else if (l.startsWith("GRP=")) {
        } else if (l.startsWith("NTEX=")) {
           int nbt = Integer.valueOf(l.substring(5)).intValue();
        } else if (l.startsWith("TEX[")) {
           String tim = l.substring(l.indexOf("=")+1);
        }
    }
    is.close();
    loadMat(new FileInputStream(new File(path+mat)));
    loadPly(new FileInputStream(new File(path+ply)));
  }

  public static String readString(InputStream is) throws Exception {
    StringBuffer sb = new StringBuffer();
    int c;
    while ((c=is.read())!=-1) sb.append((char)c);
    return sb.toString();
  }

  public static void loadMat(InputStream is) throws IOException {
    LineNumberReader lnr = new LineNumberReader(new InputStreamReader(is));
    String line;
    boolean first=true;
    int nmat = 0, nm = 0;
    while ((line = lnr.readLine())!=null) {
        if (line.startsWith("#")||line.startsWith("@")) continue;
        StringTokenizer st = new StringTokenizer(line);
        if (first) {
           nmat = Integer.valueOf(st.nextElement().toString()).intValue();
           first = false;
           continue;
        }
        if (nm < nmat) {
           String num = st.nextElement().toString();
           int flag = Integer.valueOf(st.nextElement().toString()).intValue();
           char shading = st.nextElement().toString().charAt(0);
           char type = st.nextElement().toString().charAt(0);
           if (type=='C') {
              int r = Integer.valueOf(st.nextElement().toString()).intValue();
              int g = Integer.valueOf(st.nextElement().toString()).intValue();
              int b = Integer.valueOf(st.nextElement().toString()).intValue();
              Color c = new Color(r,g,b);
              if (!colset.contains(c))
                 colset.addElement(c);
              colors.addElement(new Integer(colset.indexOf(c)));
           } else {
              Vector v = new Vector();
              v.addElement(st.nextElement().toString()); // num image
              while (st.hasMoreElements())
                  v.addElement(Integer.valueOf(st.nextElement().toString())); // u,v
              colors.addElement(new Integer(0));
           }
           nm++;
        }
    }
  }

  public static void loadPly(InputStream is) throws IOException {
    LineNumberReader lnr = new LineNumberReader(new InputStreamReader(is));
    int numCol = 1,nv = 0,np = 0, nn = 0,nvert = 0,npoly = 0,nnorm = 0;
    boolean first=true;
    String line;
    while ((line = lnr.readLine())!=null) {
        if (line.startsWith("#")||line.startsWith("@")) continue;
        StringTokenizer st = new StringTokenizer(line);
        if (first) {
           nvert = Integer.valueOf(st.nextElement().toString()).intValue();
           nnorm = Integer.valueOf(st.nextElement().toString()).intValue();
           npoly = Integer.valueOf(st.nextElement().toString()).intValue();
           first = false;
        } else if (nv < nvert) {
           double x = Double.valueOf(st.nextElement().toString()).doubleValue();
           double y = Double.valueOf(st.nextElement().toString()).doubleValue();
           double z = Double.valueOf(st.nextElement().toString()).doubleValue();
           vertices.add(new Point3D((float)x,(float)y,(float)z));
           nv++;
        } else if (nn < nnorm) {
           nn++;
        } else if (np < npoly) {
           int flag = Integer.valueOf(st.nextElement().toString()).intValue();
           int p1 = Integer.valueOf(st.nextElement().toString()).intValue();
           int p2 = Integer.valueOf(st.nextElement().toString()).intValue();
           int p3 = Integer.valueOf(st.nextElement().toString()).intValue();
           int p4 = Integer.valueOf(st.nextElement().toString()).intValue();
           Vector face = new Vector();
           face.add(new Integer(p1));
           face.add(new Integer(p2));
           face.add(new Integer(p3));
           faces.add(face);
           np++;
       }
    }
    is.close();
  }

  public static void save(String out,float zoom,String[] args) throws Exception {
      //**************
      //* Write to .c
      //**************

      String name = out;
      String uname = name.toUpperCase();
      FileWriter fw = new FileWriter(name+".inc");
      PrintWriter pw = new PrintWriter(fw);

      // Vertices
      pw.println(uname+"_NBV\tEQU\t"+vertices.size());
      pw.println();
      pw.println(name+"_vertices");
      int l = vertices.size();
      for (int i=0;i<l;i++) {
          TransObj.Point3D p = (TransObj.Point3D)vertices.elementAt(i);
          pw.println("\tdb\t"+((int)(p.x*zoom))+","+((int)(p.y*zoom))+","+((int)(p.z*zoom)));
      }
      pw.println();

      // Polygones
      pw.println(uname+"_NBT\tEQU\t"+faces.size());
      pw.println();
      l = faces.size();
      pw.println(name+"_triangles");
      for (int i=0;i<l;i++) {
          Vector f = (Vector)faces.elementAt(i);
          int ll = f.size();
          pw.print("\tdb\t");
          for (int j=0;j<ll;j++) pw.print(((Integer)f.elementAt(j)).intValue()*6+",");
          int cc = ((Integer)colors.elementAt(i)).intValue();
          pw.println(args.length>3?args[cc+2]:String.valueOf(cc));
      }
      pw.println();
      pw.println(name+"_normals");
      Point3D n = new Point3D(0,0,0);
      for (int i=0;i<l;i++) {
          Vector f = (Vector)faces.elementAt(i);
          Point3D a = (Point3D)vertices.elementAt(((Integer)f.elementAt(0)).intValue());
          Point3D b = (Point3D)vertices.elementAt(((Integer)f.elementAt(1)).intValue());
          Point3D c = (Point3D)vertices.elementAt(((Integer)f.elementAt(2)).intValue());
          getNormal(a,b,c,n);
          n.normalize();
          pw.println("\tdb\t"+((int)(n.x*31))+","+((int)(n.y*31))+","+((int)(n.z*31)));
      }
      pw.println();
      pw.println(name);
      pw.println("\tdw\t"+name+"_nbv,"+name+"_nbt");
      pw.println("\tdd\t"+name+"_vertices,"+name+"_triangles,"+name+"_normals");
      pw.println();

      pw.flush();
      fw.close();
  }

  public static void getNormal(Point3D a,Point3D b,Point3D c,Point3D vn) {
    vn.x = ((c.y-b.y)*(b.z-a.z))-((c.z-b.z)*(b.y-a.y));
    vn.y = ((c.z-b.z)*(b.x-a.x))-((c.x-b.x)*(b.z-a.z));
    vn.z = ((c.x-b.x)*(b.y-a.y))-((c.y-b.y)*(b.x-a.x));
  }
}

