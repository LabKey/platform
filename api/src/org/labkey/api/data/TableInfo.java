package org.labkey.api.data;

import org.labkey.api.util.NamedObjectList;
import org.labkey.api.util.StringExpressionFactory;
import org.labkey.api.view.ActionURL;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryUpdateForm;
import org.labkey.api.security.User;
import org.apache.beehive.netui.pageflow.Forward;

import java.sql.SQLException;
import java.util.Set;
import java.util.Map;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: Matthew
 * Date: Apr 27, 2006
 * Time: 11:29:43 AM
 */
public interface TableInfo
{
    public static int TABLE_TYPE_NOT_IN_DB = 0;
    public static int TABLE_TYPE_TABLE = 1;
    public static int TABLE_TYPE_VIEW = 2;


    String getName();

    SQLFragment getFromSQL();

    SQLFragment getFromSQL(String alias);

    String getAliasName();

    DbSchema getSchema();

    /** getSchema().getSqlDialect() */
    SqlDialect getSqlDialect();

    String[] getPkColumnNames();

    ColumnInfo[] getPkColumns();

    ColumnInfo getVersionColumn();

    String getVersionColumnName();

    String getTitleColumn();

    int getTableType();

    NamedObjectList getSelectList();

    /** getSelectList().get(pk) */
    String getRowTitle(Object pk) throws SQLException;

    ColumnInfo getColumn(String colName);

    ColumnInfo getColumnFromPropertyURI(String propertyURI);

    ColumnInfo[] getColumns();

    ColumnInfo[] getUserEditableColumns();

    ColumnInfo[] getColumns(String colNames);

    ColumnInfo[] getColumns(String... colNameArray);

    /**
     * Returns a set of display columns to be used
     */
    DataColumn[] getDisplayColumns(String colNames);

    DataColumn[] getDisplayColumns(String[] colNames);

    Set<String> getColumnNameSet();

    String getSequence();

    /**
     * Return the details URL expression for a particular record.
     * The column map passed in maps from a name of a column in this table
     * to the actual ColumnInfo used to generate the SQL for the SELECT
     * statement.  (e.g. if this is the Protocol table, the column "LSID" might
     * actually be represented by the "ProtocolLSID" column from the ProtocolApplication table).
     */
    StringExpressionFactory.StringExpression getDetailsURL(Map<String, ColumnInfo> columns);

    boolean hasPermission(User user, int perm);

    ActionURL delete(User user, ActionURL srcURL, QueryUpdateForm form) throws Exception;

    Forward insert(User user, QueryUpdateForm form) throws Exception;

    Forward update(User user, QueryUpdateForm form) throws Exception;

    /**
     * Return the method of a given name.  Methods are accessible via the QueryModule's query
     * language.  Most tables do not have methods. 
     */
    MethodInfo getMethod(String name);

    List<FieldKey> getDefaultVisibleColumns();

    public boolean isPublic();

    public String getPublicName();

    public String getPublicSchemaName();
}
