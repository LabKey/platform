package org.labkey.api.laboratory.assay;

/**
 * Created with IntelliJ IDEA.
 * User: bimber
 * Date: 10/6/12
 * Time: 1:34 PM
 */
public class SimpleAssayDataProvider extends AbstractAssayDataProvider
{
    public SimpleAssayDataProvider(String providerName)
    {
        _providerName = providerName;

        _importMethods.add(new DefaultAssayImportMethod(providerName));
    }

    @Override
    public String getName()
    {
        return _providerName;
    }

    @Override
    public String getKey()
    {
        return this.getClass().getName() + "||SimpleAssay||" + getName();
    }

    @Override
    public boolean supportsRunTemplates()
    {
        return false;
    }
}
