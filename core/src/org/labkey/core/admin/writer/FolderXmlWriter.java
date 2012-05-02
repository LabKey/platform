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

import org.labkey.api.data.Container;
import org.labkey.api.data.MvUtil;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.util.XmlBeansUtil;
import org.labkey.api.writer.VirtualFile;
import org.labkey.folder.xml.FolderDocument;
import org.labkey.study.xml.MissingValueIndicatorsType;

import java.util.Map;

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
