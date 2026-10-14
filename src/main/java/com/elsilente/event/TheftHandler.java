package com.elsilente.event;

import com.elsilente.ElSilenteMod;
import com.elsilente.config.ElSilenteConfig;
import com.elsilente.entity.ElSilenteEntity;
import com.elsilente.util.Spawns;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Cuando El Silente mata a un jugador, le roba UN objeto (con peso x3 para
 * herramientas, armas y armaduras) y lo esconde en un cofre cercano.
 */
@Mod.EventBusSubscriber(modid = ElSilenteMod.MODID)
public class TheftHandler {

    /**
     * Cofres del Silente sin abrir por jugador (posiciones). El Amuleto del
     * Latido late hacia el botin robado mientras este mapa tenga una entrada
     * valida (se limpia al abrir el cofre). Es estado de sesion: tras un
     * reinicio el rastro se pierde pero el cofre sigue llamandose
     * "Cofre del Silente".
     */
    private static final Map<UUID, BlockPos> STOLEN_CHESTS = new HashMap<>();

    /** Posicion del Cofre del Silente del jugador, o null si no hay botin pendiente. */
    public static BlockPos getStolenChest(UUID playerId) {
        return STOLEN_CHESTS.get(playerId);
    }

    /** Al abrir el cofre se recupera el botin y se corta el rastro. */
    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }
        BlockPos stored = STOLEN_CHESTS.get(event.getEntity().getUUID());
        if (stored != null && stored.equals(event.getPos())) {
            STOLEN_CHESTS.remove(event.getEntity().getUUID());
            // «Recupera el botín» (§9): recuperaste lo que El Silente te robó.
            if (event.getEntity() instanceof ServerPlayer player) {
                AdvancementHandler.award(player, AdvancementHandler.RECUPERA_BOTIN, "recuperado");
            }
        }
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (!(event.getSource().getEntity() instanceof ElSilenteEntity)) {
            return;
        }
        if (!ElSilenteConfig.COMMON.theftEnabled.get()) {
            return;
        }

        ServerLevel level = (ServerLevel) player.level();
        Inventory inventory = player.getInventory();

        List<ItemStack> candidates = new ArrayList<>();
        List<Integer> kinds = new ArrayList<>();
        List<Integer> indices = new ArrayList<>();
        collect(inventory.items, 0, candidates, kinds, indices);
        collect(inventory.armor, 1, candidates, kinds, indices);
        collect(inventory.offhand, 2, candidates, kinds, indices);

        if (candidates.isEmpty()) {
            return;
        }

        int total = 0;
        int[] weights = new int[candidates.size()];
        for (int i = 0; i < candidates.size(); i++) {
            weights[i] = candidates.get(i).isDamageableItem() ? 3 : 1;
            total += weights[i];
        }
        int roll = player.getRandom().nextInt(total);
        int chosen = 0;
        for (int i = 0; i < weights.length; i++) {
            roll -= weights[i];
            if (roll < 0) {
                chosen = i;
                break;
            }
        }

        BlockPos spot = Spawns.findChestSpot(level, player.blockPosition(), 20, 40);
        if (spot == null) {
            return;
        }

        ItemStack stolen = candidates.get(chosen).copy();
        clearSlot(inventory, kinds.get(chosen), indices.get(chosen));

        level.setBlock(spot, Blocks.CHEST.defaultBlockState(), 3);
        if (level.getBlockEntity(spot) instanceof ChestBlockEntity chest) {
            chest.setItem(0, stolen);
            chest.setCustomName(Component.literal("Cofre del Silente"));
            chest.setChanged();
        }

        STOLEN_CHESTS.put(player.getUUID(), spot);

        player.sendSystemMessage(Component.literal("Algo ha sido tomado de ti...")
                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        player.sendSystemMessage(Component.literal("El Amuleto del Latido latira hacia lo robado.")
                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }

    private static void collect(List<ItemStack> source, int kind, List<ItemStack> candidates,
                                List<Integer> kinds, List<Integer> indices) {
        for (int i = 0; i < source.size(); i++) {
            if (!source.get(i).isEmpty()) {
                candidates.add(source.get(i));
                kinds.add(kind);
                indices.add(i);
            }
        }
    }

    private static void clearSlot(Inventory inventory, int kind, int index) {
        switch (kind) {
            case 0 -> inventory.items.set(index, ItemStack.EMPTY);
            case 1 -> inventory.armor.set(index, ItemStack.EMPTY);
            case 2 -> inventory.offhand.set(index, ItemStack.EMPTY);
            default -> {
            }
        }
    }
}
