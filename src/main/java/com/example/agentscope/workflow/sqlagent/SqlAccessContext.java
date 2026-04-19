package com.example.agentscope.workflow.sqlagent;

import java.util.Locale;

/**
 * Request-scoped access context for SQL chat.
 */
public record SqlAccessContext(
        String userId,
        String tenantId,
        String role) {

    private static final String ADMIN_ROLE = "ADMIN";

    public static SqlAccessContext tenantUser(String userId, String tenantId) {
        return new SqlAccessContext(userId, tenantId, "USER");
    }

    public static SqlAccessContext admin(String userId) {
        return new SqlAccessContext(userId, null, ADMIN_ROLE);
    }

    public static SqlAccessContext fromHeaders(String userId, String tenantId, String role) {
        String normalizedUserId = isBlank(userId) ? "demo-user" : userId.trim();
        String normalizedRole = isBlank(role) ? "USER" : role.trim().toUpperCase(Locale.ROOT);
        if (ADMIN_ROLE.equals(normalizedRole)) {
            return admin(normalizedUserId);
        }
        if (isBlank(tenantId)) {
            throw new IllegalArgumentException("Tenant header is required for non-admin users.");
        }
        return new SqlAccessContext(normalizedUserId, tenantId.trim(), normalizedRole);
    }

    public boolean isAdmin() {
        return ADMIN_ROLE.equalsIgnoreCase(role);
    }

    public boolean requiresTenantIsolation() {
        return !isAdmin();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
