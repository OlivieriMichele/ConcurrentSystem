# Piano di Lavoro Pragmatico & Snello — Assignment #02
**Corso:** Sistemi Concorrenti (Laurea Magistrale ISI - UNIBO Cesena)  
**Docente:** Prof. Alessandro Ricci  
**Target:** Chiusura completa in **3–4 giorni effettivi** con il massimo voto e zero attriti.

---

## 1. Filosofia: Massima Efficienza, Zero Burocrazia

In un corso da **6 CFU con 3 assignment** (in cui il terzo, focalizzato su modelli asincroni/distribuiti come Akka/Vert.x, sarà tipicamente il più impegnativo del corso), spendere 10-12 giorni su questo singolo assignment con cerimonie da team enterprise (decine di branch, Pull Request bloccanti, mock interview formali da un'ora, suite di benchmark multipiattaforma) è un dispendio inutile di energie.

Il Prof. Ricci valuta:
1. **La pulizia concettuale del codice concorrente** (assenza di data race, gestione pulita di `wait`/`notifyAll` nei monitor, corretto dimensionamento dei thread pool).
2. **La correttezza del Monitor custom** e del pattern a barriera.
3. **La specifica formale FSP** (con assenza di deadlock in LTSA) e la verifica minimale con **JPF**.
4. **Una relazione sintetica, precisa e chirurgica** (non un trattato di 30 pagine).

Non viene valutato il workflow Git né la complessità del processo.

---

## 2. Regole di Workflow Snello

* **Git minimale:** 
  * Un solo branch principale (`main`) o al massimo un branch condiviso `dev`.
  * Nessuna Pull Request bloccante o code review formale.
  * Coordinamento asincrono informale (WhatsApp/Telegram): ci si avvisa prima di pushare se si tocca un file condiviso.
* **Riutilizzo al 100% del codice del Prof:**
  * Il package `sisco.ass02.n_body_seq` fornisce già la fisica dei corpi (`Body`), i vettori (`P2d`, `V2d`), le costanti gravitazionali e una GUI funzionante (`SimulationView`, `SimulationMainWithGUI`).
  * **Non si riscrive nulla della fisica o del motore da zero**: si adatta, si incapsula e si parallelizza ciò che è già presente.

---

## 3. Divisione Pragmatica dei Compiti (Zero Dipendenze)

Il lavoro è separato in due blocchi indipendenti in modo che entrambi i membri possano partire immediatamente in autonomia senza bloccarsi a vicenda:

| Membro | Compiti Assegnati (Indipendenti) | Output Concreto | Tempo Stimato |
| :--- | :--- | :--- | :--- |
| **Membro 1** | **1. Step 1 (Thread + Monitor):**<br>• Scrive la classe `CyclicBarrierMonitor` (con `wait()`, `notifyAll()`, e generation counter).<br>• Scrive i `WorkerThread` con partizionamento statico per indici.<br>**2. Verifica JPF:**<br>• Crea una classe di test JPF minimale (~20 righe) con 2 thread e la barriera, verificando l'assenza di deadlock. | `CyclicBarrierMonitor.java`<br>`SimulationWorker.java`<br>`TestJPFBarrier.java` | ~1.5 giorni |
| **Membro 2** | **1. Step 1 (Executor Framework):**<br>• Riadatta il ciclo di simulazione usando `ExecutorService` (`newFixedThreadPool`) e sottomettendo i task con `invokeAll()`.<br>**2. Benchmark di Scalabilità:**<br>• Scrive una classe `BenchmarkMain` ed esegue i test sul proprio PC (il più performante dei due) registrando i tempi `Seq` vs `Threads` vs `Executors` su 1, 2, 4, 8 core. | `NBodyExecutorSim.java`<br>`BenchmarkMain.java`<br>Tabella/Grafico tempi e speedup | ~1.5 giorni |
| **Insieme / Chi finisce prima** | **Step 2 (GUI & Controller):**<br>• Prende `SimulationMainWithGUI` e `SimulationView` del prof.<br>• Aggiunge un pannello con 4 bottoni (`Start`, `Stop`, `Suspend`, `Step`).<br>• Scrive un piccolo Monitor `SimulationController` con lock/condition per gestire gli stati (`RUNNING`, `PAUSED`, `STOPPED`, `STEP`). | `SimulationController.java`<br>`SimulationGUI.java` | ~1 giorno |

---

## 4. Specifiche Tecniche Chirurgiche

### 4.1 Step 1 - Multithreading con Monitor (Membro 1)
* **Monitor `CyclicBarrierMonitor`:**
  * Implementato con `synchronized`, `wait()`, `notifyAll()`.
  * Utilizza una variabile `generation` per evitare risvegli spuri e consentire il riutilizzo ciclico sicuro nei passi temporali successivi:
    ```java
    public synchronized void await() throws InterruptedException {
        int myGen = this.generation;
        arrived++;
        if (arrived == totalParticipants) {
            arrived = 0;
            generation++;
            notifyAll();
        } else {
            while (myGen == this.generation) {
                wait();
            }
        }
    }
    ```
* **Worker Thread:**
  * Creati una sola volta all'inizio (persistenti per tutti gli $M$ step).
  * Ricevono `startIndex` e `endIndex` per iterare solo sulla propria fetta di corpi.
  * Ciclo: Calcolo Forze $\rightarrow$ `barrier1.await()` $\rightarrow$ Aggiornamento Posizioni $\rightarrow$ `barrier2.await()`.

### 4.2 Step 1 - Task con Executor (Membro 2)
* Creazione di `ExecutorService executor = Executors.newFixedThreadPool(nWorkers)`.
* Ad ogni step:
  1. `executor.invokeAll(forceTasks)` $\rightarrow$ Barriera implicita 1.
  2. `executor.invokeAll(posTasks)` $\rightarrow$ Barriera implicita 2.
* Evitare di ricreare le liste di task da zero ad ogni iterazione: pre-istanziare i task e ri-sottomettere gli stessi `Callable`/`Runnable`.

### 4.3 Verifica JPF (Membro 1)
* File di test compatto (`TestJPFBarrier.java`): 2 thread che eseguono 2 iterazioni su una barriera a 2 partecipanti con asserzione finale.
* JPF deve verificare semplicemente l'assenza di deadlock e race condition sul monitor. L'esecuzione deve completarsi in pochi secondi.

### 4.4 Benchmark e Performance (Membro 2)
* Esecuzione su **una sola macchina** con CPU multicore (es. Apple Silicon o Intel recente):
  * $N = 5000$ corpi, $M = 100$ step.
  * Core: $1, 2, 4, 8$ worker.
* Output: un file `.csv` o direttamente la tabella dei tempi medi con calcolo di:
  $$\text{Speedup } S(p) = \frac{T_1}{T_p}, \quad \text{Efficienza } E(p) = \frac{S(p)}{p}$$

### 4.5 Step 2 - GUI con Monitor di Stato (Insieme)
* Si riutilizza `SimulationView` fornita dal prof.
* Si aggiunge un pannello pulsanti in testa o in coda al frame (`FlowLayout` con JButton `Start`, `Suspend/Resume`, `Step`, `Stop`).
* Il `SimulationController` (Monitor) espone:
  * `start()`, `pause()`, `resume()`, `step()`, `stop()`.
  * `checkAndWait()` richiamato dal thread coordinatore ad ogni step: se è in stato `PAUSED` e non è stato richiesto uno `step`, si mette in `wait()`.
* **Rendering thread-safe:** Inviare alla View una copia leggera delle posizioni via `SwingUtilities.invokeLater()`, così l'EDT non blocca la simulazione e non si verificano eccezioni di concorrenza.

---

## 5. Struttura Chirurgica della Relazione (`doc/`)

Il README richiede esplicitamente *"a brief analysis"* e *"a brief description"*. Puntiamo a **6–8 pagine complessive**, dense e prive di riempitivi:

1. **Analisi del Problema e Concorrenza (1 pagina):**
   * Perché non servono lock sui singoli corpi (fasi disgiunte di lettura/scrittura).
   * Perché servono due barriere per step (lettura globale posizioni al tempo $t$ vs scrittura locale posizioni al tempo $t+dt$).
2. **Architettura e FSP (2 pagine):**
   * Diagramma essenziale delle classi (o elenco conciso dei ruoli).
   * Specifica FSP del ciclo di simulazione e del monitor a barriera (circa 30–40 righe di FSP).
   * Screenshot dell'output di LTSA che attesta l'assenza di deadlock.
3. **Verifica con JPF (1 pagina):**
   * Spiegazione della riduzione di scala necessaria per evitare lo *state space explosion*.
   * Codice del micro-test verificato.
   * Output testuale di JPF (*"no errors detected, elapsed time: Xs"*).
4. **Valutazione delle Performance (1.5 – 2 pagine):**
   * Tabella comparativa: Sequenziale vs Threads vs Executors (su 1, 2, 4, 8 thread).
   * Grafico di Speedup ed Efficienza.
   * Breve commento tecnico (legge di Amdahl, impatto dell'overhead di sottomissione task negli Executors rispetto ai thread persistenti).
5. **Architettura GUI dello Step 2 (1 pagina):**
   * Descrizione del monitor `SimulationController`.
   * Disaccoppiamento tra il motore di simulazione e l'Event Dispatch Thread (EDT) di Swing tramite snapshot asincroni.

---

## 6. Allineamento Pre-Esame (30 Minuti, Non un Processo)

Il giorno prima dell'orale o della consegna, fate una chiamata Discord/Teams di **30 minuti** con condivisione schermo:
* **Membro 1** condivide lo schermo e illustra a Membro 2:
  * Come ha scritto `CyclicBarrierMonitor` (perché il generation counter, perché `notifyAll()`).
  * Il micro-test JPF e il modello FSP.
* **Membro 2** condivide lo schermo e illustra a Membro 1:
  * Come ha strutturato l'`ExecutorService` e la sottomissione con `invokeAll()`.
  * La tabella/grafico dei benchmark.
* **Entrambi** provano insieme la GUI verificando la reattività dei 4 bottoni.

Questo allineamento è più che sufficiente per rispondere con sicurezza e padronanza a qualsiasi domanda del docente all'orale.

---

## 7. Tabella di Marcia (3–4 Giorni)

```
Giorno 1:
├── Membro 1: Scrittura CyclicBarrierMonitor + WorkerThread
└── Membro 2: Scrittura versione ExecutorService con invokeAll()

Giorno 2:
├── Membro 1: Test JPF su barriera + Modello FSP per LTSA
└── Membro 2: Esecuzione benchmark prestazioni (Seq vs Thread vs Executor)

Giorno 3:
├── Insieme: Integrazione GUI (Controller con Start/Stop/Suspend/Step)
└── Insieme: Stesura prime sezioni della relazione doc/

Giorno 4:
├── Insieme: Finalizzazione relazione (grafici + screenshot LTSA/JPF)
├── Allineamento 30 minuti pre-esame a schermo condiviso
└── Creazione archivio finale Assignment-02.zip
```
