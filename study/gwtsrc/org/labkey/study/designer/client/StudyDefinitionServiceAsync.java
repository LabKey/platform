package org.labkey.study.designer.client;

import org.labkey.study.designer.client.model.GWTStudyDefinition;
import com.google.gwt.user.client.rpc.AsyncCallback;

/**
 * Created by IntelliJ IDEA.
 * User: Mark Igra
 * Date: Feb 14, 2007
 * Time: 8:47:24 PM
 */
public interface StudyDefinitionServiceAsync
{

    void save(GWTStudyDefinition def, AsyncCallback async);

    void getBlank(AsyncCallback async);

    void getRevision(int studyId, int revision, AsyncCallback async);

    void getVersions(int studyId, AsyncCallback async);

    void getTemplate(AsyncCallback async);
}
