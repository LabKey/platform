package org.labkey.query.controllers;

import org.labkey.api.action.ApiAction;
import org.labkey.api.action.ApiResponse;
import org.labkey.api.action.ApiQueryResponse;
import org.labkey.api.action.ApiSimpleResponse;
import org.labkey.api.query.*;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.security.RequiresPermissionClass;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.util.ResultSetUtil;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.apache.commons.lang.StringUtils;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.sql.SQLException;

/**
 * Created by IntelliJ IDEA.
 * User: dave
 * Date: Aug 4, 2009
 * Time: 5:17:26 PM
 */

@RequiresPermissionClass(ReadPermission.class)
public class ValidateQueryAction extends ApiAction<ValidateQueryAction.ValidateQueryForm>
{
    public ApiResponse execute(ValidateQueryForm form, BindException errors) throws Exception
    {
        if (null == StringUtils.trimToNull(form.getQueryName()) || null == StringUtils.trimToNull(form.getSchemaName()))
            throw new IllegalArgumentException("You must specify a schema and query name!");

        //get the schema
        UserSchema schema = (UserSchema) DefaultSchema.get(getViewContext().getUser(), getViewContext().getContainer()).getSchema(form.getSchemaName());
        if (null == schema)
            throw new IllegalArgumentException("Could not find the schema '" + form.getSchemaName() + "'!");

        TableInfo table = schema.getTable(form.getQueryName());
        if (null == table)
            throw new IllegalArgumentException("The query '" + form.getQueryName() + "' was not found in the schema '" + form.getSchemaName() + "'!");

        //get the set of columns
        List<ColumnInfo> cols = null;
        if (!form.isIncludeAllColumns())
        {
            List<FieldKey> defVisCols = table.getDefaultVisibleColumns();
            Map<FieldKey, ColumnInfo> colMap = QueryService.get().getColumns(table, defVisCols);
            cols = new ArrayList<ColumnInfo>(colMap.values());
        }

        //try to execute it with a rowcount of 0 (will throw SQLException to client if it fails
        Table.TableResultSet results = null;
        try
        {
            //use selectForDisplay to mimic the behavior one would get in the UI
            if (form.isIncludeAllColumns())
                results = Table.selectForDisplay(table, Table.ALL_COLUMNS, null, null, 0, 0);
            else
                results = Table.selectForDisplay(table, cols, null, null, 0, 0);
        }
        finally
        {
            ResultSetUtil.close(results);
        }

        //if we got here, the query is OK
        return new ApiSimpleResponse("valid", true);
    }

    public static class ValidateQueryForm
    {
        private String _schemaName;
        private String _queryName;
        private boolean _includeAllColumns = false;

        public String getSchemaName()
        {
            return _schemaName;
        }

        public void setSchemaName(String schemaName)
        {
            _schemaName = schemaName;
        }

        public String getQueryName()
        {
            return _queryName;
        }

        public void setQueryName(String queryName)
        {
            _queryName = queryName;
        }

        public boolean isIncludeAllColumns()
        {
            return _includeAllColumns;
        }

        public void setIncludeAllColumns(boolean includeAllColumns)
        {
            _includeAllColumns = includeAllColumns;
        }
    }

}
