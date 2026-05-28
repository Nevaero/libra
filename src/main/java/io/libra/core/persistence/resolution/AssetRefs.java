package io.libra.core.persistence.resolution;

import io.libra.core.entities.Asset;
import io.libra.core.entities.Currency;
import io.libra.core.entities.Security;

// Pure write-side flattening of an Asset into its persistence triple (type, code, mic).
// No IO — purely a pattern match on the sealed hierarchy. The inverse direction (resolution)
// needs a repository and lives behind AssetResolver.
public final class AssetRefs {

    private AssetRefs() {
    }

    public static String typeOf(Asset asset) {
        if (asset == null) {
            return null;
        }
        return switch (asset) {
            case Currency c -> AssetRef.CURRENCY;
            case Security s -> AssetRef.SECURITY;
        };
    }

    public static String codeOf(Asset asset) {
        return asset == null ? null : asset.code();
    }

    public static String micOf(Asset asset) {
        if (asset == null) {
            return null;
        }
        return switch (asset) {
            case Currency c -> null;
            case Security s -> s.mic();
        };
    }

    public static AssetRef of(Asset asset) {
        return new AssetRef(typeOf(asset), codeOf(asset), micOf(asset));
    }
}
