<#import "template.ftl" as layout>
<@layout.registrationLayout; section>
    <#if section = "title">
        ${msg("loginTitle",realm.name)}
    <#elseif section = "header">
        ${msg("loginTitleHtml",realm.name)}
    <#elseif section = "form">
        <form id="kc-totp-login-form" class="${properties.kcFormClass!}" action="${url.loginAction}" method="post">
<#if message?has_content>
<label for="totp" class="2fa-title">${msg("uio-login-error-"+message.summary+"-title")}</label>
 <div class="alert-${message.type} ${properties.kcAlertClass!} pf-m-<#if message.type = 'error'>danger<#else>${message.type}</#if>">
                  <div class="pf-c-alert__icon">
                      <#if message.type = 'success'><span class="${properties.kcFeedbackSuccessIcon!}"></span></#if>
                      <#if message.type = 'warning'><span class="${properties.kcFeedbackWarningIcon!}"></span></#if>
                      <#if message.type = 'error'><span class="${properties.kcFeedbackErrorIcon!}"></span></#if>
                      <#if message.type = 'info'><span class="${properties.kcFeedbackInfoIcon!}"></span></#if>
                  </div>
                      <span class="${properties.kcAlertTitleClass!}">${kcSanitize(msg("uio-login-error-"+message.summary+"-summary"))?no_esc}</span>
              </div>
</#if>
            <div class="${properties.kcFormGroupClass!}">
                <div class="${properties.kcLabelWrapperClass!}">
                     <label for="totp" class="${properties.kcLabelClass!}"><a href=${msg("uio-2fa-help-link")}>${msg("uio-2fa-help-text")}</a></label>
                    <p id="instruction1" class="instruction">
                      <a id="loginRestartLink" href="${url.loginRestartFlowUrl}">${msg("uio-login-restart")}</a>
                    </p>
            </div>
       </div>

        </form>
    </#if>
</@layout.registrationLayout>
