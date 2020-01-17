package org.labkey.api.data;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.property.IPropertyValidator;
import org.labkey.api.gwt.client.DefaultScaleType;
import org.labkey.api.gwt.client.DefaultValueType;
import org.labkey.api.gwt.client.FacetingBehaviorType;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.api.util.StringExpression;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class WrappedColumnInfo
{
    public static MutableColumnInfo wrap(ColumnInfo delegate)
    {
        return new MutableColumnInfoWrapper(delegate);
    }

    /* create a delegating columnInfo wrapper, the returned columnInfo is Mutable, but if you want to set a LOT of properties
     * use wrapCopy() instead (e.g. for PropertyColumn.copyAttributes())
     *
     * NOTE: BaseColumnInfo.copyAttribtuesFrom() does not copy displayField/filterField, here we drop it if the parentTables don't match
     */
    public static MutableColumnInfo wrapDelegating(TableInfo parent_, FieldKey fieldKey_, ColumnInfo delegate_, String label_, String alias_)
    {
        AbstractWrappedColumnInfo inner = new AbstractWrappedColumnInfo(delegate_)
        {
            final FieldKey fieldKey = fieldKey_;
            final TableInfo parent = parent_;
            final String label = label_;
            final String alias = alias_;

            @Override
            public FieldKey getFieldKey()
            {
                return fieldKey;
            }

            @Override
            public TableInfo getParentTable()
            {
                return parent;
            }

            @Override
            public String getLabelValue()
            {
                return label;
            }

            @Override
            public String getLabel()
            {
                return super.getLabel();
            }

            @Override
            public String getAlias()
            {
                return alias;
            }

            @Override
            public boolean isAliasSet()
            {
                return null != alias;
            }

            @Override
            public @Nullable ColumnInfo getDisplayField()
            {
                // don't return displayField if it's from a different table, that would probably be wrong...
                return parent == delegate.getParentTable() ? delegate.getDisplayField() : null;
            }

            @Override
            public ColumnInfo getFilterField()
            {
                // don't return filterField if it's from a different table, that would probably be wrong...
                return parent == delegate.getParentTable() ? delegate.getFilterField() : null;
            }
        };
        return new MutableColumnInfoWrapper(inner);
    }


    /*
     * Instead of delegating all getters(), use an ExprColumn, this is useful when you want to set a LOT of properties
     * e.g. PropertyColumn.copyAttributes()
     */
    public static ExprColumn wrapAsExprColumn(TableInfo parent, String tableAlias, FieldKey fieldKey, ColumnInfo sourceColumnInfo, String label, String alias)
    {
        tableAlias = StringUtils.defaultString(tableAlias, ExprColumn.STR_TABLE_ALIAS);
        ExprColumn ret = new ExprColumn(parent, fieldKey, sourceColumnInfo.getValueSql(tableAlias), sourceColumnInfo.getJdbcType());
        ret.copyAttributesFrom(sourceColumnInfo);
        ret.copyURLFrom(sourceColumnInfo, null, null);
        ret.setLabel(label);  // The alias should be used to generate the default label, not whatever was in underlyingColumn; name might be set later (e.g., meta data override)
        ret.setAlias(alias);
        return ret;
    }


    private static class MutableColumnInfoWrapper extends AbstractWrappedColumnInfo implements MutableColumnInfo
    {
        boolean _locked = false;

        MutableColumnInfoWrapper(ColumnInfo delegate)
        {
            super(delegate);
        }

        @Override
        public void checkLocked()
        {
            if (_locked)
                throw new IllegalStateException("ColumnInfo is locked: " + (null!=getParentTable()?getParentTable().getName()+".":"") + getName());
        }

        @Override
        public ColumnInfo lock()
        {
            setLocked(true);
            ColumnInfo ret = delegate;
            // if delegate is not locked, wrap with a readonly wrapper
            if (ret instanceof MutableColumnInfo && !((MutableColumnInfo) ret).isLocked())
                ret = new AbstractWrappedColumnInfo(ret) {};
            return ret;
        }

        @Override
        public void setLocked(boolean b)
        {
            if (_locked && !b)
                throw new IllegalStateException("Can't unlock a ColumnInfo: " + getName());
            _locked = b;
        }

        @Override
        public boolean isLocked()
        {
            return _locked;
        }

        @Override
        public void setSortDirection(Sort.SortDirection sortDirection_)
        {
            checkLocked();
            delegate = new AbstractWrappedColumnInfo(delegate)
            {
                final Sort.SortDirection sortDirection = sortDirection_;
                @Override
                public Sort.SortDirection getSortDirection()
                {
                    return sortDirection;
                }
            };
        }

        @Override
        public void setInputType(String inputType_)
        {
            checkLocked();
            delegate = new AbstractWrappedColumnInfo(delegate)
            {
                final String inputType = inputType_;

                @Override
                public String getInputType()
                {
                    return inputType;
                }
            };
        }

        @Override
        public void setInputLength(int inputLength)
        {
            checkLocked();
            throw new UnsupportedOperationException();
        }

        @Override
        public void setInputRows(int inputRows)
        {
            checkLocked();
            throw new UnsupportedOperationException();
        }

        @Override
        public void setDisplayWidth(String displayWidth)
        {
            checkLocked();
            throw new UnsupportedOperationException();
        }

        @Override
        public void setFormat(String format)
        {
            checkLocked();
            throw new UnsupportedOperationException();
        }

        @Override
        public void setExcelFormatString(String excelFormatString)
        {
            checkLocked();
            throw new UnsupportedOperationException();
        }

        @Override
        public void setTsvFormatString(String tsvFormatString)
        {
            checkLocked();
            throw new UnsupportedOperationException();
        }

        @Override
        public void setTextExpression(StringExpression expr)
        {
            checkLocked();
            throw new UnsupportedOperationException();
        }

        @Override
        public void setLabel(String label_)
        {
            checkLocked();
            delegate = new AbstractWrappedColumnInfo(delegate)
            {
                final String label = label_;

                @Override
                public String getLabelValue()
                {
                    return label;
                }
            };
        }

        @Override
        public void setShortLabel(String shortLabel)
        {
            checkLocked();
            throw new UnsupportedOperationException();
        }

        @Override
        public void setDescription(String description_)
        {
            checkLocked();
            delegate = new AbstractWrappedColumnInfo(delegate)
            {
                final String description = description_;

                @Override
                public String getDescription()
                {
                    return description;
                }
            };
        }

        @Override
        public void setHidden(boolean hidden_)
        {
            checkLocked();
            delegate = new AbstractWrappedColumnInfo(delegate)
            {
                final boolean hidden = hidden_;

                @Override
                public boolean isHidden()
                {
                    return hidden;
                }
            };
        }

        @Override
        public void setShownInDetailsView(boolean shownInDetailsView_)
        {
            checkLocked();
            delegate = new AbstractWrappedColumnInfo(delegate)
            {
                final boolean shownInDetailsView = shownInDetailsView_;

                @Override
                public boolean isShownInDetailsView()
                {
                    return shownInDetailsView;
                }
            };
        }

        @Override
        public void setShownInInsertView(boolean shownInInsertView_)
        {
            checkLocked();
            delegate = new AbstractWrappedColumnInfo(delegate)
            {
                final boolean shownInInsertView = shownInInsertView_;

                @Override
                public boolean isShownInInsertView()
                {
                    return shownInInsertView;
                }
            };
        }

        @Override
        public void setShownInUpdateView(boolean shownInUpdateView_)
        {
            checkLocked();
            delegate = new AbstractWrappedColumnInfo(delegate)
            {
                final boolean shownInUpdateView = shownInUpdateView_;

                @Override
                public boolean isShownInUpdateView()
                {
                    return shownInUpdateView;
                }
            };
        }

        @Override
        public void setURL(StringExpression url_)
        {
            checkLocked();
            delegate = new AbstractWrappedColumnInfo(delegate)
            {
                final StringExpression url = url_;

                @Override
                public StringExpression getURL()
                {
                    return url;
                }
            };
        }

        @Override
        public void setURLTargetWindow(String urlTargetWindow)
        {
            checkLocked();
            throw new UnsupportedOperationException();
        }

        @Override
        public void setURLCls(String urlCls)
        {
            checkLocked();
            throw new UnsupportedOperationException();
        }

        @Override
        public void setOnClick(String onClick)
        {
            checkLocked();
            throw new UnsupportedOperationException();
        }

        @Override
        public void setRecommendedVariable(boolean recommendedVariable)
        {
            checkLocked();
            throw new UnsupportedOperationException();
        }

        @Override
        public void setDefaultScale(DefaultScaleType defaultScale)
        {
            checkLocked();
            throw new UnsupportedOperationException();
        }

        @Override
        public void setMeasure(boolean measure_)
        {
            checkLocked();
            delegate = new AbstractWrappedColumnInfo(delegate)
            {
                final boolean measure = measure_;

                @Override
                public boolean isMeasure()
                {
                    return measure;
                }
            };
        }

        @Override
        public void setDimension(boolean dimension_)
        {
            checkLocked();
            delegate = new AbstractWrappedColumnInfo(delegate)
            {
                final boolean dimension = dimension_;

                @Override
                public boolean isDimension()
                {
                    return dimension;
                }
            };
        }

        @Override
        public void setNullable(boolean nullable_)
        {
            checkLocked();
            delegate = new AbstractWrappedColumnInfo(delegate)
            {
                final boolean nullable = nullable_;

                @Override
                public boolean isNullable()
                {
                    return nullable;
                }
            };
        }

        @Override
        public void setRequired(boolean required_)
        {
            checkLocked();
            delegate = new AbstractWrappedColumnInfo(delegate)
            {
                final boolean required = required_;

                @Override
                public boolean isRequired()
                {
                    return required;
                }
            };
        }

        @Override
        public void setImportAliasesSet(Set<String> importAliases_)
        {
            checkLocked();
            delegate = new AbstractWrappedColumnInfo(delegate)
            {
                final LinkedHashSet<String> importAliases = new LinkedHashSet<>(importAliases_);

                @NotNull
                @Override
                public Set<String> getImportAliasSet()
                {
                    return importAliases;
                }
            };
        }

        @Override
        public void setPropertyType(PropertyType propertyType)
        {
            checkLocked();
            throw new UnsupportedOperationException();
        }

        @Override
        public void setFacetingBehaviorType(FacetingBehaviorType facetingBehaviorType_)
        {
            checkLocked();
            delegate = new AbstractWrappedColumnInfo(delegate)
            {
                final FacetingBehaviorType facetingBehaviorType = facetingBehaviorType_;

                @Override
                public FacetingBehaviorType getFacetingBehaviorType()
                {
                    return facetingBehaviorType;
                }
            };
        }

        @Override
        public void setCrosstabColumnDimension(FieldKey crosstabColumnDimension)
        {
            checkLocked();
            throw new UnsupportedOperationException();
        }

        @Override
        public void setCrosstabColumnMember(CrosstabMember member)
        {
            checkLocked();
            throw new UnsupportedOperationException();
        }

        @Override
        public void setPHI(PHI phi)
        {
            checkLocked();
            throw new UnsupportedOperationException();
        }

        @Override
        public void setRedactedText(String redactedText)
        {
            checkLocked();
            throw new UnsupportedOperationException();
        }

        @Override
        public void setExcludeFromShifting(boolean isExcludeFromShifting)
        {
            checkLocked();
            throw new UnsupportedOperationException();
        }

        @Override
        public void setScale(int scale)
        {
            checkLocked();
            throw new UnsupportedOperationException();
        }

        @Override
        public void setFieldKey(FieldKey fieldKey_)
        {
            checkLocked();
            delegate = new AbstractWrappedColumnInfo(delegate)
            {
                final FieldKey fieldKey = fieldKey_;

                @Override
                public FieldKey getFieldKey()
                {
                    return fieldKey;
                }
            };
        }

        @Override
        public void setAlias(String alias_)
        {
            checkLocked();
            delegate = new AbstractWrappedColumnInfo(delegate)
            {
                final String alias = alias_;

                @Override
                public String getAlias()
                {
                    return alias;
                }
            };
        }

        @Override
        public void setMetaDataName(String metaDataName)
        {
            checkLocked();
            throw new UnsupportedOperationException();
        }

        @Override
        public void setPropertyURI(String propertyURI_)
        {
            checkLocked();
            delegate = new AbstractWrappedColumnInfo(delegate)
            {
                final String propertyURI = propertyURI_;

                @Override
                public String getPropertyURI()
                {
                    return propertyURI;
                }
            };
        }

        @Override
        public void setConceptURI(String conceptURI_)
        {
            checkLocked();
            delegate = new AbstractWrappedColumnInfo(delegate)
            {
                final String conceptURI = conceptURI_;

                @Override
                public String getConceptURI()
                {
                    return conceptURI;
                }
            };
        }

        @Override
        public void setRangeURI(String rangeURI)
        {
            checkLocked();
            throw new UnsupportedOperationException();
        }

        @Override
        public void setTextAlign(String textAlign_)
        {
            checkLocked();
            delegate = new AbstractWrappedColumnInfo(delegate)
            {
                final String textAlign = textAlign_;

                @Override
                public String getTextAlign()
                {
                    return textAlign;
                }
            };
        }

        @Override
        public void setJdbcDefaultValue(String jdbcDefaultValue)
        {
            checkLocked();
            throw new UnsupportedOperationException();
        }

        @Override
        public void setDefaultValue(Object defaultValue)
        {
            checkLocked();
            throw new UnsupportedOperationException();
        }

        @Override
        public void setDisplayField(ColumnInfo field)
        {
            checkLocked();
            throw new UnsupportedOperationException();
            // see also getRenderer()
        }

        @Override
        public void setWidth(String width)
        {
            checkLocked();
            throw new UnsupportedOperationException();
        }

        @Override
        public void setUserEditable(boolean editable_)
        {
            checkLocked();
            delegate = new AbstractWrappedColumnInfo(delegate)
            {
                final boolean editable = editable_;

                @Override
                public boolean isUserEditable()
                {
                    return editable;
                }
            };
        }

        @Override
        public void setDisplayColumnFactory(DisplayColumnFactory factory_)
        {
            checkLocked();
            delegate = new AbstractWrappedColumnInfo(delegate)
            {
                final DisplayColumnFactory factory = factory_;

                @Override
                public DisplayColumnFactory getDisplayColumnFactory()
                {
                    return factory;
                }

                // see also AbstractWrappedColumnInfo.getRenderer()
            };
        }

        @Override
        public void setShouldLog(boolean shouldLog)
        {
            checkLocked();
            throw new UnsupportedOperationException();
        }

        @Override
        public void setAutoIncrement(boolean autoIncrement_)
        {
            checkLocked();
            delegate = new AbstractWrappedColumnInfo(delegate)
            {
                final boolean autoIncrement = autoIncrement_;

                @Override
                public boolean isAutoIncrement()
                {
                    return autoIncrement_;
                }
            };
        }

        @Override
        public void setReadOnly(boolean readOnly_)
        {
            checkLocked();
            delegate = new AbstractWrappedColumnInfo(delegate)
            {
                final boolean readOnly = readOnly_;

                @Override
                public boolean isReadOnly()
                {
                    return readOnly;
                }
            };
        }

        @Override
        public void setSortFieldKeysFromXml(String xml)
        {
            checkLocked();
            throw new UnsupportedOperationException();
        }

        @Override
        public void setSqlTypeName(String sqlTypeName)
        {
            checkLocked();
            throw new UnsupportedOperationException();
        }

        @Override
        public void setSortFieldKeys(List<FieldKey> sortFieldKeys)
        {
            checkLocked();
            throw new UnsupportedOperationException();
        }

        @Override
        public void setJdbcType(JdbcType jdbcType_)
        {
            checkLocked();
            delegate = new AbstractWrappedColumnInfo(delegate)
            {
                JdbcType jdbcType = jdbcType_;

                @Override
                public JdbcType getJdbcType()
                {
                    return jdbcType;
                }

                @Override
                public String getSqlTypeName ()
                {
                    return getSqlDialect().getSqlTypeName(jdbcType);
                }
            };
        }

        @Override
        public void clearFk()
        {
            checkLocked();
            delegate = new AbstractWrappedColumnInfo(delegate)
            {
                @Override
                public ForeignKey getFk()
                {
                    return null;
                }

                @Override
                public TableInfo getFkTableInfo()
                {
                    return null;
                }
            };
        }

        @Override
        public void setFk(@Nullable ForeignKey fk_)
        {
            checkLocked();
            delegate = new AbstractWrappedColumnInfo(delegate)
            {
                final ForeignKey fk = fk_;
                @Override
                public ForeignKey getFk()
                {
                    return fk;
                }

                @Override
                public TableInfo getFkTableInfo()
                {
                    return null;
                }
            };
        }

        @Override
        public void setFk(@NotNull Builder<ForeignKey> b)
        {
            setFk(b.build());
        }

        @Override
        public void setKeyField(boolean keyField_)
        {
            checkLocked();
            delegate = new AbstractWrappedColumnInfo(delegate)
            {
                final boolean keyField = keyField_;

                @Override
                public boolean isKeyField()
                {
                    return keyField;
                }
            };
        }

        @Override
        public void setMvColumnName(FieldKey mvColumnName)
        {
            checkLocked();
            throw new UnsupportedOperationException();
        }

        @Override
        public void setMvIndicatorColumn(boolean mvIndicatorColumn)
        {
            checkLocked();
            throw new UnsupportedOperationException();
        }

        @Override
        public void setRawValueColumn(boolean rawColumn)
        {
            checkLocked();
            throw new UnsupportedOperationException();
        }

        @Override
        public void setIsUnselectable(boolean unselectable_)
        {
            checkLocked();
            delegate = new AbstractWrappedColumnInfo(delegate)
            {
                final boolean unselectable = unselectable_;

                @Override
                public boolean isUnselectable()
                {
                    return unselectable;
                }
            };
        }

        @Override
        public void setParentTable(TableInfo parentTable)
        {
            checkLocked();
            throw new UnsupportedOperationException();
        }

        @Override
        public void setDefaultValueType(DefaultValueType defaultValueType)
        {
            checkLocked();
            throw new UnsupportedOperationException();
        }

        @Override
        public void setConditionalFormats(@NotNull List<ConditionalFormat> formats)
        {
            checkLocked();
            throw new UnsupportedOperationException();
        }

        @Override
        public void setValidators(List<? extends IPropertyValidator> validators)
        {
            checkLocked();
            throw new UnsupportedOperationException();
        }

        @Override
        public void setCalculated(boolean calculated_)
        {
            checkLocked();
            delegate = new AbstractWrappedColumnInfo(delegate)
            {
                final boolean calculated = calculated_;

                @Override
                public boolean isCalculated()
                {
                    return calculated;
                }
            };
        }

        @Override
        public void setColumnLogging(ColumnLogging columnLogging_)
        {
            checkLocked();
            delegate = new AbstractWrappedColumnInfo(delegate)
            {
                final ColumnLogging columnLogging = columnLogging_;

                @Override
                public ColumnLogging getColumnLogging()
                {
                    return columnLogging;
                }
            };
        }
    }


    public static class TestCase extends Assert
    {
        @Test
        public void readonly()
        {

        }

        @Test
        public void mutable()
        {

        }

        @Test
        public void xml()
        {

        }
    }
}
