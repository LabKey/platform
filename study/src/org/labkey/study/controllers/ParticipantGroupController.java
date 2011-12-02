/*
 * Copyright (c) 2011 LabKey Corporation
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
package org.labkey.study.controllers;

import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.action.ApiAction;
import org.labkey.api.action.ApiResponse;
import org.labkey.api.action.ApiSimpleResponse;
import org.labkey.api.action.MutatingApiAction;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Table;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.QueryForm;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.RequiresPermissionClass;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.study.model.ParticipantCategory;
import org.labkey.study.model.ParticipantGroup;
import org.labkey.study.model.ParticipantGroupManager;
import org.springframework.validation.BindException;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Created by IntelliJ IDEA.
 * User: klum
 * Date: May 30, 2011
 * Time: 2:58:38 PM
 */
public class ParticipantGroupController extends BaseStudyController
{
    private static final ActionResolver ACTION_RESOLVER = new DefaultActionResolver(ParticipantGroupController.class);

    public ParticipantGroupController()
    {
        super();
        setActionResolver(ACTION_RESOLVER);
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class CreateParticipantCategory extends MutatingApiAction<ParticipantCategorySpecification>
    {
        @Override
        public ApiResponse execute(ParticipantCategorySpecification form, BindException errors) throws Exception
        {
            ApiSimpleResponse resp = new ApiSimpleResponse();

            form.setContainer(getContainer().getId());

            ParticipantCategory category = ParticipantGroupManager.getInstance().setParticipantCategory(getContainer(), getUser(), form, form.getParticipantIds());

            resp.put("success", true);
            resp.put("category", category.toJSON());

            return resp;
        }
    }

    /**
     * Bean used to create and update participant categories
     */
    public static class ParticipantCategorySpecification extends ParticipantCategory
    {
        private String[] _participantIds = new String[0];

        public String[] getParticipantIds()
        {
            return _participantIds;
        }

        public void setParticipantIds(String[] participantIds)
        {
            _participantIds = participantIds;
        }

        public void fromJSON(JSONObject json)
        {
            super.fromJSON(json);

            if (json.has("participantIds"))
            {
                JSONArray ptids = json.getJSONArray("participantIds");
                String[] ids = new String[ptids.length()];

                for (int i=0; i < ptids.length(); i++)
                {
                    ids[i] = ptids.getString(i);
                }
                setParticipantIds(ids);
            }
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class UpdateParticipantCategory extends MutatingApiAction<ParticipantCategorySpecification>
    {
        @Override
        public ApiResponse execute(ParticipantCategorySpecification form, BindException errors) throws Exception
        {
            ApiSimpleResponse resp = new ApiSimpleResponse();

            if (form.isNew())
            {
                throw new IllegalArgumentException("The specified category does not exist, you must pass in the RowId");
            }

            SimpleFilter filter = new SimpleFilter("RowId", form.getRowId());
            ParticipantCategory[] defs = ParticipantGroupManager.getInstance().getParticipantCategories(getContainer(), getUser(), filter);
            if (defs.length == 1)
            {
                form.copySpecialFields(defs[0]);
                ParticipantCategory category = ParticipantGroupManager.getInstance().setParticipantCategory(getContainer(), getUser(), form, form.getParticipantIds());

                resp.put("success", true);
                resp.put("category", category.toJSON());

                return resp;
            }
            else
                throw new RuntimeException("Unable to update the category with rowId: " + form.getRowId());
        }
    }

    enum Modification {ADD, REMOVE};

    private abstract class ModifyCategoryParticipants extends MutatingApiAction<ParticipantCategorySpecification>
    {
        public ApiResponse execute(ParticipantCategorySpecification form, BindException errors, Modification modification) throws Exception
        {
            ApiSimpleResponse resp = new ApiSimpleResponse();

            if (form.isNew())
            {
                throw new IllegalArgumentException("The specified category does not exist, you must pass in the RowId");
            }

            SimpleFilter filter = new SimpleFilter("RowId", form.getRowId());
            ParticipantCategory[] defs = ParticipantGroupManager.getInstance().getParticipantCategories(getContainer(), getUser(), filter);
            if (defs.length == 1)
            {
                ParticipantCategory def = defs[0];
                form.copySpecialFields(def);

                ParticipantCategory category;

                if (modification == Modification.ADD)
                    category = ParticipantGroupManager.getInstance().addCategoryParticipants(getContainer(), getUser(), def, form.getParticipantIds());
                else
                    category = ParticipantGroupManager.getInstance().removeCategoryParticipants(getContainer(), getUser(), def, form.getParticipantIds());

                resp.put("success", true);
                resp.put("category", category.toJSON());

                return resp;
            }
            else
                throw new RuntimeException("Unable to update the category with rowId: " + form.getRowId());
        }

    }

    @RequiresPermissionClass(ReadPermission.class)
    public class AddParticipantsToCategory extends ModifyCategoryParticipants
    {
        @Override
        public ApiResponse execute(ParticipantCategorySpecification form, BindException errors) throws Exception
        {
            return super.execute(form, errors, Modification.ADD);
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class RemoveParticipantsFromCategory extends ModifyCategoryParticipants
    {
        @Override
        public ApiResponse execute(ParticipantCategorySpecification form, BindException errors) throws Exception
        {
            return super.execute(form, errors, Modification.REMOVE);
        }
    }


    @RequiresPermissionClass(ReadPermission.class)
    public class GetParticipantCategory extends ApiAction<ParticipantCategory>
    {
        @Override
        public ApiResponse execute(ParticipantCategory form, BindException errors) throws Exception
        {
            ApiSimpleResponse resp = new ApiSimpleResponse();

            ParticipantCategory category = ParticipantGroupManager.getInstance().getParticipantCategory(getContainer(), getUser(), form.getLabel());

            resp.put("success", true);
            resp.put("category", category.toJSON());

            return resp;
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class GetParticipantCategories extends ApiAction<Object>
    {
        @Override
        public ApiResponse execute(Object form, BindException errors) throws Exception
        {
            ApiSimpleResponse resp = new ApiSimpleResponse();

            ParticipantCategory[] categories = ParticipantGroupManager.getInstance().getParticipantCategories(getContainer(), getUser());
            JSONArray defs = new JSONArray();

            for (ParticipantCategory pc : categories)
            {
                defs.put(pc.toJSON());
            }
            resp.put("success", true);
            resp.put("categories", defs);

            return resp;
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class DeleteParticipantCategory extends MutatingApiAction<ParticipantCategory>
    {
        @Override
        public ApiResponse execute(ParticipantCategory form, BindException errors) throws Exception
        {
            ApiSimpleResponse resp = new ApiSimpleResponse();

            ParticipantCategory category = form;

            if (form.isNew())
            {
                // try to match a single category by label/container
                SimpleFilter filter = new SimpleFilter("Container", getContainer());
                filter.addCondition("Label", form.getLabel());

                ParticipantCategory[] defs = ParticipantGroupManager.getInstance().getParticipantCategories(getContainer(), getUser(), filter);
                if (defs.length == 1)
                    category = defs[0];
            }
            else
            {
                SimpleFilter filter = new SimpleFilter("RowId", form.getRowId());
                ParticipantCategory[] defs = ParticipantGroupManager.getInstance().getParticipantCategories(getContainer(), getUser(), filter);
                if (defs.length == 1)
                    category = defs[0];
            }

            ParticipantGroupManager.getInstance().deleteParticipantCategory(getContainer(), getUser(), category);
            resp.put("success", true);

            return resp;
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class GetParticipantsFromSelectionAction extends MutatingApiAction<ParticipantSelection>
    {
        @Override
        public ApiResponse execute(ParticipantSelection form, BindException errors) throws Exception
        {
            ApiSimpleResponse resp = new ApiSimpleResponse();
            try
            {
                Set<String> ptids = new LinkedHashSet<String>();

                QuerySettings settings = form.getQuerySettings();
                settings.setMaxRows(Table.ALL_ROWS);

                QuerySchema querySchema = DefaultSchema.get(getUser(), getContainer()).getSchema(form.getSchemaName().toString());
                QueryView view = ((UserSchema)querySchema).createView(getViewContext(), settings, errors);

                if (view != null)
                {
                    if (form.isSelectAll())
                    {
                        for (String ptid : StudyController.generateParticipantList(view))
                            ptids.add(ptid);
                    }
                    else
                    {
                        List<String> participants = ParticipantGroupManager.getInstance().getParticipantsFromSelection(getContainer(), view, Arrays.asList(form.getSelections()));
                        ptids.addAll(participants);
                    }
                }
                resp.put("ptids", ptids);
                resp.put("success", true);
            }
            catch (SQLException e)
            {
                errors.reject(ERROR_MSG, e.getMessage());
            }
            return resp;
        }
    }

    public static class ParticipantSelection extends QueryForm
    {
        private String[] _selections;
        private boolean _selectAll;
        private String _requestURL;

        public String getRequestURL()
        {
            return _requestURL;
        }

        public void setRequestURL(String requestURL)
        {
            _requestURL = requestURL;
        }

        public String[] getSelections()
        {
            return _selections;
        }

        public void setSelections(String[] selections)
        {
            _selections = selections;
        }

        public boolean isSelectAll()
        {
            return _selectAll;
        }

        public void setSelectAll(boolean selectAll)
        {
            _selectAll = selectAll;
        }
    }
}
