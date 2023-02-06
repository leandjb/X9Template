package run;

import sdkExamples.X9RuntimeLicenseKey;


public class main {

    public static void main(String[] args) {
        /*

         * Set the runtime license key (this class is part of sdk-examples). X9RuntimeLicenseKey

         * must be updated with the license key text provided for your evaluation.

         */

        X9RuntimeLicenseKey.setLicenseKey();

        /*

         * Initialize the environment.

         */

        X9SdkRoot.logStartupEnvironment(X9REFORMATX9);

        X9SdkRoot.loadXmlConfigurationFiles();

        if (!sdkBase.bindConfiguration(X9.X9_37_CONFIG)) {

            throw X9Exception.abort("bind unsuccessful");

        }

    }
}
