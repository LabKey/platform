/*
 * Copyright (c) 2008-2019 LabKey Corporation
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
package org.labkey.pipeline.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.old.JSONObject;
import org.labkey.api.admin.InvalidFileException;
import org.labkey.api.cache.BlockingCache;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.cache.DbCache;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.Filter;
import org.labkey.api.data.ObjectFactory;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.pipeline.DirectoryNotDeletedException;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.PipelineUrls;
import org.labkey.api.pipeline.trigger.PipelineTriggerConfig;
import org.labkey.api.pipeline.trigger.PipelineTriggerRegistry;
import org.labkey.api.pipeline.trigger.PipelineTriggerType;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.QueryUpdateServiceException;
import org.labkey.api.query.UserSchema;
import org.labkey.api.search.SearchService;
import org.labkey.api.security.User;
import org.labkey.api.security.ValidEmail;
import org.labkey.api.settings.LookAndFeelProperties;
import org.labkey.api.trigger.TriggerConfiguration;
import org.labkey.api.util.ConfigurationException;
import org.labkey.api.util.ContainerUtil;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.MailHelper;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.util.XmlBeansUtil;
import org.labkey.api.util.emailTemplate.EmailTemplate;
import org.labkey.api.util.emailTemplate.EmailTemplateService;
import org.labkey.api.util.emailTemplate.UserOriginatedEmailTemplate;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.webdav.WebdavService;
import org.labkey.api.writer.ZipUtil;
import org.labkey.folder.xml.FolderDocument;
import org.labkey.pipeline.PipelineWebdavProvider;
import org.labkey.pipeline.status.StatusController;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;

import javax.mail.Address;
import javax.mail.Message;
import javax.mail.internet.MimeMessage;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.FileSystemAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static org.labkey.api.action.SpringActionController.ERROR_MSG;


/**
 * Manages pipeline root configurations and notification emails for job success and failures.
 */
public class PipelineManager
{
    private static final Logger _log = LogManager.getLogger(PipelineManager.class);
    private static final PipelineSchema pipeline = PipelineSchema.getInstance();
    private static final BlockingCache<String, PipelineRoot> CACHE = CacheManager.getBlockingStringKeyCache(CacheManager.UNLIMITED, CacheManager.DAY, "Pipeline roots",
        (key, argument) -> new TableSelector(pipeline.getTableInfoPipelineRoots(), (Filter)argument, null).getObject(PipelineRoot.class));

    protected static PipelineRoot getPipelineRootObject(Container container, String type)
    {
        SimpleFilter filter = SimpleFilter.createContainerFilter(container);
        filter.addCondition(FieldKey.fromParts("Type"), type);

        return CACHE.get(getCacheKey(container, type), filter);
    }

    private static String getCacheKey(Container c, @Nullable String type)
    {
        return c.getId() + "/" + StringUtils.trimToEmpty(type);
    }

    @Nullable
    public static PipelineRoot findPipelineRoot(@NotNull Container container)
    {
        return findPipelineRoot(container, PipelineService.PRIMARY_ROOT);
    }

    @Nullable
    public static PipelineRoot findPipelineRoot(@NotNull Container container, String type)
    {
        while (container != null && !container.isRoot())
        {
            PipelineRoot pipelineRoot = getPipelineRootObject(container, type);
            if (null != pipelineRoot)
                return pipelineRoot;
            container = container.getParent();
        }
        return null;
    }


    static public PipelineRoot[] getPipelineRoots(String type)
    {
        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("Type"), type);

        return new TableSelector(pipeline.getTableInfoPipelineRoots(), filter, null).getArray(PipelineRoot.class);
    }

    static public void setPipelineRoot(User user, Container container, URI[] roots, String type,
                                       boolean searchable)
    {
        PipelineRoot oldValue = getPipelineRootObject(container, type);
        PipelineRoot newValue = null;

        try
        {
            if (roots == null || roots.length == 0 || (roots.length == 1 && roots[0] == null))
            {
                if (oldValue != null)
                {
                    Table.delete(PipelineSchema.getInstance().getTableInfoPipelineRoots(), oldValue.getPipelineRootId());
                }
            }
            else
            {
                if (oldValue == null)
                {
                    newValue = new PipelineRoot();
                }
                else
                {
                    newValue = new PipelineRoot(oldValue);
                }
                newValue.setPath(roots[0].toString());
                newValue.setSupplementalPath(roots.length > 1 ? roots[1].toString() : null);
                newValue.setContainerId(container.getId());
                newValue.setType(type);
                newValue.setSearchable(searchable);
                if (oldValue == null)
                {
                    Table.insert(user, pipeline.getTableInfoPipelineRoots(), newValue);
                }
                else
                {
                    Table.update(user, pipeline.getTableInfoPipelineRoots(), newValue, newValue.getPipelineRootId());
                }

                org.labkey.api.util.Path davPath = WebdavService.getPath().append(container.getParsedPath()).append(PipelineWebdavProvider.PIPELINE_LINK);
                SearchService ss = SearchService.get();
                if (null != ss)
                    ss.addPathToCrawl(davPath, null);
            }
        }
        finally
        {
            CACHE.remove(getCacheKey(container, type));
        }

        ContainerManager.firePropertyChangeEvent(new ContainerManager.ContainerPropertyChangeEvent(
                container, user, ContainerManager.Property.PipelineRoot, oldValue, newValue));
    }

    static public void purge(Container container, User user)
    {
        SQLFragment sql = new SQLFragment();
        sql.append("UPDATE ").append(ExperimentService.get().getTinfoExperimentRun()).
                append(" SET JobId = NULL WHERE JobId IN (SELECT RowId FROM ").
                append(pipeline.getTableInfoStatusFiles(), "p").
                append(" WHERE container = ? ) AND Container = ?");
        sql.add(container.getId());
        sql.add(container.getId());

        try (DbScope.Transaction transaction = ExperimentService.get().ensureTransaction())
        {
            DbCache.clear(ExperimentService.get().getTinfoExperimentRun());
            new SqlExecutor(PipelineSchema.getInstance().getSchema()).execute(sql);

            ContainerUtil.purgeTable(pipeline.getTableInfoStatusFiles(), container, "Container");

            transaction.commit();
        }

        try
        {
            ContainerUtil.purgeTable(pipeline.getTableInfoPipelineRoots(), container, "Container");
        }
        finally
        {
            CACHE.remove(getCacheKey(container, null));
        }

        // Delete trigger configurations through the UserSchema so that we stop any associated listeners. See issue 33986
        try
        {
            PipelineQuerySchema schema = new PipelineQuerySchema(user, container);
            TableInfo table = schema.createTriggerConfigurationsTable(null);  // bypass security check since this is internal, see issue 36249
            table.getUpdateService().truncateRows(user, container, null, null);
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
        catch (QueryUpdateServiceException | BatchValidationException e)
        {
            throw new UnexpectedException(e);
        }
    }

    static void setPipelineProperty(Container container, String name, String value)
    {
        PropertyManager.PropertyMap props = PropertyManager.getWritableProperties(container, "pipelineRoots", true);
        if (value == null)
            props.remove(name);
        else
            props.put(name, value);
        props.save();
    }

    static String getPipelineProperty(Container container, String name)
    {
        Map<String, String> props = PropertyManager.getProperties(container, "pipelineRoots");
        return props.get(name);
    }

    public static void sendNotificationEmail(PipelineStatusFileImpl statusFile, Container c, User user)
    {
        PipelineMessage message;
        if (PipelineJob.TaskStatus.complete.matches(statusFile.getStatus()))
        {
            String interval = PipelineEmailPreferences.get().getSuccessNotificationInterval(c);
            if (!"0".equals(interval) && interval != null) return;

            message = createPipelineMessage(c, statusFile,
                    EmailTemplateService.get().getEmailTemplate(PipelineJobSuccess.class),
                    PipelineEmailPreferences.get().getNotifyOwnerOnSuccess(c),
                    PipelineEmailPreferences.get().getNotifyUsersOnSuccess(c));
        }
        else
        {
            String interval = PipelineEmailPreferences.get().getFailureNotificationInterval(c);
            if (!"0".equals(interval) && interval != null)
            {
                _log.info("Deciding not to send error notification email based on interval " + interval);
                return;
            }

            _log.info("Creating error notification email");
            message = createPipelineMessage(c, statusFile,
                    EmailTemplateService.get().getEmailTemplate(PipelineJobFailed.class),
                    PipelineEmailPreferences.get().getNotifyOwnerOnError(c),
                    PipelineEmailPreferences.get().getNotifyUsersOnError(c));
            if (message == null)
            {
                _log.info("Did not create a message for error notification email");
            }
        }

        try
        {
            if (message != null)
            {
                Message m = message.createMessage(user);
                MailHelper.send(m, null, c);
            }
        }
        catch (ConfigurationException me)
        {
            _log.error("Failed sending an email notification message for a pipeline job", me);
        }
    }

    public static void sendNotificationEmail(PipelineStatusFileImpl[] statusFiles, Container c, Date min, Date max, boolean isSuccess)
    {
        PipelineDigestTemplate template = isSuccess ?
                EmailTemplateService.get().getEmailTemplate(PipelineDigestJobSuccess.class) :
                EmailTemplateService.get().getEmailTemplate(PipelineDigestJobFailed.class);

        PipelineDigestMessage[] messages = createPipelineDigestMessage(c, statusFiles, template,
                PipelineEmailPreferences.get().getNotifyOwnerOnSuccess(c),
                PipelineEmailPreferences.get().getNotifyUsersOnSuccess(c),
                min, max);

        if (messages != null)
        {
            for (PipelineDigestMessage msg : messages)
            {
                try
                {
                    Message m = msg.createMessage();
                    MailHelper.send(m, null, c);
                }
                catch (ConfigurationException me)
                {
                    // Stop trying if email is misconfigured
                    _log.error("Failed sending an email notification message for a pipeline job", me);
                    return;
                }
                catch (Exception e)
                {
                    // Keep trying to send to other recipients
                    ExceptionUtil.logExceptionToMothership(null, e);
                }
            }
        }
    }

    private static PipelineMessage createPipelineMessage(Container c, PipelineStatusFileImpl statusFile,
                                                        PipelineEmailTemplate template,
                                                        boolean notifyOwner, String notifyUsers)
    {
        if (notifyOwner || !StringUtils.isEmpty(notifyUsers))
        {
            StringBuilder sb = new StringBuilder();

            if (notifyOwner && !StringUtils.isEmpty(statusFile.getEmail()))
            {
                try
                {
                    ValidEmail ve = new ValidEmail(statusFile.getEmail());
                    sb.append(ve.getEmailAddress());
                    sb.append(';');
                }
                catch (ValidEmail.InvalidEmailException e)
                {
                    _log.warn("Pipeline job status file uses an invalid email: " + statusFile.getEmail() + ". RowId: " + statusFile.getRowId());
                }
            }

            if (!StringUtils.isEmpty(notifyUsers))
                sb.append(notifyUsers);

            if (sb.length() > 0)
            {
                PipelineMessage message = new PipelineMessage(c, template, statusFile);
                message.setRecipients(sb.toString());
                return message;
            }
        }
        return null;
    }

    private static PipelineDigestMessage[] createPipelineDigestMessage(Container c, PipelineStatusFileImpl[] statusFiles,
                                                        PipelineDigestTemplate template,
                                                        boolean notifyOwner, String notifyUsers,
                                                        Date min, Date max)
    {
        if (notifyOwner || !StringUtils.isEmpty(notifyUsers))
        {
            Map<String, StringBuilder> recipients = new HashMap<>();
            for (PipelineStatusFileImpl sf : statusFiles)
            {
                if (notifyOwner && !StringUtils.isEmpty(sf.getEmail()))
                {
                    if (!recipients.containsKey(sf.getEmail()))
                    {
                        StringBuilder sb = new StringBuilder();
                        sb.append(sf.getEmail());
                        sb.append(';');

                        if (!StringUtils.isEmpty(notifyUsers))
                            sb.append(notifyUsers);

                        recipients.put(sf.getEmail(), sb);
                    }
                }
            }

            if (recipients.isEmpty() && !StringUtils.isEmpty(notifyUsers))
            {
                StringBuilder sb = new StringBuilder();
                sb.append(notifyUsers);

                recipients.put("notifyUsers", sb);
            }

            List<PipelineDigestMessage> messages = new ArrayList<>();
            for (StringBuilder sb : recipients.values())
            {
                PipelineDigestMessage message = new PipelineDigestMessage(c, template, statusFiles, min, max, sb.toString());
                messages.add(message);
            }
            return messages.toArray(new PipelineDigestMessage[messages.size()]);
        }
        return null;
    }

    private static class PipelineMessage
    {
        private Container _c;
        private PipelineEmailTemplate _template;
        private PipelineStatusFileImpl _statusFile;
        private String _recipients;

        public PipelineMessage(Container c, PipelineEmailTemplate template, PipelineStatusFileImpl statusFile)
        {
            _c = c;
            _template = template;
            _statusFile = statusFile;
        }
        //public void setTemplate(PipelineEmailTemplate template){_template = template;}
        //public void setStatusFiles(PipelineStatusFileImpl[] statusFiles){_statusFiles = statusFiles;}
        public void setRecipients(String recipients){_recipients = recipients;}

        public MimeMessage createMessage(User user)
        {
            try
            {
                MailHelper.MultipartMessage m = MailHelper.createMultipartMessage();

                ActionURL url = StatusController.urlDetails(_statusFile);

                _template.setOriginatingUser(user);
                _template.setDataUrl(url.getURIString());
                _template.setJobDescription(_statusFile.getDescription());
                _template.setStatus(_statusFile.getStatus());
                _template.setTimeCreated(_statusFile.getCreated());

                m.setTemplate(_template, _c);
                m.addFrom(new Address[]{_template.renderFrom(_c, LookAndFeelProperties.getInstance(_c).getSystemEmailAddress())});

                m.addRecipients(Message.RecipientType.TO, MailHelper.createAddressArray(_recipients));

                return m;
            }
            catch (Exception e)
            {
                _log.error("Failed creating an email notification message for a pipeline job", e);
            }
            return null;
        }
    }

    private static class PipelineDigestMessage
    {
        private final Container _c;
        private final PipelineDigestTemplate _template;
        private final PipelineStatusFileImpl[] _statusFiles;
        private final String _recipients;
        private final Date _min;
        private final Date _max;

        public PipelineDigestMessage(Container c, PipelineDigestTemplate template, PipelineStatusFileImpl[] statusFiles,
                                     Date min, Date max, String recipients)
        {
            _c = c;
            _template = template;
            _statusFiles = statusFiles;
            _min = min;
            _max = max;
            _recipients = recipients;
        }

        public MimeMessage createMessage()
        {
            try
            {
                MailHelper.MultipartMessage m = MailHelper.createMultipartMessage();

                _template.setStatusFiles(_statusFiles);
                _template.setStartTime(_min);
                _template.setEndTime(_max);

                _template.renderAllToMessage(m, _c);

                m.addFrom(new Address[]{_template.renderFrom(_c, LookAndFeelProperties.getInstance(_c).getSystemEmailAddress())});
                m.addRecipients(Message.RecipientType.TO, MailHelper.createAddressArray(_recipients));

                return m;
            }
            catch (Exception e)
            {
                _log.error("Failed creating an email notification message for a pipeline job", e);
            }
            return null;
        }
    }

    public static abstract class PipelineEmailTemplate extends UserOriginatedEmailTemplate
    {
        protected String _dataUrl;
        protected String _jobDescription;
        protected Date _timeCreated;
        protected String _status;

        protected static final String DEFAULT_BODY = "Job description: ^jobDescription^\n" +
                "Created: ^timeCreated^\n" +
                "Status: ^status^\n" +
                "Additional details for this job can be obtained by navigating to this link:\n\n^dataURL^\n\n" +
                "Manage your email notifications at\n" +
                "^setupURL^\n";

        protected PipelineEmailTemplate(@NotNull String name, String description, String subject, String body)
        {
            super(name, description, subject, body, ContentType.Plain, Scope.Site);
        }

        public void setDataUrl(String dataUrl){_dataUrl = dataUrl;}
        public void setJobDescription(String description){_jobDescription = description;}
        public void setTimeCreated(Date timeCreated){_timeCreated = timeCreated;}
        public void setStatus(String status){_status = status;}

        @Override
        protected void addCustomReplacements(Replacements replacements)
        {
            super.addCustomReplacements(replacements);
            replacements.add("dataURL", String.class, "Link to the job details for this pipeline job", ContentType.Plain, c -> _dataUrl);
            replacements.add("jobDescription", String.class, "The job description", ContentType.Plain, c -> _jobDescription);
            replacements.add("timeCreated", Date.class, "The date and time this job was created", ContentType.Plain, c -> _timeCreated);
            replacements.add("status", String.class, "The job status", ContentType.Plain, c -> _status);
            replacements.add("setupURL", String.class, "URL to configure the pipeline, including email notifications", ContentType.Plain, c -> PageFlowUtil.urlProvider(PipelineUrls.class).urlSetup(c).getURIString());
        }
    }

    public static class PipelineJobSuccess extends PipelineEmailTemplate
    {
        public PipelineJobSuccess()
        {
            super("Pipeline job succeeded", "Sent to users who have been configured to receive notifications when a pipeline job completes successfully", "The pipeline job: ^jobDescription^ has completed successfully", DEFAULT_BODY);
        }
    }

    public static class PipelineJobFailed extends PipelineEmailTemplate
    {
        public PipelineJobFailed()
        {
            super("Pipeline job failed", "Sent to users who have been configured to receive notifications when a pipeline job fails", "The pipeline job: ^jobDescription^ did not complete successfully", DEFAULT_BODY);
        }
    }

    public static abstract class PipelineDigestTemplate extends EmailTemplate
    {
        private PipelineStatusFileImpl[] _statusFiles;
        private Date _startTime;
        private Date _endTime;

        protected static final String DEFAULT_BODY = "The following jobs have completed between the time of: ^startTime^ " +
                "and the end time of: ^endTime^:\n\n^pipelineJobs^";

        protected PipelineDigestTemplate(String name, String description, String subject, String body)
        {
            super(name, description, subject, body, ContentType.HTML, Scope.Site);
        }

        @Override
        protected void addCustomReplacements(Replacements replacements)
        {
            replacements.add("pipelineJobs", String.class, "The list of all pipeline jobs that have completed for this notification period", ContentType.HTML, c -> getJobStatus());
            replacements.add("startTime", Date.class, "The start of the time period for job completion", ContentType.HTML, c -> _startTime);
            replacements.add("endTime", Date.class, "The end of the time period for job completion", ContentType.HTML, c -> _endTime);
        }

        public void setStatusFiles(PipelineStatusFileImpl[] statusFiles){_statusFiles = statusFiles;}
        public void setStartTime(Date startTime){_startTime = startTime;}
        public void setEndTime(Date endTime){_endTime = endTime;}

        private String getJobStatus()
        {
            if (_statusFiles != null)
            {
                StringBuilder sb = new StringBuilder();
                sb.append("<table>");
                sb.append("<tr><td>Description</td><td>Created</td><td>Status</td><td>Details</td></tr>");
                for (PipelineStatusFileImpl sf : _statusFiles)
                {
                    ActionURL url = StatusController.urlDetails(sf);
                    sb.append("<tr>");
                    sb.append("<td>").append(PageFlowUtil.filter(sf.getDescription())).append("</td>");
                    sb.append("<td>").append(PageFlowUtil.filter(sf.getCreated())).append("</td>");
                    sb.append("<td>").append(PageFlowUtil.filter(sf.getStatus())).append("</td>");
                    sb.append("<td><a href=\"").append(url.getURIString()).append("\">").append(url.getURIString()).append("</a></td>");
                    sb.append("</tr>");
                    sb.append("<tr><td colspan=4><hr/></td></tr>");
                }
                sb.append("</table>");
                return sb.toString();
            }
            return null;
        }
    }

    public static class PipelineDigestJobSuccess extends PipelineDigestTemplate
    {
        public PipelineDigestJobSuccess()
        {
            super("Pipeline jobs succeeded (digest)",
                    "Sent for pipeline jobs that have completed successfully during a configured time period", "The pipeline jobs have completed successfully",
                    DEFAULT_BODY
            );
        }
    }

    public static class PipelineDigestJobFailed extends PipelineDigestTemplate
    {
        public PipelineDigestJobFailed()
        {
            super("Pipeline jobs failed (digest)",
                    "Sent for pipeline jobs that have not completed successfully during a configured time period", "The pipeline jobs did not complete successfully",
                    DEFAULT_BODY
            );
        }
    }

    public static TriggerConfiguration getTriggerConfiguration(Container container, String name)
    {
        TableInfo tinfo = PipelineSchema.getInstance().getTableInfoTriggerConfigurations();
        SimpleFilter filter = SimpleFilter.createContainerFilter(container);
        filter.addCondition(FieldKey.fromParts("Name"), name);

        return new TableSelector(tinfo, filter, null).getObject(TriggerConfiguration.class);
    }

    public static boolean insertOrUpdateTriggerConfiguration(User user, Container container, TriggerConfiguration config) throws Exception
    {
        UserSchema schema = QueryService.get().getUserSchema(user, container, PipelineSchema.getInstance().getSchemaName());
        if (schema != null)
        {
            TableInfo tableInfo = schema.getTable(PipelineQuerySchema.TRIGGER_CONFIGURATIONS_TABLE_NAME);
            if (tableInfo != null)
            {
                if (config.getRowId() != null)
                    config.beforeUpdate(user);
                else
                    config.beforeInsert(user, container.getId());
                ObjectFactory factory = ObjectFactory.Registry.getFactory(TriggerConfiguration.class);
                Map<String, Object> row = factory.toMap(config, null);

                QueryUpdateService qus = tableInfo.getUpdateService();
                List<Map<String, Object>> rowList = new LinkedList<>();
                rowList.add(row);
                if (qus != null)
                {
                    if (row.get("RowId") != null)
                        rowList = qus.updateRows(user, container, rowList, null, null, null);
                    else
                        rowList = qus.insertRows(user, container, rowList, new BatchValidationException(), null, null);
                }
                return rowList.size() > 0;
            }
        }
        return false;
    }

    public static void validateTriggerConfiguration(TriggerConfiguration config, Container container, User user, Errors errors)
    {
        Integer rowId = config.getRowId();
        String name = config.getName();
        String type = config.getType();
        String pipelineId = config.getPipelineId();
        boolean isEnabled = config.isEnabled();

        // validate that the config name is unique for this container
        if (StringUtils.isNotEmpty(name))
        {
            if (name.length() > 255)
                errors.rejectValue("Name", null, "Name must be less than 256 characters");

            Collection<PipelineTriggerConfig> existingConfigs = PipelineTriggerRegistry.get().getConfigs(container, null, name, false);
            if (!existingConfigs.isEmpty())
            {
                for (PipelineTriggerConfig existingConfig : existingConfigs)
                {
                    if (rowId == null || !rowId.equals(existingConfig.getRowId()))
                    {
                        errors.rejectValue("Name", null, "A pipeline trigger configuration already exists in this container for the given name: " + name);
                        break;
                    }
                }
            }
        }
        else
        {
            errors.rejectValue("Name", null,  "A name is required for trigger configurations.");
        }

        // validate that the type is a valid registered PipelineTriggerType
        PipelineTriggerType triggerType = PipelineTriggerRegistry.get().getTypeByName(type);
        if (triggerType == null)
        {
            errors.rejectValue("Type", null, "Invalid pipeline trigger type:" + type);
            return;
        }

        // validate that the pipelineId is a valid TaskPipeline
        if (pipelineId != null)
        {
            try
            {
                PipelineJobService.get().getTaskPipeline(pipelineId);
            }
            catch (NotFoundException e)
            {
                errors.rejectValue("PipelineId", null, "Invalid pipeline task id: " + pipelineId);
            }
        }
        else
        {
            errors.reject(null, null, "Pipeline Task ID required.");
            return;
        }

        // validate that the configuration values parse as valid JSON
        validateConfigJson(triggerType, config.getConfiguration(), pipelineId, isEnabled, errors, container, user);

        Object customConfiguration = config.getCustomConfiguration();
        if (customConfiguration != null && !customConfiguration.toString().equals(""))
            validateConfigJson(triggerType, customConfiguration, pipelineId, isEnabled, errors, true, container, user);
    }

    private static void validateConfigJson(PipelineTriggerType triggerType, Object configuration,  String pipelineId, boolean isEnabled, Errors errors, Container sourceContainer, User user)
    {
        validateConfigJson(triggerType, configuration, pipelineId, isEnabled, errors, false, sourceContainer, user);
    }

    private static void validateConfigJson(PipelineTriggerType triggerType, Object configuration,  String pipelineId, boolean isEnabled, Errors errors, boolean jsonValidityOnly, Container sourceContainer, User user)
    {
        JSONObject json = null;
        if (configuration != null)
        {
            try
            {
                ObjectMapper mapper = new ObjectMapper();
                json = mapper.readValue(configuration.toString(), JSONObject.class);
            }
            catch (IOException e)
            {
                errors.reject("Invalid JSON object for the configuration field: " + e.toString());
            }
        }

        // give the PipelineTriggerType a chance to validate the configuration JSON object
        if (triggerType != null && !jsonValidityOnly)
        {
            List<Pair<String, String>> configErrors = triggerType.validateConfiguration(pipelineId, isEnabled, json, sourceContainer, user);
            for (Pair<String, String> msg : configErrors)
                errors.rejectValue(msg.first, null, msg.second);
        }
    }

    @Deprecated // Prefer validateFolderImportFileNioPath //TODO not sure if this is used or exposed outside of our code
    public static File validateFolderImportFilePath(String archiveFilePath, PipeRoot pipeRoot, Errors errors)
    {
        return validateFolderImportFileNioPath(archiveFilePath, pipeRoot, errors).toFile();
    }

    public static Path validateFolderImportFileNioPath(String archiveFilePath, PipeRoot pipeRoot, Errors errors)
    {
        Path archiveFile = Path.of(archiveFilePath);

        if (!archiveFile.isAbsolute())
        {
            // Resolve the relative path to an absolute path under the current container's root
            archiveFile = archiveFilePath.contains("://") ? pipeRoot.resolveToNioPathFromUrl(archiveFilePath) : pipeRoot.resolveToNioPath(archiveFilePath);
        }

        // Be sure that the referenced file exists and is under the pipeline root
        if (archiveFile == null || !Files.exists(archiveFile))
        {
            errors.reject(ERROR_MSG, "Could not find file at path: " + archiveFilePath);
        }
        else if (!pipeRoot.isCloudRoot() && !pipeRoot.isUnderRoot(archiveFile))     // TODO: check for isCloud, then file should be in temp
        {
            errors.reject(ERROR_MSG, "Cannot access file " + archiveFilePath);
        }

        return archiveFile;
    }

    private static Path expandZipLocally(PipeRoot pipelineRoot, Path archiveFile, BindException errors)
    {
        try
        {
            // check if the archive file already exists in the unzip dir of this pipeline root
            Path importDir = pipelineRoot.getImportDirectory().toPath();
            if (!archiveFile.getParent().toAbsolutePath().toString().equalsIgnoreCase(importDir.toAbsolutePath().toString()))
                importDir = pipelineRoot.deleteImportDirectory(null);

            if (Files.notExists(importDir) || Files.list(importDir).noneMatch(s -> s.getFileName().toString().equalsIgnoreCase(archiveFile.getFileName().toString())))
            {
                // Only unzip once
                try (InputStream is = Files.newInputStream(archiveFile))
                {
                    ZipUtil.unzipToDirectory(is, importDir);
                }
            }

            return importDir;
        }
        catch (FileNotFoundException e)
        {
            errors.reject(ERROR_MSG, "File not found.");
        }
        catch (FileSystemAlreadyExistsException | DirectoryNotDeletedException e)
        {
            errors.reject(ERROR_MSG, e.getMessage());
        }
        catch (IOException e)
        {
            errors.reject(ERROR_MSG, "This file does not appear to be a valid .zip file.");
        }

        // Return null if errors were observed
        return null;
    }

    private static Path getImportXmlFile(@NotNull PipeRoot pipelineRoot, @NotNull Path archiveFile, @NotNull String xmlFileName, BindException errors) throws InvalidFileException
    {
        Path xmlFile = archiveFile;

        if (archiveFile.getFileName().toString().toLowerCase().endsWith(".zip"))
        {
            Path importDir = expandZipLocally(pipelineRoot, archiveFile, errors);
            xmlFile = getXmlFilePathFromArchive(importDir, archiveFile, xmlFileName);
        }
        //Downloading expanded archive will be handled later in the archive processing...
        //We don't really have the job context here

        return xmlFile;
    }

    public static @NotNull Path getXmlFilePathFromArchive(Path importDir, Path archiveFile, @NotNull String xmlFileName) throws InvalidFileException
    {
        // when importing a folder archive for a study, the study.xml file may not be at the root
        if ("study.xml".equalsIgnoreCase(xmlFileName) && archiveFile.getFileName().toString().toLowerCase().endsWith(".folder.zip"))
        {
            File folderXml = new File(importDir.toFile(), "folder.xml");
            FolderDocument folderDoc;
            try
            {
                folderDoc = FolderDocument.Factory.parse(folderXml, XmlBeansUtil.getDefaultParseOptions());
                XmlBeansUtil.validateXmlDocument(folderDoc, xmlFileName);
            }
            catch (Exception e)
            {
                throw new InvalidFileException(folderXml.getParentFile(), folderXml, e);
            }

            if (folderDoc.getFolder().isSetStudy())
            {
                importDir = importDir.resolve(folderDoc.getFolder().getStudy().getDir());
            }
        }

        return importDir.toAbsolutePath().resolve(xmlFileName);
    }

    public static Path getArchiveXmlFile(Container container, Path archiveFile, String xmlFileName, BindException errors) throws InvalidFileException
    {
        PipeRoot pipelineRoot = PipelineService.get().findPipelineRoot(container);
        Path xmlFile = getImportXmlFile(pipelineRoot, archiveFile, xmlFileName, errors);

        // if this is an import from a source template folder that has been previously implicitly exported
        // to the unzip dir (without ever creating a zip file) then just look there for the xmlFile.
        if (pipelineRoot != null && Files.isDirectory(archiveFile))
        {
            xmlFile = java.nio.file.Path.of(archiveFile.toString(), xmlFileName);
        }

        return xmlFile;
    }
}
