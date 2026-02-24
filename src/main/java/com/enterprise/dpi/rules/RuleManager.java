package com.enterprise.dpi.rules;

import java.util.HashSet;
import java.util.Set;

/**
 * Manages blocked domains, IPs, or apps.
 * Thread-safe for reads. FastPath workers read from this.
 */
public class RuleManager {

    private final Set<String> blockedDomains = new HashSet<>();

    public RuleManager() {
        // Load some sample rules (in a real app, read from config/DB)
        blockedDomains.add("www.facebook.com");
        blockedDomains.add("www.youtube.com");
        blockedDomains.add("tiktok.com");
    }

    /**
     * Checks if an extracted SNI matches a blocked domain pattern.
     */
    public boolean isBlocked(String sni) {
        if (sni == null || sni.isEmpty()) {
            return false;
        }

        // Exact match
        if (blockedDomains.contains(sni)) {
            return true;
        }

        // Suffix match (e.g. *.youtube.com)
        for (String blocked : blockedDomains) {
            if (sni.endsWith("." + blocked) || sni.equals(blocked)) {
                return true;
            }
        }

        return false;
    }
}
