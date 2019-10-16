/*
 * Copyright (c) 2014-2019 LabKey Corporation
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
package org.labkey.study.specimen.report.participant;

import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.study.StudyService;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.element.Option;
import org.labkey.api.util.element.Select;
import org.labkey.study.specimen.report.SpecimenVisitReportParameters;
import org.labkey.study.specimen.report.SpecimenVisitReport;
import org.labkey.study.model.SpecimenTypeSummary;
import org.labkey.study.model.VisitImpl;
import org.labkey.study.SpecimenManager;
import org.labkey.study.controllers.specimen.SpecimenController;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.util.Pair;

import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

import static org.labkey.api.util.HtmlString.unsafe;

/**
 * User: brittp
 * Created: Jan 30, 2008 3:40:07 PM
 */
public class ParticipantTypeReportFactory extends SpecimenVisitReportParameters
{
    private String _selectedType;

    protected List<? extends SpecimenVisitReport> createReports()
    {
        List<? extends SpecimenTypeSummary.TypeCount> types = getSelectedTypes();
        List<VisitImpl> visits = SpecimenManager.getInstance().getVisitsWithSpecimens(getContainer(), getUser(), getCohort());
        List<ParticipantVisitReport> reports = new ArrayList<>();
        for (SpecimenTypeSummary.TypeCount type : types)
        {
            String displayLabel = type.geDisplayLabel();
            SimpleFilter filter = new SimpleFilter();
            addBaseFilters(filter);
            while (type != null)
            {
                if (type.getLabel() != null)
                    filter.addCondition(type.getSpecimenViewFilterColumn(), type.getLabel());
                else
                    filter.addCondition(type.getSpecimenViewFilterColumn(), type.getLabel(), CompareType.ISBLANK);
                type = type.getParent();
            }
            reports.add(new ParticipantVisitReport(displayLabel, visits, filter, this));
        }
        return reports;
    }

    public boolean allowsParticipantAggregates()
    {
        return false;
    }

    @Override
    public List<Pair<String, HtmlString>> getAdditionalFormInputHtml()
    {
        return Collections.singletonList(getSpecimenTypePicker());
    }

    private void appendOptions(List<? extends SpecimenTypeSummary.TypeCount> types, Select.SelectBuilder builder, String parentId, String selectedId, int indent)
    {
        for (SpecimenTypeSummary.TypeCount type : types)
        {
            String spacing = generateSpacing(indent);
            String label = getLabel(type);
            HtmlString encodedLabel = unsafe(spacing + HtmlString.of(label));

            String id = parentId != null ? parentId + TYPE_COMPONENT_SEPARATOR + label : label;
            builder.addOption(new Option.OptionBuilder()
                    .value(id)
                    .label(encodedLabel)
                    .selected(id.equals(selectedId))
                    .build());

            appendOptions(type.getChildren(), builder, id, selectedId, indent + 1);
        }
    }

    private String generateSpacing(int indent)
    {
        String spacing = "";
        for (int i = 0; i < indent * 5; i++)
        {
            spacing = spacing.concat(HtmlString.NBSP.toString());
        }
        return spacing;
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
    private static final String TYPE_COMPONENT_SEPARATOR = "~#~";

    protected List<? extends SpecimenTypeSummary.TypeCount> getSelectedTypes()
    {
        SpecimenTypeSummary summary = SpecimenManager.getInstance().getSpecimenTypeSummary(getContainer(), getUser());
        if (_selectedType == null || _selectedType.equals(ALL_PRIMARY_TYPES_FORM_VALUE))
            return summary.getPrimaryTypes();
        if (_selectedType.equals(ALL_DERIVATIVE_TYPES_FORM_VALUE))
            return summary.getDerivatives();
        if (_selectedType.equals(ALL_ADDITIVE_TYPES_FORM_VALUE))
            return summary.getAdditives();
        String[] typeLabels = _selectedType.split(TYPE_COMPONENT_SEPARATOR);

        List<? extends SpecimenTypeSummary.TypeCount> types = summary.getPrimaryTypes();
        SpecimenTypeSummary.TypeCount selected = null;
        for (String typeLabel : typeLabels)
        {
            selected = getTypeCountByLabel(types, typeLabel);
            if (selected != null)
                types = selected.getChildren();
        }

        // issue 13510: default to showing one report per primary type (if no type match found in list)
        return selected == null ? summary.getPrimaryTypes() : Collections.singletonList(selected);
    }

    private String getLabel(SpecimenTypeSummary.TypeCount type)
    {
        if (type.getLabel() == null || type.getLabel().length() == 0)
            return "[unknown]";
        else
            return type.getLabel();

    }

    private SpecimenTypeSummary.TypeCount getTypeCountByLabel(List<? extends SpecimenTypeSummary.TypeCount> counts, String label)
    {
        boolean unknown = "[unknown]".equals(label);
        for (SpecimenTypeSummary.TypeCount count : counts)
        {
            if (getLabel(count).equals(label) || (unknown && (count.getLabel() == null || count.getLabel().length() == 0)))
                return count;
        }
        return null;
    }

    protected Pair<String, HtmlString> getSpecimenTypePicker()
    {
        Select.SelectBuilder select = new Select.SelectBuilder();
        SpecimenTypeSummary summary = SpecimenManager.getInstance().getSpecimenTypeSummary(getContainer(), getUser());
        select.name("selectedType");

        select.addOption(new Option.OptionBuilder()
            .value(ALL_PRIMARY_TYPES_FORM_VALUE)
            .label("One report per primary type")
            .selected(ALL_PRIMARY_TYPES_FORM_VALUE.equals(_selectedType))
            .build());

        select.addOption(new Option.OptionBuilder()
                .value(ALL_DERIVATIVE_TYPES_FORM_VALUE)
                .label("One report per derivative type")
                .selected(ALL_DERIVATIVE_TYPES_FORM_VALUE.equals(_selectedType))
                .build());

        select.addOption(new Option.OptionBuilder()
                .value(ALL_ADDITIVE_TYPES_FORM_VALUE)
                .label("One report per additive type")
                .selected(ALL_ADDITIVE_TYPES_FORM_VALUE.equals(_selectedType))
                .build());

        appendOptions(summary.getPrimaryTypes(), select, null, _selectedType, 0);
        return new Pair<>("Specimen type", unsafe(select.toString()));
    }

    public String getLabel()
    {
        return StudyService.get().getSubjectNounSingular(getContainer()) + " by Specimen Type";
    }

    @Override
    public String getReportType()
    {
        return "ParticipantByType";
    }

    public Class<? extends SpecimenController.SpecimenVisitReportAction> getAction()
    {
        return SpecimenController.ParticipantTypeReportAction.class;
    }
}
