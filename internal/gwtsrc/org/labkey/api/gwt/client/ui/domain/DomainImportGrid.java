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
package org.labkey.api.gwt.client.ui.domain;

import com.google.gwt.user.client.ui.Grid;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.ListBox;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;

import java.util.ArrayList;
import java.util.List;

/**
 * User: jgarms
 * Date: Nov 5, 2008
 */
public class DomainImportGrid extends Grid
{
    List<InferencedColumn> columns;
    List<TypeListBox> typePickers;

    public DomainImportGrid()
    {
        super(1,0);
        setStyleName("labkey-data-region labkey-show-borders");

        RowFormatter rowFormatter = new RowFormatter();
        rowFormatter.setStyleName(0, "labkey-row-header");

        setRowFormatter(rowFormatter);
    }

    public void setColumns(List<InferencedColumn> columns)
    {
        this.columns = columns;
        typePickers = new ArrayList<TypeListBox>(columns.size());

        resizeColumns(columns.size());
        int numDataRows = columns.get(0).getData().size();
        resizeRows(numDataRows + 2); // Need a row for the name and a row for the type

        int numCols = columns.size();
        for(int columnIndex=0; columnIndex<columns.size(); columnIndex++)
        {
            InferencedColumn column = columns.get(columnIndex);
            GWTPropertyDescriptor prop = column.getPropertyDescriptor();

            setWidget(0, columnIndex, new HTML("<b>" + prop.getName() + "</b>"));

            TypeListBox typePicker = new TypeListBox(Type.getTypeByXsdType(prop.getRangeURI()));
            setWidget(1, columnIndex, typePicker);
            typePickers.add(typePicker);

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
    }

    private class TypeListBox extends ListBox
    {
        public TypeListBox(Type type)
        {
            super();
            addItem(type);
            switch(type)
            {
                case StringType:
                    break;
                case IntType:
                    addItem(Type.DoubleType);
                    addItem(Type.StringType);
                    break;
                case DoubleType:
                    addItem(Type.StringType);
                    break;
                case DateTimeType:
                    addItem(Type.StringType);
                    break;
                case BooleanType:
                    addItem(Type.StringType);
                    break;
            }
        }

        private void addItem(Type type)
        {
            addItem(type.getLabel());
        }
    }

    public enum Type
    {
        StringType("Text (String)", "xsd:string"),
        IntType("Integer", "xsd:int"),
        DoubleType("Number (Double)", "xsd:double"),
        DateTimeType("DateTime", "xsd:dateTime"),
        BooleanType("Boolean", "xsd:boolean");

        private String label;
        private String xsd;

        private Type(String label, String xsd)
        {
            this.label = label;
            this.xsd = xsd;
        }

        public String getLabel()
        {
            return label;
        }

        public String getXsdType()
        {
            return xsd;
        }

        public static Type getTypeByLabel(String label)
        {
            for (Type type : values())
            {
                if (type.getLabel().equals(label))
                    return type;
            }
            return null;
        }

        public static Type getTypeByXsdType(String xsd)
        {
            for (Type type : values())
            {
                if (type.getXsdType().equals(xsd))
                    return type;
            }
            return null;
        }

    }
    
}
