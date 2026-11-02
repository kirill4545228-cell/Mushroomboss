package com.mushroomrealm.entity;

import java.util.EnumSet;
import java.util.List;

import com.mushroomrealm.registry.ModEntities;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.AreaEffectCloudEntity;
import net.minecraft.entity.EntityData;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.RevengeGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.world.ServerWorldAccess;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Mushroom Colossus (boss).
 * - spawn animation (rises from the ground, invulnerable)
 * - Slam: ground pound that damages and throws back everything around it
 * - Spore Roar: poison/nausea spore cloud and summons mushroom beetles
 * - Enrage at 50% health: faster, stronger, shorter cooldowns
 * - 3 second death animation
 */
public class MushroomBossEntity extends BbMobEntity {
	public static final int ACTION_SLAM = 1;
	public static final int ACTION_ROAR = 2;
	public static final int ACTION_ENRAGE = 3;
	public static final int ACTION_SPAWN = 4;
	public static final int ACTION_HURT = 5;

	private static final int DEATH_TICKS = 60;

	private final ServerBossBar bossBar = new ServerBossBar(this.getDisplayName(), BossBar.Color.PURPLE, BossBar.Style.NOTCHED_10);

	private int slamCooldown = 60;
	private int roarCooldown = 160;
	private boolean enraged;

	public MushroomBossEntity(EntityType<? extends MushroomBossEntity> type, World world) {
		super(type, world);
		this.experiencePoints = 600;
	}

	public static DefaultAttributeContainer.Builder createAttributes() {
		return HostileEntity.createHostileAttributes()
				.add(EntityAttributes.GENERIC_MAX_HEALTH, 320.0)
				.add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.27)
				.add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 14.0)
				.add(EntityAttributes.GENERIC_FOLLOW_RANGE, 40.0)
				.add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 0.9)
				.add(EntityAttributes.GENERIC_ARMOR, 8.0);
	}

	/** Called by the Spore Heart item. */
	public void startSpawn() {
		this.startAction(ACTION_SPAWN, 40);
	}

	@Override
	public EntityData initialize(ServerWorldAccess world, LocalDifficulty difficulty, SpawnReason spawnReason, @Nullable EntityData entityData, @Nullable NbtCompound entityNbt) {
		EntityData data = super.initialize(world, difficulty, spawnReason, entityData, entityNbt);
		this.startSpawn();
		return data;
	}

	// ---------------------------------------------------------------- AI

	@Override
	protected void initGoals() {
		this.goalSelector.add(0, new SwimGoal(this));
		this.goalSelector.add(1, new ChaseGoal());
		this.goalSelector.add(5, new WanderAroundFarGoal(this, 0.6));
		this.goalSelector.add(6, new LookAtEntityGoal(this, PlayerEntity.class, 16.0F));
		this.goalSelector.add(6, new LookAroundGoal(this));
		this.targetSelector.add(1, new RevengeGoal(this));
		this.targetSelector.add(2, new ActiveTargetGoal<>(this, PlayerEntity.class, true));
	}

	private class ChaseGoal extends Goal {
		private int repath;

		ChaseGoal() {
			this.setControls(EnumSet.of(Goal.Control.MOVE, Goal.Control.LOOK));
		}

		@Override
		public boolean canStart() {
			LivingEntity target = MushroomBossEntity.this.getTarget();
			return target != null && target.isAlive();
		}

		@Override
		public boolean shouldContinue() {
			return this.canStart();
		}

		@Override
		public void tick() {
			MushroomBossEntity boss = MushroomBossEntity.this;
			LivingEntity target = boss.getTarget();
			if (target == null) {
				return;
			}
			boss.getLookControl().lookAt(target, 30.0F, 30.0F);
			if (boss.isBusy()) {
				boss.getNavigation().stop();
				return;
			}
			if (boss.squaredDistanceTo(target) > 12.0) {
				if (--this.repath <= 0) {
					this.repath = 8;
					boss.getNavigation().startMovingTo(target, 1.0);
				}
			} else {
				boss.getNavigation().stop();
			}
		}
	}

	@Override
	protected boolean isImmobile() {
		return super.isImmobile() || this.getAction() == ACTION_SPAWN;
	}

	@Override
	public boolean isInvulnerableTo(DamageSource damageSource) {
		if (this.getAction() == ACTION_SPAWN && !damageSource.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return true;
		}
		return super.isInvulnerableTo(damageSource);
	}

	@Override
	protected void mobTick() {
		super.mobTick();
		this.bossBar.setPercent(this.getHealth() / this.getMaxHealth());

		if (this.slamCooldown > 0) {
			this.slamCooldown--;
		}
		if (this.roarCooldown > 0) {
			this.roarCooldown--;
		}

		LivingEntity target = this.getTarget();
		if (this.isBusy() || target == null || !target.isAlive()) {
			return;
		}

		if (!this.enraged && this.getHealth() < this.getMaxHealth() * 0.5F) {
			this.enraged = true;
			this.applyEnrage();
			this.startAction(ACTION_ENRAGE, 48);
			return;
		}

		double distSq = this.squaredDistanceTo(target);
		if (this.slamCooldown <= 0 && distSq < 36.0) {
			this.startAction(ACTION_SLAM, 28);
			this.slamCooldown = this.enraged ? 50 : 90;
		} else if (this.roarCooldown <= 0 && distSq < 400.0) {
			this.startAction(ACTION_ROAR, 40);
			this.roarCooldown = this.enraged ? 240 : 380;
		}
	}

	private void applyEnrage() {
		EntityAttributeInstance speed = this.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
		if (speed != null) {
			speed.setBaseValue(0.34);
		}
		EntityAttributeInstance damage = this.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE);
		if (damage != null) {
			damage.setBaseValue(18.0);
		}
	}

	// ---------------------------------------------------------------- actions

	@Override
	protected void onActionTick(int action, int elapsed) {
		if (action == ACTION_SLAM || action == ACTION_ROAR || action == ACTION_ENRAGE) {
			// stand still while performing an attack
			this.setVelocity(0.0, this.getVelocity().y, 0.0);
			this.getNavigation().stop();
		}
		switch (action) {
			case ACTION_SLAM -> {
				if (elapsed == 14) {
					this.doSlam();
				}
			}
			case ACTION_ROAR -> {
				if (elapsed == 18) {
					this.doRoar();
				}
			}
			case ACTION_ENRAGE -> {
				if (elapsed == 20) {
					this.doEnrageBlast();
				}
			}
			default -> {
			}
		}
	}

	private void doSlam() {
		if (!(this.getWorld() instanceof ServerWorld sw)) {
			return;
		}
		float damage = (float) this.getAttributeValue(EntityAttributes.GENERIC_ATTACK_DAMAGE);
		Box box = this.getBoundingBox().expand(4.0, 1.0, 4.0);
		List<LivingEntity> victims = sw.getEntitiesByClass(LivingEntity.class, box, e -> e != this && e.isAlive() && !(e instanceof BbMobEntity));
		for (LivingEntity victim : victims) {
			victim.damage(sw.getDamageSources().mobAttack(this), damage);
			victim.takeKnockback(1.4, this.getX() - victim.getX(), this.getZ() - victim.getZ());
			victim.setVelocity(victim.getVelocity().add(0.0, 0.5, 0.0));
			victim.velocityModified = true;
		}
		BlockState mushroom = Blocks.RED_MUSHROOM_BLOCK.getDefaultState();
		sw.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, mushroom), this.getX(), this.getY() + 0.2, this.getZ(),
				80, 2.5, 0.2, 2.5, 0.2);
		sw.spawnParticles(ParticleTypes.EXPLOSION, this.getX(), this.getY() + 0.5, this.getZ(), 3, 1.5, 0.1, 1.5, 0.0);
		sw.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.HOSTILE, 1.6F, 0.6F);
	}

	private void doRoar() {
		if (!(this.getWorld() instanceof ServerWorld sw)) {
			return;
		}
		sw.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ENTITY_RAVAGER_ROAR, SoundCategory.HOSTILE, 2.0F, 0.7F);

		AreaEffectCloudEntity cloud = new AreaEffectCloudEntity(sw, this.getX(), this.getY(), this.getZ());
		cloud.setOwner(this);
		cloud.setRadius(6.0F);
		cloud.setDuration(140);
		cloud.setParticleType(ParticleTypes.SPORE_BLOSSOM_AIR);
		cloud.addEffect(new StatusEffectInstance(StatusEffects.POISON, 100, 1));
		cloud.addEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 140, 0));
		sw.spawnEntity(cloud);

		int existing = sw.getEntitiesByType(ModEntities.MUSHROOM_BEETLE, this.getBoundingBox().expand(24.0), e -> true).size();
		int count = this.enraged ? 4 : 3;
		for (int i = 0; i < count && existing + i < 8; i++) {
			MushroomBeetleEntity beetle = ModEntities.MUSHROOM_BEETLE.create(sw);
			if (beetle == null) {
				continue;
			}
			double x = this.getX() + this.random.nextGaussian() * 3.0;
			double z = this.getZ() + this.random.nextGaussian() * 3.0;
			beetle.refreshPositionAndAngles(x, this.getY(), z, this.random.nextFloat() * 360.0F, 0.0F);
			beetle.initialize(sw, sw.getLocalDifficulty(beetle.getBlockPos()), SpawnReason.MOB_SUMMONED, null, null);
			if (this.getTarget() != null) {
				beetle.setTarget(this.getTarget());
			}
			sw.spawnEntityAndPassengers(beetle);
		}
	}

	private void doEnrageBlast() {
		if (!(this.getWorld() instanceof ServerWorld sw)) {
			return;
		}
		sw.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ENTITY_RAVAGER_ROAR, SoundCategory.HOSTILE, 2.5F, 0.5F);
		List<LivingEntity> victims = sw.getEntitiesByClass(LivingEntity.class, this.getBoundingBox().expand(8.0, 2.0, 8.0),
				e -> e != this && e.isAlive() && !(e instanceof BbMobEntity));
		for (LivingEntity victim : victims) {
			victim.takeKnockback(1.8, this.getX() - victim.getX(), this.getZ() - victim.getZ());
			victim.velocityModified = true;
			victim.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 200, 0), this);
		}
		sw.spawnParticles(ParticleTypes.SPORE_BLOSSOM_AIR, this.getX(), this.getY() + 2.0, this.getZ(), 200, 4.0, 2.0, 4.0, 0.1);
	}

	@Override
	public boolean damage(DamageSource source, float amount) {
		boolean result = super.damage(source, amount);
		if (result && !this.getWorld().isClient && !this.isBusy() && this.isAlive()) {
			this.startAction(ACTION_HURT, 10);
		}
		return result;
	}

	@Override
	public boolean canHaveStatusEffect(StatusEffectInstance effect) {
		if (effect.getEffectType() == StatusEffects.POISON || effect.getEffectType() == StatusEffects.NAUSEA || effect.getEffectType() == StatusEffects.WITHER) {
			return false;
		}
		return super.canHaveStatusEffect(effect);
	}

	// ---------------------------------------------------------------- death

	@Override
	protected void updatePostDeath() {
		this.deathTime++;
		if (!this.getWorld().isClient && this.getWorld() instanceof ServerWorld sw && this.deathTime % 4 == 0) {
			sw.spawnParticles(ParticleTypes.SPORE_BLOSSOM_AIR, this.getX(), this.getY() + 2.0, this.getZ(), 10, 1.5, 1.5, 1.5, 0.02);
		}
		if (this.deathTime >= DEATH_TICKS && !this.getWorld().isClient && !this.isRemoved()) {
			this.getWorld().sendEntityStatus(this, (byte) 60);
			this.remove(RemovalReason.KILLED);
		}
	}

	// ---------------------------------------------------------------- boss bar / persistence

	@Override
	public void onStartedTrackingBy(ServerPlayerEntity player) {
		super.onStartedTrackingBy(player);
		this.bossBar.addPlayer(player);
	}

	@Override
	public void onStoppedTrackingBy(ServerPlayerEntity player) {
		super.onStoppedTrackingBy(player);
		this.bossBar.removePlayer(player);
	}

	@Override
	public void setCustomName(@Nullable Text name) {
		super.setCustomName(name);
		this.bossBar.setName(this.getDisplayName());
	}

	@Override
	public void remove(RemovalReason reason) {
		super.remove(reason);
		this.bossBar.clearPlayers();
	}

	@Override
	public void checkDespawn() {
		// the boss never despawns
	}

	@Override
	public void writeCustomDataToNbt(NbtCompound nbt) {
		super.writeCustomDataToNbt(nbt);
		nbt.putBoolean("Enraged", this.enraged);
	}

	@Override
	public void readCustomDataFromNbt(NbtCompound nbt) {
		super.readCustomDataFromNbt(nbt);
		this.enraged = nbt.getBoolean("Enraged");
		if (this.enraged) {
			this.applyEnrage();
		}
		if (this.hasCustomName()) {
			this.bossBar.setName(this.getDisplayName());
		}
	}

	// ---------------------------------------------------------------- sounds

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.ENTITY_RAVAGER_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.ENTITY_RAVAGER_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.ENTITY_RAVAGER_DEATH;
	}

	@Override
	protected void playStepSound(BlockPos pos, BlockState state) {
		this.playSound(SoundEvents.ENTITY_RAVAGER_STEP, 0.6F, 0.7F);
	}
}
