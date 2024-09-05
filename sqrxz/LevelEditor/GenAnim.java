import java.io.File;
import java.io.FileReader;
import java.io.LineNumberReader;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

public class GenAnim {

	static final int IW = 17;
	
	/**
	 * @param args
	 */
	public static void main(String[] args) throws Exception {
		LineNumberReader lnr = new LineNumberReader(new FileReader("animated.txt"));
		String line;
		List<List<Integer>> animList = new ArrayList<List<Integer>>();
		int max = 0;
		while ((line = lnr.readLine()) != null) {
			line = line.trim();
			if (line.length() == 0) continue;
			List<Integer> list = new ArrayList<Integer>();
			animList.add(list);
			StringTokenizer st = new StringTokenizer(line,",");
			while(st.hasMoreTokens()) {
				String token = st.nextToken();
				try {
					Integer i = Integer.decode(token);
					list.add(i);
				} catch(NumberFormatException nfe) {
					// ignore
				}
			}
			if (list.size() > max)
				max = list.size();
		}
		lnr.close();
		PrintWriter pw = new PrintWriter(new File("animated.h"));
		pw.println("#define AMINATED_COUNT " + animList.size());
		pw.println("#define MAX_ANIM_FRAME " + max);
		pw.println();
		pw.println("const u16 anim_fcount[AMINATED_COUNT] = {");
		for (int i = 0; i < animList.size(); ++i) {
			List<Integer> list = animList.get(i);
			pw.println(list.size() + (i + 1 < animList.size() ? "," : ""));
		}
		pw.println("};");
		pw.println();
		pw.println("const u16 anim_frames[AMINATED_COUNT][4][MAX_ANIM_FRAME] = {");
		for (int i = 0; i < animList.size(); ++i) {
			List<Integer> list = animList.get(i);
			String s0 = "", s1 = "", s2 = "", s3 = "";
			for (int j = 0; j < max; ++j) {
				int id = j < list.size() ? list.get(j) : -1;
				if (id >= 0) {
					id = ((id / IW) * IW * 4) + (id % IW) * 2;
				}
				s0 += id + (j + 1 < max ? "," : "");
				s1 += (id < 0 ? id : (id + 1)) + (j + 1 < max ? "," : "");
				s2 += (id < 0 ? id : (id + IW * 2)) + (j + 1 < max ? "," : "");
				s3 += (id < 0 ? id : (id + IW * 2 + 1)) + (j + 1 < max ? "," : "");
			}
			pw.println("{");
			pw.println("{"+s0+"},");
			pw.println("{"+s1+"},");
			pw.println("{"+s2+"},");
			pw.println("{"+s3+"}");
			pw.println("}" + (i + 1 < animList.size() ? "," : ""));
		}
		pw.println("};");
		pw.close();
	}
}
