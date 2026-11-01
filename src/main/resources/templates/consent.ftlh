<#-- @ftlvariable name="clientName" type="java.lang.String" -->
<#-- @ftlvariable name="principalName" type="java.lang.String" -->
<#-- @ftlvariable name="clientId" type="java.lang.String" -->
<#-- @ftlvariable name="state" type="java.lang.String" -->
<#-- @ftlvariable name="scopes" type="java.util.Collection<java.lang.String>" -->
<#-- @ftlvariable name="previouslyAuthorizedScopes" type="java.util.Collection<java.lang.String>" -->
<#import "/spring.ftl" as spring />
<#import "layout/main.ftlh" as layout />
<#import "layout/csrf.ftlh" as csrf />
<@layout.shell title="Authorize access" useNavlist=false>
    <section class="card" aria-labelledby="page-title">
        <img class="card-symbol" src="<@spring.url '/images/razornd-identity-symbol.svg'/>" alt="">
        <h1 id="page-title">Consent required</h1>
        <p class="subtitle">
            <strong>${(clientName!'Application')}</strong> is requesting access to your account.
        </p>
        <p class="account-tag">You are signed in as<strong>${(principalName!'')}</strong></p>
        <form action="<@spring.url '/oauth2/authorize'/>" method="post">
            <@csrf.field />
            <input type="hidden" name="client_id" value="${(clientId!'')}">
            <input type="hidden" name="state" value="${(state!'')}">
            <h2 class="permission-title">Requested permissions</h2>
            <#list scopes as scope>
                <label class="permission" for="${scope}">
                    <span class="permission-icon" aria-hidden="true">✓</span>
                    <span><h2>${scope}</h2><p>Allow this application to access the ${scope} permission.</p></span>
                    <input id="${scope}" type="checkbox" name="scope" value="${scope}"
                           <#if previouslyAuthorizedScopes?seq_contains(scope)>checked disabled</#if>>
                </label>
            </#list>
            <p class="consent-note">Only grant access to applications you trust.</p>
            <div class="button-row">
                <button class="secondary" type="submit" name="user_oauth_approval" value="false">Cancel</button>
                <button class="primary" type="submit" id="submit-consent">Allow<span class="arrow"
                                                                                     aria-hidden="true">→</span>
                </button>
            </div>
        </form>
    </section>
</@layout.shell>
