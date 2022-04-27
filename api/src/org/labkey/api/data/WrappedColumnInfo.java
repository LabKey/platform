package org.labkey.api.data;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.dataiterator.SimpleTranslator;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.property.IPropertyValidator;
import org.labkey.api.gwt.client.DefaultScaleType;
import org.labkey.api.gwt.client.DefaultValueType;
import org.labkey.api.gwt.client.FacetingBehaviorType;
import org.labkey.api.query.FieldKey;
import org.labkey.api.util.StringExpression;

import java.util.List;
import java.util.Map;
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
     * NOTE: BaseColumnInfo.copyAttributesFrom() does not copy displayField/filterField, here we drop it if the parentTables don't match
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

            @Override
            public TableDescription getFkTableDescription()
            {
                return delegate.getFkTableDescription();
            }
        };
        return new MutableColumnInfoWrapper(inner);
    }
     */


    /*
     * Instead of delegating all getters(), use an ExprColumn, this is useful when you want to set a LOT of properties
     * e.g. PropertyColumn.copyAttributes()
     */
    public static BaseColumnInfo wrapAsCopy(TableInfo parent, FieldKey fieldKey, ColumnInfo sourceColumnInfo, String label, String alias)
    {
        var ret = new BaseColumnInfo(fieldKey, parent, sourceColumnInfo.getJdbcType())
        {
            @Override
            public SQLFragment getValueSql(String tableAlias)
            {
                return sourceColumnInfo.getValueSql(tableAlias);
            }

            @Override
            public String getSelectName()
            {
                assert getParentTable() instanceof SchemaTableInfo : "Use getValueSql()";
                return sourceColumnInfo.getSelectName();
            }

            @Override
            public void declareJoins(String parentAlias, Map<String, SQLFragment> map)
            {
                sourceColumnInfo.declareJoins(parentAlias, map);
            }
        };
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
        public void setSortDirection(Sort.SortDirection sortDirection)
        {
            checkLocked();
            delegate = new AbstractWrappedColumnInfo(delegate)
            {
                @Override
                public Sort.SortDirection getSortDirection()
                {
                    return sortDirection;
                }
            };
        }

        @Override
        public void setInputType(String inputType)
        {
            checkLocked();
            delegate = new AbstractWrappedColumnInfo(delegate)
            {
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
        public void setLabel(String label)
        {
            checkLocked();
            delegate = new AbstractWrappedColumnInfo(delegate)
            {
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
        public void setDescription(String description)
        {
            checkLocked();
            delegate = new AbstractWrappedColumnInfo(delegate)
            {
                @Override
                public String getDescription()
                {
                    return description;
                }
            };
        }

        @Override
        public void setHidden(boolean hidden)
        {
            checkLocked();
            delegate = new AbstractWrappedColumnInfo(delegate)
            {
                @Override
                public boolean isHidden()
                {
                    return hidden;
                }
            };
        }

        @Override
        public void setShownInDetailsView(boolean shownInDetailsView)
        {
            checkLocked();
            delegate = new AbstractWrappedColumnInfo(delegate)
            {
                @Override
                public boolean isShownInDetailsView()
                {
                    return shownInDetailsView;
                }
            };
        }

        @Override
        public void setShownInInsertView(boolean shownInInsertView)
        {
            checkLocked();
            if (shownInInsertView == delegate.isShownInInsertView())
                return;
            delegate = new AbstractWrappedColumnInfo(delegate)
            {
                @Override
                public boolean isShownInInsertView()
                {
                    return shownInInsertView;
                }
            };
        }

        @Override
        public void setShownInUpdateView(boolean shownInUpdateView)
        {
            checkLocked();
            if (shownInUpdateView == delegate.isShownInUpdateView())
                return;
            delegate = new AbstractWrappedColumnInfo(delegate)
            {
                @Override
                public boolean isShownInUpdateView()
                {
                    return shownInUpdateView;
                }
            };
        }

        @Override
        public void setURL(StringExpression url)
        {
            checkLocked();
            delegate = new AbstractWrappedColumnInfo(delegate)
            {
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
        public void setNameExpression(String nameExpression)
        {
            checkLocked();
            delegate = new AbstractWrappedColumnInfo(delegate)
            {
                @Override
                public String getNameExpression()
                {
                    return nameExpression;
                }
            };
        }

        @Override
        public void setMeasure(boolean measure)
        {
            checkLocked();
            delegate = new AbstractWrappedColumnInfo(delegate)
            {
                @Override
                public boolean isMeasure()
                {
                    return measure;
                }
            };
        }

        @Override
        public void setDimension(boolean dimension)
        {
            checkLocked();
            delegate = new AbstractWrappedColumnInfo(delegate)
            {
                @Override
                public boolean isDimension()
                {
                    return dimension;
                }
            };
        }

        @Override
        public void setNullable(boolean nullable)
        {
            checkLocked();
            delegate = new AbstractWrappedColumnInfo(delegate)
            {
                @Override
                public boolean isNullable()
                {
                    return nullable;
                }
            };
        }

        @Override
        public void setRequired(boolean required)
        {
            checkLocked();
            delegate = new AbstractWrappedColumnInfo(delegate)
            {
                @Override
                public boolean isRequired()
                {
                    return required;
                }
            };
        }

        @Override
        public void setImportAliasesSet(Set<String> importAliases)
        {
            checkLocked();
            delegate = new AbstractWrappedColumnInfo(delegate)
            {
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
        public void setFacetingBehaviorType(FacetingBehaviorType facetingBehaviorType)
        {
            checkLocked();
            delegate = new AbstractWrappedColumnInfo(delegate)
            {
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
        public void setPrecision(int scale)
        {
            checkLocked();
            throw new UnsupportedOperationException();
        }

        @Override
        public void setFieldKey(FieldKey fieldKey)
        {
            checkLocked();
            delegate = new AbstractWrappedColumnInfo(delegate)
            {
                @Override
                public FieldKey getFieldKey()
                {
                    return fieldKey;
                }
            };
        }

        @Override
        public void setAlias(String alias)
        {
            checkLocked();
            delegate = new AbstractWrappedColumnInfo(delegate)
            {
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
        public void setPropertyURI(String propertyURI)
        {
            checkLocked();
            delegate = new AbstractWrappedColumnInfo(delegate)
            {
                @Override
                public String getPropertyURI()
                {
                    return propertyURI;
                }
            };
        }

        @Override
        public void setConceptURI(String conceptURI)
        {
            checkLocked();
            delegate = new AbstractWrappedColumnInfo(delegate)
            {
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
        public void setTextAlign(String textAlign)
        {
            checkLocked();
            delegate = new AbstractWrappedColumnInfo(delegate)
            {
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
        public void setUserEditable(boolean editable)
        {
            checkLocked();
            if (editable == delegate.isUserEditable())
                return;
            delegate = new AbstractWrappedColumnInfo(delegate)
            {
                @Override
                public boolean isUserEditable()
                {
                    return editable;
                }
            };
        }

        @Override
        public void setDisplayColumnFactory(DisplayColumnFactory factory)
        {
            checkLocked();
            delegate = new AbstractWrappedColumnInfo(delegate)
            {
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
        public void setAutoIncrement(boolean autoIncrement)
        {
            checkLocked();
            delegate = new AbstractWrappedColumnInfo(delegate)
            {
                @Override
                public boolean isAutoIncrement()
                {
                    return autoIncrement;
                }
            };
        }

        @Override
        public void setReadOnly(boolean readOnly)
        {
            checkLocked();
            if (readOnly == delegate.isReadOnly())
                return;
            delegate = new AbstractWrappedColumnInfo(delegate)
            {
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
        public void setJdbcType(JdbcType jdbcType)
        {
            checkLocked();
            delegate = new AbstractWrappedColumnInfo(delegate)
            {
                @Override
                public @NotNull JdbcType getJdbcType()
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
            };
        }

        @Override
        public void setFk(@Nullable ForeignKey fk)
        {
            checkLocked();
            delegate = new AbstractWrappedColumnInfo(delegate)
            {
                @Override
                public ForeignKey getFk()
                {
                    return fk;
                }
            };
        }

        @Override
        public void setFk(@NotNull Builder<ForeignKey> b)
        {
            setFk(b.build());
        }

        @Override
        public void setKeyField(boolean keyField)
        {
            checkLocked();
            delegate = new AbstractWrappedColumnInfo(delegate)
            {
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
        public void setIsUnselectable(boolean unselectable)
        {
            checkLocked();
            delegate = new AbstractWrappedColumnInfo(delegate)
            {
                @Override
                public boolean isUnselectable()
                {
                    return unselectable;
                }
            };
        }

        @Deprecated
        @Override
        public void setParentTable(TableInfo parentTable)
        {
            checkLocked();
            delegate = new AbstractWrappedColumnInfo(delegate)
            {
                @Override
                public TableInfo getParentTable()
                {
                    return parentTable;
                }
            };
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
        public void setCalculated(boolean calculated)
        {
            checkLocked();
            delegate = new AbstractWrappedColumnInfo(delegate)
            {
                @Override
                public boolean isCalculated()
                {
                    return calculated;
                }
            };
        }

        @Override
        public void setColumnLogging(ColumnLogging columnLogging)
        {
            checkLocked();
            delegate = new AbstractWrappedColumnInfo(delegate)
            {
                @Override
                public ColumnLogging getColumnLogging()
                {
                    return columnLogging;
                }
            };
        }

        @Override
        public void setHasDbSequence(boolean b)
        {
            throw new java.lang.UnsupportedOperationException();
        }

        @Override
        public void setIsRootDbSequence(boolean b)
        {
            throw new java.lang.UnsupportedOperationException();
        }

        @Override
        public void setRemapMissingBehavior(SimpleTranslator.RemapMissingBehavior missingBehavior)
        {
            checkLocked();
            delegate = new AbstractWrappedColumnInfo(delegate)
            {
                @Override
                public SimpleTranslator.RemapMissingBehavior getRemapMissingBehavior()
                {
                    return missingBehavior;
                }
            };
        }

        @Override
        public void setPrincipalConceptCode(String code)
        {
            checkLocked();
            delegate = new AbstractWrappedColumnInfo(delegate)
            {
                @Override
                public String getPrincipalConceptCode()
                {
                    return code;
                }
            };
        }

        @Override
        public void setSourceOntology(String abbr)
        {
            throw new java.lang.UnsupportedOperationException();
        }

        @Override
        public void setConceptSubtree(String path)
        {
            throw new java.lang.UnsupportedOperationException();
        }

        @Override
        public void setConceptImportColumn(String name)
        {
            throw new java.lang.UnsupportedOperationException();
        }

        @Override
        public void setConceptLabelColumn(String name)
        {
            throw new java.lang.UnsupportedOperationException();
        }

        @Override
        public void setDerivationDataScope(String scope)
        {
            throw new java.lang.UnsupportedOperationException();
        }

        @Override
        public void setScannable(boolean scannable)
        {
            checkLocked();
            throw new UnsupportedOperationException();
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
