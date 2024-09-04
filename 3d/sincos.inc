PREC	EQU	256
BSIZE	EQU	5
MULT	EQU	31
cosinus
	db	01fh,01eh,01eh,01eh,01eh,01eh,01eh,01eh,01eh,01eh,01eh,01dh,01dh,01dh,01dh,01ch
	db	01ch,01ch,01ch,01bh,01bh,01ah,01ah,01ah,019h,019h,018h,018h,017h,017h,016h,016h
	db	015h,015h,014h,014h,013h,013h,012h,011h,011h,010h,00fh,00fh,00eh,00dh,00dh,00ch
	db	00bh,00bh,00ah,009h,008h,008h,007h,006h,006h,005h,004h,003h,003h,002h,001h,000h
	db	000h,000h,0ffh,0feh,0fdh,0fdh,0fch,0fbh,0fah,0fah,0f9h,0f8h,0f8h,0f7h,0f6h,0f5h
	db	0f5h,0f4h,0f3h,0f3h,0f2h,0f1h,0f1h,0f0h,0efh,0efh,0eeh,0edh,0edh,0ech,0ech,0ebh
	db	0ebh,0eah,0eah,0e9h,0e9h,0e8h,0e8h,0e7h,0e7h,0e6h,0e6h,0e6h,0e5h,0e5h,0e4h,0e4h
	db	0e4h,0e4h,0e3h,0e3h,0e3h,0e3h,0e2h,0e2h,0e2h,0e2h,0e2h,0e2h,0e2h,0e2h,0e2h,0e2h
	db	0e1h,0e2h,0e2h,0e2h,0e2h,0e2h,0e2h,0e2h,0e2h,0e2h,0e2h,0e3h,0e3h,0e3h,0e3h,0e4h
	db	0e4h,0e4h,0e4h,0e5h,0e5h,0e6h,0e6h,0e6h,0e7h,0e7h,0e8h,0e8h,0e9h,0e9h,0eah,0eah
	db	0ebh,0ebh,0ech,0ech,0edh,0edh,0eeh,0efh,0efh,0f0h,0f1h,0f1h,0f2h,0f3h,0f3h,0f4h
	db	0f5h,0f5h,0f6h,0f7h,0f8h,0f8h,0f9h,0fah,0fah,0fbh,0fch,0fdh,0fdh,0feh,0ffh,000h
	db	000h,000h,001h,002h,003h,003h,004h,005h,006h,006h,007h,008h,008h,009h,00ah,00bh
	db	00bh,00ch,00dh,00dh,00eh,00fh,00fh,010h,011h,011h,012h,013h,013h,014h,014h,015h
	db	015h,016h,016h,017h,017h,018h,018h,019h,019h,01ah,01ah,01ah,01bh,01bh,01ch,01ch
	db	01ch,01ch,01dh,01dh,01dh,01dh,01eh,01eh,01eh,01eh,01eh,01eh,01eh,01eh,01eh,01eh
sinus
	db	000h,000h,001h,002h,003h,003h,004h,005h,006h,006h,007h,008h,008h,009h,00ah,00bh
	db	00bh,00ch,00dh,00dh,00eh,00fh,00fh,010h,011h,011h,012h,013h,013h,014h,014h,015h
	db	015h,016h,016h,017h,017h,018h,018h,019h,019h,01ah,01ah,01ah,01bh,01bh,01ch,01ch
	db	01ch,01ch,01dh,01dh,01dh,01dh,01eh,01eh,01eh,01eh,01eh,01eh,01eh,01eh,01eh,01eh
	db	01fh,01eh,01eh,01eh,01eh,01eh,01eh,01eh,01eh,01eh,01eh,01dh,01dh,01dh,01dh,01ch
	db	01ch,01ch,01ch,01bh,01bh,01ah,01ah,01ah,019h,019h,018h,018h,017h,017h,016h,016h
	db	015h,015h,014h,014h,013h,013h,012h,011h,011h,010h,00fh,00fh,00eh,00dh,00dh,00ch
	db	00bh,00bh,00ah,009h,008h,008h,007h,006h,006h,005h,004h,003h,003h,002h,001h,000h
	db	000h,000h,0ffh,0feh,0fdh,0fdh,0fch,0fbh,0fah,0fah,0f9h,0f8h,0f8h,0f7h,0f6h,0f5h
	db	0f5h,0f4h,0f3h,0f3h,0f2h,0f1h,0f1h,0f0h,0efh,0efh,0eeh,0edh,0edh,0ech,0ech,0ebh
	db	0ebh,0eah,0eah,0e9h,0e9h,0e8h,0e8h,0e7h,0e7h,0e6h,0e6h,0e6h,0e5h,0e5h,0e4h,0e4h
	db	0e4h,0e4h,0e3h,0e3h,0e3h,0e3h,0e2h,0e2h,0e2h,0e2h,0e2h,0e2h,0e2h,0e2h,0e2h,0e2h
	db	0e1h,0e2h,0e2h,0e2h,0e2h,0e2h,0e2h,0e2h,0e2h,0e2h,0e2h,0e3h,0e3h,0e3h,0e3h,0e4h
	db	0e4h,0e4h,0e4h,0e5h,0e5h,0e6h,0e6h,0e6h,0e7h,0e7h,0e8h,0e8h,0e9h,0e9h,0eah,0eah
	db	0ebh,0ebh,0ech,0ech,0edh,0edh,0eeh,0efh,0efh,0f0h,0f1h,0f1h,0f2h,0f3h,0f3h,0f4h
	db	0f5h,0f5h,0f6h,0f7h,0f8h,0f8h,0f9h,0fah,0fah,0fbh,0fch,0fdh,0fdh,0feh,0ffh,000h
