#!/usr/bin/bash
rm *_tuned*
for file in *.vgm; do
    [ -e "$file" ] || continue  # Skip if no .vgm files exist
    echo "Processing $file"
    python3 sms2ngpc_vgm_tuner.py "${file}" 0
    ./vgm2psg "${file%.vgm}_tuned.vgm" "${file%.vgm}.psg"
done
java -cp . Compress3
