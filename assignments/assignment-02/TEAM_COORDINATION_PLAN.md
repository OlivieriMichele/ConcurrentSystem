# Piano di Coordinamento e Metodologia di Team - Assignment #02
**Corso:** Sistemi Concorrenti (Laurea Magistrale ISI - UNIBO Cesena)  
**Docente:** Prof. Alessandro Ricci  
**Ruolo:** Team Manager & Architetto Concorrente  

---

## 1. Obiettivo Strategico del Team

L'obiettivo primario di questo piano di lavoro è garantire che entrambi i membri del team (**Membro 1** e **Membro 2**) acquisiscano una **piena padronanza concettuale, architetturale e implementativa** dell'intero progetto, eliminando il rischio di *isole di competenza* ("silos").

All'esame orale con il docente, entrambi i membri devono essere in grado di:
1. Spiegare e giustificare ogni singola riga di codice concorrente (perché un `wait()` è dentro un `while`, dove si trova la sezione critica, perché non ci sono data race).
2. Spiegare sia la versione a **Thread + Monitor** sia quella basata su **Executor Framework**, evidenziandone differenze prestazionali e modelli di memoria.
3. Argomentare il modello formale **FSP/LTSA** e i risultati del model-checking con **Java PathFinder (JPF)**.
4. Spiegare come il disaccoppiamento tra il motore di simulazione e l'Event Dispatch Thread (EDT) della GUI garantisce liveness e reattività senza deadlock.

---

## 2. Il Modello di Collaborazione: *Cross-Defense & Inverted Verification*

### L'Anti-Pattern da Evitare: *Silos Orizzontale*
- ❌ *Persona A fa il motore concorrente, Persona B fa la GUI e la relazione.*  
  **Esito:** Persona A non sa come funziona l'EDT e i comandi interattivi; Persona B non sa giustificare il monitor a barriera e viene penalizzata all'orale.

### Il Modello Vincente: *Sviluppo Parallelo a Validazione Incrociata*
- ✅ Ogni componente ha un **Autore Primario (Lead Dev)** e un **Validatore Critico (Reviewer & Tester)**.
- ✅ Chi scrive l'implementazione $A$ deve essere l'artefice dei test/benchmark dell'implementazione $B$, e viceversa.
- ✅ I contratti d'interfaccia e i modelli formali si definiscono sempre a quattro mani.

```
+--------------------------------------------------------------------------+
|  FASE 0 (INSIEME): Design Architetturale, Modello FSP, Contratti shared  |
+--------------------------------------------------------------------------+
             |                                              |
      [Membro 1: Autore]                             [Membro 2: Autore]
   Versione Thread + Monitor Barrier               Versione Executor Framework
             |                                              |
   [Membro 2: Reviewer & JPF Test]                [Membro 1: Reviewer & Benchmark]
   Scrive model check JPF per Monitor             Scrive test prestazionali & speedup
             |                                              |
+--------------------------------------------------------------------------+
|  FASE 2 (INCROCIATA): GUI Controller Monitor & Decoupling EDT            |
|  - Membro 1: State Monitor (PAUSED, STOP, STEP) con wait/notify          |
|  - Membro 2: View & EDT Decoupling (Snapshot immutabili su Swing/JavaFX) |
+--------------------------------------------------------------------------+
             |
+--------------------------------------------------------------------------+
|  FASE 3 (INSIEME): Relazione Tecnica e "Mock Oral Exam" di coppia        |
+--------------------------------------------------------------------------+
```

---

## 3. Roadmap Operativa Dettagliata

### Fase 0: Definizione dei Contratti e Architettura Comune (Insieme)
*Durata stimata: 1 sessione congiunta (2-3 ore).*

Prima di scrivere codice, entrambi i membri stabiliscono:
1. **L'Invariante di Correttezza per $N$-Body:**
   - La simulazione ad ogni passo temporale $dt$ è divisa in **due fasi distinte**:
     - *Fase 1 (Lettura globale / Scrittura locale):* Ogni worker calcola le forze sul proprio sottoinsieme di corpi $b[i]$ leggendo le posizioni al tempo $t$ di tutti i corpi $b[j]$. Nessun corpo modifica la propria posizione in questa fase $\rightarrow$ **zero data race**.
     - *Barriera 1:* Tutti i worker devono terminare il calcolo prima di procedere.
     - *Fase 2 (Scrittura disgiunta):* Ogni worker aggiorna posizione e velocità del proprio sottoinsieme $b[i]$ usando la velocità calcolata.
     - *Barriera 2:* Consolidamento dello stato al tempo $t+dt$.
2. **Interfaccia Comune del Motore (`SimulationEngine`):**
   ```java
   public interface SimulationEngine {
       void init(int nBodies, int nSteps, int nWorkers);
       void execute(); // Per la versione headless dello Step 1
   }
   ```
3. **Modello FSP Preliminare:**
   Stesura alla lavagna del processo che modella il ciclo a due barriere per comprendere la sequenza di stati.

---

### Fase 1: Step 1 (Headless & Performance) ad Assegnazione Incrociata
*Durata stimata: 4-5 giorni.*

| Aspetto | Membro 1 | Membro 2 |
| :--- | :--- | :--- |
| **Implementazione Thread + Monitor** | **Lead Developer:** Implementa la classe Monitor `Barrier` (custom monitor con `synchronized`, `wait()`, `notifyAll()`) e la classe `SimulationWorkerThread` (thread persistenti). | **Reviewer & JPF Tester:** Esegue il code review riga per riga. Crea la configurazione e il modello semplificato per **Java PathFinder (JPF)** per verificare l'assenza di deadlock e la correttezza del monitor. |
| **Implementazione Task-based (Executors)** | **Reviewer & Benchmarker:** Esegue il code review riga per riga. Scrive la suite di benchmark per misurare tempo di esecuzione e speedup scalando il numero di worker da 1 a $N$ core. | **Lead Developer:** Progetta e implementa l'`ExecutorService` con pool di thread fisso e la sottomissione dei task di calcolo (`invokeAll()` con lista di `Callable`). |

> **Regola aurea:** Membro 2 non può dire "non so come funziona la barriera", perché è lui che ha dovuto modellarla e farla passare su JPF! Membro 1 non può dire "non conosco l'Executor", perché è lui che ha scritto il benchmark per misurarne lo scalamento!

---

### Fase 2: Step 2 (GUI & Controllo Interattivo)
*Durata stimata: 3-4 giorni.*

Lo Step 2 richiede la gestione asincrona dei comandi (`Start`, `Stop`, `Suspend`, `Step-by-Step`) e la visualizzazione fluida senza bloccare la grafica.

#### Suddivisione dei compiti:
- **Membro 1 $\rightarrow$ Il Monitor di Controllo dello Stato (`SimulationController`):**
  - Implementa un Monitor che gestisce la macchina a stati: `IDLE`, `RUNNING`, `PAUSED`, `STEPPING`, `STOPPED`.
  - Fornisce il metodo bloccante `checkState()` richiamato dal motore di simulazione al termine di ogni iterazione:
    - Se lo stato è `PAUSED`, il thread di simulazione si sospende (`wait()`).
    - Un click su `Resume` o `Step` risveglia il thread (`notifyAll()`).
  - Gestisce l'arresto pulito (`STOPPED`) senza utilizzare `Thread.stop()` (deprecato).

- **Membro 2 $\rightarrow$ Il Disaccoppiamento Grafico & EDT:**
  - Sviluppa la View (Swing/JavaFX) con pannello di controllo e canvas di rendering.
  - Implementa il pattern a **Snapshot Immutabili**: il thread di simulazione, completata l'iterazione, genera un clone leggero/array immutabile delle coordinate dei corpi e invoca il rendering asincrono tramite:
    ```java
    SwingUtilities.invokeLater(() -> view.render(snapshot));
    ```
  - Questo garantisce che la simulazione proceda senza attendere il refresh dello schermo e che l'EDT non rimanga mai congelato.

#### Inversione per la Validazione:
Membro 2 conduce uno **stress test** dell'interfaccia: clic ripetuti, combinazioni rapide (*Pause $\rightarrow$ Step $\rightarrow$ Step $\rightarrow$ Resume $\rightarrow$ Stop*) per verificare che il Monitor di Membro 1 non si blocchi in deadlock o perda risvegli (*lost wakeups*).

---

### Fase 3: Benchmark, Model-Checking e Relazione
*Durata stimata: 3 giorni.*

1. **Test di Scalabilità (Prestazioni):**
   - Eseguire i test su **entrambi i computer** dei membri del team (utile se uno ha Mac Apple Silicon e l'altro Intel/AMD x86, per mostrare portabilità e comportamento differente della JVM).
   - Grafici di confronto: *Speedup vs Numero di Core* (1, 2, 4, 8, 16 worker) e calcolo dell'overhead tra *Thread persistenti* e *Task Executors*.
2. **Scrittura della Relazione (`doc/`):**
   - Si scrive a sezioni incrociate: l'autore di una sezione la redige, l'altro la revisiona formulando obiezioni critiche prima del commit finale.
   - Entrambi verificano la correttezza formale della specifica FSP e delle formule FLTL.

---

### Fase 4: La Simulazione d'Esame ("Mock Defense")
*Durata stimata: 1 ora prima della consegna definitiva.*

Prendetevi un'ora in cui ciascuno interroga l'altro simulando le tipiche domande d'esame del Prof. Ricci.

#### Checklist di verifica incrociata:
- [ ] **Membro 1 interroga Membro 2:**
  - *"Come garantisce `invokeAll()` la sincronizzazione tra la Fase 1 e la Fase 2 nell'implementazione ad Executor?"*
  - *"Nel disaccoppiamento GUI-motore, cosa succederebbe se passassimo alla View direttamente il riferimento all'array dei corpi anziché uno snapshot?"*
- [ ] **Membro 2 interroga Membro 1:**
  - *"Nel Monitor `Barrier`, perché usiamo `notifyAll()` anziché `notify()`? In quale scenario `notify()` causerebbe un deadlock?"*
  - *"Se l'utente preme 'Pause' mentre i worker stanno calcolando le forze nel mezzo di uno step, dove e quando viene intercettata la sospensione?"*
- [ ] **Entrambi verificano il Modello FSP:**
  - *"Quali sono le proprietà di Safety e Liveness verificate tramite LTSA? Cosa significa la violazione di una proprietà FLTL in questo contesto?"*

---

## 4. Regole Operative per Git

Per mantenere un repository ordinato ed evitare conflitti:

1. **Branch dedicati per feature:**
   - `feature/step1-barrier-threads` (Membro 1)
   - `feature/step1-executors` (Membro 2)
   - `feature/step2-controller-monitor` (Membro 1)
   - `feature/step2-gui-view` (Membro 2)
2. **Review prima del Merge:**
   - Nessun merge diretto su `master`.
   - Ogni Pull Request deve essere letta e approvata dall'altro membro.
3. **Ignorare file compilati:**
   - Assicurarsi che le cartelle `target/` e i file `.class` siano sempre esclusi per non sporcare la cronologia di git.
