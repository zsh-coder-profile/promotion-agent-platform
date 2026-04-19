package com.example.agentscope.workflow.sqlagent.tools;

import com.example.agentscope.workflow.sqlagent.SqlAccessContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SqlTenantIsolationTest {

    @Test
    void applyTenantIsolationAddsTenantFilterForNormalUsers() {
        SqlTools.ScopedQuery scopedQuery = SqlTools.applyTenantIsolation(
                "SELECT id, amount FROM orders ORDER BY created_at DESC",
                SqlAccessContext.tenantUser("user-a", "tenant-a"));

        assertEquals(
                "SELECT id, amount FROM orders WHERE orders.tenant_id = ? ORDER BY created_at DESC",
                scopedQuery.sql());
        assertArrayEquals(new Object[]{"tenant-a"}, scopedQuery.args());
    }

    @Test
    void applyTenantIsolationSkipsTenantFilterForAdmins() {
        SqlTools.ScopedQuery scopedQuery = SqlTools.applyTenantIsolation(
                "SELECT id, amount FROM orders",
                SqlAccessContext.admin("admin-user"));

        assertEquals("SELECT id, amount FROM orders", scopedQuery.sql());
        assertArrayEquals(new Object[0], scopedQuery.args());
    }

    @Test
    void applyTenantIsolationDoesNotAppendTenantFilterWhenSqlAlreadyScoped() {
        String sql = """
                SELECT
                    u.id AS user_id,
                    u.username,
                    u.phone,
                    uc.id AS user_coupon_id,
                    uc.acquired_at,
                    uc.expires_at
                FROM user_coupons uc
                         JOIN coupons c
                              ON c.id = uc.coupon_id
                                  AND c.tenant_id = 'tenant-a'
                         JOIN users u
                              ON u.id = uc.user_id
                                  AND u.tenant_id = 'tenant-a'
                WHERE uc.tenant_id = 'tenant-a'
                  AND c.coupon_name = '满100减15代金券'
                  AND uc.status = 'UNUSED'
                  AND NOT EXISTS (
                    SELECT 1
                    FROM orders o
                    WHERE o.tenant_id = 'tenant-a'
                      AND o.user_coupon_id = uc.id
                )
                ORDER BY uc.acquired_at DESC
                LIMIT 5
                """.trim();

        SqlTools.ScopedQuery scopedQuery = SqlTools.applyTenantIsolation(
                sql,
                SqlAccessContext.tenantUser("user-a", "tenant-a"));

        assertEquals(sql, scopedQuery.sql());
        assertArrayEquals(new Object[0], scopedQuery.args());
    }

    @Test
    void applyTenantIsolationRejectsMismatchedTenantLiteralInSql() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () ->
                SqlTools.applyTenantIsolation(
                        "SELECT id FROM orders WHERE orders.tenant_id = 'tenant-b'",
                        SqlAccessContext.tenantUser("user-a", "tenant-a")));

        assertEquals("SQL tenant scope does not match current user tenant.", error.getMessage());
    }
}
