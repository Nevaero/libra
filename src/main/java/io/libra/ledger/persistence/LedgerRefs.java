package io.libra.ledger.persistence;

import io.libra.ledger.persistence.entity.AccountEntity;
import io.libra.ledger.persistence.entity.BalanceEntity;
import io.libra.ledger.persistence.entity.JournalEntryEntity;
import io.libra.ledger.persistence.entity.PostingEntity;
import io.libra.core.persistence.entity.MoneyEntity;
import io.libra.core.persistence.resolution.AssetRef;

import java.util.LinkedHashSet;
import java.util.Set;

// Collects the flat AssetRefs an entity (tree) holds so the read path can pre-populate a
// resolver with a single batch query, then thread it through the MapStruct mappers.
public final class LedgerRefs {

    private LedgerRefs() {
    }

    public static AssetRef of(AccountEntity account) {
        return new AssetRef(account.getAssetType(), account.getAssetCode(), account.getAssetMic());
    }

    public static AssetRef of(BalanceEntity balance) {
        return new AssetRef(balance.getAssetType(), balance.getAssetCode(), balance.getAssetMic());
    }

    public static AssetRef of(MoneyEntity money) {
        return new AssetRef(money.getAssetType(), money.getAssetCode(), money.getAssetMic());
    }

    public static Set<AssetRef> of(JournalEntryEntity entry) {
        Set<AssetRef> refs = new LinkedHashSet<>();
        for (PostingEntity posting : entry.getPostings()) {
            refs.add(of(posting.getAmount()));
            refs.add(of(posting.getBalanceAfter()));
        }
        return refs;
    }
}
