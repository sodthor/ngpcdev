#ifndef _WIN32_H
#define _WIN32_H

#include <SDL.h>
#include <SDL_types.h>

#include "debug.h"

void initVideo();
void flipScreen();
void checkInput();

extern FILE _iob[3];
extern int done;

#endif // _WIN32_H