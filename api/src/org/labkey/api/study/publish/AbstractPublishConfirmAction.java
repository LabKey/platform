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

package org.labkey.api.study.publish;

import org.apache.commons.beanutils.ConversionException;
import org.apache.commons.beanutils.ConvertUtils;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.action.FormViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.data.ActionButton;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DataRegionSelection;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.study.Dataset;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.api.study.StudyUrls;
import org.labkey.api.study.TimepointType;
import org.labkey.api.study.query.PublishResultsQueryView;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.JspView;
import org.labkey.api.view.RedirectException;
import org.labkey.api.view.VBox;
import org.labkey.api.view.template.ClientDependency;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

import java.util.ArrayList;
import java.util.Collections;
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
public abstract class AbstractPublishConfirmAction<FORM extends PublishConfirmForm> extends FormViewAction<FORM>
{
    @Nullable protected String _targetStudyName;
    protected Map<Object, String> _postedVisits;
    protected Map<Object, String> _postedDates;
    protected Map<Object, String> _postedPtids;
    protected Map<Object, String> _postedTargetStudies;
    protected Set<Integer> _selectedObjects;
    protected List<Integer> _allObjects = Collections.emptyList();
    protected Container _targetStudy;
    protected ActionURL _successURL;

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

    @Override
    public void validateCommand(FORM form, Errors errors)
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
            StudyPublishService.get().getTimepointType(_targetStudy);
            _targetStudyName = study.getLabel();
        }

        _selectedObjects = new HashSet<>(AbstractPublishStartAction.getCheckboxIds(getViewContext()));
        _allObjects = form.getObjectIdValues();

        if (_allObjects == null) // On first post, this is empty, so use the current selection
            _allObjects = new ArrayList<>(_selectedObjects);

        if (form.getReturnURLHelper() == null)
            errors.reject(SpringActionController.ERROR_MSG, "No return URL configured for this form");
    }

    @Override
    public boolean handlePost(FORM form, BindException errors) throws Exception
    {
        if (form.isAttemptPublish() && form.getDefaultValueSource().equals(PublishConfirmForm.DefaultValueSource.UserSpecified.name()))
        {
            _postedVisits = new HashMap<>();
            _postedDates = new HashMap<>();
            _postedPtids = new HashMap<>();
            _postedTargetStudies = new HashMap<>();

            attemptLinkage(form, errors, _selectedObjects, _allObjects, _targetStudy, _postedTargetStudies, _postedVisits, _postedDates, _postedPtids);
        }
        return !errors.hasErrors();
    }

    @Override
    public URLHelper getSuccessURL(FORM form)
    {
        return _successURL;
    }

    /**
     * Create the schema for the underlying results table to display in the publish confirm view.
     */
    protected abstract UserSchema getUserSchema(FORM form);

    /**
     * Get the query settings to construct the publish confirm view
     */
    protected abstract QuerySettings getQuerySettings(FORM form);

    /**
     * Checks if the specimen participant/visit/dates from the source don't match based on the specimen id and target study
     */
    protected abstract boolean isMismatchedSpecimenInfo(FORM form);

    /**
     * Show the column to indicate whether there are specimens which match the subject/timepoint combination
     */
    protected boolean showSpecimenMatchColumn(FORM form)
    {
        return false;
    }

    /**
     * Returns the publish URL
     */
    protected abstract ActionURL getPublishHandlerURL(FORM form);

    /**
     * The field key for the results objectId
     */
    protected abstract FieldKey getObjectIdFieldKey(FORM form);

    /**
     * The PublishSource enum which represents the source data
     */
    protected abstract Dataset.PublishSource getPublishSource(FORM form);

    /**
     * Generate the map of field keys which will be added to publish results query view to represent the subject,
     * timepoint editable columns etc.
     */
    protected abstract Map<StudyPublishService.LinkToStudyKeys, FieldKey> getAdditionalColumns(FORM form);

    /**
     * Perform the link to study operation
     */
    protected abstract ActionURL linkToStudy(FORM form, Container targetStudy, Map<Integer, PublishKey> publishData, List<String> publishErrors);

    /**
     * Returns the hidden form fields that need to be included on the data region form
     */
    protected Map<String, Object> getHiddenFormFields(PublishConfirmForm form)
    {
        Map<String, Object> fields = new HashMap<>();

        fields.put("rowId", form.getRowId());
        String returnURL = getViewContext().getRequest().getParameter(ActionURL.Param.returnUrl.name());
        if (returnURL == null)
        {
            returnURL = getViewContext().getActionURL().toString();
        }
        fields.put(ActionURL.Param.returnUrl.name(), returnURL);

        return fields;
    }

    /**
     * Specifies the columns in the publish results query view that should not be visible (but still be in the data view)
     * @return
     */
    protected Set<String> getHiddenPublishResultsCaptions(FORM form)
    {
        return new HashSet<>(Collections.singleton("Assay Match"));
    }

    @Override
    public ModelAndView getView(FORM form, boolean reshow, BindException errors) throws Exception
    {
        getPageConfig().addClientDependency(ClientDependency.fromPath("study/assayPublish.js"));

        UserSchema schema = getUserSchema(form);
        boolean mismatched = isMismatchedSpecimenInfo(form);

        // Show the form
        QuerySettings settings = getQuerySettings(form);
        settings.setAllowChooseView(false);
        settings.setSelectionKey(form.getDataRegionSelectionKey());
        if (form.getContainerFilterName() != null)
            settings.setContainerFilterName(form.getContainerFilterName());
        PublishResultsQueryView queryView = new PublishResultsQueryView(schema, settings, errors,
                getPublishSource(form),
                getObjectIdFieldKey(form),
                _allObjects, _targetStudy, _postedTargetStudies, _postedVisits, _postedDates, _postedPtids,
                mismatched,
                showSpecimenMatchColumn(form),
                form.isIncludeTimestamp(),
                getAdditionalColumns(form),
                getHiddenFormFields(form),
                getHiddenPublishResultsCaptions(form));

        List<ActionButton> buttons = new ArrayList<>();
        URLHelper returnURL = form.getReturnURLHelper();
        if (null == returnURL)
        {
            // consider deleting in the future unless we can find legitimate cases where the return URL is not provided in the form bean
//            returnURL = PageFlowUtil.urlProvider(AssayUrls.class).getAssayRunsURL(getContainer(), _protocol).addParameter("clearDataRegionSelectionKey", publishConfirmForm.getDataRegionSelectionKey());
        }

        ActionURL publishURL = getPublishHandlerURL(form);

        publishURL.replaceParameter("defaultValueSource", PublishConfirmForm.DefaultValueSource.UserSpecified.toString());
        publishURL.replaceParameter("validate", "false");
        publishURL.replaceParameter("autoLinkCategory", form.getAutoLinkCategory());
        ActionButton publishButton = new ActionButton(publishURL, "Link to Study");
        publishButton.setScript("return assayPublish_onLinkToStudy(this)", true);
        buttons.add(publishButton);

        publishURL.replaceParameter("validate", "true");
        ActionButton validateButton = new ActionButton(publishURL, "Re-Validate");
        validateButton.setScript("return assayPublish_onLinkToStudy(this)", true);
        buttons.add(validateButton);

        TimepointType timepointType = null;
        if (_targetStudy != null)
            timepointType = StudyPublishService.get().getTimepointType(_targetStudy);

        if (timepointType != null && !timepointType.equals(TimepointType.VISIT))
        {
            publishURL.replaceParameter("defaultValueSource", PublishConfirmForm.DefaultValueSource.PublishSource.toString());
            publishURL.replaceParameter("includeTimestamp", "true");
            ActionButton includeTimeButton = new ActionButton(publishURL, "Display DateTime");
            includeTimeButton.setScript("return assayPublish_onLinkToStudy(this)", true);
            buttons.add(includeTimeButton);
        }

        if (mismatched)
        {
            publishURL.deleteParameter("validate");
            publishURL.replaceParameter("defaultValueSource", PublishConfirmForm.DefaultValueSource.PublishSource.toString());
            ActionButton fromAssayButton = new ActionButton(publishURL, "Reset with Assay Data");
            buttons.add(fromAssayButton);

            publishURL.replaceParameter("defaultValueSource", PublishConfirmForm.DefaultValueSource.Specimen.toString());
            ActionButton fromSpecimenButton = new ActionButton(publishURL, "Reset with Specimen Data");
            buttons.add(fromSpecimenButton);
        }

        ActionButton cancelButton = new ActionButton("Cancel", returnURL);
        cancelButton.setScript("LABKEY.setSubmit(true);", true);
        buttons.add(cancelButton);

        queryView.setButtons(buttons);

        return new VBox(new JspView<>("/org/labkey/api/study/publish/publishHeader.jsp",
                new PublishConfirmBean(timepointType, mismatched), errors), queryView);
    }

    private void attemptLinkage(FORM form, BindException errors,
                            Set<Integer> selectedObjects, List<Integer> allObjects,
                            Container targetStudy,
                            Map<Object, String> postedTargetStudies,
                            Map<Object, String> postedVisits,
                            Map<Object, String> postedDates,
                            Map<Object, String> postedPtids)
            throws RedirectException
    {
        Map<Integer, PublishKey> publishData = new LinkedHashMap<>();
        String[] participantIds = form.getParticipantId();
        String[] visitIds = form.getVisitId();
        String[] dates = form.getDate();
        String[] targetStudies = form.getTargetStudy();

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

                if (StudyPublishService.get().getTimepointType(rowLevelTargetStudy) == TimepointType.VISIT)
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
                        publishData.put(objectId, new PublishKey(rowLevelTargetStudy, participantId, visitId.floatValue(), objectId));
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
                        publishData.put(objectId, new PublishKey(rowLevelTargetStudy, participantId, date, objectId));
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

        if (errors.getErrorCount() == 0 && !form.isValidate())
        {
            List<String> publishErrors = new ArrayList<>();
            _successURL  = linkToStudy(form, targetStudy, publishData, publishErrors);
            if (publishErrors.isEmpty())
            {
                DataRegionSelection.clearAll(getViewContext(), form .getDataRegionSelectionKey());
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
}
