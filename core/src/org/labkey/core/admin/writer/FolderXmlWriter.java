package org.labkey.core.admin.writer;

import org.labkey.api.data.Container;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.util.XmlBeansUtil;
import org.labkey.api.writer.VirtualFile;
import org.labkey.folder.xml.FolderDocument;

/**
 * User: cnathe
 * Date: Jan 18, 2012
 *
 * This writer is largely responsible for folder.xml.  It constructs the FolderDocument (xml bean used to read/write folder.xml)
 * that gets added to the FolderExportContext, writes the top-level folder attributes, and writes out the bean when it's complete.
 */
public class FolderXmlWriter implements InternalFolderWriter
{
    public String getSelectionText()
    {
        return null;
    }

    public void write(Container c, FolderExportContext ctx, VirtualFile vf) throws Exception
    {
        FolderDocument.Folder folderXml = ctx.getXml();

        // Insert standard comment explaining where the data lives, who exported it, and when
        XmlBeansUtil.addStandardExportComment(folderXml, ctx.getContainer(), ctx.getUser());

        folderXml.setArchiveVersion(ModuleLoader.getInstance().getCoreModule().getVersion());
        folderXml.setLabel(c.getName()); // TODO: change to setName

        // Save the folder.xml file.  This gets called last, after all other writers have populated the other sections.
        vf.saveXmlBean("folder.xml", ctx.getDocument());

        ctx.lockDocument();
    }

    public static FolderDocument getFolderDocument()
    {
        FolderDocument doc = FolderDocument.Factory.newInstance();
        doc.addNewFolder();
        return doc;
    }
}
