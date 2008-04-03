package org.labkey.api.gwt.client.assay;

import com.google.gwt.user.client.rpc.RemoteService;
import com.google.gwt.user.client.rpc.SerializableException;
import org.labkey.api.gwt.client.assay.model.GWTProtocol;
import org.labkey.api.gwt.client.model.GWTDomain;

import java.util.Map;
import java.util.List;

/**
 * User: brittp
* Date: June 20, 2007
* Time: 2:37:12 PM
*/
public interface AssayService extends RemoteService
{
    GWTProtocol getAssayDefinition(int rowId, boolean copy) throws SerializableException;

    GWTProtocol getAssayTemplate(String providerName) throws SerializableException;

    GWTProtocol saveChanges(GWTProtocol plate, boolean replaceIfExisting) throws AssayException;

    /**
     *
     * @param orig Unchanged domain
     * @param update Edited domain
     * @return list of errors
     * @throws Exception
     * @gwt.typeArgs <java.lang.String>
     */
    List updateDomainDescriptor(GWTDomain orig, GWTDomain update) throws Exception;
    
    // PropertiesEditor.LookupService
    /**
     *
     * @return list of container paths
     * @gwt.typeArgs <java.lang.String>
     */
    List getContainers();

    /**
     * @return list of schema names
     * @gwt.typeArgs <java.lang.String>
     */
    List getSchemas(String containerId);

    /**
     *
     * @param containerId container
     * @param schemaName name of schema for query module
     * @return map table name to pk column name
     * @gwt.typeArgs <java.lang.String,java.lang.String>
     */
    Map getTablesForLookup(String containerId, String schemaName);
}
