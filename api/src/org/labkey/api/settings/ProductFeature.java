package org.labkey.api.settings;

/**
 * A listing of functionality that is available in the non-starter tiers of our applications
 */
public enum ProductFeature
{
    Assay("assay"),
    ELN("labbook"),
    FreezerManagement("inventory"),
    SampleManagement("sampleManager"),
    Workflow("sampleManagement");

    private final String _moduleProvider;

    ProductFeature(String moduleProvider)
    {
        _moduleProvider = moduleProvider;
    }

    public String getModuleProvider()
    {
        return _moduleProvider;
    }
}
