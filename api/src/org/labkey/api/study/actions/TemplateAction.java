package org.labkey.api.study.actions;

import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.ACL;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.study.assay.AssayProvider;
import org.labkey.api.study.assay.AssayService;
import org.labkey.api.data.*;
import org.labkey.api.view.NavTree;
import org.labkey.api.util.CaseInsensitiveHashMap;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.validation.BindException;

import java.sql.ResultSet;
import java.util.Map;

/**
 * User: brittp
* Date: Jul 26, 2007
* Time: 7:23:37 PM
*/
@RequiresPermission(ACL.PERM_INSERT)
public class TemplateAction extends BaseAssayAction<ProtocolIdForm>
{
    public ModelAndView getView(ProtocolIdForm rowIdForm, BindException errors) throws Exception
    {
        ExpProtocol protocol = getProtocol(rowIdForm);
        AssayProvider provider = AssayService.get().getProvider(protocol);
        PropertyDescriptor[] columns = provider.getRunDataColumns(protocol);
        Map<String, String> colNameToPdname = new CaseInsensitiveHashMap<String>();
        DataRegion dr = createDataRegion(OntologyManager.getTinfoObject(), "ObjectURI", columns, colNameToPdname, "Template");
        SimpleFilter filter = new SimpleFilter();
        filter.addWhereClause("0 = 1", new Object[]{});

        // reset all captions to the property descriptor names, since names are expected by the importer
        for (DisplayColumn dc : dr.getDisplayColumns())
            dc.setCaption(colNameToPdname.get(dc.getName()));

        dr.removeColumns(provider.getDataRowIdFieldKey().toString());

        RenderContext ctx = new RenderContext(getViewContext());
        ctx.setContainer(getContainer());
        ctx.setBaseFilter(filter);

        ResultSet rs = dr.getResultSet(ctx);
        ExcelWriter xl = new ExcelWriter(rs, dr.getDisplayColumns());
        xl.write(getViewContext().getResponse());
        return null;
    }

    public NavTree appendNavTrail(NavTree root)
    {
        throw new UnsupportedOperationException("Not Yet Implemented");
    }
}
