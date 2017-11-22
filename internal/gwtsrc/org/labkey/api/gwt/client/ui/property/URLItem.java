/*
 * Copyright (c) 2008-2010 LabKey Corporation
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
package org.labkey.api.gwt.client.ui.property;

import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.ui.*;
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.gwt.client.ui.PropertyPane;
import org.labkey.api.gwt.client.ui.HelpPopup;
import org.labkey.api.gwt.client.util.PropertyUtil;

/**
 * User: jeckels
 * Date: Nov 14, 2008
 */
public class URLItem<DomainType extends GWTDomain<FieldType>, FieldType extends GWTPropertyDescriptor> extends PropertyPaneItem<DomainType, FieldType>
{
    private TextBox _textBox = new TextBox();

    public URLItem(PropertyPane<DomainType, FieldType> propertyPane)
    {
        super(propertyPane);
    }

    public int addToTable(FlexTable flexTable, int row)
    {
        _textBox.setWidth("100%");
        _textBox.addChangeHandler(createChangeHandler());
        _textBox.addKeyUpHandler(createKeyUpHandler());

        FlowPanel labelPanel = new FlowPanel();
        labelPanel.add(new InlineLabel("URL"));
        labelPanel.add(new HelpPopup("URL", "<p>A template for generating hyperlinks for this field. The <strong>${ }</strong> syntax " +
                "may be used to substitute a field's value into the URL. Three general formats are supported:</p>"+
                "<span style='white-space:nowrap;'><strong>Full URL</strong></span><br/>" +
                "<span>Starts with http:// or https://</span><br/>" +
                "<span style='white-space:nowrap;'><em>http://server/path/page.html?id=${Param}</em></span><br>" +
                "&nbsp;<br>" +
                "<span style='white-space:nowrap;'><strong>Container-relative LabKey URL</strong></span><br/>" +
                "<span>Include controller and action name, omitting context path and folder path</span><br/>" +
                "<span style='white-space:nowrap;'><em>/wiki/page.view?name=${Name}</em></span><br/>" +
                "&nbsp;<br>" +
                "<span style='white-space:nowrap;'><strong>Constructed LabKey URL</strong></span><br/>" +
                "<span>Use substitution parameters to construct a full relative URL</span><br/>" +
                "<span style='white-space:nowrap;'><em>${contextPath}${containerPath}/wiki-page.view?name=${Name}</em></span><br/>" +
                "&nbsp;<br>" +
                "<a href='https://www.labkey.org/Documentation/wiki-page.view?name=urlEncoding' target='_blank'>More information</a>"
                ));

        flexTable.setWidget(row, LABEL_COLUMN, labelPanel);
        DOM.setElementProperty(_textBox.getElement(), "id", "url");
        flexTable.setWidget(row, INPUT_COLUMN, _textBox);

        return ++row;
    }

    public boolean copyValuesToPropertyDescriptor(FieldType pd)
    {
        boolean changed = !PropertyUtil.nullSafeEquals(pd.getURL(), trimValue(_textBox.getText()));
        pd.setURL(trimValue(_textBox.getText()));
        return changed;
    }

    public void enabledChanged()
    {
        _textBox.setEnabled(isEnabled());
    }

    public void showPropertyDescriptor(DomainType domain, FieldType pd)
    {
        _textBox.setText(pd.getURL());
    }
}