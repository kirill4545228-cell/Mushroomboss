package com.mushroomrealm.registry;

import com.mushroomrealm.MushroomRealm;
import com.mushroomrealm.entity.MushroomBeetleEntity;
import com.mushroomrealm.entity.MushroomBossEntity;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.SpawnRestriction;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.world.Heightmap;
import net.minecraft.world.biome.BiomeKeys;

public final class ModEntities {
	private ModEntities() {
	}

	public static final EntityType<MushroomBeetleEntity> MUSHROOM_BEETLE = Registry.register(
			Registries.ENTITY_TYPE,
			MushroomRealm.id("mushroom_beetle"),
			FabricEntityTypeBuilder.<MushroomBeetleEntity>createMob()
					.spawnGroup(SpawnGroup.MONSTER)
					.entityFactory(MushroomBeetleEntity::new)
					.spawnRestriction(SpawnRestriction.Location.ON_GROUND, Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, MushroomBeetleEntity::canSpawnBeetle)
					.dimensions(EntityDimensions.fixed(0.9f, 0.8f))
					.trackRangeBlocks(64)
					.build());

	public static final EntityType<MushroomBossEntity> MUSHROOM_BOSS = Registry.register(
			Registries.ENTITY_TYPE,
			MushroomRealm.id("mushroom_boss"),
			FabricEntityTypeBuilder.create(SpawnGroup.MONSTER, MushroomBossEntity::new)
					.dimensions(EntityDimensions.fixed(3.0f, 4.6f))
					.trackRangeBlocks(96)
					.build());

	public static void register() {
		FabricDefaultAttributeRegistry.register(MUSHROOM_BEETLE, MushroomBeetleEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(MUSHROOM_BOSS, MushroomBossEntity.createAttributes());

		// Beetles live in mushroom fields, dark forests and swamps
		BiomeModifications.addSpawn(
				BiomeSelectors.includeByKey(BiomeKeys.MUSHROOM_FIELDS, BiomeKeys.DARK_FOREST, BiomeKeys.SWAMP),
				SpawnGroup.MONSTER, MUSHROOM_BEETLE, 14, 1, 3);
	}
}
