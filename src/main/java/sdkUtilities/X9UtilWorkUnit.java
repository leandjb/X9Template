package sdkUtilities;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.x9ware.actions.X9Exception;
import com.x9ware.apacheIO.FilenameUtils;
import com.x9ware.base.X9SdkBase;
import com.x9ware.beans.X9UtilWorkUnitAttr;
import com.x9ware.config.X9ConfigSelector;
import com.x9ware.core.X9;
import com.x9ware.core.X9TotalsXml;
import com.x9ware.elements.X9C;
import com.x9ware.tools.X9CommandLine;
import com.x9ware.tools.X9Date;
import com.x9ware.tools.X9File;
import com.x9ware.tools.X9FileUtils;
import com.x9ware.tools.X9Numeric;
import com.x9ware.tools.X9Task;
import com.x9ware.tools.X9TempFile;

/**
 * X9UtilWorkUnit defines the files that are associated with a single X9Util work unit which are
 * subsequently passed to various X9Util worker tasks. We include methods to create and populate a
 * new X9UtilWorkUnit object from provided inputs and to perform various other common functions for
 * those work efforts. Our design is to allow worker tasks to be created from command line arguments
 * or alternatively from other more complex sources such as line items within a command file
 * (allowing a large number of tasks to be performed in a single X9Util batch operation) or against
 * a directory (where work units are created from the file names themselves). These features take
 * advantage of the multi-threaded processing capabilities of the SDK where each work unit can have
 * its own X9SdkBase and function independently of all other concurrent tasks.
 *
 * @author X9Ware LLC. Copyright(c) 2012-2018 X9Ware LLC. All Rights Reserved. This is proprietary
 *         software as developed and licensed by X9Ware LLC under the exclusive legal right of the
 *         copyright holder. All licensees are provided the right to use the software only under
 *         certain conditions, and are explicitly restricted from other specific uses including
 *         modification, sharing, reuse, redistribution, or reverse engineering.
 */
public final class X9UtilWorkUnit {

	/*
	 * Public.
	 */
	public final X9CommandLine x9commandLine;
	public String utilFunctionName;
	public File inputFile;
	public File imageFolder;
	public File secondaryFile;
	public File outputFile;
	public File resultsFile;

	/*
	 * Private.
	 */
	private boolean isImageRepairEnabled;
	private boolean isImageResizeEnabled;

	/*
	 * Constants.
	 */
	public static final String FUNCTION_WRITE = "Write";
	public static final String FUNCTION_TRANSLATE = "Translate";
	public static final String FUNCTION_IMPORT = "Import";
	public static final String FUNCTION_EXPORT = "Export";
	public static final String FUNCTION_EXPORT_CSV = "ExportCsv";
	public static final String FUNCTION_VALIDATE = "Validate";
	public static final String FUNCTION_SCRUB = "Scrub";
	public static final String FUNCTION_MAKE = "Make";
	public static final String FUNCTION_MERGE = "Merge";
	public static final String FUNCTION_UPDATE = "Update";
	public static final String FUNCTION_SPLIT = "Split";
	public static final String FUNCTION_COMPARE = "Compare";
	public static final String FUNCTION_IMAGE_PULL = "ImagePull";

	public static final String[] FUNCTION_NAMES = new String[] { FUNCTION_WRITE, FUNCTION_TRANSLATE,
			FUNCTION_IMPORT, FUNCTION_EXPORT, FUNCTION_EXPORT_CSV, FUNCTION_VALIDATE,
			FUNCTION_SCRUB, FUNCTION_MAKE, FUNCTION_MERGE, FUNCTION_UPDATE, FUNCTION_SPLIT,
			FUNCTION_COMPARE, FUNCTION_IMAGE_PULL };

	public static final String[] AUTO_BIND_FUNCTION_NAMES = new String[] { FUNCTION_TRANSLATE,
			FUNCTION_IMPORT, FUNCTION_EXPORT, FUNCTION_EXPORT_CSV, FUNCTION_VALIDATE,
			FUNCTION_SCRUB, FUNCTION_UPDATE, FUNCTION_SPLIT, FUNCTION_COMPARE };

	public static final char COMMAND_LINE_ARGS_SEPARATION_CHARACTER = '|';

	private static final int SKIP_INTERVAL_DEFAULT = 60;
	private static final String SUMMARY_SUFFIX = "_summary";
	private static final String HELP_BREAK_LINE = StringUtils.rightPad("", 80, "-");

	/*
	 * Global switches.
	 */
	public static final String SWITCH_CONFIG = "config";
	public static final String SWITCH_IMAGE_EXPORT = "i";
	public static final String SWITCH_EXCLUDE = "exclude";
	public static final String SWITCH_MULTI_FILE = "xm";
	public static final String SWITCH_DO_NOT_REWRITE = "dnr";
	public static final String SWITCH_DATE_TIME_STAMP = "dts";
	public static final String SWITCH_EXTENSION_INPUT = "exti";
	public static final String SWITCH_EXTENSION_OUTPUT = "exto";
	public static final String SWITCH_SKIP_INTERVAL = "skpi";
	public static final String SWITCH_LOGGING = "l";
	public static final String SWITCH_WRITE_JSON_TOTALS = "j";
	public static final String SWITCH_WRITE_XML_TOTALS = "x";
	public static final String SWITCH_WRITE_TEXT_TOTALS = "t";
	public static final String SWITCH_WORK_UNIT = "workUnit";
	public static final String SWITCH_IMAGE_REPAIR_ENABLED = "imageRepairEnabled";
	public static final String SWITCH_IMAGE_RESIZE_ENABLED = "imageResizeEnabled";

	/*
	 * Writer switches.
	 */
	public static final String SWITCH_HEADERS_XML = "xml";
	public static final String SWITCH_END_NOT_PROVIDED = "enp";
	public static final String[] WRITE_SWITCHES = { SWITCH_CONFIG, SWITCH_LOGGING,
			SWITCH_WRITE_JSON_TOTALS, SWITCH_WRITE_XML_TOTALS, SWITCH_WRITE_TEXT_TOTALS,
			SWITCH_HEADERS_XML, SWITCH_DO_NOT_REWRITE, SWITCH_DATE_TIME_STAMP,
			SWITCH_IMAGE_REPAIR_ENABLED, SWITCH_IMAGE_RESIZE_ENABLED, SWITCH_END_NOT_PROVIDED };

	/*
	 * Translate switches.
	 */
	public static final String SWITCH_WRITE_ADDENDA = "a";
	public static final String[] TRANSLATE_SWITCHES = { SWITCH_CONFIG, SWITCH_LOGGING,
			SWITCH_WRITE_JSON_TOTALS, SWITCH_WRITE_XML_TOTALS, SWITCH_WRITE_TEXT_TOTALS,
			SWITCH_WRITE_ADDENDA, SWITCH_IMAGE_EXPORT };

	/*
	 * Import switches.
	 */
	public static final String SWITCH_REPLACE_TRAILER_TOTALS = "r";
	public static final String[] IMPORT_SWITCHES = { SWITCH_CONFIG, SWITCH_LOGGING,
			SWITCH_WRITE_JSON_TOTALS, SWITCH_WRITE_XML_TOTALS, SWITCH_WRITE_TEXT_TOTALS,
			SWITCH_REPLACE_TRAILER_TOTALS };

	/*
	 * Export switches.
	 */
	public static final String SWITCH_RECORD_TYPES = "rectypes";
	public static final String SWITCH_ABORT_WHEN_EMPTY = "awe";
	public static final String SWITCH_ITEM_EXPORT = "xf";
	public static final String SWITCH_ITEM_EXPORT_WITH_COLUMN_HEADINGS = "xfc";
	public static final String SWITCH_GROUP_EXPORT = "xg";
	public static final String SWITCH_TIFF_TAG_EXPORT = "xt";
	public static final String SWITCH_XML_EXPORT_FLAT = "xmlf";
	public static final String SWITCH_XML_EXPORT_HIERARCHICAL = "xmlh";
	public static final String SWITCH_CSV_EXPORT = "xc";
	public static final String SWITCH_IMAGE_RELATIVE = "ir";
	public static final String SWITCH_IMAGE_EXPORT_NONE = "none";
	public static final String SWITCH_IMAGE_EXPORT_TIF = "tif";
	public static final String SWITCH_IMAGE_EXPORT_JPG = "jpg";
	public static final String SWITCH_IMAGE_EXPORT_PNG = "png";
	public static final String SWITCH_IMAGE_EXPORT_GIF = "gif";
	public static final String SWITCH_IMAGE_BASE64_BASIC = "i64";
	public static final String SWITCH_IMAGE_BASE64_MIME = "i64mime";
	public static final String SWITCH_MULTIPAGE_TIFF_EXPORT = "mptiff";
	public static final String SWITCH_MULTIPAGE_IRD_EXPORT = "mpird";
	public static final String SWITCH_XML_INCLUDE_EMPTY_FIELDS = "ef";
	public static final String SWITCH_DECIMAL_POINTS = "dp";
	public static final String[] EXPORT_SWITCHES = { SWITCH_CONFIG, SWITCH_LOGGING,
			SWITCH_WRITE_JSON_TOTALS, SWITCH_WRITE_XML_TOTALS, SWITCH_WRITE_TEXT_TOTALS,
			SWITCH_EXTENSION_INPUT, SWITCH_SKIP_INTERVAL, SWITCH_RECORD_TYPES,
			SWITCH_ABORT_WHEN_EMPTY, SWITCH_ITEM_EXPORT, SWITCH_ITEM_EXPORT_WITH_COLUMN_HEADINGS,
			SWITCH_GROUP_EXPORT, SWITCH_TIFF_TAG_EXPORT, SWITCH_XML_EXPORT_FLAT,
			SWITCH_XML_EXPORT_HIERARCHICAL, SWITCH_MULTI_FILE, SWITCH_CSV_EXPORT,
			SWITCH_IMAGE_EXPORT, SWITCH_IMAGE_RELATIVE, SWITCH_IMAGE_EXPORT_TIF,
			SWITCH_IMAGE_EXPORT_JPG, SWITCH_IMAGE_EXPORT_PNG, SWITCH_IMAGE_EXPORT_GIF,
			SWITCH_IMAGE_BASE64_BASIC, SWITCH_IMAGE_BASE64_MIME, SWITCH_MULTIPAGE_TIFF_EXPORT,
			SWITCH_MULTIPAGE_IRD_EXPORT, SWITCH_XML_INCLUDE_EMPTY_FIELDS, SWITCH_DECIMAL_POINTS };

	/*
	 * ExportCsv switches. In support of automated operations, our design is to embed the majority
	 * of ExportCsv options within this xml definition. This allows export-csv to be invoked with
	 * just the format reference, with all parameters then applied appropriately. This approach is
	 * very different from the standard export command, but it is also a major differentiation.
	 */
	public static final String SWITCH_EXPORT_CONTROLS = "xctl";
	public static final String SWITCH_EXPORT_FORMAT = "xfmt";
	public static final String[] EXPORT_CSV_SWITCHES = { SWITCH_CONFIG, SWITCH_LOGGING,
			SWITCH_WRITE_JSON_TOTALS, SWITCH_WRITE_XML_TOTALS, SWITCH_WRITE_TEXT_TOTALS,
			SWITCH_EXPORT_CONTROLS, SWITCH_EXPORT_FORMAT };

	/*
	 * Validate switches.
	 */
	public static final String[] VALIDATE_SWITCHES = { SWITCH_CONFIG, SWITCH_LOGGING,
			SWITCH_WRITE_JSON_TOTALS, SWITCH_WRITE_XML_TOTALS, SWITCH_WRITE_TEXT_TOTALS };

	/*
	 * Scrub switches.
	 */
	public static final String[] SCRUB_SWITCHES = { SWITCH_CONFIG, SWITCH_LOGGING,
			SWITCH_WRITE_JSON_TOTALS, SWITCH_WRITE_XML_TOTALS, SWITCH_WRITE_TEXT_TOTALS,
			SWITCH_EXTENSION_INPUT, SWITCH_EXTENSION_OUTPUT, SWITCH_MULTI_FILE };

	/*
	 * Make switches.
	 */
	public static final String SWITCH_MAKE_REFORMATTER = "reformatter";
	public static final String SWITCH_MAKE_GENERATOR = "generator";
	public static final String[] MAKE_SWITCHES = { SWITCH_CONFIG, SWITCH_LOGGING,
			SWITCH_MAKE_REFORMATTER, SWITCH_MAKE_GENERATOR, SWITCH_WRITE_JSON_TOTALS,
			SWITCH_WRITE_XML_TOTALS, SWITCH_WRITE_TEXT_TOTALS };

	/*
	 * Merge switches.
	 */
	public static final String SWITCH_T99_MISSING = "t99m";
	public static final String SWITCH_MERGE_BY_BUNDLE = "mrgb";
	public static final String SWITCH_MODIFY_BUNDLES = "modb";
	public static final String SWITCH_INCLUDE_SUBFOLDERS = "sf";
	public static final String SWITCH_SORT_DESCENDING = "sd";
	public static final String SWITCH_GROUP_BY_ITEM_COUNT = "gbic";
	public static final String SWITCH_DO_NOT_RENAME = "dnr";
	public static final String SWITCH_UPDATE_TIMESTAMP = "utsf";
	public static final String SWITCH_MAXIMUM_FILE_SIZE = "max";
	public static final String SWITCH_EXTENSION_RENAME = "extr";
	public static final String SWITCH_EXTENSION_FAILED = "extf";
	public static final String[] MERGE_SWITCHES = { SWITCH_CONFIG, SWITCH_LOGGING,
			SWITCH_WRITE_JSON_TOTALS, SWITCH_WRITE_XML_TOTALS, SWITCH_WRITE_TEXT_TOTALS,
			SWITCH_EXTENSION_INPUT, SWITCH_EXTENSION_RENAME, SWITCH_EXTENSION_FAILED,
			SWITCH_T99_MISSING, SWITCH_MERGE_BY_BUNDLE, SWITCH_MODIFY_BUNDLES,
			SWITCH_INCLUDE_SUBFOLDERS, SWITCH_SORT_DESCENDING, SWITCH_GROUP_BY_ITEM_COUNT,
			SWITCH_SKIP_INTERVAL, SWITCH_DO_NOT_RENAME, SWITCH_UPDATE_TIMESTAMP,
			SWITCH_MAXIMUM_FILE_SIZE };

	/*
	 * Update switches.
	 */
	public static final String[] UPDATE_SWITCHES = { SWITCH_CONFIG, SWITCH_LOGGING,
			SWITCH_WRITE_JSON_TOTALS, SWITCH_WRITE_XML_TOTALS, SWITCH_WRITE_TEXT_TOTALS,
			SWITCH_EXTENSION_INPUT, SWITCH_EXTENSION_OUTPUT, SWITCH_MULTI_FILE };

	/*
	 * Split switches.
	 */
	public static final String[] SPLIT_SWITCHES = { SWITCH_CONFIG, SWITCH_LOGGING,
			SWITCH_WRITE_JSON_TOTALS, SWITCH_WRITE_XML_TOTALS, SWITCH_WRITE_TEXT_TOTALS, };

	/*
	 * Compare switches.
	 */
	public static final String SWITCH_VERBOSE = "v";
	public static final String SWITCH_MASK = "mask";
	public static final String SWITCH_DELETE = "delete";
	public static final String[] COMPARE_SWITCHES = { SWITCH_CONFIG, SWITCH_LOGGING,
			SWITCH_WRITE_JSON_TOTALS, SWITCH_WRITE_XML_TOTALS, SWITCH_WRITE_TEXT_TOTALS,
			SWITCH_EXCLUDE, SWITCH_DELETE, SWITCH_VERBOSE, SWITCH_MASK };

	/*
	 * Image pull switches.
	 */
	public static final String SWITCH_APPEND_TIMESTAMP_TO_IMAGE_FOLDER_NAME = "ix";
	public static final String SWITCH_CLEAR_IMAGE_FOLDER = "ic";
	public static final String SWITCH_DO_NOT_ABORT_WHEN_IMAGE_FOLDER_NOT_EMPTY = "ia";
	public static final String SWITCH_INCLUDE_61_62_CREDITS = "cr";
	public static final String SWITCH_PULL_BACK_SIDE_IMAGES = "ib";
	public static final String SWITCH_THREADS = "threads";
	public static final String[] IMAGE_PULL_SWITCHES = { SWITCH_CONFIG, SWITCH_LOGGING,
			SWITCH_APPEND_TIMESTAMP_TO_IMAGE_FOLDER_NAME, SWITCH_CLEAR_IMAGE_FOLDER,
			SWITCH_DO_NOT_ABORT_WHEN_IMAGE_FOLDER_NOT_EMPTY, SWITCH_INCLUDE_61_62_CREDITS,
			SWITCH_PULL_BACK_SIDE_IMAGES, SWITCH_THREADS };

	/*
	 * Functional switches by command.
	 */
	private static final Map<String, String[]> FUNCTIONAL_SWITCHES = new HashMap<>();
	static {
		FUNCTIONAL_SWITCHES.put(FUNCTION_WRITE, WRITE_SWITCHES);
		FUNCTIONAL_SWITCHES.put(FUNCTION_TRANSLATE, TRANSLATE_SWITCHES);
		FUNCTIONAL_SWITCHES.put(FUNCTION_IMPORT, IMPORT_SWITCHES);
		FUNCTIONAL_SWITCHES.put(FUNCTION_EXPORT, EXPORT_SWITCHES);
		FUNCTIONAL_SWITCHES.put(FUNCTION_EXPORT_CSV, EXPORT_CSV_SWITCHES);
		FUNCTIONAL_SWITCHES.put(FUNCTION_VALIDATE, VALIDATE_SWITCHES);
		FUNCTIONAL_SWITCHES.put(FUNCTION_SCRUB, SCRUB_SWITCHES);
		FUNCTIONAL_SWITCHES.put(FUNCTION_MAKE, MAKE_SWITCHES);
		FUNCTIONAL_SWITCHES.put(FUNCTION_MERGE, MERGE_SWITCHES);
		FUNCTIONAL_SWITCHES.put(FUNCTION_UPDATE, UPDATE_SWITCHES);
		FUNCTIONAL_SWITCHES.put(FUNCTION_SPLIT, SPLIT_SWITCHES);
		FUNCTIONAL_SWITCHES.put(FUNCTION_COMPARE, COMPARE_SWITCHES);
		FUNCTIONAL_SWITCHES.put(FUNCTION_IMAGE_PULL, IMAGE_PULL_SWITCHES);
	}

	/*
	 * Required files by function.
	 */
	public static final String[] INPUT_FILE_REQUIRED = new String[] { FUNCTION_WRITE,
			FUNCTION_TRANSLATE, FUNCTION_IMPORT, FUNCTION_EXPORT, FUNCTION_EXPORT_CSV,
			FUNCTION_VALIDATE, FUNCTION_SCRUB, FUNCTION_MAKE, FUNCTION_MERGE, FUNCTION_UPDATE,
			FUNCTION_SPLIT, FUNCTION_COMPARE, FUNCTION_IMAGE_PULL };

	public static final String[] SECONDARY_FILE_REQUIRED = new String[] { FUNCTION_SCRUB,
			FUNCTION_UPDATE, FUNCTION_SPLIT, FUNCTION_COMPARE };

	public static final String[] OUTPUT_FILE_REQUIRED = new String[] { FUNCTION_MERGE };

	public static final String[] RESULTS_FILE_REQUIRED = new String[] {};

	public static final String[] IMAGE_FOLDER_REQUIRED = new String[] {};

	/*
	 * Allowed files by function.
	 */
	public static final String[] INPUT_FILE_ALLOWED = new String[] { FUNCTION_WRITE,
			FUNCTION_TRANSLATE, FUNCTION_IMPORT, FUNCTION_EXPORT, FUNCTION_VALIDATE, FUNCTION_SCRUB,
			FUNCTION_MAKE, FUNCTION_MERGE, FUNCTION_UPDATE, FUNCTION_SPLIT, FUNCTION_COMPARE,
			FUNCTION_IMAGE_PULL };

	public static final String[] SECONDARY_FILE_ALLOWED = new String[] { FUNCTION_WRITE,
			FUNCTION_SCRUB, FUNCTION_MAKE, FUNCTION_UPDATE, FUNCTION_EXPORT_CSV, FUNCTION_SPLIT,
			FUNCTION_COMPARE, FUNCTION_IMAGE_PULL };

	public static final String[] OUTPUT_FILE_ALLOWED = new String[] { FUNCTION_WRITE,
			FUNCTION_TRANSLATE, FUNCTION_IMPORT, FUNCTION_EXPORT, FUNCTION_VALIDATE, FUNCTION_SCRUB,
			FUNCTION_MAKE, FUNCTION_MERGE, FUNCTION_UPDATE, FUNCTION_SPLIT, FUNCTION_COMPARE };

	public static final String[] RESULTS_FILE_ALLOWED = new String[] { FUNCTION_SCRUB,
			FUNCTION_COMPARE, FUNCTION_UPDATE, FUNCTION_SPLIT, FUNCTION_IMAGE_PULL };

	public static final String[] IMAGE_FOLDER_ALLOWED = new String[] { FUNCTION_TRANSLATE,
			FUNCTION_IMPORT, FUNCTION_EXPORT, FUNCTION_IMAGE_PULL };

	/**
	 * Logger instance.
	 */
	private static final Logger LOGGER = LoggerFactory.getLogger(X9UtilWorkUnit.class);

	/**
	 * X9UtilWorkUnit Constructor.
	 *
	 * @param command_Line
	 *            command line for this work unit
	 */
	public X9UtilWorkUnit(final X9CommandLine command_Line) {
		/*
		 * Set our command line reference.
		 */
		x9commandLine = command_Line;

		/*
		 * Determine if the command line references a saved xml work unit that was previously
		 * constructed by X9UtilitiesUi. When that command line switch is set, it will point us to
		 * an xml file that has the full command line to be executed. Our task here is easy since we
		 * only need to load that xml file and parse the command line as it has been saved for us.
		 * Note that the command line arguments are separated by the pipe character since there most
		 * probably are blanks embedded in the various input and output file names.
		 */
		if (isCommandSwitchSet(SWITCH_WORK_UNIT)) {
			final X9UtilWorkUnitXml workUnitXml = new X9UtilWorkUnitXml();
			workUnitXml.readExternalXmlFile(getCommandLineFile(SWITCH_WORK_UNIT));
			final X9UtilWorkUnitAttr workUnitAttr = workUnitXml.getAttr();
			x9commandLine.parse(StringUtils.split(workUnitAttr.commandLineAsExecuted,
					COMMAND_LINE_ARGS_SEPARATION_CHARACTER));
		}
	}

	/**
	 * Assign work unit files which are associated with a new X9UtilWorkUnit. We assign files from
	 * listed inputs which can come from the command line or from an input text file. We know which
	 * files must be required based on the function being performed. We thus check if those files
	 * have been defined on the command line and will return true when present and false when any of
	 * the required files have been omitted.
	 *
	 * @param files
	 *            command line files
	 * @return true when the assignments represent a valid unit of work
	 * @throws FileNotFoundException
	 */
	public boolean setup(final File... files) throws FileNotFoundException {
		/*
		 * Get the default output file extension and default to "new".
		 */
		final String ext = getCommandSwitchValue(SWITCH_EXTENSION_OUTPUT);
		final String outExtension = StringUtils.isNotBlank(ext) ? ext : "new";

		/*
		 * Ensure that a valid function switch is present and set the function name.
		 */
		if (!isValidFunctionSwitchPresent()) {
			logAsUsageWithAllAvailableFunctions();
			return false;
		}

		/*
		 * Assign files based on the function being performed.
		 */
		boolean isValidWorkUnit = true;
		if (files.length == 0) {
			isValidWorkUnit = false;
			LOGGER.error("no files are present on the command line!");
			logAsUsageWithAllAvailableFunctions();
		} else if (StringUtils.equals(utilFunctionName, FUNCTION_WRITE)) {
			inputFile = files[0];
			switch (files.length) {
				case 1: {
					/*
					 * -write <inputFile>
					 */
					outputFile = new File(
							FilenameUtils.removeExtension(inputFile.toString()) + "." + X9C.X937);
					break;
				}
				case 2: {
					/*
					 * -write <inputFile> <outputFile>
					 */
					outputFile = files[1];
					break;
				}
				case 3: {
					/*
					 * -write <inputFile> <headerXml> <outputFile>
					 */
					secondaryFile = files[1];
					outputFile = files[2];
					break;
				}
				default: {
					isValidWorkUnit = false;
					LOGGER.error("Usage: x9util -write  csvInputFile headerXmlFile outputFile");
				}
			}
		} else if (StringUtils.equals(utilFunctionName, FUNCTION_TRANSLATE)) {
			inputFile = files[0];
			switch (files.length) {
				case 1: {
					/*
					 * -translate <inputFile>
					 */
					final String folder = X9FileUtils.getFolderName(inputFile);
					final String baseName = FilenameUtils.getBaseName(inputFile.toString());
					outputFile = new File(folder, baseName + "." + X9C.CSV);
					imageFolder = new File(folder, baseName + "_IMAGES");
					break;
				}
				case 2: {
					/*
					 * -translate <inputFile> <outputFile>
					 */
					outputFile = files[1];
					final String folder = X9FileUtils.getFolderName(outputFile);
					final String baseName = FilenameUtils.getBaseName(outputFile.toString());
					imageFolder = new File(folder, baseName + "_IMAGES");
					break;
				}
				case 3: {
					/*
					 * -translate <inputFile> <outputFile> <imageFolder>
					 */
					outputFile = files[1];
					imageFolder = files[2];
					break;
				}
				default: {
					isValidWorkUnit = false;
					LOGGER.error("Usage: x9util -translate inputFile csvOutputFile");
				}
			}
		} else if (StringUtils.equals(utilFunctionName, FUNCTION_IMPORT)) {
			inputFile = files[0];
			switch (files.length) {
				case 1: {
					/*
					 * -import <inputFile>
					 */
					outputFile = new File(
							FilenameUtils.removeExtension(inputFile.toString()) + "." + X9C.X937);
					break;
				}
				case 2: {
					/*
					 * -import <inputFile> <outputFile>
					 */
					outputFile = files[1];
					break;
				}
				case 3: {
					/*
					 * -import <inputFile> <outputFile> <imageFolder>
					 */
					outputFile = files[1];
					imageFolder = files[2];
					break;
				}
				default: {
					isValidWorkUnit = false;
					LOGGER.error("Usage: x9util -import csvInputFile outputFile");
				}
			}
		} else if (StringUtils.equals(utilFunctionName, FUNCTION_EXPORT)) {
			inputFile = files[0];
			final boolean isExportToXml = isCommandSwitchSet(SWITCH_XML_EXPORT_FLAT)
					|| isCommandSwitchSet(SWITCH_XML_EXPORT_HIERARCHICAL);
			final String extension = isExportToXml ? X9C.XML : X9C.CSV;
			switch (files.length) {
				case 1: {
					/*
					 * -export <inputFile>
					 */
					final String folder = X9FileUtils.getFolderName(inputFile);
					final String baseName = FilenameUtils.getBaseName(inputFile.toString());
					outputFile = new File(folder, baseName + "." + extension);
					imageFolder = new File(folder, baseName + "_IMAGES");
					break;
				}
				case 2: {
					/*
					 * -export <inputFile> <outputFile>
					 */
					outputFile = files[1];
					final String folder = X9FileUtils.getFolderName(outputFile);
					final String baseName = FilenameUtils.getBaseName(outputFile.toString());
					imageFolder = new File(folder, baseName + "_IMAGES");
					break;
				}
				case 3: {
					/*
					 * -export <inputFile> <outputFile> <imageFolder>
					 */
					outputFile = files[1];
					imageFolder = files[2];
					break;
				}
				default: {
					isValidWorkUnit = false;
					LOGGER.error("Usage: x9util -export inputFile outputFile");
				}
			}

			int imageFormatCount = 0;
			if (isCommandSwitchSet(SWITCH_IMAGE_EXPORT_TIF)) {
				imageFormatCount++;
			}
			if (isCommandSwitchSet(SWITCH_IMAGE_EXPORT_JPG)) {
				imageFormatCount++;
			}
			if (isCommandSwitchSet(SWITCH_IMAGE_EXPORT_PNG)) {
				imageFormatCount++;
			}
			if (isCommandSwitchSet(SWITCH_IMAGE_EXPORT_GIF)) {
				imageFormatCount++;
			}
			if (imageFormatCount > 1) {
				isValidWorkUnit = false;
				LOGGER.error("multiple image formats selected");
			}
		} else if (StringUtils.equals(utilFunctionName, FUNCTION_EXPORT_CSV)) {
			inputFile = files[0];
			switch (files.length) {
				case 1: {
					/*
					 * -exportCsv <inputFile>
					 */
					break;
				}
				default: {
					isValidWorkUnit = false;
					LOGGER.error("Usage: x9util -exportCsv inputFile");
				}
			}
		} else if (StringUtils.equals(utilFunctionName, FUNCTION_VALIDATE)) {
			inputFile = files[0];
			switch (files.length) {
				case 1: {
					/*
					 * -validate <inputFile>
					 */
					resultsFile = new File(
							FilenameUtils.removeExtension(inputFile.toString()) + "." + X9C.CSV);
					break;
				}
				case 2: {
					/*
					 * -validate <inputFile> <resultsFile>
					 */
					resultsFile = files[1];
					break;
				}
				default: {
					isValidWorkUnit = false;
					LOGGER.error("Usage: x9util -validate inputFile resultsFile");
				}
			}
		} else if (StringUtils.equals(utilFunctionName, FUNCTION_SCRUB)) {
			inputFile = files[0];
			switch (files.length) {
				case 2: {
					/*
					 * -scrub <inputFile> <parmFile>
					 */
					secondaryFile = files[1];
					final String baseName = FilenameUtils.removeExtension(inputFile.toString());
					outputFile = new File(baseName + "." + outExtension);
					resultsFile = new File(baseName + "." + X9C.CSV);
					break;
				}
				case 3: {
					/*
					 * -scrub <inputFile> <parmFile> <outputFile>
					 */
					secondaryFile = files[1];
					outputFile = files[2];
					resultsFile = new File(
							FilenameUtils.removeExtension(outputFile.toString()) + "." + X9C.CSV);
					break;
				}
				case 4: {
					/*
					 * -scrub <inputFile> <parmFile> <outputFile> <resultsFile>
					 */
					secondaryFile = files[1];
					outputFile = files[2];
					resultsFile = files[3];
					break;
				}
				default: {
					isValidWorkUnit = false;
					LOGGER.error("Usage: x9util -scrub inputFile parametersFile "
							+ "outputFile resultsFile");
				}
			}
		} else if (StringUtils.equals(utilFunctionName, FUNCTION_MAKE)) {
			inputFile = files[0];
			switch (files.length) {
				case 2: {
					/*
					 * -make <inputFile> <outputFile>
					 */
					outputFile = files[1];
					break;
				}
				case 3: {
					/*
					 * -make <inputFile> <routingListFile> <outputFile>
					 */
					secondaryFile = files[1];
					outputFile = files[2];
					break;
				}
				default: {
					isValidWorkUnit = false;
					LOGGER.error("Usage: x9util -make inputFile routingListFile outputFile");
				}
			}
		} else if (StringUtils.equals(utilFunctionName, FUNCTION_MERGE)) {
			inputFile = files[0];
			switch (files.length) {
				case 2: {
					/*
					 * -merge <inputFolder> <outputFolder>
					 */
					outputFile = files[1];
					break;
				}
				default: {
					isValidWorkUnit = false;
					LOGGER.error("Usage: x9util -merge x9InputFolder outputFile");
				}
			}
		} else if (StringUtils.equals(utilFunctionName, FUNCTION_UPDATE)) {
			inputFile = files[0];
			switch (files.length) {
				case 2: {
					/*
					 * -update <inputFile> <xmlParameters>
					 */
					secondaryFile = files[1];
					final String baseName = FilenameUtils.removeExtension(inputFile.toString());
					outputFile = new File(baseName + "." + outExtension);
					resultsFile = new File(baseName + "." + X9C.CSV);
					break;
				}
				case 3: {
					/*
					 * -update <inputFile> <xmlParameters> <outputFile>
					 */
					secondaryFile = files[1];
					outputFile = files[2];
					final String baseName = FilenameUtils.removeExtension(outputFile.toString());
					resultsFile = new File(baseName + "." + X9C.CSV);
					break;
				}
				case 4: {
					/*
					 * -update <inputFile> <xmlParameters> <outputFile> <resultsFile>
					 */
					secondaryFile = files[1];
					outputFile = files[2];
					resultsFile = files[3];
					break;
				}
				default: {
					isValidWorkUnit = false;
					LOGGER.error("Usage: x9util -update inputFile parameters "
							+ "outputFile resultsFile");
				}
			}
		} else if (StringUtils.equals(utilFunctionName, FUNCTION_SPLIT)) {
			inputFile = files[0];
			switch (files.length) {
				case 2: {
					/*
					 * -split <inputFile> <xmlParameters>
					 */
					secondaryFile = files[1];
					outputFile = new File(X9FileUtils.getFolderName(inputFile));
					resultsFile = new File(
							FilenameUtils.removeExtension(inputFile.toString()) + "." + X9C.CSV);
					break;
				}
				case 3: {
					/*
					 * -update <inputFile> <xmlParameters> <outputFolder>
					 */
					secondaryFile = files[1];
					outputFile = files[2];
					resultsFile = new File(
							FilenameUtils.removeExtension(inputFile.toString()) + "." + X9C.CSV);
					break;
				}
				case 4: {
					/*
					 * -update <inputFile> <xmlParameters> <outputFolder> <resultsFile>
					 */
					secondaryFile = files[1];
					outputFile = files[2];
					resultsFile = files[3];
					break;
				}
				default: {
					isValidWorkUnit = false;
					LOGGER.error("Usage: x9util -split inputFile parameters "
							+ "x9OutputFolder resultsFile");
				}
			}
		} else if (StringUtils.equals(utilFunctionName, FUNCTION_COMPARE)) {
			inputFile = files[0];
			switch (files.length) {
				case 2: {
					/*
					 * -compare <inputFile1> <inputFile2>
					 */
					secondaryFile = files[1];
					final String folder = X9FileUtils.getFolderName(inputFile);
					final String baseName = FilenameUtils.getBaseName(inputFile.toString());
					outputFile = new File(folder, baseName + "_output." + X9C.TXT);
					resultsFile = new File(folder, baseName + "_output." + X9C.CSV);
					break;
				}
				case 3: {
					/*
					 * -compare <inputFile1> <inputFile2> <resultsFile>
					 */
					secondaryFile = files[1];
					resultsFile = files[2];
					final String folder = X9FileUtils.getFolderName(resultsFile);
					final String baseName = FilenameUtils.getBaseName(resultsFile.toString());
					outputFile = new File(folder, baseName + "." + X9C.CSV);
					break;
				}
				case 4: {
					/*
					 * -compare <inputFile1> <inputFile2> <outputFile> <resultsFile>
					 */
					secondaryFile = files[1];
					outputFile = files[2];
					resultsFile = files[3];
					break;
				}
				default: {
					isValidWorkUnit = false;
					LOGGER.error(
							"Usage: x9util -compare inputFile1 inputFile2 outputFile resultsFile");
				}
			}
		} else if (StringUtils.equals(utilFunctionName, FUNCTION_IMAGE_PULL)) {
			inputFile = files[0];
			switch (files.length) {
				case 1: {
					/*
					 * -imagePull <inputFile>
					 */
					final String folder = X9FileUtils.getFolderName(inputFile);
					final String baseName = FilenameUtils.getBaseName(inputFile.toString());
					resultsFile = new File(folder, baseName + "_RESULTS." + X9C.CSV);
					imageFolder = new File(folder, baseName + "_IMAGES");
					break;
				}
				case 2: {
					/*
					 * -imagePull <inputFile> <resultsFile>
					 */
					resultsFile = files[1];
					final String folder = X9FileUtils.getFolderName(resultsFile);
					final String baseName = FilenameUtils.getBaseName(resultsFile.toString());
					imageFolder = new File(folder, baseName + "_IMAGES");
					break;
				}
				case 3: {
					/*
					 * -imagePull <inputFile> <resultsFile> <imageFolder>
					 */
					resultsFile = files[1];
					imageFolder = files[2];
					break;
				}
				case 4: {
					/*
					 * -imagePull <inputFile> <csvParameters> <resultsFile> <imageFolder>
					 */
					secondaryFile = files[1];
					resultsFile = files[2];
					imageFolder = files[3];
					break;
				}
				default: {
					isValidWorkUnit = false;
					LOGGER.error("Usage: x9util -imagePull inputFile parametersFile "
							+ "resultsFile imageFolder");
				}
			}
		} else {
			isValidWorkUnit = false;
			logAsUsageWithAllAvailableFunctions();
		}

		/*
		 * Get functional switches for the current command.
		 */
		final String[] functionalSwitches = getFunctionalSwitches();

		/*
		 * Log any invalid command line switches that were identified.
		 */
		final String invalidSwitches = checkIfValidSwitches(functionalSwitches);
		if (StringUtils.isNotBlank(invalidSwitches)) {
			LOGGER.error("switch(es) are invalid({}) for selected function({})", invalidSwitches,
					utilFunctionName);
		}

		/*
		 * Log all command lines switches which are actually present with their values.
		 */
		logEnabledSwitches(functionalSwitches);

		/*
		 * Log all assigned input and output files.
		 */
		if (inputFile != null) {
			LOGGER.info("input file({})", inputFile);
		}

		if (secondaryFile != null) {
			LOGGER.info("secondary file({})", secondaryFile);
		}

		if (outputFile != null) {
			LOGGER.info("output file({})", outputFile);
		}

		if (resultsFile != null) {
			LOGGER.info("results file({})", resultsFile);
		}

		if (imageFolder != null) {
			LOGGER.info("image folder({})", imageFolder);
		}

		/*
		 * Ensure that our input file/folder exists which is always mandatory.
		 */
		if (isValidWorkUnit && !X9FileUtils.existsWithPathTracing(inputFile)) {
			throw X9Exception.abort("input file notFound({})", inputFile);
		}

		/*
		 * Returns true when the assignments represent a valid unit of work.
		 */
		return isValidWorkUnit;
	}

	/**
	 * Check if switches present on the command line are valid for the current function.
	 *
	 * @param functionalSwitches
	 *            array of valid switch settings for the active function
	 * @return null when all switches are valid otherwise string of invalid switch names
	 */
	public String checkIfValidSwitches(final String[] functionalSwitches) {
		/*
		 * Validate actual switches which are present on the command line.
		 */
		String invalidSwitches = "";
		final String[] commandSwitches = x9commandLine.getCommandSwitches();
		if (commandSwitches != null && commandSwitches.length > 0) {
			/*
			 * Build an array of actual command line switch values.
			 */
			final int switchCount = commandSwitches.length;
			final String[] switchesPresent = new String[switchCount];
			for (int i = 0; i < switchCount; i++) {
				/*
				 * Remove the leading dash (it will always be present). We then also extract the
				 * switch name for those switches that have attached values. In that case, the
				 * format is -switch:value so we just extract the actual switch name that appears
				 * before the ":". The remainder of the switch text (the value) can be ignored.
				 */
				switchesPresent[i] = StringUtils
						.substringBefore(StringUtils.removeStart(commandSwitches[i], "-"), ":");
			}

			/*
			 * Determine if command line switches that are physically present on the command line
			 * are contextually valid based on the actual command being executed.
			 */
			for (final String switchName : switchesPresent) {
				/*
				 * Identify and accept the actual function name itself. For example, UTIL writer
				 * will always have the "-write" switch present. Beyond that, we have certain global
				 * switches which apply to all commands (debug, loggingOn/Off, consoleOn/Off, etc).
				 */
				if (!StringUtils.equalsAnyIgnoreCase(switchName, utilFunctionName,
						X9UtilBatch.DEBUG_SWITCH, X9UtilBatch.CONSOLE_ON_SWITCH,
						X9UtilBatch.CONSOLE_OFF_SWITCH, X9UtilBatch.SWITCH_LOG_FOLDER)) {
					/*
					 * Determine if this switch is present for the current function.
					 */
					boolean isFound = false;
					for (final String functionalSwitch : functionalSwitches) {
						if (StringUtils.equalsAnyIgnoreCase(switchName, functionalSwitch)) {
							isFound = true;
							break;
						}
					}

					/*
					 * Build a string of invalid switches for this command.
					 */
					if (!isFound) {
						invalidSwitches = invalidSwitches
								+ (StringUtils.isNotBlank(invalidSwitches) ? ";" : "") + switchName;
					}
				}
			}
		}

		/*
		 * Log additional research information when we have identified invalid switches.
		 */
		if (StringUtils.isNotBlank(invalidSwitches)) {
			LOGGER.info("invalidSwitches({}) commandSwitches({}) functionalSwitches({})",
					invalidSwitches, StringUtils.join(commandSwitches, ";"),
					StringUtils.join(functionalSwitches, ";"));
		}

		/*
		 * Return invalid switch names and null when all switches are valid.
		 */
		return StringUtils.isBlank(invalidSwitches) ? null : invalidSwitches;
	}

	/**
	 * Log all functional switches which are currently enabled.
	 *
	 * @param functionalSwitches
	 *            array of valid switch settings for the active function
	 */
	public void logEnabledSwitches(final String[] functionalSwitches) {
		for (final String switchId : functionalSwitches) {
			if (isCommandSwitchSet(switchId)) {
				final String switchValue = getCommandSwitchValue(switchId);
				if (StringUtils.isBlank(switchValue)) {
					LOGGER.info("switch({}) enabled", switchId);
				} else {
					LOGGER.info("switch({}) enabled value({})", switchId, switchValue);
				}
			}
		}
	}

	/**
	 * Get the functional switches for this function.
	 *
	 * @return array of functional switches
	 */
	public String[] getFunctionalSwitches() {
		final String[] functionalSwitches = FUNCTIONAL_SWITCHES.get(utilFunctionName);
		return functionalSwitches == null ? new String[] {} : functionalSwitches;
	}

	/**
	 * Log command usage as a user help facility.
	 */
	public void logCommandUsage() {
		/*
		 * Format command line options by function.
		 */
		LOGGER.info(HELP_BREAK_LINE);
		LOGGER.info("command usage:");
		if (isCommandSwitchSet("write")) {
			LOGGER.info("x9util -write inputFile.csv [headerXml] [outputFile.x9] ");
			LOGGER.info("[-config:] [-l] [-j] [-x] [-t]");
			LOGGER.info("writes a new x9 output file from the provided input csv file");
			LOGGER.info("all image filenames must be provided in absolute format");
			LOGGER.info("headerXml       headerXml file which defines output x9 parameters; "
					+ "must be first csv row when omitted");
			LOGGER.info("outputFile      defaults to inputFile.x9 when not specified");
			LOGGER.info("-enp            overrides the need for the end statement on the items "
					+ "csv file (which is otherwise mandatory");
			LOGGER.info("-dnr            do not rewrite output file when it already exists");
			LOGGER.info("-dts            append date-time stamp to output file");
			LOGGER.info("-config:        specifies the x9 configuration to be loaded");
			LOGGER.info("-l              lists all records to the log");
			LOGGER.info("-j              creates json totals file in the output folder");
			LOGGER.info("-x              creates xml totals file in the output folder");
			LOGGER.info("-t              creates text totals file in the output folder");
		} else if (isCommandSwitchSet("translate")) {
			LOGGER.info("x9util -translate inputFile.x9 [outputFile.csv] [imageFolder]");
			LOGGER.info("[-config:] [-a] [-i] [-l] [-j] [-x] [-t]");
			LOGGER.info("reads an x9 input file to create an output csv and optional image folder");
			LOGGER.info("outputFile      defaults to inputFile.csv when not specified");
			LOGGER.info("imageFolder     defaults to outputFile_IMAGES when not specified");
			LOGGER.info("-config:        specifies the x9 configuration to be loaded");
			LOGGER.info("-a              includes addenda in the output csv file");
			LOGGER.info("-i              indicates that images should be exported to the "
					+ "imageFolder");
			LOGGER.info("-l              lists all records to the log");
			LOGGER.info("-j              creates json totals file in the output folder");
			LOGGER.info("-x              creates xml totals file in the output folder");
			LOGGER.info("-t              creates text totals file in the output folder");
		} else if (isCommandSwitchSet("import")) {
			LOGGER.info("x9util -import inputFile.csv [outputFile.x9] [imageFolder]");
			LOGGER.info("[-config:] [-l] [-j] [-x] [-t]");
			LOGGER.info("imports an input csv file and optional imageFolder to create an "
					+ "output x9 file");
			LOGGER.info("all image filenames must be provided in absolute format");
			LOGGER.info("outputFile      defaults to inputFile.x9 when not specified");
			LOGGER.info("imageFolder     defaults to outputFile_IMAGES when not specified");
			LOGGER.info("-config:        specifies the x9 configuration to be loaded");
			LOGGER.info("-r              indicates that trailer record totals should be "
					+ "automatically repaired");
			LOGGER.info("-l              lists all records to the log");
			LOGGER.info("-j              creates json totals file in the output folder");
			LOGGER.info("-x              creates xml totals file in the output folder");
			LOGGER.info("-t              creates text totals file in the output folder");
		} else if (isCommandSwitchSet("export")) {
			LOGGER.info("x9util -export inputFile.x9 [outputFile.csv] [imageFolder]");
			LOGGER.info("[-config:] [-xc] [-xf] [-xg] [-xm] [-i] [-ir] [-tif] [-jpg] [-png] "
					+ "[-gif] [-i64] [-i64mime] [-mp] [-xt] [-xml] [-ef] [-l] [-j] [-x] [-t]");
			LOGGER.info("exports an x9 input file to create output csv/xml with optional images");
			LOGGER.info("outputFile      defaults to inputFile.csv when not specified");
			LOGGER.info("imageFolder     defaults to outputFile_IMAGES when not specified");
			LOGGER.info("-config:        specifies the x9 configuration to be loaded");
			LOGGER.info("-xc             exports to csv as native x9 record/field format");
			LOGGER.info("-xf             exports to csv as parsed items in fixed column format");
			LOGGER.info("-xfc            exports to csv in fixed column format with headings");
			LOGGER.info("-xg             exports to csv as record groups in variable columns");
			LOGGER.info("-xm             folder level (multiple inputs written to one output");
			LOGGER.info("-i              exports images to the image folder with absolute "
					+ "file names inserted into the image data field");
			LOGGER.info("-ir             exports images to the image folder with relative "
					+ "file names inserted into the image data field");
			LOGGER.info("-tif            exports images in tif format");
			LOGGER.info("-jpg            exports images in jpg format");
			LOGGER.info("-png            exports images in png format");
			LOGGER.info("-gif            exports images in gif format");
			LOGGER.info("-i64            inserts images directly into the image data field as "
					+ "base64-basic strings during csv/xml export");
			LOGGER.info("-i64mime        inserts images directly into the image data field as "
					+ "base64-mime strings during csv/xml export");
			LOGGER.info("-mp             creates and exports a multi-page tiff to the image "
					+ "folder from the front+back tiff images");
			LOGGER.info("-xt             exports image tiff tags to csv");
			LOGGER.info("-xml            exports to xml (instead of our default which otherwise "
					+ "exports to csv)");
			LOGGER.info("-ef             includes fields which contain blanks data during "
					+ "xml export");
			LOGGER.info("-l              lists all records to the log");
			LOGGER.info("-j              creates json totals file in the output folder");
			LOGGER.info("-x              creates xml totals file in the output folder");
			LOGGER.info("-t              creates text totals file in the output folder");
		} else if (isCommandSwitchSet("exportCsv")) {
			LOGGER.info("x9util -exportCsv inputFile.x9 -xctl: -xfmt: -l");
			LOGGER.info("exports an x9 input file to a specific csv format with optional images");
			LOGGER.info("-xctl:          defines the export control xml file to be referenced");
			LOGGER.info("-xfmt:          defines the export format definition to be utilized");
		} else if (isCommandSwitchSet("validate")) {
			LOGGER.info("x9util -validate inputFile.x9 [outputFile.csv]");
			LOGGER.info("[-config:] [-l] [-j] [-x] [-t]");
			LOGGER.info("validates an x9 input file and creates an output csv error file");
			LOGGER.info("final exitStatus is set based on the types of errors which were "
					+ "identified during validation");
			LOGGER.info("exitStatus which is negative implies the validation was aborted");
			LOGGER.info("exitStatus = 0  (no errors found)");
			LOGGER.info("exitStatus = 1  (informational error messages issued)");
			LOGGER.info("exitStatus = 2  (warning error messages issued)");
			LOGGER.info("exitStatus = 3  (error error messages issued)");
			LOGGER.info("exitStatus = 4  (severe error messages issued)");
			LOGGER.info("-config:        specifies the x9 configuration to be loaded");
			LOGGER.info("-l              lists all records to the log");
			LOGGER.info("-j              creates json totals file in the output folder");
			LOGGER.info("-x              creates xml totals file in the output folder");
			LOGGER.info("-t              creates text totals file in the output folder");
		} else if (isCommandSwitchSet("scrub")) {
			LOGGER.info("x9util -scrub inputFile.x9 parameters.xml [outputFile.x9] [results.csv]");
			LOGGER.info("[-config:] [-l] [-j] [-x] [-t]");
			LOGGER.info("scrubs an x9 input file and creates a sanitized output x9 file");
			LOGGER.info("results.csv contains a summary of the fields which have been scrubbed");
			LOGGER.info("parameters      mandatory and defines the scrub actions to be applied");
			LOGGER.info("outputFile      defaults to inputFile.new when not specified");
			LOGGER.info("results         defaults to inputFile.csv when not specified");
			LOGGER.info("-config:        specifies the x9 configuration to be loaded");
			LOGGER.info("-xm             folder level (each file is scrubbed separately");
			LOGGER.info("-exti:x1|x2|... list of one or more input file extensions");
			LOGGER.info("-exto			 output file extension which otherwise defaults to 'new'");
			LOGGER.info("-l              lists all records to the log");
			LOGGER.info("-j              creates json totals file in the output folder");
			LOGGER.info("-x              creates xml totals file in the output folder");
			LOGGER.info("-t              creates text totals file in the output folder");
		} else if (isCommandSwitchSet("make")) {
			LOGGER.info("x9util -make inputFile.x9 [routingList.csv] outputFile.x9");
			LOGGER.info("[-config:] [-l] [-j] [-x] [-t]");
			LOGGER.info("make/generate an x9 file using a provided reformatter and generator");
			LOGGER.info("routingList      optional and defines the routing list to be utilized");
			LOGGER.info("-config:        specifies the x9 configuration to be loaded");
			LOGGER.info("-l              lists all records to the log");
			LOGGER.info("-j              creates json totals file in the output folder");
			LOGGER.info("-x              creates xml totals file in the output folder");
			LOGGER.info("-t              creates text totals file in the output folder");
		} else if (isCommandSwitchSet("merge")) {
			LOGGER.info("x9util -merge inputFolder outputFile.x9");
			LOGGER.info("[-config:] [-l] [-j] [-x] [-t]");
			LOGGER.info("merges the contents of the specified folder to a single x9 output file");
			LOGGER.info("inputFolder     required and contains input files to be merged");
			LOGGER.info("outputFile      required and will contain all items from all input files");
			LOGGER.info("-exti:x1|x2|... list of one or more input file extensions");
			LOGGER.info("-extr:merged    extension used to rename merged files on completion");
			LOGGER.info("-extf:failed    extension used to rename failed files on completion");
			LOGGER.info("-max:nnnn       maximum output size as either mb/kb or item count");
			LOGGER.info("-sf             include subfolders within the input folder");
			LOGGER.info("-sd             sort selected files descending by their attributes");
			LOGGER.info("-gbic           group files by item count for packaging");
			LOGGER.info("-t99            t99 trailers must be present to select an input file");
			LOGGER.info("-mrgb           indicates that merge is at the bundle level");
			LOGGER.info("-modb           bundle RTs should be modified from cash letter headers");
			LOGGER.info("-skpi           skip internal in seconds for transmissions in progress");
			LOGGER.info("-utsf:file.csv  indicates the time stamp file should be created");
			LOGGER.info("-dnr            do not rename merged files (ONLY used for testing");
			LOGGER.info("-config:        specifies the x9 configuration to be loaded");
			LOGGER.info("-l              lists all records to the log");
			LOGGER.info("-j              creates json totals file in the output folder");
			LOGGER.info("-x              creates xml totals file in the output folder");
			LOGGER.info("-t              creates text totals file in the output folder");
		} else if (isCommandSwitchSet("update")) {
			LOGGER.info("x9util -update inputFile.x9 parameters.xml [outputFile.x9] [results.csv]");
			LOGGER.info("[-config:] [-exto:] [-l] [-j] [-x] [-t]");
			LOGGER.info("updates an existing x9 input file by searching for field values and then "
					+ "creating an output x9 file with replacement values per the parameters file");
			LOGGER.info("results.csv contains a list of the before and after values for each field "
					+ " updated by this operation");
			LOGGER.info("parameters      mandatory and defines values for find/replace actions");
			LOGGER.info("outputFile      defaults to inputFile.new when not specified");
			LOGGER.info("results         defaults to inputFile.csv when not specified");
			LOGGER.info("-config:        specifies the x9 configuration to be loaded");
			LOGGER.info("-exto			 output file extension which otherwise defaults to 'new'");
			LOGGER.info("-l              lists all records to the log");
			LOGGER.info("-j              creates json totals file in the output folder");
			LOGGER.info("-x              creates xml totals file in the output folder");
			LOGGER.info("-t              creates text totals file in the output folder");
		} else if (isCommandSwitchSet("split")) {
			LOGGER.info("x9util -split inputFile.x9 parameters.xml [outputFolder] [results.csv]");
			LOGGER.info("[-config:] [-l] [-j] [-x] [-t]");
			LOGGER.info("splits an existing x9 input file by into segments on one or more input "
					+ "fields and then creating one or more output files per the parameters file");
			LOGGER.info("results.csv contains a list of the output files which have been created "
					+ "with record and item counts");
			LOGGER.info("parameters      mandatory and defines values for find/replace actions");
			LOGGER.info("outputFolder    defaults to the folder for inputFile.x9");
			LOGGER.info("results         defaults to inputFile.csv when not specified");
			LOGGER.info("-config:        specifies the x9 configuration to be loaded");
			LOGGER.info("-l              lists all records to the log");
			LOGGER.info("-j              creates json totals file in the output folder");
			LOGGER.info("-x              creates xml totals file in the output folder");
			LOGGER.info("-t              creates text totals file in the output folder");
		} else if (isCommandSwitchSet("compare")) {
			LOGGER.info(
					"x9util -compare inputFile1.x9 inputFile2.x9 [outputFile.txt] [results.csv]");
			LOGGER.info("[-config:] [-j] [-x] [-t] [ex:xx.xx|xx.xx|xx.xx|...");
			LOGGER.info(
					"compares two x9 files and creates a results file of any differences found");
			LOGGER.info("-exclude:xx.xx|xx.xx|xx.xx|... field(s) to be excluded from the compare");
			LOGGER.info("-config:        specifies the x9 configuration to be loaded");
			LOGGER.info("-j              creates json totals file in the output folder");
			LOGGER.info("-x              creates xml totals file in the output folder");
			LOGGER.info("-t              creates text totals file in the output folder");
		} else if (isCommandSwitchSet("imagePull")) {
			LOGGER.info(
					"x9util -imagePull inputFile.csv [parameters.xml] [resultsFile.csv] [imageFolder]");
			LOGGER.info("[-config:] [-cr] [-ib] [-ix] [-ic] [-ia] [-l]");
			LOGGER.info("extracts images from a provided series of x9 files based on item "
					+ "number and optional amount");
			LOGGER.info("parameters      optional and defines the fields to be written to "
					+ "results.csv; default is to write our standard list; requires all four (4) "
					+ "command line files to prevent ambiguity");
			LOGGER.info("resultsFile     defaults to inputFile_RESULTS.csv when not specified");
			LOGGER.info("imageFolder     defaults to resultsFile_IMAGES when not specified and "
					+ "can only be specified when results.csv is also specified");
			LOGGER.info("-config:        specifies the x9 configuration to be loaded");
			LOGGER.info("-cr             include credit record types 61 and 62");
			LOGGER.info("-ib             pull back side images");
			LOGGER.info("-ix             append a timestamp to the assigned image folder name");
			LOGGER.info("-ic             clear the assigned image folder");
			LOGGER.info("-ia             do not abort if the output image folder is not empty");
			LOGGER.info("-l              list record types 25/31 to the log");
		} else {
			logAllAvailableUtilityFunctions();
		}
		LOGGER.info(HELP_BREAK_LINE);
	}

	/**
	 * Get a newly allocated sdkBase for this work unit.
	 *
	 * @return newly allocated sdkBase
	 */
	public X9SdkBase getNewSdkBase() {
		/*
		 * Create an sdk instance.
		 */
		final X9SdkBase sdkBase = new X9SdkBase();

		/*
		 * Set our default image repair actions.
		 */
		isImageRepairEnabled = false;
		isImageResizeEnabled = false;

		/*
		 * Set image repair action from the command line.
		 */
		if (isCommandSwitchSet(SWITCH_IMAGE_REPAIR_ENABLED)) {
			isImageRepairEnabled = true;
		}

		/*
		 * Set image repair resize action from the command line.
		 */
		if (isCommandSwitchSet(SWITCH_IMAGE_RESIZE_ENABLED)) {
			isImageRepairEnabled = true;
			isImageResizeEnabled = true;
		}

		/*
		 * Return the created sdk base.
		 */
		return sdkBase;
	}

	/**
	 * Bind to the configuration identified on the command line and default to file header.
	 *
	 * @param sdkBase
	 *            sdkBase for this environment
	 */
	public void autoBindToCommandLineConfiguration(final X9SdkBase sdkBase) {
		if (bindToCommandLineConfiguration(sdkBase)) {
			X9ConfigSelector.autoBindToConfiguration(sdkBase, inputFile);
		}
	}

	/**
	 * Bind to the configuration identified on the command line with default to x937. This bind also
	 * supports an automated determination of the configuration (using X9ConfigSelector) which is
	 * based on the content of the type 01 file header. This automated bind is indicated by simply
	 * omitting the configuration name on the command line (which tells us to do the inspection).
	 *
	 * @param sdkBase
	 *            sdkBase for this environment
	 * @return true if auto bind is active
	 */
	public boolean bindToCommandLineConfiguration(final X9SdkBase sdkBase) {
		/*
		 * Set the configuration name when provided and otherwise default to x9.37. A big exception
		 * here is auto-bind, which initially defaults to x937 (in order to read the file header),
		 * and will then will be subsequently changed to a possibly more targeted configuration.
		 * This is a bit of a catch22, since the file header must be read, so we need to initially
		 * bind to something in order to read that record.
		 */
		String configName = X9.X9_37_CONFIG;
		final boolean isBindAuto = isBindConfigurationAuto();
		if (isBindAuto) {
			LOGGER.info("autoBind active");
		} else if (isCommandSwitchSet(SWITCH_CONFIG)) {
			configName = getCommandSwitchValue(SWITCH_CONFIG);
			LOGGER.info("configuration set from commandLine({})", configName);
		}

		/*
		 * Bind to the selected configuration.
		 */
		if (!sdkBase.bindConfiguration(configName)) {
			throw X9Exception.abort("bind unsuccessful({})", configName);
		}

		/*
		 * Return true when auto bind is active.
		 */
		return isBindAuto;
	}

	/**
	 * Determine if bind configuration is defaulting to auto, which directs that the configuration
	 * be automatically determined from the type 01 file header. The auto bind process can be
	 * generally applied to any of our utility functions that are file readers.
	 *
	 * @return true or false
	 */
	public boolean isBindConfigurationAuto() {
		return StringUtils.equalsAny(utilFunctionName, AUTO_BIND_FUNCTION_NAMES)
				&& !isCommandSwitchSet(SWITCH_CONFIG);
	}

	/**
	 * Get the maximum number of threads for this system. This number can be defined externally
	 * (from the command line parameter "-threads:" and will otherwise default to either 2 (for 32
	 * bit systems) and 4 (for 64 bit systems). Our design is to support larger numbers as possible,
	 * but we need to be careful here since each of these threads will be doing substantial work
	 * with a large read buffer. Also remember that our ability to process files concurrently is
	 * directly related to how large the Java heap is set either when X9Utilities is compiled or the
	 * command line assignment when running under a JVM.
	 *
	 * @return assigned thread count
	 */
	public int getThreadCount() {
		final String threads = getCommandSwitchValue(SWITCH_THREADS);
		final int threadCount;
		if (StringUtils.isBlank(threads)) {
			threadCount = X9Task.getSuggestedConcurrentThreads();
		} else {
			threadCount = X9Numeric.toInt(threads);
			if (threadCount < 0) {
				throw X9Exception.abort("threads({}) not numeric", threads);
			}
			if (threadCount > 8) {
				throw X9Exception.abort("threads({}) excessive", threads);
			}
		}
		return threadCount;
	}

	/**
	 * Get a list of files to be processed for this function. We interrogate the multi-file command
	 * line switch and return either a single file or a list of files subject to that setting. This
	 * is a convenience method since it can be used to simplify overall file list processing.
	 *
	 * @return list of files
	 */
	public List<X9File> getInputFileList() {
		final List<X9File> fileList;
		if (isCommandSwitchSet(X9UtilWorkUnit.SWITCH_MULTI_FILE)) {
			verifyInputIsFolder();
			final String inputExtensions = getCommandSwitchValue(SWITCH_EXTENSION_INPUT);
			final String[] inputFileExtensions = validateInputFileExtensions(inputExtensions);
			fileList = X9FileUtils.createInputFileList(inputFile, X9FileUtils.SUBFOLDERS_INCLUDED,
					inputFileExtensions, getFileSkipInterval(),
					X9FileUtils.LOG_SELECTED_FILES_ENABLED);
		} else {
			fileList = new ArrayList<>(1);
			fileList.add(new X9File(inputFile));
			if (!inputFile.exists()) {
				throw X9Exception.abort("inputFile not found({})", inputFile);
			}
			if (inputFile.isDirectory()) {
				throw X9Exception.abort("inputFile is directory({})", inputFile);
			}
		}
		return fileList;
	}

	/**
	 * Verify that our input and output folders exist and that they are different.
	 */
	public void verifyInputAndOutputAsFolders() {
		verifyInputIsFolder();
		verifyOutputIsFolder();
		if (inputFile.equals(outputFile)) {
			throw X9Exception.abort("inputFolder and outputFolder cannot be the same");
		}
	}

	/**
	 * Verify that our input file exists and is a directory (folder).
	 */
	public void verifyInputIsFolder() {
		if (inputFile.isDirectory()) {
			LOGGER.info("inputFolder({}) located", inputFile);
		} else {
			throw X9Exception.abort("inputFolder not a directory({})", inputFile);
		}
	}

	/**
	 * Verify that our output file exists and is a directory (folder).
	 */
	public void verifyOutputIsFolder() {
		if (outputFile.isDirectory()) {
			LOGGER.info("outputFolder({}) located", outputFile);
		} else {
			throw X9Exception.abort("outputFolder not a directory({})", outputFile);
		}
	}

	/**
	 * Validate input file extensions.
	 *
	 * @param inputExtensions
	 *            input extensions separated by pipe character
	 * @return input file extensions
	 */
	public String[] validateInputFileExtensions(final String inputExtensions) {
		/*
		 * Validate that we have at least one input file extension.
		 */
		final String[] inputFileExtensions = StringUtils.split(inputExtensions, '|');
		if (inputFileExtensions == null || inputFileExtensions.length == 0) {
			throw X9Exception.abort("inputFileExtensions({}) missing", SWITCH_EXTENSION_INPUT);
		}

		/*
		 * Validate that none of the input file extensions are blank.
		 */
		for (final String inputFileExtension : inputFileExtensions) {
			if (StringUtils.isBlank(inputFileExtension)) {
				throw X9Exception.abort("inputFileExtensions({}) has blank entry",
						SWITCH_EXTENSION_INPUT);
			}
		}

		/*
		 * Return the input file extensions.
		 */
		return inputFileExtensions;
	}

	/**
	 * Get the file modify delay window and default when not specified.
	 *
	 * @return file skip interval
	 */
	public int getFileSkipInterval() {
		final int fileSkipInterval;
		final String skipInterval = x9commandLine.getSwitchValue(SWITCH_SKIP_INTERVAL);
		if (StringUtils.isNotBlank(skipInterval)) {
			fileSkipInterval = X9Numeric.toInt(skipInterval);
			if (fileSkipInterval < 0) {
				throw X9Exception.abort("delayInterval({}) not numeric", skipInterval);
			}
		} else {
			fileSkipInterval = SKIP_INTERVAL_DEFAULT;
			LOGGER.info("skipInterval defaulted({})", fileSkipInterval);
		}
		return fileSkipInterval;
	}

	/**
	 * Write summary totals as requested by command line switches.
	 *
	 * @param x9totalsXml
	 *            accumulated summary totals
	 */
	public void writeSummaryTotals(final X9TotalsXml x9totalsXml) {
		writeSummaryTotals(x9totalsXml, isCommandSwitchSet(SWITCH_WRITE_XML_TOTALS),
				isCommandSwitchSet(SWITCH_WRITE_TEXT_TOTALS),
				isCommandSwitchSet(SWITCH_WRITE_JSON_TOTALS));
	}

	/**
	 * Write summary totals per provided switches.
	 *
	 * @param x9totalsXml
	 *            accumulated summary totals
	 * @param isWriteXmlTotals
	 *            true if xml totals to be written
	 * @param isWriteTextTotals
	 *            true if text totals to be written
	 * @param isWriteJsonTotals
	 *            true if text totals to be written
	 */
	public void writeSummaryTotals(final X9TotalsXml x9totalsXml, final boolean isWriteXmlTotals,
			final boolean isWriteTextTotals, final boolean isWriteJsonTotals) {
		/*
		 * Write file statistics to xml when requested via command line switch.
		 */
		if (isWriteXmlTotals) {
			final File summaryFile = makeSummaryFile(X9C.XML);
			if (summaryFile != null) {
				x9totalsXml.writeExternalXmlFile(summaryFile);
			}
		}

		/*
		 * Write file statistics to text when requested via command line switch.
		 */
		if (isWriteTextTotals) {
			x9totalsXml.writeTextFile(makeSummaryFile(X9C.TXT));
		}

		/*
		 * Write file statistics to json when requested via command line switch.
		 */
		if (isWriteJsonTotals) {
			x9totalsXml.writeJsonFile(makeSummaryFile(X9C.JSON));
		}
	}

	/**
	 * Make a summary file to be used to write statistics. It is important that the summary files
	 * have unique names since we can export an x9 file to xml and hence the summary file must be
	 * assigned a name that is different that the exported xml file itself.
	 *
	 * @param extension
	 *            desired summary file extension (eg, txt or xml)
	 * @return summary file
	 */
	public File makeSummaryFile(final String extension) {
		final File file = outputFile != null ? outputFile : inputFile;
		return file != null
				? new File(FilenameUtils.removeExtension(file.toString()) + SUMMARY_SUFFIX + "."
						+ extension)
				: null;
	}

	/**
	 * Abort when the input file is empty.
	 */
	public void abortOnEmptyFile() {
		throw X9Exception.abort("input file is empty({})", inputFile);
	}

	/**
	 * Abort when the input file is structurally flawed.
	 */
	public void abortOnStructurallyFlawedFile() {
		throw X9Exception.abort("input file is structurally flawed({})", inputFile);
	}

	/**
	 * Determine if a given switch is set on the command line for this work unit.
	 *
	 * @param switchId
	 *            switch identifier
	 * @return true or false
	 */
	public boolean isCommandSwitchSet(final String switchId) {
		return x9commandLine.isSwitchSet(switchId);
	}

	/**
	 * Get a switch value from the command line for this work unit.
	 *
	 * @param switchId
	 *            switch identifier
	 * @return switch value
	 */
	public String getCommandSwitchValue(final String switchId) {
		return x9commandLine.getSwitchValue(switchId);
	}

	/**
	 * Determine if image repair is enabled.
	 *
	 * @return true or false
	 */
	public boolean isImageRepairEnabled() {
		return isImageRepairEnabled;
	}

	/**
	 * Determine if image repair automated resize is enabled.
	 *
	 * @return true or false
	 */
	public boolean isImageResizeEnabled() {
		return isImageResizeEnabled;
	}

	/**
	 * Determine if a valid function switch is present on the command line.
	 *
	 * @return true or false
	 */
	public boolean isValidFunctionSwitchPresent() {
		/*
		 * Examine the command line for possible command line switches.
		 */
		int switchCount = 0;
		for (final String functionName : FUNCTION_NAMES) {
			if (isCommandSwitchSet(functionName)) {
				switchCount++;
				utilFunctionName = functionName;
			}
		}

		/*
		 * Error if there are multiple function switches present (very unexpected). This will catch
		 * the situation where there are different command line switches present, but it obviously
		 * does not identify the alternative situation when the same switch is present more than
		 * once. This latter case would be unusual but is not considered an error.
		 */
		if (switchCount > 1) {
			utilFunctionName = "";
			LOGGER.info("multiple function switches found on the command line");
			return false;
		}

		/*
		 * Error if no function switch found on the command line.
		 */
		if (switchCount == 0) {
			LOGGER.info("no function found on the command line");
			return false;
		}

		/*
		 * Otherwise we found exactly one function switch so all is well.
		 */
		return true;
	}

	/**
	 * Determine if multiple function switches are present on the command line.
	 *
	 * @return true or false
	 */
	public boolean hasMultipleFunctionSwitchesPresent() {
		int switchCount = 0;
		for (final String functionName : FUNCTION_NAMES) {
			if (isCommandSwitchSet(functionName)) {
				switchCount++;
				utilFunctionName = functionName;
			}
		}
		return (switchCount > 1);
	}

	/**
	 * Get an output temp file instance based on time stamp and do not rewrite switches.
	 * 
	 * @return temp file instance for the new output file
	 */
	public X9TempFile getOutputTempFileUsingDtsDnrSwitches() {
		final String timeStamp = isCommandSwitchSet(SWITCH_DATE_TIME_STAMP) ? "yyyyMMdd_HHmmss"
				: "";
		return getTempFileWithOptionalTimestamp(outputFile.toString(), timeStamp,
				isCommandSwitchSet(SWITCH_DO_NOT_REWRITE));
	}

	/**
	 * Get the use case file as set on the command line and confirm existence.
	 *
	 * @param switchId
	 *            command line switch identifier
	 * @return use case file
	 */
	private File getCommandLineFile(final String switchId) {
		if (!isCommandSwitchSet(switchId)) {
			throw X9Exception.abort("{} command line switch required", switchId);
		}

		final File file = new File(x9commandLine.getSwitchValue(switchId));
		LOGGER.info("{} file set from commandLine({})", switchId, file);

		if (!X9FileUtils.existsWithPathTracing(file)) {
			throw X9Exception.abort("{} file notFound({})", switchId, file);
		}

		return file;
	}

	/**
	 * Log proper usage followed by all available functions.
	 */
	private void logAsUsageWithAllAvailableFunctions() {
		LOGGER.error("Usage: when invoking x9utilities, the function must be -write -translate "
				+ "-import -export -exportCsv -validate -scrub -make -merge -update -split "
				+ "-compare  or -imagePull");
		LOGGER.error("Usage: for our X9Export product license, the only available functions "
				+ "are -export, -exportCsv, and -translate");
		logAllAvailableUtilityFunctions();
	}

	/**
	 * Log all available functions.
	 */
	private void logAllAvailableUtilityFunctions() {
		LOGGER.info("logAllAvailableUtilityFunctions");
		LOGGER.info("x9util -write -h");
		LOGGER.info("x9util -translate -h");
		LOGGER.info("x9util -import -h");
		LOGGER.info("x9util -export -h");
		LOGGER.info("x9util -exportCsv -h");
		LOGGER.info("x9util -validate -h");
		LOGGER.info("x9util -scrub -h");
		LOGGER.info("x9util -make -h");
		LOGGER.info("x9util -merge -h");
		LOGGER.info("x9util -update -h");
		LOGGER.info("x9util -split -h");
		LOGGER.info("x9util -compare -h");
		LOGGER.info("x9util -imagePull -h");
		LOGGER.info("-h provides more detailed information when entered along with each "
				+ "of the above functions (for example, -write -h)");
	}

	/**
	 * Get an output temp file instance with an optional time stamp suffix. We also provide the
	 * option to abort if the constructed file already exits (do not rewrite).
	 * 
	 * @param fileName
	 *            output file name
	 * @param timeStamp
	 *            time stamp date pattern or empty string when not needed
	 * @param isDoNotRewrite
	 *            true if the file should not be written otherwise false
	 * @return temp file instance for the new output file
	 */
	public static X9TempFile getTempFileWithOptionalTimestamp(final String fileName,
			final String timeStamp, final boolean isDoNotRewrite) {
		/*
		 * Ensure a file name has been provided.
		 */
		final String trimmedFileName = StringUtils.trim(fileName);
		if (StringUtils.isBlank(trimmedFileName)) {
			throw X9Exception.abort("output fileName missing");
		}

		/*
		 * Add the current timestamp when directed.
		 */
		final String outputFileName = StringUtils.isBlank(timeStamp) ? trimmedFileName
				: FilenameUtils.removeExtension(trimmedFileName) + "."
						+ X9Date.formatDateAsString(X9Date.getCurrentDate(), timeStamp) + "."
						+ FilenameUtils.getExtension(trimmedFileName);

		/*
		 * Abort if the output file already exists and we are instructed to not rewrite.
		 */
		final File timeStampedFile = new File(outputFileName);
		if (isDoNotRewrite && timeStampedFile.exists()) {
			throw X9Exception.abort("doNotRewrite=true and outputFile exists({})", timeStampedFile);
		}

		/*
		 * Return the constructed time stamped file.
		 */
		return getTempFileInstance(timeStampedFile);
	}

	/**
	 * Get an X9TempFile instance for an output file being created.
	 *
	 * @param file
	 *            output file
	 * @return temp file instance for the new output file
	 */
	public static X9TempFile getTempFileInstance(final File file) {
		return new X9TempFile(file);
	}

}
