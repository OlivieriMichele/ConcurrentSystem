package sisco.ex01_synchwithsem;

import java.util.concurrent.Semaphore;

public class Ponger extends ActiveComponent {

	private final Semaphore pingDone, pongDone;
	
	public Ponger(Semaphore pingDone, Semaphore pongDone) {
		super("Ponger");
		this.pingDone = pingDone;
		this.pongDone = pongDone;
	}	
	
	public void run() {
		while (true) {
			try {
				pongDone.acquire();
				println("pong");
				sleep(1000);
				pingDone.release();
			} catch (Exception ex) {}
				
		}
	}
}