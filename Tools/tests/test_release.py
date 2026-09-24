import sys
from pathlib import Path
import unittest
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from check_release import validate
from extract_prohibited import extract

class ReleaseTest(unittest.TestCase):
    def args(self):
        return dict(badging="package: name='my.nicegram.vr' versionCode='7089049' versionName='0.1.0 (Telegram 12.10.3)'\napplication-label:'Nicegram VR'\nminSdkVersion:'29'\ntargetSdkVersion:'34'\nuses-permission: name='android.permission.RECORD_AUDIO'",
          manifest='''E: manifest
  A: android:installLocation(0x010102b7)=0
  E: application
    A: android:name(0x01010003)="org.telegram.messenger.QuestApplicationLoader"
    E: meta-data
      A: android:name(0x01010003)="com.oculus.supportedDevices"
      A: android:value(0x01010024)="quest3|quest3s"
    E: activity
      A: android:name(0x01010003)="org.telegram.ui.LaunchActivity"
      A: android:excludeFromRecents(0x01010017)=true
      E: layout
        A: android:defaultWidth(0x010104f4)=420dp
        A: android:defaultHeight(0x010104f5)=720dp
        A: android:minWidth(0x0101013f)=360dp
        A: android:minHeight(0x01010140)=480dp
''', signature='Verified using v2 scheme (APK Signature Scheme v2): true\nSigner #1 certificate SHA-256 digest: abc123',
          members=['lib/arm64-v8a/libtmessages.so'],size=60000000,
          prohibited=['READ_CONTACTS'],tag='v0.1.0-rc.1',expected_cert='abc123',previous_code=7089039)
    def test_valid(self): self.assertEqual('PASS',validate(**self.args())['packaging_checks'])
    def test_rejects_each_broken_artifact(self):
        for field,value in [('signature',''),('expected_cert','def456'),('prohibited',[]),('members',['lib/x86/libx.so']),('size',1000000000),('tag','v0.1.01'),('previous_code',7089049),('manifest',''),('badging',self.args()['badging']+'\napplication-debuggable')]:
            with self.subTest(field=field):
                args=self.args();args[field]=value
                with self.assertRaises(ValueError): validate(**args)
    def test_target_must_fit_the_new_app_requirement(self):
        args=self.args();args['badging']=args['badging'].replace("targetSdkVersion:'34'", "targetSdkVersion:'36'")
        with self.assertRaises(ValueError): validate(**args)

    def test_recents_on_another_activity_does_not_count(self):
        args=self.args();args['manifest']=args['manifest'].replace('org.telegram.ui.LaunchActivity','org.telegram.ui.OtherActivity')
        with self.assertRaises(ValueError): validate(**args)

    def test_prohibited_fails(self):
        args=self.args();args['badging']+="\nuses-permission: name='android.permission.READ_CONTACTS'"
        with self.assertRaises(ValueError): validate(**args)
    def test_constants_outside_the_table_are_not_policy(self):
        html='<script>FAKE_PERMISSION</script><table><tr><td><code>READ_CONTACTS</code></td><td><code>INSTALL_PACKAGES</code></td><td><code>CALL_PHONE</code></td></tr></table>'
        self.assertEqual(['CALL_PHONE','INSTALL_PACKAGES','READ_CONTACTS'],extract(html))
    def test_missing_policy_fails(self):
        with self.assertRaises(ValueError): extract('<html>READ_CONTACTS INSTALL_PACKAGES CALL_PHONE</html>')

if __name__=='__main__': unittest.main()
