package com.elsilente.entity;

import com.elsilente.ElSilenteMod;
import com.elsilente.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * EL SILENTE DE LAS SOMBRAS.
 *
 * Dos fases controladas por el flag sincronizado {@code Aggro}:
 *  - STALK (Aggro=false): quieto, observa al jugador mas cercano con ojos blancos.
 *  - CHASE (Aggro=true): persecucion y ataque con ojos rojos.
 */
public class ElSilenteEntity extends Monster {

    private static final EntityDataAccessor<Boolean> DATA_AGGRO =
            SynchedEntityData.defineId(ElSilenteEntity.class, EntityDataSerializers.BOOLEAN);

    public static final List<PendingSpawn> PENDING_SPAWNS = new ArrayList<>();

    /** Mirada directa que lo disuelve en fase STALK: 0,5 s (antes 3 s). */
    private static final int STARE_TICKS_TO_VANISH = 10;

    private int stareTicks;
    private int whisperTimer = 200;
    private int chaseSoundTimer = 40;
    private int teleportTimer = 20;
    private int bedTimer = 40;
    private int doorTimer = 40;

    public ElSilenteEntity(EntityType<? extends ElSilenteEntity> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 80.0D)
                // 0,34: por encima del sprint del jugador, pero alcanzable.
                .add(Attributes.MOVEMENT_SPEED, 0.34D)
                .add(Attributes.ATTACK_DAMAGE, 10.0D)
                .add(Attributes.FOLLOW_RANGE, 64.0D)
                // 1.0: no sufre retroceso al recibir golpes.
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_AGGRO, false);
    }

    public boolean isAggro() {
        return this.entityData.get(DATA_AGGRO);
    }

    public void setAggro(boolean aggro) {
        this.entityData.set(DATA_AGGRO, aggro);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Aggro", this.isAggro());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.setAggro(tag.getBoolean("Aggro"));
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new StalkLookGoal(this));
        this.goalSelector.addGoal(4, new MeleeAttackGoal(this, 1.0D, false));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, false,
                living -> this.isAggro()));
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            return;
        }
        if (this.isAggro()) {
            chaseTick();
        } else {
            stalkTick();
        }
    }

    private void stalkTick() {
        Player nearest = this.level().getNearestPlayer(this, 48.0D);
        if (nearest == null) {
            this.stareTicks = 0;
            return;
        }

        if (--this.whisperTimer <= 0) {
            this.whisperTimer = 200 + this.getRandom().nextInt(400);
            this.level().playSound(null, this.blockPosition(), ModSounds.WHISPER.get(),
                    SoundSource.HOSTILE, 0.4F, 1.0F);
        }

        // Vigilancia estatica: mientras el jugador duerme se coloca al pie de la cama
        // en lugar de atacarle (el salto de tension llega al despertar).
        if (nearest.isSleeping()) {
            this.stareTicks = 0;
            this.getLookControl().setLookAt(nearest, 30.0F, 30.0F);
            bedVigil(nearest);
            return;
        }

        if (this.distanceTo(nearest) < 8.0D) {
            startChase(nearest);
            return;
        }

        Vec3 look = nearest.getLookAngle().normalize();
        Vec3 to = this.getEyePosition().subtract(nearest.getEyePosition()).normalize();
        boolean stared = look.dot(to) > 0.985D && nearest.hasLineOfSight(this);
        if (stared) {
            this.stareTicks++;
            // Esquivo: se disuelve al poco de mirarle de frente (solo en STALK).
            if (this.stareTicks >= STARE_TICKS_TO_VANISH) {
                vanish();
                return;
            }
        } else {
            this.stareTicks = 0;
        }

        tryReposition(nearest);
    }

    private void chaseTick() {
        if (--this.chaseSoundTimer <= 0) {
            this.chaseSoundTimer = 60 + this.getRandom().nextInt(21);
            this.level().playSound(null, this.blockPosition(), ModSounds.CHASE.get(),
                    SoundSource.HOSTILE, 0.9F, 1.0F);
        }

        if (this.level().getMaxLocalRawBrightness(this.blockPosition()) >= 8) {
            vanish();
            return;
        }
        if (this.level().getFluidState(this.blockPosition()).is(FluidTags.WATER)) {
            vanish();
            return;
        }

        LivingEntity target = this.getTarget();
        if (target == null || !target.isAlive()) {
            Player nearest = this.level().getNearestPlayer(this, 40.0D);
            if (nearest != null && nearest.isAlive()) {
                this.setTarget(nearest);
            } else {
                vanish();
            }
            return;
        }
        if (this.distanceTo(target) > 40.0D) {
            vanish();
            return;
        }

        tryReposition(target);
    }

    /**
     * Movimientos contranatural: si NO hay linea de visión con el objetivo y este
     * esta lejos (>12 b), con un 25 % de probabilidad por segundo se materializa
     * hasta 6 bloques mas cerca, en silencio y siempre en la oscuridad. Prefiere
     * colocarse junto a una puerta si la hay (marcos de puerta).
     */
    private void tryReposition(LivingEntity target) {
        if (--this.teleportTimer > 0) {
            return;
        }
        this.teleportTimer = 20;
        if (target == null || !target.isAlive()) {
            return;
        }
        if (this.distanceTo(target) < 12.0D || this.hasLineOfSight(target)) {
            return;
        }
        if (this.getRandom().nextFloat() > 0.25F) {
            return;
        }

        BlockPos dest = findDoorSpot();
        if (dest == null) {
            Vec3 dir = target.position().subtract(this.position());
            double dist = dir.length();
            if (dist < 12.0D) {
                return;
            }
            double step = Math.min(6.0D, dist - 3.0D);
            Vec3 candidate = this.position().add(dir.normalize().scale(step));
            dest = findGround(candidate);
        }
        if (dest == null) {
            return;
        }
        AABB destination = this.getBoundingBox().move(dest.getX() + 0.5D - this.getX(),
                dest.getY() - this.getY(), dest.getZ() + 0.5D - this.getZ());
        if (!this.level().isLoaded(dest)
                || this.level().getMaxLocalRawBrightness(dest) > 7
                || !this.level().noCollision(this, destination)) {
            return;
        }

        this.teleportTo(dest.getX() + 0.5D, dest.getY(), dest.getZ() + 0.5D);
        if (this.level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.LARGE_SMOKE, this.getX(), this.getY() + 1.0D, this.getZ(),
                    10, 0.3D, 0.6D, 0.3D, 0.02D);
        }
    }

    /** Puerta de madera oscura en un radio de 6 bloques, o null. */
    private BlockPos findDoorSpot() {
        if (--this.doorTimer > 0) {
            return null;
        }
        this.doorTimer = 40;
        if (this.getRandom().nextFloat() > 0.3F) {
            return null;
        }
        BlockPos origin = this.blockPosition();
        BlockPos door = null;
        for (BlockPos candidate : BlockPos.betweenClosed(origin.offset(-6, -3, -6), origin.offset(6, 3, 6))) {
            if (this.level().getBlockState(candidate).getBlock() instanceof DoorBlock
                    && this.level().getMaxLocalRawBrightness(candidate) <= 7) {
                door = candidate.immutable();
                break;
            }
        }
        if (door == null) {
            return null;
        }
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos side = door.relative(direction);
            if (this.level().getBlockState(side).getCollisionShape(this.level(), side).isEmpty()
                    && !this.level().getBlockState(side.below())
                            .getCollisionShape(this.level(), side.below()).isEmpty()
                    && this.level().getMaxLocalRawBrightness(side) <= 7) {
                return side;
            }
        }
        return null;
    }

    /** Posicion con suelo libre (hasta 3 bloques de desnivel) para el teleport. */
    private BlockPos findGround(Vec3 candidate) {
        BlockPos feet = BlockPos.containing(candidate);
        int guard = 0;
        while (!this.level().getBlockState(feet).getCollisionShape(this.level(), feet).isEmpty()
                && guard++ < 4) {
            feet = feet.above();
        }
        for (int i = 0; i < 4; i++) {
            BlockPos floor = feet.below(i);
            if (!this.level().getBlockState(floor).getCollisionShape(this.level(), floor).isEmpty()) {
                BlockPos stand = floor.above();
                if (this.level().getBlockState(stand).getCollisionShape(this.level(), stand).isEmpty()) {
                    return stand;
                }
                return null;
            }
        }
        return null;
    }

    /**
     * Vigilancia al pie de la cama: mientras el jugador duerme, se materializa
     * en el extremo de la cama mirandole. No le ataca hasta que despierta.
     */
    private void bedVigil(Player sleeper) {
        if (this.distanceTo(sleeper) < 2.0D) {
            return;
        }
        if (--this.bedTimer > 0) {
            return;
        }
        this.bedTimer = 40;
        if (this.getRandom().nextFloat() > 0.4F) {
            return;
        }

        BlockPos bed = sleeper.blockPosition();
        if (!(this.level().getBlockState(bed).getBlock() instanceof BedBlock)) {
            return;
        }
        Direction facing = this.level().getBlockState(bed).getValue(BedBlock.FACING);
        BlockPos foot = bed.relative(facing.getOpposite());
        BlockPos alternate = bed.relative(facing);
        BlockPos spot = isStandingSpot(foot) ? foot : (isStandingSpot(alternate) ? alternate : null);
        if (spot == null) {
            return;
        }

        this.teleportTo(spot.getX() + 0.5D, spot.getY(), spot.getZ() + 0.5D);
        if (this.level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.LARGE_SMOKE, this.getX(), this.getY() + 1.0D, this.getZ(),
                    16, 0.4D, 0.8D, 0.4D, 0.02D);
        }
    }

    private boolean isStandingSpot(BlockPos pos) {
        return this.level().getBlockState(pos).getCollisionShape(this.level(), pos).isEmpty()
                && !this.level().getBlockState(pos.below()).getCollisionShape(this.level(), pos.below()).isEmpty()
                && this.level().getMaxLocalRawBrightness(pos) <= 7;
    }

    /** Entra en persecucion directa contra un jugador (usado por spawn natural, emboscada y ritual). */
    public void startChase(Player player) {
        this.setAggro(true);
        this.setTarget(player);
        this.setPersistenceRequired();
        this.stareTicks = 0;
        this.chaseSoundTimer = 40;
        this.level().playSound(null, this.blockPosition(), ModSounds.SCREAM.get(),
                SoundSource.HOSTILE, 1.5F, 1.0F);
        if (this.level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.LARGE_SMOKE, this.getX(), this.getY() + 1.0D, this.getZ(),
                    30, 0.4D, 0.8D, 0.4D, 0.02D);
        }
    }

    private void vanish() {
        if (this.level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.LARGE_SMOKE, this.getX(), this.getY() + 1.0D, this.getZ(),
                    40, 0.4D, 0.8D, 0.4D, 0.02D);
            this.level().playSound(null, this.blockPosition(), ModSounds.VANISH.get(),
                    SoundSource.HOSTILE, 1.0F, 1.0F);
            Player near = this.level().getNearestPlayer(this, 64.0D);
            if (near != null) {
                PENDING_SPAWNS.add(new PendingSpawn(near.getUUID(),
                        server.getGameTime() + 1200L + this.getRandom().nextInt(2400)));
            }
        }
        this.discard();
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        boolean success = super.doHurtTarget(target);
        if (success) {
            this.level().playSound(null, this.blockPosition(), ModSounds.ATTACK.get(),
                    SoundSource.HOSTILE, 1.0F, 1.0F);
            if (target instanceof LivingEntity living) {
                double dx = living.getX() - this.getX();
                double dz = living.getZ() - this.getZ();
                double horizontal = Math.max(1.0E-4D, Math.sqrt(dx * dx + dz * dz));
                living.knockback(0.6D, dx / horizontal, dz / horizontal);
            }
        }
        return success;
    }

    @Override
    public int getExperienceReward() {
        return 20;
    }

    public static boolean checkSpawnRules(EntityType<ElSilenteEntity> type, ServerLevelAccessor level,
                                          MobSpawnType spawnType, BlockPos pos, RandomSource random) {
        // Solo el spawn NATURAL respeta las reglas de acecho: huevos de invocacion,
        // comandos y spawners deben funcionar siempre (tambien a plena luz del dia).
        if (spawnType != MobSpawnType.NATURAL) {
            return true;
        }
        if (level.getMaxLocalRawBrightness(pos) > 7) {
            return false;
        }
        if (level.canSeeSky(pos) && level.getLevel().isDay()) {
            return false;
        }
        if (level.getLevel().getNearestPlayer(pos.getX(), pos.getY(), pos.getZ(), 20.0D, false) != null) {
            return false;
        }
        Player watcher = level.getLevel().getNearestPlayer(pos.getX(), pos.getY(), pos.getZ(), 48.0D, false);
        if (watcher != null) {
            Vec3 look = watcher.getLookAngle().normalize();
            Vec3 to = Vec3.atCenterOf(pos).subtract(watcher.getEyePosition()).normalize();
            if (look.dot(to) > 0.7D) {
                return false;
            }
        }
        if (!Monster.checkMonsterSpawnRules(type, level, spawnType, pos, random)) {
            return false;
        }
        ElSilenteMod.LOGGER.debug("[El Silente] Spawn natural aceptado en {} (luz {})",
                pos, level.getMaxLocalRawBrightness(pos));
        return true;
    }

    /** Observa fijamente al jugador mas cercano mientras no esta agresivo. */
    static class StalkLookGoal extends Goal {
        private final ElSilenteEntity mob;
        private Player lookTarget;

        StalkLookGoal(ElSilenteEntity mob) {
            this.mob = mob;
        }

        @Override
        public boolean canUse() {
            if (this.mob.isAggro()) {
                return false;
            }
            Player player = this.mob.level().getNearestPlayer(this.mob, 32.0D);
            if (player == null || !player.isAlive()) {
                return false;
            }
            this.lookTarget = player;
            return true;
        }

        @Override
        public boolean canContinueToUse() {
            return !this.mob.isAggro() && this.lookTarget != null && this.lookTarget.isAlive()
                    && this.mob.distanceToSqr(this.lookTarget) < 1024.0D;
        }

        @Override
        public void tick() {
            this.mob.getLookControl().setLookAt(this.lookTarget, 30.0F, 30.0F);
        }
    }

    public static class PendingSpawn {
        public final UUID playerId;
        public final long at;

        public PendingSpawn(UUID playerId, long at) {
            this.playerId = playerId;
            this.at = at;
        }
    }
}
