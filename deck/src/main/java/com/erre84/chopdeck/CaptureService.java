package com.erre84.chopdeck;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioPlaybackCaptureConfiguration;
import android.media.AudioRecord;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;

/**
 * Captura el audio que suena en otras apps (YouTube, Chrome…) en un buffer circular
 * de 3 minutos. Basado en la entrada en directo de ChopGO.
 * Mientras la pantalla de ChopDeck está delante, no graba (para no capturarse a sí misma).
 */
public final class CaptureService extends Service {
    static final int SR = 44100;
    static final int MAXS = 180;
    static volatile CaptureService current;
    /** true = no añadir audio (la propia app está en primer plano). */
    static volatile boolean ignore = true;
    static volatile String error = "";

    private final short[] ring = new short[SR * 2 * MAXS];
    private long written = 0;   // frames escritos en total
    private long mark = 0;      // frames ya entregados como deck
    private long signalFrames = 0; // frames con señal desde la última toma
    volatile float db = -96f;
    volatile long lastSignal = 0;
    private volatile boolean running = false;
    private MediaProjection projection;
    private AudioRecord rec;

    @Override public IBinder onBind(Intent i) { return null; }

    static boolean isRunning() { CaptureService c = current; return c != null && c.running; }

    private Notification note(String text) {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        NotificationChannel ch = new NotificationChannel("capture", "Captura de audio", NotificationManager.IMPORTANCE_LOW);
        nm.createNotificationChannel(ch);
        PendingIntent open = PendingIntent.getActivity(this, 1, new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop = PendingIntent.getService(this, 2, new Intent(this, CaptureService.class).setAction("stop"), PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, "capture")
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentTitle("ChopDeck SP · grabando")
                .setContentText(text)
                .setContentIntent(open)
                .setOngoing(true)
                .addAction(new Notification.Action.Builder(null, "VOLVER A CHOPDECK", open).build())
                .addAction(new Notification.Action.Builder(null, "PARAR", stop).build())
                .build();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || "stop".equals(intent.getAction())) { halt(); return START_NOT_STICKY; }
        if (running) return START_NOT_STICKY;
        try {
            Notification n = note("Reproduce el sample en YouTube o Chrome y vuelve a ChopDeck");
            if (Build.VERSION.SDK_INT >= 29) startForeground(77, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
            else startForeground(77, n);
            Intent data = intent.getParcelableExtra("data");
            int result = intent.getIntExtra("result", Activity.RESULT_CANCELED);
            MediaProjectionManager mpm = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
            projection = mpm.getMediaProjection(result, data);
            if (projection == null) throw new IllegalStateException("Permiso de captura denegado");
            projection.registerCallback(new MediaProjection.Callback() {
                @Override public void onStop() { halt(); }
            }, new Handler(Looper.getMainLooper()));
            AudioPlaybackCaptureConfiguration cfg = new AudioPlaybackCaptureConfiguration.Builder(projection)
                    .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                    .addMatchingUsage(AudioAttributes.USAGE_GAME)
                    .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                    .build();
            int min = AudioRecord.getMinBufferSize(SR, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT);
            rec = new AudioRecord.Builder()
                    .setAudioFormat(new AudioFormat.Builder().setSampleRate(SR)
                            .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
                    .setAudioPlaybackCaptureConfig(cfg)
                    .setBufferSizeInBytes(Math.max(32768, min * 4))
                    .build();
            if (rec.getState() != AudioRecord.STATE_INITIALIZED) throw new IllegalStateException("Este móvil no permite la captura interna");
            synchronized (this) { written = 0; mark = 0; signalFrames = 0; }
            error = "";
            running = true;
            current = this;
            rec.startRecording();
            new Thread(this::loop, "chopdeck-capture").start();
        } catch (Throwable t) {
            error = String.valueOf(t.getMessage());
            halt();
        }
        return START_NOT_STICKY;
    }

    private void loop() {
        short[] block = new short[4096];
        while (running) {
            AudioRecord r = rec;
            if (r == null) break;
            int n = r.read(block, 0, block.length);
            if (n <= 0) { if (n < 0) { error = "Error de lectura " + n; break; } continue; }
            n -= n % 2;
            int peak = 0;
            for (int i = 0; i < n; i++) { int a = Math.abs((int) block[i]); if (a > peak) peak = a; }
            db = (float) Math.max(-96, 20 * Math.log10(Math.max(1, peak) / 32768.0));
            if (ignore) continue;
            boolean sig = peak >= 40;
            if (sig) lastSignal = SystemClock.elapsedRealtime();
            synchronized (this) {
                int frames = n / 2;
                int cap = SR * MAXS;
                for (int f = 0; f < frames; f++) {
                    int pos = (int) ((written + f) % cap) * 2;
                    ring[pos] = block[f * 2];
                    ring[pos + 1] = block[f * 2 + 1];
                }
                written += frames;
                if (sig || signalFrames > 0) signalFrames += frames;
            }
        }
        running = false;
    }

    /** Segundos de audio con señal pendientes de convertir en deck. */
    synchronized double fresh() {
        long avail = Math.min(written - mark, (long) SR * MAXS);
        return Math.min(avail, signalFrames) / (double) SR;
    }

    /** Devuelve los últimos segundos pendientes (desde la última toma), sin silencios al principio ni al final. */
    synchronized short[] take(int seconds) {
        int cap = SR * MAXS;
        long avail = Math.min(written - mark, (long) cap);
        long want = Math.min(avail, (long) seconds * SR);
        if (want <= 0) return new short[0];
        long start = written - want;
        short[] out = new short[(int) want * 2];
        for (long f = 0; f < want; f++) {
            int pos = (int) ((start + f) % cap) * 2;
            out[(int) f * 2] = ring[pos];
            out[(int) f * 2 + 1] = ring[pos + 1];
        }
        mark = written;
        signalFrames = 0;
        int a = 0, b = (int) want;
        while (a < b && Math.abs(out[a * 2]) < 40 && Math.abs(out[a * 2 + 1]) < 40) a++;
        while (b > a && Math.abs(out[(b - 1) * 2]) < 40 && Math.abs(out[(b - 1) * 2 + 1]) < 40) b--;
        a = Math.max(0, a - SR / 100);
        b = Math.min((int) want, b + SR / 10);
        if (b - a < SR / 4) return new short[0];
        short[] trimmed = new short[(b - a) * 2];
        System.arraycopy(out, a * 2, trimmed, 0, trimmed.length);
        return trimmed;
    }

    void halt() {
        running = false;
        try { if (rec != null) { rec.stop(); rec.release(); } } catch (Throwable ignored) { }
        rec = null;
        try { if (projection != null) projection.stop(); } catch (Throwable ignored) { }
        projection = null;
        try { stopForeground(STOP_FOREGROUND_REMOVE); } catch (Throwable ignored) { }
        stopSelf();
    }

    // El buffer sigue disponible tras parar, para poder traer lo último capturado.
    @Override public void onDestroy() { running = false; super.onDestroy(); }
}
