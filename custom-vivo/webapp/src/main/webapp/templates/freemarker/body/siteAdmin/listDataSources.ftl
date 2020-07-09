${stylesheets.add('<link rel="stylesheet" href="${urls.theme}/css/adminapp.css" />')}
${stylesheets.add('<meta http-equiv="refresh" content="60">')}

<#assign pageTitle = "Data Sources">
<#assign addURI = "http%3A%2F%2Fvivoweb.org%2Fontology%2Fadminapp%2FTurtleDataIngest">

<#if type?has_content && type == "merge">
	<#assign pageTitle = "Merge Configurations">
	<#assign addURI = "http%3A%2F%2Fvivoweb.org%2Fontology%2Fadminapp%2FDataMerge">
<#elseif type?has_content && type == "publish">
	<#assign pageTitle = "Publish Configurations">
	<#assign addURI = "http%3A%2F%2Fvivoweb.org%2Fontology%2Fadminapp%2FDataPublish">
</#if>

<h2>${pageTitle}
<a href="${urls.base}/editForm?controller=Entity&VClassURI=${addURI}"><img class="add-individual" src="${urls.images}/individual/addIcon.gif" alt="${i18n().add}" /></a>
</h2>

<table class="adminTable">
    <thead>
        <tr>
	    <td>Priority</td>
	    <td></td>
        <td>Configuration</td>
	    <td>Last updated</td>
	    <td>Next update</td>
	    <td>% done</td>
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
	    <td>${dataSource.status.completionPercentage}</td>
	    <td>
	    	<#if dataSource.status.running>
	    		...
	    	<#else>
	    		${dataSource.status.totalRecords}
	    	</#if>
	    </td>
	    <td>
	        <#if dataSource.status.running>
		    	<span class="statusRunning">RUNNING</span>
		    <#elseif !dataSource.status.statusOk>
                <span class="statusError" title="${dataSource.status.message!}">ERROR</span>
			<#else>
                <span class="statusIdle">IDLE</span>
			</#if>
        </td>
	</tr>
    </#list>
    </tbody>
</table>


