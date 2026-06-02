/*
 * Copyright (c) 2021-2026 LabKey Corporation
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
package org.labkey.specimen.report;

import org.labkey.api.query.FieldKey;
import org.labkey.api.util.SafeToRenderEnum;

import java.util.ArrayList;
import java.util.List;

public enum SpecimenTypeLevel implements SafeToRenderEnum
{
    PrimaryType()
    {
        @Override
        public List<SpecimenTypeBeanProperty> getGroupingColumns()
        {
            List<SpecimenTypeBeanProperty> list = new ArrayList<>();
            list.add(new SpecimenTypeBeanProperty(FieldKey.fromParts("PrimaryType", "Description"), "primaryType", this));
            return list;
        }

        @Override
        public String[] getTitleHierarchy(SummaryByVisitType summary)
        {
            return new String[]{summary.getPrimaryType()};
        }

        @Override
        public String getLabel()
        {
            return "Primary Type";
        }
    },
    Derivative()
    {
        @Override
        public List<SpecimenTypeBeanProperty> getGroupingColumns()
        {
            List<SpecimenTypeBeanProperty> parent = SpecimenTypeLevel.PrimaryType.getGroupingColumns();
            parent.add(new SpecimenTypeBeanProperty(FieldKey.fromParts("DerivativeType", "Description"), "derivative", this));
            return parent;
        }

        @Override
        public String[] getTitleHierarchy(SummaryByVisitType summary)
        {
            return new String[]{summary.getPrimaryType(), summary.getDerivative()};
        }

        @Override
        public String getLabel()
        {
            return "Derivative";
        }
    },
    Additive()
    {
        @Override
        public List<SpecimenTypeBeanProperty> getGroupingColumns()
        {
            List<SpecimenTypeBeanProperty> parent = SpecimenTypeLevel.Derivative.getGroupingColumns();
            parent.add(new SpecimenTypeBeanProperty(FieldKey.fromParts("AdditiveType", "Description"), "additive", this));
            return parent;
        }

        @Override
        public String[] getTitleHierarchy(SummaryByVisitType summary)
        {
            return new String[]{summary.getPrimaryType(), summary.getDerivative(), summary.getAdditive()};
        }

        @Override
        public String getLabel()
        {
            return "Additive";
        }
    };

    public abstract String[] getTitleHierarchy(SummaryByVisitType summary);

    public abstract List<SpecimenTypeBeanProperty> getGroupingColumns();

    public abstract String getLabel();
}
