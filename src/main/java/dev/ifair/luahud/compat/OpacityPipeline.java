package dev.ifair.luahud.compat;
//? if =1.21.5 {
/*import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.BlendFunction;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.util.Identifier;

// Opaque item/entity pipelines also need blending when shader alpha is reduced.
public final class OpacityPipeline extends RenderPipeline {
    private static final Map<RenderPipeline, RenderPipeline> CACHE = new IdentityHashMap<>();
    private OpacityPipeline(RenderPipeline source) {
        super(Identifier.of("luahud", "opacity/" + source.getLocation().getNamespace() + "/" + source.getLocation().getPath()),
                source.getVertexShader(), source.getFragmentShader(), source.getShaderDefines(),
                source.getSamplers(), source.getUniforms(), Optional.of(BlendFunction.TRANSLUCENT),
                source.getDepthTestFunction(), source.getPolygonMode(), source.isCull(), source.isWriteColor(),
                source.isWriteAlpha(), source.isWriteDepth(), source.getColorLogic(), source.getVertexFormat(),
                source.getVertexFormatMode(), source.getDepthBiasScaleFactor(), source.getDepthBiasConstant());
    }
    public static RenderPipeline blended(RenderPipeline source) {
        return source.getBlendFunction().isPresent() ? source : CACHE.computeIfAbsent(source, OpacityPipeline::new);
    }
}
*///?}
