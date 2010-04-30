/*
 * Copyright (c) 2010 LabKey Corporation
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
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.*;
import gwt.client.org.labkey.study.StudyApplication;
import gwt.client.org.labkey.study.designer.client.model.*;
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
        readOnly = !"true".equals(PropertyUtil.getServerProperty("edit"));
        if (0 == studyId)
        {
            getService().getBlank(new AsyncCallback<GWTStudyDefinition>(){

                public void onFailure(Throwable caught)
                {
                    Window.alert("Couldn't get blank protocol: " + caught.getMessage());
                }

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
        return overviewPanel.validate() &&
                vaccinePanel.validate() &&
                immunizationPanel.validate() &&
                assayPanel.validate();
    }

    void showStudy(final int studyId, final int revision)
    {
        getService().getRevision(studyId, revision, new AsyncCallback<GWTStudyDefinition>(){
            public void onFailure(Throwable caught)
            {
                Window.alert("Couldn't get protocol " + studyId + ", revision " + revision + " : " + caught.getMessage());
            }

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
        mainPanel.add(label);
        mainPanel.add(new HTML("<br><p style=\"font:bold 12pt Arial\">Study Protocol Overview</p>"));
        overviewPanel = new OverviewPanel(this);
        mainPanel.add(overviewPanel);

        mainPanel.add(new HTML("<br><p style=\"font:bold 12pt Arial\">Vaccine Design</p>"));
        vaccinePanel = new VaccinePanel(this, definition.getImmunogens(), definition.getAdjuvants());
        mainPanel.add(vaccinePanel);

        mainPanel.add(new HTML("<br><p style=\"font:bold 12pt Arial\">Immunization Protocol</p>"));
        immunizationPanel = new ImmunizationPanel(this);
        mainPanel.add(immunizationPanel);
        mainPanel.add(new HTML("<br><p style=\"font:bold 12pt Arial\">Assays</p>"));
        assayPanel = new AssayPanel(this);
        mainPanel.add(assayPanel);
        HorizontalPanel buttonPanel = new HorizontalPanel();
        buttonPanel.setSpacing(3);
        mainPanel.add(buttonPanel);

        if (readOnly)
        {
            if ("true".equals(PropertyUtil.getServerProperty("canEdit")))
            {

                String editURL = PropertyUtil.getRelativeURL("designer.view") + "?edit=true&studyId=" + definition.getCavdStudyId();
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
                                if (null != PropertyUtil.getServerProperty("finishURL"))
                                     WindowUtil.setLocation(PropertyUtil.getServerProperty("finishURL"));
                                else
                                    WindowUtil.setLocation(PropertyUtil.getRelativeURL("designer.view") + "?studyId=" + info.getStudyId());
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

            getService().save(definition, new AsyncCallback<GWTStudyDesignVersion>()
            {
                public void onFailure(Throwable caught)
                {
                    Window.alert("Failure: " + caught.getMessage());

                }

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
            overviewPanel.updateRevisionInfo();
            saveStatus.setText("Revision " + info.getRevision() + " saved successfully.");
        }
    }

    StudyDefinitionServiceAsync getService()
    {
        if (service == null)
        {
            service = (StudyDefinitionServiceAsync) GWT.create(StudyDefinitionService.class);
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

    public boolean isReadOnly()
    {
        return readOnly;
    }

    public void setReadOnly(boolean readOnly)
    {
        this.readOnly = readOnly;
    }
}
