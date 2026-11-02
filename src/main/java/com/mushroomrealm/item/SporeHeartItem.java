package com.mushroomrealm.item;

import java.util.List;

import com.mushroomrealm.entity.MushroomBossEntity;
import com.mushroomrealm.registry.ModEntities;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/** Right click a giant mushroom block with it to awaken the Mushroom Colossus. */
public class SporeHeartItem extends Item {
	public SporeHeartItem(Settings settings) {
		super(settings);
	}

	@Override
	public ActionResult useOnBlock(ItemUsageContext context) {
		World world = context.getWorld();
		BlockState state = world.getBlockState(context.getBlockPos());
		if (!(state.isOf(Blocks.RED_MUSHROOM_BLOCK) || state.isOf(Blocks.BROWN_MUSHROOM_BLOCK) || state.isOf(Blocks.MUSHROOM_STEM))) {
			return ActionResult.PASS;
		}
		if (world.isClient || !(world instanceof ServerWorld serverWorld)) {
			return ActionResult.SUCCESS;
		}

		PlayerEntity player = context.getPlayer();
		BlockPos pos = context.getBlockPos().offset(context.getSide());

		if (!serverWorld.getEntitiesByType(ModEntities.MUSHROOM_BOSS, new Box(pos).expand(64.0), e -> true).isEmpty()) {
			if (player != null) {
				player.sendMessage(Text.translatable("message.mushroomrealm.boss_already_awake"), true);
			}
			return ActionResult.FAIL;
		}

		MushroomBossEntity boss = ModEntities.MUSHROOM_BOSS.create(serverWorld);
		if (boss == null) {
			return ActionResult.FAIL;
		}
		boss.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, context.getPlayerYaw() + 180.0F, 0.0F);
		if (!serverWorld.isSpaceEmpty(boss)) {
			if (player != null) {
				player.sendMessage(Text.translatable("message.mushroomrealm.no_space"), true);
			}
			return ActionResult.FAIL;
		}

		boss.startSpawn();
		serverWorld.spawnEntityAndPassengers(boss);
		serverWorld.playSound(null, pos, SoundEvents.ENTITY_RAVAGER_ROAR, SoundCategory.HOSTILE, 2.0F, 0.6F);

		if (player == null || !player.getAbilities().creativeMode) {
			context.getStack().decrement(1);
		}
		return ActionResult.CONSUME;
	}

	@Override
	public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
		tooltip.add(Text.translatable("item.mushroomrealm.spore_heart.tooltip").formatted(Formatting.GRAY));
	}
}
