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
package com.swirlds.demos;

import java.awt.BorderLayout;
import java.awt.Checkbox;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.GridLayout;
import java.awt.Label;
import java.awt.Panel;
import java.awt.TextField;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.geom.Rectangle2D;
import java.net.InetAddress;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Locale;
import java.util.function.BiFunction;

import javax.swing.JFrame;
import javax.swing.JPanel;

import com.swirlds.platform.AddressBook;
import com.swirlds.platform.Browser;
import com.swirlds.platform.Event;
import com.swirlds.platform.Platform;
import com.swirlds.platform.SwirldMain;
import com.swirlds.platform.SwirldState;

/**
 * This app draws the hashgraph on the screen. Events are circles, with earlier ones lower. Events are color
 * coded: A non-witness is gray, and a witness has a color of green (famous), blue (not famous) or red
 * (undecided fame). When the event becomes part of the consensus, its color becomes darker.
 */
public class HashgraphDemoMain implements SwirldMain {
	// outline of labels
	static final Color	LABEL_OUTLINE	= new Color(255, 255, 255);
	// unknown-fame witness, non-cons
	static final Color	LIGHT_RED		= new Color(192, 0, 0);
	static final Color	DARK_RED		= new Color(128, 0, 0);
	// unknown-fame witness, consensus
	static final Color	LIGHT_GREEN		= new Color(0, 192, 0);
	// famous witness, non-consensus
	static final Color	DARK_GREEN		= new Color(0, 128, 0);
	// famous witness, consensus
	static final Color	LIGHT_BLUE		= new Color(0, 0, 192);
	// non-famous witness, non-consensus
	static final Color	DARK_BLUE		= new Color(0, 0, 128);
	// non-famous witness, consensus
	static final Color	LIGHT_GRAY		= new Color(160, 160, 160);
	// non-witness, non-consensus
	static final Color	DARK_GRAY		= new Color(0, 0, 0);
	// non-witness, consensus
	public Platform		platform;
	// app is run by this
	public int			selfId;
	// ID for this member the entire window, including Swirlds menu, Picture, checkboxes
	JFrame				window;
	// the JFrame with the hashgraph plus text at the top
	Picture				picture;
	// paintComponent will draw this copy of the set of events
	private Event[]		eventsCache;
	// the number of members in the
	// addressBook
	private int			numMembers		= -1;
	// the nicknames of all the members
	private String[]	names;

	// the following allow each member to have multiple columns so lines don't cross

	// number of columns (more than number of members if preventCrossings)
	private int			numColumns;
	// mems2col[a][b] = which member-b column is adjacent to some member-a column
	private int			mems2col[][];
	// col2mems[c][0] = the member for column c, col2mems[c][1] = second member or -1 if none
	private int			col2mems[][];

	// if checked, this member calls to gossip once per second
	private Checkbox	slowCheckbox;
	// if checked, freeze the display (don't update it)
	private Checkbox	freezeCheckbox;
	// if checked, use multiple columns per member to void lines crossing
	private Checkbox	expandCheckbox;

	// the following control which labels to print on each vertex

	// the round number for the event
	private Checkbox	labelRoundCheckbox;
	// the consensus round received for the event
	private Checkbox	labelRoundRecCheckbox;
	// the consensus order number for the event
	private Checkbox	labelConsOrderCheckbox;
	// the consensus time stamp for the event
	private Checkbox	labelConsTimestampCheckbox;
	// the generation number for the event
	private Checkbox	labelGenerationCheckbox;
	// the ID number of the member who created the event
	private Checkbox	labelCreatorCheckbox;
	// the sequence number for that creator (starts at 0)
	private Checkbox	labelSeqCheckbox;

	// only draw this many events, at most
	private TextField	eventLimit;

	// format the consensusTimestamp label
	DateTimeFormatter	formatter		= DateTimeFormatter
												.ofPattern("H:m:s.n")
												.withLocale(Locale.US)
												.withZone(
														ZoneId.systemDefault());

	/**
	 * Return the color for an event based on calculations in the consensus algorithm A non-witness is gray,
	 * and a witness has a color of green (famous), blue (not famous) or red (undecided fame). When the
	 * event becomes part of the consensus, its color becomes darker.
	 * 
	 * @param event
	 *            the event to color
	 * @return its color
	 */
	private Color eventColor(Event event) {
		if (!event.isWitness()) {
			return event.isConsensus() ? DARK_GRAY : LIGHT_GRAY;
		}
		if (!event.isFameDecided()) {
			return event.isConsensus() ? DARK_RED : LIGHT_RED;
		}
		if (event.isFamous()) {
			return event.isConsensus() ? DARK_GREEN : LIGHT_GREEN;
		}
		return event.isConsensus() ? DARK_BLUE : LIGHT_BLUE;
	}

	/**
	 * The window is a subclass of JPanel.
	 * 
	 */
	private class Picture extends JPanel {
		private static final long	serialVersionUID	= 1L;
		int							ymin, ymax, width, n;
		double						r;
		long						minGen, maxGen;
		// where to draw next in the window, and the font height
		int							row, col, textLineHeight;

		// find x position on the screen for event e2 which has a parent or child of e1
		private int xpos(Event e1, Event e2) {
			if (e1 != null) { // find the column for e2 next to the column for e1
				return (mems2col[(int) e1.getCreatorId()][(int) e2
						.getCreatorId()] + 1) * width / (numColumns + 1);
			} else { // there is no e1, so pick one of the e2 columns arbitrarily (next to 0 or 1)
				return (mems2col[e2.getCreatorId() == 0 ? 1 : 0][(int) e2
						.getCreatorId()] + 1) * width / (numColumns + 1);
			}
		}

		// find y position on the screen for an event
		private int ypos(Event event) {
			return (event == null) ? -100 : (int) (ymax - r
					* (1 + 2 * (event.getGeneration() - minGen)));
		}

		// this is used in paintComponent to draw text at the top of the window
		private void print(Graphics g, String text, double value) {
			g.drawString(String.format(text, value), col, row++
					* textLineHeight - 3);
		}

		/**
		 * Called by the runtime system whenever the panel needs painting.
		 */
		public void paintComponent(Graphics g) {
			super.paintComponent(g);
			g.setFont(new Font(Font.MONOSPACED, 12, 12));
			FontMetrics fm = g.getFontMetrics();
			int fa = fm.getMaxAscent();
			int fd = fm.getMaxDescent();
			textLineHeight = fa + fd;
			int numMem = platform.getState().getAddressBookCopy().getSize();
			calcMemsColNames();
			width = getWidth();

			String ip = "";
			try {
				ip = (InetAddress.getLocalHost().getHostAddress());
			} catch (Exception e) {
			}

			row = 1;
			col = 10;

			print(g, "%5.0f trans/sec", platform.getTransPerSecond());
			print(g, "%5.0f events/sec", platform.getEventsPerSecond());
			print(g, "%5.3f sec, create to consensus",
					platform.getCreatedConsensusTime());
			print(g, "%5.3f sec, receive to consensus",
					platform.getReceivedConsensusTime());
			print(g, "IP address is " + ip, 0);

			int height1 = (row - 1) * textLineHeight;    // text area at the top
			int height2 = getHeight() - height1; // the main display, below the text
			g.setColor(Color.BLACK);
			ymin = (int) Math.round(height1 + 0.025 * height2);
			ymax = (int) Math.round(height1 + 0.975 * height2) - textLineHeight;
			for (int i = 0; i < numColumns; i++) {
				String name;
				if (col2mems[i][1] == -1) {
					name = "" + names[col2mems[i][0]];
				} else {
					name = "" + names[col2mems[i][0]] + "|"
							+ names[col2mems[i][1]];
				}
				int x = (i + 1) * width / (numColumns + 1);
				g.drawLine(x, ymin, x, ymax);
				Rectangle2D rect = fm.getStringBounds(name, g);
				g.drawString(name, (int) (x - rect.getWidth() / 2),
						(int) (ymax + rect.getHeight()));
			}

			Event[] events = eventsCache;
			if (events == null) { // in case a screen refresh happens before any events
				return;
			}
			int maxEvents;
			try {
				maxEvents = Math.max(0, Integer.parseInt(eventLimit.getText()));
			} catch (NumberFormatException err) {
				maxEvents = 0;
			}

			if (maxEvents > 0) {
				events = Arrays.copyOfRange(events,
						Math.max(0, events.length - maxEvents), events.length);
			}

			minGen = Integer.MAX_VALUE;
			maxGen = Integer.MIN_VALUE;
			for (Event event : events) {
				minGen = Math.min(minGen, event.getGeneration());
				maxGen = Math.max(maxGen, event.getGeneration());
			}
			maxGen = Math.max(maxGen, minGen + 2);
			n = numMem + 1;
			double gens = maxGen - minGen;
			double dy = (ymax - ymin) * (gens - 1) / gens;
			r = Math.min(width / n / 4, dy / gens / 2);
			int d = (int) (2 * r);

			// for each event, draw 2 downward lines to its parents
			for (Event event : events) {
				g.setColor(eventColor(event));
				Event e1 = event.getSelfParent();
				Event e2 = event.getOtherParent();
				if (e1 != null && e1.getGeneration() >= minGen) {
					g.drawLine(xpos(e2, event), ypos(event), xpos(e2, event),
							ypos(e1));
				}
				if (e2 != null && e2.getGeneration() >= minGen) {
					g.drawLine(xpos(e2, event), ypos(event), xpos(event, e2),
							ypos(e2));
				}
			}

			// for each event, draw its circle
			for (Event event : events) {
				Event e2 = event.getOtherParent();
				Color color = eventColor(event);
				g.setColor(color);
				g.fillOval(xpos(e2, event) - d / 2, ypos(event) - d / 2, d, d);
				g.setFont(g.getFont().deriveFont(Font.BOLD));

				String s = "";

				if (labelRoundCheckbox.getState()) {
					s += " " + event.getRoundCreated();
				}
				if (labelRoundRecCheckbox.getState()) {
					s += " " + event.getRoundReceived();
				}
				// if not consensus, then there's no order yet
				if (labelConsOrderCheckbox.getState() && event.isConsensus()) {
					s += " " + event.getConsensusOrder();
				}
				if (labelConsTimestampCheckbox.getState()) {
					Instant t = event.getConsensusTimestamp();
					if (t != null) {
						s += " " + formatter.format(t);
					}
				}
				if (labelGenerationCheckbox.getState()) {
					s += " " + event.getGeneration();
				}
				if (labelCreatorCheckbox.getState()) {
					s += " " + event.getCreatorId(); // ID number of member who created it
				}
				if (labelSeqCheckbox.getState()) {
					s += " " + event.getCreatorSeq(); // sequence number for the creator (starts at 0)
				}
				if (s != "") {
					Rectangle2D rect = fm.getStringBounds(s, g);
					int x = (int) (xpos(e2, event) - rect.getWidth() / 2. - fa / 4.);
					int y = (int) (ypos(event) + rect.getHeight() / 2. - fd / 2);
					g.setColor(LABEL_OUTLINE);
					g.drawString(s, x - 1, y - 1);
					g.drawString(s, x + 1, y - 1);
					g.drawString(s, x - 1, y + 1);
					g.drawString(s, x + 1, y + 1);
					g.setColor(color);
					g.drawString(s, x, y);
				}
			}
		}
	}

	/**
	 * This is just for debugging: it allows the app to run in Eclipse. If the config.txt exists and lists a
	 * particular SwirldMain class as the one to run, then it can run in Eclipse (with the green triangle
	 * icon).
	 * 
	 * @param args
	 *            these are not used
	 */
	public static void main(String[] args) {
		Browser.main(args);
	}

	/**
	 * In order to draw this "expanded" hashgraph (where each member has multiple columns and lines don't
	 * cross), we need several data tables. So fill in four arrays: numMembers, mems2col, col2mems, and
	 * names, if they haven't already been filled in, or if the number of members has changed.
	 */
	private void calcMemsColNames() {
		final AddressBook addressBook = platform.getState()
				.getAddressBookCopy();
		final int m = addressBook.getSize();
		if (m != numMembers) {
			numMembers = m;
			names = new String[m];
			for (int i = 0; i < m; i++) {
				names[i] = addressBook.getAddress(i).getNickname();
			}
		}

		final boolean expand = (expandCheckbox.getState()); // is checkbox checked?

		if (col2mems != null
				&& (!expand && col2mems.length == m || expand
						&& col2mems.length == m * (m - 1) / 2 + 1)) {
			return; // don't recalculate twice in a row for the same size
		}

		// fix corner cases missed by the formulas here
		if (numMembers == 1) {
			numColumns = 1;
			col2mems = new int[][] { { 0, -1 } };
			mems2col = new int[][] { { 0 } };
			return;
		} else if (numMembers == 2) {
			numColumns = 2;
			col2mems = new int[][] { { 0, -1 }, { 1, -1 } };
			mems2col = new int[][] { { 0, 1 }, { 0, 0 } };
			return;
		}

		if (!expand) { // if unchecked so only one column per member, then the arrays are trivial
			numColumns = m;
			mems2col = new int[m][m];
			col2mems = new int[numColumns][2];
			for (int i = 0; i < m; i++) {
				col2mems[i][0] = i;
				col2mems[i][1] = -1;
				for (int j = 0; j < m; j++) {
					mems2col[i][j] = j;
				}
			}
			return;
		}

		numColumns = m * (m - 1) / 2 + 1;
		mems2col = new int[m][m];
		col2mems = new int[numColumns][2];

		for (int x = 0; x < m * (m - 1) / 2 + 1; x++) {
			final int d1 = ((m % 2) == 1) ? 0 : 2 * ((x - 1) / (m - 1)); // amount to add to x to skip
			// columns
			col2mems[x][0] = col2mem(m, d1 + x);// find even m answer by asking for m+1 with skipped cols
			col2mems[x][1] = (((m % 2) == 1) || ((x % (m - 1)) > 0) || (x == 0) || (x == m
					* (m - 1) / 2)) ? -1 : col2mem(m, d1 + x + 2);
			final int d = ((m % 2) == 1) ? 0 : 2 * (x / (m - 1)); // amount to add to x to skip columns
			final int a = col2mem(m, d + x);
			final int b = col2mem(m, d + x + 1);
			if (x < m * (m - 1) / 2) { // on the last iteration, x+1 is invalid, so don't record it
				mems2col[b][a] = x;
				mems2col[a][b] = x + 1;
			}
		}
	}

	/**
	 * return the member number for column x, if there are m members. This is set up so each member appears
	 * in multiple columns, and for any two members there will be exactly one location where they have
	 * adjacent columns.
	 * 
	 * The pattern used comes from a Eulerian cycle on the complete graph of m members, formed by combining
	 * floor(m/2) disjoint Eulerian paths on the complete graph of m-1 members.
	 * 
	 * This method assumes an odd number of members. If there are an even number of members, then assume
	 * there is one extra member, use this method, then delete the columns of the fictitious member, and
	 * combine those columns on either side of each deleted one.
	 * 
	 * @param m
	 *            the number of members (must be odd)
	 * @param x
	 *            the column (from 0 to 1+m*(m-1)/2)
	 * @return the member number (from 0 to m-1)
	 */
	private int col2mem(int m, int x) {
		m = (m / 2) * 2 + 1; // if m is even, round up to the nearest odd
		final int i = (x / m) % (m / 2); // the ith Eulerian path on the complete graph of m-1 vertices
		final int j = x % m;       // position along that ith path

		if (j == m - 1)
			return m - 1; // add the mth vertex after each Eulerian path to get a Eulerian cycle
		if ((j % 2) == 0)
			return i + j / 2; // in a given path, every other vertex counts up
		return (m - 2 + i - (j - 1) / 2) % (m - 1); // and every other vertex counts down
	}

	// ////////////////////////////////////////////////////////////////////
	// the following are the methods required by the SwirldState interface
	// ////////////////////////////////////////////////////////////////////

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void init(Platform platform, int id) {
		this.platform = platform;
		this.selfId = id;
		String[] pars = platform.getParameters(); // read delay parameter from config.sys
		if (pars.length < 1 || pars[0].trim().equals("0")) {// default is fast, and so is parameter 0
			platform.setSleepAfterSync(0);
		} else { // parameter 1 is checked which is slow: 1 sync (2 events) per member per second
			platform.setSleepAfterSync(1000);
		}

		platform.setAbout("Hashgraph Demo v. 1.0\n");
		window = platform.createWindow(false); // Uses BorderLayout. Size is chosen by the Platform
		int p = 0; // which parameter to use
		BiFunction<Integer, String, Checkbox> cb = (n, s) -> new Checkbox(s,
				null, pars.length <= n ? false : pars[n].trim().equals("1"));

		slowCheckbox = cb.apply(p++,
				"slow: this member initiates gossip once a second");
		freezeCheckbox = cb.apply(p++, "freeze: don't change this window");
		expandCheckbox = cb
				.apply(p++, "expand: draw more so lines don't cross");
		labelRoundCheckbox = cb.apply(p++, "Labels: Round");
		labelRoundRecCheckbox = cb.apply(p++,
				"Labels: Round received (consensus)");
		labelConsOrderCheckbox = cb.apply(p++, "Labels: Order (consensus)");
		labelConsTimestampCheckbox = cb.apply(p++,
				"Labels: Timestamp (consensus)");
		labelGenerationCheckbox = cb.apply(p++, "Labels: Generation");
		labelCreatorCheckbox = cb.apply(p++, "Labels: Creator ID");
		labelSeqCheckbox = cb.apply(p++, "Labels: Creator Seq");

		slowCheckbox.addItemListener(new ItemListener() {
			public void itemStateChanged(ItemEvent e) {
				platform.setSleepAfterSync(e.getStateChange() == 1 ? 1000 : 0);
			}
		});

		eventLimit = new TextField(pars.length <= p ? "" : pars[p].trim(), 5);
		p++;
		Panel limitRow = new Panel(new FlowLayout(FlowLayout.LEADING));
		limitRow.add(new Label("Display last "), BorderLayout.WEST);
		limitRow.add(eventLimit, BorderLayout.WEST);
		limitRow.add(new Label(" events"), BorderLayout.WEST);
		Panel inputs = new Panel(new GridLayout(0, 1));
		Component[] comps = new Component[] { slowCheckbox, freezeCheckbox,
				expandCheckbox, labelRoundCheckbox, labelRoundRecCheckbox,
				labelConsOrderCheckbox, labelConsTimestampCheckbox,
				labelGenerationCheckbox, labelCreatorCheckbox,
				labelSeqCheckbox, limitRow };
		for (Component c : comps) {
			inputs.add(c, BorderLayout.WEST);
		}
		window.add(inputs, BorderLayout.NORTH);
		picture = new Picture();
		window.add(picture, BorderLayout.CENTER);
		window.pack();
		window.setVisible(true);
		for (int i = 0; i < comps.length; i++) {
			comps[i].setBounds(0, i * 1, 480, 16);
		}
		inputs.setPreferredSize(new Dimension(480, 40 + 16 * comps.length));
		window.pack();
		limitRow.setSize(new Dimension(480, 200));
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void run() {
		while (true) {
			if (window != null && !freezeCheckbox.getState()) {
				eventsCache = platform.getAllEvents();
				// after this getAllEvents call, the set of events to draw is frozen
				// for the duration of this screen redraw. However, the syncing
				// continues in the background, so later events will continue to be
				// received. If this is run in fast mode (with the "slow"
				// checkbox not checked) with all members on a single machine,
				// then the process can be so fast that every event will become
				// part of the consensus before it is drawn on the screen, and
				// there may be few or no non-consensus events visible. When it's
				// slower, though, there will generally be both consensus and
				// non-consensus events visible.
				window.repaint();
			}
			try {
				Thread.sleep(250);// redraw the screen 4 times a second
			} catch (Exception e) {
			}
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void preEvent() {
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public SwirldState newState() {
		return new HashgraphDemoState();
	}
}
