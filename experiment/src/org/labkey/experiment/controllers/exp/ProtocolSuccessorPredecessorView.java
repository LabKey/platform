package org.labkey.experiment.controllers.exp;

import org.labkey.api.view.GridView;
import org.labkey.api.view.ActionURL;
import org.labkey.api.data.*;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.experiment.api.Protocol;
import org.labkey.experiment.api.ExperimentServiceImpl;

import java.util.Map;
import java.util.HashMap;
import java.util.List;

/**
 * User: jeckels
* Date: Dec 19, 2007
*/
public class ProtocolSuccessorPredecessorView extends GridView
{
    private Map<String, Protocol> _protocolCache = new HashMap<String, Protocol>();

    protected ProtocolSuccessorPredecessorView(String parentProtocolLSID, int actionSequence, Container c, String lsidSelectColumn, String sequenceSelectColumn, String filterColumn, String title)
    {
        super(new DataRegion());
        TableInfo ti = ExperimentServiceImpl.get().getTinfoProtocolActionPredecessorLSIDView();
        List<ColumnInfo> cols = ti.getColumns(lsidSelectColumn, sequenceSelectColumn);
        getDataRegion().setColumns(cols);
        getDataRegion().addDisplayColumn(0, new ProtocolNameDisplayColumn(lsidSelectColumn, _protocolCache, "Name"));
        getDataRegion().addDisplayColumn(new ProtocolDescriptionDisplayColumn(lsidSelectColumn, _protocolCache));
        getDataRegion().getDisplayColumn(0).setURL(ActionURL.toPathString("Experiment", "protocolPredecessors", c.getPath()) + "?ParentLSID=" + parentProtocolLSID + "&Sequence=${" + sequenceSelectColumn + "}");
        getDataRegion().getDisplayColumn(0).setTextAlign("left");

        SimpleFilter filter = new SimpleFilter();
        filter.addCondition("ParentProtocolLSID", parentProtocolLSID, CompareType.EQUAL);
        filter.addCondition(filterColumn, actionSequence, CompareType.EQUAL);
        setFilter(filter);

        setSort(new Sort("ActionSequence"));

        getDataRegion().setButtonBar(ButtonBar.BUTTON_BAR_EMPTY);

        setTitle(title);
    }

    private static class ProtocolNameDisplayColumn extends AbstractProtocolDisplayColumn
    {
        public ProtocolNameDisplayColumn(String lsidColumnName, Map<String, Protocol> protocolCache, String columnName)
        {
            super(lsidColumnName, protocolCache);
            setCaption(columnName);
            setWidth("250");
        }

        protected String getProtocolValue(Protocol protocol)
        {
            return PageFlowUtil.filter(protocol.getName());
        }
    }

    private static class ProtocolDescriptionDisplayColumn extends AbstractProtocolDisplayColumn
    {
        public ProtocolDescriptionDisplayColumn(String lsidColumnName, Map<String, Protocol> protocolCache)
        {
            super(lsidColumnName, protocolCache);
            setCaption("Description");
            setWidth("50%");
        }

        protected String getProtocolValue(Protocol protocol)
        {
            return PageFlowUtil.filter(protocol.getProtocolDescription());
        }
    }

    private abstract static class AbstractProtocolDisplayColumn extends SimpleDisplayColumn
    {
        private final String _lsidColumnName;
        private final Map<String, Protocol> _protocolCache;

        public AbstractProtocolDisplayColumn(String lsidColumnName, Map<String, Protocol> protocolCache)
        {
            _lsidColumnName = lsidColumnName;
            _protocolCache = protocolCache;
        }

        protected Protocol getProtocol(RenderContext ctx)
        {
            String lsid = (String) ctx.get(_lsidColumnName);
            Protocol protocol = _protocolCache.get(lsid);
            if (protocol == null)
            {
                protocol = ExperimentServiceImpl.get().getProtocol(lsid);
                _protocolCache.put(lsid, protocol);
            }
            return protocol;
        }

        public String getValue(RenderContext ctx)
        {
            Protocol protocol = getProtocol(ctx);
            if (protocol != null)
            {
                return getProtocolValue(protocol);
            }
            else
            {
                return "(Unknown)";
            }
        }

        protected abstract String getProtocolValue(Protocol protocol);
    }


}
