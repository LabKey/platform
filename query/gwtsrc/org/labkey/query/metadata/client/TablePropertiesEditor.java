/*
 * Copyright (c) 2008-2009 LabKey Corporation
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

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.ui.*;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.gwt.client.ui.*;
import org.labkey.api.gwt.client.ui.property.DescriptionItem;
import org.labkey.api.gwt.client.ui.property.FormatItem;
import org.labkey.api.gwt.client.ui.property.URLItem;

import java.util.ArrayList;
import java.util.List;

/**
 * User: jeckels
 * Date: Nov 14, 2008
 */
public class TablePropertiesEditor extends PropertiesEditor<GWTTableInfo, GWTColumnInfo>
{
    private ImageButton _wrapFieldButton;

    public TablePropertiesEditor(Saveable parent, LookupServiceAsync service)
    {
        super(parent, service);
    }

    private ImageButton getWrapFieldButton()
    {
        if (_wrapFieldButton == null)
        {
            _wrapFieldButton = new ImageButton("Alias Field", new ClickHandler()
            {
                public void onClick(ClickEvent e)
                {
                    final ListBox list = new ListBox(false);
                    for (GWTColumnInfo pd : _domain.getFields())
                    {
                        if (_domain.isMandatoryField(pd))
                        {
                            list.addItem(pd.getName());
                        }
                    }
                    list.setName("sourceColumn");
                    final DialogBox dialog = new DialogBox(false, true);
                    dialog.setText("Choose a field to wrap");
                    VerticalPanel panel = new VerticalPanel();
                    panel.setSpacing(10);
                    list.setWidth("100%");
                    panel.setWidth("100%");
                    panel.add(list);
                    panel.setHorizontalAlignment(HasHorizontalAlignment.ALIGN_CENTER);

                    HorizontalPanel buttonPanel = new HorizontalPanel();

                    ImageButton okButton = new ImageButton("OK", new ClickHandler()
                    {
                        public void onClick(ClickEvent e)
                        {
                            for (GWTPropertyDescriptor existingPD : _domain.getFields())
                            {
                                if (existingPD.getName().equals(list.getValue(list.getSelectedIndex())))
                                {
                                    GWTColumnInfo pd = new GWTColumnInfo();
                                    pd.setRangeURI(existingPD.getRangeURI());
                                    pd.setName("Wrapped" + existingPD.getName());
                                    pd.setWrappedColumnName(existingPD.getName());
                                    addField(pd);
                                    dialog.hide();
                                    break;
                                }
                            }
                        }
                    });
                    ImageButton cancelButton = new ImageButton("Cancel", new ClickHandler()
                    {
                        public void onClick(ClickEvent e)
                        {
                            dialog.hide();
                        }
                    });

                    buttonPanel.add(okButton);
                    buttonPanel.add(cancelButton);
                    panel.add(buttonPanel);
                    dialog.setWidget(panel);
                    dialog.setPopupPositionAndShow(WindowUtil.createPositionCallback(dialog));
                }
            });
        }
        _wrapFieldButton.setVisible(false);
        return _wrapFieldButton;
    }

    @Override
    public void init(GWTTableInfo domain)
    {
        super.init(domain);
        getWrapFieldButton().setVisible(domain != null && !domain.isUserDefinedQuery());
    }

    @Override
    protected void refreshButtons(HorizontalPanel buttonPanel)
    {
        buttonPanel.clear();
        buttonPanel.add(getWrapFieldButton());
    }

    protected int getExtraPropertiesHeight()
    {
        return 130;
    }

    protected List<PropertyPane<GWTTableInfo, GWTColumnInfo>> createPropertyPanes(DockPanel propertyDock)
    {
        PropertyPane<GWTTableInfo, GWTColumnInfo> propertyPane = new PropertyPane<GWTTableInfo, GWTColumnInfo>(this, "Additional Properties");
        propertyPane.addItem(new DescriptionItem<GWTTableInfo, GWTColumnInfo>(propertyPane));
        propertyPane.addItem(new URLItem<GWTTableInfo, GWTColumnInfo>(propertyPane));
        propertyPane.addItem(new FormatItem<GWTTableInfo, GWTColumnInfo>(propertyPane));
        propertyPane.addItem(new WrappedColumnItem(propertyPane));
        List<PropertyPane<GWTTableInfo, GWTColumnInfo>> result = new ArrayList<PropertyPane<GWTTableInfo, GWTColumnInfo>>();
        result.add(propertyPane);
        return result;
    }

    @Override
    protected boolean isTypeEditable(GWTPropertyDescriptor pd, FieldStatus status)
    {
        return false;
    }

    @Override
    protected LookupEditor<GWTColumnInfo> createLookupEditor()
    {
        return new LookupEditor<GWTColumnInfo>(_lookupService, this, false);
    }

}