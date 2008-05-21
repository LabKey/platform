/*
 * Copyright (c) 2008 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.labkey.api.action;

import org.labkey.api.query.QueryView;
import org.labkey.api.query.QueryAction;
import org.labkey.api.security.ACL;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.WebPartView;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.api.view.TermsOfUseException;
import org.labkey.api.view.template.PageConfig;
import org.labkey.api.util.AppProps;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.validation.BindException;

import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletException;

/**
 * User: jeckels
 * Date: Jan 18, 2008
 */
public abstract class QueryViewAction<Form extends QueryViewAction.QueryExportForm, ViewType extends QueryView> extends SimpleViewAction<Form>
{
    protected QueryViewAction(Class<? extends Form> formClass)
    {
        super(formClass);
    }

    public void checkPermissions() throws UnauthorizedException
    {
        if ("excelWebQuery".equals(getViewContext().getRequest().getParameter("exportType")))
        {
            try
            {
                super.checkPermissions();
            }
            catch (TermsOfUseException e)
            {
                // We don't enforce terms of use for access through ExcelWebQuery 
            }
            catch (UnauthorizedException e)
            {
                if (!getViewContext().getUser().isGuest())
                    HttpView.throwUnauthorized();
                throw new UnauthorizedException(true);
            }
        }
        super.checkPermissions();
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
        if (null != result)
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
