/*
 * Copyright (c) 2019 LabKey Corporation
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

package org.labkey.assay;

import org.labkey.api.action.ApiSimpleResponse;
import org.labkey.api.action.MutatingApiAction;
import org.labkey.api.action.ReturnUrlForm;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.assay.AssayQCService;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.audit.permissions.CanSeeAuditLogPermission;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.DataRegionSelection;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.qc.QCState;
import org.labkey.api.qc.QCStateManager;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.LimitedUser;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.QCAnalystPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.roles.CanSeeAuditLogRole;
import org.labkey.api.security.roles.Role;
import org.labkey.api.security.roles.RoleManager;
import org.labkey.api.study.actions.AssayRunUploadForm;
import org.labkey.api.study.actions.BaseAssayAction;
import org.labkey.api.study.assay.AssayProvider;
import org.labkey.api.study.assay.AssayUrls;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.VBox;
import org.labkey.api.view.WebPartView;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AssayController2 extends SpringActionController
{
    private static final DefaultActionResolver _actionResolver = new DefaultActionResolver(AssayController2.class);
    public static final String NAME = "assay2";

    public AssayController2()
    {
        setActionResolver(_actionResolver);
    }

    public static class UpdateQCStateForm extends ReturnUrlForm
    {
        private Integer _state;
        private String _comment;
        private Set<Integer> _runs;

        public Integer getState()
        {
            return _state;
        }

        public void setState(Integer state)
        {
            _state = state;
        }

        public String getComment()
        {
            return _comment;
        }

        public void setComment(String comment)
        {
            _comment = comment;
        }

        public Set<Integer> getRuns()
        {
            return _runs;
        }

        public void setRuns(Set<Integer> runs)
        {
            _runs = runs;
        }

        public void setRun(Integer run)
        {
            if (_runs == null)
                _runs = new HashSet<>();
            _runs.add(run);
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class QCStateAction extends SimpleViewAction<UpdateQCStateForm>
    {
        @Override
        public ModelAndView getView(UpdateQCStateForm form, BindException errors) throws Exception
        {
            if (form.getRuns() == null)
            {
                if (DataRegionSelection.hasSelected(getViewContext()))
                    form.setRuns(DataRegionSelection.getSelectedIntegers(getViewContext(), true));
            }
            VBox view = new VBox();

            if (form.getRuns() != null && !form.getRuns().isEmpty())
            {
                if (getContainer().hasPermission(getUser(), QCAnalystPermission.class))
                {
                    JspView jspView = new JspView<>("/org/labkey/assay/view/updateQCState.jsp", form, errors);
                    jspView.setFrame(WebPartView.FrameType.PORTAL);
                    view.addView(jspView);
                }

                if (form.getRuns().size() == 1)
                {
                    // construct the audit log query view
                    User user = getUser();
                    if (!getContainer().hasPermission(user, CanSeeAuditLogPermission.class))
                    {
                        Set<Role> contextualRoles = new HashSet<>(user.getStandardContextualRoles());
                        contextualRoles.add(RoleManager.getRole(CanSeeAuditLogRole.class));
                        user = new LimitedUser(user, user.getGroups(), contextualRoles, false);
                    }

                    UserSchema schema = AuditLogService.getAuditLogSchema(user, getContainer());
                    ExpRun run = ExperimentService.get().getExpRun(form.getRuns().stream().findFirst().get());
                    if (run != null && schema != null)
                    {
                        QuerySettings settings = new QuerySettings(getViewContext(), "auditHistory");
                        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("RunLsid"), run.getLSID());
                        filter.addCondition(FieldKey.fromParts("QCState"), null, CompareType.NONBLANK);

                        settings.setBaseFilter(filter);
                        settings.setQueryName("ExperimentAuditEvent");

                        QueryView auditView = schema.createView(getViewContext(), settings, errors);
                        auditView.setTitle("QC History");

                        view.addView(auditView);
                    }
                }
                return view;
            }
            else
                return new HtmlView("No runs have been selected to update");
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Change QC State");
        }
    }

    @RequiresPermission(QCAnalystPermission.class)
    public class UpdateQCStateAction extends MutatingApiAction<UpdateQCStateForm>
    {
        @Override
        public void validateForm(UpdateQCStateForm form, Errors errors)
        {
            if (form.getState() == null)
                errors.reject(ERROR_MSG, "QC State cannot be blank");
            if (form.getRuns().isEmpty())
                errors.reject(ERROR_MSG, "No runs were selected to update their QC State");
        }

        @Override
        public ApiSimpleResponse execute(UpdateQCStateForm form, BindException errors) throws Exception
        {
            ApiSimpleResponse response = new ApiSimpleResponse();
            if (form.getRuns() != null)
            {
                AssayQCService svc = AssayQCService.getProvider();
                ExpRun run = null;

                for (int id : form.getRuns())
                {
                    // just get the first run
                    run = ExperimentService.get().getExpRun(id);
                    if (run != null)
                        break;
                }

                if (run != null)
                {
                    QCState state = QCStateManager.getInstance().getQCStateForRowId(getContainer(), form.getState());
                    if (state != null)
                        svc.setQCStates(run.getProtocol(), getContainer(), getUser(), List.copyOf(form.getRuns()), state, form.getComment());
                }
                response.put("success", true);
            }
            return response;
        }
    }

    @RequiresPermission(InsertPermission.class)
    public class ModuleAssayUploadAction extends BaseAssayAction<AssayRunUploadForm>
    {
        private ExpProtocol _protocol;

        @Override
        public ModelAndView getView(AssayRunUploadForm form, BindException errors)
        {
            if (!PipelineService.get().hasValidPipelineRoot(getContainer()))
                throw new NotFoundException("Pipeline root must be configured before uploading assay files");

            _protocol = form.getProtocol();

            AssayProvider ap = form.getProvider();
            if (!(ap instanceof ModuleAssayProvider))
                throw new NotFoundException("Assay must be a ModuleAssayProvider, but assay design " + _protocol.getName() + " was of type '" + ap.getName() + "', implemented by " + ap.getClass().getName());
            ModuleAssayProvider provider = (ModuleAssayProvider) ap;
            return provider.createUploadView(form);
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            Container c = getContainer();
            ActionURL batchListURL = PageFlowUtil.urlProvider(AssayUrls.class).getAssayBatchesURL(c, _protocol, null);

            return super.appendNavTrail(root)
                    .addChild(_protocol.getName() + " Batches", batchListURL)
                    .addChild("Data Import");
        }
    }
}