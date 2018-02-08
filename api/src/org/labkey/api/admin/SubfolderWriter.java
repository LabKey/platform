/*
 * Copyright (c) 2012-2017 LabKey Corporation
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
package org.labkey.api.admin;


import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.writer.VirtualFile;
import org.labkey.folder.xml.FolderDocument;
import org.labkey.folder.xml.SubfolderType;
import org.labkey.folder.xml.SubfoldersDocument;
import org.labkey.folder.xml.SubfoldersType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * FolderWriter that serializes out child containers.
 * User: cnathe
 * Date: 10/10/12
 */
public class SubfolderWriter extends BaseFolderWriter
{
    private static final String DIRECTORY_NAME = "subfolders";
    public static final String SUBFOLDERS_FILENAME = "subfolders.xml";

    @Override
    public void write(Container container, ImportContext<FolderDocument.Folder> ctx, VirtualFile vf) throws Exception
    {
        // start with just those child containers that the user has permissions to export.
        List<Container> allChildren = ContainerManager.getChildren(container, ctx.getUser(), FolderExportPermission.class, true);
        List<Container> childrenToExport = new ArrayList<>();
        getChildrenToExport(ctx, allChildren, childrenToExport);

        if (childrenToExport.size() > 0)
        {
            // Set up the pointer in the folder.xml file and the new dir
            ctx.getXml().addNewSubfolders().setDir(DIRECTORY_NAME);
            VirtualFile subfoldersDir = vf.getDir(DIRECTORY_NAME);

            // create the subfolders.xml so that the import will know what folders to expect
            SubfoldersDocument subfoldersDoc = SubfoldersDocument.Factory.newInstance();
            SubfoldersType subfoldersXml = subfoldersDoc.addNewSubfolders();

            // call the folder writer for each of the children with the new root
            for (Container child : childrenToExport)
            {
                // add the subfolder to the xml and create the dir in the subfolders dir for this child container
                SubfolderType childXml = subfoldersXml.addNewSubfolder();
                childXml.setName(child.getName());
                VirtualFile childDir = subfoldersDir.getDir(child.getName());

                // need to create a new FolderExportContext for each child (different roots, folder.xml, etc.)
                FolderExportContext childCtx = new FolderExportContext(ctx.getUser(), child, ctx.getDataTypes(), ctx.getFormat(),
                        ctx.isIncludeSubfolders(), ctx.getPhiLevel(), ctx.isShiftDates(), ctx.isAlternateIds(), ctx.isMaskClinic(), ctx.getLoggerGetter());

                FolderWriterImpl childFolderWriter = new FolderWriterImpl();
                childFolderWriter.write(child, childCtx, childDir);
            }

            subfoldersDir.saveXmlBean(SUBFOLDERS_FILENAME, subfoldersDoc);
        }
    }

    public static void getChildrenToExport(ImportContext context, List<Container> potentialChildren, List<Container> childrenToExport)
    {
        for (Container child : potentialChildren)
        {
            // only include subfolders if requested by user (otherwise just container tabs)
            // but don't include the current folder in the case of creating a folder from template
            if (child.isContainerTab() || (context.isIncludeSubfolders() && !child.isWorkbook()))
                childrenToExport.add(child);
        }
    }

    public static class TestCase extends Assert
    {
        @Test
        public void testGetChildrenToExport()
        {
            // fake root with children of different types
            Container fakeRoot = ContainerManager.createFakeContainer("fakeRoot", null);
            Container c1 = ContainerManager.createFakeContainer("subfolder", fakeRoot);
            Container c2 = ContainerManager.createFakeContainer("nestedsubfolder", c1);
            Container c3 = ContainerManager.createFakeContainer("containertab", fakeRoot);
            c3.setType(Container.TYPE.tab);
            Container c4 = ContainerManager.createFakeContainer("workbook", fakeRoot);
            c4.setType(Container.TYPE.workbook);

            List<Container> childList = Arrays.asList(c1, c2, c3, c4);
            FolderExportContext fec = new FolderExportContext(null, fakeRoot, null, null, null);

            // test including all subfolders (except workbooks)
            fec.setIncludeSubfolders(true);
            List<Container> allSubfolders = new ArrayList<>();
            getChildrenToExport(fec, childList, allSubfolders);
            assertEquals(childList.size() - 1, allSubfolders.size());

            // test not including subfolders (just container tabs by default)
            fec.setIncludeSubfolders(false);
            List<Container> noSubfolders = new ArrayList<>();
            getChildrenToExport(fec, childList, noSubfolders);
            assertEquals("Expected one container tab subfolder", 1, noSubfolders.size());
            assertEquals("containertab", noSubfolders.get(0).getName());
        }
    }
}
