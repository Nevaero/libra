package io.libra.core.persistence.resolution;

import java.util.Collection;

// Batch resolution factory : given the full set of refs an aggregate needs, returns a
// pre-populated resolver. One query (per asset class) instead of one per field — the N+1 is
// eliminated by construction, independently of any @Cacheable proxy.
//
// Usage : a consumer collects every AssetRef / InstrumentRef of the entity tree it is about
// to map, calls the matching factory once, then threads the returned resolver through its
// MapStruct mappers as a @Context.
public interface ReferenceResolution {

    AssetResolver assetResolverFor(Collection<AssetRef> refs);

    InstrumentResolver instrumentResolverFor(Collection<InstrumentRef> refs);
}
