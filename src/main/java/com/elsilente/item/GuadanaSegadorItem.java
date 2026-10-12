package com.elsilente.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
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
 */
public class GuadanaSegadorItem extends SwordItem {

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

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        boolean success = super.hurtEnemy(stack, target, attacker);

        // Oscuridad 5 segundos (100 ticks).
        target.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 100, 0));

        // +30% de daño si es de noche: se aplica como golpe adicional.
        if (success && target.isAlive() && attacker.level().isNight()) {
            float extra = (this.getDamage() + 1.0F) * 0.3F;
            net.minecraft.world.damagesource.DamageSource source = attacker instanceof Player player
                    ? attacker.level().damageSources().playerAttack(player)
                    : attacker.level().damageSources().mobAttack(attacker);
            target.hurt(source, extra);
        }
        return success;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("Oscuridad de 5 segundos con cada golpe")
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        tooltip.add(Component.literal("+30% de dano si es de noche")
                .withStyle(ChatFormatting.DARK_AQUA));
    }
}
