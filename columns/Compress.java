import java.io.*;
import java.util.*;

public class Compress
{

  //********************
  // Huffman Compression
  //********************

  static int[] weight = new int[256];
  static int[][] codes = new int[256][]; // byte encoding
  
  // Classes for tree

  static class Node
  {
    int weight;
    Node(int w)
    {
      weight = w;
    }
  }

  static class Leaf extends Node
  {
    int value;
    Leaf(int w,int v)
    {
      super(w);
      value = v;
    }
  }

  static class Branch extends Node
  {
    Node left, right;
    Branch(int w,Node l,Node r)
    {
      super(w);
      left = l;
      right = r;
    }
  }

  // Count bytes occurences & return total size
  static int count(String fname) throws Exception
  {
    for (int i=0;i<256;i++)
        weight[i] = 0;
    FileInputStream fis = new FileInputStream(fname);
    int data;
    while ((data=fis.read())>=0)
        weight[data]+=1;
    fis.close();
    int total = 0;
    for (int i=0;i<256;i++)
        total+=weight[i];
    return total;
  }

  // Insert node by ascending weight
  static void insert(Vector v,Node n)
  {
    int i=0;
    for (;i<v.size();i++)
    {
        Node nn = (Node)v.elementAt(i);
        if (nn.weight>n.weight)
           break;
    }
    v.insertElementAt(n,i);
  }

  // build tree
  static Node build()
  {
    Vector v = new Vector();
    for (int i=0;i<256;i++)
        if (weight[i]>0)
           insert(v,new Leaf(weight[i],i));
    while (v.size()>1)
    {
        Node n1 = (Node)v.elementAt(0);
        v.removeElementAt(0);
        Node n2 = (Node)v.elementAt(0);
        v.removeElementAt(0);
        insert(v,new Branch(n1.weight+n2.weight,n1,n2));
    }
    return (Node)v.elementAt(0);
  }

  // Bit stream : write bit per bit
  static class BitOutputStream
  {
    OutputStream os;
    int b, mask;

    BitOutputStream(OutputStream os)
    {
      this.os = os;
      b = 0;
      mask = 0x80;
    }

    void writeBit(int bit) throws Exception
    {
      if (bit>0)
         b = b|mask;
      mask>>=1;
      if (mask==0)
      {
         mask=0x80;
         os.write(b);
         b = 0;
      }
    }

    void writeBits(int[] bits) throws Exception
    {
      for (int i=0;i<bits.length;i++)
          writeBit(bits[i]);
    }

    void writeByte(int data) throws Exception
    {
      int msk = 0x80;
      while (msk>0)
      {
          writeBit(data&msk);
          msk>>=1;
      }
    }

    void close() throws Exception
    {
      if (mask!=0x80)
      {
         while (mask>0) // fill byte with 1
         {
             b = b | mask;
             mask >>= 1;
         }
         os.write(b);
      }
      os.flush();
      os.close();
    }
  }

  // Save Huffman tree recursively
  static void saveTree(BitOutputStream bos,Node n,Vector l,Vector code) throws Exception
  {
    if (n instanceof Leaf)
    {
       bos.writeBit(0);
       l.addElement(n);
       int[] c = new int[code.size()];
       for (int i=0;i<c.length;i++)
           c[i] = ((Integer)code.elementAt(i)).intValue();
       codes[((Leaf)n).value] = c;
       return;
    }
    bos.writeBit(1);
    code.addElement(new Integer(0));
    saveTree(bos,((Branch)n).left,l,code);
    code.removeElementAt(code.size()-1);
    code.addElement(new Integer(1));
    saveTree(bos,((Branch)n).right,l,code);
    code.removeElementAt(code.size()-1);
  }

  // Save compressed file
  static void saveCompress(String in,String out,Node root,int size) throws Exception
  {
    BitOutputStream bos = new BitOutputStream(new FileOutputStream(out));
    // First 3 bytes : initial file size
    bos.writeByte((size>>16)&0xff);
    bos.writeByte((size>>8)&0xff);
    bos.writeByte(size&0xff);
    Vector l = new Vector();
    // Save tree
    saveTree(bos,root,l,new Vector());
    // Save terminal nodes
    while (l.size()>0)
    {
      bos.writeByte(((Leaf)l.elementAt(0)).value);
      l.removeElementAt(0);
    }
    FileInputStream fis = new FileInputStream(in);
    int data;
    // Encode and save data
    while ((data=fis.read())>=0)
        bos.writeBits(codes[data]);
    fis.close();
    bos.close();
  }

  // bit per bit reader
  static class BitInputStream
  {
    InputStream is;
    int b, mask;

    BitInputStream(InputStream is)
    {
      this.is = is;
      b = 0;
      mask = 0;
    }

    int readBit() throws Exception
    {
      if (mask==0)
      {
         if ((b = is.read())==-1)
            return b;
         mask = 0x80;
      }
      int ret = (b&mask)!=0 ? 1 : 0;
      mask>>=1;
      return ret;
    }

    int readByte() throws Exception
    {
      int ret = 0;
      for (int i=0;i<8;i++)
          ret = (ret<<1)|readBit();
      return ret;
    }

    void close() throws Exception
    {
      is.close();
    }
  }

  // Read Huffman header : size + tree
  static int readHeader(BitInputStream bis,Branch root,Vector l) throws Exception
  {
    int size = bis.readByte();
    size=(size<<8)+bis.readByte();
    size=(size<<8)+bis.readByte();
    bis.readBit(); // root
    readTree(bis,root,l);
    return size;
  }

  // Read tree
  static void readTree(BitInputStream bis,Branch b,Vector l) throws Exception
  {
    if (bis.readBit()==0)
       l.addElement(b.left = new Leaf(0,-1));
    else
       readTree(bis,(Branch)(b.left = new Branch(0,null,null)),l);
    if (bis.readBit()==0)
       l.addElement(b.right = new Leaf(0,-1));
    else
       readTree(bis,(Branch)(b.right = new Branch(0,null,null)),l);
  }

  // decode and save data
  static void saveDecompress(BitInputStream bis,String out,Branch root,int size) throws Exception
  {
    FileOutputStream fos = new FileOutputStream(out);
    for (int i=size;i>0;i--)
    {
      Node n = root;
      while (n instanceof Branch)
          n = bis.readBit() == 0 ? ((Branch)n).left : ((Branch)n).right;
      fos.write(((Leaf)n).value);
    }
    fos.close();
  }

  //*****************************
  // Lempel Ziv Welch Compression
  //*****************************

  static int DBITS = 9; // code length in bits
  static int DSIZE = 1<<DBITS;
  static int DMAX  = 65536; // max dictionary size

  // Hexa on 2 characters
  static String hex(int i)
  {
    String s = "0"+Integer.toHexString(i);
    return s.substring(s.length()-2);
  }

  // Write code (nb of bits + bits)
  static void outCode(BitOutputStream bos,int pos) throws Exception
  {
    int k = DSIZE>>1;
    while (k>0)
    {
        bos.writeBit(pos&k);
        k>>=1;
    }
  }

  static void initDic(Hashtable ht)
  {
    ht.clear();
    for (int i=0;i<256;i++)
        ht.put(hex(i),new Integer(i));
  }

  // Lempel Ziv compression
  static void lzCompress(String in,String out) throws Exception
  {
    // init dictionnary
    Hashtable ht = new Hashtable();
    initDic(ht);
    FileInputStream fis = new FileInputStream(in);
    BitOutputStream bos = new BitOutputStream(new FileOutputStream(out));
    int data=0,cpt=256,size=0;
    String p = "";
    // read data & encode
    while((data=fis.read())!=-1)
    {
        String c = hex(data);
        if (ht.get(p+c)!=null)
           p+=c;
        else
        {
           outCode(bos,((Integer)ht.get(p)).intValue());
           ht.put(p+c,new Integer(cpt++));
           size+=(p+c).length()/2;
           if (cpt==DSIZE-1||size>DMAX)
           {
              outCode(bos,data);
              outCode(bos,DSIZE-1);
              initDic(ht);
              p = "";
              cpt = 256;
              size = 0;
           }
           else
              p=c;
        }
    }
    outCode(bos,((Integer)ht.get(p)).intValue());
    bos.close();
    fis.close();
  }

  // write decoded data from hexa string
  static void outString(OutputStream os,String s) throws Exception
  {
    for (int i=0;i<s.length();i+=2)
        os.write(Integer.valueOf(s.substring(i,i+2),16).intValue());
  }

  // read code
  static int readCode(BitInputStream bis) throws Exception
  {
    int v = 0;
    for (int i=0;i<DBITS;i++)
    {
        int b = bis.readBit();
        if (b<0)
           return b;
        v=(v<<1)|b;
    }
    return v;
  }

  static void initDic(Vector v)
  {
    v.removeAllElements();
    for (int i=0;i<256;i++)
        v.addElement(hex(i));
  }

  // Lempel Ziv Welch decompression
  static void lzDecompress(String in,String out) throws Exception
  {
    // init dictionnary
    Vector v = new Vector();
    initDic(v);
    BitInputStream bis = new BitInputStream(new FileInputStream(in));
    FileOutputStream fos = new FileOutputStream(out);
    // read & decode data
    int cw = readCode(bis);
    fos.write(cw);
    int z;
    while ((z = readCode(bis))!=-1)
    {
        if (z==DSIZE-1)
        {
           initDic(v);
           cw = readCode(bis);
           fos.write(cw);
           continue;
        }
        int pw = cw;
        cw = z;
        if (cw<v.size())
        {
           String s = (String)v.elementAt(cw);
           outString(fos,s);
           String p = (String)v.elementAt(pw);
           String c = s.substring(0,2);
           v.addElement(p+c);
        }
        else
        {
           String p = (String)v.elementAt(pw);
           String c = p.substring(0,2);
           cw = v.size();
           outString(fos,p+c);
           v.addElement(p+c);
        }
    }
    fos.close();
    bis.close();
  }

  //*****
  // Main
  //*****

  public static void main(String args[]) throws Exception
  {
    if (args[0].equals("hx")) // huffman decompression
    {
       BitInputStream bis = new BitInputStream(new FileInputStream(args[1]));
       Branch root = new Branch(0,null,null);
       Vector l = new Vector();
       // read size & tree
       int size = readHeader(bis,root,l);
       // read terminal values
       while (l.size()>0)
       {
           Leaf n = (Leaf)l.elementAt(0);
           l.removeElementAt(0);
           n.value = bis.readByte();
       }
       // decode & save
       saveDecompress(bis,args[2],root,size);
       bis.close();
    }
    else if (args[0].equals("hc")) // huffman compression
    {
       int size = count(args[1]);
       Node root = build();
       saveCompress(args[1],args[2],root,size);
    }
    else if (args[0].equals("lx")) // Lempel Ziv Welch decompression
    {
       DBITS = Integer.valueOf(args[1]).intValue();
       DSIZE = 1<<DBITS;
       lzDecompress(args[2],args[3]);
    }
    else if (args[0].equals("lc")) // Lempel Ziv Welch compression
    {
       DBITS = Integer.valueOf(args[1]).intValue();
       DSIZE = 1<<DBITS;
       if (args.length>4)
          DMAX = Integer.valueOf(args[4]).intValue();
       lzCompress(args[2],args[3]);
    }
  }
}
