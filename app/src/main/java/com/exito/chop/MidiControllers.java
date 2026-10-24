package com.exito.chop;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.media.midi.MidiDevice;
import android.media.midi.MidiDeviceInfo;
import android.media.midi.MidiDeviceStatus;
import android.media.midi.MidiManager;
import android.media.midi.MidiOutputPort;
import android.media.midi.MidiReceiver;
import android.os.Handler;
import android.os.Looper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import org.json.JSONObject;

/** USB / Android-system MIDI input with per-device learn maps (no SLAB proprietary protocol). */
final class MidiControllers {
    static final String[] LABELS={
        "Pad 01","Pad 02","Pad 03","Pad 04","Pad 05","Pad 06","Pad 07","Pad 08",
        "Pad 09","Pad 10","Pad 11","Pad 12","Pad 13","Pad 14","Pad 15","Pad 16",
        "Banco A","Banco B","Banco C","Banco D", "Fuente anterior","Fuente siguiente",
        "Reproducir fuente","Parar todo","Secuenciador ON/OFF","Drums ON/OFF",
        "Pitch ±12 semitonos","Tempo 50–200 %","Volumen drums","Compresión","Reverb",
        "Filtro · tono FX","Mezcla FX","Cantidad FX 1","Cantidad FX 2","Cantidad FX 3","Cantidad FX 4"
    };
    static final String[] ACTIONS={
        "pad:0","pad:1","pad:2","pad:3","pad:4","pad:5","pad:6","pad:7",
        "pad:8","pad:9","pad:10","pad:11","pad:12","pad:13","pad:14","pad:15",
        "bank:0","bank:1","bank:2","bank:3", "source-prev","source-next",
        "play","stop","sequence","drums", "pitch","tempo","drum-volume","compression","reverb",
        "filter","fx-mix","fx:0","fx:1","fx:2","fx:3"
    };
    private final Context context;
    private final Engine engine;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final MidiManager manager;
    private final SharedPreferences prefs;
    private final Map<String,String> assignments=new HashMap<>();
    private final Map<Integer,Integer> heldVoices=new HashMap<>();
    private final MidiStreamDecoder decoder;
    private MidiDevice opened;
    private MidiOutputPort outputPort;
    private MidiDeviceInfo selected;
    private String currentName="", learning=null;
    private boolean active;
    private final MidiReceiver input=new MidiReceiver(){
        @Override public void onSend(byte[] data,int offset,int count,long timestamp){decoder.feed(data,offset,count);}
    };
    private final MidiManager.DeviceCallback hotplug=new MidiManager.DeviceCallback(){
        @Override public void onDeviceAdded(MidiDeviceInfo info){
            if(selected==null&&hasInput(info)&&label(info).equals(prefs.getString("last-device","")))connect(info);
        }
        @Override public void onDeviceRemoved(MidiDeviceInfo info){
            if(selected!=null&&selected.getId()==info.getId()){disconnect();engine.message("Controladora MIDI desconectada");}
        }
        @Override public void onDeviceStatusChanged(MidiDeviceStatus status){}
    };
    MidiControllers(Context context,Engine engine){
        this.context=context.getApplicationContext();this.engine=engine;
        manager=(MidiManager)context.getSystemService(Context.MIDI_SERVICE);
        prefs=context.getSharedPreferences("exito-midi-maps",Context.MODE_PRIVATE);
        decoder=new MidiStreamDecoder((status,a,b)->handler.post(()->receive(status,a,b)));
    }
    void start(){if(manager==null||active)return;active=true;manager.registerDeviceCallback(hotplug,handler);
        String last=prefs.getString("last-device","");for(MidiDeviceInfo i:manager.getDevices())if(hasInput(i)&&label(i).equals(last)){connect(i);break;}
    }
    void close(){active=false;if(manager!=null)manager.unregisterDeviceCallback(hotplug);disconnect();}
    static boolean hasInput(MidiDeviceInfo info){return info.getOutputPortCount()>0;}
    static String label(MidiDeviceInfo info){
        String name=info.getProperties().getString(MidiDeviceInfo.PROPERTY_NAME);
        if(name==null||name.trim().isEmpty())name=info.getProperties().getString(MidiDeviceInfo.PROPERTY_PRODUCT);
        if(name==null||name.trim().isEmpty())name="Controladora MIDI";
        return name;
    }
    void disconnect(){
        learning=null;for(Integer id:heldVoices.values())if(engine.gate)engine.output.off(id);heldVoices.clear();
        if(outputPort!=null){outputPort.disconnect(input);try{outputPort.close();}catch(IOException ignored){}outputPort=null;}
        if(opened!=null){try{opened.close();}catch(IOException ignored){}opened=null;}
        selected=null;currentName="";assignments.clear();
    }
    void connect(MidiDeviceInfo info){
        if(manager==null||!hasInput(info))return;
        disconnect();selected=info;currentName=label(info);final int id=info.getId();
        prefs.edit().putString("last-device",currentName).apply();loadMap();
        manager.openDevice(info,device->{
            if(!active||selected==null||selected.getId()!=id){if(device!=null)try{device.close();}catch(IOException ignored){}return;}
            if(device==null){engine.message("No se pudo abrir MIDI: "+currentName);return;}
            opened=device;
            for(int n=0;n<info.getOutputPortCount();n++){
                MidiOutputPort port=device.openOutputPort(n);
                if(port!=null){outputPort=port;port.connect(input);engine.message("MIDI conectado: "+currentName+" · puerto "+(n+1));return;}
            }
            engine.message("Controladora sin salida MIDI accesible");
        },handler);
    }
    void showMenu(MainActivity host){
        String summary=currentName.isEmpty()?"Sin controladora conectada":"Conectada: "+currentName;
        new AlertDialog.Builder(host).setTitle("CONTROLADORAS MIDI · USB-C")
          .setMessage(summary+"\nSLAB: ponla en modo MIDI universal. Usa USB OTG o un hub alimentado cuando sea necesario.")
          .setItems(new String[]{"Seleccionar controladora","MIDI Learn · asignar control","Ver asignaciones","Borrar asignaciones de esta controladora"},
            (d,which)->{if(which==0)chooseDevice(host);if(which==1)chooseAction(host);if(which==2)showAssignments(host);if(which==3)clearMap(host);})
          .setNegativeButton("Cerrar",null).show();
    }
    private void chooseDevice(MainActivity host){
        if(manager==null){engine.message("Este móvil no ofrece el servicio MIDI de Android");return;}
        ArrayList<MidiDeviceInfo> devices=new ArrayList<>();
        for(MidiDeviceInfo info:manager.getDevices())if(hasInput(info))devices.add(info);
        if(devices.isEmpty()){
            new AlertDialog.Builder(host).setTitle("Sin dispositivos MIDI")
              .setMessage("Conecta una controladora MIDI por USB-C/OTG, activa su modo MIDI general y vuelve a intentarlo. No todas las controladoras ni hubs son compatibles con Android.")
              .setPositiveButton("Entendido",null).show();return;
        }
        String[] names=new String[devices.size()];
        for(int i=0;i<names.length;i++)names[i]=label(devices.get(i))+" · "+devices.get(i).getOutputPortCount()+" puertos";
        new AlertDialog.Builder(host).setTitle("Selecciona entrada MIDI").setItems(names,(d,i)->connect(devices.get(i))).setNegativeButton("Cancelar",null).show();
    }
    private void chooseAction(MainActivity host){
        if(selected==null||outputPort==null){new AlertDialog.Builder(host).setMessage("Primero conecta una controladora MIDI.").setPositiveButton("Elegir controladora",(d,w)->chooseDevice(host)).setNegativeButton("Cerrar",null).show();return;}
        new AlertDialog.Builder(host).setTitle("MIDI Learn · elegir función").setItems(LABELS,(d,i)->{
            learning=ACTIONS[i];engine.message("MIDI Learn: mueve un knob o pulsa un pad para «"+LABELS[i]+"»");
        }).setNegativeButton("Cancelar",null).show();
    }
    private void showAssignments(MainActivity host){
        StringBuilder s=new StringBuilder();for(Map.Entry<String,String> e:assignments.entrySet())s.append(e.getKey()).append(" → ").append(e.getValue()).append('\n');
        new AlertDialog.Builder(host).setTitle("Mapa MIDI · "+currentName).setMessage(s.length()==0?"Sin asignaciones personalizadas. Por defecto, las notas 36–51 disparan los 16 pads del banco seleccionado.":s.toString())
          .setPositiveButton("Cerrar",null).show();
    }
    private void clearMap(MainActivity host){
        if(currentName.isEmpty())return;
        new AlertDialog.Builder(host).setTitle("Borrar mapa MIDI").setMessage("Se borrarán las asignaciones de «"+currentName+"», no los proyectos de audio.")
           .setPositiveButton("Borrar",(d,w)->{assignments.clear();saveMap();engine.message("Asignaciones MIDI borradas");}).setNegativeButton("Cancelar",null).show();
    }
    private void loadMap(){assignments.clear();try{
        JSONObject json=new JSONObject(prefs.getString("map:"+currentName,"{}"));
        java.util.Iterator<String> keys=json.keys();while(keys.hasNext()){String key=keys.next();String target=json.optString(key,"");
          for(String valid:ACTIONS)if(valid.equals(target))assignments.put(key,target);}
    }catch(Exception ignored){}}
    private void saveMap(){JSONObject json=new JSONObject(assignments);prefs.edit().putString("map:"+currentName,json.toString()).apply();}
    private void receive(int status,int first,int second){
        if(!active||selected==null||outputPort==null)return;
        int type=status&0xf0,channel=status&15;
        if(type!=0x80&&type!=0x90&&type!=0xb0)return;
        boolean cc=type==0xb0;
        String key=(cc?"CC":"NOTE")+(channel+1)+":"+first;
        int voiceKey=(cc?0x10000:0)|(channel<<8)|first;
        boolean released=type==0x80||(type==0x90&&second==0);
        if(released){release(voiceKey);return;}
        if(learning!=null){
            if(cc||type==0x90){assignments.put(key,learning);saveMap();engine.message("MIDI Learn guardado: "+key+" → "+learning);learning=null;}
            return;
        }
        String target=assignments.get(key);
        if(target==null&&type==0x90&&first>=36&&first<52)target="pad:"+(first-36);
        if(target==null)return;
        if(target.startsWith("pad:")){
            if(cc&&second<64){release(voiceKey);return;}
            if(cc&&heldVoices.containsKey(voiceKey))return;
            int index=Integer.parseInt(target.substring(4));
            int id=engine.tapPad(engine.midiBank*16+index);
            if(id>=0)heldVoices.put(voiceKey,id);
            return;
        }
        // 7-bit CC values are continuous; notes/buttons trigger only on press.
        if(cc){
            float norm=second/127f;
            if("pitch".equals(target)){engine.setPitch(Math.round(norm*24)-12);return;}
            if("tempo".equals(target)){engine.setTempo(.5f+norm*1.5f);return;}
            if("drum-volume".equals(target)){engine.drums.volume=norm;engine.changed();return;}
            if("compression".equals(target)){engine.compression=norm*100;engine.changed();return;}
            if("reverb".equals(target)){engine.reverb=norm*.5f;engine.changed();return;}
            if("filter".equals(target)){engine.mutate.tone=norm;engine.changed();return;}
            if("fx-mix".equals(target)){engine.mutate.mix=norm;engine.changed();return;}
            if(target.startsWith("fx:")){engine.mutate.amount(Integer.parseInt(target.substring(3)),norm);engine.changed();return;}
            if(second<64)return;
        }
        switch(target){
            case "bank:0":case "bank:1":case "bank:2":case "bank:3":engine.midiBank=target.charAt(5)-'0';engine.changed();break;
            case "source-prev":engine.slots.activate(Math.max(0,engine.slots.active-1));break;
            case "source-next":engine.slots.activate(Math.min(7,engine.slots.active+1));break;
            case "play":engine.playAll();break;
            case "stop":engine.harmony.stop();engine.sequencer.stop();engine.drums.stop();engine.stop();break;
            case "sequence":engine.sequencer.toggle();break;
            case "drums":engine.drums.toggle();break;
        }
    }
    private void release(int voiceKey){Integer id=heldVoices.remove(voiceKey);if(id!=null&&engine.gate)engine.output.off(id);}
}
