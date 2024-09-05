import java.util.ArrayList;
import java.util.Stack;

public class LevelSolver
{
    Stack<LevelState> stack;
    Stack<Integer> moves;
    ArrayList<ArrayList<Integer>> sol;

    public LevelSolver(LevelState start)
    {
        stack = new Stack<LevelState>();
        moves = new Stack<Integer>();
        LevelState s = new LevelState(start);
        search(s);
    }

    private void search(LevelState s)
    {
        if (s.tx==-1)
        {
            if (sol==null)
                sol=new ArrayList<ArrayList<Integer>>();
            ArrayList<Integer> list = new ArrayList<Integer>(moves.size());
            list.addAll(moves);
            sol.add(list);
            return;
        }
        if (stack.size()>20)
            return;
        stack.push(s);
        // try A
        LevelState next = s.pushA(null);
        if (next.sx>0 && stack.indexOf(next)<0)
        {
            moves.push(0);
            search(next);
            moves.pop();
        }
        // try B
        next = s.pushB(null);
        if (next.sx>0 && stack.indexOf(next)<0)
        {
            moves.push(1);
            search(next);
            moves.pop();
        }
        stack.pop();
    }

    public int[][] getSolutions()
    {
        if (sol==null)
            return null;
        int[][] ret = new int[sol.size()][];
        for (int i=0;i<ret.length;++i)
        {
            ArrayList<Integer> list = sol.get(i);
            ret[i] = new int[list.size()];
            for (int j=0;j<ret[i].length;++j)
                ret[i][j] = list.get(j);
        }
        return ret;
    }
}
