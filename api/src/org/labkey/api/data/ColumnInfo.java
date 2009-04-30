/*
 * Copyright (c) 2004-2009 Fred Hutchinson Cancer Research Center
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

import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.labkey.api.gwt.client.DefaultValueType;
import org.labkey.api.query.FieldKey;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.StringExpressionFactory;
import org.labkey.api.util.StringExpressionFactory.StringExpression;
import org.labkey.data.xml.ColumnType;

import java.beans.Introspector;
import java.io.File;
import java.net.URLEncoder;
import java.sql.*;
import java.util.*;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ColumnInfo extends ColumnRenderProperties implements SqlColumn
{
    private static final DisplayColumnFactory DEFAULT_FACTORY = new DisplayColumnFactory()
    {
        public DisplayColumn createRenderer(ColumnInfo colInfo)
        {
            return new DataColumn(colInfo);
        }
    };

    private String name;
    private String alias;
    private String caption;
    private String sqlTypeName;
    private String textAlign = null;
    private String cssClass;
    private String cssStyle;
    private ForeignKey fk = null;
    private StringExpression url = null;
    private String defaultValue = null;
    private String autoFillValue = null;
    private int scale = 0;
    private int precision = 0;
    private int colIndex = 0; //index of column in table. 1 based.
    private boolean nullable = false;
    private boolean isAutoIncrement = false;
    private boolean isKeyField = false;
    private boolean isReadOnly = false;
    private boolean isUserEditable = true;
    private boolean isUnselectable = false;
    private boolean isHidden = false;
    private TableInfo parentTable = null;
    static Set<String> nonEditableColNames = null;
    private DisplayColumnFactory _displayColumnFactory = DEFAULT_FACTORY;
    private int sqlTypeInt = Types.NULL;
    private String metaDataName = null;
    private String selectName = null;
    private String description = null;
    protected ColumnInfo displayField;
    private String propertyURI = null;
    private DefaultValueType _defaultValueType = null;

    // Only set if we have an associated qc column for this column
    private String qcColumnName = null;

    private static Logger _log = Logger.getLogger(ColumnInfo.class);

    public ColumnInfo(String name)
    {
        this.name = name;
    }

    public ColumnInfo(ResultSetMetaData rsmd, int col) throws SQLException
    {
        this.name = rsmd.getColumnName(col);
        this.setColIndex(col);
        this.setSqlTypeName(rsmd.getColumnTypeName(col));
        this.sqlTypeInt = rsmd.getColumnType(col);
    }

    public ColumnInfo(String name, TableInfo parentTable)
    {
        this.name = name;
        this.parentTable = parentTable;
    }

    public ColumnInfo(ColumnInfo from)
    {
        this(from.getName(), from.getParentTable());
    }

    public ColumnInfo(ColumnInfo from, TableInfo parent)
    {
        this(from.getName(), parent);
        copyAttributesFrom(from);
    }

    public String getName()
    {
        return name;
    }

    public FieldKey getFieldKey()
    {
        return FieldKey.fromString(name);
    }

    public String getAlias()
    {
        if (alias == null)
            return getName();
        return alias;
    }

    protected void setAlias(String alias)
    {
        this.alias = alias;
    }

    public void copyAttributesFrom(ColumnInfo col)
    {
        setAutoFillValue(col.getAutoFillValue());
        setAutoIncrement(col.isAutoIncrement());
        if (col.caption != null)
            setCaption(col.getCaption());
        setCssClass(col.getCssClass());
        setDefaultValue(col.getDefaultValue());
        setDescription(col.getDescription());
        if (col.isFormatStringSet())
            setFormatString(col.getFormatString());
        setInputLength(col.getInputLength());
        setInputRows(col.getInputRows());
        setInputType(col.getInputType());
        setNullable(col.getNullable());
        setDisplayColumnFactory(col.getDisplayColumnFactory());
        setScale(col.getScale());
        setSqlTypeName(col.getSqlTypeName());
        this.sqlTypeInt = col.sqlTypeInt;
        setTextAlign(col.getTextAlign());
        setUserEditable(col.isUserEditable());
        setWidth(col.getWidth());
        setFk(col.getFk());
        setPropertyURI(col.getPropertyURI());
        setIsUnselectable(col.isUnselectable());
        setDefaultValueType(col.getDefaultValueType());

        // We intentionally do not copy "isHidden", since it is usually not applicable.
        // We also do not copy URL since the column aliases do not get fixed up.
        // Instead, set the URL on the FK

        // Consider: it does not always make sense to preserve the "isKeyField" property.
        setKeyField(col.isKeyField());

        setQcColumnName(col.getQcColumnName());
    }


    public String getMetaDataName()
    {
        return metaDataName;      // Actual name returned by metadata; use to query meta data or to select columns enclosed in quotes
    }


    public String getSelectName()
    {
        if (selectName == null)
        {
            selectName = getSqlDialect().getColumnSelectName(getAlias());
        }
        return selectName;
    }

    public SQLFragment getValueSql()
    {
        return getValueSql(getParentTable().getName());
    }

    public SQLFragment getValueSql(String tableAliasName)
    {
        return new SQLFragment(tableAliasName + "." + getSelectName());
    }

    public String getPropertyURI()
    {
        if (null == propertyURI)
            propertyURI = "http://terms.fhcrc.org/dbschemas/" + getParentTable().getSchema().getName() + "#" + getTableAlias() + "." + getSelectName();
        return propertyURI;
    }

    protected void setPropertyURI(String propertyURI)
    {
        this.propertyURI = propertyURI;
    }

    public void declareJoins(String parentAlias, Map<String, SQLFragment> map)
    {
    }

    public String getTableAlias()
    {
        return parentTable.getName();
    }

    public SqlDialect getSqlDialect()
    {
        if (parentTable == null)
            return null;
        return parentTable.getSqlDialect();
    }

    public String getCaption()
    {
        if (null == caption)
            caption = captionFromName(name);
        return caption;
    }

    @Override
    public String getFormatString()
    {
        if (isDateTimeType())
            return getDateFormatString();
        else
            return formatString;
    }

    private String getDateFormatString()
    {
        if (null == formatString || "Date".equalsIgnoreCase(formatString))
            return DateUtil.getStandardDateFormatString();

        if ("DateTime".equalsIgnoreCase(formatString))
            return DateUtil.getStandardDateTimeFormatString();

        return formatString;
    }

    public boolean isFormatStringSet()
    {
        return (formatString != null);
    }

    public String getTextAlign()
    {
        if (textAlign != null)
            return textAlign;
        return isStringType() ? "left" : "right";
    }

    public void setTextAlign(String textAlign)
    {
        this.textAlign = textAlign;
    }

    public String getCssClass()
    {
        return cssClass;
    }

    public String getCssStyle()
    {
        return cssStyle;
    }

    public String getDefaultValue()
    {
        return defaultValue;
    }

    public String getAutoFillValue()
    {
        return autoFillValue;
    }

    public ColumnInfo getDisplayField()
    {
        if (displayField != null)
            return displayField;
        ForeignKey fk = getFk();
        if (fk == null)
            return null;
//        _displayField = fk.createLookupColumn(this, null);
//        return _displayField;
        return fk.createLookupColumn(this, null);
    }

    public ColumnInfo getSortField()
    {
        if (getParentTable() == null)
            return this;
        if (getParentTable().getSqlDialect().isSortableDataType(getSqlDataTypeName()))
            return this;
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
        // Consider: (nicksh) negative numbers also end up wrapping
        return "java.util.Date".equals(javaTypeFromSqlType(getSqlTypeInt(), false));
    }

    public void setDisplayField(ColumnInfo field)
    {
        displayField = field;
    }

    public boolean getNullable()
    {
        return nullable;
    }

    public int getScale()
    {
        return scale;
    }

    public void setWidth(String width)
    {
        this.displayWidth = width;
    }

    public String getWidth()
    {
        if (null != displayWidth)
            return displayWidth;
        if (fk != null)
        {
            ColumnInfo fkTitleColumn = getDisplayField();
            if (null != fkTitleColumn && fkTitleColumn != this)
                return displayWidth = fkTitleColumn.getWidth();
        }

        if (isStringType())
            return displayWidth = String.valueOf(Math.min(getScale() * 6, 200));
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
        this.isUserEditable = editable;
    }


    public void setDisplayColumnFactory(DisplayColumnFactory factory)
    {
        _displayColumnFactory = factory;
    }

    public DisplayColumnFactory getDisplayColumnFactory()
    {
        return _displayColumnFactory;
    }

    public String getLegalName()
    {
        return legalNameFromName(name);
    }
    
    public String getPropertyName()
    {
        return propNameFromName(name);
    }

    /**
     * Version column can be used for optimistic concurrency.
     * for now we assume that this column is never updated
     * explicitly.
     */
    public boolean isVersionColumn()
    {
        return "_ts".equals(name) || "Modified".equals(name);
    }


    public String getInputType()
    {
        if (null == inputType)
        {
            TableInfo lookupTable = null;
            if (null != fk)
            {
                lookupTable = fk.getLookupTableInfo();
            }
            if (lookupTable == null)
            {
                if (isStringType() && scale > 255)
                    inputType = "textarea";
                else if ("image".equalsIgnoreCase(getSqlDataTypeName()))
                    inputType = "file";
                else if (getSqlTypeInt() == Types.BIT || getSqlTypeInt() == Types.BOOLEAN)
                    inputType = "checkbox";
                else
                    inputType = "text";
            }
            else
                inputType = "select";
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
        if (-1 == inputRows)
            return 15;

        return inputRows;
    }


    public String getSqlDataTypeName()
    {
        return sqlTypeName;
    }

    public int getSqlTypeInt()
    {
        if (sqlTypeInt == Types.NULL)
        {
            SqlDialect d;
            if (getParentTable() == null)
                d = CoreSchema.getInstance().getSqlDialect();
            else
                d = getParentTable().getSqlDialect();
            sqlTypeInt = d.sqlTypeIntFromSqlTypeName(sqlTypeName);
        }
        return sqlTypeInt;
    }


    /* Don't return TYPEs just real java objects */
    public Class getJavaObjectClass()
    {
        return javaClassFromSqlType(getSqlTypeInt(), true);
    }

    public Class getJavaClass()
    {
        return javaClassFromSqlType(getSqlTypeInt(), isNullable());
    }

    public boolean isAutoIncrement()
    {
        return isAutoIncrement;
    }

    public void setAutoIncrement(boolean autoIncrement)
    {
        isAutoIncrement = autoIncrement;
    }

    public boolean isReadOnly()
    {
        return isReadOnly || isAutoIncrement || isVersionColumn();
    }

    public void setReadOnly(boolean readOnly)
    {
        isReadOnly = readOnly;
    }

    public StringExpressionFactory.StringExpression getURL()
    {
        if (this.url != null)
            return this.url;
        ForeignKey fk = getFk();
        if (fk == null)
            return null;

        return fk.getURL(this);
    }

    public void setURL(String url)
    {
        this.url = StringExpressionFactory.create(url, true);
    }

    public void setURL(StringExpression expr)
    {
        this.url = expr;
    }

    public void copyToXml(ColumnType xmlCol, boolean full)
    {
        xmlCol.setColumnName(name);
        if (full)
        {
            if (fk instanceof SchemaForeignKey)
            {
                SchemaForeignKey sfk = (SchemaForeignKey) fk;
                org.labkey.data.xml.ColumnType.Fk xmlFk = xmlCol.addNewFk();
                xmlFk.setFkColumnName(sfk._lookupKey);
                xmlFk.setFkTable(sfk._tableName);
                DbSchema fkDbOwnerSchema = DbSchema.getDbSchema(sfk._dbSchemaName, sfk._ownerName);

                if (null==fkDbOwnerSchema)
                {
                    xmlFk.setFkDbSchema("********** Error:  can't load schema " + sfk._dbSchemaName + "/" + sfk._ownerName);
                }
                else if (fkDbOwnerSchema != getParentTable().getSchema())
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
                xmlCol.setUrl(url.toString());

            if (isReadOnly)
                xmlCol.setIsReadOnly(isReadOnly);
            if (!isUserEditable)
                xmlCol.setIsUserEditable(isUserEditable);
            if (isHidden)
                xmlCol.setIsHidden(isHidden);
            if (isUnselectable)
                xmlCol.setIsUnselectable(isUnselectable);
            if (null != caption)
                xmlCol.setColumnTitle(caption);
            if (colIndex != 0)
                xmlCol.setColumnIndex(colIndex);
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
            if (null != formatString)
                xmlCol.setFormatString(formatString);
            if (null != textAlign)
                xmlCol.setTextAlign(textAlign);
            if (null != description)
                xmlCol.setDescription(description);
        }
    }


    public void loadFromXml(ColumnType xmlCol, boolean merge)
    {
        //Following things would exist from meta data...
        if (! merge)
        {
            sqlTypeName = xmlCol.getDatatype();
            colIndex = xmlCol.getColumnIndex();
        }
        if ((!merge || null == fk) && xmlCol.getFk() != null)
        {
            ColumnType.Fk xfk = xmlCol.getFk();
            fk = new SchemaForeignKey(this, xfk.getFkDbSchema(), null, xfk.getFkTable(), xfk.getFkColumnName(), false);
        }

        name = xmlCol.getColumnName();
        if (xmlCol.isSetColumnTitle())
            setCaption(xmlCol.getColumnTitle());
        if (xmlCol.isSetInputLength())
            inputLength = xmlCol.getInputLength();
        if (xmlCol.isSetInputRows())
            inputRows = xmlCol.getInputRows();
        if (xmlCol.isSetInputType())
            inputType = xmlCol.getInputType();
        if (xmlCol.isSetUrl())
            setURL(xmlCol.getUrl());
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
            formatString = xmlCol.getFormatString();
        if (xmlCol.isSetTsvFormatString())
            tsvFormatString = xmlCol.getTsvFormatString();
        if (xmlCol.isSetExcelFormatString())
            excelFormatString = xmlCol.getExcelFormatString();
        if (xmlCol.isSetTextAlign())
            textAlign = xmlCol.getTextAlign();
        if (xmlCol.isSetPropertyURI())
            propertyURI = xmlCol.getPropertyURI();
        if (xmlCol.isSetSortDescending())
            setSortDirection(xmlCol.getSortDescending() ? Sort.SortDirection.DESC : Sort.SortDirection.ASC);
        if (xmlCol.isSetDescription())
            description = xmlCol.getDescription();
        if (xmlCol.isSetIsHidden())
            isHidden = xmlCol.getIsHidden();
        if (xmlCol.isSetIsUnselectable())
            isUnselectable = xmlCol.getIsUnselectable();
        // UNDONE: errors sometimes???
        try
        {
            if (xmlCol.isSetDisplayWidth())
                setDisplayWidth(xmlCol.getDisplayWidth());
        }
        catch (Throwable x)
        {
            x.printStackTrace();
        }
        if (xmlCol.isSetNullable())
            nullable = xmlCol.getNullable();
    }

    public static String captionFromName(String name)
    {
        if (name == null)
            return null;

        if (name.length() == 0)
            return name;

        StringBuffer buf = new StringBuffer(name.length() + 10);
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

        StringBuffer buf = new StringBuffer(name.length());
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


    public static boolean booleanFromString(String str)
    {
        if (null == str || str.trim().length() == 0)
            return false;
        if (str.equals("0") || str.equalsIgnoreCase("false"))
            return false;
        if (str.equals("1") || str.equalsIgnoreCase("true"))
            return true;
        return (Boolean)ConvertUtils.convert(str, Boolean.class);
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


    private static Pattern pat = Pattern.compile("\\$\\{[^}]*}");

    public static String variableSubstitution(String src, Map map, boolean urlencode)
    {
        StringBuffer sb = new StringBuffer();
        Matcher matcher = pat.matcher(src);
        while (matcher.find())
        {
            String varName = src.substring(matcher.start() + 2, matcher.end() - 1);

            //by default substitute "" for unmatched substitutions
            String substValue = "";
            Object o = map.get(varName);
            if (null != o)
                substValue = o.toString();

            if (urlencode)
                try
                {
                    substValue = URLEncoder.encode(substValue, "UTF-8");
                }
                catch (Exception e)
                {
                    _log.error("", e);
                }

            matcher.appendReplacement(sb, substValue);
        }
        matcher.appendTail(sb);

        return sb.toString();
    }


    public String toString()
    {
        StringBuffer sb = new StringBuffer(64);

        sb.append("  ");
        sb.append(StringUtils.rightPad(name, 25));
        sb.append(" ");
        sb.append(sqlTypeName);
        //UNDONE: Not supporting fixed decimal
        if ("VARCHAR".equalsIgnoreCase(sqlTypeName) || "CHAR".equalsIgnoreCase(sqlTypeName))
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

    public String getDescription()
    {
        return description;
    }

    public void setDescription(String description)
    {
        this.description = description;
    }

    static public class SchemaForeignKey implements ForeignKey
    {
        public ColumnInfo createLookupColumn(ColumnInfo foreignKey, String displayField)
        {
            TableInfo lookupTable = getLookupTableInfo();
            ColumnInfo lookupColumn = lookupTable.getColumn(displayField == null ? lookupTable.getTitleColumn() : displayField);
            if (lookupColumn == null)
            {
                return null;
            }

            if (_joinWithContainer)
            {
                return new LookupColumn(foreignKey, lookupTable.getColumn(this._lookupKey), lookupColumn, _joinWithContainer);
            }
            return LookupColumn.create(foreignKey, lookupTable.getColumn(this._lookupKey), lookupColumn, true);
        }

        public TableInfo getLookupTableInfo()
        {
            DbSchema schema = DbSchema.get(this._dbSchemaName);
            return schema.getTable(this._tableName);
        }

        public StringExpressionFactory.StringExpression getURL(ColumnInfo parent)
        {
            return null;
        }

        final String _dbSchemaName;
        final String _ownerName;
        final String _tableName;
        final String _lookupKey;
        final boolean _joinWithContainer;

        public SchemaForeignKey(ColumnInfo foreignKey, String dbSchemaName, String ownerName, String tableName, String lookupKey, boolean joinWithContaienr)
        {
            this._dbSchemaName = dbSchemaName == null ? foreignKey.getParentTable().getSchema().getName() : dbSchemaName;
            this._ownerName = ownerName == null ? foreignKey.getParentTable().getSchema().getOwner() : ownerName;
            this._tableName = tableName;
            this._lookupKey = lookupKey;
            this._joinWithContainer = joinWithContaienr;
        }

        public boolean isJoinWithContainer()
        {
            return _joinWithContainer;
        }
        
        public String getLookupTableName()
        {
            return _tableName;
        }

        public String getLookupColumnName()
        {
            return _lookupKey;
        }

        public String getLookupSchemaName()
        {
            return _dbSchemaName;
        }
    }

    public DisplayColumn getRenderer()
    {
        if (displayField == null || displayField == this)
        {
            return _displayColumnFactory.createRenderer(this);
        }
        else
        {
            return displayField.getRenderer();
        }
    }


    static
    {
        nonEditableColNames = new CaseInsensitiveHashSet();
        nonEditableColNames.add("created");
        nonEditableColNames.add("createdBy");
        nonEditableColNames.add("modified");
        nonEditableColNames.add("modifiedBy");
        nonEditableColNames.add("_ts");
        nonEditableColNames.add("entityId");
        nonEditableColNames.add("container");
    }

    public static ArrayList<ColumnInfo> createFromDatabaseMetaData(DatabaseMetaData dbmd, String catalogName, String schemaName, SchemaTableInfo parentTable)
            throws SQLException
    {
        //Use linked hash map to preserve ordering...
        LinkedHashMap<String, ColumnInfo> colList = new LinkedHashMap<String, ColumnInfo>();
        ResultSet rsCols = dbmd.getColumns(catalogName, schemaName, parentTable.getMetaDataName(), null);      // PostgreSQL change: query meta data with metaDataName
        SqlDialect.ColumnMetaDataReader reader = parentTable.getSqlDialect().getColumnMetaDataReader(rsCols);

        while (rsCols.next())
        {
            String metaDataName = reader.getName();
            ColumnInfo col = new ColumnInfo(metaDataName, parentTable);
            colList.put(col.name, col);

            col.metaDataName = metaDataName;
            col.sqlTypeName = reader.getSqlTypeName();
            col.sqlTypeInt = reader.getSqlType();
            col.isAutoIncrement = reader.isAutoIncrement();
            col.scale = reader.getScale();
            col.nullable = reader.isNullable();
            col.colIndex = reader.getPosition();

            if (nonEditableColNames.contains(col.getPropertyName()))
                col.setUserEditable(false);
        }
        rsCols.close();

        // load keys in two phases
        // 1) combine multi column keys
        // 2) update columns

        ResultSet rsKeys = dbmd.getImportedKeys(catalogName, schemaName, parentTable.getMetaDataName());
        int iPkTableSchema = findColumn(rsKeys, "PKTABLE_SCHEM");
        int iPkTableName = findColumn(rsKeys, "PKTABLE_NAME");
        int iPkColumnName = findColumn(rsKeys, "PKCOLUMN_NAME");
        int iFkColumnName = findColumn(rsKeys, "FKCOLUMN_NAME");
        int iKeySequence = findColumn(rsKeys, "KEY_SEQ");
        int iFkName = findColumn(rsKeys, "FK_NAME");
        
        ArrayList<ImportedKey> importedKeys = new ArrayList<ImportedKey>();
        while (rsKeys.next())
        {
            String pkOwnerName = rsKeys.getString(iPkTableSchema);
            String pkTableName = rsKeys.getString(iPkTableName);
            String pkColumnName = rsKeys.getString(iPkColumnName);
            String colName = rsKeys.getString(iFkColumnName);
            int keySequence = rsKeys.getInt(iKeySequence);
            String fkName = rsKeys.getString(iFkName);

            if (keySequence == 1)
            {
                ImportedKey key = new ImportedKey();
                key.fkName = fkName;
                key.pkOwnerName = pkOwnerName;
                key.pkTableName = pkTableName;
                key.pkColumnNames.add(pkColumnName);
                key.fkColumnNames.add(colName);
                importedKeys.add(key);
            }
            else
            {
                assert importedKeys.size() > 0;
                ImportedKey key = importedKeys.get(importedKeys.size()-1);
                assert key.fkName.equals(fkName);
                key.pkColumnNames.add(pkColumnName);
                key.fkColumnNames.add(colName);
            }
        }
        rsKeys.close();

        for (ImportedKey key : importedKeys)
        {
            int i=-1;
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
                ColumnInfo col = colList.get(colName);
                if (col.fk != null)
                {
                    _log.warn("More than one FK defined for column " + parentTable.getName() + col.getName() + ". Skipping constraint " + key.fkName);
                    continue;
                }

                col.fk = new SchemaForeignKey(col, key.pkOwnerName, null, key.pkTableName, key.pkColumnNames.get(i), joinWithContainer);
            }
            else
            {
                _log.warn("Skipping multiple column foreign key " + key.fkName + " ON " + parentTable.getName());
            }
        }

        return new ArrayList<ColumnInfo>(colList.values());
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


    static class ImportedKey
    {
        String fkName;
        String pkOwnerName;
        String pkTableName;
        ArrayList<String> pkColumnNames = new ArrayList<String>(2);
        ArrayList<String> fkColumnNames = new ArrayList<String>(2);
    }

    public boolean isDateTimeType()
    {
        int sqlType = getSqlTypeInt();
        return (sqlType == Types.DATE) ||
                (sqlType == Types.TIME) ||
                (sqlType == Types.TIMESTAMP);
    }

    public boolean isStringType()
    {
        int sqlType = getSqlTypeInt();
        return (sqlType == Types.CLOB) ||
                (sqlType == Types.CHAR) ||
                (sqlType == Types.VARCHAR) ||
                (sqlType == Types.LONGVARCHAR);
    }

    public boolean isLongTextType()
    {
        int sqlType = getSqlTypeInt();
        return (sqlType == Types.CLOB) ||
                (sqlType == Types.LONGVARCHAR);
    }

    public boolean isBooleanType()
    {
        int sqlType = getSqlTypeInt();
        return (sqlType == Types.BOOLEAN) ||
                (sqlType == Types.BIT);
    }

    public static String javaTypeFromSqlType(int sqlType, boolean isObj)
    {
        switch (sqlType)
        {
            case Types.DOUBLE:
                if (isObj)
                    return "Double";
                else
                    return "double";
            case Types.BIT:
            case Types.BOOLEAN:
                if (isObj)
                    return "Boolean";
                else
                    return "boolean";
            case Types.INTEGER:
                if (isObj)
                    return "Integer";
                else
                    return "int";
            case Types.TIMESTAMP:
            case Types.TIME:
            case Types.DATE:
                return "java.util.Date";
            case Types.VARCHAR:
            case Types.CHAR:
            case Types.LONGVARCHAR:
                return "String";
            default:
                return "String";
        }
    }

    public static Class javaClassFromSqlType(int sqlType, boolean isObj)
    {
        switch (sqlType)
        {
            case Types.DOUBLE:
            case Types.NUMERIC:
            case Types.DECIMAL:
                if (isObj)
                    return Double.class;
                else
                    return Double.TYPE;
            case Types.FLOAT:
            case Types.REAL:
                if (isObj)
                    return Float.class;
                else
                    return Float.TYPE;
            case Types.BIT:
            case Types.BOOLEAN:
                if (isObj)
                    return Boolean.class;
                else
                    return Boolean.TYPE;
            case Types.INTEGER:
            case Types.SMALLINT:
            case Types.TINYINT:
                if (isObj)
                    return Integer.class;
                else
                    return Integer.TYPE;
            case Types.BIGINT:
                if (isObj)
                    return Long.class;
                else
                    return Long.TYPE;
            case Types.TIMESTAMP:
            case Types.TIME:
            case Types.DATE:
                return java.util.Date.class;
            case Types.VARCHAR:
            case Types.CHAR:
            case Types.LONGVARCHAR:
                return String.class;
            default:
                return String.class;
        }
    }


    public void setCaption(String caption)
    {
        this.caption = caption;
    }


    public void setName(String name)
    {
        this.name = name;
    }


    public String getSqlTypeName()
    {
        return sqlTypeName;
    }


    public void setSqlTypeName(String sqlTypeName)
    {
        this.sqlTypeName = sqlTypeName;
        this.sqlTypeInt = Types.NULL;
    }

    public String getFriendlyTypeName()
    {
        return getFriendlyTypeName(getJavaClass());
    }

    public static String getFriendlyTypeName(Class javaClass)
    {
        if (javaClass.equals(String.class))
            return "Text (String)";
        else if (javaClass.equals(Integer.class) || javaClass.equals(Integer.TYPE))
            return "Integer";
        else if (javaClass.equals(Double.class) || javaClass.equals(Double.TYPE))
            return "Number (Double)";
        else if (javaClass.equals(Float.class) || javaClass.equals(Float.TYPE))
            return "Number (Float)";
        else if (javaClass.equals(Boolean.class) || javaClass.equals(Boolean.TYPE))
            return "True/False (Boolean)";
        else if (javaClass.equals(Long.class) || javaClass.equals(Long.TYPE))
            return "Long Integer";
        else if (javaClass.equals(File.class))
            return "File";
        else if (Date.class.isAssignableFrom(javaClass))
            return "Date and Time";
        else
            return "Other";
    }


    public void setCssClass(String cssClass)
    {
        this.cssClass = cssClass;
    }


    public void setCssStyle(String cssStyle)
    {
        this.cssStyle = cssStyle;
    }


    public ForeignKey getFk()
    {
        return fk;
    }


    public void setFk(ForeignKey fk)
    {
        this.fk = fk;
    }


    public void setDefaultValue(String defaultValue)
    {
        this.defaultValue = defaultValue;
    }


    public void setAutoFillValue(String autoFillValue)
    {
        this.autoFillValue = autoFillValue;
    }


    public void setScale(int scale)
    {
        this.scale = scale;
    }


    public int getPrecision()
    {
        return precision;
    }


    public void setPrecision(int precision)
    {
        this.precision = precision;
    }


    public int getColIndex()
    {
        return colIndex;
    }


    public void setColIndex(int colIndex)
    {
        this.colIndex = colIndex;
    }


    public boolean isNullable()
    {
        return nullable;
    }


    public void setNullable(boolean nullable)
    {
        this.nullable = nullable;
    }


    public boolean isKeyField()
    {
        return isKeyField;
    }


    public void setKeyField(boolean keyField)
    {
        isKeyField = keyField;
    }

    public boolean isHidden()
    {
        return isHidden;
    }

    public void setIsHidden(boolean hidden)
    {
        isHidden = hidden;
    }

    public boolean isQcEnabled()
    {
        return qcColumnName != null;
    }

    public String getQcColumnName()
    {
        return qcColumnName;
    }

    public void setQcColumnName(String qcColumnName)
    {
        this.qcColumnName = qcColumnName;
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
        isUnselectable = b;
    }


    public TableInfo getParentTable()
    {
        return parentTable;
    }


    public void setParentTable(TableInfo parentTable)
    {
        this.parentTable = parentTable;
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
        Map map = context.getRow();
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
        _defaultValueType = defaultValueType;
    }
}
