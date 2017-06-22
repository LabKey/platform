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
package org.labkey.api.pipeline.cmd;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.util.FileType;
import org.labkey.api.pipeline.file.FileAnalysisJobSupport;

/**
 * <code>TaskPath</code> is an abstract specifier of how an input/output file
 * is to be handled during pipeline processing.
 */
public class TaskPath
{
    private FileType _type;
    private String _name;
    private boolean _splitFiles;
    private boolean _copyInput;
    private boolean _optional;
    private OutputLocation _outputLocation = OutputLocation.DEFAULT;
    private String _outputDir;
    private boolean _useProtocolNameAsOutputBaseName;
    private boolean _useFileTypeBaseName;

    /**
     * Default bean constructor.
     */
    public TaskPath()
    {
    }

    /**
     * Constructor for a file-type derived from the basename for the initial
     * file or directory.
     *
     * @param type Type to be appended to the basename
     */
    public TaskPath(FileType type)
    {
        _type = type;
    }

    /**
     * Short-cut for the file-type constructor which accepts a string extension.
     *
     * @param ext File extension string used to construct a <code>FileType</code>
     */
    public TaskPath(String ext)
    {
        _type = new FileType(ext);
    }

    /**
     * Gets a file-type that may be combined with the basename of the initial
     * file or directory to get the input/output file of interest.  If this is null,
     * then the "name" property must be specified.
     *
     * @return Type to be appended to the basename
     */
    public FileType getType()
    {
        return _type;
    }

    /**
     * Sets a file-type that may be combined with the basename of the initial
     * file or directory to get the input/output file of interest.  If this is null,
     * then the "name" property must be specified.
     *
     * @param type Type to be appended to the basename
     */
    public void setType(FileType type)
    {
        _type = type;
    }

    /**
     * Short-cut for the file-type setting which accepts a string extension.
     *
     * @param ext File extension string used to construct a <code>FileType</code>
     */
    public void setExtension(String ext)
    {
        _type = new FileType(ext);
    }

    /**
     * Gets a full filename used as an input/output.  The "extension" property
     * must be null for this to be used.
     *
     * @return A full filename
     */
    public String getName()
    {
        return _name;
    }

    /**
     * Sets a full filename used as an input/output.  The "extension" property
     * must be null for this to be used.
     *
     * @param name A full filename
     */
    public void setName(String name)
    {
        _name = name;
    }

    public String getDefaultRole()
    {
        if (getType() != null)
        {
            return getType().getDefaultRole();
        }

        return getName();
    }

    /**
     * Used to determine whether files generated from this path represent
     * the set of split files (all input files), or just the current file
     * being processed.
     *
     * @return True if path represents full set of split files
     */
    public boolean isSplitFiles()
    {
        return _splitFiles;
    }

    /**
     * Set whether files generated from this path represent
     * the set of split files (all input files), or just the current file
     * being processed.
     *  
     * @param splitFiles True if path represents full set of split files
     */
    public void setSplitFiles(boolean splitFiles)
    {
        _splitFiles = splitFiles;
    }

    /**
     * @return True if this file must be copied to the work directory on input
     */
    public boolean isCopyInput()
    {
        return _copyInput;
    }

    /**
     * Specify whether the input file specified must be copied to the work directory.
     * This is useful in cases where the program run cannot handle path names, but
     * requires the file to be in the current directory.
     * 
     * @param copyInput True if this file must be copied to the work directory on input
     */
    public void setCopyInput(boolean copyInput)
    {
        _copyInput = copyInput;
    }

    /**
     * @return True if this file is optional, and not required for successful processing
     */
    public boolean isOptional()
    {
        return _optional;
    }

    /**
     * Specify whether the file specified (usually input) must be must be present for
     * successful processing.  This is useful for cache files which, if present, will
     * decrease processing time, but with may also be generated from other inputs, if
     * necessary.
     *
     * @param optional True if this file is optional
     */
    public void setOptional(boolean optional)
    {
        _optional = optional;
    }

    /**
     * Indicates that the output file should always be copied directly to the analysis directory, instead of relying
     * on the normal job behavior for where to put it.
     * @deprecated Use .getOutputLocation() instead.
     */
    @Deprecated
    public boolean isForceToAnalysisDir()
    {
        return _outputLocation == OutputLocation.ANALYSIS_DIR;
    }

    /** @deprecated Use .setOutputLocation(ANALYSIS_DIR) instead. */
    @Deprecated
    public void setForceToAnalysisDir(boolean forceToAnalysisDir)
    {
        if (forceToAnalysisDir)
        {
            _outputLocation = OutputLocation.ANALYSIS_DIR;
            _outputDir = null;
        }
        else
        {
            _outputLocation = OutputLocation.DEFAULT;
            _outputDir = null;
        }
    }

    /**
     * Set the {@link OutputLocation} for this TaskPath.
     * The location to place this output file after the task is successfully completed.
     */
    public void setOutputLocation(@NotNull OutputLocation loc)
    {
        _outputLocation = loc;
    }

    /**
     * Get the {@link OutputLocation} for this TaskPath.
     * The location to place this output file after the task is successfully completed.
     */
    @NotNull
    public OutputLocation getOutputLocation()
    {
        return _outputLocation;
    }

    /**
     * Set the output directory for this TaskPath.
     * The output dir is a directory path relative to the analysis directory,
     * or, if the path starts with "/", relative to the pipeline root.
     */
    public void setOutputDir(@NotNull String dir)
    {
        _outputLocation = OutputLocation.PATH;
        _outputDir = dir;
    }

    /**
     * Get the output directory for this TaskPath.
     */
    @Nullable
    public String getOutputDir()
    {
        if (_outputLocation == OutputLocation.PATH)
            return _outputDir;
        else
            return null;
    }

    /**
     * Indicates that the base name for this input or output file will use the protocol's name
     * instead of the input file's base name.
     * NOTE: This is experimental and may be change or removed from future releases without warning.
     *
     * @return true if the base name for this input or output file should use the protocol's name.
     */
    public boolean isUseProtocolNameAsBaseName()
    {
        return _useProtocolNameAsOutputBaseName;
    }

    public void setUseProtocolNameAsOutputBaseName(boolean useProtocolNameAsOutputBaseName)
    {
        _useProtocolNameAsOutputBaseName = useProtocolNameAsOutputBaseName;
    }

    /**
     * Indicates that the base name for this input or output file will get the base name based on the file type.
     * @return true if the base name for this input or output file should be based on the file type
     */
    public boolean isUseFileTypeBaseName()
    {
        return _useFileTypeBaseName;
    }

    public void setUseFileTypeBaseName(boolean useFileTypeBaseName)
    {
        _useFileTypeBaseName = useFileTypeBaseName;
    }

    public enum OutputLocation
    {
        /** The default output location as retured by {@link FileAnalysisJobSupport#findOutputFile(String)}. */
        DEFAULT,

        /**
         * The current job's analysis directory where the final analysis usually ends up.
         * @see FileAnalysisJobSupport#getAnalysisDirectory()
         */
        ANALYSIS_DIR,

        /**
         * The current job's data directory where the original input files reside.
         * @see FileAnalysisJobSupport#getDataDirectory()
         */
        DATA_DIR,

        /**
         * Use the output directory named by {@link #getOutputDir()}.
         */
        PATH
    }
}
