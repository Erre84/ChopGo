package com.exito.chop;
import android.content.Context;import android.graphics.*;import android.widget.Button;
/** Original hardware-style pad, drawn natively without bitmap assets. */
final class PadButton extends Button {
 final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);final RectF rect=new RectF();final Typeface lettering;private Bitmap thumbnail;
 void setThumbnail(Bitmap image){if(thumbnail!=image){thumbnail=image;invalidate();}}
 PadButton(Context c){super(c);lettering=Typography.control(c);setTypeface(lettering);setMinWidth(0);setMinimumWidth(0);setPadding(0,0,0,0);setAllCaps(false);setBackgroundColor(Color.TRANSPARENT);setLayerType(LAYER_TYPE_SOFTWARE,null);}
 float d(float x){return x*getResources().getDisplayMetrics().density;}
 @Override protected void onDraw(Canvas c){
  float w=getWidth(),h=getHeight();rect.set(d(2),d(2),w-d(2),h-d(2));paint.setShader(null);paint.setColor(0xff8d9891);c.drawRoundRect(rect,d(5),d(5),paint);
  rect.inset(d(2),d(2));int top=isPressed()?0xffc6cbbd:isSelected()?0xffe0e8d5:0xfff4f1df,bottom=isPressed()?0xffb8c3b4:0xffd4d7c8;
  paint.setShader(new LinearGradient(0,0,0,h,top,bottom,Shader.TileMode.CLAMP));c.drawRoundRect(rect,d(3),d(3),paint);paint.setShader(null);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(d(1));paint.setColor(isActivated()?0xffa95646:0xfff8f7ec);rect.inset(d(2),d(2));c.drawRoundRect(rect,d(2),d(2),paint);paint.setStyle(Paint.Style.FILL);
  // A static frame per chop costs much less CPU than 16 embedded players.
  if(thumbnail!=null && h>d(48)){
   float left=d(6),topY=d(26),right=w-d(6),bottomY=h-d(25);
   if(bottomY>topY){paint.setColor(Color.WHITE);paint.setAlpha(isEnabled()?235:90);
    c.drawBitmap(thumbnail,null,new RectF(left,topY,right,bottomY),paint);paint.setAlpha(255);
    if(isSelected()){paint.setStyle(Paint.Style.STROKE);paint.setColor(0xffa85542);paint.setStrokeWidth(d(2));c.drawRoundRect(new RectF(left,topY,right,bottomY),d(2),d(2),paint);paint.setStyle(Paint.Style.FILL);}}
  }
  String[] label=getText().toString().split("\\n",2);String number=label[0],detail=label.length>1?label[1]:"";paint.setTypeface(lettering);paint.setTextSize(d(13));paint.setColor(isEnabled()?0xff34483f:0xff879086);fit(number,w-d(22));c.drawText(number,d(11),d(20),paint);
  if(h<d(42))return;paint.setTextSize(d(10));paint.setColor(isActivated()?0xff9e4a3d:0xff64776e);fit(detail,w-d(20));c.drawText(detail,d(10),h-d(11),paint);
 }
 void fit(String text,float width){float measured=paint.measureText(text);if(measured>width&&width>0)paint.setTextSize(paint.getTextSize()*width/measured);}
}
