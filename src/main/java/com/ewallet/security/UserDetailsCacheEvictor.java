package com.ewallet.security;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Drops a user's cached {@link UserDetailsImpl} (which carries the password hash) when their
 * credentials change. Without this, login keeps checking the old hash until the cache entry expires.
 *
 * <p>Eviction waits for the commit: evicting first would let a login that lands before the commit
 * re-cache the old hash.
 */
@Component
@RequiredArgsConstructor
public class UserDetailsCacheEvictor {

    static final String CACHE = "userDetails";

    private final CacheManager cacheManager;

    public void evictAfterCommit(String username) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    evict(username);
                }
            });
        } else {
            evict(username);
        }
    }

    private void evict(String username) {
        Cache cache = cacheManager.getCache(CACHE);
        if (cache != null && username != null) {
            cache.evict(username);
        }
    }
}
