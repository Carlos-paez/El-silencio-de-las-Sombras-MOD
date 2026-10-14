package com.elsilente.config;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Configuracion del mod (ForgeConfigSpec, seccion COMMON).
 *
 * Archivo: config/elsilente-common.toml. Todos los valores tienen defaults
 * equivalentes al comportamiento anterior (hardcodeado) para no romper partidas.
 */
public final class ElSilenteConfig {

    public static final ForgeConfigSpec COMMON_SPEC;
    public static final Common COMMON;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        COMMON = new Common(builder);
        COMMON_SPEC = builder.build();
    }

    private ElSilenteConfig() {
    }

    public static class Common {

        // --- Emboscadas (AmbushHandler) ---
        public final ForgeConfigSpec.BooleanValue ambushEnabled;
        public final ForgeConfigSpec.DoubleValue ambushChance;
        public final ForgeConfigSpec.IntValue ambushCooldownTicks;

        // --- Robo de botin (TheftHandler) ---
        public final ForgeConfigSpec.BooleanValue theftEnabled;

        // --- Antorchas: El Silente apaga las luces cercanas en STALK ---
        public final ForgeConfigSpec.BooleanValue snuffLightsEnabled;

        // --- Spawn natural ---
        public final ForgeConfigSpec.IntValue maxShadowsPerPlayer;

        // --- Presencia acumulativa (PresenceHandler + FX client) ---
        public final ForgeConfigSpec.BooleanValue presenceEnabled;

        // --- La base deja de ser segura (abrir puertas al acechar) ---
        public final ForgeConfigSpec.BooleanValue openDoorsEnabled;

        // --- Defensas activas con coste (sal, campana, simbolo) ---
        public final ForgeConfigSpec.BooleanValue saltEnabled;
        public final ForgeConfigSpec.BooleanValue bellEnabled;
        public final ForgeConfigSpec.IntValue symbolCharges;

        Common(ForgeConfigSpec.Builder builder) {
            builder.push("general");

            ambushEnabled = builder
                    .comment("Activa las emboscadas sorpresa de El Silente por la noche.")
                    .define("ambushEnabled", true);
            ambushChance = builder
                    .comment("Probabilidad por comprobacion de que ocurra una emboscada (0.0 - 1.0).")
                    .defineInRange("ambushChance", 0.20D, 0.0D, 1.0D);
            ambushCooldownTicks = builder
                    .comment("Enfriamiento minimo entre emboscadas para el mismo jugador (20 ticks = 1 s).")
                    .defineInRange("ambushCooldownTicks", 12000, 1200, 120000);

            theftEnabled = builder
                    .comment("Si El Silente te mata, roba un objeto y lo esconde en un Cofre del Silente.")
                    .define("theftEnabled", true);

            snuffLightsEnabled = builder
                    .comment("En fase STALK, El Silente apaga las antorchas/lanternas/candelas cercanas.")
                    .define("snuffLightsEnabled", true);

            builder.pop();
            builder.push("spawns");

            maxShadowsPerPlayer = builder
                    .comment("Maximo de Sombras Debiles vivas por jugador.")
                    .defineInRange("maxShadowsPerPlayer", 6, 1, 24);

            builder.pop();
            builder.push("presence");

            presenceEnabled = builder
                    .comment("Sistema de presencia acumulativa: escalada de susurros, niebla y alucinaciones.")
                    .define("presenceEnabled", true);

            openDoorsEnabled = builder
                    .comment("En fase STALK, El Silente abre las puertas de madera para cruzar hacia ti.")
                    .define("openDoorsEnabled", true);

            builder.pop();
            builder.push("defenses");

            saltEnabled = builder
                    .comment("La Linea de sal bloquea el spawn natural de Sombras en su radio (no las emboscadas).")
                    .define("saltEnabled", true);
            bellEnabled = builder
                    .comment("La Campana de vigilia cancela la emboscada de esa noche y atrae Sombras Debiles.")
                    .define("bellEnabled", true);
            symbolCharges = builder
                    .comment("Cargas de luz del Simbolo del Alba (recarga con un Corazon Silente).")
                    .defineInRange("symbolCharges", 3, 1, 9);

            builder.pop();
        }
    }
}
