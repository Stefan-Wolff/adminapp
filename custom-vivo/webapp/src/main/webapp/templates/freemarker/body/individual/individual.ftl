<#-- $This file is distributed under the terms of the license in /doc/license.txt$ -->

<#assign mostSpecType = "">
<#list individual.mostSpecificTypes as type>
	<#assign mostSpecType = type>
	<#break>
</#list>

<#assign bodyClass = "">
<#assign adminappTypes = ["Data Ingest", "Data Merge", "Merge Rule", "TextMergeAtom", "Author Group Merge Pattern", "ObjectPropertyMergeAtom", "Linked Merge Rule", "Data Publish", "Sparql Endpoint"]>
<#if adminappTypes?seq_contains(mostSpecType)>
	${stylesheets.add('<link rel="stylesheet" href="${urls.base}/css/adminapp.css" />')}
	<#assign bodyClass = "adminapp">
	
	<h2 class="processing-tabs" id="processing-tabs"></h2>
	<div id="adminapp-back"></div>
	${headScripts.add('<script type="text/javascript" src="${urls.base}/js/adminapp.js"></script>')}
	
	<script>
		addCurPage(document.location, "${title?html}", "${mostSpecType}");
		showHeadTabs("${urls.base}");
		showBreadCrumb();
	</script>
</#if>


<div class="${bodyClass}">
<#include "individual-setup.ftl">
<#import "lib-vivo-properties.ftl" as vp>

<#assign individualProductExtensionPreHeader>
    <#include "individual-altmetric.ftl">
</#assign>

<#assign individualProductExtension>
    <#-- Include for any class specific template additions -->
    ${classSpecificExtension!}
    ${departmentalGrantsExtension!} 
    <!--PREINDIVIDUAL OVERVIEW.FTL-->
    <#include "individual-vocabularyService.ftl">
    <#include "individual-webpage.ftl">
    <#include "individual-overview.ftl">
    ${affiliatedResearchAreas!}
        </section> <!-- #individual-info -->
    </section> <!-- #individual-intro -->
    <!--postindividual overiew ftl-->
</#assign>

<#if individual.conceptSubclass() >
    <#assign overview = propertyGroups.pullProperty("http://www.w3.org/2004/02/skos/core#broader")!> 
    <#assign overview = propertyGroups.pullProperty("http://www.w3.org/2004/02/skos/core#narrower")!> 
    <#assign overview = propertyGroups.pullProperty("http://www.w3.org/2004/02/skos/core#related")!> 
</#if>

<#if sources?has_content>
<h2>Data sources for this individual</h2>
<ul>
<#list sources as source>
    <li><a href="${urls.base}/individual?uri=${source.uri?url}">${source.name}</a></li>
</#list>
</ul>
</#if>

<#include "individual-vitro.ftl">
<script>
var i18nStrings = {
    displayLess: '${i18n().display_less}',
    displayMoreEllipsis: '${i18n().display_more_ellipsis}',
    showMoreContent: '${i18n().show_more_content}',
    verboseTurnOff: '${i18n().verbose_turn_off}',
};
</script>

${stylesheets.add('<link rel="stylesheet" href="${urls.base}/css/individual/individual-vivo.css" />')}

${headScripts.add('<script type="text/javascript" src="${urls.base}/js/jquery_plugins/jquery.truncator.js"></script>')}
${scripts.add('<script type="text/javascript" src="${urls.base}/js/individual/individualUtils.js"></script>')}
${scripts.add('<script type="text/javascript" src="https://d1bxh8uas1mnw7.cloudfront.net/assets/embed.js"></script>')}

</div>