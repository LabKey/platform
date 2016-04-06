/*
 * Copyright (c) 2009-2016 LabKey Corporation
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
package org.labkey.query;

import org.labkey.api.admin.BaseFolderWriter;
import org.labkey.api.admin.FolderExportContext;
import org.labkey.api.admin.FolderWriter;
import org.labkey.api.admin.FolderWriterFactory;
import org.labkey.api.admin.FolderArchiveDataTypes;
import org.labkey.api.admin.ImportContext;
import org.labkey.api.admin.ImportException;
import org.labkey.api.data.Container;
import org.labkey.api.query.CustomView;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.SchemaKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.writer.VirtualFile;
import org.labkey.folder.xml.FolderDocument;

import java.util.List;
import java.util.Set;

/**
 * User: adam
 * Date: May 23, 2009
 * Time: 8:25:19 AM
 */
public class CustomViewWriter extends BaseFolderWriter
{
    private static final String DEFAULT_DIRECTORY = "views";  // TODO: qviews?
    private VirtualFile _viewDir = null;

    public String getDataType()
    {
        return FolderArchiveDataTypes.GRID_VIEWS;
    }

    @Override
    public void write(Container object, ImportContext<FolderDocument.Folder> ctx, VirtualFile root) throws Exception
    {
        Container c = ctx.getContainer();
        User user = ctx.getUser();
        Set<String> viewsToExport = null;

        if (ctx.getClass().equals(FolderExportContext.class))
        {
           viewsToExport = ((FolderExportContext)ctx).getViewIds();
        }

        // TODO: Export views from external schemas as well?
        DefaultSchema folderSchema = DefaultSchema.get(user, c);

        Set<SchemaKey> userSchemaPaths = folderSchema.getUserSchemaPaths(true);

        for (SchemaKey schemaKey : userSchemaPaths)
        {
            UserSchema schema = QueryService.get().getUserSchema(user, c, schemaKey);

            List<String> tableAndQueryNames = schema.getTableAndQueryNames(false);

            for (String tableName : tableAndQueryNames)
            {
                List<CustomView> customViews = QueryService.get().getSharedCustomViews(user, c, schemaKey.toString(), tableName, false);

                for (CustomView customView : customViews)
                {
                    if(viewsToExport == null || viewsToExport.contains(customView.getEntityId()))
                    {
                        VirtualFile customViewDir = ensureViewDirectory(ctx, root);
                        if (customView.serialize(customViewDir))
                        {
                            // Create the <view> element only if we have a custom view to write
                            if (!ctx.getXml().isSetViews())
                                ctx.getXml().addNewViews().setDir(DEFAULT_DIRECTORY);
                        }
                    }
                }
            }
        }
    }

    // Create the <views> element
    private VirtualFile ensureViewDirectory(ImportContext<FolderDocument.Folder> ctx, VirtualFile root) throws ImportException
    {
        if (null == _viewDir)
        {
            _viewDir = root.getDir(DEFAULT_DIRECTORY);
        }

        return _viewDir;
    }

    public static class Factory implements FolderWriterFactory
    {
        public FolderWriter create()
        {
            return new CustomViewWriter();
        }
    }
}
