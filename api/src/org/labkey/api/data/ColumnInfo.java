package org.labkey.api.data;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.IPropertyValidator;
import org.labkey.api.gwt.client.DefaultValueType;
import org.labkey.api.query.FieldKey;
import org.labkey.api.util.StringExpression;
import org.labkey.data.xml.ColumnType;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public interface ColumnInfo extends ColumnRenderProperties
{
    String DEFAULT_PROPERTY_URI_PREFIX = "http://terms.fhcrc.org/dbschemas/";
    DisplayColumnFactory DEFAULT_FACTORY = BaseColumnInfo.DEFAULT_FACTORY;
    DisplayColumnFactory NOWRAP_FACTORY = BaseColumnInfo.NOWRAP_FACTORY;
    DisplayColumnFactory NOLOOKUP_FACTORY = BaseColumnInfo.NOLOOKUP_FACTORY;

    class ImportedKey
    {
        protected final String fkName;
        protected final String pkSchemaName;
        protected final String pkTableName;
        protected final ArrayList<String> pkColumnNames = new ArrayList<>(2);
        protected final ArrayList<String> fkColumnNames = new ArrayList<>(2);

        public ImportedKey(String fkName, String pkSchemaName, String pkTableName, String pkColumnName, String colName)
        {
            this.fkName = fkName;
            this.pkSchemaName = pkSchemaName;
            this.pkTableName = pkTableName;
            pkColumnNames.add(pkColumnName);
            fkColumnNames.add(colName);
        }
    }

    // Safe version of findColumn().  Returns jdbc column index to specified column, or 0 if it doesn't exist or an
    // exception occurs.  SAS JDBC driver throws when attempting to resolve indexes when no records exist.
    static int findColumn(ResultSet rs, String name)
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

    @Override
    String getName();

    FieldKey getFieldKey();

    // use only for debugging, will change after call to getAlias()
    boolean isAliasSet();

    String getAlias();

    String getMetaDataName();

    String getSelectName();

    SQLFragment getValueSql(String tableAliasName);

    @Override
    String getPropertyURI();

    void declareJoins(String parentAlias, Map<String, SQLFragment> map);

    String getTableAlias(String baseAlias);

    SqlDialect getSqlDialect();

    // Return the actual value we have stashed; use this when copying attributes, so you don't hard-code label
    String getLabelValue();

    @Override
    String getLabel();

    boolean isFormatStringSet();

    String getTextAlign();

    String getJdbcDefaultValue();

    Object getDefaultValue();

    @Nullable ColumnInfo getDisplayField();

    @Nullable List<ColumnInfo> getSortFields();

    ColumnInfo getFilterField();

    boolean isNoWrap();

    @NotNull String getWidth();

    TableInfo getFkTableInfo();

    boolean isUserEditable();

    DisplayColumnFactory getDisplayColumnFactory();

    boolean isShouldLog();

    String getLegalName();

    String getPropertyName();

    String getJdbcRsName();

    /**
     * Version column can be used for optimistic concurrency.
     * for now we assume that this column is never updated
     * explicitly.
     */
    boolean isVersionColumn();

    SQLFragment getVersionUpdateExpression();

    String getInputType();

    int getInputLength();

    int getInputRows();

    boolean isAutoIncrement();

    boolean isReadOnly();

    StringExpression getEffectiveURL();

    void copyToXml(ColumnType xmlCol, boolean full);

    // UNDONE: Do we still need DomainProperty for this?
    boolean isRequiredForInsert(@Nullable DomainProperty dp);

    DisplayColumn getRenderer();

    String getSqlTypeName();

    List<FieldKey> getSortFieldKeys();

    @NotNull JdbcType getJdbcType();

    ForeignKey getFk();

    /**
     * @return whether the column is part of the primary key for the table
     */
    boolean isKeyField();

    @Override
    boolean isMvEnabled();

    FieldKey getMvColumnName();

    boolean isMvIndicatorColumn();

    boolean isRawValueColumn();

    /**
     * Returns true if this column does not contain data that should be queried, but is a lookup into a valid table.
     */
    boolean isUnselectable();

    TableInfo getParentTable();

    String getColumnName();

    Object getValue(ResultSet rs) throws SQLException;

    int getIntValue(ResultSet rs) throws SQLException;

    String getStringValue(ResultSet rs) throws SQLException;

    Object getValue(RenderContext context);

    Object getValue(Map<String, ?> map);

    DefaultValueType getDefaultValueType();

    boolean isLookup();

    @NotNull List<ConditionalFormat> getConditionalFormats();

    @NotNull List<? extends IPropertyValidator> getValidators();

    /**
     * Unfortunately we cannot differentiate cases shownInInsertView is not set (it defaults to true) from cases where it is explicitly set to true
     * This is an attempt to centralize code to ignore columns that we assume should not actually be shownInInsertView
     * At some future point we should consider better handling true/false/null (ie. has not explicitly been set) for properties like
     * shownInInsertView, shownInUpdateView, etc.
     */

    default boolean inferIsShownInInsertView()
    {
        return !isNullable() && isUserEditable() && !isAutoIncrement() && isShownInInsertView();
    }

    boolean isCalculated();

    // If true, you can't use this column when auto-generating LabKey SQL, it is not selected in the underlying query
    // only query can set this true
    boolean isAdditionalQueryColumn();

    ColumnLogging getColumnLogging();

    // statics added to make conversion easier
    static String labelFromName(String name)
    {
        return BaseColumnInfo.labelFromName(name);
    }

    static String propNameFromName(String name)
    {
        return BaseColumnInfo.propNameFromName(name);
    }

    static String legalNameFromName(String name)
    {
        return BaseColumnInfo.legalNameFromName(name);
    }

    static String getFriendlyTypeName(Class javaClass)
    {
        return ColumnRenderProperties.getFriendlyTypeName(javaClass);
    }

    static boolean booleanFromString(String str)
    {
        return BaseColumnInfo.booleanFromString(str);
    }
    static boolean booleanFromObj(Object o)
    {
        return BaseColumnInfo.booleanFromObj(o);
    }
}
