package nachos.userprog;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.ListIterator;
import java.util.NoSuchElementException;
import java.util.TreeMap;
import java.util.TreeSet;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;

/**
 * A kernel that can support multiple user processes.
 */
public class UserKernel extends ThreadedKernel {
	/**
	 * Allocate a new user kernel.
	 */
	public UserKernel() {
		super();
	}

	/**
	 * Initialize this kernel. Creates a synchronized console and sets the
	 * processor's exception handler.
	 */
	public void initialize(String[] args) {
		super.initialize(args);

		console = new SynchConsole(Machine.console());

		fManager = new FileManager();
		pManager = new ProcessManager();

		Machine.processor().setExceptionHandler(new Runnable() {
			public void run() {
				exceptionHandler();
			}
		});
		
		initialiseFreePages();
	}

	/**
	 * Test the console device.
	 */
	public void selfTest() {
		super.selfTest();
		System.out.println("will be echoed until q is typed.");
		
		char c;

		do {
			c = (char) console.readByte(true);
			console.writeByte(c);
		} while (c != 'q');

		System.out.println("");
	}


	/**
	 * Returns the current process.
	 * 
	 * @return the current process, or <tt>null</tt> if no process is current.
	 */
	public static UserProcess currentProcess() {
		if (!(KThread.currentThread() instanceof UThread))
			return null;

		return ((UThread) KThread.currentThread()).process;
	}

	/**
	 * The exception handler. This handler is called by the processor whenever a
	 * user instruction causes a processor exception.
	 * 
	 * <p>
	 * When the exception handler is invoked, interrupts are enabled, and the
	 * processor's cause register contains an integer identifying the cause of
	 * the exception (see the <tt>exceptionZZZ</tt> constants in the
	 * <tt>Processor</tt> class). If the exception involves a bad virtual
	 * address (e.g. page fault, TLB miss, read-only, bus error, or address
	 * error), the processor's BadVAddr register identifies the virtual address
	 * that caused the exception.
	 */
	public void exceptionHandler() {
		Lib.assertTrue(KThread.currentThread() instanceof UThread);

		UserProcess p = ((UThread) KThread.currentThread()).process;
		int c = Machine.processor().readRegister(Processor.regCause);
		p.handleException(c);
	}

	/**
	 * Start running user programs, by creating a process and running a shell
	 * program in it. The name of the shell program it must run is returned by
	 * <tt>Machine.getShellProgramName()</tt>.
	 * 
	 * @see nachos.machine.Machine#getShellProgramName
	 */
	public void run() {
		super.run();

		UserProcess process = UserProcess.newUserProcess();

		String shellProgram = Machine.getShellProgramName();
		Lib.assertTrue(process.execute(shellProgram, new String[] {}));

		KThread.currentThread().finish();
	}

	/**
	 * Terminate this kernel. Never returns.
	 */
	public void terminate() {
		super.terminate();
	}
	
	public class FileManager {
		public FileManager() {
			openList = new HashMap<String, FNode>();
		}
		
		private class FNode {
			FNode(String file_name) {
				this.fname = file_name;
				this.count = 1;
				this.unlink = false;
			}
			
			String fname;
			int count;
			boolean unlink;
		}
	
		public void openFile(String file_name) {
			FNode fNode = openList.get(file_name);
			if(fNode != null) {
				fNode.count++;
			}
			else {
				fNode = new FNode(file_name);
				openList.put(file_name, fNode);
			}
		}
		public boolean decrementCount(String file_name) {
			FNode fNode = openList.get(file_name);
			if(fNode == null) return false;
			fNode.count--;
			if(fNode.count == 0) {
				openList.remove(file_name);
				if(fNode.unlink == true) {
					return fileSystem.remove(file_name);
				}
			}
			return true;
		}
		
		public boolean unlink(String file_name) {
			FNode fNode = (FNode)openList.get(file_name);
			if(fNode == null) {
				return fileSystem.remove(file_name);
			}
			fNode.unlink = true;
			return true;
		}
		
		public boolean isUnlink(String file_name) {
			FNode fNode = openList.get(file_name);
			if(fNode == null) return false;
			return fNode.unlink;
		}
		
		private HashMap<String, FNode> openList;
	}	

	public static UserKernel getKernel() {
		if(kernel instanceof UserKernel) return (UserKernel)kernel;
		return null;
	}
	
		private class PBlock implements Comparable<PBlock>{
			public int start;
			public int num_page;
			
			public PBlock(int s,int n){
				start = s;
				num_page = n;
			}
			
			public int compareTo(PBlock other){
				final int EQUAL_t = 0;
				final int AFTER_t = 1;
				final int BEFORE_t = -1;
				if(num_page > other.num_page)
					return AFTER_t;
				if(num_page == other.num_page)
					return EQUAL_t;				
				if(num_page < other.num_page)
					return BEFORE_t;
				
				Lib.assertNotReached("page block compare error");
				return EQUAL_t;
			}
		}
		private TreeSet<PBlock> freeBlocks = new TreeSet<PBlock>();         //Sorted by size
		private LinkedList<PBlock> freePool = new LinkedList<PBlock>(); //Sorted by position
		private int sumFreePages = 0;
		private Semaphore accessFreePage = null;
		
		private void initialiseFreePages(){
			accessFreePage = new Semaphore(1);
			
			accessFreePage.P();
			
			sumFreePages = Machine.processor().getNumPhysPages();
			freeBlocks.clear();
			freeBlocks.add(new PBlock(0,sumFreePages));
			
			accessFreePage.V();
		}
		
		public int getSmallestFreeBlock(){
			accessFreePage.P();
			
			int res = 0;
			try{
				PBlock blk1= freeBlocks.first();
				
				if(blk1 != null){
					res = blk1.num_page;
				}
			}catch(NoSuchElementException e){
			}
			
			accessFreePage.V();
			
			return res;
		}
		
		public int getLargestFreeBlock(){
			accessFreePage.P();
			
			int res = 0;
			try{
				PBlock blk1= freeBlocks.last();
				
				if(blk1 != null){
					res = blk1.num_page;
				}
			}catch(NoSuchElementException e){
			}
			
			accessFreePage.V();
			
			return res;
		}
		public void releaseContiguousBlock(int s,int n){
			accessFreePage.P(); 
			
			PBlock r = new PBlock(s,n);
			int postNeighbourOffset = r.start + r.num_page;
			int preNeighbourOffset = r.start;
			
			sumFreePages += r.num_page;
			ListIterator<PBlock> it = freePool.listIterator();
			
			while(it.hasNext()){
				PBlock cur = it.next();
				
				if(cur.start < r.start){
					if(cur.start + cur.num_page == preNeighbourOffset){
						freeBlocks.remove(cur);
						cur.num_page += r.num_page;
						freeBlocks.add(cur);
						
						accessFreePage.V();
						return;
					}
				}else{
					if(cur.start == postNeighbourOffset){
						freeBlocks.remove(cur);
						cur.num_page += r.num_page;
						cur.start = r.start;
						freeBlocks.add(cur);
						
						accessFreePage.V();
						return;
					}
					
					it.previous();
					break;
				}
			}
			
			it.add(r);
			freeBlocks.add(r);
			
			accessFreePage.V();
		}
		
		public int allocateContiguousBlock(int numPages){
			accessFreePage.P();
			
			int result = -1;
			Iterator<PBlock> it = freeBlocks.iterator();
			
			while(it.hasNext()){
				PBlock cur = it.next();
				
				if(cur.num_page >= numPages){
					result = cur.start;
					
					freeBlocks.remove(cur);
					freePool.remove(cur);
			
					sumFreePages -= cur.num_page;
					cur.start += numPages;
					cur.num_page -= numPages;
					
					if(cur.num_page > 0){
						accessFreePage.V();
						releaseContiguousBlock(cur.start,cur.num_page);
						accessFreePage.P();
					}
					
				}
			}
			
			accessFreePage.V(); 
					
			return result;
		}
		
		public int getNumOfFreePages(){
			return sumFreePages;
		}
		
		public int allocate(){
			return allocateContiguousBlock(1);
		}
		
		public void release(int start){
			releaseContiguousBlock(start,1);
		}
		
		public TranslationEntry[] allocateTable(int numPages){
			TranslationEntry[] pageTable = new TranslationEntry[numPages];
			int curVirtualPage = 0;
			
			while(curVirtualPage < numPages){
				int neededPages = numPages - curVirtualPage;
				int start = -1;
				int largestBlock = getLargestFreeBlock();
				
				if(largestBlock <= 0){
					break;
				}else if(largestBlock < neededPages){
					neededPages = largestBlock;
				}

				start = allocateContiguousBlock(neededPages);
					
				if(start >= 0){ 
					for(int i=0;i<neededPages;i++){
						pageTable[i+curVirtualPage] = new TranslationEntry(i + curVirtualPage, i + start, true, false, false, false);
					}
					curVirtualPage += neededPages;
				}
			}
				
			if(pageTable[pageTable.length-1] == null){
				releaseTable(pageTable);
				
				pageTable = null;
			}
			
			return pageTable;
		}
		
		public void releaseTable(TranslationEntry[] pTable){
			PBlock blk = null;
			for(int curVirtPage = 0; curVirtPage < pTable.length; curVirtPage++){
				TranslationEntry transentry = pTable[curVirtPage];
				if(transentry!=null && transentry.valid){
					if(blk!=null){
						if((blk.start + blk.num_page) == transentry.ppn){
							blk.num_page += 1;
						}else{
							releaseContiguousBlock(blk.start,blk.num_page);
							blk.num_page = 1;
							blk.start = transentry.ppn;
						}
					}else{
					  blk = new PBlock(transentry.ppn, 1);
					}
				}
			}
			if(blk!=null){
				releaseContiguousBlock(blk.start,blk.num_page);
			}
		}
		public class ProcessManager {
			public int newProcess(UserProcess proc, int parent) {
				ProcessNode newProcNode = new ProcessNode(proc, parent, nextProcessID);
				procList.put(newProcNode.pid, newProcNode);
				nextProcessID++;
				return newProcNode.pid;
			}

			
			public boolean exists(int pID) {
				return procList.containsKey(pID);
			}

			
			public void parentChange(int childPID, int parentPID) {
				procList.get(childPID).parent = parentPID;
			}
			
			public UserProcess getProcess(int pID) {
				return procList.get(pID).process;
			}

			public boolean checkNoChildren(int parentID) {
				Iterator iterator = procList.keySet().iterator();
				ProcessNode procNode;
				while(iterator.hasNext()) {
					procNode = procList.get(iterator.next());
					if(procNode.parent == parentID) {
						return false;
					}
				}
				return true;
			}
			public ProcessManager() {
				procList = new TreeMap<Integer, ProcessNode>();
			}

			public void remParent(int PID_par) {
				Iterator iter = procList.keySet().iterator();
				ProcessNode procNode;
				while(iter.hasNext()) {
					procNode = procList.get(iter.next());
					if(procNode.parent == PID_par) {
						parentChange(procNode.pid, -1);
					}
				}
			}
			public int getParent(int child_PID) {
				return procList.get(child_PID).parent;
			}
			
			public void setReturnCode(int pid_return, int Code_return) {
				procList.get(pid_return).exitStatus = Code_return;
			}
			
			public boolean checkforRunning(int processID) {
				return procList.get(processID).running;
			}
			
			public void setFinish(int processID) {
				procList.get(processID).running = false;
			}
			
			public int getReturnCode(int pID) {
				ProcessNode proc = procList.get(pID);
				return proc.exitStatus;
			}
			
			public boolean checkError(int PID) {
				return procList.get(PID).error;
			}
			
			public void setError(int PID) {
				procList.get(PID).error = true;
			}
			
			public boolean isLast(int pID) {
				Iterator iterate = procList.keySet().iterator();
				ProcessNode procNode;
				int count = 0;
				while(iterate.hasNext()) {
					procNode = procList.get(iterate.next());
					if(procNode.pid != pID && procNode.running) {
						count++;
					}
				}
				return (count == 0);
			}
			
			private class ProcessNode {
				public ProcessNode(UserProcess pro1, int par1, int pid1) {
					this.pid = pid1;
					this.parent = par1;
					this.process = pro1;
					this.running = true;
					this.joined = false;
					this.error = false;
				}
				
				int pid;
				int parent;
				int exitStatus;
				boolean joined;
				boolean running;
				boolean error;
				UserProcess process;
			}
			
			private int nextProcessID = 0;
			private TreeMap<Integer, ProcessNode> procList;
		}


		public ProcessManager pManager;



	public FileManager fManager;


	/** Globally accessible reference to the synchronized console. */
	public static SynchConsole console;

	// dummy variables to make javac smarter
	private static Coff dummy1 = null;
}
