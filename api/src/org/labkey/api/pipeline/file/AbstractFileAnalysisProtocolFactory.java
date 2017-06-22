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

import org.apache.commons.io.input.ReaderInputStream;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.pipeline.ParamParser;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.pipeline.PipelineProtocolFactory;
import org.labkey.api.pipeline.PipelineProvider;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.TaskPipeline;
import org.labkey.api.reader.Readers;
import org.labkey.api.util.NetworkDrive;
import org.labkey.api.util.PageFlowUtil;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.List;

/**
 * Base class for protocol factories that are primarily focused on analyzing data files (as opposed to other types of resources)
 */
abstract public class AbstractFileAnalysisProtocolFactory<T extends AbstractFileAnalysisProtocol> extends PipelineProtocolFactory<T>
{
    private static Logger _log = Logger.getLogger(AbstractFileAnalysisProtocolFactory.class);

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
    public File getAnalysisDir(File dirData, String protocolName, PipeRoot root)
    {
        File defaultFile = new File(new File(dirData, getName()), protocolName);
        // Check if the pipeline root wants us to write somewhere else, because the source file might be in a read-only
        // pipeline location
        String relativePath = root.relativePath(defaultFile);
        return root.resolvePath(relativePath);
    }

    /**
     * Returns true if the file uses the type of protocol created by this factory.
     */
    public boolean isProtocolTypeFile(File file)
    {
        return NetworkDrive.exists(new File(file.getParent(), getParametersFileName()));
    }

    /**
     * Get the parameters file location, given a directory containing the mass spec data.
     *
     * @param dirData mass spec data directory
     * @param protocolName name of protocol for analysis
     * @param root pipeline root under which the files are stored
     * @return parameters file
     */
    @Nullable
    public File getParametersFile(@Nullable File dirData, String protocolName, PipeRoot root)
    {
        if (dirData == null)
        {
            return null;
        }
        File defaultFile = new File(getAnalysisDir(dirData, protocolName, root), getParametersFileName());
        // Check if the pipeline root wants us to write somewhere else, because the source file might be in a read-only
        // pipeline location
        String relativePath = root.relativePath(defaultFile);
        return root.resolvePath(relativePath);
    }

    /**
     * Get the default parameters file, given the pipeline root directory.
     *
     * @param root pipeline root directory
     * @return default parameters file
     */
    public File getDefaultParametersFile(PipeRoot root)
    {
        return new File(getProtocolDir(root, false), getDefaultParametersFileName());
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
    public String[] getProtocolNames(PipeRoot root, File dirData, boolean archived)
    {
        String[] protocolNames = super.getProtocolNames(root, dirData, archived);

        // The default parameters file is not really a protocol so remove it from the list.
        return ArrayUtils.removeElement(protocolNames, DEFAULT_PARAMETERS_NAME);
    }

    public void initSystemDirectory(File rootDir, File systemDir)
    {
        // Make sure the root protocol directory is in the right place.
        File protocolRootDir = locateProtocolRootDir(rootDir, systemDir);

        // Make sure the defaults for this particular protocol are in the right place.
        File fileLegacyDefaults = new File(rootDir, getLegacyDefaultParametersFileName());
        if (NetworkDrive.exists(fileLegacyDefaults))
        {
            File protocolDir = new File(protocolRootDir, getName());
            fileLegacyDefaults.renameTo(new File(protocolDir, getDefaultParametersFileName()));
        }
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

    public abstract T createProtocolInstance(String name, String description, String xml);

    protected T createProtocolInstance(ParamParser parser)
    {
        // Remove the pipeline specific parameters.
        String name = parser.removeInputParameter(PipelineJob.PIPELINE_PROTOCOL_NAME_PARAM);
        String description = parser.removeInputParameter(PipelineJob.PIPELINE_PROTOCOL_DESCRIPTION_PARAM);
        String folder = parser.removeInputParameter(PipelineJob.PIPELINE_LOAD_FOLDER_PARAM);
        String email = parser.removeInputParameter(PipelineJob.PIPELINE_EMAIL_ADDRESS_PARAM);

        T instance = createProtocolInstance(name, description, parser.getXML());

        instance.setEmail(email);

        return instance;
    }

    public T load(PipeRoot root, String name, boolean archived) throws IOException
    {
        T instance = loadInstance(getProtocolFile(root, name, archived));

        // Don't allow the XML to override the name passed in.  This
        // can be extremely confusing.
        instance.setName(name);
        return instance;
    }

    public T loadInstance(File file) throws IOException
    {
        ParamParser parser = createParamParser();
        parser.parse(new FileInputStream(file));
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

        return createProtocolInstance(parser);
    }

    public String getDefaultParametersXML(PipeRoot root) throws IOException
    {
        File fileDefault = getDefaultParametersFile(root);
        if (!fileDefault.exists())
            return null;

        return new FileDefaultsReader(fileDefault).readXML();
    }

    protected class FileDefaultsReader extends DefaultsReader
    {
        private final File _fileDefaults;

        public FileDefaultsReader(File fileDefaults)
        {
            _fileDefaults = fileDefaults;
        }

        public Reader createReader() throws IOException
        {
            return Readers.getReader(_fileDefaults);
        }
    }
    
    abstract protected class DefaultsReader
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
        if (xml == null || xml.length() == 0)
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

        File fileDefault = getDefaultParametersFile(root);
        fileDefault.getParentFile().mkdirs();

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(fileDefault)))
        {
            writer.write(xml, 0, xml.length());
        }
        catch (IOException eio)
        {
            _log.error("Error writing default parameters file.", eio);
            throw eio;
        }
    }

    public static <T extends AbstractFileAnalysisProvider<F, TaskPipeline>, F extends AbstractFileAnalysisProtocolFactory>
            F fromFile(Class<T> clazz, File file)
    {
        List<PipelineProvider> providers = PipelineService.get().getPipelineProviders();
        for (PipelineProvider provider : providers)
        {
            if (!(clazz.isInstance(provider)))
                continue;

            T mprovider = (T) provider;
            F factory = mprovider.getProtocolFactory(file);
            if (factory != null)
                return factory;
        }

        // TODO: Return some default?
        return null;
    }

    @Nullable
    public AbstractFileAnalysisProtocol getProtocol(PipeRoot root, File dirData, String protocolName, boolean archived)
    {
        try
        {
            File protocolFile = getParametersFile(dirData, protocolName, root);
            AbstractFileAnalysisProtocol result;
            if (NetworkDrive.exists(protocolFile))
            {
                result = loadInstance(protocolFile);

                // Don't allow the instance file to override the protocol name.
                result.setName(protocolName);
            }
            else
            {
                protocolFile = getProtocolFile(root, protocolName, archived);
                if (protocolFile == null || !protocolFile.exists())
                    return null;

                result = load(root, protocolName, archived);
            }
            return result;
        }
        catch (IOException e)
        {
            _log.warn("Error loading protocol file.", e);
            return null;
        }
    }

}
