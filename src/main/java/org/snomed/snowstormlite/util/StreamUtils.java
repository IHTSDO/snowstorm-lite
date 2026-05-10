package org.snomed.snowstormlite.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.function.IntConsumer;

public class StreamUtils {

	public static int copyWithProgress(InputStream inputStream, OutputStream outputStream, int totalStreamLength, String messageFormat) throws IOException {
		long basis = totalStreamLength <= 0 ? 1L : totalStreamLength;
		return copyWithProgress(inputStream, outputStream, basis, messageFormat, null);
	}

	public static int copyWithProgress(InputStream inputStream, OutputStream outputStream, long totalStreamLength,
			String messageFormat, IntConsumer onPercent) throws IOException {
		long basis = totalStreamLength <= 0 ? 1L : totalStreamLength;
		int byteCount = 0;
		int bytesRead;
		int percentageLogged = -1;
		int lastCallbackPercent = -1;
		for (byte[] buffer = new byte[4096]; (bytesRead = inputStream.read(buffer)) != -1; byteCount += bytesRead) {
			outputStream.write(buffer, 0, bytesRead);

			int percentage = (int) Math.floor(Math.min(100d, (byteCount / (double) basis) * 100d));
			if (percentage % 10 == 0 && percentage > percentageLogged) {
				if (messageFormat != null) {
					System.out.printf(messageFormat + "%n", percentage);
				}
				percentageLogged = percentage;
			}
			if (onPercent != null && percentage != lastCallbackPercent) {
				onPercent.accept(percentage);
				lastCallbackPercent = percentage;
			}
		}

		outputStream.flush();
		if (onPercent != null && lastCallbackPercent < 100) {
			onPercent.accept(100);
		}
		return byteCount;
	}
}
