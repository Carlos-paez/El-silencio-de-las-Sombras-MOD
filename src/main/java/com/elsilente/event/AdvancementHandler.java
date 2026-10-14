package com.elsilente.event;

import com.elsilente.ElSilenteMod;
import net.minecraft.advancements.Advancement;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Advances de narrativa in-game (§9 de MEJORAS.md).
 *
 * Algunos advancements no pueden usar triggers de vanilla (recuperar el botin
 * o invocar al Umbral son hechos propios del mod), asi que se conceden por
 * codigo con criterios "minecraft:impossible" definidos en los JSON de
 * data/elsilente/advancements/.
 */
@Mod.EventBusSubscriber(modid = ElSilenteMod.MODID)
public final class AdvancementHandler {

    public static final String RECUPERA_BOTIN = "recupera_botin";
    public static final String INVOCA_UMBRAL = "invoca_umbral";
    public static final String SOBREVIVE = "sobrevive_emboscada";

    private AdvancementHandler() {
    }

    /** Concede un criterio de un advancement del mod si existe. */
    public static void award(ServerPlayer player, String path, String criterion) {
        Advancement advancement = player.serverLevel().getServer().getAdvancements()
                .getAdvancement(new ResourceLocation(ElSilenteMod.MODID, path));
        if (advancement != null) {
            player.getAdvancements().award(advancement, criterion);
        }
    }

    /**
     * «Sobrevive a una emboscada»: se concede al matar a El Silente o cuando
     * el encargo se desvanace con tu vida (la otra via esta en vanish()).
     */
    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }
        if (!(event.getEntity() instanceof com.elsilente.entity.ElSilenteEntity)) {
            return;
        }
        if (event.getSource().getEntity() instanceof ServerPlayer player) {
            award(player, SOBREVIVE, "venciste");
        }
    }
}
