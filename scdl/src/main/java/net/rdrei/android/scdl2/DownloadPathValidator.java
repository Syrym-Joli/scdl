package net.rdrei.android.scdl2;

import java.io.File;

public class DownloadPathValidator {

	public enum ErrorCode {
		INSECURE_PATH, PERMISSION_DENIED, RELATIVE_PATH, NOT_A_DIRECTORY,
	};

	// Injectable
	public DownloadPathValidator() {
	}

	/**
	 * Using a single class to catch all validation related errors is a
	 * deliberate choice. It reduces boilerplate a lot as exceptions are all
	 * caught in one place and handled the same way except for the error message
	 * provided.
	 * 
	 * @author pascal
	 * 
	 */
	public static class DownloadPathValidationException extends Exception {

		private final ErrorCode mErrorCode;
		private static final long serialVersionUID = 1L;

		public DownloadPathValidationException(final ErrorCode code) {
			mErrorCode = code;
		}

		public ErrorCode getErrorCode() {
			return mErrorCode;
		}
	}

	public void validateCustomPathOrThrow(final String path)
			throws DownloadPathValidationException {
		if (path.contains("..")) {
			throw new DownloadPathValidationException(ErrorCode.INSECURE_PATH);
		}

		final File file = new File(path);

		if (file.exists()) {
			if (!file.isDirectory()) {
				throw new DownloadPathValidationException(
						ErrorCode.NOT_A_DIRECTORY);
			}
		} else {
			if (!file.mkdirs()) {
				throw new DownloadPathValidationException(
						ErrorCode.PERMISSION_DENIED);
			}
		}

		if (!file.canWrite()) {
			throw new DownloadPathValidationException(
					ErrorCode.PERMISSION_DENIED);
		}
	}
}
