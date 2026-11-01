<#import "/spring.ftl" as spring />
<#import "csrf.ftlh" as csrf />
<#macro header>
    <header class="header">
        <a class="logo" href="<@spring.url '/'/>" aria-label="razornd identity home">
            <img src="<@spring.url '/images/razornd-identity-wordmark-dark.svg'/>"
                 alt="razornd identity"
                 width="440"
                 height="96">
        </a>
        <#nested>
    </header>
</#macro>

<#macro navlist>
    <nav class="header-links" aria-label="Account">
        <a href="<@spring.url '/'/>">Home</a>
        <form class="logout-form" action="<@spring.url '/logout'/>" method="post">
            <@csrf.field />
            <button class="text-link" type="submit">Sign out</button>
        </form>
    </nav>
</#macro>
