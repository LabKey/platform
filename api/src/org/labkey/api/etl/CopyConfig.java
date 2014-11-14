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
package org.labkey.api.etl;


import org.labkey.api.query.SchemaKey;

/**
 * User: matthewb
 * Date: 2013-04-16
 * Time: 9:19 AM
 *
 * This is a simple POJO that can be used to describe a simple copy operation.
 * Doesn't specify anything about filtering of source, etc.  However, for convienence, does
 * have a place to put name of a timestamp column.
 *
 * There are now also flags to indicate the use of a source or filterStrategy. These were
 * introduced to support stored procedure transforms for which source and filter are optional.
 */
public class CopyConfig
{
    protected String _id;

    protected SchemaKey _sourceSchema;
    protected String _sourceQuery;
    protected String _sourceTimestampColumnName = null;
    protected SourceOptions _sourceOptions = null;
    protected boolean _useSource = true;
    protected boolean _useFilterStrategy = true;

    protected SchemaKey _targetSchema;
    protected String _targetQuery;
    protected TargetOptions _targetOptions = TargetOptions.append;
    protected boolean _useTarget = true;
    protected TargetTypes _targetType = TargetTypes.query;
    protected String _targetPath;
    protected String _targetFilePrefix;
    protected String _targetFileExtension;

    protected SchemaKey _procedureSchema;
    protected String _procedure;

    public CopyConfig()
    {
    }


    public CopyConfig(String sourceSchema, String source, String targetSchema, String target)
    {
        this._sourceSchema = SchemaKey.decode(sourceSchema);
        this._sourceQuery = source;
        this._targetSchema = SchemaKey.decode(targetSchema);
        this._targetQuery = target;
    }


    public enum TargetOptions
    {
        merge,
        append,
        truncate
    }


    public enum SourceOptions
    {
        deleteRowsAfterSelect
    }

    public enum TargetTypes
    {
        query,
        file
    }

    public SchemaKey getSourceSchema()
    {
        return _sourceSchema;
    }

    public void setSourceSchema(SchemaKey sourceSchema)
    {
        this._sourceSchema = sourceSchema;
    }

    public String getSourceQuery()
    {
        return _sourceQuery;
    }

    public void setSourceQuery(String sourceQuery)
    {
        this._sourceQuery = sourceQuery;
    }

    public String getSourceTimestampColumnName()
    {
        return _sourceTimestampColumnName;
    }

    public void setSourceTimestampColumnName(String sourceTimestampColumnName)
    {
        this._sourceTimestampColumnName = sourceTimestampColumnName;
    }

    public SchemaKey getTargetSchema()
    {
        return _targetSchema;
    }

    public void setTargetSchema(SchemaKey targetSchema)
    {
        this._targetSchema = targetSchema;
    }

    public String getTargetQuery()
    {
        return _targetQuery;
    }

    public void setTargetQuery(String targetQuery)
    {
        this._targetQuery = targetQuery;
    }

    public SourceOptions getSourceOptions()
    {
        return _sourceOptions;
    }

    public void setSourceOptions(SourceOptions sourceOptions)
    {
        _sourceOptions = sourceOptions;
    }

    public TargetOptions getTargetOptions()
    {
        return _targetOptions;
    }

    public void setTargetOptions(TargetOptions targetOptions)
    {
        _targetOptions = targetOptions;
    }

    public String getId()
    {
        return _id;
    }

    public void setId(String id)
    {
        _id = id;
    }

    public boolean isUseSource()
    {
        return _useSource;
    }

    public void setUseSource(boolean useSource)
    {
         _useSource = useSource;
    }

    public boolean isUseFilterStrategy()
    {
        return _useFilterStrategy;
    }

    public void setUseFilterStrategy(boolean useFilterStrategy)
    {
        _useFilterStrategy = useFilterStrategy;
    }

    public boolean isUseTarget()
    {
        return  _useTarget;
    }

    public void setUseTarget(boolean useTarget)
    {
        _useTarget = useTarget;
    }

    public SchemaKey getProcedureSchema()
    {
        return _procedureSchema;
    }

    public void setProcedureSchema(SchemaKey procedureSchema)
    {
        _procedureSchema = procedureSchema;
    }

    public String getProcedure()
    {
        return _procedure;
    }

    public void setProcedure(String procedure)
    {
        _procedure = procedure;
    }

    public TargetTypes getTargetType()
    {
        return _targetType;
    }

    public void setTargetType(TargetTypes targetType)
    {
        _targetType = targetType;
    }

    public String getTargetPath()
    {
        return _targetPath;
    }

    public void setTargetPath(String targetPath)
    {
        _targetPath = targetPath;
    }

    public String getTargetFilePrefix()
    {
        return _targetFilePrefix;
    }

    public void setTargetFilePrefix(String targetFilePrefix)
    {
        _targetFilePrefix = targetFilePrefix;
    }

    public String getTargetFileExtension()
    {
        return _targetFileExtension;
    }

    public void setTargetFileExtension(String targetFileExtension)
    {
        _targetFileExtension = targetFileExtension;
    }
}
