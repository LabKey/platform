package org.labkey.wiki.renderer;

import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.view.template.ClientDependency;
import org.labkey.api.wiki.FormattedHtml;

import java.util.LinkedHashSet;
import java.util.Map;

public class ClientDependencySubstitutionHandler implements HtmlRenderer.SubstitutionHandler
{
    public FormattedHtml getSubstitution(Map<String, String> params)
    {
        params = new CaseInsensitiveHashMap<>(params);
        if (!params.containsKey("path"))
        {
            return new FormattedHtml("<br><font class='error' color='red'>Error: must provide the path of the client dependencies</font>");
        }

        LinkedHashSet<ClientDependency> cds = new LinkedHashSet<>();
        ClientDependency cd = ClientDependency.fromFilePath(params.get("path"));
        if (cd == null)
            return new FormattedHtml("<br><font class='error' color='red'>Error: unknown path: " + params.get("path") + "</font>");

        cds.add(cd);

        return new FormattedHtml("", false, null, cds);
    }
}
