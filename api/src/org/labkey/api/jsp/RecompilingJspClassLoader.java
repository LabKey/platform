/*
 * Copyright (c) 2008-2019 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.api.jsp;

import org.apache.jasper.JspC;
import org.apache.jasper.servlet.JspCServletContext;
import org.apache.jasper.servlet.TldScanner;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tomcat.JarScanner;
import org.apache.tomcat.util.scan.StandardJarScanner;
import org.labkey.api.annotations.JavaRuntimeVersion;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.module.ResourceFinder;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.HideConfigurationDetails;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintWriter;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Checks if the JSP source has changed since the last time the JSP was compiled, and if so, recompiles the JSP and
 * uses that version to serve the request.
 *
 * Used in dev mode at runtime. Production mode servers assume that all JSPs have been precompiled and are up to date.
 *
 * User: adam
 * Date: Oct 25, 2008
 */
public class RecompilingJspClassLoader extends JspClassLoader
{
    private static final Logger _log = LogManager.getLogger(RecompilingJspClassLoader.class);
    private static final String JSP_JAVA_PATH = "/jspTempDir/classes";
    private static final String JSP_CLASSES_DIR = "/classes/java/jsp";
    private static final String JSP_PACKAGE_PATH = JSP_PACKAGE.replaceAll("\\.", "/");
    private static final Map<ResourceFinder, ClassLoader> _classLoaders = new HashMap<>();
    private static final boolean TEST = false;          // Set to true to force a re-compile of each JSP the first time it's encountered
    private static final Set<String> _compiledJsps = new HashSet<>();    // Used during test mode
    private static final String DB_SCRIPT_PATH = "/schemas/dbscripts";

    @Override
    public Class loadClass(ServletContext context, String jspFilename) throws ClassNotFoundException
    {
        String compiledJspPath = getCompiledJspPath(jspFilename);
        Collection<ResourceFinder> finders = ModuleLoader.getInstance().getResourceFindersForPath(compiledJspPath);

        for (ResourceFinder finder : finders)
        {
            File jspJavaFileBuildDirectory = new File(finder.getBuildPath() + JSP_JAVA_PATH);
            File jspClassesFileBuildDirectory = new File(finder.getBuildPath() + JSP_CLASSES_DIR);
            File classFile = new File(jspClassesFileBuildDirectory, JSP_PACKAGE_PATH + compiledJspPath + ".class");
            File sourceFile = null;
            if (null != finder.getSourcePath())
                sourceFile = new File(getCompleteSourcePath(finder.getSourcePath(), getSourceJspPath(jspFilename)));

            if (classFile.exists() || (null != sourceFile && sourceFile.exists()))
                return getCompiledClassFile(classFile, jspJavaFileBuildDirectory, jspClassesFileBuildDirectory, finder, jspFilename);
        }

        return super.loadClass(context, jspFilename);
    }


    @JavaRuntimeVersion  // Change CompilerTargetVM and CompilerSourceVM settings below
    private Class getCompiledClassFile(File classFile, File jspJavaFileBuildDirectory, File jspClassesFileBuildDir, ResourceFinder finder, String jspFileName)
    {
        String relativePath = getSourceJspPath(jspFileName);
        // Create File object for JSP source
        String sourcePath = getCompleteSourcePath(finder.getSourcePath(), relativePath);
        File sourceFile = new File(sourcePath);

        Collection<ResourceFinder> apiResourceFinders = ModuleLoader.getInstance().getResourceFindersForPath("/org/labkey/api/");
        try
        {
            String className = getJspClassName(jspFileName);

            synchronized(_classLoaders)
            {
                // Is source more recent than compiled class?
                boolean requiresRecompile = sourceFile.exists() && (!classFile.exists() || sourceFile.lastModified() > classFile.lastModified());
                boolean requiresTestRecompile = TEST && !_compiledJsps.contains(sourcePath);

                if (requiresRecompile || requiresTestRecompile)
                {
                    _log.info("Recompiling " + relativePath);

                    // Copy .jsp file from source to build staging directory
                    File stagingJsp = new File(jspJavaFileBuildDirectory.getParentFile().getParent()  + "/jspWebappDir/webapp", relativePath);
                    if (!stagingJsp.getParentFile().exists())
                        stagingJsp.getParentFile().mkdirs();
                    FileUtil.copyFile(sourceFile, stagingJsp);

                    ClassPath cp = new ClassPath();
                    cp.addDirectory(new File(finder.getBuildPath(), "/explodedModule/lib"));
                    // N.B. Our build references specific tomcat versions (set in the root-level gradle.properties file),
                    // whereas here we add the tomcat libraries for the local installation to the classpath. This should
                    // mostly be OK, but if seeing different behavior between the JSPs from the Gradle build and those
                    // compiled while in dev mode, this may be a culprit.
                    File tomcatLib = ModuleLoader.getInstance().getTomcatLib();
                    if (null != tomcatLib)
                        cp.addDirectory(tomcatLib);
                    // With the Gradle build, api and internal are first-class modules and their libraries are no longer
                    // put into WEB-INF/lib so we include their individual lib directories in the classpath for the JSPs.
                    for (ResourceFinder apiFinder : apiResourceFinders)
                        cp.addDirectory(new File(apiFinder.getBuildPath(), "/explodedModule/lib"));
                    cp.addDirectory(getModulesApiLib());

                    // Compile the .jsp file
                    JspC jasper = new JspC() {
                        // This override eliminates unnecessary TLD scanning during recompile
                        @Override
                        protected TldScanner newTldScanner(JspCServletContext context, boolean namespaceAware, boolean validate, boolean blockExternal)
                        {
                            StandardJarScanner scanner = new StandardJarScanner();
                            scanner.setJarScanFilter((jarScanType, s) -> false);
                            context.setAttribute(JarScanner.class.getName(), scanner);
                            return super.newTldScanner(context, namespaceAware, validate, blockExternal);
                        }
                    };
                    jasper.setUriroot(jspJavaFileBuildDirectory.getParentFile().getParent() + "/jspWebappDir/webapp/");
                    jasper.setOutputDir(jspJavaFileBuildDirectory.getAbsolutePath());
                    jasper.setPackage("org.labkey.jsp.compiled");
                    jasper.setCompilerTargetVM("16");
                    jasper.setCompilerSourceVM("16");
                    jasper.setCompile(false);
                    jasper.setListErrors(true);

                    if (relativePath.startsWith("/"))
                        relativePath = relativePath.substring(1);
                    jasper.setJspFiles(relativePath);
                    jasper.setClassPath(cp.getPath());
                    jasper.execute();

                    // Compile the _jsp.java file
                    String stagingJava = classFile.getAbsolutePath()
                            .replace(jspClassesFileBuildDir.getAbsolutePath(), jspJavaFileBuildDirectory.getAbsolutePath())
                            .replaceFirst("\\.class", ".java");
                    compileJavaFile(stagingJava, cp.getPath(), jspFileName, sourceFile, jspClassesFileBuildDir.getAbsolutePath());

                    _classLoaders.remove(finder);

                    if (TEST)
                        _compiledJsps.add(sourcePath);
                }

                ClassLoader loader = _classLoaders.get(finder);

                if (null == loader)
                {
                    // Convert directory to a URL
                    URL url = jspClassesFileBuildDir.toURI().toURL();
                    loader = new URLClassLoader(new URL[]{url}, Thread.currentThread().getContextClassLoader());
                    _classLoaders.put(finder, loader);
                }

                return loader.loadClass(className);
            }
       }
       catch (Exception e)
       {
           throw new RuntimeException(e);
       }
    }

    private String getCompleteSourcePath(String finderSource, String jspSource)
    {
        StringBuilder ret = new StringBuilder(jspSource);

        if (ret.indexOf(DB_SCRIPT_PATH) == -1) // it's a regular jsp file, source folder will be something like src/org/labkey/modulename etc.
        {
            ret.insert(0, "/src");
        }
        else // it's a db upgrade script, drop the namespace portion of the path and change to correct location in resources folder
        {
            ret.replace(0, ret.indexOf(DB_SCRIPT_PATH), "/resources");
        }
        ret.insert(0, finderSource);

        return ret.toString();
    }

    private void compileJavaFile(String javaPath, String classPath, String jspFilePath, File jspFile, String classDirPath) throws Exception
    {
        ByteArrayOutputStream errorStream = new ByteArrayOutputStream();
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        int ret = compiler.run(null, null, errorStream, javaPath, "-cp", classPath, "-d", classDirPath, "-g");

        if (0 != ret)
            throw new JspCompilationException(jspFilePath, jspFile, errorStream.toString());
    }

    private static class JspCompilationException extends ServletException implements HideConfigurationDetails
    {
        private JspCompilationException(String jspFilePath, File jspFile, String errors)
        {
            super("Error compiling " + jspFilePath + "\n" + errors);
            logJspPath(jspFile, errors);
        }

        // Attempt to generate and log a link to the JSP file that failed to compile. IntelliJ will render this as a
        // single click link that navigates close to the error line.
        private static void logJspPath(File jspSource, String errors)
        {
            int idx = errors.indexOf("_jsp.java:");
            String path = "";
            if (idx != -1)
            {
                int begin = idx + 10;
                int end = errors.indexOf(":", begin);

                if (end != -1)
                {
                    path = jspSource.getAbsolutePath() + ":" + errors.substring(begin, end) + "\n";
                    _log.error("Error compiling JSP:\n" + path);
                }
            }
        }

        @Override
        public StackTraceElement[] getStackTrace()
        {
            // Don't care about the stack trace
            return new StackTraceElement[]{};
        }

        @Override
        public void printStackTrace(PrintWriter pw)
        {
            // Don't care about the stack trace
        }
    }

    private static class ClassPath
    {
        private final String SEP = System.getProperty("path.separator");
        private final StringBuilder _path = new StringBuilder();

        private void addFile(String filePath)
        {
            _path.append(filePath).append(SEP);
        }

        private void addFile(File file)
        {
            addFile(file.getAbsolutePath());
        }

        private void addDirectory(String dirPath)
        {
            addDirectory(new File(dirPath));
        }

        private void addDirectory(File dir)
        {
            if (dir.exists())
            {
                assert dir.isDirectory();

                for (File file : dir.listFiles())
                    addFile(file);
            }
        }

        private String getPath()
        {
            return _path.toString();
        }
    }

    private String getModulesApiLib()
    {
        return AppProps.getInstance().getProjectRoot() + "/build/modules-api";
    }
}
