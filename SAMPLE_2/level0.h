#define LEVEL0_WIDTH 48
#define LEVEL0_HEIGHT 48
 
const u8 LEVEL0_MAP[LEVEL0_WIDTH*LEVEL0_HEIGHT] =
{
1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,
1,2,0,0,0,0,0,0,0,0,0,0,0,3,0,4,0,0,0,0,0,0,0,0,0,0,0,5,0,6,0,0,0,0,0,0,0,0,0,0,0,7,0,0,0,0,0,1,
1,0,2,0,0,0,0,0,0,0,0,0,3,0,0,0,4,0,0,0,0,0,0,0,0,0,5,0,0,0,6,0,0,0,0,0,0,0,0,0,7,0,6,0,0,0,0,1,
1,0,0,2,0,0,0,0,0,0,0,3,0,0,0,0,0,4,0,0,0,0,0,0,0,5,0,0,0,0,0,6,0,0,0,0,0,0,0,7,0,0,0,5,0,0,0,1,
1,0,0,0,2,0,0,0,0,0,3,0,0,0,0,0,0,0,4,0,0,0,0,0,5,0,0,0,0,0,0,0,6,0,0,0,0,0,7,0,0,0,0,0,4,0,0,1,
1,0,0,0,0,2,0,0,0,3,0,0,0,0,0,0,0,0,0,4,0,0,0,5,0,0,0,0,0,0,0,0,0,6,0,0,0,7,0,0,0,0,0,0,0,3,0,1,
1,0,0,0,0,0,2,0,3,0,0,0,0,0,0,0,0,0,0,0,4,0,5,0,0,0,0,0,0,0,0,0,0,0,6,0,7,0,0,0,0,0,0,0,0,0,2,1,
1,0,0,0,0,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,0,0,0,0,0,0,0,0,0,1,
1,0,0,0,0,0,3,0,4,0,0,0,0,0,0,0,0,0,0,0,5,0,6,0,0,0,0,0,0,0,0,0,0,0,7,0,2,0,0,0,0,0,0,0,0,0,0,1,
1,0,0,0,0,3,0,0,0,4,0,0,0,0,0,0,0,0,0,5,0,0,0,6,0,0,0,0,0,0,0,0,0,7,0,0,0,2,0,0,0,0,0,0,0,0,0,1,
1,0,0,0,3,0,0,0,0,0,4,0,0,0,0,0,0,0,5,0,0,0,0,0,6,0,0,0,0,0,0,0,7,0,0,0,0,0,2,0,0,0,0,0,0,0,5,1,
1,0,0,3,0,0,0,0,0,0,0,4,0,0,0,0,0,5,0,0,0,0,0,0,0,6,0,0,0,0,0,7,0,0,0,0,0,0,0,2,0,0,0,0,0,4,0,1,
1,0,3,0,0,0,0,0,0,0,0,0,4,0,0,0,5,0,0,0,0,0,0,0,0,0,6,0,0,0,7,0,0,0,0,0,0,0,0,0,2,0,0,0,3,0,0,1,
1,3,0,0,0,0,0,0,0,0,0,0,0,4,0,5,0,0,0,0,0,0,0,0,0,0,0,6,0,7,0,0,0,0,0,0,0,0,0,0,0,2,0,2,0,0,0,1,
1,0,0,0,0,0,0,1,1,1,1,1,1,1,1,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,0,0,1,
1,4,0,0,0,0,0,1,0,0,0,0,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,2,0,0,0,0,0,0,0,0,0,0,0,0,2,0,0,0,0,0,1,
1,0,4,0,0,0,0,1,0,0,0,0,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,3,0,0,0,0,0,0,0,0,0,0,0,3,0,0,0,0,0,0,1,
1,0,0,4,0,0,0,1,0,0,0,0,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,4,0,0,0,0,0,0,0,0,0,0,4,0,0,0,0,0,0,0,1,
1,0,0,0,4,0,0,1,0,0,0,0,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,5,0,0,0,0,0,0,0,0,0,5,0,0,0,0,0,0,0,0,1,
1,0,0,0,0,4,0,1,0,0,0,0,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,6,0,0,0,0,0,0,0,0,6,0,0,0,0,0,0,0,0,0,1,
1,0,0,0,0,0,4,1,0,0,0,0,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,7,0,0,0,0,0,0,0,7,0,0,0,0,0,0,0,0,0,0,1,
1,0,0,0,0,0,0,1,1,1,1,1,1,1,2,2,2,2,2,2,2,2,0,0,0,0,0,0,5,5,5,5,5,5,5,5,0,0,0,0,0,0,0,0,0,0,0,1,
1,0,0,0,0,0,5,0,0,0,0,0,0,0,2,0,0,0,0,0,0,2,0,0,0,0,0,0,5,0,0,0,0,0,0,5,0,0,0,0,0,0,0,0,0,0,0,1,
1,0,0,0,0,5,0,0,0,0,0,0,0,0,2,0,0,0,0,0,0,2,0,0,0,0,0,0,5,0,0,0,0,0,0,5,0,0,0,0,0,0,0,0,0,0,0,1,
1,0,0,0,5,0,0,0,0,0,0,0,0,0,2,0,0,0,0,0,0,2,0,0,0,0,0,0,5,0,0,0,0,0,0,5,0,0,0,0,0,0,0,0,0,0,0,1,
1,0,0,5,0,0,0,0,0,0,0,0,0,0,2,0,0,0,0,0,0,2,0,0,0,0,0,0,5,0,0,0,0,0,0,5,0,0,0,0,0,0,0,0,0,0,0,1,
1,0,5,0,0,0,0,0,0,0,0,0,0,0,2,0,0,0,0,0,0,2,0,0,0,0,0,0,5,0,0,0,0,0,0,5,0,0,0,0,0,0,0,0,0,0,0,1,
1,5,0,0,0,0,0,0,0,0,0,0,0,0,2,0,0,0,0,0,0,2,0,0,0,0,0,0,5,0,0,0,0,0,0,5,0,0,0,0,0,0,0,0,0,0,0,1,
1,0,0,0,0,0,0,0,0,0,0,0,0,0,2,2,2,2,2,2,2,3,3,3,3,3,3,3,5,5,5,5,5,5,5,5,7,7,7,7,7,7,7,0,0,0,0,1,
1,6,0,0,0,0,0,0,0,0,0,0,0,7,0,0,0,0,0,0,0,3,0,0,0,0,0,0,3,0,0,0,0,0,0,7,0,0,0,0,0,0,7,0,0,0,0,1,
1,0,6,0,0,0,0,0,0,0,0,0,6,0,0,0,0,0,0,0,0,3,0,0,0,0,0,0,3,0,0,0,0,0,0,7,0,0,0,0,0,0,7,0,0,0,0,1,
1,0,0,6,0,0,0,0,0,0,0,5,0,0,0,0,0,0,0,0,0,3,0,0,0,0,0,0,3,0,0,0,0,0,0,7,0,0,0,0,0,0,7,0,0,0,0,1,
1,0,0,0,6,0,0,0,0,0,4,0,0,0,0,0,0,0,0,0,0,3,0,0,0,0,0,0,3,0,0,0,0,0,0,7,0,0,0,0,0,0,7,0,0,0,0,1,
1,0,0,0,0,6,0,0,0,3,0,0,0,0,0,0,0,0,0,0,0,3,0,0,0,0,0,0,3,0,0,0,0,0,0,7,0,0,0,0,0,0,7,0,0,0,0,1,
1,0,0,0,0,0,6,0,2,0,0,0,0,0,0,0,0,0,0,0,0,3,0,0,0,0,0,0,3,0,0,0,0,0,0,7,0,0,0,0,0,0,7,0,0,0,0,1,
1,0,0,0,0,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,3,3,3,3,3,3,3,4,4,4,4,4,4,4,7,7,7,7,7,7,7,7,6,6,6,6,1,
1,0,0,0,0,0,7,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,4,0,0,0,0,0,0,4,0,0,0,0,0,0,6,0,0,0,0,1,
1,0,0,0,0,7,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,4,0,0,0,0,0,0,4,0,0,0,0,0,0,6,0,0,0,0,1,
1,0,0,0,7,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,4,0,0,0,0,0,0,4,0,0,0,0,0,0,6,0,0,0,0,1,
1,0,0,7,0,0,0,0,0,0,0,1,2,3,4,5,6,7,0,0,0,0,0,0,0,0,0,0,4,0,0,0,0,0,0,4,0,0,0,0,0,0,6,0,0,0,0,1,
1,0,7,0,0,0,0,0,0,0,0,2,3,4,5,6,7,1,0,0,0,0,0,0,0,0,0,0,4,0,0,0,0,0,0,4,0,0,0,0,0,0,6,0,0,0,0,1,
1,7,0,0,0,0,0,0,0,0,0,3,4,5,6,7,1,2,0,0,0,0,0,0,0,0,0,0,4,0,0,0,0,0,0,4,0,0,0,0,0,0,6,0,0,0,0,1,
1,0,6,0,0,0,0,0,0,0,0,4,5,6,7,1,2,3,0,0,0,0,0,0,0,0,0,0,4,4,4,4,4,4,4,5,5,5,5,5,5,5,6,6,6,6,6,1,
1,0,0,5,0,0,0,0,0,0,0,5,6,7,1,2,3,4,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,5,0,0,0,0,0,0,5,0,0,0,0,1,
1,0,0,0,4,0,0,0,0,0,0,6,7,1,2,3,4,5,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,5,0,0,0,0,0,0,5,0,0,0,0,1,
1,0,0,0,0,3,0,0,0,0,0,7,1,2,3,4,5,6,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,5,0,0,0,0,0,0,5,0,0,0,0,1,
1,0,0,0,0,0,2,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,5,0,0,0,0,0,0,5,0,0,0,0,1,
1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,
};