package sdkUtilities;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.x9ware.actions.X9Exception;
import com.x9ware.apacheIO.WildcardFileFilter;
import com.x9ware.base.X9SdkRoot;
import com.x9ware.core.X9ProductManager;
import com.x9ware.elements.X9ProductFactory;
import com.x9ware.elements.X9ProductGroup;
import com.x9ware.licensing.X9Credentials;
import com.x9ware.licensing.X9License;
import com.x9ware.logging.X9JdkLogger;
import com.x9ware.options.X9OptionsManager;
import com.x9ware.toolbox.X9Purge;
import com.x9ware.tools.X9BuildAttr;
import com.x9ware.tools.X9CommandLine;
import com.x9ware.tools.X9FileUtils;
import com.x9ware.tools.X9Task;

/**
 * X9UtilBatch is an abstract class which contains the core batch driver logic as needed by command
 * line (batch) processing. X9UtilBatch was originally designed to support various batch environment
 * (X9Utilities, X9Export, etc) that were standalone offerings with a separate executable, but that
 * need was eliminated with the R4.04 product and license key enhancements. X9UtilBatch is still
 * useful as functional separation and simplification of X9UtilMain, and has been retained since it
 * may add value in the future. X9UtilBatch represents a single logical work unit where all enclosed
 * tasks are performed as a group and will result in an exit status that is the maximum of all
 * performed tasks. Everything we do here runs sequentially within a single thread. We implement
 * closeable as part of try-with-resources to easily ensure that our close method is always invoked
 * by the caller. Finally, note that we purposefully have not defined any of our methods as final,
 * allowing them to be overridden by possible extension classes of the these capabilities.
 *
 * @author X9Ware LLC. Copyright(c) 2012-2018 X9Ware LLC. All Rights Reserved. This is proprietary
 *         software as developed and licensed by X9Ware LLC under the exclusive legal right of the
 *         copyright holder. All licensees are provided the right to use the software only under
 *         certain conditions, and are explicitly restricted from other specific uses including
 *         modification, sharing, reuse, redistribution, or reverse engineering.
 */
abstract public class X9UtilBatch implements AutoCloseable {

	/*
	 * Private.
	 */
	private final String programName;
	private final boolean isEnvironmentToBeOpened;
	private final boolean isEnvironmentToBeClosed;
	private int exitStatus = EXIT_STATUS_ZERO;
	private X9License x9license;

	/**
	 * Constants.
	 */
	private static final String HELP_SWITCH = "h";
	public static final String DEBUG_SWITCH = "debug";
	public static final String SWITCH_LOG_FOLDER = "log";
	public static final String CONSOLE_ON_SWITCH = "consoleOn";
	public static final String CONSOLE_OFF_SWITCH = "consoleOff";

	/*
	 * Sdk open and close flags.
	 */
	public static final boolean ENVIRONMENT_OPEN_ENABLED = true;
	public static final boolean ENVIRONMENT_OPEN_DISABLED = false;
	public static final boolean ENVIRONMENT_CLOSE_ENABLED = true;
	public static final boolean ENVIRONMENT_CLOSE_DISABLED = false;

	/*
	 * Public exit status.
	 */
	public static final int EXIT_STATUS_ZERO = 0;
	public static final int EXIT_STATUS_ABORTED = -1;
	public static final int EXIT_STATUS_INVALID_FUNCTION = -2;
	public static final int EXIT_STATUS_FILE_NOT_FOUND = -3;

	/**
	 * Logger instance.
	 */
	private static final Logger LOGGER = LoggerFactory.getLogger(X9UtilBatch.class);

	/**
	 * X9UtilBatch Constructor.
	 *
	 * @param program_Name
	 *            batch program name
	 */
	public X9UtilBatch(final String program_Name) {
		this(program_Name, ENVIRONMENT_OPEN_ENABLED, ENVIRONMENT_CLOSE_ENABLED);
	}

	/**
	 * X9UtilBatch Constructor.
	 *
	 * @param program_Name
	 *            batch program name
	 * @param is_EnvironmentToBeOpened
	 *            true or false
	 * @param is_EnvironmentToBeClosed
	 *            true or false
	 */
	public X9UtilBatch(final String program_Name, final boolean is_EnvironmentToBeOpened,
			final boolean is_EnvironmentToBeClosed) {
		programName = program_Name;
		isEnvironmentToBeOpened = is_EnvironmentToBeOpened;
		isEnvironmentToBeClosed = is_EnvironmentToBeClosed;
	}

	/**
	 * Launch a new utility function in a standard way.
	 *
	 * @param args
	 *            command line arguments
	 * @return x9utilities work results
	 */
	public X9UtilWorkResults launch(final String[] args) {
		final X9CommandLine x9commandLine = new X9CommandLine(args);
		if (isEnvironmentToBeOpened) {
			open(x9commandLine);
		}
		return process(x9commandLine);
	}

	/**
	 * Open this batch environment.
	 *
	 * @param x9commandLine
	 *            current command line
	 */
	public void open(final X9CommandLine x9commandLine) {
		/*
		 * Initialize.
		 */
		initSdkSettings();
		initLoggingEnvironment(x9commandLine);
		initClientLicense();
		initIssueCommentaries();

		/*
		 * Perform further initialization when not a functional help request.
		 */
		if (!x9commandLine.isSwitchSet(HELP_SWITCH)) {
			initLoadStartupFiles();
			initPurgeExpiredLogs();
		}
	}

	/**
	 * Initialize sdk settings.
	 */
	public void initSdkSettings() {
		X9SdkRoot.setHeadless();
		X9ProductManager.setProductName(programName, null);
	}

	/**
	 * Initialize the logging environment.
	 *
	 * @param x9commandLine
	 *            current command line
	 */
	public void initLoggingEnvironment(final X9CommandLine x9commandLine) {
		/*
		 * Activate the console log when console logging is enabled from the command line. When
		 * running from an EXE created by JPackage, the --win-console option is always enabled which
		 * activates the console; this processing then determines if output is actually written.
		 * When running directly under a JVM, there are additional controls provided based on
		 * launching with either java.exe or javaw.exe. It would conceptually be nice to only
		 * activate when we are physically attached to a console, but that determination is
		 * difficult because System.console() is more about how the application is started than
		 * whether a console device physically exists on this system. For example, system console
		 * will be null when running under Eclipse. After considerable research, our decision is
		 * that we want to make the application behave very predictably and thus we faithfully
		 * follow our console setting from the command line. Hence bottom line is that the
		 * consoleOn/consoleOff will completely drive our decision to open the console log.
		 * According to the API, whether a virtual machine has a console is dependent upon the
		 * underlying platform and also upon the manner in which the virtual machine is invoked. If
		 * the virtual machine is started from an interactive command line without redirecting the
		 * standard input and output streams then its console will exist and will typically be
		 * connected to the keyboard and display from which the virtual machine was launched. If the
		 * virtual machine is started automatically, for example by a background job scheduler, then
		 * it will typically not have a console.
		 */
		final boolean isConsoleEnabled = !x9commandLine.isSwitchSet(CONSOLE_OFF_SWITCH);
		X9JdkLogger.setConsoleLogEnabled(isConsoleEnabled);

		/*
		 * Initialize the log using an optional log folder directive from the command line.
		 */
		final String logFolderName;
		if (x9commandLine.isSwitchSet(SWITCH_LOG_FOLDER)) {
			logFolderName = x9commandLine.getSwitchValue(SWITCH_LOG_FOLDER);
			X9JdkLogger.initialize(new File(logFolderName));
		} else {
			logFolderName = "defaulted";
			X9JdkLogger.initialize();
		}

		/*
		 * Enable debug from the command line when directed.
		 */
		final boolean isDebug = x9commandLine.isSwitchSet(DEBUG_SWITCH);
		if (isDebug) {
			X9JdkLogger.setLogLevelAsDebug();
		}

		/*
		 * Log our startup message.
		 */
		LOGGER.info("{} started; logFolder[{}] isConsoleEnabled({}) isDebug({})", programName,
				logFolderName, isConsoleEnabled, isDebug);
		LOGGER.info("command line switches -consoleOn and -consoleOff can be used to "
				+ "enable/disable the console window");
	}

	/**
	 * Initialize the client license (must be done after logging has been opened).
	 */
	public void initClientLicense() {
		/*
		 * The encrypted license is typically only set by SDK applications. However, we also allow
		 * SDK customers to directly invoke X9Utilities functions from their SDK applications.
		 * Because of that, we allow an SDK license to be accepted here.
		 */
		final String licenseXmlDocument = X9BuildAttr.getEncryptedLicenseXmlDocument();
		if (StringUtils.isNotBlank(licenseXmlDocument)) {
			/*
			 * Set a hard-wired license.
			 */
			x9license = X9Credentials.setLicenseFromXmlDocumentString(licenseXmlDocument);
		} else {
			/*
			 * Get the best possible batch license (which includes SDK licenses).
			 */
			final X9ProductGroup x9productGroup = X9ProductFactory.getBatchProductGroup();
			x9license = X9Credentials.getRuntimeLicense(x9productGroup);

			/*
			 * Abort if no license was found or if it is expired.
			 */
			if (x9license == null) {
				throw X9Exception.abort("no license found");
			} else if (x9license.isLicenseExpired()) {
				throw X9Exception.abort("license is expired");
			} else if (!x9license.isMatchingProduct(x9productGroup)) {
				throw X9Exception.abort("license has non-matching product");
			}

			/*
			 * Set and log the client credentials for this runtime environment.
			 */
			X9Credentials.setAndLogCredentials(x9license);
		}
	}

	/**
	 * Issue various commentary messages (must be done after the client license is assigned).
	 */
	public void initIssueCommentaries() {
		/*
		 * Log when this is a candidate build (not yet final).
		 */
		X9BuildAttr.issueCandidateBuildMessageWhenNeeded(programName);
	}

	/**
	 * Load the required startup files.
	 */
	public void initLoadStartupFiles() {
		X9SdkRoot.logStartupEnvironment(programName);
		X9SdkRoot.loadXmlConfigurationFiles();
		X9OptionsManager.logStartupFolders();
	}

	/**
	 * Purge the expired log files.
	 */
	public void initPurgeExpiredLogs() {
		X9Purge.purgeLogFiles();
	}

	/**
	 * Run the next x9utilities batch process. In most situations, there will only be single
	 * process() task within a given x9utilities execution. However, we are designed to allow open,
	 * multiple process tasks, and then finally close. Also note that since we are closeable, the
	 * close would typically be issued automatically behind the scenes by try-with-resources.
	 *
	 * @param x9commandLine
	 *            current command line
	 * @return x9utilities work results
	 */
	public X9UtilWorkResults process(final X9CommandLine x9commandLine) {
		/*
		 * Log the command line.
		 */
		final String[] args = x9commandLine.getCommandArgs();
		LOGGER.info("command line: {}", StringUtils.join(args, ' '));
		LOGGER.info("command args({}) argsLength({})", StringUtils.join(args, '|'), args.length);

		/*
		 * Process based on command line directives.
		 */
		final long startTime = System.currentTimeMillis();
		final X9UtilWorkUnit workUnit = new X9UtilWorkUnit(x9commandLine);
		try {
			if (x9commandLine.isSwitchSet(HELP_SWITCH)) {
				/*
				 * Log command usage (help) when requested.
				 */
				workUnit.logCommandUsage();
			} else {
				/*
				 * Execute the command when determined to be a known function.
				 */
				final boolean isValidCommand = workUnit.setup(x9commandLine.getCommandFiles());
				if (isValidCommand) {
					/*
					 * Invoke the requested function.
					 */
					final int status = runBatchCommand(workUnit);

					/*
					 * Accumulate the highest encountered exit status but do not allow that to
					 * replace any negative exit status values that have been set earlier.
					 */
					if (exitStatus >= 0) {
						if (status < 0) {
							/*
							 * Assign this new exit status when it is negative.
							 */
							exitStatus = status;
						} else {
							/*
							 * Otherwise assign the highest exit status that we have encountered.
							 */
							exitStatus = Math.max(status, exitStatus);
						}
					}

					/*
					 * Log the completion, elapsed time, and highest exit status encountered.
					 */
					LOGGER.info(
							"function({}) completed elapsed({}) status({}) "
									+ "accumulated exitStatus({})",
							workUnit.utilFunctionName.toLowerCase(),
							X9Task.formatElapsedSeconds(startTime), status, exitStatus);
				} else {
					exitStatus = EXIT_STATUS_INVALID_FUNCTION;
				}
			}
		} catch (final FileNotFoundException ex) {
			LOGGER.error("fileNotFoundException exception", ex);
			exitStatus = EXIT_STATUS_FILE_NOT_FOUND;
		} catch (final Throwable t) {
			LOGGER.error("exception", t);
			exitStatus = EXIT_STATUS_ABORTED;
		}

		/*
		 * Return our work results.
		 */
		return new X9UtilWorkResults(workUnit, exitStatus);
	}

	/**
	 * Close this batch environment.
	 */
	@Override
	public void close() {
		if (isEnvironmentToBeClosed) {
			X9SdkRoot.shutdown();
			LOGGER.info("{} exitStatus({})", programName, exitStatus);
			X9JdkLogger.closeLog();
		}
	}

	/**
	 * Update the exit status which is posted at end of run.
	 *
	 * @param taskStatus
	 *            task exit status
	 * @return updated exit status
	 */
	protected int updateExitStatus(final int taskStatus) {
		exitStatus = Math.max(exitStatus, taskStatus);
		return exitStatus;
	}

	/**
	 * Get a list of work units from the contents of a directory using a wild card pattern. Each
	 * work unit will consist solely of the input file. This then requires that each worker task
	 * supports a single parameter and can default all others from that.
	 *
	 * @param directory
	 *            current directory
	 * @param fileFilter
	 *            file filter which represents the wild card patterns to be matched
	 * @param x9commandLine
	 *            command line with switch settings that apply to this unit of work
	 * @return list of work units
	 * @throws FileNotFoundException
	 */
	protected List<X9UtilWorkUnit> getWorkUnitsRecursively(final File directory,
			final WildcardFileFilter fileFilter, final X9CommandLine x9commandLine)
			throws FileNotFoundException {
		final List<File> fileList = new ArrayList<>();
		X9FileUtils.getFilteredListRecursively(directory, fileList, fileFilter);
		final List<X9UtilWorkUnit> workUnitList = new ArrayList<>();
		for (final File file : fileList) {
			final X9UtilWorkUnit workUnit = new X9UtilWorkUnit(x9commandLine);
			workUnit.setup(file);
			workUnitList.add(workUnit);
		}
		return workUnitList;
	}

	/**
	 * Get the assigned program name.
	 *
	 * @return program name
	 */
	protected String getProgramName() {
		return programName;
	}

	/**
	 * Get the current client license, which must be previously set for this work unit.
	 *
	 * @return current client license
	 */
	protected X9License getClientLicense() {
		if (x9license == null) {
			throw X9Exception.abort("license not set");
		}
		return x9license;
	}

	/**
	 * Run the command as indicated from the work unit.
	 *
	 * @param workUnit
	 *            command work unit
	 * @return exit status based on command completion
	 */
	public abstract int runBatchCommand(final X9UtilWorkUnit workUnit);

}
