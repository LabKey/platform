/*
 * Copyright (c) 2008-2018 LabKey Corporation
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
import org.labkey.api.data.Container;
import org.labkey.api.formSchema.FormSchema;
import org.labkey.api.pipeline.PipelineActionConfig;
import org.labkey.api.pipeline.TaskPipeline;
import org.labkey.api.util.FileType;
import org.labkey.api.util.URLHelper;

import java.io.File;
import java.io.FileFilter;
import java.nio.file.DirectoryStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * <code>FileAnalysisTaskPipeline</code>
 * A filter interface that implements both the io.FileFilter and nio.DirectoryStream.Filter interfaces
 */
public interface FileAnalysisTaskPipeline extends TaskPipeline<FileAnalysisTaskPipelineSettings>
{
    interface FilePathFilter extends FileFilter, DirectoryStream.Filter<Path>
    {
        @Override
        boolean accept(File file);

        @Override
        boolean accept(Path path);
    }


    /**
     * Returns the name of the protocol factory for this pipeline, which
     * will be used as the root directory name for all analyses of this type
     * and the directory name of the saved system protocol XML files.
     *
     * @return the name of the protocol factory
     */
    String getProtocolFactoryName();

    /**
     * Returns the full list of acceptable file types that can be used to
     * start this pipeline.
     *
     * @return list containing acceptable initial file types
     */
    @NotNull
    List<FileType> getInitialFileTypes();

    /**
     * Returns a <code>FileFilter</code> for use in creating an input file set.
     *
     * @return filter for input files
     */
    @NotNull
    FilePathFilter getInitialFileTypeFilter();

    @NotNull
    URLHelper getAnalyzeURL(Container c, String path);

    @NotNull
    Map<FileType, List<FileType>> getTypeHierarchy();

    @Nullable
    PipelineActionConfig.displayState getDefaultDisplayState();

    boolean isAllowForTriggerConfiguration();

    /**
     * Write out the job info as a tsv file similar to the R transformation runProperties format.
     * This is a info file for an entire job (or split job) that command line or script tasks may use
     * to determine the inputs files and other job related metadata.
     *
     * @see org.labkey.api.pipeline.file.AbstractFileAnalysisJob#writeJobInfoTSV(java.io.File)
     * @see org.labkey.api.qc.TsvDataExchangeHandler
     * @link https://www.labkey.org/Documentation/wiki-page.view?name=runProperties
     * @return
     */
    boolean isWriteJobInfoFile();

    /**
     * Allow the job to be split if there are multiple file inputs.
     * @return
     */
    boolean isSplittable();

    String getHelpText();

    Boolean isMoveAvailable();

    Boolean isInitialFileTypesRequired();

    FormSchema getFormSchema();

    FormSchema getCustomFieldsFormSchema();
}
