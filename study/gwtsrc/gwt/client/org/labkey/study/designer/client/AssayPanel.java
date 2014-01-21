/*
 * Copyright (c) 2010-2014 LabKey Corporation
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

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.*;
import org.labkey.api.gwt.client.util.StringUtils;
import gwt.client.org.labkey.study.designer.client.model.*;

import java.util.List;

/**
 * User: Mark Igra
 * Date: Dec 20, 2006
 * Time: 4:20:46 PM
 */
public class AssayPanel extends Composite
{
    GWTStudyDefinition studyDef;
    GWTAssaySchedule assaySchedule;
    Designer designer;
    AssayGrid eg;

    public AssayPanel(Designer parent)
    {
        this.designer = parent;
        this.studyDef = parent.getDefinition();
        this.assaySchedule = studyDef.getAssaySchedule();

        eg = new AssayGrid(parent);
        VerticalPanel vpanel = new VerticalPanel();

        if (!designer.isReadOnly())
        {
            final TextArea tbDescription = new TextArea();
            tbDescription.setWidth("100%");
            tbDescription.setVisibleLines(5);
            tbDescription.setName("assayPlan"); //For easier testing
            ActivatingLabel descriptionEditor = new ActivatingLabel(tbDescription, "Click to type assay plan here");
            if (null != assaySchedule.getDescription())
                descriptionEditor.setText(assaySchedule.getDescription());
            descriptionEditor.addChangeListener(new ChangeListener() {
                public void onChange(Widget sender) {
                    assaySchedule.setDescription(((HasText) sender).getText());
                    designer.setDirty(true);
                }
            });
            vpanel.add(descriptionEditor);
            vpanel.add(eg);
        }
        else
        {
            if (eg.getCategoryRowCount() == 0 && assaySchedule.getTimepoints().size() == 0)
            {
                String html = "No assays have been scheduled.";
                if (designer.canEdit)
                    html += "<br>Click the edit button to add assays.";
                vpanel.add(new HTML(html));
            }
            else
            {
                HTML description = new HTML(StringUtils.filter(assaySchedule.getDescription(), true));
                vpanel.add(description);
                vpanel.add(eg);
            }
        }

        eg.updateAll();
        initWidget(vpanel);
    }

    public void updateAll()
    {
        eg.updateAll();
    }
    
    public boolean validate()
    {
        return true;
    }

    class AssayGrid extends ScheduleGrid
    {
        private GWTAssayDefinition ghostAssayDefinition = null;

        AssayGrid(Designer designer)
        {
            super(assaySchedule, "Assay Schedule", designer);
            DOM.setAttribute(getElement(), "id", "AssayGrid");
            setReadOnly(designer.isReadOnly());
        }

        int getCategoryColumnCount()
        {
            return 2; //Assay, Lab
        }

        int getCategoryRowCount()
        {
            return assaySchedule.getAssays().size();
        }


        Widget getCategoryHeader(int col)
        {
            if (col == 0)
                return new Label("Assay");
            else
                return new Label("Lab");
        }

        Widget getCategoryWidget(int categoryIndex, int col)
        {
            if (categoryIndex >= assaySchedule.getAssays().size())
                return null;
            
            if (col == 0)
            {
                final GWTAssayDefinition ad = assaySchedule.getAssay(categoryIndex);
                final Label lb = new Label(ad.getAssayName());
                return lb;
            }
            else
                return getLabPicker(assaySchedule.getAssay(categoryIndex));
        }

        Object getCategoryValue(int categoryIndex, int col)
        {
            if (categoryIndex >= assaySchedule.getAssays().size())
                return null;

            if (col == 0)
                return assaySchedule.getAssay(categoryIndex);
            else
                return assaySchedule.getAssay(categoryIndex).getLab();
        }

        Object getEventValue(int categoryIndex, GWTTimepoint tp)
        {
            if (categoryIndex >= assaySchedule.getAssays().size())
                return null;

            GWTAssayDefinition assayDefinition = assaySchedule.getAssay(categoryIndex);
            return assaySchedule.getAssayPerformed(assayDefinition, tp);
        }

        Widget getGhostCategoryWidget(int col)
        {
            if (col == 0)
            {
                return new AssayListBox();
            }
            return new Label("");
        }

        private class AssayListBox extends ListBox implements ChangeListener
        {
            private ChangeListenerCollection externalListeners = new ChangeListenerCollection();
            AssayListBox()
            {
                //Handle changes locally and may or may not fire external listeners depending
                //on user confirmation
                super.addChangeListener(this);
                addItem("<Select Assay>");
                for (int i = 0; i < studyDef.getAssays().size(); i++)
                {
                    String assayName = studyDef.getAssays().get(i);
                    addItem(assayName);
                }
            }

            public void addChangeListener(ChangeListener listener)
            {
                externalListeners.add(listener);
            }

            public void removeChangeListener(ChangeListener listener)
            {
                externalListeners.remove(listener);    //To change body of overridden methods use File | Settings | File Templates.
            }

            public void onChange(Widget sender)
            {
                String assayName = studyDef.getAssays().get(this.getSelectedIndex() - 1);
                boolean exists = false;
                for (GWTAssayDefinition assayDefinition : assaySchedule.getAssays())
                {
                    if (assayName.equals(assayDefinition.getAssayName()))
                    {
                        exists = true;
                        break;
                    }
                }

                if (exists)
                {
                    setSelectedIndex(0);
                    Window.alert(assayName + " is already listed in this assay plan.");
                }
                else
                {
                    ghostAssayDefinition = new GWTAssayDefinition(assayName, null);
                    designer.setDirty(true);
                    externalListeners.fireChange(this);
                }

            }
        }
        
        void makeGhostCategoryReal()
        {
            int assayIndex = assaySchedule.getAssays().size();
            assaySchedule.addAssay(ghostAssayDefinition);
            setWidget(getHeaderRows() + assayIndex, 1, getCategoryWidget(assayIndex, 0));
            setWidget(getHeaderRows() + assayIndex, 2, getCategoryWidget(assayIndex, 1));
        }

        @Override
        public String getRowNoun()
        {
            return "assay";
        }

        void deleteCategory(int index)
        {
            assaySchedule.getAssays().remove(index);
            designer.setDirty(true);
        }

        Widget getEventWidget(int categoryIndex, GWTTimepoint tp)
        {
            if (categoryIndex >= assaySchedule.getAssays().size())
                return null;

            return new AssayCheckBox(assaySchedule.getAssay(categoryIndex), tp);
        }

        private ListBox getLabPicker(final GWTAssayDefinition assayDefinition)
        {

            List<String> labs = studyDef.getLabs();
            String selectedLab = assayDefinition.getLab();

            ListBox lb = new ListBox();
            //If selection is not valid, just add it to the top of the picker to reflect current state
            if (null == selectedLab || null == labs || !labs.contains(selectedLab))
            {
                lb.addItem(selectedLab == null ? "" : selectedLab);
                lb.setItemSelected(0, true);
            }
            if (null != labs)
                for (int i = 0; i < labs.size(); i++)
                {
                    lb.addItem(labs.get(i));
                    if (labs.get(i).equals(selectedLab))
                        lb.setItemSelected(i, true);
                }
            lb.addChangeListener(new ChangeListener() {
                public void onChange(Widget sender) {
                    assayDefinition.setLab(((ListBox) sender).getItemText(((ListBox) sender).getSelectedIndex()));
                    designer.setDirty(true);
                }
            });
            return lb;

        }

        class AssayCheckBox extends FlowPanel implements ClickListener
        {
            final GWTAssayDefinition assayDefinition;
            final GWTTimepoint tp;
            CheckBox checkbox = new CheckBox();
            Label label = new Label();

            AssayCheckBox(GWTAssayDefinition assayDefinition, GWTTimepoint tp)
            {
                this.assayDefinition = assayDefinition;
                this.tp = tp;

                checkbox.setChecked (assaySchedule.isAssayPerformed(assayDefinition, tp));
                updateLabel();
                checkbox.addClickHandler(new ClickHandler() {
                    public void onClick(ClickEvent e)
                    {
                        assaySchedule.setAssayPerformed(AssayCheckBox.this.assayDefinition, AssayCheckBox.this.tp, checkbox.isChecked());
                        designer.setDirty(true);
                        updateLabel();
                    }
                });
                label.addClickHandler(new ClickHandler() {
                    public void onClick(ClickEvent e)
                    {
                        SampleMeasureDialog dlg = new SampleMeasureDialog();
                        dlg.setPopupPosition(label.getAbsoluteLeft(), label.getAbsoluteTop() + label.getOffsetHeight());
                        dlg.show();
                    }
                });
                this.add(checkbox);
                this.add(label);
            }

            public GWTAssayNote getAssayNote()
            {
                return assaySchedule.getAssayPerformed(assayDefinition, tp);
            }

            public void onClick(Widget sender)
            {
                assaySchedule.setAssayPerformed(assayDefinition, tp, checkbox.isChecked());
                designer.setDirty(true);
                updateLabel();
            }

            void updateLabel()
            {
                if (checkbox.isChecked() && getAssayNote() != null)
                    label.setText(getAssayNote().getSampleMeasure() != null && !getAssayNote().getSampleMeasure().isEmpty() ? getAssayNote().getSampleMeasure().toString() : "Add Measure");
            }

            class SampleMeasureDialog extends DialogBox
            {
                SampleMeasurePanel editor;
                public SampleMeasureDialog()
                {
                    this.setText("Edit Measurement");
                    VerticalPanel vp = new VerticalPanel();

                    if (getAssayNote() != null && getAssayNote().getSampleMeasure() != null)
                        editor = new SampleMeasurePanel(new GWTSampleMeasure(getAssayNote().getSampleMeasure()), studyDef);
                    else
                        editor = new SampleMeasurePanel(new GWTSampleMeasure(), studyDef);

                    vp.add(editor);
                    HorizontalPanel hp = new HorizontalPanel();
                    hp.add(new Button("OK", new ClickListener()
                    {
                        public void onClick(Widget sender)
                        {
                            getAssayNote().setSampleMeasure(editor.getValue());
                            designer.setDirty(true);
                            updateLabel();
                            SampleMeasureDialog.this.hide();
                        }
                    }));
                    hp.add(new Button("Cancel", new ClickListener()
                    {
                        public void onClick(Widget sender)
                        {
                              SampleMeasureDialog.this.hide();
                        }
                    }));
                    vp.add(hp);
                    this.setWidget(vp);
                }
            }
        }

    }
}

