/*
 * Copyright (c) 2013-2015 LabKey Corporation
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

import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.xmlbeans.XmlException;
import org.apache.xmlbeans.impl.values.XmlValueOutOfRangeException;
import org.labkey.api.etl.CopyConfig;
import org.labkey.api.query.SchemaKey;
import org.labkey.di.pipeline.TransformManager;
import org.labkey.etl.xml.ColumnTransformType;
import org.labkey.etl.xml.SourceObjectType;
import org.labkey.etl.xml.TargetObjectType;
import org.labkey.etl.xml.TransformType;

import java.util.LinkedHashMap;
import java.util.Map;

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
        setSaveState(transformXML.getSaveState());
        parseWorkOptions(transformXML);
    }

    protected void parseWorkOptions(TransformType transformXML) throws XmlException
    {
        parseSource(transformXML);
        parseDestination(transformXML);
    }

    protected void parseSource(TransformType transformXML) throws XmlException
    {
        SourceObjectType source = transformXML.getSource();

        if (null != source)
        {
            setSourceSchema(SchemaKey.fromString(source.getSchemaName()));
            setSourceQuery(source.getQueryName());

            if (null != source.getContainerFilter())
                setSourceContainerFilter(source.getContainerFilter().toString());

            if (null != source.getTimestampColumnName())
                setSourceTimestampColumnName(source.getTimestampColumnName());
            else if (null != source.getRunColumnName())
                setSourceRunColumnName(source.getRunColumnName());
            setUseSourceTransaction(source.getUseTransaction());
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
        TargetObjectType destination = transformXML.getDestination();

        if (null != destination)
        {
            setBatchSize(destination.getBatchSize().intValue());
            setBatchColumn(destination.getBatchColumn());
            if (destination.getType() == null || CopyConfig.TargetTypes.query.name().equals(destination.getType().toString()))
            {
                setTargetType(CopyConfig.TargetTypes.query);
                setTargetSchema(SchemaKey.fromString(destination.getSchemaName()));
                setTargetQuery(destination.getQueryName());
                if (destination.isSetBulkLoad())
                {
                    setBulkLoad(destination.getBulkLoad());
                }
                setUseTargetTransaction(destination.getUseTransaction());
                setTransactionSize(destination.getTransactionSize().intValue());
            }
            else
            {
                try
                {
                    setTargetType(TargetTypes.valueOf(destination.getType().toString()));
                }
                catch (XmlValueOutOfRangeException e)
                {
                    throw new XmlException("Bad target type"); // TODO: error messages in constants
                }

                Map<TargetFileProperties, String> fileProps = new LinkedHashMap<>();
                setTargetFileProperties(fileProps);
                fileProps.put(TargetFileProperties.dir, StringUtils.trimToEmpty(destination.getDir()));
                fileProps.put(TargetFileProperties.baseName, destination.getFileBaseName());
                // Allow leading dot to be optional
                String extension = StringUtils.trimToEmpty(destination.getFileExtension());
                extension = "".equals(extension) || extension.startsWith(".") ? extension : "." + extension;
                fileProps.put(TargetFileProperties.extension, extension);
                if (destination.getColumnDelimiter() != null)
                    fileProps.put(TargetFileProperties.columnDelimiter, StringEscapeUtils.unescapeJava(destination.getColumnDelimiter()));
                if (destination.getRowDelimiter() != null)
                    fileProps.put(TargetFileProperties.rowDelimiter, StringEscapeUtils.unescapeJava(destination.getRowDelimiter()));
                if (destination.getQuote() != null)
                    fileProps.put(TargetFileProperties.quote, StringEscapeUtils.unescapeJava(destination.getQuote()));
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

            validateDestination();

            if (null != destination.getColumnTransforms())
            {
                for (ColumnTransformType column : destination.getColumnTransforms().getColumnArray())
                    _columnTransforms.put(column.getSource(), column.getTarget());
            }
        }
        else
            _useTarget = false;
    }

    private void validateDestination() throws XmlException
    {
        if ( (getTargetType().equals(TargetTypes.query) && (getTargetSchema() == null || getTargetQuery() == null))
                || (getTargetType().equals(TargetTypes.file) && (getTargetFileProperties().get(TargetFileProperties.dir) == null || getTargetFileProperties().get(TargetFileProperties.baseName) == null))) // OK to allow empty extension?
            throw new XmlException(TransformManager.INVALID_DESTINATION);
    }
    @Override
    public Map<String, String> getColumnTransforms()
    {
        return _columnTransforms;
    }
}
