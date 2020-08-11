/*
 * Copyright (c) 2011 LabKey Corporation
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

package org.labkey.study.view;

import org.labkey.api.util.HtmlString;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.JspView;

import java.util.List;

/**
 * User: brittp
 * Date: Oct 1, 2011 1:55:57 PM
 */
public class StudyToolsWebPart extends JspView<StudyToolsWebPart.StudyToolsBean>
{
    public static class Item
    {
        private final HtmlString _label;
        private final HtmlString _icon;
        private final URLHelper _url;

        public Item(String label, String icon, URLHelper url)
        {
            _label = HtmlString.of(label);
            _icon = HtmlString.of(icon);
            _url = url;
        }

        public HtmlString getLabel()
        {
            return _label;
        }

        public HtmlString getIcon()
        {
            return _icon;
        }

        public URLHelper getUrl()
        {
            return _url;
        }
    }

    public static class StudyToolsBean
    {
        private final boolean _wide;
        private final List<Item> _items;

        public StudyToolsBean(boolean wide, List<Item> items)
        {
            _wide = wide;
            _items = items;
        }

        public boolean isWide()
        {
            return _wide;
        }

        public List<Item> getItems()
        {
            return _items;
        }
    }

    public StudyToolsWebPart(String title, boolean wide, List<Item> items)
    {
        super("/org/labkey/study/view/toolsWebPart.jsp", new StudyToolsBean(wide, items));
        setTitle(title);
    }
}
