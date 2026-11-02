package com.mushroomrealm.client.bb;

import java.io.BufferedReader;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.Resource;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Loads and caches the .bbmodel json files from assets/mushroomrealm/bbmodels/. */
public final class BbModels {
	private static final Logger LOGGER = LoggerFactory.getLogger("mushroomrealm");
	private static final Map<Identifier, BbModel> CACHE = new HashMap<>();

	private BbModels() {
	}

	public static BbModel get(Identifier id) {
		BbModel model = CACHE.get(id);
		if (model == null) {
			model = load(id);
			CACHE.put(id, model);
		}
		return model;
	}

	public static void clear() {
		CACHE.clear();
	}

	private static BbModel load(Identifier id) {
		try {
			Resource resource = MinecraftClient.getInstance().getResourceManager().getResource(id).orElseThrow();
			try (BufferedReader reader = resource.getReader()) {
				JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
				return BbModel.parse(json);
			}
		} catch (Exception e) {
			LOGGER.error("Failed to load Blockbench model {}", id, e);
			return BbModel.empty();
		}
	}
}
