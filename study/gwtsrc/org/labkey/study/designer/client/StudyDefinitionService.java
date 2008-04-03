package org.labkey.study.designer.client;

import com.google.gwt.user.client.rpc.RemoteService;
import org.labkey.study.designer.client.model.GWTStudyDefinition;
import org.labkey.study.designer.client.model.GWTStudyDesignVersion;

/**
 * Created by IntelliJ IDEA.
 * User: Mark Igra
 * Date: Feb 14, 2007
 * Time: 8:46:26 PM
 */
public interface StudyDefinitionService extends RemoteService
{
    public GWTStudyDesignVersion save(GWTStudyDefinition def);
    public GWTStudyDefinition getBlank() throws Exception;
    public GWTStudyDefinition getRevision(int studyId, int revision) throws Exception;
    public GWTStudyDesignVersion[] getVersions(int studyId) throws Exception;
    public GWTStudyDefinition getTemplate() throws Exception;
}
