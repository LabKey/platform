/*
 * Copyright (c) 2009-2010 LabKey Corporation
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
import com.google.gwt.user.client.ui.*;
import com.google.gwt.user.client.DOM;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.ClickEvent;

/**
 * User: jeckels
 * Date: Nov 14, 2008
 */
public class VisibilityItem<DomainType extends GWTDomain<FieldType>, FieldType extends GWTPropertyDescriptor> extends PropertyPaneItem<DomainType, FieldType>
{
    private CheckBox _gridCheckBox = new CheckBox("Grid");
    private CheckBox _detailsCheckBox = new CheckBox("Details");
    private CheckBox _insertCheckBox = new CheckBox("Insert");
    private CheckBox _updateCheckBox = new CheckBox("Update");

    public VisibilityItem(PropertyPane<DomainType, FieldType> propertyPane)
    {
        super(propertyPane);
    }

    public int addToTable(FlexTable flexTable, int row)
    {
        ClickHandler handler = new ClickHandler()
        {
            public void onClick(ClickEvent event)
            {
                _propertyPane.copyValuesToPropertyDescriptor();
            }
        };
        _gridCheckBox.addClickHandler(handler);
        _detailsCheckBox.addClickHandler(handler);
        _insertCheckBox.addClickHandler(handler);
        _updateCheckBox.addClickHandler(handler);

        flexTable.setWidget(row, LABEL_COLUMN, new Label("Shown In Display Modes"));
        DOM.setElementProperty(_gridCheckBox.getElement(), "id", "propertyShownInGrid");
        DOM.setElementProperty(_insertCheckBox.getElement(), "id", "propertyShownInInsert");
        DOM.setElementProperty(_updateCheckBox.getElement(), "id", "propertyShownInUpdate");
        DOM.setElementProperty(_detailsCheckBox.getElement(), "id", "propertyShownInDetail");

        VerticalPanel leftPanel = new VerticalPanel();
        leftPanel.add(_gridCheckBox);
        leftPanel.add(_updateCheckBox);

        VerticalPanel rightPanel = new VerticalPanel();
        rightPanel.add(_insertCheckBox);
        rightPanel.add(_detailsCheckBox);

        HorizontalPanel panel = new HorizontalPanel();
        panel.add(leftPanel);
        panel.add(new HTML("&nbsp;"));
        panel.add(rightPanel);

        flexTable.setWidget(row, INPUT_COLUMN, panel);

        return ++row;
    }

    public boolean copyValuesToPropertyDescriptor(FieldType field)
    {
        boolean changed = field.isHidden() == _gridCheckBox.getValue().booleanValue() ||
                          !field.isShownInInsertView() == _insertCheckBox.getValue().booleanValue() ||
                          !field.isShownInUpdateView() == _updateCheckBox.getValue().booleanValue() ||
                          !field.isShownInDetailsView() == _detailsCheckBox.getValue().booleanValue();

        field.setHidden(!_gridCheckBox.getValue().booleanValue());
        field.setShownInInsertView(_insertCheckBox.getValue().booleanValue());
        field.setShownInUpdateView(_updateCheckBox.getValue().booleanValue());
        field.setShownInDetailsView(_detailsCheckBox.getValue().booleanValue());

        return changed;
    }

    public void enabledChanged()
    {
        _gridCheckBox.setEnabled(isEnabled());
        _insertCheckBox.setEnabled(isEnabled());
        _updateCheckBox.setEnabled(isEnabled());
        _detailsCheckBox.setEnabled(isEnabled());
    }

    public void showPropertyDescriptor(DomainType domainType, FieldType pd)
    {
        _gridCheckBox.setValue(!pd.isHidden());
        _insertCheckBox.setValue(pd.isShownInInsertView());
        _updateCheckBox.setValue(pd.isShownInUpdateView());
        _detailsCheckBox.setValue(pd.isShownInDetailsView());
    }
}