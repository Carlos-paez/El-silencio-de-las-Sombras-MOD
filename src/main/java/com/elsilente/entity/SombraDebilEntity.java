package com.elsilente.entity;

import com.elsilente.registry.ModEntities;
import com.elsilente.registry.ModItems;
import com.elsilente.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.List;

/**
 * SOMBRA DEBIL.
 *
 * Entidad NEUTRAL (hereda de Mob, NO de Monster: si fuera Monster bloquearia el
 * sueño del jugador, que es justo lo que la mecanica de pesadilla necesita).
 *
 * Reglas:
 *  - Spawn natural en zonas con luz <= 7, entre 16 y 48 bloques del jugador y
 *    fuera de su cono de mirada; maximo 6 por jugador.
 *  - Solo ataca si el jugador esta a menos de 8 bloques: 1 corazon (2 HP) cada
 *    3 segundos (60 ticks).
 *  - Invulnerable a todo salvo el arma simbolica (elsilente:simbolo_alba).
 *  - Se disipa con una fuente de luz (luz >= 8) cerca de donde pisa.
 *  - Acumula el dano infligido: al llegar a 10 HP evoluciona en un El Silente
 *    que entra directamente en persecucion.
 */
public class SombraDebilEntity extends PathfinderMob {

    private static final EntityDataAccessor<Integer> DATA_DEALT =
            SynchedEntityData.defineId(SombraDebilEntity.class, EntityDataSerializers.INT);

    /** HP que debe acumular sobre el jugador para mutar en El Silente. */
    private static final int DAMAGE_TO_EVOLVE = 10;
    /** Enfriamiento entre mordiscos: 1 corazon cada 3 segundos. */
    private static final int ATTACK_COOLDOWN = 60;

    private int attackCooldown = ATTACK_COOLDOWN;
    private int idleTimer = 200;

    public SombraDebilEntity(EntityType<? extends SombraDebilEntity> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.22D)
                .add(Attributes.FOLLOW_RANGE, 16.0D)
                .add(Attributes.ATTACK_DAMAGE, 2.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.0D);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_DEALT, 0);
    }

    public int getDamageDealt() {
        return this.entityData.get(DATA_DEALT);
    }

    public void setDamageDealt(int value) {
        this.entityData.set(DATA_DEALT, value);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new HauntGoal(this));
        this.goalSelector.addGoal(6, new RandomStrollGoal(this, 0.6D));
        this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
    }

    // ---------------------------------------------------------------------------
    // Invulnerabilidad: solo el arma simbolica la puede dañar.
    // ---------------------------------------------------------------------------

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        // /kill y el vacio deben seguir funcionando (limpieza).
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return false;
        }
        Entity attacker = source.getEntity();
        if (attacker instanceof Player player) {
            if (player.getMainHandItem().is(ModItems.SIMBOLO_ALBA.get())
                    || player.getOffhandItem().is(ModItems.SIMBOLO_ALBA.get())) {
                return false;
            }
        }
        return true;
    }

    // ---------------------------------------------------------------------------
    // Tick: enfriamiento de ataque + disipacion por luz.
    // ---------------------------------------------------------------------------

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            return;
        }
        if (this.attackCooldown > 0) {
            this.attackCooldown--;
        }
        // Susurro grave de fondo cada 15-30 s (solo si esta a la vista cercana).
        if (--this.idleTimer <= 0) {
            this.idleTimer = 300 + this.getRandom().nextInt(300);
            this.level().playSound(null, this.blockPosition(), ModSounds.SHADOW_IDLE.get(),
                    SoundSource.NEUTRAL, 0.25F, 1.0F);
        }
        if (this.level().getMaxLocalRawBrightness(this.blockPosition()) >= 8) {
            dissipate();
        }
    }

    /** La sombra se deshace (luz, Trofeo del Umbral, etc.). */
    public void dissipate() {
        if (this.level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.LARGE_SMOKE, this.getX(), this.getY() + 0.4D, this.getZ(),
                    12, 0.3D, 0.5D, 0.3D, 0.02D);
            this.level().playSound(null, this.blockPosition(), ModSounds.VANISH.get(),
                    SoundSource.NEUTRAL, 0.5F, 1.4F);
        }
        this.discard();
    }

    // ---------------------------------------------------------------------------
    // Ataque: 2 HP cada 3 s y evolucion al llegar a 10 HP.
    // ---------------------------------------------------------------------------

    private void bite(Player player) {
        this.attackCooldown = ATTACK_COOLDOWN;
        boolean success = player.hurt(this.damageSources().mobAttack(this), 2.0F);
        if (!success) {
            return;
        }
        int dealt = getDamageDealt() + 2;
        setDamageDealt(dealt);
        if (dealt >= DAMAGE_TO_EVOLVE) {
            evolve(player);
        }
    }

    /** La sombra madura y da lugar a un El Silente en persecución directa. */
    private void evolve(Player player) {
        ElSilenteEntity silente = ModEntities.EL_SILENTE.get().create(this.level());
        if (silente != null) {
            silente.moveTo(this.getX(), this.getY(), this.getZ(), this.getYRot(), 0.0F);
            this.level().addFreshEntity(silente);
            silente.startChase(player);
        }
        this.discard();
    }

    // ---------------------------------------------------------------------------
    // Spawn natural
    // ---------------------------------------------------------------------------

    public static boolean checkSpawnRules(EntityType<SombraDebilEntity> type, ServerLevelAccessor level,
                                          MobSpawnType spawnType, BlockPos pos, RandomSource random) {
        // Solo el spawn natural respeta las reglas: huevos y comandos siempre.
        if (spawnType != MobSpawnType.NATURAL) {
            return true;
        }
        if (level.getMaxLocalRawBrightness(pos) > 7) {
            return false;
        }
        Level world = level.getLevel();
        Player nearest = world.getNearestPlayer(pos.getX(), pos.getY(), pos.getZ(), 48.0D, false);
        if (nearest == null) {
            return false;
        }
        double distSqr = nearest.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D);
        // Nunca pegado al jugador (16 b) ni mas lejos del radio buscado (48 b).
        if (distSqr < 16.0D * 16.0D) {
            return false;
        }
        // Fuera del cono de vision del jugador: nunca a la vista directa.
        Vec3 look = nearest.getLookAngle().normalize();
        Vec3 to = Vec3.atCenterOf(pos).subtract(nearest.getEyePosition()).normalize();
        if (look.dot(to) > 0.7D) {
            return false;
        }
        // Cupo por jugador: maximo 6 sombras alrededor de el.
        List<SombraDebilEntity> nearby = world.getEntitiesOfClass(SombraDebilEntity.class,
                new AABB(pos).inflate(48.0D));
        if (nearby.size() >= 6) {
            return false;
        }
        com.elsilente.ElSilenteMod.LOGGER.debug("[El Silente] Spawn de sombra aceptado en {} (luz {})",
                pos, level.getMaxLocalRawBrightness(pos));
        return true;
    }

    @Override
    public int getExperienceReward() {
        return 0;
    }

    // ---------------------------------------------------------------------------
    // NBT
    // ---------------------------------------------------------------------------

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("DamageDealt", getDamageDealt());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setDamageDealt(tag.getInt("DamageDealt"));
    }

    /**
     * Persigue al jugador mas cercano solo si esta a menos de 8 bloques y le
     * muerde 1 corazon cada 3 segundos.
     */
    static class HauntGoal extends Goal {
        private final SombraDebilEntity sombra;
        private Player hauntTarget;

        HauntGoal(SombraDebilEntity sombra) {
            this.sombra = sombra;
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            Player player = this.sombra.level().getNearestPlayer(this.sombra, 12.0D);
            if (player == null || !player.isAlive()) {
                return false;
            }
            if (this.sombra.distanceTo(player) >= 8.0D) {
                return false;
            }
            this.hauntTarget = player;
            return true;
        }

        @Override
        public boolean canContinueToUse() {
            return this.hauntTarget != null && this.hauntTarget.isAlive()
                    && this.sombra.distanceTo(this.hauntTarget) < 9.0D;
        }

        @Override
        public void stop() {
            this.hauntTarget = null;
            this.sombra.getNavigation().stop();
        }

        @Override
        public void tick() {
            if (this.hauntTarget == null) {
                return;
            }
            this.sombra.getLookControl().setLookAt(this.hauntTarget, 30.0F, 30.0F);
            if (this.sombra.distanceToSqr(this.hauntTarget) > 4.0D) {
                this.sombra.getNavigation().moveTo(this.hauntTarget, 1.0D);
            } else {
                this.sombra.getNavigation().stop();
                if (this.sombra.attackCooldown <= 0) {
                    this.sombra.bite(this.hauntTarget);
                }
            }
        }
    }
}
