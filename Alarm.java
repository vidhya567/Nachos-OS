package nachos.threads;

import nachos.machine.*;
import java.util.PriorityQueue;
/**
 * Uses the hardware timer to provide preemption, and to allow threads to sleep
 * until a certain time.
 */
public class Alarm {
	/**
	 * Allocate a new Alarm. Set the machine's timer interrupt handler to this
	 * alarm's callback.
	 * 
	 * <p>
	 * <b>Note</b>: Nachos will not function correctly with more than one alarm.
	 */
	public Alarm() {
		Machine.timer().setInterruptHandler(new Runnable() {
			public void run() {
				timerInterrupt();
			}
		});
	}

	/**
	 * The timer interrupt handler. This is called by the machine's timer
	 * periodically (approximately every 500 clock ticks). Causes the current
	 * thread to yield, forcing a context switch if there is another thread that
	 * should be run.
	 */
	public void timerInterrupt() {
		//KThread.currentThread().yield();
		while (!threadsSleepingQueue.isEmpty()) {
			if (threadsSleepingQueue.peek().time <= Machine.timer().getTime()) {
				threadsSleepingQueue.peek().thread.ready();
				threadsSleepingQueue.poll();
			} else
				break;
		}
		KThread.yield();
	}

	/**
	 * Put the current thread to sleep for at least <i>x</i> ticks, waking it up
	 * in the timer interrupt handler. The thread must be woken up (placed in
	 * the scheduler ready set) during the first timer interrupt where
	 * 
	 * <p>
	 * <blockquote> (current time) >= (WaitUntil called time)+(x) </blockquote>
	 * 
	 * @param x the minimum number of clock ticks to wait.
	 * 
	 * @see nachos.machine.Timer#getTime()
	 */
	public void waitUntil(long x) {
		long wakeTime = Machine.timer().getTime() + x;
		boolean origState = Machine.interrupt().disable();
		waiting_thread toAlarm = new waiting_thread(wakeTime, KThread.currentThread());
		threadsSleepingQueue.offer(toAlarm);
		KThread.sleep();
		Machine.interrupt().restore(origState);
	}

    private class waiting_thread implements Comparable<waiting_thread> {

		public KThread thread;
		public long time;

		public waiting_thread(long time,KThread thread ) {
			this.thread = thread;
			this.time = time;
		}

		@Override
		public int compareTo(waiting_thread w_thread) {
			return (new Long(time)).compareTo(w_thread.time);
		}

	}

	PriorityQueue<waiting_thread> threadsSleepingQueue = new PriorityQueue<waiting_thread>();
}
