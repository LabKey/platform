package org.labkey.study.model;

import org.labkey.api.security.User;

/**
 * Created by IntelliJ IDEA.
 * User: klum
 * Date: Nov 3, 2011
 * Time: 11:25:52 AM
 */
public interface ParticipantCategoryListener
{
    void categoryDeleted(User user, ParticipantCategory category) throws Exception;
    void categoryCreated(User user, ParticipantCategory category) throws Exception;
    void categoryUpdated(User user, ParticipantCategory category) throws Exception;
}
