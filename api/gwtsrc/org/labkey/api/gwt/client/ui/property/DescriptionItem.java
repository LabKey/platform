/*
 * Copyright (c) 2008-2016 LabKey Corporation
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

import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.gwt.client.ui.PropertyPane;
import com.google.gwt.user.client.ui.TextArea;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.FlexTable;
import com.google.gwt.user.client.DOM;
import org.labkey.api.gwt.client.util.PropertyUtil;

/**
 * User: jeckels
 * Date: Nov 14, 2008
 */
public class DescriptionItem<DomainType extends GWTDomain<FieldType>, FieldType extends GWTPropertyDescriptor> extends PropertyPaneItem<DomainType, FieldType>
{
    private TextArea _descriptionTextArea = new TextArea();

    public DescriptionItem(PropertyPane<DomainType, FieldType> propertyPane)
    {
        super(propertyPane);
    }

    public int addToTable(FlexTable flexTable, int row)
    {
        _descriptionTextArea.addChangeHandler(createChangeHandler());
        _descriptionTextArea.addKeyUpHandler(createKeyUpHandler());

        flexTable.setWidget(row, LABEL_COLUMN, new Label("Description"));
        DOM.setElementProperty(_descriptionTextArea.getElement(), "id", "propertyDescription");
        _descriptionTextArea.setSize("260px", "50px");
        flexTable.setWidget(row, INPUT_COLUMN, _descriptionTextArea);

        return ++row;
    }

    public boolean copyValuesToPropertyDescriptor(FieldType pd)
    {
        boolean changed = !PropertyUtil.nullSafeEquals(pd.getDescription(), trimValue(_descriptionTextArea.getText()));
        pd.setDescription(trimValue(_descriptionTextArea.getText()));
        return changed;
    }

    public void enabledChanged()
    {
        _descriptionTextArea.setEnabled(isEnabled());
    }

    public void showPropertyDescriptor(DomainType domain, FieldType pd)
    {
        _descriptionTextArea.setText(pd.getDescription());
    }
}