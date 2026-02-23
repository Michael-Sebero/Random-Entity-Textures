package michaelsebero.randomentitytextures.mixin;

import michaelsebero.randomentitytextures.client.RandomEntityClient;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.util.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Intercepts every {@link TextureManager#bindTexture} call while an entity is
 * being rendered, replacing the {@link ResourceLocation} with a random variant
 * if one is configured in the resource pack.
 *
 * SRG: bindTexture -> func_110577_a
 *
 * WHY @ModifyVariable and not @ModifyArg:
 *   @ModifyArg requires an INVOKE instruction at the injection point — it
 *   intercepts an argument being passed into a nested call. Injecting at HEAD
 *   means there is no such instruction yet, so Mixin throws
 *   InvalidInjectionException.
 *
 *   @ModifyVariable rewrites the value of a local variable slot directly.
 *   With index = 1 (and no argsOnly), we target LVT slot 1, which for any
 *   non-static method is the first parameter:
 *     slot 0 = this  (TextureManager instance)
 *     slot 1 = first parameter (ResourceLocation)
 *
 * WHY argsOnly WAS REMOVED:
 *   The previous code used argsOnly = true with index = 1. The semantics of
 *   argsOnly vary between Mixin 0.7.x patch releases: some builds treat the
 *   index as 0-based from the arg list (making index=1 the non-existent second
 *   arg), others keep it as the LVT index. Removing argsOnly and using the
 *   raw LVT index = 1 is unambiguous across all versions.
 */
@Mixin(value = TextureManager.class, priority = 800)
public abstract class TextureManagerMixin {

    @ModifyVariable(
        method = "func_110577_a(Lnet/minecraft/util/ResourceLocation;)V",
        at = @At("HEAD"),
        index = 1,
        remap = false
    )
    private ResourceLocation randomentity$replaceTexture(ResourceLocation original) {
        if (!RandomEntityClient.isRenderingEntities()) {
            return original;
        }
        return RandomEntityClient.getVariantTexture(original);
    }
}
