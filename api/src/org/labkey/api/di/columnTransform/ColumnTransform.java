/*
 * Copyright (c) 2016-2017 LabKey Corporation
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
package org.labkey.api.di.columnTransform;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.dataiterator.CopyConfig;
import org.labkey.api.dataiterator.SimpleTranslator;
import org.labkey.api.query.SchemaKey;
import org.labkey.api.writer.ContainerUser;

import java.io.Serializable;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * User: tgaluhn
 * Date: 9/22/2016
 *
 * Abstract class to define column level transformations for an ETL.
 * These could include such operations as mapping field names in flight,
 * modifying a value, applying a non-trivial lookup,
 * or performing some other operation altogether. Wraps many SimpleTranslator methods with a simpler interface.
 *
 * An instance of the implementing class is created when the ETL xml is parsed, a new instance
 * for each usage of that class anywhere. This same instance is reused for each ETL run.
 *
 * There are two groups of properties and setters. Th first set are configuration properties (e.g., etl name), set when the ETL xml is parsed.
 * These configuration settings are constant across ETL runs, and serializable.
 *
 * The second group of properties/setters are job specific (transform run id) and transient.
 *
 * If an implementing class needs additional class level members, they MUST either be serializable objects,
 * or declared transient. Note that as the same instance of this class is reused for each run,
 * those members will retain their values unless explicitly reset with an override of reset().

 * Two default methods of importance here are requiresSourceColumnName() and requiresTargetColumnName().
 * These are checked in the initial parsing/validation of an etl xml. Defaults are requiresSource == true,
 * requiresTarget == false; override as appropriate. As a shorthand helper, if requiresTarget == false and no
 * target column name is given in the xml, the source column name is used for both source and target.
 *
 * Summary:
 * Implement doTransform() to do the work.
 * Override reset() if a transient member or state needs to be cleared between runs
 * Override requiresSourceColumnName() and/or requiresTargetColumnName() if the defaults true, false aren't correct for your implementation.
 */
public abstract class ColumnTransform implements Serializable
{
    private String _etlName;
    private String _stepId;
    private SchemaKey _sourceSchema;
    private String _sourceQuery;
    private String _sourceColumnName;
    private SchemaKey _targetSchema;
    private String _targetQuery;
    private String _targetColumnName;
    private CopyConfig.TargetTypes _targetType;
    private Map<String, Object> _constants;

    // These properties are specific to a job, not a configuration, and will be initialized in the addTransform() call for each run.
    private transient SimpleTranslator _data;
    private transient int _transformRunId;
    private transient Integer _inputPosition;
    private transient Set<ColumnInfo> _outColumns;
    private transient ContainerUser _containerUser;

    // All of the setters here are called when the ETL xml is parsed. These members are all expected
    // to be configuration settings which will be constant across ETL runs, and serializable.
    public void setEtlName(@NotNull String etlName)
    {
        _etlName = etlName;
    }

    public void setStepId(@NotNull String stepId)
    {
        _stepId = stepId;
    }

    public void setSourceSchema(@Nullable SchemaKey sourceSchema)
    {
        _sourceSchema = sourceSchema;
    }

    public void setSourceQuery(@Nullable String sourceQuery)
    {
        _sourceQuery = sourceQuery;
    }

    public void setSourceColumnName(@Nullable String sourceColumnName)
    {
        _sourceColumnName = sourceColumnName;
    }

    public void setTargetSchema(@Nullable SchemaKey targetSchema)
    {
        _targetSchema = targetSchema;
    }

    public void setTargetQuery(@Nullable String targetQuery)
    {
        _targetQuery = targetQuery;
    }

    public void setTargetColumnName(@Nullable String targetColumnName)
    {
        _targetColumnName = targetColumnName;
    }

    public void setTargetType(@Nullable CopyConfig.TargetTypes targetType)
    {
        _targetType = targetType;
    }

    public void setConstants(@NotNull Map<String, Object> constants)
    {
        _constants = constants;
    }
    // End parsed configuration property setters

    // The getters for the serializable configuration properties
    @NotNull String getEtlName()
    {
        return _etlName;
    }

    @NotNull String getStepId()
    {
        return _stepId;
    }

    @Nullable SchemaKey getSourceSchema()
    {
        return _sourceSchema;
    }

    @Nullable String getSourceQuery()
    {
        return _sourceQuery;
    }

    @Nullable String getSourceColumnName()
    {
        return _sourceColumnName;
    }

    @Nullable SchemaKey getTargetSchema()
    {
        return _targetSchema;
    }

    @Nullable String getTargetQuery()
    {
        return _targetQuery;
    }

    @Nullable String getTargetColumnName()
    {
        return _targetColumnName;
    }

    @Nullable CopyConfig.TargetTypes getTargetType()
    {
        return _targetType;
    }

    @NotNull Map<String, Object> getConstants()
    {
        return Collections.unmodifiableMap(_constants);
    }

    @Nullable
    protected Object getConstant(String constantName)
    {
        return getConstants().get(constantName);
    }
    // End getters for parsed configuration properties

    // These getters return properties which are specific to an etl job, not a configuration.
    // The backing properties should be transient and are expected to be initialized in the addTransform() call.
    @NotNull
    public SimpleTranslator getData()
    {
        return _data;
    }

    public int getTransformRunId()
    {
        return _transformRunId;
    }

    @Nullable
    public Integer getInputPosition()
    {
        return _inputPosition;
    }

    @NotNull
    public Set<ColumnInfo> getOutColumns()
    {
        return _outColumns;
    }

    @NotNull
    public ContainerUser getContainerUser()
    {
        return _containerUser;
    }
    // End getters for job specific values

    /**
     * Returns the row value for the configured source column
     * @return the value in this row
     */
    @Nullable
    public Object getInputValue()
    {
        return _data.getInputColumnValue(_inputPosition);
    }

    /**
     * Returns the row value for any arbitrary column in the source query
     * @param columnName the name of the column
     * @return the value in this row
     */
    @Nullable
    public Object getInputValue(String columnName)
    {
        return _data.getInputColumnValue(_data.getColumnNameMap().get(columnName));
    }

    /**
     * Add a column to the output of the ETL
     * @param name The name of the output column
     * @param supplier Method called to determine value for the output column
     */
    public void addOutputColumn(String name, Supplier supplier)
    {
        ColumnInfo ci = new ColumnInfo(name);
        int outPosition = getData().addColumn(ci, supplier);
        getOutColumns().add(getData().getColumnInfo(outPosition));
    }

    /**
     * Injects this ColumnTransform into the process of building DataIterators for the ETL job
     *
     * @param cu The ContainerUser context running the ETL
     * @param data SimpleTranslator holding the columns to be output into the ETL destination
     * @param transformRunId transformRunId of this run
     * @param inputPosition the index of the source column in the source query. If null, no source column was specified in the ETL xml
     * @return The set of output columns, if any, added by the transform class implementation
     */
    @NotNull
    public Set<ColumnInfo> addTransform(ContainerUser cu, @NotNull SimpleTranslator data, int transformRunId, @Nullable Integer inputPosition)
    {
        _containerUser = cu;
        _data = data;
        _transformRunId = transformRunId;
        _inputPosition = inputPosition;
        _outColumns = new HashSet<>();
        reset();
        registerOutput();
        return _outColumns;
    }

    /**
     * Register the ColumnTransform output columns, including setting up any Suppliers of output values
     * <p>
     * This method is called once per ETL run.
     * Override this method if a given source column does not drive exactly one target column.
     */
    protected void registerOutput()
    {
        addOutputColumn(getTargetColumnName(), () ->
                doTransform(getInputValue()));
    }

    /**
     * Implement this method to perform the transform operation on the input value for a given column.
     * If needed, other values (from other columns, constants, or configuration settings such as
     * etlName, source query name, etc.) are available via getters.
     *
     * Any checked exception which is caught in this method should be rethrown as a ColumnTransformException,
     * passing the original caught throwable.
     * This ensures proper unwrapping for log and console output of the original exception.
     *
     * @param inputValue The value from the source query
     * @return The output value to be written to the target
     */
    protected abstract Object doTransform(Object inputValue);

    /**
     * @return true if the source column attribute is required in the ETL xml
     *
     */
    public boolean requiresSourceColumnName() {return true;}

    /**
     *
     * @return true if the target column attribute is required in the ETL xml
     *
     * Note if requiresTargetColumnName() == false, and no target column is supplied in the config,
     * the same column name is used for both source and target.
     */
    public boolean requiresTargetColumnName() {return false;}

    /**
     * Called at the start of every run. Override if there are transient cached values which should be cleared/reset
     * across runs.
     */
    public void reset()
    {
        // No-op in default
    }
}
