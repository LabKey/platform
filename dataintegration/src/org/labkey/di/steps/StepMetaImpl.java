/*
 * Copyright (c) 2013 LabKey Corporation
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
package org.labkey.di.steps;

import org.apache.xmlbeans.XmlException;
import org.labkey.api.etl.CopyConfig;
import org.labkey.api.query.SchemaKey;
import org.labkey.etl.xml.SchemaQueryType;
import org.labkey.etl.xml.TransformType;

/**
 * User: tgaluhn
 * Date: 10/8/13
 */
public abstract class StepMetaImpl extends CopyConfig implements StepMeta
{
    protected String description;
    protected StepProvider provider;

    // errors
    static final String INVALID_TARGET_OPTION = "Invalid targetOption attribute value specified";
    static final String INVALID_SOURCE_OPTION = "Invalid sourceOption attribute value specified";
    static final String INVALID_SOURCE = "No source element specified.";
    static final String INVALID_DESTINATION = "No destination element specified.";
    static final String INVALID_PROCEDURE = "No procedure element specified.";

    @Override
    public StepProvider getProvider()
    {
        return provider;
    }

    @Override
    public void setProvider(StepProvider provider)
    {
        this.provider = provider;
    }

    @Override
    public String getDescription()
    {
        return description;
    }

    @Override
    public void setDescription(String description)
    {
        this.description = description;
    }

    @Override
    public void parseConfig(TransformType transformXML) throws XmlException
    {
        setId(transformXML.getId());
        if (null != transformXML.getDescription())
        {
            setDescription(transformXML.getDescription());
        }
        parseWorkOptions(transformXML);
    }

    protected void parseWorkOptions(TransformType transformXML) throws XmlException
    {
        parseSource(transformXML);
        parseDestination(transformXML);
    }

    protected void parseSource(TransformType transformXML) throws XmlException
    {
        SchemaQueryType source = transformXML.getSource();

        if (null != source)
        {
            setSourceSchema(SchemaKey.fromString(source.getSchemaName()));
            setSourceQuery(source.getQueryName());
            if (null != source.getTimestampColumnName())
                setSourceTimestampColumnName(source.getTimestampColumnName());
            if (null != source.getSourceOption())
            {
                try
                {
                    setSourceOptions(CopyConfig.SourceOptions.valueOf(source.getSourceOption().toString()));
                }
                catch (IllegalArgumentException x)
                {
                    throw new XmlException(INVALID_SOURCE_OPTION);
                }
            }
        }
    }

    protected void parseDestination(TransformType transformXML) throws XmlException
    {
        SchemaQueryType destination = transformXML.getDestination();

        if (null != destination)
        {
            setTargetSchema(SchemaKey.fromString(destination.getSchemaName()));
            setTargetQuery(destination.getQueryName());
            if (null != destination.getTargetOption())
            {
                try
                {
                    setTargetOptions(CopyConfig.TargetOptions.valueOf(destination.getTargetOption().toString()));
                }
                catch (IllegalArgumentException x)
                {
                    throw new XmlException(INVALID_TARGET_OPTION);
                }
            }
        }
    }
}
