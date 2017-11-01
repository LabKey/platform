/*
 * Copyright (c) 2010-2017 LabKey Corporation
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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.ContainerTable;
import org.labkey.api.data.ConvertHelper;
import org.labkey.api.data.DbSequenceManager;
import org.labkey.api.data.Filter;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.Parameter;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.data.UpdateableTableInfo;
import org.labkey.api.dataiterator.DataIterator;
import org.labkey.api.dataiterator.DataIteratorBuilder;
import org.labkey.api.dataiterator.DataIteratorContext;
import org.labkey.api.dataiterator.DataIteratorUtil;
import org.labkey.api.dataiterator.LoggingDataIterator;
import org.labkey.api.dataiterator.SimpleTranslator;
import org.labkey.api.dataiterator.TableInsertDataIterator;
import org.labkey.api.module.FolderType;
import org.labkey.api.module.FolderTypeManager;
import org.labkey.api.query.AbstractQueryUpdateService;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.DuplicateKeyException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.InvalidKeyException;
import org.labkey.api.query.QueryAction;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.QueryUpdateServiceException;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.util.ContainerContext;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NotFoundException;
import org.labkey.core.CoreController;
import org.labkey.core.query.CoreQuerySchema;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

/**
 * User: labkey
 * Date: Jan 6, 2010
 * Time: 2:18:59 PM
 */
public class WorkbooksTableInfo extends ContainerTable implements UpdateableTableInfo
{
    public WorkbooksTableInfo(CoreQuerySchema coreSchema)
    {
        super(coreSchema);

        setDescription("Contains one row for each workbook in this folder or project");
        setName("Workbooks");

        List<FieldKey> defCols = new ArrayList<>();
        defCols.add(FieldKey.fromParts("ID"));
        defCols.add(FieldKey.fromParts("Title"));
        defCols.add(FieldKey.fromParts("Description"));
        defCols.add(FieldKey.fromParts("CreatedBy"));
        defCols.add(FieldKey.fromParts("Created"));
        this.setDefaultVisibleColumns(defCols);

        //workbook true
        this.addCondition(new SQLFragment("Type='workbook'"));

        setInsertURL(new DetailsURL(new ActionURL(CoreController.CreateWorkbookAction.class, coreSchema.getContainer())));

        // Use the generic query update url, but use the parent Container as the ContainerContext.
        // The details url still uses the workbook's container as the ContainerContext.
        DetailsURL updateURL = QueryService.get().urlDefault(coreSchema.getContainer(), QueryAction.updateQueryRow, this);
        updateURL.setContainerContext(new ContainerContext.FieldKeyContext(FieldKey.fromParts("Parent")));
        setUpdateURL(updateURL);
    }

    @Override
    protected String getContainerFilterColumn()
    {
        return "Parent";
    }

    @Override
    public boolean hasPermission(@NotNull UserPrincipal user, @NotNull Class<? extends Permission> perm)
    {
        if (getUpdateService() != null)
            return (DeletePermission.class.isAssignableFrom(perm) || ReadPermission.class.isAssignableFrom(perm) || InsertPermission.class.isAssignableFrom(perm) || UpdatePermission.class.isAssignableFrom(perm)) && _userSchema.getContainer().hasPermission(user, perm);
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
            Filter filter;

            // Support ID, RowId, or EntityId, or Container to identify a row
            String id = keys.get("ID") == null ? null : keys.get("ID").toString();
            if (id == null)
                id = keys.get("RowId") == null ? null : keys.get("RowId").toString();

            if (id != null)
            {
                try
                {
                    filter = new SimpleFilter(FieldKey.fromParts("ID"), Integer.valueOf(id));
                }
                catch (NumberFormatException ex)
                {
                    return null;
                }
            }
            else if (keys.get("EntityId") != null)
            {
                String entityId = keys.get("EntityId").toString();
                filter = new SimpleFilter(FieldKey.fromParts("EntityId"), entityId);
            }
            else if (keys.get("Container") != null)
            {
                String entityId = keys.get("Container").toString();
                filter = new SimpleFilter(FieldKey.fromParts("EntityId"), entityId);
            }
            else
            {
                return null;
            }

            try
            {
                return new TableSelector(getQueryTable(), filter, null).getMap();
            }
            catch (NumberFormatException e)
            {
                return null;
            }
        }

        @Override
        protected boolean hasPermission(User user, Class<? extends Permission> acl)
        {
            return getQueryTable().hasPermission(user, acl);
        }

        @Override
        public List<Map<String, Object>> insertRows(User user, Container container, List<Map<String, Object>> rows, BatchValidationException errors, @Nullable Map<Enum, Object> configParameters, Map<String, Object> extraScriptContext) throws DuplicateKeyException, QueryUpdateServiceException, SQLException
        {
            return super._insertRowsUsingDIB(user, container, rows, getDataIteratorContext(errors, InsertOption.INSERT, configParameters), extraScriptContext);
        }

        @Override
        protected Map<String, Object> insertRow(User user, Container container, Map<String, Object> row) throws DuplicateKeyException, ValidationException, QueryUpdateServiceException, SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        protected Map<String, Object> updateRow(User user, Container container, Map<String, Object> row, @NotNull Map<String, Object> oldRow) throws InvalidKeyException, ValidationException, QueryUpdateServiceException, SQLException
        {
            String idString = oldRow.get("ID") == null ? "" : oldRow.get("ID").toString();
            Container workbook = null;
            try
            {
                int id = Integer.parseInt(idString);
                workbook = ContainerManager.getForRowId(id);
            }
            catch (NumberFormatException ignored) {}

            if (null == workbook || !workbook.isWorkbook())
                throw new NotFoundException("Could not find a workbook with id '" + idString + "'");

            // Only allow description, title, and searchable to be updated
            if (row.containsKey("Description"))
                ContainerManager.updateDescription(workbook, (String)row.get("Description"), user);
            if (row.containsKey("Title"))
                ContainerManager.updateTitle(workbook, (String) row.get("Title"), user);
            if (row.containsKey("Searchable") && row.get("Searchable") instanceof Boolean)
                ContainerManager.updateSearchable(workbook, (Boolean) row.get("Searchable"), user);
            return row;
        }

        @Override
        protected Map<String, Object> deleteRow(User user, Container container, Map<String, Object> oldRow)
        {
            String idString = oldRow.get("ID") == null ? "" : oldRow.get("ID").toString();
            Container workbook = null;
            try
            {
                int id = Integer.parseInt(idString);
                workbook = ContainerManager.getForRowId(id);
            }
            catch (NumberFormatException ignored) {}

            if (null == workbook || !workbook.isWorkbook())
                throw new NotFoundException("Could not find a workbook with id '" + idString + "'");
            ContainerManager.delete(workbook, user);
            return oldRow;
        }

        @Override
        public DataIteratorBuilder createImportDIB(User user, Container container, DataIteratorBuilder data, DataIteratorContext context)
        {
            // NOTE: we aren't using the StardardETL since it Path column is overriding the Name column
            DataIteratorBuilder etl = LessThanStandardDIB.forInsert(getQueryTable(), data, container, user, context);
            return ((UpdateableTableInfo)getQueryTable()).persistRows(etl, context);
        }
    }


    @Override
    public boolean insertSupported()
    {
        return true;
    }

    @Override
    public boolean updateSupported()
    {
        return true;
    }

    @Override
    public boolean deleteSupported()
    {
        return true;
    }

    @Override
    public TableInfo getSchemaTableInfo()
    {
        return getRealTable();
    }

    @Override
    public ObjectUriType getObjectUriType()
    {
        return null;
    }

    @Nullable
    @Override
    public String getObjectURIColumnName()
    {
        return null;
    }

    @Nullable
    @Override
    public String getObjectIdColumnName()
    {
        return null;
    }

    @Nullable
    @Override
    public CaseInsensitiveHashMap<String> remapSchemaColumns()
    {
        CaseInsensitiveHashMap<String> remap = new CaseInsensitiveHashMap<>();
        remap.put("RowId", "ID");
        return remap;
    }

    @Nullable
    @Override
    public CaseInsensitiveHashSet skipProperties()
    {
        return null;
    }

    @Override
    public DataIteratorBuilder persistRows(DataIteratorBuilder data, DataIteratorContext context)
    {
        return new WorkbookDataIteratorBuilder(data, context);
    }

    @Override
    public Parameter.ParameterMap insertStatement(Connection conn, User user) throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Parameter.ParameterMap updateStatement(Connection conn, User user, Set<String> columns) throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Parameter.ParameterMap deleteStatement(Connection conn) throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    // Just wraps built-in columns
    private static class LessThanStandardDIB implements DataIteratorBuilder
    {
        private final TableInfo _target;
        private final DataIteratorBuilder _inputBuilder;
        private final Container _c;
        private final User _user;
        private final DataIteratorContext _context;

        public LessThanStandardDIB(TableInfo target, DataIteratorBuilder data, Container container, User user, DataIteratorContext context)
        {
            _target = target;
            _inputBuilder = data;
            _c = container;
            _user = user;
            _context = context;
        }

        @Override
        public DataIterator getDataIterator(DataIteratorContext context)
        {
            DataIterator input = _inputBuilder.getDataIterator(context);

            DataIterator in = SimpleTranslator.wrapBuiltInColumns(input, context, _c, _user, _target);
            return in;
        }

        public static DataIteratorBuilder forInsert(TableInfo queryTable, DataIteratorBuilder data, Container container, User user, DataIteratorContext context)
        {
            return new LessThanStandardDIB(queryTable, data, container, user, context);
        }
    }

    private class WorkbookDataIteratorBuilder implements DataIteratorBuilder
    {
        DataIteratorContext _context;
        final DataIteratorBuilder _in;

        WorkbookDataIteratorBuilder(@NotNull DataIteratorBuilder in, DataIteratorContext context)
        {
            _context = context;
            _in = in;
        }

        @Override
        public DataIterator getDataIterator(DataIteratorContext context)
        {
            _context = context;
            DataIterator input = _in.getDataIterator(context);
            if (null == input)
                return null;           // Can happen if context has errors

            DataIterator in = new WorkbookDataIterator(input, context);

            return LoggingDataIterator.wrap(in);
        }
    }

    private class WorkbookDataIterator extends SimpleTranslator
    {
        private final Integer _parentInputCol;
        private final Integer _nameInputCol;
        private final Integer _titleInputCol;
        private final Integer _descriptionInputCol;
        private final Integer _folderTypeInputCol;

        private Container _currentContainer = null;

        private WorkbookDataIterator(DataIterator data, DataIteratorContext context)
        {
            super(data, context);

            // input columns required for creating new workbook
            Map<String,Integer> inputColMap = DataIteratorUtil.createColumnAndPropertyMap(data);
            _parentInputCol = inputColMap.get("parent");
            _nameInputCol = inputColMap.get("name");
            _titleInputCol = inputColMap.get("title");
            _descriptionInputCol = inputColMap.get("description");
            _folderTypeInputCol = inputColMap.get("folderType");

            for (int i=1 ; i<=data.getColumnCount() ; i++)
            {
                ColumnInfo col = data.getColumnInfo(i);
                if (col.getName().equalsIgnoreCase("rowid")) continue;
                if (col.getName().equalsIgnoreCase("entityid")) continue;
                if (col.getName().equalsIgnoreCase("container")) continue;
                if (col.getName().equalsIgnoreCase("parent")) continue;
                addColumn(i);
            }

            // output columns
            addColumn(new ColumnInfo("RowId", JdbcType.INTEGER), (Callable<Integer>) () -> _currentContainer != null ? _currentContainer.getRowId() : null);

            int entityIdOutputCol = addColumn(new ColumnInfo("EntityId", JdbcType.VARCHAR), (Callable<String>) () -> _currentContainer != null ? _currentContainer.getEntityId().toString() : null);

            // Not sure if this is right, but return the newly inserted container as 'container' instead of the parent container.
            addAliasColumn("Container", entityIdOutputCol);

            addColumn(new ColumnInfo("Parent", JdbcType.VARCHAR), (Callable<String>) () ->
            {
                Container parentContainer = _currentContainer != null ? _currentContainer.getParent() : null;
                return parentContainer != null ? parentContainer.getEntityId().toString() : null;
            });

            // UNDONE: add all other container/workbook columns
        }

        @Override
        public boolean isScrollable()
        {
            // Inserts into container table on .next() so can't be scrolled.
            return false;
        }

        /**
         * Performs the actual insert into container table via ContainerManager and sets folder type.
         */
        @Override
        protected void processNextInput()
        {
            _currentContainer = null;

            Container parent = getInputParentContainer();
            if (parent != null)
            {
                String name = getInputString(_nameInputCol);
                String title = getInputString(_titleInputCol);
                String description = getInputString(_descriptionInputCol);
                // XXX: Where to get user?
                User user = getUserSchema().getUser();

                _currentContainer = ContainerManager.createContainer(parent, name, title, description, Container.TYPE.workbook, user);

                // folderType (optional, defaults to workbook)
                String folderTypeName = getInputString(_folderTypeInputCol);
                if (folderTypeName == null)
                    folderTypeName = WorkbookFolderType.NAME;

                FolderType folderType = FolderTypeManager.get().getFolderType(folderTypeName);
                if (folderType != null)
                    _currentContainer.setFolderType(folderType, user);
            }
        }

        // Get the parent container from the input row or from the schema's current container.
        Container getInputParentContainer()
        {
            Container parentContainer = getUserSchema().getContainer();

            if (_parentInputCol != null)
            {
                Object parentContainerVal = getInputColumnValue(_parentInputCol);
                if (parentContainerVal != null)
                    parentContainer = ConvertHelper.convert(parentContainerVal, Container.class);

                if (parentContainer == null)
                {
                    addFieldError("container", "Container was missing or not found");
                    return null;
                }

                // parent must be normal (not workbook or container tab)
                if (Container.TYPE.normal != parentContainer.getType())
                {
                    addFieldError("container", "Parent container must be a normal container!");
                    return null;
                }
            }

            return parentContainer;
        }

        String getInputString(Integer inputColumn)
        {
            if (inputColumn == null)
                return null;

            Object o = getInputColumnValue(inputColumn);
            return o == null ? null : String.valueOf(o);
        }
    }

    private class _WorkbookDataIteratorBuilder implements DataIteratorBuilder
    {
        DataIteratorContext _context;
        final DataIteratorBuilder _in;

        _WorkbookDataIteratorBuilder(@NotNull DataIteratorBuilder in, DataIteratorContext context)
        {
            _context = context;
            _in = in;
        }

        @Override
        public DataIterator getDataIterator(DataIteratorContext context)
        {
            _context = context;
            DataIterator input = _in.getDataIterator(context);
            if (null == input)
                return null;           // Can happen if context has errors

            final SimpleTranslator it = new SimpleTranslator(input, context);

            final Map<String, Integer> inputCols = new HashMap<>();
            final Map<String, Integer> outputCols = new HashMap<>();

            for (int c = 1; c <= input.getColumnCount(); c++)
            {
                ColumnInfo col = input.getColumnInfo(c);
                if ("container".equalsIgnoreCase(col.getName()))
                    inputCols.put("container", c);
//                if ("parent".equalsIgnoreCase(col.getName()))
//                    inputCols.put("parent", c);
                else if ("sortOrder".equalsIgnoreCase(col.getName()))
                    inputCols.put("sortOrder", c);
                else if ("name".equalsIgnoreCase(col.getName()))
                    inputCols.put("name", c);
            }

            if (!inputCols.containsKey("parent"))
                throw new IllegalArgumentException("parent container required");

            // parent container
            outputCols.put("parent", it.addColumn(new ColumnInfo("parent", JdbcType.VARCHAR), (Callable) () ->
            {
                int parentInputCol = inputCols.get("container");
                Object parentContainerVal = it.getInputColumnValue(parentInputCol);
                Container parentContainer = ConvertHelper.convert(parentContainerVal, Container.class);
                // XXX: how do we signal field errors?
                if (parentContainer == null)
                    throw new Exception("Container was missing or not found");

                // parent must be normal (not workbook or container tab)
                if (Container.TYPE.normal != parentContainer.getType())
                    throw new Exception("Parent container must be a normal container!");

                return parentContainer.getEntityId();
            }));

            // sort order (depends on parent container column)
            outputCols.put("sortOrder", it.addColumn(new ColumnInfo("sortOrder", JdbcType.INTEGER), (Callable) () ->
            {
                int parentOutputCol = outputCols.get("parent");
                String parentEntityId = it.get(parentOutputCol).toString();
                Container parentContainer = ContainerManager.getForId(parentEntityId);
                return DbSequenceManager.get(parentContainer, ContainerManager.WORKBOOK_DBSEQUENCE_NAME).next();
            }));

            // name column
            outputCols.put("name", it.addColumn(new ColumnInfo("name", JdbcType.VARCHAR), (Callable) () ->
            {
                int nameInputCol = inputCols.get("name");
                Object nameVal = it.getInputColumnValue(nameInputCol);
                String name;
                if (nameVal != null)
                {
                    name = ConvertHelper.convert(nameVal, String.class);
                }
                else
                {
                    Object sortOrderOutputCol = outputCols.get("sortOrder");
                    assert sortOrderOutputCol != null;
                    name = String.valueOf(sortOrderOutputCol);
                }

                StringBuilder error = new StringBuilder();
                if (!Container.isLegalName(name, false, error))
                    throw new Exception(error.toString());

                return name;
            }));

            // title column
            it.addColumn(new ColumnInfo("title", JdbcType.VARCHAR), (Callable) () ->
            {
                int titleInputCol = inputCols.get("title");
                Object titleVal = it.getInputColumnValue(titleInputCol);
                String title = ConvertHelper.convert(titleVal, String.class);
                StringBuilder error = new StringBuilder();
                if (!Container.isLegalName(title, false, error))
                    throw new Exception(error.toString());

                return title;
            });


            // description
            it.addColumn("description", inputCols.get("description"));


            // type
            it.addConstantColumn("type", JdbcType.VARCHAR, Container.TYPE.workbook);


            // UNDONE: folder type column

            DataIteratorBuilder dib = TableInsertDataIterator.create(it, getRealTable(), context);

            // UNDONE: all the security and caching stuff that ContainerManager.createContainer() does.

            return LoggingDataIterator.wrap(dib.getDataIterator(context));
        }
    }
}
