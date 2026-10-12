package com.elsilente.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
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
 * ARMA SIMBOLICA (elsilente:simbolo_alba).
 *
 * El UNICO objeto capaz de dañar a las Sombras Debiles, que son invulnerables
 * al resto de fuentes de daño. Daño total 6 (1 de base + 3 del arma + 2 del
 * material). Si se empuña, suelta particulas de ceniza.
 */
public class SimboloAlbaItem extends SwordItem {

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
        tooltip.add(Component.literal("Unico golpe que una Sombra no puede resistir")
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        tooltip.add(Component.literal("Brilla con ceniza cuando esta empuñado")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
