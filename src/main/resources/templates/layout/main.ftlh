<#macro page title>
    <#import "/spring.ftl" as spring />
    <!doctype html>
    <html lang="en">
    <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <meta name="theme-color" content="#17211f">
        <title>${title} — razornd identity</title>
        <link rel="icon" type="image/svg+xml" href="<@spring.url '/images/razornd-identity-favicon.svg'/>">
        <link rel="stylesheet" href="<@spring.url '/css/styles.css'/>">
    </head>
    <body>
    <#nested>
    </body>
    </html>
</#macro>

<#macro shell title useNavlist>
    <#import "header.ftlh" as header />
    <@page title=title>
        <div class="shell">
            <@header.header>
                <#if useNavlist>
                    <@header.navlist />
                </#if>
            </@header.header>
            <div class="stage">
                <#nested />
            </div>
        </div>
    </@page>
</#macro>
