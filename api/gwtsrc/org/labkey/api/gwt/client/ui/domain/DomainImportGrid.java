/*
 * Copyright (c) 2018-2019 LabKey Corporation
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

import com.google.gwt.event.dom.client.ChangeEvent;
import com.google.gwt.event.dom.client.ChangeHandler;
import com.google.gwt.user.client.ui.FlexTable;
import com.google.gwt.user.client.ui.Grid;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.InlineHTML;
import com.google.gwt.user.client.ui.ListBox;
import com.google.gwt.user.client.ui.VerticalPanel;
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.gwt.client.ui.BoundCheckBox;
import org.labkey.api.gwt.client.ui.PropertyType;
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
public class DomainImportGrid<DomainType extends GWTDomain<FieldType>, FieldType extends GWTPropertyDescriptor> extends VerticalPanel
{
    List<FieldType> _columns = new ArrayList<FieldType>();
    Map<GWTPropertyDescriptor, BooleanProperty> _importColumnMap = new HashMap<GWTPropertyDescriptor, BooleanProperty>();
    Map<String, BoundCheckBox> _includeWidgetMap = new HashMap<String, BoundCheckBox>();
    DomainType _domain;

    private Grid _grid;
    private ColumnMapper _mapper;
    private List<InferencedColumn> _inferredColumns;
    private Map<String, FieldType> _columnMap = new HashMap<String, FieldType>();
    private static final String NO_MAPPING_COLUMN = "No mapping";

    public DomainImportGrid(DomainType domain)
    {
        _domain = domain;

        _grid = new Grid(1, 0);
        _grid.setStyleName("labkey-data-region-legacy labkey-show-borders");

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
        _mapper = new ColumnMapper(_inferredColumns, columnsToMap, columnsToMapInfo);

        // initialized auto mapped columns in the grid
        for (String mappedColumn : _mapper.getMappedColumnNames())
            selectMappedColumn(mappedColumn, true);

        add(_mapper);
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
            BoundCheckBox includeInImport = new BoundCheckBox("id_import_" + columnMapKey, include, dirty -> _importColumnMap.put(prop, include));
            _includeWidgetMap.put(prop.getName(), includeInImport);
            
            namePanel.add(includeInImport);
            namePanel.add(new InlineHTML("&nbsp;<b>" + prop.getName() + "</b>&nbsp;"));
            _grid.getRowFormatter().setStyleName(0, "labkey-column-header");
            _grid.setWidget(0, columnIndex, namePanel);

            // save in the import map
            _importColumnMap.put(prop, include);
            _columns.add((FieldType)prop);

            // type  info
            _grid.getRowFormatter().setStyleName(1, "labkey-row");
            _grid.setWidget(1, columnIndex, new InlineHTML(PropertyType.fromName(prop.getRangeURI()).getDisplay()));

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

                _grid.getRowFormatter().setStyleName(row+2, "labkey-row");
                _grid.setHTML(row+2, columnIndex, cellData);
            }
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

    private class ColumnMapperChangeHandler implements ChangeHandler
    {
        private String _lastValue;
        private ListBox _cmp;

        public ColumnMapperChangeHandler(ListBox component)
        {
            _cmp = component;
        }

        @Override
        public void onChange(ChangeEvent event)
        {
            String value = _cmp.getSelectedValue();
            if (value != null && !NO_MAPPING_COLUMN.equals(value))
                selectMappedColumn(value, true);

            // reset the previous value
            if (_lastValue != null)
                selectMappedColumn(_lastValue, false);

            _lastValue = value;
        }
    }

    public class ColumnMapper extends VerticalPanel
    {
        List<ListBox> _columnSelectors = new ArrayList<>();
        private List<String> _columnsToMap;

        public ColumnMapper(List<InferencedColumn> inferredColumns, List<String> columnsToMap,
                            List<GWTPropertyDescriptor> columnsToMapInfo)
        {
            _columnsToMap = columnsToMap;

            VerticalPanel panel = new VerticalPanel();
            panel.add(new HTML("<br/>"));
            panel.add(new HTML("<b>Column Mapping:</b>"));
            panel.add(new InlineHTML("The list below are columns that already exist in the Domain and can be mapped with the " +
                    "inferred columns from the uploaded file.<br>Establish a mapping by selecting a column from the dropdown list to match " +
                    "the existing Domain column.<br>When the data is imported, the data from the inferred column will be added to the " +
                    "mapped Domain column.<br/><br/>"));

            int row = 0;
            FlexTable table = new FlexTable();
            table.setStyleName("labkey-data-region-legacy");

            table.getRowFormatter().setStyleName(row, "labkey-row");
            table.setWidget(row, 0, new InlineHTML("Domain Column"));
            table.setWidget(row++, 1, new InlineHTML("Column from File"));

            Map<String, GWTPropertyDescriptor> extraInfoMap = new HashMap<String, GWTPropertyDescriptor>();
            for (GWTPropertyDescriptor prop : columnsToMapInfo)
                extraInfoMap.put(prop.getName(), prop);

            for (String destinationColumn : columnsToMap)
            {
                ListBox selector = new ListBox();
                Set<String> aliases = new HashSet<String>();

                GWTPropertyDescriptor prop = extraInfoMap.get(destinationColumn);
                if (prop != null)
                    aliases = convertAliasToSet(prop.getImportAliases());

                selector.setWidth("250px");

                selector.addChangeHandler(new ColumnMapperChangeHandler(selector));
                
                selector.setName(destinationColumn);
                selector.addItem(NO_MAPPING_COLUMN);
                // add an entry in the mapping dropdown for each inferred field
                Map<String, Integer> fieldMap = new HashMap<>();
                int idx = 1;
                for (InferencedColumn column : inferredColumns)
                {
                    String name = column.getPropertyDescriptor().getName();
                    selector.addItem(name);
                    fieldMap.put(name, idx++);
                }

                // initialize the selection with the best match
                GWTPropertyDescriptor matchedCol = findClosestColumn(destinationColumn, prop, aliases, inferredColumns);
                if (matchedCol != null && fieldMap.containsKey(matchedCol.getName()))
                {
                    selector.setSelectedIndex(fieldMap.get(matchedCol.getName()));
                }
                _columnSelectors.add(selector);

                table.getRowFormatter().setStyleName(row, "labkey-row");
                table.setWidget(row, 0, new InlineHTML(selector.getName()));
                table.setWidget(row++, 1, selector);
            }

            panel.add(table);
            add(panel);
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
            for (ListBox listBox : _columnSelectors)
            {
                String value = listBox.getSelectedValue();
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
                ListBox selector = _columnSelectors.get(i);
                String fileColumn = selector.getSelectedValue();
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
