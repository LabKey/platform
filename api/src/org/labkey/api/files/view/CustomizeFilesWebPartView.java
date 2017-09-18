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
import org.labkey.api.view.JspView;
import org.labkey.api.view.Portal;

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

            fileSet = propertyMap.get(FilesWebPart.FILESET_PROPERTY_NAME);
            path = propertyMap.get(FilesWebPart.PATH_PROPERTY_NAME);
            _rootOffset = propertyMap.get(FilesWebPart.ROOT_OFFSET_PROPERTY_NAME);
            _title = propertyMap.get(FilesWebPart.TITLE_PROPERTY_NAME);

            if(propertyMap.get(FilesWebPart.SIZE_PROPERTY_NAME) != null)
                size = Integer.parseInt(propertyMap.get(FilesWebPart.SIZE_PROPERTY_NAME));

            _folderTreeVisible = BooleanUtils.toBoolean(propertyMap.get(FilesWebPart.FOLDER_TREE_VISIBLE_PROPERTY_NAME));

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
    }
}
