/*
 * Copyright (c) 2004-2017 Fred Hutchinson Cancer Research Center
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
package org.labkey.api.view;

import org.labkey.api.util.URLHelper;
import org.labkey.api.view.WebPartView.FrameType;

import java.io.PrintWriter;
import java.util.List;

public interface WebPartFrame
{
    class FrameConfig
    {
        public FrameConfig()
        {
        }

        public FrameConfig(String title)
        {
            this._title = title;
        }

        public String _title = null;
        public String _titleHref = null;
        public String _className = null;
        public URLHelper _closeURL = null;
        public boolean _isEmbedded = false;
        public boolean _showTitle  = true;
        public boolean _isWebpart  = true;
        public boolean _isEmpty = false;
        public String _helpPopup;
        public FrameType _frame = FrameType.PORTAL;
        public Portal.WebPart _webpart = null;
        public NavTree _navMenu = null;
        public List<NavTree> _customMenus = null;
        public String _location;
        public NavTree _customize;
        public boolean _hidePageTitle = false;
        public boolean _isCollapsible = false;
        public boolean _collapsed = false;
        public NavTree _portalLinks = new NavTree();
        public String _rootId = null;
        public boolean _showfloatingCustomBtn  = false; // for frameless webpart only
        public List<NavTree> _floatingBtns = null;
    }

    void doStartTag(PrintWriter out);

    void doEndTag(PrintWriter out);
}
