package com.exito.chop;

import android.content.*;
import android.net.Uri;
import android.view.*;
import android.widget.*;

/** Two-bank pad browser. A single optional VideoView is shared by 32 audio pads. */
final class WebDeckView extends ScrollView {
 final Engine e;final MainActivity host;final LinearLayout body;
 final Button[] pads=new Button[16];final TextView label;
 final VideoView preview;
 final Runnable listener=this::refresh;
 int bank=0;
 WebDeckView(MainActivity activity){super(activity);host=activity;e=Engine.get(activity);setBackgroundColor(SamplerView.BG);
  body=new LinearLayout(activity);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(12,10,12,18);addView(body);
  LinearLayout top=row();add(top,button("← SAMPLE DECK",()->host.showPanel()),1);
  add(top,button("WEB DECK · 32 PADS",()->{}),2);body.addView(top);
  label=new TextView(activity);label.setTextColor(SamplerView.PINK);label.setPadding(8,8,8,8);body.addView(label);
  preview=new VideoView(activity);body.addView(preview,new LinearLayout.LayoutParams(-1,dp(155)));
  LinearLayout banks=row();add(banks,button("BANCO A · 01–16",()->{bank=0;e.webDeck.bank=0;refresh();}),1);
  add(banks,button("BANCO B · 17–32",()->{bank=1;e.webDeck.bank=1;refresh();}),1);body.addView(banks);
  for(int row=0;row<4;row++){LinearLayout line=row();for(int col=0;col<4;col++){
   int local=row*4+col;Button b=button("WEB "+(local+1),()->{e.webDeck.selected=bank*16+local;e.webDeck.play(bank*16+local);refresh();});
   b.setOnLongClickListener(v->{e.webDeck.selected=bank*16+local;actions(bank*16+local);return true;});pads[local]=b;add(line,b,1);
  }body.addView(line);}
  body.addView(button("CARGAR ARCHIVO EN EL PAD SELECCIONADO",()->host.chooseWebMedia()));
  body.addView(button("ENVIAR CHOP DEL SAMPLE DECK AL PAD",()->{e.webDeck.fromSample(e.webDeck.selected);refresh();}));
  body.addView(button("ASOCIAR ENLACE DE YOUTUBE / SAMPLE TTE / WEB",()->linkDialog()));
  body.addView(button("VER VÍDEO / ABRIR FUENTE DEL PAD",()->showVideo()));
  body.addView(button("AJUSTAR PITCH Y TEMPO DEL PAD",()->adjust()));
  body.addView(button("BORRAR PAD SELECCIONADO",()->new android.app.AlertDialog.Builder(host)
   .setTitle("Vaciar el pad "+(e.webDeck.selected+1)).setPositiveButton("Borrar",(d,w)->{e.webDeck.clearPad(e.webDeck.selected);refresh();})
   .setNegativeButton("Cancelar",null).show()));
  TextView help=new TextView(activity);help.setTextColor(SamplerView.MUTED);help.setTextSize(12);
  help.setText("Toca un pad para escucharlo; mantén pulsado para editarlo.\n"
   +"Cada uno de los 32 pads puede contener audio, vídeo local y un enlace diferente. "
   +"El audio se guarda en WAV, se carga al tocar y pasa por el BUS 4 de forma predeterminada. "
   +"Para YouTube o Samplette carga/captura primero en el Sample Deck, luego envía el chop; "
   +"un enlace por sí solo no captura ni descarga audio. Clips de hasta 45 segundos. "
   +"Los proyectos .chopgo contienen los WAV y vídeos locales que entren dentro del límite.");
  help.setPadding(8,15,8,8);body.addView(help);refresh();
 }
 int dp(int v){return (int)(v*getResources().getDisplayMetrics().density+.5f);}
 LinearLayout row(){LinearLayout r=new LinearLayout(host);r.setOrientation(LinearLayout.HORIZONTAL);return r;}
 void add(LinearLayout r,View v,int weight){r.addView(v,new LinearLayout.LayoutParams(0,dp(55),weight));}
 Button button(String title,Runnable action){Button b=new Button(host);b.setText(title);b.setTextSize(11);b.setAllCaps(false);b.setTextColor(SamplerView.FG);b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xff25374a));b.setOnClickListener(v->action.run());return b;}
 void actions(int idx){new android.app.AlertDialog.Builder(host).setTitle("Web pad "+(idx+1)).setItems(new String[]{"Reproducir","Importar audio/vídeo","Enviar chop actual","Asociar enlace","Ver vídeo o abrir enlace","Pitch / Tempo","Borrar"},
  (d,k)->{e.webDeck.selected=idx;switch(k){case 0:e.webDeck.play(idx);break;case 1:host.chooseWebMedia();break;case 2:e.webDeck.fromSample(idx);break;case 3:linkDialog();break;case 4:showVideo();break;case 5:adjust();break;case 6:e.webDeck.clearPad(idx);break;}refresh();}).show();}
 void linkDialog(){int id=e.webDeck.selected;EditText t=new EditText(host);t.setSingleLine();t.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_URI);t.setHint("https://...");t.setText(e.webDeck.get(id).url);
  new android.app.AlertDialog.Builder(host).setTitle("Enlace del pad "+(id+1)).setView(t).setPositiveButton("Guardar enlace",(d,w)->{String value=t.getText().toString().trim();if(!value.isEmpty()&&!(value.startsWith("https://")||value.startsWith("http://"))){e.message("Introduce un enlace http/https");return;}e.webDeck.updateLink(id,value);refresh();}).setNegativeButton("Cancelar",null).show();}
 void showVideo(){WebDeck.Pad pad=e.webDeck.get(e.webDeck.selected);if(!pad.video.isEmpty()){
  try{Uri uri=Uri.parse(pad.video);preview.setVideoURI(uri);preview.setOnPreparedListener(mp->{mp.setLooping(false);preview.start();});return;}catch(Exception ex){e.message("Vídeo no disponible: "+Engine.reason(ex));}}
  if(!pad.url.isEmpty()){try{Uri u=Uri.parse(pad.url);if(!"https".equals(u.getScheme())&&!"http".equals(u.getScheme()))throw new IllegalArgumentException("URL no válida");host.startActivity(new Intent(Intent.ACTION_VIEW,u));return;}catch(Exception error){e.message("No se pudo abrir el enlace: "+Engine.reason(error));}}
  e.message("Este pad no tiene vídeo guardado ni enlace.");}
 void adjust(){int idx=e.webDeck.selected;WebDeck.Pad p=e.webDeck.get(idx);LinearLayout box=new LinearLayout(host);box.setOrientation(1);
  TextView title=new TextView(host);title.setText("Pitch ±24 semitonos | Tempo 50–200 %");box.addView(title);
  SeekBar pitch=new SeekBar(host);pitch.setMax(48);pitch.setProgress(Math.round(p.pitch)+24);box.addView(pitch);
  SeekBar tempo=new SeekBar(host);tempo.setMax(150);tempo.setProgress(Math.round(p.tempo*100)-50);box.addView(tempo);
  new android.app.AlertDialog.Builder(host).setTitle("Ajuste · WEB "+(idx+1)).setView(box).setPositiveButton("Aplicar",(d,w)->e.webDeck.parameters(idx,pitch.getProgress()-24,(tempo.getProgress()+50)/100f)).setNegativeButton("Cancelar",null).show();}
 void refresh(){bank=e.webDeck.bank;int current=e.webDeck.selected;
  for(int n=0;n<16;n++){int id=bank*16+n;WebDeck.Pad pad=e.webDeck.get(id);String name=pad.title.length()>18?pad.title.substring(0,18)+"…":pad.title;
   pads[n].setText((id+1)+" · "+(pad.loaded()?"●":"○")+(name.isEmpty()?"":"\n"+name));pads[n].setSelected(id==current);pads[n].setAlpha(id==current?1:.85f);}
  WebDeck.Pad pad=e.webDeck.get(current);label.setText("WEB DECK · Banco "+(char)('A'+bank)+" · Pad "+(current+1)+" / 32\n"+(pad.title.isEmpty()?"Vacío":pad.title)+(pad.url.isEmpty()?"":" · URL vinculada"));}
 @Override protected void onAttachedToWindow(){super.onAttachedToWindow();e.listeners.add(listener);refresh();}
 @Override protected void onDetachedFromWindow(){e.listeners.remove(listener);preview.stopPlayback();super.onDetachedFromWindow();}
}
