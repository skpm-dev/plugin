# skpm Plugin

**[skpm.org](https://skpm.org)** — the package manager for Skript.

> The Bukkit plugin that installs, updates, and manages Skript packages directly from your server console or in-game chat.

---

## Requirements

- **Paper** (or any Bukkit-compatible fork) 1.21+
- **[Skript](https://github.com/SkriptLang/Skript)** already installed on the server

---

## Installation

1. Download the latest **`SKPM.jar`** from the [releases page](https://github.com/skpm-dev/plugin/releases)
2. Drop it into `plugins/`
3. Restart the server

No configuration required.

---

## Commands

### Registry packages

| Command | Description |
|---|---|
| `/skpm install <package>` | Install a package from the skpm registry |
| `/skpm update <package>` | Update an installed package to the latest version |
| `/skpm update` | Update all installed packages |
| `/skpm remove <package> --confirm` | Uninstall a package and remove its scripts |
| `/skpm list` | List all installed packages and their versions |
| `/skpm info <package>` | Show metadata, versions, and file details for a package |
| `/skpm search <query>` | Search the registry by name or description |

### SpigotMC packages

Prefix any install with `spigotmc:` to fetch directly from SpigotMC via the Spiget API. Only **free resources in the Skript category** are accepted.

```
/skpm install spigotmc:12345       ← install by numeric resource ID
/skpm install spigotmc:SkEditor    ← install by name (prompts if ambiguous)
```

SpigotMC installs are marked **unverified** — Spiget provides no expected checksums, so integrity cannot be guaranteed.

---

## How it works

When you run `/skpm install <package>`:

1. **Fetches** package metadata from `registry.skpm.org`
2. **Checks** that all addon and dependency version constraints are satisfied
3. **Downloads** each script file to a staging directory
4. **Verifies** SHA-256 checksums against registry values
5. **Moves** the staging directory atomically into `plugins/Skript/scripts/skpm/<package>/`
6. **Updates** `plugins/SKPM/skript.lock` with the package name, version, and per-file hashes
7. **Reloads** each script via `skript reload`

The lockfile is the source of truth for `/skpm list`. On failure at any step, staging files are cleaned up and the lockfile is not touched.

---

## File layout

```
plugins/
├── SKPM.jar
├── SKPM/
│   └── skript.lock              ← installed package manifest (JSON)
└── Skript/
    └── scripts/
        └── skpm/
            ├── economy/
            │   └── economy.sk
            └── join-message/
                └── join-message.sk
```

**`skript.lock` schema:**

```json
{
  "schemaVersion": 1,
  "generatedAt": "2026-05-17T12:00:00Z",
  "packages": [
    {
      "name": "economy",
      "version": "1.2.3",
      "description": "A simple economy system",
      "files": {
        "economy.sk": "sha256:a1b2c3..."
      }
    }
  ]
}
```

---

## Permissions

| Permission | Default | Grants |
|---|---|---|
| `skpm.install` | OP | `/skpm install` |
| `skpm.remove` | OP | `/skpm remove` |
| `skpm.update` | OP | `/skpm update` |
| `skpm.search` | All players | `/skpm search` |
| `skpm.list` | All players | `/skpm list` |
| `skpm.info` | All players | `/skpm info` |
| `skpm.admin` | OP | All of the above |

---

## Related

- **[skpm-dev/cli](https://github.com/skpm-dev/cli)** — CLI tool for publishing packages
- **[skpm-dev/registry](https://github.com/skpm-dev/registry)** — Registry API and data store
