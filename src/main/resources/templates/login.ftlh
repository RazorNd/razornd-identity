<#import "/spring.ftl" as spring />
<#import "layout/main.ftlh" as layout />
<#import "layout/csrf.ftlh" as csrf />
<@layout.page title="Sign in">
    <main class="login">
        <aside class="brand-panel">
            <a class="logo" href="<@spring.url '/'/>" aria-label="razornd identity home">
                <img src="<@spring.url '/images/razornd-identity-wordmark-light.svg' />"
                     alt="razornd identity"
                     width="440" height="96"></a>
            <div class="brand-copy">
                <h2>One account.<br>Your services.</h2>
                <p>One sign-in for razornd applications.</p>
            </div>
            <svg class="door-art" viewBox="0 0 650 520" fill="none" preserveAspectRatio="xMidYMax slice"
                 aria-hidden="true">
                <defs>
                    <linearGradient id="leaf" x1="375" y1="180" x2="470" y2="250" gradientUnits="userSpaceOnUse">
                        <stop stop-color="#4ba98a" />
                        <stop offset="1" stop-color="#6de0bb" />
                    </linearGradient>
                </defs>
                <path d="M78 470V180C78 65 150 48 219 71L465 162L265 245V442" stroke="#6de0bb" stroke-opacity=".65" />
                <path d="M375 200L465 162V488L375 454Z" fill="url(#leaf)" />
                <path d="M0 498 265 442 650 520M265 442V245L465 162" stroke="#6de0bb" stroke-opacity=".8" />
            </svg>
        </aside>
        <section class="login-panel" aria-labelledby="page-title">
            <div class="content">
                <img class="login-symbol" src="<@spring.url '/images/razornd-identity-mono-dark.svg' />" alt="">
                <h1 id="page-title">Sign in to your account</h1>
                <form class="form" action="<@spring.url '/login'/>" method="post">
                    <@csrf.field />
                    <#if error??>
                        <div class="notice" role="alert">The username or password is incorrect. Please try again.</div>
                    </#if>
                    <div class="field"><label for="username">Username</label>
                        <div class="input-wrap">
                            <input id="username"
                                   name="username"
                                   type="text"
                                   autocomplete="username"
                                   autocapitalize="none"
                                   spellcheck="false"
                                   required>
                        </div>
                    </div>
                    <div class="field"><label for="password">Password</label>
                        <div class="input-wrap">
                            <input id="password"
                                   name="password"
                                   type="password"
                                   autocomplete="current-password"
                                   required>
                        </div>
                    </div>
                    <button class="primary full" type="submit">Sign in<span class="arrow" aria-hidden="true">→</span>
                    </button>
                </form>
            </div>
        </section>
    </main>
</@layout.page>
