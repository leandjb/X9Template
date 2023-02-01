package sdkUtilities;

import java.io.File;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

import com.x9ware.beans.X9UtilSplitBean;
import com.x9ware.jaxb.X9Jaxb;
import com.x9ware.jaxb.X9JaxbXmlFileInterface;

/**
 * X9UtilSplitXml reads a split xml definition as persisted to xml.
 *
 * @author X9Ware LLC. Copyright(c) 2012-2018 X9Ware LLC. All Rights Reserved. This is proprietary
 *         software as developed and licensed by X9Ware LLC under the exclusive legal right of the
 *         copyright holder. All licensees are provided the right to use the software only under
 *         certain conditions, and are explicitly restricted from other specific uses including
 *         modification, sharing, reuse, redistribution, or reverse engineering.
 */
public final class X9UtilSplitXml implements X9JaxbXmlFileInterface<X9UtilSplitBean> {

	/*
	 * Private.
	 */
	private X9UtilSplitBean splitBean = new X9UtilSplitBean();

	/**
	 * X9UtilSplitXml Constructor.
	 */
	public X9UtilSplitXml() {
	}

	/**
	 * Get the split bean outputs instance.
	 *
	 * @return split bean outputs instance
	 */
	public X9UtilSplitBean.Outputs getOutputs() {
		return splitBean.outputs;
	}

	/**
	 * Get the split output list.
	 *
	 * @return split output list
	 */
	public List<X9UtilSplitBean.Output> getOutputsList() {
		return splitBean.outputs.outputList;
	}

	/**
	 * Allocate and return the default output entry.
	 *
	 * @return default output entry
	 */
	public X9UtilSplitBean.Output getDefaultOutput() {
		/*
		 * The default entry will always exist and will always own all of the free (non-assigned)
		 * items. The only question is whether it will be actually written.
		 */
		X9UtilSplitBean.Output defaultEntry = null;
		final String defaultFileName = splitBean.outputs.defaultFileName;
		defaultEntry = new X9UtilSplitBean.Output();
		defaultEntry.fileName = defaultFileName;

		/*
		 * Set writeEnabled for the default segment to true or false, which will ultimately
		 * determine if these free items will be written to a catch-all output segment.
		 */
		defaultEntry.writeEnabled = StringUtils.isNotBlank(defaultFileName);
		return defaultEntry;
	}

	@Override
	public X9UtilSplitBean readExternalXmlFile(final File xmlFile) {
		return splitBean = X9Jaxb.getXmlFromFile(new X9UtilSplitBean(), xmlFile);
	}

	@Override
	public void writeExternalXmlFile(final File xmlFile) {
		splitBean.updateReleaseAndBuild();
		X9Jaxb.putXml(splitBean, xmlFile);
	}

}
