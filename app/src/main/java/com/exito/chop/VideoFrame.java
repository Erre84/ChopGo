package com.exito.chop;

/** Non-destructive image framing, independent of Android and the audio engine. */
final class VideoFrame {
 float zoom=1,cx=.5f,cy=.5f;
 float width,height,sourceWidth,sourceHeight,drawWidth,drawHeight,tx,ty;
 void size(float w,float h,float sw,float sh){width=Math.max(1,w);height=Math.max(1,h);sourceWidth=Math.max(1,sw);sourceHeight=Math.max(1,sh);layout();}
 void restore(float zoom,float x,float y){this.zoom=Float.isFinite(zoom)?Math.max(1,Math.min(12,zoom)):1;cx=Float.isFinite(x)?x:.5f;cy=Float.isFinite(y)?y:.5f;layout();}
 void reset(){restore(1,.5f,.5f);}
 void fill(boolean top){float fit=Math.min(width/sourceWidth,height/sourceHeight);restore(Math.max(width/sourceWidth,height/sourceHeight)/fit,.5f,top?0:.5f);}
 void pan(float dx,float dy){cx-=dx/Math.max(1,drawWidth);cy-=dy/Math.max(1,drawHeight);layout();}
 void scale(float factor,float x,float y){float vx=(x-tx)/Math.max(1,drawWidth),vy=(y-ty)/Math.max(1,drawHeight);zoom=Math.max(1,Math.min(12,zoom*factor));layout();cx=(width/2-x+vx*drawWidth)/drawWidth;cy=(height/2-y+vy*drawHeight)/drawHeight;layout();}
 void layout(){
  if(width<=0||height<=0||sourceWidth<=0||sourceHeight<=0)return;
  float scale=Math.min(width/sourceWidth,height/sourceHeight)*zoom;drawWidth=sourceWidth*scale;drawHeight=sourceHeight*scale;
  tx=drawWidth<=width?(width-drawWidth)/2:Math.max(width-drawWidth,Math.min(0,width/2-cx*drawWidth));
  ty=drawHeight<=height?(height-drawHeight)/2:Math.max(height-drawHeight,Math.min(0,height/2-cy*drawHeight));
  cx=(width/2-tx)/drawWidth;cy=(height/2-ty)/drawHeight;
 }
}
