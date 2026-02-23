package michaelsebero.randomentitytextures.mixin;

import net.minecraft.client.renderer.RenderGlobal;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Retained as an empty stub so the entry in mixins.randomentitytextures.json
 * does not need to change (removing a registered mixin class can cause
 * startup errors in some Mixin 0.7.x builds).
 *
 * Entity tracking was moved to RenderManagerMixin (now targeting
 * RenderLivingBase.doRender). See that class for details.
 */
@Mixin(RenderGlobal.class)
public abstract class RenderGlobalMixin {
    // No injections.
}
