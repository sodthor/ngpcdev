import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;

import javax.imageio.ImageIO;

public class GenSpritePriority {

	public static void main(String[] args) throws IOException {
		int sprW = 2, sprH = 2;
		BufferedImage img = ImageIO.read(new File("gfx/sprites.png"));
		int tW = img.getWidth() / 8;
		int tH = img.getHeight() / 8;
		int[] priority = new int[tW * tH];
		int k = 0;
		for (int y = 0; y < tH; y += sprH) {
			for (int x = 0; x < tW; x += sprW) {
				for (int i = 0; i < sprH; ++i) {
					for (int j = 0; j < sprW; ++j) {
						priority[k++] = x + j + tW * (y + i); 
					}
				}
			}
		}
		PrintWriter pw = new PrintWriter(new File("sprites_prio.h"));
		pw.println("const u16 SPRITES_PRIORITY[" + (tW * tH) + "] = {");
		for (int i = priority.length - 1; i >= 0; --i) {
			pw.print(priority[i] + (i > 0 ? "," : "};"));
			if (i % tW == 0) {
				pw.println();
			}
		}
		pw.close();
	}
}
