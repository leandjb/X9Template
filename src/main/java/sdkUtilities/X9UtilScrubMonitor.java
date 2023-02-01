package sdkUtilities;

import java.util.List;

import com.x9ware.base.X9SdkBase;
import com.x9ware.tools.X9CsvWriter;
import com.x9ware.tools.X9TaskMonitor;
import com.x9ware.tools.X9TaskWorker;

/**
 * X9UtilScrubMonitor directs file scrub activities against a series of one or more files. The exact
 * number of started threads is dependent on the number of available processors and system property
 * settings. Performance is maximized by splitting input across internally created lists which are
 * then processed independently by concurrent threads. Statistics are accumulated within each worker
 * task and aggregated and reported on completion.
 *
 * @author X9Ware LLC. Copyright(c) 2012-2018 X9Ware LLC. All Rights Reserved. This is proprietary
 *         software as developed and licensed by X9Ware LLC under the exclusive legal right of the
 *         copyright holder. All licensees are provided the right to use the software only under
 *         certain conditions, and are specifically restricted from other specific uses including
 *         modification, sharing, reuse, redistribution, or reverse engineering.
 */
public final class X9UtilScrubMonitor extends X9TaskMonitor<X9UtilScrubEntry> {

	/*
	 * Private.
	 */
	private final X9UtilWorkUnit workUnit;
	private final X9CsvWriter csvWriter;

	/**
	 * X9UtilScrubMonitor Constructor.
	 *
	 * @param maximumThreadCount
	 *            maximum thread count
	 * @param work_Unit
	 *            current work unit
	 * @param csv_Writer
	 *            common csv writer
	 */
	public X9UtilScrubMonitor(final int maximumThreadCount, final X9UtilWorkUnit work_Unit,
			final X9CsvWriter csv_Writer) {
		super(maximumThreadCount);
		workUnit = work_Unit;
		csvWriter = csv_Writer;
	}

	@Override
	public X9TaskWorker<X9UtilScrubEntry> allocateNewWorkerInstance(
			final List<X9UtilScrubEntry> workerList) {
		/*
		 * Allocate a new and independent sdkBase for each file to be processed (since we are
		 * processing multiple files concurrently from different background threads).
		 */
		final X9SdkBase sdkBase = workUnit.getNewSdkBase();

		/*
		 * Allocate and return a new scrub worker which will process a series of files.
		 */
		return new X9UtilScrubWorker(sdkBase, this, workerList, csvWriter);
	}

}
