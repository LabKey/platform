/*
 * Copyright (c) 2007-2017 LabKey Corporation
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

import java.nio.file.Files;
import java.nio.file.Path;
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

    private static String getDavTreeFileRoot(String fileRoot, String legacyFileRoot, Container c)
    {
        String treeFileRoot = null;
        FileContentService service = FileContentService.get();
        if (null != service)
        {
            if (fileRoot != null)
            {
                if (fileRoot.startsWith(FileContentService.FILES_LINK))
                {
                    // @files disappears when root set to cloud, so make sure it exists before trying in expandPath there
                    Path path = service.getFileRootPath(c);
                    if (null != path)
                    {
                        if (Files.exists(path.resolve(fileRoot)))
                            treeFileRoot = c.getEncodedPath() + fileRoot;
                    }
                }
                else
                {
                    treeFileRoot = c.getEncodedPath() + fileRoot;
                }
            }
            else if (legacyFileRoot != null) // legacy file root
            {
                if (legacyFileRoot.equals(FileContentService.PIPELINE_LINK))
                {
                    PipeRoot root = PipelineService.get().findPipelineRoot(c);
                    treeFileRoot = FilesWebPart.getRootPath(c, root != null && root.isDefault() ? FileContentService.FILES_LINK : FileContentService.PIPELINE_LINK, null, true);
                }
                else if (legacyFileRoot.startsWith(FileContentService.CLOUD_LINK))
                {
                    String storeName = legacyFileRoot.substring((FileContentService.CLOUD_LINK + "/").length());
                    treeFileRoot = FilesWebPart.getRootPath(c, FileContentService.CLOUD_LINK, storeName, true);
                }
                else
                {
                    treeFileRoot = FilesWebPart.getRootPath(c, FileContentService.FILE_SETS_LINK, legacyFileRoot, true);
                }
            }
            else
            {
                if (service.isCloudRoot(c))
                    treeFileRoot = FilesWebPart.getRootPath(c, FileContentService.CLOUD_LINK, service.getCloudRootName(c), true);
                else
                    treeFileRoot = FilesWebPart.getRootPath(c, FileContentService.FILES_LINK, null, true);
            }
        }
        return treeFileRoot;
    }

}
