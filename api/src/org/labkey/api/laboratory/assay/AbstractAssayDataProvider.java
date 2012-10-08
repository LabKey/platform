package org.labkey.api.laboratory.assay;

import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.laboratory.NavItem;
import org.labkey.api.module.Module;
import org.labkey.api.security.User;
import org.labkey.api.study.assay.AssayProvider;
import org.labkey.api.study.assay.AssayService;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.template.ClientDependency;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
    protected Module _module = null;

    public String getName()
    {
        return _providerName;
    }

    public ActionURL getInstructionsUrl(Container c, User u)
    {
        return null;
    }

    public List<NavItem> getDataNavItems(Container c, User u)
    {
        List<NavItem> items = new ArrayList<NavItem>();
        ExpProtocol[] protocols = ExperimentService.get().getExpProtocols(c, c.getProject(), ContainerManager.getSharedContainer());
        for (ExpProtocol p : protocols)
        {
            AssayProvider provider = AssayService.get().getProvider(p);
            if (provider == null)
                continue;
            if (provider.equals(getAssayProvider()))
                items.add(new AssayNavItem(this, p));
        }
        return items;
    }

    public boolean isModuleEnabled(Container c)
    {
        return _module == null ? true : c.getActiveModules().contains(_module);
    }

    public List<NavItem> getSampleNavItems(Container c, User u)
    {
        return Collections.emptyList();
    }

    public List<NavItem> getSettingsItems(Container c, User u)
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

    public AssayImportMethod getImportMethodByName(String methodName)
    {
        for (AssayImportMethod m : _importMethods)
        {
            if (m.getName().equals(methodName))
                return m;
        }
        return null;
    }

    public String getDefaultImportMethodName(Container c, User u, int protocolId)
    {
        Map<String, String> props = PropertyManager.getProperties(c, getDefaultMethodPropertyKey());
        if (props == null || props.size() == 0)
            return _importMethods.size() == 0 ?  null : _importMethods.iterator().next().getName();
        else
        {
            return props.get(_providerName + "|" + protocolId);
        }
    }

    private String getDefaultMethodPropertyKey()
    {
        return this.getClass().getName() + "||DefaultImportMethods";
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

    public JSONObject getTemplateMetadata(ViewContext ctx)
    {
        JSONObject meta = new JSONObject();
        JSONObject domainMeta = new JSONObject();

        JSONObject runMeta = new JSONObject();
        runMeta.put("Name", new JSONObject().put("hidden", true));
        runMeta.put("runDate", new JSONObject().put("hidden", true));
        runMeta.put("comments", new JSONObject().put("hidden", true));
        runMeta.put("performedBy", new JSONObject().put("hidden", true));
        domainMeta.put("Run", runMeta);

        JSONObject resultsMeta = new JSONObject();
        resultsMeta.put("sampleId", new JSONObject().put("lookups", false));
        resultsMeta.put("subjectId", new JSONObject().put("lookups", false));

        domainMeta.put("Results", resultsMeta);

        meta.put("domains", domainMeta);

        return meta;
    }

    @NotNull
    public Set<ClientDependency> getClientDependencies()
    {
        return Collections.emptySet();
    }

    public JSONObject getJsonObject(JSONObject parent, String key)
    {
        return parent.containsKey(key) ? parent.getJSONObject(key): new JSONObject();
    }

    public Module getOwningModule()
    {
        return _module;
    }
}
