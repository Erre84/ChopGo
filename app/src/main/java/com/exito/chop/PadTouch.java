package com.exito.chop;
import android.view.*;import android.os.*;import java.util.function.IntSupplier;
/** One repeated trigger at a time per touched pad; no timer survives finger release. */
final class PadTouch implements View.OnTouchListener {
 final Engine e;final IntSupplier pad;final Handler handler=new Handler(Looper.getMainLooper());int id=-1;boolean down=false;Runnable repeat;
 PadTouch(Engine engine,IntSupplier index){e=engine;pad=index;}
 void trigger(){int which=pad.getAsInt();
   if(e.sequencer.levelsEnabled){int velocity=which%16+1;id=e.playLevel(e.slots.active,e.sequencer.levelsPad,velocity);}
   else id=e.tapPad(which);
 }
 void stopRepeat(){down=false;if(repeat!=null)handler.removeCallbacks(repeat);repeat=null;}
 @Override public boolean onTouch(View v,MotionEvent event){switch(event.getActionMasked()){
 case MotionEvent.ACTION_DOWN:
   v.getParent().requestDisallowInterceptTouchEvent(true);v.setPressed(true);down=true;trigger();
   if(e.sequencer.repeatHeld){final long ms=Math.max(25,Math.round(60000.0/Math.max(30,e.slots.masterBpm)*4/e.sequencer.repeatDivision));
     repeat=new Runnable(){public void run(){if(!down||!e.sequencer.repeatHeld)return;if(e.gate&&id>=0)e.output.off(id);trigger();handler.postDelayed(this,ms);}};
     handler.postDelayed(repeat,ms);
   }return true;
 case MotionEvent.ACTION_UP:case MotionEvent.ACTION_CANCEL:
   stopRepeat();if(e.gate&&id>=0)e.output.off(id);v.setPressed(false);v.getParent().requestDisallowInterceptTouchEvent(false);v.performClick();return true;
 }return true;}
}
