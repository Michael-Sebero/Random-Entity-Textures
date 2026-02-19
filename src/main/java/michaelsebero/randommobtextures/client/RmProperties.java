package michaelsebero.randommobtextures.client;

import michaelsebero.randommobtextures.RandomMobsMod;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.biome.Biome;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Holds the compiled texture-variant data for a single mob texture path.
 * Created once (lazily) and cached by {@link RandomMobsClient}.
 *
 * Two modes:
 *   Rule mode      - parsed from a .properties file; supports biome /
 *                    height filtering and weighted selection.
 *   Variant list   - auto-detected numbered textures with no extra constraints.
 */
public final class RmProperties {

    // ── Fields ────────────────────────────────────────────────────────────────

    /** Non-null only in variant-list mode. */
    private final ResourceLocation[] variants;

    /** Non-null only in rule mode. */
    private final RmRule[] rules;

    // ── Constructors ──────────────────────────────────────────────────────────

    /** Variant-list mode: no .properties file, just numbered textures. */
    RmProperties(ResourceLocation[] variants) {
        this.variants = variants;
        this.rules    = null;
    }

    /** Rule mode: parses the given {@link Properties} object. */
    RmProperties(Properties props, ResourceLocation baseTexture) {
        this.variants = null;
        this.rules    = parseRules(props, baseTexture);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public boolean isValid() {
        if (rules != null) {
            for (RmRule r : rules) if (!r.isValid()) return false;
            return rules.length > 0;
        }
        return variants != null && variants.length > 0;
    }

    public ResourceLocation getTexture(ResourceLocation original, TrackedEntity entity) {
        if (rules != null) {
            for (RmRule rule : rules) {
                if (rule.matches(entity)) {
                    ResourceLocation result = rule.getTexture(entity);
                    if (result != null) return result;
                }
            }
        }
        if (variants != null && variants.length > 0) {
            return variants[Math.abs(entity.mcp$randomId()) % variants.length];
        }
        return original;
    }

    // ── Rule parsing ──────────────────────────────────────────────────────────

    private static RmRule[] parseRules(Properties props, ResourceLocation baseTexture) {
        List<RmRule> list = new ArrayList<>();

        int propCount = props.size();
        for (int n = 1; n <= propCount + 1; n++) {
            String skinsVal = props.getProperty("skins." + n);
            if (skinsVal == null) continue;

            int[] skinIndices = parseIntList(skinsVal);
            if (skinIndices == null || skinIndices.length == 0) continue;

            int[]   rawWeights  = parseIntList(props.getProperty("weights." + n));
            Biome[] biomes      = parseBiomes(props.getProperty("biomes." + n));
            int[]   heightRange = parseHeightRange(
                                      props.getProperty("heights." + n),
                                      props.getProperty("minHeight." + n),
                                      props.getProperty("maxHeight." + n));

            ResourceLocation[] textures = buildTextureArray(baseTexture, skinIndices);
            if (textures == null || textures.length == 0) continue;

            int[] cumWeights = null;
            int   totalWeight = 1;
            if (rawWeights != null && rawWeights.length > 0) {
                int[] w = alignArray(rawWeights, textures.length, average(rawWeights));
                cumWeights  = buildCumulative(w);
                totalWeight = cumWeights[cumWeights.length - 1];
                if (totalWeight <= 0) { cumWeights = null; totalWeight = 1; }
            }

            int minH = (heightRange != null) ? heightRange[0] : Integer.MIN_VALUE;
            int maxH = (heightRange != null) ? heightRange[1] : Integer.MAX_VALUE;

            list.add(new RmRule(textures, cumWeights, totalWeight, biomes, minH, maxH));
        }

        return list.toArray(new RmRule[0]);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static ResourceLocation[] buildTextureArray(ResourceLocation baseTexture, int[] skinIndices) {
        ResourceLocation mcpBase = RandomMobsClient.toMcpatcherLocation(baseTexture);

        List<ResourceLocation> out = new ArrayList<>();
        for (int idx : skinIndices) {
            ResourceLocation loc;
            if (idx <= 1) {
                loc = baseTexture;
            } else {
                if (mcpBase == null) { loc = baseTexture; }
                else                 { loc = RandomMobsClient.getIndexedLocation(mcpBase, idx); }
            }
            if (loc == null) continue;

            if (idx > 1 && !RandomMobsClient.hasResource(loc)) {
                RandomMobsMod.LOG.warn("RandomMobs: texture not found: {}", loc.getResourcePath());
                continue;
            }
            out.add(loc);
        }
        return out.toArray(new ResourceLocation[0]);
    }

    // ── Parsing utilities ─────────────────────────────────────────────────────

    static int[] parseIntList(String s) {
        if (s == null || s.trim().isEmpty()) return null;
        List<Integer> result = new ArrayList<>();
        for (String token : s.trim().split("[\\s,]+")) {
            if (token.isEmpty()) continue;
            if (token.contains("-")) {
                String[] parts = token.split("-", 2);
                try {
                    int lo = Integer.parseInt(parts[0].trim());
                    int hi = Integer.parseInt(parts[1].trim());
                    for (int i = lo; i <= hi; i++) result.add(i);
                } catch (NumberFormatException ignored) {}
            } else {
                try { result.add(Integer.parseInt(token.trim())); }
                catch (NumberFormatException ignored) {}
            }
        }
        if (result.isEmpty()) return null;
        int[] arr = new int[result.size()];
        for (int i = 0; i < arr.length; i++) arr[i] = result.get(i);
        return arr;
    }

    static Biome[] parseBiomes(String s) {
        if (s == null || s.trim().isEmpty()) return null;
        List<Biome> biomes = new ArrayList<>();
        for (String name : s.trim().split("[\\s,]+")) {
            if (name.isEmpty()) continue;
            boolean found = false;
            for (Biome b : Biome.REGISTRY) {
                if (b == null) continue;
                ResourceLocation rn = b.getRegistryName();
                if ((rn != null && rn.getResourcePath().equalsIgnoreCase(name))
                        || b.getBiomeName().equalsIgnoreCase(name)) {
                    biomes.add(b);
                    found = true;
                }
            }
            if (!found) {
                RandomMobsMod.LOG.warn("RandomMobs: unknown biome name '{}' in properties file", name);
            }
        }
        return biomes.isEmpty() ? null : biomes.toArray(new Biome[0]);
    }

    static int[] parseHeightRange(String heights, String minH, String maxH) {
        if (heights != null && !heights.trim().isEmpty()) {
            String t = heights.trim();
            if (t.contains("-")) {
                String[] parts = t.split("-", 2);
                try {
                    return new int[]{
                        Integer.parseInt(parts[0].trim()),
                        Integer.parseInt(parts[1].trim())
                    };
                } catch (NumberFormatException ignored) {}
            }
        }
        if (minH != null || maxH != null) {
            int lo = 0, hi = 255;
            try { if (minH != null) lo = Integer.parseInt(minH.trim()); } catch (NumberFormatException ignored) {}
            try { if (maxH != null) hi = Integer.parseInt(maxH.trim()); } catch (NumberFormatException ignored) {}
            return new int[]{lo, hi};
        }
        return null;
    }

    private static int[] alignArray(int[] src, int targetLen, int fillValue) {
        if (src.length == targetLen) return src;
        int[] out = new int[targetLen];
        System.arraycopy(src, 0, out, 0, Math.min(src.length, targetLen));
        for (int i = src.length; i < targetLen; i++) out[i] = fillValue;
        return out;
    }

    private static int[] buildCumulative(int[] weights) {
        int[] cum = new int[weights.length];
        int sum = 0;
        for (int i = 0; i < weights.length; i++) {
            sum += Math.max(0, weights[i]);
            cum[i] = sum;
        }
        return cum;
    }

    private static int average(int[] arr) {
        if (arr.length == 0) return 1;
        long sum = 0;
        for (int v : arr) sum += v;
        return (int) (sum / arr.length);
    }
}
