/*
 * Copyright (c) 2009-2015 LabKey Corporation
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

package org.labkey.announcements;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.labkey.api.action.ApiResponse;
import org.labkey.api.action.ApiSimpleResponse;
import org.labkey.api.action.MutatingApiAction;
import org.labkey.api.action.SimpleApiJsonForm;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.security.Group;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.ValidEmail;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.roles.EmailNonUsersPermission;
import org.labkey.api.util.MailHelper;
import org.springframework.validation.BindException;

import javax.mail.Address;
import javax.mail.Message;
import javax.mail.MessagingException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * User: klum
 * Date: Jul 24, 2009
 */
@RequiresPermission(ReadPermission.class)
public class SendMessageAction extends MutatingApiAction<SendMessageAction.MessageForm>
{
    private Map<String, Set<String>> _recipientMap = new HashMap<>();

    enum Props
    {
        msgFrom,
        msgRecipients,
        msgSubject,
        msgContent
    }

    enum MsgContent
    {
        type,
        content,
    }

    enum MsgRecipient
    {
        type,
        address,
        principalId,
    }

    public ApiResponse execute(MessageForm form, BindException errors) throws Exception
    {
        JSONObject json = form.getJsonObject();
        ApiSimpleResponse response = new ApiSimpleResponse();

        String from = json.getString(Props.msgFrom.name());
        String subject = json.getString(Props.msgSubject.name());
        JSONArray recipients;
        JSONArray contents;

        if (from == null)
            throw new IllegalArgumentException("You must supply a msgFrom value.");

        if (json.containsKey(Props.msgRecipients.name()))
        {
            recipients = json.getJSONArray(Props.msgRecipients.name());
            if (recipients == null || recipients.length() < 1)
                throw new IllegalArgumentException("No message recipients supplied.");
        }
        else
            throw new IllegalArgumentException("No message recipients supplied.");

        if (json.containsKey(Props.msgContent.name()))
        {
            contents = json.getJSONArray(Props.msgContent.name());
            if (contents == null || contents.length() < 1)
                throw new IllegalArgumentException("No message contents supplied.");
        }
        else
            throw new IllegalArgumentException("No message contents supplied.");

        MailHelper.MultipartMessage msg = MailHelper.createMultipartMessage();

        msg.setFrom(getEmail(from));
        msg.setSubject(subject);

        addMsgRecipients(msg, recipients);
        addMsgContents(msg, contents);

        MailHelper.send(msg, getUser(), getContainer());

        response.put("success", true);
        return response;
    }

    private Address getEmail(String email) throws IllegalArgumentException
    {
        try
        {
            ValidEmail validEmail = new ValidEmail(email);

            if (!canEmailNonUsers(getUser()))
            {
                User user = UserManager.getUser(validEmail);

                if (user == null)
                    throw new IllegalArgumentException("The email address '" + email + "' is not associated with a user account, and the current user does not have permission to send to it.");
            }
            return validEmail.getAddress();
        }
        catch (ValidEmail.InvalidEmailException e)
        {
            throw new IllegalArgumentException("Invalid email format.", e);
        }
    }

    private boolean canEmailNonUsers(User user)
    {
        return getContainer().hasPermission(user, EmailNonUsersPermission.class) ||
            ContainerManager.getRoot().hasPermission(user, EmailNonUsersPermission.class);
    }

    private String[] resolveEmailAddress(JSONObject recipient)
    {
        String address = recipient.getString(MsgRecipient.address.name());
        int principalId = NumberUtils.toInt(recipient.getString(MsgRecipient.principalId.name()), -100);

        if (address != null)
        {
            return new String[]{address};
        }
        else if (principalId != -100)
        {
            if (!isServerSideRequest())
                throw new IllegalArgumentException("Use of principalId is allowed only for server side scripts");

            // specifies a user or group id
            User user = UserManager.getUser(principalId);
            if (user != null)
                return new String[]{user.getEmail()};
            else
            {
                Group group = SecurityManager.getGroup(principalId);
                if (group != null)
                {
                    if (group.isSystemGroup())
                        throw new IllegalArgumentException("Invalid group ID: site groups are not allowed");

                    return SecurityManager.getGroupMemberNames(principalId);
                }
                else
                    throw new IllegalArgumentException("Unable to resolve principalId");
            }
        }
        else
            throw new IllegalArgumentException("Invalid group or user ID format (must be: id:<user or group id>");
    }

    private void addMsgRecipients(MailHelper.MultipartMessage msg, JSONArray recipients) throws IllegalArgumentException
    {
        try
        {
            for (int i=0; i < recipients.length(); i++)
            {
                JSONObject recipient = recipients.getJSONObject(i);
                String type = recipient.getString(MsgRecipient.type.name());
                Message.RecipientType rtype = Message.RecipientType.TO;

                if (!_recipientMap.containsKey(type))
                    _recipientMap.put(type, new HashSet<String>());
                
                if (StringUtils.equalsIgnoreCase(type, "TO"))
                    rtype = Message.RecipientType.TO;
                else if (StringUtils.equalsIgnoreCase(type, "CC"))
                    rtype = Message.RecipientType.CC;
                else if (StringUtils.equalsIgnoreCase(type, "BCC"))
                    rtype = Message.RecipientType.BCC;

                for (String email : resolveEmailAddress(recipient))
                {
                    Set<String> emails = _recipientMap.get(type);

                    // avoid duplicate emails per type
                    if (!emails.contains(email))
                    {
                        Address address = getEmail(email);
                        msg.addRecipient(rtype, address);

                        emails.add(email);
                    }
                }
            }
        }
        catch (MessagingException me)
        {
            throw new IllegalArgumentException("Unable to add the specified recipient email address.", me);
        }
    }

    private void addMsgContents(MailHelper.MultipartMessage msg, JSONArray contents) throws Exception
    {
        for (int i=0; i < contents.length(); i++)
        {
            try
            {
                JSONObject part = contents.getJSONObject(i);
                if (part.getString(MsgContent.type.name()) != null && part.getString(MsgContent.type.name()).trim().toLowerCase().startsWith("text/plain"))
                {
                    msg.setTextContent(part.getString(MsgContent.content.name()));
                }
                else
                {
                    msg.setHtmlContent(part.getString(MsgContent.content.name()));
                }
            }
            catch (JSONException je)
            {
                throw new IllegalArgumentException("Unable to add the specified message contents. Please use LABKEY.Message.createMsgContent for each msgContent array element.", je);
            }
        }
    }

    public static class MessageForm extends SimpleApiJsonForm
    {
    }
}
