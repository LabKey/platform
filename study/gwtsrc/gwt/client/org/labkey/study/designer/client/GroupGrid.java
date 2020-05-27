/*
 * Copyright (c) 2010-2019 LabKey Corporation
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

package gwt.client.org.labkey.study.designer.client;

import com.google.gwt.user.client.ui.*;
import com.google.gwt.user.client.Window;

import java.util.List;

import gwt.client.org.labkey.study.designer.client.model.GWTCohort;
import gwt.client.org.labkey.study.designer.client.model.GWTStudyDefinition;
import org.labkey.api.gwt.client.util.StringUtils;

/**
 * User: Mark Igra
 * Date: Dec 14, 2006
 * Time: 2:53:56 PM
 */
public class GroupGrid extends EditableGrid
{
    private List/*<Cohort>*/ groups;
    private GWTStudyDefinition studyDef;
    GWTCohort ghostCohort;

    public GroupGrid(GWTStudyDefinition studyDefinition)
    {
        this.groups = studyDefinition.getGroups();
        this.studyDef = studyDefinition;
        ghostCohort = new GWTCohort();
    }
    @Override
    public int getDataColumnCount()
    {
        return 2;
    }

    @Override
    public int getDataRowCount()
    {
        return groups.size();
    }

    Widget[] columnNames = new Widget[] {new Label("Name"), new Label("Count") };
    @Override
    public Widget getColumnHeader(int row, int column)
    {
        if (column >= columnNames.length)
            return new HTML("No such column");
        return columnNames[column];
    }

    @Override
    public Widget getCellWidget(int row, int column)
    {
        if (row < 0 || row > groups.size())
            return new Label("No such group: " + row);

        return getWidget((GWTCohort) groups.get(row), column);
    }

    @Override
    Object getCellValue(int row, int col)
    {
        GWTCohort group = (GWTCohort) groups.get(row);
        if (null == group)
            return null;

        if (col == 0)
            return group.getName();
        else
            return Integer.valueOf(group.getCount());
    }

    @Override
    public Widget getGhostRowWidget(int col)
    {
        return getWidget(ghostCohort, col);
    }

    private Widget getWidget(GWTCohort group, int column)
    {
        if (column == 0)
            return getNameWidget(group);
        if (column == 1)
            return getCountWidget(group);

        return new Label("No such column" + column);
    }

    @Override
    public int getHeaderRows()
    {
        return 1;
    }

    @Override
    public void makeGhostRowReal()
    {
        groups.add(ghostCohort);
        ghostCohort = new GWTCohort();
        studyDef.fireChangeEvents();
    }

    @Override
    void deleteRow(int dataRow)
    {
        groups.remove(dataRow);
        updateAll();
    }


    private TextBox getNameWidget(final GWTCohort cohort)
    {
        TextBox tb = new TextBox();
        tb.setText(StringUtils.trimToEmpty(cohort.getName()));
        tb.addChangeListener(new ChangeListener(){
            @Override
            public void onChange(Widget sender)
            {
                cohort.setName(((TextBox) sender).getText());
            }
        });

        return tb;
    }

    private TextBox getCountWidget(final GWTCohort cohort)
    {
        final TextBox tb = new TextBox();
        tb.setWidth("4em");
        tb.setText(String.valueOf(cohort.getCount()));
        tb.addChangeListener(new ChangeListener(){
            @Override
            public void onChange(Widget sender)
            {
                String countStr = tb.getText();
                int count = 0;
                try
                {
                    count = Integer.parseInt(countStr);
                    cohort.setCount(count);
                }
                catch (NumberFormatException e)
                {
                    Window.alert("Please enter a number");
                    tb.setFocus(true);
                    return;
                }
                tb.setText(String.valueOf(count));
            }
        });

        return tb;
    }


}
