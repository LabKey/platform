/*
 * Copyright (c) 2026 LabKey Corporation
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

import org.apache.logging.log4j.Logger;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.labkey.api.admin.FolderArchiveDataTypes;
import org.labkey.api.admin.FolderExportContext;
import org.labkey.api.admin.StaticLoggerGetter;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.attachments.InputStreamAttachmentFile;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.list.ListDefinition;
import org.labkey.api.exp.list.ListItem;
import org.labkey.api.exp.list.ListService;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.security.User;
import org.labkey.api.test.TestTimeout;
import org.labkey.api.util.TestContext;
import org.labkey.api.util.logging.LogHelper;
import org.labkey.api.writer.MemoryVirtualFile;
import org.labkey.list.view.ListItemAttachmentParent;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

// Merge import must consume a file-name slot for unchanged rows, or later rows read an earlier row's file
@TestTimeout(TestTimeout.DEFAULT * 5)
public class ListAttachmentMergeImportTestCase extends Assert
{
    private static final Logger LOG = LogHelper.getLogger(ListAttachmentMergeImportTestCase.class, "List attachment merge import test");

    private static final String SOURCE_PROJECT = "ListAttachmentMergeImportTest Source";
    private static final String TARGET_PROJECT = "ListAttachmentMergeImportTest Target";
    private static final String LIST_NAME = "AttachmentMerge";
    private static final String KEY_NAME = "Name";
    private static final String ATTACHMENT_FIELD = "File";
    private static final String FILE_NAME = "image.png";

    private static User _user;
    private static Container _source;
    private static Container _target;
    private static ListDefinition _list;

    @BeforeClass
    public static void setup() throws Exception
    {
        _user = TestContext.get().getUser();

        deleteTestContainers();
        _source = ContainerManager.ensureContainer(SOURCE_PROJECT, _user);
        _target = ContainerManager.ensureContainer(TARGET_PROJECT, _user);

        _list = ListService.get().createList(_source, LIST_NAME, ListDefinition.KeyType.Varchar);
        _list.setKeyName(KEY_NAME);

        Domain domain = _list.getDomain();
        assertNotNull("expected the list domain", domain);
        DomainProperty dp = domain.addProperty();
        dp.setName(ATTACHMENT_FIELD);
        dp.setType(PropertyService.get().getType(_source, PropertyType.ATTACHMENT.getXmlName()));
        dp.setPropertyURI(ListDomainKind.createPropertyURI(LIST_NAME, ATTACHMENT_FIELD, _source, _list.getKeyType()).toString());
        _list.save(_user);
    }

    @AfterClass
    public static void cleanup()
    {
        _list = null;
        _source = null;
        _target = null;
        _user = null;

        deleteTestContainers();
    }

    private static void deleteTestContainers()
    {
        for (String path : List.of(SOURCE_PROJECT, TARGET_PROJECT))
        {
            Container project = ContainerManager.getForPath(path);
            if (null != project)
                ContainerManager.deleteAll(project, TestContext.get().getUser());
        }
    }

    private static QueryUpdateService updateService(ListDefinition list)
    {
        TableInfo table = list.getTable(_user);
        assertNotNull("expected the list table to resolve", table);
        QueryUpdateService qus = table.getUpdateService();
        assertNotNull("expected an update service", qus);
        return qus;
    }

    private static void insertRow(String key, String content) throws Exception
    {
        Map<String, Object> row = new CaseInsensitiveHashMap<>();
        row.put(KEY_NAME, key);
        row.put(ATTACHMENT_FIELD, new InputStreamAttachmentFile(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)), FILE_NAME));

        BatchValidationException errors = new BatchValidationException();
        updateService(_list).insertRows(_user, _source, List.of(row), errors, null, null);
        if (errors.hasErrors())
            throw errors.getLastRowError();
    }

    private static String attachmentContent(ListDefinition list, Container c, String key) throws Exception
    {
        ListItem item = list.getListItem(key, _user, c);
        assertNotNull("expected list item '" + key + "' in " + c.getPath(), item);

        try (InputStream is = AttachmentService.get().getInputStream(new ListItemAttachmentParent(item.getEntityId(), c), FILE_NAME))
        {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static void importArchive(MemoryVirtualFile listsDir, boolean merge) throws Exception
    {
        List<String> errors = new LinkedList<>();
        new ListImporter(new ListImportContext(null, merge)).processMany(listsDir, _target, _user, errors, LOG);
        assertEquals("import errors", List.of(), errors);
    }

    @Test
    public void mergeIntoNonEmptyTargetKeepsAttachmentsAligned() throws Exception
    {
        insertRow("Alpha", "alpha-content");
        insertRow("Beta", "beta-content");

        MemoryVirtualFile listsDir = new MemoryVirtualFile();
        FolderExportContext ctx = new FolderExportContext(_user, _source, Set.of(FolderArchiveDataTypes.LIST_DATA), "new", new StaticLoggerGetter(LOG));
        assertTrue("expected the list to be exported", new ListWriter().write(_source, _user, listsDir, ctx));

        importArchive(listsDir, false);
        ListDefinition imported = ListService.get().getList(_target, LIST_NAME);
        assertNotNull("expected the list to be imported", imported);

        // Alpha stays unchanged in the merge while Beta is re-inserted from the archive
        updateService(imported).deleteRows(_user, _target, List.of(Map.of(KEY_NAME, "Beta")), null, null);
        assertNull("expected Beta to be deleted", imported.getListItem("Beta", _user, _target));

        importArchive(listsDir, true);

        assertEquals("merged Alpha attachment", "alpha-content", attachmentContent(imported, _target, "Alpha"));
        assertEquals("merged Beta attachment", "beta-content", attachmentContent(imported, _target, "Beta"));
    }
}
