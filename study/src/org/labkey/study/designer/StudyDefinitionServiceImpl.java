/*
 * Copyright (c) 2007-2009 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.labkey.study.designer;

import org.apache.log4j.Logger;
import org.labkey.api.data.Container;
import org.labkey.api.gwt.server.BaseRemoteService;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.view.ViewContext;
import org.labkey.study.controllers.designer.DesignerController;
import org.labkey.study.designer.client.StudyDefinitionService;
import org.labkey.study.designer.client.model.GWTAssayDefinition;
import org.labkey.study.designer.client.model.GWTStudyDefinition;
import org.labkey.study.designer.client.model.GWTStudyDesignVersion;
import org.labkey.study.xml.StudyDesignDocument;

import java.sql.SQLException;

/**
 * Created by IntelliJ IDEA.
 * User: Mark Igra
 * Date: Feb 14, 2007
 * Time: 9:21:22 PM
 */
public class StudyDefinitionServiceImpl extends BaseRemoteService implements StudyDefinitionService
{
    private static Logger _log = Logger.getLogger(StudyDefinitionServiceImpl.class);

    public StudyDefinitionServiceImpl(ViewContext context)
    {
        super(context);
    }

    public GWTStudyDesignVersion save(GWTStudyDefinition def)
    {
        if (!getContainer().hasPermission(getUser(), UpdatePermission.class))
        {
            GWTStudyDesignVersion result = new GWTStudyDesignVersion();
            result.setSaveSuccessful(false);
            result.setErrorMessage("You do not have permission to save");
            return result;
        }
        try
        {
            StudyDesignDocument design = XMLSerializer.toXML(def);
            StudyDesignVersion version = new StudyDesignVersion();
            version.setXML(design.toString());
            version.setStudyId(def.getCavdStudyId());
            version.setLabel(def.getStudyName());
            GWTStudyDesignVersion result = StudyDesignManager.get().saveStudyDesign(getUser(), getContainer(), version).toGWTVersion(_context);
            result.setSaveSuccessful(true);
            return result;
        }
        catch (SaveException se)
        {
            GWTStudyDesignVersion result = new GWTStudyDesignVersion();
            result.setSaveSuccessful(false);
            result.setErrorMessage(se.getMessage());
            return result;
        }
        catch (SQLException x)
        {
            _log.error(x);
            ExceptionUtil.logExceptionToMothership(getThreadLocalRequest(), x);
            GWTStudyDesignVersion result = new GWTStudyDesignVersion();
            result.setSaveSuccessful(false);
            result.setErrorMessage("Save failed: " + x.getMessage());
            return result;
        }

    }

    public GWTStudyDefinition getBlank()
    {
        try
        {
            GWTStudyDefinition def = DesignerController.getTemplate(getUser(), getContainer());
            //Lock the assays
            for (int i = 0; i < def.getAssays().size(); i++)
                ((GWTAssayDefinition) def.getAssays().get(i)).setLocked(true);
            def.setCavdStudyId(0);
            def.setRevision(0);
            def.setStudyName(null);

            return def;
        }
        catch (Exception e)
        {
            throw UnexpectedException.wrap(e);
        }
    }

    public GWTStudyDefinition getRevision(int studyId, int revision)
    {
        try
        {
            Container container = getContainer();
            StudyDesignVersion version;
            if (revision >= 0)
                version = StudyDesignManager.get().getStudyDesignVersion(container, studyId, revision);
            else
                version = StudyDesignManager.get().getStudyDesignVersion(container, studyId);

            GWTStudyDefinition template = getTemplate();
            GWTStudyDefinition def = XMLSerializer.fromXML(version.getXML(), template.getCavdStudyId() == studyId ? null : template);
            def.setCavdStudyId(version.getStudyId());
            def.setRevision(version.getRevision());
            return def;
        }
        catch (Exception e)
        {
            throw UnexpectedException.wrap(e);
        }
    }

    public GWTStudyDefinition getTemplate()
    {
        try
        {
            return DesignerController.getTemplate(getUser(), getContainer());
        }
        catch (Exception e)
        {
            throw UnexpectedException.wrap(e);
        }
    }

    public GWTStudyDesignVersion[] getVersions(int studyId)
    {
        try
        {
            StudyDesignVersion[] versions = StudyDesignManager.get().getStudyDesignVersions(getContainer(), studyId);
            GWTStudyDesignVersion[] gwtVersions = new GWTStudyDesignVersion[versions.length];
            for (int i = 0; i < versions.length; i++)
                gwtVersions[i] = versions[i].toGWTVersion(_context);

            return gwtVersions;
        }
        catch (SQLException e)
        {
            throw UnexpectedException.wrap(e);
        }
    }

}
