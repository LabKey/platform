package org.labkey.api.data;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.dataiterator.SimpleTranslator;
import org.labkey.api.exp.property.IPropertyValidator;
import org.labkey.api.gwt.client.DefaultValueType;
import org.labkey.api.query.FieldKey;
import org.labkey.api.util.StringExpression;
import org.labkey.api.util.StringExpressionFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

    void setDbSequenceBatchSize(@Nullable Integer batchSize);

    void setHasDbSequence(boolean b);

    void setIsRootDbSequence(boolean b);

    void setRemapMissingBehavior(SimpleTranslator.RemapMissingBehavior missingBehavior);


    // helpers
    // TODO: fix up OORIndicator

    default void remapFieldKeys(@Nullable FieldKey parent, @Nullable Map<FieldKey, FieldKey> remap)
    {
        remapFieldKeys(parent, remap, null, false);
    }


    default void remapFieldKeys(@Nullable FieldKey parent, @Nullable Map<FieldKey, FieldKey> remap, @Nullable Set<String> remapWarnings, boolean throwErrors)
    {
        checkLocked();
        if (null==parent && (null == remap || remap.isEmpty()))
            return;

        // Only one of these should be non-null, otherwise it's a bit ambiguious what should happen
        // we could create split up the method, but that would be a bit of duplication too.
        assert( null==parent || null==remap );

        // TODO should mvColumnName be a fieldkey so we can reparent etc?
        if (null != getMvColumnName())
        {
            FieldKey r = null==remap ? null : remap.get(getMvColumnName());
            if (null != r && r.getParent()==null)
                setMvColumnName(r);
        }

        remapUrlFieldKeys(parent, remap, remapWarnings);
        remapTextExpressionFieldKeys(parent, remap, remapWarnings);
        remapForeignKeyFieldKeys(parent, remap, remapWarnings);
        remapSortFieldKeys(parent, remap, remapWarnings);
        remapDisplayColumnFactory(parent, remap, remapWarnings);
        remapColumnLogging(parent, remap, remapWarnings);
    }


    default void remapUrlFieldKeys(@Nullable FieldKey parent, @Nullable Map<FieldKey, FieldKey> remap, @Nullable Set<String> remapWarnings)
    {
        StringExpression se = getURL();
        if (se instanceof StringExpressionFactory.FieldKeyStringExpression && se != AbstractTableInfo.LINK_DISABLER)
        {
            StringExpressionFactory.FieldKeyStringExpression remapped = ((StringExpressionFactory.FieldKeyStringExpression)se).remapFieldKeys(parent, remap);
            setURL(remapped);
        }
    }

    default void remapTextExpressionFieldKeys(@Nullable FieldKey parent, @Nullable Map<FieldKey, FieldKey> remap, @Nullable Set<String> remapWarnings)
    {
        StringExpression se = getTextExpression();
        if (se instanceof StringExpressionFactory.FieldKeyStringExpression)
        {
            StringExpressionFactory.FieldKeyStringExpression remapped = ((StringExpressionFactory.FieldKeyStringExpression)se).remapFieldKeys(parent, remap);
            setTextExpression(remapped);
        }
    }


    default void remapForeignKeyFieldKeys(@Nullable FieldKey parent, @Nullable Map<FieldKey, FieldKey> remap, @Nullable Set<String> remapWarnings)
    {
        ForeignKey fk = getFk();
        if (fk == null)
            return;
        ForeignKey remappedFk = fk.remapFieldKeys(parent, remap);
        setFk(remappedFk);
    }


    default void remapSortFieldKeys(@Nullable FieldKey parent, @Nullable Map<FieldKey, FieldKey> remap, @Nullable Set<String> remapWarnings)
    {
        if (getSortFieldKeys() == null)
            return;

        List<FieldKey> remappedKeys = new ArrayList<>();
        for (FieldKey key : getSortFieldKeys())
        {
            var mapped = FieldKey.remap(key, parent, remap);
            if (null == mapped)
            {
                if (null != remapWarnings)
                    remapWarnings.add("Unable to find sort field key: " + key.toDisplayString());
                mapped = key;
            }
            remappedKeys.add(mapped);
        }

        setSortFieldKeys(remappedKeys);
    }

    default void remapDisplayColumnFactory(@Nullable FieldKey parent, @Nullable Map<FieldKey, FieldKey> remap, @Nullable Set<String> remapWarnings)
    {
        DisplayColumnFactory factory = getDisplayColumnFactory();
        if (DEFAULT_FACTORY == factory || !(factory instanceof RemappingDisplayColumnFactory))
            return;

        RemappingDisplayColumnFactory remapped = ((RemappingDisplayColumnFactory) factory).remapFieldKeys(parent, remap);
        setDisplayColumnFactory(remapped);
    }

    /** remapColumnLogging always throws on error, so we don't need to pass in throwErrors */
    default void remapColumnLogging(@Nullable FieldKey parent, @Nullable Map<FieldKey, FieldKey> remap, @Nullable Set<String> remapWarnings)
    {
        ColumnLogging logging = getColumnLogging();
        if (logging == null)
            return;
        ColumnLogging remapped = logging.remapFieldKeys(parent, remap, remapWarnings);
        setColumnLogging(remapped);
    }

    /* should be called just before setLocked() */
    default void afterConstruct()
    {
    }
}
