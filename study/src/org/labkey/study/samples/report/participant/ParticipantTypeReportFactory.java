package org.labkey.study.samples.report.participant;

import org.labkey.study.samples.report.SpecimenVisitReportParameters;
import org.labkey.study.samples.report.SpecimenVisitReport;
import org.labkey.study.model.SpecimenTypeSummary;
import org.labkey.study.model.VisitImpl;
import org.labkey.study.SampleManager;
import org.labkey.study.controllers.samples.SpringSpecimenController;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.util.PageFlowUtil;

import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

/**
 * Copyright (c) 2008-2009 LabKey Corporation
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * <p/>
 * User: brittp
 * Created: Jan 30, 2008 3:40:07 PM
 */
public class ParticipantTypeReportFactory extends SpecimenVisitReportParameters
{
    private String _selectedType;

    protected List<? extends SpecimenVisitReport> createReports()
    {
        List<? extends SpecimenTypeSummary.TypeCount> types = getSelectedTypes();
        VisitImpl[] visits = SampleManager.getInstance().getVisitsWithSpecimens(getContainer(), getUser(), getCohort());
        List<ParticipantVisitReport> reports = new ArrayList<ParticipantVisitReport>();
        for (SpecimenTypeSummary.TypeCount type : types)
        {
            if (type == null || type.getLabel() == null || type.getLabel().length() == 0)
                continue;
            String label = type.getFullLabel();
            SimpleFilter filter = new SimpleFilter();
            addBaseFilters(filter);
            while (type != null)
            {
                filter.addCondition(type.getSpecimenViewFilterColumn(), type.getRowId());
                type = type.getParent();
            }
            reports.add(new ParticipantVisitReport(label, visits, filter, this));
        }
        return reports;
    }

    public boolean allowsParticipantAggregegates()
    {
        return false;
    }

    public List<String> getAdditionalFormInputHtml()
    {
        return Collections.singletonList(getSpecimenTypePicker());
    }

    private void appendOptions(List<? extends SpecimenTypeSummary.TypeCount> types, StringBuilder builder, String parentId, String selectedId, int indent)
    {
        for (SpecimenTypeSummary.TypeCount type : types)
        {
            String label;
            if (type.getLabel() == null || type.getLabel().length() == 0)
                label = "[unknown]";
            else
                label = type.getLabel();

            String id = parentId != null ? parentId + "," + type.getRowId() : "" + type.getRowId();
            builder.append("<option value=\"").append(id).append("\"");
            if (id.equals(selectedId))
                builder.append(" SELECTED");
            builder.append(">");
            for (int i = 0; i < indent*5; i++)
                builder.append("&nbsp;");
            builder.append(PageFlowUtil.filter(label)).append("</option>\n");
            appendOptions(type.getChildren(), builder, id, selectedId, indent + 1);
        }
    }

    public String getSelectedType()
    {
        return _selectedType;
    }

    public void setSelectedType(String selectedType)
    {
        _selectedType = selectedType;
    }

    private static final String ALL_PRIMARY_TYPES_FORM_VALUE = "allPrimary";
    private static final String ALL_DERIVATIVE_TYPES_FORM_VALUE = "allDerivative";
    private static final String ALL_ADDITIVE_TYPES_FORM_VALUE = "allAdditive";

    protected List<? extends SpecimenTypeSummary.TypeCount> getSelectedTypes()
    {
        SpecimenTypeSummary summary = SampleManager.getInstance().getSpecimenTypeSummary(getContainer());
        if (_selectedType == null || _selectedType.equals(ALL_PRIMARY_TYPES_FORM_VALUE))
            return summary.getPrimaryTypes();
        if (_selectedType.equals(ALL_DERIVATIVE_TYPES_FORM_VALUE))
            return summary.getDerivatives();
        if (_selectedType.equals(ALL_ADDITIVE_TYPES_FORM_VALUE))
            return summary.getAdditives();
        String[] typeRowIdStrings = _selectedType.split(",");
        int[] typeRowIds = new int[typeRowIdStrings.length];
        for (int i = 0; i < typeRowIds.length; i++)
            typeRowIds[i] = Integer.parseInt(typeRowIdStrings[i]);

        List<? extends SpecimenTypeSummary.TypeCount> types = summary.getPrimaryTypes();
        SpecimenTypeSummary.TypeCount selected = null;
        for (int typeRowId : typeRowIds)
        {
            selected = getTypeCountByRowId(types, typeRowId);
            types = selected.getChildren();
        }
        return Collections.singletonList(selected);
    }

    private SpecimenTypeSummary.TypeCount getTypeCountByRowId(List<? extends SpecimenTypeSummary.TypeCount> counts, int rowid)
    {
        for (SpecimenTypeSummary.TypeCount count : counts)
        {
            if (count.getRowId().intValue() == rowid)
                return count;
        }
        return null;
    }

    protected String getSpecimenTypePicker()
    {
        StringBuilder builder = new StringBuilder();
        SpecimenTypeSummary summary = SampleManager.getInstance().getSpecimenTypeSummary(getContainer());
        builder.append("<select name=\"selectedType\">\n");
        builder.append("<option value=\"" + ALL_PRIMARY_TYPES_FORM_VALUE + "\"");
        builder.append(ALL_PRIMARY_TYPES_FORM_VALUE.equals(_selectedType) ? "SELECTED" : "");
        builder.append(">One report per primary type</option>\n");

        builder.append("<option value=\"" + ALL_DERIVATIVE_TYPES_FORM_VALUE + "\"");
        builder.append(ALL_DERIVATIVE_TYPES_FORM_VALUE.equals(_selectedType) ? "SELECTED" : "");
        builder.append(">One report per derivative type</option>\n");

        builder.append("<option value=\"" + ALL_ADDITIVE_TYPES_FORM_VALUE + "\"");
        builder.append(ALL_ADDITIVE_TYPES_FORM_VALUE.equals(_selectedType) ? "SELECTED" : "");
        builder.append(">One report per additive type</option>\n");

        appendOptions(summary.getPrimaryTypes(), builder, null, _selectedType, 0);
        builder.append("</select>");
        return builder.toString();
    }

    public String getLabel()
    {
        return "By Specimen Type";
    }

    public Class<? extends SpringSpecimenController.SpecimenVisitReportAction> getAction()
    {
        return SpringSpecimenController.ParticipantTypeReportAction.class;
    }
}
