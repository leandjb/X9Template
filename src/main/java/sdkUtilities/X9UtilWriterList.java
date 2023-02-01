package sdkUtilities;

import java.io.File;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.x9ware.actions.X9Exception;
import com.x9ware.base.X9SdkIO;
import com.x9ware.beans.X9HeaderAttr937;
import com.x9ware.core.X9HeaderXml937;
import com.x9ware.core.X9Writer;
import com.x9ware.tools.X9CsvLine;
import com.x9ware.tools.X9CsvReader;
import com.x9ware.tools.X9Numeric;

/**
 * X9UtilWriterList constructs a list of items which are optionally reordered by customer and then
 * also optionally restructured to be either multi-item or single item deposits. Items are reordered
 * by customer when the input consists of "t25" lines that includes a batch profile name. Items are
 * restructured into single item deposits when that option is selected within headerXml.
 *
 * @author X9Ware LLC. Copyright(c) 2012-2018 X9Ware LLC. All Rights Reserved. This is proprietary
 *         software as developed and licensed by X9Ware LLC under the exclusive legal right of the
 *         copyright holder. All licensees are provided the right to use the software only under
 *         certain conditions, and are explicitly restricted from other specific uses including
 *         modification, sharing, reuse, redistribution, or reverse engineering.
 */
public class X9UtilWriterList {

	/*
	 * Constants.
	 */
	private static final char COMMA = ',';
	private static final int SEVEN_DIGITS = 7;

	/**
	 * Logger instance.
	 */
	private static final Logger LOGGER = LoggerFactory.getLogger(X9UtilWriter.class);

	/**
	 * X9UtilWriterList Constructor is private (static class).
	 */
	private X9UtilWriterList() {
	}

	/**
	 * Load the incoming csv file into a sorted map which is ordered by depositor.
	 *
	 * @param sdkIO
	 *            current sdkIO
	 * @param x9writer
	 *            current x9writer
	 * @param workUnit
	 *            current work unit
	 * @param x9headerXml937
	 *            x9headerXml937 instance
	 * @param isAbortIfEndMissing
	 *            true if we should abort when end is missing
	 * @return csv line map
	 */
	public static Map<String, X9UtilWriterCsvLines> loadCsv(final X9SdkIO sdkIO,
			final X9Writer x9writer, final X9UtilWorkUnit workUnit,
			final X9HeaderXml937 x9headerXml937, final boolean isAbortIfEndMissing) {
		/*
		 * Read the csv into a map (to reorder by profile) and close on completion.
		 */
		final X9UtilWriterProfileMap itemMap = new X9UtilWriterProfileMap();
		try (X9CsvReader csvReader = sdkIO.getCsvReader()) {
			/*
			 * Load the X9HeaderXml file when present on the command line. Our current documentation
			 * indicates that this must be provided as the second command line file. We then extend
			 * compatibility to R3.11 and earlier when this definition was provided via the xml
			 * headers switch. The change to a command line file was made with R3.12 as part of
			 * standardization when gathering command line files for the x9utilities UI console.
			 */
			File headersFile = workUnit.secondaryFile;
			if (headersFile == null) {
				final String fileName = workUnit
						.getCommandSwitchValue(X9UtilWorkUnit.SWITCH_HEADERS_XML);
				if (StringUtils.isNotBlank(fileName)) {
					headersFile = new File(fileName);
					LOGGER.info("headerXml assigned from command line xmlFile({})", headersFile);
				}
			}

			/*
			 * Load headerXml when it must be provided as the first csv line.
			 */
			if (headersFile == null) {
				/*
				 * Get the first csv input line which must contain our header directive.
				 */
				final String[] firstLine = csvReader.getNextIncomingStringArray();
				LOGGER.info("headerLine({})", StringUtils.trim(StringUtils.join(firstLine, ',')));

				/*
				 * Process the user supplied header directive.
				 */
				if (firstLine == null) {
					throw X9Exception.abort("missing header line");
				} else if (firstLine.length != 2) {
					throw X9Exception.abort(
							"header line field count must be two; found({}) content({})",
							firstLine.length, StringUtils.join(firstLine, '|'));
				} else if (StringUtils.equals(firstLine[0], X9Writer.CSV_LINE_TYPE_HEADER_XML)) {
					/*
					 * Load the file header from an externally defined file.
					 */
					final String fileName = firstLine[1];
					LOGGER.info("headerXml assigned from xmlFile({})", fileName);
					headersFile = new File(fileName);
				} else {
					/*
					 * Invalid csv header record type.
					 */
					throw X9Exception.abort("first input line must be headerXml; found({})",
							firstLine[0]);
				}
			}

			/*
			 * Read the headerXml definition from the external xml file.
			 */
			x9headerXml937.readHeaderDefinition(headersFile);
			final X9HeaderAttr937 headerAttr = x9headerXml937.getAttr();

			/*
			 * Load the csv rows to an internal list until "end" is encountered, with the purpose to
			 * accumulate all check amounts (needed to insert the credit automatically).
			 */
			String batchProfile = "";
			boolean isEndEncountered = false;
			X9CsvLine csvLine = csvReader.getNextCsvLine();
			while (csvLine != null && csvLine.isPopulated() && !isEndEncountered) {
				/*
				 * Check if end encountered.
				 */
				final int lineNumber = csvLine.getLineNumber();
				final String[] record = csvLine.getCsvArray();
				isEndEncountered = StringUtils.equals(record[0], X9Writer.CSV_LINE_TYPE_END);
				if (isEndEncountered) {
					/*
					 * Indicate that "end" has been encountered on the csv input file.
					 */
					LOGGER.info("end encountered at csv lineNumber({})", lineNumber);
				} else if (StringUtils.equals(record[0], X9Writer.CSV_LINE_TYPE_BATCH_PROFILE)) {
					if (record.length == 2) {
						/*
						 * Set the batch profile name which will be assigned to all subsequent csv
						 * lines. This action is highly dependent on csv lines maintaining their
						 * original order within the item map.
						 */
						batchProfile = record[1];
					} else {
						throw X9Exception.abort("batchProfile must be two columns; content({})",
								StringUtils.join(record, COMMA));
					}
				} else {
					/*
					 * Get the batch profile name when provided on the "t25" csv array. We provide
					 * this alternative as a single line csv solution, where the transaction and the
					 * profile name can exist on the same csv line. This allows the csv to be easily
					 * viewed in excel (or other similar tools) since transactions are defined as a
					 * single csv row. This is a big advantage for customers who are batching.
					 */
					if (StringUtils.equals(record[0], X9Writer.CSV_LINE_TYPE_T25)) {
						batchProfile = record.length > X9Writer.ITEM_BATCH_PROFILE
								? record[X9Writer.ITEM_BATCH_PROFILE]
								: "";
					}

					/*
					 * Determine if items are batched as single or multi-item deposits.
					 */
					final String creditStructure = x9writer
							.getDirectedValue(headerAttr.creditStructure);
					final boolean isSingleItemDeposits;
					if (StringUtils.isBlank(creditStructure)) {
						isSingleItemDeposits = false;
					} else if (StringUtils.equals(creditStructure,
							X9HeaderXml937.DEPOSITS_SINGLE_ITEM)) {
						isSingleItemDeposits = true;
					} else if (StringUtils.equalsAny(creditStructure,
							X9HeaderXml937.DEPOSITS_MULTI_ITEM)) {
						isSingleItemDeposits = false;
					} else {
						throw X9Exception.abort("creditStructure({}) invalid; expecting {} or {}",
								creditStructure, X9HeaderXml937.DEPOSITS_SINGLE_ITEM,
								X9HeaderXml937.DEPOSITS_MULTI_ITEM);
					}

					/*
					 * Formulate a map key which is used to add incoming csv lines to our map and
					 * optionally regroup them. Grouping can be applied in two manners. First is the
					 * ability to group multiple items into a single deposit. Second is to group
					 * based on the actual depositor (this facility is mandatory when a batch
					 * profile is selected). When grouping is disabled (the typical run mode), all
					 * input is processed in the exact order as presented. Use of single item
					 * deposits is optional and requires that each item be represented by a single
					 * csv row. For this reason, the batch profile name exists as a column within
					 * the "t25" definition. Hence each csv row is one item. We also allow the batch
					 * profile name to be provided as as standalone row within the csv. In that
					 * situation, it applies to all csv rows that follow until the next profile row
					 * is encountered. This alternative approach allows more complex writer
					 * applications (like back side images that have an applied paid stamp, or an
					 * RCC deposit file with drawn images) to take advantage of batch profiles.
					 */
					final String mapKey1 = StringUtils.isNotBlank(batchProfile) ? batchProfile : "";
					final String mapKey2 = isSingleItemDeposits
							? X9Numeric.getAsString(itemMap.size() + 1, SEVEN_DIGITS)
							: "";
					final String mapKey = mapKey1 + mapKey2;

					/*
					 * Add the current csv line to our map. Note that although we are reordering
					 * items by profile, the map key still ensures that items remain in their
					 * original sequence within the batch. This is important since it is needed for
					 * more complex functions (such as paidStamps and RCC) which will have multiple
					 * csv rows for each item. In those situations, the original csv row sequence
					 * will always be maintained since it is critical to the defined data flow.
					 */
					itemMap.addNewLine(mapKey, batchProfile, csvLine);

					/*
					 * Log when debugging.
					 */
					if (LOGGER.isDebugEnabled()) {
						LOGGER.debug(
								"adding mapKey({}) batchProfile({}) mapKey1({}) "
										+ "mapKey2({}) record({})",
								mapKey, batchProfile, mapKey1, mapKey2,
								StringUtils.join(record, COMMA));
					}

					/*
					 * Get the next incoming csv line.
					 */
					csvLine = csvReader.getNextCsvLine();
				}
			}

			/*
			 * Ensure that "end" was present and otherwise abort.
			 */
			if (isAbortIfEndMissing && !isEndEncountered) {
				throw X9Exception.abort("\"end\" not last csv input line");
			}
		} catch (final Exception ex) {
			/*
			 * Log csv lines up to the point of failure when we have aborted.
			 */
			for (final X9UtilWriterCsvLines csvLines : itemMap.values()) {
				for (final X9CsvLine csvLine : csvLines) {
					csvLine.logWhenEnabled(true);
				}
			}

			/*
			 * Abort.
			 */
			throw X9Exception.abort(ex);
		}

		/*
		 * Return the csv line map.
		 */
		return itemMap;
	}

}
