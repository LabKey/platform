package org.labkey.api.settings;

/**
 * A listing of functionality that is available in the non-starter tiers of our applications
 */
public enum ProductFeature
{
    Assay("assay"),
    ELN("labbook"),
    FreezerManagement("inventory"),
    Media("recipe"),
    SampleManagement("sampleManagement"),
    Workflow("sampleManagement");

    private final String _module;

    ProductFeature(String module)
    {
        _module = module;
    }

    public String getModule()
    {
        return _module;
    }
}
