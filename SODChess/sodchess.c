#include "SODChess.h"
#include "moves.h"

/**************************************
 *
 */

u8 blackattack[64];
u8 whiteattack[64];

const s8 imoves[] = {0,1,2,5,3,4,6,3,5,2};

void computeControls(Board b)
{
	s8 *c,color,nn,cc,x,y,n,p,i;
	u8 colcheck;
	u16 offset,len,end;
	u8 *attack;

	for (n=0;n<32;n++)
	{
		whiteattack[n]=0;
		blackattack[n]=0;
	}

	b->check = 0;
	for (n=0;n<32;n++)
	{
		if ((p = b->pieces[n])==DEAD)
			continue;
		c = &b->c.buffer[control_idx[n]];
		cc = 0;
		color = COLOR(n);
		if ((nn= n-color)>7)
			nn = imoves[nn-6];
		else if (nn<8)
			nn = imoves[color==BLACK?1:0];
		colcheck = (color==BLACK?1:2);
		attack = (color==BLACK?blackattack:whiteattack);

		for (i=0;i<8;i++)
		{
			if ((len = moves[nn][p][i][1])==0)
				break;
			offset = moves[nn][p][i][0];
			end = offset+len;
			x = DEAD;
			while (x==DEAD && offset<end)
			{
				y = c[cc++] = moves_dst[offset++];
				x = b->board[y];
				attack[y] = 1;
			}
			if (x!=DEAD && COLOR(x)!=color)
			{
				if (TYPE(x)==KING)
					b->check |= colcheck;
				else
				{
					y = DEAD;
					while (y==DEAD && offset<end)
						y = b->board[moves_dst[offset++]];
					if (y!=DEAD && TYPE(y)==KING && COLOR(y)!=color) // pinned ?
						b->c.pinned |= (1<<x);
				}
			}
		}
		b->c.buffer[COUNT_IDX+n] = cc;
	}
}

/**************************************
 *
 */

void init(Board b)
{
	s16 i;
	nbmoves = 0;
	for (i=0;i<64;b->board[i++]=DEAD);
	for (i=0;i<8;i++)
	{
		b->pieces[i+BLACK]=POS(6,i);
		b->board[b->pieces[i+BLACK]] = i+BLACK;
		b->pieces[i+WHITE]=POS(1,i);
		b->board[b->pieces[i+WHITE]] = i+WHITE;
	}
	for (i=8;i<16;i++)
	{
		b->pieces[i+BLACK]=POS(7,i-8);
		b->board[b->pieces[i+BLACK]] = i+BLACK;
		b->pieces[i+WHITE]=POS(0,i-8);
		b->board[b->pieces[i+WHITE]] = i+WHITE;
	}
	b->castle=0x77; // 2*4 bits set to 1 : Rooks & king + bit control
	computeControls(b);
#ifdef TEST
	curbook = -1;
#else
	curbook = 0;
#endif
}

/**************************************
 *
 */

s8 hasControl(Board b,s8 color,s8 pos)
{
	s8 i,j,cc,*c,k=color-1;
	for (i=0;i<16;i++)
	{
		if (b->pieces[++k]==DEAD)
			continue;
		c = &b->c.buffer[control_idx[k]];
		cc = b->c.buffer[COUNT_IDX+k];
		for (j=0;j<cc;j++)
			if (c[j]==pos)
				return 1;
	}
	return 0;
}

/**************************************
 *
 */

void completePawnMoves(Board b,s8 *out,s8* cc,s8 cp,s8 src)
{
	s8 i = COL(src), dy = cp==WHITE?8:-8, y = cp==WHITE?1:6, _cc = *cc, p;
	u8 pasy = cp==WHITE?4:3, passant = cp==WHITE ? b->c.passantb : b->c.passantw;
	s8 sy = src+dy, row = ROW(src);

	_cc = 0;
	if (b->board[sy]==DEAD)
	{
		out[_cc++]=sy;
		if (row==y&&b->board[sy+dy]==DEAD)
			out[_cc++]=sy+dy;
	}
	if (i>0)
	{
		p = b->board[sy-1];
		if ((p!=DEAD&&COLOR(p)!=cp)||(row==pasy && (passant&(1<<(i-1)))))
			out[_cc++]=sy-1;
	}
	if (i<7)
	{
		p = b->board[sy+1];
		if ((p!=DEAD&&COLOR(p)!=cp)||(row==pasy && (passant&(1<<(i+1)))))
			out[_cc++]=sy+1;
	}
	*cc = _cc;
}

/**************************************
 *
 */

void completeKingMoves(Board b,s8 *out,s8 *c,s8* cc,s8 cp)
{
	s8 _cc=*cc;
	memcpy(out,c,_cc);
	if (cp==WHITE && b->castle&0x07 && !isCheck(b,WHITE))
	{
		if ((b->castle&0x05)&&(b->pieces[Q_ROOK]!=DEAD)&&(b->board[1]==DEAD)&&(b->board[2]==DEAD)&&(b->board[3]==DEAD))
		{
			if (!(hasControl(b,BLACK,2) || hasControl(b,BLACK,3)))
				out[_cc++] = 2;
		}
		if ((b->castle&0x06)&&(b->pieces[K_ROOK]!=DEAD)&&(b->board[5]==DEAD)&&(b->board[6]==DEAD))
		{
			if (!(hasControl(b,BLACK,5) || hasControl(b,BLACK,6)))
				out[_cc++] = 6;
		}
	}
	else if ((cp==BLACK) && (b->castle&0x70) && !isCheck(b,BLACK))
	{
		if ((b->castle&0x50)&&(b->pieces[Q_ROOK+BLACK]!=DEAD)&&(b->board[57]==DEAD)&&(b->board[58]==DEAD)&&(b->board[59]==DEAD))
		{
			if (!(hasControl(b,WHITE,58) || hasControl(b,WHITE,59)))
				out[_cc++] = 58;
		}
		if ((b->castle&0x60)&&(b->pieces[K_ROOK+BLACK]!=DEAD)&&(b->board[61]==DEAD)&&(b->board[62]==DEAD))
		{
			if (!(hasControl(b,WHITE,61) || hasControl(b,WHITE,62)))
				out[_cc++] = 62;
		}
	}
	*cc=_cc;
}

/***************************************************************
 * verify
 *
 * return 0 if move from src to dst is possible (not check test)
 */

int verify(Board b,s8 src,s8 dst)
{
	s8 p = b->board[src], cp = COLOR(p), out[10];
	s8 *c, cc = b->c.buffer[COUNT_IDX+p],i;

	p = p%BLACK;
	if (p<8)
	{
		completePawnMoves(b,out,&cc,cp,src);
		c = out;
	}
	else if (p==KING)
	{
		completeKingMoves(b,out,&b->c.buffer[control_idx[p+cp]],&cc,cp);
		c = out;
	}
	else
		c = &b->c.buffer[control_idx[p+cp]];

	for (i=0;i<cc&&c[i]!=dst;i++);
	if (i<cc)
	{
		s8 pp = b->board[dst];
		return pp==DEAD||COLOR(pp)!=cp?0:1;
	}
	return 1;
}

/**************************************
 *
 */

void initSearch()
{
	u8 i;
	for (i=0;i<MAX_BACKBOARDS;i++)
	{
#ifdef NGPC
		bboards[i] = i<Z80_BACKBOARDS ? &_bboards_z80[i] : &_bboards_tlcs[i];
#else
		bboards[i] = &_bboards[i];
#endif
		free_bb[i] = 0;
	}

}

/**************************************
 *
 */

u8 freeBB()
{
	u8 i=0;
	while (free_bb[i])
		i++;
	free_bb[i] = 1;
	return i;
}

/**************************************
 *
 */

u8 possibleMoves(Board b,s8 *keep_s,s8 *keep_d,s8 *keep_n,s16 *keep_v,s8 color,u8* keep_bb,s8 maxkeep)
{
	s8 i,j,n,p,cc,*c,pp,out[10],nbk = 0,mink;
	s16 min = -MAX,value;
	u8 work = freeBB();

	for (i=0;i<16;i++)
	{
		// Verify User action (reset, flip...)
		userAction();
		if (reset)
			return 0;

		n = i+color;
		p = b->pieces[n];
		if (p==DEAD||isPinned(b,n)) // dead or pinned ? can't move
			continue;
 		cc = b->c.buffer[COUNT_IDX+n];
		if (i<8) // move with no control
		{
			completePawnMoves(b,out,&cc,color,p);
			c = out;
		}
		else if (i==KING) // castle moves
		{
			completeKingMoves(b,out,&b->c.buffer[control_idx[n]],&cc,color);
			c = out;
		}
		else
			c = &b->c.buffer[control_idx[n]];

		for (j=0;j<cc;j++)
		{
			// Verify User action (reset, flip...)
			userAction();
			if (reset)
				return 0;

			pp = b->board[c[j]];
			if (i<8 && COL(c[j])!=COL(p) && pp==DEAD)
				continue;
			if (pp!=DEAD&&(COLOR(pp)==color))
				continue;
			copy(bboards[work],b);
			n = play(p,c[j],bboards[work],0);

			if (isCheck(bboards[work],color))
				continue;

			if (maxkeep==0) // check mat test from eval function
			{
				free_bb[work] = 0;
				return 1;
			}

			n = n || isCheck(bboards[work],INV(color));
			value = eval(bboards[work],color);

/*
#ifdef WIN32
			if (nbmoves==11)
			{
				char buf[16];
				logLine();
				sprintf(buf,"%c%d%c%d %d",COL(p)+'a',ROW(p)+1,COL(c[j])+'a',ROW(c[j])+1,value);
				LOG(buf);
			}
#endif
*/
			if (nbk<maxkeep)
			{
				keep_s[nbk]   = p;
				keep_d[nbk]   = c[j];
				keep_n[nbk]   = n;
				keep_bb[nbk]  = work;
				keep_v[nbk++] = value;
				work = freeBB();
			}
			else if (value>keep_v[mink])
			{
				int tmp = keep_bb[mink];
				keep_s[mink]  = p;
				keep_d[mink]  = c[j];
				keep_n[mink]  = n;
				keep_bb[mink] = work;
				keep_v[mink]  = value;
				work = tmp;
			}
			if (nbk==maxkeep)
			{
				mink = 0;
				for (nbk=1;nbk<maxkeep;nbk++)
					if (keep_v[mink]>keep_v[nbk])
						mink = nbk;
			}
		}
	}
	free_bb[work] = 0;
	return nbk;
}

/**************************************
 *
 */

s8 cursrc=DEAD,curdst=DEAD,mindepth;
#ifdef WIN32
u32 nbn;
#endif

s16 compute(s8 color,s8 depth,Board b,s16 alpha,s16 beta)
{
	s8 j,n,nbk = 0;
	s8 keep_s[MAXKEEP];
	s8 keep_d[MAXKEEP];
	s8 keep_n[MAXKEEP];
	s16 keep_v[MAXKEEP];
	u8 keep_bb[MAXKEEP];
	s16 min = -MAX, value;
#ifdef WIN32
	time_t t0,t1;
#endif

	if (curbook>=0)
	{
		u8 nb = book[curbook];
		if (nb>0)
		{
			nb = random()%nb;
			cursrc = book[curbook+1+nb*5];
			curdst = book[curbook+2+nb*5];
			return 0;
		}
	}

	if (depth==DEPTH-1)
	{
		cursrc=DEAD;
		mindepth = INITDEPTH;
#ifdef WIN32
		nbn=0;
		time(&t0);
#endif
	}

	nbk = possibleMoves(b,keep_s,keep_d,keep_n,keep_v,color,keep_bb,keep[depth]);
	if (nbk==0)
		return -MAX;

	if (reset)
		return 0;

	if (nbk==1&&depth==DEPTH-1)
	{
		cursrc=keep_s[0];
		curdst=keep_d[0];
		min = keep_v[0];
		free_bb[keep_bb[0]] = 0;
	}
	else
	{
		for (j=0;j<nbk;j++)
		{
			// Verify User action (reset, flip...)
			userAction();
			if (reset)
				return 0;

			if (depth==DEPTH-1)
				progress(nbk,j);

			n = depth==mindepth && mindepth%2==0 && mindepth>0 && keep_n[j];
#ifdef WIN32
			line[nbl++] = keep_s[j];
			line[nbl++] = keep_d[j];
#endif
			if (n)
				mindepth -=2;
			if (depth==mindepth)
				value = keep_v[j];
			else
				value = -compute(INV(color),(s8)(depth-1),bboards[keep_bb[j]],(s16)(-beta),(s16)(-alpha));
			free_bb[keep_bb[j]] = 0;
			if (n)
				mindepth+=2;
#ifdef WIN32
			nbl-=2;
#endif
			if (value>min)
			{
				if (depth==DEPTH-1)
				{
					cursrc=keep_s[j];
					curdst=keep_d[j];
				}
				min = value;
			}
		}
	}
#ifdef WIN32
	logLine();
	if (depth==DEPTH-1)
	{
		char buf[10];
		time(&t1);
		sprintf(buf,"time %d",t1-t0);
		LOG(buf);
	}

#endif

	if (min>alpha)
		alpha = min;
	if (alpha>=beta)
		return alpha;
	return min;
}

/**************************************
 *
 */

s8 around(Board b,s8 pos,s8 color)
{
	s8 nb = 0, c = COL(pos), r = ROW(pos), i, ret = 0;
	s8 tab[8];

	for (i=0;i<nb;tab[i++]=DEAD);

	if (c>0)
	{
		tab[nb++] = b->board[pos-1];
		if (r>0)
			tab[nb++] = b->board[pos-9];
		if (r<7)
			tab[nb++] = b->board[pos+7];
	}
	if (c<7)
	{
		tab[nb++] = b->board[pos+1];
		if (r>0)
			tab[nb++] = b->board[pos-7];
		if (r<7)
			tab[nb++] = b->board[pos+9];
	}
	if (r>0)
		tab[nb++] = b->board[pos-8];
	if (r<7)
		tab[nb++] = b->board[pos+8];

	for (i=0;i<nb;i++)
		if (tab[i]!=DEAD && COLOR(tab[i])==color)
			ret++;

	return ret;
}

/**************************************
 *
 */

u8 nbattack[64];
u8 nbdefense[64];
s16 minattack[64];
s16 multiattack[32];
u8 nbmulti[32];

s16 eval(Board b,s8 color)
{
	s8 i,j,*c,cc,p,ci,cj,k;
	s16 v = 0,vi,vp;
	u8 bcheck = isCheck(b,BLACK);
	u8 wcheck = isCheck(b,WHITE);
#ifdef WIN32
	nbn++;
#endif

	// Check mat ?
	if (((color==WHITE)&&bcheck)||((color==BLACK)&&wcheck))
	{
		if (possibleMoves(b,NULL,NULL,NULL,NULL,INV(color),NULL,0)==0)
			return MAX;
	}

	for (i=0;i<64;i++)
	{
		nbattack[i] = 0;
		nbdefense[i] = 0;
		minattack[i] = MAX;
	}
	for (i=0;i<32;i++)
	{
		nbmulti[i] = 0;
		multiattack[i] = MAX;
	}
	// Control
	for (i=0;i<32;i++)
	{
		j = b->pieces[i];
		if (j==DEAD)
			continue;
		vi = vpieces[i];
		ci = i>=BLACK ? -1 : 1;
		v += ci*vi*PIECE;
		if (isPinned(b,i))
			continue;
		cc = b->c.buffer[COUNT_IDX+i];
		c = &b->c.buffer[control_idx[i]];
		k=1;
		for (j=0;j<cc;j++)
		{
			cj = c[j];
			v += ci*CASE*vboard[cj];
			p = b->board[cj];
			if (p!=DEAD)
			{
				
				if (COLOR(p)!=COLOR(i))
				{
					++nbattack[cj];
					if (vi>0 && vi<minattack[cj])
						minattack[cj] = vi;
					vp = vpieces[p];
					if (vp>vi)
					{
						++nbmulti[i];
						if (vp<multiattack[i])
							multiattack[i] = vp;
					}
				}
				else
					++nbdefense[cj];
			}
			else
			{
				if ((i<BLACK && !blackattack[cj])||(i>=BLACK && !whiteattack[cj]))
					k=0;
			}
		}
		if (((i>8&&i<BLACK)||(i>=BLACK+8)) && k)
			v -= ci*vi*LOCKED;
	}
	// Pieces attacked with no defense or while check
	for (i=0;i<BLACK;i++)
	{
		p = b->pieces[i];
		if ((p==DEAD)||(i==KING)||!nbattack[p])
			continue;
		if (color==WHITE||wcheck) // black to play
		{
			vi = vpieces[i];
			if (nbattack[p] && !nbdefense[p])
				v-=vi*PIECE;
			else if (minattack[p]<vi)
				v-=(vi-minattack[p])*PIECE;
		}
	}
	for (i=BLACK;i<32;i++)
	{
		p = b->pieces[i];
		if ((p==DEAD)||(i==KING+BLACK)||!nbattack[p])
			continue;
		if (color==BLACK||bcheck) // white to play
		{
			vi = vpieces[i];
			if (nbattack[p] && !nbdefense[p])
				v+=vi*PIECE;
			else if (minattack[p]<vi)
				v+=(vi-minattack[p])*PIECE;
		}
	}
	// Multiple attacks on a bigger piece
	if (color==WHITE)
	{
		for (i=0;i<BLACK;i++)
			if (nbmulti[i]>1)
				v+=(multiattack[i]-vpieces[i])*PIECE;
	}
	else
	{
		for (i=BLACK;i<32;i++)
			if (nbmulti[i]>1)
				v-=(multiattack[i]-vpieces[i])*PIECE;
	}

	// Castle bonus
	if (b->castle&0x08)
		v+=CASTLE;
	else if (!(b->castle&0x04))
		v-=CASTLE;
	if (b->castle&0x80)
		v-=CASTLE;
	else if (!(b->castle&0x40))
		v+=CASTLE;
	i = b->pieces[QUEEN+color];
	if (i!=DEAD)
	{
		u8 pos = POS(color==BLACK?7:0,3);
		if ((i!=pos)&&(nbmoves<QUEENMVLIM))
			v-=(color==WHITE?QUEENMV:-QUEENMV);
	}
	// Pawns struct
	for (i=0;i<8;i++)
	{
		if (b->pieces[i]!=DEAD)
		{
			cc = COL(b->pieces[i]);
			for (j=i+1;j<8;j++)
				if (cc==COL(b->pieces[j]))
					v-=DBL_PAWN;
		}
	}
	for (i=BLACK;i<BLACK+8;i++)
	{
		if (b->pieces[i]!=DEAD)
		{
			cc = COL(b->pieces[i]);
			for (j=i+1;j<BLACK+8;j++)
				if (cc==COL(b->pieces[j]))
					v+=DBL_PAWN;
		}
	}

	// Kings protection
	v += KINGPROTECT*around(b,b->pieces[KING],WHITE);
	v -= KINGPROTECT*around(b,b->pieces[KING+BLACK],BLACK);

	return color==WHITE ? v : -v;
}

/**************************************
 *
 */

s8 play(s8 src,s8 dst,Board b,u8 dobook)
{
	s8 pp = b->board[dst],p,i,castle=1;
	if (pp!=DEAD)
		b->pieces[pp] = DEAD;
	p = b->board[src];
	b->board[src] = DEAD;
	b->board[dst] = p;
	b->pieces[p] = dst;
	b->c.pinned = 0;
	if (p<8)
	{
		b->c.passantw = dst-src==16 ? (1<<p) : 0;
		if (COL(dst)!=COL(src) && pp==DEAD) // en passant
		{
			i = POS(ROW(src),COL(dst));
			b->pieces[b->board[i]] = DEAD;
			b->board[i] = DEAD;
		}
		b->c.passantb = 0;
		if (ROW(dst)==7) // Promotion
		{
			for (i=0;i<7;i++)
			{
				if (b->pieces[promotion[i]]==DEAD)
				{
					b->pieces[p] = DEAD;
					b->pieces[promotion[i]] = dst;
					b->board[dst] = promotion[i];
					break;
				}
			}
		}
	}
	else if (p>=BLACK && p<BLACK+8)
	{
		b->c.passantb = src-dst==16 ? (1<<(p-BLACK)) : 0;
		if (COL(dst)!=COL(src) && pp==DEAD) // en passant
		{
			i = POS(ROW(dst),COL(src));
			b->pieces[b->board[i]] = DEAD;
			b->board[i] = DEAD;
		}
		b->c.passantw = 0;
		if (ROW(dst)==0) // Promotion
		{
			for (i=0;i<7;i++)
			{
				if (b->pieces[promotion[i]+BLACK]==DEAD)
				{
					b->pieces[p] = DEAD;
					b->pieces[promotion[i]+BLACK] = dst;
					b->board[dst] = promotion[i]+BLACK;
					break;
				}
			}
		}
	}
	// Castle
	if (p==KING)
	{
		if (dst-src==2)
			play(7,5,b,0);
		else if (src-dst==2)
			play(0,3,b,0);
		else
			castle = 0;
	}
	else if (p==KING+BLACK)
	{
		if (dst-src==2)
			play(63,61,b,0);
		else if (src-dst==2)
			play(56,59,b,0);
		else
			castle = 0;
	}
	else
		castle = 0;
	if (!castle)
		computeControls(b);
	switch(p)
	{
		case Q_ROOK:			// BBBB WWWW
			b->castle &= 0xfe;	// 1111 1110
			break;
		case Q_ROOK+BLACK:
			b->castle &= 0xef;	// 1110 1111
			break;
		case K_ROOK:
			b->castle &= 0xfd;	// 1111 1101
			break;
		case K_ROOK+BLACK:
			b->castle &= 0xdf;	// 1101 1111
			break;
		case KING:
			b->castle &= 0xfb;	// 1111 1011
			if (castle)
				b->castle |= 0x08;
			else
				b->castle &= 0xf8;
			break;
		case KING+BLACK:
			b->castle &= 0xbf;	// 1011 1111
			if (castle)
				b->castle |= 0x80;
			else
				b->castle &= 0x8f;
			break;
	}
	if (curbook>=0 && dobook)
	{
		u8 nb = book[curbook];
		for (i=0;i<nb;i++)
		{
			if ((book[curbook+1+i*5]==src) && (book[curbook+2+i*5]==dst))
				break;
		}
		if (i==nb)
			curbook = -1;
		else
		{
			u32 l = book[curbook+3+i*5];
			l = l*256+book[curbook+4+i*5];
			l = l*256+book[curbook+5+i*5];
			curbook=l;
		}
	}
	return pp!=DEAD;
}
