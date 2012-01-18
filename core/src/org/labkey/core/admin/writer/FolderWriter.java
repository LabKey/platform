
package org.labkey.core.admin.writer;

import org.apache.log4j.Logger;
import org.labkey.api.admin.ExternalFolderWriter;
import org.labkey.api.data.Container;
import org.labkey.api.writer.VirtualFile;
import org.labkey.api.writer.Writer;

import java.util.Set;

/**
 * User: cnathe
 * Date: Jan 18, 2012
 */
public class FolderWriter implements InternalFolderWriter
{
    private static final Logger LOG = Logger.getLogger(FolderWriter.class);

    public String getSelectionText()
    {
        return null;
    }

    public void write(Container c, FolderExportContext ctx, VirtualFile vf) throws Exception
    {
        LOG.info("Exporting folder to " + vf.getLocation());

        Set<String> dataTypes = ctx.getDataTypes();

        // Call all the external writers (those defined outside the core module) first -- this ensures that folder.xml
        // is the last writer called.
        for (ExternalFolderWriter writer : FolderSerializationRegistryImpl.get().getRegisteredFolderWriters())
        {
            String text = writer.getSelectionText();

            if (null == text || dataTypes.contains(text))
                writer.write(c, ctx, vf);
        }

        // Now call all the writers defined in the core module.
        for (Writer<Container, FolderExportContext> writer : FolderSerializationRegistryImpl.get().getInternalFolderWriters())
        {
            String text = writer.getSelectionText();

            if (null == text || dataTypes.contains(text))
                writer.write(c, ctx, vf);
        }

        LOG.info("Done exporting folder to " + vf.getLocation());
    }
}

