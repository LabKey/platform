/*
 * Copyright (c) 2008-2017 LabKey Corporation
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
package org.labkey.api.pipeline.file;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.pipeline.ParamParser;
import org.labkey.api.util.FileType;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * <code>FileAnalysisJobSupport</code>
 *
 * @author brendanx
 */
public interface FileAnalysisJobSupport
{
    /**
     * @return protocol name of the current protocol.
     */
    String getProtocolName();

    /**
     * @return the base name for the full set of files.
     */
    String getJoinedBaseName();

    /**
     * @return the base names for all the split input files, or just this
     *  job's single base name in an array, if this is a split job.
     */
    List<String> getSplitBaseNames();

    /**
     * @return base name of the original input file.
     */
    String getBaseName();

    /**
     * @param fileType The file type to compare
     * @return base name for the specified FileType
     */
    String getBaseNameForFileType(FileType fileType);

    /**
     * @return the directory in which the original input file resides.
     */
    @Deprecated //Prefer the getDataDirectoryPath version as File return type doesn't support full URIs very well
    File getDataDirectory();
    default Path getDataDirectoryPath()
    {
        // TODO This needs implementation in derived classes...
        // This is typically safe but may cause an error if FileSystem provider isn't configured
        return getDataDirectory().toPath();
    }

    /**
     * @return the directory where the input files reside, and where the
     *      final analysis should end up.
     */
    @Deprecated // Please use getAnalysisDirectoryPath instead, as File objects may have issues with full URIs
    File getAnalysisDirectory();
    default Path getAnalysisDirectoryPath()
    {
        // TODO This needs implementation in derived classes...
        // This is typically safe but may cause an error if FileSystem provider isn't configured
        return getAnalysisDirectory().toPath();
    }

    /**
     * Returns a file for use as input in the pipeline, given its name.
     * This allows the task definitions to name files they require as input,
     * and the pipeline definition to specify where those files should come from.
     */
    @Deprecated // Please use findInputPath instead, as File objects may have issues with full URIs
    File findInputFile(String name);
    default Path findInputPath(String filepath)
    {
        // TODO This needs implementation in derived classes...
        // This is typically safe but may cause an error if FileSystem provider isn't configured
        return findInputFile(filepath).toPath();
    }

    /**
     * Returns a file for use as output in the pipeline, given its name. 
     * This allows the task definitions to name files they create as output,
     * and the pipeline definition to specify where those files should end up.
     */
    @Deprecated //Please switch to use findOutputPath
    File findOutputFile(String name); //TODO update implementations to return nio.Path directly
    default Path findOutputPath(String name)
    {
        //This is generally safe, but may fail if the appropriate filesystem providers are not registered.
        return findOutputFile(name).toPath();
    }

    /**
     * Returns a file for the output dir and file name.
     * The output dir is a directory path relative to the analysis directory,
     * or, if the path starts with "/", relative to the pipeline root.
     */
    @Deprecated //Please switch to use findOutputPath
    File findOutputFile(@NotNull String outputDir, @NotNull String fileName);
    default Path findOutputPath(@NotNull String outputDir, @NotNull String filename)
    {
        //This is generally safe, but may fail if the appropriate filesystem providers are not registered.
        return findOutputFile(outputDir, filename).toPath();
    }

    /**
     * @return a parameter parser object for writing parameters to a file.
     */
    ParamParser createParamParser();

    /**
     * @return name-value map of the BioML parameters.
     */
    Map<String, String> getParameters();

    /**
     * @return the parameters input file used to drive the pipeline.
     */
    @Nullable
    @Deprecated //Use Path based versions
    File getParametersFile();

    /**
     * @return the job info file used to provide the external executable or script task with input file context.
     */
    @Nullable
    @Deprecated //Use Path based versions
    File getJobInfoFile();

    /**
     * @return a list of all input files analyzed.
     */
    @Deprecated
    List<File> getInputFiles();


    /**
     * @return the parameters input file used to drive the pipeline.
     */
    @Nullable
    default Path getParametersFilePath()
    {
        //Implemented as such for backwards compatibility
        return getParametersFile() == null ? null : getParametersFile().toPath();
    }

    /**
     * @return the job info file used to provide the external executable or script task with input file context.
     */
    @Nullable
    default Path getJobInfoFilePath()
    {
        //Implemented as such for backwards compatibility
        return getJobInfoFile() == null? null : getJobInfoFile().toPath();
    }

    default List<Path> getInputFilePaths()
    {
        //Implemented as such for backwards compatibility
        return getInputFiles().stream().map(File::toPath).collect(Collectors.toList());
    }

    /**
     * returns support level for .xml.gz handling:
     * SUPPORT_GZ or PREFER_GZ
     * we always read .xml.gz, but may also have a
     * preference for producing it in the pipeline
     */
    FileType.gzSupportLevel getGZPreference();

}
