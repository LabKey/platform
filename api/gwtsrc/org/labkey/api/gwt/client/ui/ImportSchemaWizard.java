/*
 * Copyright (c) 2008-2015 LabKey Corporation
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

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.*;
import org.labkey.api.gwt.client.util.StringUtils;

/**
 * User: jgarms
* Date: Oct 28, 2008
*/
class ImportSchemaWizard extends DialogBox
{
    private final TextArea schemaTsv;
    private final PropertiesEditor propertiesEditor;

    public ImportSchemaWizard(PropertiesEditor propertiesEditor)
    {
        super(false, true);
        this.propertiesEditor = propertiesEditor;
        VerticalPanel vPanel = new VerticalPanel();
        String warning = "<b>NOTE: This will replace any existing fields you have defined.</b>";
        if (propertiesEditor._domain.getFields().size() > 0)
        {
            warning = "<b><font class=\"labkey-error\">WARNING: This will delete all existing fields and data!<br/>" +
                    "If you only wish to rename, add or delete fields, use the domain editor.</font></b>";
        }
        HTML html = new HTML(
            warning +
                "<p><b>Paste tab-delimited text with the following column headers and one row for each field</b><br>\n" +
                "<b>Property</b> - Required. Field name. Must start with a character and include only characters and numbers<br>\n" +
                "<b>Format</b> - Optional. Format for a date or numeric field<br>\n" +
                "<b>RangeURI</b> - Optional. Values: xsd:int, xsd:string, xsd:double, xsd:boolean, xsd:dateTime. Defaults to xsd:string<br>\n" +
                "<b>Label</b> - Optional. Name that users will see for the field<br>\n" +
                "<b>NotNull</b> - Optional. Set to TRUE if this value is required<br>\n" +
                "<b>Hidden</b> - Optional. Set to TRUE if this field should not be shown in default grid views<br>\n" +
                "<b>LookupSchema</b> - Optional. If there is a lookup defined on this column, this is the target schema<br>\n" +
                "<b>LookupQuery</b> - Optional. If there is a lookup defined on this column, this is the target query or table name<br>\n" +
                "<b>Description</b> - Optional. Description of the field</p>");
        vPanel.add(html);

        schemaTsv = new TextArea();
        schemaTsv.setCharacterWidth(80);
        schemaTsv.setHeight("300px");
        schemaTsv.setName("tsv");
        DOM.setElementAttribute(schemaTsv.getElement(), "id", "schemaImportBox");
        vPanel.add(schemaTsv);

        HorizontalPanel buttonPanel = new HorizontalPanel();
        buttonPanel.setSpacing(5);
        buttonPanel.add(new ImageButton("Import", new ClickHandler()
        {
            public void onClick(ClickEvent e)
            {
                processTsv();
            }
        }));
        buttonPanel.add(new ImageButton("Cancel", new ClickHandler()
        {
            public void onClick(ClickEvent e)
            {
                ImportSchemaWizard.this.hide();
            }
        }));
        vPanel.add(buttonPanel);

        HorizontalPanel mainPanel = new HorizontalPanel();
        mainPanel.setSpacing(10);
        mainPanel.add(vPanel);

        setWidget(mainPanel);
    }

    private void processTsv()
    {
        String tsv = StringUtils.trimToNull(schemaTsv.getText());
        if (tsv == null)
        {
            Window.alert("Please enter some tab-delimited text");
            return;
        }
        boolean success = new GWTTabLoader(tsv).processTsv(propertiesEditor);

        if (success)
        {
            // done, hide ourselves
            ImportSchemaWizard.this.hide();
        }
    }

}
