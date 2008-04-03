package org.labkey.study.query;

import org.labkey.api.data.*;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.UserSchema;
import org.labkey.api.study.assay.AssayPublishService;
import org.labkey.api.view.DataView;
import org.labkey.api.view.ViewContext;

import java.io.IOException;
import java.io.Writer;
import java.sql.SQLException;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: Karl Lum
 * Date: Oct 10, 2007
 */
public class PublishedRecordQueryView extends DataSetQueryView
{
    private String _sourceLsid;
    private int _protocolId;
    private int _recordCount;

    public PublishedRecordQueryView(ViewContext context, UserSchema schema, QuerySettings settings, String sourceLsid, int protocolId, int recordCount)
    {
        super(context, schema, settings, null, null);
        _sourceLsid = sourceLsid;
        _protocolId = protocolId;
        _recordCount = recordCount;
    }

    protected TableInfo createTable()
    {
        TableInfo table = super.createTable();
        ColumnInfo sourceLsidCol = table.getColumn("SourceLSID");
        if (sourceLsidCol != null)
            sourceLsidCol.setIsHidden(false);
        return table;
    }

    protected void setupDataView(DataView view)
    {
        if (_sourceLsid != null)
        {
            SimpleFilter filter = new SimpleFilter();

            filter.addWhereClause("sourceLsid = ?", new Object[]{_sourceLsid});
            SimpleFilter baseFilter = (SimpleFilter) view.getRenderContext().getBaseFilter();
            if (baseFilter != null)
                baseFilter.addAllClauses(filter);
            else
                baseFilter = filter;
            view.getRenderContext().setBaseFilter(baseFilter);
        }
        super.setupDataView(view);
    }

    protected DataRegion createDataRegion()
    {
        DataRegion rgn = new PublishedRecordDataRegion(_recordCount, _protocolId, _sourceLsid);

        rgn.setMaxRows(getMaxRows());
        rgn.setOffset(getOffset());
        rgn.setShowRows(getShowRows());
        rgn.setSelectionKey(getSelectionKey());
        rgn.setName(getDataRegionName());
        rgn.setDisplayColumns(getDisplayColumns());
        return rgn;
    }

    private static class PublishedRecordDataRegion extends DataRegion
    {
        private static final String MISSING_ROWS_MSG = "%s rows that were previously copied in this event have been recalled (or deleted)." +
                " The audit record(s) of the deleted rows can be found in the <a href=\"%s\">copy-to-study history view</a>, or the" +
                " study dataset history view.";

        private int _recordCount;
        private int _protocolId;
        private int _count;
        private String _sourceLsid;

        public PublishedRecordDataRegion(int recordCount, int protocolId, String sourceLsid)
        {
            _recordCount = recordCount;
            _protocolId = protocolId;
            _sourceLsid = sourceLsid;
        }

        @Override
        protected int renderTableContents(RenderContext ctx, Writer out, List<DisplayColumn> renderers) throws SQLException, IOException
        {
            _count = super.renderTableContents(ctx, out, renderers);
            return _count;
        }

        protected void renderFormEnd(RenderContext ctx, Writer out) throws IOException
        {
            super.renderFormEnd(ctx, out);
            if (_count < _recordCount)
            {
                ExpRun expRun = ExperimentService.get().getExpRun(_sourceLsid);
                Container c = ctx.getContainer();
                if (expRun != null && expRun.getContainer() != null)
                    c = expRun.getContainer();
                ExpProtocol protocol = ExperimentService.get().getExpProtocol(_protocolId);
                if (_count == 0)
                    out.write(String.format(MISSING_ROWS_MSG, "All", AssayPublishService.get().getPublishHistory(c, protocol)));
                else

                    out.write(String.format(MISSING_ROWS_MSG, _recordCount - _count, AssayPublishService.get().getPublishHistory(c, protocol)));
            }
        }
   }
}

