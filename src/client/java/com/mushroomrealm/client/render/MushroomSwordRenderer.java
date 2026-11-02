package com.mushroomrealm.client.render;

import com.mushroomrealm.MushroomRealm;
import com.mushroomrealm.client.bb.BbModel;
import com.mushroomrealm.client.bb.BbModels;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;

/**
 * Draws the Mushroom Sword from its animated Blockbench model.
 * The item model json (models/item/mushroom_sword.json) is "builtin/entity" and carries the
 * "display" transforms from mushroom_sword_java.bbmodel. While the player swings the sword in hand,
 * the "attack" animation of the model is played.
 */
public class MushroomSwordRenderer implements BuiltinItemRendererRegistry.DynamicItemRenderer {
	private static final Identifier MODEL = MushroomRealm.id("bbmodels/mushroom_sword.json");
	private static final Identifier TEXTURE = MushroomRealm.id("textures/item/mushroom_sword.png");

	private static final double OFFSET_X = 0.0;
	private static final double OFFSET_Z = 0.0;

	private long swingStartNanos = -1L;
	private float lastSwingProgress = 0.0F;

	@Override
	public void render(ItemStack stack, ModelTransformationMode mode, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, int overlay) {
		BbModel model = BbModels.get(MODEL);
		BbModel.Animation attack = model.getAnimation("attack");

		float time = 0.0F;
		boolean inHand = mode == ModelTransformationMode.FIRST_PERSON_LEFT_HAND
				|| mode == ModelTransformationMode.FIRST_PERSON_RIGHT_HAND
				|| mode == ModelTransformationMode.THIRD_PERSON_LEFT_HAND
				|| mode == ModelTransformationMode.THIRD_PERSON_RIGHT_HAND;
		if (inHand && attack != null) {
			MinecraftClient client = MinecraftClient.getInstance();
			ClientPlayerEntity player = client.player;
			if (player != null && player.getMainHandStack().getItem() == stack.getItem()) {
				float progress = player.getHandSwingProgress(client.getTickDelta());
				if (progress > 0.0F && (this.lastSwingProgress <= 0.0F || progress < this.lastSwingProgress)) {
					this.swingStartNanos = System.nanoTime();
				}
				this.lastSwingProgress = progress;
				if (this.swingStartNanos >= 0L) {
					float elapsed = (System.nanoTime() - this.swingStartNanos) / 1.0E9F;
					if (elapsed < attack.length) {
						time = elapsed;
					} else {
						this.swingStartNanos = -1L;
					}
				}
			}
		}

		matrices.push();
		// Coordinates are used exactly like in the Blockbench java-block preview (the "display" transforms were tuned for them).
		// If the sword looks off-center in game you can shift it here, e.g. OFFSET_X = 0.5 (= 8 px).
		matrices.translate(OFFSET_X, 0.0, OFFSET_Z);
		VertexConsumer vc = vertexConsumers.getBuffer(RenderLayer.getEntityCutoutNoCull(TEXTURE));
		model.render(matrices, vc, light, overlay, attack, time, null);
		matrices.pop();
	}
}
