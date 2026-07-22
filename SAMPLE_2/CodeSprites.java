import java.awt.image.*;
import java.io.*;
import java.util.*;
import javax.imageio.*;
import javax.imageio.stream.*;

public class CodeSprites
{
  public static String int2Hex(int i, int s) {
    String val = "000" + Integer.toHexString(i);
    return val.substring(val.length() - s);
  }

  private static boolean compPals(List<Integer> a, List<Integer> b) {
    for (int i = 0; i < 4; i++)
      if (!a.get(i).equals(b.get(i))) return false;
    return true;
  }

  private static int rgb2ngp(int col) {
    return ((col & 0xf0) << 4) + ((col & 0xf000) >> 8) + ((col & 0xf00000) >> 20);
  }

  public static void saveSprites(List<BufferedImage> imgs, List<List<Integer>> pals,
                                 List<Integer> palIdx, PrintWriter pw) throws Exception {
    int[] pix = new int[64];
    for (int i = 0; i < imgs.size(); i++) {
      imgs.get(i).getRGB(0, 0, 8, 8, pix, 0, 8);

      // Build sorted 4-colour palette for this tile
      List<Integer> pal = new ArrayList<>(4);
      pal.add(rgb2ngp(pix[0]));
      for (int j = 1; j < 64 && pal.size() < 4; j++) {
        int pj = rgb2ngp(pix[j]);
        int k = 0;
        while (k < pal.size()) {
          int pk = pal.get(k);
          if (pk >= pj) {
            if (pk > pj) pal.add(k, pj);
            pj = -1;
            break;
          }
          k++;
        }
        if (pj >= 0) pal.add(pj);
      }
      while (pal.size() < 4) pal.add(0, 0);

      // Find or register palette
      boolean newPal = true;
      for (int j = 0; j < pals.size() && newPal; j++) {
        if (compPals(pal, pals.get(j))) {
          palIdx.add(j);
          newPal = false;
        }
      }
      if (newPal) {
        palIdx.add(pals.size());
        pals.add(pal);
      }

      // NGP tile encoding: 2 bits per pixel, 8 pixels per row, 8 rows
      for (int j = 0; j < 8; j++) {
        int v = 0;
        for (int k = 0; k < 8; k++)
          v = (v << 2) + pal.indexOf(rgb2ngp(pix[j * 8 + k]));
        pw.print("0x" + int2Hex(v, 4)
            + (j < 7 || i < imgs.size() - 1 ? "," : "")
            + (j < 7 ? "" : "\n"));
      }
    }
  }

  // Read all frames of a (possibly animated) GIF and slice each into 8×8 tiles.
  // Returns the number of tiles per row (sprite sheet width in tiles).
  public static int doTiles(File file, List<BufferedImage> v) throws Exception {
    ImageInputStream iis = ImageIO.createImageInputStream(file);
    Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
    if (!readers.hasNext()) throw new IOException("No ImageReader for: " + file);
    ImageReader reader = readers.next();
    reader.setInput(iis, false, true);

    int numFrames = reader.getNumImages(true);
    int tilesPerRow = 0;

    // Accumulate canvas for animated GIFs (restore-to-background style)
    BufferedImage canvas = null;

    for (int f = 0; f < numFrames; f++) {
      BufferedImage frame = reader.read(f);
      // Convert to ARGB so getRGB() always works
      BufferedImage argb = new BufferedImage(frame.getWidth(), frame.getHeight(),
                                            BufferedImage.TYPE_INT_ARGB);
      argb.createGraphics().drawImage(frame, 0, 0, null);

      if (canvas == null) canvas = argb;

      int w = canvas.getWidth() / 8;
      int h = canvas.getHeight() / 8;
      if (tilesPerRow == 0) tilesPerRow = w;

      for (int row = 0; row < h; row++) {
        for (int col = 0; col < w; col++) {
          BufferedImage tile = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
          int[] pix = argb.getRGB(col * 8, row * 8, 8, 8, null, 0, 8);
          tile.setRGB(0, 0, 8, 8, pix, 0, 8);
          v.add(tile);
        }
      }
    }
    reader.dispose();
    return tilesPerRow;
  }

  public static void main(String[] args) throws Exception {
    if (args.length < 3) {
      System.err.println("Usage: CodeSprites <image> <output.h> <PREFIX>");
      System.exit(1);
    }

    List<BufferedImage>    imgs   = new ArrayList<>();
    List<List<Integer>>    pals   = new ArrayList<>();
    List<Integer>          palIdx = new ArrayList<>();

    int line = CodeSprites.doTiles(new File(args[0]), imgs);

    String id  = args[2];
    String lid = id.toLowerCase();

    PrintWriter pw = new PrintWriter(new FileOutputStream(args[1]));
    pw.println("#define " + id + "_TILES " + imgs.size());
    pw.println("#define " + id + "_LINE "  + line);
    pw.println();
    pw.println("const u16 " + lid + "_tiles[" + id + "_TILES*8] = {");
    CodeSprites.saveSprites(imgs, pals, palIdx, pw);
    pw.println("};");
    pw.println();

    pw.println("#define " + id + "_PALS " + pals.size());
    pw.println();
    pw.println("const u16 " + lid + "_pals[" + id + "_PALS*4]= {");
    for (int i = 0; i < pals.size(); i++) {
      List<Integer> pal = pals.get(i);
      StringBuilder s = new StringBuilder();
      for (int j = 0; j < 4; j++)
        s.append("0x").append(int2Hex(pal.get(j), 4)).append(j < 3 ? "," : "");
      pw.println(s + (i + 1 < pals.size() ? "," : ""));
    }
    pw.println("};");
    pw.println();

    pw.println("const u8 " + lid + "_palidx[" + id + "_TILES] = {");
    for (int i = 0; i < palIdx.size(); i++)
      pw.print(palIdx.get(i) + (i + 1 < palIdx.size() ? "," : "") + ((i + 1) % 16 == 0 ? "\n" : ""));
    pw.println("\n};");
    pw.close();
  }
}
