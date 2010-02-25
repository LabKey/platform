/*
 * Copyright (c) 2007-2009 LabKey Corporation
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

import org.apache.commons.lang.BooleanUtils;
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
        private String pageId;
        private int index;
        private String fileSet;
        private String path;
        private boolean useFileSet;
        private boolean _folderTreeVisible;
        private String _location;

        public CustomizeWebPartForm(Portal.WebPart webPart)
        {
            pageId = webPart.getPageId();
            index = webPart.getIndex();
            _location = webPart.getLocation();

            Map<String, String> propertyMap = webPart.getPropertyMap();

            fileSet = propertyMap.get("fileSet");
            path = propertyMap.get("path");
            _folderTreeVisible = BooleanUtils.toBoolean(propertyMap.get("folderTreeVisible"));

            useFileSet = fileSet != null;
        }

        public String getPageId()
        {
            return pageId;
        }

        public void setPageId(String pageId)
        {
            this.pageId = pageId;
        }

        public int getIndex()
        {
            return index;
        }

        public void setIndex(int index)
        {
            this.index = index;
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
    }
}
