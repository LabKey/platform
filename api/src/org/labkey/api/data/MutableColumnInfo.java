package org.labkey.api.data;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.exp.property.IPropertyValidator;
import org.labkey.api.gwt.client.DefaultValueType;
import org.labkey.api.query.FieldKey;

import java.util.ArrayList;
import java.util.List;

public interface MutableColumnInfo extends MutableColumnRenderProperties, ColumnInfo
{
    void setFieldKey(FieldKey key);

    void setAlias(String alias);

    void setMetaDataName(String metaDataName);

    void setPropertyURI(String propertyURI);

    void setConceptURI(String conceptURI);

    void setRangeURI(String rangeURI);

    void setTextAlign(String textAlign);

    void setJdbcDefaultValue(String jdbcDefaultValue);

    void setDefaultValue(Object defaultValue);

    void setDisplayField(ColumnInfo field);

    void setWidth(String width);

    void setUserEditable(boolean editable);

    void setDisplayColumnFactory(DisplayColumnFactory factory);

    void setShouldLog(boolean shouldLog);

    void setAutoIncrement(boolean autoIncrement);

    void setReadOnly(boolean readOnly);

    default void setSortFieldKeysFromXml(String xml)
    {
        List<FieldKey> keys = new ArrayList<>();
        for (String key : xml.split(","))
        {
            keys.add(FieldKey.fromString(key));
        }

        setSortFieldKeys(keys);
    }

    void setSqlTypeName(String sqlTypeName);

    void setSortFieldKeys(List<FieldKey> sortFieldKeys);

    void setJdbcType(JdbcType type);

    void clearFk();

    void setFk(@Nullable ForeignKey fk);

    void setFk(@NotNull Builder<ForeignKey> b);

    void setKeyField(boolean keyField);

    void setMvColumnName(FieldKey mvColumnName);

    void setMvIndicatorColumn(boolean mvIndicatorColumn);

    void setRawValueColumn(boolean rawColumn);

    void setIsUnselectable(boolean b);

    // As a rule try to set the parent table at construction time, if you're calling setParentTable(), it's probably not quite right
    @Deprecated
    void setParentTable(TableInfo parentTable);

    void setDefaultValueType(DefaultValueType defaultValueType);

    void setConditionalFormats(@NotNull List<ConditionalFormat> formats);

    void setValidators(List<? extends IPropertyValidator> validators);

    void checkLocked();

    void setLocked(boolean b);

    // return a ColumnInfo that does not suppport MutableColumnInfo or a MutableColumnInfo that is locked
    default ColumnInfo lock()
    {
        setLocked(true);
        return this;
    }

    boolean isLocked();

    void setCalculated(boolean calculated);

    void setColumnLogging(ColumnLogging columnLogging);

    void setHasDbSequence(boolean b);

    void setIsRootDbSequence(boolean b);
}
