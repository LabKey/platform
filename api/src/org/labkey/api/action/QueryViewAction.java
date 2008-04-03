package org.labkey.api.action;

import org.labkey.api.query.QueryView;
import org.labkey.api.query.QueryAction;
import org.labkey.api.security.ACL;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.WebPartView;
import org.labkey.api.view.template.PageConfig;
import org.labkey.api.util.AppProps;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.validation.BindException;

import javax.servlet.http.HttpServletResponse;

/**
 * User: jeckels
 * Date: Jan 18, 2008
 */
@RequiresPermission(ACL.PERM_NONE)
public abstract class QueryViewAction<Form extends QueryViewAction.QueryExportForm, ViewType extends QueryView> extends SimpleViewAction<Form>
{
    protected QueryViewAction(Class<? extends Form> formClass)
    {
        super(formClass);
        RequiresPermission permissionAnnotation = getClass().getAnnotation(RequiresPermission.class);
        if (permissionAnnotation == null || permissionAnnotation.value() != ACL.PERM_NONE)
        {
            throw new IllegalArgumentException("QueryViewAction subclasses must have a RequiresPermission annotation with ACL.PERM_NONE, " +
                    "as the ExcelWebQuery implementation forces a permission check inside the action instead of as an annotation." +
                    " The QueryViewAction superclass handles this check for you.");
        }
    }

    public ModelAndView getView(Form form, BindException errors) throws Exception
    {
        if (QueryAction.exportRowsExcel.name().equals(form.getExportType()))
        {
            getViewContext().requiresPermission(ACL.PERM_READ);
            createInitializedQueryView(form, errors, true, form.getExportRegion()).exportToExcel(getViewContext().getResponse());
            return null;
        }
        else if (QueryAction.exportRowsTsv.name().equals(form.getExportType()))
        {
            getViewContext().requiresPermission(ACL.PERM_READ);
            createInitializedQueryView(form, errors, true, form.getExportRegion()).exportToTsv(getViewContext().getResponse(), form.isExportAsWebPage());
            return null;
        }
        else if ("excelWebQuery".equals(form.getExportType()))
        {
            if (!getViewContext().getContainer().hasPermission(getViewContext().getUser(), ACL.PERM_READ))
            {
                if (!getViewContext().getUser().isGuest())
                    HttpView.throwUnauthorized();
                getViewContext().getResponse().setHeader("WWW-Authenticate", "Basic realm=\"" + AppProps.getInstance().getSystemDescription() + "\"");
                getViewContext().getResponse().setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                return null;
            }
            getViewContext().requiresPermission(ACL.PERM_READ);
            createInitializedQueryView(form, errors, true, form.getExportRegion()).exportToExcelWebQuery(getViewContext().getResponse());
            return null;
        }
        else if (QueryAction.printRows.name().equals(form.getExportType()))
        {
            getViewContext().requiresPermission(ACL.PERM_READ);
            ViewType result = createInitializedQueryView(form, errors, false, form.getExportRegion());
            _print = true;
            getPageConfig().setTemplate(PageConfig.Template.Print);
            result.setFrame(WebPartView.FrameType.NONE);
            result.setPrintView(true);
            return result;
        }
        else
        {
            getViewContext().requiresPermission(ACL.PERM_READ);
            return getHtmlView(form, errors);
        }
    }

    protected ModelAndView getHtmlView(Form form, BindException errors) throws Exception
    {
        return createInitializedQueryView(form, errors, false, null);
    }

    protected final ViewType createInitializedQueryView(Form form, BindException errors, boolean forExport, String dataRegion) throws Exception
    {
        ViewType result = createQueryView(form, errors, forExport, dataRegion);
        result.setUseQueryViewActionExportURLs(true);
        return result;
    }

    protected abstract ViewType createQueryView(Form form, BindException errors, boolean forExport, String dataRegion) throws Exception;

    public static class QueryExportForm
    {
        private String _exportType;
        private boolean _exportAsWebPage;
        private String _exportRegion;

        public String getExportType()
        {
            return _exportType;
        }

        public void setExportType(String exportType)
        {
            _exportType = exportType;
        }

        public boolean isExportAsWebPage()
        {
            return _exportAsWebPage;
        }

        public void setExportAsWebPage(boolean exportAsWebPage)
        {
            _exportAsWebPage = exportAsWebPage;
        }

        public String getExportRegion()
        {
            return _exportRegion;
        }

        public void setExportRegion(String exportRegion)
        {
            _exportRegion = exportRegion;
        }
    }
}
