
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.swirlds.platform.Address;
import com.swirlds.platform.AddressBook;
import com.swirlds.platform.FCDataInputStream;
import com.swirlds.platform.FCDataOutputStream;
import com.swirlds.platform.FastCopyable;
import com.swirlds.platform.Platform;
import com.swirlds.platform.SwirldState;
import com.swirlds.platform.Utilities;

/**
 * This holds the current state of the swirld. For this simple "hello swirld" code, each transaction is just
 * a string, and the state is just a list of the strings in all the transactions handled so far, in the
 * order that they were handled.
 */
public class HelloSwirldDemoState implements SwirldState {
	// The "state" is just a list of the strings in all transactions,
	// listed in the order received here, which will eventually be
	// the consensus order of the community.
	private List<String> strings = Collections
			.synchronizedList(new ArrayList<String>());
	private AddressBook addressBook;

	public synchronized List<String> getStrings() {
		return strings;
	}

	public synchronized String getReceived() {
		return strings.toString();
	}

	public String toString() {
		return strings.toString();
	}

	// ///////////////////////////////////////////////////////////////////

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
		HelloSwirldDemoState copy = new HelloSwirldDemoState();
		copy.copyFrom(this);
		return copy;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void copyTo(FCDataOutputStream outStream) {
		try {
			Utilities.writeStringArray(outStream,
					strings.toArray(new String[0]));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void copyFrom(FCDataInputStream inStream) {
		try {
			strings = new ArrayList<String>(
					Arrays.asList(Utilities.readStringArray(inStream)));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public synchronized void copyFrom(SwirldState old) {
		strings = Collections.synchronizedList(
				new ArrayList<String>(((HelloSwirldDemoState) old).strings));
		addressBook = ((HelloSwirldDemoState) old).addressBook.copy();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public synchronized void handleTransaction(long id, boolean consensus,
			Instant timeCreated, byte[] transaction, Address address) {
		strings.add(new String(transaction, StandardCharsets.UTF_8));
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