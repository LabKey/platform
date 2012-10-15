package org.labkey.api.admin;


import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.writer.VirtualFile;
import org.labkey.folder.xml.FolderDocument;
import org.labkey.folder.xml.SubfolderType;
import org.labkey.folder.xml.SubfoldersDocument;
import org.labkey.folder.xml.SubfoldersType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
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
        // start with just those child containers that the user has Admin permissions
        List<Container> allChildren = ContainerManager.getChildren(container, ctx.getUser(), AdminPermission.class, true);
        List<Container> childrenToExport = new ArrayList<Container>();
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
                        ctx.isIncludeSubfolders(), ctx.isRemoveProtected(), ctx.isShiftDates(), ctx.isAlternateIds(), ctx.getLogger());

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
            // only include subfolders if reguested by user (otherwise just workbooks and container tabs)
            // TODO: support container tabs
            if (child.isWorkbook() || context.isIncludeSubfolders())
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
            // c3.setContainerTab(true);// TODO: add test case for container tab type container
            Container c4 = ContainerManager.createFakeContainer("workbook", fakeRoot);
            c4.setWorkbook(true);

            List<Container> childList = Arrays.asList(c1, c2, c3, c4);
            FolderExportContext fec = new FolderExportContext(null, fakeRoot, null, null, null);

            // test including all subfolders
            fec.setIncludeSubfolders(true);
            List<Container> allSubfolders = new ArrayList<Container>();
            getChildrenToExport(fec, childList, allSubfolders);
            assertEquals(childList.size(), allSubfolders.size());

            // test not including subfolders
            fec.setIncludeSubfolders(false);
            List<Container> noSubfolders = new ArrayList<Container>();
            getChildrenToExport(fec, childList, noSubfolders);
            assertEquals("Expected only one workbook child container", 1, noSubfolders.size());// TODO: this should be 2 once we have container tab included
            assertEquals("workbook", noSubfolders.get(0).getName());
        }
    }
}
