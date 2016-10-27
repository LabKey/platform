package org.labkey.api.di.columnTransform;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.dataiterator.CopyConfig;
import org.labkey.api.dataiterator.SimpleTranslator;
import org.labkey.api.query.SchemaKey;
import org.labkey.api.writer.ContainerUser;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * User: tgaluhn
 * Date: 9/26/2016
 *
 * Base class for custom column transforms.
 * Implements everything required by the interface contract, and encapsulates
 * calls to SimpleTranslator methods with a simplified set of public methods.
 *
 * Implement doTransform() to do the work.
 */
public abstract class AbstractColumnTransform implements ColumnTransform
{
    // Properties backing the interface setters
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

    @NotNull
    @Override
    public String getEtlName()
    {
        return _etlName;
    }

    @Override
    public void setEtlName(@NotNull String etlName)
    {
        _etlName = etlName;
    }

    @NotNull
    @Override
    public String getStepId()
    {
        return _stepId;
    }

    @Override
    public void setStepId(@NotNull String stepId)
    {
        _stepId = stepId;
    }

    @Nullable
    @Override
    public SchemaKey getSourceSchema()
    {
        return _sourceSchema;
    }

    @Override
    public void setSourceSchema(@Nullable SchemaKey sourceSchema)
    {
        _sourceSchema = sourceSchema;
    }

    @Nullable
    @Override
    public String getSourceQuery()
    {
        return _sourceQuery;
    }

    @Override
    public void setSourceQuery(@Nullable String sourceQuery)
    {
        _sourceQuery = sourceQuery;
    }

    @Nullable
    @Override
    public String getSourceColumnName()
    {
        return _sourceColumnName;
    }

    @Override
    public void setSourceColumnName(@Nullable String sourceColumnName)
    {
        _sourceColumnName = sourceColumnName;
    }

    @Nullable
    @Override
    public SchemaKey getTargetSchema()
    {
        return _targetSchema;
    }

    @Override
    public void setTargetSchema(@Nullable SchemaKey targetSchema)
    {
        _targetSchema = targetSchema;
    }

    @Nullable
    @Override
    public String getTargetQuery()
    {
        return _targetQuery;
    }

    @Override
    public void setTargetQuery(@Nullable String targetQuery)
    {
        _targetQuery = targetQuery;
    }

    @Nullable
    @Override
    public String getTargetColumnName()
    {
        return _targetColumnName;
    }

    @Override
    public void setTargetColumnName(@Nullable String targetColumnName)
    {
        _targetColumnName = targetColumnName;
    }

    @Nullable
    @Override
    public CopyConfig.TargetTypes getTargetType()
    {
        return _targetType;
    }

    @Override
    public void setTargetType(@Nullable CopyConfig.TargetTypes targetType)
    {
        _targetType = targetType;
    }

    @NotNull
    @Override
    public Map<String, Object> getConstants()
    {
        return Collections.unmodifiableMap(_constants);
    }

    @Nullable
    @Override
    public Object getConstant(String constantName)
    {
        return _constants.get(constantName);
    }

    @Override
    public void setConstants(@NotNull Map<String, Object> constants)
    {
        _constants = constants;
    }

    @NotNull
    @Override
    public SimpleTranslator getData()
    {
        return _data;
    }

    @Override
    public int getTransformRunId()
    {
        return _transformRunId;
    }

    @Nullable
    @Override
    public Integer getInputPosition()
    {
        return _inputPosition;
    }

    @NotNull
    @Override
    public Set<ColumnInfo> getOutColumns()
    {
        return _outColumns;
    }

    @NotNull
    @Override
    public ContainerUser getContainerUser()
    {
        return _containerUser;
    }

    @Nullable
    @Override
    public Object getInputValue()
    {
        return _data.getInputColumnValue(_inputPosition);
    }

    @Nullable
    @Override
    public Object getInputValue(String columnName)
    {
        return _data.getInputColumnValue(_data.getColumnNameMap().get(columnName));
    }

    @Override
    public void addOutputColumn(String name, Supplier supplier)
    {
        ColumnInfo ci = new ColumnInfo(name);
        int outPosition = getData().addColumn(ci, supplier);
        getOutColumns().add(getData().getColumnInfo(outPosition));
    }

    @NotNull
    @Override
    public Set<ColumnInfo> addTransform(ContainerUser cu, @NotNull SimpleTranslator data, int transformRunId, @Nullable Integer inputPosition)
    {
        _containerUser = cu;
        _data = data;
        _transformRunId = transformRunId;
        _inputPosition = inputPosition;
        _outColumns = new HashSet<>();
        registerOutput();
        return _outColumns;
    }

    /**
     * Register the ColumnTransform output columns, including setting up any Suppliers of output values
     * <p>
     * This method is called once per ETL run.
     */
    protected void registerOutput()
    {
        addOutputColumn(getTargetColumnName(), () ->
                doTransform(getInputValue()));
    }

    /**
     * Implement this method to perform the transform operation on the input value for a given column.
     * If needed, other values (from other columns, constants, or configuration settings such as
     * etlName, source query name, etc.) are available via public getters.
     *
     * Any checked exception which is caught in this method should be rethrown as a ColumnTransformException.
     * This ensures proper unwrapping for log and console output of the original exception.
     *
     * @param inputValue The value from the source query
     * @return The output value to be written to the target
     */
    protected abstract Object doTransform(Object inputValue);
}
