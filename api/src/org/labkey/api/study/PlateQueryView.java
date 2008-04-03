package org.labkey.api.study;

import org.labkey.api.query.QueryView;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.UserSchema;
import org.labkey.api.query.QueryAction;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.ActionURL;
import org.labkey.api.data.ActionButton;
import org.labkey.api.data.Sort;

import java.util.List;
import java.io.PrintWriter;
import java.io.IOException;
import java.sql.SQLException;

/**
 * User: brittp
 * Date: Nov 2, 2006
 * Time: 4:55:09 PM
 */
public abstract class PlateQueryView extends QueryView
{
    protected PlateQueryView(UserSchema schema, QuerySettings settings)
    {
        super(schema, settings);
    }

    public abstract void setSort(Sort sort);

    public abstract void setButtons(List<ActionButton> buttons);

    public abstract boolean hasRecords() throws SQLException, IOException;

    public abstract void addHiddenFormField(String key, String value);

    public ActionURL getCustomizeURL()
    {
        return urlFor(QueryAction.chooseColumns);
    }

    public void renderCustomizeLinks(PrintWriter out) throws Exception
    {
        // do nothing: we don't want a query picker for specimen views
    }
}
