package sdkUtilities;

import java.io.File;

import com.x9ware.create.X9ScrubXml;

/**
 * X9UtilScrubEntry defines the attributes of a single file to be scrubbed.
 *
 * @author X9Ware LLC. Copyright(c) 2012-2018 X9Ware LLC. All Rights Reserved. This is proprietary
 *         software as developed and licensed by X9Ware LLC under the exclusive legal right of the
 *         copyright holder. All licensees are provided the right to use the software only under
 *         certain conditions, and are specifically restricted from other specific uses including
 *         modification, sharing, reuse, redistribution, or reverse engineering.
 */
public final class X9UtilScrubEntry {

	/**
	 * X9UtilWorkUnit instance which describes the unit of work assigned to us.
	 */
	private final X9UtilWorkUnit workUnit;

	/*
	 * Private.
	 */
	private final X9ScrubXml scrubXml;
	private final File inputFile;
	private final File outputFile;

	/**
	 * X9UtilScrubEntry Constructor.
	 *
	 * @param work_Unit
	 *            current work unit
	 * @param scrub_Xml
	 *            scrub xml definition to be applied
	 * @param input_File
	 *            current input file
	 * @param output_File
	 *            current output file
	 */
	public X9UtilScrubEntry(final X9UtilWorkUnit work_Unit, final X9ScrubXml scrub_Xml,
			final File input_File, final File output_File) {
		workUnit = work_Unit;
		scrubXml = scrub_Xml;
		inputFile = input_File;
		outputFile = output_File;
	}

	/**
	 * Get the assigned work unit.
	 *
	 * @return work unit
	 */
	public X9UtilWorkUnit getWorkUnit() {
		return workUnit;
	}

	/**
	 * Get the scrub xml definition to be applied.
	 *
	 * @return scrub xml definition
	 */
	public X9ScrubXml getScrubXml() {
		return scrubXml;
	}

	/**
	 * Get the assigned input file.
	 *
	 * @return input file
	 */
	public File getInputFile() {
		return inputFile;
	}

	/**
	 * Get the assigned output file.
	 *
	 * @return output file
	 */
	public File getOutputFile() {
		return outputFile;
	}

}
