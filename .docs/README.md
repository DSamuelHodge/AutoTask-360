# AutoTask-360 / CoS — Technical Documentation

This is the technical reference for the AutoTask-360 Android app and the CoS
(Calendar + CRM) Rust brain it hosts. It documents the **actual codebase**:
file structure, HTTP API, MCP server, daemon RPC surface, capability model,
and the build/deploy loop. It is not a product overview.

The stack is two repositories, built as one system:

| Repo | Role |
|---|---|
| `AutoTask-360/` | Android app (Kotlin). Foreground engine, Ktor loopback server, MCP endpoint, accessibility eyes/hands, OTA self-update, WhatsApp bridge. Bundles the daemon as `libcosd.so`. |
| `agent-cal-crm/` | Rust daemon ("the brain"). Calendar + CRM in libSQL, `aware.*` RPC handlers, `cos serve`/`cos seed` binaries. Cross-compiled to `aarch64-linux-android`. |

## Docs index

- [Architecture](architecture.md) — processes, IPC, trust boundaries, storage
- [File structure](file-structure.md) — every source file in both repos
- [HTTP API](http-api.md) — all Ktor `/v1/*` + `/mcp` endpoints
- [MCP server](mcp.md) — protocol (2026-07-28) + tool registry
- [Daemon RPC](daemon-rpc.md) — `aware.*`, `crm.*`, `cal.*` methods
- [Capabilities & permissions](capabilities.md) — permission model, special access, policy guard
- [Build & deploy](build-deploy.md) — cross-compile, APK build, OTA, adb loop
