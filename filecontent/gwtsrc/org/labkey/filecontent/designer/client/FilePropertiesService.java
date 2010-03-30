package org.labkey.filecontent.designer.client;

import org.labkey.api.gwt.client.DefaultValueType;
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.gwt.client.ui.LookupService;

import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: klum
 * Date: Mar 25, 2010
 * Time: 12:42:49 PM
 * To change this template use File | Settings | File Templates.
 */
public interface FilePropertiesService extends LookupService
{
    public GWTDomain getDomainDescriptor(String typeURI);
    public List<String> updateDomainDescriptor(GWTDomain orig, GWTDomain dd);
}
