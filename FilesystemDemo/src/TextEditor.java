
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

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.function.*;

import javax.swing.*;

import com.swirlds.platform.*;
import com.swirlds.platform.fc.*;

/**
 * A simple text editor that saves files on a Swirlds Fast Copyable Filesystem. Every save is transmitted as
 * a transaction to peer nodes. This code also demonstrates how to invoke the graphical FileExplorer to load
 * or save files in a filesystem.
 */
public class TextEditor extends JPanel {
	private static final long serialVersionUID = 1L;

	/** The Swirlds Platform object for this app and node */
	private Platform platform;

	private JFrame topFrame;
	private JTextArea text;
	private JButton saveButton;
	private JTextField statusBar;

	private JTextField docPath;

	/** use openOn() */
	private TextEditor(JFrame topFrame, Platform platform) {
		this.topFrame = topFrame;
		this.platform = platform;
		build();
	}

	/** Open a GUI */
	public static TextEditor openOn(Platform platform)
			throws InterruptedException {
		class Holder {
			TextEditor val;
		}
		;
		Holder h = new Holder();
		SwingUtilities.invokeLater(() -> {
			openOn1(platform, (wp) -> {
				synchronized (h) {
					h.val = wp;
					h.notifyAll();
				}
			});
		});
		synchronized (h) {
			while (h.val == null)
				h.wait();
			return h.val;
		}
	}

	private static void openOn1(Platform platform, Consumer<TextEditor> c) {
		JFrame frame = platform.createWindow(false);

		TextEditor wp = new TextEditor(frame, platform);
		frame.getContentPane().add(wp);

		frame.pack();
		frame.setVisible(true);
		c.accept(wp);
	}

	/** Update the text editor's status bar with some text */
	public void status(String text) {
		SwingUtilities.invokeLater(() -> {
			statusBar.setText(text);
		});
	}

	private void build() {
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

		text = new JTextArea(10, 25);
		add(text);

		docPath = new JTextField();
		setUnitHeight(docPath);
		docPath.setEditable(false);
		add(docPath);

		JPanel buttons = new JPanel();
		JButton loadButton = new JButton("Load");
		loadButton.addActionListener(this::load);
		buttons.add(loadButton);
		saveButton = new JButton("Save");
		saveButton.addActionListener(this::save);
		saveButton.setEnabled(false);
		buttons.add(saveButton);
		JButton saveAsButton = new JButton("Save As");
		saveAsButton.addActionListener(this::saveAs);
		buttons.add(saveAsButton);
		add(buttons);

		statusBar = new JTextField();
		statusBar.setEditable(false);
		setUnitHeight(statusBar);
		add(statusBar);
	}

	private void setUnitHeight(JTextField field) {
		field.setMaximumSize(
				new Dimension((int) field.getMaximumSize().getWidth(), 1));
	}

	/** Open a file explorer that allows loading a file into the edit pane */
	private void load(ActionEvent e) {
		try {
			FilesystemDemoState state = getState();
			FilesystemFC fs = state.getFS();
			String pathname = FileExplorer.choose(topFrame, fs, false);
			if (pathname != null && fs.isFile(pathname)) {
				text.setText(state.fileContents(pathname));
				setDocPath(pathname);
			}
		} catch (IOException e1) {
			showError(e1);
		}
	}

	private FilesystemDemoState getState() {
		return ((FilesystemDemoState) platform.getState());
	}

	private static final long[] txHints = new long[0];

	/** Open a file explorer that allows saving the current text */
	private void saveAs(ActionEvent e) {
		try {
			FilesystemDemoState state = getState();
			boolean done = false;
			while (!done) {
				String pathname = FileExplorer.choose(topFrame, state.getFS(),
						true);
				if (pathname == null)
					done = true;
				else if (state.getFS().isDir(pathname))
					showMessage(String.format("Sorry, \"%s\" is a directory.",
							pathname));
				else {
					done = true;
					publishTx(pathname, text.getText());
					setDocPath(pathname);
				}
			}
		} catch (IOException e1) {
			showError(e1);
		}
	}

	private void publishTx(String pathname, String text2) throws IOException {
		platform.createTransaction(
				new FileTransaction(pathname, text2).serialize(), txHints);
	}

	private void save(ActionEvent e) {
		try {
			publishTx(docPath.getText(), text.getText());
		} catch (IOException e1) {
			showError(e1);
		}
	}

	private void setDocPath(String pathname) {
		docPath.setText(pathname);
		saveButton.setEnabled(true);
	}

	private void showError(IOException e1) {
		showMessage(e1.getMessage());
	}

	private void showMessage(String message) {
		JOptionPane.showMessageDialog(topFrame, message);
	}
}
