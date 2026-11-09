/*
 * Copyright (c) 2008-2026 LabKey Corporation
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

import org.apache.commons.io.input.ReaderInputStream;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.pipeline.ParamParser;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.pipeline.PipelineProtocolFactory;
import org.labkey.api.reader.Readers;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.NetworkDrive;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.logging.LogHelper;
import org.labkey.api.writer.PrintWriters;
import org.labkey.vfs.FileLike;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.StringReader;
import java.nio.file.InvalidPathException;

/**
 * Base class for protocol factories that are primarily focused on analyzing data files (as opposed to other types of resources)
 */
abstract public class AbstractFileAnalysisProtocolFactory<T extends AbstractFileAnalysisProtocol<?>> extends PipelineProtocolFactory<T>
{
    private static final Logger _log = LogHelper.getLogger(AbstractFileAnalysisProtocolFactory.class, "Pipeline protocol and parameter errors");

    public static final String DEFAULT_PARAMETERS_NAME = "default";

    /**
     * Get the file name used for parameter files in analysis directories.
     *
     * @return file name
     */
    public String getParametersFileName()
    {
        return getName() + ".xml";
    }

    /**
     * Get the file name for the default parameters for all protocols of this type.
     * 
     * @return file name
     */
    public String getDefaultParametersFileName()
    {
        return DEFAULT_PARAMETERS_NAME + ".xml";
    }

    /**
     * Get the file name for the old default parameters for all protocols of this type,
     * back when these files were stored in the root.
     *
     * @return file name
     */
    public String getLegacyDefaultParametersFileName()
    {
        return getName() + "_default_input.xml";
    }

    /**
     * Get the analysis directory location, given a directory containing the mass spec data.
     *
     * @param dirData mass spec data directory
     * @param protocolName name of protocol for analysis
     * @param root pipeline root under which the files are stored
     * @return analysis directory
     */
    public FileLike getAnalysisDir(FileLike dirData, String protocolName, PipeRoot root)
    {
        FileLike defaultFile = dirData.resolveChild(getName()).resolveChild(protocolName);
        // Check if the pipeline root wants us to write somewhere else, because the source file might be in a read-only
        // pipeline location
        String relativePath = root.relativePath(defaultFile);
        return root.resolvePathToFileLike(relativePath);
    }

    /**
     * Returns true if the file uses the type of protocol created by this factory.
     */
    public boolean isProtocolTypeFile(File file)
    {
        return NetworkDrive.exists(new File(file.getParent(), getParametersFileName()));
    }

    /**
     * Get the parameters file location, given a directory containing the input files to the job.
     *
     * @param dirData input data directory
     * @param protocolName name of protocol for analysis
     * @param root pipeline root under which the files are stored
     * @return parameters file
     */
    @Nullable
    public FileLike getParametersFile(@Nullable FileLike dirData, String protocolName, PipeRoot root)
    {
        if (dirData == null)
        {
            return null;
        }
        FileLike defaultFile = getAnalysisDir(dirData, protocolName, root).resolveChild(getParametersFileName());
        // Check if the pipeline root wants us to write somewhere else, because the source file might be in a read-only
        // pipeline location
        String relativePath = root.relativePath(defaultFile);
        return root.resolvePathToFileLike(relativePath);
    }

    /**
     * Get the default parameters file, given the pipeline root directory.
     *
     * @param root pipeline root directory
     * @return default parameters file
     */
    public FileLike getDefaultParametersFile(PipeRoot root)
    {
        return getProtocolDir(root, false).resolveChild(getDefaultParametersFileName());
    }

    /**
     * Make sure default parameters for this protocol type exist.
     *
     * @param root pipeline root
     */
    public void ensureDefaultParameters(PipeRoot root) throws IOException
    {
        if (!NetworkDrive.exists(getDefaultParametersFile(root)))
            setDefaultParametersXML(root, getDefaultParametersXML(root));
    }

    @Override
    public String[] getProtocolNames(PipeRoot root, FileLike dirData, boolean archived)
    {
        String[] protocolNames = super.getProtocolNames(root, dirData, archived);

        // The default parameters file is not really a protocol so remove it from the list.
        return ArrayUtils.removeElement(protocolNames, DEFAULT_PARAMETERS_NAME);
    }

    /**
     * Override to set a custom validator.
     *
     * @return a parser for working with a parameter stream
     */
    public ParamParser createParamParser()
    {
        return PipelineJobService.get().createParamParser();
    }

    public abstract T createProtocolInstance(String name, String description, String xml, Container container);

    protected T createProtocolInstance(ParamParser parser, Container container)
    {
        // Remove the pipeline specific parameters.
        String name = parser.removeInputParameter(PipelineJob.PIPELINE_PROTOCOL_NAME_PARAM);
        String description = parser.removeInputParameter(PipelineJob.PIPELINE_PROTOCOL_DESCRIPTION_PARAM);
        String folder = parser.removeInputParameter(PipelineJob.PIPELINE_LOAD_FOLDER_PARAM);
        String email = parser.removeInputParameter(PipelineJob.PIPELINE_EMAIL_ADDRESS_PARAM);

        T instance = createProtocolInstance(name, description, parser.getXML(), container);

        instance.setEmail(email);

        return instance;
    }

    @Override
    public T load(PipeRoot root, String name, boolean archived) throws IOException
    {
        T instance = loadInstance(getProtocolFile(root, name, archived), root.getContainer());

        // Don't allow the XML to override the name passed in.  This
        // can be extremely confusing.
        instance.setName(name);
        return instance;
    }

    public T loadInstance(FileLike file, Container container) throws IOException
    {
        ParamParser parser = createParamParser();
        try (InputStream is = file.openInputStream())
        {
            parser.parse(is);
            if (parser.getErrors() != null)
            {
                ParamParser.Error err = parser.getErrors()[0];
                if (err.getLine() == 0)
                {
                    throw new IOException("Failed parsing input parameters '" + file + "'.\n" +
                            err.getMessage());
                }
                else
                {
                    throw new IOException("Failed parsing input parameters '" + file + "'.\n" +
                            "Line " + err.getLine() + ": " + err.getMessage());
                }
            }

            return createProtocolInstance(parser, container);
        }
    }

    public String getDefaultParametersXML(PipeRoot root) throws IOException
    {
        FileLike fileDefault = getDefaultParametersFile(root);
        if (!fileDefault.exists())
            return null;

        return new FileDefaultsReader(fileDefault).readXML();
    }

    protected static class FileDefaultsReader extends DefaultsReader
    {
        private final FileLike _fileDefaults;

        public FileDefaultsReader(FileLike fileDefaults)
        {
            _fileDefaults = fileDefaults;
        }

        @Override
        public Reader createReader() throws IOException
        {
            return Readers.getReader(_fileDefaults.openInputStream());
        }
    }
    
    abstract protected static class DefaultsReader
    {
        abstract public Reader createReader() throws IOException;

        public String readXML() throws IOException
        {
            try (BufferedReader reader = new BufferedReader(createReader()))
            {
                return PageFlowUtil.getReaderContentsAsString(reader);
            }
            catch (FileNotFoundException enf)
            {
                _log.error("Default parameters file missing. Check product setup.", enf);
                throw enf;
            }
            catch (IOException eio)
            {
                _log.error("Error reading default parameters file.", eio);
                throw eio;
            }
        }
    }

    public void setDefaultParametersXML(PipeRoot root, String xml) throws IOException
    {
        if (xml == null || xml.isEmpty())
            throw new IllegalArgumentException("You must supply default parameters for " + getName() + ".");

        ParamParser parser = createParamParser();
        parser.parse(new ReaderInputStream(new StringReader(xml)));
        if (parser.getErrors() != null)
        {
            ParamParser.Error err = parser.getErrors()[0];
            if (err.getLine() == 0)
                throw new IllegalArgumentException(err.getMessage());
            else
                throw new IllegalArgumentException("Line " + err.getLine() + ": " + err.getMessage());
        }

        FileLike fileDefault = getDefaultParametersFile(root);
        FileUtil.createDirectories(fileDefault.getParent());

        try (PrintWriter writer = PrintWriters.getPrintWriter(fileDefault.openOutputStream()))
        {
            writer.write(xml, 0, xml.length());
        }
        catch (IOException eio)
        {
            _log.error("Error writing default parameters file.", eio);
            throw eio;
        }
    }

    @Nullable
    public AbstractFileAnalysisProtocol<?> getProtocol(PipeRoot root, FileLike dirData, String protocolName, boolean archived)
    {
        try
        {
            FileLike protocolFile = getParametersFile(dirData, protocolName, root);
            AbstractFileAnalysisProtocol<?> result;
            if (NetworkDrive.exists(protocolFile))
            {
                result = loadInstance(protocolFile, root.getContainer());

                // Don't allow the instance file to override the protocol name.
                result.setName(protocolName);
            }
            else
            {
                try
                {
                    protocolFile = getProtocolFile(root, protocolName, archived);
                    if (protocolFile == null || !protocolFile.exists())
                        return null;
                }
                catch (InvalidPathException e)
                {
                    return null;
                }

                result = load(root, protocolName, archived);
            }
            return result;
        }
        catch (IOException|InvalidPathException e)
        {
            _log.warn("Error loading protocol file.", e);
            return null;
        }
    }

}
