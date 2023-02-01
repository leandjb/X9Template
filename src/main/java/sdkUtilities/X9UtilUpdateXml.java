package sdkUtilities;

import java.io.File;
import java.util.List;

import com.x9ware.beans.X9UtilUpdateBean;
import com.x9ware.jaxb.X9Jaxb;
import com.x9ware.jaxb.X9JaxbXmlFileInterface;

/**
 * X9UtilUpdateXml reads a match-replace xml definition as persisted to xml.
 *
 * @author X9Ware LLC. Copyright(c) 2012-2018 X9Ware LLC. All Rights Reserved. This is proprietary
 *         software as developed and licensed by X9Ware LLC under the exclusive legal right of the
 *         copyright holder. All licensees are provided the right to use the software only under
 *         certain conditions, and are explicitly restricted from other specific uses including
 *         modification, sharing, reuse, redistribution, or reverse engineering.
 */
public final class X9UtilUpdateXml implements X9JaxbXmlFileInterface<X9UtilUpdateBean> {

	/*
	 * Private.
	 */
	private X9UtilUpdateBean updateBean = new X9UtilUpdateBean();

	/**
	 * X9UtilUpdateXml Constructor.
	 */
	public X9UtilUpdateXml() {
	}

	/**
	 * Get the constant list.
	 *
	 * @return constant list
	 */
	public List<X9UtilUpdateBean.Constant> getConstantList() {
		return updateBean.constants.constantList;
	}

	/**
	 * Get the swap list.
	 *
	 * @return swap list
	 */
	public List<X9UtilUpdateBean.Swap> getSwapList() {
		return updateBean.swaps.swapList;
	}

	@Override
	public X9UtilUpdateBean readExternalXmlFile(final File xmlFile) {
		return updateBean = X9Jaxb.getXmlFromFile(new X9UtilUpdateBean(), xmlFile);
	}

	@Override
	public void writeExternalXmlFile(final File xmlFile) {
		updateBean.updateReleaseAndBuild();
		X9Jaxb.putXml(updateBean, xmlFile);
	}

}
