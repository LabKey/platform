/*
 * Copyright (c) 2007-2008 LabKey Corporation
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

package org.labkey.api.study.actions;

import org.apache.commons.beanutils.ConversionException;
import org.apache.commons.beanutils.ConvertUtils;
import org.apache.struts.action.ActionErrors;
import org.apache.struts.action.ActionMessage;
import org.labkey.api.data.ActionButton;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DataRegionSelection;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.ACL;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.study.TimepointType;
import org.labkey.api.study.assay.AssayProvider;
import org.labkey.api.study.assay.AssayPublishKey;
import org.labkey.api.study.assay.AssayPublishService;
import org.labkey.api.study.assay.AssayService;
import org.labkey.api.study.query.PublishRunDataQueryView;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.*;
import org.springframework.validation.BindException;
import org.springframework.web.servlet.ModelAndView;

import java.util.*;

/**
 * User: brittp
 * Date: Jul 27, 2007
 * Time: 11:02:04 AM
 */
@RequiresPermission(ACL.PERM_INSERT)
public class PublishConfirmAction extends BaseAssayAction<PublishConfirmAction.PublishConfirmForm>

{
    private ExpProtocol _protocol;

    public static class PublishConfirmForm extends ProtocolIdForm implements DataRegionSelection.DataSelectionKeyForm
    {
        private String _targetStudy;
        private String[] _participantId;
        private String[] _visitId;
        private String[] _date;
        private boolean _attemptPublish;
        private boolean _validate;
        private String _dataRegionSelectionKey;
        private String _returnURL;

        public String getReturnURL()
        {
            return _returnURL;
        }

        public void setReturnURL(String returnURL)
        {
            _returnURL = returnURL;
        }

        public String getDataRegionSelectionKey()
        {
            return _dataRegionSelectionKey;
        }

        public void setDataRegionSelectionKey(String dataRegionSelectionKey)
        {
            _dataRegionSelectionKey = dataRegionSelectionKey;
        }

        public String getTargetStudy()
        {
            return _targetStudy;
        }

        public void setTargetStudy(String targetStudy)
        {
            _targetStudy = targetStudy;
        }

        public String[] getParticipantId()
        {
            return _participantId;
        }

        public void setParticipantId(String[] participantId)
        {
            _participantId = participantId;
        }

        public String[] getVisitId()
        {
            return _visitId;
        }

        public void setVisitId(String[] visitId)
        {
            _visitId = visitId;
        }
        
        public boolean isAttemptPublish()
        {
            return _attemptPublish;
        }


        public String[] getDate()
        {
            return _date;
        }

        public void setDate(String[] date)
        {
            _date = date;
        }

        public void setAttemptPublish(boolean attemptPublish)
        {
            _attemptPublish = attemptPublish;
        }

        public boolean isValidate()
        {
            return _validate;
        }

        public void setValidate(boolean validate)
        {
            _validate = validate;
        }
    }

    public ModelAndView getView(PublishConfirmForm publishConfirmForm, BindException errors) throws Exception
    {
        ViewContext context = getViewContext();
        _protocol = getProtocol(publishConfirmForm);
        AssayProvider provider = AssayService.get().getProvider(_protocol);
        List<Integer> objectIds = getCheckboxIds(false);
        Container targetStudy = ContainerManager.getForId(publishConfirmForm.getTargetStudy());
        Map<Object, String> postedVisits = null;
        Map<Object, String> postedPtids = null;
        boolean dateBased = AssayPublishService.get().getTimepointType(targetStudy) == TimepointType.DATE;
        // todo: this isn't a great way to determine if this is our final post, but it'll do for now:
        if (publishConfirmForm.isAttemptPublish())
        {
            ActionErrors actionErrors = PageFlowUtil.getActionErrors(getViewContext().getRequest(), true);
            postedVisits = new HashMap<Object, String>();
            postedPtids = new HashMap<Object, String>();
            Set<AssayPublishKey> publishData = new HashSet<AssayPublishKey>();
            String[] participantIds = publishConfirmForm.getParticipantId();
            String[] visitIds = publishConfirmForm.getVisitId();
            String[] dates = publishConfirmForm.getDate();
            boolean missingPtid = false;
            boolean missingVisitId = false;
            boolean missingDate = false;
            boolean badVisitIds = false;
            boolean badDates = false;
            for (int i = 0; i < objectIds.size(); i++)
            {
                Integer objectId = objectIds.get(i);
                String participantId = participantIds != null && participantIds.length > i ? participantIds[i] : null;
                if (participantId == null || participantId.trim().length() == 0)
                    missingPtid = true;
                else
                    participantId = participantId.trim();

                if (AssayPublishService.get().getTimepointType(targetStudy) == TimepointType.VISIT)
                {
                    String visitIdStr = visitIds != null && visitIds.length > i ? visitIds[i] : null;
                    Float visitId = null;
                    if (visitIdStr == null || visitIdStr.trim().length() == 0)
                        missingVisitId = true;
                    else
                    {
                        visitIdStr = visitIdStr.trim();
                        try
                        {
                            visitId = Float.parseFloat(visitIdStr);
                        }
                        catch (NumberFormatException e)
                        {
                            badVisitIds = true;
                        }
                    }
                    postedPtids.put(objectId, participantId);
                    postedVisits.put(objectId, visitIdStr);
                    if (visitId != null)
                        publishData.add(new AssayPublishKey(participantId,  visitId.floatValue(),  objectId));
                }
                else // TimepointType.DATE
                {
                    String dateStr = dates != null && dates.length > i ? dates[i] : null;
                    Date date = null;
                    if (dateStr == null || dateStr.trim().length() == 0)
                        missingDate = true;
                    else
                    {
                        dateStr = dateStr.trim();
                        try
                        {
                            date = (Date) ConvertUtils.convert(dateStr, Date.class);
                        }
                        catch (ConversionException e)
                        {
                            badDates = true;
                        }
                    }
                    postedPtids.put(objectId, participantId);
                    postedVisits.put(objectId, dateStr);
                    if (date != null)
                        publishData.add(new AssayPublishKey(participantId,  date,  objectId));
                }
            }
            if (missingPtid)
                actionErrors.add("main", new ActionMessage("Error", "You must specify a Participant ID for all rows."));
            if (missingVisitId)
                actionErrors.add("main", new ActionMessage("Error", "You must specify a Visit ID for all rows."));
            if (badVisitIds)
                actionErrors.add("main", new ActionMessage("Error", "Visit IDs must be numbers."));
            if (missingDate || badDates)
                actionErrors.add("main", new ActionMessage("Error", "You must specify a Date for all rows."));

            if (actionErrors.isEmpty() && !publishConfirmForm.isValidate())
            {
                List<String> publishErrors = new ArrayList<String>();
                ActionURL successURL  = provider.publish(context.getUser(), _protocol, targetStudy, publishData, publishErrors);
                if (publishErrors.isEmpty())
                {
                    DataRegionSelection.clearAll(getViewContext(), publishConfirmForm.getDataRegionSelectionKey());
                    HttpView.throwRedirect(successURL);
                }
                for (String publishError : publishErrors)
                {
                    actionErrors.add("main", new ActionMessage("Error", publishError));
                }
            }
        }
        String name = provider.getRunDataTableName(_protocol);
        UserSchema schema = AssayService.get().createSchema(context.getUser(), getContainer());
        QuerySettings settings = new QuerySettings(context, name);
        settings.setSchemaName(schema.getSchemaName());
        settings.setQueryName(name);
        settings.setAllowChooseView(false);
        PublishRunDataQueryView view = new PublishRunDataQueryView(_protocol, context, settings,
                objectIds, targetStudy, postedVisits, postedPtids);
        List<ActionButton> buttons = new ArrayList<ActionButton>();
        String returnURL;
        if (publishConfirmForm.getReturnURL() != null)
        {
            returnURL = publishConfirmForm.getReturnURL();
        }
        else
        {
            returnURL = getSummaryLink(_protocol).addParameter("clearDataRegionSelectionKey", publishConfirmForm.getDataRegionSelectionKey()).toString();
        }
        ActionButton cancelButton = new ActionButton("Cancel");
        cancelButton.setURL(returnURL);
        buttons.add(cancelButton);
        ActionURL publishURL = getPublishHandlerURL(_protocol, provider);
        publishURL.replaceParameter("validate", "true");
        ActionButton validateButton = new ActionButton(publishURL.getLocalURIString(), "Validate");
        buttons.add(validateButton);
        publishURL.replaceParameter("validate", "false");
        ActionButton publishButton = new ActionButton(publishURL.getLocalURIString(), "Copy to Study");
        buttons.add(publishButton);
        view.setButtons(buttons);
        return new VBox(new ActionErrorsView(),
                        new HtmlView("Note: Participant and " + (dateBased ? "Dates" : "Visit IDs") + " are required for all rows."), //FIX: 3998
                        view);
    }


    protected ActionURL getPublishHandlerURL(ExpProtocol protocol, AssayProvider provider)
    {
        return AssayService.get().getPublishConfirmURL(getContainer(), protocol).deleteParameters();
    }

    public NavTree appendNavTrail(NavTree root)
    {
        NavTree result = super.appendNavTrail(root);
        result.addChild(_protocol.getName(), AssayService.get().getAssayRunsURL(getContainer(), _protocol));
        result.addChild("Copy to Study: " + _protocol.getName() + ": Verify Data");
        return result;
    }
}
