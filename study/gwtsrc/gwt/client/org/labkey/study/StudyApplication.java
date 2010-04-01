package gwt.client.org.labkey.study;

import com.google.gwt.core.client.EntryPoint;
import com.google.gwt.user.client.ui.RootPanel;
import org.labkey.api.gwt.client.util.PropertyUtil;

/**
 * Created by IntelliJ IDEA.
 * User: matthewb
 * Date: Mar 31, 2010
 * Time: 1:59:56 PM
 */
public class StudyApplication implements EntryPoint
{
    public enum GWTModule
    {
        AssayDesigner("gwt.client.org.labkey.assay.designer.client.AssayDesigner")
        {
            @Override
            EntryPoint getEntryPoint()
            {
                return new gwt.client.org.labkey.assay.designer.client.AssayDesigner();
            }
        },
        ListChooser("gwt.client.org.labkey.assay.upload.client.ListChooser")
        {
            @Override
            EntryPoint getEntryPoint()
            {
                return new gwt.client.org.labkey.assay.upload.client.ListChooser();
            }
        },
        TemplateDesigner("gwt.client.org.labkey.plate.designer.client.TemplateDesigner")
        {
            @Override
            EntryPoint getEntryPoint()
            {
                return new gwt.client.org.labkey.plate.designer.client.TemplateDesigner();
            }
        },
        StudyChartDesigner("gwt.client.org.labkey.study.chart.client.StudyChartDesigner")
        {
            @Override
            EntryPoint getEntryPoint()
            {
                return new gwt.client.org.labkey.study.chart.client.StudyChartDesigner();
            }
        },
        DatasetImporter("gwt.client.org.labkey.study.dataset.client.DatasetImporter")
        {
            @Override
            EntryPoint getEntryPoint()
            {
                return new gwt.client.org.labkey.study.dataset.client.DatasetImporter();
            }
        },
        DatasetDesigner("gwt.client.org.labkey.study.dataset.client.Designer")
        {
            @Override
            EntryPoint getEntryPoint()
            {
                return new gwt.client.org.labkey.study.dataset.client.Designer();
            }
        },
        StudyDesigner("gwt.client.org.labkey.study.designer.client.Designer")
        {
            @Override
            EntryPoint getEntryPoint()
            {
                return new gwt.client.org.labkey.study.designer.client.Designer();
            }
        };


        public final String className;

        GWTModule(String clss)
        {
            this.className = clss;
        }

        abstract EntryPoint getEntryPoint();
    }
    

    public static RootPanel getRootPanel()
    {
        String name = PropertyUtil.getServerProperty("RootPanel");
        if (null == name)
            name = "gwt.StudyApplication-Root";
        return RootPanel.get(name);
    }


    public void onModuleLoad()
    {
        GWTModule module = GWTModule.valueOf(PropertyUtil.getServerProperty("GWTModule"));
        EntryPoint entry = module.getEntryPoint();
        entry.onModuleLoad();
    }
}
