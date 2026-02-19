package michaelsebero.randommobtextures.mixin;

import michaelsebero.randommobtextures.client.RandomMobsClient;
import net.minecraft.client.renderer.entity.RenderLivingBase;
import net.minecraft.entity.EntityLivingBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Sets and clears the "currently rendering entity" state in
 * {@link RandomMobsClient} by injecting into {@link RenderLivingBase#doRender}.
 *
 * This is the direct equivalent of what OptiFine does: OptiFine patches
 * RenderManager.renderEntitySimple via ASM to set/clear
 * renderGlobal.renderedEntity before and after each entity's render call.
 * We achieve the same result by injecting into RenderLivingBase.doRender,
 * which is the method that actually performs the rendering (and within which
 * bindEntityTexture / TextureManager.bindTexture are called).
 *
 * WHY THIS REPLACES THE FORGE-EVENT APPROACH:
 *   RandomMobsMod previously used @Mod.EventBusSubscriber with
 *   RenderLivingEvent.Pre/Post to set currentEntity. That approach has a
 *   subtle but fatal flaw under ForgeGradle 2.3: @Mod.EventBusSubscriber
 *   scans the class for @SubscribeEvent methods after class-load-time ASM
 *   transformers (including @SideOnly stripping) have already run. Depending
 *   on transformation order, the event handler methods can be stripped before
 *   the bus subscriber scanner sees them, leaving nothing registered. A direct
 *   Mixin injection has no such dependency on transformation order.
 *
 * SRG / descriptor reference (1.12.2):
 *   RenderLivingBase.doRender(EntityLivingBase, double, double, double, float, float)
 *     -> func_76986_a(Lnet/minecraft/entity/EntityLivingBase;DDDFF)V
 *
 *   'T' in RenderLivingBase<T extends EntityLivingBase> erases to EntityLivingBase
 *   in the bytecode of the override, which is what the descriptor must reflect.
 *   remap = false bypasses the refmap entirely and targets the method directly
 *   by its runtime SRG name and descriptor, which is stable and guaranteed.
 */
@Mixin(RenderLivingBase.class)
public abstract class RenderManagerMixin {

    @Inject(
        method = "func_76986_a(Lnet/minecraft/entity/EntityLivingBase;DDDFF)V",
        at = @At("HEAD"),
        remap = false
    )
    private void randommobs$beginRender(EntityLivingBase entity, double x, double y, double z,
                                         float yaw, float partialTicks, CallbackInfo ci) {
        RandomMobsClient.nextEntity(entity);
    }

    @Inject(
        method = "func_76986_a(Lnet/minecraft/entity/EntityLivingBase;DDDFF)V",
        at = @At("RETURN"),
        remap = false
    )
    private void randommobs$endRender(EntityLivingBase entity, double x, double y, double z,
                                       float yaw, float partialTicks, CallbackInfo ci) {
        RandomMobsClient.nextEntity(null);
    }
}
