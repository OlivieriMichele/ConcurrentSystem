# Workflow git

- Update esercizi dal prof: `git pull`  (tracking su upstream/master)
- Backup sul mio repo:      `git push origin master`
- Push verso prof bloccato (upstream push = DISABLED)

Conflitti dopo pull: risolvi i file, poi `git add -A && git commit`.

Cartelle `target/` (build Maven) ignorate via `.git/info/exclude` (locale, non versionato).
Su nuovo clone: `echo target/ >> .git/info/exclude`.
