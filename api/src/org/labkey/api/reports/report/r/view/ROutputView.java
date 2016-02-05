/*
 * Copyright (c) 2008-2016 LabKey Corporation
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

package org.labkey.api.reports.report.r.view;

import org.apache.log4j.Logger;
import org.labkey.api.reports.ReportService;
import org.labkey.api.reports.report.RReportDescriptor;
import org.labkey.api.reports.report.ScriptEngineReport;
import org.labkey.api.reports.report.r.ParamReplacement;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.UniqueID;
import org.labkey.api.view.HttpView;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * User: Karl Lum
 * Date: May 5, 2008
 */
public class ROutputView extends HttpView
{
    private String _label;
    private String _name;
    private boolean _collapse;
    private boolean _showHeader = true;
    private boolean _isRemote = false;
    private List<File> _files = new ArrayList<>();
    private Map<String, String> _properties;
    protected static Logger LOG = Logger.getLogger(ROutputView.class);
    private static boolean ALLOW_REMOTE_FILESIZE_BYPASS = false;

    public ROutputView(ParamReplacement param)
    {
        _files = new ArrayList<>(param.getFiles());
        _name = param.getName();
        _showHeader = param.getHeaderVisible();
        _properties = param.getProperties();
        _isRemote = param.isRemote();
    }

    public String getLabel()
    {
        return _label;
    }

    protected String getName()
    {
        return _name;
    }

    public void setLabel(String label)
    {
        _label = label;
    }

    public boolean isCollapse()
    {
        return _collapse;
    }

    public void setCollapse(boolean collapse)
    {
        _collapse = collapse;
    }

    public boolean isShowHeader()
    {
        return _showHeader;
    }

    public void setShowHeader(boolean showHeader)
    {
        _showHeader = showHeader;
    }

    public List<File> getFiles()
    {
        return _files;
    }

    public void addFile(File file)
    {
        _files.add(file);
    }

    public Map<String, String> getProperties()
    {
        return _properties;
    }

    public void setProperties(Map<String, String> properties)
    {
        _properties = properties;
    }

    protected String getUniqueId(String id)
    {
        return id.concat(String.valueOf(UniqueID.getServerSessionScopedUID()));
    }

    protected String renderInternalAsString(File file) throws Exception
    {
        return null;
    }

    protected void renderTitle(Object model, PrintWriter out) throws Exception
    {
        StringBuilder sb = new StringBuilder();

        if (_showHeader)
        {
            sb.append("<tr class=\"labkey-wp-header\"><th colspan=2 align=left>");
            sb.append("   <a href=\"#\" onclick=\"return LABKEY.Utils.toggleLink(this, false);\">");
            sb.append("   <img src=\"");
            sb.append(getViewContext().getContextPath());
            sb.append("/_images/");
            sb.append(_collapse ? "plus.gif" : "minus.gif");
            sb.append("\"></a>&nbsp;");
            sb.append(PageFlowUtil.filter(_label));
            sb.append("</th></tr>");
        }
        out.write(sb.toString());
    }

    protected File moveToTemp(File file, String prefix)
    {
        File root = ScriptEngineReport.getTempRoot(ReportService.get().createDescriptorInstance(RReportDescriptor.TYPE));

        File newFile = new File(root, FileUtil.makeFileNameWithTimestamp(FileUtil.getBaseName(file.getName()), FileUtil.getExtension(file)));
        newFile.delete();

        LOG.debug("Moving '" + file.getAbsolutePath() + "' to '" + newFile.getAbsolutePath() + "'");
        if (file.renameTo(newFile))
            return newFile;

        LOG.debug("Failed to move " + file.getAbsolutePath() + "' to '" + newFile.getAbsolutePath() + "'");
        return null;
    }

    protected boolean exists(File file)
    {
        long size = 0;

        if (file != null && file.exists())
        {
            // Files.size() or File.length() may report 0 incorrectly in certain network
            // configurations.  For example, in an Rserve scenario we were seeing
            // 0 length sizes being reported for R artifacts even though the Rserve process
            // on a remote machine had finished writing the file to an NFS share.  In this case
            // don't check the length
            //
            // Disable the bypass checking but leave the code intact per issue 21896, to give us the option
            // to expose this in the future if necessary
            if (ALLOW_REMOTE_FILESIZE_BYPASS && _isRemote)
                return true;

            try
            {
                size = Files.size(Paths.get(file.getAbsolutePath()));
            }
            catch(IOException ignore){}
        }

        return (size > 0);
    }

    static final String PREFIX = "RReport";

    public static void cleanUpTemp(final long cutoff)
    {
        File tempDir = new File(System.getProperty("java.io.tmpdir"));

        if (tempDir.exists())
        {
            File[] filesToDelete = tempDir.listFiles(new FileFilter(){
                @Override
                public boolean accept(File file)
                {
                    if (!file.isDirectory() && file.getName().startsWith(PREFIX))
                    {
                        return file.lastModified() < cutoff;
                    }
                    return false;
                }
            });
            
            for (File file : filesToDelete)
            {
                file.delete();
            }
        }
    }
}
