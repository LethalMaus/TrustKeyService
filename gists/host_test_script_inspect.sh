adb shell am broadcast \
  -n "$RECEIVER" \
  -a "$PACKAGE.action.EXECUTE_COMMAND" \
  --es request_id "$request_id" \
  --es operation inspect \
  --es alias "$alias" >/dev/null

adb logcat -d -s "$TAG:I"
