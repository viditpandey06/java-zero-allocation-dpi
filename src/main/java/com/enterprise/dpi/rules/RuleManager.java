package com.enterprise.dpi.rules;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Manages blocked domains, IPs, or apps.
 * Thread-safe for reads. FastPath workers read from this.
 */
public class RuleManager {

    private final Set<String> blockedDomains = new HashSet<>();

    public RuleManager(Collection<String> domains) {
        if (domains != null) {
            blockedDomains.addAll(domains);
        }
    }

    /**
     * Checks if an extracted SNI matches a blocked domain pattern.
     */
    public boolean isBlocked(String sni) {
        if (sni == null || sni.isEmpty()) {
            return false;
        }
        if (blockedDomains.contains(sni)) {
            return true;
        }
        for (String blocked : blockedDomains) {
            if (sni.endsWith("." + blocked) || sni.equals(blocked)) {
                return true;
            }
        }
        return false;
    }
}
