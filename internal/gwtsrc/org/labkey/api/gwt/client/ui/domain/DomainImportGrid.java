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
package org.labkey.api.gwt.client.ui.domain;

import com.extjs.gxt.ui.client.event.ComponentEvent;
import com.extjs.gxt.ui.client.event.IconButtonEvent;
import com.extjs.gxt.ui.client.event.SelectionListener;
import com.extjs.gxt.ui.client.util.Point;
import com.extjs.gxt.ui.client.widget.Dialog;
import com.extjs.gxt.ui.client.widget.HorizontalPanel;
import com.extjs.gxt.ui.client.widget.TabItem;
import com.extjs.gxt.ui.client.widget.TabPanel;
import com.extjs.gxt.ui.client.widget.button.ToolButton;
import com.extjs.gxt.ui.client.widget.layout.FitLayout;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.DockPanel;
import com.google.gwt.user.client.ui.Grid;
import com.google.gwt.user.client.ui.InlineHTML;
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.gwt.client.ui.BoundCheckBox;
import org.labkey.api.gwt.client.ui.CachingLookupService;
import org.labkey.api.gwt.client.ui.ConceptPicker;
import org.labkey.api.gwt.client.ui.DomainProvider;
import org.labkey.api.gwt.client.ui.LookupServiceAsync;
import org.labkey.api.gwt.client.ui.PropertyPane;
import org.labkey.api.gwt.client.ui.property.DescriptionItem;
import org.labkey.api.gwt.client.ui.property.DimensionItem;
import org.labkey.api.gwt.client.ui.property.FormatItem;
import org.labkey.api.gwt.client.ui.property.ImportAliasesItem;
import org.labkey.api.gwt.client.ui.property.MeasureItem;
import org.labkey.api.gwt.client.ui.property.MvEnabledItem;
import org.labkey.api.gwt.client.ui.property.RequiredItem;
import org.labkey.api.gwt.client.ui.property.URLItem;
import org.labkey.api.gwt.client.ui.property.ValidatorItem;
import org.labkey.api.gwt.client.ui.property.VisibilityItem;
import org.labkey.api.gwt.client.util.BooleanProperty;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * User: jgarms
 * Date: Nov 5, 2008
 */
public class DomainImportGrid<DomainType extends GWTDomain<FieldType>, FieldType extends GWTPropertyDescriptor> extends Grid implements DomainProvider
{
    List<FieldType> _columns = new ArrayList<FieldType>();
    Map<GWTPropertyDescriptor, BooleanProperty> _importColumnMap = new HashMap<GWTPropertyDescriptor, BooleanProperty>();
    CachingLookupService _lookupService;
    DomainType _domain;
    boolean _showPropertiesPanel;
    Dialog _propertiesPanel;
    List<PropertyPane<DomainType, FieldType>> _properties;


    public DomainImportGrid(LookupServiceAsync service, DomainType domain)
    {
        super(1,0);

        _lookupService = new CachingLookupService(service);
        _domain = domain;
        _showPropertiesPanel = false;

        setStyleName("labkey-data-region labkey-show-borders");

        RowFormatter rowFormatter = new RowFormatter();
        rowFormatter.setStyleName(0, "labkey-row-header");

        setRowFormatter(rowFormatter);
    }

    public void setColumns(List<InferencedColumn> columns)
    {
        resizeColumns(columns.size());
        int numDataRows = columns.get(0).getData().size();
        resizeRows(numDataRows + 2); // Need a row for the name and a row for the type

        for(int columnIndex=0; columnIndex<columns.size(); columnIndex++)
        {
            InferencedColumn column = columns.get(columnIndex);
            GWTPropertyDescriptor prop = column.getPropertyDescriptor();

            // name panel with checkbox to enable/disable import
            HorizontalPanel namePanel = new HorizontalPanel();
            BooleanProperty include = new BooleanProperty(true);
            BoundCheckBox includeInImport = new BoundCheckBox("id_import_" + prop.getName(), include, null);
            namePanel.add(includeInImport);
            namePanel.add(new InlineHTML("&nbsp;<b>" + prop.getName() + "</b>&nbsp;"));

            if (_showPropertiesPanel)
            {
                ToolButton btn = new ToolButton("x-tool-right", new SelectionListener<IconButtonEvent>()
                {
                    public void componentSelected(IconButtonEvent event)
                    {
                        for (PropertyPane<DomainType, FieldType> prop : _properties)
                            prop.showPropertyDescriptor(_columns.get(0), true);

                        Point pt = event.getXY();

                        _propertiesPanel.setPagePosition(pt.x + Window.getScrollLeft(), pt.y + Window.getScrollTop());
                        _propertiesPanel.show();
                    }
                });
                btn.setToolTip("Click to edit additional properties for this column");
                namePanel.add(btn);
            }
            setWidget(0, columnIndex, namePanel);

            // save in the import map
            _importColumnMap.put(prop, include);
            _columns.add((FieldType)prop);

            // type picker
            ConceptPicker picker = new ConceptPicker.Bound(_lookupService, "ff_type" + columnIndex, prop);
            setWidget(1, columnIndex, picker);

            // don't allow file and attachment properties for import (they don't really make sense here)
            picker.setAllowAttachmentProperties(false);
            picker.setAllowFileLinkProperties(false);
            //picker.setIsRangeEditable(false);

            List<String> data = column.getData();
            for (int row=0; row<numDataRows; row++)
            {
                String cellData = data.get(row);

                // In order to suggest that there is more data than is displayed,
                // we will gray out the last two rows somewhat.
                if (numDataRows > 3 && row == numDataRows - 2)
                    cellData = "<font color=\"333333\">" + cellData + "</font>";
                else if (numDataRows > 3 && row == numDataRows - 1)
                    cellData = "<font color=\"666666\">" + cellData + "</font>";

                setHTML(row+2, columnIndex, cellData);
            }
        }

        if (_showPropertiesPanel)
        {
            TabPanel tabPanel = new TabPanel();
            _properties = createPropertyPanes(null);

            for (PropertyPane<DomainType, FieldType> propertiesPane : _properties)
            {
                TabItem item = new TabItem(propertiesPane.getName());

                item.setSize(300, 400);
                item.setLayout(new FitLayout());
                item.add(propertiesPane);

                tabPanel.add(item);
            }
            _propertiesPanel = new Dialog();
            _propertiesPanel.setModal(true);
            _propertiesPanel.setPlain(true);
            _propertiesPanel.setHeading("Column Properties");
            _propertiesPanel.setSize(300, 400);
            _propertiesPanel.setHideOnButtonClick(true);
            _propertiesPanel.setButtons(Dialog.OKCANCEL);

            _propertiesPanel.add(tabPanel);
        }
    }

    public List<FieldType> getColumns()
    {
        // don't include columns that are unselected for import
        List<FieldType> columns = new ArrayList<FieldType>();

        for (FieldType prop : _columns)
        {
            if (isImportEnabled(prop))
                columns.add(prop);
        }
        return columns;
    }

    public boolean isImportEnabled(GWTPropertyDescriptor prop)
    {
        return _importColumnMap.get(prop).booleanValue();
    }

    private List<PropertyPane<DomainType, FieldType>> createPropertyPanes(DockPanel propertyDock)
    {
        PropertyPane<DomainType, FieldType> displayPane = new PropertyPane<DomainType, FieldType>(this, "Display");
        displayPane.addItem(new DescriptionItem<DomainType, FieldType>(displayPane));
        displayPane.addItem(new URLItem<DomainType, FieldType>(displayPane));
        displayPane.addItem(new VisibilityItem<DomainType, FieldType>(displayPane));

        PropertyPane<DomainType, FieldType> formatPane = new PropertyPane<DomainType, FieldType>(this, "Format");
        formatPane.addItem(new FormatItem<DomainType, FieldType>(formatPane));

        PropertyPane<DomainType, FieldType> validatorPane = new PropertyPane<DomainType, FieldType>(this, "Validators");
        validatorPane.addItem(new RequiredItem<DomainType, FieldType>(validatorPane));
        validatorPane.addItem(new ValidatorItem<DomainType, FieldType>(validatorPane));

        PropertyPane<DomainType, FieldType> advancedPane = new PropertyPane<DomainType, FieldType>(this, "Advanced");
        advancedPane.addItem(new MvEnabledItem<DomainType, FieldType>(advancedPane));
        advancedPane.addItem(new ImportAliasesItem<DomainType, FieldType>(advancedPane));
        advancedPane.addItem(new MeasureItem<DomainType, FieldType>(advancedPane));
        advancedPane.addItem(new DimensionItem<DomainType, FieldType>(advancedPane));

        List<PropertyPane<DomainType, FieldType>> result = new ArrayList<PropertyPane<DomainType, FieldType>>();
        result.add(displayPane);
        result.add(formatPane);
        result.add(validatorPane);
        result.add(advancedPane);

        return result;
    }

    public DomainType getCurrentDomain()
    {
        return _domain;
    }
}
