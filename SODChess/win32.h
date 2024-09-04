#include <stdio.h>
#include <stdlib.h>
#include <time.h>

#define s8 char
#define u8 unsigned char
#define s16 short
#define u16 unsigned short
#define u32 unsigned int

#ifdef TEST
#define INITLOG {FILE *f = fopen("tmp.log","w");fclose(f);}
#define LOG(s) {FILE *f = fopen("tmp.log","a");fprintf(f,"%s\n",s);fclose(f);}
#else
#define INITLOG {}
#define LOG(s) {}
#endif

#ifndef SODCHESS_MAIN

extern s16 _log;
extern s8 line[],nbl;
extern void dumpLine();
extern void logLine();

#endif
