package org.labkey.api.laboratory;

import org.labkey.api.action.UrlProvider;
import org.labkey.api.data.Container;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.security.User;
import org.labkey.api.view.ActionURL;

/**
 * Created with IntelliJ IDEA.
 * User: bimber
 * Date: 10/1/12
 * Time: 9:23 AM
 */
public interface LaboratoryUrls extends UrlProvider
{
    public ActionURL getSearchUrl(Container c, String schemaName, String queryName);

    public ActionURL getImportUrl(Container c, User u, String schemaName, String queryName);

    public ActionURL getAssayRunTemplateUrl(Container c, ExpProtocol protocol);
}
