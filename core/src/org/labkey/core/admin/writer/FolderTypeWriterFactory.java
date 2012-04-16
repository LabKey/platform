package org.labkey.core.admin.writer;

import org.labkey.api.admin.BaseFolderWriter;
import org.labkey.api.admin.FolderWriter;
import org.labkey.api.admin.FolderWriterFactory;
import org.labkey.api.admin.ImportContext;
import org.labkey.api.data.Container;
import org.labkey.api.module.Module;
import org.labkey.api.writer.VirtualFile;
import org.labkey.folder.xml.FolderDocument;
import org.labkey.folder.xml.FolderType;

/**
 * User: cnathe
 * Date: Apr 10, 2012
 */
public class FolderTypeWriterFactory implements FolderWriterFactory
{
    @Override
    public FolderWriter create()
    {
        return new FolderTypeWriter();
    }

    public class FolderTypeWriter extends BaseFolderWriter
    {
        @Override
        public String getSelectionText()
        {
            return "Folder type and active modules";
        }

        @Override
        public void write(Container c, ImportContext<FolderDocument.Folder> ctx, VirtualFile vf) throws Exception
        {
            FolderDocument.Folder folderXml = ctx.getXml();
            FolderType ftXml = folderXml.addNewFolderType();
            ftXml.setName(c.getFolderType().getName());

            if (null != c.getDefaultModule())
            {
                ftXml.setDefaultModule(c.getDefaultModule().getName());
            }             

            FolderType.Modules modulesXml = ftXml.addNewModules();
            for (Module module : c.getActiveModules())
            {
                modulesXml.addModuleName(module.getName());
            }
        }
    }
}
