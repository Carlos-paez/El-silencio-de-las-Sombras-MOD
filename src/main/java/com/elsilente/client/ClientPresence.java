package com.elsilente.client;

/** Nivel de presencia (0-100) sincronizado desde el servidor. Solo cliente. */
public final class ClientPresence {

    public static final int MAX = 100;

    private static int level = 0;

    private ClientPresence() {
    }

    public static int getLevel() {
        return level;
    }

    public static void setLevel(int value) {
        level = Math.max(0, Math.min(MAX, value));
    }
}
