package org.labkey.filecontent.designer.client;

import com.google.gwt.user.client.rpc.AsyncCallback;
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.gwt.client.ui.LookupServiceAsync;

import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: klum
 * Date: Mar 25, 2010
 * Time: 12:44:09 PM
 * To change this template use File | Settings | File Templates.
 */
public interface FilePropertiesServiceAsync extends LookupServiceAsync
{
    void getDomainDescriptor(String typeURI, AsyncCallback<GWTDomain> async);
    void updateDomainDescriptor(GWTDomain orig, GWTDomain dd, AsyncCallback<List<String>> async);
}
