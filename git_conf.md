# Configurazione Git & Workflow del Corso

Repository del corso (Prof. Alessandro Ricci):
`https://github.com/pslab-unibo/sisco-2026-2027.git`

---

## 1. Configurazione Iniziale Remote

Aggiungi il repository del prof come remote `upstream`:
```bash
git remote add upstream https://github.com/pslab-unibo/sisco-2026-2027.git
```

*(Opzionale/Consigliato)* Disabilita i push accidentali verso `upstream`:
```bash
git remote set-url --push upstream DISABLED
```

Verifica la configurazione dei remote:
```bash
git remote -v
```
Dovresti vedere:
```text
origin    https://github.com/OlivieriMichele/ConcurrentSystem.git (fetch)
origin    https://github.com/OlivieriMichele/ConcurrentSystem.git (push)
upstream  https://github.com/pslab-unibo/sisco-2026-2027.git (fetch)
upstream  DISABLED (push)
```

---

## 2. Scaricare gli Aggiornamenti dal Prof (Fetch / Pull)

### Solo Fetch (senza toccare il codice locale)
Per scaricare commit e rami aggiornati dal prof:
```bash
git fetch upstream
```

Per vedere le differenze tra il tuo master e quello del prof:
```bash
git log HEAD..upstream/master --oneline
```

### Unire le novità nel proprio branch locale (Merge)
```bash
git checkout master
git merge upstream/master
```
*(Oppure in un solo passaggio con pull)*:
```bash
git pull upstream master
```

---

## 3. Salvare il Lavoro sul Tuo Repository (Origin)

Dopo aver completato gli esercizi o integrato i lab del prof:
```bash
git add .
git commit -m "Descrizione delle modifiche"
git push origin master
```

---

## 4. Note Utili & Risoluzione Conflitti

- **In caso di conflitti dopo il merge/pull:**
  1. Apri i file in conflitto e risolvi le sezioni marcate con `<<<<<<<` e `>>>>>>>`.
  2. Aggiungi i file risolti: `git add -A`.
  3. Completa il commit: `git commit -m "Risolti conflitti con upstream"`.

- **Ignorare file di build Maven (`target/`):**
  Per evitare di versionare la cartella di build localmente:
  ```bash
  echo target/ >> .git/info/exclude
  ```
