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
import org.labkey.api.security.RequiresPermissionClass;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.study.model.ParticipantCategory;
import org.labkey.study.model.ParticipantGroupManager;
import org.springframework.validation.BindException;

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
}
