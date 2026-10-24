package com.exito.chop;
final class Scale {
 static final String[] NAMES={"Proyecto · mayor/menor","Mayor","Menor natural","Dórica","Frigia","Lidia","Mixolidia","Locria","Pentatónica mayor","Pentatónica menor","Menor armónica","Cromática"};
 static final int[][] NOTES={{0,2,4,5,7,9,11},{0,2,3,5,7,8,10},{0,2,3,5,7,9,10},{0,1,3,5,7,8,10},{0,2,4,6,7,9,11},{0,2,4,5,7,9,10},{0,1,3,5,6,8,10},{0,2,4,7,9},{0,3,5,7,10},{0,2,3,5,7,8,11},{0,1,2,3,4,5,6,7,8,9,10,11}};
 static int note(int key,int scale,int octave,int degree){int[] s=NOTES[scale==0?(key>=12?1:0):scale-1];return Math.max(0,Math.min(127,(octave+1)*12+key%12+s[Math.floorMod(degree,s.length)]+Math.floorDiv(degree,s.length)*12));}
 static int[] chord(int key,int scale,int octave,int degree,int mode){int[] intervals=mode==0?new int[]{0}:mode==1?new int[]{0,2}:mode==2?new int[]{0,4}:mode==3?new int[]{0,2,4}:mode==4?new int[]{0,2,4,6}:mode==5?new int[]{0,1,4}:new int[]{0,3,4};int[] n=new int[intervals.length];for(int i=0;i<n.length;i++)n[i]=note(key,scale,octave,degree+intervals[i]);return n;}
}
