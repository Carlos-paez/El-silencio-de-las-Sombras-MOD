package com.elsilente.entity;

import com.elsilente.registry.ModEntities;
import com.elsilente.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * EL UMBRAL - jefe final.
 *
 * Limite entre el mundo de los mortales y el mundo de las sombras:
 *  - 300 HP con barra de jefe (ServerBossEvent).
 *  - FASE 1 (>50%): invoca Sombras Debiles (1 cada 5 s, maximo 4 vivas).
 *  - FASE 2 (<=50%): ademas invoca El Silente (1 cada 15 s, maximo 2),
 *    recibe un 10% mas de dano por golpe y usa el GOLPE DE AREA (slam) con
 *    telegrado de 0,8 s: empuje + oscuridad en 5 bloques.
 *  - ENRAGE (<25%): rugido, +27% de velocidad y slam cada 5 s (antes 8 s).
 *  - Drops garantizados: Trofeo del Umbral y Guadana del Segador (loot table).
 *
 * Solo se invoca con el ritual de medianoche (RitualHandler).
 */
public class UmbralEntity extends Monster {

    private final ServerBossEvent bossEvent = new ServerBossEvent(
            Component.translatable("entity.elsilente.umbral"),
            BossEvent.BossBarColor.PURPLE,
            BossEvent.BossBarOverlay.NOTCHED_10);

    private int shadowSummonTimer = 100;
    private int silenteSummonTimer = 300;
    private boolean phaseTwoRoared = false;

    // Golpe de area (solo Fase 2): enfriamiento, telegrado y estado.
    private int slamCooldown = 100;
    private int slamWindup = 0;
    private boolean enraged = false;

    /** Radio del slam (bloques). */
    private static final double SLAM_RADIUS = 5.0D;
    /** Vida en la que entra en rage (< 25%). */
    private static final float ENRAGE_THRESHOLD = 0.25F;

    public UmbralEntity(EntityType<? extends UmbralEntity> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 300.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.30D)
                .add(Attributes.ATTACK_DAMAGE, 12.0D)
                .add(Attributes.FOLLOW_RANGE, 64.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0D, false));
        this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    // ---------------------------------------------------------------------------
    // Barra de jefe
    // ---------------------------------------------------------------------------

    @Override
    public void startSeenByPlayer(net.minecraft.server.level.ServerPlayer player) {
        super.startSeenByPlayer(player);
        this.bossEvent.addPlayer(player);
    }

    @Override
    public void stopSeenByPlayer(net.minecraft.server.level.ServerPlayer player) {
        super.stopSeenByPlayer(player);
        this.bossEvent.removePlayer(player);
    }

    // ---------------------------------------------------------------------------
    // Fases e invocaciones
    // ---------------------------------------------------------------------------

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            return;
        }
        this.bossEvent.setProgress(this.getHealth() / this.getMaxHealth());

        if (--this.shadowSummonTimer <= 0) {
            this.shadowSummonTimer = 100;
            summonShadows();
        }
        if (isPhaseTwo()) {
            if (!this.phaseTwoRoared) {
                this.phaseTwoRoared = true;
                roar();
            }
            if (!enraged && this.getHealth() <= this.getMaxHealth() * ENRAGE_THRESHOLD) {
                enterEnrage();
            }
            if (--this.silenteSummonTimer <= 0) {
                this.silenteSummonTimer = 300;
                summonSilente();
            }
            tickSlam();
        }
    }

    // -------------------------------------------------------------------------
    // Golpe de area (Fase 2): telegrado de 0,8 s y onda de impacto.
    // -------------------------------------------------------------------------

    private void tickSlam() {
        if (this.slamWindup > 0) {
            // Aviso: almas subiendo alrededor del jefe durante el cargado.
            if (--this.slamWindup == 0) {
                slamStrike();
            } else if (this.level() instanceof ServerLevel server && this.slamWindup % 3 == 0) {
                server.sendParticles(ParticleTypes.SOUL, this.getX(), this.getY() + 0.2D,
                        this.getZ(), 6, 1.2D, 0.6D, 1.2D, 0.02D);
            }
            return;
        }
        if (--this.slamCooldown > 0) {
            return;
        }
        LivingEntity target = this.getTarget();
        if (target == null || !target.isAlive() || this.distanceTo(target) > 6.0F) {
            this.slamCooldown = 20;
            return;
        }
        // Telegrado: rugido grave y recogida de almas antes del impacto.
        this.slamWindup = 16;
        this.level().playSound(null, this.blockPosition(), ModSounds.UMBRAL_ROAR.get(),
                SoundSource.HOSTILE, 1.1F, 0.6F);
        if (this.level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.SOUL, this.getX(), this.getY() + 0.3D,
                    this.getZ(), 16, 1.4D, 0.8D, 1.4D, 0.03D);
        }
    }

    /** Onda de impacto: daño + empuje fuerte + oscuridad a 5 bloques. */
    private void slamStrike() {
        this.slamCooldown = enraged ? 100 : 160;
        this.level().playSound(null, this.blockPosition(), SoundEvents.GENERIC_EXPLODE,
                SoundSource.HOSTILE, 0.55F, 1.35F);
        if (this.level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.FLASH, this.getX(), this.getY() + 0.6D,
                    this.getZ(), 2, 0.0D, 0.0D, 0.0D, 0.0D);
            server.sendParticles(ParticleTypes.ASH, this.getX(), this.getY() + 0.4D,
                    this.getZ(), 70, SLAM_RADIUS, 0.6D, SLAM_RADIUS, 0.05D);
        }
        for (Player player : this.level().getEntitiesOfClass(Player.class,
                this.getBoundingBox().inflate(SLAM_RADIUS))) {
            if (!player.isAlive() || player.isCreative() || player.isSpectator()) {
                continue;
            }
            if (this.distanceTo(player) > SLAM_RADIUS + 1.0F) {
                continue;
            }
            player.hurt(this.level().damageSources().mobAttack(this), 6.0F);
            Vec3 away = player.position().subtract(this.position());
            double horizontal = Math.max(1.0E-4D, Math.sqrt(away.x * away.x + away.z * away.z));
            player.knockback(1.4D, away.x / horizontal, away.z / horizontal);
            player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.DARKNESS, 60, 0));
            if (this.level() instanceof ServerLevel server) {
                server.sendParticles(ParticleTypes.SOUL, player.getX(), player.getY() + 1.0D,
                        player.getZ(), 8, 0.3D, 0.5D, 0.3D, 0.02D);
            }
        }
    }

    /** Al 25% de vida: rugido, velocidad +27% y slam mas frecuente. */
    private void enterEnrage() {
        this.enraged = true;
        this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.38D);
        this.level().playSound(null, this.blockPosition(), ModSounds.UMBRAL_ROAR.get(),
                SoundSource.HOSTILE, 1.6F, 0.75F);
        if (this.level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.LARGE_SMOKE, this.getX(), this.getY() + 1.2D,
                    this.getZ(), 80, 0.8D, 1.2D, 0.8D, 0.05D);
            server.sendParticles(ParticleTypes.CRIMSON_SPORE, this.getX(), this.getY() + 1.0D,
                    this.getZ(), 40, 0.6D, 1.0D, 0.6D, 0.03D);
        }
    }

    private boolean isPhaseTwo() {
        return this.getHealth() <= this.getMaxHealth() * 0.5F;
    }

    /** Al 50% de vida recibe un 10% mas de dano por golpe. */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (isPhaseTwo()) {
            amount *= 1.1F;
        }
        return super.hurt(source, amount);
    }

    private void roar() {
        this.level().playSound(null, this.blockPosition(), ModSounds.UMBRAL_ROAR.get(),
                SoundSource.HOSTILE, 1.5F, 1.0F);
        if (this.level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.LARGE_SMOKE, this.getX(), this.getY() + 1.2D, this.getZ(),
                    60, 0.7D, 1.0D, 0.7D, 0.04D);
        }
    }

    private void summonShadows() {
        if (this.getTarget() == null) {
            return;
        }
        List<SombraDebilEntity> alive = this.level().getEntitiesOfClass(SombraDebilEntity.class,
                this.getBoundingBox().inflate(40.0D));
        if (alive.size() >= 4) {
            return;
        }
        SombraDebilEntity sombra = ModEntities.SOMBRA_DEBIL.get().create(this.level());
        if (sombra == null) {
            return;
        }
        BlockPos spot = summonSpot();
        sombra.moveTo(spot.getX() + 0.5D, spot.getY(), spot.getZ() + 0.5D,
                this.getRandom().nextFloat() * 360.0F, 0.0F);
        this.level().addFreshEntity(sombra);
        summonFx(spot);
    }

    private void summonSilente() {
        Player target = this.level().getNearestPlayer(this, 64.0D);
        if (target == null || !target.isAlive()) {
            return;
        }
        List<ElSilenteEntity> alive = this.level().getEntitiesOfClass(ElSilenteEntity.class,
                this.getBoundingBox().inflate(40.0D));
        if (alive.size() >= 2) {
            return;
        }
        ElSilenteEntity silente = ModEntities.EL_SILENTE.get().create(this.level());
        if (silente == null) {
            return;
        }
        BlockPos spot = summonSpot();
        silente.moveTo(spot.getX() + 0.5D, spot.getY(), spot.getZ() + 0.5D,
                this.getRandom().nextFloat() * 360.0F, 0.0F);
        this.level().addFreshEntity(silente);
        silente.startChase(target);
        summonFx(spot);
    }

    private void summonFx(BlockPos spot) {
        this.level().playSound(null, spot, ModSounds.UMBRAL_SUMMON.get(),
                SoundSource.HOSTILE, 0.9F, 1.0F);
        if (this.level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.LARGE_SMOKE, spot.getX() + 0.5D, spot.getY() + 0.6D,
                    spot.getZ() + 0.5D, 24, 0.4D, 0.5D, 0.4D, 0.02D);
        }
    }

    /** Punto libre con suelo a 2-4 bloques del jefe para materializar invocaciones. */
    private BlockPos summonSpot() {
        RandomSource random = this.getRandom();
        BlockPos origin = this.blockPosition();
        for (int attempt = 0; attempt < 8; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            int dist = 2 + random.nextInt(3);
            BlockPos candidate = origin.offset(
                    (int) Math.round(Math.cos(angle) * dist), 0, (int) Math.round(Math.sin(angle) * dist));

            BlockPos feet = candidate;
            int guard = 0;
            while (!this.level().getBlockState(feet).getCollisionShape(this.level(), feet).isEmpty()
                    && guard++ < 4) {
                feet = feet.above();
            }
            for (int down = 0; down < 5; down++) {
                BlockPos floor = feet.below(down);
                if (!this.level().getBlockState(floor).getCollisionShape(this.level(), floor).isEmpty()) {
                    BlockPos stand = floor.above();
                    if (this.level().getBlockState(stand).getCollisionShape(this.level(), stand).isEmpty()) {
                        return stand;
                    }
                    break;
                }
            }
        }
        return origin;
    }

    // ---------------------------------------------------------------------------
    // Jefe: no se va nunca
    // ---------------------------------------------------------------------------

    @Override
    public void checkDespawn() {
        // El jefe jamas se desvanece por distancia o dificultad.
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public int getExperienceReward() {
        return 100;
    }
}
