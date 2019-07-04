package org.labkey.api.assay;

import org.labkey.api.pipeline.PipelineProvider;
import org.labkey.api.study.assay.AssayProvider;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface ModuleAssayCollections
{
    List<AssayProvider> getAssayProviders();
    Map<String, PipelineProvider> getPipelineProviders();
    Set<String> getRunLsidPrefixes();
}
