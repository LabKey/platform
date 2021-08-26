package org.labkey.api.study.publish;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.action.BaseViewAction;
import org.labkey.api.action.HasBindParameters;
import org.labkey.api.data.DataRegionSelection;
import org.labkey.api.view.ViewForm;
import org.springframework.beans.PropertyValue;
import org.springframework.beans.PropertyValues;
import org.springframework.validation.BindException;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class PublishConfirmForm extends ViewForm implements DataRegionSelection.DataSelectionKeyForm, HasBindParameters
{
    /**
     * Controls the source of the subject and timepoint values for the publish results query view
     */
    public enum DefaultValueSource
    {
        PublishSource,  // The publish source results table.
        Specimen,       // For assays only, specimen linked to assay results.
        UserSpecified,  // Values that a user might provide from the confirm view.
    }

    private void convertStringArrayParam(PropertyValue pv)
    {
        if (null != pv && pv.getValue() instanceof String)
        {
            String str = (String) pv.getValue();
            if (str.contains("\t"))
                pv.setConvertedValue(StringUtils.splitPreserveAllTokens(str, '\t'));
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

    private Integer _rowId;
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
    private String _defaultValueSource = DefaultValueSource.PublishSource.name();
    private String _autoLinkCategory;

    public Integer getRowId()
    {
        return _rowId;
    }

    public void setRowId(Integer rowId)
    {
        _rowId = rowId;
    }

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

    public String getDefaultValueSource()
    {
        return _defaultValueSource;
    }

    public void setDefaultValueSource(String defaultValueSource)
    {
        _defaultValueSource = defaultValueSource;
    }

    public String getAutoLinkCategory()
    {
        return _autoLinkCategory;
    }

    public void setAutoLinkCategory(String autoLinkCategory)
    {
        _autoLinkCategory = autoLinkCategory;
    }
}
