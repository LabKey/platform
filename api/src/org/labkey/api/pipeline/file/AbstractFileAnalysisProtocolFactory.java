/*
 * Copyright (c) 2008 LabKey Corporation
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

import org.apache.commons.lang.ArrayUtils;
import org.apache.log4j.Logger;
import org.labkey.api.pipeline.*;
import org.labkey.api.util.NetworkDrive;

import java.io.*;
import java.net.URI;
import java.util.List;

/**
 * <code>AbstractMS2SearchProtocolFactory</code>
 */
abstract public class AbstractFileAnalysisProtocolFactory<T extends AbstractFileAnalysisProtocol> extends PipelineProtocolFactory<T>
{
    private static Logger _log = Logger.getLogger(AbstractFileAnalysisProtocolFactory.class);

    public static final String DEFAULT_PARAMETERS_NAME = "default";
    
    /**
     * Get the file name used for MS2 search parameters in analysis directories.
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
     * @return analysis directory
     */
    public File getAnalysisDir(File dirData, String protocolName)
    {
        return new File(new File(dirData, getName()), protocolName);
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
     * @return parameters file
     */
    public File getParametersFile(File dirData, String protocolName)
    {
        return new File(getAnalysisDir(dirData, protocolName), getParametersFileName());
    }

    /**
     * Get the default parameters file, given the pipeline root directory.
     *
     * @param dirRoot pipeline root directory
     * @return default parameters file
     */
    public File getDefaultParametersFile(File dirRoot)
    {
        return new File(getProtocolDir(dirRoot.toURI()), getDefaultParametersFileName());
    }

    /**
     * Make sure default parameters for this protocol type exist.
     *
     * @param dirRoot pipeline root directory
     */
    public void ensureDefaultParameters(File dirRoot) throws IOException
    {
        if (!NetworkDrive.exists(getDefaultParametersFile(dirRoot)))
            setDefaultParametersXML(dirRoot, getDefaultParametersXML(dirRoot));
    }

    public String[] getProtocolNames(URI uriRoot)
    {
        String[] protocolNames = super.getProtocolNames(uriRoot);

        // The default parameters file is not really a protocol so remove it from the list.
        return (String[]) ArrayUtils.removeElement(protocolNames, DEFAULT_PARAMETERS_NAME);
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
        String name = parser.removeInputParameter("pipeline, protocol name");
        String description = parser.removeInputParameter("pipeline, protocol description");
        String folder = parser.removeInputParameter("pipeline, load folder");
        String email = parser.removeInputParameter("pipeline, email address");

        T instance = createProtocolInstance(name, description, parser.getXML());

        instance.setEmail(email);

        return instance;
    }

    public T load(URI uriRoot, String name) throws IOException
    {
        return loadInstance(getProtocolFile(uriRoot, name));
    }

    public T loadInstance(File file) throws IOException
    {
        BufferedReader inputReader = null;
        StringBuffer xmlBuffer = new StringBuffer();
        try
        {
            inputReader = new BufferedReader(new FileReader(file));
            String line;
            while ((line = inputReader.readLine()) != null)
                xmlBuffer.append(line).append("\n");
        }
        catch (IOException eio)
        {
            throw new IOException("Failed to load protocol file '" + file + "'.");
        }
        finally
        {
            if (inputReader != null)
            {
                try
                {
                    inputReader.close();
                }
                catch (IOException eio)
                {
                }
            }
        }

        ParamParser parser = createParamParser();
        parser.parse(xmlBuffer.toString());
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

    public String getDefaultParametersXML(File dirRoot) throws FileNotFoundException, IOException
    {
        File fileDefault = getDefaultParametersFile(dirRoot);
        if (!fileDefault.exists())
            return null;

        return new FileDefaultsReader(fileDefault).readXML();
    }

    protected class FileDefaultsReader extends DefaultsReader
    {
        private File _fileDefaults;

        public FileDefaultsReader(File fileDefaults)
        {
            _fileDefaults = fileDefaults;
        }

        public Reader createReader() throws IOException
        {
            return new FileReader(_fileDefaults);
        }
    }
    
    abstract protected class DefaultsReader
    {
        abstract public Reader createReader() throws IOException;

        public String readXML() throws IOException
        {
            BufferedReader reader = null;
            try
            {
                reader = new BufferedReader(createReader());

                StringBuffer defaults = new StringBuffer();
                String line;
                while ((line = reader.readLine()) != null)
                {
                    defaults.append(line).append("\n");
                }
                return defaults.toString();
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
            finally
            {
                if (reader != null)
                {
                    try
                    {
                        reader.close();
                    }
                    catch (IOException eio)
                    {
                    }
                }
            }            
        }
    }

    public void setDefaultParametersXML(File dirRoot, String xml) throws IOException
    {
        if (xml == null || xml.length() == 0)
            throw new IllegalArgumentException("You must supply default parameters for " + getName() + ".");

        ParamParser parser = createParamParser();
        parser.parse(xml);
        if (parser.getErrors() != null)
        {
            ParamParser.Error err = parser.getErrors()[0];
            if (err.getLine() == 0)
                throw new IllegalArgumentException(err.getMessage());
            else
                throw new IllegalArgumentException("Line " + err.getLine() + ": " + err.getMessage());
        }

        File fileDefault = getDefaultParametersFile(dirRoot);

        BufferedWriter writer = null;
        try
        {
            fileDefault.getParentFile().mkdirs();
            writer = new BufferedWriter(new FileWriter(fileDefault));
            writer.write(xml, 0, xml.length());
        }
        catch (IOException eio)
        {
            _log.error("Error writing default parameters file.", eio);
            throw eio;
        }
        finally
        {
            if (writer != null)
            {
                try
                {
                    writer.close();
                }
                catch (IOException eio)
                {
                    _log.error("Error writing default parameters file.", eio);
                    throw eio;
                }
            }
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
}
