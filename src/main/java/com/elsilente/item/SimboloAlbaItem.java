package com.elsilente.item;

import com.elsilente.ElSilenteMod;
import com.elsilente.config.ElSilenteConfig;
import com.elsilente.entity.ElSilenteEntity;
import com.elsilente.entity.SombraDebilEntity;
import com.elsilente.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * ARMA SIMBOLICA (elsilente:simbolo_alba) — versión mejorada (§7 de MEJORAS.md).
 *
 * El UNICO objeto capaz de dañar a las Sombras Debiles, que son invulnerables
 * al resto de fuentes de daño. Daño total 6 (1 de base + 3 del arma + 2 del
 * material). Ahora gestiona CARGAS DE LUZ:
 *
 *  - Golpe cargado contra El Silente: consume 1 carga y añade +6 de daño.
 *  - Clic derecho: pulso de alba que disipa Sombras a 12 bloques (1 carga).
 *  - Recarga (agachado + clic derecho): consume 1 Corazon Silente del
 *    inventario y rellena todas las cargas.
 *
 * Maximo de cargas configurable (ElSilenteConfig.symbolCharges).
 */
public class SimboloAlbaItem extends SwordItem {

    /** NBT con las cargas de luz restantes. */
    private static final String TAG_CHARGES = "AlbaCargas";
    /** Radio del pulso de alba (bloques). */
    private static final double PULSE_RADIUS = 12.0D;
    /** Daño adicional del golpe cargado contra El Silente. */
    private static final float CHARGED_DAMAGE = 6.0F;

    private static final Tier TIER = new Tier() {
        @Override
        public int getUses() {
            return 250;
        }

        @Override
        public float getSpeed() {
            return 7.0F;
        }

        @Override
        public float getAttackDamageBonus() {
            return 2.0F;
        }

        @Override
        public int getLevel() {
            return 2;
        }

        @Override
        public int getEnchantmentValue() {
            return 14;
        }

        @Override
        public Ingredient getRepairIngredient() {
            return Ingredient.of(Items.OBSIDIAN);
        }
    };

    public SimboloAlbaItem(Properties properties) {
        // modifier 3 + bonus de material 2 + base 1 = 6 de daño total.
        super(TIER, 3, -2.2F, properties);
    }

    // -------------------------------------------------------------------------
    // Cargas de luz
    // -------------------------------------------------------------------------

    public static int maxCharges() {
        return ElSilenteConfig.COMMON.symbolCharges.get();
    }

    /** Cargas restantes; sin NBT se asume lleno (primer uso pilla el maximo). */
    public static int getCharges(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(TAG_CHARGES)) {
            return maxCharges();
        }
        return Math.max(0, Math.min(maxCharges(), tag.getInt(TAG_CHARGES)));
    }

    public static void setCharges(ItemStack stack, int value) {
        stack.getOrCreateTag().putInt(TAG_CHARGES, Math.max(0, Math.min(maxCharges(), value)));
    }

    // -------------------------------------------------------------------------
    // Golpe cargado contra El Silente
    // -------------------------------------------------------------------------

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        boolean hit = super.hurtEnemy(stack, target, attacker);
        if (!hit || !(target instanceof ElSilenteEntity) || attacker.level().isClientSide) {
            return hit;
        }
        int charges = getCharges(stack);
        if (charges <= 0) {
            return hit;
        }
        setCharges(stack, charges - 1);

        // La luz sagrada hiere a El Silente: +6 de daño. Hay que saltarse la
        // ventana de invulnerabilidad del golpe base (10 ticks) para que el
        // bonus se aplique en el mismo golpe.
        if (attacker instanceof Player player) {
            int invulnerable = target.invulnerableTime;
            target.invulnerableTime = 0;
            target.hurt(player.damageSources().playerAttack(player), CHARGED_DAMAGE);
            target.invulnerableTime = invulnerable;
        }
        if (attacker.level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.FLASH, target.getX(), target.getY() + 1.0D,
                    target.getZ(), 3, 0.0D, 0.0D, 0.0D, 0.0D);
            server.sendParticles(ParticleTypes.ASH, target.getX(), target.getY() + 1.0D,
                    target.getZ(), 12, 0.3D, 0.5D, 0.3D, 0.02D);
        }
        attacker.level().playSound(null, target.blockPosition(), SoundEvents.BEACON_POWER_SELECT,
                SoundSource.PLAYERS, 0.7F, 1.4F);
        return hit;
    }

    // -------------------------------------------------------------------------
    // Clic derecho: pulso de alba (disipa Sombras a distancia) o recarga
    // -------------------------------------------------------------------------

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            return InteractionResultHolder.success(stack);
        }

        int charges = getCharges(stack);
        int max = maxCharges();

        // Agachado: recarga con un Corazon Silente del inventario.
        if (player.isShiftKeyDown()) {
            if (charges >= max) {
                return InteractionResultHolder.pass(stack);
            }
            int slot = findHeart(player);
            if (slot < 0) {
                player.displayClientMessage(Component.translatable("elsilente.message.simbolo.no_hearts")
                        .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC), true);
                return InteractionResultHolder.fail(stack);
            }
            player.getInventory().items.get(slot).shrink(1);
            setCharges(stack, max);
            player.displayClientMessage(Component.translatable("elsilente.message.simbolo.reloaded")
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC), false);
            level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME,
                    SoundSource.PLAYERS, 0.8F, 1.2F);
            if (level instanceof ServerLevel server) {
                server.sendParticles(ParticleTypes.ASH, player.getX(), player.getY() + 1.0D,
                        player.getZ(), 24, 0.4D, 0.7D, 0.4D, 0.02D);
            }
            return InteractionResultHolder.consume(stack);
        }

        if (charges <= 0) {
            player.displayClientMessage(Component.translatable("elsilente.message.simbolo.empty")
                    .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC), true);
            return InteractionResultHolder.fail(stack);
        }

        // Pulso de alba: disipa las Sombras Debiles a la vista.
        setCharges(stack, charges - 1);
        List<SombraDebilEntity> shadows = level.getEntitiesOfClass(SombraDebilEntity.class,
                player.getBoundingBox().inflate(PULSE_RADIUS));
        for (SombraDebilEntity shadow : shadows) {
            shadow.dissipate();
        }

        if (level instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.FLASH, player.getX(), player.getY() + 1.0D,
                    player.getZ(), 4, 0.0D, 0.0D, 0.0D, 0.0D);
            server.sendParticles(ParticleTypes.ASH, player.getX(), player.getY() + 1.0D,
                    player.getZ(), 40, PULSE_RADIUS / 2.0D, 1.0D, PULSE_RADIUS / 2.0D, 0.03D);
        }
        level.playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE,
                SoundSource.PLAYERS, 1.0F, 1.1F);
        player.displayClientMessage(Component.translatable("elsilente.message.simbolo.pulse",
                shadows.size())
                .withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC), false);
        return InteractionResultHolder.consume(stack);
    }

    /** Slot del primer Corazon Silente del inventario, o -1. */
    private static int findHeart(Player player) {
        List<ItemStack> items = player.getInventory().items;
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).is(ModItems.CORAZON_SILENTE.get())) {
                return i;
            }
        }
        return -1;
    }

    // -------------------------------------------------------------------------

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        super.inventoryTick(stack, level, entity, slot, selected);
        if (!selected || level.isClientSide || entity.tickCount % 20 != 0) {
            return;
        }
        if (level instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.ASH, entity.getX(), entity.getY() + 1.1D, entity.getZ(),
                    2, 0.25D, 0.4D, 0.25D, 0.0D);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("elsilente.tooltip.simbolo.charges",
                getCharges(stack), maxCharges())
                .withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.literal("Unico golpe que una Sombra no puede resistir")
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        tooltip.add(Component.translatable("elsilente.tooltip.simbolo.hint")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
