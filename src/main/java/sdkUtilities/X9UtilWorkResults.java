package sdkUtilities;

/**
 * X9UtilWorkResults is used to post X9Utilities completion results.
 *
 * @author X9Ware LLC. Copyright(c) 2012-2018 X9Ware LLC. All Rights Reserved. This is proprietary
 *         software as developed and licensed by X9Ware LLC under the exclusive legal right of the
 *         copyright holder. All licensees are provided the right to use the software only under
 *         certain conditions, and are explicitly restricted from other specific uses including
 *         modification, sharing, reuse, redistribution, or reverse engineering.
 */
public class X9UtilWorkResults {

	/*
	 * Private.
	 */
	private final X9UtilWorkUnit workUnit;
	private final int exitStatus;

	/**
	 * X9UtilWorkResults Constructor.
	 *
	 * @param work_Unit
	 *            work unit
	 * @param exit_Status
	 *            exit status
	 */
	public X9UtilWorkResults(final X9UtilWorkUnit work_Unit, final int exit_Status) {
		workUnit = work_Unit;
		exitStatus = exit_Status;
	}

	/**
	 * Get work unit.
	 *
	 * @return work unit
	 */
	public X9UtilWorkUnit getWorkUnit() {
		return workUnit;
	}

	/**
	 * Get exit status.
	 *
	 * @return exit status
	 */
	public int getExitStatus() {
		return exitStatus;
	}

}
