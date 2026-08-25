package net.zombiesleeping.entity;

import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.network.PlayMessages;
import net.minecraftforge.network.NetworkHooks;

import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;

import net.zombiesleeping.init.ZombiesleepingModEntities;
import net.zombiesleeping.block.ZombieremainsBlock;
import net.zombiesleeping.procedures.ConfigProcedure;

import java.util.EnumSet;
import java.util.List;
import java.util.ArrayList;

public class ScreamerEntity extends Monster {
    // DataWatcher für Client-Server Sync
    private static final EntityDataAccessor<Boolean> IS_SITTING = SynchedEntityData.defineId(ScreamerEntity.class, EntityDataSerializers.BOOLEAN);
    
    private int screamCooldown = 0;
    private int ambientSoundTimer = 0;
    private int sittingTime = 0;
    private int movementTimer = 0; // Timer um zu tracken wie lange keine Bewegung
    private BlockPos lastPos;
    
    private static final int SCREAM_COOLDOWN_TICKS = 100; // 5 Sekunden
    private static final int SCREAM_RANGE = 16;
    private static final int AMBIENT_SOUND_MIN_DELAY = 200; // 10 Sekunden
    private static final int AMBIENT_SOUND_MAX_DELAY = 400; // 20 Sekunden
    private static final int PREFERRED_DISTANCE = 8; // Bevorzugter Abstand zum Spieler
    private static final int MIN_DISTANCE = 4; // Minimaler Abstand - ab hier wegrennen
    private static final int MOVEMENT_CHECK_TIME = 100; // 5 Sekunden ohne Bewegung = hinsetzen

    public ScreamerEntity(PlayMessages.SpawnEntity packet, Level world) {
        this(ZombiesleepingModEntities.SCREAMER.get(), world);
    }

    public ScreamerEntity(EntityType<ScreamerEntity> type, Level world) {
        super(type, world);
        setMaxUpStep(0.6f);
        xpReward = 0;
        setNoAi(false);
        this.ambientSoundTimer = random.nextInt(AMBIENT_SOUND_MAX_DELAY - AMBIENT_SOUND_MIN_DELAY) + AMBIENT_SOUND_MIN_DELAY;
        this.sittingTime = 200 + random.nextInt(400);
        this.lastPos = this.blockPosition();
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(IS_SITTING, true); 
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
        this.goalSelector.addGoal(1, new ScreamAndKeepDistanceGoal(this));
        this.goalSelector.addGoal(3, new RandomLookAroundGoal(this));
        this.goalSelector.addGoal(4, new FloatGoal(this));
    }

    @Override
    public void tick() {
        super.tick();
        
        if (screamCooldown > 0) {
            screamCooldown--;
        }
        BlockPos currentPos = this.blockPosition();
        if (!currentPos.equals(lastPos)) {
            movementTimer = 0; // Reset bei Bewegung
            lastPos = currentPos;
			setSitting(false);
        } else {
            movementTimer++;
        }
        
        if (getTarget() == null) {
            if (!isSitting()) {
                if (movementTimer >= MOVEMENT_CHECK_TIME || (random.nextInt(600) == 0 && movementTimer > 20)) {
                    setSitting(true);
                    sittingTime = 200 + random.nextInt(400);
                    movementTimer = 0;
                }
            }
        } else {
            if (isSitting()) {
                setSitting(false);
            }
        }
        
        if (!this.level().isClientSide && ambientSoundTimer > 0) {
            ambientSoundTimer--;
            if (ambientSoundTimer <= 0) {
                playRandomAmbientSound();
                ambientSoundTimer = random.nextInt(AMBIENT_SOUND_MAX_DELAY - AMBIENT_SOUND_MIN_DELAY) + AMBIENT_SOUND_MIN_DELAY;
            }
        }
    }

    private void playRandomAmbientSound() {
        int soundNumber = 3 + random.nextInt(14); // 3-16
        String soundName = "zombiesleeping:screamer" + soundNumber;
        SoundEvent sound = ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation(soundName));
        
        if (sound != null) {
            float pitch = 0.8f + random.nextFloat() * 0.4f;
            this.level().playSound(null, this.getX(), this.getY(), this.getZ(), 
                sound, SoundSource.HOSTILE, 1.0f, pitch);
        }
    }

    public void performScreamAttack() {
        if (screamCooldown > 0 || this.level().isClientSide) {
            return;
        }

        // Setze Cooldown
        screamCooldown = SCREAM_COOLDOWN_TICKS;

        // Spiele Schrei-Sound ab (screamer17-20)
        int screamSoundNumber = 17 + random.nextInt(4); // 17-20
        String screamSoundName = "zombiesleeping:screamer" + screamSoundNumber;
        SoundEvent screamSound = ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation(screamSoundName));
        
        if (screamSound != null) {
            this.level().playSound(null, this.getX(), this.getY(), this.getZ(), 
                screamSound, SoundSource.HOSTILE, 2.0f, 1.0f);
        }

		if (this.level() instanceof ServerLevel serverLevel) {
            createScreamParticles(serverLevel);
            convertZombieRemainsInRange(serverLevel);
            alertNearbyMobs(serverLevel);

            // NEU: Dem Target-Player die Config-Effekte geben
            LivingEntity target = this.getTarget();
            if (target instanceof Player player) {
                for (ConfigProcedure.ConfiguredEffect configuredEffect : ConfigProcedure.screamerTargetEffects) {
                    player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                        configuredEffect.effect,
                        configuredEffect.duration,
                        configuredEffect.amplifier,
                        false, // ambient (gibt an ob es von einem Beacon kommt)
                        true   // visible (Partikel sichtbar)
                    ));
                }
            }
        }
    }

    private void createScreamParticles(ServerLevel level) {
        double centerX = this.getX();
        double centerY = this.getY() + 1.0;
        double centerZ = this.getZ();
        
        for (int angle = 0; angle < 360; angle += 10) {
            double radians = Math.toRadians(angle);
            double x = centerX + Math.cos(radians) * SCREAM_RANGE;
            double z = centerZ + Math.sin(radians) * SCREAM_RANGE;
            
            level.sendParticles(ParticleTypes.SOUL, x, centerY, z, 3, 0.2, 0.5, 0.2, 0.1);
        }
        
        for (int i = 0; i < 50; i++) {
            double angle = random.nextDouble() * 2 * Math.PI;
            double distance = random.nextDouble() * SCREAM_RANGE;
            double x = centerX + Math.cos(angle) * distance;
            double z = centerZ + Math.sin(angle) * distance;
            double y = centerY + random.nextDouble() * 2 - 1;
            
            level.sendParticles(ParticleTypes.SCULK_SOUL, x, y, z, 1, 0.1, 0.1, 0.1, 0.05);
        }
    }

    private void convertZombieRemainsInRange(ServerLevel level) {
        BlockPos screamerPos = this.blockPosition();
        Player targetPlayer = this.getTarget() instanceof Player ? (Player) this.getTarget() : null;

        for (int x = -SCREAM_RANGE; x <= SCREAM_RANGE; x++) {
            for (int y = -4; y <= 4; y++) {
                for (int z = -SCREAM_RANGE; z <= SCREAM_RANGE; z++) {
                    BlockPos pos = screamerPos.offset(x, y, z);
                    double distance = screamerPos.distSqr(pos);
                    
                    if (distance <= SCREAM_RANGE * SCREAM_RANGE) {
                        // GEAENDERT: erst pruefen, ob der Chunk ueberhaupt geladen ist.
                        // Ohne diesen Check wuerde getBlockState() fuer ungeladene
                        // Positionen den Chunk synchron nachladen bzw. sogar neu
                        // generieren - bei ~9800 gepruesten Positionen pro Schrei
                        // waere das ein potenziell sehr teurer Ruckler, wenn der
                        // Screamer nahe am Rand des geladenen Bereichs steht.
                        if (!level.isLoaded(pos)) {
                            continue;
                        }

                        BlockState state = level.getBlockState(pos);

                        if (state.getBlock() instanceof ZombieremainsBlock) {
                            // GEAENDERT: nicht mehr sofort spawnen, sondern in dieselbe
                            // Pipeline einreihen wie der normale randomTick. Verhindert,
                            // dass ein Schrei in einem dicht bebauten Remains-Feld
                            // dutzende Mobs im selben Tick auf einmal erzeugt.
                            net.zombiesleeping.ZombieSpawnPipeline.enqueue(level, pos.immutable(), targetPlayer, true);
                        }
                    }
                }
            }
        }
    }

    private void alertNearbyMobs(ServerLevel level) {
        // GEAENDERT: erst pruefen, ob es ueberhaupt ein Ziel gibt - die
        // Entity-Suche im 32-Block-Wuerfel ist die teurere Operation und
        // war vorher unnoetig, wenn kein Ziel vorhanden war.
        Player target = this.getTarget() instanceof Player ? (Player) this.getTarget() : null;
        if (target == null) return;

        AABB searchArea = new AABB(this.blockPosition()).inflate(SCREAM_RANGE);
        List<Mob> nearbyMobs = level.getEntitiesOfClass(Mob.class, searchArea);

        for (Mob mob : nearbyMobs) {
            if (mob != this && mob.isAlive()) {
                if (mob.getTarget() == null) {
                    mob.setTarget(target);
                }
                
                // GEFIXT: Verhindert das unendliche Stacking des Speed-Buffs über Tags
                // und macht den Buff-Multiplier über die Config einstellbar.
                if (!mob.getTags().contains("screamer_buffed")) {
                    mob.addTag("screamer_buffed");
                    if (mob.getAttribute(Attributes.MOVEMENT_SPEED) != null) {
                        double buffMultiplier = ConfigProcedure.SCREAMER_SPEED_BUFF_MULTIPLIER.get();
                        mob.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(
                            mob.getAttribute(Attributes.MOVEMENT_SPEED).getBaseValue() * buffMultiplier
                        );
                    }
                }
            }
        }
    }

    public boolean canScream() {
        return screamCooldown <= 0;
    }

    // Neue Methoden für Client-Server Sync
    public boolean isSitting() {
        return this.entityData.get(IS_SITTING);
    }

    public void setSitting(boolean sitting) {
        this.entityData.set(IS_SITTING, sitting);
    }

    @Override
    public MobType getMobType() {
        return MobType.UNDEAD;
    }

    @Override
    public SoundEvent getAmbientSound() {
        return null;
    }

    @Override
    public SoundEvent getHurtSound(DamageSource ds) {
        return ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("zombiesleeping:screamer1"));
    }

    @Override
    public SoundEvent getDeathSound() {
        return ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("zombiesleeping:screamer2"));
    }

    @Override
    public boolean hurt(DamageSource damagesource, float amount) {
        if (damagesource.is(DamageTypes.DROWN))
            return false;

        setSitting(false);
        return super.hurt(damagesource, amount);
    }

    @Override
    public boolean canBreatheUnderwater() {
        return true;
    }

    public static void init() {
    }

    public static AttributeSupplier.Builder createAttributes() {
        AttributeSupplier.Builder builder = Mob.createMobAttributes();
        builder = builder.add(Attributes.MOVEMENT_SPEED, 0.3);
        builder = builder.add(Attributes.MAX_HEALTH, 15);
        builder = builder.add(Attributes.ARMOR, 0);
        builder = builder.add(Attributes.ATTACK_DAMAGE, 3);
        builder = builder.add(Attributes.FOLLOW_RANGE, 32);
        return builder;
    }

    public static class ScreamAndKeepDistanceGoal extends Goal {
        private final ScreamerEntity screamer;
        private LivingEntity target;
        private int attackTime = 0;
        private static final int ATTACK_INTERVAL = 60;

        public ScreamAndKeepDistanceGoal(ScreamerEntity screamer) {
            this.screamer = screamer;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity target = this.screamer.getTarget();
            if (target == null || !target.isAlive()) {
                return false;
            }

            this.target = target;
            return this.screamer.distanceToSqr(target) <= ScreamerEntity.SCREAM_RANGE * ScreamerEntity.SCREAM_RANGE;
        }

        @Override
        public boolean canContinueToUse() {
            return this.target != null && 
                   this.target.isAlive() && 
                   this.screamer.distanceToSqr(this.target) <= ScreamerEntity.SCREAM_RANGE * ScreamerEntity.SCREAM_RANGE * 2;
        }

        @Override
        public void start() {
            this.attackTime = 0;
        }

        @Override
        public void stop() {
            this.target = null;
            this.attackTime = 0;
            this.screamer.getNavigation().stop();
        }

        @Override
        public void tick() {
            if (this.target == null) return;
            
            this.screamer.getLookControl().setLookAt(this.target, 30.0F, 30.0F);
            
            double distance = this.screamer.distanceToSqr(this.target);
            double actualDistance = Math.sqrt(distance);
            
            if (actualDistance < ScreamerEntity.MIN_DISTANCE) {
                runAwayFromTarget();
            } else if (actualDistance > ScreamerEntity.PREFERRED_DISTANCE) {
                approachTarget();
            } else {
                this.screamer.getNavigation().stop();
            }
            
            this.attackTime++;
            if (this.attackTime >= ATTACK_INTERVAL && this.screamer.canScream()) {
                this.screamer.performScreamAttack();
                this.attackTime = 0;
            }
        }

        private void runAwayFromTarget() {
            double deltaX = this.screamer.getX() - this.target.getX();
            double deltaZ = this.screamer.getZ() - this.target.getZ();
            
            double distance = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
            if (distance > 0) {
                deltaX /= distance;
                deltaZ /= distance;
            }
            
            double fleeDistance = ScreamerEntity.PREFERRED_DISTANCE;
            double fleeX = this.screamer.getX() + deltaX * fleeDistance;
            double fleeZ = this.screamer.getZ() + deltaZ * fleeDistance;
            
            this.screamer.getNavigation().moveTo(fleeX, this.screamer.getY(), fleeZ, 2.2D);
        }

        private void approachTarget() {
            this.screamer.getNavigation().moveTo(this.target, 0.8D);
        }
    }
}