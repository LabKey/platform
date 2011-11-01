package org.labkey.study.view;

import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.UserSchema;
import org.labkey.api.view.ViewContext;

/**
 * Created by IntelliJ IDEA.
 * User: markigra
 * Date: 10/28/11
 * Time: 3:17 PM
 */
public class StudyListQueryView extends QueryView
{
    public StudyListQueryView(ViewContext ctx)
    {
        super(QueryService.get().getUserSchema(ctx.getUser(), ctx.getContainer(), "study"));
        QuerySettings settings = getSchema().getSettings(ctx, "qwpStudies", "StudyProperties");
        settings.setAllowChooseQuery(false);
        settings.setBaseSort(new Sort("Label"));
        setSettings(settings);
        setShowBorders(true);
        setShadeAlternatingRows(true);
        setShowSurroundingBorder(false);
    }

    @Override
    protected TableInfo createTable()
    {
        //Cast is OK since coming from study schema where it is created as FilteredTable
        FilteredTable table = (FilteredTable) super.createTable();
        table.setContainerFilter(ContainerFilter.Type.CurrentAndSubfolders.create(getUser()));
        return table;
    }
}
