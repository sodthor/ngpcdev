import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.geom.Line2D;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import javax.swing.JPanel;

public class LevelPanel extends JPanel implements MouseListener
{
    public static final int ZOOM = 12;
    public static final int ZOOM2 = ZOOM>>1;
    public static final int DX = (16*LevelPanel.ZOOM)>>3;
    public static final int DY = (12*LevelPanel.ZOOM)>>3;

    private LevelState state;
    private int pen;
    
    public LevelPanel()
    {
        state = new LevelState();
        clearLevel();
        pen = 1;
        addMouseListener(this);
    }

    @Override
    public void paintComponent(Graphics g)
    {
        paintState(state, (Graphics2D) g, 0);
    }

    public void paintState(LevelState s, Graphics2D g, int f)
    {
        Color[] pal = new Color[] {Color.ORANGE, Color.MAGENTA, Color.BLUE, Color.ORANGE.darker(), Color.MAGENTA.darker(), Color.BLUE.darker(), Color.LIGHT_GRAY};
        int w = getWidth();
        int h = getHeight();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, w, h);
        for (int i=0;i<16;++i)
        {
            for (int j=0;j<16;++j)
            {
                switch(s.level[j][i])
                {
                    case 0:
                        g.setColor(Color.gray);
                        g.drawRect(DX+i*ZOOM, DY+j*ZOOM, ZOOM, ZOOM);
                        break;
                    case 1:
                        g.setColor(Color.darkGray);
                        g.fillRect(DX+i*ZOOM, DY+j*ZOOM, ZOOM, ZOOM);
                        break;
                    case 2:
                        g.setColor(Color.red);
                        g.fillOval(DX+1+i*ZOOM, DY+1+j*ZOOM, ZOOM-2, ZOOM-2);
                        break;
                    case 3:
                        g.setColor(Color.green);
                        g.fillOval(DX+1+i*ZOOM, DY+1+j*ZOOM, ZOOM-2, ZOOM-2);
                        break;
                    default:
                        g.setColor(pal[s.level[j][i]-4]);
                        g.fillRect(DX+i*ZOOM, DY+j*ZOOM, ZOOM, ZOOM);
                }
            }
        }
        g.setColor(Color.black);
        for (int i=0;i<s.remCount;++i)
        {
            g.fillOval(DX+1+s.remX[i]*ZOOM+ZOOM2-f, DY+1+s.remY[i]*ZOOM+ZOOM2-f, f*2, f*2);
        }

        ArrayList<Line2D.Double> lines = s.findLines();
        double theta = (Math.PI / (2 * LevelState.ROT_COUNT));
        if (s.theta<0)
            theta *= (LevelState.ROT_COUNT+s.theta);
        else if (s.theta>0)
            theta *= (s.theta-LevelState.ROT_COUNT);
        else
            theta = 0;
        Stroke svst = g.getStroke();
        if (theta != 0)
        {
            g.setStroke(new BasicStroke(3));
            g.setColor(Color.darkGray);
        }
        else
        {
            g.setStroke(new BasicStroke(1));
            g.setColor(Color.yellow);
        }

        for (Line2D.Double l : lines)
        {
            double ax = (l.x1 * ZOOM - 8*ZOOM);
            double ay = (l.y1 * ZOOM - 8*ZOOM);
            int tx1 = 8*ZOOM + (int) (ax * Math.cos(theta) - ay * Math.sin(theta));
            int ty1 = 8*ZOOM + (int) (ay * Math.cos(theta) + ax * Math.sin(theta));
            ax = (l.x2 * ZOOM - 8*ZOOM);
            ay = (l.y2 * ZOOM - 8*ZOOM);
            int tx2 = 8*ZOOM + (int) (ax * Math.cos(theta) - ay * Math.sin(theta));
            int ty2 = 8*ZOOM + (int) (ay * Math.cos(theta) + ax * Math.sin(theta));
            g.drawLine(DX+tx1, DY+ty1, DX+tx2, DY+ty2);
            if (theta == 0)
            {
                g.fillOval(DX+tx1-2, DY+ty1-2, 4, 4);
                g.fillOval(DX+tx2-2, DY+ty2-2, 4, 4);
            }
        }
        g.setStroke(svst);
        g.setColor(Color.black);
        g.drawString("l="+lines.size(), 12, 12);
    }

    public void clearLevel()
    {
        for (int i=0;i<16;++i)
            Arrays.fill(state.level[i], 0);
        state.sx=state.sy=1;
        state.level[state.sy][state.sx]=2;
        state.tx=state.ty=14;
        state.level[state.ty][state.tx]=3;
        repaint(0);
    }

    public void loadLevel(InputStream is) throws IOException
    {
        LineNumberReader lnr = new LineNumberReader(new InputStreamReader(is));
        lnr.readLine();
        for (int i=0;i<16;++i)
        {
            String line = lnr.readLine();
            String[] values = line.substring(line.indexOf("{")+1, line.indexOf("}")).split(",");
            for (int j=0;j<16;++j)
            {
                state.level[i][j] = Integer.valueOf(values[j].trim(), 10);
                if (state.level[i][j] == 2)
                {
                    state.sx = j;
                    state.sy = i;
                }
                else if (state.level[i][j] == 3)
                {
                    state.tx = j;
                    state.ty = i;
                }
            }
        }
        repaint(0);
    }

    public void saveLevel(OutputStream os) throws IOException
    {
        PrintWriter pw = new PrintWriter(os);
        pw.println("{");
        for (int i=0;i<16;++i)
        {
            pw.print("    {");
            for (int j=0;j<16;++j)
                pw.print(String.valueOf(state.level[i][j])+(j<15?", ":""));
            pw.println("}"+(i<15?",":""));
        }
        pw.println("}");
        pw.close();
    }

    public void saveLines(OutputStream os) throws IOException
    {
        ArrayList<Line2D.Double> lines = state.findLines();
        int k = 0;
        StringBuilder sbx1 = new StringBuilder();
        StringBuilder sbx2 = new StringBuilder();
        StringBuilder sby1 = new StringBuilder();
        StringBuilder sby2 = new StringBuilder();
        for (Line2D.Double l : lines)
        {
            int x1 = (int) (l.x1 * 8 - 8*8);
            int y1 = (int) (l.y1 * 8 - 8*8);
            int x2 = (int) (l.x2 * 8 - 8*8);
            int y2 = (int) (l.y2 * 8 - 8*8);
            if (sbx1.length()>0)
            {
                sbx1.append(',');
                sbx2.append(',');
                sby1.append(',');
                sby2.append(',');
            }
            sbx1.append(String.valueOf(x1));
            sbx2.append(String.valueOf(x2));
            sby1.append(String.valueOf(y1));
            sby2.append(String.valueOf(y2));
        }
        for (int i=lines.size();i<64;++i)
        {
            sbx1.append(",0xf000");
            sbx2.append(",0xf000");
            sby1.append(",0xf000");
            sby2.append(",0xf000");
        }
        PrintWriter pw = new PrintWriter(os);
        pw.println("{");
        pw.println("{" + sbx1.toString() +"},");
        pw.println("{" + sbx2.toString() +"},");
        pw.println("{" + sby1.toString() +"},");
        pw.println("{" + sby2.toString() +"}");
        pw.println("}");
        pw.close();
    }

    public void setPen(int p)
    {
        pen = p;
    }

    public void mouseClicked(MouseEvent e)
    {
    }

    public void mousePressed(MouseEvent e)
    {
    }

    public void mouseReleased(MouseEvent e)
    {
        int x = (e.getX()-DX)/ZOOM;
        int y = (e.getY()-DY)/ZOOM;
        if (x<0 || x>15 || y<0 ||y>15)
            return;
        if (e.getButton()==3)
            state.level[y][x] = 0;
        else
        {
            if (pen==2 && state.level[state.sy][state.sx]==2)
                state.level[state.sy][state.sx] = 0;
            else if (pen==3 && state.level[state.ty][state.tx]==3)
                state.level[state.ty][state.tx] = 0;
            state.level[y][x] = pen;
            if (pen==2)
            {
                state.sx=x;
                state.sy=y;
            }
            else if (pen==3)
            {
                state.tx=x;
                state.ty=y;
            }
        }
        repaint(0);
    }

    public void mouseEntered(MouseEvent e)
    {
    }

    public void mouseExited(MouseEvent e)
    {
    }

    public void rotateA()
    {
        LevelState s = new LevelState();
        s.rotateRight(state.level);
        state = s;
        repaint(0);
    }

    public void rotateB()
    {
        LevelState s = new LevelState();
        s.rotateLeft(state.level);
        state = s;
        repaint(0);
    }

    public void pushA()
    {
        state = state.pushA(this);
        repaint(0);
    }

    public void pushB()
    {
        state = state.pushB(this);
        repaint(0);
    }

    public void scroll(int dx, int dy)
    {
        state = state.scroll(dx,dy);
        repaint(0);
    }

    public void refresh(LevelState s)
    {
        Graphics2D g = (Graphics2D) getGraphics();
        if (s.remCount>0)
        {
            for (int i = (s.remCount > 0) ? ZOOM2-1 : 0; i>=0; --i)
            {
                paintState(s, g, i);
                try { Thread.sleep(100); } catch (InterruptedException ex) {}
            }
            s.remCount = 0;
        }
        if (s.theta!=0)
        {
            while (s.theta!=0)
            {
                paintState(s, g, 0);
                try { Thread.sleep(100); } catch (InterruptedException ex) {}
                if (s.theta>0)
                    s.theta -= 1;
                else
                    s.theta += 1;
            }
        }
        else
        {
            paintState(s, g, 0);
            try { Thread.sleep(100); } catch (InterruptedException ex) {}
        }
    }

    public void solveIt()
    {
        LevelSolver solver = new LevelSolver(state);
        int[][] sol = solver.getSolutions();
        if (sol==null)
        {
            System.out.println("No solution");
            return;
        }
        System.out.println("Solutions:");
        System.out.println("Count="+sol.length);
        for (int i=0;i<sol.length;++i)
        {
            System.out.print(i+": "+sol[i].length+" ");
            for (int j=0;j<sol[i].length;++j)
                System.out.print((sol[i][j]==0?"A":"B")+" ");
            System.out.println();
        }
    }
}
