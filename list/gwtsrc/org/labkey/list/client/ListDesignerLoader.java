package org.labkey.list.client;

import com.google.gwt.core.client.EntryPoint;
import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.RunAsyncCallback;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.RootPanel;

/**
 * Created by IntelliJ IDEA.
 * User: matthewb
 * Date: Apr 21, 2010
 * Time: 1:43:55 PM
 */
public class ListDesignerLoader implements EntryPoint
{
    public void onModuleLoad()
    {
        RootPanel root =  RootPanel.get("org.labkey.list.Designer-Root");

        GWT.runAsync(new RunAsyncCallback(){
            public void onFailure(Throwable reason)
            {
                Window.alert(reason.getMessage());
            }

            public void onSuccess()
            {
                new ListDesigner().onModuleLoad();
            }
        });
    }
}
