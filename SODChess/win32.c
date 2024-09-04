#define SODCHESS_MAIN
#include "win32.h"

#include "SODChess.h"

struct _board _curboard;
Board curboard = &_curboard;

s16 _log;
s8 line[DEPTH*2],nbl;
char *display = "PPPPPPPPRNBQKBNRpppppppprnbqkbnr";
s8 xboard;

void dump(Board b)
{
	s16 i,j,k=56;
	char bb[128];
	for (i=0;i<8;i++)
	{
		*bb=0;
		for (j=0;j<8;j++)
		{
			s8 p = b->board[k+j];
			sprintf(bb,"%s%c",bb,p==DEAD?' ':((b->c.pinned & (1<<p)) ? '*' : display[p]));
		}
		k-=8;
		if (xboard)
			LOG(bb)
		else
			printf("%s\n",bb);
	}
	sprintf(bb,"%d %08x\n",eval(b,WHITE),b->castle);
	if (xboard)
		LOG(bb)
	else
		printf("%s",bb);
}

void dumpControl(Board b,s8 wmin,s8 wmax,s8 bmin,s8 bmax)
{
	s16 i,j,k=56;
	s8 bb[128],cc,*c;
	char dd[128];
	for (i=0;i<128;bb[i++]='+');
	for (i=wmin;i<=wmax;i++)
	{
		if (b->pieces[i]==DEAD)
			continue;
		cc = b->c.buffer[COUNT_IDX+i];
		c = &b->c.buffer[control_idx[i]];
		for (j=0;j<cc;j++)
			bb[c[j]]='X';
	}
	for (i=bmin;i<=bmax;i++)
	{
		if (b->pieces[i]==DEAD)
			continue;
		cc = b->c.buffer[COUNT_IDX+i];
		c = &b->c.buffer[control_idx[i]];
		for (j=0;j<cc;j++)
			bb[c[j]+64]='X';
	}
	for (i=0;i<8;i++)
	{
		sprintf(dd,"%d ",8-i);
		for (j=0;j<8;j++)
			sprintf(dd,"%s%c",dd,bb[k+j]);
		sprintf(dd,"%s   %d ",dd,8-i);
		for (j=0;j<8;j++)
			sprintf(dd,"%s%c",dd,bb[k+j+64]);
		k-=8;
		if (xboard)
			LOG(dd)
		else
			printf("%s\n",dd);
	}
	sprintf(dd,"\n  ");
	for (i=0;i<8;i++)
		sprintf(dd,"%s%c",dd,'a'+i);
	sprintf(dd,"%s     ",dd);
	for (i=0;i<8;i++)
		sprintf(dd,"%s%c",dd,'a'+i);
	if (xboard)
		LOG(dd)
	else
		printf("%s\n",dd);
}

void playMove(char* move)
{
	play((s8)POS(move[1]-'1',move[0]-'a'),(s8)POS(move[3]-'1',move[2]-'a'),curboard,1);
}

void dumpLine()
{
	s8 i;
	for (i=0;i<nbl;i+=2)
		printf("%c%d%c%d ",COL(line[i])+'a',ROW(line[i])+1,COL(line[i+1])+'a',ROW(line[i+1])+1);
}

void logLine()
{
		char buf[256];
		s8 i;
		if (nbl==0)
			return;
		buf[0]=0;
		for (i=0;i<nbl;i+=2)
			sprintf(buf,"%s%c%d%c%d ",buf,COL(line[i])+'a',ROW(line[i])+1,COL(line[i+1])+'a',ROW(line[i+1])+1);
		LOG(buf);
}

extern u32 nbn;

void dos_mode()
{
	char buf[5],cmd=0,_cont=1;
	xboard = 0;
	dump(curboard);
	while (1)
	{
		do
		{
			scanf("%s",buf);
			cmd=0;
			if (strcmp(buf,"log")==0)
			{
				_log = 1-_log;
				cmd = 1;
			}
			else if (strcmp(buf,"exit")==0)
				_cont = 0;
		} while (cmd&&_cont);
		if (!_cont)
			break;
		playMove(buf);
		dump(curboard);
		compute(BLACK,DEPTH-1,curboard,-20000,20000);
		if (cursrc==DEAD) break;
		play(cursrc,curdst,curboard,1);
		dump(curboard);
//		dumpControl(curboard,0,15,16,31);
		dumpControl(curboard,0,0,QUEEN+BLACK,QUEEN+BLACK);
		printf("%d\n",nbn);
	}
}

char buf[32][128];
s16 nb_in;

void read_input()
{
	do
	{
		fgets(buf[nb_in++],128,stdin);
		if (!strcmp(buf[nb_in-1],"quit"))
			exit(0);
LOG("get")
LOG(buf[nb_in-1])
	}
	while (stdin->_cnt>0);
}

void remove_cmd(s16 i)
{
	s16 j,k=0;
	for (j=i;j<nb_in;j++)
		strcpy(buf[k++],buf[j]);
	nb_in = k;
}

s16 wait_cmd(char *cmd,char flush)
{
	s16 i,first=1;
	while(1)
	{
		for (i=0;i<nb_in&&strncmp(buf[i],cmd,strlen(cmd));i++);
		if (i<nb_in)
			break;
		read_input();
	}
	if (flush)
		remove_cmd(i);
	return flush?0:i;
}

void checkhand()
{
	nb_in = 0;
	xboard = 1;
	wait_cmd("xboard",1);
	// Send settings
	printf("tellicsnoalias set 1 SODCHESS\n");
	printf("tellicsnoalias kibitz Hello from SODCHESS\n");
	printf("feature ping=1 setboard=0 san=0 time=1 draw=0\n");
	printf("feature sigs16=0 sigterm=0 reuse=0 analyze=0\n");
	printf("feature myname=\"SODChess\" name=1\n");
	printf("feature playother=1 colors=0\n");
	printf("feature variants=\"normal,nocastle\"\n");
	printf("feature done=1\n");
	fflush(stdout);
	// Read answer
	wait_cmd("ping",1);
	// Init
	printf("tellicsnoalias set 1 SODChess\n");
 	printf(" game/5 minutes primary time control\n");
	printf("pondering enabled.\n");
	printf("pong 1\n");
	fflush(stdout);
	// Wait time & first move
	wait_cmd("otim",1);
	// Send hello
	printf("SODChess vs \n");
	printf("tellicsnoalias kibitz Hello from SODCHESS\n");
	fflush(stdout);
}

void sendmove(char *s)
{
	printf("move %s\n\n",s);
	fflush(stdout);
}

void progress(s8 max,s8 cur)
{
}

void userAction()
{
	if (stdin->_cnt>0)
		read_input();
}

void xboard_mode()
{
	char mv[8];
	checkhand();
LOG("begin")
	remove_cmd(1);
	do
	{
		while (nb_in<1)
			read_input();
		playMove(buf[0]);
		nbmoves++;
LOG("white")
LOG(buf[1])
		compute(BLACK,DEPTH-1,curboard,-20000,20000);
		if (cursrc==DEAD)
			break;
		play(cursrc,curdst,curboard,1);
		nbmoves++;
		sprintf(mv,"%c%d%c%d",COL(cursrc)+'a',ROW(cursrc)+1,COL(curdst)+'a',ROW(curdst)+1);
LOG("black")
LOG(mv)
		sendmove(mv);
//exit(0);
dump(curboard);
dumpControl(curboard,0,15,16,31);
		wait_cmd("otim",1);
		remove_cmd(1);
		
	} while(1);
LOG("end")
	wait_cmd("quit",1);
}

s16 main(s16 argc, char* argv[])
{
	INITLOG
	initrand();
	initSearch();
	init(curboard);
	if (argc==1 || strcmp(argv[1],"xboard"))
		dos_mode();
	else
		xboard_mode();
	return 0;
}
