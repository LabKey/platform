<%
/*
 * Copyright (c) 2006-2011 LabKey Corporation
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
%>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.api.security.permissions.AdminPermission" %>
<%@ page import="org.labkey.api.wiki.WikiRendererType" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.study.controllers.StudyController" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.attachments.Attachment" %>
<%@ page extends="org.labkey.study.view.BaseStudyPage" %>
<%
    ViewContext context = getViewContext();
    boolean canEdit = context.getContainer().hasPermission(context.getUser(), AdminPermission.class);
    String cancelLink = context.getActionURL().getParameter("returnURL");
    if (cancelLink == null || cancelLink.length() == 0)
        cancelLink = new ActionURL(StudyController.ManageStudyAction.class, context.getContainer()).toString();
%>
<div class="extContainer" id="manageStudyPropertiesDiv"></div>
<script type="text/javascript">

var canEdit = <%=canEdit?"true":"false"%>;
var editableFormPanel = canEdit;
var studyPropertiesFormPanel = null;

function removeProtocolDocument(name, xid)
{
    if (Ext)
    {
        function remove()
        {
            var params =
            {
                name: name
            };

            Ext.Ajax.request(
            {
                url    : LABKEY.ActionURL.buildURL('study', 'removeProtocolDocument'),
                method : 'POST',
                success: function()
                {
                    var el = document.getElementById(xid);
                    if (el)
                    {
                        el.parentNode.removeChild(el);
                    }
                },
                failure: function()
                {
                    alert('Failed to remove study protocol document.');
                },
                params : params
            });
        }

        Ext.Msg.show({
            title : 'Remove Attachment',
            msg : 'Please confirm you would like to remove this study protocol document. This cannot be undone.',
            buttons: Ext.Msg.OKCANCEL,
            icon: Ext.MessageBox.QUESTION,
            fn  : function(b) {
                if (b == 'ok') {
                    remove();
                }
            }
        });
    }
}

function addExtFilePickerHandler()
{
    var fibasic = new Ext.ux.form.FileUploadField(
    {
        width: 300
    });

    var removeBtn = new Ext.Button({
        text: "remove",
        name: "remove",
        uploadId: fibasic.id,
        handler: removeNewAttachment
    });

    var uploadField = new Ext.form.CompositeField({
        renderTo: 'filePickers',
        items:[fibasic, removeBtn]
    });

    studyPropertiesFormPanel.add(uploadField);
}

function removeNewAttachment(btn)
{
    // In order to 'remove' an attachment before it is submitted we must hide and disable it. We CANNOT destroy the
    // elements related to the upload field, if we do the form will not validate. This is a known Issue with Ext 3.4.
    // http://www.sencha.com/forum/showthread.php?25479-2.0.1-2.1-Field.destroy()-on-Fields-rendered-by-FormLayout-does-not-clean-up.
    Ext.getCmp(btn.uploadId).disable();
    btn.ownerCt.hide();
}

var maskEl = null;

function mask()
{
    maskEl = Ext.getBody();
    maskEl.mask();
}

function unmask()
{
    if (maskEl)
        maskEl.unmask();
    maskEl = null;
}


function showSuccessMessage(message, after)
{
    Ext.get("formError").update("");
    var el = Ext.get("formSuccess");
    el.update(message);
    el.pause(3).fadeOut({callback:function(){el.update("");}});
}


function onSaveSuccess_updateRows()
{
    // if you want to stay on page, you need to refresh anyway to udpate attachments
    var msgbox = Ext.Msg.show({
       title:'Status',
       msg: '<span class="labkey-message">Changes saved</span>',
       buttons: false
    });
    var el = msgbox.getDialog().el;
    el.pause(1).fadeOut({callback:cancelButtonHandler});
}

function onSaveSuccess_formSubmit()
{
    // if you want to stay on page, you need to refresh anyway to udpate attachments
    var msgbox = Ext.Msg.show({
       title:'Status',
       msg: '<span class="labkey-message">Changes saved</span>',
       buttons: false
    });
    var el = msgbox.getDialog().el;
    el.pause(1).fadeOut({callback:cancelButtonHandler});
}


function onSaveFailure_updateRows(error)
{
    unmask();
    Ext.get("formSuccess").update("");
    Ext.get("formError").update(error.exception);
}


function onSaveFailure_formSubmit(form, action)
{
    unmask();
    switch (action.failureType)
    {
        case Ext.form.Action.CLIENT_INVALID:
            Ext.Msg.alert('Failure', 'Form fields may not be submitted with invalid values');
            break;
        case Ext.form.Action.CONNECT_FAILURE:
            Ext.Msg.alert('Failure', 'Ajax communication failed');
            break;
        case Ext.form.Action.SERVER_INVALID:
           Ext.Msg.alert('Failure', action.result.msg);
    }
}


function submitButtonHandler()
{
    var form = studyPropertiesFormPanel.getForm();
    if (form.isValid())
    {
        <%-- This works except for the file attachment
        var rows = studyPropertiesFormPanel.getFormValues();
        LABKEY.Query.updateRows(
        {
            schemaName:'study',
            queryName:'StudyProperties',
            rows:rows,
            success : onSaveSuccess_updateRows,
            failure : onSaveFailure_updateRows
        });
        --%>
        form.fileUpload = true;
        form.submit(
        {
            success : onSaveSuccess_formSubmit,
            failure : onSaveFailure_formSubmit
        });
        mask();
    }
    else
    {
        Ext.MessageBox.alert("Error Saving", "There are errors in the form.");
    }
}


function editButtonHandler()
{
    editableFormPanel = true;
    destroyFormPanel();
    createPage();
}


function cancelButtonHandler()
{
    LABKEY.setSubmit(true);
    window.location = <%=q(cancelLink)%>;
}


function doneButtonHandler()
{
    LABKEY.setSubmit(true);
    window.location = <%=q(cancelLink)%>;
}


function destroyFormPanel()
{
    if (studyPropertiesFormPanel)
    {
        studyPropertiesFormPanel.destroy();
        studyPropertiesFormPanel = null;
    }
}


var renderTypes = {<%
String comma = "";
for (WikiRendererType type : getRendererTypes())
{
    %><%=comma%><%=q(type.name())%>:<%=q(type.getDisplayName())%><%
    comma = ",";
}%>};

function renderFormPanel(data, editable)
{
    destroyFormPanel();
    var buttons = [];

    if (editable)
    {
        buttons.push({text:"Submit", handler: submitButtonHandler});
        buttons.push({text: "Cancel", handler: cancelButtonHandler});
    }
    else if (<%=canEdit ? "true" : "false"%>)
    {
        buttons.push({text:"Edit", handler: editButtonHandler});
        buttons.push({text:"Done", handler: doneButtonHandler});
    }

    var renderTypeCombo = new Ext.form.ComboBox(
    {
        hiddenName : 'DescriptionRendererType',
        name : 'RendererTypeDisplayName',
        mode: 'local',
        triggerAction: 'all',
        valueField: 'renderType',
        displayField: 'displayText',
        store : new Ext.data.ArrayStore(
        {
            id : 0, fields:['renderType', 'displayText'],
            data : [
<%
                comma = "";
                for (WikiRendererType type : getRendererTypes())
                {
                    %><%=comma%>[<%=q(type.name())%>,<%=q(type.getDisplayName())%>]<%
                    comma = ",";
                }
%>
            ]
        })
    });

    // fields we've handled (whether or now we're showing them)
    var handledFields = {};
    var items = [];

    items.push({name:'Label'});
    handledFields['Label'] = true;
    items.push({name:'Investigator'});
    handledFields['Investigator'] = true;
    items.push({name:'studyGrant'});
    handledFields['studyGrant'] = true;
    items.push({name:'Description', width:500});
    handledFields['Description'] = true;
    if (editableFormPanel)
        items.push(renderTypeCombo);
    handledFields[renderTypeCombo.hiddenName] = true;
    items.push({fieldLabel:'Protocol Documents', width:500, xtype:'panel', contentEl:'attachmentsDiv'});
    // the original form didn't include these, but we can decide later
    handledFields['StartDate'] = true;
    handledFields['Container'] = true;
    handledFields['TimepointType'] = true;

    // Now let's add all the other fields
    var cm = data.columnModel;
    var col, i;
    for (i=0 ; i<cm.length ; i++)
    {
        col = cm[i];
        col = cm[i];
        if (col.hidden) continue;
        if (handledFields[col.dataIndex]) continue;
        items.push({name:col.dataIndex});
    }
    for (i=0 ; i<items.length ; i++)
    {
        items[i].disabled = !editableFormPanel;
        items[i].disabledClass = 'noop';    // TODO the default disabledClass makes everything unreadable
    }

    studyPropertiesFormPanel = new LABKEY.ext.FormPanel(
    {
        selectRowsResults:data,
        padding : 10,
        defaults : { width:500, disabled : !editableFormPanel, disabledClass:'noop' },
        labelWidth:150,   <%-- don't wrap even on Large font theme --%>
        buttonAlign:'left',
        buttons: buttons,
        items:items
    });
    studyPropertiesFormPanel.render('formDiv');
    var renderType = data.rows[0][renderTypeCombo.hiddenName];
    if (renderTypes[renderType])
    {
        renderTypeCombo.setValue(renderTypes[renderType]);
        renderTypeCombo.originalValue = renderTypes[renderType];
    }
}


function onQuerySuccess(data) // e.g. callback from Query.selectRows
{
    renderFormPanel(data, editableFormPanel);
}


function onQueryFailure(a)
{
    alert(a);
}


function createPage()
{
    LABKEY.Query.selectRows({
        schemaName: 'study',
        queryName: 'StudyProperties',
        columns: '*',
        success: onQuerySuccess,
        failure: onQueryFailure
    });
}

function isFormDirty()
{
    return studyPropertiesFormPanel && studyPropertiesFormPanel.getForm().isDirty();
}

window.onbeforeunload = LABKEY.beforeunload(isFormDirty);

Ext.onReady(createPage);

</script>

<span id=formSuccess class=labkey-message-strong></span><span id=formError class=labkey-error></span>&nbsp;</br>
<div id='formDiv'></div>
<div id='attachmentsDiv' class='x-hidden'>
<table>
<%
        int x = -1;
        for (Attachment att : getStudy().getProtocolDocuments())
        {
            x++;
            %><tr id="attach-<%=x%>" style="min-width:20px;">
                <td>&nbsp;<img src="<%=request.getContextPath() + att.getFileIcon()%>" alt="logo"/></td>
                <td>&nbsp;<%= h(att.getName()) %></td>
                <td>&nbsp;[<a onclick="removeProtocolDocument(<%=PageFlowUtil.jsString(att.getName())%>, 'attach-<%=x%>'); ">remove</a>]</td>
            </tr ><%
        }
%>
</table>
<div id="filePickers">
</div>
<div>
<a onclick="addExtFilePickerHandler(); return false;" href="#addFile"><img src="<%=request.getContextPath()%>/_images/paperclip.gif">&nbsp;Attach a file</a>
</div>
</div>

<style>
/*!
 * Ext JS Library 3.4.0
 * Copyright(c) 2006-2011 Sencha Inc.
 * licensing@sencha.com
 * http://www.sencha.com/license
 */
/*
 * FileUploadField component styles
 */
.x-form-file-wrap {
    position: relative;
    height: 22px;
}
.x-form-file-wrap .x-form-file {
	position: absolute;
	right: 0;
	-moz-opacity: 0;
	filter:alpha(opacity: 0);
	opacity: 0;
	z-index: 2;
    height: 22px;
}
.x-form-file-wrap .x-form-file-btn {
	position: absolute;
	right: 0;
	z-index: 1;
}
.x-form-file-wrap .x-form-file-text {
    position: absolute;
    left: 0;
    z-index: 3;
    color: #777;
}
</style>
<script>
/*!
 * Ext JS Library 3.4.0
 * Copyright(c) 2006-2011 Sencha Inc.
 * licensing@sencha.com
 * http://www.sencha.com/license
 */
Ext.ns('Ext.ux.form');

/**
 * @class Ext.ux.form.FileUploadField
 * @extends Ext.form.TextField
 * Creates a file upload field.
 * @xtype fileuploadfield
 */
Ext.ux.form.FileUploadField = Ext.extend(Ext.form.TextField,  {
    /**
     * @cfg {String} buttonText The button text to display on the upload button (defaults to
     * 'Browse...').  Note that if you supply a value for {@link #buttonCfg}, the buttonCfg.text
     * value will be used instead if available.
     */
    buttonText: 'Browse...',
    /**
     * @cfg {Boolean} buttonOnly True to display the file upload field as a button with no visible
     * text field (defaults to false).  If true, all inherited TextField members will still be available.
     */
    buttonOnly: false,
    /**
     * @cfg {Number} buttonOffset The number of pixels of space reserved between the button and the text field
     * (defaults to 3).  Note that this only applies if {@link #buttonOnly} = false.
     */
    buttonOffset: 3,
    /**
     * @cfg {Object} buttonCfg A standard {@link Ext.Button} config object.
     */

    // private
    readOnly: true,

    /**
     * @hide
     * @method autoSize
     */
    autoSize: Ext.emptyFn,

    // private
    initComponent: function(){
        Ext.ux.form.FileUploadField.superclass.initComponent.call(this);

        this.addEvents(
            /**
             * @event fileselected
             * Fires when the underlying file input field's value has changed from the user
             * selecting a new file from the system file selection dialog.
             * @param {Ext.ux.form.FileUploadField} this
             * @param {String} value The file value returned by the underlying file input field
             */
            'fileselected'
        );
    },

    // private
    onRender : function(ct, position){
        Ext.ux.form.FileUploadField.superclass.onRender.call(this, ct, position);

        this.wrap = this.el.wrap({cls:'x-form-field-wrap x-form-file-wrap'});
        this.el.addClass('x-form-file-text');
        this.el.dom.removeAttribute('name');
        this.createFileInput();

        var btnCfg = Ext.applyIf(this.buttonCfg || {}, {
            text: this.buttonText
        });
        this.button = new Ext.Button(Ext.apply(btnCfg, {
            renderTo: this.wrap,
            cls: 'x-form-file-btn' + (btnCfg.iconCls ? ' x-btn-icon' : '')
        }));

        if(this.buttonOnly){
            this.el.hide();
            this.wrap.setWidth(this.button.getEl().getWidth());
        }

        this.bindListeners();
        this.resizeEl = this.positionEl = this.wrap;
    },

    bindListeners: function(){
        this.fileInput.on({
            scope: this,
            mouseenter: function() {
                this.button.addClass(['x-btn-over','x-btn-focus'])
            },
            mouseleave: function(){
                this.button.removeClass(['x-btn-over','x-btn-focus','x-btn-click'])
            },
            mousedown: function(){
                this.button.addClass('x-btn-click')
            },
            mouseup: function(){
                this.button.removeClass(['x-btn-over','x-btn-focus','x-btn-click'])
            },
            change: function(){
                var v = this.fileInput.dom.value;
                this.setValue(v);
                this.fireEvent('fileselected', this, v);
            }
        });
    },

    createFileInput : function() {
        this.fileInput = this.wrap.createChild({
            id: this.getFileInputId(),
            name: this.name||this.getId(),
            cls: 'x-form-file',
            tag: 'input',
            type: 'file',
            size: 1
        });
    },

    reset : function(){
        if (this.rendered) {
            this.fileInput.remove();
            this.createFileInput();
            this.bindListeners();
        }
        Ext.ux.form.FileUploadField.superclass.reset.call(this);
    },

    // private
    getFileInputId: function(){
        return this.id + '-file';
    },

    // private
    onResize : function(w, h){
        Ext.ux.form.FileUploadField.superclass.onResize.call(this, w, h);

        this.wrap.setWidth(w);

        if(!this.buttonOnly){
            var w = this.wrap.getWidth() - this.button.getEl().getWidth() - this.buttonOffset;
            this.el.setWidth(w);
        }
    },

    // private
    onDestroy: function(){
        Ext.ux.form.FileUploadField.superclass.onDestroy.call(this);
        Ext.destroy(this.fileInput, this.button, this.wrap);
    },

    onDisable: function(){
        Ext.ux.form.FileUploadField.superclass.onDisable.call(this);
        this.doDisable(true);
    },

    onEnable: function(){
        Ext.ux.form.FileUploadField.superclass.onEnable.call(this);
        this.doDisable(false);

    },

    // private
    doDisable: function(disabled){
        this.fileInput.dom.disabled = disabled;
        this.button.setDisabled(disabled);
    },


    // private
    preFocus : Ext.emptyFn,

    // private
    alignErrorIcon : function(){
        this.errorIcon.alignTo(this.wrap, 'tl-tr', [2, 0]);
    }

});

Ext.reg('fileuploadfield', Ext.ux.form.FileUploadField);

// backwards compat
Ext.form.FileUploadField = Ext.ux.form.FileUploadField;
</script>




