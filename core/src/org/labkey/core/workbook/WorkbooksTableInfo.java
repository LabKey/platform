/*
 * Copyright (c) 2010 LabKey Corporation
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
package org.labkey.core.workbook;

import org.labkey.api.query.*;
import org.labkey.api.data.*;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.view.NotFoundException;
import org.labkey.core.query.CoreQuerySchema;

import java.sql.SQLException;
import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: labkey
 * Date: Jan 6, 2010
 * Time: 2:18:59 PM
 */
public class WorkbooksTableInfo extends ContainerTable
{
    public WorkbooksTableInfo(CoreQuerySchema coreSchema)
    {
        super(coreSchema);

        setDescription("Contains one row for workbook in this folder or project");

        List<FieldKey> defCols = new ArrayList<FieldKey>();
        defCols.add(FieldKey.fromParts("ID"));
        defCols.add(FieldKey.fromParts("Title"));
        defCols.add(FieldKey.fromParts("CreatedBy"));
        defCols.add(FieldKey.fromParts("Created"));
        this.setDefaultVisibleColumns(defCols);

        //workbook true
        this.addCondition(new SQLFragment("Workbook=?", true));
    }

    @Override
    protected String getContainerFilterColumn()
    {
        return "Parent";
    }

    @Override
    public boolean hasPermission(User user, Class<? extends Permission> perm)
    {
        if (getUpdateService() != null)
            return DeletePermission.class.isAssignableFrom(perm) && _schema.getContainer().hasPermission(user, perm);
        return false;
    }

    @Override
    public QueryUpdateService getUpdateService()
    {
        return new WorkbookUpdateService(this);
    }

    private class WorkbookUpdateService extends AbstractQueryUpdateService
    {
        WorkbookUpdateService(TableInfo queryTable)
        {
            super(queryTable);
        }

        @Override
        protected Map<String, Object> getRow(User user, Container container, Map<String, Object> keys) throws InvalidKeyException, QueryUpdateServiceException, SQLException
        {
            String id = keys.get("ID") == null ? null : keys.get("ID").toString();
            if (id == null)
            {
                return null;
            }
            try
            {
                return Table.selectObject(getQueryTable(), Table.ALL_COLUMNS, new SimpleFilter("ID", new Integer(id)), null, Map.class);
            }
            catch (NumberFormatException e)
            {
                return null;
            }
        }

        @Override
        protected Map<String, Object> insertRow(User user, Container container, Map<String, Object> row) throws DuplicateKeyException, ValidationException, QueryUpdateServiceException, SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        protected Map<String, Object> updateRow(User user, Container container, Map<String, Object> row, Map<String, Object> oldRow) throws InvalidKeyException, ValidationException, QueryUpdateServiceException, SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        protected Map<String, Object> deleteRow(User user, Container container, Map<String, Object> oldRow) throws InvalidKeyException, QueryUpdateServiceException, SQLException
        {
            String idString = oldRow.get("ID") == null ? "" : oldRow.get("ID").toString();
            Container workbook = null;
            try
            {
                int id = Integer.parseInt(idString);
                workbook = ContainerManager.getForRowId(id);
            }
            catch (NumberFormatException e) {}

            if (null == workbook || !workbook.isWorkbook())
                throw new NotFoundException("Could not find a workbook with id '" + idString + "'");
            ContainerManager.delete(workbook, user);
            return oldRow;
        }
    }
}
