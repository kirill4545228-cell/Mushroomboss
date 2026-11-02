package com.mushroomrealm.client.render;

import com.mushroomrealm.MushroomRealm;
import com.mushroomrealm.client.bb.BbModel;
import com.mushroomrealm.client.bb.BbModels;
import com.mushroomrealm.entity.BbMobEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

/** Renders a mob from a Blockbench model + its animations. */
public abstract class BbEntityRenderer<T extends BbMobEntity> extends EntityRenderer<T> {
	private final Identifier modelId;
	private final Identifier texture;
	private final float scale;

	protected BbEntityRenderer(EntityRendererFactory.Context ctx, String name, float scale, float shadowRadius) {
		super(ctx);
		this.modelId = MushroomRealm.id("bbmodels/" + name + ".json");
		this.texture = MushroomRealm.id("textures/entity/" + name + ".png");
		this.scale = scale;
		this.shadowRadius = shadowRadius;
	}

	/** Name of the animation for an action id from the entity (see BbMobEntity#getAction). */
	protected abstract String actionAnimation(int action);

	/** Looping animation while no action is running (idle / walk / fly ...). */
	protected abstract String locomotionAnimation(T entity);

	@Override
	public Identifier getTexture(T entity) {
		return this.texture;
	}

	@Override
	public void render(T entity, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light) {
		BbModel model = BbModels.get(this.modelId);

		String animName;
		float time;
		boolean dead = entity.isDead();
		if (dead) {
			animName = "death";
			time = (entity.deathTime + tickDelta) / 20.0F;
		} else if (entity.getAction() != 0) {
			animName = this.actionAnimation(entity.getAction());
			time = entity.getActionTime(tickDelta);
		} else {
			animName = this.locomotionAnimation(entity);
			time = (entity.age + tickDelta) / 20.0F;
		}
		BbModel.Animation anim = model.getAnimation(animName);

		float bodyYaw = MathHelper.lerpAngleDegrees(tickDelta, entity.prevBodyYaw, entity.bodyYaw);
		float headYaw = MathHelper.lerpAngleDegrees(tickDelta, entity.prevHeadYaw, entity.headYaw);
		float yawDelta = MathHelper.clamp(MathHelper.wrapDegrees(headYaw - bodyYaw), -50.0F, 50.0F);
		float pitch = MathHelper.clamp(entity.getPitch(tickDelta), -40.0F, 40.0F);
		BbModel.BoneHook hook = dead ? null : (bone, pos, rot, sc) -> {
			if ("head".equals(bone)) {
				rot[1] -= yawDelta;
				rot[0] -= pitch;
			}
		};

		matrices.push();
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0F - bodyYaw));
		matrices.scale(this.scale, this.scale, this.scale);

		int overlay = LivingEntityRenderer.getOverlay(entity, 0.0F);
		VertexConsumer vc = vertexConsumers.getBuffer(RenderLayer.getEntityCutoutNoCull(this.texture));
		model.render(matrices, vc, light, overlay, anim, time, hook);
		matrices.pop();

		super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light);
	}
}
