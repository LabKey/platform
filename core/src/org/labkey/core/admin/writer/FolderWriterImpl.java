/*
 * Copyright (c) 2012 LabKey Corporation
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

package org.labkey.core.admin.writer;

import org.apache.log4j.Logger;
import org.labkey.api.data.Container;
import org.labkey.api.writer.VirtualFile;

import java.util.Set;

/**
 * User: cnathe
 * Date: Jan 18, 2012
 */
public class FolderWriterImpl implements InternalFolderWriter
{
    private static final Logger LOG = Logger.getLogger(FolderWriterImpl.class);

    public String getSelectionText()
    {
        return null;
    }

    public void write(Container c, FolderExportContext ctx, VirtualFile vf) throws Exception
    {
        LOG.info("Exporting folder to " + vf.getLocation());

        Set<String> dataTypes = ctx.getDataTypes();

        // Call all the writers first -- this ensures that folder.xml is the last writer called.
        for (org.labkey.api.admin.FolderWriter writer : FolderSerializationRegistryImpl.get().getRegisteredFolderWriters())
        {
            String text = writer.getSelectionText();

            if (null == text || dataTypes.contains(text))
                writer.write(c, ctx, vf);
        }

        FolderXmlWriter xmlWriter = new FolderXmlWriter();
        xmlWriter.write(c, ctx, vf);

        LOG.info("Done exporting folder to " + vf.getLocation());
    }
}

