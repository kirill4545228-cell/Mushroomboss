package com.mushroomrealm.entity;

import net.minecraft.entity.AreaEffectCloudEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.entity.ai.goal.RevengeGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.ServerWorldAccess;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Mushroom Beetle: fast hostile bug. Leaps at its target, glides down slowly,
 * poisons on bite and bursts into a spore cloud when it dies.
 */
public class MushroomBeetleEntity extends BbMobEntity {
	public static final int ACTION_ATTACK = 1;

	private int leapCooldown = 40;

	public MushroomBeetleEntity(EntityType<? extends MushroomBeetleEntity> type, World world) {
		super(type, world);
		this.experiencePoints = 6;
	}

	public static DefaultAttributeContainer.Builder createAttributes() {
		return HostileEntity.createHostileAttributes()
				.add(EntityAttributes.GENERIC_MAX_HEALTH, 22.0)
				.add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.3)
				.add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 4.0)
				.add(EntityAttributes.GENERIC_FOLLOW_RANGE, 24.0)
				.add(EntityAttributes.GENERIC_ARMOR, 4.0);
	}

	public static boolean canSpawnBeetle(EntityType<MushroomBeetleEntity> type, ServerWorldAccess world, SpawnReason reason, BlockPos pos, Random random) {
		return HostileEntity.canSpawnInDark(type, world, reason, pos, random);
	}

	@Override
	protected void initGoals() {
		this.goalSelector.add(0, new SwimGoal(this));
		this.goalSelector.add(2, new MeleeAttackGoal(this, 1.15, false));
		this.goalSelector.add(5, new WanderAroundFarGoal(this, 0.8));
		this.goalSelector.add(6, new LookAtEntityGoal(this, PlayerEntity.class, 8.0F));
		this.goalSelector.add(6, new LookAroundGoal(this));
		this.targetSelector.add(1, new RevengeGoal(this));
		this.targetSelector.add(2, new ActiveTargetGoal<>(this, PlayerEntity.class, true));
	}

	@Override
	public boolean tryAttack(Entity target) {
		boolean hit = super.tryAttack(target);
		if (hit) {
			if (this.getAction() == 0) {
				this.startAction(ACTION_ATTACK, 12);
			}
			if (target instanceof LivingEntity living) {
				living.addStatusEffect(new StatusEffectInstance(StatusEffects.POISON, 80, 0), this);
			}
		}
		return hit;
	}

	@Override
	public void tickMovement() {
		super.tickMovement();

		// glide: slow fall (the model has wings)
		if (!this.isOnGround() && !this.isTouchingWater()) {
			Vec3d v = this.getVelocity();
			if (v.y < -0.08) {
				this.setVelocity(v.x, -0.08, v.z);
			}
			this.fallDistance = 0.0F;
		}

		if (!this.getWorld().isClient) {
			if (this.leapCooldown > 0) {
				this.leapCooldown--;
			}
			LivingEntity target = this.getTarget();
			if (target != null && this.isOnGround() && this.leapCooldown <= 0) {
				double distSq = this.squaredDistanceTo(target);
				if (distSq > 9.0 && distSq < 64.0 && this.random.nextInt(6) == 0) {
					Vec3d dir = new Vec3d(target.getX() - this.getX(), 0.0, target.getZ() - this.getZ()).normalize();
					this.setVelocity(dir.x * 0.6, 0.45, dir.z * 0.6);
					this.velocityDirty = true;
					this.leapCooldown = 60 + this.random.nextInt(40);
				}
			}
		}
	}

	@Override
	public boolean handleFallDamage(float fallDistance, float damageMultiplier, DamageSource damageSource) {
		return false;
	}

	@Override
	public void onDeath(DamageSource damageSource) {
		super.onDeath(damageSource);
		if (!this.getWorld().isClient) {
			AreaEffectCloudEntity cloud = new AreaEffectCloudEntity(this.getWorld(), this.getX(), this.getY(), this.getZ());
			cloud.setOwner(this);
			cloud.setRadius(2.0F);
			cloud.setDuration(60);
			cloud.setParticleType(ParticleTypes.SPORE_BLOSSOM_AIR);
			cloud.addEffect(new StatusEffectInstance(StatusEffects.POISON, 60, 0));
			this.getWorld().spawnEntity(cloud);
		}
	}

	@Override
	public boolean canHaveStatusEffect(StatusEffectInstance effect) {
		if (effect.getEffectType() == StatusEffects.POISON) {
			return false;
		}
		return super.canHaveStatusEffect(effect);
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.ENTITY_SILVERFISH_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.ENTITY_SILVERFISH_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.ENTITY_SILVERFISH_DEATH;
	}

	@Override
	protected void playStepSound(BlockPos pos, BlockState state) {
		this.playSound(SoundEvents.ENTITY_SILVERFISH_STEP, 0.15F, 1.0F);
	}
}
