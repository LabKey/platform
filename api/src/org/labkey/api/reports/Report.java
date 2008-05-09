package org.labkey.api.reports;

import org.labkey.api.reports.report.ReportDescriptor;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.ActionURL;
import org.labkey.api.data.Container;
import org.labkey.api.exp.api.ExpProtocol;

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
    }
}
