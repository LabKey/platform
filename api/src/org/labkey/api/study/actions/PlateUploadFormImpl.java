package org.labkey.api.study.actions;

import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.study.assay.PlateBasedAssayProvider;
import org.labkey.api.study.assay.PlateSamplePropertyHelper;

import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: klum
 * Date: 10/9/12
 */
public class PlateUploadFormImpl<ProviderType extends PlateBasedAssayProvider> extends AssayRunUploadForm<ProviderType> implements PlateUploadForm<ProviderType>
{
    private Map<String, Map<DomainProperty, String>> _sampleProperties;
    private PlateSamplePropertyHelper _samplePropertyHelper;


    public PlateSamplePropertyHelper getSamplePropertyHelper()
    {
        return _samplePropertyHelper;
    }

    public void setSamplePropertyHelper(PlateSamplePropertyHelper helper)
    {
        _samplePropertyHelper = helper;
    }

    public Map<String, Map<DomainProperty, String>> getSampleProperties()
    {
        return _sampleProperties;
    }

    public void setSampleProperties(Map<String, Map<DomainProperty, String>> sampleProperties)
    {
        _sampleProperties = sampleProperties;
    }
}
