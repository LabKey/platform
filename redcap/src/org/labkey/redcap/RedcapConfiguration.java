/*
 * Copyright (c) 2013-2015 LabKey Corporation. All rights reserved. No portion of this work may be reproduced in
 * any form or by any electronic or mechanical means without written permission from LabKey Corporation.
 */
package org.labkey.redcap;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.util.XmlBeansUtil;
import org.labkey.study.xml.TimepointType;
import org.labkey.study.xml.redcapExport.RedcapConfigDocument;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * User: klum
 * Date: 6/19/13
 */
public class RedcapConfiguration
{
    public enum Params
    {
        serverUrl,
        projectName,
        subjectId,
        archivePath,
        timepointType,
        configFile,
    }

    public enum DuplicateNamePolicy
    {
        fail,
        merge,
    }

    private DuplicateNamePolicy _duplicateNamePolicy = DuplicateNamePolicy.fail;
    private TimepointType.Enum _timepointType = TimepointType.DATE;
    private List<RedcapProject> _projects = new ArrayList<>();
    private Map<String, String> _tokenMap = new HashMap<>();
    private boolean _mergeLookups = true;

    private RedcapConfiguration(){}

    /**
     * Create a configuration from a saved settings object, this is to serve as a bridge between the new code and the
     * legacy code. Over time it would be good to merge a configuration and settings object.
     *
     * @param savedSettings
     */
    RedcapConfiguration(PipelineJob job, RedcapManager.RedcapSettings savedSettings)
    {
        try
        {
            // create the project to token map
            for (int i=0; i < savedSettings.getProjectname().length; i++)
            {
                _tokenMap.put(savedSettings.getProjectname()[i], savedSettings.getToken()[i]);
            }

            // parse the settings from the xml metadata
            RedcapConfigDocument doc = RedcapConfigDocument.Factory.parse(savedSettings.getMetadata(), XmlBeansUtil.getDefaultParseOptions());
            parseConfiguration(job, doc);


        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    // constructor used for testing
    RedcapConfiguration(String serverUrl, String projectName, String subjectId, File archiveFile, TimepointType.Enum timepointType)
    {
        _projects.add(new RedcapProject(projectName, subjectId, serverUrl, false));

        _timepointType = timepointType;
    }

    /**
     * Returns the api token for the specified redcap project name
     */
    @Nullable
    public String getToken(String projectName)
    {
        return _tokenMap.get(projectName);
    }

    public String getSummary()
    {
        StringBuffer summary = new StringBuffer();

        summary.append("Command Options").append("\n");
        summary.append("timepoint type:").append("\t").append(getTimepointType().toString()).append("\n");

        for (RedcapProject project : getProjects())
        {
            summary.append("project name:").append("\t").append(project.getProjectName());
            summary.append("\t").append("server URL :").append("\t").append(project.getServerUrl());
            summary.append("\t").append("subject ID :").append("\t").append(project.getSubjectId()).append("\n");
            summary.append("\t").append("match subject ID by label :").append("\t").append(project.isMatchSubjectIdByLabel()).append("\n");
        }
        return summary.toString();
    }

    public TimepointType.Enum getTimepointType()
    {
        return _timepointType;
    }

    public List<RedcapProject> getProjects()
    {
        return _projects;
    }

    public Map<String, RedcapProject> getProjectMap()
    {
        Map<String, RedcapProject> projectMap = new HashMap<>();

        for (RedcapProject project : getProjects())
            projectMap.put(project.getProjectName(), project);
        return projectMap;
    }

    public boolean isUnzipContents()
    {
        return true;
        //return _unzipContents;
    }

    public DuplicateNamePolicy getDuplicateNamePolicy()
    {
        return _duplicateNamePolicy;
    }

    public boolean isMergeLookups()
    {
        return _mergeLookups;
    }

    private void parseConfiguration(PipelineJob job, RedcapConfigDocument doc)
    {
        try
        {
            RedcapConfigDocument.RedcapConfig rc = doc.getRedcapConfig();

            String timepointType = rc.getTimepointType();
            Set<String> projectNames = new HashSet<>();

            if (rc.isSetDuplicateNamePolicy())
            {
                String duplicateNamePolicy = rc.getDuplicateNamePolicy().toString();
                if (duplicateNamePolicy != null)
                    _duplicateNamePolicy = DuplicateNamePolicy.valueOf(duplicateNamePolicy);
            }

            if ("visit".equalsIgnoreCase(timepointType))
                _timepointType = TimepointType.VISIT;

            if (rc.isSetMergeLookups())
            {
                _mergeLookups = rc.getMergeLookups();
            }

            for (RedcapConfigDocument.RedcapConfig.Projects.Project project : rc.getProjects().getProjectArray())
            {
                if (!projectNames.contains(project.getProjectName()))
                {
                    projectNames.add(project.getProjectName());
                    String projectName = project.getProjectName();
                    String serverUrl = project.getServerUrl();
                    String subjectId = project.getSubjectId();

                    if (subjectId == null)
                    {
                        job.warn("No subject ID specified in the configuration, defaulting to patient_id");
                        subjectId = "patient_id";
                    }

                    RedcapProject rcProject = new RedcapProject(projectName, subjectId, serverUrl, project.getDemographic());
                    rcProject.setMatchSubjectIdByLabel(project.getMatchSubjectIdByLabel());

                    // pick up any redcap form specific configurations
                    RedcapConfigDocument.RedcapConfig.Projects.Project.Forms forms = project.getForms();
                    if (forms != null)
                    {
                        Map<String, RedcapForm> formMap = new HashMap<>();

                        for (RedcapConfigDocument.RedcapConfig.Projects.Project.Forms.Form form : forms.getFormArray())
                        {
                            formMap.put(form.getFormName(), new RedcapForm(form.getFormName(), form.getDemographic(), form.getDateField()));
                        }
                        rcProject.setFormMap(formMap);
                    }
                    _projects.add(rcProject);
                }
                else
                {
                    job.error("Duplicate project names in the configuration file: " + project.getProjectName());
                    throw new IllegalArgumentException("Duplicate project names in the configuration file: " + project.getProjectName());
                }
            }
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    public static class RedcapProject
    {
        private String _projectName;
        private String _subjectId;
        private String _serverUrl;
        private boolean _isMatchSubjectIdByLabel;
        private boolean _isDemographic;
        private Map<String, RedcapForm> _formMap = new HashMap<>();
        private Pattern _subjectIdPattern;

        public RedcapProject(String projectName, String subjectId, String serverUrl, boolean isDemographic)
        {
            _projectName = projectName;
            _subjectId = subjectId;
            _serverUrl = serverUrl;
            _isDemographic = isDemographic;
        }

        public boolean isMatchSubjectIdByLabel()
        {
            return _isMatchSubjectIdByLabel;
        }

        public void setMatchSubjectIdByLabel(boolean matchSubjectIdByLabel)
        {
            _subjectIdPattern = Pattern.compile(_subjectId, Pattern.CASE_INSENSITIVE);
            _isMatchSubjectIdByLabel = matchSubjectIdByLabel;
        }

        public boolean subjectIdMatches(String name)
        {
            if (_isMatchSubjectIdByLabel && _subjectIdPattern != null)
                return _subjectIdPattern.matcher(name).find();

            return false;
        }

        public String getProjectName()
        {
            return _projectName;
        }

        public String getSubjectId()
        {
            return _subjectId;
        }

        public String getServerUrl()
        {
            return _serverUrl;
        }

        public boolean isDemographic()
        {
            return _isDemographic;
        }

        public Map<String, RedcapForm> getFormMap()
        {
            return _formMap;
        }

        public void setFormMap(Map<String, RedcapForm> formMap)
        {
            _formMap = formMap;
        }
    }

    public static class RedcapForm
    {
        private String _name;
        private boolean _isDemographic;     // export as a demographic dataset
        private String _dateField;          // the name of the redcap field to use as the date (only relevant for date based studies)

        public RedcapForm(String name, boolean isDemographic, String dateField)
        {
            _name = name;
            _isDemographic = isDemographic;
            _dateField = dateField;
        }

        public String getName()
        {
            return _name;
        }

        public void setName(String name)
        {
            _name = name;
        }

        public boolean isDemographic()
        {
            return _isDemographic;
        }

        public void setDemographic(boolean demographic)
        {
            _isDemographic = demographic;
        }

        public String getDateField()
        {
            return _dateField;
        }

        public void setDateField(String dateField)
        {
            _dateField = dateField;
        }
    }
}
