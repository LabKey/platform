package org.labkey.study.samples.report.request;

import org.labkey.study.samples.report.specimentype.TypeReportFactory;
import org.labkey.api.util.Pair;

import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: brittp
 * Date: Jun 25, 2009
 * Time: 10:57:36 AM
 */
public abstract class BaseRequestReportFactory extends TypeReportFactory
{
    private boolean _completedRequestsOnly = false;

    public boolean isCompletedRequestsOnly()
    {
        return _completedRequestsOnly;
    }

    public void setCompletedRequestsOnly(boolean completedRequestsOnly)
    {
        _completedRequestsOnly = completedRequestsOnly;
    }

    public List<Pair<String, String>> getAdditionalFormInputHtml()
    {
        List<Pair<String, String>> superInputs = super.getAdditionalFormInputHtml();

        StringBuilder builder = new StringBuilder();
        builder.append("<select name=\"completedRequestsOnly\">\n");
        builder.append("<option value=\"false\">Include in-process requests</option>\n");
        builder.append("<option value=\"true\"").append(_completedRequestsOnly ? " SELECTED" : "");
        builder.append(">Completed requests only</option>\n");
        builder.append("</select>");
        superInputs.add(new Pair<String, String>("Request status", builder.toString()));
        return superInputs;
    }
}
