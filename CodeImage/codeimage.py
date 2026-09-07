from __future__ import annotations

import argparse
import io
import os
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Callable, Sequence

from PIL import Image, ImageDraw, ImageOps


BAYER4 = ((0, 8, 2, 10), (12, 4, 14, 6), (3, 11, 1, 9), (15, 7, 13, 5))


@dataclass(frozen=True)
class Options:
    output: str = "c"
    planes: int = 2
    contrast: int = 1
    balance: str = "color-middle"
    palettes: int = 16
    force: bool = False
    reduce_tiles: bool = False
    flip_tiles: bool = False
    resize: bool = False
    dither: bool = False
    merge: int = 0
    background: tuple[int, int, int] = (0, 0, 0)
    hicolor: bool = False
    max_size: bool = False


@dataclass
class EncodedImage:
    data: bytes
    preview: Image.Image
    tile_width: int
    tile_height: int
    tile_count: int
    palette_count: int
    colors: int
    diff: int
    stored_tile_count: int


def ngp_channel(value: int) -> int:
    value = max(0, min(255, value))
    return min(255, (value & 0xF0) + (0x10 if (value & 0x0F) > 7 and value < 0xF0 else 0))


def ngp_word(color: tuple[int, int, int]) -> int:
    red, green, blue = (ngp_channel(channel) for channel in color)
    return (red >> 4) | (green & 0xF0) | ((blue & 0xF0) << 4)


def color_distance(first: tuple[int, int, int], second: tuple[int, int, int]) -> int:
    red = first[0] - second[0]
    green = first[1] - second[1]
    blue = first[2] - second[2]
    return (red * red * 30 + green * green * 59 + blue * blue * 11) // 100


def apply_dither(image: Image.Image, background: tuple[int, int, int]) -> Image.Image:
    result = image.convert("RGBA")
    pixels = result.load()
    for y in range(result.height):
        for x in range(result.width):
            red, green, blue, alpha = pixels[x, y]
            if (red, green, blue) == background:
                continue
            offset = (BAYER4[y & 3][x & 3] / 16.0 - 0.5) * 17.0
            pixels[x, y] = tuple(max(0, min(255, round(channel + offset))) for channel in (red, green, blue)) + (alpha,)
    return result


def prepare_image(source: Image.Image, options: Options) -> Image.Image:
    image = source.convert("RGBA")
    if options.resize and (image.width > 160 or image.height > 152):
        scale = min(160 / image.width, 152 / image.height)
        image = image.resize((max(1, round(image.width * scale)), max(1, round(image.height * scale))), Image.Resampling.LANCZOS)
    if options.dither:
        image = apply_dither(image, options.background)
    return image


def prepare_hicolor_image(source: Image.Image, options: Options) -> Image.Image:
    image = source.convert("RGBA")
    target = Image.new("RGBA", (160, 152), options.background + (255,))
    if options.max_size:
        fitted = ImageOps.fit(image, target.size, method=Image.Resampling.BICUBIC)
    elif image.width <= 160 and image.height <= 152:
        fitted = image
    else:
        scale = min(160 / image.width, 152 / image.height)
        fitted = image.resize((max(1, round(image.width * scale)), max(1, round(image.height * scale))), Image.Resampling.BICUBIC)
    target.alpha_composite(fitted, ((160 - fitted.width) // 2, (152 - fitted.height) // 2))
    return apply_dither(target, options.background) if options.dither else target


def tile_pixels(image: Image.Image, background: tuple[int, int, int]) -> tuple[list[list[tuple[int, int, int]]], int, int]:
    width = image.width // 8
    height = image.height // 8
    if width == 0 or height == 0:
        raise ValueError("Image must be at least 8 x 8 pixels")
    pixels = image.load()
    tiles: list[list[tuple[int, int, int]]] = []
    for tile_y in range(height):
        for tile_x in range(width):
            tile = []
            for y in range(8):
                for x in range(8):
                    red, green, blue, alpha = pixels[tile_x * 8 + x, tile_y * 8 + y]
                    tile.append(background if alpha == 0 else (red, green, blue))
            tiles.append(tile)
    return tiles, width, height


def choose_plane_colors(tiles: Sequence[Sequence[tuple[int, int, int]]], options: Options) -> tuple[list[tuple[int, int, int]], list[tuple[int, int, int]]]:
    counts: dict[tuple[int, int, int], int] = {}
    for tile in tiles:
        for color in tile:
            counts[color] = counts.get(color, 0) + 1
    colors = sorted(counts, key=lambda color: (sum(color), color))
    if options.planes == 1:
        return colors, []
    if options.balance.startswith("color"):
        split = len(colors) // 2
        if options.balance.endswith("dark"):
            split = len(colors) // 3
        elif options.balance.endswith("light"):
            split = (len(colors) * 2) // 3
    else:
        total = sum(counts.values())
        target = total // (3 if options.balance.endswith("dark") else 1 if options.balance.endswith("light") else 2)
        running = 0
        split = len(colors)
        for index, color in enumerate(colors):
            running += counts[color]
            if running >= target:
                split = index + 1
                break
    return colors[:split], colors[split:]


def nearest_index(color: tuple[int, int, int], palette: Sequence[tuple[int, int, int]]) -> int:
    return min(range(len(palette)), key=lambda index: color_distance(color, palette[index]))


def reduce_palette(colors: Sequence[tuple[int, int, int]], counts: dict[tuple[int, int, int], int], limit: int) -> list[tuple[int, int, int]]:
    palette = list(dict.fromkeys(colors))
    while len(palette) > limit:
        best_pair: tuple[int, int] | None = None
        best_score = 2**63 - 1
        for first in range(1, len(palette)):
            for second in range(first + 1, len(palette)):
                score = color_distance(palette[first], palette[second]) * (counts.get(palette[first], 1) + counts.get(palette[second], 1))
                if score < best_score:
                    best_score, best_pair = score, (first, second)
        if best_pair is None:
            break
        first, second = best_pair
        first_color, second_color = palette[first], palette[second]
        first_weight = counts.get(first_color, 1)
        second_weight = counts.get(second_color, 1)
        merged = tuple(round((first_color[i] * first_weight + second_color[i] * second_weight) / (first_weight + second_weight)) for i in range(3))
        palette[first] = merged
        palette.pop(second)
        counts[merged] = first_weight + second_weight
    return palette


def palette_distance(first: Sequence[tuple[int, int, int]], second: Sequence[tuple[int, int, int]]) -> int:
    first_body = first[1:] or first[:1]
    second_body = second[1:] or second[:1]
    return sum(min(color_distance(color, candidate) for candidate in second_body) for color in first_body) + sum(min(color_distance(color, candidate) for candidate in first_body) for color in second_body)


def merge_palette_set(local_palettes: list[list[tuple[int, int, int]]], counts: Sequence[dict[tuple[int, int, int], int]], limit: int, background: tuple[int, int, int]) -> list[list[tuple[int, int, int]]]:
    palettes = [list(palette) for palette in local_palettes]
    palette_counts = [dict(count) for count in counts]
    while len(palettes) > limit:
        first, second = min(((first, second) for first in range(len(palettes)) for second in range(first + 1, len(palettes))), key=lambda pair: palette_distance(palettes[pair[0]], palettes[pair[1]]))
        merged_colors = [background] + [color for color in palettes[first][1:] + palettes[second][1:] if color != background]
        merged_counts: dict[tuple[int, int, int], int] = {}
        for count in (palette_counts[first], palette_counts[second]):
            for color, weight in count.items():
                merged_counts[color] = merged_counts.get(color, 0) + weight
        palettes[first] = reduce_palette(merged_colors, merged_counts, 4)
        palette_counts[first] = merged_counts
        palettes.pop(second)
        palette_counts.pop(second)
    return palettes


def make_palettes(tiles: Sequence[Sequence[tuple[int, int, int]]], options: Options) -> tuple[list[list[tuple[int, int, int]]], list[list[list[int]]], list[list[int]], list[list[list[tuple[int, int, int]]]]]:
    first_colors, second_colors = choose_plane_colors(tiles, options)
    plane_colors = [first_colors] + ([second_colors] if options.planes == 2 else [])
    all_palettes: list[list[list[tuple[int, int, int]]]] = []
    encoded_planes: list[list[list[int]]] = []
    palette_ids: list[list[int]] = []
    for colors in plane_colors:
        color_set = set(colors)
        local_palettes: list[list[tuple[int, int, int]]] = []
        local_counts: list[dict[tuple[int, int, int], int]] = []
        for tile in tiles:
            tile_colors = [color if color in color_set else options.background for color in tile]
            counts: dict[tuple[int, int, int], int] = {}
            for color in tile_colors:
                counts[color] = counts.get(color, 0) + 1
            ordered_colors = [options.background] + [color for color in counts if color != options.background]
            local_palettes.append(reduce_palette(ordered_colors, counts.copy(), 4))
            local_counts.append(counts)
        palettes = merge_palette_set(local_palettes, local_counts, max(1, min(options.palettes, len(local_palettes))), options.background)
        all_palettes.append(palettes)
        plane_indices: list[list[int]] = []
        plane_palette_ids: list[int] = []
        for tile in tiles:
            tile_colors = [color if color in color_set else options.background for color in tile]
            palette_index, palette = min(enumerate(palettes), key=lambda candidate: sum(color_distance(color, candidate[1][nearest_index(color, candidate[1])]) for color in tile_colors if color != options.background))
            plane_palette_ids.append(palette_index)
            plane_indices.append([nearest_index(color, palette) if color != options.background else 0 for color in tile_colors])
        palette_ids.append(plane_palette_ids)
        encoded_planes.append(plane_indices)
    flattened = [palette for palettes in all_palettes for palette in palettes]
    return flattened, encoded_planes, palette_ids, all_palettes


def flipped_tile(tile: Sequence[int], vertical: bool, horizontal: bool) -> list[int]:
    return [tile[(7 - y if vertical else y) * 8 + (7 - x if horizontal else x)] for y in range(8) for x in range(8)]


def tile_key(tile: Sequence[int], fuzz: int) -> tuple[int, ...]:
    return tuple(tile if fuzz == 0 else (value // (fuzz + 1) for value in tile))


def u16(value: int) -> bytes:
    return int(value & 0xFFFF).to_bytes(2, "little")


def output_text(values: Sequence[int], output: str, label: str, c_type: str = "u16", c_length: int | None = None, line_width: int = 8, line_comment: str | None = None, asm_line_comment: str = ";") -> str:
    chunks = [values[index:index + line_width] for index in range(0, len(values), line_width)]
    if output == "asm":
        lines = []
        for index, chunk in enumerate(chunks):
            if line_comment is not None:
                lines.append(f"{asm_line_comment} {line_comment.format(index=index)}")
            lines.append("\tdw\t" + ",".join(f"{value:04x}h" for value in chunk))
        return f"{label}\n" + "\n".join(lines) + "\n"
    size = c_length if c_length is not None else len(values)
    lines = []
    for index, chunk in enumerate(chunks):
        if line_comment is not None:
            lines.append(f"// {line_comment.format(index=index)}")
        lines.append(",".join(f"0x{value:04x}" for value in chunk) + ("," if index + 1 < len(chunks) else ""))
    return f"const {c_type} {label}[{size}] = {{\n" + "\n".join(lines) + "\n};\n"


def packed_lines(values: Sequence[int], output: str, row_size: int, comments: bool = False) -> list[str]:
    chunks = [values[index:index + 8] for index in range(0, len(values), 8)]
    lines: list[str] = []
    for index, chunk in enumerate(chunks):
        if comments and index * 8 % row_size == 0:
            row = index * 8 // row_size
            lines.append(f"{'//' if output == 'c' else ';'} Line {row}")
        if output == "asm":
            lines.append("\tdw\t" + ",".join(f"{value:05x}h" for value in chunk))
        else:
            lines.append(",".join(f"0x{value:04x}" for value in chunk) + ("," if index + 1 < len(chunks) else ""))
    return lines


def tile_words(tiles: Sequence[Sequence[int]]) -> list[int]:
    words: list[int] = []
    for tile in tiles:
        for row in range(8):
            value = 0
            for column in range(8):
                value = (value << 2) | tile[row * 8 + column]
            words.append(value)
    return words


def reconstruction(tiles: Sequence[Sequence[tuple[int, int, int]]], plane_tiles: Sequence[Sequence[Sequence[int]]], palette_ids: Sequence[Sequence[int]], palettes: Sequence[Sequence[Sequence[tuple[int, int, int]]]], width: int, height: int) -> Image.Image:
    result = Image.new("RGB", (width * 8, height * 8))
    pixels = result.load()
    for plane, encoded_tiles in enumerate(plane_tiles):
        for tile_index, tile in enumerate(encoded_tiles):
            palette = palettes[plane][palette_ids[plane][tile_index]]
            for offset, color_index in enumerate(tile):
                x = (tile_index % width) * 8 + offset % 8
                y = (tile_index // width) * 8 + offset // 8
                if plane == 0 or color_index != 0:
                    pixels[x, y] = palette[color_index]
    return result


def image_diff(original: Image.Image, reconstructed: Image.Image, background: tuple[int, int, int] = (0, 0, 0)) -> int:
    source = Image.new("RGBA", original.size, background + (255,))
    source.alpha_composite(original.convert("RGBA"))
    source = source.convert("RGB").resize(reconstructed.size, Image.Resampling.BILINEAR)
    return sum(color_distance(first, second) for first, second in zip(source.getdata(), reconstructed.getdata()))


def encode_hicolor(source: Image.Image, identifier: str, options: Options, progress: Callable[[str], None] | None = None) -> EncodedImage:
    image = prepare_hicolor_image(source, options)
    all_words: list[int] = []
    all_palette_words: list[int] = []
    all_index_words: list[int] = []
    text_parts: list[str] = []
    colors: set[int] = set()
    reconstructed = Image.new("RGB", image.size)
    name = identifier.upper()
    row_options = Options(output=options.output, planes=options.planes, contrast=options.contrast, balance=options.balance, palettes=8, force=True, background=options.background)
    for row in range(19):
        if progress:
            progress(f"Encoding HiColor row {row + 1}/19...")
        row_image = image.crop((0, row * 8, 160, (row + 1) * 8))
        tiles, _, _ = tile_pixels(row_image, options.background)
        flattened, plane_tiles, palette_ids, palettes = make_palettes(tiles, row_options)
        row_preview = reconstruction(tiles, plane_tiles, palette_ids, palettes, 20, 1)
        reconstructed.paste(row_preview, (0, row * 8))
        row_words = tile_words([tile for plane in plane_tiles for tile in plane])
        row_palette_words = [ngp_word(color) for palette in flattened for color in palette[:4] + [(0, 0, 0)] * (4 - len(palette))]
        all_words.extend(row_words)
        all_palette_words.extend(row_palette_words)
        if options.planes == 2:
            for plane in range(2):
                for tile_index, palette_index in enumerate(palette_ids[plane]):
                    all_index_words.append((palette_index << 9) + tile_index + 40 + row * 20)
        colors.update(row_palette_words)
        if options.output != "bin":
            pass
    if options.output == "bin":
        data = b"".join(u16(value) for value in all_words + all_palette_words + all_index_words)
    else:
        total_words = len(all_words) + len(all_palette_words) + len(all_index_words)
        tile_row_words = 20 * 8 * options.planes
        if options.output == "c":
            lines = packed_lines(all_words, options.output, tile_row_words, comments=True)
            lines[-1] += ","
            lines += packed_lines(all_palette_words, options.output, 20 * 8)
            lines[-1] += ","
            lines += packed_lines(all_index_words, options.output, 20 * 8)
            data = (f"const u16 {name}_ID[{total_words}] = {{\n" + "\n".join(lines) + "\n};\n").encode("ascii")
        else:
            id_lines = packed_lines(all_words, "asm", tile_row_words, comments=True)
            dyn_lines = packed_lines(all_palette_words, "asm", 20 * 8)
            idx_lines = packed_lines(all_index_words, "asm", 20 * 8)
            data = (f"{name}_ID\n" + "\n".join(id_lines) + f"\nEND_{name}_ID\n\n{name}_DYN\n" + "\n".join(dyn_lines) + f"\n{name}_IDX\n" + "\n".join(idx_lines) + f"\n{name}_DATA\n\tdd\t{name}_ID,{name}_DYN,{name}_IDX\n").encode("ascii")
    if progress:
        progress("Complete")
    return EncodedImage(data, reconstructed, 20, 19, 20 * 19 * options.planes, len(colors), len(colors), image_diff(image, reconstructed, options.background), 20 * 19 * options.planes)


def encode_image(source: Image.Image, identifier: str, options: Options, progress: Callable[[str], None] | None = None) -> EncodedImage:
    if options.hicolor:
        return encode_hicolor(source, identifier, options, progress)

    def report(message: str) -> None:
        if progress:
            progress(message)

    image = prepare_image(source, options)
    tiles, width, height = tile_pixels(image, options.background)
    logical_count = width * height * options.planes
    if logical_count > 512 and not options.force:
        raise ValueError(f"Image is too large: {width} x {height} x {options.planes} tiles exceeds 512")
    report("Analysing palettes...")
    flattened, plane_tiles, palette_ids, palettes = make_palettes(tiles, options)
    fuzz = options.merge if options.reduce_tiles else 0
    unique_tiles: list[list[int]] = [[0] * 64] if options.reduce_tiles and not options.flip_tiles else []
    tile_lookup: dict[tuple[int, ...], int] = {tile_key(tile, fuzz): index for index, tile in enumerate(unique_tiles)}
    tile_indices: list[list[int]] = [[] for _ in plane_tiles]
    flip_codes: list[list[int]] = [[] for _ in plane_tiles]
    for plane, plane_data in enumerate(plane_tiles):
        for tile in plane_data:
            variants = [tile]
            if options.flip_tiles:
                variants += [flipped_tile(tile, True, False), flipped_tile(tile, False, True), flipped_tile(tile, True, True)]
            key = tile_key(tile, fuzz)
            if key not in tile_lookup:
                base = len(unique_tiles)
                unique_tiles.extend(variants)
                for offset, variant in enumerate(variants):
                    tile_lookup.setdefault(tile_key(variant, fuzz), base + offset)
            index = tile_lookup[key]
            tile_indices[plane].append(index // 4 if options.flip_tiles else index)
            flip_codes[plane].append((index % 4) << 14 if options.flip_tiles else 0)
    if options.reduce_tiles:
        stored_tiles = unique_tiles[::4] if options.flip_tiles else unique_tiles
    else:
        stored_tiles = [tile for plane in plane_tiles for tile in plane]
    words: list[int] = []
    for tile in stored_tiles:
        for row in range(8):
            value = 0
            for column in range(8):
                value = (value << 2) | tile[row * 8 + column]
            words.append(value)
    palette_words = [ngp_word(color) for palette in flattened for color in palette[:4] + [(0, 0, 0)] * (4 - len(palette))]
    name = identifier.upper()
    if options.output == "bin":
        data = b"".join(u16(value) for value in words + palette_words)
    else:
        prefix = (f"#include \"img.h\"\n\n#define {name}_WIDTH {width}\n#define {name}_HEIGHT {height}\n#define {name}_TILES_COUNT {stored_tiles.__len__() if options.reduce_tiles else width * height * options.planes}\n\n#define {name}_NPALS1 {len(palettes[0])}\n" if options.output == "c" else f"{name}_WIDTH\tEQU\t{width}\n{name}_HEIGHT\tEQU\t{height}\n{name}_TILES_COUNT\tEQU\t{stored_tiles.__len__() if options.reduce_tiles else width * height * options.planes}\n\n{name}_NPALS1\tEQU\t{len(palettes[0])}\n")
        if options.planes == 2:
            prefix += (f"#define {name}_NPALS2 {len(palettes[1])}\n\n" if options.output == "c" else f"{name}_NPALS2\tEQU\t{len(palettes[1])}\n\n")
        parts = [prefix]
        if options.reduce_tiles:
            parts.append(output_text(words, options.output, f"{name}_TILES", "u16", len(words), 8))
        else:
            plane_words = [tile_words(plane) for plane in plane_tiles]
            for plane, values in enumerate(plane_words):
                parts.append(output_text(values, options.output, f"{name}_TILES{plane + 1}", "u16", len(values), 8))
        for plane, palette_set in enumerate(palettes):
            values = [ngp_word(color) for palette in palette_set for color in palette[:4] + [(0, 0, 0)] * (4 - len(palette))]
            parts.append(output_text(values, options.output, f"{name}_PALS{plane + 1}", "u16", len(values), 4))
        for plane, indices in enumerate(palette_ids):
            if len(palettes[plane]) > 1:
                parts.append(output_text(indices, options.output, f"{name}_PALIDX{plane + 1}", "u8", len(indices), width))
        if options.reduce_tiles:
            for plane, indices in enumerate(tile_indices):
                parts.append(output_text(indices, options.output, f"{name}_IDX{plane + 1}", "u16", len(indices), width))
                if options.flip_tiles:
                    parts.append(output_text(flip_codes[plane], options.output, f"{name}_FLIP{plane + 1}", "u16", len(indices), width))
        data = "\n".join(parts).encode("ascii")
    preview = reconstruction(tiles, plane_tiles, palette_ids, palettes, width, height)
    report("Complete")
    output_colors = len({ngp_word(color) for plane in palettes for palette in plane for color in palette})
    return EncodedImage(data, preview, width, height, logical_count, sum(len(palette) for palette in palettes), output_colors, image_diff(image, preview, options.background), len(stored_tiles))


class CodeImageWindow:
    def __init__(self) -> None:
        from PyQt6.QtCore import Qt
        from PyQt6.QtGui import QImage, QPixmap
        from PyQt6.QtWidgets import (QCheckBox, QComboBox, QFileDialog, QFormLayout, QGroupBox, QHBoxLayout, QLabel, QMainWindow, QMessageBox, QPushButton, QSlider, QSpinBox, QSplitter, QStatusBar, QVBoxLayout, QWidget)
        self.QImage, self.QPixmap, self.Qt = QImage, QPixmap, Qt
        self.QFileDialog, self.QMessageBox = QFileDialog, QMessageBox
        self.window = QMainWindow()
        self.window.setWindowTitle("CodeImage | NGPC tile converter")
        self.window.resize(1120, 720)
        self.source: Image.Image | None = None
        self.encoded: EncodedImage | None = None
        root = QWidget()
        main = QVBoxLayout(root)
        toolbar = QHBoxLayout()
        self.load_button = QPushButton("Open image")
        self.go_button = QPushButton("Encode")
        self.save_button = QPushButton("Save output")
        self.save_button.setEnabled(False)
        self.native_preview = QCheckBox("1:1 preview")
        toolbar.addWidget(self.load_button)
        toolbar.addWidget(self.go_button)
        toolbar.addWidget(self.save_button)
        toolbar.addWidget(self.native_preview)
        toolbar.addStretch()
        main.addLayout(toolbar)
        splitter = QSplitter(Qt.Orientation.Horizontal)
        controls = QGroupBox("Encoding")
        form = QFormLayout(controls)
        self.output = QComboBox(); self.output.addItems(["C header", "Assembly"])
        self.planes = QComboBox(); self.planes.addItems(["1", "2"]); self.planes.setCurrentIndex(1)
        self.contrast = QSpinBox(); self.contrast.setRange(-2, 9); self.contrast.setValue(1)
        self.balance = QComboBox(); self.balance.addItems(["Color - middle", "Color - dark", "Color - light", "Weight - middle", "Weight - dark", "Weight - light"])
        self.palettes = QSpinBox(); self.palettes.setRange(1, 16); self.palettes.setValue(16)
        form.addRow("Output", self.output); form.addRow("Planes", self.planes); form.addRow("Contrast", self.contrast); form.addRow("Balance", self.balance); form.addRow("Palettes", self.palettes)
        background_controls = QHBoxLayout()
        self.background = []
        for channel in range(3):
            control = QSpinBox(); control.setRange(0, 255); control.setValue(0); control.setPrefix(("R " , "G ", "B ")[channel]); control.valueChanged.connect(self.update_background_swatch)
            self.background.append(control)
            background_controls.addWidget(control)
        self.background_swatch = QLabel("   "); self.background_swatch.setMinimumWidth(42); background_controls.addWidget(self.background_swatch)
        form.addRow("Background", background_controls)
        self.force = QCheckBox("Allow more than 512 tiles")
        self.resize = QCheckBox("Resize to NGPC screen")
        self.reduce = QCheckBox("Reduce duplicate tiles")
        self.flip = QCheckBox("Store horizontal/vertical flips")
        self.dither = QCheckBox("Apply Bayer dithering")
        self.hicolor = QCheckBox("HiColor (20 x 19 tile rows)")
        self.max_size = QCheckBox("HiColor: fill the full screen")
        for control in (self.hicolor, self.max_size, self.force, self.resize, self.reduce, self.flip, self.dither):
            form.addRow(control)
        self.merge = QSlider(Qt.Orientation.Horizontal); self.merge.setRange(0, 8); self.merge.setValue(0); self.merge.setTickPosition(QSlider.TickPosition.TicksBelow)
        form.addRow("Merge tolerance", self.merge)
        preview_box = QWidget(); preview_layout = QVBoxLayout(preview_box)
        self.original_label = QLabel("Open an image to begin"); self.original_label.setAlignment(Qt.AlignmentFlag.AlignCenter); self.original_label.setMinimumSize(520, 420); self.original_label.setStyleSheet("background: #17202a; color: #bdc7d1;")
        self.result_label = QLabel("Encoded preview"); self.result_label.setAlignment(Qt.AlignmentFlag.AlignCenter); self.result_label.setMinimumSize(520, 420); self.result_label.setStyleSheet("background: #0b1014; color: #bdc7d1;")
        preview_layout.addWidget(self.original_label); preview_layout.addWidget(self.result_label)
        splitter.addWidget(controls); splitter.addWidget(preview_box); splitter.setSizes([320, 760]); main.addWidget(splitter)
        self.details = QLabel("Ready")
        main.addWidget(self.details)
        self.window.setCentralWidget(root); self.window.setStatusBar(QStatusBar())
        self.load_button.clicked.connect(self.load_image); self.go_button.clicked.connect(self.encode); self.save_button.clicked.connect(self.save_output); self.native_preview.toggled.connect(self.refresh_previews)
        self.update_background_swatch()

    def show(self) -> None:
        self.window.show()

    def pixmap_for(self, image: Image.Image, label: object) -> object:
        image = image.convert("RGBA")
        raw = image.tobytes("raw", "RGBA")
        qimage = self.QImage(raw, image.width, image.height, self.QImage.Format.Format_RGBA8888)
        pixmap = self.QPixmap.fromImage(qimage.copy())
        if self.native_preview.isChecked():
            return pixmap
        return pixmap.scaled(label.size(), self.Qt.AspectRatioMode.KeepAspectRatio, self.Qt.TransformationMode.SmoothTransformation)

    def refresh_previews(self) -> None:
        if self.source is not None:
            self.original_label.setPixmap(self.pixmap_for(self.source, self.original_label))
        if self.encoded is not None:
            self.result_label.setPixmap(self.pixmap_for(self.encoded.preview, self.result_label))

    def load_image(self) -> None:
        path, _ = self.QFileDialog.getOpenFileName(self.window, "Open image", "", "Images (*.png *.jpg *.jpeg *.gif *.bmp *.webp)")
        if not path:
            return
        try:
            self.source = Image.open(path).convert("RGBA")
            self.original_label.setText(""); self.original_label.setPixmap(self.pixmap_for(self.source, self.original_label))
            self.details.setText(f"Loaded {Path(path).name} | {self.source.width} x {self.source.height}")
        except Exception as error:
            self.QMessageBox.critical(self.window, "Could not open image", str(error))

    def update_background_swatch(self) -> None:
        red, green, blue = (control.value() for control in self.background)
        self.background_swatch.setStyleSheet(f"background-color: rgb({red}, {green}, {blue}); border: 1px solid #718096;")

    def current_options(self) -> Options:
        balance = ["color-middle", "color-dark", "color-light", "weight-middle", "weight-dark", "weight-light"][self.balance.currentIndex()]
        background = tuple(control.value() for control in self.background)
        return Options(output="c" if self.output.currentIndex() == 0 else "asm", planes=self.planes.currentIndex() + 1, contrast=self.contrast.value(), balance=balance, palettes=self.palettes.value(), force=self.force.isChecked(), reduce_tiles=self.reduce.isChecked(), flip_tiles=self.flip.isChecked(), resize=self.resize.isChecked(), dither=self.dither.isChecked(), merge=self.merge.value(), background=background, hicolor=self.hicolor.isChecked(), max_size=self.max_size.isChecked())

    def encode(self) -> None:
        if self.source is None:
            self.QMessageBox.information(self.window, "Open an image", "Choose an image before encoding."); return
        try:
            self.encoded = encode_image(self.source, "image", self.current_options(), lambda message: self.details.setText(message))
            self.result_label.setText(""); self.result_label.setPixmap(self.pixmap_for(self.encoded.preview, self.result_label)); self.save_button.setEnabled(True)
            self.details.setText(f"Tiles: {self.encoded.tile_count:,} logical / {self.encoded.stored_tile_count:,} stored | Colors: {self.encoded.colors:,} | Diff: {self.encoded.diff:,} | {len(self.encoded.data):,} bytes")
        except Exception as error:
            self.QMessageBox.critical(self.window, "Encoding failed", str(error))

    def save_output(self) -> None:
        if self.encoded is None:
            return
        suffix = {"c": ".h", "asm": ".inc"}[self.current_options().output]
        path, _ = self.QFileDialog.getSaveFileName(self.window, "Save output", f"image{suffix}", "All files (*)")
        if path:
            Path(path).write_bytes(self.encoded.data)
            self.details.setText(f"Saved {Path(path).name}")


def main() -> int:
    parser = argparse.ArgumentParser(description="PyQt6 NGPC image/tile converter")
    parser.add_argument("image", nargs="?", help="image to convert; omit to open the GUI")
    parser.add_argument("-o", "--output", choices=("c", "asm"), default="c")
    parser.add_argument("--planes", type=int, choices=(1, 2), default=2)
    parser.add_argument("--resize", action="store_true")
    parser.add_argument("--dither", action="store_true")
    parser.add_argument("--reduce", action="store_true")
    parser.add_argument("--flip", action="store_true")
    parser.add_argument("--force", action="store_true")
    parser.add_argument("--hicolor", action="store_true", help="encode fixed 160 x 152 HiColor rows")
    parser.add_argument("--max-size", action="store_true", help="fill the whole HiColor screen when scaling")
    parser.add_argument("-O", "--out-file")
    args = parser.parse_args()
    if args.image:
        source = Image.open(args.image).convert("RGBA")
        encoded = encode_image(source, Path(args.image).stem, Options(output=args.output, planes=args.planes, resize=args.resize, dither=args.dither, reduce_tiles=args.reduce, flip_tiles=args.flip, force=args.force, hicolor=args.hicolor, max_size=args.max_size))
        Path(args.out_file or f"{Path(args.image).stem}{'.inc' if args.output == 'asm' else '.h'}").write_bytes(encoded.data)
        return 0
    # Avoid a broken GTK3 theme plugin on systems where Qt is launched from Snap.
    os.environ.setdefault("QT_QPA_PLATFORMTHEME", "generic")
    from PyQt6.QtWidgets import QApplication
    app = QApplication(sys.argv)
    window = CodeImageWindow(); window.show()
    return app.exec()


if __name__ == "__main__":
    raise SystemExit(main())
