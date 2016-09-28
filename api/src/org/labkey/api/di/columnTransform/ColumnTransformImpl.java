package org.labkey.api.di.columnTransform;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.dataiterator.CopyConfig;
import org.labkey.api.dataiterator.SimpleTranslator;
import org.labkey.api.query.SchemaKey;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * User: tgaluhn
 * Date: 9/26/2016
 *
 * Top level abstract implementation of the ColumnTransform interface.
 * Implements everything required by the interface contract, and encapsulates
 * calls to SimpleTranslator methods with a simplified set of public methods.
 *
 * For the most implementations, don't extend this class; subclass the child
 * AbstractColumnTransform class instead.
 */
public abstract class ColumnTransformImpl implements ColumnTransform
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
        return _constants;
    }

    @Override
    public void setConstants(@NotNull Map<String, Object> constants)
    {
        _constants = constants;
    }

    private transient SimpleTranslator _data;
    private transient int _transformRunId;
    private transient Integer _inputPosition;
    private transient Set<ColumnInfo> _outColumns;

    public SimpleTranslator getData()
    {
        return _data;
    }

    public int getTransformRunId()
    {
        return _transformRunId;
    }

    public Integer getInputPosition()
    {
        return _inputPosition;
    }

    public Set<ColumnInfo> getOutColumns()
    {
        return _outColumns;
    }

    protected Object getInputValue()
    {
        return _data.getInputColumnValue(_inputPosition);
    }

    protected Object getInputValue(String columnName)
    {
        return _data.getInputColumnValue(_data.getColumnNameMap().get(columnName));
    }

    protected Map<String, Object> getConstantValues()
    {
        return Collections.unmodifiableMap(_constants);
    }

    protected Object getConstantValue(String constantName)
    {
        return _constants.get(constantName);
    }

    protected void addOutputColumn(String name, Supplier supplier)
    {
        ColumnInfo ci = new ColumnInfo(name);
        int outPosition = getData().addColumn(ci, supplier);
        getOutColumns().add(getData().getColumnInfo(outPosition));
    }

    @NotNull
    @Override
    public Set<ColumnInfo> addTransform(@NotNull SimpleTranslator data, int transformRunId, @Nullable Integer inputPosition)
    {
        _data = data;
        _transformRunId = transformRunId;
        _inputPosition = inputPosition;
        _outColumns = new HashSet<>();
        registerOutput();
        return _outColumns;
    }

    /**
     * Register the ColumnTransform output columns, including setting up any Suppliers of output values
     */
    protected abstract void registerOutput();
}
