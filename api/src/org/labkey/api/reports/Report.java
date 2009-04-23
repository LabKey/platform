/*
 * Copyright (c) 2006-2009 LabKey Corporation
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

package org.labkey.api.reports;

import org.labkey.api.reports.report.ReportDescriptor;
import org.labkey.api.util.VirtualFile;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.ViewContext;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.sql.ResultSet;

/**
 * Created by IntelliJ IDEA.
 * User: Mark Igra
 * Date: May 10, 2006
 * Time: 1:01:50 PM
 */
public interface Report
{
    String getType();
    String getTypeDescription();
    String getDescriptorType();

    /**
     * Render this report in the specified context
     */
    HttpView renderReport(ViewContext context) throws Exception;

    /**
     * Return the data view (if any) for this report. Many reports are created from a source
     * data grid (or query view), this view can be displayed on another tab with the rendered results.
     */
    HttpView renderDataView(ViewContext context) throws Exception;

    /**
     * Returns a view appropriate for displaying report results, may be a simple view, or
     * it may aggregate more than one of the rendered views a report can display.
     */
    HttpView getRunReportView(ViewContext context) throws Exception;

    ReportDescriptor getDescriptor();
    void setDescriptor(ReportDescriptor descriptor);

    /**
     * Called before the report is saved to allow any additional save tasks by
     * individual reports.
     * @param context
     */
    void beforeSave(ViewContext context);

    /**
     * Called before the report is deleted to allow any additional cleanup by
     * individual reports.
     * @param context
     */
    void beforeDelete(ViewContext context);

    ActionURL getRunReportURL(ViewContext context);
    ActionURL getEditReportURL(ViewContext context);

    /**
     * Allows source grid data to be downloaded for query based reports. This would be most
     * commonly used in R reports.
     */
    ActionURL getDownloadDataURL(ViewContext context);

    /**
     * Optional support for server side report caching
     */
    void clearCache();

    /**
     * Generic method to allow serialization of a report.
     */
    void serialize(Writer writer) throws IOException;

    /**
     * Serializes a report to a file(s) in the specified directory. Reports will auto-generate
     * the eventual file name, reports may generate more than one file.
     *
     * @param directory - the folder to serialize the report to
     */
    void serializeToFolder(VirtualFile directory) throws IOException;

    /**
     * Optional method to perform report specific processing after file based deserialization
     * @param reportMetaFile - the original file containing the report meta-data
     */
    void afterDeserializeFromFile(File reportMetaFile) throws IOException;
    
    /**
     * Reports which are query or result set oriented, can implement this interface to
     * generate the result set from which results are rendered.
     */
    public interface ResultSetGenerator
    {
        public ResultSet generateResultSet(ViewContext context) throws Exception;
    }

    /**
     * Chart based reports that support HTML image map generation can implement this
     * interface.
     */
    public interface ImageMapGenerator
    {
        /**
         * Creates an image map with a standard tooltip, no urls are generated.
         * @param id - the image map id value.
         */
        public String generateImageMap(ViewContext context, String id) throws Exception;

        /**
         * Creates an image map with a standard tooltip, and urls derived from the specified callback.
         * @param imageMapCallback - the name of a javascript function to be called in the url's for the
         * image map. Standard params will be used in the callback: (key, x, y).
         */
        public String generateImageMap(ViewContext context, String id, String imageMapCallback) throws Exception;

        /**
         * Creates an image map with a standard tooltip, and urls derived from the specified callback.
         * @param imageMapCallback - the name of a javascript function to be called in the url's for the
         * image map. Standard params plus the specified column names (if available) will be used in the callback.
         */
        public String generateImageMap(ViewContext context, String id, String imageMapCallback, String[] callbackParams) throws Exception;
    }

    enum renderParam {
        reportWebPart,
        reportId,
        showTabs,
        showSection,
        reportName,
    }
}
