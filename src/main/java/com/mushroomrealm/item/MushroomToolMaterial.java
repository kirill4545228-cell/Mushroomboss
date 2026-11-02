package com.mushroomrealm.item;

import com.mushroomrealm.registry.ModItems;
import net.minecraft.item.ToolMaterial;
import net.minecraft.recipe.Ingredient;

public enum MushroomToolMaterial implements ToolMaterial {
	INSTANCE;

	@Override
	public int getDurability() {
		return 1800;
	}

	@Override
	public float getMiningSpeedMultiplier() {
		return 7.0F;
	}

	@Override
	public float getAttackDamage() {
		return 3.0F; // + 3 from SwordItem = 6 base damage
	}

	@Override
	public int getMiningLevel() {
		return 3;
	}

	@Override
	public int getEnchantability() {
		return 18;
	}

	@Override
	public Ingredient getRepairIngredient() {
		return Ingredient.ofItems(ModItems.MUSHROOM_SHARD);
	}
}
