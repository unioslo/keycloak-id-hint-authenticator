<#import "template.ftl" as layout>
<@layout.registrationLayout; section>
    <#if section = "title">
        ${msg("loginTitle",realm.name)}
    <#elseif section = "header">
        ${msg("loginTitleHtml",realm.name)}
    <#elseif section = "form">
                   <h1>${msg("uio-2fa-push-title")}</h1>
<#if message?has_content>
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
        <form id="kc-totp-login-form" class="${properties.kcFormClass!}" action="${url.loginAction}" method="post">
            <div class="${properties.kcFormGroupClass!}">
                <div class="${properties.kcLabelWrapperClass!}">
                    <label for="totp" class="${properties.kcLabelClass!}">${kcSanitize(msg("uio-2fa-"+method+"-label"))?no_esc}</label>
                </div>

                <div class="${properties.kcInputWrapperClass!}">
                    <input id="totp" name="secret_answer" autocomplete="one-time-code" type="text" class="${properties.kcInputClass!}" autofocus />
                </div>
            </div>

            <div class="${properties.kcFormGroupClass!}">
                <div id="kc-form-options" class="${properties.kcFormOptionsClass!}">
                    <div class="${properties.kcFormOptionsWrapperClass!}">
                    </div>
                </div>

                <div id="kc-form-buttons" class="${properties.kcFormButtonsClass!}">
                    <div class="${properties.kcFormButtonsWrapperClass!}">
                        <input type="hidden" id="id-hidden-input" name="credentialId" <#if auth.selectedCredential?has_content>value="${auth.selectedCredential}"</#if>/>
                        <input type="hidden" id="id-hidden-input" name="state" <#if state?has_content>value="${state}"</#if>/>
                        <input type="hidden" id="id-hidden-input" name="transactionId" <#if transactionId?has_content>value="${transactionId}"</#if>/>
                        <input class="${properties.kcButtonClass!} ${properties.kcButtonPrimaryClass!} ${properties.kcButtonBlockClass!} ${properties.kcButtonLargeClass!}"
                               name="login" id="kc-login" type="submit" value="${msg("doLogIn")}"/>
                    </div>
     
                </div>
                <div class="${properties.kcInputWrapperClass!}">
<p id="instruction2" class="instruction">
               <a id="loginRestartLink" href="${url.loginRestartFlowUrl}">${msg("uio-login-restart")}</a>
            </p>
                    <label for="totp" class="${properties.kcLabelClass!}"> <a href=${msg("uio-2fa-help-link")}>${msg("uio-2fa-help-text")}</a></label>
                </div>
            </div>

            
        </form>
    </#if>
</@layout.registrationLayout>
