import Toybox.Application;
import Toybox.Attention;
import Toybox.Communications;
import Toybox.Lang;
import Toybox.System;
import Toybox.Timer;
import Toybox.WatchUi;

(:glance)
class HelmLinkApp extends Application.AppBase {

    private var _pingTimer as Timer.Timer or Null;

    function initialize() {
        AppBase.initialize();
    }

    function onStart(state as Dictionary?) as Void {
        Communications.registerForPhoneAppMessages(method(:onPhoneMessage));
        _pingTimer = new Timer.Timer();
        _pingTimer.start(method(:onPingTick), 5000, true);
    }

    function onStop(state as Dictionary?) as Void {
        if (_pingTimer != null) {
            _pingTimer.stop();
            _pingTimer = null;
        }
    }

    function onPingTick() as Void {
        CommManager.sendCommand({"cmd" => "PING"});
        var now = System.getTimer();
        if (AutopilotState.lastPongTime > 0 && (now - AutopilotState.lastPongTime) > 12000) {
            AutopilotState.phoneConnected = false;
            WatchUi.requestUpdate();
        }
        if (AutopilotState.pending && AutopilotState.pendingStartTime > 0
                && (now - AutopilotState.pendingStartTime) > 15000) {
            AutopilotState.pending = false;
            WatchUi.requestUpdate();
        }
    }

    function onPhoneMessage(msg as Communications.PhoneAppMessage) as Void {
        var data = msg.data;
        if (data instanceof Dictionary) {
            if (data.hasKey("pong")) {
                var wasConnected = AutopilotState.phoneConnected;
                AutopilotState.phoneConnected = true;
                AutopilotState.lastPongTime = System.getTimer();
                if (!wasConnected) {
                    AutopilotState.cmdSeq = 0;
                }
            }

            var ackSeq = 0;
            if (data.hasKey("ack_seq")) {
                ackSeq = data["ack_seq"] as Number;
            }
            var allAcked = (ackSeq >= AutopilotState.cmdSeq);
            var isConfirmed = data.hasKey("confirmed");

            if (isConfirmed || allAcked) {
                if (data.hasKey("engaged")) {
                    var wasEngaged = AutopilotState.engaged;
                    AutopilotState.engaged = data["engaged"] as Boolean;

                    if (AutopilotState.pending) {
                        AutopilotState.pending = false;
                        if (AutopilotState.engaged != wasEngaged) {
                            playConfirmTone();
                        }
                    }
                }
                if (data.hasKey("mode")) {
                    var modeName = data["mode"] as String;
                    if (modeName.equals("AUTO")) {
                        AutopilotState.mode = AutopilotState.MODE_AUTO;
                    } else if (modeName.equals("WIND")) {
                        AutopilotState.mode = AutopilotState.MODE_WIND;
                    } else if (modeName.equals("NO_DRIFT")) {
                        AutopilotState.mode = AutopilotState.MODE_NO_DRIFT;
                    }
                    if (AutopilotState.pending) {
                        AutopilotState.pending = false;
                    }
                }
            }

            if (allAcked && data.hasKey("heading")) {
                AutopilotState.heading = data["heading"] as Number;
            }

            WatchUi.requestUpdate();
        }
    }

    private function playConfirmTone() as Void {
        if (Attention has :playTone) {
            if (AutopilotState.engaged) {
                Attention.playTone(Attention.TONE_START);
            } else {
                Attention.playTone(Attention.TONE_STOP);
            }
        }
        if (Attention has :vibrate) {
            Attention.vibrate([new Attention.VibeProfile(50, 200)]);
        }
    }

    function getInitialView() {
        return [new HelmLinkView(), new HelmLinkDelegate()];
    }

    (:glance)
    function getGlanceView() {
        return [new HelmLinkGlanceView()];
    }
}
