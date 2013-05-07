package org.labkey.api.ehr.dataentry;

import org.json.JSONObject;
import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.security.User;
import org.labkey.api.util.Pair;
import org.labkey.api.view.template.ClientDependency;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Created with IntelliJ IDEA.
 * User: bimber
 * Date: 4/27/13
 * Time: 8:36 AM
 */
public interface FormSection
{
    abstract public String getName();

    abstract public String getLabel();

    abstract public String getXtype();

    abstract public boolean hasPermission(Container c, User u);

    abstract public Set<Pair<String, String>> getTableNames();

    abstract public Set<TableInfo> getTables(Container c, User u);

    abstract public JSONObject toJSON(Container c, User u);

    abstract public LinkedHashSet<ClientDependency> getClientDependencies();
}
