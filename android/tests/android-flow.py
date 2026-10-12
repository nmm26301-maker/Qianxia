import os, subprocess, time, re, xml.etree.ElementTree as ET
from pathlib import Path

adb = os.environ['QIANXIA_ADB']
out = Path(__file__).resolve().parents[1] / 'verification'

def command(*args):
    return subprocess.run([adb, *args], check=True, capture_output=True, text=True).stdout

def screen():
    command('shell', 'uiautomator', 'dump', '/sdcard/qianxia-ui.xml')
    xml = command('shell', 'cat', '/sdcard/qianxia-ui.xml')
    (out / 'android-latest.xml').write_text(xml)
    return ET.fromstring(xml)

def match(root, value):
    return next((n for n in root.iter('node') if value in (n.get('text'), n.get('content-desc'))), None)

def click_node(n):
    if n is None: raise RuntimeError('UI element missing')
    x1,y1,x2,y2 = map(int, re.findall(r'\d+',n.get('bounds')))
    command('shell','input','tap',str((x1+x2)//2),str((y1+y2)//2))

def click(value):
    click_node(match(screen(),value))

def fill_passwords(value):
    root=screen()
    nodes=[n for n in root.iter('node') if n.get('class')=='android.widget.EditText']
    for n in nodes:
        click_node(n)
        command('shell','input','text',value)
    command('shell','input','keyevent','4')

def wait_text(value, attempts=25):
    for _ in range(attempts):
        root=screen()
        if any(value in n.get('text','') for n in root.iter('node')): return root
        time.sleep(.5)
    raise RuntimeError('Did not reach: '+value)

def capture(name):
    with open(out/name,'wb') as f:
        subprocess.run([adb,'exec-out','screencap','-p'],stdout=f,check=True)

click('选择要备份的内容')
click('备份 迁移测试应用')
click('下一步：加密备份')
fill_passwords('Migration123')
click('开始')
click('恢复')
wait_text('本地备份完成')
print('PASS Native app selection and encrypted backup',flush=True)
click('输入密码并解密恢复')
fill_passwords('WrongPassword123')
click('解密')
wait_text('密码错误')
print('PASS Native wrong password rejected',flush=True)
click('输入密码并解密恢复')
fill_passwords('Migration123')
click('解密')
wait_text('解密后的内容')
wait_text('迁移测试应用')
capture('android-restored.png')
print('PASS Native decrypt and verified restore manifest',flush=True)
command('uninstall','com.qianxia.fixture')
click('安装应用（系统会要求确认）')
click('安装')
for _ in range(20):
    root=screen()
    n=match(root,'Install') or match(root,'INSTALL')
    if n is not None:
        click_node(n);break
    time.sleep(.5)
else: raise RuntimeError('System installer confirmation not shown')
for _ in range(30):
    if 'com.qianxia.fixture' in command('shell','pm','list','packages','com.qianxia.fixture'): break
    time.sleep(1)
else:raise RuntimeError('Restored package not installed')
command('shell','am','start','-n','com.qianxia.fixture/.MainActivity')
wait_text('迁匣安装包迁移测试成功')
capture('android-installed-fixture.png')
print('PASS System-confirmed restored APK installs and launches',flush=True)
