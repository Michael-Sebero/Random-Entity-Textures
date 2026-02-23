package michaelsebero.randomentitytextures.client;

import michaelsebero.randomentitytextures.RandomEntityMod;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.ResourceLocation;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Core engine for Random Entity Textures.
 *
 * Entity tracking is maintained by RenderManagerMixin which injects into
 * RenderLivingBase.doRender (func_76986_a):
 *   HEAD   -> nextEntity(entity)   set before any bindTexture call
 *   RETURN -> nextEntity(null)     clear after all bindTexture calls
 *
 * TextureManagerMixin intercepts every TextureManager.bindTexture call and,
 * while currentEntity is non-null, redirects to a variant texture.
 */
public final class RandomEntityClient {

    // ── Rendering state ───────────────────────────────────────────────────────

    private static EntityLivingBase currentEntity = null;

    // ── Texture cache ─────────────────────────────────────────────────────────

    private static final String PREFIX_ENTITY  = "textures/entity/";
    private static final String PREFIX_MCPATCH = "mcpatcher/mob/";
    private static final String SUFFIX_PNG     = ".png";
    private static final String SUFFIX_PROPS   = ".properties";

    private static final String[] DEPENDENT_SUFFIXES = {
        "_armor", "_eyes", "_exploding", "_shooting",
        "_fur", "_invulnerable", "_angry", "_tame", "_collar"
    };

    private static final Map<String, RetProperties> CACHE = new HashMap<>();

    // Sentinel: stored when a path has no variants so we never re-scan it.
    // Must be compared with == (identity), never .equals().
    private static final RetProperties NO_VARIANTS = new RetProperties((ResourceLocation[]) null);

    private RandomEntityClient() {}

    // ── State machine callbacks (called by RenderManagerMixin) ────────────────

    public static void nextEntity(EntityLivingBase entity) {
        currentEntity = entity;
    }

    public static boolean isRenderingEntities() {
        return currentEntity != null;
    }

    // ── Texture lookup ────────────────────────────────────────────────────────

    public static ResourceLocation getVariantTexture(ResourceLocation original) {
        // Gate 1: are we inside a doRender call?
        if (currentEntity == null) {
            return original;
        }

        // Gate 2: did EntityMixin successfully mix TrackedEntity into this entity?
        // If this fires but currentEntity is never a TrackedEntity, EntityMixin
        // is not being applied — check your mixins JSON lists EntityMixin.
        if (!(currentEntity instanceof TrackedEntity)) {
            RandomEntityMod.LOG.warn("RandomEntityTextures: currentEntity is not a TrackedEntity ({}). " +
                "Verify that EntityMixin is registered in your mixins JSON.",
                currentEntity.getClass().getName());
            return original;
        }

        // Gate 3: only process mob textures (textures/entity/...)
        String path = original.getResourcePath();
        if (!path.startsWith(PREFIX_ENTITY)) {
            return original;
        }

        // Cache lookup / build
        RetProperties props = CACHE.get(path);
        if (props == null) {
            props = buildProperties(original);
            if (props != null) {
                RandomEntityMod.LOG.info("RandomEntityTextures: loaded variants for {}", path);
                CACHE.put(path, props);
            } else {
                // No variants found in the resource pack for this texture.
                // This is normal for textures that have no numbered variants or
                // .properties file. Check that your pack puts files under
                // assets/<domain>/mcpatcher/mob/ (e.g. mcpatcher/mob/zombie/zombie2.png).
                RandomEntityMod.LOG.debug("RandomEntityTextures: no variants found for {} — " +
                    "pack may not have mcpatcher/mob/ variants for this mob.", path);
                CACHE.put(path, NO_VARIANTS);
            }
        }

        // Gate 4: sentinel / invalid
        if (props == NO_VARIANTS || !props.isValid()) {
            return original;
        }

        return props.getTexture(original, (TrackedEntity) currentEntity);
    }

    public static void clearCache() {
        CACHE.clear();
        RandomEntityMod.LOG.info("RandomEntityTextures: texture variant cache cleared.");
    }

    // ── Properties building ───────────────────────────────────────────────────

    private static RetProperties buildProperties(ResourceLocation textureLoc) {
        ResourceLocation mcpLoc = toMcpatcherLocation(textureLoc);
        if (mcpLoc == null) return null;

        // Try a .properties file first (supports biome / height rules).
        ResourceLocation propsFile = findPropertiesFile(mcpLoc);
        if (propsFile != null) {
            Properties raw = loadProperties(propsFile);
            if (raw != null) {
                RetProperties result = new RetProperties(raw, textureLoc);
                if (result.isValid()) return result;
            }
        }

        // Fall back to auto-detected numbered variants.
        ResourceLocation[] variants = autoDetectVariants(textureLoc, mcpLoc);
        if (variants != null) {
            return new RetProperties(variants);
        }

        return null;
    }

    private static ResourceLocation findPropertiesFile(ResourceLocation mcpLoc) {
        String path = mcpLoc.getResourcePath();
        String base = path.endsWith(SUFFIX_PNG)
            ? path.substring(0, path.length() - SUFFIX_PNG.length())
            : path;

        // Direct: mcpatcher/mob/zombie/zombie.properties
        ResourceLocation direct = new ResourceLocation(mcpLoc.getResourceDomain(), base + SUFFIX_PROPS);
        if (hasResource(direct)) return direct;

        // Parent: for dependent textures like zombie_eyes look for zombie.properties
        for (String suffix : DEPENDENT_SUFFIXES) {
            if (base.endsWith(suffix)) {
                String parentBase = base.substring(0, base.length() - suffix.length());
                ResourceLocation parent = new ResourceLocation(
                    mcpLoc.getResourceDomain(), parentBase + SUFFIX_PROPS);
                if (hasResource(parent)) return parent;
            }
        }
        return null;
    }

    private static ResourceLocation[] autoDetectVariants(ResourceLocation original,
                                                          ResourceLocation mcpBase) {
        List<ResourceLocation> list = new ArrayList<>();

        // Index 0 is always the original vanilla textures/entity/ path, exactly
        // as OptiFine does in RandomMobs.getTextureVariants. This ensures that
        // the vanilla texture is always part of the rotation even when the pack
        // does not supply a mcpatcher base (zombie.png) — only variants
        // (zombie2.png, zombie3.png, …).
        list.add(original);

        // Scan for mcpatcher/mob/zombie/zombie2.png, zombie3.png, … up to 10
        // consecutive misses. This terminates reliably even with numbering gaps.
        int consecutiveMisses = 0;
        for (int i = 1; consecutiveMisses < 10; i++) {
            ResourceLocation candidate = getIndexedLocation(mcpBase, i + 1);
            if (candidate != null && hasResource(candidate)) {
                list.add(candidate);
                consecutiveMisses = 0;
            } else {
                consecutiveMisses++;
            }
        }

        if (list.size() <= 1) return null;

        RandomEntityMod.LOG.info("RandomEntityTextures: {} — {} variants auto-detected",
            original.getResourcePath(), list.size());
        return list.toArray(new ResourceLocation[0]);
    }

    // ── ResourceLocation helpers ──────────────────────────────────────────────

    public static ResourceLocation toMcpatcherLocation(ResourceLocation loc) {
        String path = loc.getResourcePath();
        if (!path.startsWith(PREFIX_ENTITY)) return null;
        return new ResourceLocation(loc.getResourceDomain(),
            PREFIX_MCPATCH + path.substring(PREFIX_ENTITY.length()));
    }

    public static ResourceLocation getIndexedLocation(ResourceLocation base, int index) {
        if (base == null) return null;
        String path = base.getResourcePath();
        int dot = path.lastIndexOf('.');
        if (dot < 0) return null;
        return new ResourceLocation(base.getResourceDomain(),
            path.substring(0, dot) + index + path.substring(dot));
    }

    public static boolean hasResource(ResourceLocation loc) {
        if (loc == null) return false;
        try {
            Minecraft.getMinecraft().getResourceManager().getResource(loc);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private static Properties loadProperties(ResourceLocation loc) {
        try {
            Properties props = new Properties();
            props.load(Minecraft.getMinecraft().getResourceManager()
                .getResource(loc).getInputStream());
            return props;
        } catch (IOException e) {
            RandomEntityMod.LOG.warn("RandomEntityTextures: could not read properties file: {}", loc);
            return null;
        }
    }
}
