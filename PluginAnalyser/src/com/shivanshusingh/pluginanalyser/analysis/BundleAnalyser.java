package com.shivanshusingh.pluginanalyser.analysis;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import org.objectweb.asm.ClassReader;

import com.shivanshusingh.pluginanalyser.utils.Util;
import com.shivanshusingh.pluginanalyser.utils.Version;
import com.shivanshusingh.pluginanalyser.utils.logging.Log;
import com.shivanshusingh.pluginanalyser.utils.parsing.Constants;
import com.shivanshusingh.pluginanalyser.utils.parsing.FuncSig;
import com.shivanshusingh.pluginanalyser.utils.parsing.ParsingUtil;
//import org.apache.ivy.osgi.core.ManifestParser;

/**
 * Analyzes the plugin / bundles.
 * 
 * @author Shivanshu Singh
 * 
 */
public class BundleAnalyser extends ManifestParser {

	
	// Map for: Plugin Name (symbolic name) =>
	// pluginId(symbolicname[version.qualifier]) => plugin extract file names
	// set..
	public static Map<String, Map<String, Set<String>>> pluginMap = new HashMap<String, Map<String, Set<String>>>();
	// Map for: ExportedPackage name  =>   ExportedPackageId(name[version]) => pluginId set..
	public static Map<String, Map<String, Set<String>>> exportedPackagesMap = new HashMap<String, Map<String, Set<String>>>();
	private static long UNKNOWN_SYMBOLIC_NAME_Counter = 0;

	/**
	 * @param pluginFolderPath
	 * @param outputLocation
	 * @param pathToJavaClasses
	 *            the path to the location containing the standard Java platform
	 *            related classes / jars.
	 * @param eraseOld
	 * @throws IOException
	 */
	public static void analyseAndRecordAllInformationFromBasePluginFolder(String pluginFolderPath, String outputLocation,
			String pathToJavaClasses, boolean eraseOld) throws IOException {
		if (!Util.checkDirectory(new File(pluginFolderPath), true, true, true, false)) {
			Log.errln("xxxx Error Accessing Plugins source base Directory for Plugin Analysis : " + pluginFolderPath
					+ "\n Cannot continue with the analysis.");
			return;
		}

		if (!Util.checkAndCreateDirectory(outputLocation)) {
			Log.errln("xxxx Error Accessing/Creating Output Directory for Plugin Analysis Output at: " + outputLocation
					+ "\n Cannot continue with the analysis.");
			return;
		}

		if (eraseOld)
			Util.clearFolder(new File(outputLocation));

		if (Util.checkDirectory(new File(pathToJavaClasses), true, true, true, false)) {
			// the whole process of creating a plugin extract for java platform
			// classes.
			buildJavaSDKClassesExtract(pathToJavaClasses, outputLocation);

		} else {
			Log.errln("xxxx Error Accessing Java Classes Location in Plugin Analysis : " + pluginFolderPath
					+ "\n No Java classes will be included.");

		}

		// reading all the files (plugin jars) in the specified plugin folder
		long l1 = System.currentTimeMillis();

		Log.outln("==== Analysing Source:" + pluginFolderPath);
		File folder = new File(pluginFolderPath);
		/*if (null == folder) {
			Log.outln("==== nothing here.");
			return;
		}*/
		File[] listOfFiles = folder.listFiles();
		long pluginAnalysedCounter = 0;
		for (int i = 0; i < listOfFiles.length; i++) {
			if (listOfFiles[i].isFile()) {
				String pluginJarName = listOfFiles[i].getName();
				if (pluginJarName.toLowerCase().endsWith(Constants.EXTENSION_JAR)) {
					// this means that this is a plugin jar (it is assumed that
					// this would be a plugin jar if it is at this location)
					pluginAnalysedCounter++;
					analyseAndRecordAllInformationFromPluginJar(pluginFolderPath, pluginJarName, outputLocation);

				}

			} else if (listOfFiles[i].isDirectory()) {

				// some plugins may be unpacked and so exist as directories
				// instead of jars.
				pluginAnalysedCounter++;
				analyseAndRecordAllInformationFromPluginDir(pluginFolderPath, listOfFiles[i].getName(), outputLocation);
				// Log.outln("Directory " + listOfFiles[i].getName());
			}
		}
		//writing the plugin map to disk.
		Util.writeObjectToDisk(pluginMap,Constants.EXTRACT_FILE_PREFIX_PLUGINMAP
				+Constants.EXTRACT_FILE_NAME_PLUGINMAP + Constants.EXTRACT_FILE_EXTENSION_PLUGINMAP, outputLocation);
		//writing the exportedPackages map to disk.
		Util.writeObjectToDisk(exportedPackagesMap,Constants.EXTRACT_FILE_PREFIX_EXPPACKAGEMAP
				+Constants.EXTRACT_FILE_NAME_EXPPACKAGEMAP + Constants.EXTRACT_FILE_EXTENSION_EXPPACKAGEMAP, outputLocation);
				
		long l2 = System.currentTimeMillis();
		Log.outln(pluginAnalysedCounter + " plugin have been analyzed");
		Log.errln(pluginAnalysedCounter + " plugin have been analyzed");
		Log.outln("for source:" + pluginFolderPath + " time: " + Util.getFormattedTime(l2 - l1));
		Log.errln("for source:" + pluginFolderPath + " time: " + Util.getFormattedTime(l2 - l1));
	}

	/**
	 * @param pathPrefix
	 * @param pluginDirName
	 * @param outputLocation
	 * @throws IOException
	 */
	public static void analyseAndRecordAllInformationFromPluginDir(String pathPrefix, String pluginDirName,
			String outputLocation) throws IOException {
		long l1 = System.currentTimeMillis();

		try {
			DependencyVisitor v = new DependencyVisitor();
			BundleInfo bundleInformation = new BundleInfo();
			String dirNameWithPathFull = pathPrefix + pluginDirName;

			File folder = new File(dirNameWithPathFull);
			if (null == folder || !folder.isDirectory()) {
				Log.outln("==== ==== nothing here.");
				return;
			}

			bundleInformation = getBundleManifestAndMetaInformationFromDir(folder);
			Log.outln("now starting the plugin_from_dir dependency  extraction for  : " + folder.getPath());
			extractDependenciesAndExportsFromDir(v, bundleInformation, folder);

			writeData(v, bundleInformation, folder.getName(), outputLocation);
			long l2 = System.currentTimeMillis();

			Log.errln("==== analysed:  \n " + dirNameWithPathFull + "\n time: " + Util.getFormattedTime(l2 - l1));
		} catch (Exception e) {
			Log.errln("xxxx ERROR WHILE ANALYSING PLUGIN Folder : " + pathPrefix + pluginDirName);
			e.printStackTrace();
		}

	}

	/**
	 * @param pathPrefix
	 * @param pluginJarName
	 * @param outputLocation
	 * @throws IOException
	 */
	public static void analyseAndRecordAllInformationFromPluginJar(String pathPrefix, String pluginJarName,
			String outputLocation) throws IOException {
		long l1 = System.currentTimeMillis();

		try {
			DependencyVisitor v = new DependencyVisitor();
			BundleInfo bundleInformation = new BundleInfo();
			// ////////archive/////////////////////////////////
			String jarFileNameWithPathFull = pathPrefix + pluginJarName;

			// ZipFile f = new ZipFile(jarFileNameWithPathFull);
			JarFile f = new JarFile(jarFileNameWithPathFull);

			// Actual part of getting the dependecies and offerrings from the
			// current jar file.////

			bundleInformation = getBundleManifestAndMetaInformationFromJar(f);
			Log.outln("== now starting the  plugin dependency  extraction");
			extractDependenciesAndExportsFromJar(v, bundleInformation, f);

			// ////////////////////////////////////////

			// ///////// //sing le class reading try //////////////

			/*
			 * jarFileNameWithPathFull=
			 * "./bin/com/shivanshusingh/PluginAnalyser_OLD/DUMMYFORTESTClassSignatureExtractor.class"
			 * ; File f = new File(jarFileNameWithPathFull); InputStream in= new
			 * FileInputStream(f); new ClassReader(in).accept(v, 0);
			 */
			// /////////////////////////// ////////////////////////

			writeData(v, bundleInformation, pluginJarName, outputLocation);
			long l2 = System.currentTimeMillis();

			Log.errln("==== analysed:  \n " + jarFileNameWithPathFull + "\n time: " + Util.getFormattedTime(l2 - l1));

		} catch (Exception e) {
			Log.errln("xxxx ERROR WHILE ANALYSING PLUGIN Jar : " + pathPrefix + pluginJarName);
			Log.errln(Util.getStackTrace(e));
			e.printStackTrace();
		}
	}

	/**
	 * the whole process of creating a plugin extract for java platform classes.  This is so that the java sdk classes can be   marked for satisfaction during Dependency Analysis.
	 * @param pathToJavaClasses
	 * @param outputLocation
	 */
	private static void buildJavaSDKClassesExtract(String pathToJavaClasses, String outputLocation) {
		File javaSDKClassesFolder = new File(pathToJavaClasses);
		long l1 = System.currentTimeMillis();

		try {
			DependencyVisitor v = new DependencyVisitor();

			BundleInfo dummyBundleInfo = new BundleInfo();
			extractDependenciesAndExportsFromJavaSDKDir(v, javaSDKClassesFolder);

			writeJavaSDKClassesData(v, dummyBundleInfo, Constants.EXTRACT_FILE_NAME_JAVA_CLASSES_SDK, outputLocation);
			long l2 = System.currentTimeMillis();

			Log.errln("==== analysed:  \n " + javaSDKClassesFolder.getAbsolutePath() + "\n time: "
					+ Util.getFormattedTime(l2 - l1));
		} catch (Exception e) {
			Log.errln("xxxx ERROR WHILE ANALYSING JavaSDKClassDir Folder : " + pathToJavaClasses);
			e.printStackTrace();
		}
	}

	/**
	 * @param visitor
	 *            {@link DependencyVisitor}
	 * @param bundleInformation
	 * @param folder
	 * @throws IOException
	 */
	private static void extractDependenciesAndExportsFromDir(DependencyVisitor visitor, BundleInfo bundleInformation,
			File folder) throws IOException {

		Log.outln("==== Starting the Plugin_from_Dir : " + folder.getCanonicalPath() + " analysis ====");

		// getting a recursive list of all files contained in this plugin dir.
		Set<String> dirFileList = Util.listFilesForFolder(folder);

		Iterator<String> en = dirFileList.iterator();

		int classesAnalyzedCounter = 0;

		while (en.hasNext()) {
			String dirEntryPath=en.next();
			File e = new File(dirEntryPath);

			String name = e.getName();
			
			// Log.outln(name);

			if (name.toLowerCase().endsWith(".class")) {

				classesAnalyzedCounter++;
				new ClassReader(new FileInputStream(e)).accept(visitor, 0);

			} else if (name.toLowerCase().endsWith(Constants.EXTENSION_JAR)) {
				// nested jar.

				Log.outln("====> " + name + " found");
				Log.outln(bundleInformation.getClasspathEntries().toString());

				// now check if this nested jar file is one of the Bundle
				// classpath dependencies (lib jars)
				if (null != bundleInformation) {
					for (Object libJarNameEndingObj : bundleInformation.getClasspathEntries()) {
						String libJarNameEnding = (String) libJarNameEndingObj;
						// Log.outln("CHECKING:"+name+": ends with:"+libJarNameEnding);
						if (dirEntryPath.toLowerCase().endsWith(libJarNameEnding.toLowerCase())) {
							// good news, the jar is present in the bundle
							// manifest jar file entries' list.
							Log.outln("====> now analysing internal lib jar:" + name);

							String TEMPFileName = (Util.getTEMP_DIR_PATH() + "/pa-sks-plugin-tmp-").replace("//", "/")
									+ Math.random() + (
									// jarfileinstance.getName()
									// // + "_" +
									name).replaceAll("/", "_").replace(" ", "_");

							BufferedReader bufferedTempReader = new BufferedReader(new FileReader(e));

							BufferedWriter bufferedTempWriter = new BufferedWriter(new FileWriter(TEMPFileName));
							int inread;
							while ((inread = bufferedTempReader.read()) != -1) {
								bufferedTempWriter.write(inread);
							}
							bufferedTempWriter.close();
							bufferedTempReader.close();
							Log.outln("==== created : " + TEMPFileName + "====");
							extractDependenciesAndExportsFromJar(visitor, bundleInformation, new JarFile(TEMPFileName));
							Log.outln("==== delete = " + new File(TEMPFileName).delete() + " : " + TEMPFileName + "====");
							Log.outln("==== ==== ==== ==== ");

							break;// get out when found and analysed.
						}
					}
				}
			}
		}
		Log.outln(classesAnalyzedCounter + " Class Files read.");
		Log.outln("==== Ending the Plungin_from_Dir :  " + folder.getCanonicalPath() + " analysis =====");
	}

	/**
	 * @param visitor
	 *            {@link DependencyVisitor}
	 * @param bundleInformation
	 *            {@link BundleInfo}
	 * @param jarfileinstance
	 * @throws IOException
	 */
	private static void extractDependenciesAndExportsFromJar(DependencyVisitor visitor, BundleInfo bundleInformation,
			JarFile jarfileinstance) throws IOException {

		// // for zip files reading ///////////////
		// ZipFile f = new ZipFile(jarFileNameWithPathFull.trim());
		// Enumeration<? extends ZipEntry> en = f.entries();
		// ///////////////////////////////////////////

		Log.outln("==== Starting the Archive : " + jarfileinstance.getName() + " analysis ====");

		Enumeration<? extends JarEntry> en = jarfileinstance.entries();
		int classesAnalyzedCounter = 0;

		while (en.hasMoreElements()) {
			JarEntry e = en.nextElement();

			String name = e.getName();
			// Log.outln(name);

			if (name.toLowerCase().endsWith(".class")) {

				classesAnalyzedCounter++;
				new ClassReader(jarfileinstance.getInputStream(e)).accept(visitor, 0);

			} else if (name.toLowerCase().endsWith(Constants.EXTENSION_JAR)) {
				// nested jar.

				Log.outln("====> " + name + " found");
				// Log.outln(bundleInformation.getClasspathEntries().toString());

				// now check if this nested jar file is one of the Bundle
				// classpath dependencies (lib jars)
				if (null != bundleInformation) {
					for (Object libJarNameEndingObj : bundleInformation.getClasspathEntries()) {
						String libJarNameEnding = (String) libJarNameEndingObj;
						// Log.outln("CHECKING:"+name+": ends with:"+libJarNameEnding);
						if (name.toLowerCase().endsWith(libJarNameEnding.toLowerCase())) {
							// good news, the jar is present in the bundle
							// manifest jar file entries' list.
							Log.outln("====> now analysing internal lib jar:" + name);

							String TEMPFileName = (Util.getTEMP_DIR_PATH() + "/pa-sks-plugin-tmp-").replace("//", "/")
									+ Math.random() + ( // jarfileinstance.getName()
														// // + "_" +
									name).replaceAll("/", "_").replace(" ", "_");

							BufferedReader bufferedTempReader = new BufferedReader(new InputStreamReader(
									jarfileinstance.getInputStream(e)));

							BufferedWriter bufferedTempWriter = new BufferedWriter(new FileWriter(TEMPFileName));
							int inread;
							while ((inread = bufferedTempReader.read()) != -1) {
								bufferedTempWriter.write(inread);
							}
							bufferedTempWriter.close();
							bufferedTempReader.close();
							Log.outln("==== created : " + TEMPFileName + "==== ");
							extractDependenciesAndExportsFromJar(visitor, bundleInformation, new JarFile(TEMPFileName));
							Log.outln("==== delete = " + new File(TEMPFileName).delete() + " : " + TEMPFileName + "====");
							Log.outln("==== ==== ==== ==== ");

							break;// get out when found and analysed.
						}
					}
				}
			}
		}
		Log.outln("==== ==== " + classesAnalyzedCounter + " Class Files read.");
		Log.outln("==== Ending the Archive : " + jarfileinstance.getName() + " analysis ====");
		jarfileinstance.close();
	}

	private static void extractDependenciesAndExportsFromJavaSDKJar(DependencyVisitor visitor, JarFile jarFile)
			throws IOException {
		Log.outln("==== Starting the JavaSDKClassesJar: " + jarFile.getName() + " analysis ====");

		Enumeration<? extends JarEntry> en = jarFile.entries();
		int classesAnalyzedCounter = 0;

		while (en.hasMoreElements()) {
			JarEntry e = en.nextElement();

			String name = e.getName();
			// Log.outln(name);

			if (name.toLowerCase().endsWith(".class")) {

				classesAnalyzedCounter++;
				new ClassReader(jarFile.getInputStream(e)).accept(visitor, 0);

			} else if (name.toLowerCase().endsWith(Constants.EXTENSION_JAR)) {
				// nested jar.

				String TEMPFileName = (Util.getTEMP_DIR_PATH() + "/pa-sks-plugin-tmp-").replace("//", "/") + Math.random()
						+ ( // jarfileinstance.getName()
							// // + "_" +
						name).replaceAll("/", "_").replace(" ", "_");

				BufferedReader bufferedTempReader = new BufferedReader(new InputStreamReader(jarFile.getInputStream(e)));

				BufferedWriter bufferedTempWriter = new BufferedWriter(new FileWriter(TEMPFileName));
				int inread;
				while ((inread = bufferedTempReader.read()) != -1) {
					bufferedTempWriter.write(inread);
				}
				bufferedTempWriter.close();
				bufferedTempReader.close();
				Log.outln("==== created : " + TEMPFileName + "==== ");
				extractDependenciesAndExportsFromJavaSDKJar(visitor, new JarFile(TEMPFileName));
				Log.outln("==== delete = " + new File(TEMPFileName).delete() + " : " + TEMPFileName + "====");
				Log.outln("==== ==== ==== ==== ");

				break;// get out when found and analysed.
			}
		}

		Log.outln("==== ==== " + classesAnalyzedCounter + " Class Files read.");
		Log.outln("==== Ending the JavaSDKClassesJar : " + jarFile.getName() + " analysis ====");
		jarFile.close();
	}

	private static void extractDependenciesAndExportsFromJavaSDKDir(DependencyVisitor visitor, File folder)
			throws IOException {

		Log.outln("==== Starting the JavaSDKClasses Dir : " + folder.getCanonicalPath() + " analysis ====");

		// getting a recursive list of all files contained in this plugin dir.
		Set<String> dirFileList = Util.listFilesForFolder(folder);

		Iterator<String> en = dirFileList.iterator();

		int classesAnalyzedCounter = 0;

		while (en.hasNext()) {
			String entry = en.next();
			File e = new File(entry);

			String name = e.getName();
			// Log.outln(name);

			if (name.toLowerCase().endsWith(".class")) {

				classesAnalyzedCounter++;
				new ClassReader(new FileInputStream(e)).accept(visitor, 0);

			} else if (name.toLowerCase().endsWith(Constants.EXTENSION_JAR)) {
				// nested jar.

				extractDependenciesAndExportsFromJavaSDKJar(visitor, new JarFile(entry));

			}
		}

		Log.outln(classesAnalyzedCounter + " Class Files read.");
		Log.outln("==== Ending the JavaSDKClasses :  " + folder.getCanonicalPath() + " analysis =====");
	}

	/**
	 * @param manifestStream
	 * @return
	 */
	private static BundleInfo extractManifestInformation(InputStream manifestStream) {
		BundleInfo bundleInformation = new BundleInfo();

		try {

			bundleInformation = com.shivanshusingh.pluginanalyser.analysis.ManifestParser.parseManifest(manifestStream);// new
																														// BundleInfo(manifestStream);
			System.err.println("==== " + bundleInformation.toString());
			// extractBundleInfo(bundleInformation);

		} catch (ParseException e) {
			Log.outln("xxxx  NO Manifest found here or cannot parse that  or maybe theres nothing to parse inthis.    \n xxxx  Marking bundleInfo as to be IGNORED.");
			bundleInformation.ignoreBundle = true;

			Log.errln(Util.getStackTrace(e));
			System.err.println("xxxx " + bundleInformation.toString());
		} catch (IOException e) {
			e.printStackTrace();
		}
		return bundleInformation;
	}

	/**
	 * @deprecated
	 * @param manifest
	 * @return
	 */
	@SuppressWarnings("unused")
	private static BundleInfo extractManifestInformation(Manifest manifest) {
		BundleInfo bundleinfo = new BundleInfo();
		try {
			bundleinfo = com.shivanshusingh.pluginanalyser.analysis.ManifestParser.parseManifest(manifest);

		} catch (ParseException e) {
			Log.outln("xxxx  NO Manifest found here or cannot parse that  or maybe theres nothing to parse inthis.    \n xxxx  Marking bundleInfo as to be IGNORED.");
			bundleinfo.ignoreBundle = true;

			// e.printStackTrace();
		}
		return bundleinfo;
	}

	/**
	 * @param folder
	 * @return
	 */
	private static BundleInfo getBundleManifestAndMetaInformationFromDir(File folder) {
		// recursively constructing a set of paths of all files in this plugin
		// folder.
		Set<String> dirFileList = Util.listFilesForFolder(folder);
		BundleInfo bundleInformation = new BundleInfo();
		// getting plugin meta-inf/manifest.mf manifest information

		Iterator<String> en = dirFileList.iterator();
		boolean flag_manifestFound = false, flag_pluginxmlFound = false;
		StringBuffer pluginxmlText = new StringBuffer();
		while (en.hasNext() && (!flag_manifestFound || !flag_pluginxmlFound)) {

			File e = new File(en.next().toString());
			String name = e.getName();
			// Log.outln("==== handling :" + name);

			if (!flag_manifestFound && e.getAbsolutePath().toLowerCase().endsWith("meta-inf/manifest.mf")) {
				// getting the manifest.
				flag_manifestFound = true;
				try {

					Log.outln("== manifest try: file name = " + folder.getPath() + ">" + name);

					bundleInformation = extractManifestInformation(new FileInputStream(e));

				} catch (Exception exception) {
				}
			} else if (!flag_pluginxmlFound && name.toLowerCase().endsWith("plugin.xml")) {
				flag_pluginxmlFound = true;
				try {
					Log.outln("== plugin.xml capture: file name = " + folder.getPath() + ">" + name);
					BufferedReader br;

					br = new BufferedReader(new FileReader(e));

					String ss;
					while ((ss = br.readLine()) != null) {
						pluginxmlText.append(ss);
					}
					br.close();
					// Log.outln(pluginxmlText);
				} catch (Exception e1) {
					e1.printStackTrace();
				}
			}
		}
		// if(null!=bundleInformation)
		bundleInformation.setPluginXml(pluginxmlText.toString());
		return bundleInformation;

	}

	/**
	 * @param jarfileinstance
	 * @return
	 * @throws IOException
	 */
	private static BundleInfo getBundleManifestAndMetaInformationFromJar(JarFile jarfileinstance) throws IOException {

		BundleInfo bundleInformation = new BundleInfo();
		// getting plugin meta-inf/manifest.mf manifest information
		Enumeration<? extends JarEntry> en = jarfileinstance.entries();

		boolean flag_manifestFound = false, flag_pluginxmlFound = false;
		StringBuffer pluginxmlText = new StringBuffer();
		while (en.hasMoreElements() && (!flag_manifestFound || !flag_pluginxmlFound)) {

			JarEntry e = en.nextElement();
			String name = e.getName();

			if (!flag_manifestFound && name.toLowerCase().endsWith("meta-inf/manifest.mf")) {
				// getting the manifest.
				flag_manifestFound = true;
				try {

					Log.outln("== manifest try: file name = " + jarfileinstance.getName() + ">" + name);

					
					bundleInformation = extractManifestInformation(jarfileinstance.getInputStream(e));
					// extractManifestInformation(jarfileinstance.getManifest());

				} catch (Exception exception) {
				}
			} else if (!flag_pluginxmlFound && name.toLowerCase().endsWith("plugin.xml")) {
				flag_pluginxmlFound = true;
				Log.outln("== plugin.xml capture. : file name = " + jarfileinstance.getName() + ">" + name);
				BufferedReader br = new BufferedReader(new InputStreamReader(jarfileinstance.getInputStream(e)));
				String ss;
				while ((ss = br.readLine()) != null) {
					pluginxmlText.append(ss);
				}
				br.close();
				// Log.outln(pluginxmlText);
			}
		}
		if (null != bundleInformation)
			bundleInformation.setPluginXml(pluginxmlText.toString());
		return bundleInformation;

	}

	private static PluginPruningObject getBundlePruningInfo(Set<String> allMethodsSet, Set<String> invokationsSet,
			Set<String> allInheritanceHierarchies, Set<String> allInterfaceImplLists) {

		PluginPruningObject pruningObject = new PluginPruningObject();

		for (String invokation : invokationsSet) {

			invokation=invokation.trim();
			FuncSig thisInvokationElements = new FuncSig(invokation);
			String thisInvokationClassname = thisInvokationElements.getClassName();

			// check if the function is one of provided by java.lang.Object -
			// marking those as satisfied by default and marking them to be
			// pruned.
			
			String javaInvokation=invokation;
			FuncSig javaInvokationElements = new FuncSig(javaInvokation);
			javaInvokationElements.setClassName(Constants.JAVA_LANG_OBJECT);
			javaInvokation=javaInvokationElements.getSignature().trim();
			if(Constants.JAVA_LANG_OBJECT_FUNCTIONS.contains(javaInvokation))
			{
				pruningObject.invokationsToBeRemoved.add(invokation);
			continue;
			}
				
			// see if the current invokation can be satisifed through any of its
			// inheritence hierarchy classes. If yes, add the invokation to be
			// removed else create a proxy for this invokation that may be
			// checkd for later on in DependencyFinder.

			for (String inheritanceHierarchy : allInheritanceHierarchies) {
				if (inheritanceHierarchy.startsWith(thisInvokationClassname.trim())) {
					// probably found the entry that we were looking for.

					String superclassNames[] = inheritanceHierarchy
							.split(Constants.DELIM_PLUGIN_ELEMENT_SUPERCLASS_INTERFACE);
					if (null != superclassNames && 1 <= superclassNames.length
							&& 0 == thisInvokationClassname.trim().compareTo(superclassNames[0].trim())) {
						// yes surely found it. and there is going to be only
						// one inheritance hierarchy that we would be interested
						// in.

						boolean flag_invokationIndirectlySatisfied = false;
						for (int x = 1; x < superclassNames.length; x++) {
							String superClassName = superclassNames[x].trim();
							if (!"".equalsIgnoreCase(superClassName)) {
								// constructing the new method signature.
								FuncSig newInvokationElements =  new FuncSig( thisInvokationElements);
								newInvokationElements.setClassName(superClassName);
								String newInvokation = newInvokationElements.getSignature();

								if (allMethodsSet.contains(newInvokation)) {
									pruningObject.invokationsToBeRemoved.add(invokation);

									flag_invokationIndirectlySatisfied = true;
									break;
								}

							}
						}

						// if invokation not satisfied through the internal
						// inheritance hierarchy, add the last element in the
						// hierarchy as a potential proxy for satisfaction
						// through other plugins.

						if (!flag_invokationIndirectlySatisfied) {
							String superClassName = superclassNames[superclassNames.length - 1].trim();
							FuncSig newInvokationElements = new FuncSig(invokation);
							newInvokationElements.setClassName(superClassName);
							String newInvokation = newInvokationElements.getSignature();

							if (1 <= superClassName.length()) {
								Set<String> proxySet = new HashSet<String>();
								if (pruningObject.invokationProxies.containsKey(invokation))
									proxySet = pruningObject.invokationProxies.get(invokation);
								proxySet.add(newInvokation);
								pruningObject.invokationProxies.put(invokation, proxySet);
							}
						}
						break;
					}
				}
			}
			for (String entry : allInterfaceImplLists) {
				// System.out.println(thisInvokationClass+" > (interface) at the starting of?: "
				// + entry);

				if (entry.startsWith(thisInvokationClassname.trim())) {
					// probably found the entry that we were looking for.

					// System.out.println(thisInvokationClass+" > (interface) at the starting of?: "
					// + entry);

					String tokens[] = entry.split(Constants.DELIM_PLUGIN_ELEMENT_SUPERCLASS_INTERFACE);
					if (0 == thisInvokationClassname.trim().compareTo(tokens[0].trim())) {
						// yes surely found it. and there is going to be only
						// one interfaceImplList that we would be interested
						// in.
						boolean flag_invokationIndirectlySatisfied = false;

						Set<String> thisInvokationClassAllInterfaceImplementSet = new HashSet<String>();
						for (int x = 1; x < tokens.length; x++) {
							// getting the list of interfaces implemented.
							String token = tokens[x].trim();
							if (!"".equalsIgnoreCase(token)) {
								String[] interfaces = token.split(";");
								for (int y = 0; y < interfaces.length; y++) {
									String interfaceImplemented = interfaces[y].trim();
									if (!"".equalsIgnoreCase(interfaceImplemented)) {
										// constructing the new method
										// signature.
										FuncSig newInvokationElements =    new FuncSig  (invokation);
										newInvokationElements.setClassName(interfaceImplemented);
										String newInvokation = newInvokationElements.getSignature();

										// checking for being satisfied.
										if (allMethodsSet.contains(newInvokation)) {
											pruningObject.invokationsToBeRemoved.add(invokation);

											flag_invokationIndirectlySatisfied = true;
											break;
										} else {
											// collecting all interface class
											// proxies that may need to be added
											// to the proxies list
											thisInvokationClassAllInterfaceImplementSet.add(newInvokation);
										}
									}
								}
							}
							if (flag_invokationIndirectlySatisfied)
								break;
						}

						// if invokation not satisfied through the internal
						// interface implements, them as a potential proxies for
						// satisfaction through other plugins.

						if (!flag_invokationIndirectlySatisfied) {

							Set<String> proxySet = new HashSet<String>();
							if (pruningObject.invokationProxies.containsKey(invokation))
								proxySet = pruningObject.invokationProxies.get(invokation);
							for (String newInvokation : thisInvokationClassAllInterfaceImplementSet) {
								proxySet.add(newInvokation);
								Log.outln("==========  adding iproxy:" + invokation + " => " + newInvokation);
							}
							pruningObject.invokationProxies.put(invokation, proxySet);

						}
					}
				}
				break;
			}
		}
		return pruningObject;
	}

	private static StringBuffer getInheritanceHeirarchy(String className, String delim,
			Map<String, TypeDependency> allTypeDependencies_SuperClassAndInterfaces) {
		StringBuffer toWrite = new StringBuffer(className);

		if (allTypeDependencies_SuperClassAndInterfaces.containsKey(className)) {
			TypeDependency typeDep = (TypeDependency) allTypeDependencies_SuperClassAndInterfaces.get(className);

			if (null != typeDep.superClass && !"".equalsIgnoreCase(typeDep.superClass)) {
				toWrite.append(delim
						+ getInheritanceHeirarchy(typeDep.superClass, delim, allTypeDependencies_SuperClassAndInterfaces));
			}
		}
		// System.out.println(toWrite);
		return toWrite;
	}

	/**
	 * @param v
	 * @param bundleinfo
	 * @param pluginFileName
	 * @param outputLocation
	 * @throws IOException
	 */
	private static void writeData(DependencyVisitor v, BundleInfo bundleinfo, String pluginFileName, String outputLocation)
			throws IOException {

		pluginFileName = pluginFileName.trim();
		if (pluginFileName.endsWith(Constants.EXTENSION_JAR))
			pluginFileName = pluginFileName.substring(0, pluginFileName.length() - Constants.EXTENSION_JAR.length());

		outputLocation = (outputLocation + "/").trim().replaceAll("//", "/").replaceAll("\\\\", "\\");
		pluginFileName=pluginFileName.replace('/', '_').replace('\\', '_'); 
		FileWriter fwriter = new FileWriter(outputLocation + Constants.EXTRACT_FILE_PREFIX_PLUGIN
				+pluginFileName + Constants.EXTRACT_FILE_EXTENSION_PLUGIN);
		BufferedWriter writer = new BufferedWriter(fwriter);

		// ////////////////////////////////////////////////
		Set<String> allDetectedTypes = v.getAllDetectedTypes();
		Set<String> allExternalDetectedTypes = v.getAllExternalDetectedTypes();
		Set<String> allExternalNonJavaDetectedTypes = v.getAllExternalNonJavaDetectedTypes();

		Set<String> allMyMethods = v.getAllMyMethods();
		Set<String> allMyDeprecatedMethods = v.getAllMyDeprecatedMethods();
		Set<String> allMyDeprecatedPublicMethods = v.getAllMyDeprecatedPublicMethods();
		Set<String> allMyPublicMethods = v.getAllMyPublicMethods();

		Set<String> allInvokations = v.getAllInvokations();
		Set<String> allExternalInvokations = v.getAllExternalMethodInvokations();
		Set<String> allExternalNonJavaInvokations = v.getAllExternalNonJavaMethodInvokations();

		Set<String> allMyClasses = v.getAllMyClasses();
		Set<String> allMyDeprecatedClasses = v.getAllMyDeprecatedClasses();
		Set<String> allMyDeprecatedPublicClasses = v.getAllMyDeprecatedPublicClasses();
		Set<String> allMyPublicClasses = v.getAllMyPublicClasses();

		// ///////////////////////////////////

		Map<String, TypeDependency> allTypeDependencies_SuperClassAndInterfaces = new HashMap<String, TypeDependency>();
		allTypeDependencies_SuperClassAndInterfaces.putAll(v.getAllMyTypeDependencies());

		Set<String> allInheritancePairs = new HashSet<String>();
		Set<String> allInheritanceHierarchies = new HashSet<String>();
		Set<String> allInterfaceImplPairs = new HashSet<String>();
		Set<String> allInterfaceImplLists = new HashSet<String>();
		Set<String> allInheritancePairsAndInterfaceImplPairsSuperSet = new HashSet<String>();

		Set<String> allInvokationProxyPairs = new HashSet<String>();

		// towards getting a set of all classes so that the various sets can be
		// built.
		Set<String> classesKeySet = allTypeDependencies_SuperClassAndInterfaces.keySet();

		// building all inheritance Pairs set and adding to the
		// allInheritancePairsAndInterfaceImplPairsSuperSet
		for (String key : classesKeySet) {
			TypeDependency typeDep = (TypeDependency) allTypeDependencies_SuperClassAndInterfaces.get(key);
			if (null != typeDep.superClass && !"".equalsIgnoreCase(typeDep.superClass)) {
				String entry = key + Constants.DELIM_PLUGIN_ELEMENT_SUPERCLASS_INTERFACE + typeDep.superClass;
				allInheritancePairs.add(entry);
				allInheritancePairsAndInterfaceImplPairsSuperSet.add(entry);
			}
		}

		// building all inheritance Hierarchies Map

		for (String key : classesKeySet) {
			String entry = "";
			entry += getInheritanceHeirarchy(key, Constants.DELIM_PLUGIN_ELEMENT_SUPERCLASS_INTERFACE,
					allTypeDependencies_SuperClassAndInterfaces);
			if (null != entry && !"".equalsIgnoreCase(entry.trim()) && !key.trim().equalsIgnoreCase(entry.trim()))
				allInheritanceHierarchies.add(entry);

		}

		// building all interface implementation lists, pairs and adding to the
		// allInheritancePairsAndInterfaceImplPairsSuperSet
		for (String key : classesKeySet)

		{
			TypeDependency typeDep = (TypeDependency) allTypeDependencies_SuperClassAndInterfaces.get(key);

			String entry = "";
			if (null != typeDep.interfaces && 1 <= typeDep.interfaces.size()) {

				// System.out.println("++++++++ interfaces implemented:"+typeDep1.interfaces.size());
				for (String interfaceImplemented : typeDep.interfaces) {
					if (null != interfaceImplemented && !"".equalsIgnoreCase(interfaceImplemented.trim())) {
						entry += interfaceImplemented.trim() + ";";

						String localEntry = key.trim() + Constants.DELIM_PLUGIN_ELEMENT_SUPERCLASS_INTERFACE
								+ interfaceImplemented.trim();
						allInterfaceImplPairs.add(localEntry);
						allInheritancePairsAndInterfaceImplPairsSuperSet.add(localEntry);
					}
				}

				if (!"".equalsIgnoreCase(entry))
					allInterfaceImplLists.add(key.trim() + Constants.DELIM_PLUGIN_ELEMENT_SUPERCLASS_INTERFACE
							+ entry.trim());
			}
		}

		// ///////////Pruning /////////////////////////////////////////
		PluginPruningObject pluginPruningObject = new PluginPruningObject();

		pluginPruningObject = getBundlePruningInfo(allMyMethods, allExternalInvokations, allInheritanceHierarchies,
				allInterfaceImplLists);

		// building the allInvokationProxyRecords Set.
		for (String invokation : pluginPruningObject.invokationProxies.keySet()) {
			Set<String> invokationProxies = pluginPruningObject.invokationProxies.get(invokation);
			if (null != invokationProxies && 1 <= invokationProxies.size()) {

				for (String proxy : invokationProxies) {
					String oneRecord = invokation + Constants.DELIM_PLUGIN_ELEMENT_SUPERCLASS_INTERFACE + proxy.trim();
					allInvokationProxyPairs.add(oneRecord);
				}
			}
		}
		// now finally removing the invokations. (actual pruning) at the plugin
		// level.
		Log.outln("====Now Pruning  for superClass and Interface function invokations at the plugin Level");
		Log.errln("====Now Pruning  for superClass and Interface function invokations at the plugin Level");

		for (String invokation : pluginPruningObject.invokationsToBeRemoved) {
			 allExternalInvokations.remove(invokation);
			 allExternalNonJavaInvokations.remove(invokation);
		}

		// Pruning over.
		Log.outln("====  Done, Pruning  for superClass and Interface function invokations at the plugin Level");
		Log.errln("====  Done, Pruning  for superClass and Interface function invokations at the plugin Level");

		// ////////////////////////////////

		List<String> allDetectedTypes_List = new ArrayList<String>(allDetectedTypes);
		List<String> allExternalDetectedTypes_List = new ArrayList<String>(allExternalDetectedTypes);
		List<String> allExternalNonJavaDetectedTypes_List = new ArrayList<String>(allExternalNonJavaDetectedTypes);

		List<String> allMyMethods_List = new ArrayList<String>(allMyMethods);
		List<String> allMyDeprecatedMethods_List = new ArrayList<String>(allMyDeprecatedMethods);
		List<String> allMyDeprecatedPublicMethods_List = new ArrayList<String>(allMyDeprecatedPublicMethods);
		List<String> allMyPublicMethods_List = new ArrayList<String>(allMyPublicMethods);

		List<String> allInvokations_List = new ArrayList<String>(allInvokations);
		List<String> allExternalInvokations_List = new ArrayList<String>(allExternalInvokations);
		List<String> allExternalNonJavaInvokations_List = new ArrayList<String>(allExternalNonJavaInvokations);

		List<String> allMyClasses_List = new ArrayList<String>(allMyClasses);
		List<String> allMyDeprecatedClasses_List = new ArrayList<String>(allMyDeprecatedClasses);
		List<String> allMyDeprecatedPublicClasses_List = new ArrayList<String>(allMyDeprecatedPublicClasses);
		List<String> allMyPublicClasses_List = new ArrayList<String>(allMyPublicClasses);

		Map<String, Map<String, Integer>> globals = v.getGlobals();
		List<String> jarPackages_List = new ArrayList<String>(globals.keySet());
		List<String> classPackages_List = new ArrayList<String>(v.getPackages());

		List<String> allInheritancePairs_List = new ArrayList<String>(allInheritancePairs);
		List<String> allInheritanceHierarchies_List = new ArrayList<String>(allInheritanceHierarchies);
		List<String> allInterfaceImplPairs_List = new ArrayList<String>(allInterfaceImplPairs);
		List<String> allInterfaceImplLists_List = new ArrayList<String>(allInterfaceImplLists);
		List<String> allInheritancePairsAndInterfaceImplPairsSuperSet_List = new ArrayList<String>(
				allInheritancePairsAndInterfaceImplPairsSuperSet);
		List<String> allInvokationProxyPairs_List = new ArrayList<String>(allInvokationProxyPairs);

		java.util.Collections.sort(allDetectedTypes_List);
		java.util.Collections.sort(allMyMethods_List);
		java.util.Collections.sort(allMyPublicMethods_List);
		java.util.Collections.sort(allMyDeprecatedMethods_List);
		java.util.Collections.sort(allMyDeprecatedPublicMethods_List);
		java.util.Collections.sort(allInvokations_List);
		java.util.Collections.sort(allMyClasses_List);
		java.util.Collections.sort(jarPackages_List);
		java.util.Collections.sort(classPackages_List);
		java.util.Collections.sort(allExternalInvokations_List);
		java.util.Collections.sort(allExternalNonJavaInvokations_List);
		java.util.Collections.sort(allExternalDetectedTypes_List);
		java.util.Collections.sort(allExternalNonJavaDetectedTypes_List);
		java.util.Collections.sort(allMyPublicClasses_List);
		java.util.Collections.sort(allMyDeprecatedClasses_List);
		java.util.Collections.sort(allMyDeprecatedPublicClasses_List);
		java.util.Collections.sort(allInheritancePairs_List);
		java.util.Collections.sort(allInheritanceHierarchies_List);
		java.util.Collections.sort(allInterfaceImplPairs_List);
		java.util.Collections.sort(allInterfaceImplLists_List);
		java.util.Collections.sort(allInheritancePairsAndInterfaceImplPairsSuperSet_List);
		java.util.Collections.sort(allInvokationProxyPairs_List);

		// /////////// BUNDLE MANIFEST ///////////////////
		// java.util.Collections.sort(bundleRequirements);
		// this is the set of other plugins that this plugin would depend on.
		// bundleinfo.getRequires() and bundleinfo.getImports() eventually point
		// to bundleinfo.getRequirements() without any differences.

		boolean flag_bundleInfoExists = true;
		if (null == bundleinfo)
			flag_bundleInfoExists = false;

		writer.write(Constants.BUNDLE_IGNORE + "\n");
		if (flag_bundleInfoExists)
			writer.write(bundleinfo.ignoreBundle + "\n");

		writer.write(Constants.MARKER_TERMINATOR + "\n");
		
		writer.write(Constants.BUNDLE_FRAGMENT_HOST + "\n");
		for (Object s : bundleinfo.getBundleFragmentHosts())
			writer.write(s.toString() + "\n");

		writer.write(Constants.MARKER_TERMINATOR + "\n");

		writer.write(Constants.BUNDLE_REQUIREMENTS + "\n");
		if (flag_bundleInfoExists) {

			for (Object s : bundleinfo.getRequirements())
				writer.write(s.toString() + "\n");
			// Log.outln("Bundle Requirements = " +
			// bundleinfo.getRequirements().toString()+"\n"+bundleinfo.getRequirements().size()
			// +" , Bundle Requirements"); // Require-Bundle
		}
		writer.write(Constants.MARKER_TERMINATOR + "\n");
		writer.write(Constants.BUNDLE_EXPORTS + "\n");
		if (flag_bundleInfoExists) {
			for (Object s : bundleinfo.getExports())
				writer.write(s.toString() + "\n");
			// Log.outln("Bundle Exports = " +
			// bundleinfo.getExports().toString()); // Export-Package
		}
		writer.write(Constants.MARKER_TERMINATOR + "\n");
		writer.write(Constants.BUNDLE_PACKAGE_IMPORTS + "\n");
		if (flag_bundleInfoExists) {
			for (Object s : bundleinfo.getImports())
				writer.write(s.toString() + "\n");
			// Log.outln("Bundle Imports = " +
			// bundleinfo.getImports().toString());
		}
		writer.write(Constants.MARKER_TERMINATOR + "\n");
		writer.write(Constants.BUNDLE_OTHER_BUNDLE_IMPORTS + "\n");
		if (flag_bundleInfoExists) {
			for (Object s : bundleinfo.getRequires())
				writer.write(s.toString() + "\n");
			// Log.outln("Bundle Imports = " +
			// bundleinfo.getImports().toString());
		}
		writer.write(Constants.MARKER_TERMINATOR + "\n");
		writer.write(Constants.BUNDLE_SYMBOLICNAME + "\n");
		String symbolicName=Constants.PROPERTY_VALUE_UNKNOWN_LITERAL+"_"+UNKNOWN_SYMBOLIC_NAME_Counter++;
		if (flag_bundleInfoExists) {
			symbolicName=((null != bundleinfo.getSymbolicName() && !"".equalsIgnoreCase(bundleinfo.getSymbolicName().trim() ) ) ? bundleinfo.getSymbolicName().toString().trim() : symbolicName);
			// Log.outln("Symbolic Name = "+
			// bundleinfo.getSymbolicName().toString());
		}
		writer.write(  symbolicName + "\n");
		writer.write(Constants.MARKER_TERMINATOR + "\n");
		
		writer.write(Constants.BUNDLE_VERSION + "\n");
		String bundleVersion=Constants.BUNDLE_VERSION_UNKNOWN_LITERAL;
		if (flag_bundleInfoExists) {
			bundleVersion=((null != bundleinfo.getVersion() && !"".equalsIgnoreCase(bundleinfo.getVersion().toString().trim() )) ? bundleinfo.getVersion().toString().trim()  : bundleVersion);
			// Log.outln("Version = " +
			// bundleinfo.getVersion().toString());
		}
		writer.write( bundleVersion+ "\n");
		writer.write(Constants.MARKER_TERMINATOR + "\n");

		// adding the  plugin ID and extract file name to the plugin map.
		addToPluginMap(pluginFileName, symbolicName, bundleVersion);
				
		// now that we have the symbolic name and version for this plugin,
		// building the package exporter map.
		if (flag_bundleInfoExists) 
			addPackageExportsToPackageExporterMap(bundleinfo.getExports(),    symbolicName, bundleVersion);
		
		writer.write(Constants.BUNDLE_VERSION_WITHOUT_QUALIFIER + "\n");
		String bundleVersionWithoutQual=Constants.BUNDLE_VERSION_UNKNOWN_WITHOUT_QUALIFIER_LITERAL;
		if (flag_bundleInfoExists) {
			bundleVersionWithoutQual=((null != bundleinfo.getVersion() && !"".equalsIgnoreCase(bundleinfo.getVersion().toString().trim() )) ? bundleinfo.getVersion().withoutQualifier().toString().trim() : bundleVersionWithoutQual);
			// Log.outln("Version without qualifier  = " +
			// bundleinfo.getVersion().withoutQualifier().toString());
		}
		writer.write( bundleVersionWithoutQual+ "\n");
		writer.write(Constants.MARKER_TERMINATOR + "\n");
		
		writer.write(Constants.BUNDLE_CLASSPATHENTRIES + "\n");
		if (flag_bundleInfoExists) {
			for (Object s : bundleinfo.getClasspathEntries())
				writer.write(s.toString() + "\n");
			// Log.outln("Bundle ClassPathEntries  = " +
			// bundleinfo.getClasspathEntries().toString());
		}
		writer.write(Constants.MARKER_TERMINATOR + "\n");
		// Log.outln("Bundle hashcode  = " + bundleinfo.hashCode() );

		// //////////////////////////////////////////////////////

		writer.write(Constants.PLUGIN_ALL_INHERITANCE_HIERARCHIES + "\n");
		for (String s : allInheritanceHierarchies_List)
			writer.write(s + "\n");

		writer.write(Constants.MARKER_TERMINATOR + "\n");

		writer.write(Constants.PLUGIN_ALL_INHERITANCE_PAIRS + "\n");
		for (String s : allInheritancePairs_List)
			writer.write(s + "\n");
		writer.write(Constants.MARKER_TERMINATOR + "\n");

		writer.write(Constants.PLUGIN_ALL_INTERFACE_IMPLEMENTATION_LISTS + "\n");
		for (String s : allInterfaceImplLists_List)
			writer.write(s + "\n");
		writer.write(Constants.MARKER_TERMINATOR + "\n");

		writer.write(Constants.PLUGIN_ALL_INTERFACE_IMPLEMENTATION_PAIRS + "\n");
		for (String s : allInterfaceImplPairs_List)
			writer.write(s + "\n");
		writer.write(Constants.MARKER_TERMINATOR + "\n");

		writer.write(Constants.PLUGIN_ALL_INHERITANCE_AND_INTERFACE_PAIRS + "\n");
		for (String s : allInheritancePairsAndInterfaceImplPairsSuperSet_List)
			writer.write(s + "\n");
		writer.write(Constants.MARKER_TERMINATOR + "\n");

		writer.write(Constants.PLUGIN_ALL_INVOKATION_PROXY_PAIRS + "\n");
		for (String s : allInvokationProxyPairs_List)
			writer.write(s.trim() + "\n");
		writer.write(Constants.MARKER_TERMINATOR + "\n");

		// ///////////////////////////////////////////////////////

		writer.write(Constants.PLUGIN_ALL_MY_TYPES + "\n");
		// "All My Classes (Types)  ========\n");

		for (String s : allMyClasses_List) {
			writer.write(s + "\n");
		}
		writer.write(Constants.MARKER_TERMINATOR + "\n");
		// writer.write(allMyClasses.size() + "," + " own classes (types).\n");
		// Log.outln(allMyClasses.size() + "," +
		// " own classes (types).");

		writer.write(Constants.PLUGIN_ALL_MY_TYPES_PUBLIC + "\n");
		// "All My Public Classes (Types) ========\n");

		for (String s : allMyPublicClasses_List) {
			writer.write(s + "\n");
		}
		writer.write(Constants.MARKER_TERMINATOR + "\n");
		// writer.write(allMyPublicClasses.size() + ","+
		// " own public classes (types).\n");
		// Log.outln(allMyPublicClasses.size() + "," +
		// " own public classes (types).");

		writer.write(Constants.PLUGIN_ALL_MY_METHODS + "\n");
		// "All My Methods ========\n");

		for (String s : allMyMethods_List) {
			writer.write(s + "\n");
		}
		writer.write(Constants.MARKER_TERMINATOR + "\n");
		// writer.write(allMyMethods.size() + "," + " internal methods.\n");
		// Log.outln(allMyMethods.size() + "," + " internal methods.");

		writer.write(Constants.PLUGIN_ALL_MY_METHODS_PUBLIC + "\n");
		// "All My Public Methods ========\n");

		for (String s : allMyPublicMethods_List) {
			writer.write(s + "\n");
		}
		writer.write(Constants.MARKER_TERMINATOR + "\n");
		// writer.write(allMyPublicMethods.size() + ","+
		// " internal public methods.\n");
		// Log.outln(allMyPublicMethods.size() + "," +
		// " internal public methods.");

		writer.write(Constants.PLUGIN_ALL_MY_METHOD_CALLS + "\n");
		// "All Invokations ========\n");

		for (String s : allInvokations_List) {
			writer.write(s + "\n");
		}
		writer.write(Constants.MARKER_TERMINATOR + "\n");
		// writer.write(allInvokations.size() + "," +
		// " method invokations (intrnal and external).\n");
		// Log.outln(allInvokations.size() + "," +
		// " method invokations (intrnal and external).");

		writer.write(Constants.PLUGIN_ALL_MY_METHOD_CALLS_EXTERNAL + "\n");
		// "All External Invokations ========\n");

		for (String s : allExternalInvokations_List) {
			writer.write(s + "\n");
		}
		writer.write(Constants.MARKER_TERMINATOR + "\n");
		// writer.write(externalInvokations.size() + ","+
		// " method invokations (external).\n");
		// Log.outln(externalInvokations.size() + "," +
		// " method invokations (external).");

		writer.write(Constants.PLUGIN_ALL_MY_METHOD_CALLS_EXTERNAL_AND_NON_JAVA + "\n");
		// "All External and non Excluded Invokations ========\n");

		for (String s : allExternalNonJavaInvokations_List) {
			writer.write(s + "\n");
		}
		writer.write(Constants.MARKER_TERMINATOR + "\n");
		// writer.write(externalNonJavaInvokations.size() + "," +
		// " method invokations (external and non excluded).\n");
		// Log.outln(externalNonJavaInvokations.size() + ","
		// + " method invokations (external and non excluded).");

		writer.write(Constants.PLUGIN_ALL_TYPES_DETECTED + "\n");
		// "All Detected Types ========\n");
		for (String s : allDetectedTypes_List) {
			writer.write(s + "\n");
		}
		writer.write(Constants.MARKER_TERMINATOR + "\n");
		// writer.write(allDetectedTypes.size() + "," +
		// " types (internal and external).\n");
		// Log.outln(allDetectedTypes.size() + ","
		// + " types (internal and external).");

		writer.write(Constants.PLUGIN_ALL_TYPES_DETECTED_EXTERNAL + "\n");
		// "All External Detected Types ========\n");
		for (String s : allExternalDetectedTypes_List) {
			writer.write(s + "\n");
		}
		writer.write(Constants.MARKER_TERMINATOR + "\n");
		// writer.write(allExternalDetectedTypes.size() + "," +
		// " types (external).\n");
		// Log.outln(allExternalDetectedTypes.size() + ","
		// + " types (external).");

		writer.write(Constants.PLUGIN_ALL_TYPES_DETECTED_EXTERNAL_AND_NON_JAVA + "\n");
		// "All External Non Java Detected Types ========\n");
		for (String s : allExternalNonJavaDetectedTypes_List) {
			writer.write(s + "\n");
		}
		writer.write(Constants.MARKER_TERMINATOR + "\n");
		// writer.write(allExternalNonJavaDetectedTypes.size() + "," +
		// " types (external non excluded).\n");
		// Log.outln(allExternalNonJavaDetectedTypes.size() + ","
		// + " types (external Non excluded).");

		writer.write(Constants.PLUGIN_ALL_JAR_PACKAGES + "\n");
		// "All Jar Packages ========\n");

		for (String s : jarPackages_List) {
			writer.write(s + "\n");
		}
		writer.write(Constants.MARKER_TERMINATOR + "\n");
		// writer.write(jarPackages.size() + "," + " jar packages.\n");

		writer.write(Constants.PLUGIN_ALL_CLASS_PACKAGES + "\n");
		// "All  Class packages ========\n");

		for (String s : classPackages_List) {
			writer.write(s + "\n");
		}
		writer.write(Constants.MARKER_TERMINATOR + "\n");
		// writer.write(classPackages.size() + "," + " class packages.\n");
		writer.write(Constants.PLUGIN_ALL_MY_METHODS_DEPRECATED + "\n");
		// "All My Deprecated Methods ========\n");

		for (String s : allMyDeprecatedMethods_List) {
			writer.write(s + "\n");
		}
		writer.write(Constants.MARKER_TERMINATOR + "\n");
		// writer.write(allMyDeprecatedMethods.size() + "," +
		// " deprecated methods.\n");
		// .println(allMyDeprecatedMethods.size() + ","
		// + " deprecated methods.");

		writer.write(Constants.PLUGIN_ALL_MY_TYPES_DEPRECATED + "\n");
		// "All My Deprecated Classes ========\n");

		for (String s : allMyDeprecatedClasses_List) {
			writer.write(s + "\n");
		}
		writer.write(Constants.MARKER_TERMINATOR + "\n");

		// writer.write(allMyDeprecatedClasses.size() + "," +
		// " deprecated   classes. \n");
		// Log.outln(allMyDeprecatedClasses.size() + ","
		// + " deprecated   classes.");

		writer.write(Constants.BUNDLE_PLUGIN_XML + "\n");
		if (null != bundleinfo) {// this means that there won't be any
									// plugin.xml available.
			writer.write(bundleinfo.getPluginXml() + "\n");
		}
		writer.write(Constants.MARKER_TERMINATOR + "\n");

		// writer.write("===================================================\n");
		writer.close();
		fwriter.close();
	}

	/**
	 * 
	 * adds the package exports of the current plugin to the exportedPackagesMap.
	 * 
	 * @param packageExports
	 * @param bundleSymbolicName
	 * @param bundleVersionWithQualifier
	 */
	private static void addPackageExportsToPackageExporterMap(Set packageExports, String bundleSymbolicName, String bundleVersionWithQualifier) {
		String pluginId = ParsingUtil.buildIdentifier(bundleSymbolicName, bundleVersionWithQualifier);
		for(Object export:packageExports)
		{
			String packageExport=export.toString();
			String packageName=ParsingUtil.getNameFromBundleDependencyEntry(packageExport, true, true).trim();
			String packageVersion=ParsingUtil.getVersionStrFromBundleDependencyEntry(packageExport).trim();
			
			//  defaulting no version information to 0.0.0 as per OSGi core 5.0.0 specification. (section 3.6.5 Export-package, pp42)
			if(packageVersion.length()<1)
				packageVersion=new Version(0, 0, 0, null).toString();
			
			String packageId=ParsingUtil.buildIdentifier(packageName, packageVersion);
			
			Map<String,Set<String>> interimExportedPackageIdMap=new HashMap<String, Set<String>>();
			if(exportedPackagesMap.containsKey(packageName))
				interimExportedPackageIdMap=exportedPackagesMap.get(packageName);
			
			Set<String> interimExportingPluginSet=new HashSet<String>();
			if(interimExportedPackageIdMap.containsKey(packageId))
				interimExportingPluginSet=interimExportedPackageIdMap.get(packageId);
			
			interimExportingPluginSet.add(pluginId);
			interimExportedPackageIdMap.put(packageId, interimExportingPluginSet);
			exportedPackagesMap.put(packageName, interimExportedPackageIdMap);
			
		}
	}

	/**
	 * 
	 * adds a plugin entry to the Plugin map.
	 * @param pluginFileName
	 * @param bundleSymbolicName
	 * @param bundleVersionWithQualifier
	 */
	private static void addToPluginMap(String pluginFileName, String bundleSymbolicName, String bundleVersionWithQualifier) {
		// adding the  plugin ID and extract file name to the plugin map.
		Map<String,Set<String>> interimPluginIdMap=new HashMap<String, Set<String>>();
		if(pluginMap.containsKey(bundleSymbolicName))
			interimPluginIdMap=pluginMap.get(bundleSymbolicName);
		String pluginId = ParsingUtil.buildIdentifier(bundleSymbolicName, bundleVersionWithQualifier);
		Set<String> interimPluginFileSet=new HashSet<String>();
		if(interimPluginIdMap.containsKey(pluginId))
			interimPluginFileSet=interimPluginIdMap.get(pluginId);
		interimPluginFileSet.add(pluginFileName);
		//adding back to the Map and outer map.
		interimPluginIdMap.put(pluginId, interimPluginFileSet);
		pluginMap.put(bundleSymbolicName, interimPluginIdMap);
	}

	private static void writeJavaSDKClassesData(DependencyVisitor v, BundleInfo bundleinfo, String pluginFileName,
			String outputLocation) throws IOException {

		pluginFileName = pluginFileName.trim();
		if (pluginFileName.endsWith(Constants.EXTENSION_JAR))
			pluginFileName = pluginFileName.substring(0, pluginFileName.length() - Constants.EXTENSION_JAR.length());

		outputLocation = (outputLocation + "/").trim().replaceAll("//", "/").replaceAll("\\\\", "\\");
		pluginFileName= pluginFileName.replace('/', '_').replace('\\', '_');
		FileWriter fwriter = new FileWriter(outputLocation + Constants.EXTRACT_FILE_PREFIX_PLUGIN
				+ pluginFileName + Constants.EXTRACT_FILE_EXTENSION_PLUGIN);
		BufferedWriter writer = new BufferedWriter(fwriter);

		// ////////////////////////////////////////////////
		Set<String> allDetectedTypes = v.getAllDetectedTypes();
		Set<String> allMyMethods = v.getAllMyMethods();
		Set<String> allMyPublicMethods = v.getAllMyPublicMethods();

		Set<String> allMyClasses = v.getAllMyClasses();
		Set<String> allMyPublicClasses = v.getAllMyPublicClasses();

		// ///////////////////////////////////

		Map<String, TypeDependency> allTypeDependencies_SuperClassAndInterfaces = v.getAllMyTypeDependencies();

		Set<String> allInheritancePairs = new HashSet<String>();
		Set<String> allInheritanceHierarchies = new HashSet<String>();
		Set<String> allInterfaceImplPairs = new HashSet<String>();
		Set<String> allInterfaceImplLists = new HashSet<String>();
		Set<String> allInheritancePairsAndInterfaceImplPairsSuperSet = new HashSet<String>();

		// towards getting a set of all classes so that the various sets can be
		// built.
		Set<String> classesKeySet = allTypeDependencies_SuperClassAndInterfaces.keySet();

		// building all inheritance Pairs set and adding to the
		// allInheritancePairsAndInterfaceImplPairsSuperSet
		for (String key : classesKeySet) {
			TypeDependency typeDep = (TypeDependency) allTypeDependencies_SuperClassAndInterfaces.get(key);
			if (null != typeDep.superClass && !"".equalsIgnoreCase(typeDep.superClass)) {
				String entry = key + Constants.DELIM_PLUGIN_ELEMENT_SUPERCLASS_INTERFACE + typeDep.superClass;
				allInheritancePairs.add(entry);
				allInheritancePairsAndInterfaceImplPairsSuperSet.add(entry);
			}
		}

		// building all inheritance Hierarchies Map

		for (String key : classesKeySet) {
			String entry = "";
			entry += getInheritanceHeirarchy(key, Constants.DELIM_PLUGIN_ELEMENT_SUPERCLASS_INTERFACE,
					allTypeDependencies_SuperClassAndInterfaces);
			if (null != entry && !"".equalsIgnoreCase(entry.trim()) && !key.trim().equalsIgnoreCase(entry.trim()))
				allInheritanceHierarchies.add(entry);

		}

		// building all interface implementation lists, pairs and adding to the
		// allInheritancePairsAndInterfaceImplPairsSuperSet
		for (String key : classesKeySet)

		{
			TypeDependency typeDep = (TypeDependency) allTypeDependencies_SuperClassAndInterfaces.get(key);

			String entry = "";
			if (null != typeDep.interfaces && 1 >= typeDep.interfaces.size()) {

				// System.out.println("++++++++ interfaces implemented:"+typeDep1.interfaces.size());
				for (String interfaceImplemented : typeDep.interfaces) {
					if (null != interfaceImplemented && !"".equalsIgnoreCase(interfaceImplemented.trim())) {
						entry += interfaceImplemented.trim() + ";";

						String localEntry = key.trim() + Constants.DELIM_PLUGIN_ELEMENT_SUPERCLASS_INTERFACE
								+ interfaceImplemented.trim();
						allInterfaceImplPairs.add(localEntry);
						allInheritancePairsAndInterfaceImplPairsSuperSet.add(localEntry);
					}
				}

				if (!"".equalsIgnoreCase(entry))
					allInterfaceImplLists.add(key.trim() + Constants.DELIM_PLUGIN_ELEMENT_SUPERCLASS_INTERFACE
							+ entry.trim());
			}
		}

		List<String> allDetectedTypes_List = new ArrayList<String>(allDetectedTypes);

		List<String> allMyMethods_List = new ArrayList<String>(allMyMethods);
		List<String> allMyPublicMethods_List = new ArrayList<String>(allMyPublicMethods);

		List<String> allMyClasses_List = new ArrayList<String>(allMyClasses);
		List<String> allMyPublicClasses_List = new ArrayList<String>(allMyPublicClasses);

		Map<String, Map<String, Integer>> globals = v.getGlobals();
		List<String> jarPackages_List = new ArrayList<String>(globals.keySet());
		List<String> classPackages_List = new ArrayList<String>(v.getPackages());

		List<String> allInheritancePairs_List = new ArrayList<String>(allInheritancePairs);
		List<String> allInheritanceHierarchies_List = new ArrayList<String>(allInheritanceHierarchies);
		List<String> allInterfaceImplPairs_List = new ArrayList<String>(allInterfaceImplPairs);
		List<String> allInterfaceImplLists_List = new ArrayList<String>(allInterfaceImplLists);
		List<String> allInheritancePairsAndInterfaceImplPairsSuperSet_List = new ArrayList<String>(
				allInheritancePairsAndInterfaceImplPairsSuperSet);

		java.util.Collections.sort(allDetectedTypes_List);
		java.util.Collections.sort(allMyMethods_List);
		java.util.Collections.sort(allMyPublicMethods_List);
		java.util.Collections.sort(allMyClasses_List);
		java.util.Collections.sort(jarPackages_List);
		java.util.Collections.sort(classPackages_List);
		java.util.Collections.sort(allMyPublicClasses_List);
		java.util.Collections.sort(allInheritancePairs_List);
		java.util.Collections.sort(allInheritanceHierarchies_List);
		java.util.Collections.sort(allInterfaceImplPairs_List);
		java.util.Collections.sort(allInterfaceImplLists_List);
		java.util.Collections.sort(allInheritancePairsAndInterfaceImplPairsSuperSet_List);

		// /////////// BUNDLE MANIFEST ///////////////////
		// java.util.Collections.sort(bundleRequirements);
		// this is the set of other plugins that this plugin would depend on.
		// bundleinfo.getRequires() and bundleinfo.getImports() eventually point
		// to bundleinfo.getRequirements() without any differences.

		boolean flag_bundleInfoExists = true;
		if (null == bundleinfo)
			flag_bundleInfoExists = false;

		writer.write(Constants.BUNDLE_IGNORE + "\n");
		if (flag_bundleInfoExists)
			writer.write(bundleinfo.ignoreBundle + "\n");

		writer.write(Constants.MARKER_TERMINATOR + "\n");

		writer.write(Constants.BUNDLE_REQUIREMENTS + "\n");
		if (flag_bundleInfoExists) {

			for (Object s : bundleinfo.getRequirements())
				writer.write(s.toString() + "\n");
			// Log.outln("Bundle Requirements = " +
			// bundleinfo.getRequirements().toString()+"\n"+bundleinfo.getRequirements().size()
			// +" , Bundle Requirements"); // Require-Bundle
		}
		writer.write(Constants.MARKER_TERMINATOR + "\n");
		writer.write(Constants.BUNDLE_EXPORTS + "\n");
		if (flag_bundleInfoExists) {
			for (Object s : bundleinfo.getExports())
				writer.write(s.toString() + "\n");
			// Log.outln("Bundle Exports = " +
			// bundleinfo.getExports().toString()); // Export-Package
		}
		writer.write(Constants.MARKER_TERMINATOR + "\n");
		writer.write(Constants.BUNDLE_PACKAGE_IMPORTS + "\n ");
		if (flag_bundleInfoExists) {
			for (Object s : bundleinfo.getImports())
				writer.write(s.toString() + "\n");
			// Log.outln("Bundle Imports = " +
			// bundleinfo.getImports().toString());
		}
		writer.write(Constants.MARKER_TERMINATOR + "\n");
		writer.write(Constants.BUNDLE_OTHER_BUNDLE_IMPORTS + "\n ");
		if (flag_bundleInfoExists) {
			for (Object s : bundleinfo.getRequires())
				writer.write(s.toString() + "\n");
			// Log.outln("Bundle Imports = " +
			// bundleinfo.getImports().toString());
		}
		writer.write(Constants.MARKER_TERMINATOR + "\n");
		writer.write(Constants.BUNDLE_SYMBOLICNAME + "\n");
		String symbolicName=Constants.EXTRACT_FILE_NAME_JAVA_CLASSES_SDK;
		if (flag_bundleInfoExists) {
			symbolicName=((null != bundleinfo.getSymbolicName() && !"".equalsIgnoreCase(bundleinfo.getSymbolicName().trim() ) ) ? bundleinfo.getSymbolicName().toString().trim() : symbolicName);
			// Log.outln("Symbolic Name = "+
			// bundleinfo.getSymbolicName().toString());
		}
		writer.write(  symbolicName + "\n");
	
		writer.write(Constants.MARKER_TERMINATOR + "\n");
		
		writer.write(Constants.BUNDLE_VERSION + "\n");
		String bundleVersion=Constants.BUNDLE_VERSION_UNKNOWN_LITERAL;
		if (flag_bundleInfoExists) {
			bundleVersion=((null != bundleinfo.getVersion() && !"".equalsIgnoreCase(bundleinfo.getVersion().toString().trim() )) ? bundleinfo.getVersion().toString().trim()  : bundleVersion);
			// Log.outln("Version = " +
			// bundleinfo.getVersion().toString());
		}
		writer.write( bundleVersion+ "\n");

		writer.write(Constants.MARKER_TERMINATOR + "\n");

		// adding the  plugin ID and extract file name to the plugin map.

		addToPluginMap(pluginFileName, symbolicName, bundleVersion);
			
	
		
		writer.write(Constants.BUNDLE_VERSION_WITHOUT_QUALIFIER + "\n");
		String bundleVersionWithoutQual=Constants.BUNDLE_VERSION_UNKNOWN_WITHOUT_QUALIFIER_LITERAL;
		if (flag_bundleInfoExists) {
			bundleVersionWithoutQual=((null != bundleinfo.getVersion() && !"".equalsIgnoreCase(bundleinfo.getVersion().toString().trim() )) ? bundleinfo.getVersion().withoutQualifier().toString().trim() : bundleVersionWithoutQual);
			// Log.outln("Version without qualifier  = " +
			// bundleinfo.getVersion().withoutQualifier().toString());
		}
		writer.write( bundleVersionWithoutQual+ "\n");
		
		
		writer.write(Constants.BUNDLE_CLASSPATHENTRIES + "\n");
		if (flag_bundleInfoExists) {
			for (Object s : bundleinfo.getClasspathEntries())
				writer.write(s.toString() + "\n");
			// Log.outln("Bundle ClassPathEntries  = " +
			// bundleinfo.getClasspathEntries().toString());
		}
		writer.write(Constants.MARKER_TERMINATOR + "\n");
		// Log.outln("Bundle hashcode  = " + bundleinfo.hashCode() );

		// //////////////////////////////////////////////////////

		writer.write(Constants.PLUGIN_ALL_INHERITANCE_HIERARCHIES + "\n");

		for (String s : allInheritanceHierarchies_List)
			writer.write(s + "\n");

		writer.write(Constants.MARKER_TERMINATOR + "\n");

		writer.write(Constants.PLUGIN_ALL_INHERITANCE_PAIRS + "\n");
		for (String s : allInheritancePairs_List)
			writer.write(s + "\n");
		writer.write(Constants.MARKER_TERMINATOR + "\n");

		writer.write(Constants.PLUGIN_ALL_INTERFACE_IMPLEMENTATION_LISTS + "\n");
		for (String s : allInterfaceImplLists_List)
			writer.write(s + "\n");
		writer.write(Constants.MARKER_TERMINATOR + "\n");

		writer.write(Constants.PLUGIN_ALL_INTERFACE_IMPLEMENTATION_PAIRS + "\n");
		for (String s : allInterfaceImplPairs_List)
			writer.write(s + "\n");
		writer.write(Constants.MARKER_TERMINATOR + "\n");

		writer.write(Constants.PLUGIN_ALL_INHERITANCE_AND_INTERFACE_PAIRS + "\n");
		for (String s : allInheritancePairsAndInterfaceImplPairsSuperSet_List)
			writer.write(s + "\n");
		writer.write(Constants.MARKER_TERMINATOR + "\n");

		writer.write(Constants.PLUGIN_ALL_INVOKATION_PROXY_PAIRS + "\n");
		// for (String s : allInvokationProxyPairs_List)
		// writer.write(s.trim() + "\n");
		writer.write(Constants.MARKER_TERMINATOR + "\n");

		// ///////////////////////////////////////////////////////

		writer.write(Constants.PLUGIN_ALL_MY_TYPES + "\n");
		// "All My Classes (Types)  ========\n");

		for (String s : allMyClasses_List) {
			writer.write(s + "\n");
		}
		writer.write(Constants.MARKER_TERMINATOR + "\n");
		// writer.write(allMyClasses.size() + "," + " own classes (types).\n");
		// Log.outln(allMyClasses.size() + "," +
		// " own classes (types).");

		writer.write(Constants.PLUGIN_ALL_MY_TYPES_PUBLIC + "\n");
		// "All My Public Classes (Types) ========\n");

		for (String s : allMyPublicClasses_List) {
			writer.write(s + "\n");
		}
		writer.write(Constants.MARKER_TERMINATOR + "\n");
		// writer.write(allMyPublicClasses.size() + ","+
		// " own public classes (types).\n");
		// Log.outln(allMyPublicClasses.size() + "," +
		// " own public classes (types).");

		writer.write(Constants.PLUGIN_ALL_MY_METHODS + "\n");
		// "All My Methods ========\n");

		for (String s : allMyMethods_List) {
			writer.write(s + "\n");
		}
		writer.write(Constants.MARKER_TERMINATOR + "\n");
		// writer.write(allMyMethods.size() + "," + " internal methods.\n");
		// Log.outln(allMyMethods.size() + "," + " internal methods.");

		writer.write(Constants.PLUGIN_ALL_MY_METHODS_PUBLIC + "\n");
		// "All My Public Methods ========\n");

		for (String s : allMyPublicMethods_List) {
			writer.write(s + "\n");
		}
		writer.write(Constants.MARKER_TERMINATOR + "\n");
		// writer.write(allMyPublicMethods.size() + ","+
		// " internal public methods.\n");
		// Log.outln(allMyPublicMethods.size() + "," +
		// " internal public methods.");

		writer.write(Constants.PLUGIN_ALL_MY_METHOD_CALLS + "\n");
		// "All Invokations ========\n");

		// for (String s : allInvokations_List)
		// writer.write(s + "\n");

		writer.write(Constants.MARKER_TERMINATOR + "\n");
		// writer.write(allInvokations.size() + "," +
		// " method invokations (intrnal and external).\n");
		// Log.outln(allInvokations.size() + "," +
		// " method invokations (intrnal and external).");

		writer.write(Constants.PLUGIN_ALL_MY_METHOD_CALLS_EXTERNAL + "\n");
		// "All External Invokations ========\n");
		// for (String s : allExternalInvokations_List)
		// writer.write(s + "\n");
		writer.write(Constants.MARKER_TERMINATOR + "\n");
		// writer.write(externalInvokations.size() + ","+
		// " method invokations (external).\n");
		// Log.outln(externalInvokations.size() + "," +
		// " method invokations (external).");

		writer.write(Constants.PLUGIN_ALL_MY_METHOD_CALLS_EXTERNAL_AND_NON_JAVA + "\n");
		// "All External and non Excluded Invokations ========\n");

		// for (String s : allExternalNonJavaInvokations_List) {
		// writer.write(s + "\n");
		// }
		writer.write(Constants.MARKER_TERMINATOR + "\n");
		// writer.write(externalNonJavaInvokations.size() + "," +
		// " method invokations (external and non excluded).\n");
		// Log.outln(externalNonJavaInvokations.size() + ","
		// + " method invokations (external and non excluded).");

		writer.write(Constants.PLUGIN_ALL_TYPES_DETECTED + "\n");
		// "All Detected Types ========\n");
		for (String s : allDetectedTypes_List) {
			writer.write(s + "\n");
		}
		writer.write(Constants.MARKER_TERMINATOR + "\n");
		// writer.write(allDetectedTypes.size() + "," +
		// " types (internal and external).\n");
		// Log.outln(allDetectedTypes.size() + ","
		// + " types (internal and external).");

		writer.write(Constants.PLUGIN_ALL_TYPES_DETECTED_EXTERNAL + "\n");
		// "All External Detected Types ========\n");
		// for (String s : allExternalDetectedTypes_List) {
		// writer.write(s + "\n");
		// }
		writer.write(Constants.MARKER_TERMINATOR + "\n");
		// writer.write(allExternalDetectedTypes.size() + "," +
		// " types (external).\n");
		// Log.outln(allExternalDetectedTypes.size() + ","
		// + " types (external).");

		writer.write(Constants.PLUGIN_ALL_TYPES_DETECTED_EXTERNAL_AND_NON_JAVA + "\n");
		// "All External Non Java Detected Types ========\n");
		// for (String s : allExternalNonJavaDetectedTypes_List) {
		// writer.write(s + "\n");
		// }
		writer.write(Constants.MARKER_TERMINATOR + "\n");
		// writer.write(allExternalNonJavaDetectedTypes.size() + "," +
		// " types (external non excluded).\n");
		// Log.outln(allExternalNonJavaDetectedTypes.size() + ","
		// + " types (external Non excluded).");

		writer.write(Constants.PLUGIN_ALL_JAR_PACKAGES + "\n");
		// "All Jar Packages ========\n");

		for (String s : jarPackages_List) {
			writer.write(s + "\n");
		}
		writer.write(Constants.MARKER_TERMINATOR + "\n");
		// writer.write(jarPackages.size() + "," + " jar packages.\n");

		writer.write(Constants.PLUGIN_ALL_CLASS_PACKAGES + "\n");
		// "All  Class packages ========\n");

		for (String s : classPackages_List) {
			writer.write(s + "\n");
		}
		writer.write(Constants.MARKER_TERMINATOR + "\n");
		// writer.write(classPackages.size() + "," + " class packages.\n");
		writer.write(Constants.PLUGIN_ALL_MY_METHODS_DEPRECATED + "\n");
		// "All My Deprecated Methods ========\n");
		// for (String s : allMyDeprecatedMethods_List) {
		// writer.write(s + "\n");
		// }
		writer.write(Constants.MARKER_TERMINATOR + "\n");
		// writer.write(allMyDeprecatedMethods.size() + "," +
		// " deprecated methods.\n");
		// .println(allMyDeprecatedMethods.size() + ","
		// + " deprecated methods.");

		writer.write(Constants.PLUGIN_ALL_MY_TYPES_DEPRECATED + "\n");
		// "All My Deprecated Classes ========\n");
		// for (String s : allMyDeprecatedClasses_List) {
		// writer.write(s + "\n");
		// }
		writer.write(Constants.MARKER_TERMINATOR + "\n");

		// writer.write(allMyDeprecatedClasses.size() + "," +
		// " deprecated   classes. \n");
		// Log.outln(allMyDeprecatedClasses.size() + ","
		// + " deprecated   classes.");

		writer.write(Constants.BUNDLE_PLUGIN_XML + "\n");
		if (null != bundleinfo) {// this means that there won't be any
									// plugin.xml available.
			writer.write(bundleinfo.getPluginXml() + "\n");
		}
		writer.write(Constants.MARKER_TERMINATOR + "\n");

		// writer.write("===================================================\n");
		writer.close();
		fwriter.close();
	}
}
