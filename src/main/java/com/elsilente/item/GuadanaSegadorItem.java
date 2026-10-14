package com.elsilente.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * GUADANA DEL SEGADOR (elsilente:guadana_segador).
 *
 * Drop del jefe El Umbral. Daño 9, aplica OSCURIDAD 5 segundos y, si es de
 * noche, inflige un 30% de daño adicional.
 *
 * Carga de almas (§14): cada criatura que mata con ella guarda 1 alma
 * (maximo 5, NBT "Almas"). Llena, el proximo golpe libera todas las almas:
 * +8 de daño al objetivo (ignorando la ventana de invulnerabilidad) y un
 * estallido de 4 de daño a 5 bloques. Al llenarse la guadaña BRILLA.
 */
public class GuadanaSegadorItem extends SwordItem {

    /** Almas necesarias para el estallido. */
    private static final int MAX_SOULS = 5;
    private static final String TAG_SOULS = "Almas";
    /** Daño del golpe liberado y del estallido. */
    private static final float SOUL_BURST_DAMAGE = 8.0F;
    private static final float SOUL_AOE_DAMAGE = 4.0F;
    private static final double SOUL_AOE_RADIUS = 5.0D;

    private static final Tier TIER = new Tier() {
        @Override
        public int getUses() {
            return 400;
        }

        @Override
        public float getSpeed() {
            return 6.0F;
        }

        @Override
        public float getAttackDamageBonus() {
            return 4.0F;
        }

        @Override
        public int getLevel() {
            return 3;
        }

        @Override
        public int getEnchantmentValue() {
            return 18;
        }

        @Override
        public Ingredient getRepairIngredient() {
            return Ingredient.of(Items.DIAMOND);
        }
    };

    public GuadanaSegadorItem(Properties properties) {
        // 1 (base) + 4 (modificador) + 4 (material) = 9 de daño.
        super(TIER, 4, -2.8F, properties);
    }

    // -------------------------------------------------------------------------
    // Carga de almas
    // -------------------------------------------------------------------------

    public static int getSouls(ItemStack stack) {
        if (!stack.hasTag() || !stack.getTag().contains(TAG_SOULS)) {
            return 0;
        }
        return Math.max(0, Math.min(MAX_SOULS, stack.getTag().getInt(TAG_SOULS)));
    }

    public static void setSouls(ItemStack stack, int value) {
        stack.getOrCreateTag().putInt(TAG_SOULS, Math.max(0, Math.min(MAX_SOULS, value)));
    }

    /** La guadaña brilla cuando esta cargada con almas. */
    @Override
    public boolean isFoil(ItemStack stack) {
        return super.isFoil(stack) || getSouls(stack) >= MAX_SOULS;
    }

    private static net.minecraft.world.damagesource.DamageSource sourceOf(LivingEntity attacker) {
        return attacker instanceof Player player
                ? attacker.level().damageSources().playerAttack(player)
                : attacker.level().damageSources().mobAttack(attacker);
    }

    /** Libera las almas: golpe cargado + estallido de almas a 5 bloques. */
    private void releaseSouls(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        setSouls(stack, 0);

        // La ventana de invulnerabilidad del golpe base no debe bloquearlo.
        int invulnerable = target.invulnerableTime;
        target.invulnerableTime = 0;
        target.hurt(sourceOf(attacker), SOUL_BURST_DAMAGE);
        target.invulnerableTime = invulnerable;

        for (LivingEntity other : attacker.level().getEntitiesOfClass(LivingEntity.class,
                target.getBoundingBox().inflate(SOUL_AOE_RADIUS))) {
            if (other == target || other == attacker || !other.isAlive()) {
                continue;
            }
            other.hurt(sourceOf(attacker), SOUL_AOE_DAMAGE);
        }

        if (attacker.level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.SOUL, target.getX(), target.getY() + 1.0D,
                    target.getZ(), 30, 1.5D, 1.0D, 1.5D, 0.03D);
        }
        attacker.level().playSound(null, target.blockPosition(), SoundEvents.SOUL_ESCAPE,
                SoundSource.HOSTILE, 1.0F, 0.7F);
    }

    private void harvestSoul(ItemStack stack, LivingEntity attacker) {
        int souls = Math.min(MAX_SOULS, getSouls(stack) + 1);
        setSouls(stack, souls);
        if (attacker.level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.SOUL, attacker.getX(), attacker.getY() + 1.2D,
                    attacker.getZ(), 4, 0.3D, 0.4D, 0.3D, 0.02D);
        }
    }

    // -------------------------------------------------------------------------

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        boolean success = super.hurtEnemy(stack, target, attacker);

        // Oscuridad 5 segundos (100 ticks).
        target.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 100, 0));

        // +30% de daño si es de noche: se aplica como golpe adicional.
        if (success && target.isAlive() && attacker.level().isNight()) {
            float extra = (this.getDamage() + 1.0F) * 0.3F;
            target.hurt(sourceOf(attacker), extra);
        }

        // Estallido de almas si la carga esta llena (§14).
        if (success && target.isAlive() && getSouls(stack) >= MAX_SOULS) {
            releaseSouls(stack, target, attacker);
        }

        // La ultima mata guarda 1 alma (el estallido no encadena almas).
        if (success && !target.isAlive()) {
            harvestSoul(stack, attacker);
        }
        return success;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("elsilente.tooltip.guadana.darkness")
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        tooltip.add(Component.translatable("elsilente.tooltip.guadana.night")
                .withStyle(ChatFormatting.DARK_AQUA));
        tooltip.add(Component.translatable("elsilente.tooltip.guadana.souls", getSouls(stack), MAX_SOULS)
                .withStyle(ChatFormatting.LIGHT_PURPLE));
        if (getSouls(stack) >= MAX_SOULS) {
            tooltip.add(Component.translatable("elsilente.tooltip.guadana.souls_full")
                    .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD));
        }
    }
}
