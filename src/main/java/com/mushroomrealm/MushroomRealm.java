package com.mushroomrealm;

import com.mushroomrealm.registry.ModEntities;
import com.mushroomrealm.registry.ModItems;
import net.fabricmc.api.ModInitializer;
import net.minecraft.util.Identifier;

public class MushroomRealm implements ModInitializer {
	public static final String MOD_ID = "mushroomrealm";

	public static Identifier id(String path) {
		return new Identifier(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		ModEntities.register();
		ModItems.register();
	}
}
