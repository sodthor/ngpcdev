REM Setup NGPC development environment
REM Change the following paths to reflect the locations of the NGPC compilers

@path=%PATH%;..\..\T900\BIN
@SET THOME=..\..\T900\
@PROMPT $P$_NGPC$G

make
