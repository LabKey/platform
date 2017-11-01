/*
 * Copyright (c) 2008-2017 LabKey Corporation
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
import com.google.gwt.http.client.URL;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.gwt.client.ui.*;
import org.labkey.api.gwt.client.ui.property.ConditionalFormatItem;
import org.labkey.api.gwt.client.ui.property.DefaultScaleItem;
import org.labkey.api.gwt.client.ui.property.DescriptionItem;
import org.labkey.api.gwt.client.ui.property.DimensionItem;
import org.labkey.api.gwt.client.ui.property.ExcludeFromShiftingItem;
import org.labkey.api.gwt.client.ui.property.FormatItem;
import org.labkey.api.gwt.client.ui.property.PHIItem;
import org.labkey.api.gwt.client.ui.property.RecommendedVariableItem;
import org.labkey.api.gwt.client.ui.property.MeasureItem;
import org.labkey.api.gwt.client.ui.property.URLItem;
import org.labkey.api.gwt.client.ui.property.VisibilityItem;
import org.labkey.api.gwt.client.util.PropertyUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * User: jeckels
 * Date: Nov 14, 2008
 */
public class TablePropertiesEditor extends PropertiesEditor<GWTTableInfo, GWTColumnInfo>
{
    private ImageButton _wrapFieldButton;
    private HTML _otherContainerMessage = new HTML();

    public TablePropertiesEditor(RootPanel rootPanel, Saveable parent, LookupServiceAsync service)
    {
        super(rootPanel, parent, service, new GWTColumnInfo());
        _contentPanel.insert(_otherContainerMessage, 0);
        // Since removing a field won't delete any real data, don't bother lecturing the user on the danger
        _warnAboutDelete = false;
        if(_phiSelector != null)
            _phiSelector.setEnabled(false);  // don't allow PHI metadata setting via this pathway
                                             // TODO: make this work for provisioned tables, does not currently (PropertiesEditor.select() overrides)
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


    protected boolean isReorderable()
    {
        return false;
    }

    @Override
    public void init(GWTTableInfo tableInfo)
    {
        super.init(tableInfo);

        if (tableInfo.getDefinitionFolder() != null)
        {
            String url = PropertyUtil.getContextPath() + "/" + PropertyUtil.getController() + "/";
            String[] pathParts = tableInfo.getDefinitionFolder().split("/");
            for (String pathPart : pathParts)
            {
                if (pathPart.length() > 0)
                {
                    //issue 14006: changed encodeComponent to encodePathSegment, b/c the former will convert spaces to '+'
                    String part = URL.encodePathSegment(pathPart);
                    url += part + "/";
                }
            }
            url += PropertyUtil.getAction() + ".view?" + PropertyUtil.getQueryString();
            _otherContainerMessage.setHTML("This metadata is inherited from another folder. You may override it in " +
                    "this folder or <a href=\"" + url + "\"> edit it in the " + tableInfo.getDefinitionFolder() + "</a> folder.");
        }
        else
        {
            _otherContainerMessage.setHTML("");
        }

        getWrapFieldButton().setVisible(!tableInfo.isUserDefinedQuery());
    }

    @Override
    protected void refreshButtons(HorizontalPanel buttonPanel)
    {
        buttonPanel.clear();
        buttonPanel.add(getWrapFieldButton());
    }

    @Override
    protected List<PropertyPane<GWTTableInfo, GWTColumnInfo>> createPropertyPanes(DockPanel propertyDock)
    {
        PropertyPane<GWTTableInfo, GWTColumnInfo> formatPane = new PropertyPane<GWTTableInfo, GWTColumnInfo>(this, "Format");
        formatPane.addItem(new FormatItem<GWTTableInfo, GWTColumnInfo>(formatPane));
        formatPane.addItem(new ConditionalFormatItem<GWTTableInfo, GWTColumnInfo>(getRootPanel(), formatPane));
        addChangeHandler(formatPane.getChangeListener());

        PropertyPane<GWTTableInfo, GWTColumnInfo> reportingPane = new PropertyPane<GWTTableInfo, GWTColumnInfo>(this, "Reporting");
        reportingPane.addItem(new MeasureItem<GWTTableInfo, GWTColumnInfo>(reportingPane));
        reportingPane.addItem(new DimensionItem<GWTTableInfo, GWTColumnInfo>(reportingPane));
        reportingPane.addItem(new RecommendedVariableItem<GWTTableInfo, GWTColumnInfo>(reportingPane));
        reportingPane.addItem(new DefaultScaleItem<GWTTableInfo, GWTColumnInfo>(reportingPane));
        addChangeHandler(reportingPane.getChangeListener());

        PropertyPane<GWTTableInfo, GWTColumnInfo> propertyPane = new PropertyPane<GWTTableInfo, GWTColumnInfo>(this, "Additional Properties");
        propertyPane.addItem(new DescriptionItem<GWTTableInfo, GWTColumnInfo>(propertyPane));
        propertyPane.addItem(new URLItem<GWTTableInfo, GWTColumnInfo>(propertyPane));
        propertyPane.addItem(new VisibilityItem<GWTTableInfo, GWTColumnInfo>(propertyPane));
        _phiSelector = new PHIItem<GWTTableInfo, GWTColumnInfo>(propertyPane);
        propertyPane.addItem(_phiSelector);
        propertyPane.addItem(new ExcludeFromShiftingItem<GWTTableInfo, GWTColumnInfo>(propertyPane));
        propertyPane.addItem(new WrappedColumnItem(propertyPane));
        addChangeHandler(propertyPane.getChangeListener());
        
        List<PropertyPane<GWTTableInfo, GWTColumnInfo>> result = new ArrayList<PropertyPane<GWTTableInfo, GWTColumnInfo>>();
        result.add(propertyPane);
        result.add(formatPane);
        result.add(reportingPane);
        return result;
    }

    @Override
    protected boolean isRangeEditable(Row row)
    {
        return false;
    }

    @Override
    protected boolean isTypeEditable(Row row)
    {
        return true;
    }

    /** Don't bother enforcing particular column names - we have to support whatever the underlying table supports */
    @Override
    protected String validateFieldName(String value)
    {
        return null;
    }

    @Override
    protected int getExtraPropertiesHeight()
    {
        return 305;
    }
}