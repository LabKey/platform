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
package org.labkey.wiki.export;

import org.labkey.api.admin.BaseFolderWriter;
import org.labkey.api.admin.FolderWriter;
import org.labkey.api.admin.FolderWriterFactory;
import org.labkey.api.admin.FolderArchiveDataTypes;
import org.labkey.api.admin.ImportContext;
import org.labkey.api.data.Container;
import org.labkey.api.writer.VirtualFile;
import org.labkey.folder.xml.FolderDocument;
import org.labkey.wiki.WikiWebdavProvider;


/**
 * User: jeckels
 * Date: Jan 18, 2012
 */
public class WikiWriterFactory implements FolderWriterFactory
{
    private static final String DIRECTORY_NAME = "wikis";
    public static final String WIKIS_FILENAME = "wikis.xml";

    @Override
    public FolderWriter create()
    {
        return new WikiFolderWriter();
    }

    private class WikiFolderWriter extends BaseFolderWriter
    {
        @Override
        public String getDataType()
        {
            return FolderArchiveDataTypes.WIKIS_AND_THEIR_ATTACHMENTS;
        }

        @Override
        public void write(Container container, ImportContext<FolderDocument.Folder> ctx, VirtualFile vf) throws Exception
        {
            // Set up the pointer in the folder.xml file
            ctx.getXml().addNewWikis().setDir(DIRECTORY_NAME);

            // Just dump the @wiki WebDav tree to the output
            VirtualFile wikiDir = vf.getDir(DIRECTORY_NAME);
            WikiWebdavProvider.WikiProviderResource parent = new WikiWebdavProvider.WikiProviderResource(new DummyWebdavResource(), container);
            wikiDir.saveWebdavTree(parent, ctx.getUser());
        }

    }
}
