package com.exito.chop;
/** Fifty original rhythmic/note-order arpeggiator patterns for rap and hip-hop. */
final class HipHopArps {
 static final int COUNT=50;
 static final String[] NAMES={
  "BOOM BAP · Basement Swing",
  "BOOM BAP · Vinyl Steps",
  "BOOM BAP · Premier Stabs",
  "BOOM BAP · Dusty Keys",
  "BOOM BAP · Lo-Fi Bounce",
  "BOOM BAP · Staircase 90",
  "BOOM BAP · Half-Time Swing",
  "BOOM BAP · Headnod Sparse",
  "BOOM BAP · Offbeat Rhodes",
  "BOOM BAP · Midnight Break",
  "JAZZ RAP · Blue Notes",
  "JAZZ RAP · Bebop Skip",
  "JAZZ RAP · Walking Minor",
  "JAZZ RAP · Dorian Glide",
  "JAZZ RAP · Jazz Club 92",
  "JAZZ RAP · Broken Ninth",
  "JAZZ RAP · Smoke & Keys",
  "JAZZ RAP · Cool Fourths",
  "JAZZ RAP · Late-Night Jam",
  "JAZZ RAP · Triplet Feel",
  "SOUL / NEO SOUL · Warm Seventh",
  "SOUL / NEO SOUL · Soulful Pulse",
  "SOUL / NEO SOUL · Tender Steps",
  "SOUL / NEO SOUL · Sweet Echo",
  "SOUL / NEO SOUL · Golden Hour",
  "SOUL / NEO SOUL · Fender Flow",
  "SOUL / NEO SOUL · Gospel Turns",
  "SOUL / NEO SOUL · Soft Velvet",
  "SOUL / NEO SOUL · Soul Stutter",
  "SOUL / NEO SOUL · Sunday Loop",
  "G-FUNK · West Coast Glide",
  "G-FUNK · Sine Bounce",
  "G-FUNK · Synth Chrome",
  "G-FUNK · Low Rider",
  "G-FUNK · Portamento Feel",
  "G-FUNK · Sunset Lead",
  "G-FUNK · 808 Funk Walk",
  "G-FUNK · Mirror Funk",
  "G-FUNK · Compton Keys",
  "G-FUNK · Cruise Control",
  "TRAP / DARK · Dark Triads",
  "TRAP / DARK · Trap Bells",
  "TRAP / DARK · Sparse Minor",
  "TRAP / DARK · Noir Stabs",
  "TRAP / DARK · Triplet Trap",
  "TRAP / DARK · Shadow Arp",
  "TRAP / DARK · Frozen Pluck",
  "TRAP / DARK · Haunted Loop",
  "TRAP / DARK · Night Drill",
  "TRAP / DARK · Half-Time Dread"};
 static final int[] MASKS={0x0C88,0xE988,0x88E8,0xB838,0xA2AE,0xCC89,0xCE88,0x9888,0xB408,0xA5E4,0x3101,0x4115,0x5950,0x1451,0x4755,0x9411,0x1891,0x1351,0x6121,0x495D,0x6A22,0xB622,0xA4A2,0xA1A2,0xAA6A,0x2273,0x3206,0x2633,0x822A,0x8296,0x89E8,0xA8A0,0xA8BA,0x2820,0x8AAE,0xC08A,0x8E89,0x0908,0xF0A8,0x84AC,0x9101,0x7119,0x5955,0x5253,0x55D4,0x1131,0x5919,0x3313,0xF111,0x6149};
 static boolean hit(int p,int step){return (MASKS[Math.floorMod(p,COUNT)]&(0x8000 >>> (step&15)))!=0;}
 static int degree(int p,int step,int span){
  span=Math.max(1,span);p=Math.floorMod(p,COUNT);int category=p/10,variant=p%10;
  // Contours differ across categories; walk direction, rotation, leaps and octave movement.
  int n=step+variant;
  switch(category){
   case 0: return Math.floorMod(n+((n/4)&1),span);
   case 1: return Math.floorMod(n*2+(n/3),span);
   case 2: return Math.floorMod((n/2)+(n%4==3?2:0),span);
   case 3: return Math.floorMod((n%8<4?n:7-n)+variant,span);
   default:return Math.floorMod((n*3/2)+((n%3)==0?2:0),span);
  }
 }
 private HipHopArps(){}
}
