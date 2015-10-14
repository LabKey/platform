/*
 * Copyright (c) 2015 LabKey Corporation. All rights reserved. No portion of this work may be reproduced in
 * any form or by any electronic or mechanical means without written permission from LabKey Corporation.
 */
package org.labkey.test.util.redcap;

import org.jetbrains.annotations.NotNull;
import org.labkey.test.TestFileUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ConfigXmlBuilder
{
    private static final File templateXmlFile = TestFileUtils.getSampleData("redcap/template.xml");
    private static final String templateXml = TestFileUtils.getFileContents(templateXmlFile);
    private List<RedCapProject> _projects;
    private String _timepointType;
    private String _duplicateNamePolicy;

    public ConfigXmlBuilder()
    {
        _projects = new ArrayList<>();
    }

    public ConfigXmlBuilder withProjects(RedCapProject... projects)
    {
        _projects = new ArrayList<>(Arrays.asList(projects));
        return this;
    }

    public ConfigXmlBuilder withTimepointType(String timepointType)
    {
        _timepointType = timepointType;
        return this;
    }

    public ConfigXmlBuilder withDuplicateNamePolicy(String duplicateNamePolicy)
    {
        _duplicateNamePolicy = duplicateNamePolicy;
        return this;
    }

    public String build()
    {
        String configXml = templateXml
                .replaceFirst("(<red:timepointType>)(.*)(</red:timepointType>)",
                        _timepointType != null ? "$1" + _timepointType + "$3" : "")
                .replaceFirst("(<red:duplicateNamePolicy>)(.*)(</red:duplicateNamePolicy>)",
                        _duplicateNamePolicy != null ? "$1" + _duplicateNamePolicy + "$3" : "");

        Pattern formPattern = Pattern.compile("(<red:projects>)(.*)(</red:projects>)", Pattern.DOTALL);

        if (_projects.size() > 0)
        {
            Matcher matcher = formPattern.matcher(configXml);
            matcher.find();
            final String templateFormSnippet = matcher.group(2);

            StringBuilder formSnippets = new StringBuilder();
            formSnippets.append(matcher.group(1));
            formSnippets.append("\n");
            for (RedCapProject project : _projects)
            {
                formSnippets.append(project.build(templateFormSnippet));
            }
            formSnippets.append(matcher.group(3));

            configXml = matcher.replaceFirst(formSnippets.toString());
        }

        return configXml;
    }

    public static class RedCapProject
    {
        private final String _serverUrl;
        private final String _projectName;
        private final String _subjectId;
        private Boolean _matchSubjectIdByLabel;
        private Boolean _demographic;
        private List<RedCapProjectForm> _forms;

        public RedCapProject(@NotNull String serverUrl, @NotNull String projectName, @NotNull String subjectId)
        {
            _serverUrl = serverUrl;
            _projectName = projectName;
            _subjectId = subjectId;
            _forms = new ArrayList<>();
        }

        public RedCapProject withMatchSubjectIdByLabel(Boolean matchSubjectIdByLabel)
        {
            _matchSubjectIdByLabel = matchSubjectIdByLabel;
            return this;
        }

        public RedCapProject withDemographic(Boolean demographic)
        {
            _demographic = demographic;
            return this;
        }

        public RedCapProject withForms(RedCapProjectForm... forms)
        {
            _forms = new ArrayList<>(Arrays.asList(forms));
            return this;
        }

        protected String build(String templateProjectSnippet)
        {
            templateProjectSnippet = templateProjectSnippet
                    .replace("@@serverUrl@@", _serverUrl)
                    .replace("@@projectName@@", _projectName)
                    .replace("@@subjectId@@", _subjectId)
                    .replaceFirst("(<red:matchSubjectIdByLabel>)(.*)(</red:matchSubjectIdByLabel>)",
                            _matchSubjectIdByLabel != null ? "$1" + _matchSubjectIdByLabel.toString() + "$3" : "")
                    .replaceFirst("(<red:demographic>)(.*)(</red:demographic>)",
                            _demographic != null ? "$1" + _demographic.toString() + "$3" : "");

            Pattern formPattern = Pattern.compile("(<red:forms>)(.*)(</red:forms>)", Pattern.DOTALL);

            Matcher matcher = formPattern.matcher(templateProjectSnippet);
            matcher.find();
            final String templateFormSnippet = matcher.group(2);

            if (_forms.size() > 0)
            {
                StringBuilder formSnippets = new StringBuilder();
                formSnippets.append(matcher.group(1));
                formSnippets.append("\n");
                for (RedCapProjectForm form : _forms)
                {
                    formSnippets.append(form.build(templateFormSnippet));
                }
                formSnippets.append(matcher.group(3));

                templateProjectSnippet = matcher.replaceFirst(formSnippets.toString());
            }
            else
            {
                templateProjectSnippet = matcher.replaceFirst("");
            }

            return templateProjectSnippet;
        }
    }

    public static class RedCapProjectForm
    {
        private final String _formName;
        private final String _dateField;
        private final boolean _demographic;

        public RedCapProjectForm(@NotNull String formName, @NotNull String dateField, boolean demographic)
        {
            _formName = formName;
            _dateField = dateField;
            _demographic = demographic;
        }

        protected String build(String templateFormSnippet)
        {
            return templateFormSnippet
                    .replace("@@formName@@", _formName)
                    .replace("@@dateField@@", _dateField)
                    .replace("@@demographic@@", String.valueOf(_demographic));
        }
    }
}
