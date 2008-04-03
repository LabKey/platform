package org.labkey.api.exp.api;

import java.util.Date;
import org.labkey.api.security.User;

public interface ExpExperiment extends ExpObject
{
    ExpRun[] getRuns();
    ExpRun[] getRuns(ExpProtocol parentProtocol, ExpProtocol childProtocol);
    ExpProtocol[] getProtocols();
    Date getCreated();
    User getCreatedBy();
    void removeRun(User user, ExpRun run) throws Exception;
    void addRun(User user, ExpRun run) throws Exception;
    void save(User user) throws Exception;
}
