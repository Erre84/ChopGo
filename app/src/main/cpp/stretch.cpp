#include <jni.h>
#include <algorithm>
#include <cmath>
#include <vector>
#include <stdexcept>
#include "signalsmith-stretch.h"
extern "C" JNIEXPORT jshortArray JNICALL Java_com_exito_chop_Stretch_render(JNIEnv *env,jclass,jshortArray input,jint rate,jfloat speed,jfloat pitch){
 try{
  if(!input||rate<8000||!std::isfinite(speed)||speed<.25||speed>4||!std::isfinite(pitch)||pitch<.25||pitch>4)throw std::runtime_error("Invalid stretch parameters");
  const int frames=env->GetArrayLength(input)/2;const long long target=std::llround(frames/double(speed));if(target<1||target>1073741800)throw std::runtime_error("Audio too long");
  jshortArray result=env->NewShortArray(target*2);if(!result)return nullptr;
  signalsmith::stretch::SignalsmithStretch<float> stretch(42);stretch.presetDefault(2,rate);stretch.setTransposeFactor(pitch);
  const int pre=stretch.inputLatency(),latency=stretch.outputLatency(),chunk=4096;
  const int inSize=std::max(pre,chunk),outSize=std::max(latency,int(std::ceil(chunk/speed))+4);
  std::vector<jshort> packed(inSize*2),converted(outSize*2);std::vector<float> left(inSize),right(inSize),outL(outSize),outR(outSize);float *ins[]={left.data(),right.data()},*outs[]={outL.data(),outR.data()};
  auto read=[&](int start,int count){std::fill(left.begin(),left.end(),0);std::fill(right.begin(),right.end(),0);int n=std::max(0,std::min(count,frames-start));if(n){env->GetShortArrayRegion(input,start*2,n*2,packed.data());for(int i=0;i<n;i++){left[i]=packed[i*2]/32768.f;right[i]=packed[i*2+1]/32768.f;}}};
  read(0,pre);stretch.seek(ins,pre,speed);long long emitted=0;
  auto write=[&](int count){int skip=std::max<long long>(0,latency-emitted);long long at=emitted+skip-latency;int n=std::min<long long>(count-skip,target-at);if(n>0&&at>=0){for(int i=0;i<n;i++)for(int c=0;c<2;c++){float x=(c?outR:outL)[i+skip];converted[i*2+c]=(jshort)std::lround(std::max(-1.f,std::min(.999969f,x))*32768.f);}env->SetShortArrayRegion(result,at*2,n*2,converted.data());}emitted+=count;};
  for(int at=0;at<frames;at+=chunk){int n=std::min(chunk,frames-at),outCount=int(std::llround((at+n)/double(speed))-std::llround(at/double(speed)));read(pre+at,n);stretch.process(ins,n,outs,outCount);write(outCount);if(env->ExceptionCheck())return nullptr;}
  // flush resets internal state, so it is called once with the complete remaining latency.
  stretch.flush(outs,latency);write(latency);return result;
 }catch(const std::exception &e){env->ThrowNew(env->FindClass("java/lang/IllegalStateException"),e.what());return nullptr;}
}
