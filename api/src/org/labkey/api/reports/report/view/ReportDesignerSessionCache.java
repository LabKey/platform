/*
 * Copyright (c) 2011 LabKey Corporation
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

import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.lang.StringUtils;
import org.labkey.api.data.Container;
import org.labkey.api.reports.Report;
import org.labkey.api.reports.report.ReportDescriptor;
import org.labkey.api.view.ViewContext;

import javax.servlet.http.HttpSession;
import java.util.Map;

/*
* User: adam
* Date: Jan 14, 2011
* Time: 10:28:07 AM
*/
@Deprecated //
public class ReportDesignerSessionCache
{
    public static final String SCRIPT_REPORT_CACHE_PREFIX = "ScriptReportCache/";

    /**
     * Populates the form with cached report state information.
     * @throws Exception
     */
    public static void populateBeanFromCache(ScriptReportBean bean, String key, ViewContext context) throws Exception
    {
        HttpSession session = context.getRequest().getSession(true);
        Map<String, Object> map = (Map<String, Object>)session.getAttribute(getReportCacheKey(key, context.getContainer()));

        BeanUtils.populate(bean, map);
    }

    public static boolean isCacheValid(String key, ViewContext context) throws Exception
    {
        HttpSession session = context.getRequest().getSession(true);

        return session.getAttribute(getReportCacheKey(key, context.getContainer())) != null;
    }

    public static ScriptReportBean initReportCache(ScriptReportBean bean, Report report) throws Exception
    {
        String key = bean.getViewContext().getActionURL().getParameter(RunReportView.CACHE_PARAM);

        if (!StringUtils.isEmpty(key) && isCacheValid(key, bean.getViewContext()))
        {
            populateBeanFromCache(bean, key, bean.getViewContext());
        }
        else if (report != null)
        {
            ReportDescriptor reportDescriptor = report.getDescriptor();
            bean.populateFromDescriptor(reportDescriptor);

            // save in session cache
            updateReportCache(bean, true);
        }

        return bean;
    }

    public static String getReportCacheKey(Object reportId, Container c)
    {
        StringBuilder sb = new StringBuilder();

        sb.append(SCRIPT_REPORT_CACHE_PREFIX);
        sb.append(c.getId());
        sb.append('/');
        sb.append(String.valueOf(reportId));

        return sb.toString();
    }

    public static void updateReportCache(ScriptReportBean form, boolean replace) throws Exception
    {
        // saves report editing state in session
        Map<String, Object> map = form.getCacheableMap();

        HttpSession session = form.getRequest().getSession(true);

        if (replace)
        {
            session.setAttribute(getReportCacheKey(form.getReportId(), form.getContainer()), map);
        }
        else
        {
            String key = getReportCacheKey(form.getReportId(), form.getContainer());
            Object o = session.getAttribute(key);

            if (o instanceof Map)
            {
                ((Map)o).put(ReportDescriptor.Prop.viewName.name(), null);
                ((Map)o).putAll(map);
            }
            else
            {
                session.setAttribute(key, map);
            }
        }
    }
}
