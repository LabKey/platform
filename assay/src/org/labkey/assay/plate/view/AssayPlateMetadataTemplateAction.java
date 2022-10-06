package org.labkey.assay.plate.view;

import org.json.old.JSONObject;
import org.labkey.api.action.ExportAction;
import org.labkey.api.assay.plate.PlateService;
import org.labkey.api.assay.plate.PlateTemplate;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.StringUtilsLabKey;
import org.springframework.validation.BindException;

import javax.servlet.http.HttpServletResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

@RequiresPermission(ReadPermission.class)
public class AssayPlateMetadataTemplateAction extends ExportAction<AssayPlateMetadataTemplateAction.Form>
{
    public static class Form
    {
        private String _template;

        public String getTemplate()
        {
            return _template;
        }

        public void setTemplate(String template)
        {
            _template = template;
        }
    }

    @Override
    public void export(AssayPlateMetadataTemplateAction.Form form, HttpServletResponse response, BindException errors) throws Exception
    {
        String template = null;
        if (form.getTemplate() != null)
        {
            PlateTemplate plateTemplate = PlateService.get().getPlateTemplateFromLsid(getContainer(), form.getTemplate());
            if (plateTemplate != null)
            {
                Random rand = new Random();

                JSONObject json = new JSONObject();
                var wellGroupMap = plateTemplate.getWellGroupTemplateMap();
                for (var typeGroupEntry : wellGroupMap.entrySet())
                {
                    var type = typeGroupEntry.getKey();
                    var groups = typeGroupEntry.getValue();

                    var layerJson = new LinkedHashMap<>();

                    for (var name : groups.keySet())
                    {
                        // CONSIDER: create more realistic example data based on the group type.
                        // CONSIDER: get the assay's template domain to create real prop values
                        var propsJson = Map.of("dilution", String.format("%.2f", rand.nextDouble()));
                        layerJson.put(name, propsJson);
                    }

                    json.put(type.name(), layerJson);
                }

                template = json.toString(2);
            }
        }

        // create a generic template
        if (template == null)
        {
            template = "" +
                    "{\n" +
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
                    "    \"sample\" : {\n" +
                    "      -- arbitrary properties can be associated with the \n" +
                    "      -- well group, these will be imported into the results grid\n" +
                    "      -- as columns for example : dilution and ID fields\n" +
                    "      \"SA01\" : {\"dilution\": 1.0, \"ID\" : 111},\n" +
                    "      \"SA02\" : {\"dilution\": 2.0, \"ID\" : 222},\n" +
                    "      \"SA03\" : {\"dilution\": 3.0, \"ID\" : 333},\n" +
                    "      \"SA04\" : {\"dilution\": 4.0, \"ID\" : 444}\n" +
                    "    }\n" +
                    "}";
        }

        String payload = "" +
                "-- this is not valid JSON but is an example of the required structure\n" +
                "-- of the plate metadata file contents (removing the comments will make it valid)\n" +
                template;

        PageFlowUtil.streamFileBytes(getViewContext().getResponse(), "metadataTemplate.json", payload.getBytes(StringUtilsLabKey.DEFAULT_CHARSET), true);
    }
}
