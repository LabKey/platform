package org.labkey.api.settings;

/**
 * A listing of functionality that is available in the non-starter tiers of our applications
 */
public enum ProductFeature
{
    ApiKeys("core"),
    Assay("assay"),
    AssayQC("premium"),
    BiologicsRegistry("biologics"),
    CalculatedFields("core"),
    ChartBuilding("core"),
    DataChangeCommentRequirement("core"),
    ELN("labbook"),
    FreezerManagement("inventory"),
    Media("recipe"),
    NonstandardAssay("nonstandardAssay"),
    Folders("sampleManagement"),
    SampleManagement("sampleManagement"),
    TransformScripts("core"),
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
