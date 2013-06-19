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

import com.google.gwt.event.dom.client.ChangeEvent;
import com.google.gwt.event.dom.client.ChangeHandler;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.ui.*;
import com.google.gwt.user.client.Window;
import gwt.client.org.labkey.study.designer.client.model.*;
import org.labkey.api.gwt.client.util.StringUtils;

import java.util.List;

/**
 * User: Mark Igra
 * Date: Jan 28, 2007
 * Time: 10:56:47 AM
 */
public class AssayDefinitionDialog extends DialogBox
{
    List<GWTAssayDefinition> assays;
    ListBox listBox;
    EditPanel editPanel;
    int itemSelected;
    AssayPanel parent;

    /**
     * Assay Definition Dialog in the form of "Create new"
     */
    public AssayDefinitionDialog(AssayPanel parent, final GWTAssayDefinition assayDefinition)
    {
        this.parent = parent;

        VerticalPanel vpanel = new VerticalPanel();

        editPanel = new EditPanel();
        editPanel.setAssay(assayDefinition);

        vpanel.add(editPanel);
        HorizontalPanel buttonPanel = new HorizontalPanel();
        buttonPanel.setSpacing(3);
        ClickHandler hideListener = new ClickHandler() {
            public void onClick(ClickEvent e)
            {
                AssayDefinitionDialog.this.hide();
            }
        };
        buttonPanel.add(new Button("Cancel", hideListener));
        ClickHandler okListener =  new ClickHandler() {
                    public void onClick(ClickEvent e)
                    {
                        AssayDefinitionDialog.this.parent.studyDef.getAssays().add(assayDefinition);
                        AssayDefinitionDialog.this.parent.assaySchedule.addAssay(assayDefinition);
                        AssayDefinitionDialog.this.parent.eg.updateAll();
                        AssayDefinitionDialog.this.parent.designer.setDirty(true);
                        AssayDefinitionDialog.this.hide();
                     }
            };
        Button okButton = new Button("OK", okListener);
        okButton.addClickHandler(hideListener);
        buttonPanel.add(okButton);
        
        vpanel.add(buttonPanel);
        setText("Define Assay");
        setWidget(vpanel);
    }

    public AssayDefinitionDialog(final AssayPanel parent, List<GWTAssayDefinition> assayDefinitions)
    {
        this.parent = parent;
        
        FlexTable mainTable = new FlexTable();
        listBox = new ListBox();
        listBox.setVisibleItemCount(12);
        this.assays = assayDefinitions;
        for (GWTAssayDefinition def : assayDefinitions)
        {
            listBox.addItem(def.getName());
        }
        listBox.addChangeHandler(new ChangeHandler(){
            public void onChange(ChangeEvent e)
            {
                selectItem(listBox.getSelectedIndex(), false);
            }
        });
        listBox.setWidth("250");
        listBox.setName("assayList");
        mainTable.setWidget(0, 0, listBox);
        mainTable.getCellFormatter().setVerticalAlignment(0, 0, HasVerticalAlignment.ALIGN_TOP);
        editPanel = new EditPanel();
        if (assayDefinitions.size() > 0)
            selectItem(0, true);
        mainTable.setWidget(0, 1, editPanel);

        HorizontalPanel buttonPanel = new HorizontalPanel();
        buttonPanel.setSpacing(3);
        buttonPanel.add(new Button("Add", new ClickHandler()
        {
            public void onClick(ClickEvent e)
            {
                String name = "New Assay";
                int index = 1;
                while (null != findByName(name + index))
                    index++;
                name = name + index;

                GWTAssayDefinition newAssay = new GWTAssayDefinition(name, null, new GWTSampleMeasure(2, GWTSampleMeasure.Unit.ML, GWTSampleType.PLASMA));
                assays.add(newAssay);
                listBox.addItem(name);
                parent.designer.setDirty(true);
                selectItem(assays.size() - 1, true);
            }
        }));
        buttonPanel.add(new Button("Delete", new ClickHandler() {

            public void onClick(ClickEvent e)
            {
                if (itemSelected < assays.size())
                {
                    GWTAssayDefinition assay = assays.get(itemSelected);
                    if (assay.isLocked())
                    {
                        Window.alert("This is a standard assay and cannot be removed from the list.");
                        return;
                    }
                    
                    assays.remove(itemSelected);
                    listBox.removeItem(itemSelected);
                    if (assays.size() > 0)
                        selectItem(Math.max(itemSelected - 1, 0), true);

                    parent.designer.setDirty(true);
                }
            }
        }));
        buttonPanel.add(new Button("Done", new ClickHandler()
        {
            public void onClick(ClickEvent e)
            {
                AssayDefinitionDialog dlg = AssayDefinitionDialog.this;

                dlg.hide();
                GWTAssaySchedule assaySchedule = dlg.parent.assaySchedule;
                for (int i = assaySchedule.getAssays().size() - 1; i >= 0; i--)
                {
                    GWTAssayDefinition def = assaySchedule.getAssay(i);
                    if (!assays.contains(def))
                        assaySchedule.removeAssay(def);
                }
                AssayDefinitionDialog.this.parent.updateAll();
            }
        }));
        mainTable.setWidget(1, 0, buttonPanel);
        mainTable.getFlexCellFormatter().setColSpan(1, 0, 2);
        setText("Define Assay");
        setWidget(mainTable);
    }

    private void selectItem(int item, boolean updateSelection)
    {
        itemSelected = item;
        if (updateSelection)
            listBox.setSelectedIndex(item);

        editPanel.setAssay(assays.get(item));
    }

    private class EditPanel extends VerticalPanel
    {
        Label lblLocked = new Label("This is a standard assay and cannot be changed");
        TextBox tbAssayName = new TextBox();
        TextArea taDescription = new TextArea();
        TextArea taLabs = new TextArea();
        SampleMeasurePanel smp;
        GWTAssayDefinition assayDefinition;

        EditPanel()
        {
            add(lblLocked);
            tbAssayName.setWidth("300");
            tbAssayName.setName("assayName");
            tbAssayName.addChangeHandler(new ChangeHandler() {
                public void onChange(ChangeEvent e)
                {
                    if (null == StringUtils.trimToNull(tbAssayName.getText()))
                    {
                        Window.alert("An assay must have a name");
                        tbAssayName.setFocus(true);
                        return;
                    }
                    //No dup names, but could be changing back to same name after error.
                    GWTAssayDefinition named = findByName(tbAssayName.getText().trim());
                    if (null != named && named != assayDefinition)
                    {
                        Window.alert("Assay names must be unique.");
                        tbAssayName.setFocus(true);
                        return;
                    }
                    assayDefinition.setName(tbAssayName.getText().trim());
                    listBox.setItemText(itemSelected, assayDefinition.getName());
                    setDirty();
                }
            });
            add(tbAssayName);
            add (new Label("Description"));
            taDescription.setVisibleLines(5);
            taDescription.setWidth("300");
            taDescription.setName("assayDescription");
            taDescription.addChangeHandler(new ChangeHandler() {
                public void onChange(ChangeEvent e)
                {
                    assayDefinition.setDescription(StringUtils.trimToNull(taDescription.getText()));
                    setDirty();
                }
            });
            add(taDescription);

            add(new Label("Labs (one per line)"));
            taLabs.setWidth("300");
            taLabs.setVisibleLines(3);
            taLabs.setName("assayLabs");                   
            add(taLabs);
            taLabs.addChangeHandler(new ChangeHandler()
            {
                public void onChange(ChangeEvent e)
                {
                    assayDefinition.setLabs(stringToLabs(StringUtils.trimToNull(taLabs.getText())));
                    setDirty();
                }
            });
            add(new Label("Material Required"));
            setAssay(null);
        }

        void setAssay(GWTAssayDefinition assayDefinition)
        {
            this.assayDefinition = assayDefinition;
            boolean enable = null != assayDefinition && !assayDefinition.isLocked();
            tbAssayName.setEnabled(enable);
            tbAssayName.setEnabled(enable);
            taDescription.setEnabled(enable);
            taLabs.setEnabled(enable);
            if (null != smp)
                smp.setEnabled(enable);

            if (null == assayDefinition)
                return;

            lblLocked.setVisible(assayDefinition.isLocked());
            tbAssayName.setText(assayDefinition.getName());
            taDescription.setText(assayDefinition.getDescription() == null ? "" : assayDefinition.getDescription());
            taLabs.setText(labsToString());
            if (null != smp)
                remove(smp);
            smp = new SampleMeasurePanel(assayDefinition.getDefaultMeasure(), parent.studyDef);
            smp.setEnabled(enable);
            smp.addChangeListener(new ChangeListener()
            {
                public void onChange(Widget sender)
                {
                    setDirty();
                }
            });
            add(smp);
        }

        private String labsToString()
        {
            if (null == assayDefinition.getLabs())
                return "";
            
            StringBuffer sb = new StringBuffer();
            String sep = "";
            for (int i = 0; i < assayDefinition.getLabs().length; i++)
            {
                sb.append(sep);
                sb.append(assayDefinition.getLabs()[i]);
                sep = "\n";
            }
            return sb.toString();
        }

        private String[] stringToLabs(String str)
        {
            if (null == StringUtils.trimToNull(str))
                return new String[0];

            String[] labs = str.split("\n");
            for (int i = 0; i < labs.length; i++)
                labs[i] = labs[i].trim();

            return labs;
        }

        private void setDirty()
        {
            parent.designer.setDirty(true);
        }
    }
    

    private GWTAssayDefinition findByName(String name)
    {
        for (GWTAssayDefinition gwtAssayDefinition : assays)
        {
            if (name.equalsIgnoreCase(gwtAssayDefinition.getName()))
                return gwtAssayDefinition;
        }
        return null;
    }
}
