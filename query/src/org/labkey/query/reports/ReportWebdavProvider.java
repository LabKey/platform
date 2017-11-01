/*
 * Copyright (c) 2009-2017 LabKey Corporation
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
package org.labkey.query.reports;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.reports.Report;
import org.labkey.api.reports.ReportService;
import org.labkey.api.security.User;
import org.labkey.api.util.FileStream;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.StringUtilsLabKey;
import org.labkey.api.webdav.AbstractDocumentResource;
import org.labkey.api.webdav.AbstractWebdavResourceCollection;
import org.labkey.api.webdav.WebdavResolver;
import org.labkey.api.webdav.WebdavResolverImpl;
import org.labkey.api.webdav.WebdavResource;
import org.labkey.api.webdav.WebdavService;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/*
* User: Karl Lum
* Date: Nov 13, 2008
* Time: 3:34:11 PM
*/
public class ReportWebdavProvider implements WebdavService.Provider
{
    final static String VIEW_NAME = "@views";

    public Set<String> addChildren(@NotNull WebdavResource target)
    {
        if (!(target instanceof WebdavResolverImpl.WebFolderResource))
            return null;
        WebdavResolverImpl.WebFolderResource folder = (WebdavResolverImpl.WebFolderResource) target;
        Container c = folder.getContainer();

        return hasViews(null, c) ? PageFlowUtil.set(VIEW_NAME) : null;
    }

    public WebdavResource resolve(@NotNull WebdavResource parent, @NotNull String name)
    {
        if (!VIEW_NAME.equalsIgnoreCase(name))
            return null;
        if (!(parent instanceof WebdavResolverImpl.WebFolderResource))
            return null;
        WebdavResolverImpl.WebFolderResource folder = (WebdavResolverImpl.WebFolderResource) parent;
        Container c = folder.getContainer();

        return VIEW_NAME.equals(name) ? new ViewProviderResource(folder, c) : null;
    }

    private boolean hasViews(User user, Container c)
    {
        return !ReportService.get().getReports(user, c).isEmpty();
    }

    static class ViewProviderResource extends AbstractWebdavResourceCollection
    {
        Container _c;

        ViewProviderResource(WebdavResource parent, Container c)
        {
            super(parent.getPath(), VIEW_NAME);
            _c = c;
            setPolicy(c.getPolicy());
        }

        @Override
        public String getName()
        {
            return VIEW_NAME;
        }

        private Map<String, Report> _map;
        public WebdavResource find(String name)
        {
            Map<String, Report> map = getReportMap();
            if (map.containsKey(name))
                return new ViewResource(this, map.get(name));

            return null;
        }

        @NotNull
        public Collection<String> listNames()
        {
            Map<String, Report> map = getReportMap();
            return new ArrayList<>(map.keySet());
        }

        private Map<String, Report> getReportMap()
        {
            if (_map == null)
            {
                _map = new HashMap<>();
                for (Report report : ReportService.get().getReports(null, _c))
                {
                    _map.put(report.getDescriptor().getReportName() + ".xml", report);
                }
            }
            return _map;
        }

        public boolean exists()
        {
            return true;
        }

        public boolean isFile()
        {
            return false;
        }
    }

    public static class ViewResource extends AbstractDocumentResource
    {
        Report _report;
        Container _c;
        ViewProviderResource _folder;

        ViewResource(ViewProviderResource folder, Report report)
        {
            super(folder.getPath(), report.getDescriptor().getReportName() + ".xml");
            _report = report;
            _c = folder._c;
            setPolicy(folder._c.getPolicy());
            _folder = folder;
        }

        @NotNull
        public Collection<WebdavResolver.History> getHistory()
        {
            return Collections.emptyList();
        }

        public boolean exists()
        {
            return true;
        }

        public boolean isCollection()
        {
            return false;
        }

        public boolean isFile()
        {
            return exists();
        }

        public InputStream getInputStream(User user) throws IOException
        {
            byte[] buf = _report.getDescriptor().serialize(_c).getBytes(StringUtilsLabKey.DEFAULT_CHARSET);
            return new ByteArrayInputStream(buf);
        }

        public long copyFrom(User user, FileStream in) throws IOException
        {
/*
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            FileUtil.copyData(in,buf);
            long len = buf.size();
            WikiVersion version = WikiManager.getLatestVersion(_wiki);
            version.setBody(buf.toString("UTF-8"));
            try
            {
                WikiManager.updateWiki(user, _wiki, version);
                WikiManager.getLatestVersion(_wiki, true);
                return len;
            }
            catch (SQLException x)
            {
                throw new IOException("error writing to wiki");
            }
*/
            return 0;
        }


        // You can't actually delete this file, however, some clients do delete instead of overwrite,
        // so pretend we deleted it.
        public boolean delete(User user) throws IOException
        {
            if (user != null && !canDelete(user,true))
                return false;
            copyFrom(user, FileStream.EMPTY);
            return true;
        }

        public WebdavResource parent()
        {
            return _folder;
        }

        public long getCreated()
        {
            return _report.getDescriptor().getCreated().getTime();
        }

        public long getLastModified()
        {
            return _report.getDescriptor().getModified().getTime();
        }

        public String getContentType()
        {
            return "text/xml";
        }

        public long getContentLength()
        {
            try
            {

                byte[] buf = _report.getDescriptor().serialize(_c).getBytes(StringUtilsLabKey.DEFAULT_CHARSET);
                return buf.length;
            }
            catch (Exception e)
            {
                return 0;
            }
        }

        public WebdavResource find(String name)
        {
            return null;
        }
    }
}
