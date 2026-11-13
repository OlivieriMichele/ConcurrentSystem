# Appunti e Guida di Progettazione: N-Body Simulation (Assignment #02)
**Corso di Sistemi Concorrenti — A.A. 2026/2027**  
*ISI LM UNIBO — Campus di Cesena*

---

## 0. Le Domande Fondamentali: *Perché quelle scelte?* e *Perché lavora in quel modo?*

Prima di esaminare i dettagli implementativi, rispondiamo direttamente e formalmente alle due domande guida che motivano l'intera architettura concorrente.

### 0.1 Perché lavora in quel modo? (Analisi delle Dipendenze dei Dati)
Nel codice sequenziale di riferimento (`sisco.ass02.n_body_seq.NBodySimulation`), ogni incremento temporale virtuale $dt$ si sviluppa in due passaggi distinti:
1. **Calcolo delle Forze e Nuove Velocità:**
   $$\forall i \in \{0 \dots N-1\}: \quad \vec{F}_i = \sum_{j \neq i} \vec{F}_{i \leftarrow j}(\mathbf{p}_i(t), \mathbf{p}_j(t)), \quad \vec{v}_i(t+dt) = \vec{v}_i(t) + \frac{\vec{F}_i}{m_i} \cdot dt$$
2. **Aggiornamento delle Posizioni:**
   $$\forall i \in \{0 \dots N-1\}: \quad \mathbf{p}_i(t+dt) = \mathbf{p}_i(t) + \vec{v}_i(t+dt) \cdot dt$$

Se tentassimo di parallelizzare banalmente il ciclo unificando i due passaggi per ogni corpo:
```java
// ERRORE CRITICO: Race Condition / Stato Incoerente
for (each body b[i] in parallel) {
    computeForceAndVelocity(b[i]);
    b[i].updatePos(dt); // SCRITTURA ANTICIPATA DELLA POSIZIONE!
}
```
Si verificherebbe una grave **Data Race (Read/Write Conflict)** secondo le condizioni di Bernstein:
* Il thread $T_A$ che elabora il corpo $b[0]$ aggiornerebbe $b[0].pos$ al tempo $t+dt$.
* Contemporaneamente, il thread $T_B$ che sta calcolando la forza su $b[1]$ leggerebbe la posizione di $b[0]$ già alterata a $t+dt$, mentre per tutti gli altri corpi legge le posizioni al tempo $t$.
* La simulazione diverrebbe **non deterministica, fisicamente scorretta e dipendente dall'interleaving**.

> **Regola aurea del calcolo N-Body (Bulk Synchronous Parallel):**  
> *Nessun corpo può aggiornare la propria posizione prima che TUTTI i corpi abbiano completato la lettura delle posizioni al tempo $t$ per calcolare le forze.*  
> *Nessun corpo può iniziare il calcolo delle forze al passo $t+dt$ finché TUTTI i corpi non hanno aggiornato la propria posizione a $t+dt$.*

Questo impone una struttura ad **avanzamento a fasi alternate** separate da punti di rendez-vous globale (**Barriere**).

---

### 0.2 Perché quelle scelte architetturali?

| Scelta Adottata | Motivazione Tecnica & Razionale di Design |
| :--- | :--- |
| **Static Domain Decomposition** (chunking ad indici contigui) | Il problema *All-Pairs* $\mathcal{O}(N^2)$ presenta un carico computazionale perfettamente omogeneo e bilanciato: per ogni corpo $b[i]$ si iterano tutti gli altri $N-1$ corpi. Una partizione statica $\{[0, N/K), [N/K, 2N/K), \dots\}$ distribuisce esattamente $N/K$ corpi a ciascun worker, massimizza la località della cache (L1/L2) ed **elimina qualsiasi overhead o contesa** legata a code dinamiche di work-stealing. |
| **Doppia Barriera Ciclica (Cyclic Barrier)** | Modella in modo naturale il pattern BSP (Bulk Synchronous Parallel). La **Barriera 1** garantisce che tutti i worker finiscano di leggere le posizioni correnti prima che alcuno le sovrascriva. La **Barriera 2** garantisce che tutte le posizioni siano aggiornate prima di passare all'iterazione successiva e permette un punto sincronizzato per GUI/terminazione. |
| **Monitor Custom ad Alto Livello** (per la versione Threads) | Il vincolo dell'assignment richiede esplicitamente costrutti ad alto livello (`synchronized`, `wait()`, `notifyAll()`) invece di primitive a basso livello (`Thread.suspend()`, busy waiting, o semafori nudi). L'implementazione deve prevedere il meccanismo a **generazioni (*generation count*)** per evitare deadlock o ripartenze spurie tipiche dei cicli chiusi. |
| **Executor Framework con `invokeAll()`** (per la versione Tasks) | Negli esecutori a task, il coordinatore sottomette blocchi discreti di lavoro a un pool fisso di thread (`newFixedThreadPool`). Il metodo `invokeAll()` attende la terminazione di tutti i task sottomessi, fungendo da barriera intrinseca senza necessità di lock espliciti o monitor aggiuntivi. |
| **MVC Concorrente + State Monitor + Snapshot** (per lo Step 2 con GUI) | L'Event Dispatch Thread (EDT) di Swing non deve mai eseguire calcoli pesanti (pena il blocco dell'interfaccia) né leggere lo stato dei corpi mentre i thread di calcolo lo modificano (pena *inconsistent view* e *ConcurrentModificationException*). La simulazione gira in background, obbedisce a un Monitor di Controllo (`RUNNING`, `PAUSED`, `STOPPED`, `STEP`) e invia alla View solo **copie difensive immutabili** via `SwingUtilities.invokeLater()`. |

---

## 1. Analisi del Baseline Sequenziale (`sisco.ass02.n_body_seq`)

Nel package `sisco.ass02.n_body_seq` sono forniti:
* `P2d`: Coordinate cartesiane 2D con metodi mutabili (`sum`, `change`).
* `V2d`: Componenti vettoriali 2D con metodi mutabili (`sum`, `scalarMul`, `normalize`).
* `Body`: Rappresenta una particella dotata di id, massa, raggio, posizione e velocità.
  * `computeGForceBy(Body b)`: Calcola la forza gravitazionale di attrazione newtoniana esercitata da $b$:
    $$\vec{F} = \frac{G \cdot m_1 \cdot m_2}{d^2} \cdot \frac{\vec{d}}{\|\vec{d}\|}$$
    (Usa $G = 0.001$).
  * `updateVelocity(V2d acc, double dt)`: Aggiorna il vettore velocità in-place.
  * `updatePos(double dt)`: Aggiorna il punto posizione in-place.
* `NBodySimulation`: Gestisce la lista `List<Body> bodies` e il ciclo temporale.
* `SimulationMain`: Esecuzione headless di benchmark (5000 corpi per 100 step).
* `SimulationMainWithGUI`: Esecuzione con visualizzatore grafico `SimulationView` (Swing).

### Il Collo di Bottiglia Computazionale
Nel metodo `computeTotalGForceOnBody(Body b)` abbiamo:
```java
for (int j = 0; j < bodies.size(); j++) {
    Body otherBody = bodies.get(j);
    if (!b.equals(otherBody)) {
        V2d forceByOtherBody = b.computeGForceBy(otherBody);
        totalForce.sum(forceByOtherBody);
    }
}
```
Per $N$ corpi, il calcolo delle forze richiede $N \times (N-1)$ interazioni per ogni step. Con $N = 5000$, ogni singolo step esegue circa $2.5 \times 10^7$ calcoli vettoriali con radici quadrate (`Math.sqrt`). L'aggiornamento delle posizioni richiede invece solo $N$ operazioni lineari ($\mathcal{O}(N)$).  
Il calcolo delle forze assorbe oltre il 99% del tempo di calcolo totale ed è il candidato ideale per la scalabilità parallela.

---

## 2. Architettura Step 1: Headless Performance-Oriented

Nello Step 1 dobbiamo sviluppare due implementazioni distinte focalizzate esclusivamente sulle prestazioni e sulla scalabilità.

```
       +-------------------------------------------------------+
       |                  SIMULATION STEP                      |
       +-------------------------------------------------------+
                                  |
                                  v
+---------------------------------------------------------------------+
| FASE 1: Calcolo Forze e Velocità (Parallel Read / Local Write)      |
| Worker 0 -> Corpi [0 ... N/K-1]                                     |
| Worker 1 -> Corpi [N/K ... 2N/K-1]                                  |
| ...                                                                 |
| Worker K-1 -> Corpi [(K-1)N/K ... N-1]                              |
+---------------------------------------------------------------------+
                                  |
                                  v
       ================== BARRIERA 1 ==================
       (Attende che tutti i K worker finiscano le forze)
                                  |
                                  v
+---------------------------------------------------------------------+
| FASE 2: Aggiornamento Posizioni (Parallel Disjoint Write)           |
| Worker 0 -> Aggiorna posizioni [0 ... N/K-1]                        |
| Worker 1 -> Aggiorna posizioni [N/K ... 2N/K-1]                     |
| ...                                                                 |
| Worker K-1 -> Aggiorna posizioni [(K-1)N/K ... N-1]                 |
+---------------------------------------------------------------------+
                                  |
                                  v
       ================== BARRIERA 2 ==================
       (Attende che tutte le posizioni siano aggiornate;
        un worker designato o il Master incrementa vt)
                                  |
                                  v
                        [Fine Step m < M ?]
                           /           \
                     (Sì) /             \ (No)
                         v               v
                   Nuovo Step       Fine Simulazione
```

---

### 2.1 Versione A: Multithreading con Monitor Custom (`Platform Threads`)

#### Ciclo di Vita dei Thread
I thread worker vengono istanziati e avviati **una sola volta** prima dell'inizio della simulazione (`start()`), vivono per tutta la durata degli $M$ step e si arrestano al completamento:
* **Zero overhead di creazione thread per step.**
* Utilizzo di $K$ thread platform, dove tipicamente $K = \text{numero di core disponibili}$ (`Runtime.getRuntime().availableProcessors()`).

#### Progettazione del Monitor `CyclicBarrier` Custom
Un monitor barriera ingenuo basato su un solo contatore `nArrived` è fallace in un ciclo: un thread risvegliato velocemente potrebbe rientrare nella barriera dello step successivo e alterare `nArrived` prima che gli altri thread siano usciti dalla chiamata precedente.

Per garantire la correttezza, si impiega il pattern a **Generazione (Generation Counter)**:

```java
package sisco.ass02.n_body_conc_threads;

public class CyclicBarrierMonitor {
    private final int totalParticipants;
    private int arrived = 0;
    private int generation = 0;

    public CyclicBarrierMonitor(int totalParticipants) {
        this.totalParticipants = totalParticipants;
    }

    public synchronized void await() throws InterruptedException {
        int myGen = this.generation;
        arrived++;

        if (arrived == totalParticipants) {
            // L'ultimo thread che arriva fa avanzare la generazione
            arrived = 0;
            generation++;
            notifyAll(); // Risveglia tutti i partecipanti di questa generazione
        } else {
            // I thread arrivati prima attendono che la generazione cambi
            while (myGen == this.generation) {
                wait();
            }
        }
    }
}
```
* **Correttezza:** Anche se un thread risvegliato rientra immediatamente nel metodo `await()`, troverà `generation != myGen` e incrementerà il nuovo conteggio sulla nuova generazione senza interferire con chi deve ancora completare l'uscita dalla vecchia.

#### Struttura del Worker
```java
public class SimulationWorker extends Thread {
    private final int startIndex;
    private final int endIndex;
    private final List<Body> bodies;
    private final int nSteps;
    private final double dt;
    private final CyclicBarrierMonitor barrier1;
    private final CyclicBarrierMonitor barrier2;

    public SimulationWorker(int startIndex, int endIndex, List<Body> bodies,
                            int nSteps, double dt,
                            CyclicBarrierMonitor barrier1, CyclicBarrierMonitor barrier2) {
        this.startIndex = startIndex;
        this.endIndex = endIndex;
        this.bodies = bodies;
        this.nSteps = nSteps;
        this.dt = dt;
        this.barrier1 = barrier1;
        this.barrier2 = barrier2;
    }

    @Override
    public void run() {
        try {
            for (int step = 0; step < nSteps; step++) {
                // Fase 1: Calcolo Forze e Velocità per la propria partizione
                for (int i = startIndex; i < endIndex; i++) {
                    Body b = bodies.get(i);
                    V2d totalForce = computeTotalGForceOnBody(b);
                    V2d acc = new V2d(totalForce).scalarMul(1.0 / b.getMass());
                    b.updateVelocity(acc, dt);
                }

                // Sincronizzazione 1: attesa fine calcolo forze di tutti
                barrier1.await();

                // Fase 2: Aggiornamento Posizioni per la propria partizione
                for (int i = startIndex; i < endIndex; i++) {
                    bodies.get(i).updatePos(dt);
                }

                // Sincronizzazione 2: attesa fine aggiornamento posizioni
                barrier2.await();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private V2d computeTotalGForceOnBody(Body b) {
        V2d totalForce = new V2d(0, 0);
        for (Body other : bodies) {
            if (!b.equals(other)) {
                try {
                    totalForce.sum(b.computeGForceBy(other));
                } catch (Exception ignored) {}
            }
        }
        return totalForce;
    }
}
```

---

### 2.2 Versione B: Task-based con `ExecutorService`

#### Scelta della Granularità dei Task
* **Granularità Fine (Un task per ogni corpo):** Creare $N = 5000$ task ad ogni step per $M = 100$ step significa allocare e schedulare $500.000$ oggetti `Callable`/`Runnable`. L'overhead dell'allocazione di memoria e dello scheduling su `BlockingQueue` interna all'executor degraderebbe drasticamente le prestazioni.
* **Granularità Grossa (Un task per chunk di corpi):** Si creano esattamente $K$ task (pari al numero di worker/core) per ogni fase. Ogni task itera su una partizione di $N/K$ corpi. Questo bilancia perfettamente il carico con overhead di scheduling trascurabile.

#### Meccanismo di Sincronizzazione con `invokeAll()`
L'`ExecutorService` di Java offre il metodo sincrono bloccante `invokeAll(tasks)`:
```java
ExecutorService executor = Executors.newFixedThreadPool(numWorkers);

for (int step = 0; step < nSteps; step++) {
    // 1. Sottomissione e attesa completamento Fase Forze
    List<Callable<Void>> forceTasks = createForceTasks(partitions, bodies, dt);
    executor.invokeAll(forceTasks); // BARRIERA IMPLICITA 1

    // 2. Sottomissione e attesa completamento Fase Posizioni
    List<Callable<Void>> posTasks = createPositionTasks(partitions, bodies, dt);
    executor.invokeAll(posTasks); // BARRIERA IMPLICITA 2

    vt += dt;
}

executor.shutdown();
```
* **Vantaggio:** Nessuna barriera custom richiesta; la barriera è garantita dal completamento sincronizzato dei task tramite `invokeAll()`.
* **Riuso dei Task (Ottimizzazione):** Per evitare di allocare nuove istanze di `Callable` ad ogni step, le liste `forceTasks` e `posTasks` possono essere istanziate una sola volta all'avvio e ri-sottomesse ad ogni iterazione del ciclo.

---

## 3. Architettura Step 2: GUI e Interattività

Nello Step 2 introduciamo la GUI per osservare la simulazione in tempo reale e interagire con i comandi:
* `Start`: Avvia la simulazione.
* `Suspend / Resume`: Sospende temporaneamente o riprende l'avanzamento.
* `Step-by-step`: Fa avanzare la simulazione di un singolo frame/step e si rimette in pausa.
* `Stop`: Arresta definitivamente la simulazione in corso.

### 3.1 Pattern Architetturale: MVC Concorrente con Active Controller

```
                    +--------------------+
                    |        VIEW        |
                    | (VisualiserFrame)  |
                    +--------------------+
                     /        |         ^
          User click/         |         |  SwingUtilities.invokeLater()
          Key events          |         |  (Snapshot posizioni)
                   v          |         |
      +--------------------+  |  +--------------------+
      | CONTROLLER ASINCR. |  |  |       MODEL        |
      |   (State Monitor)  |--+  | (Corpi + VirtualT) |
      +--------------------+     +--------------------+
               ^                           ^
               |   verifica stato ad       | modifica corpi
               |   ogni iterazione         |
               +---------------------------+
               |  SIMULATION WORKERS / THREAD  |
               +-------------------------------+
```

### 3.2 Il Monitor di Controllo dello Stato (`SimulationController`)
I worker o il thread master della simulazione, prima di iniziare ogni iterazione $dt$, interrogano il monitor di controllo per verificare se possono procedere, se devono fermarsi o se devono attendere:

```java
public class SimulationController {
    public enum State { STOPPED, RUNNING, PAUSED }

    private State state = State.PAUSED;
    private boolean stepRequested = false;

    public synchronized void startSimulation() {
        this.state = State.RUNNING;
        notifyAll();
    }

    public synchronized void pauseSimulation() {
        this.state = State.PAUSED;
    }

    public synchronized void resumeSimulation() {
        this.state = State.RUNNING;
        notifyAll();
    }

    public synchronized void stepSimulation() {
        this.stepRequested = true;
        this.state = State.PAUSED;
        notifyAll();
    }

    public synchronized void stopSimulation() {
        this.state = State.STOPPED;
        notifyAll();
    }

    /**
     * Invocato dal thread di simulazione prima di eseguire un nuovo step.
     * Ritorna false se la simulazione deve terminare (STOPPED).
     */
    public synchronized boolean checkAndWait() throws InterruptedException {
        while (state == State.PAUSED && !stepRequested) {
            wait();
        }

        if (state == State.STOPPED) {
            return false;
        }

        if (stepRequested) {
            stepRequested = false; // Consuma lo step singolo
        }

        return true;
    }

    public synchronized boolean isStopped() {
        return state == State.STOPPED;
    }
}
```

### 3.3 Gestione del Rendering e Thread Safety con la View Swing
In `sisco.ass02.n_body_seq.SimulationView`, la classe `VisualiserPanel.paint(Graphics g)` itera su `bodies` leggendo `b.getPos()`. Se il thread di simulazione stesse modificando concorrentemente le posizioni:
1. Si verificherebbero artefatti grafici (corpi disegnati con coordinate disallineate).
2. L'EDT verrebbe rallentato dal lock se si usasse un lock condiviso.

**Soluzione adottata (Snapshot difensivo):**  
Al termine della Fase 2 di ogni step, il thread coordinatore estrae una lista immutabile di record o posizioni clonate (`List<P2d>` o snapshot di `Body`) e la inoltra alla View tramite `SwingUtilities.invokeLater()`:
```java
List<P2d> snapshot = bodies.stream()
                           .map(b -> new P2d(b.getPos().getX(), b.getPos().getY()))
                           .toList();

SwingUtilities.invokeLater(() -> {
    view.displaySnapshot(snapshot, vt, iter);
});
```
In questo modo l'EDT lavora su dati di sola lettura completamente isolati, preservando l'assoluta reattività dell'interfaccia.

---

## 4. Modellazione Formale con FSP e Verifica delle Proprietà (LTSA)

Come richiesto dalla consegna, il comportamento dei thread e della barriera ciclica viene formalizzato in **FSP (Finite State Processes)**.

### 4.1 Specifica FSP della Simulazione e della Barriera

Consideriamo un sistema con $K = 2$ worker e $M = 2$ step:

```fsp
const K = 2  // Numero di worker
const M = 2  // Numero di step

// Monitor Barriera Ciclica a 2 fasi
BARRIER(N=K) = BARRIER_ACC[0],
BARRIER_ACC[arrived:0..N] = 
    if (arrived < N) then (
        arrive -> BARRIER_ACC[arrived + 1]
    ) else (
        release -> BARRIER_REL[N]
    ),
BARRIER_REL[released:1..N] = 
    if (released > 1) then (
        exitBarrier -> BARRIER_REL[released - 1]
    ) else (
        exitBarrier -> BARRIER_ACC[0]
    ).

// Comportamento del Worker
WORKER(Id=1) = WORKER_STEP[1],
WORKER_STEP[s:1..M] = (
    computeForces[Id] ->
    b1.arrive ->
    b1.exitBarrier ->
    updatePos[Id] ->
    b2.arrive ->
    b2.exitBarrier ->
    if (s < M) then 
        WORKER_STEP[s + 1] 
    else 
        endWork[Id] -> STOP
).

// Composizione del Sistema
||NBODY_SYSTEM = (
    w[1..K]:WORKER(1) 
    || {w[1..K]}::b1:BARRIER(K) 
    || {w[1..K]}::b2:BARRIER(K)
) /{
    forall [i:1..K] {
        w[i].b1.arrive / b1.arrive,
        w[i].b1.exitBarrier / b1.exitBarrier,
        w[i].b2.arrive / b2.arrive,
        w[i].b2.exitBarrier / b2.exitBarrier
    }
}.
```

### 4.2 Proprietà di Correttezza verificate con LTSA
1. **Safety (Nessuna Race Condition su Posizioni e Forze):**  
   Nessun worker può eseguire `updatePos` se tutti i worker non hanno prima eseguito `computeForces`:
   ```fsp
   property NO_WRITE_BEFORE_READ = (
       forall [i:1..K] computeForces[i] -> ALL_COMPUTED
   ),
   ALL_COMPUTED = (
       forall [i:1..K] updatePos[i] -> NO_WRITE_BEFORE_READ
   ).
   ```
2. **Deadlock Freedom:**  
   L'LTSA analyzer verifica che non esistano deadlock terminali prima del raggiungimento di `endWork[i]` per tutti i worker (`DEADLOCK: false`).

---

## 5. Verifica con Model Checking tramite Java PathFinder (JPF)

### 5.1 Il Problema dello State Space Explosion
JPF esplora esaustivamente tutti i possibili interleaving dei thread.  
Eseguire JPF sul codice completo con $N = 5000$ e $M = 100$ causerebbe un'esplosione combinatoria immediata della memoria e tempo infinito.

### 5.2 Micro-Modello Semplificato per JPF
Per consentire la verifica esaustiva in JPF:
1. **Riduzione della scala:** $N = 2$ corpi, $K = 2$ worker, $M = 2$ step.
2. **Semplificazione della matematica:** I calcoli vettoriali continui vengono sostituiti da aggiornamenti discreti simbolici.
3. **Verifica degli invarianti tramite asserzioni:**

```java
package sisco.ass02.jpf_verification;

public class JPFNBodyVerification {
    static final int N = 2;
    static final int K = 2;
    static final int M = 2;

    static volatile boolean[] forcesComputed = new boolean[K];
    static volatile boolean[] positionsUpdated = new boolean[K];

    public static void main(String[] args) {
        CyclicBarrierMonitor b1 = new CyclicBarrierMonitor(K);
        CyclicBarrierMonitor b2 = new CyclicBarrierMonitor(K);

        for (int i = 0; i < K; i++) {
            final int id = i;
            new Thread(() -> {
                for (int step = 0; step < M; step++) {
                    // Fase 1
                    forcesComputed[id] = true;

                    try {
                        b1.await();

                        // Asserzione di Safety: TUTTI i worker devono aver completato le forze
                        // prima che chiunque aggiorni le posizioni
                        for (int w = 0; w < K; w++) {
                            assert forcesComputed[w] : "Safety violation: posizione aggiornata prima del calcolo forze!";
                        }

                        // Fase 2
                        positionsUpdated[id] = true;

                        b2.await();

                        // Asserzione: tutte le posizioni devono essere pronte prima di azzerare i flag
                        for (int w = 0; w < K; w++) {
                            assert positionsUpdated[w] : "Safety violation: step successivo iniziato prima dell'aggiornamento!";
                        }

                        // Reset per step successivo
                        forcesComputed[id] = false;
                        positionsUpdated[id] = false;

                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }).start();
        }
    }
}
```

Configurazione `jpf.properties`:
```properties
target = sisco.ass02.jpf_verification.JPFNBodyVerification
search.class = .search.heuristic.BFSHeuristic
listener = .listener.PreciseRaceDetector
```
**Risultato atteso da JPF:** `No errors found (no deadlocks, no unhandled exceptions, no assertion violations)`.

---

## 6. Piano dei Benchmark e Metodologia Sperimentale

### 6.1 Parametri e Condizioni di Test
I test di performance devono essere eseguiti solo sulla versione Step 1 (headless), confrontando:
1. **Versione Sequenziale (`sisco.ass02.n_body_seq.SimulationMain`)**
2. **Versione Parallela a Thread (`sisco.ass02.n_body_conc_threads`)**
3. **Versione Parallela a Task (`sisco.ass02.n_body_conc_executors`)**

#### Setup delle misurazioni:
* **JVM Warmup:** Eseguire 10–20 iterazioni a vuoto prima di cronometrare, per consentire al compilatore JIT (C2) di ottimizzare il bytecode.
* **Ripetizioni:** Calcolare la media e la deviazione standard su almeno 5 run indipendenti.
* **Variabili indipendenti:**
  * Numero di corpi $N \in \{1000, 2500, 5000, 10000\}$
  * Numero di core/thread $K \in \{1, 2, 4, 8, 12, 16\}$ (fino al massimo dei core fisici/logici della macchina)
  * Numero di step $M = 100$

### 6.2 Metriche di Performance
* **Speedup $S(p)$:**
  $$S(p) = \frac{T_1}{T_p}$$
  dove $T_1$ è il tempo di esecuzione con 1 thread (o sequenziale) e $T_p$ è il tempo con $p$ core.
* **Efficienza $E(p)$:**
  $$E(p) = \frac{S(p)}{p} \times 100\%$$
* **Scalabilità Forte (Strong Scaling):** Dimensione del problema $N$ fissa (es. 5000), aumentando $p$. Mostra i limiti imposti dalla Legge di Amdahl a causa della sincronizzazione a barriera.
* **Scalabilità Debole (Weak Scaling):** Il carico per processore rimane costante ($N^2 / p \approx \text{costante}$), aumentando sia $N$ che $p$.

---

## 7. Checklist di Consegna dell'Assignment

- [ ] **Sorgenti (`src/main/java`)**:
  - [ ] `sisco.ass02.n_body_seq.*` (baseline invariato)
  - [ ] `sisco.ass02.n_body_conc_threads.*` (Step 1 - Platform Threads + Monitor Barrier)
  - [ ] `sisco.ass02.n_body_conc_executors.*` (Step 1 - Executor Framework)
  - [ ] `sisco.ass02.n_body_gui.*` (Step 2 - GUI + MVC Concorrente + Comandi)
  - [ ] `sisco.ass02.jpf_verification.*` (Micro-modello per model checking)
- [ ] **Relazione Tecnica (`doc/report.md` o `.pdf`)**:
  - [ ] Analisi del problema e degli aspetti di concorrenza
  - [ ] Descrizione dell'architettura e delle scelte di sincronizzazione
  - [ ] Modello FSP con LTSA e proprietà verificate
  - [ ] Risultati dei test di performance (grafici Speedup ed Efficienza)
  - [ ] Verifica del micro-modello con Java PathFinder (JPF)
- [ ] **Pacchetto di Consegna**: Cartella compressa `Assignment-02.zip` contenente `src` e `doc`.
