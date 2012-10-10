package org.labkey.api.laboratory.assay;

import org.json.JSONObject;
import org.labkey.api.data.Container;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.laboratory.AbstractNavItem;
import org.labkey.api.laboratory.LaboratoryUrls;
import org.labkey.api.laboratory.NavItem;
import org.labkey.api.security.User;
import org.labkey.api.study.assay.AssayProvider;
import org.labkey.api.study.assay.AssaySchema;
import org.labkey.api.study.assay.AssayUrls;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;

/**
 * Created with IntelliJ IDEA.
 * User: bimber
 * Date: 10/1/12
 * Time: 8:46 AM
 */
public class AssayNavItem extends AbstractNavItem
{
    AssayDataProvider _ad;
    AssayProvider _ap;
    ExpProtocol _protocol;

    public AssayNavItem(AssayDataProvider ad, ExpProtocol protocol)
    {
        _ad = ad;
        _ap = ad.getAssayProvider();
        _protocol = protocol;
    }

    public String getName()
    {
        return _protocol.getName();
    }

    public String getLabel()
    {
        return _protocol.getName();
    }

    public AssayDataProvider getDataProvider()
    {
        return _ad;
    }

    public String getCategory()
    {
        return NavItem.Category.data.name();
    }

    public String getRendererName()
    {
        return "navItemRenderer";
    }

    public boolean isImportIntoWorkbooks()
    {
        return true;
    }

    public boolean getDefaultVisibility(Container c, User u)
    {
        //by default, we only enable assays if they were created in the current folder
        //if the DataProvider registered an associated module, also only turn on by default if that module is enabled
        Container toCompare = c.isWorkbook() ? c.getParent() : c;
        return _protocol.getContainer().equals(toCompare) && getDataProvider().isModuleEnabled(c);
    }

    public ActionURL getImportUrl(Container c, User u)
    {
        return _ap.getImportURL(c, _protocol);
    }

    public ActionURL getSearchUrl(Container c, User u)
    {
        return PageFlowUtil.urlProvider(LaboratoryUrls.class).getSearchUrl(c, AssaySchema.NAME, _protocol.getName() + " Data");
    }

    public ActionURL getBrowseUrl(Container c, User u)
    {
        return PageFlowUtil.urlProvider(AssayUrls.class).getAssayResultsURL(c, _protocol);
    }

    public ActionURL getAssayRunTemplateUrl(Container c, User u)
    {
        if (_ad != null && _ad.supportsRunTemplates())
        {
            return PageFlowUtil.urlProvider(LaboratoryUrls.class).getAssayRunTemplateUrl(c, _protocol);
        }
        return null;
    }

    public ActionURL getViewAssayRunTemplateUrl(Container c, User u)
    {
        if (_ad != null && _ad.supportsRunTemplates())
        {
            return PageFlowUtil.urlProvider(LaboratoryUrls.class).getViewAssayRunTemplateUrl(c, u, _protocol);
        }
        return null;
    }

    @Override
    public JSONObject toJSON(Container c, User u)
    {
        JSONObject json = super.toJSON(c, u);
        json.put("rowId", _protocol.getRowId());
        json.put("supportsRunTemplates", _ad.supportsRunTemplates());
        json.put("assayRunTemplateUrl", getUrlObject(getAssayRunTemplateUrl(c, u)));
        json.put("viewRunTemplateUrl", getUrlObject(getViewAssayRunTemplateUrl(c, u)));

        return json;
    }
}
