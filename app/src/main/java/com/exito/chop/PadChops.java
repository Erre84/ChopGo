package com.exito.chop;

import java.util.Arrays;

/** Sequential pad marks in source frames, independent of stretch and screen refresh. */
final class PadChops {
    static final String EMPTY = "pads-empty", MODE = "pads";
    static boolean isMode(String mode) { return EMPTY.equals(mode) || MODE.equals(mode); }
    static int count(String mode, int[] cuts) {
        return EMPTY.equals(mode) ? 0 : Math.max(0, cuts.length - 1);
    }
    static int[] append(int[] cuts, boolean empty, int frame, int frames, int rate) {
        int gap = Math.max(1, Math.round(rate * .045f));
        if (frame < 0 || frame > frames - gap)
            throw new IllegalArgumentException("No queda suficiente audio. Escucha de nuevo o termina el marcado.");
        if (empty) return new int[]{frame, frames};
        if (cuts.length >= 65) throw new IllegalArgumentException("Máximo de 64 chops por fuente.");
        if (frame - cuts[cuts.length - 2] < gap)
            throw new IllegalArgumentException("Deja al menos 45 ms entre marcas.");
        int[] next = Arrays.copyOf(cuts, cuts.length + 1);
        next[next.length - 2] = frame;
        next[next.length - 1] = frames;
        return next;
    }
}
