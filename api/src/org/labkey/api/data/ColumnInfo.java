/*
 * Copyright (c) 2004-2017 Fred Hutchinson Cancer Research Center
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

package org.labkey.api.data;

import org.apache.commons.beanutils.ConversionException;
import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.collections4.multimap.ArrayListValuedHashMap;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.collections.NamedObjectList;
import org.labkey.api.data.JdbcMetaDataSelector.JdbcMetaDataResultSetFactory;
import org.labkey.api.data.dialect.ColumnMetaDataReader;
import org.labkey.api.data.dialect.ForeignKeyResolver;
import org.labkey.api.data.dialect.JdbcMetaDataLocator;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.IPropertyValidator;
import org.labkey.api.exp.property.ValidatorKind;
import org.labkey.api.gwt.client.DefaultScaleType;
import org.labkey.api.gwt.client.DefaultValueType;
import org.labkey.api.gwt.client.FacetingBehaviorType;
import org.labkey.api.query.AliasManager;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.PdLookupForeignKey;
import org.labkey.api.query.QueryParseException;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserIdRenderer;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.StringExpression;
import org.labkey.api.util.StringExpressionFactory;
import org.labkey.api.util.StringExpressionFactory.FieldKeyStringExpression;
import org.labkey.data.xml.ColumnType;
import org.labkey.data.xml.PropertiesType;

import java.beans.Introspector;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Represents a column (be it a real column in a table or a calculated expression) that's part of
 * a {@link TableInfo}. Knows how to generate SQL to get its own value.
 */
public class ColumnInfo extends ColumnRenderProperties
{
    public static final String DEFAULT_PROPERTY_URI_PREFIX = "http://terms.fhcrc.org/dbschemas/";

    public static final DisplayColumnFactory DEFAULT_FACTORY = new DisplayColumnFactory()
    {
        public DisplayColumn createRenderer(ColumnInfo colInfo)
        {
            if (isUserId(colInfo))
            {
                return new UserIdRenderer(colInfo);
            }
            // TODO: PropertyType.FILE_LINK
            else if (colInfo.getPropertyType() == PropertyType.ATTACHMENT)
            {
                return new AttachmentDisplayColumn(colInfo);
            }


            DataColumn dataColumn = new DataColumn(colInfo);
            if (colInfo.getPropertyType() == PropertyType.MULTI_LINE)
                dataColumn.setPreserveNewlines(true);

            if (colInfo.getFk() instanceof MultiValuedForeignKey)
            {
                return new MultiValuedDisplayColumn(dataColumn, true);
            }
            return dataColumn;
        }

        private boolean isUserId(ColumnInfo col)
        {
            if (col.getJdbcType() != JdbcType.INTEGER)
                return false;
            if (col.getFk() instanceof PdLookupForeignKey)
            {
                PdLookupForeignKey lfk = (PdLookupForeignKey)col.getFk();
                if ("core".equals(lfk.getLookupSchemaName()) && "users".equals(lfk.getLookupTableName()))
                    return true;
            }
            return false;
        }
    };
    public static final DisplayColumnFactory NOWRAP_FACTORY = colInfo ->
    {
        DataColumn dataColumn = new DataColumn(colInfo);
        dataColumn.setNoWrap(true);
        return dataColumn;
    };
    public static final DisplayColumnFactory NOLOOKUP_FACTORY = colInfo -> new DataColumn(colInfo, false);


    private static final Logger _log = Logger.getLogger(ColumnInfo.class);
    private static final Set<String> nonEditableColNames = new CaseInsensitiveHashSet("created", "createdBy", "modified", "modifiedBy", "_ts", "entityId", "container");

    private FieldKey fieldKey;
    private String name;
    private String alias;
    private String sqlTypeName;
    private JdbcType jdbcType = null;
    private String textAlign = null;
    private ForeignKey fk = null;
    private String defaultValue = null;
    private String jdbcDefaultValue = null;  // TODO: Merge with defaultValue, see #17646
    private boolean isAutoIncrement = false;
    private boolean isKeyField = false;
    private boolean isReadOnly = false;
    private boolean isUserEditable = true;
    private boolean isUnselectable = false;
    private TableInfo parentTable = null;
    protected String metaDataName = null;
    protected String selectName = null;
    protected ColumnInfo displayField;
    private List<FieldKey> sortFieldKeys = null;
    private Map<FieldKey, ColumnInfo> cachedSortColumns = new HashMap<>();
    private List<ConditionalFormat> conditionalFormats = new ArrayList<>();
    private List<? extends IPropertyValidator> validators = Collections.emptyList();

    private DisplayColumnFactory _displayColumnFactory = DEFAULT_FACTORY;
    private boolean _shouldLog = true;
    private boolean _lockName = false;

    /**
     * True if this column isn't really part of the database. It might be a calculated value, or an alternate
     * representation of another "real" ColumnInfo, like a wrapped column.
     */
    private boolean _calculated = false;

    // Only set if we have an associated mv column for this column
    private FieldKey _mvColumnName = null;
    // indicates that this is an mv column for another column
    private boolean _isMvIndicatorColumn = false;
    private boolean _isRawValueColumn = false;

    // Default column logging is no logging;
    private ColumnLogging _columnLogging;

    // Always call this constructor
    public ColumnInfo(FieldKey key, TableInfo parentTable)
    {
        this.fieldKey = key;
        this.parentTable = parentTable;
        _columnLogging = new ColumnLogging(key, parentTable);
    }

    public ColumnInfo(FieldKey key)
    {
        this(key, null);
        this.name = null;
    }

    public ColumnInfo(String name)
    {
        this(null != name ? new FieldKey(null,name) : null, null);
//        assert -1 == name.indexOf('/');
    }

    public ColumnInfo(String name, JdbcType t)
    {
        this(null != name ? new FieldKey(null,name) : null, null);
        if (null == name)
            return;
//        assert -1 == name.indexOf('/');
        jdbcType = t;
    }

    public ColumnInfo(String name, JdbcType t, int scale, boolean nullable)
    {
        this(null != name ? new FieldKey(null,name) : null, null);
        if (null == name)
            return;
//        assert -1 == name.indexOf('/');
        setJdbcType(t);
        setScale(scale);
        setNullable(nullable);
    }


    public ColumnInfo(ResultSetMetaData rsmd, int col) throws SQLException
    {
        this(new FieldKey(null, rsmd.getColumnLabel(col)), null);
        this.setSqlTypeName(rsmd.getColumnTypeName(col));
        this.jdbcType = JdbcType.valueOf(rsmd.getColumnType(col));
        this.setAlias(rsmd.getColumnName(col));
    }

    public ColumnInfo(String name, TableInfo parentTable)
    {
//        assert -1 == name.indexOf('/');
        this(new FieldKey(null, name), parentTable);
    }

    public ColumnInfo(FieldKey key, TableInfo parentTable, JdbcType type)
    {
        this(key, parentTable);
        this.setJdbcType(type);
    }

    public ColumnInfo(ColumnInfo from)
    {
        this(from, from.getParentTable());
    }


    public ColumnInfo(ColumnInfo from, TableInfo parent)
    {
        this(from.getFieldKey(), parent);
        copyAttributesFrom(from);
        copyURLFrom(from, null, null);
    }


    /* used by TableInfo.addColumn */
    public boolean lockName()
    {
        _lockName = true;
        return true;
    }


    /** use setFieldKey() avoid ambiguity when columns have "/" */
    public void setName(String name)
    {
        checkLocked();
        // Disallow changing the name completely -- fixing up the casing is allowed;
        // but since fieldKey holds true name (and this.name could be null), check it there
        FieldKey newFieldKey = new FieldKey(null, name);
        assert !_lockName || 0 == this.fieldKey.compareTo(newFieldKey);
        this.fieldKey = newFieldKey;
        this.name = null;
    }


    public String getName()
    {
        if (this.name == null && this.fieldKey != null)
        {
            if (this.fieldKey.getParent() == null)
                this.name = this.fieldKey.getName();
            else
                this.name = this.fieldKey.toString();
        }
        return this.name;
    }


    public void setFieldKey(FieldKey key)
    {
        checkLocked();
        this.fieldKey = key;
        this.name = null;
    }


    public FieldKey getFieldKey()
    {
        return fieldKey;
    }


    // use only for debugging, will change after call to getAlias()
    public boolean isAliasSet()
    {
        return null != this.alias;
    }


    public String getAlias()
    {
        if (alias == null)
            alias = AliasManager.makeLegalName(getFieldKey(), getSqlDialect(), false);
        return alias;
    }


    public void setAlias(String alias)
    {
        checkLocked();
        this.alias = alias;
    }


    public void copyAttributesFrom(ColumnInfo col)
    {
        checkLocked();
        setExtraAttributesFrom(col);

        // and the remaining
        setUserEditable(col.isUserEditable());
        setNullable(col.isNullable());
        setRequired(col.isRequiredSet());
        setAutoIncrement(col.isAutoIncrement());
        setScale(col.getScale());
        this.sqlTypeName = col.sqlTypeName;
        this.jdbcType = col.jdbcType;
        this.propertyType = col.propertyType;

        // We intentionally do not copy "isHidden", since it is usually not applicable.
        // URL copy/rewrite is handled separately

        // Consider: it does not always make sense to preserve the "isKeyField" property.
        setKeyField(col.isKeyField());
        setColumnLogging(col.getColumnLogging());
    }


    /*
     * copy "non-core" attributes, e.g. leave key and type information alone
     */
    public void setExtraAttributesFrom(ColumnInfo col)
    {
        checkLocked();
        if (col.label != null)
            setLabel(col.getLabel());
        if (col.shortLabel != null)
            setShortLabel(col.getShortLabel());
        setJdbcDefaultValue(col.getJdbcDefaultValue());
        setDescription(col.getDescription());
        if (col.isFormatStringSet())
            setFormat(col.getFormat());
        if (col.getExcelFormatString() != null)
            setExcelFormatString(col.getExcelFormatString());
        if (col.getTsvFormatString() != null)
            setTsvFormatString(col.getTsvFormatString());
        setTextExpression(col.getTextExpression());
        // Don't call the getter, because if it hasn't been explicitly set we want to
        // fetch the value lazily so we don't have to traverse FKs to get the display
        // field at this point.
        setInputLength(col.inputLength);
        setInputType(col.inputType);

        setInputRows(col.getInputRows());
        if (!isKeyField() && !col.isNullable())
            setNullable(col.isNullable());
        setRequired(col.required);
        setReadOnly(col.isReadOnly);
        setDisplayColumnFactory(col.getDisplayColumnFactory());
        setTextAlign(col.getTextAlign());
        setWidth(col.getWidth());
        setFk(col.getFk());
        setPropertyURI(col.getPropertyURI());
        setSortFieldKeys(col.getSortFieldKeys());
        if (col.getConceptURI() != null)
            setConceptURI(col.getConceptURI());
        if (col.getRangeURI() != null)
            setRangeURI(col.getRangeURI());
        setIsUnselectable(col.isUnselectable());
        setDefaultValueType(col.getDefaultValueType());
        setDefaultValue(col.getDefaultValue());
        setImportAliasesSet(col.getImportAliasSet());
        setShownInDetailsView(col.isShownInDetailsView());
        setShownInInsertView(col.isShownInInsertView());
        setShownInUpdateView(col.isShownInUpdateView());
        setConditionalFormats(col.getConditionalFormats());
        setValidators(col.getValidators());

        // Intentionally do not use set/get methods for dimension and measure, since the set/get methods
        // hide the fact that these values can be null internally. It's important to preserve the notion
        // of unset values on the new columninfo.
        measure = col.measure;
        dimension = col.dimension;

        setRecommendedVariable(col.isRecommendedVariable());
        setDefaultScale(col.getDefaultScale());
        setMvColumnName(col.getMvColumnName());
        setRawValueColumn(col.isRawValueColumn());
        setMvIndicatorColumn(col.isMvIndicatorColumn());
        setFacetingBehaviorType(col.getFacetingBehaviorType());
        setExcludeFromShifting(col.isExcludeFromShifting());
        setPHI(col.getPHI());
        setShouldLog(col.isShouldLog());

        setCrosstabColumnDimension(col.getCrosstabColumnDimension());
        setCrosstabColumnMember(col.getCrosstabColumnMember());

        setCalculated(col.isCalculated());
    }


    /**
     * copy the url string expression from col with the specified rewrites
     * @param col source of the url StringExpression
     * @param parent FieldKey to prepend to any FieldKeys in the source expression (unless explicitly mapped), may be null
     * @param remap explicit list of FieldKey mappings, may be null
     *
     * Example
     *   given col.url = "?id=${RowId}&title=${Title}"
     *
     * copyURLFrom(col, "Discussion", null) --> "?id=${discussion/RowId}&title=${discussion/Title}
     * copyURLFrom(Col, null, {("RowId","Run")}) --> "?id=${Run}&title=${Title}
     */
    public void copyURLFrom(ColumnInfo col, @Nullable FieldKey parent, @Nullable Map<FieldKey,FieldKey> remap)
    {
        checkLocked();
        StringExpression url = col.getURL();
        if (null != url)
        {
            if (url != AbstractTableInfo.LINK_DISABLER)
            {
                if ((null != parent || null != remap) && url instanceof FieldKeyStringExpression)
                    url = ((FieldKeyStringExpression)url).remapFieldKeys(parent, remap);
                else
                    url = url.copy();
            }
            setURL(url);
        }
        setURLTargetWindow(col.getURLTargetWindow());
        setURLCls(col.getURLCls());
    }


    /* only copy if all field keys are in the map */
    public void copyURLFromStrict(ColumnInfo col, Map<FieldKey,FieldKey> remap)
    {
        checkLocked();
        StringExpression url = col.getURL();
        if (url instanceof FieldKeyStringExpression)
        {
            FieldKeyStringExpression fkse = (FieldKeyStringExpression)url;
            if (fkse.validateFieldKeys(remap.keySet()))
            {
                StringExpression mapped = (fkse).remapFieldKeys(null, remap);
                setURL(mapped);
            }
        }
    }


    public void setMetaDataName(String metaDataName)
    {
        checkLocked();
        this.metaDataName = metaDataName;
    }

    public String getMetaDataName()
    {
        return metaDataName;      // Actual name returned by metadata; use to query meta data or to select columns enclosed in quotes
    }


    public String getSelectName()
    {
        if (null == selectName)
        {
            if (null == getMetaDataName())
                selectName = getSqlDialect().getColumnSelectName(getName());
            else
                selectName = getSqlDialect().getColumnSelectName(getMetaDataName());
        }

        return selectName;
    }

    public SQLFragment getValueSql(String tableAliasName)
    {
        return new SQLFragment(tableAliasName + "." + getSelectName());
    }

    public String getPropertyURI()
    {
        if (null == propertyURI && null != getParentTable())
            propertyURI = DEFAULT_PROPERTY_URI_PREFIX + getParentTable().getSchema().getName() + "#" + getParentTable().getName() + "." + PageFlowUtil.encode(getName());
        return propertyURI;
    }

    public void setPropertyURI(String propertyURI)
    {
        checkLocked();
        this.propertyURI = propertyURI;
    }

    public void setConceptURI(String conceptURI)
    {
        checkLocked();
        this.conceptURI = conceptURI;
    }

    public void setRangeURI(String rangeURI)
    {
        checkLocked();
        this.rangeURI = rangeURI;
    }

    public void declareJoins(String parentAlias, Map<String, SQLFragment> map)
    {
    }

    public String getTableAlias(String baseAlias)
    {
        return parentTable.getName();
    }

    public SqlDialect getSqlDialect()
    {
        if (parentTable == null)
            return null;
        return parentTable.getSqlDialect();
    }


    // Return the actual value we have stashed; use this when copying attributes, so you don't hard-code label
    public String getLabelValue()
    {
        return label;
    }


    @Override
    public String getLabel()
    {
        if (null == label && getFieldKey() != null)
            return labelFromName(getFieldKey().getName());
        return label;
    }


    public boolean isFormatStringSet()
    {
        return format != null;
    }

    public String getTextAlign()
    {
        if (textAlign != null)
            return textAlign;
        return isStringType() || isDateTimeType() || isBooleanType() ? "left" : "right";
    }

    public void setTextAlign(String textAlign)
    {
        checkLocked();
        this.textAlign = textAlign;
    }

    public String getJdbcDefaultValue()
    {
        return jdbcDefaultValue;
    }

    public void setJdbcDefaultValue(String jdbcDefaultValue)
    {
        checkLocked();
        this.jdbcDefaultValue = jdbcDefaultValue;
    }

    public String getDefaultValue()
    {
        return defaultValue;
    }

    public void setDefaultValue(String defaultValue)
    {
        checkLocked();
        this.defaultValue = defaultValue;
    }

    @Nullable
    public ColumnInfo getDisplayField()
    {
        if (displayField != null)
            return displayField;
        ForeignKey fk = getFk();
        if (fk == null)
            return null;
        try
        {
            return fk.createLookupColumn(this, null);
        }
        catch (QueryParseException qpe)
        {
            return null;
        }
    }

    @Nullable
    public List<ColumnInfo> getSortFields()
    {
        if (getParentTable() == null)
            return Collections.singletonList(this);

        List<ColumnInfo> sortCols = new ArrayList<>();
        if (sortFieldKeys != null)
        {
            boolean foundAllInCache = true;
            for (FieldKey sortFieldKey : sortFieldKeys)
            {
                ColumnInfo column = cachedSortColumns.get(sortFieldKey);
                if (column != null)
                {
                    sortCols.add(column);
                }
                else
                {
                    foundAllInCache = false;
                    break;
                }
            }
            if (foundAllInCache)
            {
                return sortCols;
            }

            // The column may be on a separate table via a lookup, so use QueryService to resolve it
            Map<FieldKey, ColumnInfo> columns = QueryService.get().getColumns(getParentTable(), sortFieldKeys);
            if (columns.size() != sortFieldKeys.size() || columns.values().contains(null))
            {
                //if we cannot resolve any of the intended columns, rather than proceed
                // with just 1 of them, default back to the original column
                StringBuilder msg = new StringBuilder("Unable to resolve one or more sortFieldKeys for column: " + getFieldKey() + " on table: " + (getParentTable() != null ? getParentTable().getName() : "") + ".  The fieldKeys are: ");
                msg.append(StringUtils.join(sortFieldKeys, ","));
                _log.warn(msg);

                sortCols = new ArrayList<>();
                sortCols.add(this);
            }
            else
            {
                for (FieldKey fk : sortFieldKeys)
                {
                    ColumnInfo column = columns.get(fk);
                    cachedSortColumns.put(fk, column);
                    sortCols.add(column);
                }
            }
        }
        else
        {
            sortCols.add(this);
        }

        if (sortCols.size() > 0)
        {
            for (ColumnInfo sortCol : sortCols)
            {
                //is this the right place to do this check?
                if (!getParentTable().getSqlDialect().isSortableDataType(sortCol.getSqlTypeName()))
                    return null;
            }

            return sortCols;
        }

        return null;
    }

    public ColumnInfo getFilterField()
    {
        return this;
    }

    final public boolean equals(Object obj)
    {
        return super.equals(obj);
    }

    public boolean isNoWrap()
    {
        // NOTE: most non-string types don't have spaces after conversion except dates
        // let's make sure they don't wrap (bug 392)
        return java.util.Date.class.isAssignableFrom(getJdbcType().cls) || isNumericType();
    }

    public void setDisplayField(ColumnInfo field)
    {
        checkLocked();
        displayField = field;
    }

    public void setWidth(String width)
    {
        checkLocked();
        this.displayWidth = width;
    }

    public String getWidth()
    {
        if (null != displayWidth)
            return displayWidth;

//        This is the DisplayColumn's job to swap display type, shouldn't do this here
//        Also, this causes table construction recursion in the case of self-join
//        if (fk != null)
//        {
//            ColumnInfo fkTitleColumn = getDisplayField();
//            if (null != fkTitleColumn && fkTitleColumn != this)
//                return displayWidth = fkTitleColumn.getWidth();
//        }

        if (isStringType())
            return displayWidth = String.valueOf(Math.max(10, Math.min(getScale() * 6, 200)));
        else if (isDateTimeType())
            return displayWidth = "90";
        else
            return displayWidth = "60";
    }

    public TableInfo getFkTableInfo()
    {
        if (null == getFk())
            return null;
        return getFk().getLookupTableInfo();
    }


    public boolean isUserEditable()
    {
        return isUserEditable;
    }


    public void setUserEditable(boolean editable)
    {
        checkLocked();
        this.isUserEditable = editable;
    }


    public void setDisplayColumnFactory(DisplayColumnFactory factory)
    {
        checkLocked();
        _displayColumnFactory = factory;
    }

    public DisplayColumnFactory getDisplayColumnFactory()
    {
        return _displayColumnFactory;
    }

    public boolean isShouldLog()
    {
        return _shouldLog;
    }

    public void setShouldLog(boolean shouldLog)
    {
        checkLocked();
        _shouldLog = shouldLog;
    }

    public String getLegalName()
    {
        return legalNameFromName(getName());
    }

    public String getPropertyName()
    {
        return propNameFromName(getName());
    }

    /**
     * See {@link #jdbcRsNameFromName(String) }
     *
     */
    public String getJdbcRsName() { return jdbcRsNameFromName(getName()); }

    /**
     * Version column can be used for optimistic concurrency.
     * for now we assume that this column is never updated
     * explicitly.
     */
    public boolean isVersionColumn()
    {
        if ("_ts".equalsIgnoreCase(getName()) || "Modified".equalsIgnoreCase(getName()))
            return true;
        return JdbcType.BINARY == getJdbcType() && 8 == getScale() && "timestamp".equals(getSqlTypeName());
    }


    public SQLFragment getVersionUpdateExpression()
    {
        if (JdbcType.TIMESTAMP == getJdbcType())
        {
            return new SQLFragment("CURRENT_TIMESTAMP");   // Instead of {fn now()} -- see #27534
        }
        else if ("_ts".equalsIgnoreCase(getName()) && !getSqlDialect().isSqlServer() && JdbcType.BIGINT == getJdbcType())
        {
            TableInfo t = getParentTable();
            return new SQLFragment("nextval('" + t.getSelectName() + "_ts')");
        }
        return null;
    }


    public String getInputType()
    {
        if (null == inputType)
        {
            if (getPropertyType() != null && getPropertyType().getInputType() != null)
                inputType = getPropertyType().getInputType();
            else if (isStringType() && scale > 300) // lsidtype is 255 characters
                inputType = "textarea";
            else if ("image".equalsIgnoreCase(getSqlTypeName()))
                inputType = "file";
            else if (getJdbcType() == JdbcType.BOOLEAN)
                inputType = "checkbox";
            else
                inputType = "text";
        }
        return inputType;
    }


    @Override
    public int getInputLength()
    {
        if (-1 == inputLength)
        {
            if (getInputType().equalsIgnoreCase("textarea"))
                inputLength = 60;
            else
                inputLength = scale > 40 ? 40 : scale;
        }

        return inputLength;
    }


    @Override
    public int getInputRows()
    {
        if (-1 == inputRows && isStringType())
            return 15;
        return inputRows;
    }

    public boolean isAutoIncrement()
    {
        return isAutoIncrement;
    }

    public void setAutoIncrement(boolean autoIncrement)
    {
        checkLocked();
        isAutoIncrement = autoIncrement;
    }

    public boolean isReadOnly()
    {
        return isReadOnly || isAutoIncrement || isVersionColumn();
    }

    public void setReadOnly(boolean readOnly)
    {
        checkLocked();
        isReadOnly = readOnly;
    }

    public StringExpression getEffectiveURL()
    {
        StringExpression result = super.getURL();
        if (result != null)
            return result;
        ForeignKey fk = getFk();
        if (fk == null)
            return null;

        try
        {
            return fk.getURL(this);
        }
        catch (QueryParseException qpe)
        {
            return null;
        }
    }

    public void copyToXml(ColumnType xmlCol, boolean full)
    {
        xmlCol.setColumnName(getName());
        if (full)
        {
            if (fk instanceof SchemaForeignKey)
            {
                SchemaForeignKey sfk = (SchemaForeignKey) fk;
                org.labkey.data.xml.ColumnType.Fk xmlFk = xmlCol.addNewFk();
                xmlFk.setFkColumnName(sfk.getLookupColumnName());
                xmlFk.setFkTable(sfk._tableName);
                DbSchema fkDbOwnerSchema = sfk.getLookupTableInfo().getSchema().getScope().getSchema(sfk._dbSchemaName, DbSchemaType.Unknown);

                if (fkDbOwnerSchema != getParentTable().getSchema())
                {
                    xmlFk.setFkDbSchema(fkDbOwnerSchema.getName());
                }
            }

            // changed the following to not invoke getters with code, and only write out non-default values
            if (null != inputType)
                xmlCol.setInputType(inputType);

            if (-1 != inputLength)
                xmlCol.setInputLength(inputLength);

            if (-1 != inputRows)
                xmlCol.setInputRows(inputRows);
            if (null != url)
                xmlCol.setUrl(url.toXML());

            if (isReadOnly)
                xmlCol.setIsReadOnly(isReadOnly);
            if (!isUserEditable)
                xmlCol.setIsUserEditable(isUserEditable);
            if (hidden)
                xmlCol.setIsHidden(hidden);
            if (isUnselectable)
                xmlCol.setIsUnselectable(isUnselectable);
            if (null != label)
                xmlCol.setColumnTitle(label);
            if (nullable)
                xmlCol.setNullable(nullable);
            if (null != sqlTypeName)
                xmlCol.setDatatype(sqlTypeName);
            if (isAutoIncrement)
                xmlCol.setIsAutoInc(isAutoIncrement);
            if (scale != 0)
                xmlCol.setScale(scale);
            if (null != defaultValue)
                xmlCol.setDefaultValue(defaultValue);
            if (null != getDisplayWidth())
                xmlCol.setDisplayWidth(getDisplayWidth());
            if (null != format)
                xmlCol.setFormatString(format);
            if (null != textAlign)
                xmlCol.setTextAlign(textAlign);
            if (null != description)
                xmlCol.setDescription(description);

            // Note: This is only called on JDBC meta data, so we don't bother with PHI, faceting behavior, and other
            // external meta data. But perhaps this code could be shared with TableInfoWriter?
        }
    }


    public void loadFromXml(ColumnType xmlCol, boolean merge)
    {
        checkLocked();

        if (xmlCol.isSetConceptURI())
            setConceptURI(xmlCol.getConceptURI());
        if (xmlCol.isSetRangeURI())
            rangeURI = xmlCol.getRangeURI();

        //Following things would exist from meta data...
        if (! merge)
        {
            PropertyType pt = null;
            if (conceptURI != null || rangeURI != null)
                pt = PropertyType.getFromURI(conceptURI, rangeURI, null);

            // Initialize properties based on rangeURI PropertyType
            if (pt != null)
            {
                propertyType = pt;
                jdbcType = propertyType.getJdbcType();
                sqlTypeName = getSqlDialect().sqlTypeNameFromJdbcType(jdbcType);
                inputType = propertyType.getInputType();
                scale = propertyType.getScale();
            }

            if (xmlCol.isSetDatatype())
            {
                sqlTypeName = xmlCol.getDatatype();
            }
        }

        if ((!merge || null == fk) && xmlCol.getFk() != null)
        {
            ColumnType.Fk xfk = xmlCol.getFk();
            DbSchema schema = getParentTable().getSchema();
            ForeignKeyResolver resolver = getSqlDialect().getForeignKeyResolver(schema.getScope(), schema.getName(), getParentTable().getName());
            String fkSchema = xfk.getFkDbSchema();
            ImportedKey key = resolver.getImportedKey(null, null != fkSchema ? fkSchema : schema.getName(), xfk.getFkTable(), xfk.getFkColumnName(), null);

            if (!xfk.isSetFkMultiValued())
            {
                String displayColumnName = null;
                boolean useRawFKValue = false;
                if (xfk.isSetFkDisplayColumnName())
                {
                    displayColumnName = xfk.getFkDisplayColumnName().getStringValue();
                    useRawFKValue = xfk.getFkDisplayColumnName().getUseRawValue();
                }
                fk = new SchemaForeignKey(this, key.pkSchemaName, key.pkTableName, key.pkColumnNames.get(0), false, displayColumnName, useRawFKValue);
            }
            else
            {
                String type = xfk.getFkMultiValued();

                if ("junction".equals(type))
                    fk = new MultiValuedForeignKey(new SchemaForeignKey(this, key.pkSchemaName, key.pkTableName, key.pkColumnNames.get(0), false), xfk.getFkJunctionLookup());
                else
                    throw new UnsupportedOperationException("Non-junction multi-value columns NYI");
            }
        }

        setFieldKey(new FieldKey(null, xmlCol.getColumnName()));
        if (xmlCol.isSetColumnTitle())
            setLabel(xmlCol.getColumnTitle());
        if (xmlCol.isSetInputLength())
            inputLength = xmlCol.getInputLength();
        if (xmlCol.isSetInputRows())
            inputRows = xmlCol.getInputRows();
        if (xmlCol.isSetInputType())
            inputType = xmlCol.getInputType();
        if (xmlCol.isSetUrl())
            setURL(StringExpressionFactory.fromXML(xmlCol.getUrl(), false));
        if (xmlCol.isSetUrlTarget())
            setURLTargetWindow(xmlCol.getUrlTarget());
        if (xmlCol.isSetIsAutoInc())
            isAutoIncrement = xmlCol.getIsAutoInc();
        if (xmlCol.isSetIsReadOnly())
            isReadOnly = xmlCol.getIsReadOnly();
        if (xmlCol.isSetIsUserEditable())
            isUserEditable = xmlCol.getIsUserEditable();
        if (xmlCol.isSetScale())
            scale = xmlCol.getScale();
        if (xmlCol.isSetDefaultValue())
            defaultValue = xmlCol.getDefaultValue();
        if (xmlCol.isSetFormatString())
            format = xmlCol.getFormatString();
        if (xmlCol.isSetTsvFormatString())
            tsvFormatString = xmlCol.getTsvFormatString();
        if (xmlCol.isSetExcelFormatString())
            excelFormatString = xmlCol.getExcelFormatString();
        if (xmlCol.isSetTextExpression())
            textExpression = new FieldKeyStringExpression(xmlCol.getTextExpression().getStringValue(), false, StringExpressionFactory.AbstractStringExpression.NullValueBehavior.fromXML(xmlCol.getTextExpression().getReplaceMissing()));
        if (xmlCol.isSetTextAlign())
            textAlign = xmlCol.getTextAlign();
        if (xmlCol.isSetPropertyURI())
            propertyURI = xmlCol.getPropertyURI();
        if (xmlCol.isSetSortColumn())
            setSortFieldKeysFromXml(xmlCol.getSortColumn());
        if (xmlCol.isSetSortDescending())
            setSortDirection(xmlCol.getSortDescending() ? Sort.SortDirection.DESC : Sort.SortDirection.ASC);
        if (xmlCol.isSetDescription())
            description = xmlCol.getDescription();
        if (xmlCol.isSetIsHidden())
            hidden = xmlCol.getIsHidden();
        if (xmlCol.isSetShownInInsertView())
            shownInInsertView = xmlCol.getShownInInsertView();
        if (xmlCol.isSetShownInUpdateView())
            shownInUpdateView = xmlCol.getShownInUpdateView();
        if (xmlCol.isSetShownInDetailsView())
            shownInDetailsView = xmlCol.getShownInDetailsView();
        if (xmlCol.isSetDimension())
            dimension = xmlCol.getDimension();
        if (xmlCol.isSetMeasure())
            measure = xmlCol.getMeasure();
        if (xmlCol.isSetRecommendedVariable())
            recommendedVariable = xmlCol.getRecommendedVariable();
        else if (xmlCol.isSetKeyVariable())
            recommendedVariable = xmlCol.getKeyVariable();
        if (xmlCol.isSetDefaultScale())
            defaultScale = DefaultScaleType.valueOf(xmlCol.getDefaultScale().toString());
        if (xmlCol.isSetIsUnselectable())
            isUnselectable = xmlCol.getIsUnselectable();
        if (xmlCol.isSetIsKeyField())
            isKeyField = xmlCol.getIsKeyField();
        if (xmlCol.isSetDisplayWidth())
            setDisplayWidth(xmlCol.getDisplayWidth());
        if (xmlCol.isSetRequired())
            required = xmlCol.getRequired();
        if (xmlCol.isSetNullable())
            nullable = xmlCol.getNullable();
        if (xmlCol.isSetExcludeFromShifting())
            isExcludeFromShifting = xmlCol.getExcludeFromShifting();
        if (xmlCol.isSetImportAliases())
            importAliases.addAll(Arrays.asList(xmlCol.getImportAliases().getImportAliasArray()));
        if (xmlCol.isSetConditionalFormats())
        {
            setConditionalFormats(ConditionalFormat.convertFromXML(xmlCol.getConditionalFormats()));
        }
        if (xmlCol.isSetValidators())
        {
            setValidators(ValidatorKind.convertFromXML(xmlCol.getValidators()));
        }
        if (xmlCol.isSetProtected())  // column is removed from LabKey but need to support old archives, see spec #28920
        {
            if (xmlCol.getProtected())
                phi = PHI.Limited;  // always convert protected to limited PHI; may be overridden by getPhi(), though
        }
        if (xmlCol.isSetPhi())
            phi = PHI.valueOf(xmlCol.getPhi().toString());
        if (xmlCol.isSetFacetingBehavior())
            facetingBehaviorType = FacetingBehaviorType.valueOf(xmlCol.getFacetingBehavior().toString());
        if (xmlCol.isSetDisplayColumnFactory())
        {
            org.labkey.data.xml.ColumnType.DisplayColumnFactory xmlFactory = xmlCol.getDisplayColumnFactory();
            String displayColumnClassName = xmlFactory.getClassName();

            // MultiValuedMap is a little harder for the factories to work with, but it allows the same property
            // multiple times (e.g., multiple "dependency" properties on JavaScriptDisplayColumnFactory)
            MultiValuedMap<String, String> props = null;

            if (xmlFactory.isSetProperties())
            {
                props = new ArrayListValuedHashMap<>();

                for (PropertiesType.Property prop : xmlFactory.getProperties().getPropertyArray())
                    props.put(prop.getName(), prop.getStringValue());
            }

            try
            {
                switch (displayColumnClassName)
                {
                    case "DEFAULT":
                        _displayColumnFactory = DEFAULT_FACTORY;
                        break;
                    case "NOWRAP":
                        _displayColumnFactory = NOWRAP_FACTORY;
                        break;
                    case "NOLOOKUP":
                        _displayColumnFactory = NOLOOKUP_FACTORY;
                        break;
                    default:
                        Class clazz = Class.forName(displayColumnClassName);

                        if (DisplayColumnFactory.class.isAssignableFrom(clazz))
                        {
                            //noinspection unchecked
                            Class<DisplayColumnFactory> factoryClass = (Class<DisplayColumnFactory>) clazz;

                            if (null == props)
                            {
                                Constructor<DisplayColumnFactory> ctor = factoryClass.getConstructor();
                                _displayColumnFactory = ctor.newInstance();
                            }
                            else
                            {
                                Constructor<DisplayColumnFactory> ctor = factoryClass.getConstructor(MultiValuedMap.class);
                                _displayColumnFactory = ctor.newInstance(props);
                            }
                        }
                        else
                        {
                            _log.error("Class is not a DisplayColumnFactory: " + displayColumnClassName);
                        }
                        break;
                }
            }
            catch (ClassNotFoundException | InstantiationException | IllegalAccessException | NoSuchMethodException | InvocationTargetException e)
            {
                _log.error("Can't instantiate DisplayColumnFactory: " + displayColumnClassName, e);
            }
        }
    }

    private void setSortFieldKeysFromXml(String xml)
    {
        List<FieldKey> keys = new ArrayList<>();
        for (String key : xml.split(","))
        {
            keys.add(FieldKey.fromString(key));
        }

        setSortFieldKeys(keys);
    }

    public static String labelFromName(String name)
    {
        if (name == null)
            return null;

        if (name.length() == 0)
            return name;

        StringBuilder buf = new StringBuilder(name.length() + 10);
        char[] chars = new char[name.length()];
        name.getChars(0, name.length(), chars, 0);
        buf.append(Character.toUpperCase(chars[0]));
        for (int i = 1; i < name.length(); i++)
        {
            char c = chars[i];
            if (c == '_' && i < name.length() - 1)
            {
                buf.append(" ");
                i++;
                buf.append(Character.isLowerCase(chars[i]) ? Character.toUpperCase(chars[i]) : chars[i]);
            }
            else if (Character.isUpperCase(c) && Character.isLowerCase(chars[i - 1]))
            {
                buf.append(" ");
                buf.append(c);
            }
            else
            {
                buf.append(c);
            }
        }

        return buf.toString();
    }


    public static String legalNameFromName(String name)
    {
        if (name == null)
            return null;

        if (name.length() == 0)
            return null;

        StringBuilder buf = new StringBuilder(name.length());
        char[] chars = new char[name.length()];
        name.getChars(0, name.length(), chars, 0);
        //Different rule for first character
        int i = 0;
        while (i < name.length() && !Character.isJavaIdentifierStart(chars[i]))
            i++;
        //If no characters are identifier start (i.e. numeric col name), prepend "col" and try again..
        if (i == name.length())
        {
            buf.append("column");
            i = 0;
        }

        for (; i < name.length(); i++)
            if (Character.isJavaIdentifierPart(chars[i]))
                buf.append(chars[i]);

        return buf.toString();
    }

    public static String propNameFromName(String name)
    {
        if (name == null)
            return null;

        if (name.length() == 0)
            return null;

        return Introspector.decapitalize(legalNameFromName(name));
    }

    /**
     *  The jdbc resultset metadata replaces special characters in source column names.
     *  This is a problem when matching source and target columns, as we have the jdbc name for the source.
     *  I haven't found an exhaustive list of the characters and their replacements, but spaces, hyphens, parens,
     *  and forward slashes have been seen in a client db schema and have an issue.
     */
    public static String jdbcRsNameFromName(String name)
    {
        if (StringUtils.isBlank(name))
            return null;

        return name.replaceAll("\\s", "_")
                .replace("-", "_minus_")
                .replace("/", "_fs_")
                .replace("(", "_lp_")
                .replace(")", "_rp_");
    }

    public static boolean booleanFromString(String str)
    {
        if (null == str || str.trim().length() == 0)
            return false;
        if (str.equals("0") || str.equalsIgnoreCase("false"))
            return false;
        if (str.equals("1") || str.equalsIgnoreCase("true"))
            return true;
        try
        {
            return (Boolean)ConvertUtils.convert(str, Boolean.class);
        }
        catch (ConversionException e)
        {
            return false;
        }
    }


    public static boolean booleanFromObj(Object o)
    {
        if (null == o)
            return false;
        if (o instanceof Boolean)
            return (Boolean)o;
        else if (o instanceof Integer)
            return (Integer)o != 0;
        else
            return booleanFromString(o.toString());
    }


    public String toString()
    {
        StringBuilder sb = new StringBuilder(64);

        sb.append("  ");
        sb.append(StringUtils.rightPad(getName(), 25));
        sb.append(" ");

        String typeName = getSqlTypeName();
        sb.append(typeName);

        //UNDONE: Not supporting fixed decimal
        if ("VARCHAR".equalsIgnoreCase(typeName) || "CHAR".equalsIgnoreCase(typeName))
        {
            sb.append("(");
            sb.append(scale);
            sb.append(") ");
        }
        else
            sb.append(" ");

        //SQL Server specific
        if (isAutoIncrement)
            sb.append("IDENTITY ");

        sb.append(nullable ? "NULL" : "NOT NULL");

        if (null != defaultValue)
        {
            sb.append(" DEFAULT ");
            if ("CURRENT_TIMESTAMP".equals(defaultValue))
                sb.append(defaultValue);
            else
            {
                sb.append("'");
                sb.append(defaultValue);
                sb.append("'");
            }
        }

        return sb.toString();
    }

    // UNDONE: Do we still need DomainProperty for this?
    public boolean isRequiredForInsert(@Nullable DomainProperty dp)
    {
        if (isCalculated() || isAutoIncrement() || isVersionColumn() || null != getJdbcDefaultValue())
            return false;
        return !isNullable() || (null != dp && dp.isRequired());
    }

    static public class SchemaForeignKey implements ForeignKey
    {
        private final DbScope _scope;
        private final String _dbSchemaName;
        private final String _tableName;
        private String _lookupKey;
        private final String _displayColumnName;
        private final boolean _joinWithContainer;
        private final boolean _useRawFKValue;

        public SchemaForeignKey(ColumnInfo foreignKey, String dbSchemaName, String tableName, @Nullable String lookupKey, boolean joinWithContainer)
        {
            this(foreignKey, dbSchemaName, tableName, lookupKey, joinWithContainer, null, false);
        }

        public SchemaForeignKey(ColumnInfo foreignKey, String dbSchemaName, String tableName, @Nullable String lookupKey, boolean joinWithContainer, @Nullable String displayColumnName, boolean useRawFKValue)
        {
            _scope = foreignKey.getParentTable().getSchema().getScope();
            _dbSchemaName = dbSchemaName == null ? foreignKey.getParentTable().getSchema().getName() : dbSchemaName;
            _tableName = tableName;
            _lookupKey = lookupKey;
            _joinWithContainer = joinWithContainer;
            _displayColumnName = displayColumnName;
            _useRawFKValue = useRawFKValue;
        }

        public ColumnInfo createLookupColumn(ColumnInfo foreignKey, String displayField)
        {
            TableInfo lookupTable = getLookupTableInfo();
            if (null == lookupTable)
            {
                return null;
            }
            if (foreignKey.getParentTable() != null && foreignKey.getParentTable().supportsContainerFilter() && lookupTable.supportsContainerFilter())
            {
                ContainerFilterable table = (ContainerFilterable) lookupTable;
                if (table.hasDefaultContainerFilter())
                {
                    table.setContainerFilter(new DelegatingContainerFilter(foreignKey.getParentTable(), true));
                }
            }
            ColumnInfo lookupColumn;
            if (displayField != null)
            {
                lookupColumn = lookupTable.getColumn(displayField);
            }
            else if (_useRawFKValue)
            {
                return foreignKey;
            }
            else if (_displayColumnName != null)
            {
                lookupColumn = lookupTable.getColumn(_displayColumnName);
            }
            else
            {
                lookupColumn = lookupTable.getColumn(lookupTable.getTitleColumn());
            }

            if (lookupColumn == null)
            {
                return null;
            }

            LookupColumn result = LookupColumn.create(foreignKey, lookupTable.getColumn(getLookupColumnName(lookupTable)), lookupColumn, false);
            if (_joinWithContainer)
            {
                ColumnInfo fkContainer = foreignKey.getParentTable().getColumn("Container");
                assert fkContainer != null : "Couldn't find Container column in " + foreignKey.getParentTable();
                ColumnInfo lookupContainer = lookupTable.getColumn("Container");
                assert lookupContainer != null : "Couldn't find Container column in " + lookupTable;

                result.addJoin(fkContainer.getFieldKey(), lookupContainer, false);
            }
            return result;
        }

        public String getLookupDisplayName()
        {
            return _displayColumnName;
        }

        public boolean isUseRawFKValue()
        {
            return _useRawFKValue;
        }

        public TableInfo getLookupTableInfo()
        {
            DbSchema schema = _scope.getSchema(_dbSchemaName, DbSchemaType.Unknown);
            return schema.getTable(_tableName);
        }

        public StringExpression getURL(ColumnInfo parent)
        {
            return null;
        }

        public boolean isJoinWithContainer()
        {
            return _joinWithContainer;
        }

        public Container getLookupContainer()
        {
            return null;
        }

        public String getLookupTableName()
        {
            return _tableName;
        }

        public String getLookupColumnName()
        {
            return _lookupKey;
        }

        public String getLookupColumnName(@Nullable TableInfo tableInfo)
        {
            if (_lookupKey == null)
            {
                if (tableInfo == null)
                {
                    tableInfo = getLookupTableInfo();
                }

                if (tableInfo != null)
                {
                    List<String> pkColumnNames = tableInfo.getPkColumnNames();
                    if (pkColumnNames.size() == 1)
                    {
                        _lookupKey = pkColumnNames.get(0);
                    }
                }
                return null;
            }
            return _lookupKey;
        }

        public String getLookupSchemaName()
        {
            return _dbSchemaName;
        }

        public NamedObjectList getSelectList(RenderContext ctx)
        {
            TableInfo lookupTable = getLookupTableInfo();
            if (lookupTable == null)
                return new NamedObjectList();

            return lookupTable.getSelectList(getLookupColumnName());
        }

        public ForeignKey remapFieldKeys(FieldKey parent, Map<FieldKey, FieldKey> mapping)
        {
            return this;
        }

        public Set<FieldKey> getSuggestedColumns()
        {
            return null;
        }
    }

    public DisplayColumn getRenderer()
    {
        if (displayField == null || displayField == this)
        {
            return getDisplayColumnFactory().createRenderer(this);
        }
        else
        {
            return displayField.getRenderer();
        }
    }


    public static Collection<ColumnInfo> createFromDatabaseMetaData(String schemaName, SchemaTableInfo parentTable, @Nullable String columnNamePattern) throws SQLException
    {
         //Use linked hash map to preserve ordering...
        LinkedHashMap<String, ColumnInfo> colMap = new LinkedHashMap<>();
        SqlDialect dialect = parentTable.getSqlDialect();
        DbScope scope = parentTable.getSchema().getScope();
        Map<String, ImportedKey> importedKeys = new HashMap<>();    // Use map to handle multiple FKs with multiple fields from same table referencing same PK

        try (JdbcMetaDataLocator locator = dialect.getJdbcMetaDataLocator(scope, schemaName, parentTable.getMetaDataName()))
        {
            JdbcMetaDataSelector columnSelector = new JdbcMetaDataSelector(locator, new JdbcMetaDataResultSetFactory()
            {
                @Override
                public ResultSet getResultSet(DatabaseMetaData dbmd, JdbcMetaDataLocator locator) throws SQLException
                {
                    return dbmd.getColumns(locator.getCatalogName(), locator.getSchemaName(), locator.getTableName(), columnNamePattern);
                }
            });

            try (ResultSet rsCols = columnSelector.getResultSet())
            {
                ColumnMetaDataReader reader = dialect.getColumnMetaDataReader(rsCols, parentTable);

                while (rsCols.next())
                {
                    String metaDataName = reader.getName();
                    ColumnInfo col = new ColumnInfo(metaDataName, parentTable);

                    col.metaDataName = metaDataName;
                    col.selectName = dialect.getSelectNameFromMetaDataName(metaDataName);
                    col.sqlTypeName = reader.getSqlTypeName();
                    col.jdbcType = dialect.getJdbcType(reader.getSqlType(), reader.getSqlTypeName());
                    col.isAutoIncrement = reader.isAutoIncrement();
                    col.scale = reader.getScale();
                    col.nullable = reader.isNullable();
                    col.jdbcDefaultValue = reader.getDefault();

                    // isCalculated is typically an query-level ExprColumn, but in this case we have a real calculated column in the database
                    col.setCalculated(reader.isGeneratedColumn());

                    inferMetadata(col);

    /*
                    // TODO: This is a temporary hack... move to SAS dialect(s)
                    String databaseFormat = reader.getDatabaseFormat();

                    if (null != databaseFormat)
                    {
                        // Do nothing for now -- not implementing SAS format support at this point
                    if (databaseFormat.startsWith("$"))
                    {
                        _log.info("User-defined format: " + databaseFormat);
                    }
                    else
                    {
                        String tableAlias = col.getTableAlias();
                        SQLFragment sql = new SQLFragment("PUT(" + ExprColumn.STR_TABLE_ALIAS + "." + col.getName() + ", " + databaseFormat + ")");
    //                    col = new ExprColumn(col.getParentTable(), col.getName(), sql, Types.VARCHAR);

                        if (!tables.contains(tableAlias))
                        {
                            _log.info("Table: " + tableAlias);
                            tables.add(tableAlias);
                        }
                    }
                    }
    */

                    col.label = reader.getLabel();
                    col.description = reader.getDescription();

                    if (nonEditableColNames.contains(col.getPropertyName()))
                        col.setUserEditable(false);

                    colMap.put(col.getName(), col);
                }
            }

            JdbcMetaDataSelector keySelector = new JdbcMetaDataSelector(locator, (dbmd, locator1) -> dbmd.getImportedKeys(locator1.getCatalogName(), locator1.getSchemaName(), locator1.getTableName()));

            // load keys in two phases
            // 1) combine multi column keys
            // 2) update columns

            try (ResultSet rsKeys = keySelector.getResultSet())
            {
                int iPkTableSchema = findColumn(rsKeys, "PKTABLE_SCHEM");
                int iPkTableName = findColumn(rsKeys, "PKTABLE_NAME");
                int iPkColumnName = findColumn(rsKeys, "PKCOLUMN_NAME");
                int iFkColumnName = findColumn(rsKeys, "FKCOLUMN_NAME");
                int iKeySequence = findColumn(rsKeys, "KEY_SEQ");
                int iFkName = findColumn(rsKeys, "FK_NAME");

                while (rsKeys.next())
                {
                    String pkSchemaName = rsKeys.getString(iPkTableSchema);
                    String pkTableName = rsKeys.getString(iPkTableName);
                    String pkColumnName = rsKeys.getString(iPkColumnName);
                    String colName = rsKeys.getString(iFkColumnName);
                    int keySequence = rsKeys.getInt(iKeySequence);
                    String fkName = rsKeys.getString(iFkName);

                    if (keySequence == 1)
                    {
                        ImportedKey key = locator.getImportedKey(fkName, pkSchemaName, pkTableName, pkColumnName, colName);
                        importedKeys.put(fkName, key);
                    }
                    else
                    {
                        assert importedKeys.size() > 0;
                        ImportedKey key = importedKeys.get(fkName);
                        key.pkColumnNames.add(pkColumnName);
                        key.fkColumnNames.add(colName);
                    }
                }
            }
        }

        for (ImportedKey key : importedKeys.values())
        {
            int i = -1;
            boolean joinWithContainer = false;

            if (key.pkColumnNames.size() == 1)
            {
                i = 0;
                joinWithContainer = false;
            }
            else if (key.pkColumnNames.size() == 2 && "container".equalsIgnoreCase(key.fkColumnNames.get(0)))
            {
                i = 1;
                joinWithContainer = true;
            }
            else if (key.pkColumnNames.size() == 2 && "container".equalsIgnoreCase(key.fkColumnNames.get(1)))
            {
                i = 0;
                joinWithContainer = true;
            }

            if (i > -1)
            {
                String colName = key.fkColumnNames.get(i);
                ColumnInfo col = colMap.get(colName);

                if (col.fk != null)
                {
                    _log.warn("More than one FK defined for column " + parentTable.getName() + "." + col.getName() + ". Skipping constraint " + key.fkName);
                    continue;
                }

                col.fk = new SchemaForeignKey(col, key.pkSchemaName, key.pkTableName, key.pkColumnNames.get(i), joinWithContainer);
            }
            else
            {
                _log.warn("Skipping multiple column foreign key " + key.fkName + " ON " + parentTable.getName());
            }
        }

        return colMap.values();
    }

    private static void inferMetadata(ColumnInfo col)
    {
        String colName = col.getName();
        DbSchema schema = col.getParentTable().getSchema();

        if (col.metaDataName.startsWith("_"))
        {
            col.setHidden(true);
        }

        if (col.isAutoIncrement || col.isCalculated())
        {
            col.setUserEditable(false);
            col.setShownInInsertView(false);
            col.setShownInUpdateView(false);
            col.setReadOnly(true);
        }

        if (JdbcType.INTEGER == col.getJdbcType() &&
           (colName.equalsIgnoreCase("createdby") || colName.equalsIgnoreCase("modifiedby")) &&
           (schema.getScope().isLabKeyScope()))
        {
            col.setUserEditable(false);
            col.setShownInInsertView(false);
            col.setShownInUpdateView(false);
            col.setReadOnly(true);

            if(colName.equalsIgnoreCase("createdby"))
                col.setLabel("Created By");
            if(colName.equalsIgnoreCase("modifiedby"))
                col.setLabel("Modified By");
        }

        if (JdbcType.DATE == col.getJdbcType() &&
           (colName.equalsIgnoreCase("created") || colName.equalsIgnoreCase("modified")) &&
           (schema.getScope().isLabKeyScope()))
        {
            col.setUserEditable(false);
            col.setShownInInsertView(false);
            col.setShownInUpdateView(false);
            col.setReadOnly(true);

            if(colName.equalsIgnoreCase("created"))
                col.setLabel("Created");
            if(colName.equalsIgnoreCase("modified"))
                col.setLabel("Modified");
        }

        if (null == col.getInputType() && col.getJdbcType().getJavaClass().equals(String.class) && col.scale > 255)
        {
            col.setInputType("textarea");
        }
    }

    // Safe version of findColumn().  Returns jdbc column index to specified column, or 0 if it doesn't exist or an
    // exception occurs.  SAS JDBC driver throws when attempting to resolve indexes when no records exist.
    private static int findColumn(ResultSet rs, String name)
    {
        try
        {
            return rs.findColumn(name);
        }
        catch (Exception e)
        {
            return 0;
        }
    }


    public static class ImportedKey
    {
        private final String fkName;
        private final String pkSchemaName;
        private final String pkTableName;
        private final ArrayList<String> pkColumnNames = new ArrayList<>(2);
        private final ArrayList<String> fkColumnNames = new ArrayList<>(2);

        public ImportedKey(String fkName, String pkSchemaName, String pkTableName, String pkColumnName, String colName)
        {
            this.fkName = fkName;
            this.pkSchemaName = pkSchemaName;
            this.pkTableName = pkTableName;
            pkColumnNames.add(pkColumnName);
            fkColumnNames.add(colName);
        }
    }


    public String getSqlTypeName()
    {
        if (null == sqlTypeName && (propertyType != null || jdbcType != null))
        {
            SqlDialect d;
            if (getParentTable() == null)
                d = CoreSchema.getInstance().getSqlDialect();
            else
                d = getParentTable().getSqlDialect();

            JdbcType jt = propertyType != null ? propertyType.getJdbcType() : jdbcType;
            sqlTypeName = d.sqlTypeNameFromJdbcType(jt);
        }
        return sqlTypeName;
    }


    public void setSqlTypeName(String sqlTypeName)
    {
        checkLocked();
        this.sqlTypeName = sqlTypeName;
        this.jdbcType = null;
    }

    public List<FieldKey> getSortFieldKeys()
    {
        return sortFieldKeys;
    }

    public void setSortFieldKeys(List<FieldKey> sortFieldKeys)
    {
        checkLocked();
        this.sortFieldKeys = sortFieldKeys;
    }

    public void setJdbcType(JdbcType type)
    {
        checkLocked();
        this.jdbcType = type;
        this.sqlTypeName = null;
    }


    public @NotNull JdbcType getJdbcType()
    {
        if (jdbcType == null && (propertyType != null || sqlTypeName != null))
        {
            if (propertyType != null)
            {
                jdbcType = propertyType.getJdbcType();
            }
            else // we're here because sqlTypeName != null
            {
                SqlDialect d;
                if (getParentTable() == null)
                    d = CoreSchema.getInstance().getSqlDialect();
                else
                    d = getParentTable().getSqlDialect();
                int type = d.sqlTypeIntFromSqlTypeName(sqlTypeName);
                jdbcType = JdbcType.valueOf(type);
            }
        }
        return jdbcType == null ? JdbcType.OTHER : jdbcType;
    }


    public ForeignKey getFk()
    {
        return fk;
    }


    public void setFk(@Nullable ForeignKey fk)
    {
        checkLocked();
        this.fk = fk;
    }


    public void setScale(int scale)
    {
        checkLocked();
        super.setScale(scale);
    }


    /** @return whether the column is part of the primary key for the table */
    public boolean isKeyField()
    {
        return isKeyField;
    }


    public void setKeyField(boolean keyField)
    {
        checkLocked();
        isKeyField = keyField;
    }

    public boolean isMvEnabled()
    {
        return _mvColumnName != null;
    }

    public FieldKey getMvColumnName()
    {
        return _mvColumnName;
    }

    public void setMvColumnName(FieldKey mvColumnName)
    {
        checkLocked();
        this._mvColumnName = mvColumnName;
    }

    public boolean isMvIndicatorColumn()
    {
        return _isMvIndicatorColumn;
    }

    public void setMvIndicatorColumn(boolean mvIndicatorColumn)
    {
        checkLocked();
        _isMvIndicatorColumn = mvIndicatorColumn;
    }

    public boolean isRawValueColumn()
    {
        return _isRawValueColumn;
    }

    public void setRawValueColumn(boolean rawColumn)
    {
        checkLocked();
        _isRawValueColumn = rawColumn;
    }

    /**
     * Returns true if this column does not contain data that should be queried, but is a lookup into a valid table.
     *
     */
    public boolean isUnselectable()
    {
        return isUnselectable;
    }

    public void setIsUnselectable(boolean b)
    {
        checkLocked();
        isUnselectable = b;
    }


    public TableInfo getParentTable()
    {
        return parentTable;
    }


    public void setParentTable(TableInfo parentTable)
    {
        checkLocked();
        this.parentTable = parentTable;
        _columnLogging.setOriginalTableName(null != parentTable ? parentTable.getName() : "");
    }

    public String getColumnName()
    {
        return getName();
    }

    public Object getValue(ResultSet rs) throws SQLException
    {
        if (rs == null)
            return null;
        // UNDONE
        return rs.getObject(getAlias());
    }

    public int getIntValue(ResultSet rs) throws SQLException
    {
        // UNDONE
        return rs.getInt(getAlias());
    }

    public String getStringValue(ResultSet rs) throws SQLException
    {
        // UNDONE
        return rs.getString(getAlias());
    }

    public Object getValue(RenderContext context)
    {
        return context.get(getFieldKey());
    }

    public Object getValue(Map<String, ?> map)
    {
        if (map == null)
            return null;
        // UNDONE
        return map.get(getAlias());
    }

    public DefaultValueType getDefaultValueType()
    {
        return _defaultValueType;
    }

    public void setDefaultValueType(DefaultValueType defaultValueType)
    {
        checkLocked();
        _defaultValueType = defaultValueType;
    }

    @Override
    public boolean isLookup()
    {
        return getFk() != null;
    }

    @NotNull
    public List<ConditionalFormat> getConditionalFormats()
    {
        return conditionalFormats;
    }

    public void setConditionalFormats(@NotNull List<ConditionalFormat> formats)
    {
        checkLocked();
        this.conditionalFormats.clear();
        this.conditionalFormats.addAll(formats);
    }

    @NotNull
    public List<? extends IPropertyValidator> getValidators()
    {
        return validators;
    }

    public void setValidators(List<? extends IPropertyValidator> validators)
    {
        checkLocked();
        this.validators = validators;
    }

    // TODO: fix up OORIndicator

    public void remapFieldKeys(@Nullable FieldKey parent, @Nullable Map<FieldKey, FieldKey> remap)
    {
        checkLocked();
        if (null==parent && (null == remap || remap.isEmpty()))
            return;

        // TODO should mvColumnName be a fieldkey so we can reparent etc?
        if (null != getMvColumnName())
        {
            FieldKey r = null==remap ? null : remap.get(getMvColumnName());
            if (null != r && r.getParent()==null)
                setMvColumnName(r);
        }

        remapUrlFieldKeys(parent, remap);
        remapTextExpressionFieldKeys(parent, remap);
        remapForeignKeyFieldKeys(parent, remap);
        remapSortFieldKeys(parent, remap);
        DisplayColumnFactory factory = getDisplayColumnFactory();
        if (null != factory && DEFAULT_FACTORY != factory && factory instanceof RemappingDisplayColumnFactory)
            ((RemappingDisplayColumnFactory)factory).remapFieldKeys(parent, remap);
    }


    protected void remapUrlFieldKeys(@Nullable FieldKey parent, @Nullable Map<FieldKey, FieldKey> remap)
    {
        StringExpression se = getURL();
        if (se instanceof FieldKeyStringExpression && se != AbstractTableInfo.LINK_DISABLER)
        {
            FieldKeyStringExpression remapped = ((FieldKeyStringExpression)se).remapFieldKeys(parent, remap);
            setURL(remapped);
        }
    }

    protected void remapTextExpressionFieldKeys(@Nullable FieldKey parent, @Nullable Map<FieldKey, FieldKey> remap)
    {
        StringExpression se = getTextExpression();
        if (se instanceof FieldKeyStringExpression)
        {
            FieldKeyStringExpression remapped = ((FieldKeyStringExpression)se).remapFieldKeys(parent, remap);
            setTextExpression(remapped);
        }
    }


    protected void remapForeignKeyFieldKeys(@Nullable FieldKey parent,  @Nullable Map<FieldKey, FieldKey> remap)
    {
        ForeignKey fk = getFk();
        if (fk == null)
            return;
        ForeignKey remappedFk = fk.remapFieldKeys(parent, remap);
        setFk(remappedFk);
    }


    protected void remapSortFieldKeys(@Nullable FieldKey parent,  @Nullable Map<FieldKey, FieldKey> remap)
    {
        if (getSortFieldKeys() == null)
            return;

        List<FieldKey> remappedKeys = new ArrayList<>();
        for (FieldKey key : getSortFieldKeys())
        {
            remappedKeys.add(FieldKey.remap(key, parent, remap));
        }

        setSortFieldKeys(remappedKeys);
    }

    private void checkLocked()
    {
        if (_locked)
            throw new IllegalStateException("ColumnInfo is locked: " + (null!=getParentTable()?getParentTable().getName()+".":"") + getName());
    }

    boolean _locked;

    public void setLocked(boolean b)
    {
        _locked = b;
    }

    public boolean isLocked()
    {
        return _locked;
    }

    /**
     * Unfortunately we cannot differentiate cases shownInInsertView is not set (it defaults to true) from cases where it is explicitly set to true
     * This is an attempt to centralize code to ignore columns that we assume should not actually be shownInInsertView
     * At some future point we should consider better handling true/false/null (ie. has not explicitly been set) for properties like
     * shownInInsertView, shownInUpdateView, etc.
     *
     */
    public boolean inferIsShownInInsertView()
    {
        return !isNullable() && isUserEditable() && !isAutoIncrement() && isShownInInsertView();
    }

    public boolean isCalculated()
    {
        return _calculated;
    }

    public void setCalculated(boolean calculated)
    {
        checkLocked();
        _calculated = calculated;
    }


    // If true, you can't use this column when auto-generating LabKey SQL, it is not selected in the underlying query
    // only query can set this true
    public boolean isAdditionalQueryColumn()
    {
        return false;
    }

    public void setColumnLogging(ColumnLogging columnLogging)
    {
        checkLocked();
        _columnLogging = columnLogging;
    }

    public ColumnLogging getColumnLogging()
    {
        return _columnLogging;
    }
}
