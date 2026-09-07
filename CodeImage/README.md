# CodeImage for Python

A PyQt6 port of the Java CodeImage NGPC tile converter. It loads common image formats, quantizes pixels into NGPC 4-bit-per-channel colors, extracts 8x8 tiles, builds one or two planes of four-color palettes, and exports Java-compatible C headers or assembly include files.

## Original Java version

The original Swing implementation is in `CodeImage.java` with its supporting Java sources. From this directory, compile and launch it with:

```sh
javac *.java
java CodeImage
```

Running without arguments opens the Java UI. The command-line form is:

```sh
java CodeImage input.png output_id
java CodeImage input.png output_id -p2 -n16
java CodeImage input.png output_id -p2 -n16 -a
java CodeImage input.png output_id -p2 -h
```

Java writes C output as `output_id.hh` and assembly output as `output_id.inc`. Useful Java options are:

- `-p1` or `-p2`: number of color planes
- `-nN`: maximum number of palettes
- `-a`: assembly output instead of C
- `-h`: HiColor output
- `-d`: Bayer dithering
- `-r`: reduce duplicate tiles
- `-w`: reduce tiles and store flip variants
- `-mN`: merge tolerance for tile reduction

## Run the GUI

```sh
python3 -m pip install -r requirements.txt
python3 codeimage.py
```

Use **Open image**, choose the encoder options, click **Encode**, and then **Save output**. The UI supports:

- C header or assembly output
- One or two color planes
- HiColor mode with Java-compatible `ID`, `DYN`, and `IDX` sections
- Palette reduction, tile reduction, tile flips, resizing, and Bayer dithering
- RGB background selection for transparent pixels
- Logical/stored tile counts, quantized color count, output difference, and byte size
- A `1:1 preview` toggle for viewing images at native pixel size

## Command line

The same conversion engine can be used without the GUI:

```sh
python3 codeimage.py input.png -o c -O output.h --planes 2 --resize --dither --reduce --flip
python3 codeimage.py input.png -o asm -O output.inc
python3 codeimage.py input.png -o c -O hicolor.h --planes 2 --hicolor
python3 codeimage.py input.png -o asm -O hicolor.inc --planes 2 --hicolor --max-size
```

Images are cropped to complete 8x8 tiles in normal mode. HiColor mode scales or centers the source into a fixed 160 x 152 pixel, 20 x 19 tile screen. Images larger than 512 logical tiles require `--force` or the GUI checkbox.
