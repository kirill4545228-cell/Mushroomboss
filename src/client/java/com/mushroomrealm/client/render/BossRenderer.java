package com.mushroomrealm.client.render;

import com.mushroomrealm.entity.MushroomBossEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;

public class BossRenderer extends BbEntityRenderer<MushroomBossEntity> {
	public BossRenderer(EntityRendererFactory.Context ctx) {
		super(ctx, "mushroom_boss", 1.25F, 1.8F);
	}

	@Override
	protected String actionAnimation(int action) {
		return switch (action) {
			case MushroomBossEntity.ACTION_SLAM -> "attack_slam";
			case MushroomBossEntity.ACTION_ROAR -> "spore_roar";
			case MushroomBossEntity.ACTION_ENRAGE -> "enrage";
			case MushroomBossEntity.ACTION_SPAWN -> "spawn";
			case MushroomBossEntity.ACTION_HURT -> "hurt";
			default -> "idle";
		};
	}

	@Override
	protected String locomotionAnimation(MushroomBossEntity entity) {
		return entity.isMovingForAnimation() ? "walk" : "idle";
	}
}
