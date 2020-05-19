/*
 * Copyright (c) 2018-2019 LabKey Corporation
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

import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.labkey.api.admin.ImportContext;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.exp.list.ListDefinition;
import org.labkey.api.exp.list.ListService;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.Lookup;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.TestContext;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.hamcrest.CoreMatchers.containsString;
import static org.labkey.api.data.ContainerType.DataType.protocol;

public class WorkbookContainerType implements ContainerType
{
    public static final String NAME = "workbook";

    @Override
    public String getName()
    {
        return NAME;
    }

    @Override
    public boolean canHaveChildren()
    {
        return false;
    }

    @Override
    public boolean includeForImportExport(ImportContext context)
    {
        return false;
    }

    @Override
    public boolean shouldRemoveFromPortal()
    {
        return false;
    }

    @Override
    public boolean includePropertiesAsChild(boolean includeTabs)
    {
        return false;
    }

    @Override
    public boolean isInFolderNav()
    {
        return false;
    }

    @Override
    public boolean isConvertibleToTab()
    {
        return false;
    }

    @Override
    public boolean allowRowMutationFromContainer(Container primaryContainer, Container targetContainer)
    {
        //Issue 15301: allow workbooks records to be deleted/updated from the parent container
        return primaryContainer.equals(targetContainer) || targetContainer.getParent().equals(primaryContainer);
    }

    @Override
    public Class<? extends Permission> getPermissionNeededToDelete()
    {
        return DeletePermission.class;
    }

    @Override
    public Class<? extends Permission> getPermissionNeededToCreate()
    {
        return InsertPermission.class;
    }

    @Override
    public boolean isDuplicatedInContainerFilter()
    {
        return true;
    }

    @Override
    public String getContainerNoun(Container currentContainer)
    {
        return "workbook";
    }

    @Override
    public String getTitleFor(TitleContext context, Container currentContainer)
    {
        switch (context)
        {
            case appBar:
                return currentContainer.getTitle();
            case parentInNav:
                return currentContainer.getParent() != null ? currentContainer.getParent().getTitle() : currentContainer.getTitle();
            case childInNav:
                return currentContainer.getName();
            case importTarget:
                return currentContainer.getTitle();
            default:
                return currentContainer.getTitle();
        }
    }

    @Override
    public Container getContainerFor(DataType dataType, Container currentContainer)
    {
        switch (dataType)
        {
            //The intent is that outside of these blacklisted actions, return the current container (parent otherwise)
            case customQueryViews:
            case domainDefinitions:
            case dataspace:
            case fileAdmin:
            case navVisibility:
            case permissions:
            case properties:
            case protocol:
            case folderManagement:
            case fileRoot:
            case tabParent:
            case sharedSchemaOwner:
                return currentContainer.getParent();
            default:
                return currentContainer;
        }
    }

    @Override
    @NotNull
    public Set<Container> getContainersFor(DataType dataType, Container currentContainer)
    {
        Set<Container> containers = new LinkedHashSet<>();

        if (dataType == protocol)
        {
            containers.add(currentContainer);
            Container project = currentContainer.getProject();
            if (project != null)
            {
                containers.add(project);
            }

            containers.add(currentContainer.getParent());
            containers.add(ContainerManager.getSharedContainer());
        }

        return containers;
    }

    public static class AbstractTestCase extends Assert
    {
        protected Container _project;
        protected TestContext _context;
        protected List<Container> _workbooks = new ArrayList<>();

        protected String LIST1 = "List1";
        protected String LIST2 = "List2";

        protected void doInitialSetUp(String projectName) throws Exception
        {
            doCleanup(projectName);

            Container project = ContainerManager.getForPath(projectName);
            if (project == null)
            {
                project = ContainerManager.createContainer(ContainerManager.getRoot(), projectName);

                //create lists:
                ListDefinition ld1 = ListService.get().createList(project, LIST1, ListDefinition.KeyType.Varchar);
                ld1.getDomain().addProperty(new PropertyStorageSpec("PKField", JdbcType.VARCHAR));
                ld1.setKeyName("PKField");
                ld1.save(TestContext.get().getUser());

                ListDefinition ld2 = ListService.get().createList(project, LIST2, ListDefinition.KeyType.Varchar);
                ld2.getDomain().addProperty(new PropertyStorageSpec("PKField", JdbcType.VARCHAR));
                ld2.setKeyName("PKField");

                DomainProperty dp2 = ld2.getDomain().addProperty(new PropertyStorageSpec("LookupField", JdbcType.VARCHAR));
                dp2.setLookup(new Lookup(project, "Lists", LIST1));
                ld2.save(_context.getUser());
            }

            _project = project;

            //create two workbooks, auto-named by system
            _workbooks.add(ContainerManager.createContainer(project, null, "Title1", null, WorkbookContainerType.NAME, _context.getUser()));
            _workbooks.add(ContainerManager.createContainer(project, null, "Title1", null, WorkbookContainerType.NAME, _context.getUser()));
        }

        protected void testCrossContainerBehaviors(Container project, List<Container> workbooks, String schemaName, String parentTable, String childTable, String primaryChildField, String lookupField, List<String> parentPKs, Map<String, Object> extraRowValues) throws Exception
        {
            UserSchema usProject = QueryService.get().getUserSchema(_context.getUser(), project, schemaName);
            TableInfo tiChild = usProject.getTable(childTable);
            String parentField = tiChild.getColumn(lookupField).getFk().getLookupColumnName();

            assertTrue(parentPKs.size() >= 4);

            List<Map<String, Object>> toInsert = new ArrayList<>();
            toInsert.add(createParentTableRow(project, parentField, parentPKs.get(0)));
            toInsert.add(createParentTableRow(workbooks.get(0), parentField, parentPKs.get(1)));
            toInsert.add(createParentTableRow(workbooks.get(1), parentField, parentPKs.get(2)));
            toInsert.add(createParentTableRow(workbooks.get(1), parentField, parentPKs.get(3)));

            BatchValidationException errors0 = new BatchValidationException();
            usProject.getTable(parentTable).getUpdateService().insertRows(_context.getUser(), project, toInsert, errors0, Collections.emptyMap(), null);
            if (errors0.hasErrors())
            {
                throw errors0;
            }

            validateRowsByContainer(usProject, parentTable, toInsert, parentField);

            //rows in second table.  one insert into the parent should allow these rows to go into the children, following the container prop
            int i = 0;
            List<Map<String, Object>> toInsert2 = new ArrayList<>();

            for (String pk : parentPKs)
            {
                toInsert2.add(createChildRow(project, primaryChildField, "Row" + (i++), lookupField, pk, extraRowValues));
                toInsert2.add(createChildRow(workbooks.get(0), primaryChildField, "Row" + (i++), lookupField, pk, extraRowValues));
                toInsert2.add(createChildRow(workbooks.get(1), primaryChildField, "Row" + (i++), lookupField, pk, extraRowValues));
            }

            BatchValidationException errors = new BatchValidationException();
            usProject.getTable(childTable).getUpdateService().insertRows(_context.getUser(), project, toInsert2, errors, Collections.emptyMap(), null);
            if (errors.hasErrors())
            {
                throw errors;
            }

            //now insert directly into the workbook
            UserSchema usWorkbook0 = QueryService.get().getUserSchema(_context.getUser(), workbooks.get(0), schemaName);
            UserSchema usWorkbook1 = QueryService.get().getUserSchema(_context.getUser(), workbooks.get(1), schemaName);

            List<Map<String, Object>> toInsertWb = new ArrayList<>();
            toInsertWb.add(createChildRow(null, primaryChildField, "Row" + (i++), lookupField, parentPKs.get(0), extraRowValues));
            toInsertWb.add(createChildRow(null, primaryChildField, "Row" + (i++), lookupField, parentPKs.get(1), extraRowValues));

            BatchValidationException errors2 = new BatchValidationException();
            usWorkbook0.getTable(childTable).getUpdateService().insertRows(_context.getUser(), usWorkbook0.getContainer(), toInsertWb, errors2, Collections.emptyMap(), null);
            if (errors2.hasErrors())
            {
                throw errors2;
            }

            toInsertWb.forEach(x -> x.put("container", workbooks.get(0).getId()));  //store this for use downstream

            List<Map<String, Object>> allChildRows = new ArrayList<>();
            allChildRows.addAll(toInsert2);
            allChildRows.addAll(toInsertWb);
            validateRowsByContainer(usProject, childTable, allChildRows, primaryChildField);

            List<Map<String, Object>> wbRows = new ArrayList<>(allChildRows);
            wbRows.removeIf(x -> !workbooks.get(0).getId().equals(x.get("container")));
            validateRowsByContainer(usWorkbook0, childTable, wbRows, primaryChildField);

            validateLookups(usProject, childTable, FieldKey.fromString(lookupField + "/" + parentField));
            validateLookups(usWorkbook0, childTable, FieldKey.fromString(lookupField + "/" + parentField));
            validateLookups(usWorkbook1, childTable, FieldKey.fromString(lookupField + "/" + parentField));

            //make sure we cant double-insert duplicate PKs across containers
            //this PK was already used in WB1
            if (tiChild.getPkColumnNames().contains(primaryChildField))
            {
                List<Map<String, Object>> duplicateKeyRows = new ArrayList<>();
                duplicateKeyRows.add(createChildRow(workbooks.get(0), primaryChildField, "Row" + (i - 1), lookupField, null, extraRowValues));
                BatchValidationException errors3 = new BatchValidationException();
                usWorkbook0.getTable(childTable).getUpdateService().insertRows(_context.getUser(), usWorkbook0.getContainer(), duplicateKeyRows, errors3, Collections.emptyMap(), null);
                if (!errors3.hasErrors())
                {
                    throw new Exception("This indicates the duplicate key insert was allowed");
                }

                assertThat("Unexpected error message.  Message was: " + errors3.getRowErrors().get(0).getMessage(), errors3.getRowErrors().get(0).getMessage(), containsString("duplicate key"));
            }
        }

        private static void validateLookups(UserSchema us, String tableName, FieldKey lookupPath)
        {
            assertEquals("One or more rows had an invalid lookup", 0, new TableSelector(us.getTable(tableName), new SimpleFilter(lookupPath, null, CompareType.ISBLANK), null).getRowCount());
        }

        private static void validateRowsByContainer(UserSchema us, String tableName, List<Map<String, Object>> allRows, String pkFieldName)
        {
            //first verify rows are in correct container:
            Map<String, Set<String>> expectedByContainer1 = new HashMap<>();
            allRows.forEach(row -> {
                String containerId = row.get("container") == null ? us.getContainer().getId() : row.get("container").toString();
                Set<String> vals = expectedByContainer1.getOrDefault(containerId, new HashSet<>());
                vals.add(row.get(pkFieldName).toString());
                expectedByContainer1.put(containerId, vals);
            });
            TableSelector ts1 = new TableSelector(us.getTable(tableName), PageFlowUtil.set("container", pkFieldName));
            ts1.forEachResults(rs -> {
                String container = rs.getString(FieldKey.fromString("container"));
                if (container == null)
                {
                    container = us.getContainer().getId();
                }

                String pk = rs.getString(FieldKey.fromString(pkFieldName));

                assertTrue("Row not in correct container", expectedByContainer1.get(container).contains(pk));
            });

            assertEquals("Incorrect number of rows", allRows.size(), ts1.getRowCount());
        }

        private static Map<String, Object> createParentTableRow(Container c, String fieldName, String pkVal)
        {
            Map<String, Object> row = new CaseInsensitiveHashMap<>();
            row.put(fieldName, pkVal);
            row.put("container", c.getId());

            return row;
        }

        private static Map<String, Object> createChildRow(Container c, String fieldName, String fieldVal, String lookupField, String lookupVal, Map<String, Object> extraRowValues)
        {
            Map<String, Object> row = new CaseInsensitiveHashMap<>();
            row.put(fieldName, fieldVal);
            row.put(lookupField, lookupVal);

            if (c != null)
                row.put("container", c.getId());

            if (extraRowValues != null)
            {
                row.putAll(extraRowValues);
            }

            return row;
        }

        protected void doCleanup(String projectName)
        {
            Container project = ContainerManager.getForPath(projectName);
            if (project != null)
            {
                ContainerManager.deleteAll(project, TestContext.get().getUser());
            }
        }
    }

    public static class TestCase extends AbstractTestCase
    {
        private static final String PROJECT_NAME = "WorkbookIntegrationTest";

        @Before
        public void setUp() throws Exception
        {
            _context = TestContext.get();
            doInitialSetUp(PROJECT_NAME);
        }

        @Test
        public void testCrossContainerBehaviorsForList() throws Exception
        {
            testCrossContainerBehaviors(_project, _workbooks, "Lists", LIST1, LIST2, "PKField", "LookupField", Arrays.asList("Value1", "Value2", "Value3", "Value4"), null);
        }

        @After
        public void onComplete()
        {
            doCleanup(PROJECT_NAME);
        }
    }
}