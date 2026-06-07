# HAProxyCompat

**Stop seeing `127.0.0.1`. See who's actually connecting.**

When your Minecraft server sits behind a reverse proxy like FRP, nginx, HAProxy, or Traefik, every
player looks like they're connecting from the proxy. Bans, logs, and IP tools all see the proxy's
address instead of the real one. HAProxyCompat fixes that. It reads the PROXY protocol header
(v1 and v2) that your proxy sends, pulls out the real client IP, and hands it to Minecraft as if the
player connected directly.

The result: real IPs in your logs, working IP bans, and accurate geolocation. No client mod, no
extra setup for players.

> **Heads up:** this is often not optional. Once your proxy starts sending PROXY headers, a vanilla
> server has no idea what those extra bytes are and chokes on them, so players can't connect and the
> server just looks offline in the client. Installing HAProxyCompat is what makes the connection work
> at all. The real-IP part is the bonus.

- **For:** NeoForge and Fabric dedicated servers. Pick the jar that matches your loader and MC version.
- **Needs:** Nothing else. The one Netty piece Minecraft doesn't ship is tucked inside the jar for
  you, so it stays a single drop-in file.

## Pick the right jar

| File | Loader | MC versions |
|---|---|---|
| `haproxycompat-neoforge-1.21-1.0.0.jar` | NeoForge | 1.21 – 1.21.11 |
| `haproxycompat-neoforge-26.1-1.0.0.jar` | NeoForge | 26.1.x |
| `haproxycompat-fabric-1.21-1.0.0.jar` | Fabric | 1.21 – 1.21.11 |
| `haproxycompat-fabric-26.1-1.0.0.jar` | Fabric | 26.1.x |

## Quick start

1. Drop the right jar into your server's `mods/` folder.
2. Tell your proxy to send the PROXY header (see [Point your proxy at it](#point-your-proxy-at-it)).
3. If your proxy runs on a different machine, add its IP to `trusted_proxies` (see below).
4. Start the server. Done.

Out of the box it works for a proxy running on the same machine as the server. That's the only case
that needs zero config.

## Configure it

**NeoForge** — settings live in `config/haproxycompat-common.toml`. Edit them while the server is
running and they apply instantly, no restart needed.

**Fabric** — settings live in `config/haproxycompat.json`. Restart required for changes to take effect.

```toml
# NeoForge (haproxycompat-common.toml)
[general]
    enabled = true
    require_proxy_protocol = true
    trusted_proxies = ["127.0.0.1/32", "::1/128"]
    log_connections = false
```

```json
// Fabric (haproxycompat.json)
{
  "enabled": true,
  "requireProxyProtocol": true,
  "trustedProxies": ["127.0.0.1/32", "::1/128"],
  "logConnections": false
}
```

### Settings

- **enabled** — master on/off switch.
- **require\_proxy\_protocol** — `true`: only allow connections with a valid PROXY header from a
  trusted proxy. Use when the Minecraft port is reachable only through your proxy. `false`: also
  allow plain direct connections through untouched.
- **trusted\_proxies** — IP addresses / CIDR ranges that may send PROXY headers. Only these are
  believed; anyone else sending a PROXY header is dropped. Add your proxy's address here.
- **log\_connections** — print one line per connection decision. Great for debugging, noisy once working.

### Why `trusted_proxies` matters

The PROXY header is just text the connecting side sends. Anyone who can reach your server port
directly could send a fake one and claim to be any IP they like, which would let them dodge bans or
frame someone else. To stop that, HAProxyCompat only believes the header when it comes from an
address you've listed in `trusted_proxies`. Everyone else is ignored or dropped.

Two simple rules:

1. **Add your proxy's real address.** The default list only trusts the local machine. If your proxy
   lives on another host or subnet, its headers get rejected until you add its IP or CIDR here.
2. **Lock the door behind the proxy.** Firewall the Minecraft port so only your proxy can reach it.
   That way a forged header can never arrive in the first place.

If the list is empty, nobody is trusted and no real IPs get applied. The server logs a warning at
startup so you're not left guessing.

## Point your proxy at it

HAProxyCompat only listens. Your proxy still has to be told to **send** the header:

- **HAProxy:** add `send-proxy` (v1) or `send-proxy-v2` to the `server` line.
- **nginx (stream):** set `proxy_protocol on;` in the `server` block.
- **FRP:** set `transport.proxyProtocolVersion = "v2"` on the proxied service.
- **Traefik:** enable `proxyProtocol` on the TCP service's server.

## Build from source

You need JDK 21 for 1.21.x builds, JDK 25 for 26.1.x (Gradle downloads the right one automatically
via toolchains for the NeoForge builds; Fabric 26.1 requires JDK 25 to run Gradle itself).

```bat
gradlew.bat :neoforge-1.21:build
gradlew.bat :neoforge-26.1:build
gradlew.bat :fabric-1.21:build
:: Requires JDK 25:
gradlew.bat :fabric-26.1:build
```

Jars land in `versions/<subproject>/build/libs/`.

## Under the hood

Every new connection first meets a small gatekeeper (`ProxyProtocolDetector`) that sits at the very
front of the network pipeline. It peeks at the opening bytes to see if a PROXY header is there, and
checks whether the connection came from a trusted proxy. Then it decides what to do:

| From a trusted proxy? | Has a PROXY header? | What happens |
|---|---|---|
| Yes | Yes | Decode the header and apply the real client IP |
| Yes | No  | Drop it (or let it through, if `require_proxy_protocol` is off) |
| No  | Yes | Drop it. Someone's trying to spoof an IP |
| No  | No  | Drop it (or let it through, if `require_proxy_protocol` is off) |

When a header should be read, the gatekeeper hands the bytes to Netty's own PROXY parser, grabs the
real address, and writes it straight onto Minecraft's connection object. That last step happens the
moment the header arrives, which is the trick that makes the IP show up everywhere: in the join log,
in ban checks, and anywhere else the server asks "who is this?". Health-check pings that carry no
real client (the PROXY LOCAL command) are recognized and left alone.

It's a dedicated-server-only mod, so it never touches singleplayer or LAN worlds.

The core logic (`ProxyProtocolDetector`, `ProxyProtocolHandler`, mixins) is shared between both
loaders. NeoForge uses `ModConfigSpec` for live-reloading config; Fabric reads `haproxycompat.json`
at startup.

## License

GPL-3.0-or-later. See [LICENSE](LICENSE). The bundled `netty-codec-haproxy` keeps its own
Apache-2.0 license (inside the jar-in-jar).
