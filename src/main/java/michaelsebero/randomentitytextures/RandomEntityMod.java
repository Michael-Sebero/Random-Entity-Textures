package michaelsebero.randomentitytextures;

import michaelsebero.randomentitytextures.client.RandomEntityClient;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(
    modid            = RandomEntityMod.MODID,
    name             = "Random Entity Textures",
    version          = RandomEntityMod.VERSION,
    clientSideOnly   = true,
    acceptedMinecraftVersions = "[1.12,1.13)"
)
@Mod.EventBusSubscriber(modid = RandomEntityMod.MODID, value = Side.CLIENT)
public class RandomEntityMod {

    public static final String MODID   = "randomentitytextures";
    public static final String VERSION = "1.0.0";
    public static final Logger LOG     = LogManager.getLogger(MODID);

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        LOG.info("Random Entity Textures initialising.");
    }

    // ── Cache invalidation ────────────────────────────────────────────────────
    //
    // TextureStitchEvent.Post fires on initial load AND on every F3+T resource
    // pack reload, so cached NO_VARIANTS sentinels and variant lists are always
    // flushed when the active resource pack changes.
    //
    // The previous ColorHandlerEvent.Block fired exactly once during FML init
    // when the cache was already empty -- it was a no-op that never triggered
    // again on resource reload.
    //
    // NOTE: Entity tracking (setting/clearing currentEntity) is no longer done
    // here via RenderLivingEvent. It is now handled directly by RenderManagerMixin
    // injecting into RenderLivingBase.doRender (SRG func_76986_a). This avoids
    // the FG 2.3 issue where @Mod.EventBusSubscriber scans the class for handler
    // methods AFTER @SideOnly ASM stripping has already run, which can result in
    // no handlers being registered.

    @SubscribeEvent
    public static void onTextureStitch(TextureStitchEvent.Post event) {
        RandomEntityClient.clearCache();
    }
}
