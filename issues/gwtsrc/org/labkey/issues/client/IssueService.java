package org.labkey.issues.client;

import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.gwt.client.ui.LookupService;

import java.util.List;
import java.util.Map;

/**
 * Created by klum on 5/12/2016.
 */
public interface IssueService extends LookupService
{
    public GWTIssueDefinition getIssueDefinition(String defName);
    public GWTDomain getDomainDescriptor(String typeURI);

    public List<String> updateIssueDefinition(GWTIssueDefinition def, GWTDomain orig, GWTDomain dd);

    public List<Map<String, String>> getProjectGroups();
    public List<Map<String, String>> getUsersForGroup(int groupId);
    public List<Map<String, String>> getFolderMoveContainers();
}
