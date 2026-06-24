# 2026-06-24 08:39 UTC — Fix VS Code extension host failure (Claude command + chat both broken)

## TL;DR

**Why:** Clicking to open a new Claude instance in VS Code throws `command 'claude-vscode.terminal.open' not found`, and chat (and other extensions) also error. The "not found" command is a **symptom, not the cause** — VS Code's **extension host failed to load**, so no extension finished activating and none of their commands registered. Running `claude` from a plain Bash terminal works because it bypasses the extension entirely.
**End result:** Identify the extension (or VS Code state) crashing the extension host, fix/remove it, and confirm the Claude icon/command and chat both work again from inside VS Code — no longer needing the Bash + `claude` workaround.

## ▶ Next executable step (resume here)

Step 1 — `Ctrl+Shift+P` → `Developer: Reload Window`. If the Claude command and chat come back, done. If not, go to Step 2.

## Steps

- [ ] Step 1 — Reload the extension host: `Ctrl+Shift+P` → `Developer: Reload Window`. Re-test opening Claude and chat. If both work, stop here.
- [ ] Step 2 — Read the failure: `Ctrl+Shift+P` → `Developer: Show Logs...` → **Extension Host**. Scroll to the **top** and capture the first error / stack trace — that names the extension dragging the host down.
- [ ] Step 3 — Confirm it's an extension (not VS Code itself): quit VS Code, relaunch from a terminal with `code --disable-extensions`. If Claude (via CLI) and the editor behave cleanly, an extension is the cause → Step 4. If problems persist even with extensions off, it's VS Code state → Step 5.
- [ ] Step 4 — Bisect to the culprit: `Ctrl+Shift+P` → `Extensions: Start Extension Bisect`, answer the prompts until VS Code names the offending extension. Disable or uninstall it, then `Developer: Reload Window`.
- [ ] Step 5 — If VS Code state is corrupt (or bisect finds nothing): fully quit VS Code, then clear stale extension cache — on Windows delete `%USERPROFILE%\.vscode\extensions\.obsolete` and the cache folders under `%APPDATA%\Code\`. Confirm VS Code is **1.98.0+** (`Help → About`); update if older. Relaunch.
- [ ] Step 6 — If Claude specifically is still broken after the host loads cleanly: reinstall the Claude Code extension (Extensions view → right-click → Uninstall → Reload → install fresh), and verify the standalone CLI is on PATH (`claude --version` in a terminal).
- [ ] Step 7 — Verify: from inside VS Code, the Claude icon / `claude-vscode.terminal.open` command opens a new instance, and chat works. Workaround (Bash → `claude`) no longer required.

## Notes

- Workaround while broken: open a Bash terminal and run `claude` directly — skips the extension layer entirely.
- This is **not** Claude-specific; the same fix restores all the extensions that were erroring.
- This is local-environment troubleshooting, not shop-repo work — delete this plan once resolved (don't commit it as a project deliverable).
