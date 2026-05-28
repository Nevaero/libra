package io.libra.core.persistence.resolution;

import io.libra.core.entities.Asset;

// SPI : resolves a flat AssetRef into a fully-loaded domain Asset.
//
// Dependency inversion : core *declares* this port ; the reference-data module *provides*
// the implementation (backed by repositories). Mappers and Money conversions depend only on
// this abstraction, so core never depends on the reference module — no cycle.
//
// Implementations are typically map-backed (pre-fetched per aggregate load) so resolving an
// entity tree costs one batch query, not one query per field.
public interface AssetResolver {

    Asset resolve(AssetRef ref);
}
