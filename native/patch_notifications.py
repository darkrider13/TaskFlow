import re, sys, glob

# Make the notification buttons (Yes / No / Snooze) go to our silent receiver instead of opening the app.
files = glob.glob('node_modules/@capacitor/local-notifications/android/src/main/java/**/LocalNotificationManager.java', recursive=True)
if not files:
    sys.exit('ERROR: LocalNotificationManager.java not found')
p = files[0]
s = open(p, encoding='utf-8').read()

if 'com.habit.tracker.NotifActionReceiver' in s:
    print('already patched')
    sys.exit(0)

if 'localNotification.getSource()' not in s:
    sys.exit('ERROR: plugin source changed (getSource not found), patch needs an update')

pat = re.compile(r'PendingIntent\s+actionPendingIntent\s*=\s*PendingIntent\.getActivity\(.*?\);', re.S)
new = '''Intent habitxIntent = new Intent();
          habitxIntent.setClassName(context.getPackageName(), "com.habit.tracker.NotifActionReceiver");
          habitxIntent.setAction("habitx.ACT." + localNotification.getId() + "." + notificationAction.getId());
          habitxIntent.putExtra("nid", localNotification.getId());
          habitxIntent.putExtra("aid", notificationAction.getId());
          habitxIntent.putExtra("src", localNotification.getSource());
          PendingIntent actionPendingIntent = PendingIntent.getBroadcast(
            context,
            localNotification.getId() + notificationAction.getId().hashCode(),
            habitxIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
          );'''
s2, n = pat.subn(lambda m: new, s, count=1)
if n != 1:
    sys.exit('ERROR: action PendingIntent block not found, patch needs an update')
open(p, 'w', encoding='utf-8').write(s2)
print('patched', p)
