# APK install - "App not installed" / "problem parsing the package" ka ilaaj

## Sabse pehle: 3 APK try karo (Releases -> Latest APK me teeno milengi)
1. `MY-AGENT.apk` (poori) - install na ho to:
2. `MY-AGENT-lite.apk` (assistant/tile/boot/mic-service/notification-reader hata kar) - na ho to:
3. `MY-AGENT-min.apk` (sabse kam components, v3 jaisi).
Jo pehli install ho jaye wahi chalao aur mujhe batao kaun si install hui - usse pata chalega kaunsa hissa phone ko pasand nahi aa raha.
Teeno fail ho to: Actions -> aakhri run -> "Check APK is valid and signed" step ka screenshot bhejo (package, sdkVersion, Verifies).
Phone me: Settings -> Apps -> upar 3 dot -> "Show system apps"/"Clone apps"/"Second space" me 'MY AGENT' ya package com.myagent.app dhundo
(doosre user/clone me purani app padi ho to naya install nahi hota) aur use wahan se uninstall karo.

1. **Sabse naya APK lo.** GitHub repo -> **Releases -> "Latest APK (direct download)"** -> `MY-AGENT.apk` dabao.
   Ye seedha .apk hai (zip nahi). Actions ke Artifacts zip hote hain - unhe pehle extract karna padta hai,
   zip ko install karoge to "parsing problem" aata hai.
2. **Actions green hona chahiye.** Repo -> Actions -> aakhri run par hara tick. Laal (fail) ho to us run ka log bhejo.
3. **Download poora hua?** File ka size ~4-6 MB hona chahiye. Adhoora download ho to dobara download karo.
4. **Purani MY AGENT app uninstall karo, phir install.** Alag-alag key se bani APK ek ke upar ek install nahi hoti
   ("App not installed"). (Uninstall se Settings/keys hat jate hain - phir daal dena.)
5. Phone: Settings -> Apps -> Special access -> **Install unknown apps** -> jis app se khol rahe ho (Files/Chrome) ko Allow.
6. Termux se kholna ho to: `termux-open ~/storage/shared/Download/MY-AGENT.apk`
7. Phir bhi nahi: Play Protect ko thodi der OFF karke dekho; Realme/Oppo me "Security scan" ka 'Install' dabana padta hai.

## Apni key (taaki hamesha update ho sake)
README me "Signing" section dekho. Ek hi key se bani APK ek ke upar update hoti hai.

## Token/password kabhi screenshot ya chat me mat bhejo
GitHub token leak ho jaye to GitHub -> Settings -> Developer settings -> Personal access tokens -> Delete.
