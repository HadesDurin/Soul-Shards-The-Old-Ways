package sstow.utils;

import sstow.Main;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class TOWLogger {
	private static Logger logger = LogManager.getLogger(Main.MODID);

	public static void logInfo(String message) {
		logger.log(Level.INFO, message);
	}

	public static void logWarn(String message) {
		logger.log(Level.WARN, message);
	}

	public static void logFatal(String message) {
		logger.log(Level.FATAL, message);
	}

	public static void logDebug(String message) {
		logger.log(Level.DEBUG, message);
	}

	public static void log(Level level, String message) {
		logger.log(level, message);
	}
}
