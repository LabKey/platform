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

import com.google.gwt.event.dom.client.ChangeEvent;
import com.google.gwt.event.dom.client.ChangeHandler;
import com.google.gwt.user.client.ui.*;
import com.google.gwt.user.client.Window;
import gwt.client.org.labkey.study.designer.client.model.GWTStudyDefinition;
import gwt.client.org.labkey.study.designer.client.model.GWTStudyDesignVersion;
import org.labkey.api.gwt.client.util.ErrorDialogAsyncCallback;
import org.labkey.api.gwt.client.util.StringUtils;
import org.labkey.api.gwt.client.util.PropertyUtil;
import org.labkey.api.gwt.client.ui.WindowUtil;

/**
 * User: Mark Igra
 * Date: Dec 16, 2006
 * Time: 3:12:40 PM
 */
public class OverviewPanel extends Composite
{
    FlexTable layout = new FlexTable();
    RevisionRow revisionRow;
    GWTStudyDefinition definition;
    Designer designer;


    public OverviewPanel(Designer parent)
    {
        this.designer = parent;
        this.definition = designer.getDefinition();
        initWidget(layout);
        Widget studyNameWidget;
        Widget speciesWidget;
        Widget investigatorWidget;
        Widget grantWidget;
        Widget descriptionWidget;

        int layoutRow = 0;
        revisionRow = new RevisionRow(layoutRow);
        revisionRow.update();

        if (designer.isReadOnly())
        {
            studyNameWidget = new Label(StringUtils.trimToEmpty(definition.getStudyName()));
            speciesWidget = new Label(StringUtils.trimToEmpty(definition.getAnimalSpecies()));
            investigatorWidget = new Label(StringUtils.trimToEmpty(definition.getInvestigator()));
            grantWidget = new Label(StringUtils.trimToEmpty(definition.getGrant()));
            descriptionWidget = new HTML(StringUtils.filter(definition.getDescription(), true));
        }
        else
        {
            final TextBox tbStudyName = new TextBox();
            tbStudyName.setText(StringUtils.trimToEmpty(definition.getStudyName()));
            //tbStudyName.setName("protocolName"); //for testing
            tbStudyName.addChangeListener(new ChangeListener()
            {
                public void onChange(Widget sender)
                {
                    definition.setStudyName(tbStudyName.getText().trim());
                    designer.setDirty(true);
                }
            });
            studyNameWidget = tbStudyName;

            final TextBox tbSpecies = new TextBox();
            tbSpecies.setText(StringUtils.trimToEmpty(definition.getAnimalSpecies()));
            //tbSpecies.setName("species"); //for testing
            tbSpecies.addChangeListener(new ChangeListener()
            {
                public void onChange(Widget sender)
                {
                    definition.setAnimalSpecies(tbSpecies.getText().trim());
                    designer.setDirty(true);
                }
            });
            speciesWidget = tbSpecies;

            final TextBox tbInvestigator = new TextBox();
            tbInvestigator.setText(StringUtils.trimToEmpty(definition.getInvestigator()));
            //tbInvestigator.setName("investigator"); //for testing
            tbInvestigator.addChangeListener(new ChangeListener()
            {
                public void onChange(Widget sender)
                {
                    definition.setInvestigator(tbInvestigator.getText().trim());
                    designer.setDirty(true);
                }
            });
            investigatorWidget = tbInvestigator;

            final TextBox tbGrant = new TextBox();
            tbGrant.setText(StringUtils.trimToEmpty(definition.getGrant()));
            //tbGrant.setName("grant"); //for testing
            tbGrant.addChangeListener(new ChangeListener()
            {
                public void onChange(Widget sender)
                {
                    definition.setGrant(tbGrant.getText().trim());
                    designer.setDirty(true);
                }
            });
            grantWidget = tbGrant;

            final TextArea tbDescription = new TextArea();
            tbDescription.setWidth("100%");
            tbDescription.setVisibleLines(5);
            tbDescription.setName("protocolDescription"); //For easier testing
            ActivatingLabel activatingLabel = new ActivatingLabel(tbDescription, "Click to edit description");
            if (null != definition.getDescription())
                activatingLabel.setText(definition.getDescription());
            activatingLabel.addChangeListener(new ChangeListener()
            {
                public void onChange(Widget sender)
                {
                    definition.setDescription(tbDescription.getText().trim());
                    designer.setDirty(true);
                }
            });
            descriptionWidget = activatingLabel;
        }
        
        layout.insertRow(++layoutRow);
        layout.setText(layoutRow, 0, "Protocol Name");
        layout.setWidget(layoutRow, 1, studyNameWidget);
        layout.setText(layoutRow, 2, "Investigator");
        layout.setWidget(layoutRow, 3, investigatorWidget);
        layout.insertRow(++layoutRow);
        layout.setText(layoutRow, 0, "Grant");
        layout.setWidget(layoutRow, 1, grantWidget);
        layout.setText(layoutRow, 2, "Species");
        layout.setWidget(layoutRow, 3, speciesWidget);
        layout.insertRow(++layoutRow);
        layout.setText(layoutRow, 0, "Overview");
        layout.insertRow(++layoutRow);
        layout.addCell(layoutRow);
        layout.getFlexCellFormatter().setColSpan(layoutRow, 0, 4);
        layout.setWidget(layoutRow, 0, descriptionWidget);
    }

    public void updateRevisionInfo()
    {
        revisionRow.update();
    }
    
    class RevisionRow
    {
        int layoutRow;

        RevisionRow(int rowNum)
        {
            this.layoutRow = rowNum;
        }

        void update()
        {
            layout.setText(layoutRow, 0, "Protocol Id");
            layout.setText(layoutRow, 1, definition.getCavdStudyId() == 0 ? "unassigned" : "" + definition.getCavdStudyId());
            layout.setText(layoutRow, 2, "Revision");

            if (definition.getRevision() == 0)
            {
                layout.setText(layoutRow, 3, "Not saved.");
                return;
            }

            layout.setText(layoutRow, 3, definition.getRevision() + " (Loading List)");
            designer.getService().getVersions(definition.getCavdStudyId(), new ErrorDialogAsyncCallback<GWTStudyDesignVersion[]>("Error occurred getting revisions")
            {
                public void onSuccess(GWTStudyDesignVersion[] versions)
                {
                    final ListBox revisionList = new ListBox();
                    for (GWTStudyDesignVersion version : versions)
                    {
                        String title = version.getRevision() + ": " + version.getWriterName() + " " + version.getCreated();
                        revisionList.addItem(title, Integer.toString(version.getRevision()));
                        if (version.getRevision() == definition.getRevision())
                            revisionList.setItemSelected(revisionList.getItemCount() - 1, true);
                    }
                    revisionList.addChangeHandler(new ChangeHandler()
                    {
                        public void onChange(ChangeEvent e)
                        {
                            if (designer.isDirty())
                                if (!Window.confirm("Viewing another revision will discard changes. Continue?"))
                                    return;
                            
                            int revision = Integer.parseInt(revisionList.getValue(revisionList.getSelectedIndex()));
                            //Redirect here. Include revision number only if it is NOT the latest revision
                            String showRevision = PropertyUtil.getRelativeURL("designer.view") + "?studyId=" + definition.getCavdStudyId();
                            if (revisionList.getSelectedIndex() < revisionList.getItemCount() - 1)
                                showRevision += "&revision=" + revision;
                            WindowUtil.setLocation(showRevision);
                        }
                    });
                    layout.setWidget(layoutRow, 3, revisionList);
                }
            });
        }
    }


    public boolean validate()
    {
        if (null == StringUtils.trimToNull(definition.getStudyName()))
        {
            Window.alert("Please enter a protocol name");
            return false;
        }
        return true;
    }
}
