package com.schooldesk.docqa.tenancy;

public final class TenantContext {

    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void set(String tenantId) {
        CURRENT.set(tenantId);
    }

    public static String require() {
        String tenantId = CURRENT.get();
        if (tenantId == null) {
            throw new IllegalStateException(
                    "No tenant bound to the current thread. Either the request bypassed "
                            + "TenantFilter, or work was handed to an executor without propagating it.");
        }
        return tenantId;
    }

    public static void clear() {
        CURRENT.remove();
    }
}
