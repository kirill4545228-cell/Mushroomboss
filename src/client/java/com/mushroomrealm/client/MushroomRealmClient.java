package com.mushroomrealm.client;

import com.mushroomrealm.MushroomRealm;
import com.mushroomrealm.client.bb.BbModels;
import com.mushroomrealm.client.render.BeetleRenderer;
import com.mushroomrealm.client.render.BossRenderer;
import com.mushroomrealm.client.render.MushroomSwordRenderer;
import com.mushroomrealm.registry.ModEntities;
import com.mushroomrealm.registry.ModItems;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;

public class MushroomRealmClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		EntityRendererRegistry.register(ModEntities.MUSHROOM_BEETLE, BeetleRenderer::new);
		EntityRendererRegistry.register(ModEntities.MUSHROOM_BOSS, BossRenderer::new);

		BuiltinItemRendererRegistry.INSTANCE.register(ModItems.MUSHROOM_SWORD, new MushroomSwordRenderer());

		// drop cached models when resources are reloaded (F3+T)
		ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
			@Override
			public Identifier getFabricId() {
				return MushroomRealm.id("bbmodels");
			}

			@Override
			public void reload(ResourceManager manager) {
				BbModels.clear();
			}
		});
	}
}
