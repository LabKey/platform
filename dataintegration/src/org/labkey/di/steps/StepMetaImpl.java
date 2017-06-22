/*
 * Copyright (c) 2013-2017 LabKey Corporation
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
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.ParameterDescription;
import org.labkey.api.dataiterator.CopyConfig;
import org.labkey.api.di.columnTransform.ColumnTransform;
import org.labkey.api.query.SchemaKey;
import org.labkey.di.columnTransforms.ColumnMappingTransform;
import org.labkey.di.pipeline.TransformManager;
import org.labkey.etl.xml.AlternateKeyType;
import org.labkey.etl.xml.AlternateKeysType;
import org.labkey.etl.xml.ColumnTransformType;
import org.labkey.etl.xml.ColumnTransformsType;
import org.labkey.etl.xml.SourceObjectType;
import org.labkey.etl.xml.TargetObjectType;
import org.labkey.etl.xml.TransformType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * User: tgaluhn
 * Date: 10/8/13
 */
public abstract class StepMetaImpl extends CopyConfig implements StepMeta
{
    protected String description;
    protected StepProvider provider;
    private final Map<ParameterDescription, Object> _constants = new LinkedHashMap<>();
    private String _etlName;

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

            if (source.isSetSourceColumns())
            {
                setSourceColumns(Arrays.asList(source.getSourceColumns().getColumnArray()));
            }

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
            }
            else
            {
                setTargetFileOptions(destination);
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

            // The constants map may have already been populated with values from the global etl level. Any which are defined
            // at the step level will override those in the event of a collision.
            if (null != destination.getConstants())
            {
                TransformManager.get().populateParameterMap(destination.getConstants().getColumnArray(), _constants);
            }
            // Parse any column level transforms/name remappings which have been defined. Do this after parsing
            // the constants, as they are made available to column transforms.
            parseColumnTransforms(destination.getColumnTransforms(), transformXML.getId());

            parseAlternateKeys(destination.getAlternateKeys());
        }
        else
            _useTarget = false;
    }

    private void setTargetFileOptions(TargetObjectType destination) throws XmlException
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

    private void validateDestination() throws XmlException
    {
        if ( (getTargetType().equals(TargetTypes.query) && (getTargetSchema() == null || getTargetQuery() == null))
                || (getTargetType().equals(TargetTypes.file) && (getTargetFileProperties().get(TargetFileProperties.dir) == null || getTargetFileProperties().get(TargetFileProperties.baseName) == null))) // OK to allow empty extension?
            throw new XmlException(TransformManager.INVALID_DESTINATION);
    }

    private void parseColumnTransforms(@Nullable ColumnTransformsType columnTransformsXml, String stepId) throws XmlException
    {
        if (null != columnTransformsXml)
        {
            for (ColumnTransformType columnTxXml : columnTransformsXml.getColumnArray())
            {
                // Default is a column name mapping
                String transformClassName = columnTxXml.getTransformClass() != null ? columnTxXml.getTransformClass() : ColumnMappingTransform.class.getName();
                ColumnTransform colTx;
                try
                {
                    Object txObject = Class.forName(transformClassName).newInstance();
                    if (!ColumnTransform.class.isInstance(txObject))
                    {
                        throw new XmlException("Column transform class " + transformClassName + " must implement " + ColumnTransform.class.getName());
                    }
                    else
                    {
                        colTx = (ColumnTransform)txObject;
                    }
                }
                catch (ClassNotFoundException e)
                {
                    throw new XmlException("Column transform class not found " + transformClassName);
                }
                catch (InstantiationException | IllegalAccessException e)
                {
                    throw new XmlException("Exception instantiating transform class " + transformClassName, e);
                }

                // Validate required source and/or target attributes against requirements of specific transform class
                String source = StringUtils.trimToNull(columnTxXml.getSource());
                String target = StringUtils.trimToNull(columnTxXml.getTarget());
                StringBuilder errorMsg = new StringBuilder();
                if (colTx.requiresSourceColumnName() && null == source)
                {
                    errorMsg.append("source\n");
                }
                if (colTx.requiresTargetColumnName() && null == target)
                {
                    errorMsg.append("target\n");
                }
                if (errorMsg.length() > 0)
                {
                    throw new XmlException(errorMsg.insert(0, "XML missing required attributes for class " + transformClassName + ":\n").toString());
                }

                // Set all the config values expected to be constant across ETL runs
                colTx.setEtlName(_etlName);
                colTx.setStepId(stepId);
                colTx.setSourceSchema(_sourceSchema);
                colTx.setSourceQuery(_sourceQuery);
                colTx.setSourceColumnName(source);
                colTx.setTargetSchema(_targetSchema);
                colTx.setTargetQuery(_targetQuery);
                colTx.setTargetColumnName(target == null ? source : target);
                colTx.setTargetType(_targetType);
                Map<String, Object> constantValues = new CaseInsensitiveHashMap<>();
                for (Map.Entry<ParameterDescription, Object> entry : _constants.entrySet())
                {
                    constantValues.put(entry.getKey().getName(), entry.getValue());
                }
                colTx.setConstants(constantValues);

                // Add this transform to the list for the specified source column (or the list of transforms
                // which don't require a source column name)
                List<ColumnTransform> registeredColTransforms = _columnTransforms.get(source);
                if (null == registeredColTransforms)
                {
                    registeredColTransforms = new ArrayList<>();
                    _columnTransforms.put(source, registeredColTransforms);
                }
                registeredColTransforms.add(colTx);
            }
        }
    }

    private void parseAlternateKeys(@Nullable AlternateKeysType alternateKeysXml)
    {
        if (null != alternateKeysXml)
        {
            for (AlternateKeyType col : alternateKeysXml.getColumnArray())
            {
                _alternateKeys.add(col.getName());
            }
        }
    }

    @Override
    public Map<ParameterDescription, Object> getConstants()
    {
        return Collections.unmodifiableMap(_constants);
    }

    @Override
    public void putConstants(Map<ParameterDescription, Object> constants)
    {
        _constants.putAll(constants);
    }

    @Override
    public void setEtlName(String etlName)
    {
        _etlName = etlName;
    }
}
