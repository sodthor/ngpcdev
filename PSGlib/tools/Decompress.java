import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

public class Decompress {

	public static void main(String[] args) throws Exception {
		File fileIn = null;
		File fileOut = null;
		boolean relative = true;
		for (int i = 0; i < args.length; ++i) {
			switch (args[i]) {
			case "-h", "--help":
				System.out.println("java -cp . Decompress [-h] [-a|-r] <track-in.psg> <track-out.psg>");
				System.out.println("Options:");
				System.out.println("-h or --help: display this doc");
				System.out.println("-a or --absolute: normal offsets computation and output format");
				System.out.println("-r or --relative: allow negative relative offsets, substring 16 bits offset values are inverted (default)");
				break;
			case "-a", "--absolute":
				relative = false;
				break;
			case "-r", "--relative":
				relative = true;
				break;
			default:
				File f = new File(args[i]);
				if (fileIn == null) {
					if (f.exists() && f.canRead()) {
						fileIn = f;
					} else {
						System.err.println("Can't read file: " + args[i]);
						System.exit(1);
					}
				} else {
					fileOut = f;
				}
			}
		}
		if (fileIn == null) {
			System.err.println("No input file");
			System.exit(1);
		}
		if (fileOut == null) {
			System.err.println("No output file");
			System.exit(1);
		}
		byte[] b = new byte[(int) fileIn.length()];
        try (FileInputStream in = new FileInputStream(fileIn)) {
            in.read(b);
        }
        byte[] d = decompress(b, b.length, relative);
        try (FileOutputStream out = new FileOutputStream(fileOut)) {
            out.write(d);
        }
	}

	public static byte[] decompress(byte[] b, int len, boolean relative) throws Exception {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		int i = 0;
		while (i < len) {
			int data = (b[i] < 0 ? 256 : 0) + b[i];
			i += 1;
			if (data < 56 && data >= 8) {
				int l = data - 8 + Compress3.MIN_LEN;
				int offset;
				if (relative && b[i] < 0) {
					offset = i - l + (int) b[i];
					i += 1;
				} else {
					if (relative) {
						offset = (((b[i] < 0 ? 256 : 0) + b[i]) * 256) + ((b[i + 1] < 0 ? 256 : 0) + b[i + 1]);
					} else {
						offset = ((b[i] < 0 ? 256 : 0) + b[i]) + ((b[i + 1] < 0 ? 256 : 0) + b[i + 1]) * 256;
					}
					i += 2;
				}
				for (int j = 0; j < l; j++) {
					byte d = b[offset++];
					if (d >= 8 && d < 56)
						System.err.println("error a:" + (offset - 1));
					baos.write(d);
				}
			} else {
				baos.write(b[i-1]);
			}
		}
		baos.flush();
		return baos.toByteArray();
	}
}
