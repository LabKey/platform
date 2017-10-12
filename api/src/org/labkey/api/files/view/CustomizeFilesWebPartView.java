/*
 * Copyright (c) 2007-2013 LabKey Corporation
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

package org.labkey.api.files.view;

import org.apache.commons.lang3.BooleanUtils;
import org.labkey.api.cloud.CloudStoreService;
import org.labkey.api.data.Container;
import org.labkey.api.files.FileContentService;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.view.JspView;
import org.labkey.api.view.Portal;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * User: Mark Igra
 * Date: Jul 16, 2007
 * Time: 2:27:37 PM
 */
public class CustomizeFilesWebPartView extends JspView<CustomizeFilesWebPartView.CustomizeWebPartForm>
{
    public CustomizeFilesWebPartView(Portal.WebPart currentConfig)
    {
        super("/org/labkey/api/files/view/customizeWebPart.jsp");
        setModelBean(new CustomizeWebPartForm(currentConfig));
    }

    public static class CustomizeWebPartForm
    {
        private String fileSet;
        private String fileRoot; // webdav node id
        private String path;
        private boolean useFileSet;
        private boolean _folderTreeVisible;
        private String _location;
        private Portal.WebPart _webPart;
        private String _rootOffset;
        private int size = 350;
        private String _title;

        public CustomizeWebPartForm(Portal.WebPart webPart)
        {
            _webPart = webPart;
            _location = webPart.getLocation();


            Map<String, String> propertyMap = webPart.getPropertyMap();

            fileSet = propertyMap.get(FilesWebPart.FILESET_PROPERTY_NAME); // legacy file root
            path = propertyMap.get(FilesWebPart.PATH_PROPERTY_NAME);
            _rootOffset = propertyMap.get(FilesWebPart.ROOT_OFFSET_PROPERTY_NAME);
            _title = propertyMap.get(FilesWebPart.TITLE_PROPERTY_NAME);

            if(propertyMap.get(FilesWebPart.SIZE_PROPERTY_NAME) != null)
                size = Integer.parseInt(propertyMap.get(FilesWebPart.SIZE_PROPERTY_NAME));

            _folderTreeVisible = BooleanUtils.toBoolean(propertyMap.get(FilesWebPart.FOLDER_TREE_VISIBLE_PROPERTY_NAME));

            String davFileRoot = propertyMap.get(FilesWebPart.FILE_ROOT_PROPERTY_NAME);
            fileRoot = getDavTreeFileRoot(davFileRoot, fileSet, getContextContainer());

            useFileSet = fileSet != null;
        }

        public String getFileSet()
        {
            return fileSet;
        }

        public void setFileSet(String fileSet)
        {
            this.fileSet = fileSet;
        }

        public String getPath()
        {
            return path;
        }

        public void setPath(String path)
        {
            this.path = path;
        }

        public boolean isUseFileSet()
        {
            return useFileSet;
        }

        public void setUseFileSet(boolean useFileSet)
        {
            this.useFileSet = useFileSet;
        }

        public boolean isFolderTreeVisible()
        {
            return _folderTreeVisible;
        }

        public void setFolderTreeVisible(boolean folderTreeVisible)
        {
            _folderTreeVisible = folderTreeVisible;
        }

        public String getLocation()
        {
            return _location;
        }

        public Portal.WebPart getWebPart()
        {
            return _webPart;
        }

        public String getRootOffset()
        {
            return _rootOffset;
        }

        public void setRootOffset(String rootOffset)
        {
            if (null != rootOffset && !rootOffset.endsWith("/"))
                rootOffset = rootOffset + "/";
            _rootOffset = rootOffset;
        }

        public int getSize()
        {
            return size;
        }

        public void setSize(int size)
        {
            this.size = size;
        }

        public String getTitle()
        {
            return _title;
        }

        public void setTitle(String title)
        {
            _title = title;
        }

        public List<String> getEnabledCloudStores(Container container)
        {
            List<String> cloudStoreNames = new ArrayList<>();
            CloudStoreService cloud = CloudStoreService.get();
            if (cloud != null)
            {
                for (String store : cloud.getEnabledCloudStores(container))
                    if (CloudStoreService.get().containerFolderExists(store, container))
                        cloudStoreNames.add(store);
            }
            return cloudStoreNames;
        }

        public String getFileRoot()
        {
            return fileRoot;
        }

        public void setFileRoot(String fileRoot)
        {
            this.fileRoot = fileRoot;
        }
    }

    public static String getDavTreeFileRoot(String fileRoot, String legacyFileRoot, Container c)
    {
        if (fileRoot != null)
        {
            return c.getEncodedPath() + fileRoot;
        }
        else if (legacyFileRoot != null) // legacy file root
        {
            if (legacyFileRoot.equals(FileContentService.PIPELINE_LINK))
            {
                PipeRoot root = PipelineService.get().findPipelineRoot(c);
                return FilesWebPart.getRootPath(c, root != null && root.isDefault() ? FileContentService.FILES_LINK : FileContentService.PIPELINE_LINK, null, true);
            }
            else if (legacyFileRoot.startsWith(CloudStoreService.CLOUD_NAME))
            {
                // UNDONE: Configure filebrowser to not expand by default since even listing store contents costs money.
                String storeName = legacyFileRoot.substring((CloudStoreService.CLOUD_NAME + "/").length());
                return FilesWebPart.getRootPath(c, CloudStoreService.CLOUD_NAME, storeName, true);
            }
            else
            {
                return FilesWebPart.getRootPath(c, FileContentService.FILE_SETS_LINK, legacyFileRoot, true);
            }
        }
        return FilesWebPart.getRootPath(c, FileContentService.FILES_LINK, null, true);
    }

}
