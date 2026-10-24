package com.exito.chop;

/** Picture transport follows the audio voice; it never pauses the sampler. */
final class VideoSync {
 interface Picture {long positionMs();void seek(long ms);void pause();void play(float speed);}
 final Picture picture;long token,seekToken,target,lastSeek;float speed=1,playingSpeed=1;
 boolean known,frame,running,playing,seeking;
 VideoSync(Picture picture){this.picture=picture;}
 void update(long token,long sourceMs,boolean run,float speed,long now){
  boolean changed=!known||this.token!=token;this.token=token;target=sourceMs;running=run&&sourceMs>=0;
  this.speed=Float.isFinite(speed)?Math.max(.1f,Math.min(4,speed)):1;known=true;
  if(!running)pause();if(seeking)return;
  if(changed||!frame)seek(now);
  else if(running&&now-lastSeek>=350&&Math.abs(picture.positionMs()-target)>180)seek(now);
  else transport();
 }
 void seekComplete(long now){if(!seeking)return;seeking=false;frame=true;if(token!=seekToken)seek(now);else transport();}
 boolean pending(){return seeking;}
 void seek(long now){pause();seeking=true;seekToken=token;lastSeek=now;picture.seek(Math.max(0,target));}
 void pause(){picture.pause();playing=false;}
 void transport(){if(!running)pause();else if(!playing||speed!=playingSpeed){picture.play(speed);playing=true;playingSpeed=speed;}}
}
