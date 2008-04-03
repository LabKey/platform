package org.labkey.xarassay;

import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.data.Container;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.security.ACL;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.study.assay.AssayService;
import org.labkey.api.view.*;
import org.springframework.validation.BindException;
import org.springframework.web.servlet.ModelAndView;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;


public class XarAssayController extends SpringActionController
{
    private static final DefaultActionResolver _resolver = new DefaultActionResolver(XarAssayController.class,
            new Action(XarAssayUploadAction.class)
        );


    public XarAssayController()
    {
        super();
        setActionResolver(_resolver);
    }

    /*
    builds up a list of Assay providers that extend the XarAssay provider
     */
    @RequiresPermission(ACL.PERM_INSERT)
    public class ChooseAssayAction extends SimpleViewAction<XarChooseAssayForm>
    {

        public ModelAndView getView(XarChooseAssayForm form, BindException errors) throws Exception
        {

                ViewContext ctx = getViewContext();
            Container c = ctx.getContainer();
            form.setPath(ctx.getRequest().getParameter("path"));
                Integer protId = null;
            try {
                protId = new Integer(ctx.getRequest().getParameter("protId"));}
            catch (NumberFormatException e) {}

            if ((null != protId) && (null == form.getRowId()))
                form.setRowId(protId);                

            if (null != form.getRowId())
            {
                // came from Assay definition, already know which Assay we want
                ViewURLHelper helper = form.getViewContext().getViewURLHelper().clone();
                helper.setAction("xarAssayUpload");
                helper.addParameter("referer", "pipeline");
                HttpView.throwRedirect(helper);
            }

            ArrayList<ExpProtocol> assayProtocols = new ArrayList<ExpProtocol>();

            ViewURLHelper runUploadLink = new ViewURLHelper("XarAssay","xarAssayUpload", c );
            runUploadLink.addParameter("path",form.getPath());
            runUploadLink.addParameter("referer","pipeline");

            Map<String, XarAssayProvider> mapXarAssayProviders = XarAssayProvider.getXarAssayProviders();

            List<ExpProtocol> ap = AssayService.get().getAssayProtocols(c);
             for (ExpProtocol p : ap)
            {
                Lsid lsid = new Lsid(p.getLSID());
                 if (mapXarAssayProviders.containsKey(lsid.getNamespacePrefix()) )
                {
                     assayProtocols.add(p);
                    runUploadLink.replaceParameter("rowId", Integer.toString(p.getRowId()));
                    form.getLinks().put(p.getName(), runUploadLink.toString());
                }
            }
            form.setAvailableProtocols(assayProtocols);

            return new JspView<XarChooseAssayForm>("/org/labkey/xarassay/view/chooseAssay.jsp",form) ;


        }
        public NavTree appendNavTrail(NavTree root)
        {
           root.addChild("Select Assay Type");
            return root;

        }
    }
}