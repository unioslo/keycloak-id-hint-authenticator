<#import "template.ftl" as layout>
<@layout.registrationLayout; section>
    <#if section = "title">
        ${msg("loginTitle",realm.name)}
    <#elseif section = "header">
        ${msg("loginTitleHtml",realm.name)}
    <#elseif section = "form">
        <form id="kc-totp-login-form" class="${properties.kcFormClass!}" action="${url.loginAction}" method="post">
            <input type="hidden" form="kc-totp-login-form" id="poll" name="poll" <#if poll?has_content>value=${poll}</#if> />
            <input type="hidden" form="kc-totp-login-form" id="transactionId" name="transactionId" <#if transactionId?has_content>value=${transactionId}</#if> />

            <input type="hidden" form="kc-totp-login-form" id="timeout" name="timeout" value="false" />
               <div  id="spinningball"></div>
            <div class="${properties.kcFormGroupClass!}" id="boxcontent">
                <div class="${properties.kcLabelWrapperClass!}" >
                    <h1>${msg("uio-2fa-push-title")}</h1>
                    <span>
                       <h2>${msg("uio-2fa-push-label")}</h2>
                    </span>
                    <span><label for="totp" class="${properties.kcLabelClass!}">${msg("uio-2fa-push-label2-prefix")} <span id="seconds"></span> ${msg("uio-2fa-push-label2-suffix")}.</label></span>
              </div>
              
              <div class="${properties.kcLabelWrapperClass!}">
                       <p id="instruction2" class="instruction">
                      <a id="loginRestartLink" href="${url.loginRestartFlowUrl}">${msg("uio-login-restart")}</a><br/>
                       </p>
              </div>
              <div class="${properties.kcLabelWrapperClass!}">
                     <label for="totp" class="${properties.kcLabelClass!}"><a href=${msg("uio-2fa-help-link")}>${msg("uio-2fa-help-text")}</a></label>
              </div>
          </div>
            <div class="${properties.kcFormGroupClass!}">
                <div id="kc-form-options" class="${properties.kcFormOptionsClass!}">
                    <div class="${properties.kcFormOptionsWrapperClass!}">
                    </div>

   </div>
  </div>
</form>
<style>
   .spinning {
      border: 16px solid #ddd;
      border-radius: 50%;
      border-top: 16px solid #666;
      margin-left: auto;
      margin-right: auto;
      width: 120px;
      height: 120px;
      animation: spin 2s linear infinite;
    }

  @keyframes spin {
    0% { transform: rotate(0deg); }
    100% { transform: rotate(360deg); }
  }
</style>

        <script type="text/javascript">

            const realmName = "${realm.name}";
            var remaining = 30;
            function countDown() {
                const now = Date.now();
                const elapsed = Math.floor((now - startTime) / 1000);
                remaining = duration - elapsed;

                if (remaining > 0) {
                    displayTime.innerText = '' + remaining;
                } else {
                    clearInterval(interval);
                    displayTime.innerText = '0';
                }
            }

            const duration = 30;
            const startTime = Date.now();

            const displayTime = document.createElement('span');
            displayTime.classList.add('desiredClassName');
            displayTime.id = 'desiredID';

            const div = document.getElementById('seconds');
            if (div) {
                div.appendChild(displayTime);
                countDown(); // immediate update
                interval = setInterval(countDown, 1000);
            }

            let abortController = new AbortController();
            var poll_result = "pending";
            var stop_polling = false;
            function isMobileDevice() {
                return /iPhone|iPad|iPod|Android/i.test(navigator.userAgent);

            }
            document.addEventListener('visibilitychange', () => {
                if (isMobileDevice()){
                    if (document.visibilityState === 'visible') {
                        //abortController.abort();
                        stop_polling = false;
                        pollStatus();
                    } else {
                        stop_polling = true;

                    }
                }
            });

            let futureTime = Date.now() + 60000;
            var count = 61;
            const form = document.getElementById("kc-totp-login-form");
            const transactionId = form.querySelector('#transactionId').value;
            var error = 0;
            async function pollStatus() {
                //console.log("Start polling");
                if (stop_polling){
                    //console.log("Stop polling");
                    return;
                }
                //console.log("Still polling");
                try {
                    const res = await fetch(window.location.origin+"/realms/"+encodeURIComponent(realmName)+"/uiopush/uiopoll", {
                        method: "POST",
                        headers: {
                            "Accept": "application/json",
                            "Content-Type": "application/x-www-form-urlencoded"
                        },
                        body: "transactionId=" + encodeURIComponent(transactionId)
                    });

                    if (!res.ok) {
                        console.error("Polling failed", res.status);
                        return;
                    }

                    const data = await res.json();
                    //console.log("Polling response:", data);

                    if (data.status !== "pending" || remaining <= 0) {
                        showSpinningBall();

                        let stateInput = form.querySelector('input[name="validate"]');
                        if (!stateInput) {
                            stateInput = document.createElement('input');
                            stateInput.type = 'hidden';
                            stateInput.name = 'validate';
                            form.appendChild(stateInput);
                        }
                        if (data.status === "timeout" || remaining <= 0) {
                            let timeoutInput = form.querySelector('input[name="timeout"]');

                            if (!timeoutInput) {
                                timeoutInput = document.createElement('input');
                                timeoutInput.type = 'hidden';
                                timeoutInput.name = 'timeout';
                                form.appendChild(timeoutInput);
                            }

                            timeoutInput.value = "true";
                        }



                        form.submit();

                        // token is registered, continue flow



                    } else {
                        setTimeout(pollStatus, 1000);
                    }
                } catch (err) {
                    console.error("Polling error", err);
                    error++;
                    if (error >2){
                        let stateInput = form.querySelector('input[name="validate"]');
                        if (!stateInput) {
                            stateInput = document.createElement('input');
                            stateInput.type = 'hidden';
                            stateInput.name = 'validate';
                            form.appendChild(stateInput);
                        }
                        form.submit();
                    }
                    setTimeout(pollStatus, 2000); // retry with backoff
                }
            }

            // start polling



            function hideSpinningBall(){
                let spinning = document.getElementById("spinningball");
                spinning.style.display = "none";
                let content  = document.getElementById("boxcontent");
                content.style.display = "initial";

            }
            function showSpinningBall(){
                let spinning = document.getElementById("spinningball");
                let content  = document.getElementById("boxcontent");
                content.style.display = "none";
                //spinning.style.display = "inline";
                spinning.classList.add("spinning");
                //spinning.style.animation = "spin 2s linear infinite";
                //spinning.style.animationPlayState = "running";
            }


            // Start polling
            pollStatus();
        </script>



    </#if>
</@layout.registrationLayout>
