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

import com.google.gwt.user.client.ui.*;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.DOM;

import java.util.List;

import gwt.client.org.labkey.study.designer.client.model.*;
import org.labkey.api.gwt.client.util.StringUtils;

/**
 * User: Mark Igra
 * Date: Dec 18, 2006
 * Time: 9:09:13 AM
 */
public class ImmunizationPanel extends Composite
{
    GWTStudyDefinition studyDef;
    GWTImmunizationSchedule immunizationSchedule;
    ImmunizationGrid immunizationGrid;
    Designer designer;

    public ImmunizationPanel(Designer parent)
    {
        this.designer = parent;
        this.studyDef = designer.getDefinition();
        this.immunizationSchedule = studyDef.getImmunizationSchedule();
        immunizationGrid = new ImmunizationGrid(parent);
        immunizationGrid.updateAll();
        VerticalPanel vpanel = new VerticalPanel();
        if (designer.isReadOnly())
        {
            if (immunizationGrid.getCategoryRowCount() == 0 && immunizationSchedule.getTimepoints().size() == 0)
            {
                String html = "No immunizations have been scheduled.";
                if (designer.canEdit)
                    html += "<br>Click the edit button to add immunizations.";
                vpanel.add(new HTML(html));
            }
            else
            {
                vpanel.add(new HTML("This section shows the immunization schedule. Each immunization may consist of several adjuvants and immunizations."));
                vpanel.add(immunizationGrid);
            }
        }
        else
        {
            String html = "Enter the immunization schedule below." +
                    "<ul><li>Enter each group/cohort on a line by clicking the 'Add New' link" +
                    "<li>Enter the number of subjects in the cohort in the count column" +
                    "<li>Use the 'Add Timepoint' link to add new timepoints" +
                    "<li>Click on the cells underneath a timepoint to define adjuvants and immunogens used at that timepoint" +
                    "</ul>";
            vpanel.add(new HTML(html));
            vpanel.add(immunizationGrid);
        }
        initWidget(vpanel);
        DOM.setAttribute(getElement(), "id", "ImmunizationPanel");
    }

    public boolean validate()
    {
        return true;
    }

    public void updateAll()
    {
        immunizationGrid.updateAll();
    }

    private class ImmunizationGrid extends ScheduleGrid
    {
        protected ImmunizationGrid(Designer designer)
        {
            super(immunizationSchedule, "Immunization Schedule", designer);
            DOM.setAttribute(getElement(), "id", "ImmunizationGrid");
            setReadOnly(designer.isReadOnly());
        }

        int getCategoryColumnCount()
        {
            return 2;
        }

        int getCategoryRowCount()
        {
            return studyDef.getGroups().size();
        }

        Widget getCategoryHeader(int col)
        {
            if (col == 0)
                return new Label("Group / Cohort");
            else
                return new Label("Count");
        }

        Widget getCategoryWidget(int categoryIndex, int col)
        {
            return getCategoryWidget(studyDef.getGroups().get(categoryIndex), col);
        }

        Object getCategoryValue(int categoryIndex, int col)
        {
            GWTCohort group = studyDef.getGroups().get(categoryIndex);
            if (col == 0)
                return group.getName();
            else
            {
                if (group.getCount() > 0)
                    return new Integer(group.getCount());
                else
                    return null;
            }
        }


        Object getEventValue(int categoryIndex, GWTTimepoint tp)
        {
            GWTCohort cohort = studyDef.getGroups().get(categoryIndex);
            GWTImmunization immunization = immunizationSchedule.getImmunization(cohort, tp);
            if (null == immunization)
                return null;
            else
                return immunization;
        }

        Widget getGhostCategoryWidget(int col)
        {
            if (col == 0)
                return new GroupWidget();
            else
                return null;
        }

        private Widget getCategoryWidget(final GWTCohort group, int col)
        {
            final TextBox tb = new TextBox();
            if (col == 0)
            {
                tb.setText(StringUtils.trimToEmpty(group.getName()));
                tb.addChangeListener(new ChangeListener() {
                    public void onChange(Widget sender) {
                        // in the event of a group rename, add the old cohort name to the list of groups to be deleted (if they exist)
                        studyDef.addGroupToDelete(group.getName());
                        group.setCohortId(null);

                        group.setName(tb.getText());
                        designer.setDirty(true);
                    }
                });
            }
            else
            {
                if (group.getCount() > 0)
                    tb.setText(Integer.toString(group.getCount()));
                tb.addChangeListener(new ChangeListener() {
                    public void onChange(Widget sender) {
                        int count;
                        try
                        {
                            count = Integer.parseInt(tb.getText());
                            designer.setDirty(true);
                        }
                        catch (NumberFormatException e)
                        {
                            Window.alert("Please enter a number");
                            tb.setText(Integer.toString(group.getCount()));
                            tb.setFocus(true);
                            return;
                        }
                        group.setCount(count);
                    }
                });
            }
            return tb;
        }

        void makeGhostCategoryReal()
        {
            // noop
        }


        void deleteCategory(final int index)
        {
            GWTCohort cohort = studyDef.getGroups().get(index);
            studyDef.addGroupToDelete(cohort.getName());

            studyDef.getGroups().remove(index);
            designer.setDirty(true);
        }

        Widget getEventWidget(int categoryIndex, GWTTimepoint tp)
        {
            GWTCohort cohort = studyDef.getGroups().get(categoryIndex);
            return new ImmunizationWidget(cohort, tp);
        }

        @Override
        public String getRowNoun()
        {
            return "group";
        }

        public class ImmunizationWidget extends FocusPanel implements ClickListener, KeyboardListener
        {
            private GWTCohort group;
            private GWTTimepoint timepoint;
            private Label l = new Label();
            public ImmunizationWidget(GWTCohort group, GWTTimepoint timepoint)
            {
                this.group = group;
                this.timepoint = timepoint;
                this.addClickListener(this);
                this.addKeyboardListener(this);
                this.setWidth("100%");
                setTitle("Click to define immunization");
                add(l);
                updateText();
            }

            public void update(GWTImmunization immunization)
            {
                if (null == immunization)
                    immunizationSchedule.removeImmunization(group, timepoint);
                else
                    immunizationSchedule.setImmunization(group, timepoint, immunization);

                designer.setDirty(true);                                                
                updateText();
            }

            private void updateText()
            {
                GWTImmunization immunization = immunizationSchedule.getImmunization(group, timepoint);
                if (null == immunization)
                    l.setText("(none)");
                else
                    l.setText(immunization.toString());
            }


            public void onClick(Widget sender)
            {
                showPopup();
            }

            private void showPopup()
            {
                GWTImmunization immunization = immunizationSchedule.getImmunization(group, timepoint);
                if (null == immunization)
                    immunization = new GWTImmunization();
                DefineImmunizationPopup popup = new DefineImmunizationPopup(immunization, ImmunizationWidget.this);
                popup.setPopupPosition(this.getAbsoluteLeft(), this.getAbsoluteTop() + this.getOffsetHeight());
                popup.show();
            }

            public void onKeyDown(Widget sender, char keyCode, int modifiers)
            {
                //To change body of implemented methods use File | Settings | File Templates.
            }

            public void onKeyPress(Widget sender, char keyCode, int modifiers)
            {
                if (keyCode == ' ' || keyCode == 13 || keyCode == 10)
                    showPopup();
            }

            public void onKeyUp(Widget sender, char keyCode, int modifiers)
            {
                //To change body of implemented methods use File | Settings | File Templates.
            }
        }
    }

    public class DefineImmunizationPopup extends DialogBox
    {
        private GWTImmunization immunization;
        private ImmunizationGrid.ImmunizationWidget owner;
        public DefineImmunizationPopup(GWTImmunization immunization, ImmunizationGrid.ImmunizationWidget owner)
        {
            this.immunization = immunization;
            this.owner = owner;
            FlexTable ft = new FlexTable();
            ft.setHTML(0, 0, "<b>Immunogens</b>");
            ft.setHTML(0, 1, "<b>Adjuvants</b>");

            for (int i = 0; i < studyDef.getImmunogens().size(); i++)
            {
                GWTImmunogen immunogen = studyDef.getImmunogens().get(i);
                CheckBox cb = new VaccineComponentCheckbox(immunization.immunogens, immunogen);
                cb.setChecked(immunization.immunogens.contains(immunogen));
                ft.setWidget(i + 1, 0, cb);
            }

            for (int i = 0; i < studyDef.getAdjuvants().size(); i++)
            {
                GWTAdjuvant adjuvant = studyDef.getAdjuvants().get(i);
                CheckBox cb = new VaccineComponentCheckbox(immunization.adjuvants, adjuvant);
                cb.setChecked(immunization.adjuvants.contains(adjuvant));
                ft.setWidget(i + 1, 1, cb);
            }

            int rowCount = ft.getRowCount();
            ft.insertRow(rowCount);
            ft.getFlexCellFormatter().setColSpan(rowCount, 0, 2);
            Button okButton = new Button("Done", new ClickListener() {
                public void onClick(Widget sender)
                {
                    DefineImmunizationPopup.this.hide();
                    designer.setDirty(true);
                    DefineImmunizationPopup.this.owner.setFocus(true);
                }
            });
            ft.setWidget(rowCount, 0, okButton);

            setText("Define Immunization");
            setWidget(ft);
        }

        private class VaccineComponentCheckbox extends CheckBox implements ClickListener
        {
            private List components;
            private VaccineComponent vc;
            VaccineComponentCheckbox(List/*<VaccineComponent>*/ components, VaccineComponent vc)
            {
                this.components = components;
                this.vc = vc;
                this.setChecked(components.contains(vc));
                this.addClickListener(this);
                this.setText(vc.getName());
            }


            public void onClick(Widget sender)
            {
                if (isChecked())
                {
                    if (!components.contains(vc))
                        components.add(vc);
                }
                else
                {
                    if (components.contains(vc))
                        components.remove(vc);
                }

                owner.update(immunization);
            }
        }
    }

}
