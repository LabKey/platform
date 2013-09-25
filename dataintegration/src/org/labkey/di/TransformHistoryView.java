package org.labkey.di;

import org.labkey.api.data.Sort;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.UserSchema;

/**
 * Created with IntelliJ IDEA.
 * User: Dax
 * Date: 9/13/13
 * Time: 12:27 PM
  */
public class TransformHistoryView extends QueryView
{
    public TransformHistoryView(UserSchema schema, QuerySettings settings)
    {
        super(schema, settings);
        settings.setBaseSort(new Sort("-Run"));
    }
}
