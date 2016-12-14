
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

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Locale;

import com.swirlds.platform.AddressBook;
import com.swirlds.platform.Browser;
import com.swirlds.platform.Console;
import com.swirlds.platform.Platform;
import com.swirlds.platform.SwirldMain;
import com.swirlds.platform.SwirldState;

/**
 * This HelloSwirld creates a single transaction, consisting of the string "Hello Swirld", and then goes
 * into a busy loop (checking once a second) to see when the state gets the transaction. When it does, it
 * prints it, too.
 */
public class StatsDemoMain implements SwirldMain {
	// the first four come from the parameters in the config.txt file

	// should this run with no windows?
	private boolean		headless		= false;
	// number of milliseconds between writes to the log file
	private long		writePeriod		= 3000;
	// bytes in each transaction
	private int			bytesPerTrans	= 1;
	// transactions in each Event
	private int			transPerEvent	= 1;

	// path and filename of the .csv file to write to
	private String		path;
	// number of members running this app
	private int			numMembers		= -1;
	// number of members running this app on this local machine
	private int			numLocalMembers	= -1;
	// ID number for this member
	private int			selfId;
	// the app is run by this
	private Platform	platform;
	// the address book passed in by the Platform at the start
	private AddressBook	addressBook;
	// a console window for text output
	private Console		console			= null;
	// the transaction to repeatedly create
	private byte[]		transaction;

	/**
	 * This is just for debugging: it allows the app to run in Eclipse. If the config.txt exists and lists a
	 * particular SwirldMain class as the one to run, then it can run in Eclipse (with the green triangle
	 * icon).
	 * 
	 * @param args
	 *            these are not used
	 */
	public static void main(String[] args) {
		Browser.main(null);
	}

	/**
	 * Write a message to the log file. Also write it to the console, if there is one. In both cases, skip a
	 * line after writing. This method opens the file at the start and closes it at the end, to deconflict
	 * with any other process trying to read the same file. For example, this app could run headless on a
	 * server, and an FTP session could download the log file, and the file it received would have only
	 * complete log messages, never half a message.
	 * 
	 * The file is created if it doesn't exist. It will be named "StatsDemo0.csv", with the number
	 * incrementing for each simultaneous member if there is more than one. The location is the "current"
	 * directory. If run from a shell script, it will be the current folder that the shell script has. If
	 * run from Eclipse, it will be at the top of the project folder. If there is a console, it prints the
	 * location there. If not, it can be found by searching the file system for "StatsDemo0.csv".
	 * 
	 * @param append
	 *            should append to the file rather than overwriting it?
	 * @param message
	 *            the String to write
	 */
	private void writeToConsoleAndFile(boolean append, String message) {
		BufferedWriter file = null;
		try {// create or append to file in current directory
			path = System.getProperty("user.dir") + File.separator + "StatsDemo"
					+ selfId + ".csv";
			file = new BufferedWriter(new FileWriter(path, append));
			file.write(message);
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (file != null) {
				try {
					file.close();
				} catch (Exception e) {
				}
			}
		}
		if (console != null) {
			console.out.print(message.replaceAll(",", " "));
		}
	}

	/**
	 * write one line of the comments (mode 1), or one column heading (mode 2), or one number in a column
	 * (mode 3).
	 * 
	 * @param mode
	 *            which kind of thing to write
	 * @param heading
	 *            the column heading
	 * @param comment
	 *            the comment describing this statistic
	 * @param stat
	 *            the statistic itself (usually a number)
	 */
	void write(int mode, String heading, String comment, String stat) {
		switch (mode) {
			case 1 : // write the definition of a statistic (at the top)
				writeToConsoleAndFile(true, String.format("%13s:,%s", heading,
						comment + System.getProperty("line.separator")));
				break;
			case 2 : // write a column heading
				writeToConsoleAndFile(true,
						String.format(",%" + stat.length() + "s", heading));
				break;
			case 3 :// write one statistic
				writeToConsoleAndFile(true, "," + stat);
				break;
		}
	}

	/**
	 * write all the stats as comments (mode 1) or column headings (mode 2) or numbers (mode 3).
	 * 
	 * @mode which of the three types to write
	 */
	void writeAll(int mode) {
		DateTimeFormatter formatter = DateTimeFormatter
				.ofLocalizedDateTime(FormatStyle.MEDIUM).withLocale(Locale.US)
				.withZone(ZoneId.systemDefault());
		formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z");
		String time = formatter
				.format(Instant.now().atZone(ZoneId.of("US/Central")));
		if (mode == 1) {
			write(mode, "filename", path, "");
		} else {
			writeToConsoleAndFile(true, ",");
		}
		write(mode, "time", //
				"the time at which the stats are written",
				String.format("%25s", time));
		write(mode, "trans",//
				"number of transactions received so far",
				String.format("%10d", platform.getNumTrans()));
		write(mode, "secR2C",//
				"time from receiving an event to knowing its consensus (in seconds)",
				String.format("%7.2f", platform.getAvgReceivedConsensusTime()));
		write(mode, "secC2C",//
				"time from creating an event to knowing its consensus (in seconds)",
				String.format("%7.2f", platform.getAvgCreatedConsensusTime()));
		write(mode, "bytes/sec",//
				"number of bytes in the transactions received per second",
				String.format("%12.2f",
						platform.getTransPerSecond() * bytesPerTrans));
		write(mode, "trans/sec",//
				"number of transactions received per second",
				String.format("%10.2f", platform.getTransPerSecond()));
		write(mode, "events/sec",//
				"number of events received per second",
				String.format("%12.2f", platform.getEventsPerSecond()));
		write(mode, "dupEv/sec",//
				"number of events received per second that are already known",
				String.format("%11.2f",
						platform.getDuplicateEventsPerSecond()));
		write(mode, "dupEv%",//
				"percentage of events received that are already known",
				String.format("%8.2f",
						platform.getDuplicateEventsPercentage()));
		write(mode, "badEv/sec",//
				"number of corrupted events received per second",
				String.format("%11.7f", platform.getBadEventsPerSecond()));
		write(mode, "cSync/sec",//
				"(call syncs) syncs completed per second initiated by this member",
				String.format("%11.7f", platform.getCallSyncsPerSecond()));
		write(mode, "rSync/sec",//
				"(receive syncs) syncs completed per second initiated by other member",
				String.format("%11.7f", platform.getRecSyncsPerSecond()));
		write(mode, "icSync/sec",//
				"(interrupted call syncs) syncs interrupted per second initiated by this member",
				String.format("%11.7f",
						platform.getInterruptedCallSyncsPerSecond()));
		write(mode, "irSync/sec",//
				"(interrupted receive syncs) syncs interrupted per second initiated by other member",
				String.format("%11.7f",
						platform.getInterruptedRecSyncsPerSecond()));
		write(mode, "freeB",//
				"bytes of free memory (which can increase after a garbage collection)",
				String.format("%12d", Runtime.getRuntime().freeMemory()));
		write(mode, "totB",//
				"total bytes in the Java Virtual Machine",
				String.format("%12d", Runtime.getRuntime().totalMemory()));
		write(mode, "maxB",//
				"maximum bytes that the JVM might use",
				String.format("%12d", Runtime.getRuntime().maxMemory()));
		write(mode, "proc",//
				"number of processors (cores) available to the JVM",
				String.format("%6d",
						Runtime.getRuntime().availableProcessors()));
		write(mode, "name",//
				"name of this member",//
				String.format("%6s",
						addressBook.getAddress(selfId).getSelfName()));
		write(mode, "ID", //
				"ID number of this member",//
				String.format("%3d", selfId));
		write(mode, "members", //
				"total number of members participating",
				String.format("%8d", numMembers));
		write(mode, "local", //
				"number of members running on this local machine",
				String.format("%6d", numLocalMembers));
		write(mode, "write", //
				"write statistics to the file every this many milliseconds",
				String.format("%6d", writePeriod));
		write(mode, "bytes/trans", //
				"number of bytes in each transactions",
				String.format("%12d", bytesPerTrans));
		write(mode, "trans/event", //
				"number of transactions in each event",
				String.format("%12d", transPerEvent));
		writeToConsoleAndFile(true, System.getProperty("line.separator"));
	}

	// ///////////////////////////////////////////////////////////////////

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void preEvent() {
		for (int i = 0; i < transPerEvent; i++) {
			platform.createTransaction(transaction, null);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void init(Platform platform, int id) {
		long syncDelay;
		this.platform = platform;
		String[] pars = platform.getParameters();
		selfId = id;
		headless = (pars[0].trim().equals("1"));
		writePeriod = Integer.parseInt(pars[1].trim());
		syncDelay = Integer.parseInt(pars[2].trim());
		bytesPerTrans = Integer.parseInt(pars[3].trim());
		transPerEvent = Integer.parseInt(pars[4].trim());
		addressBook = platform.getState().getAddressBookCopy();
		if (!headless) { // create the window, make it visible
			console = platform.createConsole(true);
		}
		transaction = new byte[bytesPerTrans];
		platform.setAbout( // set the browser's "about" box
				"Stats Demo v. 1.1\nThis writes statistics to a log file,"
						+ " such as the number of transactions per second.");
		platform.setSleepAfterSync(syncDelay);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void run() {
		writeToConsoleAndFile(false, ""); // erase the old file, if any
		writeAll(1); // write the definitions at the top
		writeAll(2); // write the column headings
		while (platform.isRunning()) { // keep logging forever
			try {
				writeAll(3); // write a row of numbers
				Thread.sleep(writePeriod); // add new rows infrequently
			} catch (InterruptedException e) {
			}
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public SwirldState newState() {
		return new StatsDemoState();
	}
}