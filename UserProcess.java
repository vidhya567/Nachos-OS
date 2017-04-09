package nachos.userprog;

import java.io.EOFException;

import nachos.machine.Coff;
import nachos.machine.CoffSection;
import nachos.machine.Kernel;
import nachos.machine.Lib;
import nachos.machine.Machine;
import nachos.machine.OpenFile;
import nachos.machine.Processor;
import nachos.machine.TranslationEntry;
import nachos.threads.KThread;
import nachos.threads.ThreadedKernel;

/**
 * Encapsulates the state of a user process that is not contained in its user
 * thread (or threads). This includes its address translation state, a file
 * table, and information about the program being executed.
 * 
 * <p>
 * This class is extended by other classes to support additional functionality
 * (such as additional syscalls).
 * 
 * @see nachos.vm.VMProcess
 * @see nachos.network.NetProcess
 */
public class UserProcess {

	/**
	 * Allocate a new process.
	 */
	public UserProcess() {

		process_id = UserKernel.getKernel().pManager.newProcess(this, -1);

		// need to create a table of open files and set up stdin and stdout
		open_table = new OpenFile[Max_files];
		open_table[0] = UserKernel.console.openForReading();
		open_table[1] = UserKernel.console.openForWriting();
		NameofFiles = new String[Max_files];

		int numPhysPages = Machine.processor().getNumPhysPages();
		pageTable = new TranslationEntry[numPhysPages];
		for (int i = 0; i < numPhysPages; i++)
			pageTable[i] = new TranslationEntry(i, i, true, false, false, false);
	}

	/**
	 * Allocate and return a new process of the correct class. The class name is
	 * specified by the <tt>nachos.conf</tt> key
	 * <tt>Kernel.processClassName</tt>.
	 * 
	 * @return a new process of the correct class.
	 */
	public static UserProcess newUserProcess() {
		return (UserProcess) Lib.constructObject(Machine.getProcessClassName());
	}

	/**
	 * Execute the specified program with the specified arguments. Attempts to
	 * load the program, and then forks a thread to run it.
	 * 
	 * @param name
	 *            the name of the file containing the executable.
	 * @param args
	 *            the arguments to pass to the executable.
	 * @return <tt>true</tt> if the program was successfully executed.
	 */
	public boolean execute(String name, String[] args) {
		if (!load(name, args))
			return false;

		// new UThread(this).setName(name).fork();
		debug_Thread = new UThread(this);
		debug_Thread.setName(name).fork();

		return true;
	}

	/**
	 * Save the state of this process in preparation for a context switch.
	 * Called by <tt>UThread.saveState()</tt>.
	 */
	public void saveState() {
	}

	/**
	 * Restore the state of this process after a context switch. Called by
	 * <tt>UThread.restoreState()</tt>.
	 */
	public void restoreState() {
		Machine.processor().setPageTable(pageTable);
	}

	/**
	 * Read a null-terminated string from this process's virtual memory. Read at
	 * most <tt>maxLength + 1</tt> bytes from the specified address, search for
	 * the null terminator, and convert it to a <tt>java.lang.String</tt>,
	 * without including the null terminator. If no null terminator is found,
	 * returns <tt>null</tt>.
	 * 
	 * @param vaddr
	 *            the starting virtual address of the null-terminated string.
	 * @param maxLength
	 *            the maximum number of characters in the string, not including
	 *            the null terminator.
	 * @return the string read, or <tt>null</tt> if no null terminator was
	 *         found.
	 */
	public String readVirtualMemoryString(int v_address, int max_l) {
		Lib.assertTrue(max_l >= 0);

		byte[] cytes = new byte[max_l + 1];

		int bytes_Read = readVirtualMemory(v_address, cytes);

		for (int length = 0; length < bytes_Read; length++) {
			if (cytes[length] == 0)
				return new String(cytes, 0, length);
		}

		return null;
	}

	/**
	 * Transfer data from this process's virtual memory to all of the specified
	 * array. Same as <tt>readVirtualMemory(vaddr, data, 0, data.length)</tt>.
	 * 
	 * @param vaddr
	 *            the first byte of virtual memory to read.
	 * @param data
	 *            the array where the data will be stored.
	 * @return the number of bytes successfully transferred.
	 */
	public int readVirtualMemory(int vaddr, byte[] data) {
		return readVirtualMemory(vaddr, data, 0, data.length);
	}

	/**
	 * Transfer data from this process's virtual memory to the specified array.
	 * This method handles address translation details. This method must
	 * <i>not</i> destroy the current process if an error occurs, but instead
	 * should return the number of bytes successfully copied (or zero if no data
	 * could be copied).
	 * 
	 * @param vaddr
	 *            the first byte of virtual memory to read.
	 * @param data
	 *            the array where the data will be stored.
	 * @param offset
	 *            the first byte to write in the array.
	 * @param bytesToRead
	 *            the number of bytes to transfer from virtual memory to the
	 *            array.
	 * @return the number of bytes successfully transferred.
	 */
	public int readVirtualMemory(int svaddr, byte[] data, int offest, int bytesToRead) {
		Lib.assertTrue(offest >= 0 && bytesToRead >= 0 && offest + bytesToRead <= data.length);
		int vir_addr = svaddr;
		byte[] memory = Machine.processor().getMemory();

		while (bytesToRead != 0) {
			int physical_addr = translateVaddrToPaddr(vir_addr);
			if (physical_addr < 0 || physical_addr >= memory.length) {
				handleException(Processor.exceptionAddressError);
			}

			int read_bytes = Math.min(bytesToRead,
					Processor.makeAddress(Processor.pageFromAddress(physical_addr) + 1, 0) - physical_addr);
			System.arraycopy(memory, physical_addr, data, offest, read_bytes);
			
			offest += read_bytes;
			vir_addr += read_bytes;
			bytesToRead -= read_bytes;
		}

		return vir_addr - svaddr;
	}

	/**
	 * Transfer all data from the specified array to this process's virtual
	 * memory. Same as <tt>writeVirtualMemory(vaddr, data, 0, data.length)</tt>.
	 * 
	 * @param vaddr
	 *            the first byte of virtual memory to write.
	 * @param data
	 *            the array containing the data to transfer.
	 * @return the number of bytes successfully transferred.
	 */
	public int writeVirtualMemory(int vaddr, byte[] data) {
		return writeVirtualMemory(vaddr, data, 0, data.length);
	}

	/**
	 * Transfer data from the specified array to this process's virtual memory.
	 * This method handles address translation details. This method must
	 * <i>not</i> destroy the current process if an error occurs, but instead
	 * should return the number of bytes successfully copied (or zero if no data
	 * could be copied).
	 * 
	 * @param vaddr
	 *            the first byte of virtual memory to write.
	 * @param data
	 *            the array containing the data to transfer.
	 * @param offset
	 *            the first byte to transfer from the array.
	 * @param bytesToWrite
	 *            the number of bytes to transfer from the array to virtual
	 *            memory.
	 * @return the number of bytes successfully transferred.
	 */
	public int writeVirtualMemory(int svaddr, byte[] data, int offset, int bytesToWrite) {
		Lib.assertTrue(offset >= 0 && bytesToWrite >= 0 && offset + bytesToWrite <= data.length);
		int vir_addr = svaddr;
		byte[] memory = Machine.processor().getMemory();

		while (bytesToWrite != 0) {
			int physical_addr = translateVaddrToPaddr(vir_addr);
			if (physical_addr < 0 || physical_addr >= memory.length) {
				handleException(Processor.exceptionAddressError);
			}

			int bytesWritten = Math.min(bytesToWrite,
					Processor.makeAddress(Processor.pageFromAddress(physical_addr) + 1, 0) - physical_addr);
			System.arraycopy(data, offset, memory, physical_addr, bytesWritten);

			vir_addr += bytesWritten;
			offset += bytesWritten;
			bytesToWrite -= bytesWritten;
		}

		return vir_addr - svaddr;
	}

	/**
	 * Load the executable with the specified name into this process, and
	 * prepare to pass it the specified arguments. Opens the executable, reads
	 * its header information, and copies sections and arguments into this
	 * process's virtual memory.
	 * 
	 * @param name
	 *            the name of the file containing the executable.
	 * @param args
	 *            the arguments to pass to the executable.
	 * @return <tt>true</tt> if the executable was successfully loaded.
	 */
	private boolean load(String name, String[] args) {
		Lib.debug(dbgProcess, "UserProcess.load(\"" + name + "\")");

		OpenFile executable = ThreadedKernel.fileSystem.open(name, false);
		if (executable == null) {
			Lib.debug(dbgProcess, "\topen failed");
			return false;
		}

		try {
			coff = new Coff(executable);
		} catch (EOFException e) {
			executable.close();
			Lib.debug(dbgProcess, "\tcoff load failed");
			return false;
		}

		// make sure the sections are contiguous and start at page 0
		numPages = 0;
		for (int s = 0; s < coff.getNumSections(); s++) {
			CoffSection section = coff.getSection(s);
			if (section.getFirstVPN() != numPages) {
				coff.close();
				Lib.debug(dbgProcess, "\tfragmented executable");
				return false;
			}
			numPages += section.getLength();
		}

		// make sure the argv array will fit in one page
		byte[][] argv = new byte[args.length][];
		int argsSize = 0;
		for (int i = 0; i < args.length; i++) {
			argv[i] = args[i].getBytes();
			// 4 bytes for argv[] pointer; then string plus one for null byte
			argsSize += 4 + argv[i].length + 1;
		}
		if (argsSize > pageSize) {
			coff.close();
			Lib.debug(dbgProcess, "\targuments too long");
			return false;
		}

		Prog_Counter = coff.getEntryPoint();

		numPages += stackPages;

		Stack_Pointer = numPages * pageSize;

		numPages++;

		if (!loadSections())
			return false;

		// store arguments in last page
		int entryOffset = (numPages - 1) * pageSize;
		int stringOffset = entryOffset + args.length * 4;

		this.argc = args.length;
		this.argv = entryOffset;

		for (int i = 0; i < argv.length; i++) {
			byte[] stringOffsetBytes = Lib.bytesFromInt(stringOffset);
			Lib.assertTrue(writeVirtualMemory(entryOffset, stringOffsetBytes) == 4);
			entryOffset += 4;
			Lib.assertTrue(writeVirtualMemory(stringOffset, argv[i]) == argv[i].length);
			stringOffset += argv[i].length;
			Lib.assertTrue(writeVirtualMemory(stringOffset, new byte[] { 0 }) == 1);
			stringOffset += 1;
		}

		return true;
	}

	/**
	 * Allocates memory for this process, and loads the COFF sections into
	 * memory. If this returns successfully, the process will definitely be run
	 * (this is the last step in process initialization that can fail).
	 * 
	 * @return <tt>true</tt> if the sections were successfully loaded.
	 */
	protected boolean loadSections() {
		// if (numPages > Machine.processor().getNumPhysPages()) {
		// Allocate pages to the process:
		pageTable = ((UserKernel) (Kernel.kernel)).allocateTable(numPages);
		if (pageTable == null) {
			coff.close();
			Lib.debug(dbgProcess, "\tinsufficient physical memory");
			return false;
		}

		// load sections
		for (int s = 0; s < coff.getNumSections(); s++) {
			CoffSection section = coff.getSection(s);

			Lib.debug(dbgProcess,
					"\tinitializing " + section.getName() + " section (" + section.getLength() + " pages)");

			for (int i = 0; i < section.getLength(); i++) {
				int vpn = section.getFirstVPN() + i;

				int paddr = translateVaddrToPaddr(Processor.makeAddress(vpn, 0));

				if (paddr >= 0) {
					section.loadPage(i, Processor.pageFromAddress(paddr));
					pageTable[vpn].readOnly = section.isReadOnly();
				} else {
					coff.close();
					Lib.debug(dbgProcess, "\tinvalid page encoutered whilst loading sections");
					return false;
				}
			}
		}

		return true;
	}

	/**
	 * Release any resources allocated by <tt>loadSections()</tt>.
	 */
	protected void unloadSections() {
		if (pageTable != null) {
			((UserKernel) (UserKernel.kernel)).releaseTable(pageTable);
		}
	}

	/**
	 * Initialize the processor's registers in preparation for running the
	 * program loaded into this process. Set the PC register to point at the
	 * start function, set the stack pointer register to point at the top of the
	 * stack, set the A0 and A1 registers to argc and argv, respectively, and
	 * initialize all other registers to 0.
	 */
	public void initRegisters() {
		Processor processor = Machine.processor();

		// by default, everything's 0
		for (int i = 0; i < processor.numUserRegisters; i++)
			processor.writeRegister(i, 0);

		// initialize PC and SP according
		processor.writeRegister(Processor.regPC, Prog_Counter);
		processor.writeRegister(Processor.regSP, Stack_Pointer);

		// initialize the first two argument registers to argc and argv
		processor.writeRegister(Processor.regA0, argc);
		processor.writeRegister(Processor.regA1, argv);
	}

	/**
	 * Handle the halt() system call.
	 */
	private int handleHalt() {

		Machine.halt();
		Lib.assertNotReached("Machine.halt() did not halt machine!");
		return 0;
	}

	private static final int syscallHalt = 0, syscallExit = 1, syscallExec = 2, syscallJoin = 3, syscallCreate = 4,
			syscallOpen = 5, syscallRead = 6, syscallWrite = 7, syscallClose = 8, syscallUnlink = 9;

	/**
	 * Handle a syscall exception. Called by <tt>handleException()</tt>. The
	 * <i>syscall</i> argument identifies which syscall the user executed:
	 * 
	 * <table>
	 * <tr>
	 * <td>syscall#</td>
	 * <td>syscall prototype</td>
	 * </tr>
	 * <tr>
	 * <td>0</td>
	 * <td><tt>void halt();</tt></td>
	 * </tr>
	 * <tr>
	 * <td>1</td>
	 * <td><tt>void exit(int status);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>2</td>
	 * <td><tt>int  exec(char *name, int argc, char **argv);
	 * 								</tt></td>
	 * </tr>
	 * <tr>
	 * <td>3</td>
	 * <td><tt>int  join(int pid, int *status);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>4</td>
	 * <td><tt>int  creat(char *name);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>5</td>
	 * <td><tt>int  open(char *name);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>6</td>
	 * <td><tt>int  read(int fd, char *buffer, int size);
	 * 								</tt></td>
	 * </tr>
	 * <tr>
	 * <td>7</td>
	 * <td><tt>int  write(int fd, char *buffer, int size);
	 * 								</tt></td>
	 * </tr>
	 * <tr>
	 * <td>8</td>
	 * <td><tt>int  close(int fd);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>9</td>
	 * <td><tt>int  unlink(char *name);</tt></td>
	 * </tr>
	 * </table>
	 * 
	 * @param syscall
	 *            the syscall number.
	 * @param a0
	 *            the first syscall argument.
	 * @param a1
	 *            the second syscall argument.
	 * @param a2
	 *            the third syscall argument.
	 * @param a3
	 *            the fourth syscall argument.
	 * @return the value to be returned to the user.
	 */
		private int handleCreate(String fname) {
		if (UserKernel.getKernel().fManager.isUnlink(fname))
			return -1;

		OpenFile file = UserKernel.fileSystem.open(fname, true);
		UserKernel.getKernel().fManager.openFile(fname);
		if (file == null)
			return -1;
		int fileDescriptor = getFreeDescriptor();
		if (fileDescriptor == -1) {
			file.close();
			return -1;
		}
		open_table[fileDescriptor] = file;
		NameofFiles[fileDescriptor] = fname;
		return fileDescriptor;
	}
public int handleSyscall(int syscall, int a0, int a1, int a2, int a3) {
		switch (syscall) {
		case syscallHalt:
			return handleHalt();

		case syscallExit:
			return handleExit(a0);
		case syscallExec:
			return processExecHandler(readVirtualMemoryString(a0, 256), a1, a2);
		case syscallJoin:
			return procesJoinHandler(a0, a1);
		case syscallCreate:
			return handleCreate(readVirtualMemoryString(a0, 256));
		case syscallOpen:
			return handleOpen(readVirtualMemoryString(a0, 256));
		case syscallRead:
			return handleRead(a0, a1, a2);
		case syscallWrite:
			return handleWrite(a0, a1, a2);
		case syscallClose:
			return handleClose(a0);
		case syscallUnlink:
			return handleUnlink(readVirtualMemoryString(a0, 256));

		default:
			Lib.debug(dbgProcess, "Unknown syscall " + syscall);
			Lib.assertNotReached("Unknown system call!");
		}
		return 0;
	}


	private int handleOpen(String fname) {
		if (UserKernel.getKernel().fManager.isUnlink(fname))
			return -1;

		OpenFile file = UserKernel.fileSystem.open(fname, false);
		UserKernel.getKernel().fManager.openFile(fname);
		if (file == null)
			return -1;
		int fileDescriptor = getFreeDescriptor();
		if (fileDescriptor == -1) {
			file.close();
			return -1;
		}
		open_table[fileDescriptor] = file;
		NameofFiles[fileDescriptor] = fname;
		return fileDescriptor;
	}
private int handleClose(int fileDescriptor) {
		if (fileDescriptor < 0 || fileDescriptor >= Max_files)
			return -1;
		if (open_table[fileDescriptor] == null)
			return -1;
		open_table[fileDescriptor].close();
		open_table[fileDescriptor] = null;

		if (NameofFiles[fileDescriptor] != null) {
			if (UserKernel.getKernel().fManager.decrementCount(NameofFiles[fileDescriptor]) == false)
				return -1;
			NameofFiles[fileDescriptor] = null;
		}

		return 0;
	}

	private int handleRead(int fileDescriptor, int addr, int count) {
		if (fileDescriptor < 0 || fileDescriptor >= Max_files || count < 0)
			return -1;
		if (open_table[fileDescriptor] == null)
			return -1;
		if (fileDescriptor == 1 && NameofFiles[fileDescriptor] == null)
			return -1;

		int size = 0;
		byte[] buff = new byte[count];

		size = open_table[fileDescriptor].read(buff, 0, count);
		if (size < 0)
			return -1;
		return writeVirtualMemory(addr, buff, 0, size);
	}

	
	
	private int handleUnlink(String fname) {
		if (UserKernel.getKernel().fManager.unlink(fname) == false)
			return -1;
		return 0;
	}
private int handleWrite(int fileDescriptor, int addr, int count) {
		if (fileDescriptor < 0 || fileDescriptor >= Max_files || count < 0)
			return -1;
		if (open_table[fileDescriptor] == null)
			return -1;
		if (fileDescriptor == 0 && NameofFiles[fileDescriptor] == null)
			return -1;

		byte[] buff = new byte[count];
		if (readVirtualMemory(addr, buff) < count)
			return -1;
		return open_table[fileDescriptor].write(buff, 0, count);
	}

	private int getFreeDescriptor() {
		int i = 0;
		while (i < Max_files) {
			if (open_table[i] == null)
				return i;
			i++;
		}
		return -1;
	}
private int translateVaddrToPaddr(int address) {
		int virtualPage = Processor.pageFromAddress(address);
		int pageOffset = Processor.offsetFromAddress(address);
		if (virtualPage >= pageTable.length) {
			return -1;
		}
		TranslationEntry pte = pageTable[virtualPage];
		if (pte.valid == false)
			return -1;
		int physicalPage = pageTable[virtualPage].ppn;
		return Processor.makeAddress(physicalPage, pageOffset);
	}


private int procesJoinHandler(int child_id, int statusAddr) {
		if (UserKernel.getKernel().pManager.exists(child_id) == false) {
			return -1;
		}

		if (UserKernel.getKernel().pManager.getParent(child_id) != process_id) {
			return -1;
		}

		if (UserKernel.getKernel().pManager.checkforRunning(child_id)) {
			UserKernel.getKernel().pManager.getProcess(child_id).debug_Thread.join();
		}
		if (UserKernel.getKernel().pManager.checkError(child_id)) {
			return 0;
		}

		UserKernel.getKernel().pManager.parentChange(child_id, -1);

		int stat = UserKernel.getKernel().pManager.getReturnCode(child_id);
		writeVirtualMemory(statusAddr, Lib.bytesFromInt(stat));
		return 1;
	}
		/**
	 * Handle a user exception. Called by <tt>UserKernel.exceptionHandler()</tt>
	 * . The <i>cause</i> argument identifies which exception occurred; see the
	 * <tt>Processor.exceptionZZZ</tt> constants.
	 * 
	 * @param cause
	 *            the user exception that occurred.
	 */
		private int handleExit(int retCode) {
		unloadSections();

		UserKernel.getKernel().pManager.remParent(process_id);

		Lib.assertTrue(UserKernel.getKernel().pManager.checkNoChildren(process_id), "Children not disowned");

		for (int i = 0; i < Max_files; i++) {
			if (open_table[i] != null) {
				handleClose(i);
			}
		}

		UserKernel.getKernel().pManager.setReturnCode(process_id, retCode);

		UserKernel.getKernel().pManager.setFinish(process_id);

		if (UserKernel.getKernel().pManager.isLast(process_id)) {
			Kernel.kernel.terminate();
		}

		KThread.currentThread().finish();

		Lib.assertNotReached("syscall exit not present");
		return 0;
	}


	private int processExecHandler(String file, int argumentCount, int argvAddr) {
		UserProcess newProc = newUserProcess();

		UserKernel.getKernel().pManager.parentChange(newProc.process_id, process_id);

		String[] argstr = new String[argumentCount];

		byte[] argAddresses = new byte[argumentCount * 4];
		readVirtualMemory(argvAddr, argAddresses);

		for (int i = 0; i < argumentCount; i++) {
			argstr[i] = readVirtualMemoryString(Lib.bytesToInt(argAddresses, i * 4), 256);
		}

		if (newProc.execute(file, argstr) == false) {
			UserKernel.getKernel().pManager.setFinish(newProc.process_id);
			UserKernel.getKernel().pManager.setError(process_id);
			UserKernel.getKernel().pManager.setReturnCode(process_id, -1);

			return -1;
		}

		return newProc.process_id;
	}


public void handleException(int cause) {
		Processor processor = Machine.processor();

		switch (cause) {
		case Processor.exceptionSyscall:
			int result = handleSyscall(processor.readRegister(Processor.regV0), processor.readRegister(Processor.regA0),
					processor.readRegister(Processor.regA1), processor.readRegister(Processor.regA2),
					processor.readRegister(Processor.regA3));
			processor.writeRegister(Processor.regV0, result);
			processor.advancePC();
			break;

		case Processor.exceptionAddressError:
		case Processor.exceptionBusError:
		case Processor.exceptionIllegalInstruction:
		case Processor.exceptionOverflow:
		case Processor.exceptionPageFault:
		case Processor.exceptionReadOnly:
		case Processor.exceptionTLBMiss:
			System.err.println("Fatal Exception Occurred: " + Processor.exceptionNames[cause]);
			UserKernel.getKernel().pManager.setError(process_id);
			handleExit(-1);
			break;

		default:
			Lib.debug(dbgProcess, "Unexpected exception: " + Processor.exceptionNames[cause]);
			Lib.assertNotReached("Unexpected exception");
		}
	}
	

	private UThread debug_Thread;

	private int Prog_Counter, Stack_Pointer;

	private final int Max_files = 16;

	/** The program being run by this process. */
	protected Coff coff;

	/** This process's page table. */
	protected TranslationEntry[] pageTable;

	/** The number of contiguous pages occupied by the program. */
	protected int numPages;

	/** The number of pages in the program's stack. */
	protected final int stackPages = 8;

	private int argc, argv;

	private String[] NameofFiles;

	private static final int pageSize = Processor.pageSize;

	private static final char dbgProcess = 'a';

	private OpenFile[] open_table;

	private int process_id = 0;

	
}
