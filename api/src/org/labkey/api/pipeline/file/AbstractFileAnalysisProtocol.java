/*
 * Copyright (c) 2006-2017 LabKey Corporation
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
import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.pipeline.ParamParser;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineProtocol;
import org.labkey.api.util.FileType;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.api.writer.PrintWriters;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * <code>AbstractFileAnalysisProtocol</code>
 */
public abstract class AbstractFileAnalysisProtocol<JOB extends AbstractFileAnalysisJob>
        extends PipelineProtocol
{
    private static Logger _log = Logger.getLogger(AbstractFileAnalysisProtocol.class);

    public static final String LEGACY_JOINED_BASENAME = "all";

    protected String description;
    protected String xml;

    protected String email;

    public AbstractFileAnalysisProtocol(String name, String description, String xml)
    {
        super(name);
        
        this.description = description;
        setXml(xml);
    }

    public String getDescription()
    {
        return description;
    }

    public void setDescription(String description)
    {
        this.description = description;
    }

    public String getXml()
    {
        return xml;
    }

    /**
     * The xml string has bad formatting and extra whitespace from having had some parameters stripped after being read from file.
     * Fix it for redisplay.
     * @param xml The raw xml read from file
     */
    public void setXml(String xml)
    {
        try
        {
            BufferedReader reader = new BufferedReader(new StringReader(xml));
            StringBuilder stripped = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null)
                stripped.append(line.trim());
            DocumentBuilder db = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            DOMSource xmlInput = new DOMSource(db.parse(new InputSource(new StringReader(stripped.toString()))));
            StreamResult xmlOutput = new StreamResult(new StringWriter());
            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
            transformer.transform(xmlInput, xmlOutput);
            this.xml = xmlOutput.getWriter().toString();
        }
        catch (Exception e)
        {
            // This shouldn't happen; bad input xml would have been detected upstream of here.
            throw new RuntimeException("Invalid xml format", e);
        }
    }

    public String getEmail()
    {
        return email;
    }

    public void setEmail(String email)
    {
        this.email = email;
    }

    /**
     * Get the base name used to construct files names for multi-file inputs and outputs.
     * The default base name is the protocol's name except for AbstractMS2SearchProtocol which defaults to "all".
     */
    public String getJoinedBaseName()
    {
        return getName();
    }

    public String getBaseName(File file)
    {
        FileType ft = findInputType(file);
        if (ft == null)
            return file.getName();

        return ft.getBaseName(file);
    }

    public File getAnalysisDir(File dirData, PipeRoot root)
    {
        return getFactory().getAnalysisDir(dirData, getName(), root);
    }

    public File getParametersFile(File dirData, PipeRoot root)
    {
        return getFactory().getParametersFile(dirData, getName(), root);
    }

    @Override
    public void saveDefinition(PipeRoot root) throws IOException
    {
        save(getFactory().getProtocolFile(root, getName(), false), null, null);
    }

    public void saveInstance(File file, Container c) throws IOException
    {
        Map<String, String> addParams = new HashMap<>();
        addParams.put(PipelineJob.PIPELINE_EMAIL_ADDRESS_PARAM, email);
        save(file, null, addParams);
    }

    protected void save(File file, Map<String, String> addParams, Map<String, String> instanceParams) throws IOException
    {
        if (xml == null || xml.length() == 0)
        {
            xml = "<?xml version=\"1.0\"?>\n" +
                    "<bioml>\n" +
                    "</bioml>";
        }

        ParamParser parser = getFactory().createParamParser();
        parser.parse(new ReaderInputStream(new StringReader(xml)));
        if (parser.getErrors() != null)
        {
            ParamParser.Error err = parser.getErrors()[0];
            if (err.getLine() == 0)
                throw new IllegalArgumentException(err.getMessage());
            else
                throw new IllegalArgumentException("Line " + err.getLine() + ": " + err.getMessage());
        }

        File dir = file.getParentFile();
        if (!dir.exists() && !dir.mkdirs())
            throw new IOException("Failed to create directory '" + dir + "'.");

        parser.setInputParameter(PipelineJob.PIPELINE_PROTOCOL_NAME_PARAM, getName());
        parser.setInputParameter(PipelineJob.PIPELINE_PROTOCOL_DESCRIPTION_PARAM, getDescription());

        if (addParams != null)
        {
            for (Map.Entry<String, String> entry : addParams.entrySet())
                parser.setInputParameter(entry.getKey(), entry.getValue());
        }
        if (instanceParams != null)
        {
            for (Map.Entry<String, String> entry : instanceParams.entrySet())
                parser.setInputParameter(entry.getKey(), entry.getValue());
        }

        try (PrintWriter writer = PrintWriters.getPrintWriter(file))
        {
            xml = parser.getXML();
            if (xml == null)
                throw new IOException("Error writing input XML.");
            writer.write(xml, 0, xml.length());
        }
        catch (IOException eio)
        {
            _log.error("Error writing input XML.", eio);
            throw eio;
        }
    }

    public FileType findInputType(File file)
    {
        for (FileType type : getInputTypes())
        {
            if (type.isType(file))
                return type;
        }
        return null;
    }

    public abstract List<FileType> getInputTypes();
    
    public abstract AbstractFileAnalysisProtocolFactory getFactory();

    public abstract JOB createPipelineJob(ViewBackgroundInfo info,
                                          PipeRoot root, List<File> filesInput,
                                          File fileParameters, @Nullable Map<String, String> variableMap) throws IOException;
}
