/*
 * Copyright (c) 2010-2013 LabKey Corporation
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
import com.google.gwt.user.client.Command;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.DeferredCommand;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.*;
import org.labkey.api.gwt.client.ui.WindowUtil;
import org.labkey.api.gwt.client.util.StringUtils;
import gwt.client.org.labkey.study.designer.client.model.*;

import java.util.Arrays;

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

            HorizontalPanel linkPanel = new HorizontalPanel();
            linkPanel.setSpacing(8);
            Anchor editLink = new Anchor("Edit Assay List");
            editLink.addClickHandler(new ClickHandler()
            {
                public void onClick(ClickEvent e)
                {
                    final AssayDefinitionDialog dlg = new AssayDefinitionDialog(AssayPanel.this, studyDef.getAssays());
                    dlg.setPopupPosition(eg.getAbsoluteLeft(), eg.getAbsoluteTop());
                    dlg.show();
                    //For some reason GWT scrolls to top async after showing dialog. So scroll back
                    DeferredCommand.addCommand(new Command(){

                        public void execute()
                        {
                            WindowUtil.scrollIntoView(dlg);
                        }
                    });
                }
            });
            linkPanel.add(editLink);

            Anchor editSamplesLink = new Anchor("Edit Sample Types");
            editSamplesLink.addClickHandler(new ClickHandler()
            {
                public void onClick(ClickEvent e)
                {
                    final SampleTypeDefinitionDialog dlg = new SampleTypeDefinitionDialog(AssayPanel.this, studyDef.getSampleTypes());
                    dlg.setPopupPosition(eg.getAbsoluteLeft(), eg.getAbsoluteTop());
                    dlg.show();
                    //For some reason GWT scrolls to top async after showing dialog. So scroll back
                    DeferredCommand.addCommand(new Command(){

                        public void execute()
                        {
                            WindowUtil.scrollIntoView(dlg);
                        }
                    });
                    dlg.show();
                    dlg.setPopupPosition(eg.getAbsoluteLeft(), eg.getAbsoluteTop());
                    WindowUtil.scrollIntoView(dlg);
                }
            });
            linkPanel.add(editSamplesLink);

            vpanel.add(linkPanel);
        }
        else
        {
            HTML description = new HTML(StringUtils.filter(assaySchedule.getDescription(), true));
            vpanel.add(description);
            vpanel.add(eg);
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
                final Label lb = new Label(ad.getName());
                if (null != ad.getDescription())
                    lb.setTitle(ad.getDescription());
                /*
                lb.addClickListener(new ClickListener()
                {
                    public void onClick(Widget sender)
                    {
                        AssayDefinitionDialog dialog = new AssayDefinitionDialog(ad, true);
                        dialog.setPopupPosition(lb.getAbsoluteLeft(), lb.getAbsoluteTop() + lb.getOffsetHeight());
                        dialog.addChangeListener(new ChangeListener()
                        {
                            public void onChange(Widget sender)
                            {
                                updateAll();
                                designer.setDirty(true);
                            }
                        });
                        dialog.show();
                        WindowUtil.scrollIntoView(dialog);
                    }
                });
                */
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
                return assaySchedule.getAssay(categoryIndex).getDefaultLab();
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
                    GWTAssayDefinition ad = (GWTAssayDefinition) studyDef.getAssays().get(i);
                    addItem(ad.getName());
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
                GWTAssayDefinition assayDef =  (GWTAssayDefinition) studyDef.getAssays().get(this.getSelectedIndex() - 1);
                if (assaySchedule.getAssays().contains(assayDef))
                {
                    //Not necessarily adding here..
                    setSelectedIndex(0);
                    if (Window.confirm(assayDef.getName() + " is already listed in this assay plan. Would you like to add a new variant of that assay?"))
                    {
                        //Assay Names might be of the form
                        //Neutralizing Antibodies Panel 1 or something. So we get rid of panel number
                        //and add one on as needed.
                        String baseName = assayDef.getName();
                        if (baseName.indexOf("Panel") != -1)
                            baseName = baseName.substring(0, baseName.indexOf("Panel") + 5);
                        else
                            baseName = baseName + " Panel";
                        String name;
                        for (int panelIndex = 2; ; panelIndex++)
                        {
                            name = baseName + " " + panelIndex;
                            if (null == assaySchedule.findAssayByName(name))
                                break;
                        }

                        final GWTAssayDefinition newAssay = new GWTAssayDefinition(assayDef);
                        newAssay.setName(name);
                        newAssay.setLocked(false);

                        AssayDefinitionDialog dialog = new AssayDefinitionDialog(AssayPanel.this, newAssay);
                        dialog.setPopupPosition(eg.getAbsoluteLeft(), eg.getAbsoluteTop());
                        dialog.show();
                        WindowUtil.scrollIntoView(dialog);
                    }
                }
                else
                {
                    ghostAssayDefinition = assayDef;
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

            String [] labs = assayDefinition.getLabs();
            String defaultLab = assayDefinition.getDefaultLab();

            ListBox lb = new ListBox();
            //If selection is not valid, just add it to the top of the picker to reflect current state
            if (null == defaultLab || null == labs || !Arrays.asList(labs).contains(defaultLab))
            {
                lb.addItem(defaultLab == null ? "" : defaultLab);
                lb.setItemSelected(0, true);
            }
            if (null != labs)
                for (int i = 0; i < labs.length; i++)
                {
                    lb.addItem(labs[i]);
                    if (labs[i].equals(defaultLab))
                        lb.setItemSelected(i, true);
                }
            lb.addChangeListener(new ChangeListener() {
                public void onChange(Widget sender) {
                    assayDefinition.setDefaultLab(((ListBox) sender).getItemText(((ListBox) sender).getSelectedIndex()));
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
                label.setText(checkbox.isChecked() ? getAssayNote().getSampleMeasure().toString() : "");
            }

            class SampleMeasureDialog extends DialogBox
            {
                SampleMeasurePanel editor;
                public SampleMeasureDialog()
                {
                    this.setText("Edit Measurement");
                    VerticalPanel vp = new VerticalPanel();
                    editor = new SampleMeasurePanel(new GWTSampleMeasure(getAssayNote().getSampleMeasure()), studyDef);
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

