<#-- @ftlvariable name="status" type="java.lang.Integer" -->
<#-- @ftlvariable name="error" type="java.lang.String" -->
<#-- @ftlvariable name="message" type="java.lang.String" -->
<#import "layout/main.ftlh" as layout />
<@layout.shell title="Something went wrong" useNavlist=false>
    <section class="error-block" aria-labelledby="page-title">
        <div class="error-icon" aria-hidden="true">
            <svg viewBox="0 0 32 32" fill="none" stroke="currentColor" stroke-width="1.5">
                <path d="M16 4 30 28H2Z" stroke-linejoin="round" />
                <path d="M16 12v7m0 4v1" stroke-width="2" stroke-linecap="round" />
            </svg>
        </div>
        <p class="error-code">ERROR <#if status??>${status}<#else>500</#if></p>
        <h1 id="page-title">Something went wrong</h1>
        <p class="subtitle">
            <#if status?? && status == 404>The page you requested could not be found.
            <#elseif status?? && status == 403>You don't have permission to view this page.
            <#else>An unexpected error occurred. Please try again later.</#if>
        </p>
        <a class="primary" href="/">Back to home<span class="arrow" aria-hidden="true">→</span></a>
    </section>
</@layout.shell>
