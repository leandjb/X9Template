package sdkUtilities;

import java.io.File;
import java.util.Map;
import java.util.TreeMap;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.x9ware.beans.X9UtilSplitBean;

/**
 * X9UtilSplitMap anchors a sorted map of all split output segments.
 *
 * @author X9Ware LLC. Copyright(c) 2012-2018 X9Ware LLC. All Rights Reserved. This is proprietary
 *         software as developed and licensed by X9Ware LLC under the exclusive legal right of the
 *         copyright holder. All licensees are provided the right to use the software only under
 *         certain conditions, and are specifically restricted from other specific uses including
 *         modification, sharing, reuse, redistribution, or reverse engineering.
 */
public class X9UtilSplitMap {

	/*
	 * Private.
	 */
	private final Map<String, X9UtilSplitSegment> splitMap = new TreeMap<>();

	/*
	 * Constants.
	 */
	private static final int SEGMENT_SEPARATOR_LENGTH = 110;

	/**
	 * X9UtilSplitMap Constructor.
	 */
	public X9UtilSplitMap() {
	}

	/**
	 * Logger instance.
	 */
	private static final Logger LOGGER = LoggerFactory.getLogger(X9UtilSplitMap.class);

	/**
	 * Get the split map.
	 *
	 * @return split map
	 */
	public Map<String, X9UtilSplitSegment> getSplitMap() {
		return splitMap;
	}

	/**
	 * Log totals for all output segments.
	 */
	public void logAllOutputTotals() {
		int segmentNumber = 0;
		for (final X9UtilSplitSegment splitSegment : splitMap.values()) {
			LOGGER.info(StringUtils.repeat('*', SEGMENT_SEPARATOR_LENGTH));
			splitSegment.logSegmentSummary(++segmentNumber);
		}
		LOGGER.info(StringUtils.repeat('*', SEGMENT_SEPARATOR_LENGTH));
	}

	/**
	 * Get the split output tracking instance for a particular defined split output entry.
	 *
	 * @param outputFolder
	 *            output folder
	 * @param outputEntry
	 *            current output entry
	 * @return split output tracking instance
	 */
	public X9UtilSplitSegment getSplitSegment(final File outputFolder,
			final X9UtilSplitBean.Output outputEntry) {
		X9UtilSplitSegment splitOutput = splitMap.get(outputEntry.fileName);
		if (splitOutput == null) {
			splitOutput = new X9UtilSplitSegment(outputFolder, outputEntry);
			splitMap.put(outputEntry.fileName, splitOutput);
		}
		return splitOutput;
	}

}
