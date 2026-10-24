package com.exito.chop;
final class PadParams {
 int start=-1,end=-1,profile=0,eq=0;boolean twelveBit=false,vinyl=false;float wear=.2f,bandwidth=1;
 PadParams copy(){PadParams p=new PadParams();p.start=start;p.end=end;p.profile=profile;p.eq=eq;p.twelveBit=twelveBit;p.vinyl=vinyl;p.wear=wear;p.bandwidth=bandwidth;return p;}
 static PadParams[] defaults(){PadParams[] p=new PadParams[64];for(int i=0;i<64;i++)p[i]=new PadParams();return p;}
}
