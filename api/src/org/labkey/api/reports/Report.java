/*
 * Copyright (c) 2006-2017 LabKey Corporation
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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.admin.ImportContext;
import org.labkey.api.attachments.AttachmentParent;
import org.labkey.api.data.Container;
import org.labkey.api.data.Results;
import org.labkey.api.query.ValidationError;
import org.labkey.api.reports.report.ReportDescriptor;
import org.labkey.api.reports.report.ScriptOutput;
import org.labkey.api.security.User;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.thumbnail.ThumbnailProvider;
import org.labkey.api.util.Pair;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.ViewContext;
import org.labkey.api.writer.ContainerUser;
import org.labkey.api.writer.VirtualFile;
import org.springframework.validation.BindException;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * User: Mark Igra
 * Date: May 10, 2006
 * Time: 1:01:50 PM
 */
public interface Report extends AttachmentParent, ThumbnailProvider
{
    String SHARE_REPORT_TYPE = "Report.ShareReport";

    Report clone();
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
     * Determines whether the user can edit this report
     */
    boolean canEdit(User user, Container container);
    boolean canEdit(User user, Container container, List<ValidationError> errors);

    /**
     * Determines whether the user can modify the shared state of a report
     */
    boolean canShare(User user, Container container);
    boolean canShare(User user, Container container, List<ValidationError> errors);

    /**
     * Determine if this report type allows sharing via the shareReport action.
     */
    boolean allowShareButton(User user, Container container);

    /**
     * Determines whether the user can delete this report
     */
    boolean canDelete(User user, Container container);
    boolean canDelete(User user, Container container, List<ValidationError> errors);

    boolean hasPermission(@NotNull UserPrincipal user, @NotNull Container container, @NotNull Class<? extends Permission> perm);

    /**
     * Called before the report is saved to allow any additional save tasks by
     * individual reports.
     * @param context
     */
    void beforeSave(ContainerUser context);

    /**
     * Determine if the report has a modification to its content, in which case we will update the "ContentModified" field
     */
    boolean hasContentModified(ContainerUser context);

    /**
     * Called before the report is deleted to allow any additional cleanup by
     * individual reports.
     * @param context
     */
    void beforeDelete(ContainerUser context);

    // TODO: This should take Container, @Nullable ActionURL instead of a full ViewContext to behave for background threads
    ActionURL getRunReportURL(ViewContext context);

    /**
     * Anchor target (e.g., "_blank") use when rendering run report href.
     * @return
     */
    String getRunReportTarget();

    @Nullable ActionURL getEditReportURL(ViewContext context);
    @Nullable ActionURL getEditReportURL(ViewContext context, ActionURL returnURL);

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
    void serialize(ImportContext context, VirtualFile dir, String filename) throws IOException;

    /**
     * Serializes a report to a file(s) in the specified directory. Reports will auto-generate
     * the eventual file name, reports may generate more than one file.
     *
     * @param directory - the folder to serialize the report to
     */
    void serializeToFolder(ImportContext context, VirtualFile directory) throws IOException;

    /**
     * Called after import to perform report-specific processing after deserialization from a virtual file.
     */
    void afterImport(Container container, User user);

    /**
     * Called after save to perform report-specific processing after deserialization from a virtual file 
     */
    void afterSave(Container container, User user, VirtualFile root);

    /**
     * Optional method to perform report-specific processing after file based deserialization
     * @param reportMetaFile - the original file containing the report meta-data
     */
    void afterDeserializeFromFile(File reportMetaFile) throws IOException;

    /**
     * Reports provide a map of key/value properties which represent the report state, this state can be then
     * serialized to the database, json, xml etc.
     */
    Map<String, Object> serialize(Container container, User user);

    default Map<String, Object> getExternalEditorConfig(ViewContext viewContext)
    {
        return null;
    }

    default Pair<String, String> startExternalEditor(ViewContext context, String script, BindException errors)
    {
        return null;
    }

    default void saveFromExternalEditor(ContainerUser context, String script)
    {
        throw new UnsupportedOperationException("No external editor defined for report class " + this.getClass().getSimpleName());
    }

    /**
     * Reports which are query or result set oriented, can implement this interface to
     * generate the result set from which results are rendered.
     */

    interface ResultSetGenerator
    {
       // public ResultSet generateResultSet(ViewContext context) throws Exception;
       Results generateResults(ViewContext context, boolean allowAsyncQuery) throws Exception;
    }

    /**
     * Script based reports can execute and return a list of object objects instead
     * of rendering into html.  The list of script outputs may include errors, console output
     * or output parameters.
     */
    interface ScriptExecutor
    {
        List<ScriptOutput> executeScript(ViewContext context, Map<String, Object> inputParameters) throws Exception;
    }

    /**
     * Chart based reports that support HTML image map generation can implement this
     * interface.
     */
    interface ImageMapGenerator
    {
        /**
         * Creates an image map with a standard tooltip, no urls are generated.
         * @param id - the image map id value.
         */
        String generateImageMap(ViewContext context, String id) throws Exception;

        /**
         * Creates an image map with a standard tooltip, and urls derived from the specified callback.
         * @param imageMapCallback - the name of a javascript function to be called in the url's for the
         * image map. Standard params will be used in the callback: (key, x, y).
         */
        String generateImageMap(ViewContext context, String id, String imageMapCallback) throws Exception;

        /**
         * Creates an image map with a standard tooltip, and urls derived from the specified callback.
         * @param imageMapCallback - the name of a javascript function to be called in the url's for the
         * image map. Standard params plus the specified column names (if available) will be used in the callback.
         */
        String generateImageMap(ViewContext context, String id, String imageMapCallback, String[] callbackParams) throws Exception;
    }

    // implemented by reports that render images
    interface ImageReport
    {
        void renderImage(ViewContext context) throws Exception;
    }

    enum renderParam
    {
        reportWebPart,
        reportId,
        reportSessionId,
        showTabs,
        showSection,
        reportName,
    }
}
