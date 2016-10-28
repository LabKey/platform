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
import java.util.ArrayList;

import gwt.client.org.labkey.study.designer.client.model.*;
import org.labkey.api.gwt.client.ui.ImageButton;
import org.labkey.api.gwt.client.util.PropertyUtil;
import org.labkey.api.gwt.client.util.StringUtils;
import org.labkey.api.gwt.client.ui.StringListBox;

public class VaccinePanel extends Composite
{
    List<GWTImmunogen> immunogens;
    List/*<Adjuvant>*/ adjuvants;
    private GWTAdjuvant ghostAdjuvant = new GWTAdjuvant();
    private GWTImmunogen ghostImmunogen = new GWTImmunogen();
    private Designer designer;
    GWTStudyDefinition studyDef;

    public VaccinePanel(Designer parent, List immunogens, List adjuvants)
    {
        this.designer = parent;
        this.studyDef = parent.getDefinition();
        this.immunogens = immunogens;
        this.adjuvants = adjuvants;

        VerticalPanel vPanel = new VerticalPanel();
        initWidget(vPanel);
        String html;
        vPanel.setHorizontalAlignment(VerticalPanel.ALIGN_LEFT);
        vPanel.add(new HTML("<h2>Immunogens</h2>"));
        if (designer.isReadOnly())
        {
            if (immunogens == null || immunogens.size() == 0)
            {
                html = "No immunogens have been defined.";
                if (designer.canEdit)
                    html += "<br> To add immunogens, click the edit button below.";
                vPanel.add(new HTML(html));
            }
            else
            {
                html = "This section describes the immunogens and adjuvants evaluated in the study.";
                if (designer.isCanEdit())
                    html += "<br> To change the set of immunogens and adjuvants, click the edit button below.";
                vPanel.add(new HTML(html));
                vPanel.add(getImmunogenGrid());
            }
        }
        else
        {
            html = "Enter vaccine information in the grids below.<ul><li>Each immunogen in the study should be listed on one row of the immunogens grid below." +
                    "<li>Each adjuvant should be listed in the adjuvant grid. " +
                    "<li>Immunogens should have unique names. " +
                    "<li>If possible the immunogen description should include specific sequences of HIV Antigens included in the immunogen." +
                    "<li>Use the immunizations tab to describe the schedule of immunizations and combinations of immunogens and adjuvants administered at each timepoint.</ul>";
            vPanel.add(new HTML(html));

            if ("true".equals(PropertyUtil.getServerProperty("canEdit")))
            {
                final boolean showAllLookups = "true".equals(PropertyUtil.getServerProperty("showAllLookups"));
                HorizontalPanel hp = new HorizontalPanel();
                hp.add(new ImageButton("Configure Dropdown Options", new ClickListener()
                {
                    public void onClick(Widget sender)
                    {
                        DesignerLookupConfigDialog dlg = new DesignerLookupConfigDialog(true, showAllLookups);
                        dlg.setPopupPosition(sender.getAbsoluteLeft(), sender.getAbsoluteTop() + sender.getOffsetHeight());
                        dlg.show();
                    }
                }));
                vPanel.add(hp);
            }

            vPanel.add(getImmunogenGrid());
        }

        vPanel.add(new HTML("<h2>Adjuvants</h2>"));
        if (designer.isReadOnly() && (null == adjuvants || adjuvants.size() == 0))
        {
            html = "No adjuvants have been defined.";
            if (designer.canEdit)
                html += "<br> To add adjuvants, click the edit button below.";
            vPanel.add(new HTML(html));
        }
        else
        {
            vPanel.add(getAdjuvantGrid());
        }
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
            return isShowAntigens() ? colHeaders.length : colHeaders.length - 1;
        }

        private boolean isShowAntigens()
        {
            //Always show antigens in edit mode
            if (!designer.isReadOnly())
                return true;

            for (GWTImmunogen immunogen : immunogens)
                if (null != immunogen.getAntigens() && immunogen.getAntigens().size() > 0)
                    return true;

            return false;
        }

        public int getDataRowCount()
        {
            return immunogens.size();
        }

        Label[] colHeaders = {new Label("Immunogen Name"), new Label("Type"), new Label("Dose and units"), new Label("Route"), new Label("HIV Antigens")};
        public Widget getColumnHeader(int row, int column)
        {
            if (column >= colHeaders.length)
                return new Label("No such column");

            return colHeaders[column];
        }

        public Widget getCellWidget(int row, int col)
        {
            final GWTImmunogen immunogen = row >= immunogens.size() ? ghostImmunogen : immunogens.get(row);
            return getCellWidget(immunogen, row, col);
        }


        Object getCellValue(int row, int col)
        {
            final GWTImmunogen immunogen = row >= immunogens.size() ? ghostImmunogen : immunogens.get(row);
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
                GWTImmunogen immunogen = immunogens.get(row);
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
                tb.setTitle("Enter the name for immunogen " + (row + 1) + " here. Each immunogen should have a unique name.");
                tb.addChangeListener(new ChangeListener()
                {
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
                final StringListBox routeList = new StringListBox(studyDef.getRoutes(), StringUtils.trimToNull(immunogen.getAdmin()), false, true);
                routeList.setTitle("Immunogen " + (row + 1) + " route");
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
                tb.setTitle("Enter the dose for immunogen " + (row + 1) + " here. If the same immunogen type is used with different dosages, enter it on two rows.");
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
                final StringListBox lb = new StringListBox(studyDef.getImmunogenTypes(), StringUtils.trimToNull(immunogen.getType()), false, true);
                lb.setTitle("Immunogen " + (row + 1) + " type");
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

            throw new IllegalArgumentException("No such column:" + col);
        }

        public int getHeaderRows()
        {
            return 1;
        }

        public void setOwner(EditableGrid grid)
        {

        }

        @Override
        public String getRowNoun()
        {
            return "immunogen";
        }

        public void makeGhostRowReal()
        {
            immunogens.add(ghostImmunogen);
            ghostImmunogen = new GWTImmunogen();
        }


        void deleteRow(int dataRow)
        {
            GWTImmunogen immunogen = immunogens.remove(dataRow);
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
                final StringListBox listBox = new StringListBox(studyDef.getGenes(), StringUtils.trimToNull(antigen.getGene()), false, true);
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
                final StringListBox listBox = new StringListBox(studyDef.getSubTypes(), StringUtils.trimToNull(antigen.getSubtype()), false, true);
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

        @Override
        public String getRowNoun()
        {
            return "antigen";
        }

        void deleteRow(int dataRow)
        {
            antigens.remove(dataRow);
            designer.setDirty(true);
            updateAll();
        }

        private static final String GEN_BANK_HELP_STRING = "Enter the GenBank ID for this HIV Antigen";
        private static final String GEN_BANK_HELP_STRING_DISABLED = "To edit, choose 'GenBank Id' or 'Both' for the sequence identifier type";
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
                genBankIdBox.setTitle(GEN_BANK_HELP_STRING);
                sequenceTextArea.setWidth("100%");
                sequenceTextArea.setVisibleLines(4);
                sequenceTextArea.setText(StringUtils.trimToEmpty(antigen.getSequence()));
                sequenceTextArea.setTitle("Enter the full sequence for this HIV Antigen");
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
                        genBankIdBox.setTitle(genBankIdBox.isEnabled() ? GEN_BANK_HELP_STRING : GEN_BANK_HELP_STRING_DISABLED);
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
        private Widget[] columnNames = new Widget[] {new Label("Name"), new Label("Dose and units"), new Label("Route")};

        public AdjuvantGrid()
        {
            super();
            columnNames[0].setTitle("The name of the adjuvant as used in the study protocol.");
            columnNames[1].setTitle("The dose of this adjuvant. If the protocol uses the same adjuvant type with different dosages create multiple adjuvant rows.");
            columnNames[2].setTitle("Route of administration for this adjuvant.");
            setReadOnly(designer.isReadOnly());
            DOM.setAttribute(getElement(), "id", "AdjuvantGrid");
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
                    nameTextBox.setTitle("Enter the name of adjuvant.");
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
                    StringListBox listBox = new StringListBox(studyDef.getRoutes(), StringUtils.trimToNull(adjuvant.admin), false, true);
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

        @Override
        public String getRowNoun()
        {
            return "adjuvant";
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
