package sdkUtilities;

import java.io.File;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.x9ware.base.X9Object;
import com.x9ware.beans.X9UtilSplitBean;

/**
 * X9UtilSplitSegment defines one of the output segments that is being written by X9UtilSplit. Each
 * split output instance tracks the totals, items, and keys associated a this particular segment.
 *
 * @author X9Ware LLC. Copyright(c) 2012-2018 X9Ware LLC. All Rights Reserved. This is proprietary
 *         software as developed and licensed by X9Ware LLC under the exclusive legal right of the
 *         copyright holder. All licensees are provided the right to use the software only under
 *         certain conditions, and are specifically restricted from other specific uses including
 *         modification, sharing, reuse, redistribution, or reverse engineering.
 */
public class X9UtilSplitSegment {

	/*
	 * Private.
	 */
	private final X9UtilSplitBean.Output outputEntry;
	private final File outputFile;
	private final String fileName;
	private int recordCount;
	private int debitCount;
	private BigDecimal debitAmount = BigDecimal.ZERO;
	private int creditCount;
	private BigDecimal creditAmount = BigDecimal.ZERO;

	/**
	 * List of split output keys that have been assigned to this output segment.
	 */
	private final List<String> splitKeyList = new ArrayList<>(INITIAL_KEY_LIST_SIZE);

	/**
	 * List of items that have been assigned to this output segment.
	 */
	private final List<X9Object> itemList = new ArrayList<>(INITIAL_ITEM_LIST_SIZE);

	/*
	 * Constants.
	 */
	private static final int INITIAL_KEY_LIST_SIZE = 50;
	private static final int INITIAL_ITEM_LIST_SIZE = 1000;

	/**
	 * Logger instance.
	 */
	private static final Logger LOGGER = LoggerFactory.getLogger(X9UtilSplitSegment.class);

	/**
	 * X9UtilSplitSegment Constructor.
	 *
	 * @param outputFolder
	 *            output folder
	 * @param output_Entry
	 *            split output to be written
	 */
	public X9UtilSplitSegment(final File outputFolder, final X9UtilSplitBean.Output output_Entry) {
		/*
		 * Basic assignments.
		 */
		outputEntry = output_Entry;
		fileName = outputEntry.fileName;

		/*
		 * Assign this split output segment which can be null (when there is no output file for this
		 * segment, or when it has been disabled) or can be an actual file (when an output file will
		 * be written). Essentially, envision the input file being spread across segments, where not
		 * each segment is optionally written. For example, the default segment may not be needed.
		 * But there can also be a defined output segment that is not written (it only consumes
		 * items) allowing those items to be purposefully excluded from the default file.
		 */
		outputFile = StringUtils.isNotBlank(fileName) && outputEntry.writeEnabled
				? new File(outputFolder, fileName)
				: null;
	}

	/**
	 * Get the split xml bean output entry associated with this output segment.
	 *
	 * @return xml beam output entry
	 */
	public X9UtilSplitBean.Output getOutputEntry() {
		return outputEntry;
	}

	/**
	 * Get the output file.
	 *
	 * @return output file (which can be null when there is no assigned output file)
	 */
	public File getOutputFile() {
		return outputFile;
	}

	/**
	 * Get the output file base name including extension.
	 *
	 * @return output file base name
	 */
	public String getOutputFileName() {
		return fileName;
	}

	/**
	 * Increment the record count for this output segment.
	 *
	 * @return updated record count
	 */
	public int incrementRecordCount() {
		return ++recordCount;
	}

	/**
	 * Get the record count for this output segment.
	 *
	 * @return record count
	 */
	public int getRecordCount() {
		return recordCount;
	}

	/**
	 * Get the item list for this output segment.
	 *
	 * @return item list
	 */
	public List<X9Object> getItemList() {
		return itemList;
	}

	/**
	 * Get the item count for this output segment.
	 *
	 * @return item count
	 */
	public int getItemCount() {
		return itemList.size();
	}

	/**
	 * Get the debit count for this output segment.
	 *
	 * @return debit count
	 */
	public int getDebitCount() {
		return debitCount;
	}

	/**
	 * Get the debit amount for this output segment.
	 *
	 * @return debit amount
	 */
	public BigDecimal getDebitAmount() {
		return debitAmount;
	}

	/**
	 * Get the credit count for this output segment.
	 *
	 * @return credit count
	 */
	public int getCreditCount() {
		return creditCount;
	}

	/**
	 * Get the credit amount for this output segment.
	 *
	 * @return credit amount
	 */
	public BigDecimal getCreditAmount() {
		return creditAmount;
	}

	/**
	 * Add another item to this output segment.
	 *
	 * @param splitKey
	 *            current split key
	 * @param x9o
	 *            current item
	 */
	public void addItem(final String splitKey, final X9Object x9o) {
		/*
		 * Add another split output key that is being routed to this output segment. Only new
		 * (unique) keys will be added, since in most situations there will be many items that map
		 * to the same key. For example, in the simple case where the output is based on the item
		 * routing number, then all items with that routing will share the same split key.
		 */
		if (!splitKeyList.contains(splitKey)) {
			splitKeyList.add(splitKey);
		}

		/*
		 * Add this item to our running item list and update totals.
		 */
		itemList.add(x9o);
		if (x9o.isDebit()) {
			debitCount++;
			debitAmount = debitAmount.add(x9o.getItemAmount());
		} else {
			creditCount++;
			creditAmount = creditAmount.add(x9o.getItemAmount());
		}
	}

	/**
	 * Determine if a given item is attached to this output segment.
	 *
	 * @param x9Item
	 *            current item
	 * @return true or false
	 */
	public boolean isItemAttachedToSegment(final X9Object x9Item) {
		/*
		 * Search the item list by actually walking that attached items. This represents a
		 * performance improvement over a simple contains check, since we can take advantage of the
		 * fact that the items are ordered by their respective record numbers.
		 */
		boolean isItemAttachedToSegment = false;
		for (final X9Object x9o : itemList) {
			/*
			 * Match on record number.
			 */
			if (x9o.x9ObjIdx == x9Item.x9ObjIdx) {
				isItemAttachedToSegment = true;
				break;
			}

			/*
			 * Short cut the search if the record number is high (since items are in sequence).
			 */
			if (x9o.x9ObjIdx > x9Item.x9ObjIdx) {
				isItemAttachedToSegment = false;
				break;
			}
		}

		/*
		 * Return as true when the item is included in the item list for this segment.
		 */
		return isItemAttachedToSegment;
	}

	/**
	 * Log the output summary for this output segment.
	 *
	 * @param segmentNumber
	 *            segment number
	 */
	public void logSegmentSummary(final int segmentNumber) {
		/*
		 * Log all attributes associated with this output segment.
		 */
		LOGGER.info("output file: >> {})",
				(outputFile == null ? "not-written" : outputFile.toString()));
		LOGGER.info(
				"totals: recordCount({}) itemCount({}) debitCount({}) debitAmount({}) "
						+ "creditCount({}) creditAmount({})",
				recordCount, itemList.size(), debitCount, debitAmount, creditCount, creditAmount);

		/*
		 * Log the split output keys for this output segment, which can provide very helpful insight
		 * into how and why each selected item was written to this output file.
		 */
		Collections.sort(splitKeyList);
		for (final String splitKey : splitKeyList) {
			LOGGER.info("splitKey: {}", splitKey);
		}
	}

}
