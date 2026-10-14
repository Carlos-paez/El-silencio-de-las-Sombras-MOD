package com.elsilente.entity;

import com.elsilente.ElSilenteMod;
import com.elsilente.block.TrofeoUmbralBlockEntity;
import com.elsilente.config.ElSilenteConfig;
import com.elsilente.event.AdvancementHandler;
import com.elsilente.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.TorchBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.EnumSet;
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

    /** Radio de la ZONA SEGURA del Trofeo del Umbral: no entra en ella (§14). */
    static final double TROPHY_ZONE = 12.0D;

    private int stareTicks;
    private int snuffSearchTimer = 40;
    private int whisperTimer = 200;
    private int chaseSoundTimer = 40;
    private int teleportTimer = 20;
    private int bedTimer = 40;
    private int doorTimer = 40;
    private int trophyRetreatTimer = 0;
    /** Enfriamiento tras cruzar una puerta (evita el taladro de puertas). */
    private int openDoorCooldown = 0;
    /** Temporizador de busqueda de puertas para abrir (barato). */
    private int openDoorSearchTimer = 20;

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
        this.goalSelector.addGoal(2, new SnuffLightGoal(this));
        this.goalSelector.addGoal(3, new OpenDoorGoal(this));
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

        // Zona segura del Trofeo: no acecha dentro; sale andando primero.
        BlockPos trophy = nearbyTrophy();
        if (trophy != null) {
            this.stareTicks = 0;
            retreatFromTrophy(trophy);
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

        // Zona segura del Trofeo: si esta dentro, sale antes de continuar.
        BlockPos trophy = nearbyTrophy();
        if (trophy != null) {
            retreatFromTrophy(trophy);
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

        // El objetivo esta en la zona segura: no cruza el umbral del trofeo.
        // Se queda quieto, mirandole, hasta que salga.
        if (nearbyTrophy(target.blockPosition()) != null) {
            this.getNavigation().stop();
            this.getLookControl().setLookAt(target, 30.0F, 30.0F);
            return;
        }

        tryReposition(target);
    }

    // -------------------------------------------------------------------------
    // ZONA SEGURA DEL TROFEO DEL UMBRAL (§14).
    // -------------------------------------------------------------------------

    /** Trofeo vivo dentro de la zona segura de su posicion, o null. */
    BlockPos nearbyTrophy() {
        return TrofeoUmbralBlockEntity.findNearby(this.level(), this.blockPosition(), TROPHY_ZONE);
    }

    /** Trofeo vivo dentro de la zona segura de {@code from}, o null. */
    BlockPos nearbyTrophy(BlockPos from) {
        return TrofeoUmbralBlockEntity.findNearby(this.level(), from, TROPHY_ZONE);
    }

    /**
     * Sale de la zona segura: primero intenta un salto de emergencia (si esta
     * muy adentro o el camino se niega) y en caso contrario camina hacia el
     * borde. Marca la salida con humo como su teletransporte habitual.
     */
    private void retreatFromTrophy(BlockPos trophy) {
        Vec3 away = this.position().subtract(Vec3.atCenterOf(trophy));
        if (away.horizontalDistanceSqr() < 1.0E-4D) {
            away = new Vec3(1.0D, 0.0D, 0.0D);
        }
        Vec3 destPos = Vec3.atCenterOf(trophy).add(away.normalize().scale(TROPHY_ZONE + 3.0D));
        BlockPos dest = BlockPos.containing(destPos);
        if (--this.trophyRetreatTimer > 0) {
            return;
        }
        this.trophyRetreatTimer = 10;

        double distSqr = this.distanceToSqr(trophy.getX() + 0.5D, trophy.getY(), trophy.getZ() + 0.5D);
        double inner = TROPHY_ZONE - 4.0D;
        if (distSqr < inner * inner) {
            AABB destination = this.getBoundingBox().move(dest.getX() + 0.5D - this.getX(),
                    dest.getY() - this.getY(), dest.getZ() + 0.5D - this.getZ());
            if (this.level().isLoaded(dest) && this.level().noCollision(this, destination)) {
                this.teleportTo(dest.getX() + 0.5D, dest.getY(), dest.getZ() + 0.5D);
                if (this.level() instanceof ServerLevel server) {
                    server.sendParticles(ParticleTypes.LARGE_SMOKE, this.getX(), this.getY() + 1.0D,
                            this.getZ(), 10, 0.3D, 0.6D, 0.3D, 0.02D);
                }
                return;
            }
        }
        this.getNavigation().moveTo(dest.getX() + 0.5D, dest.getY(), dest.getZ() + 0.5D, 1.2D);
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
        // Nunca se materializa dentro de la zona segura del Trofeo.
        if (TrofeoUmbralBlockEntity.findNearby(this.level(), dest, TROPHY_ZONE) != null) {
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
        if (spot == null || this.nearbyTrophy(spot) != null) {
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
            // «Sobrevive a una emboscada»: se desvanace en persecucion y sigues vivo.
            if (this.isAggro() && this.getTarget() instanceof ServerPlayer escaped
                    && escaped.isAlive()) {
                AdvancementHandler.award(escaped, AdvancementHandler.SOBREVIVE, "escapaste");
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
        // Linea de sal (§7): barrera anti-spawn que no afecta a emboscadas.
        if (com.elsilente.util.SaltBarrier.isProtected(level.getLevel(), pos)) {
            return false;
        }
        // Zona segura del Trofeo: nunca nace dentro (§14).
        if (TrofeoUmbralBlockEntity.findNearby(level.getLevel(), pos, TROPHY_ZONE) != null) {
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

    /**
     * FASE STALK: sopla las luces cercanas (antorchas, linternas, velas,
     * hogueras) antes de atacar. Nunca actua si el jugador le esta mirando.
     * Configurable via ElSilenteConfig.snuffLightsEnabled.
     */
    static class SnuffLightGoal extends Goal {
        private final ElSilenteEntity mob;
        private BlockPos lightPos;
        private int cooldown;

        SnuffLightGoal(ElSilenteEntity mob) {
            this.mob = mob;
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (!ElSilenteConfig.COMMON.snuffLightsEnabled.get() || this.mob.isAggro()) {
                return false;
            }
            // Zona segura del Trofeo: no entra ni apaga luces dentro (§14).
            if (this.mob.nearbyTrophy() != null) {
                return false;
            }
            if (this.cooldown > 0) {
                this.cooldown--;
                return false;
            }
            if (this.mob.getRandom().nextFloat() > 0.5F) {
                return false;
            }
            BlockPos found = findLight();
            if (found == null) {
                return false;
            }
            if (this.mob.nearbyTrophy(found) != null) {
                return false;
            }
            // Solo si nadie le esta mirando: si le ven, se queda quieto observando.
            Player watcher = this.mob.level().getNearestPlayer(this.mob, 24.0D);
            if (watcher != null && watcher.hasLineOfSight(this.mob)) {
                return false;
            }
            this.lightPos = found;
            this.mob.getNavigation().moveTo(found.getX() + 0.5D, found.getY(), found.getZ() + 0.5D, 1.0D);
            return true;
        }

        @Override
        public boolean canContinueToUse() {
            return !this.mob.isAggro() && this.lightPos != null
                    && isSnuffable(this.mob.level().getBlockState(this.lightPos))
                    && this.mob.distanceToSqr(this.lightPos.getX() + 0.5D, this.lightPos.getY(),
                            this.lightPos.getZ() + 0.5D) < 256.0D;
        }

        @Override
        public void stop() {
            this.lightPos = null;
            this.mob.getNavigation().stop();
        }

        @Override
        public void tick() {
            if (this.lightPos == null) {
                return;
            }
            this.mob.getLookControl().setLookAt(this.lightPos.getX() + 0.5D, this.lightPos.getY() + 0.5D,
                    this.lightPos.getZ() + 0.5D);
            if (this.mob.distanceToSqr(this.lightPos.getX() + 0.5D, this.lightPos.getY(),
                    this.lightPos.getZ() + 0.5D) < 2.6D) {
                snuff(this.lightPos);
                this.lightPos = null;
                this.cooldown = 200 + this.mob.getRandom().nextInt(200);
            }
        }

        /** Luz apagable a menos de 12 bloques (no en linea de vision del jugador). */
        private BlockPos findLight() {
            if (--this.mob.snuffSearchTimer > 0) {
                return null;
            }
            this.mob.snuffSearchTimer = 40;
            BlockPos origin = this.mob.blockPosition();
            for (BlockPos candidate : BlockPos.betweenClosed(origin.offset(-12, -4, -12),
                    origin.offset(12, 4, 12))) {
                if (isSnuffable(this.mob.level().getBlockState(candidate))) {
                    return candidate.immutable();
                }
            }
            return null;
        }

        /** Extiende la luz: apaga si tiene estado LIT, o la disuelve en humo. */
        private void snuff(BlockPos pos) {
            BlockState state = this.mob.level().getBlockState(pos);
            if (state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT)) {
                this.mob.level().setBlock(pos,
                        state.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT, false),
                        3);
            } else {
                this.mob.level().removeBlock(pos, false);
            }
            if (this.mob.level() instanceof ServerLevel server) {
                server.sendParticles(ParticleTypes.SMOKE, pos.getX() + 0.5D, pos.getY() + 0.7D,
                        pos.getZ() + 0.5D, 14, 0.15D, 0.25D, 0.15D, 0.01D);
            }
            this.mob.level().playSound(null, pos, SoundEvents.GENERIC_EXTINGUISH_FIRE,
                    SoundSource.HOSTILE, 0.7F, 0.8F);
        }

        /** Antorcha, linterna, vela u hoguera (fuego vivo). */
        private static boolean isSnuffable(BlockState state) {
            Block block = state.getBlock();
            if (block instanceof TorchBlock || block instanceof WallTorchBlock
                    || block instanceof LanternBlock) {
                return true;
            }
            if (block instanceof CampfireBlock) {
                return state.getValue(CampfireBlock.LIT);
            }
            if (block instanceof CandleBlock) {
                return state.getValue(CandleBlock.LIT);
            }
            return false;
        }
    }

    /**
     * FASE STALK: la base deja de ser segura. Si no hay linea de vision con el
     * jugador y hay una puerta cerrada cerca, camina hasta ella, la abre con
     * un rasgado de garras y la cruza hacia el otro lado. Configurable via
     * ElSilenteConfig.openDoorsEnabled.
     */
    static class OpenDoorGoal extends Goal {
        private final ElSilenteEntity mob;
        private BlockPos doorPos;
        private BlockPos crossPos;
        private boolean opened;
        private int activeTicks;

        OpenDoorGoal(ElSilenteEntity mob) {
            this.mob = mob;
            // Solo MOVE: el LOOK ya lo lleva StalkLookGoal (prioridad 1).
            this.setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (!ElSilenteConfig.COMMON.openDoorsEnabled.get() || this.mob.isAggro()) {
                return false;
            }
            if (this.mob.openDoorCooldown > 0) {
                this.mob.openDoorCooldown--;
                return false;
            }
            if (this.mob.getRandom().nextFloat() > 0.35F) {
                return false;
            }
            if (--this.mob.openDoorSearchTimer > 0) {
                return false;
            }
            this.mob.openDoorSearchTimer = 20;
            // Zona segura del Trofeo: ni cruza puertas dentro ni hacia dentro (§14).
            if (this.mob.nearbyTrophy() != null) {
                return false;
            }
            Player player = this.mob.level().getNearestPlayer(this.mob, 24.0D);
            if (player == null || !player.isAlive()) {
                return false;
            }
            // Solo si NO hay linea de vision: si le ves, se queda observando.
            if (this.mob.hasLineOfSight(player)) {
                return false;
            }
            BlockPos door = findClosedDoor();
            if (door == null || this.mob.nearbyTrophy(door) != null) {
                return false;
            }
            BlockPos through = crossingSpot(door, player);
            if (through == null) {
                return false;
            }
            this.doorPos = door;
            this.crossPos = through;
            this.opened = false;
            this.activeTicks = 0;
            this.mob.getNavigation().moveTo(door.getX() + 0.5D, door.getY(), door.getZ() + 0.5D, 1.1D);
            return true;
        }

        @Override
        public boolean canContinueToUse() {
            if (this.mob.isAggro() || this.activeTicks > 160) {
                return false;
            }
            if (this.opened) {
                return this.crossPos != null && this.mob.distanceToSqr(
                        this.crossPos.getX() + 0.5D, this.crossPos.getY(),
                        this.crossPos.getZ() + 0.5D) > 2.0D;
            }
            return this.doorPos != null
                    && this.mob.level().getBlockState(this.doorPos).getBlock() instanceof DoorBlock
                    && this.mob.distanceToSqr(this.doorPos.getX() + 0.5D, this.doorPos.getY(),
                    this.doorPos.getZ() + 0.5D) < 256.0D;
        }

        @Override
        public void stop() {
            this.doorPos = null;
            this.crossPos = null;
            this.opened = false;
            this.mob.getNavigation().stop();
        }

        @Override
        public void tick() {
            this.activeTicks++;
            if (!this.opened) {
                if (this.doorPos == null) {
                    return;
                }
                this.mob.getLookControl().setLookAt(this.doorPos.getX() + 0.5D,
                        this.doorPos.getY() + 0.5D, this.doorPos.getZ() + 0.5D);
                if (this.mob.distanceToSqr(this.doorPos.getX() + 0.5D, this.doorPos.getY(),
                        this.doorPos.getZ() + 0.5D) < 3.6D) {
                    openDoor();
                }
            } else if (this.crossPos != null) {
                // Cruza el umbral hacia el lado del jugador.
                this.mob.getNavigation().moveTo(this.crossPos.getX() + 0.5D, this.crossPos.getY(),
                        this.crossPos.getZ() + 0.5D, 1.15D);
            }
        }

        private void openDoor() {
            BlockPos pos = this.doorPos;
            if (pos == null) {
                return;
            }
            BlockState state = this.mob.level().getBlockState(pos);
            if (!(state.getBlock() instanceof DoorBlock)) {
                return;
            }
            if (state.hasProperty(BlockStateProperties.OPEN)) {
                // 3: actualiza vecinos (sincroniza la otra hoja de la puerta).
                this.mob.level().setBlock(pos, state.setValue(BlockStateProperties.OPEN, true), 3);
            }
            // Garras rasgando la madera + chirrido de la puerta.
            this.mob.level().playSound(null, pos, ModSounds.CLAWS.get(),
                    SoundSource.HOSTILE, 0.9F, 1.0F);
            this.mob.level().playSound(null, pos, SoundEvents.WOODEN_DOOR_OPEN,
                    SoundSource.HOSTILE, 0.8F, 0.85F);
            if (this.mob.level() instanceof ServerLevel server) {
                server.sendParticles(ParticleTypes.SMOKE, pos.getX() + 0.5D, pos.getY() + 0.5D,
                        pos.getZ() + 0.5D, 8, 0.2D, 0.3D, 0.2D, 0.01D);
            }
            this.opened = true;
            this.mob.openDoorCooldown = 240 + this.mob.getRandom().nextInt(240);
            if (this.crossPos != null) {
                this.mob.getNavigation().moveTo(this.crossPos.getX() + 0.5D, this.crossPos.getY(),
                        this.crossPos.getZ() + 0.5D, 1.15D);
            }
        }

        /** Puerta de madera cerrada, en oscuridad, a menos de 5 bloques. */
        private BlockPos findClosedDoor() {
            BlockPos origin = this.mob.blockPosition();
            BlockPos best = null;
            double bestDist = Double.MAX_VALUE;
            for (BlockPos candidate : BlockPos.betweenClosed(origin.offset(-5, -2, -5),
                    origin.offset(5, 2, 5))) {
                BlockState state = this.mob.level().getBlockState(candidate);
                if (!(state.getBlock() instanceof DoorBlock)) {
                    continue;
                }
                if (state.hasProperty(BlockStateProperties.OPEN) && state.getValue(BlockStateProperties.OPEN)) {
                    continue;
                }
                if (this.mob.level().getMaxLocalRawBrightness(candidate) > 7) {
                    continue;
                }
                double dist = candidate.distSqr(origin);
                if (dist < bestDist) {
                    bestDist = dist;
                    best = candidate.immutable();
                }
            }
            return best;
        }

        /** Celda al otro lado de la puerta, en la direccion del jugador. */
        private BlockPos crossingSpot(BlockPos door, Player player) {
            double dx = player.getX() - (door.getX() + 0.5D);
            double dz = player.getZ() - (door.getZ() + 0.5D);
            Direction dir = Math.abs(dx) > Math.abs(dz)
                    ? (dx > 0 ? Direction.EAST : Direction.WEST)
                    : (dz > 0 ? Direction.SOUTH : Direction.NORTH);
            BlockPos through = door.relative(dir);
            if (this.mob.level().getBlockState(through)
                    .getCollisionShape(this.mob.level(), through).isEmpty()
                    && !this.mob.level().getBlockState(through.below())
                    .getCollisionShape(this.mob.level(), through.below()).isEmpty()) {
                return through;
            }
            return null;
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
