package net.nextfur.fwb.init;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Defines all 36 non-vanilla building colors across 6 gradient palette families
 * and 2 texture varieties per color (72 textured sets × 4 shapes = 288 blocks).
 */
public final class FwPaletteDefinitions {

    private FwPaletteDefinitions() {}

    public enum PaletteFamily {
        BRUTALIST("brutalist", "Weathered Brutalist", "Brutalista Desgastado", false, TextureVariety.STONE, TextureVariety.BRICKS),
        HEARTH("hearth", "Upsetti Hearth", "Lareira Vibrante", false, TextureVariety.STUCCO, TextureVariety.TILES),
        VERDANT("verdant", "Verdant Sage", "Sálvia Verdejante", false, TextureVariety.COBBLE, TextureVariety.BRICKS),
        MARITIME("maritime", "Maritime Verdigris", "Azinhavre Marítimo", false, TextureVariety.PLATE, TextureVariety.SHINGLES),
        TWILIGHT("twilight", "Twilight Velvet", "Veludo Crepuscular", false, TextureVariety.MARBLE, TextureVariety.FLUTED),
        TIMBER("timber", "Rustic Timber", "Madeira Rústica", true, TextureVariety.PLANKS, TextureVariety.FRAMED);

        private final String id;
        private final String enName;
        private final String ptName;
        private final boolean isWood;
        private final TextureVariety varietyA;
        private final TextureVariety varietyB;

        PaletteFamily(
                String id,
                String enName,
                String ptName,
                boolean isWood,
                TextureVariety varietyA,
                TextureVariety varietyB
        ) {
            this.id = id;
            this.enName = enName;
            this.ptName = ptName;
            this.isWood = isWood;
            this.varietyA = varietyA;
            this.varietyB = varietyB;
        }

        public String id() { return id; }
        public String enName() { return enName; }
        public String ptName() { return ptName; }
        public boolean isWood() { return isWood; }
        public TextureVariety varietyA() { return varietyA; }
        public TextureVariety varietyB() { return varietyB; }
    }

    public enum TextureVariety {
        STONE("stone", "Weathered Stone", "Pedra Desgastada"),
        BRICKS("bricks", "Bricks", "Tijolos"),
        STUCCO("stucco", "Stucco", "Estuque"),
        TILES("tiles", "Carved Tiles", "Ladrilhos Entalhados"),
        COBBLE("cobble", "Cobble", "Pedregulho"),
        PLATE("plate", "Riveted Plate", "Chapa Rebitada"),
        SHINGLES("shingles", "Shingles", "Telhas"),
        MARBLE("marble", "Velvet Marble", "Mármore Aveludado"),
        FLUTED("fluted", "Fluted Masonry", "Alvenaria Canelada"),
        PLANKS("planks", "Planks", "Tábuas"),
        FRAMED("framed", "Framed Timber", "Madeira Moldurada");

        private final String id;
        private final String enSuffix;
        private final String ptPrefix;

        TextureVariety(String id, String enSuffix, String ptPrefix) {
            this.id = id;
            this.enSuffix = enSuffix;
            this.ptPrefix = ptPrefix;
        }

        public String id() { return id; }
        public String enSuffix() { return enSuffix; }
        public String ptPrefix() { return ptPrefix; }
    }

    public record ColorSpec(
            String id,
            int rgb,
            String enName,
            String ptName,
            PaletteFamily family,
            int gradientStep
    ) {
        public String hexCode() {
            return String.format("#%06X", rgb & 0xFFFFFF);
        }
    }

    public record BlockSetSpec(
            String id,
            ColorSpec color,
            TextureVariety variety,
            TextureVariety pairVariety
    ) {
        public PaletteFamily family() {
            return color.family();
        }
    }

    public static final List<ColorSpec> COLORS;
    public static final List<BlockSetSpec> BLOCK_SETS;

    static {
        List<ColorSpec> colors = new ArrayList<>(36);

        // Family 1: Weathered Brutalist
        colors.add(new ColorSpec("abyssal_slate", 0x1F2229, "Abyssal Slate", "Ardósia Abissal", PaletteFamily.BRUTALIST, 1));
        colors.add(new ColorSpec("charcoal_basalt", 0x2F333D, "Charcoal Basalt", "Basalto Carvão", PaletteFamily.BRUTALIST, 2));
        colors.add(new ColorSpec("ironwood_slate", 0x444A57, "Ironwood Slate", "Ardósia Férrea", PaletteFamily.BRUTALIST, 3));
        colors.add(new ColorSpec("brutalist_ash", 0x636870, "Brutalist Ash", "Cinza Brutalista", PaletteFamily.BRUTALIST, 4));
        colors.add(new ColorSpec("pebble_greige", 0x8C8984, "Pebble Greige", "Greige Seixo", PaletteFamily.BRUTALIST, 5));
        colors.add(new ColorSpec("chalk_limestone", 0xBEB9AE, "Chalk Limestone", "Calcário Giz", PaletteFamily.BRUTALIST, 6));

        // Family 2: Upsetti Hearth
        colors.add(new ColorSpec("oxblood_maroon", 0x42161B, "Oxblood Maroon", "Bordô Sangue", PaletteFamily.HEARTH, 1));
        colors.add(new ColorSpec("crimson_ember", 0x6E1E22, "Crimson Ember", "Brasa Carmesim", PaletteFamily.HEARTH, 2));
        colors.add(new ColorSpec("cinnabar_rust", 0x9E2F26, "Cinnabar Rust", "Ferrugem Cinábrio", PaletteFamily.HEARTH, 3));
        colors.add(new ColorSpec("burnt_persimmon", 0xCC5828, "Burnt Persimmon", "Caqui Queimado", PaletteFamily.HEARTH, 4));
        colors.add(new ColorSpec("marigold_amber", 0xE3882D, "Marigold Amber", "Âmbar Calêndula", PaletteFamily.HEARTH, 5));
        colors.add(new ColorSpec("saffron_ochre", 0xF2B938, "Saffron Ochre", "Ocre Açafrão", PaletteFamily.HEARTH, 6));

        // Family 3: Verdant Sage
        colors.add(new ColorSpec("shadow_pine", 0x1D3024, "Shadow Pine", "Pinheiro Sombrio", PaletteFamily.VERDANT, 1));
        colors.add(new ColorSpec("deep_lichen", 0x2E4733, "Deep Lichen", "Líquen Profundo", PaletteFamily.VERDANT, 2));
        colors.add(new ColorSpec("olive_cypress", 0x486342, "Olive Cypress", "Cipreste Oliva", PaletteFamily.VERDANT, 3));
        colors.add(new ColorSpec("eucalyptus_fern", 0x65825C, "Eucalyptus Fern", "Samambaia Eucalipto", PaletteFamily.VERDANT, 4));
        colors.add(new ColorSpec("misty_sage", 0x8AA37E, "Misty Sage", "Sálvia Nebulosa", PaletteFamily.VERDANT, 5));
        colors.add(new ColorSpec("celadon_mint", 0xB4C9A9, "Celadon Mint", "Menta Celadon", PaletteFamily.VERDANT, 6));

        // Family 4: Maritime Verdigris
        colors.add(new ColorSpec("trench_navy", 0x182438, "Trench Navy", "Marinho Abissal", PaletteFamily.MARITIME, 1));
        colors.add(new ColorSpec("storm_indigo", 0x263B59, "Storm Indigo", "Índigo Tempestade", PaletteFamily.MARITIME, 2));
        colors.add(new ColorSpec("aegean_teal", 0x2F5C6E, "Aegean Teal", "Azul-Petróleo Egeu", PaletteFamily.MARITIME, 3));
        colors.add(new ColorSpec("patina_verdigris", 0x438285, "Patina Verdigris", "Azinhavre Pátina", PaletteFamily.MARITIME, 4));
        colors.add(new ColorSpec("seafoam_aqua", 0x6BA8A4, "Seafoam Aqua", "Espuma do Mar", PaletteFamily.MARITIME, 5));
        colors.add(new ColorSpec("glacial_mist", 0xA3CFCB, "Glacial Mist", "Névoa Glacial", PaletteFamily.MARITIME, 6));

        // Family 5: Twilight Velvet
        colors.add(new ColorSpec("eldritch_aubergine", 0x28182E, "Eldritch Aubergine", "Berinjela Arcana", PaletteFamily.TWILIGHT, 1));
        colors.add(new ColorSpec("imperial_plum", 0x44244A, "Imperial Plum", "Ameixa Imperial", PaletteFamily.TWILIGHT, 2));
        colors.add(new ColorSpec("mulberry_wine", 0x683562, "Mulberry Wine", "Vinho Amora", PaletteFamily.TWILIGHT, 3));
        colors.add(new ColorSpec("dusty_mauve", 0x8E537B, "Dusty Mauve", "Malva Empoado", PaletteFamily.TWILIGHT, 4));
        colors.add(new ColorSpec("heather_blush", 0xB57B9D, "Heather Blush", "Urze Rosado", PaletteFamily.TWILIGHT, 5));
        colors.add(new ColorSpec("wisteria_lilac", 0xD8B2C8, "Wisteria Lilac", "Lilás Glicínia", PaletteFamily.TWILIGHT, 6));

        // Family 6: Rustic Timber
        colors.add(new ColorSpec("umbral_espresso", 0x291D18, "Umbral Espresso", "Expresso Umbral", PaletteFamily.TIMBER, 1));
        colors.add(new ColorSpec("roasted_walnut", 0x473126, "Roasted Walnut", "Nogueira Torrada", PaletteFamily.TIMBER, 2));
        colors.add(new ColorSpec("cinnamon_cedar", 0x6E4936, "Cinnamon Cedar", "Cedro Canela", PaletteFamily.TIMBER, 3));
        colors.add(new ColorSpec("honey_ochrewood", 0x996B4C, "Honey Ochrewood", "Madeira Mel", PaletteFamily.TIMBER, 4));
        colors.add(new ColorSpec("driftwood_taupe", 0xBBA086, "Driftwood Taupe", "Madeira Flutuante", PaletteFamily.TIMBER, 5));
        colors.add(new ColorSpec("bleached_linen", 0xDFCFB9, "Bleached Linen", "Linho Alvejado", PaletteFamily.TIMBER, 6));

        COLORS = Collections.unmodifiableList(colors);

        List<BlockSetSpec> sets = new ArrayList<>(72);
        for (ColorSpec color : COLORS) {
            TextureVariety a = color.family().varietyA();
            TextureVariety b = color.family().varietyB();
            sets.add(new BlockSetSpec(color.id() + "_" + a.id(), color, a, b));
            sets.add(new BlockSetSpec(color.id() + "_" + b.id(), color, b, a));
        }
        BLOCK_SETS = Collections.unmodifiableList(sets);
    }
}
