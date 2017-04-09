package nachos.threads;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;

import nachos.machine.Machine;

/**
 * A <i>communicator</i> allows threads to synchronously exchange 32-bit
 * messages. Multiple threads can be waiting to <i>speak</i>, and multiple
 * threads can be waiting to <i>listen</i>. But there should never be a time
 * when both a speaker and a listener are waiting, because the two threads can
 * be paired off at this point.
 */
public class Communicator {

	private  Lock SL_lock;
	private Condition2 speaker_cond;
	private Condition2 listener_cond;
	private LinkedList<Converse> waiting_Speaker;
	private LinkedList<Converse> waiting_Listener;



	private class Converse {
		public int word;
		//listener constructor
		public Converse() {
			word = 0;
		}
		//speaker constructor
		public Converse(int x) {
			this.word = x;
		}
	}

	/**
	 * Allocate a new communicator.
	 */
	public Communicator() {
		SL_lock          = new Lock();
		speaker_cond     = new Condition2(SL_lock);
		listener_cond    = new Condition2(SL_lock);
		waiting_Speaker  = new LinkedList<Converse>();
		waiting_Listener = new LinkedList<Converse>();
	}

	/**
	 * Wait for a thread to listen through this communicator, and then transfer
	 * <i>word</i> to the listener.
	 * 
	 * <p>
	 * Does not return until this thread is paired up with a listening thread.
	 * Exactly one listener should receive <i>word</i>.
	 * 
	 * @param word
	 *            the integer to transfer.
	 */
	public void speak(int word) {
	
		SL_lock.acquire();
		
		if (waiting_Listener.size() != 0) {
			Converse listen = waiting_Listener.removeFirst();
			listen.word = word;
			listener_cond.wake();
		} else {
			waiting_Speaker.add(new Converse(word));
			speaker_cond.sleep();
		}
		SL_lock.release();
		
	}

	/**
	 * Wait for a thread to speak through this communicator, and then return the
	 * <i>word</i> that thread passed to <tt>speak()</tt>.
	 * 
	 * @return the integer transferred.
	 */
	public int listen() {

		int data = 0;
		SL_lock.acquire();
		if (waiting_Speaker.size() != 0) {
			Converse speaker = waiting_Speaker.removeFirst();
			data = speaker.word;
			speaker_cond.wake();
		} else {
			Converse newListener = new Converse();
			waiting_Listener.add(newListener);
			listener_cond.sleep();
			data = newListener.word;
		}
		SL_lock.release();
		return data;
	}  

    
}
