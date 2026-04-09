package net.joseph.vaultfilters.access;

public interface FilterMenuAdvancedAccessor {
    boolean vault_filters$getMatchAll();
    void vault_filters$setMatchAll(boolean matchAll);
    boolean vault_filters$isBlacklist();
    void vault_filters$setBlacklist(boolean blacklist);
    boolean vault_filters$shouldRespectNBT();
    void vault_filters$setRespectNBT(boolean respectNBT);
}
