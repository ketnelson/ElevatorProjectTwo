package Scheduler;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Queue;

import core.Constants;
import core.ElevatorMessage;
import core.EventListener;
import core.EventNotifier;

public class Scheduler {

	public static final String ADDRESS = "";

	EventListener listener;
	EventNotifier elevatorNotifier;

	private Calendar cal;
	private SimpleDateFormat time;
	
	LinkedList<ArrayList<Integer>> queue = new LinkedList<ArrayList<Integer>>();
	HashMap<Integer, Integer> reqUp = new HashMap<Integer, Integer>();
	HashMap<Integer, Integer> reqDown = new HashMap<Integer, Integer>();
	 
	HashMap<Integer, Integer> processedUp = new HashMap<Integer, Integer>();
	HashMap<Integer, Integer> processedDown = new HashMap<Integer, Integer>();
	
	int processing = 0;

	public Scheduler() {
		listener = new EventListener(Constants.SCHED_PORT, "SCHEDULER LISTENER");
		elevatorNotifier = new EventNotifier(Constants.ELEV_PORT, "SCHEDULER ELEVATOR NOTIFIER");
		time = new SimpleDateFormat("HH:mm:ss.SSS");
	}

	public void startListen() throws InterruptedException {
		cal = Calendar.getInstance();
		System.out.println("SCHEDULER: Starting listener...");

		for (;;) {
			ElevatorMessage msg = listener.waitForNotification();
			switch (msg.getType()) {
			case EMPTY:
				System.out.println("\nEMPTY ELEVATOR" + msg);
				
				synchronized (this) {
					processing -= 1;

					// let the waiting threads know that we are free to process another thread
					notifyAll();
					
				}
				break;
			case REQ:
				System.out.println("\nNEW ELEVATOR REQUEST" + msg);
				int from = msg.getId();
				int to = msg.getData().get(1);
				Constants.DIR direction = Constants.DIR.fromCode(msg.getData().get(0));
				
				synchronized(this) {
					ArrayList<Integer> gg = new ArrayList<Integer>();
					gg.add(from);
					gg.add(to);
					gg.add(direction.getCode());
					queue.add(gg);
					notifyAll();
				}

				break;
			case ARRIVAL:
				int car = msg.getId();
				Constants.DIR dir = Constants.DIR.fromCode(msg.getData().get(0));
				int currFloor = msg.getData().get(1);

				System.out.println("\nCAR " + car + " ON FLOOR " + currFloor + " IN DIR " + dir);
				EventNotifier notif = new EventNotifier(msg.PORT, "RETURN MSG");
				boolean stop = false;
				int floor = -1;
				
				
				
				if (dir == Constants.DIR.UP) {
					
					if(reqUp.containsKey(currFloor)) {
						stop = true;
						floor = reqUp.get(currFloor);
						reqUp.remove(currFloor);
					}
					
					if(!stop) {
						for(int i=0; i<queue.size(); i++ ) {
							ArrayList<Integer> curr = queue.get(i);
							if ((curr.get(0) == currFloor) && (curr.get(2) == 1)) {
								synchronized(this) {
									queue.remove(i);
								}
								stop = true;
								floor = curr.get(1);
								break;
							}
						}
					}
				}else {
					
					if(reqDown.containsKey(currFloor)) {
						stop = true;
						floor = reqDown.get(currFloor);
						reqDown.remove(currFloor);
					}
					
					if(!stop) {
						for(int i=0; i<queue.size(); i++ ) {
							ArrayList<Integer> curr = queue.get(i);
							if ((curr.get(0) == currFloor) && (curr.get(2) == 2)) {
								synchronized(this) {
									queue.remove(i);
								}
								stop = true;
								floor = curr.get(1);
								break;
							}
						}
					}
				}
					
					
					/*
					for(int i=0; i<queue.size(); i++ ) {
						ArrayList<Integer> curr = queue.get(i);
						if ((curr.get(0) == currFloor) && (curr.get(2) == 1)) {
							synchronized(this) {
								queue.remove(i);
							}
							stop = true;
							floor = curr.get(1);
						}
					}
					
					if(!stop && reqUp.containsKey(currFloor)) {
						stop = true;
						floor = reqUp.remove(currFloor);
					}
					*/
					
				

				ElevatorMessage newMsg;
				if (stop && floor != -1) {
					newMsg = new ElevatorMessage(ElevatorMessage.MessageType.STOP, car, floor);
				} else {
					newMsg = new ElevatorMessage(ElevatorMessage.MessageType.CONT, car, dir.getCode());
				}
				notif.sendMessage(newMsg, "");
				break;
			default:
				break;
			}
		}
	}

	public synchronized void dequeue() throws InterruptedException {
		// THIS ACTS AS A CONSUMER
		for (;;) {
			while(queue.isEmpty() || processing >= Constants.NUM_CARS) {
				wait();
			}
			
			
			
			// now we are processing one request, so one car is occupied
			processing += 1;
			
			ArrayList<Integer> msg = queue.removeFirst();
			int from = msg.get(0);
			Constants.DIR dir = Constants.DIR.fromCode(msg.get(2));
			int to = msg.get(1);

			if (dir == Constants.DIR.UP) {
				synchronized (this) {
					reqUp.put(from, to);
				}
			} else {
				synchronized (this) {
					reqDown.put(from, to);
				}
			}
			
			ElevatorMessage newMsg = new ElevatorMessage(ElevatorMessage.MessageType.REQ, from, dir.getCode());
			elevatorNotifier.sendMessage(newMsg, Constants.ELEV_ADDR);
		}

	}

	public void start() {
		
		//Logger.clearAllLogFiles();
		Scheduler s = this;

		// producer
		Thread t1 = new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					s.startListen();
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		});
		Thread t2 = new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					s.dequeue();
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		});


		t2.start();

		t1.start();
	}

	public void sendReq() {
		/*
		 * System.out.println("Sending Message"); ElevatorMessage msg = new
		 * ElevatorMessage(ElevatorMessage.MessageType.REQ, 4,
		 * Constants.DIR.UP.getCode()); elevatorNotifier.sendMessage(msg, "");
		 * reqUp.put(4, 6); System.out.println("msg: " + msg.toString()); reqUp.put(2,
		 * 7);
		 */

	}

	public static void main(String[] args) {
		Scheduler s = new Scheduler();
		s.start();

	}
}
