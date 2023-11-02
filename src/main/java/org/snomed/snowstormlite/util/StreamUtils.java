package org.snomed.snowstormlite.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class StreamUtils {

	public static int copyWithProgress(InputStream inputStream, OutputStream outputStream, int totalStreamLength, String messageFormat) throws IOException {
		int byteCount = 0;
		int bytesRead;
		int percentageLogged = -1;
		for (byte[] buffer = new byte[4096]; (bytesRead = inputStream.read(buffer)) != -1; byteCount += bytesRead) {
			outputStream.write(buffer, 0, bytesRead);

			float percentageFloat = ((float) byteCount / (float) totalStreamLength) * 100;
			int percentage = (int) Math.floor(percentageFloat);
			if (percentage % 10 == 0 && percentage > percentageLogged) {
				System.out.printf(messageFormat + "%n", percentage);
				percentageLogged = percentage;
			}
		}

		outputStream.flush();
		return byteCount;
	}

}
