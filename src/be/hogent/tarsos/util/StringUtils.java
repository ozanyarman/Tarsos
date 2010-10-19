package be.hogent.tarsos.util;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CodingErrorAction;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class StringUtils {

	private static String filterNonAscii(final String inString) {
		// Create the encoder and decoder for the character encoding
		final Charset charset = Charset.forName("US-ASCII");
		final CharsetDecoder decoder = charset.newDecoder();
		final CharsetEncoder encoder = charset.newEncoder();
		// This line is the key to removing "unmappable" characters.
		encoder.replaceWith("_".getBytes());
		encoder.onUnmappableCharacter(CodingErrorAction.REPLACE);
		String result = inString;
		try {
			// Convert a string to bytes in a ByteBuffer
			final ByteBuffer bbuf = encoder.encode(CharBuffer.wrap(inString));
			// Convert bytes in a ByteBuffer to a character ByteBuffer and then
			// to a string.
			final CharBuffer cbuf = decoder.decode(bbuf);
			result = cbuf.toString();
		} catch (final CharacterCodingException cce) {
			FileUtils.LOG.severe("Exception during character encoding/decoding: " + cce.getMessage());
		}

		return result;
	}

	/**
	 * replaces UTF-8 characters and spaces with _ . Returns the complete path.
	 * <p>
	 * E.g. <code>/tmp/01.��skar ton.mp3</code> is converted to:
	 * <code>/tmp/01.__skar_ton.mp3</code>
	 * </p>
	 * 
	 * @param data
	 *            the data to sanitize
	 * @return the complete sanitized path.
	 */
	public static String sanitize(final String data) {
		final String baseName = FileUtils.basename(data);
		String newBaseName = baseName.replaceAll(" ", "_");
		newBaseName = newBaseName.replaceAll("\\(", "-");
		newBaseName = newBaseName.replaceAll("\\)", "-");
		newBaseName = newBaseName.replaceAll("&", "and");
		newBaseName = filterNonAscii(newBaseName);
		return data.replace(baseName, newBaseName);
	}

	/**
	 * Calculates an MD5 hash for a text.
	 * 
	 * @param dataToEncode
	 *            The data to encode.
	 * @return A text representation of a hexadecimal value of lenght 32.
	 */
	public static String messageDigestFive(final String dataToEncode) {
		MessageDigest m;
		try {
			m = MessageDigest.getInstance("MD5");
			byte[] data = dataToEncode.getBytes();
			m.update(data, 0, data.length);
			BigInteger i = new BigInteger(1, m.digest());
			return String.format("%1$032X", i);
		} catch (NoSuchAlgorithmException e) {
			// MD5 Should be supported!
			throw new IllegalStateException(e);
		}
	}

}