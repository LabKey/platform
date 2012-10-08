package org.labkey.api.laboratory;

import org.json.JSONObject;
import org.labkey.api.data.Container;
import org.labkey.api.module.Module;
import org.labkey.api.security.User;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.template.ClientDependency;

import java.util.List;
import java.util.Set;

/**
 * Created with IntelliJ IDEA.
 * User: bimber
 * Date: 10/3/12
 * Time: 1:46 PM
 */
public interface DataProvider
{
    /**
     * Return the name of this DataProvider
     * @return
     */
    public String getName();

    /**
     * Return the URL holding instructions / help information
     * @return
     */
    public ActionURL getInstructionsUrl(Container c, User u);

    /**
     * Return the list of NavItems that will appear in the list of data types
     * @return
     */
    public List<NavItem> getDataNavItems(Container c, User u);

    /**
     * Return the list of NavItems that will appear in the list of samples
     * @return
     */
    public List<NavItem> getSampleNavItems(Container c, User u);

    /**
     * Return the list SettingsItems that will appear in the UI
     * @return
     */
    public List<NavItem> getSettingsItems(Container c, User u);

//    /**
//     * Return the list of ReportItems that will appear in the list of reports
//     * @return
//     */
//    public List<ReportItem> getReportItems(Container c, User u);

    /**
     * Return true if this import pathway can be used with assay templates, which allows runs to be prepared ahead of importing results
     * @return
     */
    public boolean supportsTemplates();

    /**
     * A metadata config object that will be applied to the fields on the run template page
     * @return
     */
    public JSONObject getTemplateMetadata(ViewContext ctx);

//    /**
//     * If supported, this is the action use to proactively import sample information or create exports
//     * for the instrument used to run the assay
//     * @return
//     */
//    public ActionURL getPrepareExptUrl(Container c, User u, ExpProtocol protocol);

    /**
     * @return Optional.  Returns a set of ClientDependencies that will be loaded on the request page for this assay
     */
    public Set<ClientDependency> getClientDependencies();

    /**
     * @return The module which provides this DataProvider
     */
    public Module getOwningModule();
}
