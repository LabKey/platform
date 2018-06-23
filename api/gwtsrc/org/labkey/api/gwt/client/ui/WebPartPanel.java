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

package org.labkey.api.gwt.client.ui;

import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.HTMLPanel;
import com.google.gwt.user.client.ui.Widget;

/**
 * User: jeckels
 * Date: Jun 13, 2007
 */
public class WebPartPanel extends Composite
{
    public WebPartPanel(String title, Widget contents)
    {
        final HTMLPanel panel;
        final String bodyId = HTMLPanel.createUniqueId();

        panel = new HTMLPanel(
            "<div class=\"panel panel-portal\">" +
                "<div class=\"panel-heading\">" +
                    "<h3 class=\"panel-title pull-left\" title=\"" + SafeHtmlUtils.htmlEscape(title) + "\">" + SafeHtmlUtils.htmlEscape(title) + "</h3>" +
                    "<div class=\"clearfix\"></div>" +
                "</div>" +
                "<div id=\"" + bodyId + "\" class=\"panel-body\"></div>" +
            "</div>"
        );
        panel.getElement().setAttribute("name", "webpart");

        panel.add(contents, bodyId);

        initWidget(panel);
    }
}
