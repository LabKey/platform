package org.labkey.study.view;

import com.google.gwt.core.client.EntryPoint;
import gwt.client.org.labkey.study.StudyApplication;
import org.labkey.api.view.GWTView;

import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: matthewb
 * Date: Mar 31, 2010
 * Time: 2:21:17 PM
 */
public class StudyGWTView extends GWTView
{
    public StudyGWTView(StudyApplication.GWTModule module, Map<String, String> properties)
    {
        super("gwt.StudyApplication", properties);
        getModelBean().getProperties().put("GWTModule", module.name());
    }

    public StudyGWTView(Class<? extends EntryPoint> clss, Map<String, String> properties)
    {
        this(clss.getName(), properties);
    }

    public StudyGWTView(String clss, Map<String, String> properties)
    {
        super("gwt.StudyApplication", properties);
        for (StudyApplication.GWTModule m : StudyApplication.GWTModule.values())
        {
            if (m.className.equals(clss))
            {
                getModelBean().getProperties().put("GWTModule", m.name());
                return;
            }
        }
        throw new IllegalArgumentException(clss);
    }
}
