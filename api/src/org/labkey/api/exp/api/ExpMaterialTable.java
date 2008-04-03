package org.labkey.api.exp.api;

import java.util.Set;

public interface ExpMaterialTable extends ExpTable<ExpMaterialTable.Column>
{
    void setMaterials(Set<ExpMaterial> predecessorMaterials);

    enum Column
    {
        RowId,
        Name,
        LSID,
        Flag,
        Run,
        CpasType,
        SourceProtocolLSID,
        Property,
    }
    void populate(SamplesSchema schema, ExpSampleSet ss, boolean filterSampleSet);
    void setSampleSet(ExpSampleSet ss, boolean filter);
}
