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
import org.labkey.api.action.ApiAction;
import org.labkey.api.action.ApiResponse;
import org.labkey.api.action.ApiSimpleResponse;
import org.labkey.api.action.MutatingApiAction;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.security.RequiresPermissionClass;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.study.model.ParticipantClassification;
import org.labkey.study.model.ParticipantListManager;
import org.springframework.validation.BindException;

/**
 * Created by IntelliJ IDEA.
 * User: klum
 * Date: May 30, 2011
 * Time: 2:58:38 PM
 */
public class ParticipantListController extends BaseStudyController
{
    private static final ActionResolver ACTION_RESOLVER = new DefaultActionResolver(ParticipantListController.class);

    public ParticipantListController()
    {
        super();
        setActionResolver(ACTION_RESOLVER);
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class CreateParticipantClassification extends MutatingApiAction<ParticipantClassification>
    {
        @Override
        public ApiResponse execute(ParticipantClassification form, BindException errors) throws Exception
        {
            ApiSimpleResponse resp = new ApiSimpleResponse();

            form.setContainer(getContainer().getId());

            ParticipantClassification classification = ParticipantListManager.getInstance().setParticipantClassification(getUser(), form);

            resp.put("success", true);
            resp.put("classification", classification.toJSON());

            return resp;
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class UpdateParticipantClassification extends MutatingApiAction<ParticipantClassification>
    {
        @Override
        public ApiResponse execute(ParticipantClassification form, BindException errors) throws Exception
        {
            ApiSimpleResponse resp = new ApiSimpleResponse();

            if (form.isNew())
            {
                throw new IllegalArgumentException("The specified classification does not exist, you must pass in the RowId");
            }

            SimpleFilter filter = new SimpleFilter("RowId", form.getRowId());
            ParticipantClassification[] defs = ParticipantListManager.getInstance().getParticipantClassifications(filter);
            if (defs.length == 1)
            {
                form.copySpecialFields(defs[0]);
                ParticipantClassification classification = ParticipantListManager.getInstance().setParticipantClassification(getUser(), form);

                resp.put("success", true);
                resp.put("classification", classification.toJSON());

                return resp;
            }
            else
                throw new RuntimeException("Unable to update the classification with rowId: " + form.getRowId());
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class GetParticipantClassification extends ApiAction<ParticipantClassification>
    {
        @Override
        public ApiResponse execute(ParticipantClassification form, BindException errors) throws Exception
        {
            ApiSimpleResponse resp = new ApiSimpleResponse();

            ParticipantClassification classification = ParticipantListManager.getInstance().getParticipantClassification(getContainer(), form.getLabel());

            resp.put("success", true);
            resp.put("classification", classification.toJSON());

            return resp;
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class GetParticipantClassifications extends ApiAction<Object>
    {
        @Override
        public ApiResponse execute(Object form, BindException errors) throws Exception
        {
            ApiSimpleResponse resp = new ApiSimpleResponse();

            ParticipantClassification[] classifications = ParticipantListManager.getInstance().getParticipantClassifications(getContainer());
            JSONArray defs = new JSONArray();

            for (ParticipantClassification pc : classifications)
            {
                defs.put(pc.toJSON());
            }
            resp.put("success", true);
            resp.put("classifications", defs);

            return resp;
        }
    }

    @RequiresPermissionClass(AdminPermission.class)
    public class DeleteParticipantClassification extends MutatingApiAction<ParticipantClassification>
    {
        @Override
        public ApiResponse execute(ParticipantClassification form, BindException errors) throws Exception
        {
            ApiSimpleResponse resp = new ApiSimpleResponse();

            ParticipantClassification classification = form;

            if (form.isNew())
            {
                // try to match a single classification by label/container
                SimpleFilter filter = new SimpleFilter("Container", getContainer());
                filter.addCondition("Label", form.getLabel());

                ParticipantClassification[] defs = ParticipantListManager.getInstance().getParticipantClassifications(filter);
                if (defs.length == 1)
                    classification = defs[0];
            }
            ParticipantListManager.getInstance().deleteParticipantClassification(getUser(), classification);
            resp.put("success", true);

            return resp;
        }
    }
}
