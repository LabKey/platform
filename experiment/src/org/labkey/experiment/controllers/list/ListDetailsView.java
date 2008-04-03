package org.labkey.experiment.controllers.list;

import org.labkey.api.data.ButtonBar;
import org.labkey.api.data.DataRegion;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.exp.list.ListDefinition;
import org.labkey.api.query.QueryPicker;
import org.labkey.api.query.QueryView;
import org.labkey.api.view.DataView;
import org.labkey.api.view.DetailsView;

import java.io.PrintWriter;
import java.util.Collections;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: Karl Lum
 * Date: Nov 6, 2007
 */
public class ListDetailsView extends QueryView
{
    private Object _listItemKey;
    private String _keyName;

    public ListDetailsView(ListQueryForm form, Object listItemKey)
    {
        super(form);
        ListDefinition def = form.getList();
        if (def != null)
        {
            _keyName = def.getKeyName();
        }
        _listItemKey = listItemKey;
    }

    protected DataView createDataView()
    {
        DataRegion rgn = createDataRegion();
        rgn.setButtonBar(ButtonBar.BUTTON_BAR_EMPTY, DataRegion.MODE_DETAILS);
        DetailsView view = new DetailsView(rgn);

        SimpleFilter filter = new SimpleFilter();
        if (_listItemKey != null && _keyName != null)
            filter.addCondition(_keyName, _listItemKey);

        SimpleFilter baseFilter = (SimpleFilter) view.getRenderContext().getBaseFilter();
        if (baseFilter != null)
            baseFilter.addAllClauses(filter);
        else
            baseFilter = filter;
        view.getRenderContext().setBaseFilter(baseFilter);

        return view;
    }

    protected List<QueryPicker> getQueryPickers()
    {
        return Collections.emptyList();
    }

    public void renderCustomizeLinks(PrintWriter out) throws Exception
    {
    }

    protected void renderChangeViewPickers(PrintWriter out)
    {
    }
}
