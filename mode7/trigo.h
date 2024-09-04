#ifndef _TRIGO_

#define _TRIGO_

#define PI 256
#define PI_DIV_2 128
#define PRECISION 512
#define PMULT 128
#define PMULT2 256
#define PMASK 511
#define PSHIFT 7

const s16 tcos[PRECISION] = {
128, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 126, 126, 126, 126, 125, 125, 125, 124, 124, 124, 123, 123, 122, 122, 122, 121, 121, 120, 119, 119, 118, 118, 117, 117, 116, 115, 115, 114, 113, 112, 112, 111, 110, 109, 108, 108, 107, 106, 105, 104, 103, 102, 101, 100, 99, 98, 97, 96, 95, 94, 93, 92, 91, 90, 89, 88, 87, 85, 84, 83, 82, 81, 79, 78, 77, 76, 74, 73, 72, 71, 69, 68, 67, 65, 64, 63, 61, 60, 58, 57, 56, 54, 53, 51, 50, 48, 47, 46, 44, 43, 41, 40, 38, 37, 35, 34, 32, 31, 29, 28, 26, 24, 23, 21, 20, 18, 17, 15, 14, 12, 10, 9, 7, 6, 4, 3, 1, 0, -1, -3, -4, -6, -7, -9, -10, -12, -14, -15, -17, -18, -20, -21, -23, -24, -26, -28, -29, -31, -32, -34, -35, -37, -38, -40, -41, -43, -44, -46, -47, -48, -50, -51, -53, -54, -56, -57, -58, -60, -61, -63, -64, -65, -67, -68, -69, -71, -72, -73, -74, -76, -77, -78, -79, -81, -82, -83, -84, -85, -87, -88, -89, -90, -91, -92, -93, -94, -95, -96, -97, -98, -99, -100, -101, -102, -103, -104, -105, -106, -107, -108, -108, -109, -110, -111, -112, -112, -113, -114, -115, -115, -116, -117, -117, -118, -118, -119, -119, -120, -121, -121, -122, -122, -122, -123, -123, -124, -124, -124, -125, -125, -125, -126, -126, -126, -126, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -128, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -126, -126, -126, -126, -125, -125, -125, -124, -124, -124, -123, -123, -122, -122, -122, -121, -121, -120, -119, -119, -118, -118, -117, -117, -116, -115, -115, -114, -113, -112, -112, -111, -110, -109, -108, -108, -107, -106, -105, -104, -103, -102, -101, -100, -99, -98, -97, -96, -95, -94, -93, -92, -91, -90, -89, -88, -87, -85, -84, -83, -82, -81, -79, -78, -77, -76, -74, -73, -72, -71, -69, -68, -67, -65, -64, -63, -61, -60, -58, -57, -56, -54, -53, -51, -50, -48, -47, -46, -44, -43, -41, -40, -38, -37, -35, -34, -32, -31, -29, -28, -26, -24, -23, -21, -20, -18, -17, -15, -14, -12, -10, -9, -7, -6, -4, -3, -1, 0, 1, 3, 4, 6, 7, 9, 10, 12, 14, 15, 17, 18, 20, 21, 23, 24, 26, 28, 29, 31, 32, 34, 35, 37, 38, 40, 41, 43, 44, 46, 47, 48, 50, 51, 53, 54, 56, 57, 58, 60, 61, 63, 64, 65, 67, 68, 69, 71, 72, 73, 74, 76, 77, 78, 79, 81, 82, 83, 84, 85, 87, 88, 89, 90, 91, 92, 93, 94, 95, 96, 97, 98, 99, 100, 101, 102, 103, 104, 105, 106, 107, 108, 108, 109, 110, 111, 112, 112, 113, 114, 115, 115, 116, 117, 117, 118, 118, 119, 119, 120, 121, 121, 122, 122, 122, 123, 123, 124, 124, 124, 125, 125, 125, 126, 126, 126, 126, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127
};

const s16 tsin[PRECISION] = {
0, 1, 3, 4, 6, 7, 9, 10, 12, 14, 15, 17, 18, 20, 21, 23, 24, 26, 28, 29, 31, 32, 34, 35, 37, 38, 40, 41, 43, 44, 46, 47, 48, 50, 51, 53, 54, 56, 57, 58, 60, 61, 63, 64, 65, 67, 68, 69, 71, 72, 73, 74, 76, 77, 78, 79, 81, 82, 83, 84, 85, 87, 88, 89, 90, 91, 92, 93, 94, 95, 96, 97, 98, 99, 100, 101, 102, 103, 104, 105, 106, 107, 108, 108, 109, 110, 111, 112, 112, 113, 114, 115, 115, 116, 117, 117, 118, 118, 119, 119, 120, 121, 121, 122, 122, 122, 123, 123, 124, 124, 124, 125, 125, 125, 126, 126, 126, 126, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 128, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 126, 126, 126, 126, 125, 125, 125, 124, 124, 124, 123, 123, 122, 122, 122, 121, 121, 120, 119, 119, 118, 118, 117, 117, 116, 115, 115, 114, 113, 112, 112, 111, 110, 109, 108, 108, 107, 106, 105, 104, 103, 102, 101, 100, 99, 98, 97, 96, 95, 94, 93, 92, 91, 90, 89, 88, 87, 85, 84, 83, 82, 81, 79, 78, 77, 76, 74, 73, 72, 71, 69, 68, 67, 65, 64, 63, 61, 60, 58, 57, 56, 54, 53, 51, 50, 48, 47, 46, 44, 43, 41, 40, 38, 37, 35, 34, 32, 31, 29, 28, 26, 24, 23, 21, 20, 18, 17, 15, 14, 12, 10, 9, 7, 6, 4, 3, 1, 0, -1, -3, -4, -6, -7, -9, -10, -12, -14, -15, -17, -18, -20, -21, -23, -24, -26, -28, -29, -31, -32, -34, -35, -37, -38, -40, -41, -43, -44, -46, -47, -48, -50, -51, -53, -54, -56, -57, -58, -60, -61, -63, -64, -65, -67, -68, -69, -71, -72, -73, -74, -76, -77, -78, -79, -81, -82, -83, -84, -85, -87, -88, -89, -90, -91, -92, -93, -94, -95, -96, -97, -98, -99, -100, -101, -102, -103, -104, -105, -106, -107, -108, -108, -109, -110, -111, -112, -112, -113, -114, -115, -115, -116, -117, -117, -118, -118, -119, -119, -120, -121, -121, -122, -122, -122, -123, -123, -124, -124, -124, -125, -125, -125, -126, -126, -126, -126, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -128, -127, -127, -127, -127, -127, -127, -127, -127, -127, -127, -126, -126, -126, -126, -125, -125, -125, -124, -124, -124, -123, -123, -122, -122, -122, -121, -121, -120, -119, -119, -118, -118, -117, -117, -116, -115, -115, -114, -113, -112, -112, -111, -110, -109, -108, -108, -107, -106, -105, -104, -103, -102, -101, -100, -99, -98, -97, -96, -95, -94, -93, -92, -91, -90, -89, -88, -87, -85, -84, -83, -82, -81, -79, -78, -77, -76, -74, -73, -72, -71, -69, -68, -67, -65, -64, -63, -61, -60, -58, -57, -56, -54, -53, -51, -50, -48, -47, -46, -44, -43, -41, -40, -38, -37, -35, -34, -32, -31, -29, -28, -26, -24, -23, -21, -20, -18, -17, -15, -14, -12, -10, -9, -7, -6, -4, -3, -1
};

const u16 tacos[PMULT2] = {
491, 483, 476, 471, 466, 461, 457, 454, 450, 447, 443, 440, 437, 435, 432, 429, 427, 424, 422, 419, 417, 415, 412, 410, 408, 406, 404, 402, 400, 398, 396, 394, 392, 390, 388, 386, 384, 383, 381, 379, 377, 376, 374, 372, 370, 369, 367, 366, 364, 362, 361, 359, 358, 356, 354, 353, 351, 350, 348, 347, 345, 344, 342, 341, 339, 338, 336, 335, 334, 332, 331, 329, 328, 326, 325, 324, 322, 321, 320, 318, 317, 315, 314, 313, 311, 310, 309, 307, 306, 305, 303, 302, 301, 299, 298, 297, 295, 294, 293, 291, 290, 289, 288, 286, 285, 284, 282, 281, 280, 278, 277, 276, 275, 273, 272, 271, 270, 268, 267, 266, 264, 263, 262, 261, 259, 258, 257, 256, 254, 253, 252, 250, 249, 248, 247, 245, 244, 243, 241, 240, 239, 238, 236, 235, 234, 233, 231, 230, 229, 227, 226, 225, 223, 222, 221, 220, 218, 217, 216, 214, 213, 212, 210, 209, 208, 206, 205, 204, 202, 201, 200, 198, 197, 196, 194, 193, 191, 190, 189, 187, 186, 185, 183, 182, 180, 179, 177, 176, 175, 173, 172, 170, 169, 167, 166, 164, 163, 161, 160, 158, 157, 155, 153, 152, 150, 149, 147, 145, 144, 142, 141, 139, 137, 135, 134, 132, 130, 128, 127, 125, 123, 121, 119, 117, 115, 113, 111, 109, 107, 105, 103, 101, 99, 96, 94, 92, 89, 87, 84, 82, 79, 76, 74, 71, 68, 64, 61, 57, 54, 50, 45, 40, 35, 28, 20, 0
};

#endif