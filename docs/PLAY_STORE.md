# Google Play release checklist

The code follows the published requirements below, but **approval is Google's decision**. In particular, the VpnService policy does not explicitly list local DNS-only apps among its permitted uses.

## Before uploading
- [ ] Build a **signed release App Bundle** with your own upload key (`bundleRelease`). The CI APK is debug-signed and cannot be published. Test the minified release build on a device.
- [ ] Host `PRIVACY_POLICY.md` at a public URL and add it to the Play Console.
- [ ] Complete the **VpnService declaration** (core functionality, demo video up to 90 s, disclosure video).
- [ ] State VpnService use in the store description (text below).
- [ ] Complete the **foreground service** declaration if prompted (`systemExempted`, VPN).
- [ ] Complete the **Data safety** form, including ML Kit diagnostics and Firebase identifiers if enabled.
- [ ] Review the current **location permission** declaration requirements.
- [ ] Do not promise lower in-game ping in the listing; describe DNS response time.

## Suggested listing text
> CAPJI Signal Pro uses Android's VpnService to create a local, on-device tunnel that routes only DNS requests to the DNS server you choose, to speed up name lookups. It is not a remote VPN, does not change your IP address, and does not collect your traffic.

> يستخدم التطبيق خدمة VpnService لإنشاء نفق محلي يوجّه طلبات DNS فقط إلى الخادم الذي تختاره لتسريع ترجمة أسماء المواقع. ليس VPN بعيداً، ولا يغيّر عنوان IP، ولا يجمع بياناتك.

## References
- VpnService policy: https://support.google.com/googleplay/android-developer/answer/12564964
- Target API level: https://support.google.com/googleplay/android-developer/answer/11926878
