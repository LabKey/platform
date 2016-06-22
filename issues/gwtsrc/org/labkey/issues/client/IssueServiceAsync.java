package org.labkey.issues.client;

import com.google.gwt.user.client.rpc.AsyncCallback;
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.gwt.client.ui.LookupServiceAsync;

import java.util.List;
import java.util.Map;

/**
 * Created by klum on 5/12/2016.
 */
public interface IssueServiceAsync extends LookupServiceAsync
{
    void getIssueDefinition(String defName, AsyncCallback<GWTIssueDefinition> async);
    void getDomainDescriptor(String typeURI, AsyncCallback<GWTDomain> async);

    void updateIssueDefinition(GWTIssueDefinition def, GWTDomain orig, GWTDomain dd, AsyncCallback<List<String>> async);

    void getProjectGroups(AsyncCallback<List<Map<String, String>>> async);
    void getUsersForGroup(Integer groupId, AsyncCallback<List<Map<String, String>>> async);
    void getFolderMoveContainers(AsyncCallback<List<Map<String, String>>> async);
}
