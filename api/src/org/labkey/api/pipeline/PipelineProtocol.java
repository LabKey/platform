/*
 * Copyright (c) 2005-2017 LabKey Corporation
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
package org.labkey.api.pipeline;

import org.apache.commons.beanutils.PropertyUtils;
import org.apache.xmlbeans.XmlOptions;
import org.fhcrc.cpas.pipeline.protocol.xml.PipelineProtocolPropsDocument;
import org.labkey.api.util.NetworkDrive;

import java.io.IOException;
import java.io.File;
import java.beans.PropertyDescriptor;
import java.util.Map;
import java.util.HashMap;
import java.lang.reflect.InvocationTargetException;

/**
 * A protocol captures settings to be used when running a pipeline. Values are often passed to tools
 * being invoked on the command line or similar.
 * Created: Oct 7, 2005
 * @author bmaclean
 */
public abstract class PipelineProtocol
{
    public static final String _xmlNamespace = "http://cpas.fhcrc.org/pipeline/protocol/xml";

    private String name;
    private String template;

    public PipelineProtocol()
    {
    }

    public PipelineProtocol(String name)
    {
        this.name = name;
    }

    public String getName()
    {
        return name;
    }

    public void setName(String name)
    {
        this.name = name;
    }

    public abstract PipelineProtocolFactory getFactory();

    public void validateToSave(PipeRoot root, boolean validateName, boolean abortOnExists) throws PipelineValidationException
    {
        if (validateName)
        {
            validate(root);
        }

        if (getFactory().exists(root, name, false))
        {
            if (abortOnExists)
            {
                throw new PipelineValidationException("A protocol named '" + name + "' already exists.");
            }
        }
        else if (getFactory().exists(root, name, true))
        {
            throw new PipelineValidationException("An archived protocol named '" + name + "' already exists.");
        }
    }
    
    public void validate(PipeRoot root) throws PipelineValidationException
    {
        validateProtocolName();
    }

    protected void validateProtocolName() throws PipelineValidationException
    {
        if (name == null || name.trim().length() == 0)
            throw new PipelineValidationException("Missing protocol name.");
        else if (!getFactory().isValidProtocolName(name))
            throw new PipelineValidationException("The name '" + name + "' is not a valid protocol name.");
    }

    public File getDefinitionFile(PipeRoot root)
    {
        return getFactory().getProtocolFile(root, name, false);
    }

    public void saveDefinition(PipeRoot root) throws IOException
    {
        save(getDefinitionFile(root));
    }

    public void setProperty(String propertyName, String value) throws NoSuchMethodException, IllegalAccessException, InvocationTargetException
    {
        PropertyUtils.setProperty(this, propertyName, value);
    }

    /**
     * Method that returns properties to be saved in the properties document representing
     * this protocol. This method is used by the default save method to identify what should be
     * saved in the protocol document.
     * @return Map of properties to be saved by the default save routine.
     */
    protected Map<String, String> getSaveProperties()
    {
        PropertyDescriptor props[] = PropertyUtils.getPropertyDescriptors(this);
        Map<String, String> propMap = new HashMap<>();

        for (PropertyDescriptor prop : props)
        {
            String name = prop.getName();
            if ("class".equals(name))
                continue;
            if (!PropertyUtils.isReadable(this, name) ||
                    !PropertyUtils.isWriteable(this, name))
                continue;

            try
            {
                Object value = PropertyUtils.getProperty(this, name);
                if (value != null)
                {
                    propMap.put(name, value.toString());
                }
            }
            catch (Exception e)
            {
            }

        }

        return propMap;
    }

    public void save(File file) throws IOException
    {
        File dir = file.getParentFile();
        if (!dir.exists() && !dir.mkdirs())
        {
            NetworkDrive.ensureDrive(dir.getPath());
            if (!dir.exists() && !dir.mkdirs())
                throw new IOException("Failed to create directory '" + dir + "'.");
        }

        PipelineProtocolPropsDocument doc =
                PipelineProtocolPropsDocument.Factory.newInstance();
        PipelineProtocolPropsDocument.PipelineProtocolProps ppp =
                doc.addNewPipelineProtocolProps();
        ppp.setType(getClass().getName());

        Map<String, String> propMap = getSaveProperties();

        for (Map.Entry<String, String> prop : propMap.entrySet())
        {
                    PipelineProtocolPropsDocument.PipelineProtocolProps.Property p =
                            ppp.addNewProperty();
                    p.setName(prop.getKey());
                    p.setStringValue(prop.getValue());
        }

        if (null != template)
            ppp.setTemplate(template);

            Map<String, String> mapNS = new HashMap<>();
            mapNS.put("", _xmlNamespace);
            XmlOptions opts = new XmlOptions()
                    .setSavePrettyPrint()
                    .setSaveImplicitNamespaces(mapNS);
            doc.save(file, opts);
    }

    public String getTemplate()
    {
        return template;
    }

    public void setTemplate(String template)
    {
        this.template = template;
    }
}
