# Agent Harness MCP Connection

This guide connects an agentic harness running on a Mac host to the
Sapphire-Blu MCP server running inside the Android app.

This is a **local development** setup. Do not expose port `8788` to the LAN or
internet. The production remote-access design is not implemented yet.

## Connection topology

The Android Ktor server binds to the phone's loopback interface:

```text
Agent harness on Mac
  http://127.0.0.1:8788/mcp
              |
              | adb forward tcp:8788 tcp:8788
              v
Android app 127.0.0.1:8788/mcp
              |
              | UNIX socket + bearer token
              v
Rust CoS brain daemon
```

Use `adb forward`, not `adb reverse`:

```bash
adb -s <device-serial> forward tcp:8788 tcp:8788
```

`adb reverse` is the opposite direction. It attempts to bind a host port on
the phone and conflicts with the Ktor server already listening on phone port
`8788`.

## Prerequisites

- Android device with the Sapphire-Blu APK installed.
- USB ADB or Android Wireless Debugging connected.
- `adb` available on the host.
- The app package is `com.aistudio.autotask.svcqx`.
- The host has the agent harness configuration file.

Find the device serial:

```bash
adb devices
```

If more than one device is listed, always use `-s`:

```bash
SERIAL="1010018024018888"
adb -s "$SERIAL" devices
```

## Start the Android services

```bash
SERIAL="<device-serial>"

adb -s "$SERIAL" forward tcp:8788 tcp:8788

adb -s "$SERIAL" shell am start-foreground-service \
  -n com.aistudio.autotask.svcqx/com.example.service.AutoTaskService \
  -a com.example.autotask.action.START

adb -s "$SERIAL" shell am start-foreground-service \
  -n com.aistudio.autotask.svcqx/com.example.wa.BrainService \
  -a com.example.autotask.action.BRAIN_START
```

Confirm the forward:

```bash
adb -s "$SERIAL" forward --list
```

## Token ownership

`COS_MCP_TOKEN` is issued by Sapphire-Blu, not by Codex, OpenCode, Pass, or
the LLM provider.

`BrainService.getToken()` generates a `cos-<uuid>` token the first time it is
needed and stores it in the app's private `brain_config` preferences. The same
token authenticates the Android MCP endpoint and the app-to-brain UNIX socket.

Pass should store a copy of this token; it does not generate or rotate it.

### Debug-build token retrieval

For a debuggable APK, retrieve the preference file with:

```bash
adb -s "$SERIAL" shell run-as com.aistudio.autotask.svcqx \
  cat shared_prefs/brain_config.xml
```

Read the value of `brain_token`. Never commit the value, paste it into an
issue, or put it in this repository.

Store it in Pass, using an entry containing only the token:

```bash
pass insert autotask/sapphire-blu/brain-token
```

The release product needs a secure provisioning, rotation, and revocation
flow. `run-as` is a debug-development technique only.

## Load the token into the harness

For a terminal-launched harness:

```bash
export COS_MCP_TOKEN="$(pass show autotask/sapphire-blu/brain-token)"
```

The environment variable is inherited by processes started from that shell.
The agent receives MCP tools, not the token value itself.

For a GUI harness launched by macOS `launchd`:

```bash
launchctl setenv COS_MCP_TOKEN \
  "$(pass show autotask/sapphire-blu/brain-token)"
```

Fully quit and reopen the harness after setting the variable. Existing
processes do not receive changes to their environment.

Check or remove the user-session value:

```bash
launchctl getenv COS_MCP_TOKEN
launchctl unsetenv COS_MCP_TOKEN
```

The token is held in the environment of the launched process. Prefer a
short-lived launcher that reads from Pass rather than keeping the token in a
persistent user-session environment when practical.

## Codex configuration

In `/Users/<user>/.codex/config.toml`:

```toml
[mcp_servers.cos]
url = "http://127.0.0.1:8788/mcp"
bearer_token_env_var = "COS_MCP_TOKEN"
```

The Codex MCP client reads `COS_MCP_TOKEN` and sends:

```text
Authorization: Bearer <token>
```

## OpenCode configuration

In `~/.config/opencode/opencode.jsonc`:

```jsonc
{
  "$schema": "https://opencode.ai/config.json",
  "mcp": {
    "cos": {
      "type": "remote",
      "url": "http://127.0.0.1:8788/mcp",
      "headers": {
        "Authorization": "Bearer {env:COS_MCP_TOKEN}"
      },
      "enabled": true
    }
  }
}
```

Restart OpenCode after changing its configuration.

## MCP protocol requirements

The endpoint is stateless and supports `tools/list` and `tools/call`. Each
request must include:

- `Authorization: Bearer <COS_MCP_TOKEN>`
- `MCP-Protocol-Version: 2026-07-28`
- `Mcp-Method` matching the JSON-RPC method
- `Mcp-Name` matching `params.name` for `tools/call`
- `params._meta.io.modelcontextprotocol/clientCapabilities`

The server accepts these development origins when an Origin header is sent:

- `http://127.0.0.1:8788`
- `http://localhost:8788`

## Manual authenticated smoke test

```bash
curl -sS -X POST http://127.0.0.1:8788/mcp \
  -H "Authorization: Bearer $COS_MCP_TOKEN" \
  -H "Origin: http://127.0.0.1:8788" \
  -H "MCP-Protocol-Version: 2026-07-28" \
  -H "Mcp-Method: tools/list" \
  -H "Content-Type: application/json" \
  --data '{
    "jsonrpc": "2.0",
    "id": 1,
    "method": "tools/list",
    "params": {
      "_meta": {
        "io.modelcontextprotocol/protocolVersion": "2026-07-28",
        "io.modelcontextprotocol/clientCapabilities": {}
      }
    }
  }'
```

Success returns a JSON-RPC result containing the CoS tool registry. A `401`
means the HTTP bridge is reachable but the token is missing or incorrect.

## Harness handoff prompt

Give an agentic harness this prompt after the host tunnel and token are ready:

```text
Connect to the Sapphire-Blu MCP server.

Transport: Streamable HTTP, stateless
URL: http://127.0.0.1:8788/mcp
Authentication: Authorization: Bearer $COS_MCP_TOKEN
Protocol version: 2026-07-28
Required request metadata:
  params._meta.io.modelcontextprotocol/protocolVersion = 2026-07-28
  params._meta.io.modelcontextprotocol/clientCapabilities = {}
Required mirrored headers:
  MCP-Protocol-Version = 2026-07-28
  Mcp-Method = the JSON-RPC method
  Mcp-Name = params.name for tools/call

First call tools/list. Do not call side-effectful tools until you have
described the intended action and received confirmation. Treat SMS, calls,
WhatsApp sends, UI driving, navigation, and settings changes as high risk.
If the server is unreachable, verify that adb forward tcp:8788 tcp:8788 is
active on the host and that AutoTaskService and BrainService are running on
the device. Never expose port 8788 directly to the network.
```

## Troubleshooting

### `adb: more than one device/emulator`

Use the device serial on every command:

```bash
adb -s <device-serial> forward tcp:8788 tcp:8788
```

### `401 Unauthorized`

The forward works. Reload the token from Pass and export it in the same
environment that launches the harness.

### Connection refused

Check the forward and services:

```bash
adb -s "$SERIAL" forward --list
adb -s "$SERIAL" shell dumpsys activity services \
  com.aistudio.autotask.svcqx | grep -E 'AutoTaskService|BrainService'
```

Then restart both services and recreate the forward if needed.

### `run-as: package not debuggable`

The installed APK is a release build. The debug token retrieval method is not
available. Use the product's future secure token-provisioning flow; do not
extract private release storage through workarounds.

### Wireless ADB

On Android 11+, enable **Developer options → Wireless debugging**, pair from
the host, then connect:

```bash
adb pair <phone-ip>:<pairing-port>
adb connect <phone-ip>:<adb-port>
adb devices
```

After the wireless ADB connection is active, `adb forward` works the same as
with USB. The phone and Mac need network reachability, but the MCP client
still uses `127.0.0.1:8788` on the Mac.
