/*
 * Copyright (c) 2008-2019 LabKey Corporation
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
package org.labkey.experiment;

import org.labkey.api.data.ActionButton;
import org.labkey.api.data.ButtonBar;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.DataRegion;
import org.labkey.api.exp.api.ExpSampleType;
import org.labkey.api.exp.query.ExpSchema;
import org.labkey.api.qc.SampleStatusService;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.DesignSampleTypePermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.study.StudyUrls;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.DataView;
import org.labkey.api.view.ViewContext;
import org.labkey.experiment.controllers.exp.ExperimentController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.PrintWriter;

/**
 * User: jeckels
 * Date: Oct 20, 2005
 */
public class SampleTypeWebPart extends QueryView
{
    private String _errorMessage;
    private final boolean _narrow;

    public SampleTypeWebPart(boolean narrow, ViewContext viewContext)
    {
        super(new ExpSchema(viewContext.getUser(), viewContext.getContainer()));
        _narrow = narrow;
        setSettings(createQuerySettings(viewContext, "SampleSet" + (_narrow ? "Narrow" : "")));
        setTitle("Sample Types");
        setTitleHref(new ActionURL(ExperimentController.ListSampleTypesAction.class, viewContext.getContainer()));
        setShowDetailsColumn(false);

        if (_narrow)
        {
            setButtonBarPosition(DataRegion.ButtonBarPosition.NONE);
            setShowSurroundingBorder(false);
        }
        else
        {
            setShowExportButtons(false);
            setShowBorders(true);
            setShadeAlternatingRows(true);
        }
        setAllowableContainerFilterTypes(ContainerFilter.Type.Current, ContainerFilter.Type.CurrentPlusProjectAndShared);
    }

    private QuerySettings createQuerySettings(ViewContext portalCtx, String dataRegionName)
    {
        UserSchema schema = getSchema();
        QuerySettings settings = schema.getSettings(portalCtx, dataRegionName, ExpSchema.TableType.SampleSets.toString());
        if (_narrow)
        {
            settings.setViewName("NameOnly");
        }
        if (settings.getContainerFilterName() == null)
        {
            settings.setContainerFilterName(ContainerFilter.CurrentPlusProjectAndShared.class.getSimpleName());
        }
        settings.getBaseSort().insertSortColumn(FieldKey.fromParts("Name"));
        return settings;
    }

    @Override
    protected void populateButtonBar(DataView view, ButtonBar bar)
    {
        super.populateButtonBar(view, bar);

        ActionURL deleteURL = new ActionURL(ExperimentController.DeleteSampleTypesAction.class, getContainer());
        deleteURL.addReturnURL(getViewContext().getActionURL());

        ActionButton deleteButton = new ActionButton(ExperimentController.DeleteSampleTypesAction.class, "Delete", ActionButton.Action.GET);
        deleteButton.setDisplayPermission(DesignSampleTypePermission.class);
        deleteButton.setIconCls("trash");
        deleteButton.setURL(deleteURL);
        deleteButton.setActionType(ActionButton.Action.POST);
        deleteButton.setRequiresSelection(true);
        bar.add(deleteButton);

        ActionURL urlInsert = new ActionURL(ExperimentController.EditSampleTypeAction.class, getContainer());
        urlInsert.addReturnURL(getViewContext().getActionURL());
        ActionButton createNewButton = new ActionButton(urlInsert, "New Sample Type", ActionButton.Action.LINK);
        createNewButton.setDisplayPermission(DesignSampleTypePermission.class);
        createNewButton.setURL(urlInsert);
        bar.add(createNewButton);

        ActionURL showAllURL = new ActionURL(ExperimentController.ShowAllMaterialsAction.class, getContainer());
        ActionButton showAllButton = new ActionButton(showAllURL, "Show All Materials");
        showAllButton.setDisplayPermission(ReadPermission.class);
        bar.add(showAllButton);

        if (SampleStatusService.get().supportsSampleStatus())
        {
            ActionURL manageSampleStatusesURL = new ActionURL("experiment", "manageSampleStatuses", getContainer());
            ActionButton manageSampleStatusesButton = new ActionButton(manageSampleStatusesURL, "Manage Sample Statuses");
            manageSampleStatusesButton.setDisplayPermission(AdminPermission.class);
            bar.add(manageSampleStatusesButton);
        }

//      Deferred--Uncomment if supporting SampleType-level links is desired
//        StudyUrls studyUrls = PageFlowUtil.urlProvider(StudyUrls.class);
//        if (studyUrls != null)
//        {
//            ActionURL linkToStudyURL = studyUrls.getLinkToStudyURL(getContainer(), (ExpSampleType)null);
//            linkToStudyURL.addParameter("sampleTypeIds", true);
//            ActionButton linkToStudyButton = new ActionButton(linkToStudyURL, "Link to Study");
//            linkToStudyButton.setDisplayPermission(InsertPermission.class);
//            bar.add(linkToStudyButton);
//        }
    }

    @Override
    protected void renderView(Object model, HttpServletRequest request, HttpServletResponse response) throws Exception
    {
        PrintWriter out = response.getWriter();
        if (_errorMessage != null)
        {
            out.write("<font class=\"labkey-error\">" + PageFlowUtil.filter(_errorMessage) + "</font><br>");
        }
        super.renderView(model, request, response);
    }

    public void setErrorMessage(String errorMessage)
    {
        _errorMessage = errorMessage;
    }

}
