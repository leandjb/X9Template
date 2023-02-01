package sdkExamples;

import com.x9ware.tools.X9BuildAttr;

/**
 * X9RuntimeLicenseKey is a static class which defines the license key to be assigned to this
 * runtime environment. This approach allows the license key to be embedded directly within the
 * application source code (it is not stored externally). This license key methodology is utilized
 * by the X9-SDK, X9Utilities, and E13B-OCR and supports perpetual, annualized, and evaluation
 * licenses. The license key is issued by X9Ware and is associated directly with a specific
 * organization. The license key is included in the system log for every execution of our products.
 *
 * @author X9Ware LLC. Copyright(c) 2012-2018 X9Ware LLC. All Rights Reserved. This is proprietary
 *         software as developed and licensed by X9Ware LLC under the exclusive legal right of the
 *         copyright holder. All licensees are provided the right to use the software only under
 *         certain conditions, and are specifically restricted from other specific uses including
 *         modification, sharing, reuse, redistribution, or reverse engineering.
 */
public class X9RuntimeLicenseKey {

	/**
	 * X9RuntimeLicenseKey Constructor is private (static class).
	 */
	private X9RuntimeLicenseKey() {
	}

	/*
	 * Client name: Evaluation; Company name: X9Ware LLC; Product name: X9Sdk; License key:
	 * a113-cfdd-be5a-cd12; Entered date: 2022/07/19; Expiration date: 2022/09/09.
	 */
	private static String licenseXmlDocument = "19D8F9BE172AC761E330B3053A5121EF556BCC3D4BF3B0DD55"
			+ "A736F2C9CC84760C8D82284A2EE90D5070DA48E47025A2A56B"
			+ "D18A1ECDB6A58E298ABFD5143388E783DC9A3A9B0B62E7AB46"
			+ "4BF23D129045F542D49DE144C22681A8BBD279CADECD6E6B95"
			+ "3E0CCD55CB2A77CC02E0AF4FEA2BCDA01DFC58AF85F2456A18"
			+ "13097616A50E952019CDDB046EC6C8A15313FE7481C84FD617"
			+ "3040E4EC307FCF2A3834E4767484A1C6711D210F998A677F90"
			+ "5F67C0C84B3ED3ABAB31548E09E934E992A6EBB8A9E43EAE3D"
			+ "39943EAFAB37F7A2125A692EB85F5F03E68549854A0D6B2270"
			+ "08560CEDD7F846F6C583D7FF41A7E55F935416A04FDA882606"
			+ "B1FCDFB8D65517B10A4EEE6563C87320A70FABFE058C56EA30"
			+ "37C1EBE65C680EA8C48655F5666165556DD573AAEB16ED35DC"
			+ "668F0EF9DBE484D3DC46D4EEA50CE9A47C6EC3413289E7AE35"
			+ "328F754C3C3FDA6BCA50C805FA8510F97753453417429B22D9"
			+ "D79A7E1D4A59974DD0662F8FF0E434663BA7CA1267740EDA2E"
			+ "D70185E4D2DEC036F2CF2ED8DDE267FC144F3CE26A2C47AE35"
			+ "61362C280CE29E1CF56E982C27733BB3730CFE94B2151F83E3"
			+ "CA490B5C172C9CB259C31F1616E93F2B73DF304DAD4999131E"
			+ "2B38099AB37F6159CAE74693DB2CE03BF0B322B19B4FA858D6"
			+ "C320A5038A97BEF8B296FEC9E48F6D5FFC3E9579F859E27214"
			+ "7271CD6168009EB65AC2F94CB9532BB8F1203A45180BF8E6CC"
			+ "7E0DDFEDC3397F94C33CABA62D870EBF9C5406CA68D6BC5B8C"
			+ "8393470580C241EB1332DDAE81194040A494723F99A8A8AA35"
			+ "1B56633918BA2AC9C0E4E23ECB1FCB5339763E3ED08FCCA337"
			+ "E0A3667251FEAFB94375315A9466AFF2D4336C918859D20D64"
			+ "EB34D15E7A69F7DBB9614A858F0F20C2958B3B43EA35D7878E"
			+ "8C00E647B5B752B34C5A7712C5CB93A797484046553D23FBC7"
			+ "7BCDE223FFBBD4E48EF899ABC8F73FFDC3F7F7E4C81A75154A"
			+ "CF6A18CF678A7EAC78758218768BBD6ED7F9438F550F116EFD" + "4CD9288BF156FB98D56431";

	/**
	 * Set the runtime license key.
	 */
	public static void setLicenseKey() {
		X9BuildAttr.setSdkProductLicense(licenseXmlDocument);
	}

}
