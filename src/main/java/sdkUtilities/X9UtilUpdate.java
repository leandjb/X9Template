package sdkUtilities;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.x9ware.actions.X9Exception;
import com.x9ware.apacheIO.FilenameUtils;
import com.x9ware.base.X9Object;
import com.x9ware.base.X9Sdk;
import com.x9ware.base.X9SdkBase;
import com.x9ware.base.X9SdkFactory;
import com.x9ware.base.X9SdkIO;
import com.x9ware.base.X9SdkObject;
import com.x9ware.beans.X9UtilUpdateBean;
import com.x9ware.core.X9;
import com.x9ware.core.X9TotalsXml;
import com.x9ware.fields.X9Field;
import com.x9ware.fields.X9FieldPos;
import com.x9ware.fields.X9Walk;
import com.x9ware.records.X9RecordDotField;
import com.x9ware.records.X9RecordFields;
import com.x9ware.toolbox.X9Matcher;
import com.x9ware.tools.X9CsvWriter;
import com.x9ware.tools.X9ExtProperties;
import com.x9ware.tools.X9String;
import com.x9ware.tools.X9TallyMap;
import com.x9ware.tools.X9TempFile;
import com.x9ware.validate.X9TrailerManager;
import com.x9ware.validate.X9TrailerManager937;

/**
 * X9UtilUpdate is part of our utilities package which reads an x9 file and updates fields per a
 * user supplied XML document. For each input field, the xml definition allows one or more
 * match-replace values to be applied, which allows different value assignments depending on the
 * current content. The match-replace strategy can be based on a single value, a list of possible
 * values, regex search, lookback to an earlier record, or a formulated lookup key applied against
 * an external properties file. Replacement values are logged as they are applied to the output.
 *
 * @author X9Ware LLC. Copyright(c) 2012-2018 X9Ware LLC. All Rights Reserved. This is proprietary
 *         software as developed and licensed by X9Ware LLC under the exclusive legal right of the
 *         copyright holder. All licensees are provided the right to use the software only under
 *         certain conditions, and are explicitly restricted from other specific uses including
 *         modification, sharing, reuse, redistribution, or reverse engineering.
 */
public final class X9UtilUpdate {

	/**
	 * X9SdkBase instance for this environment as assigned by our constructor.
	 */
	private final X9SdkBase sdkBase;

	/**
	 * X9UtilWorkUnit instance which describes the unit of work assigned to us.
	 */
	private final X9UtilWorkUnit workUnit;

	/**
	 * Map of x9 field swaps to be applied. The map key is the record dot field (eg, 1.4) and the
	 * map value is a list of the match-replace entries to be applied.
	 */
	private final Map<String, List<X9UtilUpdateBean.Value>> updateMap = new HashMap<>();

	/**
	 * Map of all defined constants; these can be defined once and then repetitively reused.
	 */
	private final Map<String, String> constantMap = new HashMap<>();

	/**
	 * Map of all lookback fields; these allow values to be assigned from earlier record types. The
	 * use of this map eliminates the need to load files to the heap, since we can utilize the map
	 * to obtain lookback values, which reduces our memory footprint.
	 */
	private final Map<String, String> lookbackMap = new HashMap<>();

	/**
	 * Map of external lookup tables.
	 */
	private final Map<String, X9ExtProperties> tableMap = new HashMap<>();

	/**
	 * Tally of all swaps that were performed during this run.
	 */
	private final X9TallyMap tallyMap = new X9TallyMap();

	/*
	 * Private.
	 */
	private final boolean isLoggingEnabled;
	private final X9TrailerManager x9trailerManager;
	private final X9Walk x9walk;
	private File x9inputFile;
	private File x9outputFile;
	private File updateXmlFile;
	private File updateXmlFolder;
	private int tableFieldReferenceInvalid;
	private int tableLookupsUnsuccessful;

	/*
	 * Constants.
	 */
	private static final String TABLE_SEPARATOR = "/";
	private static final String TABLE_LOOKUP_COMMAND = "//table/";
	private static final char CONSTANT_IDENTIFIER = '%'; // constants defined for repetitive reuse
	private static final char LOOKBACK_IDENTIFIER = '$'; // lookback to output field value
	private static final int ARBITRAY_MAXIMUM_FIELD_COUNT = 30;

	/**
	 * Logger instance.
	 */
	private static final Logger LOGGER = LoggerFactory.getLogger(X9UtilUpdate.class);

	/*
	 * X9UtilUpdate Constructor.
	 *
	 * @param work_Unit current work unit
	 */
	public X9UtilUpdate(final X9UtilWorkUnit work_Unit) {
		workUnit = work_Unit;
		sdkBase = workUnit.getNewSdkBase();
		x9trailerManager = new X9TrailerManager937(sdkBase); // accumulate output file totals
		x9walk = new X9Walk(sdkBase);
		isLoggingEnabled = workUnit.isCommandSwitchSet(X9UtilWorkUnit.SWITCH_LOGGING);
	}

	/**
	 * Update an existing x9 file. We have an x9 input file, an x9 output file, and a results csv
	 * file that describes the field level swaps that were applied.
	 *
	 * @return exit status
	 */
	public int process() {
		/*
		 * Get work unit files.
		 */
		x9inputFile = workUnit.inputFile;
		final X9TempFile outputTempFile = X9UtilWorkUnit.getTempFileInstance(workUnit.outputFile);
		x9outputFile = outputTempFile.getTemp();

		/*
		 * Set the configuration name when provided; we otherwise default to file header.
		 */
		workUnit.autoBindToCommandLineConfiguration(sdkBase);

		/*
		 * Get the update xml parameter file from the command line.
		 */
		updateXmlFile = workUnit.secondaryFile;
		updateXmlFolder = new File(FilenameUtils.getFullPath(updateXmlFile.toString()));

		/*
		 * Update.
		 */
		final X9TotalsXml x9totalsXml = new X9TotalsXml();
		final X9Sdk sdk = X9SdkFactory.getSdk(sdkBase);
		final X9TempFile csvTempFile = X9UtilWorkUnit.getTempFileInstance(workUnit.resultsFile);
		try (final X9SdkIO sdkIO = sdk.getSdkIO();
				final X9CsvWriter csvWriter = new X9CsvWriter(csvTempFile.getTemp())) {
			/*
			 * Update processing.
			 */
			updateProcessor(sdkIO, csvWriter);
		} catch (final Exception ex) {
			/*
			 * Set message when aborted.
			 */
			x9totalsXml.setAbortMessage(ex.toString());
			throw X9Exception.abort(ex);
		} finally {
			try {
				/*
				 * Rename the output file and csv results file on completion.
				 */
				outputTempFile.renameTemp();
				csvTempFile.renameTemp();
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
			}

			/*
			 * Write our output message.
			 */
			LOGGER.info("update {}", x9totalsXml.getTotalsString());
		}

		/*
		 * Issue messages when parameter lookups were unsuccessful.
		 */
		int exitStatus = X9UtilBatch.EXIT_STATUS_ZERO;
		if (tableFieldReferenceInvalid > 0) {
			exitStatus = 1;
			LOGGER.warn("tableFieldReferenceInvalid({})", tableFieldReferenceInvalid);
		}

		if (tableLookupsUnsuccessful > 0) {
			exitStatus = 2;
			LOGGER.warn("tableLookupsUnsuccessful({})", tableLookupsUnsuccessful);
		}

		/*
		 * Return calculated exit status.
		 */
		return exitStatus;
	}

	/**
	 * Read an x9 file (record by record) which is stored in an internal list and then used to
	 * subsequently create an output x9 file.
	 *
	 * @param sdkIO
	 *            current sdkIO
	 * @param csvWriter
	 *            current csvWriter
	 * @throws Exception
	 */
	@SuppressWarnings("resource")
	private void updateProcessor(final X9SdkIO sdkIO, final X9CsvWriter csvWriter)
			throws Exception {
		/*
		 * Load the update xml file which contains the match-replace values.
		 */
		loadUpdateDefinitions();

		/*
		 * Open input and output files.
		 */
		sdkIO.openInputFile(x9inputFile);
		sdkIO.openOutputFile(x9outputFile);

		/*
		 * Get first x9 record.
		 */
		X9SdkObject sdkObject = sdkIO.readNext();

		/*
		 * Read until end of file.
		 */
		while (sdkObject != null) {
			/*
			 * Create a new x9object for this x9 record.
			 */
			final X9Object x9o = sdkIO.createX9Object();

			/*
			 * Log when enabled via a command line switch.
			 */
			if (isLoggingEnabled) {
				LOGGER.info("x9 recordNumber({}) content({})", x9o.x9ObjIdx,
						new String(x9o.x9ObjData));
			}

			/*
			 * First update the lookback map for the current record and associated values, which is
			 * before update values are applied. The reason is that we allow a given record to
			 * reference itself, where an example would be return field 32.6 (payor account number)
			 * doing a lookup which involves 32.5 (item sequence number).
			 */
			updateLookbackMapForCurrentRecord(x9o);

			/*
			 * Examine all fields within this record and apply swap values.
			 */
			final X9Field[] fieldArray = x9walk.getFieldArray(x9o);
			for (final X9Field x9field : fieldArray) {
				/*
				 * Get the list of swap field values for the current field.
				 */
				final String recordDotField = x9field.getRecordDotField();
				final List<X9UtilUpdateBean.Value> valueMap = updateMap.get(recordDotField);

				/*
				 * Process when there are swap values defined for this field.
				 */
				if (valueMap != null) {
					/*
					 * Get the old value from the current data record.
					 */
					final String inputValue = getFieldValue(x9o, x9field);

					/*
					 * Try all defined match-replace entries for this field.
					 */
					String outputValue = inputValue;
					for (final X9UtilUpdateBean.Value valueEntry : valueMap) {
						/*
						 * Get the match/replace values for this entry.
						 */
						final String matchString = valueEntry.match;
						final String newValue = valueEntry.replace;

						/*
						 * Determine if this is a match.
						 */
						final String swapValue = X9Matcher.match(x9field, matchString, inputValue,
								newValue);

						/*
						 * Apply the replacement when matched.
						 */
						if (swapValue != null) {
							/*
							 * Use the value string as a lookup into constants and lookbacks.
							 */
							if (isWrappedConstantField(swapValue)) {
								/*
								 * Apply swap when the provided value is %constant%.
								 */
								final String constant = constantMap.containsKey(swapValue)
										? constantMap.get(swapValue)
										: "";
								outputValue = constant;
							} else if (isWrappedFieldName(swapValue)) {
								/*
								 * Apply swap when the provided value is $RecordDotField$.
								 */
								final String constant = lookbackMap.containsKey(swapValue)
										? lookbackMap.get(swapValue)
										: "";
								outputValue = constant;
							} else if (StringUtils.startsWith(swapValue, TABLE_LOOKUP_COMMAND)) {
								/*
								 * Assign swap when the provide value is //table/.
								 */
								outputValue = externalTableLookup(StringUtils.substring(swapValue,
										TABLE_LOOKUP_COMMAND.length()));
							} else {
								/*
								 * Otherwise simply assigned the provided swap value.
								 */
								outputValue = swapValue;
							}

							/*
							 * Apply field level update.
							 */
							applyUpdate(x9o, x9field, outputValue);

							/*
							 * Write this update occurrence to the output csv file.
							 */
							csvWriter.startNewLine();
							csvWriter.addField(Integer.toString(sdkObject.getRecordNumber()));
							csvWriter.addField(X9RecordDotField.makeName(x9o.x9ObjType,
									x9field.getFieldIndex()));
							csvWriter.addField(x9field.getName());
							csvWriter.addField(inputValue);
							csvWriter.addField(newValue);
							csvWriter.write();

							/*
							 * Exit the update for this field since a new value has been assigned.
							 */
							break;
						}
					}
				}
			}

			/*
			 * Update the lookback map for the current record and associated values.
			 */
			updateLookbackMapForCurrentRecord(x9o);

			/*
			 * Accumulate and roll totals (after modifications).
			 */
			x9trailerManager.accumulateAndRollTotals(x9o);

			/*
			 * Write the current data record, which has been possibly modified.
			 */
			sdkObject.setDataByteArray(x9o.x9ObjData);
			sdkObject.buildOutput();
			sdkIO.writeOutputFile(sdkObject);

			/*
			 * Get next record.
			 */
			sdkObject = sdkIO.readNext();
		}

		/*
		 * Log a summary of all swap values that were applied during this run.
		 */
		for (final Entry<String, AtomicInteger> entry : tallyMap.entrySet()) {
			LOGGER.info("update {} count({})", entry.getKey(), entry.getValue().get());
		}

		LOGGER.info("update finished");
	}

	/**
	 * Apply an update for a given field map entry directly into the current x9object.
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
	private void applyUpdate(final X9Object x9o, final X9Field x9field, final String newValue) {
		/*
		 * Move payor routings since they are defined as two fields with separate check digit.
		 */
		final boolean isPayorRouting = determineIfItemPayorRouting(x9o, x9field);
		if (isPayorRouting) {
			final X9FieldPos x9fieldPos = x9field.getPositionAndLength(x9o);
			final int position = x9fieldPos.position;
			final int length = x9fieldPos.length;
			for (int i = 0; i < length; i++) {
				x9o.x9ObjData[position + i] = (byte) newValue.charAt(i);
			}
		} else {
			/*
			 * Move the new value for all other fields. We take advantage of insert, which will
			 * automatically justify the data left or right (as needed) based on current x9 rules.
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
	 * Load the update xml definition file.
	 */
	private void loadUpdateDefinitions() {
		/*
		 * Read the xml document.
		 */
		final X9UtilUpdateXml updateXml = new X9UtilUpdateXml();
		final X9UtilUpdateBean updateBean = updateXml.readExternalXmlFile(updateXmlFile);

		/*
		 * Log input xml when requested.
		 */
		if (isLoggingEnabled) {
			LOGGER.info(updateBean.toString());
		}

		/*
		 * Get the constant and swap lists.
		 */
		final List<X9UtilUpdateBean.Constant> constantList = updateXml.getConstantList();
		final List<X9UtilUpdateBean.Swap> swapList = updateXml.getSwapList();
		LOGGER.info("number of constants({}) swaps({})", constantList.size(), swapList.size());

		/*
		 * Load all constants.
		 */
		for (final X9UtilUpdateBean.Constant constantEntry : constantList) {
			final String constantId = constantEntry.id;
			final String constantValue = constantEntry.value;
			if (!isWrappedConstantField(constantId)) {
				throw X9Exception.abort("invalid constant identifier({}); not wrapped with({})",
						constantId, CONSTANT_IDENTIFIER);
			}
			constantMap.put(constantId, constantValue);
		}

		/*
		 * Load all swaps.
		 */
		int index = 0;
		for (final X9UtilUpdateBean.Swap swapEntry : swapList) {
			/*
			 * Get the next swap definition.
			 */
			index++;
			final String recordDotField = swapEntry.field;

			/*
			 * Validate record type and field number.
			 */
			final int recordType = X9RecordDotField.getRecordType(recordDotField);
			final int fieldNumber = X9RecordDotField.getFieldNumber(recordDotField);

			if (recordType <= 0) {
				throw X9Exception.abort("invalid record type{}) index({})", recordDotField, index);
			}

			if (fieldNumber <= 0) {
				throw X9Exception.abort("invalid field number{}) index({})", recordDotField, index);
			}

			/*
			 * Add this field, with the match-replace value list, to the lookup map.
			 */
			updateMap.put(recordDotField, swapEntry.valueList);

			/*
			 * Log the match-replace values to be assigned for this field.
			 */
			for (final X9UtilUpdateBean.Value value : swapEntry.valueList) {
				LOGGER.info("update entry >>> recordDotField({}) match({}) replace({})",
						recordDotField, value.match, value.replace);
			}
		}
	}

	/**
	 * Perform a lookup against an external properties file using a provided lookup string.
	 * 
	 * @param lookupString
	 *            user provided lookup string
	 * @return lookup value or empty string when not found
	 */
	private String externalTableLookup(final String lookupString) {
		/*
		 * Validate a table lookup string that was provided as a replacement value. This string
		 * contains the table name followed by one or more parameter strings that are used for
		 * actual lookup. For example, "accountLookup.txt/$itemIsn$/$itemAmount$". This string will
		 * then do a lookup against accountLookup.xml with the current item item sequence number
		 * amount amount, delimited with a forward slash "/".
		 */
		final String[] tableParms = StringUtils.split(lookupString, TABLE_SEPARATOR);
		final int count = tableParms.length;
		if (tableParms.length < 2) {
			throw X9Exception.abort("lookupString({}) length({}) must be at least 2", lookupString,
					count);
		}

		/*
		 * Use each parameter to do a lookback for a substitution value.
		 */
		for (int i = 1; i < count; i++) {
			/*
			 * Check if this parameter is an actual field lookback, which would be wrapped by our
			 * identifier character. This allows any level of the key to be a simple constant.
			 */
			final String lookupParameter = tableParms[i];
			if (isWrappedFieldName(lookupParameter)) {
				/*
				 * Lookup and assigned from our lookback table.
				 */
				if (lookbackMap.containsKey(lookupParameter)) {
					tableParms[i] = lookbackMap.get(lookupParameter);
				} else {
					tableFieldReferenceInvalid++;
					LOGGER.error("table field reference is invalid({})", lookupParameter);
				}
			}
		}

		/*
		 * Now use the parameters to do a lookup against an external property file. A classic use of
		 * this facility would be do assign 32.6 (payor account number) using a lookup that is based
		 * on item sequence number and amount.
		 */
		final X9ExtProperties tableProp = getExternalTable(tableParms[0]);
		final String mapKey1 = buildExternalPropertiesLookupKey(tableParms);
		final String value;
		if (tableProp.containsPropertyValue(mapKey1)) {
			value = tableProp.getPropertyValue(mapKey1);
		} else {
			/*
			 * The lookup was unsuccessful, so we will try again with leading zeroes removed for all
			 * fields. This can help for fields such as amount and item sequence number. Values are
			 * loaded to the table as they exist on the file (with leading zeroes) and this
			 * secondary lookup will just try again with an alternate strategy.
			 */
			for (int i = 1; i < count; i++) {
				tableParms[i] = X9String.removeLeadingZeroes(tableParms[i]);
			}
			final String mapKey2 = buildExternalPropertiesLookupKey(tableParms);
			if (tableProp.containsPropertyValue(mapKey2)) {
				value = tableProp.getPropertyValue(mapKey2);
			} else {
				/*
				 * Otherwise the lookup key was not found in the external properties file. These
				 * should always be logged for customer research. Occurrences are counted which will
				 * ultimately results in an exit code which informs of the encountered problem.
				 */
				value = "";
				tableLookupsUnsuccessful++;
				LOGGER.error("lookup key not found({})", mapKey1);
			}
		}

		/*
		 * Return the property file lookup value or an empty string when not found.
		 */
		return value;
	}

	/**
	 * Build an external properties lookup key. The first entry within the parameter list is the
	 * table name itself, which does not participate in the lookup key. Hence we join the parameters
	 * and then have to remove the first entry within the lookup key, which is the table name.
	 * 
	 * @param tableParms
	 *            current table lookup parameters
	 * @return external table lookup key
	 */
	private String buildExternalPropertiesLookupKey(final String[] tableParms) {
		final String reconstituted = StringUtils.join(tableParms, TABLE_SEPARATOR);
		return StringUtils.substringAfter(reconstituted, TABLE_SEPARATOR);
	}

	/**
	 * Update lookback fields within the lookback map for the current record. Lookback fields are
	 * identified by record dot field, since that is the approach that is also used to update
	 * fields. Note that this design is thus based on the actual record dot field names for the
	 * current standard (it is not based on logical field names). This means that updates for
	 * x9.100-180 will be very different than x9.100-187. Although this design has some issues, it
	 * is very generic in nature and allows us to do lookbacks for any field within this file. It
	 * pushes the naming to the user assignment, but eliminates the creation of complex logical
	 * names to represent each field, which would also be difficult to implement and use.
	 * 
	 * @param x9o
	 *            current x9object
	 */
	private void updateLookbackMapForCurrentRecord(final X9Object x9o) {
		/*
		 * Store all fields for this record within the lookback map.
		 */
		final X9Field[] fieldArray = x9walk.getFieldArray(x9o);
		for (final X9Field x9field : fieldArray) {
			final String recordDotField = x9field.getRecordDotField();
			final String mapKey = createWrappedLookupName(LOOKBACK_IDENTIFIER, recordDotField);
			final String value = getFieldValue(x9o, x9field);
			lookbackMap.put(mapKey, StringUtils.trim(value));
		}

		/*
		 * Reset fields for lower record types.
		 */
		switch (x9o.x9ObjType) {
			case X9.FILE_HEADER: {
				resetCashLetterFields();
				break;
			}
			case X9.CASH_LETTER_HEADER: {
				resetBundleFields();
				break;
			}
			case X9.BUNDLE_HEADER: {
				resetItemFields();
				break;
			}
			case X9.CHECK_DETAIL:
			case X9.RETURN_DETAIL: {
				resetAddendaFields();
				break;
			}
			default: {
				break;
			}
		}
	}

	/**
	 * Get the value for a given record and field from the current data record. This is complicated
	 * by the fact that the check digit is defined as a separate field in the type 25 and type 31
	 * records. So for those two fields, we combine fields to get the full 9 digit routing for
	 * lookup. All other fields in all other records can simply be obtained directly from the x9
	 * field definition, including type 61/62 credits.
	 * 
	 * @param x9o
	 *            current x9object
	 * @param x9field
	 *            current x9field
	 * @return field value
	 */
	private String getFieldValue(final X9Object x9o, final X9Field x9field) {
		final boolean isPayorRouting = determineIfItemPayorRouting(x9o, x9field);
		final X9FieldPos x9fieldPos = x9field.getPositionAndLength(x9o);
		return isPayorRouting ? new String(x9o.x9ObjData, x9fieldPos.position, 9)
				: x9field.getValueTrimmedToUpper(x9o);
	}

	/**
	 * Determine if this is a request for a type 25/31 routing number.
	 * 
	 * @param x9o
	 *            current x9object
	 * @param x9field
	 *            current x9field
	 * @return true or false
	 */
	private boolean determineIfItemPayorRouting(final X9Object x9o, final X9Field x9field) {
		final int fieldIndex = x9field.getFieldIndex();
		final X9RecordFields x9recordFields = sdkBase.getRecordFields();
		final boolean isType25Routing = x9o.isRecordType(X9.CHECK_DETAIL)
				&& fieldIndex == x9recordFields.r25PayorBankRoutingNumber;
		final boolean isType31Routing = x9o.isRecordType(X9.RETURN_DETAIL)
				&& fieldIndex == x9recordFields.r31PayorBankRoutingNumber;
		return isType25Routing || isType31Routing;
	}

	/**
	 * Reset fields for cash letter and lower record types.
	 */
	private void resetCashLetterFields() {
		resetLookbackFieldsByRecordType(X9.CASH_LETTER_HEADER);
		resetLookbackFieldsByRecordType(X9.CASH_LETTER_TRAILER);
		resetBundleFields();
		resetItemFields();
		resetAddendaFields();
	}

	/**
	 * Reset fields for bundle and lower record types.
	 */
	private void resetBundleFields() {
		resetLookbackFieldsByRecordType(X9.BUNDLE_HEADER);
		resetLookbackFieldsByRecordType(X9.BUNDLE_TRAILER);
		resetItemFields();
		resetAddendaFields();
	}

	/**
	 * Reset fields for items and lower record types.
	 */
	private void resetItemFields() {
		resetLookbackFieldsByRecordType(X9.CHECK_DETAIL);
		resetLookbackFieldsByRecordType(X9.RETURN_DETAIL);
		resetAddendaFields();
	}

	/**
	 * Reset addenda fields.
	 */
	private void resetAddendaFields() {
		resetLookbackFieldsByRecordType(X9.CHECK_ADDENDUM_A);
		resetLookbackFieldsByRecordType(X9.RETURN_ADDENDUM_A);
		resetLookbackFieldsByRecordType(X9.RETURN_ADDENDUM_B);
	}

	/**
	 * Reset lookback fields for a given record type. We take the somewhat arbitrary approach of
	 * just removing all fields up to a extremely large field count, which exceeds the number of
	 * fields in all possible x9 record types. It just makes things easier.
	 */
	private void resetLookbackFieldsByRecordType(final int recordType) {
		for (int i = 1; i < ARBITRAY_MAXIMUM_FIELD_COUNT; i++) {
			lookbackMap.put(recordType + "." + i, "");
		}
	}

	/**
	 * Get an external table from our table map and load on the first reference request.
	 *
	 * @param tableName
	 *            external table name
	 * @return table properties
	 */
	private X9ExtProperties getExternalTable(final String tableName) {
		/*
		 * Get the table file name and allow it to be either relative or absolute.
		 */
		final File tfile = new File(tableName);
		final File tableFile = tfile.isAbsolute() ? tfile : new File(updateXmlFolder, tableName);

		/*
		 * Load profile properties on the first request and add to the map if not found.
		 */
		final String tableFileName = tableFile.toString();
		final X9ExtProperties lookupProperties = tableMap.get(tableFileName);
		final X9ExtProperties properties;
		if (lookupProperties == null) {
			properties = new X9ExtProperties(tableFile);
			tableMap.put(tableFileName, properties);
		} else {
			properties = lookupProperties;
		}
		return properties;
	}

	/**
	 * Determine if a value string is wrapped with the CONSTANT_IDENTIFIER.
	 * 
	 * @param value
	 *            value string
	 * @return true or false
	 */
	private static boolean isWrappedConstantField(final String value) {
		return isWrappedLookupName(CONSTANT_IDENTIFIER, value);
	}

	/**
	 * Determine if a value string is wrapped with the LOOKBACK_OUTPUT_IDENTIFIER.
	 * 
	 * @param name
	 *            name string
	 * @return true or false
	 */
	private static boolean isWrappedFieldName(final String name) {
		return isWrappedLookupName(LOOKBACK_IDENTIFIER, name);
	}

	/**
	 * Determine if a value string is wrapped with a specific identifier character.
	 * 
	 * @param idchar
	 *            identifier character
	 * @param name
	 *            name string
	 * @return true or false
	 */
	private static boolean isWrappedLookupName(final char idchar, final String name) {
		final String identifier = Character.toString(idchar);
		return StringUtils.startsWith(name, identifier) && StringUtils.endsWith(name, identifier);
	}

	/**
	 * Create a wrapped lookup name for a given identifier string for map lookups.
	 * 
	 * @param idchar
	 *            identifier character
	 * @param name
	 *            identifier name
	 * @return wrapped name string
	 */
	private static String createWrappedLookupName(final char idchar, final String name) {
		final String identifier = Character.toString(idchar);
		return identifier + name + identifier;
	}

}
