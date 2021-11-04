package org.labkey.api.data;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.dataiterator.SimpleTranslator;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.IPropertyValidator;
import org.labkey.api.gwt.client.DefaultScaleType;
import org.labkey.api.gwt.client.DefaultValueType;
import org.labkey.api.gwt.client.FacetingBehaviorType;
import org.labkey.api.query.FieldKey;
import org.labkey.api.util.StringExpression;
import org.labkey.data.xml.ColumnType;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Set;


/**
 *  PURE wrapper, does not support mutation/overridding except for values provided to constructor of subclass
 */
public abstract class AbstractWrappedColumnInfo implements ColumnInfo
{
    @NotNull ColumnInfo delegate;

    protected AbstractWrappedColumnInfo(@NotNull ColumnInfo delegate_)
    {
        assert delegate_ != null;
        this.delegate = delegate_;
    }

    @Override
    public String getName()
    {
        FieldKey fieldKey = getFieldKey();
        if (fieldKey.getParent() == null)
            return fieldKey.getName();
        else
            return fieldKey.toString();
    }

    @Override
    public FieldKey getFieldKey()
    {
        return delegate.getFieldKey();
    }

    @Override
    public boolean isAliasSet()
    {
        return delegate.isAliasSet();
    }

    @Override
    public String getAlias()
    {
        return delegate.getAlias();
    }

    @Override
    public String getMetaDataName()
    {
        return delegate.getMetaDataName();
    }

    @Override
    public String getSelectName()
    {
        return delegate.getSelectName();
    }

    @Override
    public SQLFragment getValueSql(String tableAliasName)
    {
        return delegate.getValueSql(tableAliasName);
    }

    @Override
    public String getPropertyURI()
    {
        return delegate.getPropertyURI();
    }

    @Override
    public void declareJoins(String parentAlias, Map<String, SQLFragment> map)
    {
        delegate.declareJoins(parentAlias, map);
    }

    @Override
    public String getTableAlias(String baseAlias)
    {
        return delegate.getTableAlias(baseAlias);
    }

    @Override
    public SqlDialect getSqlDialect()
    {
        return delegate.getSqlDialect();
    }

    @Override
    public String getLabelValue()
    {
        return delegate.getLabelValue();
    }

    @Override
    public String getLabel()
    {
        if (null == getLabelValue() && getFieldKey() != null)
            return ColumnInfo.labelFromName(getFieldKey().getName());
        return getLabelValue();
    }

    @Override
    public boolean isFormatStringSet()
    {
        return delegate.isFormatStringSet();
    }

    @Override
    public String getTextAlign()
    {
        return delegate.getTextAlign();
    }

    @Override
    public String getJdbcDefaultValue()
    {
        return delegate.getJdbcDefaultValue();
    }

    @Override
    public Object getDefaultValue()
    {
        return delegate.getDefaultValue();
    }

    @Override
    @Nullable
    public ColumnInfo getDisplayField()
    {
        return delegate.getDisplayField();
    }

    @Override
    public ColumnInfo getFilterField()
    {
        return delegate.getFilterField();
    }

    @Override
    public boolean isNoWrap()
    {
        return delegate.isNoWrap();
    }

    @Override
    @NotNull
    public String getWidth()
    {
        return delegate.getWidth();
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
        return delegate.isUserEditable();
    }

    @Override
    public DisplayColumnFactory getDisplayColumnFactory()
    {
        return delegate.getDisplayColumnFactory();
    }

    @Override
    public boolean isShouldLog()
    {
        return delegate.isShouldLog();
    }

    @Override
    public String getLegalName()
    {
        return delegate.getLegalName();
    }

    @Override
    public String getPropertyName()
    {
        return delegate.getPropertyName();
    }

    @Override
    public String getJdbcRsName()
    {
        return delegate.getJdbcRsName();
    }

    @Override
    public boolean isVersionColumn()
    {
        return delegate.isVersionColumn();
    }

    @Override
    public SQLFragment getVersionUpdateExpression()
    {
        return delegate.getVersionUpdateExpression();
    }

    @Override
    public String getInputType()
    {
        return delegate.getInputType();
    }

    @Override
    public int getInputLength()
    {
        return delegate.getInputLength();
    }

    @Override
    public int getInputRows()
    {
        return delegate.getInputRows();
    }

    @Override
    public String getNameExpression()
    {
        return delegate.getNameExpression();
    }

    @Override
    public boolean isAutoIncrement()
    {
        return delegate.isAutoIncrement();
    }

    @Override
    public boolean isReadOnly()
    {
        return delegate.isReadOnly();
    }

    @Override
    public StringExpression getEffectiveURL()
    {
        return ColumnInfo.getEffectiveURL(this);
    }

    @Override
    public void copyToXml(ColumnType xmlCol, boolean full)
    {
        delegate.copyToXml(xmlCol, full);
    }

    @Override
    public boolean isRequiredForInsert(@Nullable DomainProperty dp)
    {
        return delegate.isRequiredForInsert(dp);
    }

    @Override
    public DisplayColumn getRenderer()
    {
        // TODO duplicate code BaseColumnInfo
        // TODO this assumes that displayField has been appropriately wrapped as well as 'this'
        ColumnInfo displayField = getDisplayField();
        if (displayField == null || displayField == this)
        {
            return getDisplayColumnFactory().createRenderer(this);
        }
        else
        {
            assert displayField.getParentTable() == getParentTable();
            return displayField.getRenderer();
        }
    }

    @Override
    public String getSqlTypeName()
    {
        return delegate.getSqlTypeName();
    }

    @Override
    public List<FieldKey> getSortFieldKeys()
    {
        return delegate.getSortFieldKeys();
    }

    @Override
    @NotNull
    public JdbcType getJdbcType()
    {
        return delegate.getJdbcType();
    }

    @Override
    public ForeignKey getFk()
    {
        return delegate.getFk();
    }

    @Override
    public boolean isKeyField()
    {
        return delegate.isKeyField();
    }

    @Override
    public boolean isMvEnabled()
    {
        return delegate.isMvEnabled();
    }

    @Override
    public FieldKey getMvColumnName()
    {
        return delegate.getMvColumnName();
    }

    @Override
    public boolean isMvIndicatorColumn()
    {
        return delegate.isMvIndicatorColumn();
    }

    @Override
    public boolean isRawValueColumn()
    {
        return delegate.isRawValueColumn();
    }

    @Override
    public boolean isUnselectable()
    {
        return delegate.isUnselectable();
    }

    @Override
    public TableInfo getParentTable()
    {
        return delegate.getParentTable();
    }

    @Override
    public String getColumnName()
    {
        return getName();
    }

    @Override
    public Object getValue(ResultSet rs) throws SQLException
    {
        return delegate.getValue(rs);
    }

    @Override
    public int getIntValue(ResultSet rs) throws SQLException
    {
        return delegate.getIntValue(rs);
    }

    @Override
    public String getStringValue(ResultSet rs) throws SQLException
    {
        return delegate.getStringValue(rs);
    }

    @Override
    public Object getValue(RenderContext context)
    {
        return delegate.getValue(context);
    }

    @Override
    public Object getValue(Map<String, ?> map)
    {
        return delegate.getValue(map);
    }

    @Override
    public DefaultValueType getDefaultValueType()
    {
        return delegate.getDefaultValueType();
    }

    @Override
    public boolean isLookup()
    {
        return delegate.isLookup();
    }

    @Override
    @NotNull
    public List<ConditionalFormat> getConditionalFormats()
    {
        return delegate.getConditionalFormats();
    }

    @Override
    @NotNull
    public List<? extends IPropertyValidator> getValidators()
    {
        return delegate.getValidators();
    }

    @Override
    public boolean inferIsShownInInsertView()
    {
        return delegate.inferIsShownInInsertView();
    }

    @Override
    public boolean isCalculated()
    {
        return delegate.isCalculated();
    }

    @Override
    public boolean isAdditionalQueryColumn()
    {
        return delegate.isAdditionalQueryColumn();
    }

    @Override
    public ColumnLogging getColumnLogging()
    {
        return delegate.getColumnLogging();
    }

    @Override
    public SimpleTranslator.RemapMissingBehavior getRemapMissingBehavior()
    {
        return delegate.getRemapMissingBehavior();
    }

    @Override
    public void copyTo(ColumnRenderPropertiesImpl to)
    {
        throw new UnsupportedOperationException("NYI");
    }

    @Override
    public String getNonBlankCaption()
    {
        return delegate.getNonBlankCaption();
    }

    @Override
    public Sort.SortDirection getSortDirection()
    {
        return delegate.getSortDirection();
    }

    @Override
    public String getDisplayWidth()
    {
        return delegate.getDisplayWidth();
    }

    @Override
    public String getFormat()
    {
        return delegate.getFormat();
    }

    @Override
    public String getExcelFormatString()
    {
        return delegate.getExcelFormatString();
    }

    @Override
    public String getTsvFormatString()
    {
        return delegate.getTsvFormatString();
    }

    @Override
    public StringExpression getTextExpression()
    {
        return delegate.getTextExpression();
    }

    @Override
    public String getShortLabel()
    {
        return delegate.getShortLabel();
    }

    @Override
    public String getDescription()
    {
        return delegate.getDescription();
    }

    @Override
    public boolean isHidden()
    {
        return delegate.isHidden();
    }

    @Override
    public boolean isShownInDetailsView()
    {
        return delegate.isShownInDetailsView();
    }

    @Override
    public boolean isShownInInsertView()
    {
        return delegate.isShownInInsertView();
    }

    @Override
    public boolean isShownInUpdateView()
    {
        return delegate.isShownInUpdateView();
    }

    @Override
    public StringExpression getURL()
    {
        return delegate.getURL();
    }

    @Override
    public String getURLTargetWindow()
    {
        return delegate.getURLTargetWindow();
    }

    @Override
    public String getURLCls()
    {
        return delegate.getURLCls();
    }

    @Override
    public String getOnClick()
    {
        return delegate.getOnClick();
    }

    @Override
    public boolean isRecommendedVariable()
    {
        return delegate.isRecommendedVariable();
    }

    @Override
    public DefaultScaleType getDefaultScale()
    {
        return delegate.getDefaultScale();
    }

    @Override
    public boolean isDimension()
    {
        return delegate.isDimension();
    }

    @Override
    public boolean isMeasure()
    {
        return delegate.isMeasure();
    }

    @Override
    public boolean isNullable()
    {
        return delegate.isNullable();
    }

    @Override
    public boolean isRequired()
    {
        return delegate.isRequired();
    }

    @Override
    public boolean isRequiredSet()
    {
        return delegate.isRequiredSet();
    }

    @Override
    @NotNull
    public Set<String> getImportAliasSet()
    {
        return delegate.getImportAliasSet();
    }

    @Override
    @Nullable
    public PropertyType getPropertyType()
    {
        return delegate.getPropertyType();
    }

    @Override
    public String getConceptURI()
    {
        return delegate.getConceptURI();
    }

    @Override
    public String getRangeURI()
    {
        return delegate.getRangeURI();
    }

    @Override
    public boolean isDateTimeType()
    {
        return delegate.isDateTimeType();
    }

    @Override
    public boolean isStringType()
    {
        return delegate.isStringType();
    }

    @Override
    public boolean isLongTextType()
    {
        return delegate.isLongTextType();
    }

    @Override
    public boolean isBooleanType()
    {
        return delegate.isBooleanType();
    }

    @Override
    public boolean isNumericType()
    {
        return delegate.isNumericType();
    }

    @Override
    public boolean isUniqueIdField()
    {
        return delegate.isUniqueIdField();
    }

    @Override
    public String getFriendlyTypeName()
    {
        return delegate.getFriendlyTypeName();
    }

    @Override
    public Class getJavaObjectClass()
    {
        return delegate.getJavaObjectClass();
    }

    @Override
    public Class getJavaClass()
    {
        return delegate.getJavaClass();
    }

    @Override
    public Class getJavaClass(boolean isNullable)
    {
        return delegate.getJavaClass(isNullable);
    }

    @Override
    public FacetingBehaviorType getFacetingBehaviorType()
    {
        return delegate.getFacetingBehaviorType();
    }

    @Override
    public FieldKey getCrosstabColumnDimension()
    {
        return delegate.getCrosstabColumnDimension();
    }

    @Override
    public CrosstabMember getCrosstabColumnMember()
    {
        return delegate.getCrosstabColumnMember();
    }

    @Override
    public PHI getPHI()
    {
        return delegate.getPHI();
    }

    @Override
    public String getRedactedText()
    {
        return delegate.getRedactedText();
    }

    @Override
    public boolean isExcludeFromShifting()
    {
        return delegate.isExcludeFromShifting();
    }

    @Override
    public int getScale()
    {
        return delegate.getScale();
    }

    @Override
    public int getPrecision()
    {
        return delegate.getPrecision();
    }

    @Override
    public boolean hasDbSequence()
    {
        return delegate.hasDbSequence();
    }

    @Override
    public boolean isRootDbSequence()
    {
        return delegate.isRootDbSequence();
    }

    @Override
    public Container getDbSequenceContainer(Container container)
    {
        return delegate.getDbSequenceContainer(container);
    }

    @Override
    public String getPrincipalConceptCode()
    {
        return delegate.getPrincipalConceptCode();
    }

    @Override
    public boolean isConceptColumn()
    {
        return delegate.isConceptColumn();
    }

    @Override
    public String getSourceOntology()
    {
        return delegate.getSourceOntology();
    }

    @Override
    public String getConceptSubtree()
    {
        return delegate.getConceptSubtree();
    }

    @Override
    public String getConceptImportColumn()
    {
        return delegate.getConceptImportColumn();
    }

    @Override
    public String getConceptLabelColumn()
    {
        return delegate.getConceptLabelColumn();
    }

    @Override
    public String getDerivationDataScope()
    {
        return delegate.getDerivationDataScope();
    }

    @Override
    public String toString()
    {
        return ColumnInfo.toString(this);
    }
}
