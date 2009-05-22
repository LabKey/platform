function InitTinyMCE()
{
tinyMCE.init({
	mode : "textareas",
	theme : "advanced",
    entity_encoding : "named",
    entities : "160,nbsp,60,lt,62,gt,38,amp",
    relative_urls : "true",
    document_base_url : "",
    plugins : "table,advhr,advlink,searchreplace,contextmenu,fullscreen,nonbreaking,cleanup",
	theme_advanced_buttons1_add : "fontselect,fontsizeselect",
	theme_advanced_buttons2_add : "separator,forecolor,backcolor",
	theme_advanced_buttons2_add_before: "cut,copy,paste,separator,search,replace,separator",
	theme_advanced_buttons3_add_before : "tablecontrols,separator",
	theme_advanced_buttons3_add : "advhr,nonbreaking,separator,fullscreen",
    theme_advanced_disable : "image,code,hr,removeformat,visualaid",
    theme_advanced_layout_manager: "SimpleLayout",
    width: "100%",
    nonbreaking_force_tab : true,
    fullscreen_new_window : false,
	fullscreen_settings : {
    theme_advanced_path_location : "top"},
	theme_advanced_toolbar_location : "top",
	theme_advanced_toolbar_align : "left",
	theme_advanced_path_location : "bottom",
    apply_source_formatting : true,
	extended_valid_elements : "a[name|href|target|title|onclick],img[class|src|border=0|alt|title|hspace|vspace|width|height|align|onmouseover|onmouseout|name],hr[class|width|size|noshade],font[face|size|color|style],span[class|align|style]",
	external_link_list_url : "example_data/example_link_list.js",
	external_image_list_url : "example_data/example_image_list.js",
    theme_advanced_statusbar_location: "bottom",
    fix_list_elements : true
    });
}
