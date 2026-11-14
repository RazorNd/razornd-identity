<#import "/spring.ftl" as spring />
<#import "layout/main.ftlh" as layout />
<@layout.shell title="Account disabled" useNavlist=false>
    <section class="error-block" aria-labelledby="page-title" role="alert">
        <h1 id="page-title">Your account is disabled</h1>
        <p class="subtitle">Please contact the administration to have it re-enabled.</p>
        <a class="primary" href="<@spring.url '/login'/>">Back to sign in<span class="arrow"
                                                                              aria-hidden="true">→</span></a>
    </section>
</@layout.shell>
