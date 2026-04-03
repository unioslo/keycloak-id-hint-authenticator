         <meta push="true" />
        <form id="kc-push-login-form" name="kc-push-login-form" class="${properties.kcFormClass!}" action="${url.loginAction}" method="post">
            <div class="${properties.kcFormGroupClass!}">
                <div id="kc-form-options" class="${properties.kcFormOptionsClass!}">
                    <div class="${properties.kcFormOptionsWrapperClass!}">
                    </div>
 <div id="kc-form-buttons" class="${properties.kcFormButtonsClass!}">
                    <div class="${properties.kcFormButtonsWrapperClass!}">
                        <input type="hidden" form="kc-push-login-form" id="id-hidden-input" name="secret_key" <#if secret_key?has_content>value="${secret_key}"</#if>/>
                        <input type="hidden" form="kc-push-login-form" id="id-hidden-input" name="secret_value" <#if secret_value?has_content>value="${secret_value}"</#if>/>
                        <input type="hidden" form="kc-push-login-form" id="poll" name="poll" <#if poll?has_content>value="${poll?c}"</#if>/>
                        <input class="${properties.kcButtonClass!} ${properties.kcButtonPrimaryClass!} ${properties.kcButtonBlockClass!} ${properties.kcButtonLargeClass!}"
                               form="kc-push-login-form" name="login" id="kc-login" type="submit" value="${msg("doLogIn")}"/>
                    </div>
               </div>


            </div>
       </div>
        </form>
<script type="text/javascript">
var form = document.getElementById("kc-push-login-form");
form.submit();
</script>
