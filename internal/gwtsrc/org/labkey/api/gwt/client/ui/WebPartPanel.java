/*
 * Copyright (c) 2007-2008 LabKey Corporation
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

package org.labkey.api.gwt.client.ui;

import com.google.gwt.user.client.ui.FlexTable;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.user.client.ui.ClickListener;
import com.google.gwt.user.client.ui.Grid;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.Element;

/**
 * User: jeckels
 * Date: Jun 13, 2007
 */
public class WebPartPanel extends FlexTable
{
    public WebPartPanel(String title, Widget contents)
    {
        setStyleName("wp");
        setText(0, 0, title);

        getRowFormatter().setStyleName(0, "wpHeader");
        getCellFormatter().setStyleName(0, 0, "wpTitle");

        setWidget(1, 0, contents);
    }

    public void setContent(Widget content)
    {
        setWidget(1, 0, content);
    }
}
