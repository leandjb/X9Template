package sdkUtilities;

import java.util.ArrayList;

import org.apache.commons.lang3.StringUtils;

import com.x9ware.tools.X9CsvLine;

/**
 * X9UtilWriterCsvLines defines a list of the csv lines that are grouped for X9UtilWriter processing
 * which can be grouped by profile name when optionally reordered. Remember that these csv lines can
 * be more then just items, since they can be other line types (headerXml, imageFolder, image, etc).
 *
 * @author X9Ware LLC. Copyright(c) 2012-2018 X9Ware LLC. All Rights Reserved. This is proprietary
 *         software as developed and licensed by X9Ware LLC under the exclusive legal right of the
 *         copyright holder. All licensees are provided the right to use the software only under
 *         certain conditions, and are explicitly restricted from other specific uses including
 *         modification, sharing, reuse, redistribution, or reverse engineering.
 */
public class X9UtilWriterCsvLines extends ArrayList<X9CsvLine> {

	private static final long serialVersionUID = 1322145293790038477L;

	/*
	 * Private.
	 */
	private final String profileName;

	/*
	 * Constants.
	 */
	private static final int INITIAL_ROW_COUNT = 1000;

	/**
	 * X9UtilWriterCsvLines Constructor.
	 *
	 * @param profile_Name
	 *            profile name
	 */
	public X9UtilWriterCsvLines(final String profile_Name) {
		super(INITIAL_ROW_COUNT);
		profileName = StringUtils.isNotBlank(profile_Name) ? profile_Name : "";
	}

	/**
	 * Get the profile name.
	 *
	 * @return profile name
	 */
	public String getProfileName() {
		return profileName;
	}

}
