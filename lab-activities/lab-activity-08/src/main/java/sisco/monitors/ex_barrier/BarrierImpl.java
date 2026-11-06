package sisco.monitors.ex_barrier;

public class BarrierImpl implements Barrier {
    
    private int nThreads;
    private int nArrived;
    
    public BarrierImpl(int nParticipants) {
        this.nThreads = nParticipants;
        this.nArrived = 0;
    }

    @Override
    public void hitAndWaitAll() throws InterruptedException {
        synchronized (this) {
            nArrived++;
            while (nArrived < nThreads) {
                wait();
            }
            notifyAll();
        }
    }
}

