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

package org.labkey.study.assay;

import org.apache.commons.beanutils.ConversionException;
import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.action.BaseViewAction;
import org.labkey.api.action.FormViewAction;
import org.labkey.api.action.HasBindParameters;
import org.labkey.api.action.SpringActionController;
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
import org.labkey.api.assay.actions.BaseAssayAction;
import org.labkey.api.assay.actions.ProtocolIdForm;
import org.labkey.api.assay.AssayProtocolSchema;
import org.labkey.api.assay.AssayProvider;
import org.labkey.api.study.assay.AssayPublishKey;
import org.labkey.api.study.assay.AssayPublishService;
import org.labkey.api.assay.AssayUrls;
import org.labkey.api.study.query.PublishResultsQueryView;
import org.labkey.api.util.HelpTopic;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.RedirectException;
import org.labkey.api.view.VBox;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.template.ClientDependency;
import org.springframework.beans.PropertyValue;
import org.springframework.beans.PropertyValues;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * User: brittp
 * Date: Jul 27, 2007
 * Time: 11:02:04 AM
 */
@RequiresPermission(InsertPermission.class)
public class PublishConfirmAction extends FormViewAction<PublishConfirmAction.PublishConfirmForm>
{
    private ExpProtocol _protocol;
    @Nullable private String _targetStudyName;

    private Map<Object, String> _postedVisits;
    private Map<Object, String> _postedDates;
    private Map<Object, String> _postedPtids;
    private Map<Object, String> _postedTargetStudies;
    private Set<Integer> _selectedObjects;
    private List<Integer> _allObjects = Collections.emptyList();
    private Container _targetStudy;
    private ActionURL _successURL;

    public static class PublishConfirmForm extends ProtocolIdForm implements DataRegionSelection.DataSelectionKeyForm, HasBindParameters
    {
        private void convertStringArrayParam(PropertyValue pv)
        {
            if (null != pv && pv.getValue() instanceof String)
            {
                String str = (String)pv.getValue();
                if (str.contains("\t"))
                    pv.setConvertedValue(StringUtils.splitPreserveAllTokens(str,'\t'));
            }
        }

        @Override
        public @NotNull BindException bindParameters(PropertyValues pvs)
        {
            // springBindParameters() almost works as-is, except for trimming leading/trailing '\t' chars
            // consider hooking spring's built-in converter for String[]? maybe use json encoding see ConvertType.parseParams()
            convertStringArrayParam(pvs.getPropertyValue("targetStudy"));
            convertStringArrayParam(pvs.getPropertyValue("participantId"));
            convertStringArrayParam(pvs.getPropertyValue("visitId"));
            convertStringArrayParam(pvs.getPropertyValue("date"));
            convertStringArrayParam(pvs.getPropertyValue("objectId"));
            return BaseViewAction.springBindParameters(this, "form", pvs);
        }

        private String[] _targetStudy;
        private String[] _participantId;
        private String[] _visitId;
        private String[] _date;
        private String[] _objectIdStrings;
        private List<Integer> _objectId;
        private boolean _attemptPublish;
        private boolean _validate;
        private boolean _includeTimestamp;
        private String _dataRegionSelectionKey;
        private String _containerFilterName;
        private PublishResultsQueryView.DefaultValueSource _defaultValueSource = PublishResultsQueryView.DefaultValueSource.Assay;

        @Override
        public String getDataRegionSelectionKey()
        {
            return _dataRegionSelectionKey;
        }

        @Override
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

        public List<Integer> getObjectIdValues()
        {
            return _objectId;
        }

        public String[] getObjectId()
        {
            return _objectIdStrings;
        }

        public void setObjectId(String[] objectId)
        {
            _objectIdStrings = objectId;
            if (null != objectId)
                _objectId = Arrays.stream(objectId).map(Integer::parseInt).collect(Collectors.toList());
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

        public boolean isIncludeTimestamp()
        {
            return _includeTimestamp;
        }

        public void setIncludeTimestamp(boolean includeTimestamp)
        {
            _includeTimestamp = includeTimestamp;
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

    @Override
    public void validateCommand(PublishConfirmForm form, Errors errors)
    {
        // Check if a single target study was posted for the entire run (the common case)
        if (form.getTargetStudy() != null && form.getTargetStudy().length == 1)
        {
            _targetStudy = ContainerManager.getForId(form.getTargetStudy()[0]);
            if (_targetStudy == null)
            {
                errors.reject(SpringActionController.ERROR_MSG, "Could not find target study");
            }
        }

        if (_targetStudy != null)
        {
            Study study = StudyService.get().getStudy(_targetStudy);
            if (study == null)
            {
                errors.reject(SpringActionController.ERROR_MSG, "No study configured for " + _targetStudy);
            }
            AssayPublishService.get().getTimepointType(_targetStudy);
            _targetStudyName = study.getLabel();
        }

        _selectedObjects = new HashSet<>(BaseAssayAction.getCheckboxIds(getViewContext()));
        _allObjects = form.getObjectIdValues();

        if (_allObjects == null) // On first post, this is empty, so use the current selection
            _allObjects = new ArrayList<>(_selectedObjects);

        _protocol = form.getProtocol();
    }

    @Override
    public boolean handlePost(PublishConfirmForm form, BindException errors) throws Exception
    {
        if (form.isAttemptPublish() && form.getDefaultValueSourceEnum() == PublishResultsQueryView.DefaultValueSource.UserSpecified)
        {
            _postedVisits = new HashMap<>();
            _postedDates = new HashMap<>();
            _postedPtids = new HashMap<>();
            _postedTargetStudies = new HashMap<>();

            attemptCopy(form, errors, getViewContext(), form.getProvider(), _selectedObjects, _allObjects, _targetStudy, _postedTargetStudies, _postedVisits, _postedDates, _postedPtids);
        }

        return !errors.hasErrors();
    }

    @Override
    public URLHelper getSuccessURL(PublishConfirmForm publishConfirmForm)
    {
        return _successURL;
    }

    @Override
    public ModelAndView getView(PublishConfirmForm publishConfirmForm, boolean reshow, BindException errors) throws Exception
    {
        getPageConfig().addClientDependency(ClientDependency.fromPath("study/assayPublish.js"));

        ViewContext context = getViewContext();
        AssayProvider provider = publishConfirmForm.getProvider();

        AssayProtocolSchema schema = provider.createProtocolSchema(getUser(), getContainer(), _protocol, _targetStudy);
        boolean mismatched = AssayPublishService.get().hasMismatchedInfo(_allObjects, schema);

        // Show the form
        QuerySettings settings = schema.getSettings(context, AssayProtocolSchema.DATA_TABLE_NAME, AssayProtocolSchema.DATA_TABLE_NAME);
        settings.setAllowChooseView(false);
        settings.setSelectionKey(publishConfirmForm.getDataRegionSelectionKey());
        if (publishConfirmForm.getContainerFilterName() != null)
            settings.setContainerFilterName(publishConfirmForm.getContainerFilterName());
        PublishResultsQueryView queryView = new PublishResultsQueryView(provider, _protocol, schema, settings,
                _allObjects, _targetStudy, _postedTargetStudies, _postedVisits, _postedDates, _postedPtids, publishConfirmForm.getDefaultValueSourceEnum(), mismatched,
                publishConfirmForm.isIncludeTimestamp());

        List<ActionButton> buttons = new ArrayList<>();
        URLHelper returnURL = publishConfirmForm.getReturnURLHelper();
        if (null == returnURL)
        {
            returnURL = PageFlowUtil.urlProvider(AssayUrls.class).getAssayRunsURL(getContainer(), _protocol).addParameter("clearDataRegionSelectionKey", publishConfirmForm.getDataRegionSelectionKey());
        }

        ActionURL publishURL = getPublishHandlerURL(_protocol);

        publishURL.replaceParameter("defaultValueSource", PublishResultsQueryView.DefaultValueSource.UserSpecified.toString());
        publishURL.replaceParameter("validate", "false");
        ActionButton publishButton = new ActionButton(publishURL, "Copy to Study");
        publishButton.setScript("return assayPublish_onCopyToStudy(this)", true);
        buttons.add(publishButton);

        publishURL.replaceParameter("validate", "true");
        ActionButton validateButton = new ActionButton(publishURL, "Re-Validate");
        validateButton.setScript("return assayPublish_onCopyToStudy(this)", true);
        buttons.add(validateButton);

        TimepointType timepointType = null;
        if (_targetStudy != null)
            timepointType = AssayPublishService.get().getTimepointType(_targetStudy);

        if (timepointType != null && !timepointType.equals(TimepointType.VISIT))
        {
            publishURL.replaceParameter("defaultValueSource", PublishResultsQueryView.DefaultValueSource.Assay.toString());
            publishURL.replaceParameter("includeTimestamp", "true");
            ActionButton includeTimeButton = new ActionButton(publishURL, "Display DateTime");
            includeTimeButton.setScript("return assayPublish_onCopyToStudy(this)", true);
            buttons.add(includeTimeButton);
        }

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
        cancelButton.setScript("LABKEY.setSubmit(true);", true);
        buttons.add(cancelButton);

        queryView.setButtons(buttons);

        return new VBox(new JspView<>("/org/labkey/study/assay/view/publishHeader.jsp",
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
            _successURL  = provider.copyToStudy(getUser(), getContainer(), _protocol, targetStudy, publishData, publishErrors);
            if (publishErrors.isEmpty())
            {
                DataRegionSelection.clearAll(getViewContext(), publishConfirmForm.getDataRegionSelectionKey());
                // Issue 14111: successURL shouldn't be null if there are no errors, but better to go somewhere than to NPE.
                if (_successURL == null)
                    _successURL = PageFlowUtil.urlProvider(StudyUrls.class).getDatasetsURL(targetStudy);
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
        return PageFlowUtil.urlProvider(StudyUrls.class).getCopyToStudyConfirmURL(getContainer(), protocol).deleteParameters();
    }

    @Override
    public NavTree appendNavTrail(NavTree root)
    {
        getPageConfig().setHelpTopic(new HelpTopic("publishAssayData"));
        root.addChild("Assay List", PageFlowUtil.urlProvider(AssayUrls.class).getAssayListURL(getContainer()));
        root.addChild(_protocol.getName(), PageFlowUtil.urlProvider(AssayUrls.class).getAssayRunsURL(getContainer(), _protocol));
        root.addChild("Copy to " + (_targetStudyName == null ? "Study" : _targetStudyName) + ": Verify Results");
        return root;
    }
}
