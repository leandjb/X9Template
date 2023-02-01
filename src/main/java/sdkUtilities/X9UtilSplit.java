package sdkUtilities;

import java.io.File;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.x9ware.actions.X9Exception;
import com.x9ware.base.X9Object;
import com.x9ware.base.X9ObjectManager;
import com.x9ware.base.X9Sdk;
import com.x9ware.base.X9SdkBase;
import com.x9ware.base.X9SdkFactory;
import com.x9ware.base.X9SdkIO;
import com.x9ware.base.X9SdkObject;
import com.x9ware.beans.X9UtilSplitBean;
import com.x9ware.core.X9;
import com.x9ware.core.X9Reader;
import com.x9ware.core.X9TotalsXml;
import com.x9ware.fields.X9Field;
import com.x9ware.fields.X9FieldManager;
import com.x9ware.fields.X9FieldPos;
import com.x9ware.fields.X9Walk;
import com.x9ware.records.X9RecordDotField;
import com.x9ware.records.X9RecordFields;
import com.x9ware.toolbox.X9Matcher;
import com.x9ware.tools.X9CsvWriter;
import com.x9ware.tools.X9FileUtils;
import com.x9ware.tools.X9TallyMap;
import com.x9ware.tools.X9TempFile;
import com.x9ware.validate.X9TrailerManager;
import com.x9ware.validate.X9TrailerManager937;

/**
 * X9UtilSplit is part of our utilities package which reads an x9 file and splits it into multiple
 * x9 output files. This is accomplished by constructing a split key using the fields defined within
 * a user supplied XML document. There will be a variable number of output files (segments), where
 * logical split keys are mapped to these segments. A segment can be defined as non-writable, which
 * means that the those items are consumed but not physically written. An optional default output
 * segment can be defined as a catch-all for items that are otherwise not addressed by the split
 * criteria. Finally, the fields participating in the split can be defined with a replacement value,
 * allowing those fields to be updated as part of the split process. For example, you can split on
 * destination routing and then modify that field to an alternative value as part of the split.
 * Replacement values are logged and then applied as each output split file is created. On
 * completion, totals for all output segments are written to the log.
 *
 * @author X9Ware LLC. Copyright(c) 2012-2018 X9Ware LLC. All Rights Reserved. This is proprietary
 *         software as developed and licensed by X9Ware LLC under the exclusive legal right of the
 *         copyright holder. All licensees are provided the right to use the software only under
 *         certain conditions, and are explicitly restricted from other specific uses including
 *         modification, sharing, reuse, redistribution, or reverse engineering.
 */
public final class X9UtilSplit {

	/**
	 * X9SdkBase instance for this environment as assigned by our constructor.
	 */
	private final X9SdkBase sdkBase;

	/**
	 * X9UtilWorkUnit instance which describes the unit of work assigned to us.
	 */
	private final X9UtilWorkUnit workUnit;

	/**
	 * Tally of all swaps that were performed during this run.
	 */
	private final X9TallyMap tallyMap = new X9TallyMap();

	/**
	 * X9UtilSplitMap anchors all split outputs that will be created per the split xml definition.
	 */
	private final X9UtilSplitMap splitMap = new X9UtilSplitMap();

	/*
	 * Private.
	 */
	private final X9Walk x9walk;
	private final boolean isLoggingEnabled;
	private File x9inputFile;
	private File splitXmlFile;
	private int defaultItemsWritten;
	private int skippedItemsNotWritten;

	/*
	 * Constants.
	 */
	private static final String DEFAULT = "default";
	private static final int EXIT_STATUS_DEFAULT_ITEMS = 4;

	/**
	 * Logger instance.
	 */
	private static final Logger LOGGER = LoggerFactory.getLogger(X9UtilSplit.class);

	/*
	 * X9UtilSplit Constructor.
	 *
	 * @param work_Unit current work unit
	 */
	public X9UtilSplit(final X9UtilWorkUnit work_Unit) {
		workUnit = work_Unit;
		sdkBase = workUnit.getNewSdkBase();
		x9walk = new X9Walk(sdkBase);
		isLoggingEnabled = workUnit.isCommandSwitchSet(X9UtilWorkUnit.SWITCH_LOGGING);
	}

	/**
	 * Split an existing x9 file. We have an x9 input file which will be split to multiple x9 output
	 * files, per an xml definition which defines the field values that participate in the split.
	 *
	 * @return exit status
	 */
	public int process() {
		/*
		 * Set the configuration name when provided; we otherwise default to file header.
		 */
		workUnit.autoBindToCommandLineConfiguration(sdkBase);

		/*
		 * Get the split xml parameter file from the command line.
		 */
		splitXmlFile = workUnit.secondaryFile;

		/*
		 * Get the output folder as provided on the command line and ensure that it exists.
		 */
		final File outputFolder = workUnit.outputFile;
		if (!outputFolder.exists()) {
			throw X9Exception.abort("outputFolder not found({})", outputFolder);
		}
		if (!outputFolder.isDirectory()) {
			throw X9Exception.abort("outputFolder not a directory({})", outputFolder);
		}

		/*
		 * Load the x9 input file into the heap.
		 */
		x9inputFile = workUnit.inputFile;
		final int itemCount = loadInputFile(x9inputFile);

		/*
		 * Load the split xml file which defines how the file will be divided into parts. Note that
		 * this will walk the items within the input file, to assign the split keys. This means that
		 * the input file must be loaded before we can load the split definitions.
		 */
		final X9UtilSplitBean splitBean = loadSplitDefinitions();

		/*
		 * High level split processing.
		 */
		int itemsWritten = 0;
		final X9Sdk sdk = X9SdkFactory.getSdk(sdkBase);
		try (final X9SdkIO sdkIO = sdk.getSdkIO()) {
			/*
			 * Split the input file into all output segments, which includes the default segment.
			 * Each item is assigned to some output segment, whether by specific value key or
			 * otherwise to the default (unmapped) segment. However, segments are only written when
			 * they have an actual output file. This design allows us to balance input items to
			 * output items, since all items should be accounted for.
			 */
			for (final X9UtilSplitSegment splitSegment : splitMap.getSplitMap().values()) {
				if (splitSegment.getOutputFile() == null) {
					/*
					 * Accumulate for items not written.
					 */
					final int segmentItemCount = splitSegment.getItemCount();
					itemsWritten += segmentItemCount;

					if (segmentItemCount > 0 && StringUtils.equals(splitSegment.getOutputFileName(),
							splitBean.outputs.defaultFileName)) {
						defaultItemsWritten = segmentItemCount;
					}
				} else {
					/*
					 * Accumulate for items actually written.
					 */
					itemsWritten += createSplitOutputSegment(outputFolder, splitBean.outputs,
							splitSegment);
				}
			}
		} catch (final Exception ex) {
			throw X9Exception.abort(ex);
		} finally {
			/*
			 * Log output totals across all output segments.
			 */
			splitMap.logAllOutputTotals();
		}

		/*
		 * Sanity check to ensure that all items are accounted for. All items are not necessarily
		 * written, but we still want to make sure that inputs equal outputs.
		 */
		if (itemCount != (itemsWritten + skippedItemsNotWritten)) {
			throw X9Exception.abort(
					"internal error itemCount({}) itemsWritten({}) " + "skippedItems({})",
					itemCount, itemsWritten, skippedItemsNotWritten);
		}

		/*
		 * Write the csv results file which has summary totals for all output segments.
		 */
		final X9TempFile csvTempFile = X9UtilWorkUnit.getTempFileInstance(workUnit.resultsFile);
		try (final X9CsvWriter csvWriter = new X9CsvWriter(csvTempFile.getTemp())) {
			for (final X9UtilSplitSegment splitSegment : splitMap.getSplitMap().values()) {
				final File splitOutputFile = splitSegment.getOutputFile();
				if (splitOutputFile != null) {
					csvWriter.startNewLine();
					csvWriter.addField(splitOutputFile.toString());
					csvWriter.addField(Integer.toString(splitSegment.getRecordCount()));
					csvWriter.addField(Integer.toString(splitSegment.getDebitCount()));
					csvWriter.addField(splitSegment.getDebitAmount().toString());
					csvWriter.addField(Integer.toString(splitSegment.getCreditCount()));
					csvWriter.addField(splitSegment.getCreditAmount().toString());
					csvWriter.write();
				}
			}
		} catch (final Exception ex) {
			throw X9Exception.abort(ex);
		} finally {
			csvTempFile.renameTemp();
		}

		/*
		 * Release all sdkBase storage (since we loaded the file to the heap).
		 */
		sdkBase.systemReset();

		/*
		 * Return exit status four (default items written) or zero (all ok).
		 */
		LOGGER.info("split defaultItemsWritten({}) skippedItemsNotWritten({})", defaultItemsWritten,
				skippedItemsNotWritten);
		return defaultItemsWritten > 0 ? EXIT_STATUS_DEFAULT_ITEMS : X9UtilBatch.EXIT_STATUS_ZERO;
	}

	/**
	 * Create a specific split output segment, by writing all targeted items based on splitXml field
	 * definitions. Each item will only belong to a single output segment, based on how they are
	 * split across the output definitions. There is also a default (exception) entry that receives
	 * all non-assigned items, which can be optionally written per splitXml.
	 *
	 * @param outputFolder
	 *            output folder
	 * @param splitSegment
	 *            current split segment
	 * @return number of items written
	 */
	private int createSplitOutputSegment(final File outputFolder,
			final X9UtilSplitBean.Outputs splitOutputs, final X9UtilSplitSegment splitSegment) {
		/*
		 * Get the output segment file, which may be time-stamped per the output definition.
		 */
		final X9UtilSplitBean.Output outputEntry = splitSegment.getOutputEntry();
		final String fileName = outputEntry.fileName;
		final File absoluteFile = X9FileUtils.isFileNameAbsolute(fileName) ? new File(fileName)
				: new File(outputFolder, fileName);
		final X9TempFile outputTempFile = X9UtilWorkUnit.getTempFileWithOptionalTimestamp(
				absoluteFile.toString(), splitOutputs.dateTimeStamp, splitOutputs.doNotRewrite);
		final File outputFile = outputTempFile.getTemp();

		/*
		 * Walk all items.
		 */
		final X9ObjectManager x9objectManager = sdkBase.getObjectManager();
		X9Object x9oCashLetterHeader = null;
		X9Object x9oBundleHeader = null;
		boolean isCashLetterHeaderWritten = false;
		boolean isBundleHeaderWritten = false;

		/*
		 * Create a new x9.37 file from the internal x9.37 list that was previously created.
		 */
		int itemsWritten = 0;
		final X9TotalsXml x9totalsXml = new X9TotalsXml();
		final X9TrailerManager x9trailerManager = new X9TrailerManager937(sdkBase);
		final X9Sdk sdk = X9SdkFactory.getSdk(sdkBase);
		try (final X9SdkIO sdkIO = sdk.getSdkIO()) {
			/*
			 * Open this split output segment.
			 */
			sdkIO.openOutputFile(outputFile);

			/*
			 * Walk the list and write this specific output segment, which is will be a subset of
			 * original file. Each output segment will always contain the file header and file
			 * trailer, even when there are no items. Bundle headers with the corresponding trailer
			 * will be written only when the bundle contains at least one item. This is true for all
			 * output segments, but is especially noticeable for the default (catch-all) output
			 * file. Other than this presence, the physical bundle records are not changed, since
			 * the thought is that the bundle headers/trailers might contain some sort of relevant
			 * information that needs to be preserved. X9TrailerManager is used to recalculate and
			 * assign new trailer records. If the file contains credits, the assumption is that the
			 * credit items would not be copied (but they could ... since that is controlled by the
			 * split assignments). Copying the credits along with the would make no sense, since
			 * they would no longer be in balance if the debits are split across different files.
			 * Another possibility is that credits could be isolated to a separate output segment.
			 * All of this is dependent on the split xml file, which determines the output content.
			 */
			X9Object x9o = x9objectManager.getFirst();
			while (x9o != null) {

				switch (x9o.x9ObjType) {

					case X9.FILE_HEADER: {
						writeOneRecord(splitSegment, sdkIO, x9trailerManager, x9o);
						break;
					}

					case X9.CASH_LETTER_HEADER: {
						x9oCashLetterHeader = x9o; // save the cash letter header
						isCashLetterHeaderWritten = false;
						break;
					}

					case X9.BUNDLE_HEADER: {
						x9oBundleHeader = x9o; // save the bundle header
						isBundleHeaderWritten = false;
						break;
					}

					case X9.CHECK_DETAIL:
					case X9.RETURN_DETAIL:
					case X9.CREDIT:
					case X9.CREDIT_RECONCILIATION: {
						/*
						 * Determine if this item is being written to this output segment.
						 */
						if (splitSegment.isItemAttachedToSegment(x9o)) {
							/*
							 * Track the number of items actually written.
							 */
							itemsWritten++;

							/*
							 * Write the cash letter header when not yet written.
							 */
							if (x9oCashLetterHeader != null) {
								writeOneRecord(splitSegment, sdkIO, x9trailerManager,
										x9oCashLetterHeader);
								x9oCashLetterHeader = null;
								isCashLetterHeaderWritten = true;
							}

							/*
							 * Write the bundle header when not yet written.
							 */
							if (x9oBundleHeader != null) {
								writeOneRecord(splitSegment, sdkIO, x9trailerManager,
										x9oBundleHeader);
								x9oBundleHeader = null;
								isBundleHeaderWritten = true;
							}

							/*
							 * Write the item with associated addenda records.
							 */
							X9Object x9n = x9o;
							int count = x9n.countRecordsInGroup();
							while (x9n != null && count > 0) {
								/*
								 * Write this item record.
								 */
								writeOneRecord(splitSegment, sdkIO, x9trailerManager, x9n);

								/*
								 * Decrement and get next.
								 */
								count--;
								x9n = x9n.getNext();
							}
						}

						break;
					}

					case X9.BUNDLE_TRAILER: {
						if (isBundleHeaderWritten) {
							writeOneRecord(splitSegment, sdkIO, x9trailerManager, x9o);
							x9oBundleHeader = null;
						}
						isBundleHeaderWritten = false;
						break;
					}

					case X9.CASH_LETTER_TRAILER: {
						if (isCashLetterHeaderWritten) {
							writeOneRecord(splitSegment, sdkIO, x9trailerManager, x9o);
							x9oCashLetterHeader = null;
						}
						isCashLetterHeaderWritten = false;
						break;
					}

					case X9.FILE_CONTROL_TRAILER: {
						writeOneRecord(splitSegment, sdkIO, x9trailerManager, x9o);
						break;
					}

					default: {
						break;
					}
				}

				/*
				 * Get the next record.
				 */
				x9o = x9o.getNext();
			}

			/*
			 * Log our statistics.
			 */
			LOGGER.info(sdkIO.getSdkStatisticsMessage(outputFile));
		} catch (final Exception ex) {
			/*
			 * Set message when aborted.
			 */
			x9totalsXml.setAbortMessage(ex.toString());
			throw X9Exception.abort(ex);

		} finally {
			try {
				/*
				 * Rename on completion.
				 */
				outputTempFile.renameTemp();
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
				x9totalsXml.setTotals(workUnit.outputFile, x9trailerManager);

				/*
				 * Write summary totals when requested by command line switches.
				 */
				workUnit.writeSummaryTotals(x9totalsXml);

				/*
				 * Write our output message.
				 */
				LOGGER.info("split {}", x9totalsXml.getTotalsString());
			}
		}

		/*
		 * Return the number of items actually written to this segment.
		 */
		return itemsWritten;
	}

	/**
	 * Apply an update for a given field map entry.
	 *
	 * @param x9o
	 *            current x9object
	 * @param x9field
	 *            current x9field
	 * @param newValue
	 *            new value to be assigned
	 * @param isPayorRouting
	 *            true if this is a payor routing
	 */
	private void applyUpdate(final X9Object x9o, final X9Field x9field, final String newValue,
			final boolean isPayorRouting) {
		if (isPayorRouting) {
			/*
			 * Move payor routings since they are defined as two fields with separate check digit.
			 */
			final X9FieldPos x9fieldPos = x9field.getPositionAndLength(x9o);
			final int position = x9fieldPos.position;
			final int length = x9fieldPos.length;
			for (int i = 0; i < length; i++) {
				x9o.x9ObjData[position + i] = (byte) newValue.charAt(i);
			}
		} else {
			/*
			 * Move the new value for all other fields.
			 */
			x9field.insertField(x9o, newValue);
		}

		/*
		 * Increment the number of swaps that have been applied for this record type and field.
		 */
		tallyMap.incrementCount("field({}) name({})", x9field.getRecordDotField(),
				x9field.getName());
	}

	/**
	 * Load the split xml definition file.
	 *
	 * @return split bean that has been loaded
	 */
	private X9UtilSplitBean loadSplitDefinitions() {
		/*
		 * Read the xml document.
		 */
		final X9UtilSplitXml splitXml = new X9UtilSplitXml();
		final X9UtilSplitBean splitBean = splitXml.readExternalXmlFile(splitXmlFile);

		/*
		 * Log input xml when requested.
		 */
		if (isLoggingEnabled) {
			LOGGER.info(splitBean.toString());
		}

		/*
		 * Get the list of all field swaps that are to be applied.
		 */
		final List<X9UtilSplitBean.Output> outputList = splitXml.getOutputsList();
		LOGGER.info("number of output entries({})", outputList.size());

		/*
		 * Get the skipped output entry which applies to items that are purposefully skipped.
		 */
		final X9UtilSplitBean.Output skippedEntry = splitXml.getDefaultOutput();

		/*
		 * Get flags which indicate if outputs are debits only or credits only.
		 */
		final boolean isDebitsOnly = splitXml.getOutputs().debitsOnly;
		final boolean isCreditsOnly = splitXml.getOutputs().creditsOnly;

		/*
		 * Get the default output entry which applies to items not mapped to any other criteria.
		 */
		final X9UtilSplitBean.Output defaultEntry = splitXml.getDefaultOutput();

		/*
		 * Walk all items.
		 */
		X9Object x9oFileHeader = null;
		X9Object x9oCashLetterHeader = null;
		X9Object x9oBundleHeader = null;
		final X9ObjectManager x9objectManager = sdkBase.getObjectManager();
		X9Object x9o = x9objectManager.getFirst();
		while (x9o != null) {
			/*
			 * Save the various header record types so we can do a look-back to those values.
			 */
			switch (x9o.x9ObjType) {
				case X9.FILE_HEADER: {
					x9oFileHeader = x9o;
					break;
				}

				case X9.CASH_LETTER_HEADER: {
					x9oCashLetterHeader = x9o;
					break;
				}

				case X9.BUNDLE_HEADER: {
					x9oBundleHeader = x9o;
					break;
				}

				case X9.CHECK_DETAIL:
				case X9.RETURN_DETAIL:
				case X9.CREDIT:
				case X9.CREDIT_RECONCILIATION: {
					/*
					 * Forcibly skip this item based on debits-only or credits-only, where those
					 * options are selected as command line options.
					 */
					final boolean rejectAsDebitsOnly = isDebitsOnly && (x9o.isRecordType(X9.CREDIT)
							|| x9o.isRecordType(X9.CREDIT_RECONCILIATION));
					final boolean rejectAsCreditsOnly = isCreditsOnly
							&& (x9o.isRecordType(X9.CHECK_DETAIL)
									|| x9o.isRecordType(X9.RETURN_DETAIL));

					if (rejectAsDebitsOnly || rejectAsCreditsOnly) {
						skippedItemsNotWritten++;
						final X9UtilSplitSegment skippedSegment = splitMap
								.getSplitSegment(workUnit.outputFile, skippedEntry);
						skippedSegment.addItem("skipped", x9o);
						break;
					}

					/*
					 * Walk all output entries and create indicated split output segments.
					 */
					boolean isItemAddedToAnOutput = false;
					for (final X9UtilSplitBean.Output outputEntry : outputList) {
						/*
						 * Build the split key for this item, which is based on field from the
						 * preceding header records, this item record, and possibly the subsequent
						 * addenda record.
						 */
						final String splitKey = buildItemSplitKey(outputEntry, x9o, x9oFileHeader,
								x9oCashLetterHeader, x9oBundleHeader);

						/*
						 * Log when enabled.
						 */
						if (isLoggingEnabled) {
							LOGGER.info("recordNumber({}) recordType({}) splitKey({})",
									x9o.x9ObjIdx, x9o.x9ObjType, splitKey);
						}

						/*
						 * Add this item to the running item list when it matches segment criteria.
						 */
						if (StringUtils.isNotBlank(splitKey)) {
							final X9UtilSplitSegment splitSegment = splitMap
									.getSplitSegment(workUnit.outputFile, outputEntry);
							splitSegment.addItem(splitKey, x9o);
							isItemAddedToAnOutput = true;
							break;
						}
					}

					/*
					 * If this item has not yet been routed to an output segment, then add it to the
					 * default output segment when such a file has been requested.
					 */
					if (!isItemAddedToAnOutput) {
						final X9UtilSplitSegment splitSegment = splitMap
								.getSplitSegment(workUnit.outputFile, defaultEntry);
						splitSegment.addItem(DEFAULT, x9o);
					}
					break;
				}

				default: {
					break;
				}
			}

			/*
			 * Get the next record.
			 */
			x9o = x9o.getNext();
		}

		/*
		 * List the number of items that have been assigned to each output segment.
		 */
		int segmentNumber = 0;
		for (final X9UtilSplitSegment splitSegment : splitMap.getSplitMap().values()) {
			LOGGER.info("segmentNumber({}) fileName({}) itemCount({})", ++segmentNumber,
					splitSegment.getOutputFileName(), splitSegment.getItemCount());
		}

		/*
		 * Return the split bean.
		 */
		return splitBean;
	}

	/**
	 * Build the split key for the current item based on the output definition for this output. This
	 * key can contain data elements from either the preceding header records or from this item.
	 *
	 * @param outputEntry
	 *            current output entry
	 * @param x9oItem
	 *            current item
	 * @param x9oFileHeader
	 *            current file header
	 * @param x9oCashLetterHeader
	 *            current cash letter header
	 * @param x9oBundleHeader
	 *            current bundle header
	 * @return formulated split key for this item or null when the item does not match criteria
	 */
	private String buildItemSplitKey(final X9UtilSplitBean.Output outputEntry,
			final X9Object x9oItem, final X9Object x9oFileHeader,
			final X9Object x9oCashLetterHeader, final X9Object x9oBundleHeader) {
		/*
		 * Walk all entries within the field list for this output segment.
		 */
		String splitKey = "";
		int segmentNumber = 0;
		final X9FieldManager x9fieldManager = sdkBase.getFieldManager();
		for (final X9UtilSplitBean.Field fieldEntry : outputEntry.fieldList) {
			/*
			 * Validate record type and field number.
			 */
			segmentNumber++;
			final String recordDotField = fieldEntry.field;
			final int recordType = X9RecordDotField.getRecordType(recordDotField);
			final int fieldNumber = X9RecordDotField.getFieldNumber(recordDotField);

			if (recordType <= 0) {
				throw X9Exception.abort("invalid record type{}) segmentNumber({})", recordDotField,
						segmentNumber);
			}

			if (fieldNumber <= 0) {
				throw X9Exception.abort("invalid field number{}) segmentNumber({})", recordDotField,
						segmentNumber);
			}

			/*
			 * Get the x9field definition from the record dot field identifier.
			 */
			final X9Field x9field = x9fieldManager.getFieldObject(recordDotField);
			if (x9field == null) {
				throw X9Exception.abort("field not found recordDotField({}) segmentNumber({})",
						recordDotField, segmentNumber);
			}

			/*
			 * Get the field value from the targeted record.
			 */
			X9Object x9oTarget = null;
			switch (recordType) {
				case X9.FILE_HEADER: {
					x9oTarget = x9oFileHeader; // handled later when unexpectedly null
					break;
				}

				case X9.CASH_LETTER_HEADER: {
					x9oTarget = x9oCashLetterHeader; // handled later when unexpectedly null
					break;
				}

				case X9.BUNDLE_HEADER: {
					x9oTarget = x9oBundleHeader; // handled later when unexpectedly null
					break;
				}

				case X9.CHECK_DETAIL:
				case X9.RETURN_DETAIL:
				case X9.CREDIT:
				case X9.CREDIT_RECONCILIATION: {
					x9oTarget = x9oItem; // handled later when unexpectedly null
					break;
				}

				case X9.CHECK_ADDENDUM_A:
				case X9.CHECK_ADDENDUM_C:
				case X9.RETURN_ADDENDUM_A:
				case X9.RETURN_ADDENDUM_B:
				case X9.RETURN_ADDENDUM_D: {
					/*
					 * We have to walk forward to get these addenda records. For example, this would
					 * be a type 26 within a forward presentment file, or a type 33 for a returns
					 * file.
					 */
					X9Object x9o = x9oItem;
					int count = x9oItem.countRecordsInGroup();
					while (x9o != null && count > 0) {
						/*
						 * Set target when we find the first of this record type within the group.
						 */
						if (recordType == x9o.x9ObjType) {
							x9oTarget = x9oItem.getNext();
							break;
						}

						/*
						 * Decrement and get next.
						 */
						count--;
						x9o = x9o.getNext();
					}
					break;
				}

				default: {
					throw X9Exception.abort("unable to assign recordType({})", recordType);
				}
			}

			/*
			 * Return null (not applicable) when the target record was not found.
			 */
			if (x9oTarget == null) {
				return null;
			}

			/*
			 * Return null (not applicable) if the record type does not match the identified target
			 * item. The most likely situation is criteria against a credit (61/62) record type when
			 * we are currently positioned on a type 25 record.
			 */
			if (recordType != x9oTarget.x9ObjType) {
				return null;
			}

			/*
			 * Get the field value from the referenced x9object.
			 */
			final String fieldValue = x9field.getValueToUpper(x9oTarget);

			/*
			 * Log if debugging.
			 */
			if (LOGGER.isDebugEnabled()) {
				LOGGER.debug("fieldEntry({}) with recordType({}) fieldIndex({}) fieldValue({})",
						recordDotField, x9field.getRecordType(), x9field.getFieldIndex(),
						fieldValue);
			}

			/*
			 * Determine if any of the defined values for this field match target criteria.
			 */
			boolean isFieldValueMatched = false;
			String matchCriteria = "";
			for (final X9UtilSplitBean.Value value : fieldEntry.valueList) {
				if (X9Matcher.isMatched(x9field, value.match, fieldValue)) {
					isFieldValueMatched = true;
					matchCriteria = value.match;
					break;
				}
			}

			/*
			 * Return null (this item does not match criteria) when rejected by the matcher.
			 */
			if (!isFieldValueMatched) {
				return null;
			}

			/*
			 * Append the field record dot field number and match criteria to the running key.
			 */
			splitKey += recordDotField + "(" + matchCriteria + ")";
		}

		/*
		 * Remove the accumulated split key across all fields.
		 */
		return splitKey;
	}

	/**
	 * Write a single x9object to the output file for this output segment.
	 *
	 * @param splitSegment
	 *            current split segment
	 * @param sdkIO
	 *            current sdkIO
	 * @param x9trailerManager
	 *            current trailer manager
	 * @param x9o
	 *            current x9object
	 */
	private void writeOneRecord(final X9UtilSplitSegment splitSegment, final X9SdkIO sdkIO,
			final X9TrailerManager x9trailerManager, final X9Object x9o) {
		/*
		 * Increment total number of records written for this output segment.
		 */
		final int recordNumber = splitSegment.incrementRecordCount();

		/*
		 * Apply field updates.
		 */
		updateRecordFields(splitSegment, x9o);

		/*
		 * Accumulate and populate totals within the trailer records.
		 */
		x9trailerManager.accumulateAndPopulate(x9o);

		/*
		 * Create a sdkObject from this x9object and then write to the file.
		 */
		final String fileName = splitSegment.getOutputFileName();
		try {
			/*
			 * Make the output record from the current x9object. Note that if this is a type 52
			 * record, then image will also be included since we attached that to the x9o earlier.
			 */
			final X9SdkObject sdkObject = sdkIO.makeOutputRecord(x9o,
					X9SdkIO.UPDATE_TYPE52_IMAGE_LENGTHS_DISABLED);

			/*
			 * Write to the output file for this output segment.
			 */

			sdkIO.writeOutputFile(sdkObject);

			/*
			 * Log if debugging.
			 */
			if (isLoggingEnabled) {
				LOGGER.debug("fileName({}) recordNumber({}) recordType({}) x9record({})", fileName,
						recordNumber, x9o.x9ObjType, new String(x9o.x9ObjData));
			}
		} catch (final Exception ex) {
			throw X9Exception.abort(ex);
		}
	}

	/**
	 * Update record fields using the split xml definition. This facility allows fields with the
	 * split output files to be updated as part of the split itself, which eliminates the need to do
	 * these swaps as a subsequent activity.
	 *
	 * @param splitSegment
	 *            current split segment
	 * @param x9o
	 *            current output x9object being written
	 */
	private void updateRecordFields(final X9UtilSplitSegment splitSegment, final X9Object x9o) {
		/*
		 * Examine all fields within this record and apply swap values.
		 */
		final X9Field[] fieldArray = x9walk.getFieldArray(x9o);
		final X9UtilSplitBean.Output outputEntry = splitSegment.getOutputEntry();
		for (final X9Field x9field : fieldArray) {
			/*
			 * Walk all split xml definitions to see if this field is to be updated.
			 */
			final String recordDotField = x9field.getRecordDotField();
			final List<X9UtilSplitBean.Field> fieldList = outputEntry.fieldList;
			for (final X9UtilSplitBean.Field fieldEntry : fieldList) {
				if (StringUtils.equals(recordDotField, fieldEntry.field)) {
					/*
					 * Determine if this is a request to update a type 25/31 routing number.
					 */
					final int fieldIndex = x9field.getFieldIndex();
					final X9RecordFields x9recordFields = sdkBase.getRecordFields();
					final boolean isType25Routing = x9o.isRecordType(X9.CHECK_DETAIL)
							&& fieldIndex == x9recordFields.r25PayorBankRoutingNumber;
					final boolean isType31Routing = x9o.isRecordType(X9.RETURN_DETAIL)
							&& fieldIndex == x9recordFields.r31PayorBankRoutingNumber;
					final boolean isPayorRouting = isType25Routing || isType31Routing;

					/*
					 * Get the old value from the current data record. This is complicated by the
					 * fact that the check digit is defined as a separate field in the type 25 and
					 * type 31 records. So for those two fields, we combine fields to get the full 9
					 * digit routing for lookup. All other fields in all other records can simply be
					 * obtained directly from the x9 field definition, including type 61/62 credits.
					 */
					final X9FieldPos x9fieldPos = x9field.getPositionAndLength(x9o);
					final String fieldValue = isPayorRouting
							? new String(x9o.x9ObjData, x9fieldPos.position, 9)
							: x9field.getValueTrimmedToUpper(x9o);

					/*
					 * Try all defined match-replace entries for this field.
					 */
					for (final X9UtilSplitBean.Value valueEntry : fieldEntry.valueList) {
						/*
						 * Apply the replacement when matched.
						 */
						final String matchString = valueEntry.match;
						final String replacementValue = valueEntry.replace;
						if (X9Matcher.isMatched(x9field, matchString, fieldValue)
								&& StringUtils.isNotBlank(replacementValue)) {
							applyUpdate(x9o, x9field, replacementValue, isPayorRouting);
						}
					}
				}
			}
		}
	}

	/**
	 * Load the input x9.37 input file to the heap using X9ObjectManager.
	 *
	 * @param inputFile
	 *            input file to be loaded
	 * @return item count
	 */
	private int loadInputFile(final File inputFile) {
		int recordCount = 0;
		int itemCount = 0;
		final X9Sdk sdk = X9SdkFactory.getSdk(sdkBase);
		try (final X9SdkIO sdkIO = sdk.getSdkIO();
				final X9Reader x9reader = sdkIO.openInputFile(inputFile)) {
			/*
			 * Get first x9.37 record.
			 */
			X9SdkObject sdkObject = sdkIO.readNext();

			/*
			 * Read and store records until end of file.
			 */
			while (sdkObject != null) {
				/*
				 * Store all x9objects on the heap.
				 */
				recordCount++;
				final X9Object x9o = sdkIO.createAndStoreX9Object();

				if (x9o.isItem()) {
					/*
					 * Count input items.
					 */
					itemCount++;
				} else if (x9o.isRecordType(X9.IMAGE_VIEW_DATA)) {
					/*
					 * Attach images directly to the type 52 records so we can write them later.
					 */
					x9o.setDirectlyAttachedImage(x9reader.getImageBuffer());
				}

				/*
				 * Continue reading the input file.
				 */
				sdkObject = sdkIO.readNext();
			}

			LOGGER.info("inputFile({}) loaded recordCount({}) itemCount({})", inputFile,
					recordCount, itemCount);
		} catch (final Exception ex) {
			throw X9Exception.abort(ex);
		}

		/*
		 * Return the item count.
		 */
		return itemCount;
	}

}
