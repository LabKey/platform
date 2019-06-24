window.addEventListener('load', function () {
+function($) {
    $('#extraAlias').on('click', '.removeAliasTrigger' , function() {
        $(this).parents('.lk-exp-alias-group').remove();
    });

    $(".lk-exp-addAliasGroup").on('click', function () {
        addAliasGroup();
    });

    function processAliasJson(aliasMap) {
        if (aliasMap) {
            for (let j in aliasMap) {
                if (aliasMap.hasOwnProperty(j) && aliasMap[j]) {
                    addAliasGroup(j, aliasMap[j])
                }
            }
        }
    }

    function addAliasGroup(key, value) {
        let elem = $("<div class='form-group lk-exp-alias-group' name='importAliases'>" +
                "<label class=\" control-label col-sm-3 col-lg-2\">Parent Alias</label>" +
                "<div class='col-sm-3 col-lg-2'>" +
                    "<input type='text' class='form-control lk-exp-alias-key' placeholder='Import Header' name='importAliasKeys' style='float: right;'>" +
                "</div>" +
                "<div class='col-sm-3 col-lg-2'>" +
                    "<input type='text' class='form-control lk-exp-alias-value' placeholder='Parent' name='importAliasValues' style='display: inline-block;'>" +
                    // "<input type='text' class='form-control lk-exp-alias-value' placeholder='Parent' name=\"importAliases['${importAliases.key}']\" style='display: inline-block;'>" +
                    "<a class='removeAliasTrigger' style='cursor: pointer;' title='remove'><i class='fa fa-trash' style='padding: 0 8px; color: #555;'></i></a>" +
                "</div>" +
                "</div>");

        if (key && value) {
            elem.find(".lk-exp-alias-key").val(key);
            elem.find(".lk-exp-alias-value").val(value);
        }

        elem.appendTo($("#extraAlias"));
    }

    function onLoadSuccess(data) {
        let a = 100;
        let b = 10 * a;
        alert("waffles");
    }

    function onLoadFailure() {
    }

    $("#btnSubmit").on('click', (function() {
        let data = {};
        $("#createSampleSetForm").serializeArray().map(function(x){
            if (!data[x.name]) {
                data[x.name] = x.value;
            } else {
                if (!$.isArray(data[x.name])){
                    var prev = data[x.name];
                    data[x.name] = [prev];
                }
                data[x.name].push(x.value);
            }
        });

        // LABKEY.Ajax.request({
        //     url : LABKEY.ActionURL.buildURL("experiment", "createSampleSet.api"),
        //     method : 'POST',
        //     jsonData : data,
        //     success: LABKEY.Utils.getCallbackWrapper(function(response)
        //     {
        //         if (response.success) {
        //             window.onbeforeunload = null;
        //             window.location = response.returnUrl;
        //         }
        //     }),
        //     failure: LABKEY.Utils.getCallbackWrapper(function(exceptionInfo) {
        //         LABKEY.Utils.alert('Error', 'Unable to save the Sample Set for the following reason: ' + exceptionInfo.exception);
        //     }, this, true)
        // });
    }));

} (jQuery);}, false);
