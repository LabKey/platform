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

            ParticipantClassification classification = ParticipantListManager.setParticipantClassification(getUser(), form);

            resp.put("success", true);
            resp.put("classification", classification.toJSON());

            return resp;
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class GetParticipantClassification extends ApiAction<ParticipantClassification>
    {
        @Override
        public ApiResponse execute(ParticipantClassification form, BindException errors) throws Exception
        {
            ApiSimpleResponse resp = new ApiSimpleResponse();

            ParticipantClassification classification = ParticipantListManager.getParticipantClassification(getContainer(), form.getLabel());

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

            ParticipantClassification[] classifications = ParticipantListManager.getParticipantClassifications(getContainer());
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

                ParticipantClassification[] defs = ParticipantListManager.getParticipantClassifications(filter);
                if (defs.length == 1)
                    classification = defs[0];
            }
            ParticipantListManager.deleteParticipantClassification(getUser(), classification);
            resp.put("success", true);

            return resp;
        }
    }
}
