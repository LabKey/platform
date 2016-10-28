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

import com.google.gwt.core.client.EntryPoint;
import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.ui.*;
import com.google.gwt.user.client.*;
import com.google.gwt.http.client.URL;
import gwt.client.org.labkey.study.StudyApplication;
import gwt.client.org.labkey.study.designer.client.model.*;
import org.labkey.api.gwt.client.util.ErrorDialogAsyncCallback;
import org.labkey.api.gwt.client.util.ServiceUtil;
import org.labkey.api.gwt.client.util.PropertyUtil;
import org.labkey.api.gwt.client.ui.WindowUtil;
import org.labkey.api.gwt.client.ui.LinkButton;
import org.labkey.api.gwt.client.ui.ImageButton;

import java.util.List;

/**
 * Entry point classes define <code>onModuleLoad()</code>.
 */
public class Designer implements EntryPoint
{
    final Label label = new Label();
    private boolean readOnly;

    private boolean dirty;

    public GWTStudyDefinition getDefinition()
    {
        return definition;
    }

    public void setDefinition(GWTStudyDefinition definition)
    {
        this.definition = definition;
    }//Need to init from the server...
    //StudyDefinition definition = new StudyDefinition();
    private GWTStudyDefinition definition;
    private StudyDefinitionServiceAsync service;
    OverviewPanel overviewPanel;
    AssayPanel assayPanel;
    VaccinePanel vaccinePanel;
    ImmunizationPanel immunizationPanel;
    private String panelName;
    boolean canEdit;
    Label saveStatus;
    ImageButton saveButton;


    /**
     * This is the entry point method.
     */
    public void onModuleLoad()
    {
        StudyApplication.getRootPanel().add(new Label("Loading..."));
        final int studyId = Integer.parseInt(PropertyUtil.getServerProperty("studyId"));
        final int revision = Integer.parseInt(PropertyUtil.getServerProperty("revision"));
        panelName = PropertyUtil.getServerProperty("panel");
        readOnly = !"true".equals(PropertyUtil.getServerProperty("edit"));
        canEdit = "true".equals(PropertyUtil.getServerProperty("canEdit"));
        if (0 == studyId)
        {
            getService().getBlank(new ErrorDialogAsyncCallback<GWTStudyDefinition>("Couldn't get blank protocol"){

                public void onSuccess(GWTStudyDefinition result)
                {
                    showStudy(result);
                }
            }
            );
        }
        else
        {
            showStudy(studyId, revision);
        }

        Window.addWindowClosingHandler(new Window.ClosingHandler()
        {
            public void onWindowClosing(Window.ClosingEvent event)
            {
                if (dirty)
                    event.setMessage("Changes have not been saved and will be discarded.");
            }
        });

    }

    private boolean validate()
    {
        return (null == overviewPanel || overviewPanel.validate()) &&
                (null == vaccinePanel || vaccinePanel.validate()) &&
                (null == immunizationPanel || immunizationPanel.validate()) &&
                (null == assayPanel || assayPanel.validate());
    }

    void showStudy(final int studyId, final int revision)
    {
        getService().getRevision(studyId, revision, new ErrorDialogAsyncCallback<GWTStudyDefinition>("Couldn't get protocol " + studyId + ", revision " + revision){
            public void onSuccess(GWTStudyDefinition result)
            {
                showStudy(result);
            }
        }
        );
    }

    private void showStudy(GWTStudyDefinition def)
    {
        definition = def;
        VerticalPanel mainPanel = new VerticalPanel();
        mainPanel.setStylePrimaryName("study-vaccine-design");
        mainPanel.add(label);

        if (!isReadOnly())
            mainPanel.add(new DeprecatedMessagePanel(panelName));

        if (null == panelName || panelName.toLowerCase().equals("overview"))
        {
            if (null == panelName)
                mainPanel.add(new HTML("<h2>Study Protocol Overview</h2>"));
            overviewPanel = new OverviewPanel(this);
            mainPanel.add(overviewPanel);
        }

        if (null == panelName || panelName.toLowerCase().equals("vaccine"))
        {
            //Vaccine panel contains its own headings.
            vaccinePanel = new VaccinePanel(this, definition.getImmunogens(), definition.getAdjuvants());
            mainPanel.add(vaccinePanel);
        }

        if (null == panelName || panelName.toLowerCase().equals("immunizations"))
        {
            if (null == panelName)
                mainPanel.add(new HTML("<h2>Immunization Protocol</h2>"));
            immunizationPanel = new ImmunizationPanel(this);
            mainPanel.add(immunizationPanel);
        }

        if (null == panelName || panelName.toLowerCase().equals("assays"))
        {
            if (null == panelName)
                mainPanel.add(new HTML("<h2>Assays</h2>"));
            assayPanel = new AssayPanel(this);
            mainPanel.add(assayPanel);
        }

        HorizontalPanel buttonPanel = new HorizontalPanel();
        buttonPanel.getElement().setClassName("gwt-ButtonBar");
        buttonPanel.setSpacing(3);
        mainPanel.add(buttonPanel);

        if (readOnly)
        {
            if (canEdit)
            {

                String editURL = PropertyUtil.getRelativeURL("designer.view") + "?edit=true&studyId=" + definition.getCavdStudyId();
                if (null != panelName)
                    editURL += "&panel=" + panelName;
                //issue 14006: changed encodeComponent to encodePathSegment, b/c the former will convert spaces to '+'
                editURL += "&finishURL=" + URL.encodePathSegment(PropertyUtil.getCurrentURL());
                buttonPanel.add(new LinkButton("Edit", editURL));

                if ("true".equals(PropertyUtil.getServerProperty("canCreateRepository")))
                {
                    Widget createRepositoryButton = new ImageButton("Create Study Folder", new ClickHandler()
                    {
                        public void onClick(ClickEvent event)
                        {
                            createRepository();
                        }
                    });
                    buttonPanel.add(createRepositoryButton);
                }
                buttonPanel.setSpacing(3);
            }
        }
        else
        {
            buttonPanel.add(new ImageButton("Finished", new ClickHandler()
            {
                public void onClick(ClickEvent event)
                {
                    if (isDirty())
                    {
                        new Saver() {
                            void afterSave(GWTStudyDesignVersion info) {
                                String location = PropertyUtil.getServerProperty("finishURL");
                                if (null == location)
                                {
                                    location = PropertyUtil.getRelativeURL("designer.view") + "?studyId=" + info.getStudyId();
                                    if (null != panelName)
                                        location = location + "&panel=" + panelName;
                                }
                                WindowUtil.setLocation(location);
                            }
                        }.save();
                        //Do the rest async.
                        return;
                    }

                    if (null != PropertyUtil.getServerProperty("finishURL"))
                        WindowUtil.setLocation(PropertyUtil.getServerProperty("finishURL"));
                    else if (definition.getCavdStudyId() == 0)
                        WindowUtil.setLocation(PropertyUtil.getContextPath() + "/Project" + PropertyUtil.getContainerPath() + "/start.view?");
                    else
                        WindowUtil.setLocation(PropertyUtil.getRelativeURL("designer.view") + "?studyId=" + definition.getCavdStudyId());
                }

            }));

            saveButton = new ImageButton("Save", new ClickHandler() {
                public void onClick(ClickEvent event)
                {
                    new Saver().save();
            }
        });
            buttonPanel.add(saveButton);
            saveStatus = new Label("");
            buttonPanel.add(saveStatus);
        }


        if (null != panelName && "assays".equals(panelName.toLowerCase()) && "true".equals(PropertyUtil.getServerProperty("canEdit")))
        {
            if (!isReadOnly())
            {
                buttonPanel.add(new ImageButton("Configure Dropdown Options", new ClickListener()
                {
                    public void onClick(Widget sender)
                    {
                        DesignerLookupConfigDialog dlg = new DesignerLookupConfigDialog(false, true);
                        dlg.setPopupPosition(sender.getAbsoluteLeft(), sender.getAbsoluteTop() + sender.getOffsetHeight());
                        dlg.show();
                    }
                }));
            }
        }

        if (null != panelName && "assays".equals(panelName.toLowerCase()) && "true".equals(PropertyUtil.getServerProperty("canAdmin")))
        {
            if (definition.getAssaySchedule().getAssays().size() > 0)
            {
                Widget createPlaceholderDatasetsButton = new ImageButton("Create Assay Datasets", new ClickHandler()
                {
                    public void onClick(ClickEvent event)
                    {
                        getService().ensureDatasetPlaceholders(definition, new ErrorDialogAsyncCallback<GWTStudyDefinition>()
                            {
                                public void onSuccess(GWTStudyDefinition def)
                                {
                                    Window.alert("Placeholder datasets created. Use Manage/Study Schedule to define datasets or link to assay data.");
                                }
                            });
                    }
                });
                createPlaceholderDatasetsButton.setTitle("Create placeholder datasets, with no data, matching these assay names. Placeholders can be linked to assay data using Manage/Study Schedule.");
                buttonPanel.add(createPlaceholderDatasetsButton);
            }

            final ImageButton createTimepointButton = new ImageButton("Create Study Timepoints");
            createTimepointButton.setTitle("Create timepoints for date based study. Enabled if no timepoints currently exist.");
            createTimepointButton.setEnabled("true".equals(PropertyUtil.getServerProperty("canCreateTimepoints")));
            createTimepointButton.addClickHandler(new ClickHandler()
            {
                public void onClick(ClickEvent event)
                {
                    if (definition.getAssaySchedule().getTimepoints().size() == 0)
                    {
                        Window.alert("No timepoints are defined in the assay schedule.");
                        return;
                    }

                    getService().createTimepoints(definition, new ErrorDialogAsyncCallback<GWTStudyDefinition>()
                    {
                        public void onSuccess(GWTStudyDefinition def)
                        {
                            Window.alert(def.getAssaySchedule().getTimepoints().size() +  " timepoints created.");
                            createTimepointButton.setEnabled(false);
                        }
                    });
                }
            });
            buttonPanel.add(createTimepointButton);
        }
        else if (null != panelName && "immunizations".equals(panelName.toLowerCase()) && "true".equals(PropertyUtil.getServerProperty("canAdmin")))
        {
            final ImageButton createCohortButton = new ImageButton("Create Study Cohorts");
            createCohortButton.setTitle("Create study cohorts for the specified groups.");

            // visible if the user has Admin permissions and if the study definition has a group/cohort that
            // does not exist in the study folder
            createCohortButton.setVisible("true".equals(PropertyUtil.getServerProperty("canAdmin")));
            getService().hasNewCohorts(definition, new ErrorDialogAsyncCallback<Boolean>()
            {
                public void onSuccess(Boolean hasNewCohort)
                {
                    createCohortButton.setVisible(hasNewCohort);
                }
            });

            createCohortButton.addClickHandler(new ClickHandler()
            {
                public void onClick(ClickEvent event)
                {
                    if (definition.getGroups().size() == 0)
                    {
                        Window.alert("No groups are defined in the immunization schedule.");
                        return;
                    }

                    getService().createCohorts(definition, new ErrorDialogAsyncCallback<GWTStudyDefinition>()
                    {
                        public void onSuccess(GWTStudyDefinition def)
                        {
                            Window.alert("New cohorts created.");
                            createCohortButton.setVisible(false);
                        }
                    });
                }
            });
            buttonPanel.add(createCohortButton);
        }

        StudyApplication.getRootPanel().clear();
        StudyApplication.getRootPanel().add(mainPanel);
        setDirty(false);
    }

    private class Saver
    {
        void save()
        {
            if (!validate())
                return;

            if (!isDirty())
            {
                Window.alert("Protocol was not changed. No new version saved");
                return;
            }

            getService().save(definition, new ErrorDialogAsyncCallback<GWTStudyDesignVersion>()
            {
                public void onSuccess(GWTStudyDesignVersion info)
                {
                    if (info.isSaveSuccessful())
                    {
                        setDirty(false);
                        if (0 == definition.getCavdStudyId())
                            definition.setCavdStudyId(info.getStudyId());
                        definition.setRevision(info.getRevision());
                        afterSave(info);
                    }
                    else
                    {
                        Window.alert("Could not save: " + info.getErrorMessage());
                    }
                }
            });
        }

        void afterSave(GWTStudyDesignVersion info)
        {
            if (null != overviewPanel)
                overviewPanel.updateRevisionInfo();
            saveStatus.setText("Revision " + info.getRevision() + " saved successfully.");
        }
    }

    StudyDefinitionServiceAsync getService()
    {
        if (service == null)
        {
            service = GWT.create(StudyDefinitionService.class);
            ServiceUtil.configureEndpoint(service, "definitionService");
        }
        return service;
    }

    public void createRepository()
    {
        //Make sure we have at least one assay scheduled
        if (definition.getGroups().size() == 0)
        {
            Window.alert("At least one group should be defined before creating a study folder.");
            return;
        }
        boolean assaysScheduled = false;

        GWTAssaySchedule schedule = definition.getAssaySchedule();
        List<GWTAssayDefinition> assays = schedule.getAssays();
        List<GWTTimepoint> timepoints = schedule.getTimepoints();
        for (int itp = 0; itp < schedule.getTimepoints().size(); itp++)
            for (int iassay = 0; iassay < schedule.getAssays().size(); iassay++)
                if (schedule.isAssayPerformed(assays.get(iassay), timepoints.get(itp)))
                {
                    assaysScheduled = true;
                    break;
                }

        if (!assaysScheduled)
        {
            Window.alert("At least one assay should be scheduled before creating a study folder.");
            return;
        }

        String createRepositoryURL = PropertyUtil.getContextPath() + "/Study-Designer" + PropertyUtil.getContainerPath() + "/createRepository.view?wizardStepNumber=0&studyId=" + definition.getCavdStudyId();
        WindowUtil.setLocation(createRepositoryURL);
    }

    public boolean isDirty()
    {
        return dirty;
    }

    public void setDirty(boolean dirty)
    {
        this.dirty = dirty;
        if (dirty)
            saveStatus.setText("");
        if (!readOnly)
            saveButton.setEnabled(dirty);
    }

    public boolean isCanEdit()
    {
        return canEdit;
    }

    public boolean isReadOnly()
    {
        return readOnly;
    }

    public void setReadOnly(boolean readOnly)
    {
        this.readOnly = readOnly;
    }
}
