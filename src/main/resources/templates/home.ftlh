<#-- @ftlvariable name="username" type="java.lang.String" -->
<#import "/spring.ftl" as spring />
<#import "layout/main.ftlh" as layout />
<#import "layout/csrf.ftlh" as csrf />
<@layout.shell title="Home" useNavlist=true>
    <section class="status content" aria-labelledby="page-title">
        <div class="status-icon" aria-hidden="true">
            <svg viewBox="0 0 32 32" fill="none" stroke="currentColor" stroke-width="2.4">
                <path d="m6 16 7 7L27 9" />
            </svg>
        </div>
        <h1 id="page-title">You are signed in</h1>
        <p class="identity-line">You are authenticated as <strong>${username}</strong>.</p>
        <p class="close-line">You can close this tab.</p>
        <div class="actions">
            <a class="primary full" href="<@spring.url '/change-password'/>">Change password<span class="arrow"
                                                                                                  aria-hidden="true">→</span></a>
            <form class="logout-form" action="<@spring.url '/logout'/>" method="post">
                <@csrf.field />
                <button class="text-link" type="submit">Sign out</button>
            </form>
        </div>
    </section>
</@layout.shell>
