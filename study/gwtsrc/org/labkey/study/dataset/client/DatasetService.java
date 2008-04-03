package org.labkey.study.dataset.client;

import com.google.gwt.user.client.rpc.RemoteService;
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.study.dataset.client.model.GWTDataset;

import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: matthewb
 * Date: Apr 26, 2007
 * Time: 1:34:56 PM
 */
public interface DatasetService extends RemoteService //, PropertiesEditorService
{
    public GWTDataset getDataset(int id) throws Exception;

    /**
     * @param ds  Dataset this domain belongs to
     * @param orig Unchanged domain
     * @param dd New Domain
     * @return List of errors
     * @throws Exception
     * @gwt.typeArgs <java.lang.String>
     */
    public List updateDatasetDefinition(GWTDataset ds, GWTDomain orig, GWTDomain dd) throws Exception;
    public List updateDatasetDefinition(GWTDataset ds, GWTDomain orig, String schema) throws Exception;
    public GWTDomain getDomainDescriptor(String typeURI, String domainContainerId) throws Exception;
    public GWTDomain getDomainDescriptor(String typeURI) throws Exception;

    // PropertiesEditor.LookupService
    /**
     * @return list of container paths
     * @gwt.typeArgs <java.lang.String>
     */
    public List getContainers();
    /**
     * @return list of schema names
     * @gwt.typeArgs <java.lang.String>
     */
    public List getSchemas(String containerId);

    /**
     *
     * @param containerId container
     * @param schemaName name of schema for query module
     * @return map table name to pk column name
     * @gwt.typeArgs <java.lang.String,java.lang.String>
     */
    public Map getTablesForLookup(String containerId, String schemaName);
}
