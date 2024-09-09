

public class RGB
{
  public int red,green,blue,ngp,value;

  public RGB(int r,int g,int b)
  {
    setRGB(r,g,b);
  }

  public void setRGB(int r,int g,int b)
  {
    red = r;
    green = g;
    blue = b;
    updateNGP();
  }

  public RGB(int i)
  {
    setRGB(i);
  }

  
  public void setRGB(int i)
  {
    setRGB(((i>>16)&0xff),((i>>8)&0xff),(i&0xff));
  }

  public void updateNGP()
  {
    int r = (red&0xf0)+((red&0x0f)>7 && red<0xf0 ?0x10:0x00);
    int g = (green&0xf0)+((green&0x0f)>7 && green<0xf0 ?0x10:0x00);
    int b = (blue&0xf0)+((blue&0x0f)>7 && blue<0xf0 ?0x10:0x00);
    ngp   = (b<<4)+g+(r>>4);
    value = (255<<24)+(red<<16)+(green<<8)+blue;
  }

  public int hashcode() {
	  return value;
  }

  public boolean equals(Object o)
  {
    if (o==null)
       return false;
    if (o instanceof RGB)
       return ((RGB)o).value == value;
    return false;
  }

  public int toNGP()
  {
    return ngp;
  }

  public long dist(RGB rgb)
  {
	/**
    int r0 = (red&0xf0)+((red&0x0f)>7 && red<0xf0 ?0x10:0x00);
    int g0 = (green&0xf0)+((green&0x0f)>7 && green<0xf0 ?0x10:0x00);
    int b0 = (blue&0xf0)+((blue&0x0f)>7 && blue<0xf0 ?0x10:0x00);
    int r1 = (rgb.red&0xf0)+((rgb.red&0x0f)>7 && rgb.red<0xf0 ?0x10:0x00);
    int g1 = (rgb.green&0xf0)+((rgb.green&0x0f)>7 && rgb.green<0xf0 ?0x10:0x00);
    int b1 = (rgb.blue&0xf0)+((rgb.blue&0x0f)>7 && rgb.blue<0xf0 ?0x10:0x00);
    long r = r1-r0; 
    long g = g1-g0; 
    long b = b1-b0;
    /**/
	/**/
    long r = rgb.red-red; 
    long g = rgb.green-green; 
    long b = rgb.blue-blue;
    /**/
    return r*r+g*g+b*b;
  }

  private static int merge(int a,int b,int wa,int wb,int u, int v)
  {
    int n = a == b ? a : ((a*wa+b*wb)*u)/((wa+wb)*v);
    return n<256?n:255;//n<16?n:15;
  }

  public void merge(RGB rgb,int wa,int wb,int u, int v)
  {
    red = merge(red,rgb.red,wa,wb,u,v);
    green = merge(green,rgb.green,wa,wb,u,v);
    blue = merge(blue,rgb.blue,wa,wb,u,v);
    updateNGP();
  }
  
  public int rgb()
  {
    int r = (red&0xf0)+((red&0x0f)>7 && red<0xf0 ?0x10:0x00);
    int g = (green&0xf0)+((green&0x0f)>7 && green<0xf0 ?0x10:0x00);
    int b = (blue&0xf0)+((blue&0x0f)>7 && blue<0xf0 ?0x10:0x00);
    return (255<<24)+(r<<16)+(g<<8)+b;
  }

  public String toString()
  {
    return "("+red+","+green+","+blue+","+CodeImage.int2Hex(ngp,3)+")";
  }
}
