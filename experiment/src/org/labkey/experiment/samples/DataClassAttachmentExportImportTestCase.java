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
package org.labkey.experiment.samples;

import org.apache.logging.log4j.Logger;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.labkey.api.admin.FolderArchiveDataTypes;
import org.labkey.api.admin.FolderExportContext;
import org.labkey.api.admin.FolderImportContext;
import org.labkey.api.admin.StaticLoggerGetter;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.attachments.InputStreamAttachmentFile;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpDataClass;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.query.ExpSchema;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.test.TestTimeout;
import org.labkey.api.util.TestContext;
import org.labkey.api.util.logging.LogHelper;
import org.labkey.api.writer.MemoryVirtualFile;
import org.labkey.experiment.api.ExpDataClassAttachmentParent;
import org.labkey.experiment.api.ExperimentServiceImpl;
import org.labkey.folder.xml.FolderDocument;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.util.Collections.emptyList;

// Export numbers duplicate attachment names in DB order but import resolves them in TSV (RowId) order
@TestTimeout(TestTimeout.DEFAULT * 5)
public class DataClassAttachmentExportImportTestCase extends Assert
{
    private static final Logger LOG = LogHelper.getLogger(DataClassAttachmentExportImportTestCase.class, "Data class attachment export/import test");

    private static final String SOURCE_PROJECT = "DataClassAttachmentExportImportTest Source";
    private static final String TARGET_PROJECT = "DataClassAttachmentExportImportTest Target";
    private static final String DATA_CLASS_NAME = "AttachmentRoundTrip";
    private static final String ATTACHMENT_FIELD = "File";
    private static final String NOTES_FIELD = "Notes";
    private static final String FILE_NAME = "image.png";

    private static User _user;
    private static Container _source;
    private static Container _target;
    private static ExpDataClass _dataClass;

    @BeforeClass
    public static void setup() throws Exception
    {
        _user = TestContext.get().getUser();

        deleteTestContainers();
        _source = ContainerManager.ensureContainer(SOURCE_PROJECT, _user);
        _target = ContainerManager.ensureContainer(TARGET_PROJECT, _user);

        List<GWTPropertyDescriptor> props = List.of(
            new GWTPropertyDescriptor(ATTACHMENT_FIELD, PropertyType.ATTACHMENT.getTypeUri()),
            new GWTPropertyDescriptor(NOTES_FIELD, PropertyType.STRING.getTypeUri())
        );
        _dataClass = ExperimentServiceImpl.get().createDataClass(_source, _user, DATA_CLASS_NAME, null, props, emptyList(), null, null);
    }

    @AfterClass
    public static void cleanup()
    {
        _dataClass = null;
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

    private static QueryUpdateService sourceUpdateService()
    {
        UserSchema schema = QueryService.get().getUserSchema(_user, _source, ExpSchema.SCHEMA_EXP_DATA);
        assertNotNull("expected the exp.data schema", schema);
        TableInfo table = schema.getTable(DATA_CLASS_NAME);
        assertNotNull("expected the data class table to resolve", table);
        QueryUpdateService qus = table.getUpdateService();
        assertNotNull("expected an update service", qus);
        return qus;
    }

    private static void insertRow(String name, String content) throws Exception
    {
        Map<String, Object> row = new CaseInsensitiveHashMap<>();
        row.put("Name", name);
        row.put(ATTACHMENT_FIELD, new InputStreamAttachmentFile(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)), FILE_NAME));

        BatchValidationException errors = new BatchValidationException();
        sourceUpdateService().insertRows(_user, _source, List.of(row), errors, null, null);
        if (errors.hasErrors())
            throw errors;
    }

    private static ExpData getData(ExpDataClass dataClass, String name)
    {
        ExpData data = ExperimentService.get().getExpData(dataClass, name);
        assertNotNull("expected data '" + name + "' in " + dataClass.getContainer().getPath(), data);
        return data;
    }

    private static String attachmentContent(ExpDataClass dataClass, Container c, String name) throws Exception
    {
        ExpData data = getData(dataClass, name);

        try (InputStream is = AttachmentService.get().getInputStream(new ExpDataClassAttachmentParent(c, Lsid.parse(data.getLSID())), FILE_NAME))
        {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    public void duplicateAttachmentNamesSurviveExportImport() throws Exception
    {
        insertRow("First", "first-content");
        insertRow("Second", "second-content");

        // Postgres writes the updated tuple after Second, so an unsorted select differs from the TSV's RowId order
        Map<String, Object> update = new CaseInsensitiveHashMap<>();
        update.put("RowId", getData(_dataClass, "First").getRowId());
        update.put(NOTES_FIELD, "updated");
        BatchValidationException errors = new BatchValidationException();
        sourceUpdateService().updateRows(_user, _source, List.of(update), null, errors, null, null);
        if (errors.hasErrors())
            throw errors;

        assertEquals("source First attachment", "first-content", attachmentContent(_dataClass, _source, "First"));
        assertEquals("source Second attachment", "second-content", attachmentContent(_dataClass, _source, "Second"));

        MemoryVirtualFile root = new MemoryVirtualFile();
        FolderExportContext exportCtx = new FolderExportContext(_user, _source, Set.of(FolderArchiveDataTypes.DATA_CLASS_DESIGNS, FolderArchiveDataTypes.DATA_CLASS_DATA), "new", new StaticLoggerGetter(LOG));
        DataClassFolderWriter writer = new DataClassFolderWriter.DataClassDataWriter();
        writer.initialize(exportCtx);
        writer.write(_source, exportCtx, root);

        FolderDocument folderDoc = FolderDocument.Factory.newInstance();
        folderDoc.addNewFolder();
        FolderImportContext importCtx = new FolderImportContext(_user, _target, folderDoc, null, new StaticLoggerGetter(LOG), root);
        DataClassFolderImporter.get().process(null, importCtx, root);

        ExpDataClass imported = ExperimentService.get().getDataClass(_target, DATA_CLASS_NAME);
        assertNotNull("expected the data class to be imported", imported);

        assertEquals("imported First attachment", "first-content", attachmentContent(imported, _target, "First"));
        assertEquals("imported Second attachment", "second-content", attachmentContent(imported, _target, "Second"));
    }
}
