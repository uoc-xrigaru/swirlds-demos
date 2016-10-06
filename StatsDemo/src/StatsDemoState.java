
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
import java.time.Instant;

import com.swirlds.platform.Address;
import com.swirlds.platform.AddressBook;
import com.swirlds.platform.FCDataInputStream;
import com.swirlds.platform.FCDataOutputStream;
import com.swirlds.platform.FastCopyable;
import com.swirlds.platform.Platform;
import com.swirlds.platform.SwirldState;

/**
 * This holds the current state of the swirld. For this simple "hello swirld" code, each transaction is just
 * a string, and the state is just a list of the strings in all the transactions handled so far, in the
 * order that they were handled.
 */
public class StatsDemoState implements SwirldState {
	// the address book passed in by the Platform at the start
	private AddressBook addressBook;

	/**
	 * {@inheritDoc}
	 */
	@Override
	public synchronized AddressBook getAddressBookCopy() {
		return addressBook.copy();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public synchronized FastCopyable copy() {
		StatsDemoState copy = new StatsDemoState();
		copy.copyFrom(this);
		return copy;
	}

	/**
	 * {@inheritDoc}
	 * 
	 * @throws IOException
	 */
	@Override
	public void copyTo(FCDataOutputStream outStream) throws IOException {
		addressBook.copyTo(outStream);
	}

	/**
	 * {@inheritDoc}
	 * 
	 * @throws IOException
	 */
	@Override
	public void copyFrom(FCDataInputStream inStream) throws IOException {
		addressBook.copyFrom(inStream);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public synchronized void copyFrom(SwirldState old) {
		addressBook = ((StatsDemoState) old).addressBook.copy();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public synchronized void handleTransaction(long id, boolean consensus,
			Instant timeCreated, byte[] transaction, Address address) {
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void freeze() {
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public synchronized void init(Platform platform, AddressBook addressBook) {
		this.addressBook = addressBook;
	}
}