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
package org.labkey.api.gwt.client.ui.domain;

import com.extjs.gxt.ui.client.event.Events;
import com.extjs.gxt.ui.client.event.FieldEvent;
import com.extjs.gxt.ui.client.event.IconButtonEvent;
import com.extjs.gxt.ui.client.event.Listener;
import com.extjs.gxt.ui.client.event.SelectionListener;
import com.extjs.gxt.ui.client.widget.Dialog;
import com.extjs.gxt.ui.client.widget.HorizontalPanel;
import com.extjs.gxt.ui.client.widget.TabItem;
import com.extjs.gxt.ui.client.widget.TabPanel;
import com.extjs.gxt.ui.client.widget.VerticalPanel;
import com.extjs.gxt.ui.client.widget.button.ToolButton;
import com.extjs.gxt.ui.client.widget.form.ComboBox;
import com.extjs.gxt.ui.client.widget.form.FormPanel;
import com.extjs.gxt.ui.client.widget.form.LabelField;
import com.extjs.gxt.ui.client.widget.form.SimpleComboBox;
import com.extjs.gxt.ui.client.widget.form.SimpleComboValue;
import com.extjs.gxt.ui.client.widget.layout.FitLayout;
import com.google.gwt.user.client.ui.DockPanel;
import com.google.gwt.user.client.ui.Grid;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.InlineHTML;
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.gwt.client.ui.BoundCheckBox;
import org.labkey.api.gwt.client.ui.CachingLookupService;
import org.labkey.api.gwt.client.ui.ConceptPicker;
import org.labkey.api.gwt.client.ui.DirtyCallback;
import org.labkey.api.gwt.client.ui.DomainProvider;
import org.labkey.api.gwt.client.ui.LookupServiceAsync;
import org.labkey.api.gwt.client.ui.PropertyPane;
import org.labkey.api.gwt.client.ui.property.DefaultScaleItem;
import org.labkey.api.gwt.client.ui.property.DescriptionItem;
import org.labkey.api.gwt.client.ui.property.DimensionItem;
import org.labkey.api.gwt.client.ui.property.FormatItem;
import org.labkey.api.gwt.client.ui.property.ImportAliasesItem;
import org.labkey.api.gwt.client.ui.property.RecommendedVariableItem;
import org.labkey.api.gwt.client.ui.property.MeasureItem;
import org.labkey.api.gwt.client.ui.property.MvEnabledItem;
import org.labkey.api.gwt.client.ui.property.RequiredItem;
import org.labkey.api.gwt.client.ui.property.URLItem;
import org.labkey.api.gwt.client.ui.property.VisibilityItem;
import org.labkey.api.gwt.client.util.BooleanProperty;
import org.labkey.api.gwt.client.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: jgarms
 * Date: Nov 5, 2008
 */
public class DomainImportGrid<DomainType extends GWTDomain<FieldType>, FieldType extends GWTPropertyDescriptor> extends VerticalPanel implements DomainProvider, Listener<FieldEvent>
{
    List<FieldType> _columns = new ArrayList<FieldType>();
    Map<GWTPropertyDescriptor, BooleanProperty> _importColumnMap = new HashMap<GWTPropertyDescriptor, BooleanProperty>();
    Map<String, BoundCheckBox> _includeWidgetMap = new HashMap<String, BoundCheckBox>();
    CachingLookupService _lookupService;
    DomainType _domain;
    boolean _showPropertiesPanel;
    Dialog _propertiesPanel;
    List<PropertyPane<DomainType, FieldType>> _properties;

    private Grid _grid;
    private ColumnMapper _mapper;
    private List<InferencedColumn> _inferredColumns;
    private Map<String, FieldType> _columnMap = new HashMap<String, FieldType>();

    public DomainImportGrid(LookupServiceAsync service, DomainType domain)
    {
        _lookupService = new CachingLookupService(service);
        _domain = domain;
        _showPropertiesPanel = true;

        _grid = new Grid(1, 0);
        _grid.setStyleName("labkey-data-region-legacy labkey-show-borders");
        _grid.getRowFormatter().setStyleName(0, "labkey-row-header");

        add(_grid);
    }

    public void removeColumnMapper()
    {
        if (_mapper != null)
        {
            remove(_mapper);
            _mapper = null;
        }
    }

    public void addColumnMapper(List<String> columnsToMap, List<GWTPropertyDescriptor> columnsToMapInfo)
    {
        _mapper = new ColumnMapper(_inferredColumns, columnsToMap, columnsToMapInfo, this);

        // initialized auto mapped columns in the grid
        for (String mappedColumn : _mapper.getMappedColumnNames())
            selectMappedColumn(mappedColumn, true);

        add(_mapper);
        layout();
    }

    public ColumnMapper getColumnMapper()
    {
        return _mapper;
    }

    private void selectMappedColumn(String columnName, boolean mapped)
    {
        if (_includeWidgetMap.containsKey(columnName))
        {
            BoundCheckBox check = _includeWidgetMap.get(columnName);

            if (mapped)
                check.setValue(true);
            check.setEnabled(!mapped);
        }
    }

    public void handleEvent(FieldEvent fieldEvent)
    {
        if (fieldEvent != null)
        {
            SimpleComboValue value = (SimpleComboValue)fieldEvent.getValue();

            if (value != null)
                selectMappedColumn((String)value.getValue(), true);

            // reset the previous value
            value = (SimpleComboValue)fieldEvent.getOldValue();

            if (value != null)
                selectMappedColumn((String)value.getValue(), false);
        }
    }

    public void setColumns(List<InferencedColumn> columns)
    {
        _inferredColumns = columns;
        _grid.resizeColumns(columns.size());
        int numDataRows = columns.get(0).getData().size();
        _grid.resizeRows(numDataRows + 2); // Need a row for the name and a row for the type

        for (int columnIndex = 0; columnIndex < columns.size(); columnIndex++)
        {
            InferencedColumn column = columns.get(columnIndex);
            final GWTPropertyDescriptor prop = column.getPropertyDescriptor();

            String columnMapKey = ((Integer)columnIndex).toString();
            _columnMap.put(columnMapKey, (FieldType)prop);

            // name panel with checkbox to enable/disable import
            HorizontalPanel namePanel = new HorizontalPanel();
            final BooleanProperty include = new BooleanProperty(true);
            BoundCheckBox includeInImport = new BoundCheckBox("id_import_" + columnMapKey, include, new DirtyCallback()
            {
                public void setDirty(boolean dirty)
                {
                    _importColumnMap.put(prop, include);
                }
            });
            _includeWidgetMap.put(prop.getName(), includeInImport);
            
            namePanel.add(includeInImport);
            namePanel.add(new InlineHTML("&nbsp;<b>" + prop.getName() + "</b>&nbsp;"));
            
            if (_showPropertiesPanel)
            {
                ToolButton btn = new ToolButton("x-tbar-page-next", new SelectionListener<IconButtonEvent>()
                {
                    public void componentSelected(IconButtonEvent event)
                    {
                        String id = event.getComponent().getItemId();
                        if (_columnMap.containsKey(id))
                        {
                            FieldType field = _columnMap.get(id);
                            for (PropertyPane<DomainType, FieldType> prop : _properties)
                                prop.showPropertyDescriptor(field, true);

                            _propertiesPanel.show();
                            _propertiesPanel.setHeading(field.getName() + " Column Properties");
                            _propertiesPanel.center();
                        }
                    }
                });
                btn.setToolTip("Click to edit additional properties for this column");
                btn.setItemId(columnMapKey);
                namePanel.add(btn);
            }
            _grid.setWidget(0, columnIndex, namePanel);

            // save in the import map
            _importColumnMap.put(prop, include);
            _columns.add((FieldType)prop);

            // type picker
            ConceptPicker picker = new ConceptPicker.Bound(_lookupService, "ff_type" + columnIndex, prop);
            _grid.setWidget(1, columnIndex, picker);

            // don't allow file and attachment properties for import (they don't really make sense here)
            picker.setAllowAttachmentProperties(false);
            picker.setAllowFileLinkProperties(false);

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

                _grid.setHTML(row+2, columnIndex, cellData);
            }
        }

        if (_showPropertiesPanel)
        {
            TabPanel tabPanel = new TabPanel();
            tabPanel.addStyleName("extContainer");
            _properties = createPropertyPanes(null);

            for (PropertyPane<DomainType, FieldType> propertiesPane : _properties)
            {
                TabItem item = new TabItem(propertiesPane.getName());
                item.setLayout(new FitLayout());
                item.add(propertiesPane);
                tabPanel.add(item);
            }

            _propertiesPanel = new Dialog();
            _propertiesPanel.setModal(true);
            _propertiesPanel.setBorders(false);
            _propertiesPanel.setSize(500, 300);
            _propertiesPanel.setHideOnButtonClick(true);
            _propertiesPanel.setButtons(Dialog.OK);
            _propertiesPanel.setLayout(new FitLayout());
            _propertiesPanel.add(tabPanel);
        }
    }

    public List<FieldType> getColumns(boolean includeIgnored)
    {
        // don't include columns that are unselected for import
        List<FieldType> columns = new ArrayList<FieldType>();

        for (FieldType prop : _columns)
        {
            if (includeIgnored || isImportEnabled(prop))
                columns.add(prop);
        }
        return columns;
    }

    public boolean isImportEnabled(GWTPropertyDescriptor prop)
    {
        if (null != _importColumnMap.get(prop))
            return _importColumnMap.get(prop).booleanValue();
        return false;
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

        PropertyPane<DomainType, FieldType> reportingPane = new PropertyPane<DomainType, FieldType>(this, "Reporting");
        reportingPane.addItem(new MeasureItem<DomainType, FieldType>(reportingPane));
        reportingPane.addItem(new DimensionItem<DomainType, FieldType>(reportingPane));
        reportingPane.addItem(new RecommendedVariableItem<DomainType, FieldType>(reportingPane));
        reportingPane.addItem(new DefaultScaleItem<DomainType, FieldType>(reportingPane));

        PropertyPane<DomainType, FieldType> advancedPane = new PropertyPane<DomainType, FieldType>(this, "Advanced");
        advancedPane.addItem(new MvEnabledItem<DomainType, FieldType>(advancedPane));
        advancedPane.addItem(new ImportAliasesItem<DomainType, FieldType>(advancedPane));

        List<PropertyPane<DomainType, FieldType>> result = new ArrayList<PropertyPane<DomainType, FieldType>>();
        result.add(displayPane);
        result.add(formatPane);
        result.add(validatorPane);
        result.add(reportingPane);
        result.add(advancedPane);

        return result;
    }

    public DomainType getCurrentDomain()
    {
        return _domain;
    }

    public static class ColumnMapper extends FormPanel
    {
        List<SimpleComboBox<String>> _columnSelectors = new ArrayList<SimpleComboBox<String>>();
        private List<String> _columnsToMap;

        public ColumnMapper(List<InferencedColumn> inferredColumns, List<String> columnsToMap,
                            List<GWTPropertyDescriptor> columnsToMapInfo, Listener<FieldEvent> changeListener)
        {
            //setFieldWidth(350);
            setLabelWidth(150);
            setBorders(false);
            setHeaderVisible(false);

            _columnsToMap = columnsToMap;

            add(new HTML("<br/>"));
            add(new HTML("<b>Column Mapping:</b>"));
            add(new InlineHTML("The list below are columns that already exist in the Domain and can be mapped with the " +
                    "inferred columns from the uploaded file.<br>Establish a mapping by selecting a column from the dropdown list to match " +
                    "the exising Domain column.<br>When the data is imported, the data from the inferred column will be added to the " +
                    "mapped Domain column.<br/><br/>"));

            LabelField title = new LabelField("Column from File");
            title.addStyleName("labkey-strong");
            title.setFieldLabel("<span class='labkey-strong'>Server&nbsp;Column</span>");
            add(title);

            Map<String, GWTPropertyDescriptor> extraInfoMap = new HashMap<String, GWTPropertyDescriptor>();
            for (GWTPropertyDescriptor prop : columnsToMapInfo)
                extraInfoMap.put(prop.getName(), prop);

            for (String destinationColumn : columnsToMap)
            {
                SimpleComboBox selector = new SimpleComboBox<String>();
                Set<String> aliases = new HashSet<String>();

                GWTPropertyDescriptor prop = extraInfoMap.get(destinationColumn);
                if (prop != null)
                    aliases = convertAliasToSet(prop.getImportAliases());

                selector.setEmptyText("No mapping");
                selector.setWidth(250);
                selector.setTriggerAction(ComboBox.TriggerAction.ALL);

                if (changeListener != null)
                    selector.addListener(Events.Change, changeListener);
                
                selector.addListener(Events.Change, changeListener);
                selector.setName(destinationColumn);

                // add an entry in the mapping dropdown for each inferred field
                for (InferencedColumn column : inferredColumns)
                {
                    String name = column.getPropertyDescriptor().getName();
                    selector.add(name);
                }

                // initialize the selection with the best match
                GWTPropertyDescriptor matchedCol = findClosestColumn(destinationColumn, prop, aliases, inferredColumns);
                if (matchedCol != null)
                    selector.setSimpleValue(matchedCol.getName());

                _columnSelectors.add(selector);

                selector.setFieldLabel(destinationColumn);
                add(selector);
            }
        }

        public GWTPropertyDescriptor findClosestColumn(String colName, GWTPropertyDescriptor colMetaData, Set<String> aliases, List<InferencedColumn> columns)
        {
            // if we have extended metadata for the target column, try to match using property descriptor type information,
            // or column aliases (if present)
            if (colMetaData != null)
            {
                for (InferencedColumn column : columns)
                {
                    if (areColumnTypesEquivalent(colMetaData, column.getPropertyDescriptor()))
                        return column.getPropertyDescriptor();

                    // if the column has import aliases, try to use those as an additional match criteria
                    for (String alias : aliases)
                    {
                        if (areColumnNamesEquivalent(alias, column.getPropertyDescriptor().getName()))
                            return column.getPropertyDescriptor();
                    }
                }
            }

            // else if no match found, try the simple column name matching
            for (InferencedColumn column : columns)
            {
                if (areColumnNamesEquivalent(colName, column.getPropertyDescriptor().getName()))
                    return column.getPropertyDescriptor();
            }
            return null;
        }

        public Set<String> getMappedColumnNames()
        {
            Set<String> columnNames = new HashSet<String>();
            for (SimpleComboBox<String> listBox : _columnSelectors)
            {
                String value = listBox.getSimpleValue();
                if (value != null)
                    columnNames.add(value);
            }
            return columnNames;
        }

        /**
         * Map of column in the file -> column in the database
         */
        public Map<String,String> getColumnMap()
        {
            Map<String, String> result = new HashMap<String, String>();

            for (int i = 0; i < _columnsToMap.size(); i++)
            {
                String dataColumn = _columnsToMap.get(i);
                SimpleComboBox<String> selector = _columnSelectors.get(i);
                String fileColumn = selector.getSimpleValue();
                if (fileColumn != null)
                    result.put(fileColumn, dataColumn);
            }

            return result;
        }

        /**
         * Try to find a reasonable match in column names, like "Visit Date" and "Date",
         * or "ParticipantID" and "participant id".
         */
        private boolean areColumnTypesEquivalent(GWTPropertyDescriptor col1, GWTPropertyDescriptor col2)
        {
            String name1 = col1.getName().replaceAll(" ", "");
            String name2 = col2.getName().replaceAll(" ", "");

            // if the names are an exact match (less whitespace)
            if (name1.equalsIgnoreCase(name2))
                return true;

            // if the columns are the same type and one of the names represents a subset
            if (col1.getRangeURI().equalsIgnoreCase(col2.getRangeURI()))
            {
                if ((name1.indexOf(name2) >= 0) || (name2.indexOf(name1) >= 0))
                    return true;
            }
            return false;
        }

        /**
         * Try to find a reasonable match in column names, like "Visit Date" and "Date",
         * or "ParticipantID" and "participant id".
         */
        private boolean areColumnNamesEquivalent(String col1, String col2)
        {
            col1 = col1.toLowerCase();
            col2 = col2.toLowerCase();
            col1 = col1.replaceAll(" ","");
            col2 = col2.replaceAll(" ","");
            if (col1.equals(col2))
                return true;
            if (col1.indexOf(col2) >= 0)
                return true;
            if (col2.indexOf(col1) >= 0)
                return true;
            return false;
        }

        private Set<String> convertAliasToSet(String s)
        {
            Set<String> result = new LinkedHashSet<String>();
            String arrayString = convertAliasStringToArray(s);

            if (!StringUtils.isEmpty(arrayString))
            {
                String[] aliases = arrayString.split(",");
                for (String alias : aliases)
                {
                    if (!StringUtils.isEmpty(alias))
                    {
                        if (alias.startsWith("\"") && alias.endsWith("\""))
                        {
                            // Strip off the leading and trailing quotes
                            alias = alias.substring(1, alias.length() - 1);
                        }
                        result.add(alias);
                    }
                }
            }
            return result;
        }

        /**
         * Breaks apart an import alias string into its individual aliases.
         */
        public native String convertAliasStringToArray(String s) /*-{

            var pattern = '[^,; \\t\\n\\f\"]+|\"[^\"]*\"';
            var r = new RegExp(pattern, 'g');
            var result = [];
            var alias;

            while ((alias = r.exec(s)) != null)
            {
                result.push(alias);
            }
            return result.join(',');
        }-*/;
    }
}
