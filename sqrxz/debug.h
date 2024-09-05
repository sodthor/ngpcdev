#ifndef _DEBUG_H
#define _DEBUG_H

#ifdef DEBUG

#define LOGGER(s)\
    {\
        FILE *log = fopen("debug.log","a");\
        fprintf(log,"%s\n",s);\
        fclose(log);\
    }

#else
	
#define LOGGER(S)

#endif

#endif