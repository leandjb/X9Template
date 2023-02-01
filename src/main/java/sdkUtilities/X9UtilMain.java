package sdkUtilities;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.x9ware.actions.X9Exception;
import com.x9ware.elements.X9Products;

/**
 * X9UtilMain is the static main class for X9Utilities, which is the command line interface for our
 * various batch products. The actual batch functions that are allowed will be determined based on
 * the current client license. If an unsupported function is invoked that is outside of the current
 * license limitations, then we immediately abort and no further commands will be executed.
 *
 * @author X9Ware LLC. Copyright(c) 2012-2018 X9Ware LLC. All Rights Reserved. This is proprietary
 *         software as developed and licensed by X9Ware LLC under the exclusive legal right of the
 *         copyright holder. All licensees are provided the right to use the software only under
 *         certain conditions, and are explicitly restricted from other specific uses including
 *         modification, sharing, reuse, redistribution, or reverse engineering.
 */
public class X9UtilMain extends X9UtilBatch {

	/*
	 * Constants.
	 */
	public static final boolean ENVIRONMENT_OPEN_CLOSE_ENABLED = true;
	public static final boolean ENVIRONMENT_OPEN_CLOSE_DISABLED = false;

	/**
	 * Logger instance.
	 */
	private static final Logger LOGGER = LoggerFactory.getLogger(X9UtilMain.class);

	/**
	 * X9UtilMain Constructor.
	 */
	public X9UtilMain() {
		/*
		 * This constructor is always used when x9utilities is run from the command line. In this
		 * launch scenario, we use the X9UTILITIES product name, which forces an x9utilities license
		 * key to be located and applied to the runtime environment.
		 */
		super(X9Products.X9UTILITIES, ENVIRONMENT_OPEN_ENABLED, ENVIRONMENT_CLOSE_ENABLED);
	}

	/**
	 * X9UtilMain Constructor with explicitly defined environment open and close parameters.
	 *
	 * @param is_EnvironmentToBeOpenedAndClosed
	 *            true or false
	 */
	public X9UtilMain(final boolean is_EnvironmentToBeOpenedAndClosed) {
		this(is_EnvironmentToBeOpenedAndClosed, is_EnvironmentToBeOpenedAndClosed);
	}

	/**
	 * X9UtilMain Constructor with explicitly defined open and close parameters.
	 *
	 * @param is_EnvironmentToBeOpened
	 *            true or false
	 * @param is_EnvironmentToBeClosed
	 *            true or false
	 */
	public X9UtilMain(final boolean is_EnvironmentToBeOpened,
			final boolean is_EnvironmentToBeClosed) {
		/*
		 * This constructor can only be invoked from an sdk application (it is never used from
		 * x9utilities command line). This could be an sdk user application, but it could also be
		 * x9assist running the utilities console. Either way, we now open the batch environment
		 * with our sdk product name. We can logically do this since the invoking application has
		 * already had its license key validated, hence it is appropriate to allow x9utilities to
		 * launch without further license key validation. This is a core requirement, since an
		 * x9assist user running the utilities console does not have an x9utilities license.
		 */
		super(X9Products.X9SDK, is_EnvironmentToBeOpened, is_EnvironmentToBeClosed);
	}

	/**
	 * Execute an X9Utilities command.
	 *
	 * @param workUnit
	 *            current work unit
	 * @return exit status from work unit
	 */
	private int executeUtilitiesCommand(final X9UtilWorkUnit workUnit) {
		/*
		 * Run the requested batch run unit subject to the current client license.
		 */
		final int exitStatus;
		final String functionName = workUnit.utilFunctionName;
		if (StringUtils.equals(functionName, X9UtilWorkUnit.FUNCTION_TRANSLATE)) {
			final X9UtilTranslate x9utilTranslate = new X9UtilTranslate(workUnit);
			exitStatus = x9utilTranslate.process();
		} else if (StringUtils.equals(functionName, X9UtilWorkUnit.FUNCTION_WRITE)) {
			final X9UtilWriter x9utilWriter = new X9UtilWriter(workUnit);
			exitStatus = x9utilWriter.process();
		} else if (StringUtils.equals(functionName, X9UtilWorkUnit.FUNCTION_IMPORT)) {
			final X9UtilImport x9utilImport = new X9UtilImport(workUnit);
			exitStatus = x9utilImport.process();
		} else if (StringUtils.equals(functionName, X9UtilWorkUnit.FUNCTION_EXPORT)) {
			final X9UtilExport x9utilExport = new X9UtilExport(workUnit);
			exitStatus = x9utilExport.process();
		} else if (StringUtils.equals(functionName, X9UtilWorkUnit.FUNCTION_EXPORT_CSV)) {
			final X9UtilExportCsv x9utilExportCsv = new X9UtilExportCsv(workUnit);
			exitStatus = x9utilExportCsv.process();
		} else if (StringUtils.equals(functionName, X9UtilWorkUnit.FUNCTION_VALIDATE)) {
			final X9UtilValidate x9utilValidate = new X9UtilValidate(workUnit);
			exitStatus = x9utilValidate.process();
		} else if (StringUtils.equals(functionName, X9UtilWorkUnit.FUNCTION_SCRUB)) {
			final X9UtilScrub x9utilScrub = new X9UtilScrub(workUnit);
			exitStatus = x9utilScrub.process();
		} else if (StringUtils.equals(functionName, X9UtilWorkUnit.FUNCTION_MAKE)) {
			final X9UtilMake x9utilMake = new X9UtilMake(workUnit);
			exitStatus = x9utilMake.process();
		} else if (StringUtils.equals(functionName, X9UtilWorkUnit.FUNCTION_MERGE)) {
			final X9UtilMerge x9utilMerge = new X9UtilMerge(workUnit);
			exitStatus = x9utilMerge.process();
		} else if (StringUtils.equals(functionName, X9UtilWorkUnit.FUNCTION_UPDATE)) {
			final X9UtilUpdate x9utilUpdate = new X9UtilUpdate(workUnit);
			exitStatus = x9utilUpdate.process();
		} else if (StringUtils.equals(functionName, X9UtilWorkUnit.FUNCTION_SPLIT)) {
			final X9UtilSplit x9utilSplit = new X9UtilSplit(workUnit);
			exitStatus = x9utilSplit.process();
		} else if (StringUtils.equals(functionName, X9UtilWorkUnit.FUNCTION_COMPARE)) {
			final X9UtilCompare x9utilCompare = new X9UtilCompare(workUnit);
			exitStatus = x9utilCompare.process();
		} else if (StringUtils.equals(functionName, X9UtilWorkUnit.FUNCTION_IMAGE_PULL)) {
			final X9UtilImagePull x9utilImagePull = new X9UtilImagePull(workUnit);
			exitStatus = x9utilImagePull.process();
		} else {
			exitStatus = X9UtilBatch.EXIT_STATUS_INVALID_FUNCTION;
			throw X9Exception.abort("invalid work unit({})", functionName);
		}

		/*
		 * Return our exit status.
		 */
		return exitStatus;
	}

	/**
	 * Execute an X9Export command.
	 *
	 * @param workUnit
	 *            current work unit
	 * @return exit status from work unit
	 */
	private int executeExportCommand(final X9UtilWorkUnit workUnit) {
		/*
		 * Run the requested batch run unit subject to the current client license.
		 */
		final int exitStatus;
		if (StringUtils.equals(workUnit.utilFunctionName, X9UtilWorkUnit.FUNCTION_TRANSLATE)) {
			final X9UtilTranslate x9utilTranslate = new X9UtilTranslate(workUnit);
			exitStatus = x9utilTranslate.process();
		} else if (StringUtils.equals(workUnit.utilFunctionName, X9UtilWorkUnit.FUNCTION_EXPORT)) {
			final X9UtilExport x9utilExport = new X9UtilExport(workUnit);
			exitStatus = x9utilExport.process();
		} else if (StringUtils.equals(workUnit.utilFunctionName,
				X9UtilWorkUnit.FUNCTION_EXPORT_CSV)) {
			final X9UtilExportCsv x9utilExportCsv = new X9UtilExportCsv(workUnit);
			exitStatus = x9utilExportCsv.process();
		} else {
			exitStatus = X9UtilBatch.EXIT_STATUS_INVALID_FUNCTION;
			throw X9Exception.abort("invalid work unit({})", workUnit.utilFunctionName);
		}

		/*
		 * Return our exit status.
		 */
		return exitStatus;
	}

	@Override
	public int runBatchCommand(final X9UtilWorkUnit workUnit) {
		/*
		 * Assign the product name to be utilized by this environment. SDK can be set when invoked
		 * by an SDK user application or the x9assist utilities console.
		 */
		final String productName = StringUtils.equals(X9Products.X9SDK, getProgramName())
				? X9Products.X9SDK
				: getClientLicense().getProductName();

		/*
		 * Run the requested batch run unit subject to the current client license.
		 */
		final int exitStatus;
		if (StringUtils.equalsAny(productName, X9Products.X9UTILITIES, X9Products.X9SDK)) {
			exitStatus = executeUtilitiesCommand(workUnit);
		} else if (StringUtils.equals(productName, X9Products.X9EXPORT)) {
			exitStatus = executeExportCommand(workUnit);
		} else {
			exitStatus = X9UtilBatch.EXIT_STATUS_ABORTED;
			throw X9Exception.abort("unable to launch this batch product using the located "
					+ "license with productName({})", productName);
		}

		/*
		 * Return our exit status.
		 */
		return exitStatus;
	}

	/**
	 * Main as invoked directly from the command line. The only thing unique here is that we include
	 * system exit which terminates the currently running JVM. Our launch method can otherwise be
	 * used for more control over the runtime environment.
	 *
	 * @param args
	 *            command line arguments
	 */
	public static void main(final String[] args) {
		/*
		 * Run using try-with-resources to ensure we always close and system exit.
		 */
		int exitStatus = EXIT_STATUS_ABORTED;
		try (final X9UtilMain x9utilMain = new X9UtilMain()) {
			final X9UtilWorkResults workResults = x9utilMain.launch(args);
			exitStatus = workResults.getExitStatus();
		} catch (final Throwable t) {
			LOGGER.error("exception", t);
		} finally {
			System.exit(exitStatus);
		}
	}

}
