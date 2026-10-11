package me.ifair.luahud.render;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.DiffuseLighting;
//? if <1.21.9 {
import net.minecraft.client.render.entity.EntityRenderDispatcher;
//?} else {
/*import net.minecraft.client.render.entity.EntityRenderManager;
*///?}
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.boss.dragon.EnderDragonPart;
import org.joml.Quaternionf;
import org.joml.Vector3f;

final class EntityPainter {
    private EntityPainter() {}

    @SuppressWarnings("deprecation")
    static void paint(
            DrawContext context,
            Entity requestedEntity,
            float iconScale,
            float yaw,
            float pitch,
            boolean fullBright,
            boolean useEntityRotation,
            float sectionScale,
            int x,
            int y
    ) {
        Entity entity = renderEntity(requestedEntity);
        if (entity == null || entity.isRemoved()) return;

        int size = RenderElementMeasurer.scaledIconSize(iconScale, sectionScale);
        float extent = Math.max(entity.getWidth(), entity.getHeight());
        if (!Float.isFinite(extent) || extent <= 0) return;
        float scale = size / extent;

        //? if <1.21.6 {
        float originalYaw = entity.getYaw();
        float originalPitch = entity.getPitch();
        LivingRotation livingRotation = LivingRotation.capture(entity);
        MatrixStack matrices = context.getMatrices();
        EntityRenderDispatcher dispatcher = MinecraftClient.getInstance().getEntityRenderDispatcher();
        Quaternionf dispatcherRotation = new Quaternionf(dispatcher.getRotation());

        if (!useEntityRotation) {
            entity.setYaw(180f);
            entity.setPitch(0f);
            livingRotation.faceForward();
        }
        matrices.push();
        try {
            float yawRadians = (float) Math.toRadians(yaw);
            float pitchRadians = (float) Math.toRadians(pitch);
            Quaternionf verticalRotation = new Quaternionf().rotateX(pitchRadians);
            matrices.translate(x + size / 2f, y + size / 2f, 50);
            matrices.scale(scale, scale, -scale);
            matrices.translate(0, entity.getHeight() / 2f, 0);
            matrices.multiply(new Quaternionf()
                    .rotateZ((float) Math.PI)
                    .mul(verticalRotation)
                    .rotateY(yawRadians));
            //? if <1.21.5 {
            DiffuseLighting.method_34742();
            //?} else {
            /*DiffuseLighting.enableGuiShaderLighting();
            *///?}
            dispatcher.setRotation(new Quaternionf(verticalRotation).conjugate().rotateY((float) Math.PI));
            dispatcher.setRenderShadows(false);
            float entityYaw = useEntityRotation ? originalYaw : 0;
            int light = fullBright ? 0xF000F0 : dispatcher.getLight(entity, 1);
            //? if <1.21.2 {
            RenderSystem.runAsFancy(() -> dispatcher.render(
                    entity, 0, 0, 0, entityYaw, 1, matrices, context.getVertexConsumers(), light));
            //?} else {
            /*context.draw(consumers -> dispatcher.render(
                    entity, 0, 0, 0, 1, matrices, consumers, light));
            *///?}
            context.draw();
        } finally {
            dispatcher.setRenderShadows(true);
            dispatcher.setRotation(dispatcherRotation);
            matrices.pop();
            DiffuseLighting.enableGuiDepthLighting();
            livingRotation.restore();
            entity.setYaw(originalYaw);
            entity.setPitch(originalPitch);
        }
        //?} else {
        /*var dispatcher = MinecraftClient.getInstance().getEntityRenderDispatcher();
        var state = createState(dispatcher.getRenderer(entity), entity, useEntityRotation);
        if (!useEntityRotation && state instanceof net.minecraft.client.render.entity.state.LivingEntityRenderState living) {
            living.bodyYaw = 180f;
            living.relativeHeadYaw = 0f;
            living.pitch = 0f;
        }
        state.displayName = null;
        state.leashDatas = null;
        state.onFire = false;
        // Lighting is read by the deferred entity renderer in 1.21.9+.
        //? if >=1.21.9 {
        state.light = fullBright ? 0xF000F0 : dispatcher.getLight(entity, 1);
        state.shadowPieces.clear();
        state.outlineColor = 0;
        //?} else {
        ((GuiEntityLight) state).luahud$setGuiLight(fullBright ? 0xF000F0 : dispatcher.getLight(entity, 1));
        state.hitbox = null;
        state.debugInfo = null;
        //?}
        Quaternionf vertical = new Quaternionf().rotateX((float) Math.toRadians(pitch));
        Quaternionf rotation = new Quaternionf().rotateZ((float) Math.PI)
                .mul(vertical).rotateY((float) Math.toRadians(yaw));
        // The deferred renderer clips to its render texture, even without a scissor.
        // Use the screen as the viewport so heads and animated limbs can overflow
        // the layout slot, keeping the original hitbox-based scale and position.
        int viewportWidth = context.getScaledWindowWidth();
        int viewportHeight = context.getScaledWindowHeight();
        context.addEntity(state, scale,
                viewportTranslation(x, y, size, entity.getHeight(), scale, viewportWidth, viewportHeight),
                rotation, vertical, 0, 0, viewportWidth, viewportHeight);
        *///?}
    }

    static Entity renderEntity(Entity entity) {
        // Dragon hitbox parts share their owner's entity type, but the renderer
        // only accepts the dragon itself. Resolve before sizing or rendering.
        return entity instanceof EnderDragonPart part ? part.owner : entity;
    }

    static Vector3f viewportTranslation(
            int x, int y, int size, float entityHeight, float scale, int viewportWidth, int viewportHeight) {
        // Translation is applied after scaling but before rotation. Compensate
        // for the new viewport center in model units, independent of animation.
        return new Vector3f(
                (x + size / 2f - viewportWidth / 2f) / scale,
                entityHeight / 2f + (y + size / 2f - viewportHeight / 2f) / scale,
                0);
    }

    private record LivingRotation(
            LivingEntity entity,
            float bodyYaw,
            float headYaw,
            float prevHeadYaw
    ) {
        private static LivingRotation capture(Entity entity) {
            return entity instanceof LivingEntity living
                    //? if <1.21.5 {
                    ? new LivingRotation(living, living.bodyYaw, living.headYaw, living.prevHeadYaw)
                    //?} else {
                    /* ? new LivingRotation(living, living.bodyYaw, living.headYaw, living.lastHeadYaw)
                    *///?}
                    : new LivingRotation(null, 0, 0, 0);
        }

        private void faceForward() {
            if (entity == null) return;
            entity.bodyYaw = 180f;
            entity.headYaw = 180f;
            //? if <1.21.5 {
            entity.prevHeadYaw = 180f;
            //?} else {
            /*entity.lastHeadYaw = 180f;
            *///?}
        }

        private void restore() {
            if (entity == null) return;
            entity.bodyYaw = bodyYaw;
            entity.headYaw = headYaw;
            //? if <1.21.5 {
            entity.prevHeadYaw = prevHeadYaw;
            //?} else {
            /*entity.lastHeadYaw = prevHeadYaw;
            *///?}
        }
    }

    //? if >=1.21.6 {
    /*private static <E extends Entity, S extends net.minecraft.client.render.entity.state.EntityRenderState> S createState(
            net.minecraft.client.render.entity.EntityRenderer<E, S> renderer, E entity, boolean useEntityRotation) {
        float originalYaw = entity.getYaw();
        float originalPitch = entity.getPitch();
        LivingRotation livingRotation = LivingRotation.capture(entity);
        if (!useEntityRotation) {
            entity.setYaw(180f);
            entity.setPitch(0f);
            livingRotation.faceForward();
        }
        try {
            // A fresh snapshot prevents multiple icons of one entity type from
            // sharing the renderer's mutable state while drawing is deferred.
            S state = renderer.createRenderState();
            renderer.updateRenderState(entity, state, 1);
            return state;
        } finally {
            livingRotation.restore();
            entity.setYaw(originalYaw);
            entity.setPitch(originalPitch);
        }
    }
    *///?}
}
