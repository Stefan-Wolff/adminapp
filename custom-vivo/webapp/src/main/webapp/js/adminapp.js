

function showHeadTabs(urlBase) {
	var tabs;
	var type = window.localStorage.getItem("bc_type_0");
	
	if ("Data Ingest" == type) {
		tabs = '<a href="' + urlBase + '/listDataSources?" class="adminapp-active">Ingest</a> | <a href="' + urlBase + '/listDataSources?type=merge">Merge</a> | <a href="' + urlBase + '/listDataSources?type=publish">Publish</a>';
	} else if ("Data Merge" == type) {
		tabs = '<a href="' + urlBase + '/listDataSources?">Ingest</a> | <a href="' + urlBase + '/listDataSources?type=merge" class="adminapp-active">Merge</a> | <a href="' + urlBase + '/listDataSources?type=publish">Publish</a>';
	} else {
		tabs = '<a href="' + urlBase + '/listDataSources?">Ingest</a> | <a href="' + urlBase + '/listDataSources?type=merge">Merge</a> | <a href="' + urlBase + '/listDataSources?type=publish" class="adminapp-active">Publish</a>';
	}
	
	$("#processing-tabs").html(tabs);
};

function showBreadCrumb() {
	var name_0 = window.localStorage.getItem("bc_name_0") || "";
	var uri_0 = window.localStorage.getItem("bc_uri_0") || "";
	
	if ("" != name_0 && document.location != uri_0) {
		var bc = '<a href="' + uri_0 + '">&lt; ' + name_0 + '</a>';
		
		var name_1 = window.localStorage.getItem("bc_name_1") || "";
		var uri_1 = window.localStorage.getItem("bc_uri_1") || "";
		if ("" != name_1 && document.location != uri_1) {
			bc += '<a href="' + uri_1 + '">&lt; ' + name_1 + '</a>';
		}
	
		$("#adminapp-back").html(bc);
	}
};

function addCurPage(uri, name, type) {
	if ("Data Ingest" == type || "Data Merge" == type || "Data Publish" == type) {
		window.localStorage.setItem("bc_name_0", name);
		window.localStorage.setItem("bc_uri_0", uri);
		window.localStorage.setItem("bc_type_0", type);
		window.localStorage.setItem("bc_name_1", "");
	} else if ("Merge Rule" == type) {
		window.localStorage.setItem("bc_name_1", name);
		window.localStorage.setItem("bc_uri_1", uri);
	}
};

