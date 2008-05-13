/*
 * Copyright (c) 2007-2008 LabKey Corporation
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

package org.labkey.study.designer.client;

import com.google.gwt.user.client.ui.*;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.DOM;

import java.util.List;
import java.util.ArrayList;

import org.labkey.study.designer.client.ActivatingLabel;
import org.labkey.study.designer.client.model.*;
import org.labkey.api.gwt.client.util.StringUtils;
import org.labkey.api.gwt.client.ui.StringListBox;

public class VaccinePanel extends Composite
{
    List/*<Immunogen>*/ immunogens;
    List/*<Adjuvant>*/ adjuvants;
    private GWTAdjuvant ghostAdjuvant = new GWTAdjuvant();
    private GWTImmunogen ghostImmunogen = new GWTImmunogen();
    private Designer designer;

    public VaccinePanel(Designer parent, List immunogens, List adjuvants)
    {
        this.designer = parent;
        this.immunogens = immunogens;
        this.adjuvants = adjuvants;

        VerticalPanel vPanel = new VerticalPanel();
        initWidget(vPanel);
        vPanel.setHorizontalAlignment(VerticalPanel.ALIGN_LEFT);
        vPanel.add(new Label("Immunogens"));
        vPanel.add(getImmunogenGrid());
        vPanel.add(new Label("Adjuvants"));
        vPanel.add(getAdjuvantGrid());
    }


    public class ImmunogenGrid extends EditableGrid
    {
        public ImmunogenGrid()
        {
            super();
            setReadOnly(designer.isReadOnly());
            DOM.setAttribute(getElement(), "id", "ImmunogenGrid");
        }

        public int getDataColumnCount()
        {
            return 5;
        }

        public int getDataRowCount()
        {
            return immunogens.size();
        }

        Widget[] colHeaders = {new Label("Name"), new Label("Type"), new Label("Dose"), new Label("Route"), new Label("HIV Antigens")};
        public Widget getColumnHeader(int row, int column)
        {
            if (column >= colHeaders.length)
                return new Label("No such column");

            return colHeaders[column];
        }

        public Widget getCellWidget(int row, int col)
        {
            final GWTImmunogen immunogen = row >= immunogens.size() ? ghostImmunogen : (GWTImmunogen) immunogens.get(row);
            return getCellWidget(immunogen, row, col);
        }


        Object getCellValue(int row, int col)
        {
            final GWTImmunogen immunogen = row >= immunogens.size() ? ghostImmunogen : (GWTImmunogen) immunogens.get(row);
            switch (col)
            {
                case 0:
                    return immunogen.getName();
                case 1:
                    return immunogen.getType();
                case 2:
                    return immunogen.getDose();
                case 3:
                    return immunogen.getAdmin();
            }
            
            return null;
        }


        public Widget getReadOnlyWidget(int row, int col)
        {
            if (col <= 3)
                return super.getReadOnlyWidget(row, col); //Just plain labels
            else
            {
                GWTImmunogen immunogen = (GWTImmunogen) immunogens.get(row);
                if (null != immunogen.getAntigens() && immunogen.getAntigens().size() > 0)
                    return getCellWidget(row, col);
                else
                    return new Label("");
            }
        }

        Widget getGhostRowWidget(int col)
        {
            return getCellWidget(ghostImmunogen, getRowCount() - getHeaderRows(), col);
        }

        public Widget getCellWidget(final GWTImmunogen immunogen, int row, int col)
        {

            if (col == 0)
            {
                final TextBox tb = new TextBox();
                tb.setText(StringUtils.trimToEmpty(immunogen.getName()));
                tb.setTitle("Immunogen " + row + " name");
                tb.addChangeListener(new ChangeListener(){
                    public void onChange(Widget sender)
                    {
                        immunogen.setName(tb.getText());
                        designer.setDirty(true);
                        designer.immunizationPanel.updateAll();
                    }
                });
                return tb;
            }
            else if (col == 3)
            {
                final StringListBox routeList = new StringListBox(GWTStudyDefinition.routes, StringUtils.trimToNull(immunogen.getAdmin()), true);
                routeList.setTitle("Immunogen " + row + " route");
                routeList.addChangeListener(new ChangeListener(){
                    public void onChange(Widget sender)
                    {
                        immunogen.setAdmin(routeList.getText());
                        designer.setDirty(true);
                    }
                });
                return routeList;
            }
            else if (col == 2)
            {
                final TextBox tb = new TextBox();
                tb.setText(StringUtils.trimToEmpty(immunogen.getDose()));
                tb.setTitle("Immunogen " + row + " dose");
                tb.addChangeListener(new ChangeListener(){
                    public void onChange(Widget sender)
                    {
                        immunogen.setDose(tb.getText());
                        designer.setDirty(true);
                    }
                });
                return tb;
            }

            if (col == 1)
            {
                final StringListBox lb = new StringListBox(GWTStudyDefinition.immunogenTypes, immunogen.getType(), true);
                lb.setTitle("Immunogen " + row + " type");
                lb.addChangeListener(new ChangeListener() {
                    public void onChange(Widget sender)
                    {
                        immunogen.setType(lb.getText());
                        designer.setDirty(true);
                    }
                });

                return lb;
            }

            if (col == 4)
            {
                return getAntigenGrid(immunogen.getAntigens(), row);
            }

            throw new IllegalArgumentException("No such collumn:" + col);
        }

        public int getHeaderRows()
        {
            return 1;
        }

        public void setOwner(EditableGrid grid)
        {

        }

        public void makeGhostRowReal()
        {
            immunogens.add(ghostImmunogen);
            ghostImmunogen = new GWTImmunogen();
        }


        void deleteRow(int dataRow)
        {
            GWTImmunogen immunogen = (GWTImmunogen) immunogens.remove(dataRow);
            GWTImmunizationSchedule schedule = designer.getDefinition().getImmunizationSchedule();
            schedule.removeImmunogen(immunogen);
            designer.setDirty(true);
            updateAll();
            designer.immunizationPanel.updateAll();
        }
    }

    class AntigenGrid extends EditableGrid
    {
        List/*<GWTAntigen>*/ antigens;
        GWTAntigen ghostAntigen = new GWTAntigen();

        AntigenGrid(List/*<GWTAntigen>*/ antigens, int row)
        {
            this.antigens = null == antigens ? new ArrayList() : antigens;
            DOM.setAttribute(this.getElement(), "id", "AntigenGrid" + row);
            setReadOnly(designer.isReadOnly());
        }

        public int getDataColumnCount()
        {
            return 3;
        }

        public int getDataRowCount()
        {
            return antigens.size();
        }

        private Widget[] colHeaders = new Widget[] {new Label("Gene"), new Label("Subtype"), new Label("Sequence") };
        public Widget getColumnHeader(int row, int column)
        {
            return colHeaders[column];
        }

        public Widget getCellWidget(int row, int col)
        {
            return getCellWidget((GWTAntigen) antigens.get(row), col);
        }

        public Object getCellValue(int row, int col)
        {
            GWTAntigen antigen = (GWTAntigen) antigens.get(row);
            switch (col)
            {
                case 0:
                    return antigen.getGene();
                case 1:
                    return antigen.getSubtype();
                case 2:
                    String s = null;
                    if (null != antigen.getGenBankId())
                        s = antigen.getGenBankId();
                    if (null != antigen.getSequence())
                        s = (s == null ? "" : s + ": ") + antigen.getSequence();
                    return StringUtils.trimToEmpty(s);
            }

            return null;
        }


        public Widget getReadOnlyWidget(int row, int col)
        {
            Label l = new Label((String) getCellValue(row, col));
            l.setWordWrap(col == 2);
            return l;
        }

        Widget getGhostRowWidget(int col)
        {
            return getCellWidget(ghostAntigen, col);
        }

        private Widget getCellWidget(final GWTAntigen antigen, int col)
        {
            if (col == 0)
            {
                final StringListBox listBox = new StringListBox(GWTStudyDefinition.genes, StringUtils.trimToNull(antigen.getGene()), true);
                listBox.addChangeListener(new ChangeListener() {
                    public void onChange(Widget sender)
                    {
                        antigen.setGene(listBox.getText());
                        designer.setDirty(true);
                    }
                });
                return listBox;
            }
            if (col == 1)
            {
                final StringListBox listBox = new StringListBox(GWTStudyDefinition.subTypes, StringUtils.trimToNull(antigen.getSubtype()), true);
                listBox.setText(StringUtils.trimToEmpty(antigen.getSubtype()));
                listBox.addChangeListener(new ChangeListener() {
                    public void onChange(Widget sender)
                    {
                        antigen.setSubtype(listBox.getText());
                        designer.setDirty(true);
                    }
                });
                return listBox;
            }
            if (col == 2)
            {
                return new SequencePanel(antigen);
            }

            throw new IllegalArgumentException("No such column: " + col);
        }

        public int getHeaderRows()
        {
            return 1;
        }

        public void makeGhostRowReal()
        {
            antigens.add(ghostAntigen);
            ghostAntigen = new GWTAntigen();
        }

        void deleteRow(int dataRow)
        {
            antigens.remove(dataRow);
            designer.setDirty(true);
            updateAll();
        }

        public class SequencePanel extends FlexTable
        {
            private ListBox entryType = new ListBox();
            private TextBox genBankIdBox = new TextBox();
            private TextArea sequenceTextArea = new TextArea();
            GWTAntigen antigen;

            SequencePanel(GWTAntigen antigen)
            {
                this.antigen = antigen;
                entryType.addItem("GenBank Id");
                entryType.addItem("Sequence");
                entryType.addItem("Both");
                genBankIdBox.setText(StringUtils.trimToEmpty(antigen.getGenBankId()));
                sequenceTextArea.setWidth("100%");
                sequenceTextArea.setVisibleLines(4);
                sequenceTextArea.setText(StringUtils.trimToEmpty(antigen.getSequence()));
                if (null != antigen.getSequence() && null != antigen.getGenBankId())
                    entryType.setSelectedIndex(2);
                else if (null != antigen.getSequence())
                    entryType.setSelectedIndex(1);
                else
                    entryType.setSelectedIndex(0);

                this.prepareCell(1, 0);
                this.getFlexCellFormatter().setColSpan(1, 0, 2);

                setWidget(0, 0, entryType);
                setWidget(0, 1, genBankIdBox);
                if (null != antigen.getSequence())
                    setWidget(1, 0, sequenceTextArea);

                entryType.addChangeListener(new ChangeListener()
                {
                    public void onChange(Widget sender)
                    {
                        switch (entryType.getSelectedIndex())
                        {
                            case 0:
                                genBankIdBox.setEnabled(true);
                                if (null != getWidget(1, 0))
                                    remove(sequenceTextArea);
                                break;
                            case 1:
                                genBankIdBox.setEnabled(false);
                                SequencePanel.this.antigen.setGenBankId(null);
                                genBankIdBox.setText("");
                                if (null == getWidget(1, 0))
                                    setWidget(1, 0, sequenceTextArea);
                                break;
                            default:
                                genBankIdBox.setEnabled(true);
                                setWidget(1, 0, sequenceTextArea);
                        }
                        designer.setDirty(true);
                    }
                });
                genBankIdBox.addChangeListener(new ChangeListener()
                {
                    public void onChange(Widget sender)
                    {
                        designer.setDirty(true);
                        SequencePanel.this.antigen.setGenBankId(StringUtils.trimToNull(genBankIdBox.getText()));
                    }
                });
                sequenceTextArea.addChangeListener(new ChangeListener()
                {
                    public void onChange(Widget sender)
                    {
                        designer.setDirty(true);
                        SequencePanel.this.antigen.setSequence(StringUtils.trimToNull(sequenceTextArea.getText()));
                    }
                });
            }
        }

    }

    public class AdjuvantGrid extends EditableGrid
    {
        private Widget[] columnNames = new Widget[] {new Label("Name"), new Label("Dose"), new Label("Route")};

        public AdjuvantGrid()
        {
            super();
            setReadOnly(designer.isReadOnly());
        }

        public int getDataColumnCount()
        {
            return columnNames.length;
        }

        public int getDataRowCount()
        {
            return null == adjuvants ?  0 : adjuvants.size();
        }

        public Widget getColumnHeader(int row, int column)
        {
            if (column >= columnNames.length)
                return new Label("No such column: " + column);

            return columnNames[column];
        }

        Widget getCellWidget(int row, int col)
        {
            return getCellWidget((GWTAdjuvant) adjuvants.get(row), col);
        }

        Object getCellValue(int row, int col)
        {
            GWTAdjuvant adjuvant = (GWTAdjuvant) adjuvants.get(row);
            switch (col)
            {
                case 0:
                    return adjuvant.getName();
                case 1:
                    return adjuvant.getDose();
                case 2:
                    return adjuvant.admin;
            }
            return null;
        }


        Widget getGhostRowWidget(int col)
        {
            return getCellWidget(ghostAdjuvant, col);
        }

        private Widget getCellWidget(final GWTAdjuvant adjuvant, int col)
        {

            Widget w;
            String text = null;
            switch (col)
            {
                case 0:
                    TextBox nameTextBox = new TextBox();
                    text = adjuvant.getName();
                    nameTextBox.setText(StringUtils.trimToEmpty(text));
                    nameTextBox.addChangeListener(new ChangeListener() {
                        public void onChange(Widget sender)
                        {
                            adjuvant.setName(((HasText) sender).getText());
                            designer.setDirty(true);
                            designer.immunizationPanel.updateAll();
                        }
                    });
                    return nameTextBox;
                case 1:
                    TextBox doseTextBox = new TextBox();
                    text = adjuvant.getDose();
                    doseTextBox.setText(StringUtils.trimToEmpty(text));
                    doseTextBox.addChangeListener(new ChangeListener() {
                        public void onChange(Widget sender)
                        {
                            adjuvant.setDose(((HasText) sender).getText());
                            designer.setDirty(true);
                        }
                    });
                    return doseTextBox;
                case 2:
                    StringListBox listBox = new StringListBox(GWTStudyDefinition.routes, StringUtils.trimToNull(adjuvant.admin), true);
                    text = adjuvant.admin;
                    listBox.addChangeListener(new ChangeListener() {
                        public void onChange(Widget sender)
                        {
                            adjuvant.admin = ((HasText) sender).getText();
                            designer.setDirty(true);
                        }
                    });
                    return listBox;
            }
            return null;
        }

        public int getHeaderRows()
        {
            return 1;
        }

        public void makeGhostRowReal()
        {
            adjuvants.add(ghostAdjuvant);
            ghostAdjuvant = new GWTAdjuvant();
        }

        void deleteRow(int dataRow)
        {
            GWTAdjuvant adjuvant = (GWTAdjuvant) adjuvants.remove(dataRow);
            designer.getDefinition().getImmunizationSchedule().removeAdjuvant(adjuvant);
            designer.setDirty(true);
            updateAll();
            designer.immunizationPanel.updateAll();
        }
    }

    public EditableGrid getAntigenGrid(List/*<Antigen>*/ antigens, int row)
    {
        EditableGrid eg = new AntigenGrid(antigens, row);
        eg.updateAll();
        return eg;
    }

    public EditableGrid getImmunogenGrid()
    {
        EditableGrid eg = new ImmunogenGrid();
        eg.updateAll();
        return eg;
    }

    public EditableGrid getAdjuvantGrid()
    {
        EditableGrid eg = new AdjuvantGrid();
        eg.updateAll();
        return eg;
    }

    public boolean validate()
    {
        return validateComponentNames(immunogens) && validateComponentNames(adjuvants);
    }

    private boolean validateComponentNames(List/*<VaccineComponent>*/ components)
    {
        for (int i = 0; i < components.size(); i++)
        {
            VaccineComponent component = (VaccineComponent) components.get(i);
            if (null == StringUtils.trimToNull(component.getName()))
            {
                Window.alert("All vaccine components must have a name");
                return false;
            }
        }
        return true;
    }
}
