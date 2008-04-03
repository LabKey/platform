package org.labkey.experiment.property.client;

import com.google.gwt.user.client.rpc.RemoteService;

import java.util.List;
import java.util.Map;

import org.labkey.api.gwt.client.model.GWTDomain;

/**
 * Created by IntelliJ IDEA.
 * User: matthewb
 * Date: Apr 26, 2007
 * Time: 1:34:56 PM
 */
public interface PropertyService extends RemoteService //, PropertiesEditorService
{
    /** @gwt.typeArgs <java.lang.String> */
    public List updateDomainDescriptor(GWTDomain orig, GWTDomain dd) throws Exception;
    public GWTDomain getDomainDescriptor(String typeURI) throws Exception;

    // PropertiesEditor.LookupService
    /** @gwt.typeArgs <java.lang.String> */
    public List getContainers();
    /** @gwt.typeArgs <java.lang.String> */
    public List getSchemas(String containerId);
    /** @gwt.typeArgs <java.lang.String, java.lang.String> */
    public Map getTablesForLookup(String containerId, String schemaName);
}
