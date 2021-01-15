package org.labkey.api.specimen;

import org.labkey.api.query.FieldKey;
import org.labkey.api.specimen.report.SummaryByVisitType;
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
