${stylesheets.add('<link rel="stylesheet" href="${urls.base}/css/adminapp.css" />')}

<#assign pageTitle = "Ingest Configuration">
<#assign addURI = "http%3A%2F%2Fvivoweb.org%2Fontology%2Fadminapp%2FDataIngest">

<h2 class="processing-tabs">
	<#if type?has_content && type == "merge">
		<#assign pageTitle = "Merge Configuration">
		<#assign addURI = "http%3A%2F%2Fvivoweb.org%2Fontology%2Fadminapp%2FDataMerge">
		
		<a href="?">Ingest</a> | Merge | <a href="?type=publish">Publish</a>
	<#elseif type?has_content && type == "publish">
		<#assign pageTitle = "Publish Configuration">
		<#assign addURI = "http%3A%2F%2Fvivoweb.org%2Fontology%2Fadminapp%2FDataPublish">
		
		<a href="?">Ingest</a> | <a href="?type=merge">Merge</a> | Publish
	<#else>

		Ingest | <a href="?type=merge">Merge</a> | <a href="?type=publish">Publish</a>
	</#if>

	<span class="add-config"><a href="${urls.base}/editForm?controller=Entity&VClassURI=${addURI}"><img class="add-individual" src="${urls.images}/individual/addIcon.gif" alt="${i18n().add}" /> ${pageTitle}</a></span>
</h2>

<div id="adminTable-wrapper">
	<table class="adminTable">
		<thead>
			<tr>
			<td>Prio</td>
			<td></td>
			<td></td>
			<td>Configuration</td>
			<td>Last run</td>
			<td>Next run</td>
			<td>Last records</td>
			<td>Status</td>
		</tr>
		</thead>
		<tbody>
		<#list dataSources as dataSource>
			<tr>
			<td>${dataSource?index}</td>
			<td>
				<form method="post" action="${urls.base}/invokeService">
				<input type="hidden" name="uri" value="${dataSource.URI}"/>
				<#if type?has_content>
					<input type="hidden" name="type" value="${type}"/>
				</#if>
						<#if dataSource.status.running>
							<input type="submit" name="stop"  class="submit" value="Stop"/>
						<#else>
							<input type="submit" name="start" class="submit" value="Start"/>
						</#if>
				</form>
			</td>
			<td>
			<#if !type?has_content && endpoints[dataSource?index]??>
				<#assign endpoint = endpoints[dataSource?index]>
				<form method="post" onsubmit="clearGraph(this, '${endpoint.endpointUpdateURI}'); return false;">
					<input type="hidden" name="update" value="CLEAR GRAPH <${dataSource.resultsGraphURI}>"/>
					<input type="hidden" name="email" value="username"/>
					<input type="hidden" name="password" value="password"/>
					<input type="submit" class="clear-btn" value="Clear"/>
				</form>
			</#if>
			</td>
				<td>
				<a href="${urls.base}/individual?uri=${dataSource.URI?url}">
				<#if dataSource.name??>
					${dataSource.name!}
				<#else>
					${dataSource.URI}
					</#if>
			</a>
			</td>
			<td>
				<#if dataSource.lastUpdate??>
					${dataSource.lastUpdate?replace("T", " ")}
				<#else>
				---
				</#if>
				</td>
			<td>
				<#if dataSource.nextUpdate??>
					${dataSource.nextUpdate?replace("T", " ")}
				<#else>
				---
				</#if>
			</td>
			<td>
				<#if dataSource.status.running>
					~ ${dataSource.status.progress}% ~
				<#else>
					${dataSource.status.totalRecords}
				</#if>
			</td>
			<td>
				<#if dataSource.status.running>
					<span class="statusRunning">RUNNING</span>
				<#elseif !dataSource.status.statusOk>
					<span class="statusError" title="${dataSource.status.message?j_string}">ERROR</span>
				<#else>
					<span class="statusIdle">IDLE</span>
				</#if>
			</td>
		</tr>
		</#list>
		</tbody>
	</table>
</div>

<script>
	function reload() { $("#adminTable-wrapper").load(document.location + " .adminTable"); }
	setInterval("reload()", 1000);
	
	function clearGraph(form, uri) {
	$.post(uri, $(form).serialize()).done(function( data ) {
		if (-1 != data.indexOf("200 SPARQL update accepted.")) alert('done');
		else alert(data) });
	}
</script>
