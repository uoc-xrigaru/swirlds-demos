
/*
 * This file is public domain.
 *
 * SWIRLDS MAKES NO REPRESENTATIONS OR WARRANTIES ABOUT THE SUITABILITY OF 
 * THE SOFTWARE, EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED 
 * TO THE IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A
 * PARTICULAR PURPOSE, OR NON-INFRINGEMENT. SWIRLDS SHALL NOT BE LIABLE FOR 
 * ANY DAMAGES SUFFERED AS A RESULT OF USING, MODIFYING OR 
 * DISTRIBUTING THIS SOFTWARE OR ITS DERIVATIVES.
 */

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import com.swirlds.platform.*;
import com.swirlds.platform.fc.*;

/**
 * The state is primarily a Fast Copyable Filesystem. A transaction is an update to the filesystem. For now
 * that takes the form of a filename and its contents.
 */
public class FilesystemDemoState implements SwirldState {
	private Platform platform;
	private AddressBook addressBook;
	private FilesystemFC fs;

	public synchronized FilesystemFC getFS() {
		return fs;
	}

	/** @return the contents of the file given by <code>pathname</code> */
	public synchronized String fileContents(String pathname)
			throws IOException {
		return new String(fs.slurp(pathname), StandardCharsets.UTF_8);
	}

	public synchronized AddressBook getAddressBookCopy() {
		return addressBook.copy();
	}

	public synchronized FastCopyable copy() {
		FilesystemDemoState copy = new FilesystemDemoState();
		copy.copyFrom(this);
		return copy;
	}

	public synchronized void copyTo(FCDataOutputStream outStream)
			throws IOException {
		addressBook.copyTo(outStream);
		fs.copyTo(outStream);
	}

	public synchronized void copyFrom(FCDataInputStream inStream)
			throws IOException {
		addressBook.copyFrom(inStream);
		fs.copyFrom(inStream);
	}

	public synchronized void copyFrom(SwirldState old) {
		FilesystemDemoState old1 = (FilesystemDemoState) old;
		platform = old1.platform;
		addressBook = old1.addressBook.copy();
		fs = old1.fs.copy();
	}

	/**
	 * Create (or replace) a file whose pathname and contents are described in the transaction. Any
	 * intermediate directories that don't already exist locally are created first.
	 */
	public synchronized void handleTransaction(long id, boolean consensus,
			Instant timeCreated, byte[] transaction, Address address) {
		try {
			FileTransaction tx = FileTransaction.deserialize(transaction);
			if (fs.resolvePath(tx.pathname).isEmpty())
				throw new IllegalArgumentException("empty pathname");
			fs.ensureDirectoriesExist(fs.parentDir(tx.pathname));
			fs.dump(tx.text.getBytes(StandardCharsets.UTF_8), tx.pathname);
		} catch (IOException | ClassNotFoundException e) {
			throw new RuntimeException(e);
		}
	}

	public void freeze() {
	}

	public synchronized void init(Platform platform, AddressBook addressBook) {
		this.platform = platform;
		this.addressBook = addressBook;
		fs = FilesystemFC.newInstance();
		// later, the platform will pass in a filesystem, possibly restored from disk
	}

	// disabled, the file explorer may still be using the filesystem
	// protected void finalize() throws Throwable {
	// try {synchronized(this) {fs.delete();}} finally {super.finalize();}
	// }
}