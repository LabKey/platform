/*
 * Copyright (c) 2008-2026 LabKey Corporation
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
package org.labkey.api.query;

import lombok.EqualsAndHashCode;
import org.apache.commons.lang3.StringUtils;
import org.labkey.api.data.ConditionalFormat;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;

import java.util.LinkedHashMap;
import java.util.Map;

@EqualsAndHashCode(callSuper = true)
public class MetadataColumnJSON extends GWTPropertyDescriptor
{
    private String _wrappedColumnName;
    private String _valueExpression;
    private boolean _lookupCustom;
    private boolean _lockExistingField;

    public MetadataColumnJSON()
    {
        super();
    }

    public MetadataColumnJSON(MetadataColumnJSON ci)
    {
        super(ci);
        setWrappedColumnName(ci.getWrappedColumnName());
        setValueExpression(ci.getValueExpression());
        setLookupCustom(ci.isLookupCustom());
        setLockExistingField(ci.isLockExistingField());
    }

    public MetadataColumnJSON(GWTPropertyDescriptor ci)
    {
        super(ci);
        setValueExpression(ci.getValueExpression());
    }

    public String getWrappedColumnName()
    {
        return _wrappedColumnName;
    }

    public void setWrappedColumnName(String wrappedColumnName)
    {
        _wrappedColumnName = wrappedColumnName;
    }

    @Override
    public String getValueExpression()
    {
        return _valueExpression;
    }

    @Override
    public void setValueExpression(String valueExpression)
    {
        _valueExpression = valueExpression;
    }

    @Override
    public GWTPropertyDescriptor copy()
    {
        return new MetadataColumnJSON(this);
    }

    public void setLookupCustom(boolean lookupCustom)
    {
        _lookupCustom = lookupCustom;
    }

    public boolean isLookupCustom()
    {
        return _lookupCustom;
    }

    @Override
    public void setLookupSchema(String lookupSchema)
    {
        _lookupCustom = false;
        super.setLookupSchema(lookupSchema);
    }

    public boolean isLockExistingField()
    {
        return _lockExistingField;
    }

    public void setLockExistingField(boolean lockExistingField)
    {
        _lockExistingField = lockExistingField;
    }

    @Override
    public void setLookupQuery(String lookupQuery)
    {
        _lookupCustom = false;
        super.setLookupQuery(lookupQuery);
    }

    @Override
    public String getLookupDescription()
    {
        if (_lookupCustom)
            return "(custom)";
        return super.getLookupDescription();
    }

    public Map<String, Object> getAuditRecordMap()
    {
        Map<String, Object> map = new LinkedHashMap<>();
        if (!StringUtils.isEmpty(getName()))
            map.put("Name", getName());
        if (!StringUtils.isEmpty(getLabel()))
            map.put("Label", getLabel());
        if (!StringUtils.isEmpty(getValueExpression()))
            map.put("ValueExpression", getValueExpression());
        if (getScale() != null)
            map.put("Scale", getScale());
        if (!StringUtils.isEmpty(getDescription()))
            map.put("Description", getDescription());
        if (!StringUtils.isEmpty(getFormat()))
            map.put("Format", getFormat());
        if (!StringUtils.isEmpty(getURL()))
            map.put("URL", getURL());
        if (!StringUtils.isEmpty(getURLTarget()))
            map.put("URLTarget", getURLTarget());
        if (!StringUtils.isEmpty(getPHI()))
            map.put("PHI", getPHI());
        if (!StringUtils.isEmpty(getDefaultScale()))
            map.put("DefaultScale", getDefaultScale());
        map.put("Required", isRequired());
        map.put("Hidden", isHidden());
        map.put("Measure", isMeasure());
        map.put("Dimension", isDimension());
        map.put("ShownInInsert", isShownInInsertView());
        map.put("ShownInDetails", isShownInDetailsView());
        map.put("ShownInUpdate", isShownInUpdateView());
        map.put("RecommendedVariable", isRecommendedVariable());
        map.put("ExcludedFromShifting", isExcludeFromShifting());
        map.put("Scannable", isScannable());
        if (!StringUtils.isEmpty(getDerivationDataScope()))
            map.put("DerivationDataScope", getDerivationDataScope());
        if (!StringUtils.isEmpty(getImportAliases()))
            map.put("ImportAliases", getImportAliases());
        String conditionalFormatStr = ConditionalFormat.toStringVal(getConditionalFormats());
        if (!StringUtils.isEmpty(conditionalFormatStr))
            map.put("ConditionalFormat", conditionalFormatStr);

        return map;
    }

}