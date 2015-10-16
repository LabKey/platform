/*
 * Copyright (c) 2015 LabKey Corporation
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
package org.labkey.announcements;

import org.json.JSONObject;
import org.labkey.announcements.model.TourManager;
import org.labkey.announcements.model.TourModel;
import org.labkey.announcements.query.AnnouncementSchema;
import org.labkey.api.action.ApiSimpleResponse;
import org.labkey.api.action.MutatingApiAction;
import org.labkey.api.action.SimpleApiJsonForm;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.data.Container;
import org.labkey.api.query.QueryForm;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.security.ActionNames;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

/**
 * Created by Marty on 1/19/2015.
 */
public class ToursController extends SpringActionController
{
    private static final DefaultActionResolver _actionResolver = new DefaultActionResolver(ToursController.class);

    public ToursController() throws Exception
    {
        setActionResolver(_actionResolver);
    }

    // Anyone with read permission can attempt to view the list.  ToursTable will do further permission checking.
    @RequiresPermission(ReadPermission.class)
    @SuppressWarnings("UnusedDeclaration")
    public class BeginAction extends SimpleViewAction<QueryForm>
    {
        @Override
        public ModelAndView getView(QueryForm queryForm, BindException errors) throws Exception
        {
            queryForm.setSchemaName(AnnouncementSchema.SCHEMA_NAME);
            queryForm.setQueryName(AnnouncementSchema.TOURS_TABLE_NAME);

            QuerySettings settings = queryForm.getQuerySettings();
            settings.setAllowChooseView(false);
            settings.setAllowChooseQuery(false);
            QueryView view = QueryView.create(queryForm, errors);
            view.setShowBorders(true);
            view.setShowSurroundingBorder(true);
            view.setTitle("Tours");

            return view;
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }

    public static ActionURL getEditTourURL(Container c)
    {
        return new ActionURL(EditTourAction.class, c);
    }

    @ActionNames("edit, editTour")
    @RequiresPermission(ReadPermission.class) //will check below
    public class EditTourAction extends SimpleViewAction<EditTourForm>
    {
        @Override
        public ModelAndView getView(EditTourForm editTourForm, BindException errors) throws Exception
        {
            TourModel model;
            if (null != editTourForm.getRowid())
                model = TourManager.getTour(getContainer(), Integer.parseInt(editTourForm.getRowid()));
            else
                model = new TourModel();

            if (null == model)
                model = new TourModel();

            return new JspView<>("/org/labkey/announcements/view/editTour.jsp", model);
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Tour Builder");
        }
    }

    @ActionNames("tours, saveTour")
    @RequiresPermission(ReadPermission.class) //will check below
    public class SaveTourAction extends MutatingApiAction<SimpleApiJsonForm>
    {
        @Override
        public void validateForm(SimpleApiJsonForm form, Errors errors)
        {
            TourModel model;
            JSONObject json = form.getJsonObject();

            if (Integer.parseInt(json.getString("rowId")) < 0)
                model = new TourModel();
            else
                model = TourManager.getTourFromDb(getContainer(), Integer.parseInt(json.getString("rowId")));

            if( null == model)
                model = new TourModel();

            model.setTitle(json.getString("title"));
            model.setDescription(json.getString("description"));
            model.setMode(Integer.parseInt(json.getString("mode")));
            model.setJson(json.getString("tour"));

            try
            {
                TourModel ret;
                if (Integer.parseInt(json.getString("rowId")) < 0)
                {
                    ret = TourManager.insertTour(getContainer(), getUser(), model);
                }
                else
                {
                    ret = TourManager.updateTour(getUser(), model);
                }
                json.put("rowId", ret.getRowId());
                form.bindProperties(json);
            }
            catch (Exception e)
            {
                errors.reject(ERROR_MSG, "There was an error while saving. Your changes may not have been saved.");
                errors.reject(ERROR_MSG, e.getMessage() == null ? e.toString() : e.getMessage());
            }

        }

        @Override
        public Object execute(SimpleApiJsonForm form, BindException errors) throws Exception
        {
            JSONObject json = form.getJsonObject();
            ApiSimpleResponse response = new ApiSimpleResponse();
            response.put("rowId", json.getString("rowId"));
            response.put("success", true);
            return response;
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class GetTourAction extends MutatingApiAction<SimpleApiJsonForm>
    {

        @Override
        public Object execute(SimpleApiJsonForm form, BindException errors) throws Exception
        {
            JSONObject json = form.getJsonObject();
            ApiSimpleResponse response = new ApiSimpleResponse();

            response.put("mode", TourManager.getTourMode(getContainer(), json.getInt("id")));
            response.put("json", TourManager.getTourJson(getContainer(), json.getInt("id")));
            response.put("success", true);
            return response;
        }
    }

    public static class EditTourForm
    {
        private String _rowid;

        public String getRowid()
        {
            return _rowid;
        }

        public void setRowid(String rowid)
        {
            _rowid = rowid;
        }
    }
}
