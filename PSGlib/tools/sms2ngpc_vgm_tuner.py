#coded by winteriscoming
import sys
import os
import gzip

print ("started sms to ngpc vgm tuner script...")

filename=sys.argv[1]
basename=os.path.basename(filename)

stripheader=int(sys.argv[2])
print ("input file name is: " + filename)
outfile = basename[0:len(basename)-4]+"_tuned.vgm"

outfile_c = basename[0:len(basename)-4]+"_tuned.c"

c_name = basename[0:len(basename)-4]+"_tuned"

with open(filename,'rb') as f:
    readdata = f.read()

if readdata.startswith(b'\x1f\x8b'):
    print("Decompressing gzipped VGM...")
    readdata = gzip.decompress(readdata)

newdata=bytearray()

totalwritecmds=0
totalfirsttransfercmds=0
totalfrequencycmds=0
totalfrequencycmdsfornoise=0
totalfrequencycmdsneedingtuning=0
total2ndtransfer=0
totalvolumecmds=0

frequencymultiplier=8575/10000

SMS_input_clock=3580000
NGPC_input_clock=3070000

poscheck=0

periodicnoise=0

curChan=0

chanMask=0x60
volMask=0xF

loop_offset=readdata[0x1c]
loop_offset+=readdata[0x1c+1]*0x100
loop_offset+=readdata[0x1c+2]*0x10000
loop_offset+=readdata[0x1c+3]*0x1000000
loop_offset+=0x1c

data_offset = 0
if len(readdata) >= 0x38:
    data_offset_raw = readdata[0x34] | (readdata[0x35] << 8) | (readdata[0x36] << 16) | (readdata[0x37] << 24)
    if data_offset_raw != 0 and data_offset_raw != 0xFFFFFFFF:
        data_offset = data_offset_raw

header_size = 0x40 if data_offset == 0 else 0x34 + data_offset

loop_offset_with_header= loop_offset
loop_offset_without_header=loop_offset-header_size

i=0
if not (stripheader):
    print ("Header will not be stripped")
    while i<header_size:
        newdata.append(readdata[i])
        i+=1
else:
    loop_offset-=header_size
    print ("Header will be stripped")
    i=header_size

while i < len(readdata):
    cmd = int(readdata[i])

    if cmd==0x50:
        totalwritecmds=totalwritecmds+1
        writedata=readdata[i+1]
        
        curChan=chanMask&writedata
        curChan=curChan>>5
        curChan+=1
        
        if writedata&0x80:
            #this is a first transfer
            totalfirsttransfercmds=totalfirsttransfercmds+1
            if not (writedata&0x10):
                #this is a cmd for frequency
                totalfrequencycmds+=1
                if not (writedata&0x60==0x60):
                    #this is a cmd for channels 1-3 so it needs a 2nd data byte and needs tuning
                    cmdbits=readdata[i+1]&0xF0
                    databits=readdata[i+1]&0x0F
                    
                    highdatabyte=readdata[i+3]&0x3F
                    
                    fullwritedata=databits+(highdatabyte*0x10)
                    
                    #if it is a command for channel 3 and a periodic noise command on channel 4 preceded it
                    #then it needs to be tuned differently
                    if (curChan==3 and periodicnoise):
                        periodicnoise=0
                        convertedwritedata=int(round(  NGPC_input_clock/(((SMS_input_clock/(fullwritedata*32))/16)*15)/32    ))
                    else:
                        convertedwritedata=int(round(fullwritedata*frequencymultiplier))
                    
                    convertedhighbyte=convertedwritedata>>4
                    convertedlowbyte=convertedwritedata&0xF

                    frequencynew=convertedwritedata
                    highbyte=convertedhighbyte
                    
                    lowbyte=convertedlowbyte+cmdbits
                    
                    newdata.append(readdata[i])
                    newdata.append(lowbyte)
                    newdata.append(readdata[i+2])
                    newdata.append(highbyte)
                    
                    i+=4
                    
                    totalfrequencycmdsneedingtuning+=1
                else:
                    #this is a cmd for channel 4 (noise) and does not have a 2nd data byte
                    if readdata[i+1] & 0x3 == 0x3:
                        periodicnoise=1
                    else:
                        periodicnoise=0
                        
                    totalfrequencycmdsfornoise+=1
                    newdata.append(readdata[i])
                    newdata.append(readdata[i+1])
                    i+=2
            else:
                #this is a cmd for volume and does not need a 2nd data byte
                newdata.append(readdata[i])
                newdata.append(readdata[i+1])
                i+=2
                totalvolumecmds+=1
        else:
            #this is a 2nd transfer that should not happen - it means it was not counted as part of a first transfer in the tuning process
            newdata.append(readdata[i])
            newdata.append(readdata[i+1])
            i=i+2
            total2ndtransfer+=1
        
    else:
        #this is some other kind of command
        newdata.append(readdata[i])
        
        if cmd == 0x66:
            # End of file
            i += 1
            break
        elif cmd == 0x61:
            # Pause command
            newdata.append(readdata[i+1])
            newdata.append(readdata[i+2])
            i += 3
        elif cmd == 0x62 or cmd == 0x63 or (cmd >= 0x70 and cmd <= 0x8F):
            # 0 data bytes
            i += 1
        elif (cmd >= 0x30 and cmd <= 0x4F):
            # 1 data byte
            newdata.append(readdata[i+1])
            i += 2
        elif (cmd >= 0x51 and cmd <= 0x5F) or cmd == 0xA0 or cmd == 0xB0:
            # 2 data bytes
            newdata.append(readdata[i+1])
            newdata.append(readdata[i+2])
            i += 3
        elif (cmd >= 0xC0 and cmd <= 0xDF) or cmd == 0x64:
            # 3 data bytes
            newdata.append(readdata[i+1])
            newdata.append(readdata[i+2])
            newdata.append(readdata[i+3])
            i += 4
        elif (cmd >= 0xE0 and cmd <= 0xFF):
            # 4 data bytes
            newdata.append(readdata[i+1])
            newdata.append(readdata[i+2])
            newdata.append(readdata[i+3])
            newdata.append(readdata[i+4])
            i += 5
        elif cmd == 0x67:
            # Data block
            newdata.append(readdata[i+1])
            newdata.append(readdata[i+2])
            size = readdata[i+3] | (readdata[i+4] << 8) | (readdata[i+5] << 16) | (readdata[i+6] << 24)
            newdata.append(readdata[i+3])
            newdata.append(readdata[i+4])
            newdata.append(readdata[i+5])
            newdata.append(readdata[i+6])
            for j in range(size):
                newdata.append(readdata[i+7+j])
            i += 7 + size
        else:
            print("Warning: Unknown command", hex(cmd))
            i += 1

print (hex(i) + " total bytes present in vgm file")
print (str(totalwritecmds) + " total write commands present in vgm file")
print (str(totalfirsttransfercmds) + " total first transfers present in vgm file")
print (str(totalvolumecmds) + " total colume cmds present in vgm file")
print (str(totalfrequencycmds) + " total frequency and noise cmds present in vgm file")
print (str(totalfrequencycmdsneedingtuning) + " total frequency cmds for channels 1-3 needing tuning in vgm file")
print (str(totalfrequencycmdsfornoise) + " total cmds for noise in vgm file")
print (str(total2ndtransfer) + " unaccounted 2nd transfers in vgm file")
print ("Loop offset: with header: " + hex(loop_offset_with_header))
print ("Loop offset without header: " + hex(loop_offset_without_header))

f = open(outfile,'w+b')

for b in newdata:
    f.write(bytes([b]))
f.close()

f = open(outfile_c,'w')

f.write("//This is a SMS VGM file tuned to NGPC in c format\n")
if (stripheader):
    f.write("//The VGM header and footer were stripped.\n")
f.write("#define " + c_name + "_loop_point " + '0x{0:0{1}X}'.format(loop_offset,8) +"\n")
f.write("const unsigned char " + c_name + "[] = {\n")
i=0
for b in newdata:
    f.write('0x{0:0{1}X}'.format(b,2) + ",")
    i+=1
    if (i>=16):
       i=0
       f.write("\n")
    
f.write("};\n")
f.close()
