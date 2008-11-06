package org.labkey.api.gwt.client.ui.domain;

import com.google.gwt.user.client.rpc.AsyncCallback;
import org.labkey.api.gwt.client.model.GWTDomain;

import java.util.List;
import java.util.Map;

public interface DomainImporterServiceAsync
{

    void inferenceColumns(AsyncCallback<List<InferencedColumn>> async);

    void updateDomainDescriptor(GWTDomain orig, GWTDomain dd, AsyncCallback<List<String>> async);

    void getDomainDescriptor(String typeURI, AsyncCallback<GWTDomain> async);

    void importData(GWTDomain domain, Map<String, String> mappedColumnNames, AsyncCallback<List<String>> async);
}
