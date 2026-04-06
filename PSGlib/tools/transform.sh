#!/usr/bin/bash
for file in *.vgm; do
    [ -e "$file" ] || continue  # Skip if no .vgm files exist
    echo "Processing $file"
    python3 sms2ngpc_vgm_tuner.py "${file}" 0
    ./vgm2psg "${file%.vgm}_tuned.vgm" "${file%.vgm}.psg"
	./psgcomp_ng "${file%.vgm}.psg" "${file%.vgm}.psg"
	java -cp . Bin2C "${file%.vgm}.psg"
done