public class Int
{
  public int value = 0;

  public Int(int v)
  {
    value = v;
  }

  public boolean equals(Object o)
  {
    if ((o==null) || !(o instanceof Int))
       return false;
    return ((Int)o).value == value;
  }

  public String toString()
  {
    return String.valueOf(value);
  }
}
