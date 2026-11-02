package com.mushroomrealm.entity;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.world.World;

/**
 * Base class for mobs that use Blockbench animations.
 * The server decides which "action" animation is playing (attack, slam, ...), the client
 * only reads it and plays the matching animation from the model.
 */
public abstract class BbMobEntity extends HostileEntity {
	protected static final TrackedData<Integer> ACTION = DataTracker.registerData(BbMobEntity.class, TrackedDataHandlerRegistry.INTEGER);

	private int actionTicks;
	private int actionLength;
	private int clientActionStart;

	protected BbMobEntity(EntityType<? extends HostileEntity> type, World world) {
		super(type, world);
	}

	@Override
	protected void initDataTracker() {
		super.initDataTracker();
		this.dataTracker.startTracking(ACTION, 0);
	}

	/** 0 = no action, otherwise an id defined by the subclass. */
	public int getAction() {
		return this.dataTracker.get(ACTION);
	}

	/** Server only. */
	protected void startAction(int action, int ticks) {
		this.actionTicks = ticks;
		this.actionLength = ticks;
		this.dataTracker.set(ACTION, action);
	}

	protected boolean isBusy() {
		return this.getAction() != 0;
	}

	/** Seconds since the current action started (client side). */
	public float getActionTime(float tickDelta) {
		return (this.age - this.clientActionStart + tickDelta) / 20.0F;
	}

	public boolean isMovingForAnimation() {
		return this.limbAnimator.getSpeed() > 0.02F;
	}

	@Override
	public void onTrackedDataSet(TrackedData<?> data) {
		super.onTrackedDataSet(data);
		if (ACTION.equals(data) && this.getWorld().isClient) {
			this.clientActionStart = this.age;
		}
	}

	@Override
	public void tick() {
		super.tick();
		if (!this.getWorld().isClient && this.actionTicks > 0) {
			this.actionTicks--;
			int action = this.getAction();
			this.onActionTick(action, this.actionLength - this.actionTicks);
			if (this.actionTicks == 0) {
				this.dataTracker.set(ACTION, 0);
				this.onActionEnd(action);
			}
		}
	}

	/** Called every server tick while an action runs. elapsed = ticks since it started (1..length). */
	protected void onActionTick(int action, int elapsed) {
	}

	protected void onActionEnd(int action) {
	}
}
