/*
 * Copyright (c) 2008 LabKey Corporation
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
package org.labkey.query.metadata.client;

import org.labkey.api.gwt.client.ui.property.PropertyPaneItem;
import org.labkey.api.gwt.client.ui.PropertyPane;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.FlexTable;

/**
 * User: jeckels
 * Date: Nov 14, 2008
 */
public class WrappedColumnItem extends PropertyPaneItem<GWTTableInfo, GWTColumnInfo>
{
    private Label _wrappedLabel = new Label();

    public WrappedColumnItem(PropertyPane<GWTTableInfo, GWTColumnInfo> propertyPane)
    {
        super(propertyPane);
    }

    public int addToTable(FlexTable flexTable, int row)
    {
        flexTable.setWidget(row, LABEL_COLUMN, new Label("Wrapped column"));
        flexTable.setWidget(row, INPUT_COLUMN, _wrappedLabel);
        return ++row;
    }

    public boolean copyValuesToPropertyDescriptor(GWTColumnInfo field)
    {
        return false;
    }

    public void enabledChanged()
    {
    }

    public void showPropertyDescriptor(GWTTableInfo table, GWTColumnInfo field)
    {
        if (field.getWrappedColumnName() != null)
        {
            _wrappedLabel.setText(field.getWrappedColumnName());
        }
        else
        {
            _wrappedLabel.setText("<Not a wrapped column>");
        }
    }
}