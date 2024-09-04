vgm2psg %1.vgm %1.psg
psgcomp %1.psg %1.psg
\dev\pspsdk\bin\bin2c.exe %1.psg ..\ngpc\%1.h %1_PSG
