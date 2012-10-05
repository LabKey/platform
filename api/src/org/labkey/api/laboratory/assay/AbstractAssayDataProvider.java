package org.labkey.api.laboratory.assay;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.laboratory.NavItem;
import org.labkey.api.laboratory.SettingsItem;
import org.labkey.api.study.assay.AssayProvider;
import org.labkey.api.study.assay.AssayService;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.template.ClientDependency;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Created with IntelliJ IDEA.
 * User: bimber
 * Date: 10/1/12
 * Time: 1:52 PM
 */
abstract public class AbstractAssayDataProvider implements AssayDataProvider
{
    protected String _providerName = null;
    protected Collection<AssayImportMethod> _importMethods = new LinkedHashSet<AssayImportMethod>();

    public String getName()
    {
        return getAssayProvider().getName();
    }

    //TODO
    public ActionURL getInstructionsUrl()
    {
        return null;
    }

    //TODO
    public List<NavItem> getDataNavItems()
    {
        return Collections.emptyList();
    }

    //TODO
    public List<SettingsItem> getSettingsItems()
    {
        return Collections.emptyList();
    }

    public String getProviderName()
    {
        return _providerName;
    }

    public AssayProvider getAssayProvider()
    {
        return AssayService.get().getProvider(_providerName);
    }

    public Collection<AssayImportMethod> getImportMethods()
    {
        return _importMethods;
    }

    public boolean supportsTemplates()
    {
        for (AssayImportMethod im : getImportMethods())
        {
            if (im.supportsTemplates())
            {
                return true;
            }
        }
        return false;
    }

    @NotNull
    public Set<ClientDependency> getClientDependencies()
    {
        return Collections.emptySet();
    }
}
