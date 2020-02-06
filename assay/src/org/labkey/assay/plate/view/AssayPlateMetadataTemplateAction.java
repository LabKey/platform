package org.labkey.assay.plate.view;

import org.labkey.api.action.ExportAction;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.StringUtilsLabKey;
import org.springframework.validation.BindException;

import javax.servlet.http.HttpServletResponse;

@RequiresPermission(ReadPermission.class)
public class AssayPlateMetadataTemplateAction extends ExportAction
{
    @Override
    public void export(Object o, HttpServletResponse response, BindException errors) throws Exception
    {
        String template =
                "-- this is not valid JSON but is an example of the required structure\n" +
                "-- of the plate metadata file contents (removing the comments will make it valid)\n" +
                "{\n" +
                "  \"metadata\" : {\n" +
                "    -- each layer in the plate template can be matched by name\n" +
                "    -- with a JSON object, in this example both control and specimen\n" +
                "    -- are defined in the plate template that will be selected in\n" +
                "    -- the run properties during import\n" +
                "    \"control\" : {\n" +
                "      -- each well group in the layer can be matched by name\n" +
                "      -- with a JSON object, in this case POSITIVE and NEGATIVE well group names\n" +
                "      \"POSITIVE\" : {\"dilution\":  0.5},\n" +
                "      \"NEGATIVE\" : {\"dilution\":  5.0}\n" +
                "    },\n" +
                "    \"specimen\" : {\n" +
                "      -- arbitrary properties can be associated with the \n" +
                "      -- well group, these will be imported into the results grid\n" +
                "      -- as columns for example : dilution and ID fields\n" +
                "      \"SA01\" : {\"dilution\": 1.0, \"ID\" : 111},\n" +
                "      \"SA02\" : {\"dilution\": 2.0, \"ID\" : 222},\n" +
                "      \"SA03\" : {\"dilution\": 3.0, \"ID\" : 333},\n" +
                "      \"SA04\" : {\"dilution\": 4.0, \"ID\" : 444}\n" +
                "    }\n" +
                "  }\n" +
                "}";
        PageFlowUtil.streamFileBytes(getViewContext().getResponse(), "metadataTemplate.json", template.getBytes(StringUtilsLabKey.DEFAULT_CHARSET), true);
    }
}
