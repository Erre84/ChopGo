package com.exito.chop;
import android.app.*;
import android.content.*;

final class Notifs {
    static Notification make(Context c,String text,Class<?> service){
        NotificationManager nm=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationChannel channel=new NotificationChannel("exito_active","ChopGO activo",NotificationManager.IMPORTANCE_LOW);channel.setSound(null,null);nm.createNotificationChannel(channel);
        PendingIntent open=PendingIntent.getActivity(c,0,new Intent(c,MainActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent stop=PendingIntent.getService(c,service==CaptureService.class?3:2,new Intent(c,service).setAction("stop"),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Builder(c,"exito_active").setSmallIcon(android.R.drawable.ic_btn_speak_now).setContentTitle("ChopGO").setContentText(text).setContentIntent(open).setOngoing(true).addAction(new Notification.Action.Builder(null,"Detener",stop).build()).build();
    }
}
