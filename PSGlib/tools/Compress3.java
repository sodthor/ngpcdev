
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;

public class Compress3 {

	static final int MAX_LEN = 50; // (8 + MAX_LEN - MIN_LEN) < 56 (0x38)
	static final int MIN_LEN = 3;
	static int maxRelative = 128;

	private static final ThreadFactory threadFactory = Thread.ofPlatform().factory();

	public static void main(String[] args) throws Exception {
		File file = null;
		for (int i = 0; i < args.length; ++i) {
			switch (args[i]) {
			case "-h", "--help":
				System.out.println("java -cp . Compress3 [-h] [-a|-r] [track.psg]");
			    System.out.println("if no [track.psg] all psg files of the folder are processed");
				System.out.println("Options:");
				System.out.println("-h or --help: display this doc");
				System.out.println("-a or --absolute: normal offsets computation and output format");
				System.out.println("-r or --relative: allow negative relative offsets, substring 16 bits offset values are inverted (default)");
				break;
			case "-a", "--absolute":
				maxRelative = 0;
				break;
			case "-r", "--relative":
				maxRelative = 128;
				break;
			default:
				File f = new File(args[i]);
				if (args[i].toLowerCase().endsWith(".psg") && f.exists() && f.canRead()) {
					file = f;
				} else {
					System.err.println("Can't read file: " + args[i]);
					System.exit(1);
				}
			}
		}
		if (file != null) {
			compress(file);
			return;
		}
		int size = Runtime.getRuntime().availableProcessors();
		File folder = new File("./");
		File[] files = folder.listFiles((f) -> f.getName().toLowerCase().endsWith(".psg"));
		ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(size, threadFactory);
		for (final File f : files) {
			executor.submit(() -> {
				try {
					compress(f);
				} catch (Exception e) {
					e.printStackTrace();
				}
			});
		}
		while (executor.getActiveCount() > 0) {
			Thread.sleep(100L);
		}
		System.exit(0);
	}

	public static void compress(File f) throws Exception {
		Thread.currentThread().setName(f.getName());
		byte[] b = new byte[(int) f.length()];
		try (FileInputStream in = new FileInputStream(f)) {
			in.read(b);
		}

		StringBuilder sb = new StringBuilder(128);
		for (int i = 0; i < b.length; ++i) {
			sb.append(String.format("%02x:", b[i]));
		}
		String data = sb.toString();

		// compute all possible substrings and their frequency
		Map<String, Integer> frequency = new HashMap<>();
		Map<String, Integer> gains = new HashMap<>();
		Set<String> used = new HashSet<>();
		List<String> breakDown = new ArrayList<>();

		List<String> pieces = new ArrayList<>();
		pieces.add(data);
		String candidate = rebuildLists(frequency, gains, used, pieces);

		while (candidate != null && gains.get(candidate) > 0) {
			boolean found = false;
			for (String piece : pieces) {
				if (piece.charAt(0) == 0) {
					breakDown.add(piece);
					continue;
				}
				int j = 0;
				for (int i = piece.indexOf(candidate); i >= 0; i = piece.indexOf(candidate, j)) {
					if (j < i) {
						breakDown.add(piece.substring(j, i));
					}
					breakDown.add("\0" + candidate);
					j = i + candidate.length();
				}
				if (j > 0) {
					used.add(candidate);
				}
				if (j < piece.length()) {
					breakDown.add(piece.substring(j));
				}
				found |= j > 0;
			}
			if (found) {
				candidate = rebuildLists(frequency, gains, used, breakDown);
				pieces.clear();
				pieces.addAll(breakDown);
				breakDown.clear();
			} else {
				candidate = null;
			}
		}

		// check if some used substrings are included in bigger used substrings
		Map<String, String> included = new HashMap<>();
		for (String s1 : used) {
			String found = null;
			for (String s2 : used) {
				// find the longest used substring containing this one
				if (s2.length() > s1.length() && (found == null || found.length() < s2.length()) && s2.contains(s1)) {
					found = s2;
				}
			}
			if (found != null) {
				included.put(s1, found);
			}
		}

		Map<Integer, String> map = new HashMap<>();
		Map<String, Integer> offsets = new HashMap<>();
		byte[] out = new byte[b.length];
		int len = 0;

		StringBuilder result = new StringBuilder();
		for (String s : pieces) {
			boolean key = s.charAt(0) == 0;
			String substring = key ? s.substring(1) : s;
			Integer offset = key ? offsets.get(substring) : null;
			int length = substring.length();
			if (!key || (offset == null && !included.containsKey(substring))) {
				if (key) {
					offsets.put(substring, len);
					len = putInBuffer(out, len, result, substring);
				} else {
					// optimal greedy LZ77 with lazy evaluation
					int i = 0;
					while (i < length) {
						int[] match0 = findBestMatch(substring, i, length, result, len, maxRelative);
						int bestMatchLen = match0[0];
						int bestMatchIdx = match0[1];

						if (bestMatchLen >= MIN_LEN * 3) {
							if (i + 3 < length) {
								int[] match1 = findBestMatch(substring, i + 3, length, result, len, maxRelative);
								if (match1[0] > bestMatchLen) {
									bestMatchLen = 0; // fallback to literal
								}
							}
						}

						if (bestMatchLen >= MIN_LEN * 3) {
							String chunk = substring.substring(i, i + bestMatchLen);
							len = putIndexedValue(out, len, result, bestMatchLen / 3, bestMatchIdx / 3, chunk);
							i += bestMatchLen;
						} else {
							String chunk = substring.substring(i, i + 3);
							len = putInBuffer(out, len, result, chunk);
							i += 3;
						}
					}
				}
			} else {
				if (key && offset == null) {
					String including = included.get(substring);
					if (offsets.get(including) != null) {
						offset = offsets.get(including) + including.indexOf(substring) / 3;
					}
				}
				if (offset == null) {
					out[len++] = (byte) (8 + (length / 3) - MIN_LEN);
					// mark offset to fill later
					map.put(len, substring);
					result.append(String.format("%02x:XX:YY:", out[len - 1]));
					len += 2;
				} else {
					len = putIndexedValue(out, len, result, length / 3, offset, substring);
				}
			}
		}

		// put index of substrings in data
		for (Map.Entry<Integer, String> entry : map.entrySet()) {
			int idx = entry.getKey();
			String substring = entry.getValue();
			Integer offset = offsets.get(substring);
			if (offset == null) {
				// compute offset from including substring
				String including = included.get(substring);
				offset = offsets.get(including) + including.indexOf(substring) / 3;
			}
			if (maxRelative > 0) {
				out[idx] = (byte) (offset / 256);
				out[idx + 1] = (byte) (offset % 256);
			} else {
				out[idx] = (byte) (offset % 256);
				out[idx + 1] = (byte) (offset / 256);
			}
		}

		// save
        Bin2C.bin2C(f.getName(), new ByteArrayInputStream(out), len);
		byte[] dec = Decompress.decompress(out, len, maxRelative > 0);
		for (int i = 0, j = 0; i < Math.min(b.length, len); i++) {
			while (dec[j++] != b[i]) {
				System.err.println(i + " " + j);
			}
		}

		System.err.println(f.getName() + " " + b.length + "->" + len + " (" + dec.length + ")");
	}

	static Set<String> CODES = new HashSet<>();
	static {
		for (int i = 8; i < 56; i++)
			CODES.add(String.format("%02x", i));
	}

	private static boolean noSubstring(String chunk) {
		Set<String> set = new HashSet<String>(Arrays.asList(chunk.split(":")));
		return Collections.disjoint(CODES, set);
	}

	private static int putInBuffer(byte[] out, int len, StringBuilder result, String substring) {
		// put substrings in the buffer
		for (int idx = 0; idx < substring.length(); idx += 3) {
			out[len++] = (byte) Integer.parseInt(substring.substring(idx, idx + 2), 16);
		}
		result.append(substring);
		return len;
	}

	private static int putIndexedValue(byte[] out, int len, StringBuilder result, int l, Integer offset, String chunk) {
		out[len++] = (byte) (8 + l - MIN_LEN);
		if (maxRelative <= 0 || len - offset > maxRelative + l) {
			if (l == 3) {
				// no substring needed
				return putInBuffer(out, --len, result, chunk);
			} else {
				if (maxRelative > 0) {
					out[len++] = (byte) (offset / 256);
					out[len++] = (byte) (offset % 256);
				} else {
					out[len++] = (byte) (offset % 256);
					out[len++] = (byte) (offset / 256);
				}
				result.append(String.format("%02x:%02x:%02x:", out[len - 3], out[len - 2], out[len - 1]));
			}
		} else {
			out[len] = (byte) (offset - len + l);
			len++;
			result.append(String.format("%02x:%02x:", out[len - 2], out[len - 1]));
		}
		return len;
	}

	private static int[] findBestMatch(String substring, int i, int length, StringBuilder result, int len, int maxRelative) {
		int bestMatchLen = 0;
		int bestMatchIdx = -1;
		int bestSavings = -1;

		for (int matchLen = Math.min(MAX_LEN * 3, length - i); matchLen >= MIN_LEN * 3; matchLen -= 3) {
			String chunk = substring.substring(i, i + matchLen);
			if (noSubstring(chunk)) {
				int idx = result.lastIndexOf(chunk);
				if (idx >= 0) {
					int l = matchLen / 3;
					int offset = idx / 3;
					int currentLen = len + 1;
					int dist = currentLen - offset;
					int cost = (maxRelative <= 0 || dist > maxRelative + l) ? 3 : 2;
					if (cost == 3 && l == 3) cost = 3;

					int savings = l - cost;
					if (savings > bestSavings) {
						bestSavings = savings;
						bestMatchLen = matchLen;
						bestMatchIdx = idx;
					} else if (savings == bestSavings) {
						if (matchLen > bestMatchLen) {
							bestMatchLen = matchLen;
							bestMatchIdx = idx;
						}
					}
				}
			}
		}
		return new int[] { bestMatchLen, bestMatchIdx, bestSavings };
	}

	private static String rebuildLists(Map<String, Integer> frequency, Map<String, Integer> gains, Set<String> used,
			List<String> breakDown) {
		frequency.clear();
		for (String substring : breakDown) {
			if (substring.charAt(0) == 0) {
				continue;
			}
			for (int i = 0; i < substring.length(); i += 3) {
				for (int j = MIN_LEN * 3; j <= MAX_LEN * 3; j += 3) {
					if (i + j > substring.length()) {
						break;
					}
					String sub = substring.substring(i, i + j);
					frequency.put(sub, frequency.getOrDefault(sub, 0) + 1);
				}
			}
		}
		return getCandidate(frequency, gains, used);
	}

	private static String getCandidate(Map<String, Integer> frequency, Map<String, Integer> gains, Set<String> used) {
		gains.clear();
		for (Map.Entry<String, Integer> e : frequency.entrySet()) {
			String k = e.getKey();
			int l = k.length() / 3;
			boolean sub = false;
			for (String key : used) {
				if (key.length() > k.length() && key.contains(k)) {
					sub = true;
					break;
				}
			}
			int gain = e.getValue() * l - (sub ? e.getValue() * 3 : (((e.getValue() - 1) * 3) + l));
			gains.put(k, gain);
		}
		return gains.entrySet().stream()
				.max((a, b) -> ((a.getValue().intValue() == b.getValue().intValue())
						? (a.getKey().length() - b.getKey().length())
						: (a.getValue().intValue() - b.getValue().intValue())))
				.get().getKey();
	}
}
