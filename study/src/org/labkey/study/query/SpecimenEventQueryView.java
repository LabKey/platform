package org.labkey.study.query;

import org.labkey.api.view.ViewContext;
import org.labkey.api.query.UserSchema;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.DataRegion;
import org.labkey.study.model.Specimen;
import org.labkey.study.model.Study;
import org.labkey.study.model.StudyManager;

/**
 * User: brittp
 * Date: Jan 26, 2007
 * Time: 10:13:50 AM
 */
public class SpecimenEventQueryView extends StudyQueryView
{
    protected SpecimenEventQueryView(ViewContext context, UserSchema schema,
                                     QuerySettings settings, SimpleFilter filter, Sort sort)
    {
        super(context, schema, settings, filter, sort);
    }

    public static SpecimenEventQueryView createView(ViewContext context, Specimen specimen)
    {
        Study study = StudyManager.getInstance().getStudy(context.getContainer());
        StudyQuerySchema schema = new StudyQuerySchema(study, context.getUser(), true);
        String queryName = "SpecimenEvent";
        QuerySettings qs = new QuerySettings(context.getActionURL(), queryName);
        qs.setSchemaName(schema.getSchemaName());
        qs.setQueryName(queryName);
        SimpleFilter filter = new SimpleFilter("SpecimenId", specimen.getRowId());
        Sort sort = new Sort("-ShipDate");
        return new SpecimenEventQueryView(context, schema, qs, filter, sort);
    }


    @Override
    protected boolean showCustomizeLinks()
    {
        return true;
    }

    @Override
    protected DataRegion createDataRegion()
    {
        DataRegion rgn = super.createDataRegion();
        rgn.setShadeAlternatingRows(true);
        rgn.setShowColumnSeparators(true);
        return rgn;
    }
}
