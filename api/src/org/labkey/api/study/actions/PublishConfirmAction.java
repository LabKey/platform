/*
 * Copyright (c) 2007-2010 LabKey Corporation
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
import org.labkey.api.data.ActionButton;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DataRegionSelection;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.security.RequiresPermissionClass;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.study.TimepointType;
import org.labkey.api.study.assay.*;
import org.labkey.api.study.query.PublishResultsQueryView;
import org.labkey.api.util.HString;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.ReturnURLString;
import org.labkey.api.view.*;
import org.labkey.api.view.template.AppBar;
import org.springframework.validation.BindException;
import org.springframework.web.servlet.ModelAndView;

import java.util.*;

/**
 * User: brittp
 * Date: Jul 27, 2007
 * Time: 11:02:04 AM
 */
@RequiresPermissionClass(InsertPermission.class)
public class PublishConfirmAction extends BaseAssayAction<PublishConfirmAction.PublishConfirmForm>
{
    private ExpProtocol _protocol;
    private String _targetStudyName;

    public static class PublishConfirmForm extends ProtocolIdForm implements DataRegionSelection.DataSelectionKeyForm
    {
        private String _targetStudy;
        private String[] _participantId;
        private String[] _visitId;
        private String[] _date;
        private Integer[] _objectId;
        private boolean _attemptPublish;
        private boolean _validate;
        private String _dataRegionSelectionKey;
        private ReturnURLString _returnURL;
        private String _containerFilterName;
        private PublishResultsQueryView.DefaultValueSource _defaultValueSource = PublishResultsQueryView.DefaultValueSource.Assay;

        public ReturnURLString getReturnURL()
        {
            return _returnURL;
        }

        public void setReturnURL(ReturnURLString returnURL)
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

        public Integer[] getObjectId()
        {
            return _objectId;
        }

        public void setObjectId(Integer[] objectId)
        {
            _objectId = objectId;
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

        public String getContainerFilterName()
        {
            return _containerFilterName;
        }

        public void setContainerFilterName(String containerFilterName)
        {
            _containerFilterName = containerFilterName;
        }

        public void setDefaultValueSource(String defaultValueSource)
        {
            _defaultValueSource = PublishResultsQueryView.DefaultValueSource.valueOf(defaultValueSource);
        }

        public String getDefaultValueSource()
        {
            return _defaultValueSource.toString();
        }

        public PublishResultsQueryView.DefaultValueSource getDefaultValueSourceEnum()
        {
            return _defaultValueSource;
        }
    }

    public ModelAndView getView(PublishConfirmForm publishConfirmForm, BindException errors) throws Exception
    {
        ViewContext context = getViewContext();
        _protocol = getProtocol(publishConfirmForm);
        AssayProvider provider = AssayService.get().getProvider(_protocol);
        Set<Integer> selectedObjects = new HashSet<Integer>(getCheckboxIds());
        Integer[] allObjectsArray = publishConfirmForm.getObjectId();

        List<Integer> allObjects;
        if (allObjectsArray != null) // On first post, this is empty, so use the current selection
            allObjects = Arrays.asList(allObjectsArray);
        else
            allObjects = new ArrayList<Integer>(selectedObjects);

        Container targetStudy = ContainerManager.getForId(publishConfirmForm.getTargetStudy());
        if (targetStudy == null)
        {
            throw new NotFoundException("Could not find target study");
        }
        Map<Object, String> postedVisits = null;
        Map<Object, String> postedPtids = null;
        TimepointType timepointType = AssayPublishService.get().getTimepointType(targetStudy);
        _targetStudyName = AssayPublishService.get().getStudyName(targetStudy);
        
        // todo: this isn't a great way to determine if this is our final post, but it'll do for now:
        if (publishConfirmForm.isAttemptPublish() && publishConfirmForm.getDefaultValueSourceEnum() == PublishResultsQueryView.DefaultValueSource.UserSpecified)
        {
            postedVisits = new HashMap<Object, String>();
            postedPtids = new HashMap<Object, String>();
            attemptCopy(publishConfirmForm, errors, context, provider, selectedObjects, allObjects, targetStudy, postedVisits, postedPtids, timepointType);
        }

        AssaySchema schema = AssayService.get().createSchema(context.getUser(), getContainer());
        schema.setTargetStudy(targetStudy);

        boolean mismatched = AssayPublishService.get().hasMismatchedInfo(provider, _protocol, allObjects, schema);

        // Show the form
        String name = AssayService.get().getResultsTableName(_protocol);
        QuerySettings settings = schema.getSettings(context, name, name);
        settings.setAllowChooseView(false);
        PublishResultsQueryView queryView = new PublishResultsQueryView(_protocol, schema, settings,
                allObjects, targetStudy, postedVisits, postedPtids, publishConfirmForm.getDefaultValueSourceEnum(), mismatched);

        if (publishConfirmForm.getContainerFilterName() != null)
            queryView.getSettings().setContainerFilterName(publishConfirmForm.getContainerFilterName());

        List<ActionButton> buttons = new ArrayList<ActionButton>();
        HString returnURL;
        if (publishConfirmForm.getReturnURL() != null)
        {
            returnURL = publishConfirmForm.getReturnURL();
        }
        else
        {
            returnURL = new HString(getSummaryLink(_protocol).addParameter("clearDataRegionSelectionKey", publishConfirmForm.getDataRegionSelectionKey()).toString(), false);
        }
        String script = "window.onbeforeunload = null;"; // Need to prevent a warning if the user clicks on these buttons

        ActionURL publishURL = getPublishHandlerURL(_protocol);

        publishURL.replaceParameter("defaultValueSource", PublishResultsQueryView.DefaultValueSource.UserSpecified.toString());
        publishURL.replaceParameter("validate", "false");
        ActionButton publishButton = new ActionButton(publishURL, "Copy to Study");
        publishButton.setScript(script, true);
        buttons.add(publishButton);

        publishURL.replaceParameter("validate", "true");
        ActionButton validateButton = new ActionButton(publishURL, "Re-Validate");
        validateButton.setScript(script, true);
        buttons.add(validateButton);

        if (mismatched)
        {
            publishURL.deleteParameter("validate");
            publishURL.replaceParameter("defaultValueSource", PublishResultsQueryView.DefaultValueSource.Assay.toString());
            ActionButton fromAssayButton = new ActionButton(publishURL, "Reset with Assay Data");
            buttons.add(fromAssayButton);

            publishURL.replaceParameter("defaultValueSource", PublishResultsQueryView.DefaultValueSource.Specimen.toString());
            ActionButton fromSpecimenButton = new ActionButton(publishURL, "Reset with Specimen Data");
            buttons.add(fromSpecimenButton);
        }

        ActionButton cancelButton = new ActionButton("Cancel");
        cancelButton.setURL(returnURL);
        cancelButton.setScript(script, true);
        buttons.add(cancelButton);


        queryView.setButtons(buttons);
        return new VBox(new JspView<PublishConfirmBean>("/org/labkey/api/study/actions/publishHeader.jsp",
                new PublishConfirmBean(timepointType, mismatched), errors), queryView);
    }

    private void attemptCopy(PublishConfirmForm publishConfirmForm, BindException errors, ViewContext context, AssayProvider provider, Set<Integer> selectedObjects, List<Integer> allObjects, Container targetStudy, Map<Object, String> postedVisits, Map<Object, String> postedPtids, TimepointType timepointType)
            throws RedirectException
    {
        Map<Integer, AssayPublishKey> publishData = new LinkedHashMap<Integer, AssayPublishKey>();
        String[] participantIds = publishConfirmForm.getParticipantId();
        String[] visitIds = publishConfirmForm.getVisitId();
        String[] dates = publishConfirmForm.getDate();
        boolean missingPtid = false;
        boolean missingVisitId = false;
        boolean missingDate = false;
        boolean badVisitIds = false;
        boolean badDates = false;
        int index = 0;
        for (int objectId : allObjects)
        {
            // we only want to give errors for selected rows, but we want to compute visits and ptids regardless
            boolean selected = selectedObjects.contains(objectId);

            String participantId = participantIds != null && participantIds.length > index ? participantIds[index] : null;
            if (participantId == null || participantId.trim().length() == 0)
            {
                if (selected)
                {
                    missingPtid = true;
                }
            }
            else
                participantId = participantId.trim();

            if (timepointType == TimepointType.VISIT)
            {
                String visitIdStr = visitIds != null && visitIds.length > index ? visitIds[index] : null;
                Float visitId = null;
                if (visitIdStr == null || visitIdStr.trim().length() == 0)
                {
                    if (selected)
                        missingVisitId = true;
                }
                else
                {
                    visitIdStr = visitIdStr.trim();
                    try
                    {
                        visitId = Float.parseFloat(visitIdStr);
                    }
                    catch (NumberFormatException e)
                    {
                        if (selected)
                            badVisitIds = true;
                    }
                }
                postedPtids.put(objectId, participantId);
                postedVisits.put(objectId, visitIdStr);
                if (visitId != null && selected)
                    publishData.put(objectId, new AssayPublishKey(participantId, visitId.floatValue(), objectId));
            }
            else // TimepointType.DATE
            {
                String dateStr = dates != null && dates.length > index ? dates[index] : null;
                Date date = null;
                if (dateStr == null || dateStr.trim().length() == 0)
                {
                    if (selected)
                        missingDate = true;
                }
                else
                {
                    dateStr = dateStr.trim();
                    try
                    {
                        date = (Date) ConvertUtils.convert(dateStr, Date.class);
                    }
                    catch (ConversionException e)
                    {
                        if (selected)
                            badDates = true;
                    }
                }
                postedPtids.put(objectId, participantId);
                postedVisits.put(objectId, dateStr);
                if (date != null && selected)
                    publishData.put(objectId, new AssayPublishKey(participantId, date, objectId));
            }
            index++;
        }
        if (missingPtid)
            errors.reject(null, "You must specify a Participant ID for all selected rows.");
        if (missingVisitId)
            errors.reject(null, "You must specify a Visit ID for all selected rows.");
        if (badVisitIds)
            errors.reject(null, "Visit IDs must be numbers.");
        if (missingDate || badDates)
            errors.reject(null, "You must specify a Date for all selected rows.");

        if (errors.getErrorCount() == 0 && !publishConfirmForm.isValidate())
        {
            List<String> publishErrors = new ArrayList<String>();
            ActionURL successURL  = provider.copyToStudy(context.getUser(), _protocol, targetStudy, publishData, publishErrors);
            if (publishErrors.isEmpty())
            {
                DataRegionSelection.clearAll(getViewContext(), publishConfirmForm.getDataRegionSelectionKey());
                HttpView.throwRedirect(successURL);
            }
            for (String publishError : publishErrors)
            {
                errors.reject(null, publishError);
            }
        }
    }

    public static class PublishConfirmBean
    {
        private boolean _dateBased;
        private final boolean _mismatched;

        public PublishConfirmBean(TimepointType timepointType, boolean mismatched)
        {
            _dateBased = timepointType != TimepointType.VISIT;
            _mismatched = mismatched;
        }

        public boolean isDateBased()
        {
            return _dateBased;
        }

        public boolean isMismatched()
        {
            return _mismatched;
        }
    }

    protected ActionURL getPublishHandlerURL(ExpProtocol protocol)
    {
        return PageFlowUtil.urlProvider(AssayUrls.class).getCopyToStudyConfirmURL(getContainer(), protocol).deleteParameters();
    }

    public NavTree appendNavTrail(NavTree root)
    {
        NavTree result = super.appendNavTrail(root);
        result.addChild(_protocol.getName(), PageFlowUtil.urlProvider(AssayUrls.class).getAssayRunsURL(getContainer(), _protocol));
        result.addChild("Copy to " + _targetStudyName + ": Verify Results");
        return result;
    }

    public AppBar getAppBar()
    {
        return getAppBar(_protocol);
    }
}
