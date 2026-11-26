# Privacy Policy — CAPJI Signal Pro
Last updated: 26 November 2026

## Local DNS tunnel (VpnService)
The app creates a VPN interface on your device that routes **only DNS requests** to the DNS provider you select. Other traffic does not pass through the app. There is no CAPJI server. CAPJI does not collect, log, store, sell or share DNS requests or any other traffic; the app only keeps an in-memory count of answered requests.

DNS requests are sent unencrypted (UDP/TCP port 53) to the selected provider (Cloudflare, Google, Quad9, AdGuard, OpenDNS or your custom server), which processes them under its own privacy policy. The speed test also queries these providers and your internet provider's resolver. The ping test contacts your router, 1.1.1.1 and 8.8.8.8.

## Permissions
- **Precise location:** required by Android to read the Wi-Fi name and scan nearby networks. Your position is never read, stored or transmitted.
- **Camera:** only to scan QR codes. Frames are processed on the device and not saved.
- **Notifications:** the boost status and optional announcements.

## Stored data
Settings and room measurements (including network names) stay on the device. Wi-Fi passwords are not stored by the app. A QR card you save or share contains the network password.

## Third-party components
- **Google ML Kit** performs barcode scanning on the device; the library may send diagnostic and usage metrics to Google.
- **Firebase Cloud Messaging and Remote Config** are active only in builds configured with Firebase. They process a Firebase installation identifier and messaging token under Google's privacy policy.

## Contact
WhatsApp: https://wa.me/967772678571 · Instagram: https://instagram.com/axh.d

---

# سياسة الخصوصية (ملخص عربي)
يوجّه التطبيق طلبات DNS فقط عبر نفق محلي إلى المزود الذي تختاره، بلا خوادم لكابجي وبلا جمع أو تسجيل لحركة الشبكة. تُرسل طلبات DNS غير مشفرة إلى المزود المختار وتخضع لسياسته. الموقع الدقيق لإظهار بيانات الواي فاي فقط، والكاميرا لمسح الرموز داخل الهاتف. قد ترسل مكتبة ML Kit من Google مقاييس تشخيصية، ويعالج Firebase (إن فُعّل) معرّف التثبيت ورمز الإشعارات. القياسات محفوظة على جهازك، ولا يخزن التطبيق كلمات المرور.
