package com.exito.chop;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.*;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;
/** Station discovery, one live radio receiver, bounded WAV sampling. */
final class RadioDeckView extends ScrollView {
 final MainActivity host;final Engine e;final LinearLayout body,stations;
 final EditText search,country,genre;final TextView status;
 final RadioStream stream;final Handler main=new Handler(Looper.getMainLooper());
 final ArrayList<RadioDirectory.Station> results=new ArrayList<>();
 RadioDeckView(MainActivity activity){super(activity);host=activity;e=Engine.get(activity);setBackgroundColor(SamplerView.BG);
  body=new LinearLayout(host);body.setOrientation(1);body.setPadding(dp(14),dp(10),dp(14),dp(20));addView(body);
  addButton("← VOLVER A CHOPGO",()->host.showPanel());
  header("RADIO DECK · DIRECTO");
  info("Emisoras mundiales desde Radio Browser. Busca por nombre, país ISO (ES, US, GB…) o género. Las radios y sus enlaces cambian; no todas permiten grabación. Usa contenidos que tengas derecho a samplear.");
  search=edit("Nombre (BBC, KEXP, Radio 3…)");country=edit("País: ES, US, GB… (opcional)");genre=edit("Género: jazz, hiphop, soul… (opcional)");
  body.addView(search);LinearLayout filters=row();filters.addView(country,new LinearLayout.LayoutParams(0,dp(46),1));filters.addView(genre,new LinearLayout.LayoutParams(0,dp(46),2));body.addView(filters);
  LinearLayout searchRow=row();put(searchRow,button("BUSCAR",()->lookup(false)),1);put(searchRow,button("POPULARES",()->lookup(true)),1);put(searchRow,button("FAVORITOS",this::favorites),1);body.addView(searchRow);
  status=new TextView(host);status.setTextColor(SamplerView.PINK);status.setTextSize(12);status.setPadding(4,12,4,12);body.addView(status);
  stream=new RadioStream(message->main.post(()->{if(status.getParent()!=null)status.setText(message);}));
  LinearLayout controls=row();put(controls,button("■ PARAR",stream::stop),1);put(controls,button("● REC",stream::arm),1);put(controls,button("■ GUARDAR TOMA",this::saveTake),2);body.addView(controls);
  info("REC registra hasta 45 segundos de audio PCM del directo, sin abrir otros reproductores. GUARDAR TOMA crea un WAV que puedes enviar a un deck o conservar. Escucha preferiblemente emisoras MP3/AAC directas.");
  stations=new LinearLayout(host);stations.setOrientation(1);body.addView(stations);
  status.setText("Busca una emisora o abre la lista de populares.");
 }
 int dp(int value){return (int)(value*getResources().getDisplayMetrics().density+.5f);}
 LinearLayout row(){LinearLayout r=new LinearLayout(host);r.setOrientation(0);return r;}
 void put(LinearLayout r,View v,int weight){r.addView(v,new LinearLayout.LayoutParams(0,dp(49),weight));}
 EditText edit(String hint){EditText t=new EditText(host);t.setHint(hint);t.setSingleLine();t.setTextSize(12);t.setTextColor(SamplerView.FG);t.setHintTextColor(SamplerView.MUTED);return t;}
 Button button(String label,Runnable click){Button b=new Button(host);b.setAllCaps(false);b.setText(label);b.setTextSize(11);b.setTextColor(SamplerView.FG);b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xff284154));b.setOnClickListener(v->click.run());return b;}
 void addButton(String text,Runnable click){body.addView(button(text,click));}
 void header(String text){TextView title=new TextView(host);title.setText(text);title.setTextColor(SamplerView.PINK);title.setTextSize(20);title.setPadding(4,8,4,8);body.addView(title);}
 void info(String text){TextView t=new TextView(host);t.setText(text);t.setTextColor(SamplerView.MUTED);t.setTextSize(12);t.setPadding(5,9,5,9);body.addView(t);}
 void lookup(boolean popular){String name=search.getText().toString(),ct=country.getText().toString(),tag=genre.getText().toString();status.setText("Buscando emisoras…");new Thread(()->{
  try{List<RadioDirectory.Station> found=RadioDirectory.find(popular?"popular":"search",name,ct,tag);
   main.post(()->{results.clear();results.addAll(found);showStations();status.setText(found.size()+" emisoras disponibles en el directorio");});
  }catch(Exception err){main.post(()->status.setText("No se pudo consultar el directorio: "+err.getMessage()));}
 },"ChopGO-RadioDirectory").start();}
 String saved(){return host.getSharedPreferences("chopgo-radio",0).getString("favorites","");}
 boolean favorite(String id){return Arrays.asList(saved().split(",")).contains(id);}
 void markFavorite(String id){HashSet<String> set=new HashSet<>(Arrays.asList(saved().split(",")));set.remove("");if(!set.add(id))set.remove(id);host.getSharedPreferences("chopgo-radio",0).edit().putString("favorites",android.text.TextUtils.join(",",set)).apply();showStations();}
 void favorites(){HashSet<String> ids=new HashSet<>(Arrays.asList(saved().split(",")));if(ids.isEmpty()){status.setText("Todavía no hay favoritos. Busca una radio y pulsa ★.");return;}
  String links=android.text.TextUtils.join(",",ids);status.setText("Recuperando favoritos…");new Thread(()->{
   try{List<RadioDirectory.Station> found=RadioDirectory.findByIds(links);main.post(()->{results.clear();results.addAll(found);showStations();status.setText(found.size()+" favoritos");});}
   catch(Exception err){main.post(()->status.setText("Favoritos: "+err.getMessage()));}
  },"ChopGO-RadioFavorites").start();}
 void showStations(){stations.removeAllViews();int count=Math.min(70,results.size());for(int i=0;i<count;i++){
  final RadioDirectory.Station station=results.get(i);LinearLayout line=row();Button name=button(station.toString(),()->{
   if(!station.stream.startsWith("https://")){status.setText("Esta emisora usa HTTP no cifrado; no disponible en esta configuración.");return;}
   stream.play(station.name,station.stream);
  });name.setMaxLines(2);put(line,name,5);put(line,button(favorite(station.id)?"★":"☆",()->markFavorite(station.id)),1);stations.addView(line);
 }}
 void saveTake(){RadioStream.Take take=stream.take();if(take==null){status.setText("No hay toma disponible. Pulsa REC mientras suena la radio (al menos 0,2 s).");return;}
  status.setText("Creando WAV de "+take.name+"…");e.worker.execute(()->{
   try{File exports=new File(host.getCacheDir(),"exports");exports.mkdirs();String file="ChopGO_Radio_"+System.currentTimeMillis()+".wav";File wav=new File(exports,file);
    try(OutputStream out=new BufferedOutputStream(new FileOutputStream(wav))){AudioFiles.writeWav(out,take.pcm,take.rate);}
    main.post(()->new AlertDialog.Builder(host).setTitle("Toma de radio · WAV listo")
      .setMessage(take.name+" · "+String.format(java.util.Locale.US,"%.1f",take.pcm.length/(double)(2*take.rate))+" s\n¿Dónde quieres usarlo?")
      .setItems(new String[]{"Cargar en Sample Deck","Cargar en pad seleccionado de Web Deck","Exportar WAV al teléfono"},(d,which)->{
        if(which==0){host.showPanel();e.importAudio(Uri.fromFile(wav));}
        else if(which==1){e.webDeck.importFile(e.webDeck.selected,Uri.fromFile(wav));}
        else MainActivity.launch(host,"save-artifact",file);
      }).setNegativeButton("Cerrar",null).show());
   }catch(Exception err){main.post(()->status.setText("Error WAV: "+err.getMessage()));}
  });
 }
 void close(){stream.stop();}
}
