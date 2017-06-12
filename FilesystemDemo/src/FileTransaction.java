
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

import java.io.*;

/** The pathname and contents of a file, to be transmitted over the network */
public class FileTransaction implements Serializable {
	private static final long serialVersionUID = 1L;

	String pathname;
	String text;

	public FileTransaction(String pathname, String text) {
		this.pathname = pathname;
		this.text = text;
	}

	public byte[] serialize() throws IOException {
		ByteArrayOutputStream b = new ByteArrayOutputStream();
		DataOutputStream o = new DataOutputStream(b);
		o.writeUTF(pathname);
		o.writeUTF(text);
		o.close();
		return b.toByteArray();
	}

	public static FileTransaction deserialize(byte[] b)
			throws IOException, ClassNotFoundException {
		DataInputStream o = new DataInputStream(new ByteArrayInputStream(b));
		String pathname2 = o.readUTF();
		String text2 = o.readUTF();
		FileTransaction result = new FileTransaction(pathname2, text2);
		o.close();
		return result;
	}
}
