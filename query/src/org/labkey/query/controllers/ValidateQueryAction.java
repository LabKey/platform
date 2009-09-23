/*
 * Copyright (c) 2009 LabKey Corporation
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
import org.labkey.query.persist.QueryManager;
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

        QueryManager.get().validateQuery(form.getSchemaName(), form.getQueryName(),
                getViewContext().getUser(), getViewContext().getContainer(), form.isIncludeAllColumns());

        //if we got here, the query is OK
        return new ApiSimpleResponse("valid", true);
    }

    public static class ValidateQueryForm
    {
        private String _schemaName;
        private String _queryName;
        private boolean _includeAllColumns = true;

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
