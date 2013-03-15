package com.shivanshusingh.PluginAnalyser.Analysis;

import java.io.IOException;

public class AnalysisRunner {

	/**
	 * starts the analysis and recording process for a given features / plugins
	 * folder. The naming convention is this: directory/features/ contains all
	 * the feature relared jars and directory/plugins/ contains al the plugin
	 * jars that need to ne analysed / deployed together.
	 * 
	 * @param args
	 */
	public static void main(final String[] args) throws IOException {
		// "/Users/singhsk/Developer/eclipse plugins/com.objfac.ant.preprocess_0.9.1/preprocessor.jar";
		// "/Users/singhsk/Developer/eclipse plugins/com.objfac.ant.preprocess_0.9.1.zip";
		// "/Users/singhsk/Developer/asm-bytecode-analysis-framework/asm-4.1/lib/all/asm-all-4.1.jar";
		// "/Users/singhsk/Developer/voldemort-0.96/dist/voldemort-0.96.jar" ;
		// "/Users/singhsk/Developer/hadoop-1.0.4/hadoop-core-1.0.4.jar";
		// "../exports/plugins/com.shivanshu.eclipse.contributor_1.0.0.201210111512.jar";
		// "/Users/singhsk/Developer/Arcmexer/arcmexer.jar";
		// "/Users/singhsk/Developer/AndroidEclipseBundleforMac/adt-bundle-mac-x86_64.zip";

		String destinationDirectory = "/Users/singhsk/Developer/eclipse_plugins/testmirror_googleandroid1"
		// "/Users/singhsk/Developer/eclipse_plugins/testmirror_ganymede"
		;

		// now doing the extractions from features. - feature.xml i.e.
		String featureFolderPath = destinationDirectory + "/features/";
		FeatureAnalyser.analyseAndRecordAllInformationFromFeautreFolder(featureFolderPath);

		// reading all the files (plugin jars) in the specified plugin folder
		String pluginFolderPath = destinationDirectory + "/plugins/";
		 DependencyTracker.analyseAndRecordAllInformationFromPluginFolder(pluginFolderPath);

	}

}
