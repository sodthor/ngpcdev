public class Int
{
  public int value = 0;

  public Int(int v)
  {
    value = v;
  }

  @Override
  public boolean equals(Object o)
  {
    if ((o==null) || !(o instanceof Int))
       return false;
    return ((Int)o).value == value;
  }

  @Override
  public int hashCode()
  {
    return value;
  }

  @Override
  public String toString()
  {
    return String.valueOf(value);
  }
}
