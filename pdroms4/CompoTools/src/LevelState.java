
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Arrays;


public class LevelState
{
    public static final int ROT_COUNT = 12;
    int[][] level;
    int[] tcount;
    int sx, sy;
    int tx, ty;
    int theta;

    LevelState()
    {
        level = new int[16][16];
        tcount = new int[3];
    }

    LevelState(LevelState state)
    {
        this.level = state.level;
        this.tcount = state.tcount;
        this.sx = state.sx;
        this.sy = state.sy;
        this.tx = state.tx;
        this.ty = state.ty;
    }

    public LevelState pushA(LevelPanel p)
    {
        theta = ROT_COUNT;
        update(p);
        LevelState s = new LevelState();
        s.rotateRight(level);
        s.update(p);
        return s;
    }

    public LevelState pushB(LevelPanel p)
    {
        theta = -ROT_COUNT;
        update(p);
        LevelState s = new LevelState();
        s.rotateLeft(level);
        s.update(p);
        return s;
    }

    private static int limit(int n)
    {
    	if (n<0)
    		return 15;
    	if (n>15)
    		return 0;
    	return n;
    }

    public LevelState scroll(int dx,int dy)
    {
        LevelState s = new LevelState();
        s.scroll(level, dx, dy);
        return s;
    }

    private void scroll(int[][] src,int dx,int dy)
    {
    	Arrays.fill(tcount, 0);
    	for (int i = 0; i < 16; ++i)
    	{
    		for (int j = 0; j < 16; ++j)
    		{
    			int t = src[limit(i-dy)][limit(j-dx)];
                level[i][j] = t;
                if (t==2)
                {
                    sx = j;
                    sy = i;
                }
                else if (t==3)
                {
                    tx = j;
                    ty = i;
                }
                else if (t>3 && t<7)
                {
                    tcount[t-4] += 1;
                }
    		}
    	}
    }

    public void rotateLeft(int[][] src)
    {
        Arrays.fill(tcount, 0);
        for (int i = 0; i < 16; ++i)
        {
            for (int j = 0; j < 16; ++j)
            {
                int t = src[15 - j][i];
                level[i][j] = t;
                if (t==2)
                {
                    sx = j;
                    sy = i;
                }
                else if (t==3)
                {
                    tx = j;
                    ty = i;
                }
                else if (t>3 && t<7)
                {
                    tcount[t-4] += 1;
                }
            }
        }
    }

    public void rotateRight(int[][] src)
    {
        Arrays.fill(tcount, 0);
        for (int i = 0; i < 16; ++i)
        {
            for (int j = 0; j < 16; ++j)
            {
                int t = src[j][15 - i];
                level[i][j] = t;
                if (t==2)
                {
                    sx = j;
                    sy = i;
                }
                else if (t==3)
                {
                    tx = j;
                    ty = i;
                }
                else if (t>3 && t<7)
                {
                    tcount[t-4] += 1;
                }
            }
        }
    }

    private void update(LevelPanel p)
    {
        do
        {
            if (p!=null)
                p.refresh(this);
            while (gravity() && tx>=0)
            {
                if (p!=null)
                    p.refresh(this);
            }
        } while (reduce(p) && tx>=0);
    }

    private boolean isMoveable(int t)
    {
        return t==2 || (t>3 && t<7) || t==10;
    }

    private boolean gravity()
    {
        boolean moved = false;
        for (int i=15;i>=0;--i)
        {
            for (int j=0;j<16;++j)
            {
                if (isMoveable(level[i][j]) && (i==15 || level[i+1][j]==0 || (level[i][j]==2 && level[i+1][j]==3)))
                {
                    if (i<15)
                    {
                        if (level[i+1][j]==3)
                            tx = ty = -1;
                        level[i+1][j] = level[i][j];
                    }
                    level[i][j] = 0;
                    moved = true;
                }
            }
        }
        return moved;
    }

    private boolean[][] removed = new boolean[16][16];
    int[] remX = new int[256];
    int[] remY = new int[256];
    int remCount;

    private void tagRemove(int y, int x)
    {
        remX[remCount]=x;
        remY[remCount++]=y;
        level[y][x]=0;
    }

    private boolean reduce(LevelPanel p)
    {
        for (int i = 0; i < 16; ++i)
            Arrays.fill(removed[i], false);

        sx = sy = -1;
        for (int i = 0; i < 16; ++i)
        {
            for (int j = 0; j < 16; ++j)
            {
                if (level[i][j]==2)
                {
                    sx = j;
                    sy = i;
                }
                else if (level[i][j]>3 && level[i][j]<7)
                {
                    if (removed[i][j])
                        continue;
                    int t = level[i][j];
                    int cnt = 0;
                    if (i>0 && (level[i-1][j]==t))
                        cnt+=1;
                    if (i<15 && level[i+1][j]==t)
                        cnt+=1;
                    if (j>0 && level[i][j-1]==t)
                        cnt+=1;
                    if (j<15 && level[i][j+1]==t)
                        cnt+=1;
                    if (cnt>1)
                        remove(i,j,t);
                }
            }
        }
        remCount = 0;
        for (int i = 0; i < 16; ++i)
        {
            for (int j = 0; j < 16; ++j)
            {
                if (removed[i][j])
                {
                    int t = level[i][j];
                    if (--tcount[t-4] == 0)
                    {
                        t+=3;
                        for (int k = 0; k < 16; ++k)
                            for (int l = 0; l < 16; ++l)
                                if (level[k][l]==t)
                                    tagRemove(k,l);
                    }
                    tagRemove(i,j);
                }
            }
        }
        return remCount>0;
    }

    private void remove(int i,int j,int t)
    {
        if (removed[i][j]==true || level[i][j]!=t)
            return;
        removed[i][j] = true;
        if (i>0)
            remove(i-1,j,t);
        if (i<15)
            remove(i+1,j,t);
        if (j>0)
            remove(i,j-1,t);
        if (j<15)
            remove(i,j+1,t);
    }

    @Override
    public boolean equals(Object o)
    {
        if (o==null || !(o instanceof LevelState))
            return false;
        LevelState s = (LevelState)o;
        if (s.sx != sx || s.sy!=sy)
            return false;
        for (int i = 0; i < 16; ++i)
        {
            for (int j = 0; j < 16; ++j)
            {
                if (s.level[i][j] != level[i][j])
                    return false;
            }
        }
        return true;
    }

    private static void addLine(ArrayList<Line2D.Double> lines, Point2D.Double p1, Point2D.Double p2)
    {
        for (Line2D.Double l : lines)
        {
            if (l.x1==p1.x && l.y1==p1.y)
            {
                if (l.x2==p2.x)
                {
                    l.setLine(l.getP2(), p2);
                    return;
                }
                else if (l.y2==p2.y)
                {
                    l.setLine(l.getP2(), p2);
                    return;
                }
            }
            else if (l.x1==p2.x && l.y1==p2.y)
            {
                if (l.x2==p1.x)
                {
                    l.setLine(l.getP2(), p1);
                    return;
                }
                else if (l.y2==p1.y)
                {
                    l.setLine(l.getP2(), p1);
                    return;
                }
            }
            if (l.x2==p1.x && l.y2==p1.y)
            {
                if (l.x1==p2.x)
                {
                    l.setLine(l.getP1(), p2);
                    return;
                }
                else if (l.y1==p2.y)
                {
                    l.setLine(l.getP1(), p2);
                    return;
                }
            }
            else if (l.x2==p2.x && l.y2==p2.y)
            {
                if (l.x1==p1.x)
                {
                    l.setLine(l.getP1(), p1);
                    return;
                }
                else if (l.y1==p1.y)
                {
                    l.setLine(l.getP1(), p1);
                    return;
                }
            }
        }
        lines.add(new Line2D.Double(p1, p2));
    }

    public ArrayList<Line2D.Double> findLines()
    {
        ArrayList<Line2D.Double> lines = new ArrayList<Line2D.Double>();
        Point2D.Double p1, p2;
        for (int i = 0; i < 16; ++i)
        {
            for (int j = 0; j < 16; ++j)
            {
                if (level[i][j]!=1)
                    continue;
                if (i==0 || level[i-1][j]!=1)
                {
                    p1 = new Point2D.Double(j,i);
                    p2 = new Point2D.Double(j+1,i);
                    addLine(lines, p1, p2);
                }
                if (i==15 || level[i+1][j]!=1)
                {
                    p1 = new Point2D.Double(j,i+1);
                    p2 = new Point2D.Double(j+1,i+1);
                    addLine(lines, p1, p2);
                }
                if (j==0 || level[i][j-1]!=1)
                {
                    p1 = new Point2D.Double(j,i);
                    p2 = new Point2D.Double(j,i+1);
                    addLine(lines, p1, p2);
                }
                if (j==15 || level[i][j+1]!=1)
                {
                    p1 = new Point2D.Double(j+1,i);
                    p2 = new Point2D.Double(j+1,i+1);
                    addLine(lines, p1, p2);
                }
            }
        }
        return lines;
    }
}
