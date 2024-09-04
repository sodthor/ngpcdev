#define MAX_SPEED 32
#define NORM_SPEED 24
#define SHIFT_SPEED 3

const s8 speed_x[MAX_SPEED][64] = {
{  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0},
{  1,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0, -1,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0},
{  2,  1,  1,  1,  1,  1,  1,  1,  1,  1,  1,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -2, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  1,  1,  1,  1,  1,  1,  1,  1,  1,  1},
{  3,  2,  2,  2,  2,  2,  2,  2,  2,  1,  1,  1,  1,  0,  0,  0,  0,  0,  0,  0, -1, -1, -1, -1, -2, -2, -2, -2, -2, -2, -2, -2, -3, -2, -2, -2, -2, -2, -2, -2, -2, -1, -1, -1, -1,  0,  0,  0,  0,  0,  0,  0,  1,  1,  1,  1,  2,  2,  2,  2,  2,  2,  2,  2},
{  4,  3,  3,  3,  3,  3,  3,  3,  2,  2,  2,  1,  1,  1,  0,  0,  0,  0,  0, -1, -1, -1, -2, -2, -2, -3, -3, -3, -3, -3, -3, -3, -4, -3, -3, -3, -3, -3, -3, -3, -2, -2, -2, -1, -1, -1,  0,  0,  0,  0,  0,  1,  1,  1,  2,  2,  2,  3,  3,  3,  3,  3,  3,  3},
{  5,  4,  4,  4,  4,  4,  4,  3,  3,  3,  2,  2,  1,  1,  0,  0,  0,  0,  0, -1, -1, -2, -2, -3, -3, -3, -4, -4, -4, -4, -4, -4, -5, -4, -4, -4, -4, -4, -4, -3, -3, -3, -2, -2, -1, -1,  0,  0,  0,  0,  0,  1,  1,  2,  2,  3,  3,  3,  4,  4,  4,  4,  4,  4},
{  6,  5,  5,  5,  5,  5,  4,  4,  4,  3,  3,  2,  2,  1,  1,  0,  0,  0, -1, -1, -2, -2, -3, -3, -4, -4, -4, -5, -5, -5, -5, -5, -6, -5, -5, -5, -5, -5, -4, -4, -4, -3, -3, -2, -2, -1, -1,  0,  0,  0,  1,  1,  2,  2,  3,  3,  4,  4,  4,  5,  5,  5,  5,  5},
{  7,  6,  6,  6,  6,  6,  5,  5,  4,  4,  3,  3,  2,  2,  1,  0,  0,  0, -1, -2, -2, -3, -3, -4, -4, -5, -5, -6, -6, -6, -6, -6, -7, -6, -6, -6, -6, -6, -5, -5, -4, -4, -3, -3, -2, -2, -1,  0,  0,  0,  1,  2,  2,  3,  3,  4,  4,  5,  5,  6,  6,  6,  6,  6},
{  8,  7,  7,  7,  7,  7,  6,  6,  5,  5,  4,  3,  3,  2,  1,  0,  0,  0, -1, -2, -3, -3, -4, -5, -5, -6, -6, -7, -7, -7, -7, -7, -8, -7, -7, -7, -7, -7, -6, -6, -5, -5, -4, -3, -3, -2, -1,  0,  0,  0,  1,  2,  3,  3,  4,  5,  5,  6,  6,  7,  7,  7,  7,  7},
{  9,  8,  8,  8,  8,  7,  7,  6,  6,  5,  5,  4,  3,  2,  1,  0,  0,  0, -1, -2, -3, -4, -5, -5, -6, -6, -7, -7, -8, -8, -8, -8, -9, -8, -8, -8, -8, -7, -7, -6, -6, -5, -5, -4, -3, -2, -1,  0,  0,  0,  1,  2,  3,  4,  5,  5,  6,  6,  7,  7,  8,  8,  8,  8},
{ 10,  9,  9,  9,  9,  8,  8,  7,  7,  6,  5,  4,  3,  2,  1,  0,  0,  0, -1, -2, -3, -4, -5, -6, -7, -7, -8, -8, -9, -9, -9, -9,-10, -9, -9, -9, -9, -8, -8, -7, -7, -6, -5, -4, -3, -2, -1,  0,  0,  0,  1,  2,  3,  4,  5,  6,  7,  7,  8,  8,  9,  9,  9,  9},
{ 11, 10, 10, 10, 10,  9,  9,  8,  7,  6,  6,  5,  4,  3,  2,  1,  0, -1, -2, -3, -4, -5, -6, -6, -7, -8, -9, -9,-10,-10,-10,-10,-11,-10,-10,-10,-10, -9, -9, -8, -7, -6, -6, -5, -4, -3, -2, -1,  0,  1,  2,  3,  4,  5,  6,  6,  7,  8,  9,  9, 10, 10, 10, 10},
{ 12, 11, 11, 11, 11, 10,  9,  9,  8,  7,  6,  5,  4,  3,  2,  1,  0, -1, -2, -3, -4, -5, -6, -7, -8, -9, -9,-10,-11,-11,-11,-11,-12,-11,-11,-11,-11,-10, -9, -9, -8, -7, -6, -5, -4, -3, -2, -1,  0,  1,  2,  3,  4,  5,  6,  7,  8,  9,  9, 10, 11, 11, 11, 11},
{ 13, 12, 12, 12, 12, 11, 10, 10,  9,  8,  7,  6,  4,  3,  2,  1,  0, -1, -2, -3, -4, -6, -7, -8, -9,-10,-10,-11,-12,-12,-12,-12,-13,-12,-12,-12,-12,-11,-10,-10, -9, -8, -7, -6, -4, -3, -2, -1,  0,  1,  2,  3,  4,  6,  7,  8,  9, 10, 10, 11, 12, 12, 12, 12},
{ 14, 13, 13, 13, 12, 12, 11, 10,  9,  8,  7,  6,  5,  4,  2,  1,  0, -1, -2, -4, -5, -6, -7, -8, -9,-10,-11,-12,-12,-13,-13,-13,-14,-13,-13,-13,-12,-12,-11,-10, -9, -8, -7, -6, -5, -4, -2, -1,  0,  1,  2,  4,  5,  6,  7,  8,  9, 10, 11, 12, 12, 13, 13, 13},
{ 15, 14, 14, 14, 13, 13, 12, 11, 10,  9,  8,  7,  5,  4,  2,  1,  0, -1, -2, -4, -5, -7, -8, -9,-10,-11,-12,-13,-13,-14,-14,-14,-15,-14,-14,-14,-13,-13,-12,-11,-10, -9, -8, -7, -5, -4, -2, -1,  0,  1,  2,  4,  5,  7,  8,  9, 10, 11, 12, 13, 13, 14, 14, 14},
{ 16, 15, 15, 15, 14, 14, 13, 12, 11, 10,  8,  7,  6,  4,  3,  1,  0, -1, -3, -4, -6, -7, -8,-10,-11,-12,-13,-14,-14,-15,-15,-15,-16,-15,-15,-15,-14,-14,-13,-12,-11,-10, -8, -7, -6, -4, -3, -1,  0,  1,  3,  4,  6,  7,  8, 10, 11, 12, 13, 14, 14, 15, 15, 15},
{ 17, 16, 16, 16, 15, 14, 14, 13, 12, 10,  9,  8,  6,  4,  3,  1,  0, -1, -3, -4, -6, -8, -9,-10,-12,-13,-14,-14,-15,-16,-16,-16,-17,-16,-16,-16,-15,-14,-14,-13,-12,-10, -9, -8, -6, -4, -3, -1,  0,  1,  3,  4,  6,  8,  9, 10, 12, 13, 14, 14, 15, 16, 16, 16},
{ 18, 17, 17, 17, 16, 15, 14, 13, 12, 11, 10,  8,  6,  5,  3,  1,  0, -1, -3, -5, -6, -8,-10,-11,-12,-13,-14,-15,-16,-17,-17,-17,-18,-17,-17,-17,-16,-15,-14,-13,-12,-11,-10, -8, -6, -5, -3, -1,  0,  1,  3,  5,  6,  8, 10, 11, 12, 13, 14, 15, 16, 17, 17, 17},
{ 19, 18, 18, 18, 17, 16, 15, 14, 13, 12, 10,  8,  7,  5,  3,  1,  0, -1, -3, -5, -7, -8,-10,-12,-13,-14,-15,-16,-17,-18,-18,-18,-19,-18,-18,-18,-17,-16,-15,-14,-13,-12,-10, -8, -7, -5, -3, -1,  0,  1,  3,  5,  7,  8, 10, 12, 13, 14, 15, 16, 17, 18, 18, 18},
{ 20, 19, 19, 19, 18, 17, 16, 15, 14, 12, 11,  9,  7,  5,  3,  1,  0, -1, -3, -5, -7, -9,-11,-12,-14,-15,-16,-17,-18,-19,-19,-19,-20,-19,-19,-19,-18,-17,-16,-15,-14,-12,-11, -9, -7, -5, -3, -1,  0,  1,  3,  5,  7,  9, 11, 12, 14, 15, 16, 17, 18, 19, 19, 19},
{ 21, 20, 20, 20, 19, 18, 17, 16, 14, 13, 11,  9,  8,  6,  4,  2,  0, -2, -4, -6, -8, -9,-11,-13,-14,-16,-17,-18,-19,-20,-20,-20,-21,-20,-20,-20,-19,-18,-17,-16,-14,-13,-11, -9, -8, -6, -4, -2,  0,  2,  4,  6,  8,  9, 11, 13, 14, 16, 17, 18, 19, 20, 20, 20},
{ 22, 21, 21, 21, 20, 19, 18, 17, 15, 13, 12, 10,  8,  6,  4,  2,  0, -2, -4, -6, -8,-10,-12,-13,-15,-17,-18,-19,-20,-21,-21,-21,-22,-21,-21,-21,-20,-19,-18,-17,-15,-13,-12,-10, -8, -6, -4, -2,  0,  2,  4,  6,  8, 10, 12, 13, 15, 17, 18, 19, 20, 21, 21, 21},
{ 23, 22, 22, 22, 21, 20, 19, 17, 16, 14, 12, 10,  8,  6,  4,  2,  0, -2, -4, -6, -8,-10,-12,-14,-16,-17,-19,-20,-21,-22,-22,-22,-23,-22,-22,-22,-21,-20,-19,-17,-16,-14,-12,-10, -8, -6, -4, -2,  0,  2,  4,  6,  8, 10, 12, 14, 16, 17, 19, 20, 21, 22, 22, 22},
{ 24, 23, 23, 22, 22, 21, 19, 18, 16, 15, 13, 11,  9,  6,  4,  2,  0, -2, -4, -6, -9,-11,-13,-15,-16,-18,-19,-21,-22,-22,-23,-23,-24,-23,-23,-22,-22,-21,-19,-18,-16,-15,-13,-11, -9, -6, -4, -2,  0,  2,  4,  6,  9, 11, 13, 15, 16, 18, 19, 21, 22, 22, 23, 23},
{ 25, 24, 24, 23, 23, 22, 20, 19, 17, 15, 13, 11,  9,  7,  4,  2,  0, -2, -4, -7, -9,-11,-13,-15,-17,-19,-20,-22,-23,-23,-24,-24,-25,-24,-24,-23,-23,-22,-20,-19,-17,-15,-13,-11, -9, -7, -4, -2,  0,  2,  4,  7,  9, 11, 13, 15, 17, 19, 20, 22, 23, 23, 24, 24},
{ 26, 25, 25, 24, 24, 22, 21, 20, 18, 16, 14, 12,  9,  7,  5,  2,  0, -2, -5, -7, -9,-12,-14,-16,-18,-20,-21,-22,-24,-24,-25,-25,-26,-25,-25,-24,-24,-22,-21,-20,-18,-16,-14,-12, -9, -7, -5, -2,  0,  2,  5,  7,  9, 12, 14, 16, 18, 20, 21, 22, 24, 24, 25, 25},
{ 27, 26, 26, 25, 24, 23, 22, 20, 19, 17, 15, 12, 10,  7,  5,  2,  0, -2, -5, -7,-10,-12,-15,-17,-19,-20,-22,-23,-24,-25,-26,-26,-27,-26,-26,-25,-24,-23,-22,-20,-19,-17,-15,-12,-10, -7, -5, -2,  0,  2,  5,  7, 10, 12, 15, 17, 19, 20, 22, 23, 24, 25, 26, 26},
{ 28, 27, 27, 26, 25, 24, 23, 21, 19, 17, 15, 13, 10,  8,  5,  2,  0, -2, -5, -8,-10,-13,-15,-17,-19,-21,-23,-24,-25,-26,-27,-27,-28,-27,-27,-26,-25,-24,-23,-21,-19,-17,-15,-13,-10, -8, -5, -2,  0,  2,  5,  8, 10, 13, 15, 17, 19, 21, 23, 24, 25, 26, 27, 27},
{ 29, 28, 28, 27, 26, 25, 24, 22, 20, 18, 16, 13, 11,  8,  5,  2,  0, -2, -5, -8,-11,-13,-16,-18,-20,-22,-24,-25,-26,-27,-28,-28,-29,-28,-28,-27,-26,-25,-24,-22,-20,-18,-16,-13,-11, -8, -5, -2,  0,  2,  5,  8, 11, 13, 16, 18, 20, 22, 24, 25, 26, 27, 28, 28},
{ 30, 29, 29, 28, 27, 26, 24, 23, 21, 19, 16, 14, 11,  8,  5,  2,  0, -2, -5, -8,-11,-14,-16,-19,-21,-23,-24,-26,-27,-28,-29,-29,-30,-29,-29,-28,-27,-26,-24,-23,-21,-19,-16,-14,-11, -8, -5, -2,  0,  2,  5,  8, 11, 14, 16, 19, 21, 23, 24, 26, 27, 28, 29, 29},
{ 31, 30, 30, 29, 28, 27, 25, 23, 21, 19, 17, 14, 11,  8,  6,  3,  0, -3, -6, -8,-11,-14,-17,-19,-21,-23,-25,-27,-28,-29,-30,-30,-31,-30,-30,-29,-28,-27,-25,-23,-21,-19,-17,-14,-11, -8, -6, -3,  0,  3,  6,  8, 11, 14, 17, 19, 21, 23, 25, 27, 28, 29, 30, 30}
};
const s8 speed_y[MAX_SPEED][64] = {
{  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0},
{  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0, -1,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  1,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0},
{  0,  0,  0,  0,  0,  0, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -2, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  1,  1,  1,  1,  1,  1,  1,  1,  1,  1,  2,  1,  1,  1,  1,  1,  1,  1,  1,  1,  1,  0,  0,  0,  0,  0},
{  0,  0,  0,  0, -1, -1, -1, -1, -2, -2, -2, -2, -2, -2, -2, -2, -3, -2, -2, -2, -2, -2, -2, -2, -2, -1, -1, -1, -1,  0,  0,  0,  0,  0,  0,  0,  1,  1,  1,  1,  2,  2,  2,  2,  2,  2,  2,  2,  3,  2,  2,  2,  2,  2,  2,  2,  2,  1,  1,  1,  1,  0,  0,  0},
{  0,  0,  0, -1, -1, -1, -2, -2, -2, -3, -3, -3, -3, -3, -3, -3, -4, -3, -3, -3, -3, -3, -3, -3, -2, -2, -2, -1, -1, -1,  0,  0,  0,  0,  0,  1,  1,  1,  2,  2,  2,  3,  3,  3,  3,  3,  3,  3,  4,  3,  3,  3,  3,  3,  3,  3,  2,  2,  2,  1,  1,  1,  0,  0},
{  0,  0,  0, -1, -1, -2, -2, -3, -3, -3, -4, -4, -4, -4, -4, -4, -5, -4, -4, -4, -4, -4, -4, -3, -3, -3, -2, -2, -1, -1,  0,  0,  0,  0,  0,  1,  1,  2,  2,  3,  3,  3,  4,  4,  4,  4,  4,  4,  5,  4,  4,  4,  4,  4,  4,  3,  3,  3,  2,  2,  1,  1,  0,  0},
{  0,  0, -1, -1, -2, -2, -3, -3, -4, -4, -4, -5, -5, -5, -5, -5, -6, -5, -5, -5, -5, -5, -4, -4, -4, -3, -3, -2, -2, -1, -1,  0,  0,  0,  1,  1,  2,  2,  3,  3,  4,  4,  4,  5,  5,  5,  5,  5,  6,  5,  5,  5,  5,  5,  4,  4,  4,  3,  3,  2,  2,  1,  1,  0},
{  0,  0, -1, -2, -2, -3, -3, -4, -4, -5, -5, -6, -6, -6, -6, -6, -7, -6, -6, -6, -6, -6, -5, -5, -4, -4, -3, -3, -2, -2, -1,  0,  0,  0,  1,  2,  2,  3,  3,  4,  4,  5,  5,  6,  6,  6,  6,  6,  7,  6,  6,  6,  6,  6,  5,  5,  4,  4,  3,  3,  2,  2,  1,  0},
{  0,  0, -1, -2, -3, -3, -4, -5, -5, -6, -6, -7, -7, -7, -7, -7, -8, -7, -7, -7, -7, -7, -6, -6, -5, -5, -4, -3, -3, -2, -1,  0,  0,  0,  1,  2,  3,  3,  4,  5,  5,  6,  6,  7,  7,  7,  7,  7,  8,  7,  7,  7,  7,  7,  6,  6,  5,  5,  4,  3,  3,  2,  1,  0},
{  0,  0, -1, -2, -3, -4, -5, -5, -6, -6, -7, -7, -8, -8, -8, -8, -9, -8, -8, -8, -8, -7, -7, -6, -6, -5, -5, -4, -3, -2, -1,  0,  0,  0,  1,  2,  3,  4,  5,  5,  6,  6,  7,  7,  8,  8,  8,  8,  9,  8,  8,  8,  8,  7,  7,  6,  6,  5,  5,  4,  3,  2,  1,  0},
{  0,  0, -1, -2, -3, -4, -5, -6, -7, -7, -8, -8, -9, -9, -9, -9,-10, -9, -9, -9, -9, -8, -8, -7, -7, -6, -5, -4, -3, -2, -1,  0,  0,  0,  1,  2,  3,  4,  5,  6,  7,  7,  8,  8,  9,  9,  9,  9, 10,  9,  9,  9,  9,  8,  8,  7,  7,  6,  5,  4,  3,  2,  1,  0},
{  0, -1, -2, -3, -4, -5, -6, -6, -7, -8, -9, -9,-10,-10,-10,-10,-11,-10,-10,-10,-10, -9, -9, -8, -7, -6, -6, -5, -4, -3, -2, -1,  0,  1,  2,  3,  4,  5,  6,  6,  7,  8,  9,  9, 10, 10, 10, 10, 11, 10, 10, 10, 10,  9,  9,  8,  7,  6,  6,  5,  4,  3,  2,  1},
{  0, -1, -2, -3, -4, -5, -6, -7, -8, -9, -9,-10,-11,-11,-11,-11,-12,-11,-11,-11,-11,-10, -9, -9, -8, -7, -6, -5, -4, -3, -2, -1,  0,  1,  2,  3,  4,  5,  6,  7,  8,  9,  9, 10, 11, 11, 11, 11, 12, 11, 11, 11, 11, 10,  9,  9,  8,  7,  6,  5,  4,  3,  2,  1},
{  0, -1, -2, -3, -4, -6, -7, -8, -9,-10,-10,-11,-12,-12,-12,-12,-13,-12,-12,-12,-12,-11,-10,-10, -9, -8, -7, -6, -4, -3, -2, -1,  0,  1,  2,  3,  4,  6,  7,  8,  9, 10, 10, 11, 12, 12, 12, 12, 13, 12, 12, 12, 12, 11, 10, 10,  9,  8,  7,  6,  4,  3,  2,  1},
{  0, -1, -2, -4, -5, -6, -7, -8, -9,-10,-11,-12,-12,-13,-13,-13,-14,-13,-13,-13,-12,-12,-11,-10, -9, -8, -7, -6, -5, -4, -2, -1,  0,  1,  2,  4,  5,  6,  7,  8,  9, 10, 11, 12, 12, 13, 13, 13, 14, 13, 13, 13, 12, 12, 11, 10,  9,  8,  7,  6,  5,  4,  2,  1},
{  0, -1, -2, -4, -5, -7, -8, -9,-10,-11,-12,-13,-13,-14,-14,-14,-15,-14,-14,-14,-13,-13,-12,-11,-10, -9, -8, -7, -5, -4, -2, -1,  0,  1,  2,  4,  5,  7,  8,  9, 10, 11, 12, 13, 13, 14, 14, 14, 15, 14, 14, 14, 13, 13, 12, 11, 10,  9,  8,  7,  5,  4,  2,  1},
{  0, -1, -3, -4, -6, -7, -8,-10,-11,-12,-13,-14,-14,-15,-15,-15,-16,-15,-15,-15,-14,-14,-13,-12,-11,-10, -8, -7, -6, -4, -3, -1,  0,  1,  3,  4,  6,  7,  8, 10, 11, 12, 13, 14, 14, 15, 15, 15, 16, 15, 15, 15, 14, 14, 13, 12, 11, 10,  8,  7,  6,  4,  3,  1},
{  0, -1, -3, -4, -6, -8, -9,-10,-12,-13,-14,-14,-15,-16,-16,-16,-17,-16,-16,-16,-15,-14,-14,-13,-12,-10, -9, -8, -6, -4, -3, -1,  0,  1,  3,  4,  6,  8,  9, 10, 12, 13, 14, 14, 15, 16, 16, 16, 17, 16, 16, 16, 15, 14, 14, 13, 12, 10,  9,  8,  6,  4,  3,  1},
{  0, -1, -3, -5, -6, -8,-10,-11,-12,-13,-14,-15,-16,-17,-17,-17,-18,-17,-17,-17,-16,-15,-14,-13,-12,-11,-10, -8, -6, -5, -3, -1,  0,  1,  3,  5,  6,  8, 10, 11, 12, 13, 14, 15, 16, 17, 17, 17, 18, 17, 17, 17, 16, 15, 14, 13, 12, 11, 10,  8,  6,  5,  3,  1},
{  0, -1, -3, -5, -7, -8,-10,-12,-13,-14,-15,-16,-17,-18,-18,-18,-19,-18,-18,-18,-17,-16,-15,-14,-13,-12,-10, -8, -7, -5, -3, -1,  0,  1,  3,  5,  7,  8, 10, 12, 13, 14, 15, 16, 17, 18, 18, 18, 19, 18, 18, 18, 17, 16, 15, 14, 13, 12, 10,  8,  7,  5,  3,  1},
{  0, -1, -3, -5, -7, -9,-11,-12,-14,-15,-16,-17,-18,-19,-19,-19,-20,-19,-19,-19,-18,-17,-16,-15,-14,-12,-11, -9, -7, -5, -3, -1,  0,  1,  3,  5,  7,  9, 11, 12, 14, 15, 16, 17, 18, 19, 19, 19, 20, 19, 19, 19, 18, 17, 16, 15, 14, 12, 11,  9,  7,  5,  3,  1},
{  0, -2, -4, -6, -8, -9,-11,-13,-14,-16,-17,-18,-19,-20,-20,-20,-21,-20,-20,-20,-19,-18,-17,-16,-14,-13,-11, -9, -8, -6, -4, -2,  0,  2,  4,  6,  8,  9, 11, 13, 14, 16, 17, 18, 19, 20, 20, 20, 21, 20, 20, 20, 19, 18, 17, 16, 14, 13, 11,  9,  8,  6,  4,  2},
{  0, -2, -4, -6, -8,-10,-12,-13,-15,-17,-18,-19,-20,-21,-21,-21,-22,-21,-21,-21,-20,-19,-18,-17,-15,-13,-12,-10, -8, -6, -4, -2,  0,  2,  4,  6,  8, 10, 12, 13, 15, 17, 18, 19, 20, 21, 21, 21, 22, 21, 21, 21, 20, 19, 18, 17, 15, 13, 12, 10,  8,  6,  4,  2},
{  0, -2, -4, -6, -8,-10,-12,-14,-16,-17,-19,-20,-21,-22,-22,-22,-23,-22,-22,-22,-21,-20,-19,-17,-16,-14,-12,-10, -8, -6, -4, -2,  0,  2,  4,  6,  8, 10, 12, 14, 16, 17, 19, 20, 21, 22, 22, 22, 23, 22, 22, 22, 21, 20, 19, 17, 16, 14, 12, 10,  8,  6,  4,  2},
{  0, -2, -4, -6, -9,-11,-13,-15,-16,-18,-19,-21,-22,-22,-23,-23,-24,-23,-23,-22,-22,-21,-19,-18,-16,-15,-13,-11, -9, -6, -4, -2,  0,  2,  4,  6,  9, 11, 13, 15, 16, 18, 19, 21, 22, 22, 23, 23, 24, 23, 23, 22, 22, 21, 19, 18, 16, 15, 13, 11,  9,  6,  4,  2},
{  0, -2, -4, -7, -9,-11,-13,-15,-17,-19,-20,-22,-23,-23,-24,-24,-25,-24,-24,-23,-23,-22,-20,-19,-17,-15,-13,-11, -9, -7, -4, -2,  0,  2,  4,  7,  9, 11, 13, 15, 17, 19, 20, 22, 23, 23, 24, 24, 25, 24, 24, 23, 23, 22, 20, 19, 17, 15, 13, 11,  9,  7,  4,  2},
{  0, -2, -5, -7, -9,-12,-14,-16,-18,-20,-21,-22,-24,-24,-25,-25,-26,-25,-25,-24,-24,-22,-21,-20,-18,-16,-14,-12, -9, -7, -5, -2,  0,  2,  5,  7,  9, 12, 14, 16, 18, 20, 21, 22, 24, 24, 25, 25, 26, 25, 25, 24, 24, 22, 21, 20, 18, 16, 14, 12,  9,  7,  5,  2},
{  0, -2, -5, -7,-10,-12,-15,-17,-19,-20,-22,-23,-24,-25,-26,-26,-27,-26,-26,-25,-24,-23,-22,-20,-19,-17,-15,-12,-10, -7, -5, -2,  0,  2,  5,  7, 10, 12, 15, 17, 19, 20, 22, 23, 24, 25, 26, 26, 27, 26, 26, 25, 24, 23, 22, 20, 19, 17, 15, 12, 10,  7,  5,  2},
{  0, -2, -5, -8,-10,-13,-15,-17,-19,-21,-23,-24,-25,-26,-27,-27,-28,-27,-27,-26,-25,-24,-23,-21,-19,-17,-15,-13,-10, -8, -5, -2,  0,  2,  5,  8, 10, 13, 15, 17, 19, 21, 23, 24, 25, 26, 27, 27, 28, 27, 27, 26, 25, 24, 23, 21, 19, 17, 15, 13, 10,  8,  5,  2},
{  0, -2, -5, -8,-11,-13,-16,-18,-20,-22,-24,-25,-26,-27,-28,-28,-29,-28,-28,-27,-26,-25,-24,-22,-20,-18,-16,-13,-11, -8, -5, -2,  0,  2,  5,  8, 11, 13, 16, 18, 20, 22, 24, 25, 26, 27, 28, 28, 29, 28, 28, 27, 26, 25, 24, 22, 20, 18, 16, 13, 11,  8,  5,  2},
{  0, -2, -5, -8,-11,-14,-16,-19,-21,-23,-24,-26,-27,-28,-29,-29,-30,-29,-29,-28,-27,-26,-24,-23,-21,-19,-16,-14,-11, -8, -5, -2,  0,  2,  5,  8, 11, 14, 16, 19, 21, 23, 24, 26, 27, 28, 29, 29, 30, 29, 29, 28, 27, 26, 24, 23, 21, 19, 16, 14, 11,  8,  5,  2},
{  0, -3, -6, -8,-11,-14,-17,-19,-21,-23,-25,-27,-28,-29,-30,-30,-31,-30,-30,-29,-28,-27,-25,-23,-21,-19,-17,-14,-11, -8, -6, -3,  0,  3,  6,  8, 11, 14, 17, 19, 21, 23, 25, 27, 28, 29, 30, 30, 31, 30, 30, 29, 28, 27, 25, 23, 21, 19, 17, 14, 11,  8,  6,  3}
};
