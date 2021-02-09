/*
 * Copyright (c) 2016-2019 LabKey Corporation
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
package org.labkey.api.dataiterator;


import com.fasterxml.jackson.annotation.JsonCreator;
import org.labkey.api.query.SchemaKey;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: matthewb
 * Date: 2013-04-16
 * Time: 9:19 AM
 *
 * This is a simple POJO that can be used to describe a simple copy operation.
 * Doesn't specify anything about filtering of source, etc.  However, for convenience, does
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
    protected String _sourceRunColumnName = null;
    protected List<String> _sourceColumns = null;
    protected List<SourceFilter> _sourceFilters = null;
    protected Integer _sourceTimeout = null;
    protected boolean _useSource = true;
    protected boolean _useFilterStrategy = true;
    protected String _sourceContainerFilter = null;

    protected SchemaKey _targetSchema;
    protected String _targetQuery;
    protected boolean _bulkLoad;
    protected int _transactionSize;
    protected int _batchSize;
    protected String _batchColumn = null;
    protected TargetOptions _targetOptions = TargetOptions.append;
    protected boolean _useTarget = true;
    protected TargetTypes _targetType = TargetTypes.query;
    protected Map<TargetFileProperties, String> _targetFileProperties;

    protected SchemaKey _procedureSchema;
    protected String _procedure;
    protected boolean _useSourceTransaction;
    protected boolean _useProcTransaction;
    protected boolean _useTargetTransaction;
    private String _targetString;
    private boolean _gating = false;
    private boolean _saveState = false;

    protected final Set<String> _alternateKeys = new HashSet<>();

    @JsonCreator
    public CopyConfig()
    {
    }


    public CopyConfig(String sourceSchema, String source, String targetSchema, String target)
    {
        _sourceSchema = SchemaKey.decode(sourceSchema);
        _sourceQuery = source;
        _targetSchema = SchemaKey.decode(targetSchema);
        _targetQuery = target;
    }

    /**
     * Assemble the parts of the target (schema + query, or file path + name) into an output string.
     *
     */
    public String getFullTargetString()
    {
        if (_targetString == null)
        {
            if (TargetTypes.file.equals(getTargetType()))
            {
                _targetString = getTargetFileProperties().get(TargetFileProperties.baseName) + getTargetFileProperties().get(TargetFileProperties.extension);
            }
            else _targetString = getTargetSchema().toString() + "." + getTargetQuery();
        }
        return _targetString;
    }

    public enum TargetOptions
    {
        merge,
        append,
        truncate
    }

    public enum TargetTypes
    {
        query,
        file
    }

    public enum TargetFileProperties
    {
        dir,
        baseName,
        extension,
        columnDelimiter,
        quote,
        rowDelimiter
    }

    public SchemaKey getSourceSchema()
    {
        return _sourceSchema;
    }

    public void setSourceSchema(SchemaKey sourceSchema)
    {
        _sourceSchema = sourceSchema;
    }

    public String getSourceQuery()
    {
        return _sourceQuery;
    }

    public void setSourceQuery(String sourceQuery)
    {
        _sourceQuery = sourceQuery;
    }

    public String getSourceTimestampColumnName()
    {
        return _sourceTimestampColumnName;
    }

    public void setSourceTimestampColumnName(String sourceTimestampColumnName)
    {
        _sourceTimestampColumnName = sourceTimestampColumnName;
    }

    public String getSourceRunColumnName()
    {
        return _sourceRunColumnName;
    }

    public void setSourceRunColumnName(String sourceRunColumnName)
    {
        _sourceRunColumnName = sourceRunColumnName;
    }

    public List<String> getSourceColumns()
    {
        return _sourceColumns;
    }

    public void setSourceColumns(List<String> sourceColumns)
    {
        _sourceColumns = Collections.unmodifiableList(sourceColumns);
    }

    public Integer getSourceTimeout()
    {
        return _sourceTimeout;
    }

    public void setSourceTimeout(Integer sourceTimeout)
    {
        _sourceTimeout = sourceTimeout;
    }

    public SchemaKey getTargetSchema()
    {
        return _targetSchema;
    }

    public void setTargetSchema(SchemaKey targetSchema)
    {
        _targetSchema = targetSchema;
    }

    public String getTargetQuery()
    {
        return _targetQuery;
    }

    public void setTargetQuery(String targetQuery)
    {
        _targetQuery = targetQuery;
    }

    public String getSourceContainerFilter()
    {
        return _sourceContainerFilter;
    }

    public void setSourceContainerFilter(String sourceContainerFilter)
    {
        _sourceContainerFilter = sourceContainerFilter;
    }

    public List<SourceFilter> getSourceFilters()
    {
        return _sourceFilters;
    }

    public void setSourceFilters(List<SourceFilter> sourceFilters)
    {
        _sourceFilters = sourceFilters;
    }

    public boolean isBulkLoad()
    {
        return _bulkLoad;
    }

    public void setBulkLoad(boolean bulkLoad)
    {
        _bulkLoad = bulkLoad;
    }

    public String getTargetString()
    {
        return _targetString;
    }

    public void setTargetString(String targetString)
    {
        _targetString = targetString;
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

    public Map<TargetFileProperties, String> getTargetFileProperties()
    {
        return _targetFileProperties;
    }

    public void setTargetFileProperties(Map<TargetFileProperties, String> targetFileProperties)
    {
        _targetFileProperties = targetFileProperties;
    }

    public boolean isUseSourceTransaction()
    {
        return _useSourceTransaction;
    }

    public void setUseSourceTransaction(boolean useSourceTransaction)
    {
        _useSourceTransaction = useSourceTransaction;
    }

    public boolean isUseProcTransaction()
    {
        return _useProcTransaction;
    }

    public void setUseProcTransaction(boolean useProcTransaction)
    {
        _useProcTransaction = useProcTransaction;
    }

    public boolean isUseTargetTransaction()
    {
        return _useTargetTransaction;
    }

    public void setUseTargetTransaction(boolean useTargetTransaction)
    {
        _useTargetTransaction = useTargetTransaction;
    }

    public int getBatchSize()
    {
        return _batchSize;
    }

    public void setBatchSize(int batchSize)
    {
        _batchSize = batchSize;
    }

    public String getBatchColumn()
    {
        return _batchColumn;
    }

    public void setBatchColumn(String batchColumn)
    {
        _batchColumn = batchColumn;
    }

    public boolean isGating()
    {
        return _gating;
    }

    public void setGating(boolean isGating)
    {
        _gating = isGating;
    }

    public boolean isSaveState()
    {
        return _saveState;
    }

    public void setSaveState(boolean saveState)
    {
        _saveState = saveState;
    }

    public Set<String> getAlternateKeys()
    {
        return Collections.unmodifiableSet(_alternateKeys);
    }

    /*
        NOTE: this was created b/c the java-api SourceFilter class was failing jackson serialization. A better solution would be to make that class compatible w/ serialization,
        and just use that whenever this is used. For ease and for the purpose of getting test going, I added this one.  The specific stack we get when using org.labkey.remoteapi.query.SourceFilter
        is below. I believe just adding a no-arg constructor to org.labkey.remoteapi.query.SourceFilter will solve this. Again, if that's updated, we can remove this SourceFilter class and replace usages
        with org.labkey.remoteapi.query.SourceFilter.

            testSimpleEtlWithSourceFilter(org.labkey.test.tests.di.ETLTest)
    org.labkey.remoteapi.CommandException: com.fasterxml.jackson.databind.exc.InvalidDefinitionException: Cannot construct instance of `org.labkey.remoteapi.query.SourceFilter` (no Creators, like default construct, exist): cannot deserialize from Object value (no delegate- or property-based Creator)
     at [Source: (String)"[ "org.labkey.di.pipeline.TransformPipelineJob", {
      "_etlDescriptor" : [ "org.labkey.di.pipeline.TransformDescriptor", {
        "_id" : "{ETLtest}/SourceToTarget2WithFilter",
        "_name" : "Source to target2",
        "_description" : "append rows from source to target ",
        "_moduleName" : "ETLtest",
        "_loadReferencedFiles" : false,
        "_gatedByStep" : false,
        "_standalone" : true,
        "_siteScope" : false,
        "_transactSourceSchema" : null,
        "_transactTargetSchema" : "etltest""[truncated 11302 chars]; line: 29, column: 9] (through reference chain: org.labkey.di.pipeline.TransformPipelineJob["_etlDescriptor"]->org.labkey.di.pipeline.TransformDescriptor["_stepMetaDatas"]->java.util.ArrayList[0]->org.labkey.di.steps.SimpleQueryTransformStepMeta["_sourceFilters"]->java.util.ArrayList[0])
        at org.labkey.remoteapi.Command.throwError(Command.java:400)
        at org.labkey.remoteapi.Command.checkThrowError(Command.java:362)
        at org.labkey.remoteapi.Command._execute(Command.java:349)
        at org.labkey.remoteapi.Command.execute(Command.java:210)
        at org.labkey.test.util.di.DataIntegrationHelper.runTransform(DataIntegrationHelper.java:102)
        at org.labkey.test.util.di.DataIntegrationHelper.runTransformAndWait(DataIntegrationHelper.java:110)
        at org.labkey.test.tests.di.ETLHelper.runETL_API(ETLHelper.java:576)
        at org.labkey.test.tests.di.ETLHelper.runETL_API(ETLHelper.java:605)
        at org.labkey.test.tests.di.ETLTest.testSimpleEtlWithSourceFilter(ETLTest.java:571)
        at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
        at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:62)
        at java.base/jdk.internal.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)
        at org.junit.runners.model.FrameworkMethod$1.runReflectiveCall(FrameworkMethod.java:50)
        at org.junit.internal.runners.model.ReflectiveCallable.run(ReflectiveCallable.java:12)
        at org.junit.runners.model.FrameworkMethod.invokeExplosively(FrameworkMethod.java:47)
        at org.junit.internal.runners.statements.InvokeMethod.evaluate(InvokeMethod.java:17)
        at org.junit.internal.runners.statements.RunBefores.evaluate(RunBefores.java:26)
        at org.labkey.junit.rules.TestWatcher$1.evaluate(TestWatcher.java:27)
        at org.labkey.test.BaseWebDriverTest$6$1.evaluate(BaseWebDriverTest.java:684)
        at org.labkey.junit.rules.TestWatcher$1.evaluate(TestWatcher.java:27)
        at org.junit.internal.runners.statements.FailOnTimeout$CallableStatement.call(FailOnTimeout.java:298)
        at org.junit.internal.runners.statements.FailOnTimeout$CallableStatement.call(FailOnTimeout.java:292)
        at java.base/java.util.concurrent.FutureTask.run(FutureTask.java:264)
        at java.base/java.lang.Thread.run(Thread.java:832)
     */
    public static class SourceFilter
    {
        String _columnName;
        Object _value;
        String _operator;

        public SourceFilter(String columnName, Object value, String operator)
        {
            _columnName = columnName;
            _value = value;
            _operator = operator;
        }

        public SourceFilter()
        {

        }

        public String getColumnName()
        {
            return _columnName;
        }

        public void setColumnName(String columnName)
        {
            _columnName = columnName;
        }

        public Object getValue()
        {
            return _value;
        }

        public void setValue(Object value)
        {
            _value = value;
        }

        public String getOperator()
        {
            return _operator;
        }

        public void setOperator(String operator)
        {
            _operator = operator;
        }

        public org.labkey.remoteapi.query.Filter toFilter()
        {
            return new org.labkey.remoteapi.query.Filter(_columnName, _value, org.labkey.remoteapi.query.Filter.Operator.getOperatorFromUrlKey(_operator));
        }
    }
}
