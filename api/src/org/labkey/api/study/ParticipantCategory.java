package org.labkey.api.study;

/**
 * Created by IntelliJ IDEA.
 * User: klum
 * Date: Jul 11, 2012
 */
public interface ParticipantCategory
{
    public enum Type {
        manual,
        list,
        query,
        cohort,
    }
    
    String getLabel();
    String getType();
    String[] getGroupNames();
}
