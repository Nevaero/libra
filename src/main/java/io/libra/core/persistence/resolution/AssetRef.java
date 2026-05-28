package io.libra.core.persistence.resolution;

// Flat, value-based reference to an Asset (Currency | Security) as it is stored in
// persistence : (assetType, assetCode, assetMic). Serves as the lookup key for batch
// resolution — its record equality/hashCode make it a safe Map key.
public record AssetRef(String type, String code, String mic) {

    public static final String CURRENCY = "CURRENCY";
    public static final String SECURITY = "SECURITY";
}
