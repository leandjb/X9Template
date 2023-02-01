package sdkUtilities;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.x9ware.actions.X9Exception;
import com.x9ware.base.X9Object;
import com.x9ware.base.X9Sdk;
import com.x9ware.base.X9SdkBase;
import com.x9ware.base.X9SdkFactory;
import com.x9ware.base.X9SdkIO;
import com.x9ware.base.X9SdkObject;
import com.x9ware.beans.X9ScrubBean;
import com.x9ware.core.X9Reader;
import com.x9ware.core.X9TotalsXml;
import com.x9ware.create.X9Scrub;
import com.x9ware.create.X9Scrub937;
import com.x9ware.create.X9ScrubXml;
import com.x9ware.options.X9Options;
import com.x9ware.toolbox.X9RandomizedList;
import com.x9ware.tools.X9CsvWriter;
import com.x9ware.tools.X9FileUtils;
import com.x9ware.tools.X9TallyMap;
import com.x9ware.tools.X9TaskMonitor;
import com.x9ware.tools.X9TaskWorker;
import com.x9ware.tools.X9TempFile;
import com.x9ware.validate.X9TrailerManager;
import com.x9ware.validate.X9TrailerManager937;

/**
 * X9UtilScrubWorker scrubs a single file as initiated from a worker task.
 *
 * @author X9Ware LLC. Copyright(c) 2012-2018 X9Ware LLC. All Rights Reserved. This is proprietary
 *         software as developed and licensed by X9Ware LLC under the exclusive legal right of the
 *         copyright holder. All licensees are provided the right to use the software only under
 *         certain conditions, and are specifically restricted from other specific uses including
 *         modification, sharing, reuse, redistribution, or reverse engineering.
 */
public class X9UtilScrubWorker extends X9TaskWorker<X9UtilScrubEntry> {

	/**
	 * X9SdkBase instance for this environment as assigned by our constructor.
	 */
	private final X9SdkBase sdkBase;

	/*
	 * Private.
	 */
	private final X9CsvWriter csvWriter;
	private X9UtilWorkUnit workUnit;
	private X9ScrubXml scrubXml;
	private File inputFile;
	private File outputFile;
	private boolean isLoggingEnabled;

	/**
	 * Logger instance.
	 */
	private static final Logger LOGGER = LoggerFactory.getLogger(X9UtilScrubWorker.class);

	/**
	 * X9UtilScrubWorker Constructor.
	 *
	 * @param sdk_Base
	 *            sdkBase for this worker thread
	 * @param monitor
	 *            associated task monitor for call backs
	 * @param scrubList
	 *            entry list to be scrubbed
	 * @param csv_Writer
	 *            common csv writer
	 */
	public X9UtilScrubWorker(final X9SdkBase sdk_Base,
			final X9TaskMonitor<X9UtilScrubEntry> monitor, final List<X9UtilScrubEntry> scrubList,
			final X9CsvWriter csv_Writer) {
		super(monitor, scrubList);
		sdkBase = sdk_Base;
		csvWriter = csv_Writer;
	}

	@Override
	public boolean processOneEntry(final X9UtilScrubEntry scrubEntry) {
		/*
		 * Get information from the current scrub entry.
		 */
		workUnit = scrubEntry.getWorkUnit();
		inputFile = scrubEntry.getInputFile();
		outputFile = scrubEntry.getOutputFile();
		scrubXml = scrubEntry.getScrubXml();
		isLoggingEnabled = workUnit.isCommandSwitchSet(X9UtilWorkUnit.SWITCH_LOGGING);

		/*
		 * Allocate a temporary output file that will be renamed on completion.
		 */
		final X9TempFile x9tempFile = X9UtilWorkUnit.getTempFileInstance(outputFile);

		/*
		 * Set the configuration name when provided; we otherwise default to file header.
		 */
		workUnit.autoBindToCommandLineConfiguration(sdkBase);

		/*
		 * Our sdkBase is shared by all scrubs run from this same background thread. We now invoke
		 * scrub within sdkIO try-with-resources which will auto-close on completion.
		 */
		final X9TotalsXml x9totalsXml = new X9TotalsXml();
		final X9TrailerManager x9trailerManager = new X9TrailerManager937(sdkBase);
		final X9Sdk sdk = X9SdkFactory.getSdk(sdkBase);
		try (final X9SdkIO sdkIO = sdk.getSdkIO()) {
			/*
			 * Scrub processing.
			 */
			scrubOneFile(sdkIO, x9tempFile.getTemp(), x9trailerManager);
		} catch (final Exception ex) {
			/*
			 * Set message when aborted.
			 */
			x9totalsXml.setAbortMessage(ex.toString());
			throw X9Exception.abort(ex);
		} finally {
			try {
				/*
				 * Release all stored x9objects.
				 */
				sdkBase.getObjectManager().reset();

				/*
				 * Rename on completion.
				 */
				x9tempFile.renameTemp();
			} catch (final Exception ex) {
				/*
				 * Set message when aborted.
				 */
				x9totalsXml.setAbortMessage(ex.toString());
				throw X9Exception.abort(ex);
			} finally {
				/*
				 * Populate our file totals.
				 */
				x9totalsXml.setTotals(inputFile, x9trailerManager);

				/*
				 * Write summary totals when requested by command line switches.
				 */
				workUnit.writeSummaryTotals(x9totalsXml);
				LOGGER.info("scrub {}", x9totalsXml.getTotalsString());
			}
		}

		/*
		 * Return true for meaningful work performed.
		 */
		return true;
	}

	/**
	 * Scrub a single file with an exception thrown on any errors.
	 *
	 * @param sdkIO
	 *            current sdkIO
	 * @param tempFile
	 *            temp file to be written
	 * @param x9trailerManager
	 *            current trailer manager for file level totals
	 * @throws Exception
	 */
	private void scrubOneFile(final X9SdkIO sdkIO, final File tempFile,
			final X9TrailerManager x9trailerManager) throws Exception {
		/*
		 * Get the use case file and allocate a random list. We allow the file name defined in the
		 * xml definition to be relative or absolute.
		 */
		final X9ScrubBean.ScrubAttr scrubAttr = scrubXml.getAttr();
		final String useCaseName = scrubAttr.useCaseFileName;
		final File useCaseFile = X9FileUtils.appendFolderWhenNotAbsolute(useCaseName,
				X9Options.getuseCaseFolder());

		if (!X9FileUtils.existsWithPathTracing(useCaseFile)) {
			throw X9Exception.abort("useCaseFile notFound({})", useCaseFile);
		}

		final X9RandomizedList randomUseCaseList = new X9RandomizedList(sdkBase, useCaseFile,
				X9Scrub.SCRUB_VALUES_PER_LINE, X9Scrub.NUMBER_OF_LONGS_PER_LINE_IS_ZERO);

		final int useCaseCount = randomUseCaseList.getNumberOfEntries();
		final boolean isRandomUseCaseListValid = randomUseCaseList.isValidList();

		if (!isRandomUseCaseListValid) {
			throw X9Exception.abort("useCase file format is invalid at lineNumber({}) content({})",
					randomUseCaseList.getInvalidLineNumber(),
					randomUseCaseList.getInvalidContent());
		}

		if (useCaseCount == 0) {
			throw X9Exception.abort("useCase file is empty");
		}

		LOGGER.info("scrub with useCaseCount({}) inputFile({}) outputFile({})", useCaseCount,
				inputFile, tempFile);

		/*
		 * Set the list for sequential retrieval.
		 */
		randomUseCaseList.setSequentialRetrieval();

		/*
		 * Open and read the x9 file to populate x9objects.
		 */
		try (final X9Reader x9reader = sdkIO.openInputFile(inputFile)) {
			/*
			 * Get first x9 record.
			 */
			X9SdkObject sdkObject = sdkIO.readNext();

			/*
			 * Read until end of file.
			 */
			while (sdkObject != null) {
				/*
				 * Create and store a new x9object for this x9 record.
				 */
				final X9Object x9o = sdkIO.createAndStoreX9Object();

				/*
				 * Log when enabled via a command line switch.
				 */
				if (isLoggingEnabled) {
					LOGGER.info("x9 recordNumber({}) data({})", x9o.x9ObjIdx,
							new String(x9o.x9ObjData));
				}

				/*
				 * Accumulate and roll totals.
				 */
				x9trailerManager.accumulateAndRollTotals(x9o);

				/*
				 * Get next record.
				 */
				sdkObject = sdkIO.readNext();
			}
		}

		/*
		 * Assign x9header indexes.
		 */
		sdkBase.getObjectManager().assignHeaderObjectIndexReferences();

		/*
		 * Open the image reader (note that it will be closed by sdkIO auto-close).
		 */
		sdkIO.openImageReader(inputFile);

		/*
		 * Scrub the file and write summary actions to our output csv file.
		 */
		final X9Scrub x9scrub = new X9Scrub937(sdkBase, scrubXml);
		x9scrub.scrubToFile(tempFile);
		writeSummaryActions(x9scrub.getTallyMap());
	}

	/**
	 * Write summary actions for a single file to the output csv writer.
	 *
	 * @param x9tallyMap
	 *            current tally map of actions
	 * @throws IOException
	 */
	private synchronized void writeSummaryActions(final X9TallyMap x9tallyMap) throws IOException {
		/*
		 * Walk the tree map and write the scrub actions csv to the output csv file. We must be
		 * synchronized to ensure that our output csv lines remain together in the csv output.
		 */
		csvWriter.startNewLine();
		csvWriter.addField(inputFile.toString());
		csvWriter.addField(outputFile.toString());
		csvWriter.write();

		for (final Entry<String, AtomicInteger> entry : x9tallyMap.entrySet()) {
			csvWriter.startNewLine();
			csvWriter.addField(entry.getKey());
			csvWriter.addField(Integer.toString(entry.getValue().get()));
			csvWriter.write();
		}

		csvWriter.startNewLine();
		csvWriter.addField("end");
		csvWriter.write();
	}

}
