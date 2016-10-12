package gwt.client.org.labkey.study.designer.client;

import com.google.gwt.user.client.ui.HTML;
import org.labkey.api.gwt.client.util.PropertyUtil;

public class DeprecatedMessagePanel extends HTML
{
    public DeprecatedMessagePanel(String panelName)
    {
        String html = "<p class='labkey-error'><b>Notice</b>: this study design editor format has been deprecated. " +
            "Please create any new study designs in the format as defined by the ";

        if (panelName != null && panelName.toLowerCase().equals("vaccine"))
        {
            String manageLinkTxt = "Manage Study Products";
            String manageUrl = PropertyUtil.getRelativeURL("manageStudyProducts", "study-design");
            html += "<a class='labkey-text-link' href='" + manageUrl + "'>" + manageLinkTxt + "</a> link ";
        }
        else if (panelName != null && panelName.toLowerCase().equals("immunizations"))
        {
            String manageLinkTxt = "Manage Treatments";
            String manageUrl = PropertyUtil.getRelativeURL("manageTreatments", "study-design");
            html += "<a class='labkey-text-link' href='" + manageUrl + "'>" + manageLinkTxt + "</a> link ";
        }
        else if (panelName != null && panelName.toLowerCase().equals("assays"))
        {
            String manageLinkTxt = "Manage Assay Schedule";
            String manageUrl = PropertyUtil.getRelativeURL("manageAssaySchedule", "study-design");
            html += "<a class='labkey-text-link' href='" + manageUrl + "'>" + manageLinkTxt + "</a> link ";
        }
        else
        {
            html += "manage links ";
        }

        html += "on the study Manage tab.</p>";

        setHTML(html);
    }
}
