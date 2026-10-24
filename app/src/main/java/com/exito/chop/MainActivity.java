package com.exito.chop;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.media.projection.MediaProjectionManager;
import android.media.projection.MediaProjectionConfig;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import java.io.*;

public final class MainActivity extends Activity {
    static final String YOUTUBE="com.google.android.youtube";
    private String pending="",saveName="ChopGO.wav",savePath="export.wav";
    private SamplerView panel;private VideoPerformanceView videoPanel;private WebDeckView webPanel;private RadioDeckView radioPanel;private MidiControllers controllers;
    private boolean cachedPanel=false;
    static void launch(Context c,String action,String value){Intent i=new Intent(c,MainActivity.class).setAction(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP);if(value!=null)i.putExtra("value",value);c.startActivity(i);}
    @Override public void onCreate(Bundle state){super.onCreate(state);setVolumeControlStream(android.media.AudioManager.STREAM_MUSIC);getWindow().setStatusBarColor(0xff111516);getWindow().setNavigationBarColor(0xff111516);
        if(state!=null){savePath=state.getString("savePath","export.wav");saveName=state.getString("saveName","ChopGO.wav");}
        if(state!=null&&state.getBoolean("videoLarge",false))showVideoPanel();else showPanel();
        Engine.get(this).slots.restore();controllers=new MidiControllers(this,Engine.get(this));controllers.start();handle(getIntent());
    }
    void showPanel(){if(radioPanel!=null){radioPanel.close();radioPanel=null;}webPanel=null;videoPanel=null;panel=new SamplerView(this,false);panel.setOnApplyWindowInsetsListener((v,insets)->{v.setPadding(0,insets.getSystemWindowInsetTop(),0,insets.getSystemWindowInsetBottom());return insets;});setContentView(panel);panel.requestApplyInsets();}
    static void showVideo(Context c,boolean large){if(c instanceof MainActivity){MainActivity a=(MainActivity)c;if(large)a.showVideoPanel();else a.showPanel();}else launch(c,large?"video-large":"video-small",null);}
    private void showVideoPanel(){if(radioPanel!=null){radioPanel.close();radioPanel=null;}webPanel=null;panel=null;videoPanel=new VideoPerformanceView(this);videoPanel.setOnApplyWindowInsetsListener((v,insets)->{int pad=(int)(8*getResources().getDisplayMetrics().density);v.setPadding(pad,insets.getSystemWindowInsetTop()+pad,pad,insets.getSystemWindowInsetBottom()+pad);return insets;});setContentView(videoPanel);videoPanel.requestApplyInsets();}
    private void showWebDeck(){if(radioPanel!=null){radioPanel.close();radioPanel=null;}panel=null;videoPanel=null;webPanel=new WebDeckView(this);setContentView(webPanel);}
    private void showRadioDeck(){panel=null;videoPanel=null;webPanel=null;radioPanel=new RadioDeckView(this);setContentView(radioPanel);}
    void chooseWebMedia(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*");i.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"audio/*","video/*"});i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);startActivityForResult(i,121);}
    @Override protected void onDestroy(){if(controllers!=null)controllers.close();if(radioPanel!=null)radioPanel.close();super.onDestroy();}
    @Override public void onBackPressed(){if(videoPanel!=null||webPanel!=null||radioPanel!=null)showPanel();else super.onBackPressed();}
    private void chooseVideo(){Engine e=Engine.get(this);if(e.busy){e.message("Espera a que termine el proceso actual.");return;}if(e.capturing)e.freezeForEdit();Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("video/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);startActivityForResult(i,95);}
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);handle(intent);}
    @Override protected void onResume(){super.onResume();OverlayService.appVisible=true;if(panel!=null)panel.refresh();if(videoPanel!=null)videoPanel.refresh();if(webPanel!=null)webPanel.refresh();}
    @Override protected void onPause(){super.onPause();OverlayService.appVisible=false;Engine e=Engine.get(this);if(!OverlayService.active&&!isChangingConfigurations())e.pauseForInterruption();}
    private void handle(Intent i){String a=i.getAction();if(a==null)return;
        if(Intent.ACTION_SEND.equals(a)){String text=i.getStringExtra(Intent.EXTRA_TEXT);java.util.regex.Matcher match=java.util.regex.Pattern.compile("https?://[^\\s]+",java.util.regex.Pattern.CASE_INSENSITIVE).matcher(text==null?"":text);if(match.find())loadShared(match.group());else Engine.get(this).message("El contenido compartido no contiene un enlace.");i.setAction(null);return;}
        switch(a){case "web-deck":showWebDeck();break;case "radio-deck":showRadioDeck();break;case "local-cover":localCover();break;case "all-chops":allChopsDialog();break;case "export-stems":SessionExport.start(Engine.get(this),false,true);break;case "bus-mixer":showBuses();break;case "midi-controllers":if(controllers!=null)controllers.showMenu(this);break;case "project-new":projectNew();break;case "project-save":projectSave();break;case "project-open":projectOpen();break;case "project-export":projectExport();break;case "video-large":showVideoPanel();break;case "video-small":showPanel();break;case "video-file":chooseVideo();break;case "auto-panel":autoPanel();break;case "system-audio":{Engine e=Engine.get(this);if(e.capturing){e.message("Desconecta la entrada antes de cambiar");break;}e.inputAll=true;e.inputRemote=e.inputWeb=false;e.inputLabel="Audio del móvil";cachedPanel=false;enableOverlay();break;}case "computer":computer();break;case "export-kit":if(Engine.get(this).rendering)Engine.get(this).message("Espera a preparar el WAV del último ajuste.");else exportKit();break;case "midi":if(Engine.get(this).rendering)Engine.get(this).message("Espera a preparar el audio del último ajuste.");else midi();break;case "harmony-midi":harmonyMidi();break;case "ai":aiMenu();break;case "save-artifact":{String file=i.getStringExtra("value");if(file!=null&&file.matches("[A-Za-z0-9_.-]+")&&new File(getCacheDir(),"exports/"+file).isFile()){saveName=file;savePath="exports/"+file;save();}break;}case "capture":capture();break;case "import":choose(false);break;case "drums":choose(true);break;case "save":savePath="export.wav";saveName=i.getStringExtra("value");save();break;case "sample-overlay":if(!Engine.get(this).ready()){Engine.get(this).message("Primero carga una muestra para mostrarla en el panel.");break;}cachedPanel=true;enableOverlay();break;case "overlay":{cachedPanel=false;Engine e=Engine.get(this);if(e.capturing&&(e.inputWeb||e.inputAll||e.inputRemote)){e.message("Desconecta el navegador antes de cambiar a la app de YouTube.");break;}e.inputWatch="";e.inputAll=e.inputRemote=e.inputWeb=false;e.inputPackage=YOUTUBE;e.inputLabel="YouTube";enableOverlay();break;}case "youtube-web":browser("https://m.youtube.com","YouTube web");break;case "samplette":browser("https://samplette.io/","Samplette");break;case "load-source-link":loadShared(i.getStringExtra("value"));break;case "listen-loaded":Engine.get(this).playAll();break;case "drum-save":saveDrum();break;case "source-meta":metadata(false);break;case "sync-meta":metadata(true);break;case "drum-meta":drumMetadata();break;case "drum-library":drumLibrary();break;case "fx-order":fxOrder();break;case "fx-save":fxSave();break;case "fx-load":fxLoad();break;case "youtube-url":youtube();break;case "youtube-live-fallback":youtubeLiveFallback(i.getStringExtra("value"));break;}
        i.setAction(null);
    }

    private void allChopsDialog(){new AlertDialog.Builder(this).setTitle("Exportar chops de los 8 decks")
      .setMessage("Exporta todos los chops de los 4 bancos de cada deck (A/B/C/D), también los que NO se usan en la secuencia. Un WAV por chop + índice CSV en un ZIP.")
      .setPositiveButton("Con FX",(d,w)->SessionExport.start(Engine.get(this),true,true))
      .setNeutralButton("Sin FX",(d,w)->SessionExport.start(Engine.get(this),true,false))
      .setNegativeButton("Cancelar",null).show();}
    private void showBuses(){
        Engine e=Engine.get(this);new AlertDialog.Builder(this).setTitle("BUSES · rutas independientes")
          .setItems(new String[]{"Asignar decks y pistas a un bus","BUS 1 · EQ y drive","BUS 2 · EQ y drive","BUS 3 · EQ y drive","BUS 4 · EQ y drive","Restablecer rutas y efectos"},(d,i)->{
            if(i==0)chooseBusRoute();else if(i<5)editBus(i-1);else new AlertDialog.Builder(this).setTitle("Restablecer buses")
             .setPositiveButton("Restablecer",(x,y)->e.history.edit("Restablecer buses",()->e.buses.reset())).setNegativeButton("Cancelar",null).show();
          }).show();
    }
    private void chooseBusRoute(){Engine e=Engine.get(this);String[] list=new String[BusMixer.TRACKS.length];for(int i=0;i<list.length;i++)list[i]=BusMixer.TRACKS[i]+" → "+e.buses.settings[e.buses.routes[i]].name;
      new AlertDialog.Builder(this).setTitle("Asignar deck / pista").setItems(list,(d,which)->{
        String[] buses=new String[4];for(int j=0;j<4;j++)buses[j]=e.buses.settings[j].name;
        new AlertDialog.Builder(this).setTitle(BusMixer.TRACKS[which]).setSingleChoiceItems(buses,e.buses.routes[which],(x,index)->{
          e.history.edit("Asignar "+BusMixer.TRACKS[which]+" a bus",()->e.buses.routes[which]=index);x.dismiss();chooseBusRoute();
        }).setNegativeButton("Cancelar",null).show();}).setNegativeButton("Cerrar",null).show();
    }
    private void editBus(int which){Engine e=Engine.get(this);BusMixer.Settings setting=e.buses.settings[which];
      android.widget.LinearLayout box=new android.widget.LinearLayout(this);box.setOrientation(1);box.setPadding(18,6,18,6);
      android.widget.EditText name=new android.widget.EditText(this);name.setSingleLine(true);name.setText(setting.name);box.addView(name);
      String[] labels={"Graves ±18 dB","Medios ±18 dB","Agudos ±18 dB","Drive multibanda","Mezcla seco/húmedo","Volumen del bus"};
      android.widget.SeekBar[] bars=new android.widget.SeekBar[6];float[] values={setting.low,setting.mid,setting.high,setting.drive,setting.mix,setting.level};
      for(int i=0;i<6;i++){android.widget.TextView t=new android.widget.TextView(this);t.setText(labels[i]);box.addView(t);bars[i]=new android.widget.SeekBar(this);bars[i].setMax(100);bars[i].setProgress(i<3?Math.round((values[i]+18)*100/36):i==5?Math.round(values[i]*50):Math.round(values[i]*100));box.addView(bars[i]);}
      android.widget.CheckBox mute=new android.widget.CheckBox(this);mute.setText("Silenciar este bus");mute.setChecked(setting.mute);box.addView(mute);
      android.widget.ScrollView scroll=new android.widget.ScrollView(this);scroll.addView(box);
      new AlertDialog.Builder(this).setTitle("Bus "+(which+1)+" · ecualizador / FX").setView(scroll).setPositiveButton("Aplicar",(d,w)->e.history.edit("Editar bus "+(which+1),()->{
        setting.name=name.getText().toString().trim();if(setting.name.isEmpty())setting.name=BusMixer.NAMES[which];
        setting.low=bars[0].getProgress()*.36f-18;setting.mid=bars[1].getProgress()*.36f-18;setting.high=bars[2].getProgress()*.36f-18;
        setting.drive=bars[3].getProgress()/100f;setting.mix=bars[4].getProgress()/100f;setting.level=bars[5].getProgress()/50f;setting.mute=mute.isChecked();
      })).setNegativeButton("Cancelar",null).show();
    }

    private void projectSave(){
        Engine e=Engine.get(this);if(e.busy){e.message("Termina el proceso actual antes de guardar.");return;}
        android.widget.EditText name=new android.widget.EditText(this);name.setSingleLine(true);name.setSelectAllOnFocus(true);name.setText(e.projects.current().isEmpty()?"Mi proyecto":e.projects.current());
        new AlertDialog.Builder(this).setTitle("Guardar proyecto como…").setMessage("Guarda audio original de las 8 fuentes, chops, pads, drums, secuencia y ajustes. Los proyectos quedan en la app. Para tener una copia fuera, usa Exportar.").setView(name)
            .setPositiveButton("Guardar",(dialog,which)->{String clean=ProjectStore.clean(name.getText().toString());File target=e.projects.fileFor(clean);if(target.exists())new AlertDialog.Builder(this).setTitle("¿Sobrescribir «"+clean+"»?").setMessage("Se actualizará el proyecto guardado con los cambios actuales.").setPositiveButton("Sobrescribir",(d,w)->e.projects.saveNamed(clean)).setNegativeButton("Cancelar",null).show();else e.projects.saveNamed(clean);})
            .setNegativeButton("Cancelar",null).show();
    }
    private void projectNew(){
        new AlertDialog.Builder(this).setTitle("Empezar otro proyecto").setMessage("Se vaciará la sesión de trabajo (8 fuentes, drums, chops, efectos y secuenciador). Los proyectos que hayas guardado seguirán disponibles. Guarda antes de continuar si quieres conservar los últimos cambios.")
          .setPositiveButton("Nuevo proyecto",(d,w)->Engine.get(this).projects.createNew()).setNegativeButton("Cancelar",null).show();
    }
    private void projectOpen(){
        Engine e=Engine.get(this);String[] projects=e.projects.names();
        new AlertDialog.Builder(this).setTitle("Cargar proyecto").setMessage(projects.length==0?"Todavía no hay proyectos guardados en este móvil. Puedes importar uno desde tus archivos.":"Selecciona un proyecto guardado. Los cambios sin guardar de la sesión actual se perderán.")
          .setItems(projects,(d,which)->{String selected=projects[which];new AlertDialog.Builder(this).setTitle("Abrir «"+selected+"»").setMessage("¿Cargar este proyecto y sustituir la sesión actual?").setPositiveButton("Cargar",(a,b)->e.projects.loadNamed(selected)).setNegativeButton("Cancelar",null).show();})
          .setPositiveButton("Importar archivo",(d,w)->{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*");i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivityForResult(i,112);})
          .setNegativeButton("Cerrar",null).show();
    }
    private void projectExport(){
        Engine e=Engine.get(this);if(e.busy)return;
        String name=ProjectStore.clean(e.projects.current().isEmpty()?"Mi proyecto":e.projects.current());
        Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/zip").putExtra(Intent.EXTRA_TITLE,name+ProjectStore.SUFFIX);
        startActivityForResult(i,113);
    }
    private void enableOverlay(){
        if(!Settings.canDrawOverlays(this)){
            new AlertDialog.Builder(this).setTitle("Panel pequeño sobre YouTube").setMessage("Permite «Mostrar sobre otras aplicaciones». Al volver se conectará la entrada en directo y se abrirá YouTube con el panel debajo del vídeo. Android pedirá autorización de captura. Con Vídeo ON, Nueva toma pads guarda también la imagen de la pantalla o aplicación autorizada.").setPositiveButton("Abrir ajuste",(d,w)->startActivityForResult(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+getPackageName())),101)).setNegativeButton("Ahora no",null).show();return;
        }
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED&&!getSharedPreferences("exito",0).getBoolean("notificationsAsked",false)){
            getSharedPreferences("exito",0).edit().putBoolean("notificationsAsked",true).apply();requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},83);return;
        }
        Engine e=Engine.get(this);e.inputPrompt=!cachedPanel&&!e.capturing;startForegroundService(new Intent(this,OverlayService.class));if(cachedPanel){cachedPanel=false;openYouTube(this);}else if(e.capturing)openYouTube(this);else capture();
    }
    static void openYouTube(Context c){Engine e=Engine.get(c);if(e.inputRemote)return;if(e.inputAll){c.startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));return;}try{Intent i=!e.inputWatch.isEmpty()?new Intent(Intent.ACTION_VIEW,Uri.parse(e.inputWatch)).setPackage(YOUTUBE):e.inputWeb?new Intent(Intent.ACTION_VIEW,Uri.parse(e.inputHome)).addCategory(Intent.CATEGORY_BROWSABLE).setPackage(e.inputPackage):c.getPackageManager().getLaunchIntentForPackage(YOUTUBE);if(i==null)throw new IOException("No se encuentra la app seleccionada.");i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);c.startActivity(i);}catch(Exception ex){e.message("No se pudo abrir "+e.inputLabel+": "+Engine.reason(ex));}}
    private void loadShared(String text){
        if(text==null||text.trim().isEmpty()){Engine.get(this).message("Pega primero un enlace de YouTube o Samplette.");return;}
        Engine e=Engine.get(this);if(text!=null){Uri shared=Uri.parse(text.trim());if("samplette.io".equalsIgnoreCase(shared.getHost())||"www.samplette.io".equalsIgnoreCase(shared.getHost())){browser("https://samplette.io"+(shared.getPath()==null?"/":shared.getPath()),"Samplette");return;}}if(e.capturing)e.freezeForEdit();
        final long until=android.os.SystemClock.uptimeMillis()+10000;e.main.post(new Runnable(){public void run(){if(e.busy||e.capturing){if(android.os.SystemClock.uptimeMillis()<until)e.main.postDelayed(this,150);else e.message("Termina el proceso actual y vuelve a compartir el vídeo.");return;}YoutubeImporter.load(MainActivity.this,text);}});
    }
    private void browser(String home,String label){
        cachedPanel=false;
        Engine e=Engine.get(this);if(e.capturing){e.message("Pulsa Desconectar en el panel antes de cambiar entre YouTube y el navegador.");return;}
        Intent browse=new Intent(Intent.ACTION_VIEW,Uri.parse(home)).addCategory(Intent.CATEGORY_BROWSABLE);
        java.util.ArrayList<android.content.pm.ResolveInfo> candidates=new java.util.ArrayList<>();java.util.HashSet<String> packages=new java.util.HashSet<>();
        for(android.content.pm.ResolveInfo r:getPackageManager().queryIntentActivities(browse,PackageManager.MATCH_DEFAULT_ONLY)){String p=r.activityInfo.packageName;if(!p.equals(YOUTUBE)&&!p.equals(getPackageName())&&packages.add(p))candidates.add(r);}
        if(candidates.isEmpty()){e.message("No se encuentra un navegador compatible para abrir YouTube web.");return;}
        String[] names=new String[candidates.size()];for(int n=0;n<names.length;n++)names[n]=candidates.get(n).loadLabel(getPackageManager()).toString();
        new AlertDialog.Builder(this).setTitle(label+" · elige navegador").setItems(names,(dialog,n)->{e.inputWatch="";e.inputAll=e.inputRemote=false;e.inputPackage=candidates.get(n).activityInfo.packageName;e.inputLabel=label;e.inputHome=home;e.inputWeb=true;enableOverlay();}).setNegativeButton("Cancelar",null).show();
    }
    private void capture(){
        Engine e=Engine.get(this);if(e.capturing){e.freezeForEdit();return;}if(e.inputRemote){computer();return;}if(e.busy){e.inputPrompt=false;e.message("Termina la carga o ajuste antes de conectar.");return;}e.sequencer.stop();e.inputPrompt=true;e.liveStatus="Autoriza la captura de Android. Vídeo ON permite guardar imagen para los pads.";e.changed();
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){pending="capture";requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},82);return;}
        try{if(!e.inputAll)getPackageManager().getApplicationInfo(e.inputPackage,0);}catch(Exception ex){e.inputPrompt=false;e.liveStatus="No se encuentra "+e.inputLabel+".";e.message(e.liveStatus);return;}
        e.drums.stop();e.stop();
        MediaProjectionManager pm=(MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE);try{Intent permission=Build.VERSION.SDK_INT>=34?pm.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay()):pm.createScreenCaptureIntent();startActivityForResult(permission,91);}catch(Exception error){e.inputPrompt=false;e.liveStatus="No se pudo pedir permiso: "+Engine.reason(error);e.message(e.liveStatus);}
    }
    private void choose(boolean drum){
        if(!drum&&Engine.get(this).capturing)Engine.get(this).freezeForEdit();
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(drum?"audio/*":"*/*");
        if(!drum)i.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"audio/*","video/*"});
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);startActivityForResult(i,drum?93:92);
    }
    private void save(){Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(saveName.endsWith(".zip")?"application/zip":saveName.endsWith(".mid")?"audio/midi":"audio/wav").putExtra(Intent.EXTRA_TITLE,saveName==null?"ChopGO.wav":saveName);startActivityForResult(i,94);}
    private void saveDrum(){
        Engine e=Engine.get(this);if(e.drums.pcm==null){e.message("Carga primero un loop de drums.");return;}
        android.widget.EditText input=new android.widget.EditText(this);input.setSingleLine(true);input.setText("Mi loop");
        new AlertDialog.Builder(this).setTitle("Guardar drums en tu biblioteca").setView(input).setPositiveButton("Guardar",(dialog,which)->e.drums.save(input.getText().toString())).setNegativeButton("Cancelar",null).show();
    }
    /** Explicit, honest fallback: audio appears only as Android captures playback, not immediately from the URL. */
    private void youtubeLiveFallback(String watch){
        if(watch==null||!watch.matches("https://www\\.youtube\\.com/watch\\?v=[A-Za-z0-9_-]{11}")){
            Engine.get(this).message("No hay un enlace de vídeo válido para abrir.");return;
        }
        new AlertDialog.Builder(this)
            .setTitle("No se ha podido importar el audio del enlace")
            .setMessage("El extractor no ha podido obtener el audio de YouTube. Puedes abrir este mismo vídeo y capturar el sonido que Android permita, sin guardar antes un WAV. La onda y los chops aparecerán según avance la reproducción (ventana de hasta 32 segundos); no se carga la canción entera de golpe.")
            .setPositiveButton("Abrir y capturar",(d,w)->{
                Engine e=Engine.get(this);
                e.inputWatch=watch;e.inputAll=false;e.inputRemote=false;e.inputWeb=false;
                e.inputPackage=YOUTUBE;e.inputLabel="YouTube";cachedPanel=false;
                enableOverlay();
            })
            .setNegativeButton("Cerrar",null).show();
    }
    private void youtube(){android.widget.EditText text=new android.widget.EditText(this);text.setSingleLine(true);text.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_URI);text.setHint("https://www.youtube.com/watch?v=…");new AlertDialog.Builder(this).setTitle("Enlace YouTube · experimental").setMessage("Se intenta conservar una fuente cada vez. Se resuelve el flujo con NewPipe Extractor y se carga en memoria, sin grabar la reproducción. La compatibilidad depende del vídeo y de su servidor multimedia. También puedes usar Compartir → ChopGO desde YouTube.").setView(text).setPositiveButton("Intentar cargar",(d,w)->YoutubeImporter.load(this,text.getText().toString())).setNegativeButton("Cancelar",null).show();}
    private void drumLibrary(){java.io.File[] files=Engine.get(this).drums.directory().listFiles(f->f.getName().endsWith(".wav"));if(files==null||files.length==0){Engine.get(this).message("Tu biblioteca está vacía. Carga un loop y pulsa Guardar.");return;}java.util.Arrays.sort(files);String[] names=new String[files.length];for(int i=0;i<files.length;i++)names[i]=files[i].getName();new AlertDialog.Builder(this).setTitle("Tus drums guardados").setItems(names,(d,i)->Engine.get(this).drums.loadSaved(files[i])).setNegativeButton("Cerrar",null).show();}
    private void drumMetadata(){Engine e=Engine.get(this);android.widget.EditText bpm=new android.widget.EditText(this);bpm.setInputType(2);bpm.setText(""+e.drums.originalBpm);new AlertDialog.Builder(this).setTitle("BPM de origen del loop").setView(bpm).setPositiveButton("Aplicar",(d,w)->{try{e.history.begin("BPM original de drums");e.drums.originalBpm=Math.max(30,Math.min(300,Integer.parseInt(bpm.getText().toString())));e.drums.update();e.history.end();}catch(Exception ex){e.message("Introduce un BPM válido.");}}).setNegativeButton("Cancelar",null).show();}
    private void metadata(boolean common){
        Engine e=Engine.get(this);if(e.busy){e.message("Espera a que termine el proceso");return;}Slots.Slot source=e.slots.list[e.slots.active];android.widget.LinearLayout box=new android.widget.LinearLayout(this);box.setOrientation(1);box.setPadding(24,12,24,12);
        android.widget.TextView label=new android.widget.TextView(this);label.setText(common?"Tempo y tonalidad de destino":"Datos originales (estimados; corrige si hace falta)");box.addView(label);
        android.widget.EditText bpm=new android.widget.EditText(this);bpm.setInputType(2);bpm.setText(""+(common?e.slots.masterBpm:source.bpm));box.addView(bpm);
        android.widget.Spinner key=new android.widget.Spinner(this);android.widget.ArrayAdapter<String> adapter=new android.widget.ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,Analysis.KEYS);key.setAdapter(adapter);key.setSelection(common?e.slots.masterKey:source.key);box.addView(key);
        android.widget.TextView note=new android.widget.TextView(this);note.setText("Cambiar tono transpone; no convierte una canción mayor en menor. La detección de tonalidad y BPM puede equivocarse.");box.addView(note);
        new AlertDialog.Builder(this).setTitle(common?"Destino común":"Original · fuente "+(e.slots.active+1)).setView(box).setPositiveButton("Aplicar",(d,w)->{try{e.history.begin("BPM y tonalidad");int value=Math.max(40,Math.min(240,Integer.parseInt(bpm.getText().toString())));if(common){e.slots.masterBpm=value;e.slots.masterKey=key.getSelectedItemPosition();e.drums.bpm=value;e.drums.update();}else{source.bpm=value;source.key=key.getSelectedItemPosition();if(e.liveMode){e.liveMetadataOverride=true;e.liveMetadataReady=true;}}if(common&&e.capturing){e.manualTransform=false;e.followLive();e.history.end();}else if(e.linkProject&&e.source!=null&&!e.capturing)e.slots.sync();else e.history.end();e.changed();}catch(Exception ex){e.message("Introduce un BPM válido.");}}).setNegativeButton("Cancelar",null).show();
    }
    private void fxOrder(){Engine e=Engine.get(this);java.util.ArrayList<int[]> orders=new java.util.ArrayList<>();for(int a=0;a<4;a++)for(int b=0;b<4;b++)for(int c=0;c<4;c++)for(int d=0;d<4;d++)if(a!=b&&a!=c&&a!=d&&b!=c&&b!=d&&c!=d)orders.add(new int[]{a,b,c,d});String[] groups={"Carácter","Movimiento","Espacio","Textura"},names=new String[24];for(int i=0;i<24;i++){int[] p=orders.get(i);names[i]=groups[p[0]]+" → "+groups[p[1]]+" → "+groups[p[2]]+" → "+groups[p[3]];}new AlertDialog.Builder(this).setTitle("Orden de los cuatro módulos").setItems(names,(dialog,i)->{e.history.edit("Orden FX",()->e.mutate.order=orders.get(i));e.message("Orden FX actualizado.");}).show();}
    private org.json.JSONObject settingsJson()throws Exception{Engine e=Engine.get(this);return new org.json.JSONObject().put("mutate",SettingsIO.write(e.mutate)).put("heat",SettingsIO.write(e.heat));}
    private void fxSave(){try{getSharedPreferences("exito",0).edit().putString("fx-v3",settingsJson().toString()).apply();Engine.get(this).message("Preset MUTATE + HEAT guardado");}catch(Exception ex){Engine.get(this).message("Preset: "+Engine.reason(ex));}}
    private void fxLoad(){Engine e=Engine.get(this);if(e.busy)return;try{String text=getSharedPreferences("exito",0).getString("fx-v3",null);if(text==null){e.message("No hay preset V03 guardado");return;}org.json.JSONObject o=new org.json.JSONObject(text);e.history.begin("Cargar preset FX");SettingsIO.read(o.getJSONObject("mutate"),e.mutate);SettingsIO.read(o.getJSONObject("heat"),e.heat);e.history.end();e.changed();}catch(Exception ex){e.history.cancel();e.message("Preset: "+Engine.reason(ex));}}
    @Override protected void onSaveInstanceState(Bundle b){b.putBoolean("videoLarge",videoPanel!=null);b.putString("savePath",savePath);b.putString("saveName",saveName);super.onSaveInstanceState(b);}
    @Override public void onRequestPermissionsResult(int r,String[] p,int[] grants){super.onRequestPermissionsResult(r,p,grants);if(r==82){if(grants.length>0&&grants[0]==PackageManager.PERMISSION_GRANTED)capture();else{Engine e=Engine.get(this);e.inputPrompt=false;e.liveStatus="Audio interno sin permiso. La entrada no está conectada.";e.message(e.liveStatus);}pending="";}if(r==83)enableOverlay();}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);
        if(request==101){if(Settings.canDrawOverlays(this))enableOverlay();else Engine.get(this).message("El panel necesita permiso para mostrarse sobre YouTube.");return;}
        if(result!=RESULT_OK||data==null){if(request==91){Engine e=Engine.get(this);e.inputPrompt=false;e.liveStatus="Entrada desconectada: no se autorizó el audio interno.";e.message(e.liveStatus);}return;}
        if(request==91){Intent service=new Intent(this,CaptureService.class).putExtra("result",result).putExtra("projection",data);startForegroundService(service);}
        if(request==121){Uri uri=data.getData();if(uri==null)return;try{getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}Engine e=Engine.get(this);e.webDeck.importFile(e.webDeck.selected,uri);return;}
        if(request==92||request==93||request==95){Uri uri=data.getData();if(uri==null)return;try{getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}if(request==93)Engine.get(this).drums.importLoop(uri);else Engine.get(this).importAudio(uri);}
        if(request==112){Uri uri=data.getData();if(uri!=null)Engine.get(this).projects.importFrom(uri);return;}
        if(request==113){Uri uri=data.getData();if(uri!=null)Engine.get(this).projects.exportTo(uri);return;}
        if(request==94){Uri uri=data.getData();if(uri==null)return;final String filePath=savePath,fileName=saveName;Engine e=Engine.get(this);e.worker.execute(()->{try(InputStream in=new FileInputStream(new File(getCacheDir(),filePath));OutputStream out=getContentResolver().openOutputStream(uri)){if(out==null)throw new IOException("Destino no disponible");byte[] block=new byte[16384];int n;while((n=in.read(block))!=-1)out.write(block,0,n);e.message("Archivo guardado: "+fileName);}catch(Exception ex){e.message("No se pudo guardar: "+Engine.reason(ex));}});}
    }
    private void autoPanel(){new AlertDialog.Builder(this).setTitle("Panel automático desactivado").setMessage("Por seguridad, la detección automática mediante Accesibilidad está desactivada en esta versión. Abre el panel manualmente desde YouTube directo o utiliza la importación/captura por enlace.").setPositiveButton("Entendido",null).show();}
        private void computer(){Engine e=Engine.get(this);if(e.busy)return;if(e.capturing&&!NetworkInputService.active){e.message("Desconecta la entrada del móvil antes de cambiar al PC");return;}e.inputRemote=true;e.inputAll=e.inputWeb=false;if(!NetworkInputService.active)startForegroundService(new Intent(this,NetworkInputService.class));e.main.postDelayed(()->{if(isFinishing())return;String url=NetworkInputService.address;new AlertDialog.Builder(this).setTitle("Audio del PC · misma Wi-Fi").setMessage("Abre este enlace en el ordenador:\n"+url+"\n\nDescarga el puente, ejecútalo y pega el enlace de emparejamiento. Comparte una pestaña con audio. El audio completo del sistema depende del navegador y del sistema operativo. El móvil recibe los últimos 32 segundos en memoria.").setPositiveButton("Copiar enlace",(d,w)->((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(android.content.ClipData.newPlainText("EXITO PC",url))).setNeutralButton("Desconectar",(d,w)->e.freezeForEdit()).setNegativeButton("Cerrar",null).show();},600);}
    private void exportKit(){Engine e=Engine.get(this);if(!e.ready()){e.message("Carga una fuente primero");return;}new AlertDialog.Builder(this).setTitle("Exportar todos los chops de esta fuente").setItems(ExportKit.TARGETS,(d,target)->new AlertDialog.Builder(this).setTitle(ExportKit.TARGETS[target]).setMessage("Se crea un ZIP con WAV separados e instrucciones. Los formatos experimentales y los paquetes de importación se identifican en LEEME. ¿Renderizar color, EQ y FX en el audio?").setPositiveButton("Con efectos",(a,b)->ExportKit.export(e,target,true)).setNeutralButton("Sin efectos",(a,b)->ExportKit.export(e,target,false)).setNegativeButton("Cancelar",null).show()).show();}
    private void midi(){Engine e=Engine.get(this);if(!e.ready())return;new AlertDialog.Builder(this).setTitle("MIDI · notas o disparos").setMessage("Basic Pitch estima notas localmente sobre el audio con tono/tempo, antes de los FX. No separa instrumentos de una mezcla; revisa falsas notas y octavas. Los disparos de pads son un archivo distinto.").setPositiveButton("Elegir",(a,b)->new AlertDialog.Builder(this).setTitle("Contenido").setItems(new String[]{"Notas de la fuente completa","Notas del chop seleccionado","Notas de fuente y de cada chop · ZIP","Disparos de pads del secuenciador"},(d,mode)->MidiTranscriber.export(e,mode)).show()).setNegativeButton("Cancelar",null).show();}
    private void harmonyMidi(){Engine e=Engine.get(this);e.worker.execute(()->{try{File dir=new File(getCacheDir(),"exports");dir.mkdirs();File f=new File(dir,"ChopGO_Instrumentos.mid");java.nio.file.Files.write(f.toPath(),e.harmony.midi());e.main.post(()->launch(this,"save-artifact",f.getName()));}catch(Exception ex){e.message("MIDI: "+Engine.reason(ex));}});}
    private void aiMenu(){new AlertDialog.Builder(this).setTitle("CHOPGO · Transformar audio")
      .setItems(new String[]{"IA LOCAL GRATUITA · transcribir melodía y tocarla con otro instrumento (Basic Pitch)",
       "Transformación generativa externa · Stability AI (clave y créditos de pago)"},(d,option)->{if(option==0)localCover();else aiCloud();})
      .setNegativeButton("Cerrar",null).show();}
    private void localCover(){Engine e=Engine.get(this);if(!e.ready()){e.message("Carga una muestra para hacer la transformación local");return;}
      LinearLayout body=new LinearLayout(this);body.setOrientation(1);body.setPadding(22,12,22,12);
      TextView note=new TextView(this);note.setText("IA local real: Basic Pitch estima las notas y ChopGO vuelve a interpretarlas con instrumentos de síntesis. Gratis, sin cuentas y sin enviar audio. NO genera un cover completo tipo Suno: los drums, voces y armonías complejas pueden perderse o detectarse mal. Máximo 45 s por transformación.");body.addView(note);
      android.widget.EditText prompt=new android.widget.EditText(this);prompt.setHint("Piano soul oscuro, vintage, sin batería…");prompt.setMinLines(3);body.addView(prompt);
      android.widget.Spinner span=new android.widget.Spinner(this);span.setAdapter(new android.widget.ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"Chop seleccionado","Fragmento inicial de 45 segundos"}));body.addView(span);
      new AlertDialog.Builder(this).setTitle("REINTERPRETAR · IA LOCAL").setView(body)
       .setPositiveButton("Generar gratis",(d,w)->LocalAiCover.generate(e,prompt.getText().toString(),span.getSelectedItemPosition()==0))
       .setNegativeButton("Cancelar",null).show();}
    private void aiCloud(){Engine e=Engine.get(this);if(!e.ready()&&!AiCover.running&&getSharedPreferences("ai-private",0).getString("pending","").isEmpty()){e.message("Carga una muestra primero");return;}android.widget.LinearLayout box=new android.widget.LinearLayout(this);box.setOrientation(1);box.setPadding(24,10,24,10);android.widget.TextView info=new android.widget.TextView(this);info.setText("Stable Audio 3 · referencia + prompt. Envía audio a Stability AI y consume créditos de tu cuenta (8 por generación según su tarifa actual). No es una generación gratuita ni local. No necesita tener un PC encendido. Referencias menores de 6 s se repiten hasta 6 s. No se permite enviar material que el proveedor prohíba.");box.addView(info);android.widget.EditText prompt=new android.widget.EditText(this);prompt.setHint("Recrea esta idea con piano soul cálido, bajo profundo y sin batería…");prompt.setMinLines(3);box.addView(prompt);android.widget.Spinner input=new android.widget.Spinner(this);input.setAdapter(new android.widget.ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"Chop seleccionado","Fuente completa · máximo 380 s"}));box.addView(input);android.widget.Spinner duration=new android.widget.Spinner(this);duration.setAdapter(new android.widget.ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"8 segundos","16 segundos","32 segundos","64 segundos","Duración de referencia"}));duration.setSelection(1);box.addView(duration);android.widget.TextView amount=new android.widget.TextView(this);amount.setText("Variación: 0 conserva referencia; 100 prioriza prompt");box.addView(amount);android.widget.SeekBar strength=new android.widget.SeekBar(this);strength.setMax(100);strength.setProgress(55);box.addView(strength);android.widget.EditText key=new android.widget.EditText(this);key.setSingleLine(true);key.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);try{key.setHint(KeyStoreSecret.get(this).isEmpty()?"Tu clave personal de Stability AI":"Clave guardada · deja vacío para usarla");}catch(Exception ignored){key.setHint("Tu clave personal de Stability AI");}box.addView(key);android.widget.Button account=new android.widget.Button(this);account.setText("Clave y créditos del proveedor");account.setOnClickListener(v->startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://platform.stability.ai/dashboard/api-keys"))));box.addView(account);android.widget.CheckBox rights=new android.widget.CheckBox(this);rights.setText("Mi referencia está autorizada y cumple las condiciones del proveedor. Autorizo enviarla y consumir mis créditos.");box.addView(rights);android.widget.Button recover=new android.widget.Button(this);recover.setText("Recuperar generación pendiente");recover.setOnClickListener(v->AiCover.resume(e));box.addView(recover);android.widget.ScrollView scroll=new android.widget.ScrollView(this);scroll.addView(box);AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Recrear muestra con IA").setView(scroll).setPositiveButton("Generar",null).setNeutralButton("Dejar de esperar",(d,w)->{AiCover.cancel();e.message("Se deja de esperar. El proveedor puede seguir generando y cobrar el resultado; puedes recuperarlo después.");}).setNegativeButton("Cerrar",null).create();dialog.setOnShowListener(v->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(button->{try{if(!rights.isChecked()){rights.setError("Confirma el envío y el coste");return;}if(prompt.getText().toString().trim().isEmpty()){prompt.setError("Escribe el prompt");return;}if(!key.getText().toString().trim().isEmpty())KeyStoreSecret.save(this,key.getText().toString());if(KeyStoreSecret.get(this).isEmpty()){key.setError("Hace falta tu clave personal");return;}AiCover.generate(e,prompt.getText().toString(),strength.getProgress()/100f,new int[]{8,16,32,64,0}[duration.getSelectedItemPosition()],input.getSelectedItemPosition()==1);dialog.dismiss();}catch(Exception ex){e.message("IA: "+Engine.reason(ex));}}));dialog.show();}

}
