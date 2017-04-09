package nachos.threads;

import nachos.machine.*;
import java.util.LinkedList; 
import java.util.Iterator;  

import java.util.TreeSet;
import java.util.HashSet;
import java.util.Iterator;

/**
 * A scheduler that chooses threads based on their priorities.
 *
 * <p>
 * A priority scheduler associates a priority with each thread. The next thread
 * to be dequeued is always a thread with priority no less than any other
 * waiting thread's priority. Like a round-robin scheduler, the thread that is
 * dequeued is, among all the threads of the same (highest) priority, the
 * thread that has been waiting longest.
 *
 * <p>
 * Essentially, a priority scheduler gives access in a round-robin fassion to
 * all the highest-priority threads, and ignores all other threads. This has
 * the potential to
 * starve a thread if there's always a thread waiting with higher priority.
 *
 * <p>
 * A priority scheduler must partially solve the priority inversion problem; in
 * particular, priority must be donated through locks, and through joins.
 */
public class PriorityScheduler extends Scheduler {
	/**
	 * Allocate a new priority scheduler.
	 */
	public PriorityScheduler() {
	}

	/**
	 * Allocate a new priority thread queue.
	 *
	 * @param	transferPriority	<tt>true</tt> if this queue should
	 *					transfer priority from waiting threads
	 *					to the owning thread.
	 * @return	a new priority thread queue.
	 */
	public ThreadQueue newThreadQueue(boolean transferPriority) {
		//System.out.print("ThreadQueue.newThreadQueue: " + transferPriority + "\n");  // debug
		return new PriorityQueue(transferPriority);
	}

	public int getPriority(KThread thread) {
		Lib.assertTrue(Machine.interrupt().disabled());

		return getThreadState(thread).getPriority();
	}

	public int getEffectivePriority(KThread thread) {
		Lib.assertTrue(Machine.interrupt().disabled());

		return getThreadState(thread).getEffectivePriority();
	}

	public void setPriority(KThread td, int priority) {
		Lib.assertTrue(Machine.interrupt().disabled());

		Lib.assertTrue(priority >= priorityMinimum &&
				priority <= priorityMaximum);

		getThreadState(td).setPriority(priority);
	}

	public boolean increasePriority() {
		boolean intStatus = Machine.interrupt().disable();

		KThread thread = KThread.currentThread();

		int priority = getPriority(thread);
		if (priority == priorityMaximum)
			return false;

		setPriority(thread, priority+1);

		Machine.interrupt().restore(intStatus);
		return true;
	}

	public boolean decreasePriority() {
		boolean intStatus = Machine.interrupt().disable();

		KThread thread = KThread.currentThread();

		int priority = getPriority(thread);
		if (priority == priorityMinimum)
			return false;

		setPriority(thread, priority-1);

		Machine.interrupt().restore(intStatus);
		return true;
	}

	/**
	 * The default priority for a new thread. Do not change this value.
	 */
	public static final int priorityDefault = 1;
	/**
	 * The minimum priority that a thread can have. Do not change this value.
	 */
	public static final int priorityMinimum = 0;
	/**
	 * The maximum priority that a thread can have. Do not change this value.
	 */
	public static final int priorityMaximum = 7;    

	/**
	 * Return the scheduling state of the specified thread.
	 *
	 * @param	thread	the thread whose scheduling state to return.
	 * @return	the scheduling state of the specified thread.
	 */
	protected ThreadState getThreadState(KThread thread) {
		if (thread.schedulingState == null)
			thread.schedulingState = new ThreadState(thread);

		return (ThreadState) thread.schedulingState;
	}

	/**
	 * A <tt>ThreadQueue</tt> that sorts threads by priority.
	 */
	protected class PriorityQueue extends ThreadQueue {
		PriorityQueue(boolean transferPriority) {
			this.transferPriority = transferPriority;
		}

		public void waitForAccess(KThread thread) {
			Lib.assertTrue(Machine.interrupt().disabled());
			getThreadState(thread).waitForAccess(this);
		}

		public void acquire(KThread thread) {
			Lib.assertTrue(Machine.interrupt().disabled());

			this.container = getThreadState(thread);
			// container exists and transfer priority true then remove
			if (this.container != null && this.transferPriority) {
				this.container.ListOfQueues.remove(this);
			}


			container.acquire(this);//acuire container
		}

		public KThread nextThread() {
			Lib.assertTrue(Machine.interrupt().disabled());

			if (waitQueue.isEmpty())
				return null;

			//container exists and transfer priority true then remove
			if (this.container != null && this.transferPriority)  
			{
				this.container.ListOfQueues.remove(this);
			}

			KThread firstThread = pickNextThread();
			if (firstThread != null) {
				waitQueue.remove(firstThread);
				getThreadState(firstThread).acquire(this);
			}

			return firstThread;
		}

		/**
		 * Return the next thread that <tt>nextThread()</tt> would return,
		 * without modifying the state of this queue.
		 *
		 * @return	the next thread that <tt>nextThread()</tt> would
		 *		return.
		 */
		protected KThread pickNextThread() {
			KThread nextThread = null, thread = null;
			int priority;

			for (Iterator<KThread> it = waitQueue.iterator(); it.hasNext();) {  
				thread = it.next(); 
				priority = getThreadState(thread).getEffectivePriority();

				if (nextThread == null || priority > getThreadState(nextThread).getEffectivePriority()) { 
					nextThread = thread;
				}
			}

			return nextThread;
		}

		public int getEffectivePriority() {


			// return minimum if false
			if (transferPriority == false) {
				return priorityMinimum;
			}

			if (priorityInheritance) {
				effectivePriority = priorityMinimum;
				int priority;
				KThread thread = null; 
				for (Iterator<KThread> it = waitQueue.iterator(); it.hasNext();) {  
					thread = it.next(); 
					priority = getThreadState(thread).getEffectivePriority();
					if ( priority > effectivePriority) { 
						effectivePriority = priority;
					}
				}
				priorityInheritance = false;
			}

			return effectivePriority;
		}

		public void setPriorityInheritance() {
			if (transferPriority == false) {
				return;
			}

			priorityInheritance = true;

			if (container != null) {
				container.setPriorityInheritance();
			}
		}

		public void print() {
			Lib.assertTrue(Machine.interrupt().disabled());
			// implement me (if you want)
			for (Iterator<KThread> it = waitQueue.iterator(); it.hasNext();) {  
				KThread currentThread = it.next(); 
				int  priority = getThreadState(currentThread).getPriority();

				System.out.print("Thread: " + currentThread 
						+ "\t  Priority: " + priority + "\n");
			}
		}

		/**
		 * <tt>true</tt> if this queue should transfer priority from waiting
		 * threads to the owning thread.
		 */
		public boolean transferPriority;

		private LinkedList<KThread> waitQueue = new LinkedList<KThread>();  //wait queue

		//This Queue's Entry in ListOfQueues 
		private ThreadState container = null;             

		private boolean priorityInheritance;//Notify priority inheritance                   

		private int effectivePriority;//store effective priority in the waitQueue. 

	}


	/**
	 * The scheduling state of a thread. This should include the thread's
	 * priority, its effective priority, any objects it owns, and the queue
	 * it's waiting for, if any.
	 *
	 * @see	nachos.threads.KThread#schedulingState
	 */
	protected class ThreadState {

		/**
		 * Allocate a new <tt>ThreadState</tt> object and associate it with the
		 * specified thread.
		 *
		 * @param	thread	the thread this state belongs to.
		 */
		public ThreadState(KThread thread) {
			this.thread = thread;

			setPriority(priorityDefault);
		}

		/**
		 * Return the priority of the associated thread.
		 *
		 * @return	the priority of the associated thread.
		 */
		public int getPriority() {
			return priority;
		}

		/**
		 * Return the effective priority of the associated thread.
		 *
		 * @return	the effective priority of the associated thread.
		 */
		public int getEffectivePriority() {

			int effective,maxP = this.priority;

			if (priorityInheritance) {
				for (Iterator<ThreadQueue> it = ListOfQueues.iterator(); it.hasNext();) {  
					PriorityQueue pQueue = (PriorityQueue)(it.next()); 
					effective = pQueue.getEffectivePriority();
					if (effective > maxP ) {
						maxP = effective;
					}
				}
			}

			return maxP;
		}

		/**
		 * Set the priority of the associated thread to the specified value.
		 *
		 * @param	priority	the new priority.
		 */
		public void setPriority(int priority) {
			if (this.priority == priority)
				return;

			this.priority = priority;

			setPriorityInheritance();
		}

		/**
		 * Called when <tt>waitForAccess(thread)</tt> (where <tt>thread</tt> is
		 * the associated thread) is invoked on the specified priority queue.
		 * The associated thread is therefore waiting for access to the
		 * resource guarded by <tt>waitQueue</tt>. This method is only called
		 * if the associated thread cannot immediately obtain access.
		 *
		 * @param	waitQueue	the queue that the associated thread is
		 *				now waiting on.
		 *
		 * @see	nachos.threads.ThreadQueue#waitForAccess
		 */
		public void waitForAccess(PriorityQueue waitQueue) {

			Lib.assertTrue(Machine.interrupt().disabled());
			Lib.assertTrue(waitQueue.waitQueue.indexOf(thread) == -1);

			waitQueue.waitQueue.add(thread);
			waitQueue.setPriorityInheritance();

			// set waitOn
			waitOn = waitQueue;

			// if the waitQueue was previously in ListOfQueues, remove it 
			// and set its container to null
			if (ListOfQueues.indexOf(waitQueue) != -1) {
				ListOfQueues.remove(waitQueue);
				waitQueue.container = null;
			}
		}

		/**
		 * Called when the associated thread has acquired access to whatever is
		 * guarded by <tt>waitQueue</tt>. This can occur either as a result of
		 * <tt>acquire(thread)</tt> being invoked on <tt>waitQueue</tt> (where
		 * <tt>thread</tt> is the associated thread), or as a result of
		 * <tt>nextThread()</tt> being invoked on <tt>waitQueue</tt>.
		 *
		 * @see	nachos.threads.ThreadQueue#acquire
		 * @see	nachos.threads.ThreadQueue#nextThread
		 */
		public void acquire(PriorityQueue waitQueue) {

			Lib.assertTrue(Machine.interrupt().disabled());

			ListOfQueues.add(waitQueue);// put to ListOfQueues

			//clear if same 
			if (waitQueue == waitOn) {
				waitOn = null;
			}


			setPriorityInheritance();//store change in effective priority
		}	

		/**set the priorityInheritance Flag*/	 
		public void setPriorityInheritance() {
			if (priorityInheritance) {
				return;
			}

			priorityInheritance = true;

			PriorityQueue pg = (PriorityQueue)waitOn;
			if (pg != null) {
				pg.setPriorityInheritance();
			}

		}

		/** The thread with which this object is associated. */	   
		protected KThread thread;

		/** The priority of the associated thread. */
		protected int priority;

		protected int effectivePriority;            

		/** Collection of PriorityQueues that signify the Locks or other
		 *  resource that this thread currently holds */
		protected LinkedList<ThreadQueue> ListOfQueues = new LinkedList<ThreadQueue>();  

		protected ThreadQueue waitOn;//priority queue failed to acuire. 

		private boolean priorityInheritance = false; //to flag priority inheritance         

	}
}
