<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%

%>
<div id="customize-data-views-content"></div>
<script type="text/javascript">

    LABKEY.requiresExt4Sandbox(true);

</script>
<script type="text/javascript">

    function success(data)
    {
        var cbItems = [];
        for (var i=0; i < data.types.length; i++)
            cbItems.push({boxLabel : data.types[i], name : 'types', checked : true});

        var panel = Ext4.create('Ext.form.Panel',{
            renderTo : 'customize-data-views-content',
            width    : 400,
            layout   : 'anchor',
            bodyPadding: 10,
            defaults : {
                anchor : '100%'
            },
            fieldDefaults  :{
                labelAlign : 'top',
                labelWidth : 130,
                labelSeparator : ''
            },
            items : [{
                xtype      : 'checkboxgroup',
                fieldLabel : 'Data Types Available',
                columns    : 2,
                items      : cbItems
            }],
            buttons : [{
                text     : 'Cancel',
                handler  : function() {
                    var url = LABKEY.ActionURL.getParameter('returnUrl');
                    if (url)
                        window.location = url;
                    else
                        window.location = LABKEY.ActionURL.buildURL('study-redesign', 'page');
                }
            },{
                text     : 'Submit',
                formBind : true,
                handler  : function() {

                }
            }]
        });
    }

    function init()
    {
        var el = Ext4.get('customize-data-views-content');

        Ext4.Ajax.request({
            url    : LABKEY.ActionURL.buildURL('study', 'browseData.api'),
            method : 'GET',
            success: function(response) {
                var json = Ext4.decode(response.responseText);
                success(json);
            },
            failure : function() {
                el.update('failure');
            }
        });
    }
    Ext4.onReady(init);
</script>