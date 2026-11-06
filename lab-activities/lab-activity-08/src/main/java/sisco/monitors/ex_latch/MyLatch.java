package sisco.monitors.ex_latch;

public class MyLatch implements Latch{

    private int count; 

    public MyLatch(int nThreads) {
        this.count = nThreads;
    }

    @Override
    synchronized public void countDown() {
        if (this.count > 0) {
            this.count--;
            if (this.count == 0) {
                notifyAll();
            }
        }
    }

    @Override
    synchronized public void await() throws InterruptedException {
        while (this.count > 0) {
            wait();
        }
    }
    
}
