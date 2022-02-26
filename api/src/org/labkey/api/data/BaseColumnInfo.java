/*
 * Copyright (c) 2004-2018 Fred Hutchinson Cancer Research Center
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
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.collections.NamedObjectList;
import org.labkey.api.data.dialect.ColumnMetaDataReader;
import org.labkey.api.data.dialect.ForeignKeyResolver;
import org.labkey.api.data.dialect.JdbcMetaDataLocator;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.dataiterator.SimpleTranslator;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.IPropertyValidator;
import org.labkey.api.exp.property.ValidatorKind;
import org.labkey.api.gwt.client.DefaultScaleType;
import org.labkey.api.gwt.client.DefaultValueType;
import org.labkey.api.gwt.client.FacetingBehaviorType;
import org.labkey.api.ontology.OntologyService;
import org.labkey.api.query.AliasManager;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryParseException;
import org.labkey.api.query.column.BuiltInColumnTypes;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.StringExpression;
import org.labkey.api.util.StringExpressionFactory;
import org.labkey.api.util.StringExpressionFactory.FieldKeyStringExpression;
import org.labkey.api.util.logging.LogHelper;
import org.labkey.data.xml.ColumnType;
import org.labkey.data.xml.DbSequenceType;
import org.labkey.data.xml.PropertiesType;

import java.beans.Introspector;
import java.io.IOException;
import java.io.Writer;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Represents a column (be it a real column in a table or a calculated expression) that's part of
 * a {@link TableInfo}. Knows how to generate SQL to get its own value.
 */
public class BaseColumnInfo extends ColumnRenderPropertiesImpl implements MutableColumnInfo
{
    private static final Logger LOG = LogHelper.getLogger(ColumnInfo.class, "BaseColumnInfo logger");
    private static final Set<String> NON_EDITABLE_COL_NAMES = new CaseInsensitiveHashSet("created", "createdBy", "modified", "modifiedBy", "_ts", "entityId", "container");

    private FieldKey _fieldKey;
    private String _name;
    // _propertyName is computed from getName();
    private String _propertyName = null;
    private String _alias;
    private String _sqlTypeName;
    private JdbcType _jdbcType = null;
    private String _textAlign = null;
    private ForeignKey _fk = null;
    private Object _defaultValue = null;
    private String _jdbcDefaultValue = null;  // TODO: Merge with defaultValue, see #17646
    private boolean _isAutoIncrement = false;
    private boolean _hasDbSequence = false;
    private boolean _isRootDbSequence = false;
    private boolean _isKeyField = false;
    private boolean _isReadOnly = false;
    private boolean _isUserEditable = true;
    private boolean _isUnselectable = false;
    private TableInfo _parentTable = null;
    protected String _metaDataName = null;
    protected String _selectName = null;
    protected ColumnInfo _displayField;
    private List<FieldKey> _sortFieldKeys = null;
    private List<ConditionalFormat> _conditionalFormats = List.of();
    private List<? extends IPropertyValidator> _validators = List.of();
    private DisplayColumnFactory _displayColumnFactory = DEFAULT_FACTORY;
    private boolean _shouldLog = true;
    private boolean _lockName = false;
    private SimpleTranslator.RemapMissingBehavior _remapMissingBehavior = null;

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
    public BaseColumnInfo(FieldKey key, TableInfo parentTable)
    {
        _fieldKey = key;
        _parentTable = parentTable;
        _columnLogging = new ColumnLogging(key, parentTable);
    }

    public BaseColumnInfo(FieldKey key, JdbcType t)
    {
        this(key, (TableInfo)null);
        _name = null;
        _jdbcType = t;
    }

    @Deprecated // Pass in a type!
    public BaseColumnInfo(String name)
    {
        this(null != name ? new FieldKey(null, name) : null, (TableInfo)null);
//        assert -1 == name.indexOf('/');
    }

    public BaseColumnInfo(String name, JdbcType t)
    {
        this(null != name ? new FieldKey(null, name) : null, (TableInfo)null);
        if (null == name)
            return;
//        assert -1 == name.indexOf('/');
        _jdbcType = t;
    }

    public BaseColumnInfo(String name, JdbcType t, int scale, boolean nullable)
    {
        this(null != name ? new FieldKey(null, name) : null, t);
        if (null == name)
            return;
//        assert -1 == name.indexOf('/');
        setScale(scale);
        setNullable(nullable);
    }


    public BaseColumnInfo(ResultSetMetaData rsmd, int col) throws SQLException
    {
        this(new FieldKey(null, rsmd.getColumnLabel(col)), JdbcType.valueOf(rsmd.getColumnType(col)));
        setSqlTypeName(rsmd.getColumnTypeName(col));
        setAlias(rsmd.getColumnName(col));
    }

    @Deprecated
    public BaseColumnInfo(String name, TableInfo parentTable)
    {
//        assert -1 == name.indexOf('/');
        this(new FieldKey(null, name), parentTable);
    }

    public BaseColumnInfo(String name, TableInfo parentTable, JdbcType type)
    {
        this(new FieldKey(null, name), parentTable, type);
    }

    public BaseColumnInfo(FieldKey key, TableInfo parentTable, JdbcType type)
    {
        this(key, parentTable);
        setJdbcType(type);
    }

    public BaseColumnInfo(ColumnInfo from)
    {
        this(from, from.getParentTable());
    }


    public BaseColumnInfo(ColumnInfo from, TableInfo parent)
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
        // but since fieldKey holds true name (and _name could be null), check it there
        FieldKey newFieldKey = new FieldKey(null, name);
        assert !_lockName || 0 == _fieldKey.compareTo(newFieldKey);
        _fieldKey = newFieldKey;
        _name = null;
        _propertyName = null;
    }


    @Override
    public String getName()
    {
        if (_name == null && _fieldKey != null)
        {
            if (_fieldKey.getParent() == null)
                _name = _fieldKey.getName();
            else
                _name = _fieldKey.toString();
        }
        return _name;
    }


    @Override
    public void setFieldKey(FieldKey key)
    {
        checkLocked();
        _fieldKey = key;
        _name = null;
        _propertyName = null;
    }


    @Override
    public FieldKey getFieldKey()
    {
        return _fieldKey;
    }


    // use only for debugging, will change after call to getAlias()
    @Override
    public boolean isAliasSet()
    {
        return null != _alias;
    }


    @Override
    public String getAlias()
    {
        if (_alias == null)
            _alias = AliasManager.makeLegalName(getFieldKey(), getSqlDialect(), false);
        return _alias;
    }


    @Override
    public void setAlias(String alias)
    {
        checkLocked();
        _alias = alias;
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
        setPrecision(col.getPrecision());
        if (col instanceof BaseColumnInfo)
        {
            _sqlTypeName = ((BaseColumnInfo) col)._sqlTypeName;
            _jdbcType = ((BaseColumnInfo) col)._jdbcType;
            _propertyType = ((BaseColumnInfo) col)._propertyType;
        }
        else
        {
            _sqlTypeName = col.getSqlTypeName();
            _jdbcType = col.getJdbcType();
            _propertyType = col.getPropertyType();
        }

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
        if (col instanceof BaseColumnInfo)
        {//TODO why are we doing this? most of the props are the same in both methods
            setExtraAttributesFrom((BaseColumnInfo) col);
            return;
        }

        setLabel(col.getLabelValue());
        // TODO if (col.isShortLabelSet())
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
        // TODO if (col.isInputLengthSet())
        setInputLength(col.getInputLength());
        // TODO if (col.isInputTypeSet())
        setInputType(col.getInputType());

        setInputRows(col.getInputRows());
        if (!isKeyField() && !col.isNullable())
            setNullable(col.isNullable());
        // TODO if (col.isRequiredSet())
        setRequired(col.isRequired());
        // TODO if (col.isReadOnlySet())
        setReadOnly(col.isReadOnly());
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
        // TODO if (col.isMeasureSet())
        _measure = col.isMeasure();
        _dimension = col.isDimension();

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

        setPrincipalConceptCode(col.getPrincipalConceptCode());
        setSourceOntology(col.getSourceOntology());
        setConceptSubtree(col.getConceptSubtree());
        setConceptImportColumn(col.getConceptImportColumn());
        setConceptLabelColumn(col.getConceptLabelColumn());

        setDerivationDataScope(col.getDerivationDataScope());
        setScannable(col.isScannable());
    }

    /*
     * copy "non-core" attributes, e.g. leave key and type information alone
     */
    public void setExtraAttributesFrom(BaseColumnInfo col)
    {
        checkLocked();
        if (col._label != null)
            setLabel(col.getLabel());
        if (col._shortLabel != null)
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
        setInputLength(col._inputLength);
        setInputType(col._inputType);

        setInputRows(col.getInputRows());
        if (!isKeyField() && !col.isNullable())
            setNullable(col.isNullable());
        setRequired(col._required);
        setReadOnly(col._isReadOnly);
        setDisplayColumnFactory(col.getDisplayColumnFactory());
        setTextAlign(col.getTextAlign());
        setWidth(col.getWidth());
        setFk(col.getFk());
        setPropertyURI(col.getPropertyURI());
        setSortFieldKeys(col._sortFieldKeys);
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
        _measure = col._measure;
        _dimension = col._dimension;

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

        setSourceOntology(col.getSourceOntology());
        setConceptSubtree(col.getConceptSubtree());
        setConceptImportColumn(col.getConceptImportColumn());
        setConceptLabelColumn(col.getConceptLabelColumn());
        setPrincipalConceptCode(col.getPrincipalConceptCode());

        setDerivationDataScope(col.getDerivationDataScope());
        setScannable(col.isScannable());
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
        setOnClick(col.getOnClick());
    }


    /* only copy if all field keys are in the map */
    public void copyURLFromStrict(ColumnInfo col, Map<FieldKey,FieldKey> remap)
    {
        checkLocked();
        StringExpression url = col.getURL();
        if (url instanceof FieldKeyStringExpression fkse)
        {
            if (fkse.validateFieldKeys(remap.keySet()))
            {
                StringExpression mapped = (fkse).remapFieldKeys(null, remap);
                setURL(mapped);
            }
        }
    }


    @Override
    public void setMetaDataName(String metaDataName)
    {
        checkLocked();
        _metaDataName = metaDataName;
    }

    @Override
    public String getMetaDataName()
    {
        return _metaDataName;
    }

    @Override
    public String getSelectName()
    {
        assert getParentTable() instanceof SchemaTableInfo : "Use getValueSql()";
        if (null == _selectName)
        {
            if (!(getParentTable() instanceof SchemaTableInfo))
                throw new UnsupportedOperationException("Use getValueSql()");
        }
        return generateSelectName();
    }

    private String generateSelectName()
    {
        if (null == _selectName)
        {
            if (null == getMetaDataName())
                _selectName = getSqlDialect().getColumnSelectName(getName());
            else
                _selectName = getSqlDialect().getColumnSelectName(getMetaDataName());
        }
        return _selectName;
    }

    @Override
    public SQLFragment getValueSql(String tableAliasName)
    {
        // call generateSelectName() to avoid asserts in getSelectName()
        return new SQLFragment(tableAliasName + "." + generateSelectName());
    }

    @Override
    public String getPropertyURI()
    {
        if (null == _propertyURI && null != getParentTable())
            _propertyURI = DEFAULT_PROPERTY_URI_PREFIX + getParentTable().getSchema().getName() + "#" + getParentTable().getName() + "." + PageFlowUtil.encode(getName());
        return _propertyURI;
    }

    @Override
    public void setPropertyURI(String propertyURI)
    {
        checkLocked();
        _propertyURI = propertyURI;
    }

    @Override
    public void setConceptURI(String conceptURI)
    {
        checkLocked();
        _conceptURI = conceptURI;
        if (STORAGE_UNIQUE_ID_CONCEPT_URI.equals(conceptURI))
        {
            _hasDbSequence = true;
            _shownInInsertView = false;
            _isUserEditable = false;
        }
    }

    @Override
    public void setRangeURI(String rangeURI)
    {
        checkLocked();
        _rangeURI = rangeURI;
    }

    @Override
    public void declareJoins(String parentAlias, Map<String, SQLFragment> map)
    {
    }

    @Override
    public String getTableAlias(String baseAlias)
    {
        return _parentTable.getName();
    }

    @Override
    public SqlDialect getSqlDialect()
    {
        if (_parentTable == null)
            return null;
        return _parentTable.getSqlDialect();
    }


    // Return the actual value we have stashed; use this when copying attributes, so you don't hard-code label
    @Override
    public String getLabelValue()
    {
        return _label;
    }


    @Override
    public String getLabel()
    {
        if (null == _label && getFieldKey() != null)
            return labelFromName(getFieldKey().getName());
        return _label;
    }


    @Override
    public boolean isFormatStringSet()
    {
        return _format != null;
    }

    @Override
    public String getTextAlign()
    {
        if (_textAlign != null)
            return _textAlign;
        return isStringType() || isDateTimeType() || isBooleanType() ? "left" : "right";
    }

    @Override
    public void setTextAlign(String textAlign)
    {
        checkLocked();
        _textAlign = textAlign;
    }

    @Override
    public String getJdbcDefaultValue()
    {
        return _jdbcDefaultValue;
    }

    @Override
    public void setJdbcDefaultValue(String jdbcDefaultValue)
    {
        checkLocked();
        _jdbcDefaultValue = jdbcDefaultValue;
    }

    @Override
    public Object getDefaultValue()
    {
        return _defaultValue;
    }

    @Override
    public void setDefaultValue(Object defaultValue)
    {
        checkLocked();
        _defaultValue = defaultValue;
    }

    @Override
    @Nullable
    public ColumnInfo getDisplayField()
    {
        if (_displayField != null)
            return _displayField;
        if (isUnselectable())
            return null;
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

    @Override
    public ColumnInfo getFilterField()
    {
        return this;
    }

    @Override
    final public boolean equals(Object obj)
    {
        return super.equals(obj);
    }

    @Override
    public boolean isNoWrap()
    {
        // NOTE: most non-string types don't have spaces after conversion except dates
        // let's make sure they don't wrap (bug 392)
        return java.util.Date.class.isAssignableFrom(getJdbcType().cls) || isNumericType();
    }

    @Override
    public void setDisplayField(ColumnInfo field)
    {
        checkLocked();
        _displayField = field;
    }

    @Override
    public void setWidth(String width)
    {
        checkLocked();
        _displayWidth = width;
    }

    @Override
    @NotNull
    public String getWidth()
    {
        if (null != _displayWidth)
            return _displayWidth;
        return "";
    }

    @Override
    public TableInfo getFkTableInfo()
    {
        if (null == getFk())
            return null;
        return getFk().getLookupTableInfo();
    }

    @Override
    public TableDescription getFkTableDescription()
    {
        if (null == getFk())
            return null;
        return getFk().getLookupTableDescription();
    }


    @Override
    public boolean isUserEditable()
    {
        return _isUserEditable;
    }


    @Override
    public void setUserEditable(boolean editable)
    {
        checkLocked();
        _isUserEditable = editable;
    }


    @Override
    public void setDisplayColumnFactory(DisplayColumnFactory factory)
    {
        checkLocked();
        _displayColumnFactory = factory;
    }

    @Override
    public DisplayColumnFactory getDisplayColumnFactory()
    {
        return _displayColumnFactory;
    }

    @Override
    public boolean isShouldLog()
    {
        return _shouldLog;
    }

    @Override
    public void setShouldLog(boolean shouldLog)
    {
        checkLocked();
        _shouldLog = shouldLog;
    }

    @Override
    public String getLegalName()
    {
        return legalNameFromName(getName());
    }

    @Override
    public String getPropertyName()
    {
        // this is surprisingly expensive, cache it!
        if (null == _propertyName)
            _propertyName = propNameFromName(getName());
        return _propertyName;
    }

    /**
     * See {@link #jdbcRsNameFromName(String) }
     *
     */
    @Override
    public String getJdbcRsName() { return jdbcRsNameFromName(getName()); }

    /**
     * Version column can be used for optimistic concurrency.
     * for now we assume that this column is never updated
     * explicitly.
     */
    @Override
    public boolean isVersionColumn()
    {
        if ("_ts".equalsIgnoreCase(getName()) || "Modified".equalsIgnoreCase(getName()))
            return true;
        return JdbcType.BINARY == getJdbcType() && 8 == getScale() && "timestamp".equals(getSqlTypeName());
    }


    @Override
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


    @Override
    public String getInputType()
    {
        if (null == _inputType)
        {
            if (getPropertyType() != null && getPropertyType().getInputType() != null)
                _inputType = getPropertyType().getInputType();
            else if (isStringType() && _scale > 300) // lsidtype is 255 characters
                _inputType = "textarea";
            else if ("image".equalsIgnoreCase(getSqlTypeName()))
                _inputType = "file";
            else if (getJdbcType() == JdbcType.BOOLEAN)
                _inputType = "checkbox";
            else
                _inputType = "text";
        }
        return _inputType;
    }


    @Override
    public int getInputLength()
    {
        if (-1 == _inputLength)
        {
            if (getInputType().equalsIgnoreCase("textarea"))
                _inputLength = 60;
            else
                _inputLength = Math.min(_scale, 40);
        }

        return _inputLength;
    }


    @Override
    public int getInputRows()
    {
        if (-1 == _inputRows && isStringType())
            return 15;
        return _inputRows;
    }

    @Override
    public boolean isAutoIncrement()
    {
        return _isAutoIncrement;
    }

    @Override
    public void setAutoIncrement(boolean autoIncrement)
    {
        checkLocked();
        _isAutoIncrement = autoIncrement;
    }

    @Override
    public boolean hasDbSequence()
    {
        return _hasDbSequence;
    }

    @Override
    public void setHasDbSequence(boolean hasDbSequence)
    {
        _hasDbSequence = hasDbSequence;
    }

    @Override
    public boolean isRootDbSequence()
    {
        return _isRootDbSequence;
    }

    @Override
    public void setIsRootDbSequence(boolean isRootDbSequence)
    {
        _isRootDbSequence = isRootDbSequence;
    }

    @Override
    public boolean isReadOnly()
    {
        return _isReadOnly || _isAutoIncrement || _hasDbSequence || isVersionColumn();
    }

    @Override
    public void setReadOnly(boolean readOnly)
    {
        checkLocked();
        _isReadOnly = readOnly;
    }

    @Override
    public StringExpression getEffectiveURL()
    {
        return ColumnInfo.getEffectiveURL(this);
    }

    @Override
    public void copyToXml(ColumnType xmlCol, boolean full)
    {
        xmlCol.setColumnName(getName());
        if (full)
        {
            if (_fk instanceof SchemaForeignKey sfk)
            {
                org.labkey.data.xml.ColumnType.Fk xmlFk = xmlCol.addNewFk();
                xmlFk.setFkColumnName(sfk.getLookupColumnName());
                xmlFk.setFkTable(sfk._tableName);
                var lkti = sfk.getLookupTableInfo();
                DbSchema fkDbOwnerSchema = null == lkti ? null : lkti.getSchema().getScope().getSchema(sfk._dbSchemaName, DbSchemaType.Unknown);

                if (null != fkDbOwnerSchema && fkDbOwnerSchema != getParentTable().getSchema())
                {
                    xmlFk.setFkDbSchema(fkDbOwnerSchema.getName());
                }
            }

            // changed the following to not invoke getters with code, and only write out non-default values
            if (null != _inputType)
                xmlCol.setInputType(_inputType);

            if (-1 != _inputLength)
                xmlCol.setInputLength(_inputLength);

            if (-1 != _inputRows)
                xmlCol.setInputRows(_inputRows);
            if (null != _url)
                xmlCol.setUrl(_url.toXML());

            if (_isReadOnly)
                xmlCol.setIsReadOnly(_isReadOnly);
            if (!_isUserEditable)
                xmlCol.setIsUserEditable(_isUserEditable);
            if (_hidden)
                xmlCol.setIsHidden(_hidden);
            if (_isUnselectable)
                xmlCol.setIsUnselectable(_isUnselectable);
            if (null != _label)
                xmlCol.setColumnTitle(_label);
            if (_nullable)
                xmlCol.setNullable(_nullable);
            if (null != _sqlTypeName)
                xmlCol.setDatatype(_sqlTypeName);
            if (_isAutoIncrement)
                xmlCol.setIsAutoInc(_isAutoIncrement);
            if (_scale != 0)
                xmlCol.setScale(_scale);
            if (null != _defaultValue)
                xmlCol.setDefaultValue(_defaultValue.toString());
            if (!StringUtils.isBlank(getDisplayWidth()))
                xmlCol.setDisplayWidth(getDisplayWidth());
            if (null != _format)
                xmlCol.setFormatString(_format);
            if (null != _textAlign)
                xmlCol.setTextAlign(_textAlign);
            if (null != _description)
                xmlCol.setDescription(_description);

            // Note: This is only called on JDBC meta data, so we don't bother with PHI, faceting behavior, and other
            // external meta data. But perhaps this code could be shared with TableInfoWriter?
        }
    }


    public void loadFromXml(ColumnType xmlCol, boolean merge)
    {
        checkLocked();

        if (xmlCol.isSetConceptURI())
        {
            String conceptURI = xmlCol.getConceptURI();
            // User can not set this concepturi, it only applies to exp.object.objectid
            if (!StringUtils.equalsIgnoreCase(conceptURI,BuiltInColumnTypes.EXPOBJECTID_CONCEPT_URI))
                setConceptURI(conceptURI);
        }
        if (xmlCol.isSetRangeURI())
            _rangeURI = xmlCol.getRangeURI();

        //Following things would exist from meta data...
        if (! merge)
        {
            PropertyType pt = null;
            if (_conceptURI != null || _rangeURI != null)
                pt = PropertyType.getFromURI(_conceptURI, _rangeURI, null);

            // Initialize properties based on rangeURI PropertyType
            if (pt != null)
            {
                _propertyType = pt;
                _jdbcType = _propertyType.getJdbcType();
                _sqlTypeName = getSqlDialect().getSqlTypeName(_jdbcType);
                _inputType = _propertyType.getInputType();
                _scale = _propertyType.getScale();
            }

            if (xmlCol.isSetDatatype())
            {
                _sqlTypeName = xmlCol.getDatatype();
            }
        }

        if ((!merge || null == _fk) && xmlCol.getFk() != null)
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
                _fk = new SchemaForeignKey(this, key.pkSchemaName, key.pkTableName, key.pkColumnNames.get(0), false, displayColumnName, useRawFKValue);
            }
            else
            {
                String type = xfk.getFkMultiValued();

                if ("junction".equals(type))
                    _fk = new MultiValuedForeignKey(new SchemaForeignKey(this, key.pkSchemaName, key.pkTableName, key.pkColumnNames.get(0), false), xfk.getFkJunctionLookup());
                else
                    throw new UnsupportedOperationException("Non-junction multi-value columns NYI");
            }
        }

        setFieldKey(new FieldKey(null, xmlCol.getColumnName()));
        if (xmlCol.isSetColumnTitle())
            setLabel(xmlCol.getColumnTitle());
        if (xmlCol.isSetInputLength())
            _inputLength = xmlCol.getInputLength();
        if (xmlCol.isSetInputRows())
            _inputRows = xmlCol.getInputRows();
        if (xmlCol.isSetInputType())
            _inputType = xmlCol.getInputType();
        if (xmlCol.isSetUrl())
            setURL(StringExpressionFactory.fromXML(xmlCol.getUrl(), false));
        if (xmlCol.isSetUrlTarget())
            setURLTargetWindow(xmlCol.getUrlTarget());
        if (xmlCol.isSetIsAutoInc())
            _isAutoIncrement = xmlCol.getIsAutoInc();
        if (xmlCol.isSetHasDbSequence())
        {
            DbSequenceType dbSequenceType = xmlCol.getHasDbSequence();
            _hasDbSequence = dbSequenceType.getBooleanValue();
            _isRootDbSequence = dbSequenceType.getRootSequence();
        }
        if (xmlCol.isSetIsReadOnly())
            _isReadOnly = xmlCol.getIsReadOnly();
        if (xmlCol.isSetIsUserEditable())
            _isUserEditable = xmlCol.getIsUserEditable();
        if (xmlCol.isSetScale())
            _scale = xmlCol.getScale();
        if (xmlCol.isSetScannable())
            _scannable = xmlCol.getScannable();
        if (xmlCol.isSetDefaultValue())
            _defaultValue = xmlCol.getDefaultValue();
        if (xmlCol.isSetFormatString())
            _format = xmlCol.getFormatString();
        if (xmlCol.isSetTsvFormatString())
            _tsvFormatString = xmlCol.getTsvFormatString();
        if (xmlCol.isSetExcelFormatString())
            _excelFormatString = xmlCol.getExcelFormatString();
        if (xmlCol.isSetTextExpression())
            _textExpression = new FieldKeyStringExpression(xmlCol.getTextExpression().getStringValue(), false, StringExpressionFactory.AbstractStringExpression.NullValueBehavior.fromXML(xmlCol.getTextExpression().getReplaceMissing()));
        if (xmlCol.isSetTextAlign())
            _textAlign = xmlCol.getTextAlign();
        if (xmlCol.isSetPropertyURI())
            _propertyURI = xmlCol.getPropertyURI();
        if (xmlCol.isSetSortColumn())
            setSortFieldKeysFromXml(xmlCol.getSortColumn());
        if (xmlCol.isSetSortDescending())
            setSortDirection(xmlCol.getSortDescending() ? Sort.SortDirection.DESC : Sort.SortDirection.ASC);
        if (xmlCol.isSetDescription())
            _description = xmlCol.getDescription();
        if (xmlCol.isSetIsHidden())
            _hidden = xmlCol.getIsHidden();
        if (xmlCol.isSetShownInInsertView())
            _shownInInsertView = xmlCol.getShownInInsertView();
        if (xmlCol.isSetShownInUpdateView())
            _shownInUpdateView = xmlCol.getShownInUpdateView();
        if (xmlCol.isSetShownInDetailsView())
            _shownInDetailsView = xmlCol.getShownInDetailsView();
        if (xmlCol.isSetDimension())
            _dimension = xmlCol.getDimension();
        if (xmlCol.isSetMeasure())
            _measure = xmlCol.getMeasure();
        if (xmlCol.isSetRecommendedVariable())
            _recommendedVariable = xmlCol.getRecommendedVariable();
        else if (xmlCol.isSetKeyVariable())
            _recommendedVariable = xmlCol.getKeyVariable();
        if (xmlCol.isSetDefaultScale())
            _defaultScale = DefaultScaleType.valueOf(xmlCol.getDefaultScale().toString());
        if (xmlCol.isSetIsUnselectable())
            _isUnselectable = xmlCol.getIsUnselectable();
        if (xmlCol.isSetIsKeyField())
            _isKeyField = xmlCol.getIsKeyField();
        if (xmlCol.isSetDisplayWidth())
            setDisplayWidth(xmlCol.getDisplayWidth());
        if (xmlCol.isSetRequired())
            _required = xmlCol.getRequired();
        if (xmlCol.isSetNullable())
            _nullable = xmlCol.getNullable();
        if (xmlCol.isSetExcludeFromShifting())
            _isExcludeFromShifting = xmlCol.getExcludeFromShifting();
        if (xmlCol.isSetImportAliases())
        {
            LinkedHashSet<String> set = new LinkedHashSet<>(getImportAliasSet());
            set.addAll(Arrays.asList(xmlCol.getImportAliases().getImportAliasArray()));
            setImportAliasesSet(set);
        }
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
                _phi = PHI.Limited;  // always convert protected to limited PHI; may be overridden by getPhi(), though
        }
        if (xmlCol.isSetPhi())
            _phi = PHI.valueOf(xmlCol.getPhi().toString());
        if (xmlCol.isSetFacetingBehavior())
            _facetingBehaviorType = FacetingBehaviorType.valueOf(xmlCol.getFacetingBehavior().toString());
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
                    case "DEFAULT" -> _displayColumnFactory = DEFAULT_FACTORY;
                    case "NOWRAP" -> _displayColumnFactory = NOWRAP_FACTORY;
                    case "NOLOOKUP" -> _displayColumnFactory = NOLOOKUP_FACTORY;
                    default -> {
                        Class<?> clazz = Class.forName(displayColumnClassName);
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
                            LOG.error("Class is not a DisplayColumnFactory: " + displayColumnClassName);
                        }
                    }
                }
            }
            catch (ClassNotFoundException | InstantiationException | IllegalAccessException | NoSuchMethodException | InvocationTargetException e)
            {
                String message = "Can't instantiate DisplayColumnFactory: " + displayColumnClassName;
                // Defer logging an error until column is actually used, Issue #44103
                // Substitute a factory that provides a renderer that displays and logs the error at render time
                LOG.debug(message, e);
                _displayColumnFactory = colInfo -> {
                    LOG.error(message, e);
                    return new SimpleDisplayColumn()
                    {
                        @Override
                        public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
                        {
                            out.write(PageFlowUtil.filter("Error: " + message));
                        }
                    };
                };
            }
        }

        var os = OntologyService.get();
        if (null != os)
        {
            os.parseXml(xmlCol, this);
        }
    }

    @Override
    public void setSortFieldKeysFromXml(String xml)
    {
        List<FieldKey> keys = Arrays.stream(StringUtils.split(xml, ','))
                .filter(StringUtils::isNotBlank)
                .map(FieldKey::fromString)
                .collect(Collectors.toList());
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

    // TODO why is there here? and not something like RequestHelper or PageFlowUtil
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
        return ColumnInfo.toString(this);
    }

    // UNDONE: Do we still need DomainProperty for this?
    @Override
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

        @Override
        public ColumnInfo createLookupColumn(ColumnInfo foreignKey, String displayField)
        {
            TableInfo lookupTable = getLookupTableInfo();
            if (null == lookupTable)
            {
                return null;
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

        @Override
        public String getLookupDisplayName()
        {
            return _displayColumnName;
        }

        public boolean isUseRawFKValue()
        {
            return _useRawFKValue;
        }

        @Override
        public TableInfo getLookupTableInfo()
        {
            DbSchema schema = _scope.getSchema(_dbSchemaName, DbSchemaType.Unknown);
            return schema.getTable(_tableName);
        }

        @Override
        public StringExpression getURL(ColumnInfo parent)
        {
            return null;
        }

        public boolean isJoinWithContainer()
        {
            return _joinWithContainer;
        }

        @Override
        public Container getLookupContainer()
        {
            return null;
        }

        @Override
        public String getLookupTableName()
        {
            return _tableName;
        }

        @Override
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

        @Override
        public String getLookupSchemaName()
        {
            return _dbSchemaName;
        }

        @Override
        public @NotNull NamedObjectList getSelectList(RenderContext ctx)
        {
            TableInfo lookupTable = getLookupTableInfo();
            if (lookupTable == null)
                return new NamedObjectList();

            return lookupTable.getSelectList(getLookupColumnName(), Collections.emptyList(), null, null);
        }

        @Override
        public ForeignKey remapFieldKeys(FieldKey parent, Map<FieldKey, FieldKey> mapping)
        {
            return this;
        }

        @Override
        public Set<FieldKey> getSuggestedColumns()
        {
            return null;
        }
    }

    @Override
    public DisplayColumn getRenderer()
    {
        if (_displayField == null || _displayField == this)
        {
            return getDisplayColumnFactory().createRenderer(this);
        }
        else
        {
            return _displayField.getRenderer();
        }
    }


    public static Collection<BaseColumnInfo> createFromDatabaseMetaData(String schemaName, SchemaTableInfo parentTable, @Nullable String columnNamePattern) throws SQLException
    {
         //Use linked hash map to preserve ordering...
        LinkedHashMap<String, BaseColumnInfo> colMap = new LinkedHashMap<>();
        SqlDialect dialect = parentTable.getSqlDialect();
        DbScope scope = parentTable.getSchema().getScope();
        Map<String, ImportedKey> importedKeys = new HashMap<>();    // Use map to handle multiple FKs with multiple fields from same table referencing same PK

        try (JdbcMetaDataLocator locator = dialect.getTableResolver().getSingleTableLocator(scope, schemaName, parentTable))
        {
            JdbcMetaDataSelector columnSelector = new JdbcMetaDataSelector(locator, (dbmd, loc) -> dbmd.getColumns(loc.getCatalogName(), loc.getSchemaNamePattern(), loc.getTableNamePattern(), columnNamePattern));

            try (ResultSet rsCols = columnSelector.getResultSet())
            {
                ColumnMetaDataReader reader = dialect.getColumnMetaDataReader(rsCols, parentTable);

                while (rsCols.next())
                {
                    String metaDataName = reader.getName();
                    var col = new BaseColumnInfo(metaDataName, parentTable, dialect.getJdbcType(reader.getSqlType(), reader.getSqlTypeName()));

                    col._metaDataName = metaDataName;
                    col._selectName = dialect.getSelectNameFromMetaDataName(metaDataName);
                    col._sqlTypeName = reader.getSqlTypeName();
                    col._isAutoIncrement = reader.isAutoIncrement();
                    int type = reader.getSqlType();
                    if (type == Types.DECIMAL || type == Types.NUMERIC)
                    {
                        col._scale = reader.getDecimalDigits();
                        col._precision = reader.getScale();
                    }
                    else
                    {
                        col._scale = reader.getScale();
                    }
                    col._nullable = reader.isNullable();
                    col._jdbcDefaultValue = reader.getDefault();

                    // isCalculated is typically an query-level ExprColumn, but in this case we have a real calculated column in the database
                    col.setCalculated(reader.isGeneratedColumn());

                    inferMetadata(col);

                    col._label = reader.getLabel();
                    col._description = reader.getDescription();

                    if (NON_EDITABLE_COL_NAMES.contains(col.getPropertyName()))
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
                int iPkTableSchema = ColumnInfo.findColumn(rsKeys, "PKTABLE_SCHEM");
                int iPkTableName   = ColumnInfo.findColumn(rsKeys, "PKTABLE_NAME");
                int iPkColumnName  = ColumnInfo.findColumn(rsKeys, "PKCOLUMN_NAME");
                int iFkColumnName  = ColumnInfo.findColumn(rsKeys, "FKCOLUMN_NAME");
                int iKeySequence   = ColumnInfo.findColumn(rsKeys, "KEY_SEQ");
                int iFkName        = ColumnInfo.findColumn(rsKeys, "FK_NAME");

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

            if (i == -1)
            {
                LOG.warn("Skipping multiple column foreign key " + key.fkName + " ON " + parentTable.getName());
                continue;
            }

            String colName = key.fkColumnNames.get(i);
            var col = colMap.get(colName);

            if (col == null)
            {
                LOG.error("Column in FK definition was not found " + colName + ". Skipping constraint " + key.fkName);
                continue;
            }

            if (col._fk != null)
            {
                LOG.warn("More than one FK defined for column " + parentTable.getName() + "." + col.getName() + ". Skipping constraint " + key.fkName);
                continue;
            }

            col._fk = new SchemaForeignKey(col, key.pkSchemaName, key.pkTableName, key.pkColumnNames.get(i), joinWithContainer);
        }

        return colMap.values();
    }


    private static void inferMetadata(BaseColumnInfo col)
    {
        String colName = col.getName();
        DbSchema schema = col.getParentTable().getSchema();

        if (col._metaDataName.startsWith("_"))
        {
            col.setHidden(true);
        }

        if (col._isAutoIncrement || col.isCalculated())
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

        if (null == col.getInputType() && col.getJdbcType().getJavaClass().equals(String.class) && col._scale > 255)
        {
            col.setInputType("textarea");
        }
    }


    @Override
    public String getSqlTypeName()
    {
        if (null == _sqlTypeName && (_propertyType != null || _jdbcType != null))
        {
            SqlDialect d;
            if (getParentTable() == null)
                d = CoreSchema.getInstance().getSqlDialect();
            else
                d = getParentTable().getSqlDialect();

            JdbcType jt = _propertyType != null ? _propertyType.getJdbcType() : _jdbcType;
            _sqlTypeName = d.getSqlTypeName(jt);
        }
        return _sqlTypeName;
    }



    @Override
    public void setSqlTypeName(String sqlTypeName)
    {
        checkLocked();
        _sqlTypeName = sqlTypeName;
        _jdbcType = null;
    }

    @Override
    @Nullable
    public List<FieldKey> getSortFieldKeys()
    {
        if (null == _sortFieldKeys || _sortFieldKeys.isEmpty())
            return null;
        return _sortFieldKeys;
    }

    @Override
    public void setSortFieldKeys(List<FieldKey> sortFieldKeys)
    {
        checkLocked();
        _sortFieldKeys = copyFixedList(sortFieldKeys);
    }

    @Override
    public void setJdbcType(JdbcType type)
    {
        checkLocked();
        _jdbcType = type;
        _sqlTypeName = null;
    }


    @Override
    public @NotNull JdbcType getJdbcType()
    {
        if (_jdbcType == null && (_propertyType != null || _sqlTypeName != null))
        {
            if (_propertyType != null)
            {
                _jdbcType = _propertyType.getJdbcType();
            }
            else // we're here because sqlTypeName != null
            {
                SqlDialect d;
                if (getParentTable() == null)
                    d = CoreSchema.getInstance().getSqlDialect();
                else
                    d = getParentTable().getSqlDialect();
                int type = d.sqlTypeIntFromSqlTypeName(_sqlTypeName);
                _jdbcType = JdbcType.valueOf(type);
            }
        }
        return _jdbcType == null ? JdbcType.OTHER : _jdbcType;
    }


    @Override
    public ForeignKey getFk()
    {
        return _fk;
    }

    @Override
    public void clearFk()
    {
        checkLocked();
        _fk = null;
    }

    @Override
    public void setFk(@Nullable ForeignKey fk)
    {
        checkLocked();
        _fk = fk;
    }

    @Override
    public void setFk(@NotNull Builder<ForeignKey> b)
    {
        checkLocked();
        _fk = b.build();
    }


    @Override
    public void setScale(int scale)
    {
        checkLocked();
        super.setScale(scale);
    }

    @Override
    public void setPrecision(int precision)
    {
        checkLocked();
        super.setPrecision(precision);
    }


    /** @return whether the column is part of the primary key for the table */
    @Override
    public boolean isKeyField()
    {
        return _isKeyField;
    }


    @Override
    public void setKeyField(boolean keyField)
    {
        checkLocked();
        _isKeyField = keyField;
    }

    @Override
    public boolean isMvEnabled()
    {
        return _mvColumnName != null;
    }

    @Override
    public FieldKey getMvColumnName()
    {
        return _mvColumnName;
    }

    @Override
    public void setMvColumnName(FieldKey mvColumnName)
    {
        checkLocked();
        _mvColumnName = mvColumnName;
    }

    @Override
    public boolean isMvIndicatorColumn()
    {
        return _isMvIndicatorColumn;
    }

    @Override
    public void setMvIndicatorColumn(boolean mvIndicatorColumn)
    {
        checkLocked();
        _isMvIndicatorColumn = mvIndicatorColumn;
    }

    @Override
    public boolean isRawValueColumn()
    {
        return _isRawValueColumn;
    }

    @Override
    public void setRawValueColumn(boolean rawColumn)
    {
        checkLocked();
        _isRawValueColumn = rawColumn;
    }

    /**
     * Returns true if this column does not contain data that should be queried, but is a lookup into a valid table.
     *
     */
    @Override
    public boolean isUnselectable()
    {
        return _isUnselectable;
    }

    @Override
    public void setIsUnselectable(boolean b)
    {
        checkLocked();
        _isUnselectable = b;
    }


    @Override
    public TableInfo getParentTable()
    {
        return _parentTable;
    }


    @Override
    public void setParentTable(TableInfo parentTable)
    {
        checkLocked();
        _parentTable = parentTable;
        _columnLogging.setOriginalTableName(null != parentTable ? parentTable.getName() : "");
    }

    @Override
    public String getColumnName()
    {
        return getName();
    }

    @Override
    public Object getValue(ResultSet rs) throws SQLException
    {
        if (rs == null)
            return null;
        // UNDONE
        return rs.getObject(getAlias());
    }

    @Override
    public int getIntValue(ResultSet rs) throws SQLException
    {
        // UNDONE
        return rs.getInt(getAlias());
    }

    @Override
    public String getStringValue(ResultSet rs) throws SQLException
    {
        // UNDONE
        return rs.getString(getAlias());
    }

    @Override
    public Object getValue(RenderContext context)
    {
        return context.get(getFieldKey());
    }

    @Override
    public Object getValue(Map<String, ?> map)
    {
        if (map == null)
            return null;
        // UNDONE
        return map.get(getAlias());
    }

    @Override
    public DefaultValueType getDefaultValueType()
    {
        return _defaultValueType;
    }

    @Override
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

    @Override
    @NotNull
    public List<ConditionalFormat> getConditionalFormats()
    {
        return _conditionalFormats;
    }

    @Override
    public void setConditionalFormats(@NotNull List<ConditionalFormat> formats)
    {
        checkLocked();
        _conditionalFormats = copyFixedList(formats);
    }

    @Override
    @NotNull
    public List<? extends IPropertyValidator> getValidators()
    {
        return _validators;
    }

    @Override
    public void setValidators(List<? extends IPropertyValidator> validators)
    {
        checkLocked();
        _validators = copyFixedList(validators);
    }


    @Override
    public void checkLocked()
    {
        if (_locked)
            throw new IllegalStateException("ColumnInfo is locked: " + (null!=getParentTable()?getParentTable().getName()+".":"") + getName());
    }

    boolean _locked;

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
    public boolean isCalculated()
    {
        return _calculated;
    }

    @Override
    public void setCalculated(boolean calculated)
    {
        checkLocked();
        _calculated = calculated;
    }


    // If true, you can't use this column when auto-generating LabKey SQL, it is not selected in the underlying query
    // only query can set this true
    @Override
    public boolean isAdditionalQueryColumn()
    {
        return false;
    }

    @Override
    public void setColumnLogging(ColumnLogging columnLogging)
    {
        checkLocked();
        _columnLogging = columnLogging;
    }

    @Override
    public ColumnLogging getColumnLogging()
    {
        return _columnLogging;
    }

    @Override
    public SimpleTranslator.RemapMissingBehavior getRemapMissingBehavior()
    {
        return _remapMissingBehavior;
    }

    @Override
    public void setRemapMissingBehavior(SimpleTranslator.RemapMissingBehavior missingBehavior)
    {
        _remapMissingBehavior = missingBehavior;
    }
}
