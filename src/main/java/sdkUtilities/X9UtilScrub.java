package sdkUtilities;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.x9ware.actions.X9Exception;
import com.x9ware.apacheIO.FilenameUtils;
import com.x9ware.beans.X9ScrubBean;
import com.x9ware.create.X9ScrubXml;
import com.x9ware.tools.X9CsvWriter;
import com.x9ware.tools.X9File;
import com.x9ware.tools.X9Task;

/**
 * X9UtilScrub is part of our utilities package which scrubs the contents of an x9 file (both x9 and
 * image components) to remove proprietary and confidential information. A summary of scrubbed field
 * actions is written to an output text file.
 *
 * @author X9Ware LLC. Copyright(c) 2012-2018 X9Ware LLC. All Rights Reserved. This is proprietary
 *         software as developed and licensed by X9Ware LLC under the exclusive legal right of the
 *         copyright holder. All licensees are provided the right to use the software only under
 *         certain conditions, and are explicitly restricted from other specific uses including
 *         modification, sharing, reuse, redistribution, or reverse engineering.
 */
public final class X9UtilScrub {

	/**
	 * X9UtilWorkUnit instance which describes the unit of work assigned to us.
	 */
	private final X9UtilWorkUnit workUnit;

	/*
	 * Private.
	 */
	private final X9ScrubXml x9scrubXml = new X9ScrubXml();
	private final int maximumThreadCount;
	private final int maximumFilesPerIteration;
	private final boolean isLoggingEnabled;
	private X9CsvWriter csvWriter;

	/*
	 * Constants.
	 */
	private static final int MAXIMUM_FILES_PER_THREAD = 5;

	/**
	 * Logger instance.
	 */
	private static final Logger LOGGER = LoggerFactory.getLogger(X9UtilScrub.class);

	/*
	 * X9UtilScrub Constructor.
	 *
	 * @param work_Unit current work unit
	 */
	public X9UtilScrub(final X9UtilWorkUnit work_Unit) {
		workUnit = work_Unit;
		isLoggingEnabled = workUnit.isCommandSwitchSet(X9UtilWorkUnit.SWITCH_LOGGING);
		maximumThreadCount = workUnit.getThreadCount();
		maximumFilesPerIteration = X9Task.getSuggestedConcurrentThreads()
				* MAXIMUM_FILES_PER_THREAD;
		final X9ScrubBean scrubBean = x9scrubXml.loadScrubConfiguration(workUnit.secondaryFile);

		/*
		 * Log input xml when requested.
		 */
		if (isLoggingEnabled) {
			LOGGER.info(scrubBean.toString());
		}
	}

	/**
	 * Scrub one or more input files to remove PII (personally identifiable information).
	 *
	 * @return exit status
	 */
	public int process() {
		int exitStatus = X9UtilBatch.EXIT_STATUS_ABORTED;
		try (final X9CsvWriter csv_Writer = new X9CsvWriter(workUnit.resultsFile)) {
			csvWriter = csv_Writer;
			exitStatus = allocateMonitorAndRunThreads(workUnit.getInputFileList(),
					workUnit.outputFile);
		} catch (final Exception ex) {
			throw X9Exception.abort(ex);
		}
		return exitStatus;
	}

	/**
	 * Allocate our task monitor and run background threads.
	 *
	 * @param fileList
	 *            list of files to be processed
	 * @param outputFileOrFolder
	 *            output file (if running as single file) or folder (when running multi-file)
	 * @return exit status
	 */
	private int allocateMonitorAndRunThreads(final List<X9File> fileList,
			final File outputFileOrFolder) {
		/*
		 * Verify output is actually a folder when we are running as multi-file.
		 */
		if (workUnit.isCommandSwitchSet(X9UtilWorkUnit.SWITCH_MULTI_FILE)) {
			workUnit.verifyOutputIsFolder();
		}

		/*
		 * Build a list of all files to be scrubbed. When running as multi-file, the output file
		 * will be created within the provided output folder using the input file name.
		 */
		int totalEntries = 0;
		final List<X9UtilScrubEntry> entryWorkingList = new ArrayList<>();
		for (final File file : fileList) {
			totalEntries++;
			final File inputFile = file;
			final File outputFile = workUnit.isCommandSwitchSet(X9UtilWorkUnit.SWITCH_MULTI_FILE)
					? new File(outputFileOrFolder, FilenameUtils.getName(inputFile.toString()))
					: outputFileOrFolder;
			entryWorkingList.add(new X9UtilScrubEntry(workUnit, x9scrubXml, inputFile, outputFile));
		}

		/*
		 * Scrub all files.
		 */
		int exitStatus = 0;
		int iterationCount = 0;
		if (entryWorkingList.size() > 0) {
			/*
			 * Build a list of all files to be scrubbed during this iteration. The list size is
			 * limited for several reasons. First is that it keeps the thread cpu time from becoming
			 * too excessive, which could trigger the monitor to falsely attempt to interrupt the
			 * thread. Second is that if any thread unexpectedly aborts, then as least we minimize
			 * the number of files that will not be processed due to the exception.
			 */
			iterationCount++;
			int count = 0;
			final List<X9UtilScrubEntry> entryList = new ArrayList<>();
			while (count < maximumFilesPerIteration && entryWorkingList.size() > 0) {
				count++; // count and pull from the end to improve efficiency
				entryList.add(entryWorkingList.remove(entryWorkingList.size() - 1));
			}

			/*
			 * Allocate our task monitor and run background threads.
			 */
			final X9UtilScrubMonitor taskMonitor = new X9UtilScrubMonitor(maximumThreadCount,
					workUnit, csvWriter);
			exitStatus = Math.max(taskMonitor.runWaitLog(entryList), exitStatus);
		}

		/*
		 * Log iteration statistics.
		 */
		LOGGER.info("all tasks completed; iterationCount({}) totalEntries({}) exitStatus({})",
				iterationCount, totalEntries, exitStatus);

		/*
		 * Return maximum exit status.
		 */
		return exitStatus;
	}

}
