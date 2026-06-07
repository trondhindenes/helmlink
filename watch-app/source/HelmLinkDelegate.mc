import Toybox.Lang;
import Toybox.System;
import Toybox.WatchUi;
import Toybox.Attention;

class HelmLinkDelegate extends WatchUi.InputDelegate {

    function initialize() {
        InputDelegate.initialize();
    }

    function onKey(keyEvent as WatchUi.KeyEvent) as Boolean {
        var key = keyEvent.getKey();

        if (key == WatchUi.KEY_ENTER || key == WatchUi.KEY_START) {
            if (!AutopilotState.phoneConnected) {
                rejectInput();
                return true;
            }
            AutopilotState.pending = true;
            AutopilotState.pendingStartTime = System.getTimer();
            AutopilotState.cmdSeq++;
            if (AutopilotState.engaged) {
                CommManager.sendCommand({"cmd" => "DISENGAGE", "seq" => AutopilotState.cmdSeq});
            } else {
                CommManager.sendCommand({"cmd" => "ENGAGE", "mode" => AutopilotState.getModeName(), "seq" => AutopilotState.cmdSeq});
            }
            vibrateEngage();
            WatchUi.requestUpdate();
            return true;
        }

        if (key == WatchUi.KEY_UP) {
            if (!AutopilotState.phoneConnected) {
                rejectInput();
                return true;
            }
            if (AutopilotState.engaged) {
                AutopilotState.adjustHeading(AutopilotState.increment);
                AutopilotState.cmdSeq++;
                CommManager.sendCommand({"cmd" => "ADJUST", "heading" => AutopilotState.heading, "seq" => AutopilotState.cmdSeq});
                WatchUi.requestUpdate();
            }
            return true;
        }

        if (key == WatchUi.KEY_DOWN) {
            if (!AutopilotState.phoneConnected) {
                rejectInput();
                return true;
            }
            if (AutopilotState.engaged) {
                AutopilotState.adjustHeading(-AutopilotState.increment);
                AutopilotState.cmdSeq++;
                CommManager.sendCommand({"cmd" => "ADJUST", "heading" => AutopilotState.heading, "seq" => AutopilotState.cmdSeq});
                WatchUi.requestUpdate();
            }
            return true;
        }

        if (key == WatchUi.KEY_ESC) {
            WatchUi.popView(WatchUi.SLIDE_RIGHT);
            return true;
        }

        return false;
    }

    function onTap(clickEvent as WatchUi.ClickEvent) as Boolean {
        var coords = clickEvent.getCoordinates();
        var x = coords[0];
        var y = coords[1];

        var cx = 195;
        var btnW = 75;
        var btnH = 32;
        var btnY = 225;
        var btn1X = cx - btnW - 5;
        var btn10X = cx + 5;

        if (y >= btnY && y <= btnY + btnH) {
            if (x >= btn1X && x <= btn1X + btnW) {
                AutopilotState.increment = 1;
                if (Attention has :vibrate) {
                    Attention.vibrate([new Attention.VibeProfile(25, 50)]);
                }
                WatchUi.requestUpdate();
                return true;
            }
            if (x >= btn10X && x <= btn10X + btnW) {
                AutopilotState.increment = 10;
                if (Attention has :vibrate) {
                    Attention.vibrate([new Attention.VibeProfile(25, 50)]);
                }
                WatchUi.requestUpdate();
                return true;
            }
        }
        return false;
    }

    function onSwipe(swipeEvent as WatchUi.SwipeEvent) as Boolean {
        if (!AutopilotState.phoneConnected) {
            rejectInput();
            return true;
        }
        AutopilotState.pending = true;
        AutopilotState.pendingStartTime = System.getTimer();
        AutopilotState.cycleMode();
        AutopilotState.cmdSeq++;
        CommManager.sendCommand({"cmd" => "MODE", "mode" => AutopilotState.getModeName(), "seq" => AutopilotState.cmdSeq});
        vibrateMode();
        WatchUi.requestUpdate();
        return true;
    }

    // Short double-buzz: command rejected because the phone is not connected
    private function rejectInput() as Void {
        if (Attention has :vibrate) {
            Attention.vibrate([
                new Attention.VibeProfile(75, 100),
                new Attention.VibeProfile(0, 80),
                new Attention.VibeProfile(75, 100)
            ]);
        }
        WatchUi.requestUpdate();
    }

    private function vibrateEngage() as Void {
        if (Attention has :playTone) {
            Attention.playTone(Attention.TONE_KEY);
        }
        if (Attention has :vibrate) {
            Attention.vibrate([new Attention.VibeProfile(50, 100)]);
        }
    }

    private function vibrateMode() as Void {
        if (Attention has :playTone) {
            Attention.playTone(Attention.TONE_KEY);
        }
        if (Attention has :vibrate) {
            Attention.vibrate([new Attention.VibeProfile(25, 100)]);
        }
    }
}
