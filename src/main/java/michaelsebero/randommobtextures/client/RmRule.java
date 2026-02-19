package michaelsebero.randommobtextures.client;

import net.minecraft.util.ResourceLocation;
import net.minecraft.world.biome.Biome;

/**
 * Represents a single numbered rule block from a mob .properties file, e.g.:
 *
 *   skins.1   = 1-4 6
 *   weights.1 = 10 10 10 10 2
 *   biomes.1  = Ocean DeepOcean
 *   heights.1 = 0-40
 *
 * A rule matches an entity when ALL specified constraints (biome, height) are
 * satisfied. If no constraints are set the rule always matches.
 */
public final class RmRule {

    private final ResourceLocation[] textures;
    private final int[] cumWeights;
    private final int   totalWeight;
    private final Biome[] biomes;
    private final int minHeight;
    private final int maxHeight;

    // ── Construction ──────────────────────────────────────────────────────────

    RmRule(ResourceLocation[] textures,
           int[] cumWeights, int totalWeight,
           Biome[] biomes,
           int minHeight, int maxHeight) {
        this.textures    = textures;
        this.cumWeights  = cumWeights;
        this.totalWeight = totalWeight;
        this.biomes      = biomes;
        this.minHeight   = minHeight;
        this.maxHeight   = maxHeight;
    }

    // ── Matching ──────────────────────────────────────────────────────────────

    public boolean matches(TrackedEntity entity) {
        // Biome check
        if (biomes != null && biomes.length > 0) {
            Biome entityBiome = entity.mcp$initialBiome();
            if (entityBiome == null) return false;
            boolean found = false;
            for (Biome b : biomes) {
                if (b == entityBiome) { found = true; break; }
            }
            if (!found) return false;
        }

        // Height check
        if (minHeight != Integer.MIN_VALUE || maxHeight != Integer.MAX_VALUE) {
            int y = entity.mcp$initialY();
            if (y < minHeight || y > maxHeight) return false;
        }

        return true;
    }

    // ── Texture selection ─────────────────────────────────────────────────────

    public ResourceLocation getTexture(TrackedEntity entity) {
        if (textures.length == 0) return null;
        int entityRandomId = entity.mcp$randomId();

        int index;
        if (cumWeights == null) {
            index = Math.abs(entityRandomId) % textures.length;
        } else {
            int bucket = Math.abs(entityRandomId) % totalWeight;
            index = textures.length - 1;
            for (int i = 0; i < cumWeights.length; i++) {
                if (cumWeights[i] > bucket) {
                    index = i;
                    break;
                }
            }
        }

        return textures[index];
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public boolean isValid() {
        return textures != null && textures.length > 0;
    }
}
