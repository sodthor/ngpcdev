#include "ngpc.h"
#include "carthdr.h"
#include "library.h"
#include "flash.h"
#include "music.h"

#include "gamedata.h"

extern void gems_main();
extern void dynamate_main();
extern void vexed_main();
extern void __interrupt VBInterrupt(void);

GAME_DATA DATA;

void removeVBL()
{
    __asm(" di");
    VBL_INT = VBInterrupt;
    __asm(" ei");
}

void getData()
{
    u8 i;
    getSavedData();
    if (data[GEMS_SAVED_OFFSET]>0)
        return;
    for (i=0;i<10;i++)
    {
        data[GEMS_SAVED_OFFSET+i*6]='C';
        data[GEMS_SAVED_OFFSET+i*6+1]='O';
        data[GEMS_SAVED_OFFSET+i*6+2]='M';
        data[GEMS_SAVED_OFFSET+i*6+3]=0;
        *((u16*)&data[GEMS_SAVED_OFFSET+i*6+4]) = 900-(((u16)(i<5?i:i-5))*100);
    }
}

extern void menu_showFreePlayTech();
extern u8 menu_mainMenu();

void main()
{
    InitNGPC();
    ClearAll();
    SetBackgroundColour(RGB(0, 0, 0));

    hc_detect();

    getData();
    SL_SoundInit();

    menu_showFreePlayTech();

    while (1)
    {
        memset(&DATA, 0, sizeof(GAME_DATA));
        switch(menu_mainMenu())
        {
            case 0: vexed_main(); break;
            case 1: gems_main(); break;
            case 2: dynamate_main(); break;
        }
        removeVBL();
    }
}
