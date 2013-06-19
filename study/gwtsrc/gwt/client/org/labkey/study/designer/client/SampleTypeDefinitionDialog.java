/*
 * Copyright (c) 2010-2012 LabKey Corporation
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
import gwt.client.org.labkey.study.designer.client.model.*;
import org.labkey.api.gwt.client.util.StringUtils;

import java.util.List;

/**
 * User: Mark Igra
 * Date: Jan 28, 2007
 * Time: 10:56:47 AM
 */
public class SampleTypeDefinitionDialog extends DialogBox
{
    List/*<GWTSampleType>*/ sampleTypes;
    ListBox listBox;
    EditPanel editPanel;
    int itemSelected;
    AssayPanel parent;

    /**
     * SampleType Definition Dialog in the form of "Create new"
     * @param parent
     * @param sampleType
     */
    public SampleTypeDefinitionDialog(AssayPanel parent, final GWTSampleType sampleType)
    {
        this.parent = parent;

        VerticalPanel vpanel = new VerticalPanel();

        editPanel = new EditPanel();
        editPanel.setSampleType(sampleType);

        vpanel.add(editPanel);
        HorizontalPanel buttonPanel = new HorizontalPanel();
        buttonPanel.setSpacing(3);
        ClickListener hideListener = new ClickListener() {
            public void onClick(Widget sender)
            {
                SampleTypeDefinitionDialog.this.hide();
            }
        };
        buttonPanel.add(new Button("Cancel", hideListener));
        ClickListener okListener =  new ClickListener() {
                    public void onClick(Widget sender)
                    {
                        SampleTypeDefinitionDialog.this.parent.studyDef.getSampleTypes().add(sampleType);
                        SampleTypeDefinitionDialog.this.parent.eg.updateAll();
                        SampleTypeDefinitionDialog.this.parent.designer.setDirty(true);
                        SampleTypeDefinitionDialog.this.hide();
                     }
            };
        Button okButton = new Button("OK", okListener);
        okButton.addClickListener(hideListener);
        buttonPanel.add(okButton);

        vpanel.add(buttonPanel);
        setText("Define Sample Type");
        setWidget(vpanel);
    }

    public SampleTypeDefinitionDialog(final AssayPanel parent, final List<GWTSampleType> sampleTypes)
    {
        this.parent = parent;

        FlexTable mainTable = new FlexTable();
        listBox = new ListBox();
        listBox.setVisibleItemCount(12);
        this.sampleTypes = sampleTypes;
        for (GWTSampleType def : sampleTypes)
        {
            listBox.addItem(def.getName());
        }
        listBox.addChangeListener(new ChangeListener(){
            public void onChange(Widget src)
            {
                selectItem(listBox.getSelectedIndex(), false);
            }
        });
        listBox.setWidth("250");
        listBox.setName("sampleTypeList");
        mainTable.setWidget(0, 0, listBox);
        mainTable.getCellFormatter().setVerticalAlignment(0, 0, HasVerticalAlignment.ALIGN_TOP);
        editPanel = new EditPanel();
        if (sampleTypes.size() > 0)
            selectItem(0, true);
        mainTable.setWidget(0, 1, editPanel);
        mainTable.getCellFormatter().setVerticalAlignment(0, 1, HasVerticalAlignment.ALIGN_TOP);

        HorizontalPanel buttonPanel = new HorizontalPanel();
        buttonPanel.setSpacing(3);
        buttonPanel.add(new Button("Add", new ClickListener()
        {
            public void onClick(Widget sender)
            {
                String name = "SampleType";
                int index = 1;
                while (null != findByName(name + index))
                    index++;
                name = name + index;

                GWTSampleType newSampleType = new GWTSampleType(name, "Blood", "");
                SampleTypeDefinitionDialog.this.sampleTypes.add(newSampleType);
                listBox.addItem(name);
                parent.designer.setDirty(true);
                selectItem(SampleTypeDefinitionDialog.this.sampleTypes.size() - 1, true);
            }
        }));
        buttonPanel.add(new Button("Delete", new ClickListener() {

            public void onClick(Widget sender)
            {
                if (itemSelected < SampleTypeDefinitionDialog.this.sampleTypes.size())
                {
                    GWTSampleType sampleType = (GWTSampleType) SampleTypeDefinitionDialog.this.sampleTypes.get(itemSelected);
                    GWTAssayDefinition def = findSampleTypeUsage(sampleType);
                    if (null != def)
                    {
                        Window.alert("This sample type is used by the " + def.getName() + " assay and cannot be removed from the list.");
                        return;
                    }

                    SampleTypeDefinitionDialog.this.sampleTypes.remove(itemSelected);
                    listBox.removeItem(itemSelected);
                    if (SampleTypeDefinitionDialog.this.sampleTypes.size() > 0)
                        selectItem(Math.max(itemSelected - 1, 0), true);

                    parent.designer.setDirty(true);
                }
            }
        }));
        buttonPanel.add(new Button("Done", new ClickListener()
        {
            public void onClick(Widget sender)
            {
                SampleTypeDefinitionDialog dlg = SampleTypeDefinitionDialog.this;

                dlg.hide();
                SampleTypeDefinitionDialog.this.parent.updateAll();
            }
        }));
        mainTable.setWidget(1, 0, buttonPanel);
        mainTable.getFlexCellFormatter().setColSpan(1, 0, 2);
        setText("Define Sample Type");
        setWidget(mainTable);
    }

    private void selectItem(int item, boolean updateSelection)
    {
        itemSelected = item;
        if (updateSelection)
            listBox.setSelectedIndex(item);

        editPanel.setSampleType((GWTSampleType) sampleTypes.get(item));
    }

    private class EditPanel extends VerticalPanel
    {
        Label lblLocked = new Label("This sample type is currently used by an assay and cannot be changed.");
        TextBox tbSampleTypeName = new TextBox();
        TextBox tbPrimaryType = new TextBox();
        TextBox tbSampleCode = new TextBox();
        ChangeListenerCollection listeners = new ChangeListenerCollection();
        GWTSampleType sampleType;

        EditPanel()
        {
            add(lblLocked);
            add (new Label("Sample Type"));
            tbSampleTypeName.setWidth("300");
            tbSampleTypeName.setName("sampleTypeName");
            tbSampleTypeName.addChangeListener(new ChangeListener() {
                public void onChange(Widget sender)
                {
                    if (null == StringUtils.trimToNull(tbSampleTypeName.getText()))
                    {
                        Window.alert("An sample type must have a name");
                        tbSampleTypeName.setFocus(true);
                        return;
                    }
                    //No dup names, but could be changing back to same name after error.
                    GWTSampleType named = findByName(tbSampleTypeName.getText().trim());
                    if (null != named && named != sampleType)
                    {
                        Window.alert("Sample type names must be unique.");
                        tbSampleTypeName.setFocus(true);
                        return;
                    }
                    sampleType.setName(tbSampleTypeName.getText().trim());
                    listBox.setItemText(itemSelected, sampleType.getName());
                    setDirty();
                }
            });
            add(tbSampleTypeName);

            add (new Label("Primary Type"));
            tbPrimaryType.setWidth("300");
            tbPrimaryType.setName("primaryType");
            tbPrimaryType.addChangeListener(new ChangeListener() {
                public void onChange(Widget sender)
                {
                    sampleType.setPrimaryType(StringUtils.trimToNull(tbPrimaryType.getText()));
                    setDirty();
                }
            });
            add(tbPrimaryType);

            Label sampleCodeLabel = new Label("Short Sample Code (1 or 2 letters)");
            sampleCodeLabel.setTitle("The short sample code is used to generate unique vial ids");
            add (sampleCodeLabel);
            tbSampleCode.setWidth("100");
            tbSampleCode.setName("sampleCode");
            tbSampleCode.addChangeListener(new ChangeListener() {
                public void onChange(Widget sender)
                {
                    sampleType.setCode(StringUtils.trimToNull(tbSampleCode.getText()));
                    setDirty();
                }
            });
            add(tbSampleCode);

            setSampleType(null);
        }

        void setSampleType(GWTSampleType sampleType)
        {
            this.sampleType = sampleType;
            GWTAssayDefinition usage = findSampleTypeUsage(sampleType);
            boolean enable = null != sampleType && null == usage;
            tbSampleTypeName.setEnabled(enable);
            tbSampleCode.setEnabled(enable);
            tbPrimaryType.setEnabled(enable);

            if (null == sampleType)
                return;

            if (null != usage)
            {
                lblLocked.setText("This sample type cannot be modified because it is used by the assay " + usage.getName());
                lblLocked.setVisible(true);
            }
            else
                lblLocked.setVisible(false);

            tbSampleTypeName.setText(sampleType.getName());
            tbPrimaryType.setText(StringUtils.trimToEmpty(sampleType.getPrimaryType()));
            tbSampleCode.setText(StringUtils.trimToEmpty(sampleType.getShortCode()));
        }

        private void setDirty()
        {
            parent.designer.setDirty(true);
        }
    }


    private GWTSampleType findByName(String name)
    {
        for (int i = 0; i < sampleTypes.size(); i++)
        {
            GWTSampleType GWTSampleType = (GWTSampleType) sampleTypes.get(i);
            if (name.equalsIgnoreCase(GWTSampleType.getName()))
                return GWTSampleType;
        }
        return null;
    }

    private GWTAssayDefinition findSampleTypeUsage(GWTSampleType sampleType)
    {
        if (null == sampleType)
            return null;

        //Check each assay at each timepoint cause this can change...
        GWTAssaySchedule schedule = parent.assaySchedule;
        for (int a = 0; a < schedule.getAssays().size(); a++)
        {
            for (int t = 0; t < schedule.getTimepoints().size(); t++)
            {
                GWTAssayNote note = schedule.getAssayPerformed(schedule.getAssay(a), schedule.getTimepoint(t));
                if (null == note)
                    continue;

                if (note.getSampleMeasure().getType().getName().equals(sampleType.getName()))
                    return schedule.getAssay(a);
            }
        }

        //Need to check all assays that *could be used* in this study
        List/*<GWTAssayDefinition>*/ assays = parent.designer.getDefinition().getAssays();
        for (int a = 0; a < assays.size(); a++)
        {
            GWTAssayDefinition def = (GWTAssayDefinition) assays.get(a);
            if (def.getDefaultMeasure().getType().getName().equals(sampleType.getName()))
                return def;
        }

        return null;
    }


}