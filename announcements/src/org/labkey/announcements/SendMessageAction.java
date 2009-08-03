/*
 * Copyright (c) 2009 LabKey Corporation
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

import org.apache.commons.lang.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.action.*;
import org.labkey.api.security.*;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.util.MailHelper;
import org.springframework.validation.BindException;

import javax.mail.Address;
import javax.mail.Message;
import javax.mail.MessagingException;

/**
 * Created by IntelliJ IDEA.
 * User: klum
 * Date: Jul 24, 2009
 */
@RequiresPermissionClass(ReadPermission.class)
@RequiresLogin
public class SendMessageAction extends MutatingApiAction<SendMessageAction.MessageForm>
{
    enum Props {
        msgFrom,
        msgRecipients,
        msgSubject,
        msgContent,
    }
    enum MsgContent {
        type,
        content,
    }
    enum MsgRecipient {
        type,
        address,
    }

    public ApiResponse execute(MessageForm form, BindException errors) throws Exception
    {
        JSONObject json = form.getJsonObject();
        ApiSimpleResponse response = new ApiSimpleResponse();

        String from = json.getString(Props.msgFrom.name());
        String subject = json.getString(Props.msgSubject.name());
        JSONArray recipients = null;
        JSONArray contents = null;

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

        MailHelper.send(msg);

        response.put("success", true);
        return response;
    }

    private Address getEmail(String email) throws IllegalArgumentException
    {
        try {
            ValidEmail validEmail = new ValidEmail(email);
            User user = UserManager.getUser(validEmail);

            if (user == null)
                throw new IllegalArgumentException("The user email: " + email + " does not exist in the system.");
            return validEmail.getAddress();
        }
        catch (ValidEmail.InvalidEmailException e)
        {
            throw new IllegalArgumentException("Invalid email format.", e);
        }
    }

    private void addMsgRecipients(MailHelper.MultipartMessage msg, JSONArray recipients) throws IllegalArgumentException
    {
        try {
            for (int i=0; i < recipients.length(); i++)
            {
                JSONObject recipient = recipients.getJSONObject(i);

                String type = recipient.getString(MsgRecipient.type.name());
                Address address = getEmail(recipient.getString(MsgRecipient.address.name()));

                if (StringUtils.equalsIgnoreCase(type, "TO"))
                    msg.addRecipient(Message.RecipientType.TO, address);
                else if (StringUtils.equalsIgnoreCase(type, "CC"))
                    msg.addRecipient(Message.RecipientType.CC, address);
                else if (StringUtils.equalsIgnoreCase(type, "BCC"))
                    msg.addRecipient(Message.RecipientType.BCC, address);
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
            JSONObject part = contents.getJSONObject(i);
            msg.setBodyContent(part.getString(MsgContent.content.name()), part.getString(MsgContent.type.name()));
        }
    }

    public static class MessageForm implements ApiJsonForm
    {
        private JSONObject _json;

        public void setJsonObject(JSONObject jsonObj)
        {
            _json = jsonObj;
        }

        public JSONObject getJsonObject()
        {
            return _json;
        }
    }
}
