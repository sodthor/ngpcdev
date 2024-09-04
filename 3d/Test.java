class Test
{
static int[][] cube_vertices = {
	{-22,23,-22},
	{23,23,-22},
	{-22,23,23},
	{23,23,23},
	{-22,-22,-22},
	{23,-22,-22},
	{-22,-22,23},
	{23,-22,23}};

static int[][] cube_triangles = {
	{0,9,6,2},
	{9,0,3,2},
	{12,21,15,2},
	{21,12,18,2},
	{0,15,3,4},
	{15,0,12,4},
	{3,21,9,6},
	{21,3,15,6},
	{9,18,6,4},
	{18,9,21,4},
	{6,12,0,6},
	{12,6,18,6}};

static int[][] cube_normals = {
	{0,31,0},
	{0,31,0},
	{0,-31,0},
	{0,-31,0},
	{0,0,-31},
	{0,0,-31},
	{31,0,0},
	{31,0,0},
	{0,0,31},
	{0,0,31},
	{-31,0,0},
	{-31,0,0}};

static int[] mat = new int[16];
static double[] fmat = new double[16];

	public static String g(int n)
	{
		return d(n/31.0);
	}

	public static String h(int n)
	{
		String s = (((int)n)<0?"ffff":"0000")+Integer.toHexString((int)n);
		return s.substring(s.length()-4);
	}

	public static String f(int n)
	{
		String s = (((int)n)<0?"ff":"00")+Integer.toHexString((int)n);
		return s.substring(s.length()-2);
	}

	public static String d(double n)
	{
		return String.valueOf(n);
	}

	public static void main(String[] args)
	{
		double rotx = (Double.valueOf(args[0]).doubleValue()*Math.PI)/128;
		double roty = (Double.valueOf(args[1]).doubleValue()*Math.PI)/128;
		double rotz = (Double.valueOf(args[2]).doubleValue()*Math.PI)/128;

		int cx = (int)(Math.cos(rotx)*31);
		int cy = (int)(Math.cos(roty)*31);
		int cz = (int)(Math.cos(rotz)*31);
		int sx = (int)(Math.sin(rotx)*31);
		int sy = (int)(Math.sin(roty)*31);
		int sz = (int)(Math.sin(rotz)*31);

		double cx0 = Math.cos(rotx);
		double cy0 = Math.cos(roty);
		double cz0 = Math.cos(rotz);
		double sx0 = Math.sin(rotx);
		double sy0 = Math.sin(roty);
		double sz0 = Math.sin(rotz);

		System.err.println(h(cz*cy-sz*sx*sy)+","+h(-sz*cx)+","+h(cz*sy+sz*sx*cy));
		System.err.println(h(sz*cy+cz*sx*sy)+","+h(cz*cx)+","+h(sz*sy-cz*sx*cy));
		System.err.println(h(-cz*sy)+","+h(sx)+","+h(cx*cy));
		System.err.println();
		System.err.println(d(fmat[0]=cz0*cy0-sz0*sx0*sy0)+","+d(fmat[1]=-sz0*cx0)+","+d(fmat[2]=cz0*sy0+sz0*sx0*cy0));
		System.err.println(d(fmat[4]=sz0*cy0+cz0*sx0*sy0)+","+d(fmat[5]=cz0*cx0)+","+d(fmat[6]=sz0*sy0-cz0*sx0*cy0));
		System.err.println(d(fmat[8]=-cz0*sy0)+","+d(fmat[9]=sx0)+","+d(fmat[10]=cx0*cy0));
		System.err.println();
		System.err.println(h(mat[0]=((cz*cy)>>5)-((sz*sx*sy)>>10))+","+h(mat[1]=(-sz*cx)>>5)+","+h(mat[2]=((cz*sy)>>5)+((sz*sx*cy)>>10)));
		System.err.println(h(mat[4]=((sz*cy)>>5)+((cz*sx*sy)>>10))+","+h(mat[5]=(cz*cx)>>5)+","+h(mat[6]=((sz*sy)>>5)-((cz*sx*cy)>>10)));
		System.err.println(h(mat[8]=(-cz*sy)>>5)+","+h(mat[9]=sx)+","+h(mat[10]=(cx*cy)>>5));
		System.err.println();
		System.err.println(g(((cz*cy)>>5)-((sz*sx*sy)>>10))+","+g((-sz*cx)>>5)+","+g(((cz*sy)>>5)+((sz*sx*cy)>>10)));
		System.err.println(g(((sz*cy)>>5)+((cz*sx*sy)>>10))+","+g((cz*cx)>>5)+","+g(((sz*sy)>>5)-((cz*sx*cy)>>10)));
		System.err.println(g((-cz*sy)>>5)+","+g(sx)+","+g((cx*cy)>>5));
		System.err.println();
		System.err.println(h(mat[0])+","+h(mat[1])+","+h(mat[2]));
		System.err.println(h(mat[4])+","+h(mat[5])+","+h(mat[6]));
		System.err.println(h(mat[8])+","+h(mat[9])+","+h(mat[10]));
		System.err.println();
		int[][] t = cube_normals;
		for (int i=0;i<t.length;i++)
		{
			System.err.println(f((t[i][0]*mat[0]+t[i][1]*mat[4]+t[i][2]*mat[8]+mat[12])>>5)+","
					+  f((t[i][0]*mat[1]+t[i][1]*mat[5]+t[i][2]*mat[9]+mat[13])>>5)+","
					+  f((t[i][0]*mat[2]+t[i][1]*mat[6]+t[i][2]*mat[10]+mat[14])>>5));
		}
		System.err.println();
		t = cube_vertices;
		for (int i=0;i<t.length;i++)
		{
			System.err.println(f((t[i][0]*mat[0]+t[i][1]*mat[4]+t[i][2]*mat[8]+mat[12])>>5)+","
					+  f((t[i][0]*mat[1]+t[i][1]*mat[5]+t[i][2]*mat[9]+mat[13])>>5)+","
					+  f((t[i][0]*mat[2]+t[i][1]*mat[6]+t[i][2]*mat[10]+mat[14])>>5));
		}
		System.err.println();
		t = cube_vertices;
		for (int i=0;i<t.length;i++)
		{
			System.err.println(d((t[i][0]*fmat[0]+t[i][1]*fmat[4]+t[i][2]*fmat[8]+fmat[12]))+","
					+  d((t[i][0]*fmat[1]+t[i][1]*fmat[5]+t[i][2]*fmat[9]+fmat[13]))+","
					+  d((t[i][0]*fmat[2]+t[i][1]*fmat[6]+t[i][2]*fmat[10]+fmat[14])));
		}
	}
}