/*
 * Copyright (c) 2011-2012 LabKey Corporation
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

package org.labkey.api.reports.report.view;

import org.labkey.api.action.NullSafeBindException;
import org.labkey.api.reports.Report;
import org.labkey.api.reports.report.ScriptReport;
import org.springframework.validation.BindException;

/*
* User: adam
* Date: Feb 19, 2011
* Time: 11:52:45 AM
*/

// Duplicates code that's in RunReportView... but we don't want to extend TabStripView
// TODO: Eliminate that other class
public class AjaxRunScriptReportView extends AjaxScriptReportView
{
    protected BindException _errors;

    public AjaxRunScriptReportView(Report report, Mode mode) throws Exception
    {
        super(report, new ScriptReportBean(), mode);
    }

    @Override
    protected void init(ScriptReportBean bean, Mode mode) throws Exception
    {
        bean.setReportId(_reportId);
        bean.setViewContext(getViewContext());

        if (getErrors() != null)
            bean.setErrors(getErrors());
        else
            bean.setErrors(new NullSafeBindException(bean, "form"));

        if (_report != null)
        {
            bean.populateFromDescriptor(_report.getDescriptor());

            if (null == bean.getScript())
            {
                bean.setScript(((ScriptReport)_report).getDefaultScript());
            }
        }

        // for late-bound client dependencies
        if (null != bean.getClientDependencies() && bean.getClientDependencies().size() > 0)
        {
            setClientDependencies(bean.getClientDependencies());
        }

        // set the default redirect url
        if (bean.getRedirectUrl() == null)
        {
            bean.setRedirectUrl(getViewContext().cloneActionURL().
                    deleteParameter(RunReportView.CACHE_PARAM).getLocalURIString());
        }

        super.init(bean, mode);
    }

    public BindException getErrors()
    {
        if (_errors == null)
            _errors = new NullSafeBindException(this, "form");

        return _errors;
    }

    public void setErrors(BindException errors)
    {
        _errors = errors;
    }
}
