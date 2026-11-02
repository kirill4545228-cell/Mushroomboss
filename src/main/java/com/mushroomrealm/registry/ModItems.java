package com.mushroomrealm.registry;

import com.mushroomrealm.MushroomRealm;
import com.mushroomrealm.item.MushroomSwordItem;
import com.mushroomrealm.item.SporeHeartItem;
import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;
import net.minecraft.util.Rarity;

public final class ModItems {
	private ModItems() {
	}

	public static final Item MUSHROOM_SHARD = register("mushroom_shard", new Item(new FabricItemSettings()));
	public static final Item FUNGAL_CORE = register("fungal_core", new Item(new FabricItemSettings().rarity(Rarity.RARE)));
	public static final Item SPORE_HEART = register("spore_heart", new SporeHeartItem(new FabricItemSettings().maxCount(16).rarity(Rarity.EPIC)));
	public static final Item MUSHROOM_SWORD = register("mushroom_sword", new MushroomSwordItem(new FabricItemSettings().rarity(Rarity.EPIC)));

	public static final Item MUSHROOM_BEETLE_SPAWN_EGG = register("mushroom_beetle_spawn_egg",
			new SpawnEggItem(ModEntities.MUSHROOM_BEETLE, 0x604436, 0xDB3B35, new FabricItemSettings()));
	public static final Item MUSHROOM_BOSS_SPAWN_EGG = register("mushroom_boss_spawn_egg",
			new SpawnEggItem(ModEntities.MUSHROOM_BOSS, 0x5A4238, 0xF9F6EB, new FabricItemSettings()));

	public static final ItemGroup GROUP = Registry.register(Registries.ITEM_GROUP, MushroomRealm.id("main"),
			FabricItemGroup.builder()
					.icon(() -> new ItemStack(MUSHROOM_SWORD))
					.displayName(Text.translatable("itemGroup.mushroomrealm.main"))
					.entries((context, entries) -> {
						entries.add(MUSHROOM_SWORD);
						entries.add(MUSHROOM_SHARD);
						entries.add(FUNGAL_CORE);
						entries.add(SPORE_HEART);
						entries.add(MUSHROOM_BEETLE_SPAWN_EGG);
						entries.add(MUSHROOM_BOSS_SPAWN_EGG);
					})
					.build());

	private static Item register(String name, Item item) {
		return Registry.register(Registries.ITEM, MushroomRealm.id(name), item);
	}

	public static void register() {
		// forces class initialisation (registration happens in static fields)
	}
}
