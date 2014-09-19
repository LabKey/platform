package org.labkey.visualization;

import org.json.JSONObject;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.view.ViewContext;
import org.labkey.api.visualization.SQLGenerationException;
import org.labkey.api.visualization.VisualizationService;
import org.labkey.visualization.sql.VisualizationSQLGenerator;

/**
 * Created by matthew on 9/18/14.
 */
public class VisualizationServiceImpl implements VisualizationService
{
    public SQLResponse getDataGenerateSQL(Container c, User user, JSONObject json) throws SQLGenerationException
    {
        ViewContext context = new ViewContext();
        context.setUser(user);
        context.setContainer(c);

        VisualizationSQLGenerator generator = new VisualizationSQLGenerator();
        generator.setViewContext(context);
        generator.bindProperties(json);
        generator.setMetaDataOnly(true);

        SQLResponse ret = new SQLResponse();
        ret.schemaKey = generator.getPrimarySchema().getSchemaPath();
        ret.sql = generator.getSQL();
        return ret;
    }
}
