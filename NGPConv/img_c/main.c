#include "ngpc.h"
#include "carthdr.h"
#include "library.h"

#include "skull.hh"
#include "jv0.hh"
#include "jv1.hh"
#include "jv2.hh"
#include "ranma.hh"
#include "goku.hh"
#include "gotrunk.hh"
#include "kof2k1.hh"
#include "gekka.hh"
#include "gk1.hh"
#include "gk2.hh"
#include "gk3.hh"
#include "gk4.hh"
#include "gk5.hh"
#include "gk6.hh"
#include "gk7.hh"
#include "gk8.hh"
#include "gk9.hh"
#include "gka.hh"
#include "gkb.hh"
#include "gkc.hh"
#include "hinako.hh"
#include "hotaru.hh"
#include "leona.hh"
#include "scene.hh"
#include "ff0.hh"
#include "ff1.hh"
#include "chess.hh"

#define NB_IMAGES 28

const SOD_IMG images[NB_IMAGES] = {
	SKULL_ID,
	JV0_ID,
	FF1_ID,
	FF0_ID,
	RANMA_ID,
	GOKU_ID,
	GOTRUNK_ID,
	KOF2K1_ID,
	GEKKA_ID,
	JV1_ID,
	JV2_ID,
	GK1_ID,
	GK2_ID,
	GK3_ID,
	GK4_ID,
	GK5_ID,
	GK6_ID,
	GK7_ID,
	GK8_ID,
	GK9_ID,
	GKA_ID,
	GKB_ID,
	GKC_ID,
	HINAKO_ID,
	HOTARU_ID,
	LEONA_ID,
	CHESS_ID,
	SCENE_ID
};

void main()
{
	u8 i,j;

	InitNGPC();

	ClearScreen(SCR_1_PLANE);
	ClearScreen(SCR_2_PLANE);
	SetBackgroundColour(RGB(0, 0, 0));

	i=0;
	while (1)
	{
		showImage(0,(SOD_IMG*)&images[i],CENTER,CENTER);
        	while((((j=JOYPAD)&J_A) == 0) && ((j&J_B) == 0));
        	while((((JOYPAD)&J_A) != 0) || (((JOYPAD)&J_B) != 0));
		i = j&J_B? (i>0?i-1:NB_IMAGES-1) : (i+1)%NB_IMAGES;
	}
}
