package sisco.ex01_synchwithsem;

public abstract class ActiveComponent extends Thread {

	protected ActiveComponent(String name){
		super(name);
	}

	protected void print(String msg){
		synchronized (System.out){
			System.out.print(msg);
		}
	}

	protected void println(String msg){
		synchronized (System.out){
			System.out.println(msg);
		}
	}

}
