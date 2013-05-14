/*
 * Copyright (c) 2008-2013 LabKey Corporation
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
package org.labkey.list.model;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.etl.DataIteratorContext;
import org.labkey.api.exp.list.ListDefinition;
import org.labkey.api.query.AbstractQueryUpdateService;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.DefaultQueryUpdateService;
import org.labkey.api.query.DuplicateKeyException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.InvalidKeyException;
import org.labkey.api.query.QueryUpdateServiceException;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/*
* User: Dave
* Date: Jun 12, 2008
* Time: 1:51:50 PM
*/

/**
 * Implementation of QueryUpdateService for Lists
 */
public class ListQueryUpdateService extends DefaultQueryUpdateService
{
    ListDefinition _list = null;

    public ListQueryUpdateService(ListTable queryTable, TableInfo dbTable, ListDefinition list)
    {
        super(queryTable, dbTable);
        _list = list;
    }

    @Override
    protected DataIteratorContext getDataIteratorContext(BatchValidationException errors, InsertOption insertOption)
    {
        DataIteratorContext context = super.getDataIteratorContext(errors, insertOption);
        if (insertOption.batch)
        {
            context.setMaxRowErrors(100);
            context.setFailFast(false);
        }
        return context;
    }

    @Override
    protected Map<String, Object> getRow(User user, Container container, Map<String, Object> listRow) throws InvalidKeyException, QueryUpdateServiceException, SQLException
    {
        Map<String, Object> ret = null;

        if (null != listRow)
        {
            Object key = listRow.get(_list.getKeyName().toLowerCase());
            SimpleFilter filter = new SimpleFilter(FieldKey.fromParts(_list.getKeyName()), key);
            TableSelector selector = new TableSelector(getQueryTable(), filter, null);
            ret = selector.getObject(Map.class);
        }

        return ret;
    }

    @Override
    protected Map<String, Object> insertRow(User user, Container container, Map<String, Object> row) throws DuplicateKeyException, ValidationException, QueryUpdateServiceException, SQLException
    {
        throw new UnsupportedOperationException("Update Service Not Complete");
    }

    @Override
    public List<Map<String, Object>> insertRows(User user, Container container, List<Map<String, Object>> rows, BatchValidationException errors, Map<String, Object> extraScriptContext) throws DuplicateKeyException, QueryUpdateServiceException, SQLException
    {
        List<Map<String, Object>> result = super._insertRowsUsingETL(user, container, rows, getDataIteratorContext(errors, InsertOption.INSERT), extraScriptContext);
//        if (null != result && result.size() > 0 && !errors.hasErrors())
//            ListManager.get().indexList(_list);
        return result;
    }

    @Override
    protected Map<String, Object> updateRow(User user, Container container, Map<String, Object> row, @NotNull Map<String, Object> oldRow) throws InvalidKeyException, ValidationException, QueryUpdateServiceException, SQLException
    {
        throw new UnsupportedOperationException("Update Service Not Complete");
    }

    @Override
    protected Map<String, Object> deleteRow(User user, Container container, Map<String, Object> oldRowMap) throws InvalidKeyException, QueryUpdateServiceException, SQLException
    {
        // Fetch old item
        Map<String, Object> ret = getRow(user, container, oldRowMap);

        if (null != ret && ret.size() > 0)
        {
            try (DbScope.Transaction transaction = getDbTable().getSchema().getScope().ensureTransaction())
            {
                Table.delete(getDbTable(), ret.get(_list.getKeyName()));
                transaction.commit();
            }
        }

        return ret;
    }
}
