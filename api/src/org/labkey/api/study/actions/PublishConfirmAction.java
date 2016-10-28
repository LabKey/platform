/*
 * Copyright (c) 2007-2016 LabKey Corporation
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
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ActionButton;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DataRegionSelection;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.api.study.StudyUrls;
import org.labkey.api.study.TimepointType;
import org.labkey.api.study.assay.AssayProtocolSchema;
import org.labkey.api.study.assay.AssayProvider;
import org.labkey.api.study.assay.AssayPublishKey;
import org.labkey.api.study.assay.AssayPublishService;
import org.labkey.api.study.assay.AssayUrls;
import org.labkey.api.study.query.PublishResultsQueryView;
import org.labkey.api.util.HelpTopic;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.RedirectException;
import org.labkey.api.view.VBox;
import org.labkey.api.view.ViewContext;
import org.springframework.validation.BindException;
import org.springframework.web.servlet.ModelAndView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: brittp
 * Date: Jul 27, 2007
 * Time: 11:02:04 AM
 */
@RequiresPermission(InsertPermission.class)
public class PublishConfirmAction extends BaseAssayAction<PublishConfirmAction.PublishConfirmForm>
{
    private ExpProtocol _protocol;
    @Nullable private String _targetStudyName;

    public static class PublishConfirmForm extends ProtocolIdForm implements DataRegionSelection.DataSelectionKeyForm
    {
        private String[] _targetStudy;
        private String[] _participantId;
        private String[] _visitId;
        private String[] _date;
        private Integer[] _objectId;
        private boolean _attemptPublish;
        private boolean _validate;
        private String _dataRegionSelectionKey;
        private String _containerFilterName;
        private PublishResultsQueryView.DefaultValueSource _defaultValueSource = PublishResultsQueryView.DefaultValueSource.Assay;

        public String getDataRegionSelectionKey()
        {
            return _dataRegionSelectionKey;
        }

        public void setDataRegionSelectionKey(String dataRegionSelectionKey)
        {
            _dataRegionSelectionKey = dataRegionSelectionKey;
        }

        public String[] getTargetStudy()
        {
            return _targetStudy;
        }

        public void setTargetStudy(String[] targetStudy)
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
        _protocol = publishConfirmForm.getProtocol();
        AssayProvider provider = publishConfirmForm.getProvider();
        Set<Integer> selectedObjects = new HashSet<>(getCheckboxIds());
        Integer[] allObjectsArray = publishConfirmForm.getObjectId();

        List<Integer> allObjects;
        if (allObjectsArray != null) // On first post, this is empty, so use the current selection
            allObjects = Arrays.asList(allObjectsArray);
        else
            allObjects = new ArrayList<>(selectedObjects);

        // Check if a single target study was posted for the entire run (the common case)
        Container targetStudy = null;
        if (publishConfirmForm.getTargetStudy() != null && publishConfirmForm.getTargetStudy().length == 1)
        {
            targetStudy = ContainerManager.getForId(publishConfirmForm.getTargetStudy()[0]);
            if (targetStudy == null)
            {
                throw new NotFoundException("Could not find target study");
            }
        }
        Map<Object, String> postedVisits = null;
        Map<Object, String> postedDates = null;
        Map<Object, String> postedPtids = null;
        Map<Object, String> postedTargetStudies = null;
        TimepointType timepointType = null;
        if (targetStudy != null)
        {
            Study study = StudyService.get().getStudy(targetStudy);
            if (study == null)
            {
                throw new IllegalArgumentException("No study configured for " + targetStudy);
            }
            timepointType = AssayPublishService.get().getTimepointType(targetStudy);
            _targetStudyName = study.getLabel();
        }

        // todo: this isn't a great way to determine if this is our final post, but it'll do for now:
        if (publishConfirmForm.isAttemptPublish() && publishConfirmForm.getDefaultValueSourceEnum() == PublishResultsQueryView.DefaultValueSource.UserSpecified)
        {
            postedVisits = new HashMap<>();
            postedDates = new HashMap<>();
            postedPtids = new HashMap<>();
            postedTargetStudies = new HashMap<>();
            attemptCopy(publishConfirmForm, errors, context, provider, selectedObjects, allObjects, targetStudy, postedTargetStudies, postedVisits, postedDates, postedPtids);
        }

        AssayProtocolSchema schema = provider.createProtocolSchema(getUser(), getContainer(), _protocol, targetStudy);

        boolean mismatched = AssayPublishService.get().hasMismatchedInfo(allObjects, schema);

        // Show the form
        QuerySettings settings = schema.getSettings(context, AssayProtocolSchema.DATA_TABLE_NAME, AssayProtocolSchema.DATA_TABLE_NAME);
        settings.setAllowChooseView(false);
        settings.setSelectionKey(publishConfirmForm.getDataRegionSelectionKey());
        PublishResultsQueryView queryView = new PublishResultsQueryView(provider, _protocol, schema, settings,
                allObjects, targetStudy, postedTargetStudies, postedVisits, postedDates, postedPtids, publishConfirmForm.getDefaultValueSourceEnum(), mismatched);

        if (publishConfirmForm.getContainerFilterName() != null)
            queryView.getSettings().setContainerFilterName(publishConfirmForm.getContainerFilterName());

        List<ActionButton> buttons = new ArrayList<>();
        URLHelper returnURL = publishConfirmForm.getReturnURLHelper();
        if (null == returnURL)
        {
            returnURL = getSummaryLink(_protocol).addParameter("clearDataRegionSelectionKey", publishConfirmForm.getDataRegionSelectionKey());
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

        ActionButton cancelButton = new ActionButton("Cancel", returnURL);
        cancelButton.setScript(script, true);
        buttons.add(cancelButton);


        queryView.setButtons(buttons);
        return new VBox(new JspView<>("/org/labkey/api/study/actions/publishHeader.jsp",
                new PublishConfirmBean(timepointType, mismatched), errors), queryView);
    }

    private void attemptCopy(PublishConfirmForm publishConfirmForm, BindException errors,
                             ViewContext context, AssayProvider provider,
                             Set<Integer> selectedObjects, List<Integer> allObjects,
                             Container targetStudy,
                             Map<Object, String> postedTargetStudies,
                             Map<Object, String> postedVisits,
                             Map<Object, String> postedDates,
                             Map<Object, String> postedPtids)
            throws RedirectException
    {
        Map<Integer, AssayPublishKey> publishData = new LinkedHashMap<>();
        String[] participantIds = publishConfirmForm.getParticipantId();
        String[] visitIds = publishConfirmForm.getVisitId();
        String[] dates = publishConfirmForm.getDate();
        String[] targetStudies = publishConfirmForm.getTargetStudy();

        Map<Object, Container> resolvedStudies = new HashMap<>();

        boolean missingPtid = false;
        boolean missingVisitId = false;
        boolean missingDate = false;
        boolean missingStudy = false;
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

            Container rowLevelTargetStudy = null;
            if (targetStudies != null && targetStudies.length > index && null != targetStudies[index])
            {
                if (resolvedStudies.containsKey(targetStudies[index]))
                    rowLevelTargetStudy = resolvedStudies.get(targetStudies[index]);
                else
                {
                    Set<Study> studies = StudyService.get().findStudy(targetStudies[index], getUser());
                    if (studies != null && !studies.isEmpty())
                    {
                        rowLevelTargetStudy = studies.iterator().next().getContainer();
                        resolvedStudies.put(targetStudies[index], rowLevelTargetStudy);
                    }
                }
            }

            // No row level targetStudy found, use run level targetStudy.
            if (rowLevelTargetStudy == null)
                rowLevelTargetStudy = targetStudy;

            if (rowLevelTargetStudy == null)
            {
                if (selected)
                    missingStudy = true;
            }
            else
            {
                postedTargetStudies.put(objectId, rowLevelTargetStudy.getId());

                if (AssayPublishService.get().getTimepointType(rowLevelTargetStudy) == TimepointType.VISIT)
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
                        publishData.put(objectId, new AssayPublishKey(rowLevelTargetStudy, participantId, visitId.floatValue(), objectId));
                }
                else // TimepointType.DATE or CONTINUOUS
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
                    postedDates.put(objectId, dateStr);
                    if (date != null && selected)
                        publishData.put(objectId, new AssayPublishKey(rowLevelTargetStudy, participantId, date, objectId));
                }
            }
            index++;
        }

        if (missingStudy)
            errors.reject(null, "You must specify a Target Study for all selected rows.");
        if (missingPtid)
            errors.reject(null, "You must specify a Participant ID for all selected rows.");
        if (missingVisitId)
            errors.reject(null, "You must specify a Visit ID for all selected rows.");
        if (badVisitIds)
            errors.reject(null, "Visit IDs must be numbers.");
        if (missingDate || badDates)
            errors.reject(null, "You must specify a Date for all selected rows.");
        if (publishData.isEmpty())
            errors.reject(null, "No data selected to publish");

        if (errors.getErrorCount() == 0 && !publishConfirmForm.isValidate())
        {
            List<String> publishErrors = new ArrayList<>();
            ActionURL successURL  = provider.copyToStudy(getUser(), getContainer(), _protocol, targetStudy, publishData, publishErrors);
            if (publishErrors.isEmpty())
            {
                DataRegionSelection.clearAll(getViewContext(), publishConfirmForm.getDataRegionSelectionKey());
                // Issue 14111: successURL shouldn't be null if there are no errors, but better to go somewhere than to NPE.
                if (successURL == null)
                    successURL = PageFlowUtil.urlProvider(StudyUrls.class).getDatasetsURL(targetStudy);
                throw new RedirectException(successURL);
            }
            for (String publishError : publishErrors)
            {
                errors.reject(null, publishError);
            }
        }
    }

    public static class PublishConfirmBean
    {
        private TimepointType _timepointType;
        private final boolean _mismatched;

        public PublishConfirmBean(TimepointType timepointType, boolean mismatched)
        {
            _timepointType = timepointType;
            _mismatched = mismatched;
        }

        public TimepointType getTimepointType()
        {
            return _timepointType;
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
        getPageConfig().setHelpTopic(new HelpTopic("publishAssayData"));
        NavTree result = super.appendNavTrail(root);
        result.addChild(_protocol.getName(), PageFlowUtil.urlProvider(AssayUrls.class).getAssayRunsURL(getContainer(), _protocol));
        result.addChild("Copy to " + (_targetStudyName == null ? "Study" : _targetStudyName) + ": Verify Results");
        return result;
    }
}
