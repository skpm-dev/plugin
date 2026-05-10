# skpm Plugin

> The Bukkit plugin that brings skpm to your Minecraft server.

Drop it in, run a command, and your Skript packages are installed, verified, and tracked — no file transfers, no manual reloads.

---

## Requirements

- Paper (or any Bukkit-compatible server) 1.21+
- [Skript](https://github.com/SkriptLang/Skript) already installed

## Installation

1. Download the latest `SKPM.jar` from the [releases page](https://github.com/skpm-dev/plugin/releases)
2. Drop it into your server's `plugins/` folder
3. Restart the server

No configuration required.

---

## Commands

All commands require the `skpm.use` permission (op by default).

| Command | Description |
|---|---|
| `/skpm install <package>` | Download and install a package from the registry |
| `/skpm remove <package>` | Uninstall a package and remove its scripts |
| `/skpm list` | List all installed packages |

### Examples

```
/skpm install economy
/skpm install join-message
/skpm remove economy
/skpm list
```

---

## How it works

When you run `/skpm install <package>`:

1. The plugin fetches package metadata from the registry
2. Each script file is downloaded and its SHA-256 checksum is verified
3. Files are written to `plugins/Skript/scripts/skpm/<package>/`
4. Skript reloads the new scripts automatically
5. The install is recorded in `plugins/SKPM/skript.lock`

The lockfile tracks every installed package — name, version, and per-file integrity hashes. It's the source of truth for `/skpm list` and the foundation for future integrity checks.

---

## File layout

```
plugins/
├── SKPM.jar
├── SKPM/
│   └── skript.lock          ← installed package manifest
└── Skript/
    └── scripts/
        └── skpm/
            ├── economy/
            │   └── economy.sk
            └── join-message/
                └── join-message.sk
```

---

## Permissions

| Permission | Default | Description |
|---|---|---|
| `skpm.use` | op | Allows use of all `/skpm` commands |

---

## Related

- [skpm-dev/cli](https://github.com/skpm-dev/cli) — CLI tool for publishing packages
- [skpm-dev/registry](https://github.com/skpm-dev/registry) — package registry and API
