package com.mushroomrealm.client.render;

import com.mushroomrealm.entity.MushroomBeetleEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;

public class BeetleRenderer extends BbEntityRenderer<MushroomBeetleEntity> {
	public BeetleRenderer(EntityRendererFactory.Context ctx) {
		super(ctx, "mushroom_beetle", 1.0F, 0.5F);
	}

	@Override
	protected String actionAnimation(int action) {
		return action == MushroomBeetleEntity.ACTION_ATTACK ? "attack" : "idle";
	}

	@Override
	protected String locomotionAnimation(MushroomBeetleEntity entity) {
		if (!entity.isOnGround() && !entity.isTouchingWater()) {
			return "fly";
		}
		return entity.isMovingForAnimation() ? "walk" : "idle";
	}
}
