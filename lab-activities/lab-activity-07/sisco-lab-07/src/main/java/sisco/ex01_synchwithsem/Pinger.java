package sisco.ex01_synchwithsem;

import java.util.concurrent.Semaphore;

public class Pinger extends ActiveComponent {

	private final Semaphore pingDone, pongDone;
	
	public Pinger(Semaphore pingDone, Semaphore pongDone) {
		super("Pinger");
		this.pingDone = pingDone;
		this.pongDone = pongDone;
	}	
	
	public void run() {
		while (true) {
			try {
				pingDone.acquire();
				println("ping");
				sleep(1000);
				pongDone.release();
			} catch (Exception ex) {};
		}
	}
}