# SQL Chat Tenant Isolation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a SQL chat page that queries through `SqlAgentController` while enforcing tenant isolation for normal users and bypassing isolation for admins.

**Architecture:** Read demo identity from request headers, map it into a small tenant/user access context, pass that context into SQL generation and execution, and enforce row-level tenant filtering at execution time. Expose a simple static page that shows current identity and lets users chat without manually entering tenant parameters.

**Tech Stack:** Spring Boot, Spring MVC, JdbcTemplate, static HTML/CSS/JavaScript, JUnit 5, Mockito

---

### Task 1: Define access-context and controller tests

**Files:**
- Create: `src/test/java/com/example/agentscope/controller/SqlAgentControllerTest.java`
- Create: `src/main/java/com/example/agentscope/workflow/sqlagent/SqlAccessContext.java`
- Modify: `src/main/java/com/example/agentscope/controller/SqlAgentController.java`

- [ ] **Step 1: Write the failing test**
- [ ] **Step 2: Run test to verify it fails**
- [ ] **Step 3: Add minimal access-context and header parsing**
- [ ] **Step 4: Run test to verify it passes**

### Task 2: Add tenant-aware service/tool tests

**Files:**
- Modify: `src/test/java/com/example/agentscope/workflow/sqlagent/SqlAgentServiceTest.java`
- Create: `src/test/java/com/example/agentscope/workflow/sqlagent/tools/SqlTenantIsolationTest.java`
- Modify: `src/main/java/com/example/agentscope/workflow/sqlagent/SqlAgentService.java`
- Modify: `src/main/java/com/example/agentscope/workflow/sqlagent/tools/SqlTools.java`

- [ ] **Step 1: Write failing tests for normal-user isolation and admin bypass**
- [ ] **Step 2: Run tests to verify they fail**
- [ ] **Step 3: Implement minimal context-aware query execution and tenant enforcement**
- [ ] **Step 4: Run tests to verify they pass**

### Task 3: Add SQL chat page

**Files:**
- Create: `src/main/resources/static/sql-chat.html`

- [ ] **Step 1: Add the static page with identity card and chat UI**
- [ ] **Step 2: Wire the page to `/api/sql/query` using demo identity headers**
- [ ] **Step 3: Manually verify the page structure and request payloads**

### Task 4: Verify end-to-end behavior

**Files:**
- Modify: `src/main/java/com/example/agentscope/controller/SqlAgentController.java`
- Modify: `src/main/java/com/example/agentscope/workflow/sqlagent/SqlAgentService.java`
- Modify: `src/main/java/com/example/agentscope/workflow/sqlagent/tools/SqlTools.java`
- Modify: `src/main/resources/static/sql-chat.html`

- [ ] **Step 1: Run focused Maven tests**
- [ ] **Step 2: Fix any failures with minimal edits**
- [ ] **Step 3: Summarize resulting behavior and residual limits**
