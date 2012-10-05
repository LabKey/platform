package org.labkey.api.laboratory;

import org.labkey.api.view.ActionURL;
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
    public ActionURL getInstructionsUrl();

    /**
     * Return the list of NavItems that will appear in the list of data types
     * @return
     */
    public List<NavItem> getDataNavItems();

    /**
     * Return the list SettingsItems that will appear in the UI
     * @return
     */
    public List<SettingsItem> getSettingsItems();

//    /**
//     * Return the list of ReportItems that will appear in the list of reports
//     * @return
//     */
//    public List<ReportItem> getReportItems();

    /**
     * Return true if this import pathway can be used with assay templates, which allows runs to be prepared ahead of importing results
     * @return
     */
    public boolean supportsTemplates();

//    /**
//     * If supported, this is the action use to proactively import sample information or create exports
//     * for the instrument used to run the assay
//     * @return
//     */
//    public ActionURL getPrepareExptUrl(Container c, User u, ExpProtocol protocol);

//    /**
//     * If this assay supports proactive run import, this metadata object will be passed to the import UI
//     * @return
//     */
//    public JSONObject getPrepareExptMetadata();

    /**
     * @return Optional.  Returns a set of ClientDependencies that will be loaded on the request page for this assay
     */
    public Set<ClientDependency> getClientDependencies();
}
