package com.mushroomrealm.item;

import java.util.List;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Mushroom Sword.
 * - Every hit poisons the target, 30% chance to also weaken it.
 * - Right click: Spore Burst - damages, poisons and slows everything around you, heals you a bit.
 */
public class MushroomSwordItem extends SwordItem {
	private static final int BURST_COOLDOWN = 160; // 8 seconds
	private static final double BURST_RADIUS = 5.0;

	public MushroomSwordItem(Settings settings) {
		super(MushroomToolMaterial.INSTANCE, 3, -2.4F, settings);
	}

	@Override
	public boolean postHit(ItemStack stack, LivingEntity target, LivingEntity attacker) {
		target.addStatusEffect(new StatusEffectInstance(StatusEffects.POISON, 100, 1), attacker);
		if (attacker.getRandom().nextFloat() < 0.3F) {
			target.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 100, 0), attacker);
		}
		if (attacker.getWorld() instanceof ServerWorld serverWorld) {
			serverWorld.spawnParticles(ParticleTypes.SPORE_BLOSSOM_AIR, target.getX(), target.getBodyY(0.5), target.getZ(),
					14, 0.3, 0.4, 0.3, 0.02);
		}
		return super.postHit(stack, target, attacker);
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);
		if (user.getItemCooldownManager().isCoolingDown(this)) {
			return TypedActionResult.pass(stack);
		}

		if (!world.isClient && world instanceof ServerWorld serverWorld) {
			List<LivingEntity> targets = serverWorld.getEntitiesByClass(LivingEntity.class,
					user.getBoundingBox().expand(BURST_RADIUS, 2.0, BURST_RADIUS),
					e -> e != user && e.isAlive() && !e.isTeammate(user));
			for (LivingEntity target : targets) {
				target.damage(world.getDamageSources().playerAttack(user), 6.0F);
				target.addStatusEffect(new StatusEffectInstance(StatusEffects.POISON, 80, 1), user);
				target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 100, 1), user);
				target.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 100, 0), user);
				target.takeKnockback(0.7, user.getX() - target.getX(), user.getZ() - target.getZ());
			}
			if (!targets.isEmpty()) {
				user.heal(2.0F);
			}
			serverWorld.spawnParticles(ParticleTypes.SPORE_BLOSSOM_AIR, user.getX(), user.getBodyY(0.5), user.getZ(),
					80, 2.5, 0.6, 2.5, 0.05);
			serverWorld.spawnParticles(ParticleTypes.MYCELIUM, user.getX(), user.getBodyY(0.3), user.getZ(),
					60, 2.5, 0.4, 2.5, 0.1);
			world.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.ENTITY_EVOKER_CAST_SPELL,
					SoundCategory.PLAYERS, 1.0F, 0.7F);
			stack.damage(4, user, p -> p.sendToolBreakStatus(hand));
		}

		user.getItemCooldownManager().set(this, BURST_COOLDOWN);
		user.incrementStat(net.minecraft.stat.Stats.USED.getOrCreateStat(this));
		return TypedActionResult.success(stack, world.isClient());
	}

	@Override
	public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
		tooltip.add(Text.translatable("item.mushroomrealm.mushroom_sword.tooltip.hit").formatted(Formatting.GREEN));
		tooltip.add(Text.translatable("item.mushroomrealm.mushroom_sword.tooltip.use").formatted(Formatting.DARK_GREEN));
	}
}
