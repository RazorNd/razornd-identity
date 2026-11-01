<#-- @ftlvariable name="username" type="java.lang.String" -->
<#import "/spring.ftl" as spring />
<#import "layout/main.ftlh" as layout />
<#import "layout/csrf.ftlh" as csrf />
<@layout.shell title="Change password" useNavlist=true>
    <section class="content account" aria-labelledby="page-title">
        <p class="eyebrow">Account</p>
        <h1 id="page-title">Change password</h1>
        <p class="subtitle">Account: ${username}</p>
        <form class="form" action="<@spring.url '/change-password'/>" method="post">
            <@csrf.field />
            <div class="field"><label for="old-password">Current password</label>
                <@spring.bind "changePasswordForm.oldPassword" />
                <#list spring.status.errorMessages as error><div class="notice" role="alert">${error}</div></#list>
                <div class="input-wrap">
                    <input id="old-password"
                           name="oldPassword"
                           type="password"
                           autocomplete="current-password"
                           required>
                </div>
            </div>
            <div class="field"><label for="new-password">New password</label>
                <@spring.bind "changePasswordForm.newPassword" />
                <#list spring.status.errorMessages as error><div class="notice" role="alert">${error}</div></#list>
                <div class="input-wrap">
                    <input id="new-password"
                           name="newPassword"
                           type="password"
                           autocomplete="new-password"
                           required>
                </div>
            </div>
            <div class="field"><label for="repeat-password">Confirm new password</label>
                <@spring.bind "changePasswordForm.repeatPassword" />
                <#list spring.status.errorMessages as error><div class="notice" role="alert">${error}</div></#list>
                <div class="input-wrap">
                    <input id="repeat-password"
                           name="repeatPassword"
                           type="password"
                           autocomplete="new-password"
                           required>
                </div>
            </div>
            <button class="primary full" type="submit">Save password<span class="arrow"
                                                                          aria-hidden="true">→</span></button>
            <p class="form-foot"><a class="text-link" href="<@spring.url '/'/>">Back to home</a></p>
        </form>
    </section>
</@layout.shell>
