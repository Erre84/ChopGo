package com.exito.chop;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;
import java.util.Locale;

/** Large image and playable pads share the screen, without leaving the audio activity. */
final class VideoPerformanceView extends LinearLayout {
 final Engine e;final ChopVideoView video;final PadButton[] pads=new PadButton[16];final Button[] banks=new Button[4];
 final ChopThumbnailCache thumbnails;boolean showStills=true;
 final TextView state,toneText,tempoText;final SeekBar tone,tempo;final Button finish,voice;int bank;
 final Runnable listener=this::refresh;
 int dp(float v){return(int)(v*getResources().getDisplayMetrics().density+.5f);}
 VideoPerformanceView(Context c){
  super(c);e=Engine.get(c);thumbnails=new ChopThumbnailCache(c);bank=Math.min(3,e.selected/16);setOrientation(VERTICAL);setBackgroundColor(SamplerView.BG);
  LinearLayout header=line();put(header,button("VOLVER",()->MainActivity.showVideo(c,false)),1);TextView title=label("CHOPGO · VÍDEO + 16 PADS",SamplerView.PINK);title.setGravity(Gravity.CENTER);header.addView(title,new LayoutParams(0,-1,2));put(header,button("CARGAR VÍDEO",()->MainActivity.launch(c,"video-file",null)),1);addView(header,new LayoutParams(-1,dp(36)));
  boolean wide=getResources().getConfiguration().orientation==Configuration.ORIENTATION_LANDSCAPE;
  LinearLayout stage=line();stage.setOrientation(wide?HORIZONTAL:VERTICAL);addView(stage,new LayoutParams(-1,0,1));
  LinearLayout picture=new LinearLayout(c);picture.setOrientation(VERTICAL);stage.addView(picture,wide?new LayoutParams(0,-1,1.45f):new LayoutParams(-1,0,1.25f));
  video=new ChopVideoView(c);video.editing(true);video.preferFill(true);picture.addView(video,new LayoutParams(-1,0,1));
  LinearLayout frame=line();put(frame,button("VIDEO COMPLETO",()->video.frameMode(0)),1);put(frame,button("SOLO VÍDEO · LLENAR",()->video.frameMode(1)),1);picture.addView(frame,new LayoutParams(-1,dp(30)));
  LinearLayout controls=new LinearLayout(c);controls.setOrientation(VERTICAL);stage.addView(controls,wide?new LayoutParams(0,-1,1):new LayoutParams(-1,0,1));
  LinearLayout transport=line();put(transport,button("PLAY",e::playAll),1);put(transport,button("PARAR",()->{e.harmony.stop();e.sequencer.stop();e.drums.stop();e.stop();}),1);finish=button("TOCAR CHOPS",()->e.finishPadMarking(true));put(transport,finish,1);put(transport,button("NUEVA TOMA",e::startPadMarking),1);controls.addView(transport,new LayoutParams(-1,dp(35)));
  LinearLayout bankRow=line();for(int i=0;i<4;i++){final int n=i;banks[i]=button(""+(char)('A'+i),()->{bank=n;e.midiBank=n;refresh();});put(bankRow,banks[i],1);}voice=button("MONO",e.slots::toggleMono);put(bankRow,voice,1.2f);
  put(bankRow,button("FOTOS ON/OFF",()->{showStills=!showStills;thumbnails.clear();if(!showStills)for(PadButton pad:pads)pad.setThumbnail(null);refresh();}),2);
  controls.addView(bankRow,new LayoutParams(-1,dp(32)));
  state=label("",SamplerView.MUTED);state.setSingleLine(true);state.setEllipsize(android.text.TextUtils.TruncateAt.END);controls.addView(state,new LayoutParams(-1,dp(18)));
  for(int r=0;r<4;r++){LinearLayout row=line();row.setMotionEventSplittingEnabled(true);for(int k=0;k<4;k++){final int p=r*4+k;pads[p]=new PadButton(c);pads[p].setOnTouchListener(new PadTouch(e,()->bank*16+p));put(row,pads[p],1);}controls.addView(row,new LayoutParams(-1,0,1));}
  LinearLayout knobs=line(),pitchBox=new LinearLayout(c),speedBox=new LinearLayout(c);pitchBox.setOrientation(VERTICAL);speedBox.setOrientation(VERTICAL);knobs.addView(pitchBox,new LayoutParams(0,-1,1));knobs.addView(speedBox,new LayoutParams(0,-1,1));
  toneText=label("",SamplerView.PINK);tempoText=label("",SamplerView.BLUE);pitchBox.addView(toneText,new LayoutParams(-1,dp(18)));speedBox.addView(tempoText,new LayoutParams(-1,dp(18)));
  tone=slider(24,v->e.setPitch(v-12));tempo=slider(150,v->e.setTempo((v+50)/100f));pitchBox.addView(tone,new LayoutParams(-1,dp(30)));speedBox.addView(tempo,new LayoutParams(-1,dp(30)));controls.addView(knobs,new LayoutParams(-1,dp(40)));refresh();
 }
 LinearLayout line(){LinearLayout l=new LinearLayout(getContext());l.setOrientation(HORIZONTAL);return l;}
 TextView label(String s,int color){TextView v=new TextView(getContext());v.setText(s);v.setTextColor(color);v.setTextSize(11);v.setTypeface(Typography.control(getContext()));v.setGravity(Gravity.CENTER_VERTICAL);return v;}
 Button button(String s,Runnable action){Button b=new Button(getContext());b.setText(s);b.setTextColor(SamplerView.FG);b.setTypeface(Typography.control(getContext()));b.setAllCaps(false);b.setPadding(dp(2),0,dp(2),0);b.setMinWidth(0);b.setMinimumWidth(0);b.setMaxLines(2);b.setAutoSizeTextTypeUniformWithConfiguration(8,11,1,android.util.TypedValue.COMPLEX_UNIT_SP);GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0xfff0edde,0xffd0d5c7});d.setStroke(dp(1),0xff9ca99d);d.setCornerRadius(dp(4));b.setBackground(d);b.setOnClickListener(v->action.run());return b;}
 void put(LinearLayout l,View v,float weight){LayoutParams p=new LayoutParams(0,-1,weight);p.setMargins(dp(2),dp(2),dp(2),dp(2));l.addView(v,p);}
 SeekBar slider(int max,java.util.function.IntConsumer action){SeekBar s=new SeekBar(getContext());s.setMax(max);s.setProgressTintList(android.content.res.ColorStateList.valueOf(SamplerView.BLUE));s.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar s){e.history.begin("Tono y tempo");}public void onProgressChanged(SeekBar s,int p,boolean user){if(user&&!e.busy){action.accept(p);e.changed();}}public void onStopTrackingTouch(SeekBar s){e.prepare();if(!e.busy)e.history.end();}});return s;}
 void refresh(){
  bank=e.midiBank;
  if(e.padMarking){bank=Math.min(3,e.padCount()/16);e.midiBank=bank;}for(int i=0;i<4;i++)banks[i].setSelected(i==bank);
  for(int i=0;i<16;i++){int n=bank*16+i;boolean has=e.hasPad(n);pads[i].setText(String.format(Locale.US,"%02d\n",n+1)+(has?String.format(Locale.US,"%.2f s",e.padSeconds(n)):e.canTapPad(n)?e.padActionLabel():"—"));pads[i].setEnabled(e.canTapPad(n));pads[i].setSelected(has&&e.selected==n);pads[i].setActivated(!has&&e.canTapPad(n));}
  voice.setText(e.slots.list[e.slots.active].mono?"MONO":"POLI");finish.setText(e.padMarking?"TOCAR CHOPS":"CHOPS LISTOS");
  state.setText(e.padMarking?"MARCANDO · "+e.padCount()+"/64 · "+e.padActionLabel():e.name+(e.video==null?" · solo audio (sin vídeo conservado)":" · vídeo por chop"));
  if(showStills&&e.video!=null&&e.source!=null&&!e.capturing){
   int[] starts=new int[16];for(int i=0;i<16;i++){int n=bank*16+i;starts[i]=e.hasPad(n)?e.start(n):-1;}
   thumbnails.request(e.video,e.videoOffsetMs,e.rate,bank,starts,frames->{for(int i=0;i<16;i++)pads[i].setThumbnail(frames[i]);});
  }else if(!showStills||e.video==null){thumbnails.clear();for(PadButton pad:pads)pad.setThumbnail(null);}
  toneText.setText(String.format(Locale.US,"TONO %+.0f st",e.semitones));tempoText.setText("TEMPO "+Math.round(e.tempo*100)+" %");if(!tone.isPressed())tone.setProgress(Math.round(e.semitones)+12);if(!tempo.isPressed())tempo.setProgress(Math.round(e.tempo*100)-50);video.refresh();
 }
 @Override protected void onAttachedToWindow(){super.onAttachedToWindow();e.listeners.add(listener);refresh();}
 @Override protected void onDetachedFromWindow(){e.listeners.remove(listener);thumbnails.shutdown();super.onDetachedFromWindow();}
}
