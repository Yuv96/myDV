#!/usr/bin/env python3
"""Rebuild the public Lite APK with reviewed API-21 playback/cache sources."""
import hashlib, os, pathlib, re, shutil, subprocess, urllib.request, zipfile

ROOT=pathlib.Path(__file__).resolve().parent
WORK=ROOT/'build'
WORK.mkdir(exist_ok=True)
DIST=ROOT.parent/'dist'
DIST.mkdir(exist_ok=True)
SDK=pathlib.Path(os.environ['ANDROID_HOME'])
BT=SDK/'build-tools'/'35.0.0'
ANDROID=SDK/'platforms'/'android-35'/'android.jar'
def run(*args): subprocess.run([str(a) for a in args],check=True)
def download(name,url,sha):
    p=WORK/name
    if not p.exists(): urllib.request.urlretrieve(url,p)
    assert hashlib.sha256(p.read_bytes()).hexdigest()==sha, name+' checksum mismatch'
    return p
original=download('original.apk','https://github.com/mytv-android/myDV/releases/download/V1.1.9/myDV.Lite_1.1.9.apk','c01f3b93a7c0b97e481f9f9706a1ff9bc91c3431000be13695b1ca9ffcad69fa')
apktool=download('apktool.jar','https://github.com/iBotPeaches/Apktool/releases/download/v3.0.3/apktool_3.0.3.jar','dbf930b076c6b9be08d57c449cacefc3bdd6b71ebd59b3066fc0e1f5b14f9423')
vlc=download('libvlc.aar','https://repo.maven.apache.org/maven2/org/videolan/android/libvlc-all/3.7.6/libvlc-all-3.7.6.aar','6b438ab75eb3b307d9699f4594c043f46b0a9a697521bd03729c02c0a400eb8a')
bindings=download('libvlc-3.7.6-sources.jar','https://repo.maven.apache.org/maven2/org/videolan/android/libvlc-all/3.7.6/libvlc-all-3.7.6-sources.jar','95bb03b150ca4cda5fb334b02df2e4f95c627940a1134f805161ab0146566a37')
decoded=WORK/'decoded'
shutil.rmtree(decoded,ignore_errors=True) # Apktool's old build cache must not survive a source rebuild.
run('java','-jar',apktool,'d','-f',original,'-o',decoded)
with zipfile.ZipFile(vlc) as z: z.extractall(WORK/'vlc')
package=decoded/'smali/com/dycomment/tv'
for file in package.glob('PlayerView*.smali'): file.unlink()
for file in package.glob('ModernMenuHelper*.smali'): file.unlink()
main=package/'MainActivity.smali'
text=main.read_text()
pattern=r'(?ms)^\.method public synthetic lambda\$onCreate\$0\$com-dycomment-tv-MainActivity\(Landroid/media/MediaPlayer;\)V\n.*?^\.end method'
replacement='''.method public synthetic lambda$onCreate$0$com-dycomment-tv-MainActivity(Landroid/media/MediaPlayer;)V
    .locals 2
    iget-object v0, p0, Lcom/dycomment/tv/MainActivity;->videoView:Lcom/dycomment/tv/PlayerView;
    iget v1, p0, Lcom/dycomment/tv/MainActivity;->playbackSpeed:F
    invoke-virtual {v0, v1}, Lcom/dycomment/tv/PlayerView;->setPlaybackSpeed(F)V
    invoke-direct {p0}, Lcom/dycomment/tv/MainActivity;->hideLoading()V
    return-void
.end method'''
text,n=re.subn(pattern,lambda _:replacement,text); assert n==1
text,n=re.subn(r'(?ms)^\.method private applySpeed\(\)V\n.*?^\.end method', '''.method private applySpeed()V
    .locals 2
    iget-object v0, p0, Lcom/dycomment/tv/MainActivity;->videoView:Lcom/dycomment/tv/PlayerView;
    if-eqz v0, :done
    iget v1, p0, Lcom/dycomment/tv/MainActivity;->playbackSpeed:F
    invoke-virtual {v0, v1}, Lcom/dycomment/tv/PlayerView;->setPlaybackSpeed(F)V
    :done
    return-void
.end method''',text); assert n==1
for method, target in [('showDanmakuMenu','showQuick'), ('showExitMenu','showSettings'), ('updateClock','updateClock')]:
    pattern = rf'(?ms)^\.method private {method}\(\)V\n.*?^\.end method'
    replacement = f'''.method private {method}()V
    .locals 0
    invoke-static {{p0}}, Lcom/dycomment/tv/InteractionController;->{target}(Landroid/app/Activity;)V
    return-void
.end method'''
    text,n = re.subn(pattern, lambda _: replacement, text); assert n == 1, method
assert 'dispatchKeyEvent(Landroid/view/KeyEvent;)Z' not in text
text += '''
.method public dispatchKeyEvent(Landroid/view/KeyEvent;)Z
    .locals 1
    invoke-static {p0, p1}, Lcom/dycomment/tv/InteractionController;->handleKey(Landroid/app/Activity;Landroid/view/KeyEvent;)Z
    move-result v0
    if-eqz v0, :normal
    const/4 v0, 0x1
    return v0
    :normal
    invoke-super {p0, p1}, Landroid/app/Activity;->dispatchKeyEvent(Landroid/view/KeyEvent;)Z
    move-result v0
    return v0
.end method
'''
# Keep selection changes and asynchronous callbacks in the same epoch.
marker='    iput p1, p0, Lcom/dycomment/tv/MainActivity;->currentIndex:I'
assert text.count(marker)==1
text=text.replace(marker,marker+'\n    invoke-static {p0}, Lcom/dycomment/tv/PlaybackCoordinator;->selected(Landroid/app/Activity;)V')
marker='    :cond_9\n    iget-object p1, v1, Lcom/dycomment/tv/DouyinApi$FeedItem;->imageUrls:Ljava/util/List;'
assert text.count(marker)==1
text=text.replace(marker,'    :cond_9\n    invoke-static {p0}, Lcom/dycomment/tv/PlaybackCoordinator;->bound(Landroid/app/Activity;)V\n'+marker.split('\n',1)[1])
for method in ['loadVideoDetail','refreshAndPlay']:
    pattern=rf'(?ms)^\.method private {method}\(Lcom/dycomment/tv/DouyinApi\$FeedItem;\)V\n.*?^\.end method'
    replacement=f'''.method private {method}(Lcom/dycomment/tv/DouyinApi$FeedItem;)V
    .locals 0
    invoke-static {{p0, p1}}, Lcom/dycomment/tv/PlaybackCoordinator;->detail(Landroid/app/Activity;Ljava/lang/Object;)V
    return-void
.end method'''
    text,n=re.subn(pattern,lambda _:replacement,text); assert n==1,method
pattern=r'(?ms)^\.method public synthetic lambda\$onCreate\$2\$com-dycomment-tv-MainActivity\(Landroid/media/MediaPlayer;II\)Z\n.*?^\.end method'
replacement='''.method public synthetic lambda$onCreate$2$com-dycomment-tv-MainActivity(Landroid/media/MediaPlayer;II)Z
    .locals 1
    invoke-static {p0}, Lcom/dycomment/tv/PlaybackCoordinator;->error(Landroid/app/Activity;)Z
    move-result v0
    return v0
.end method'''
text,n=re.subn(pattern,lambda _:replacement,text); assert n==1
marker='.method private hideLoading()V\n    .locals 2'
assert marker in text
text=text.replace(marker,marker+'''
    invoke-static {p0}, Lcom/dycomment/tv/PlaybackCoordinator;->canHideLoading(Landroid/app/Activity;)Z
    move-result v0
    if-nez v0, :a53_hide_loading
    return-void
    :a53_hide_loading
''')
# Avatars and live metadata must not overwrite a selection made while requests were in flight.
for number in [26,29,30]:
    callback=package/f'MainActivity${number}.smali'
    body=callback.read_text()
    descriptor=f'Lcom/dycomment/tv/MainActivity${number};'
    body=body.replace('# instance fields','# instance fields\n.field private selectionEpoch:I\n')
    constructor=re.search(r'(?ms)^\.method constructor <init>.*?^\.end method',body).group()
    updated=constructor.replace('    .locals 0','    .locals 1')
    updated=updated.replace('    return-void',f'''    invoke-static {{p1}}, Lcom/dycomment/tv/PlaybackCoordinator;->token(Landroid/app/Activity;)I
    move-result v0
    iput v0, p0, {descriptor}->selectionEpoch:I
    return-void''')
    body=body.replace(constructor,updated)
    def guard(match):
        method=match.group()
        # New locals use fresh registers; original methods address arguments as pN.
        count=int(re.search(r'\.locals (\d+)',method).group(1)); a=f'v{count}'; b=f'v{count+1}'
        injection=f'''    .locals {count+2}
    iget-object {a}, p0, {descriptor}->this$0:Lcom/dycomment/tv/MainActivity;
    iget {b}, p0, {descriptor}->selectionEpoch:I
    invoke-static {{{a}, {b}}}, Lcom/dycomment/tv/PlaybackCoordinator;->valid(Landroid/app/Activity;I)Z
    move-result {a}
    if-nez {a}, :a53_current
    return-void
    :a53_current'''
        return re.sub(r'    \.locals \d+',lambda _:injection,method,count=1)
    body=re.sub(r'(?ms)^\.method public (?:onLoaded|onResult|onError|synthetic lambda\$onResult)[^\n]*\n.*?^\.end method',guard,body)
    callback.write_text(body)

# Replace the obsolete JSON chat/list endpoint with bounded read-only protobuf polling.
for method, signature, target, args in [('startLiveDanmaku','Ljava/lang/String;','start','p0, p1'),('stopLiveDanmaku','','stop','p0')]:
    pattern=rf'(?ms)^\.method private {method}\({signature}\)V\n.*?^\.end method'
    replacement=f'''.method private {method}({signature})V
    .locals 0
    invoke-static {{{args}}}, Lcom/dycomment/tv/LiveChatController;->{target}(Landroid/app/Activity;{signature})V
    return-void
.end method'''
    text,n=re.subn(pattern,lambda _:replacement,text); assert n==1,method

# Lifecycle hooks keep the notification refresh independent of clock visibility.
for method, target in [('onResume', 'resumed'), ('onPause', 'paused'), ('onDestroy', 'destroyed')]:
    marker=f'    invoke-super {{p0}}, Landroid/app/Activity;->{method}()V'
    assert text.count(marker)==1
    text=text.replace(marker,marker+f'\n    invoke-static {{p0}}, Lcom/dycomment/tv/InteractionController;->{target}(Landroid/app/Activity;)V')

main.write_text(text)
# Avoid offering upstream APKs with a different package/signature as updates.
updater=package/'UpdateHelper.smali'
text,n=re.subn(r'(?ms)^\.method public static checkUpdate\(Landroid/app/Activity;Z\)V\n.*?^\.end method',
    '.method public static checkUpdate(Landroid/app/Activity;Z)V\n    .locals 0\n    return-void\n.end method',updater.read_text())
assert n==1; updater.write_text(text)
# Prefer 720p for a fresh installation on older CPUs. Existing user choice wins.
api=package/'DouyinApi.smali'; text=api.read_text()
start=text.index('.method public static loadVideoQuality('); end=text.index('.end method',start)
method=text[start:end].replace('    invoke-interface {p0, v0, v1}', '    const/4 v1, 0x1\n\n    invoke-interface {p0, v0, v1}')
text=text[:start]+method+text[end:]; api.write_text(text)
manifest=decoded/'AndroidManifest.xml'; text=manifest.read_text()
text=text.replace('package="com.dycomment.tv"','package="com.dycomment.tv.android5"').replace('android:label="myDV Lite"','android:label="myDV Android5"')
text=text.replace('</application>','<activity android:name="com.dycomment.tv.PlaybackSelfTestActivity" android:exported="true" />\n    </application>')
text=text.replace('</application>','<activity android:name="com.dycomment.tv.FollowedLiveActivity" android:exported="false" />\n<activity android:name="com.dycomment.tv.InteractionSelfTestActivity" android:exported="true" />\n</application>')
text=text.replace("</application>",'<activity android:name="com.dycomment.tv.SwitchingSelfTestActivity" android:exported="true" />\n</application>')
text=text.replace('</application>','<activity android:name="com.dycomment.tv.QuickShareActivity" android:exported="false" />\n<activity android:name="com.dycomment.tv.SurfaceCoverTestActivity" android:exported="false" />\n</application>')
manifest.write_text(text)
ids=decoded/'res/values/ids.xml'
names=['android5_interaction_panel','android5_menu_panel','android5_comments_panel'] + [f'android5_menu_row_{i}' for i in range(32)]
ids.write_text(ids.read_text().replace('</resources>', ''.join(f'<id name="{name}" />\n' for name in names)+'</resources>'))
# One API-21 translucent material card, with statistics on the author line.
import xml.etree.ElementTree as ET
ET.register_namespace('android','http://schemas.android.com/apk/res/android')
a='{http://schemas.android.com/apk/res/android}'
layout=decoded/'res/layout/activity_main.xml'; tree=ET.parse(layout)
card=next(e for e in tree.iter() if e.get(a+'id')=='@id/infoOverlay')
row=card[0]; column=row[1]; author=column[0]; stats=card[1]
card.remove(stats); column.remove(author)
header=ET.Element('LinearLayout',{a+'orientation':'horizontal',a+'gravity':'center_vertical',a+'layout_width':'match_parent',a+'layout_height':'wrap_content'})
author.set(a+'layout_width','wrap_content'); author.set(a+'maxWidth','180dp'); author.set(a+'maxLines','1'); author.set(a+'ellipsize','end')
for key in ['background','paddingTop','paddingBottom','paddingStart','paddingEnd','layout_marginTop']:
    stats.attrib.pop(a+key,None)
stats.set(a+'layout_marginStart','12dp'); stats.set(a+'layout_width','0dp'); stats.set(a+'layout_weight','1'); stats.set(a+'textSize','12sp')
header.extend([author,stats]); column.insert(0,header)
for key in ['paddingBottom','paddingStart','paddingEnd']: card.attrib.pop(a+key,None)
card.set(a+'padding','0dp'); card.set(a+'layout_marginStart','18dp'); card.set(a+'layout_marginEnd','18dp'); card.set(a+'layout_marginBottom','48dp')
column.set(a+'padding','12dp'); column.set(a+'background','@drawable/android5_video_card')
column[1].set(a+'textColor','#e6ffffff')
tree.write(layout,encoding='utf-8',xml_declaration=True)
(decoded/'res/drawable/android5_video_card.xml').write_text('''<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">
  <corners android:radius="12dp" />
  <gradient android:angle="90" android:startColor="#c018202b" android:endColor="#a0303945" />
  <stroke android:width="1dp" android:color="#30ffffff" />
</shape>''')
config=decoded/'apktool.yml' ; text=config.read_text().replace('versionCode: 9','versionCode: 1004').replace('versionName: 1.1.9','versionName: 1.1.9-a5.4')
assert 'minSdkVersion: 21' in text; config.write_text(text)
assets=decoded/'assets'; assets.mkdir(exist_ok=True)
shutil.copy(ROOT/'LIBVLC-LICENSE.txt',assets/'LIBVLC-LICENSE.txt')
shutil.copy(ROOT/'README.md',assets/'ANDROID5-SOURCES.md')
for profile,width,height,name in [('high',1280,720,'high'),('baseline',640,360,'baseline'),('baseline',360,640,'portrait')]:
    run('ffmpeg','-hide_banner','-loglevel','error','-y','-f','lavfi','-i',f'testsrc2=size={width}x{height}:rate=25',
        '-f','lavfi','-i','sine=frequency=440:sample_rate=44100','-t','16','-c:v','libx264','-preset','veryfast','-crf','30',
        '-profile:v',profile,'-level:v','4.1' if profile=='high' else '3.0','-pix_fmt','yuv420p','-c:a','aac','-b:a','64k','-movflags','+faststart',assets/f'selftest-{name}.mp4')
shutil.copytree(WORK/'vlc/jni',decoded/'lib',dirs_exist_ok=True)
classes=WORK/'classes'; shutil.rmtree(classes, ignore_errors=True); classes.mkdir(exist_ok=True)
sources=list((ROOT/'src').rglob('*.java'))
run('javac','-source','8','-target','8','-encoding','UTF-8','-classpath',str(ANDROID)+os.pathsep+str(WORK/'vlc/classes.jar'),'-d',classes,*sources)
compiled=WORK/'compiled.jar'
with zipfile.ZipFile(compiled,'w') as z:
    for f in classes.rglob('*.class'): z.write(f,f.relative_to(classes))
run('java','-jar',apktool,'b',decoded,'-o',WORK/'base.apk')
with zipfile.ZipFile(WORK/'base.apk') as z: (WORK/'original.dex').write_bytes(z.read('classes.dex'))
dexdir=WORK/'dex'; dexdir.mkdir(exist_ok=True)
run(BT/'d8','--min-api','21','--lib',ANDROID,'--output',dexdir,WORK/'original.dex',compiled,WORK/'vlc/classes.jar')
unsigned=WORK/'unsigned.apk'
with zipfile.ZipFile(WORK/'base.apk') as source,zipfile.ZipFile(unsigned,'w',zipfile.ZIP_DEFLATED) as out:
    for item in source.infolist():
        if not re.fullmatch(r'classes\d*\.dex',item.filename) and not item.filename.startswith('META-INF/'):
            out.writestr(item,source.read(item.filename))
    for f in dexdir.glob('*.dex'): out.write(f,f.name)
aligned=WORK/'aligned.apk'; run(BT/'zipalign','-f','4',unsigned,aligned)
keystore=os.environ.get('ANDROID5_KEYSTORE',str(WORK/'test-only.p12'))
password=os.environ.get('ANDROID5_KEYSTORE_PASSWORD','android5-build')
if not pathlib.Path(keystore).exists():
    run('keytool','-genkeypair','-keystore',keystore,'-storetype','PKCS12','-storepass',password,'-keypass',password,
        '-alias','android5','-keyalg','RSA','-keysize','3072','-validity','10000','-dname','CN=myDV Android5 Community')
apk=DIST/'myDV-Android5-1.1.9-a5.4.apk'
os.environ['BUILD_SIGN_PASSWORD']=password
run(BT/'apksigner','sign','--ks',keystore,'--ks-key-alias','android5','--ks-pass','env:BUILD_SIGN_PASSWORD','--min-sdk-version','21','--out',apk,aligned)
run(BT/'apksigner','verify','--verbose','--min-sdk-version','21',apk)
shutil.copy(bindings,DIST/bindings.name)
shutil.copy(ROOT/'LIBVLC-LICENSE.txt',DIST/'LIBVLC-LICENSE.txt')
(DIST/'SHA256SUMS.txt').write_text(hashlib.sha256(apk.read_bytes()).hexdigest()+'  '+apk.name+'\n')
print('BUILT',apk)
