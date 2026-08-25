
package net.zombiesleeping.client.renderer;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.Minecraft;

import net.zombiesleeping.entity.ScreamerEntity;
import net.zombiesleeping.client.model.ModelScreamer;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack;

public class ScreamerRenderer extends MobRenderer<ScreamerEntity, ModelScreamer<ScreamerEntity>> {
	public ScreamerRenderer(EntityRendererProvider.Context context) {
		super(context, new ModelScreamer<ScreamerEntity>(context.bakeLayer(ModelScreamer.LAYER_LOCATION)), 0.5f);
		this.addLayer(new RenderLayer<ScreamerEntity, ModelScreamer<ScreamerEntity>>(this) {
			final ResourceLocation LAYER_TEXTURE = new ResourceLocation("zombiesleeping:textures/entities/screamerlight.png");

			@Override
			public void render(PoseStack poseStack, MultiBufferSource bufferSource, int light, ScreamerEntity entity, float limbSwing, float limbSwingAmount, float partialTicks, float ageInTicks, float netHeadYaw, float headPitch) {
				VertexConsumer vertexConsumer = bufferSource.getBuffer(RenderType.eyes(LAYER_TEXTURE));
				EntityModel model = new ModelScreamer(Minecraft.getInstance().getEntityModels().bakeLayer(ModelScreamer.LAYER_LOCATION));
				this.getParentModel().copyPropertiesTo(model);
				model.prepareMobModel(entity, limbSwing, limbSwingAmount, partialTicks);
				model.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
				model.renderToBuffer(poseStack, vertexConsumer, light, LivingEntityRenderer.getOverlayCoords(entity, 0), 1, 1, 1, 1);
			}
		});
	}

	@Override
	public ResourceLocation getTextureLocation(ScreamerEntity entity) {
		return new ResourceLocation("zombiesleeping:textures/entities/screamer.png");
	}
}
