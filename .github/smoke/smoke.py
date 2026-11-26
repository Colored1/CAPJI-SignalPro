"""Drives the installed app on an emulator and reports what worked."""
import os
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

APK, SHOTS, API = sys.argv[1], sys.argv[2], int(sys.argv[3])
PKG = "com.capji.signalpro"
results = []
os.makedirs(SHOTS, exist_ok=True)


def adb(*args, timeout=90):
    try:
        return subprocess.run(["adb", *args], capture_output=True, timeout=timeout).stdout.decode("utf-8", "replace")
    except subprocess.TimeoutExpired:
        return ""


def sh(command, timeout=90):
    return adb("shell", command, timeout=timeout)


def shot(name):
    data = subprocess.run(["adb", "exec-out", "screencap", "-p"], capture_output=True, timeout=60).stdout
    with open(f"{SHOTS}/{name}.png", "wb") as handle:
        handle.write(data)


def nodes():
    for _ in range(6):
        sh("rm -f /sdcard/ui.xml")
        sh("uiautomator dump /sdcard/ui.xml", timeout=40)
        xml = adb("exec-out", "cat", "/sdcard/ui.xml")
        if "<hierarchy" in xml:
            try:
                return list(ET.fromstring(xml[xml.index("<hierarchy"):]).iter("node"))
            except ET.ParseError:
                pass
        time.sleep(2)
    return []


def box(node):
    return [int(v) for v in re.findall(r"\d+", node.get("bounds"))]


def match(node, label, exact):
    for value in (node.get("text", ""), node.get("content-desc", "")):
        if value == label if exact else label in value:
            return True
    return False


def find(label, exact=False):
    return next((n for n in nodes() if match(n, label, exact)), None)


def all_text():
    return " | ".join(n.get("text", "") for n in nodes() if n.get("text"))


def swipe(up=True):
    start, end = (1750, 850) if up else (850, 1750)
    sh(f"input swipe 540 {start} 540 {end} 500")
    time.sleep(1.5)


def scroll_to(label, exact=False, tries=10):
    """Finds a node, scrolling down. Skips nodes hidden under the navigation bar."""
    for _ in range(tries):
        node = find(label, exact)
        if node is not None:
            _, top, _, bottom = box(node)
            if bottom - top > 8 and bottom < 2150:
                return node
        swipe()
    return None


def top():
    for _ in range(7):
        sh("input swipe 540 700 540 2000 120")
    time.sleep(1.5)


def tap_node(node):
    x1, y1, x2, y2 = box(node)
    sh(f"input tap {(x1 + x2) // 2} {(y1 + y2) // 2}")
    time.sleep(1.5)


def tap(label, exact=True, scroll=False):
    node = scroll_to(label, exact) if scroll else find(label, exact)
    if node is None:
        return False
    tap_node(node)
    return True


def wait_for(label, seconds, exact=False):
    end = time.time() + seconds
    while time.time() < end:
        if find(label, exact) is not None:
            return True
        time.sleep(2)
    return False


def check(name, ok, detail=""):
    results.append((name, bool(ok), detail))
    print(("PASS " if ok else "FAIL ") + name + " " + detail, flush=True)


def alive():
    return bool(sh(f"pidof {PKG}").strip())


def tunnel_up():
    return "10.215.173.1" in sh("ip addr")


def run():
    sh("settings put global hide_error_dialogs 1")
    out = adb("install", "-r", APK, timeout=240)
    check("install", "Success" in out, out.strip()[-120:])
    permissions = ["ACCESS_FINE_LOCATION", "ACCESS_COARSE_LOCATION", "CAMERA"]
    if API >= 33:
        permissions.append("POST_NOTIFICATIONS")
    for permission in permissions:
        sh(f"pm grant {PKG} android.permission.{permission}")
    sh("settings put secure location_mode 3")
    # Pre-approves the system VPN consent dialog, which only a person can tap normally.
    sh(f"appops set {PKG} ACTIVATE_VPN allow")
    adb("logcat", "-c")

    sh(f"am start -W -n {PKG}/.MainActivity")
    time.sleep(1)
    shot("01-splash")
    check("launch shows boost tab", wait_for("تفعيل التسريع وتقليل البينج", 60))
    shot("02-boost-idle")

    # ---- boost
    orb = next((n for n in nodes() if n.get("content-desc") == "تفعيل التسريع وتقليل البينج"), None)
    if orb is not None:
        tap_node(orb)
    check("disclosure dialog appears first", wait_for("أوافق وأتابع", 15))
    check("no tunnel before consent", not tunnel_up())
    shot("03-disclosure")
    tap("أوافق وأتابع")
    active = wait_for("التسريع مفعّل", 60)
    check("boost becomes active", active, "" if active else all_text()[:300])
    time.sleep(5)
    shot("04-boost-active")
    check("tun interface exists", tunnel_up())
    services = sh(f"dumpsys activity services {PKG}")
    check("service is foreground", "isForeground=true" in services)
    answers = [sh(f"ping -c 1 -W 3 {host}") for host in ("wikipedia.org", "example.com", "github.com")]
    check("names resolve through tunnel", all(a.startswith("PING ") for a in answers),
          " / ".join(a.split("\n")[0][:60] for a in answers))
    label = scroll_to("طلبات DNS مُجابة")
    count = None
    if label is not None:
        lx1, ly1, lx2, _ = box(label)
        for node in nodes():
            x1, _, x2, y2 = box(node)
            if node.get("text", "").isdigit() and y2 <= ly1 + 5 and ly1 - y2 < 120 and x1 < lx2 and x2 > lx1:
                count = int(node.get("text"))
    check("tunnel answered DNS queries", count and count > 0, f"count={count}")
    shot("05-boost-card")
    scroll_to("اختبر الآن")
    swipe()
    shot("06-dns-list")
    text = all_text()
    check("benchmark measured servers", re.search(r"\d+ ms", text), text[:200])

    # ---- radar
    check("open radar", tap("الرادار"))
    time.sleep(6)
    shot("07-radar")
    label = scroll_to("تشغيل السونار")
    if label is not None:
        middle = sum(box(label)[1::2]) // 2
        switch = next((n for n in nodes() if n.get("checkable") == "true"
                       and box(n)[1] <= middle <= box(n)[3]), None)
        if switch is not None:
            tap_node(switch)
            time.sleep(5)
            check("sonar runs", alive())
            tap_node(switch)
    swipe()
    shot("08-oscilloscope")
    check("live SSID shown", scroll_to("AndroidWifi") is not None)
    shot("09-details")
    if scroll_to("قِس هنا", exact=True) is not None:
        tap("المجلس", scroll=False)
        tap("قِس هنا", scroll=True)
        time.sleep(9)
        swipe()
        text = all_text()
        check("room reading saved", "المجلس" in text and "dBm" in text, text[-200:])
        shot("10-rooms")
    else:
        check("room logger visible", False)

    # ---- analyzer
    check("open analyzer", tap("المحلل"))
    time.sleep(3)
    check("start ping test", tap("ابدأ الاختبار"))
    time.sleep(40)
    shot("11-ping")
    verdicts = ["الاتصال مستقر", "المشكلة", "لا يوجد إنترنت", "تعذّر الوصول", "الإنترنت ضعيف"]
    seen = ""
    for _ in range(4):
        seen += all_text()
        if any(v in seen for v in verdicts):
            break
        time.sleep(8)
    found = [v for v in verdicts if v in seen]
    check("ping test ends with a verdict", found, str(found))
    check("channel recommendation", scroll_to("القناة الموصى بها") is not None)
    swipe()
    shot("12-channels")
    top()
    scroll_to("فحص الشبكات المجاورة")
    shot("13-graph")

    # ---- tools
    check("open tools", tap("الأدوات"))
    time.sleep(2)
    shot("14-tools")
    check("use generated password", tap("استخدمها في بطاقة QR", scroll=True))
    top()
    check("create QR card", tap("إنشاء البطاقة", scroll=True))
    time.sleep(3)
    save = scroll_to("حفظ", exact=True)
    shot("15-qr-card")
    if save is not None:
        tap_node(save)
        time.sleep(3)
        check("QR card saved", scroll_to("حُفظت البطاقة", tries=3) is not None
              or "CAPJI-WiFi" in sh("content query --uri content://media/external/images/media --projection _display_name"))
        if tap("مشاركة", scroll=True):
            time.sleep(4)
            shot("16-share")
            sh("input keyevent 4")
            time.sleep(2)
            check("share sheet returns", alive())
    else:
        check("QR card visible", False, all_text()[:200])
    check("open camera", tap("فتح الكاميرا", scroll=True))
    time.sleep(10)
    shot("17-scanner")
    check("scanner stays alive", alive() and find("وجّه الكاميرا") is not None)
    sh("input keyevent 4")
    time.sleep(2)
    scroll_to("جميع الحقوق")
    swipe()
    shot("18-about")

    # ---- language and stop
    check("switch to English", tap("EN") and wait_for("Tools", 10, exact=True))
    shot("19-english-tools")
    tap("Boost")
    time.sleep(3)
    top()
    shot("20-english-boost")
    check("boost still on in English", wait_for("Boost is on", 15))
    node = find("ms DNS time") or find("Boost Now", exact=True) or find("ON", exact=True)
    if node is not None:
        tap_node(node)
    time.sleep(5)
    check("boost stops", not tunnel_up() and find("Boost Now") is not None)
    shot("21-stopped")
    # Restart once to cover a second activation in the same process.
    node = find("Boost Now", exact=True)
    if node is not None:
        tap_node(node)
    check("boost restarts", wait_for("Boost is on", 60) and tunnel_up())


try:
    run()
except Exception as error:  # report whatever was collected
    check("script", False, repr(error))

log = adb("logcat", "-d", timeout=120)
with open(f"{SHOTS}/capji-log.txt", "w") as handle:
    handle.write("\n".join(l for l in log.splitlines() if re.search(r"capji|AndroidRuntime", l, re.I))[-60000:])
crashes = []
lines = log.splitlines()
for index, line in enumerate(lines):
    if "FATAL EXCEPTION" in line and PKG in "\n".join(lines[index:index + 4]):
        crashes.append("\n".join(l.split("AndroidRuntime: ")[-1] for l in lines[index:index + 30]))
    if f"ANR in {PKG}" in line:
        crashes.append("\n".join(lines[index:index + 8]))
check("no crash or ANR", not crashes, crashes[0][:2500] if crashes else "")

failed = [r for r in results if not r[1]]
summary = "\n".join(f"{'PASS' if ok else 'FAIL'} | {name} | {detail if (not ok or 'resolve' in name or 'answered' in name or 'verdict' in name) else ''}"
                    for name, ok, detail in results)
with open(f"{SHOTS}/summary.txt", "w") as handle:
    handle.write(summary)
encoded = summary[:30000].replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A")
level = "error" if failed else "notice"
print(f"::{level} title=API {API}: {len(results) - len(failed)}/{len(results)} passed::{encoded}")
sys.exit(1 if failed else 0)
