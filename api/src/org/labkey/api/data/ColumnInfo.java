/*
 * Copyright (c) 2008-2019 LabKey Corporation
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

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.dataiterator.SimpleTranslator;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.IPropertyValidator;
import org.labkey.api.gwt.client.DefaultValueType;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.PdLookupForeignKey;
import org.labkey.api.query.QueryParseException;
import org.labkey.api.query.UserIdRenderer;
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

    DisplayColumnFactory DEFAULT_FACTORY = new DisplayColumnFactory()
    {
        @Override
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
                if ("core".equalsIgnoreCase(lfk.getLookupSchemaName()) && ("siteusers".equalsIgnoreCase(lfk.getLookupTableName()) || "users".equalsIgnoreCase(lfk.getLookupTableName())))
                    return true;
            }
            return false;
        }
    };
    DisplayColumnFactory NOWRAP_FACTORY = colInfo ->
    {
        DataColumn dataColumn = new DataColumn(colInfo);
        dataColumn.setNoWrap(true);
        return dataColumn;
    };
    DisplayColumnFactory NOLOOKUP_FACTORY = colInfo -> new DataColumn(colInfo, false);

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

    /** If this column is represented by a column in the database, this is the name as returned by database metadata */
    String getMetaDataName();

    /**
     * If this column represents a column in the database (see getMetaDataName()),
     * then this method will return the name escaped in a way that is suitable for using in SQL (e.g. quoted)
     *
     * This is especially useful for generating INSERT/UPDATE statement when using SchemaTableInfo.
     * ColumnInfo.getValueSql() is the more general method and should be preferred for most usages.
     */
    String getSelectName();

    /**
     * Use this method to generate database SQL for selecting data from this column.
     *
     * <pre>
     *     new SQLFragment(
     *         "SELECT ").append(ti.getColumn("A").getValueSql("tablealias").append(" AS foo\n" ).append(
     *         "FROM ").append(ti.getFromSql("tablelias"));
     * </pre>
     *
     * @param tableAliasName
     * @return
     */
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

    // TODO return a FieldKey instead of a ColumnInfo
    @Nullable ColumnInfo getDisplayField();

    // TODO return a FieldKey instead of a ColumnInfo
    ColumnInfo getFilterField();

    boolean isNoWrap();

    @NotNull String getWidth();

    TableInfo getFkTableInfo();

    TableDescription getFkTableDescription();

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

    @Override
    String getInputType();

    @Override
    int getInputLength();

    @Override
    int getInputRows();

    @Override
    boolean isAutoIncrement();

    boolean isReadOnly();

    StringExpression getEffectiveURL();

    static StringExpression getEffectiveURL(ColumnInfo col)
    {
        StringExpression result = col.getURL();
        if (result != null)
            return result;
        ForeignKey fk = col.getFk();
        if (fk == null)
            return null;

        try
        {
            return fk.getURL(col);
        }
        catch (QueryParseException qpe)
        {
            return null;
        }
    }

    void copyToXml(ColumnType xmlCol, boolean full);

    // UNDONE: Do we still need DomainProperty for this?
    boolean isRequiredForInsert(@Nullable DomainProperty dp);

    DisplayColumn getRenderer();

    String getSqlTypeName();

    List<FieldKey> getSortFieldKeys();

    default boolean isSortable()
    {
        return null != getParentTable() && getParentTable().getSqlDialect().isSortableDataType(getSqlTypeName());
    }

    @Override
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

    @Override
    boolean isLookup();

    boolean hasDbSequence();

    boolean isRootDbSequence();

    default Container getDbSequenceContainer(Container container)
    {
        return isRootDbSequence() ? ContainerManager.getRoot() : container;
    }

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

    @Nullable SimpleTranslator.RemapMissingBehavior getRemapMissingBehavior();


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

    static boolean checkIsMutable(ColumnInfo col)
    {
        assert col instanceof MutableColumnInfo && !((MutableColumnInfo)col).isLocked();
        return col instanceof MutableColumnInfo && !((MutableColumnInfo)col).isLocked();
    }

    static String toString(ColumnInfo col)
    {
        StringBuilder sb = new StringBuilder(64);

        sb.append("  ");
        sb.append(StringUtils.rightPad(col.getName(), 25));
        sb.append(" ");

        String typeName = col.getSqlTypeName();
        sb.append(typeName);

        //UNDONE: Not supporting fixed decimal
        if ("VARCHAR".equalsIgnoreCase(typeName) || "CHAR".equalsIgnoreCase(typeName))
        {
            sb.append("(");
            sb.append(col.getScale());
            sb.append(") ");
        }
        else
            sb.append(" ");

        //SQL Server specific
        if (col.isAutoIncrement())
            sb.append("IDENTITY ");

        sb.append(col.isNullable() ? "NULL" : "NOT NULL");

        if (null != col.getDefaultValue())
        {
            sb.append(" DEFAULT ");
            if ("CURRENT_TIMESTAMP".equals(col.getDefaultValue()))
                sb.append(col.getDefaultValue());
            else
            {
                sb.append("'");
                sb.append(col.getDefaultValue());
                sb.append("'");
            }
        }

        return sb.toString();
    }
}


