/*
 * Copyright (c) 2010-2016 LabKey Corporation
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

import gwt.client.org.labkey.study.designer.client.model.GWTCohort;
import gwt.client.org.labkey.study.designer.client.model.GWTTimepoint;
import gwt.client.org.labkey.study.designer.client.model.Schedule;
import org.labkey.api.gwt.client.util.StringUtils;
import com.google.gwt.user.client.ui.*;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.DOM;

/**
 * User: Mark Igra
 * Date: Dec 21, 2006
 * Time: 4:57:04 PM
 */
public abstract class ScheduleGrid extends EditableGrid
{
    abstract int getCategoryColumnCount();
    abstract int getCategoryRowCount();
    abstract Widget getCategoryWidget(int categoryIndex, int col);
    abstract Object getCategoryValue(int categoryIndex, int col);
    abstract Object getEventValue(int categoryIndex, GWTTimepoint tp);
    abstract Widget getEventWidget(int categoryIndex, GWTTimepoint tp);
    abstract Widget getCategoryHeader(int col);
    abstract Widget getGhostCategoryWidget(int col);
    abstract void makeGhostCategoryReal();
    abstract void deleteCategory(int index);

    protected Schedule schedule;
    protected String timelineTitle;
    protected final Designer designer;

    protected ScheduleGrid(Schedule schedule, String timelineTitle, Designer designer)
    {
        this.schedule = schedule;
        this.timelineTitle = timelineTitle;
        this.designer = designer;
    }

    public void updateAll()
    {
        super.updateAll();
        getFlexCellFormatter().setColSpan(0, 1 + getCategoryColumnCount(), schedule.getTimepoints().size() + 1);
        getFlexCellFormatter().setStyleName(0, 1 + getCategoryColumnCount(), "labkey-col-header study-vaccine-design-header");
        if (!designer.isReadOnly())
        {
            //Timepoint headers are clickable
            for (int col = 1 + getCategoryColumnCount(); col < getCellCount(1); col++)
                getFlexCellFormatter().setStyleName(1, col, "labkey-col-header-active");

            //As are the cells that indicate an event...
            for (int row = getHeaderRows(); row < getRowCount(); row++)
                for (int col = 1 + getCategoryColumnCount(); col < getCellCount(getHeaderRows()); col++)
                    getFlexCellFormatter().setStyleName(row, col, "labkey-row-active");

            // and the "add new" cell for the immunization schedule
            getFlexCellFormatter().setStyleName(getRowCount() - 1, 1, "labkey-col-header-active");
            getFlexCellFormatter().setStyleName(getRowCount() - 1, 2, "labkey-col-header-active");
        }
    }

    int getDataColumnCount()
    {
        return getCategoryColumnCount() + schedule.getTimepoints().size() + (isReadOnly() ? 0 : 1);
    }

    int getDataRowCount()
    {
        return getCategoryRowCount();
    }

    Widget getCellWidget(int row, int col)
    {
        if (col < getCategoryColumnCount())
            return getCategoryWidget(row, col);
        else if (col < getDataColumnCount() - 1)
        {
            GWTTimepoint tp = schedule.getTimepoint(col - getCategoryColumnCount());
            return getEventWidget(row, tp);
        }
        else
            return new Label("");
    }


    Object getCellValue(int row, int col)
    {
        if (col < getCategoryColumnCount())
            return getCategoryValue(row, col);
        else if (col < getDataColumnCount() - (isReadOnly() ? 0 : 1))
        {
            GWTTimepoint tp = schedule.getTimepoint(col - getCategoryColumnCount());
            return getEventValue(row, tp);
        }
        else
            return null;
    }

    Widget getGhostRowWidget(int col)
    {
        if (col < getCategoryColumnCount())
            return getGhostCategoryWidget(col);

        return new Label("");
    }

    void makeGhostRowReal()
    {
        int categoryIndex = getCategoryRowCount();
        int oldGhostRowIndex = categoryIndex + getHeaderRows();
        makeGhostCategoryReal();
        for (int i = 0; i < schedule.getTimepoints().size(); i++)
        {
            getFlexCellFormatter().setStyleName(oldGhostRowIndex,  i + getCategoryColumnCount() + 1, "labkey-row-active");
            setWidget(oldGhostRowIndex, i + getCategoryColumnCount() + 1, getEventWidget(categoryIndex, schedule.getTimepoint(i)));
        }
    }

    int getHeaderRows()
    {
        return 2;
    }

    public Widget getColumnHeader(int row, int column)
    {
        if (row == 0)
        {
            if (column < getCategoryColumnCount())
                return new Label("");
            if (column == getCategoryColumnCount())
            {
                Label label = new Label(timelineTitle);
                label.setStyleName("study-vaccine-design-header");
                return label;
            }
            else
                return null;
        }

        if (column < getCategoryColumnCount())
            return getCategoryHeader(column);
        else
           return new TimepointWidget(column - getCategoryColumnCount());
    }

    class TimepointWidget extends Label
    {
        GWTTimepoint tp;
        boolean isGhost;

        TimepointWidget(final int index)
        {
            if (index < schedule.getTimepoints().size())
            {
                 tp = schedule.getTimepoint(index);
                setText(tp.toString());
                setTitle("Click to modify or delete this timepoint.");
            }
            else
            {
                isGhost = true;
                tp = new GWTTimepoint();
                setText("Add Timepoint");
                setTitle("Click to add a new timepoint.");
            }
            setWidth("100%");

            addClickListener(new ClickListener() {
                public void onClick(Widget sender)
                {
                    if (designer.isReadOnly())
                        return;

                    DefineTimepointDialog dialog = new DefineTimepointDialog();
                    dialog.setPopupPosition(getAbsoluteLeft(), getAbsoluteTop() + getOffsetHeight());
                    dialog.show();
                }
            });
        }

        void update()
        {
            designer.setDirty(true);
            if (isGhost)
            {
                schedule.addTimepoint(tp);
                updateAll();
            }
            else
                setText(tp.toString());
        }

        public class DefineTimepointDialog extends DialogBox
        {
            private TextBox tbName = new TextBox();
            private TextBox tbCount = new TextBox();
            private ListBox lbUnit = new ListBox();

            public DefineTimepointDialog()
            {
                setText("Define Timepoint");
                DOM.setAttribute(getElement(), "id", "DefineTimepointDialog");

                Grid formGrid = new Grid();
                formGrid.resize(2, 2);
                formGrid.setText(0, 0, "Name");
                formGrid.setText(1, 0, "Timepoint");
                tbName.setText(StringUtils.trimToEmpty(tp.getName()));
                tbName.setName("timepointName"); //For easier recording
                formGrid.setWidget(0, 1, tbName);
                HorizontalPanel hpTime = new HorizontalPanel();
                hpTime.add(tbCount);
                tbCount.setText(Integer.toString(tp.getUnit().daysAsUnit(tp.getDays())));
                tbCount.setName("timepointCount");
                lbUnit.addItem("Days");
                lbUnit.addItem("Weeks");
                lbUnit.setName("timepointUnit");  //For easier recording
                if (null == tp.getUnit() || tp.getUnit() == GWTTimepoint.DAYS)
                    lbUnit.setSelectedIndex(0);
                else if (tp.getUnit() == GWTTimepoint.WEEKS)
                    lbUnit.setSelectedIndex(1);
                hpTime.add(lbUnit);

                KeyboardListener enterListener = new KeyboardListener() {
                    public void onKeyDown(Widget sender, char keyCode, int modifiers) {
                    }

                    public void onKeyPress(Widget sender, char keyCode, int modifiers) {
                        if (keyCode == '\n' || keyCode == '\r')
                            doOk();
                    }

                    public void onKeyUp(Widget sender, char keyCode, int modifiers) {
                    }
                };
                tbCount.addKeyboardListener(enterListener);
                tbName.addKeyboardListener(enterListener);
                lbUnit.addKeyboardListener(enterListener);


                formGrid.setWidget(1, 1, hpTime);

                HorizontalPanel hp = new HorizontalPanel();
                hp.setSpacing(3);
                hp.add(new Button("Cancel", new ClickListener() {
                    public void onClick(Widget sender)
                    {
                        hide();
                    }
                }));
                hp.add(new Button("OK", new ClickListener(){

                    public void onClick(Widget sender)
                    {
                        doOk();
                    }
                }
                ));
                if (!isGhost)
                {
                    hp.add(new Button("Delete Timepoint", new ClickListener()
                    {
                        public void onClick(Widget sender)
                        {
                            schedule.removeTimepoint(tp);
                            designer.setDirty(true);
                            hide();
                            updateAll();
                        }
                    }));
                }

                VerticalPanel vp = new VerticalPanel();
                vp.add(formGrid);
                vp.add(hp);
                setWidget(vp);
            }


            protected void onLoad() {
                super.onLoad();
                tbCount.setFocus(true);
                tbCount.setSelectionRange(0, tbCount.getText().length());
            }

            private void doOk()
            {
                int count = 0;
                if (null == tbCount.getText() || 0 == tbCount.getText().length())
                {
                    Window.alert("Enter a time for the timepoint");
                    tbCount.setFocus(true);
                    return;
                }
                try
                {
                    count = Integer.parseInt(tbCount.getText());
                }
                catch (Exception x)
                {
                    Window.alert("Enter a valid number for the count.");
                    tbCount.setFocus(true);
                    return;
                }

                tp.setName(StringUtils.trimToNull(tbName.getText()));
                GWTTimepoint.Unit unit = GWTTimepoint.Unit.fromString(lbUnit.getItemText(lbUnit.getSelectedIndex()));
                tp.setUnit(unit);
                tp.setDays(unit.daysPerUnit * count);

                for (GWTTimepoint existing : schedule.getTimepoints())
                {
                    if (tp != existing && tp.getUnit() == existing.getUnit() && tp.getDays() == existing.getDays())
                    {
                        String msgStart = existing.getName() != null ? "Timepoint " + existing.getName() : "A timepoint";
                        Window.alert(msgStart + " already exists at " + unit.daysAsUnit(existing.getDays()) +
                                " " + unit.name + ". Duplicate timepoints are not allowed.");
                        tbCount.setFocus(true);
                        return;
                    }
                }

                hide();
                update();
            }
        }

    }

    class GroupWidget extends Label
    {
        GWTCohort cohort;

        GroupWidget()
        {
            cohort = new GWTCohort();
            setText("Add New");
            setTitle("Click to add a new group / cohort.");
            setWidth("100%");

            addClickListener(new ClickListener() {
                public void onClick(Widget sender)
                {
                    if (designer.isReadOnly())
                        return;

                    DefineGroupDialog dialog = new DefineGroupDialog();
                    dialog.setPopupPosition(getAbsoluteLeft(), getAbsoluteTop() + getOffsetHeight());
                    dialog.show();
                }
            });
        }

        void update()
        {
            designer.setDirty(true);
            designer.getDefinition().getGroups().add(cohort);
            updateAll();
        }

        public class DefineGroupDialog extends DialogBox
        {
            private RadioButton existRadio = new RadioButton("cohortType", "Select an existing group/cohort");
            private ListBox existList = new ListBox();
            private RadioButton newRadio = new RadioButton("cohortType", "Create a new group/cohort");
            private TextBox newNameTextBox = new TextBox();


            public DefineGroupDialog()
            {
                setText("Define Group / Cohort");
                DOM.setAttribute(getElement(), "id", "DefineGroupDialog");

                newRadio.setChecked(true);
                newNameTextBox.setName("newName");
                existList.setName("existName");
                for (String existCohortName : designer.getDefinition().getCohorts())
                {
                    existList.addItem(existCohortName);
                }
                existRadio.setEnabled(existList.getItemCount() > 0);
                existList.setEnabled(false); // always initially disabled

                newRadio.addClickListener(new ClickListener()
                {
                    public void onClick(Widget sender)
                    {
                        existList.setEnabled(false);
                    }
                });
                existRadio.addClickListener(new ClickListener()
                {
                    public void onClick(Widget sender)
                    {
                        existList.setEnabled(existList.getItemCount() > 0);
                    }
                });

                Grid formGrid = new Grid();
                formGrid.resize(3, 2);
                formGrid.setWidget(0, 0, newRadio);
                formGrid.setWidget(0, 1, newNameTextBox);
                formGrid.setWidget(1, 0, existRadio);
                formGrid.setWidget(1, 1, existList);

                HorizontalPanel hp = new HorizontalPanel();
                hp.setSpacing(3);
                hp.add(new Button("Cancel", new ClickListener() {
                    public void onClick(Widget sender)
                    {
                        hide();
                    }
                }));
                hp.add(new Button("OK", new ClickListener(){

                    public void onClick(Widget sender)
                    {
                        doOk();
                    }
                }
                ));

                VerticalPanel vp = new VerticalPanel();
                vp.add(formGrid);
                vp.add(hp);
                setWidget(vp);
            }

            private void doOk()
            {
                String selectedCohortName = null;
                if (newRadio.isChecked())
                {
                    selectedCohortName = StringUtils.trimToNull(newNameTextBox.getText());
                }
                else if (existRadio.isChecked())
                {
                    selectedCohortName = existList.getItemText(existList.getSelectedIndex());
                }

                if (selectedCohortName == null || selectedCohortName.length() == 0)
                {
                    Window.alert("Please specify a group/cohort name.");
                    return;
                }

                for (GWTCohort existingGroup : designer.getDefinition().getGroups())
                {
                    if (existingGroup.getName().equalsIgnoreCase(selectedCohortName))
                    {
                        Window.alert("A group/cohort already exists with the following name: " + selectedCohortName);
                        return;
                    }
                }

                cohort.setName(selectedCohortName);

                hide();
                update();
            }
        }

    }

    void deleteRow(int dataRow)
    {
        deleteCategory(dataRow);
        updateAll();
    }
}
