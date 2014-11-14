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
import org.apache.xmlbeans.impl.values.XmlValueOutOfRangeException;
import org.labkey.api.etl.CopyConfig;
import org.labkey.api.query.SchemaKey;
import org.labkey.di.pipeline.TransformManager;
import org.labkey.etl.xml.SchemaQueryType;
import org.labkey.etl.xml.TargetQueryType;
import org.labkey.etl.xml.TransformType;

/**
 * User: tgaluhn
 * Date: 10/8/13
 */
public abstract class StepMetaImpl extends CopyConfig implements StepMeta
{
    protected String description;
    protected StepProvider provider;

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
            try
            {
                if (null != source.getSourceOption())
                   setSourceOptions(CopyConfig.SourceOptions.valueOf(source.getSourceOption().toString()));
            }
            catch (XmlValueOutOfRangeException e)
            {
                throw new XmlException(TransformManager.INVALID_SOURCE_OPTION);
            }
        }
        else
            _useSource = false;
    }

    protected void parseDestination(TransformType transformXML) throws XmlException
    {
        TargetQueryType destination = transformXML.getDestination();

        if (null != destination)
        {
            if (destination.getType() == null || CopyConfig.TargetTypes.query.name().equals(destination.getType().toString()))
            {
                setTargetType(CopyConfig.TargetTypes.query);
                setTargetSchema(SchemaKey.fromString(destination.getSchemaName()));
                setTargetQuery(destination.getQueryName());
            }
            else
            {
                try
                {
                    setTargetType(CopyConfig.TargetTypes.valueOf(destination.getType().toString()));
                }
                catch (XmlValueOutOfRangeException e)
                {
                    throw new XmlException("Bad target type"); // TODO: error messsages in constants
                }

                setTargetPath(destination.getPath());
                setTargetFilePrefix(destination.getPrefix());
                setTargetFileExtension(destination.getExtension());
            }
            try
            {
                if (null != destination.getTargetOption())
                    setTargetOptions(CopyConfig.TargetOptions.valueOf(destination.getTargetOption().toString()));
            }
            catch (XmlValueOutOfRangeException e)
            {
                throw new XmlException(TransformManager.INVALID_TARGET_OPTION);
            }
        }
        else
            _useTarget = false;
    }
}
