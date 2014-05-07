/*
 * Copyright (c) 2008-2014 LabKey Corporation
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

import org.labkey.api.data.ExcelWriter;
import org.labkey.api.data.TSVWriter;
import org.labkey.api.query.ExportScriptModel;
import org.labkey.api.query.QueryAction;
import org.labkey.api.query.QueryForm;
import org.labkey.api.query.QueryView;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.api.view.WebPartView;
import org.labkey.api.view.template.PageConfig;
import org.springframework.validation.BindException;
import org.springframework.web.servlet.ModelAndView;

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
        if (QueryView.EXCEL_WEB_QUERY_EXPORT_TYPE.equals(getViewContext().getRequest().getParameter("exportType")))
            setUseBasicAuthentication(true);
        super.checkPermissions();
    }

    public ModelAndView getView(Form form, BindException errors) throws Exception
    {
        if (QueryAction.exportRowsExcel.name().equals(form.getExportType()))
        {
            createInitializedQueryView(form, errors, true, form.getExportRegion()).exportToExcel(getViewContext().getResponse(), ExcelWriter.ExcelDocumentType.xls);
            return null;
        }
        if (QueryAction.exportRowsXLSX.name().equals(form.getExportType()))
        {
            createInitializedQueryView(form, errors, true, form.getExportRegion()).exportToExcel(getViewContext().getResponse(), ExcelWriter.ExcelDocumentType.xlsx);
            return null;
        }
        else if (QueryAction.exportRowsTsv.name().equals(form.getExportType()))
        {
            createInitializedQueryView(form, errors, true, form.getExportRegion()).exportToTsv(getViewContext().getResponse(), form.isExportAsWebPage(), form.getDelim(), form.getQuote());
            return null;
        }
        else if (QueryView.EXCEL_WEB_QUERY_EXPORT_TYPE.equals(form.getExportType()))
        {
            createInitializedQueryView(form, errors, true, form.getExportRegion()).exportToExcelWebQuery(getViewContext().getResponse());
            return null;
        }
        else if (QueryAction.printRows.name().equals(form.getExportType()))
        {
            ViewType result = createInitializedQueryView(form, errors, false, form.getExportRegion());
            _print = true;
            getPageConfig().setTemplate(PageConfig.Template.Print);
            result.setFrame(WebPartView.FrameType.NONE);
            result.setPrintView(true);
            return result;
        }
        else if (QueryAction.exportScript.name().equals(form.getExportType()))
        {
            return ExportScriptModel.getExportScriptView(createInitializedQueryView(form, errors, true, form.getExportRegion()), form.getScriptType(), getPageConfig(), getViewContext().getResponse());
        }
        else
        {
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
        if (null == result)
            throw new NotFoundException("Could not create a view for the requested exportRegion: '" + form.getExportRegion() + "'");

        result.setUseQueryViewActionExportURLs(true);
        return result;
    }

    protected abstract ViewType createQueryView(Form form, BindException errors, boolean forExport, String dataRegion) throws Exception;

    public static class QueryExportForm extends QueryForm
    {
        private String _exportType;
        private boolean _exportAsWebPage;
        private TSVWriter.DELIM _delim;
        private TSVWriter.QUOTE _quote;

        public String getScriptType()
        {
            return _scriptType;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setScriptType(String scriptType)
        {
            _scriptType = scriptType;
        }

        private String _scriptType;

        public String getExportType()
        {
            return _exportType;
        }

        @SuppressWarnings({"UnusedDeclaration"})
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
            return super.getDataRegionName();
        }

        public void setExportRegion(String exportRegion)
        {
            super.setDataRegionName(exportRegion);
        }

        public boolean isExport()
        {
            return null != getExportType();
        }

        public TSVWriter.DELIM getDelim()
        {
            return _delim;
        }

        public void setDelim(TSVWriter.DELIM delim)
        {
            _delim = delim;
        }

        public TSVWriter.QUOTE getQuote()
        {
            return _quote;
        }

        public void setQuote(TSVWriter.QUOTE quote)
        {
            _quote = quote;
        }
    }
}
