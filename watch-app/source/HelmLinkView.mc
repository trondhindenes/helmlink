import Toybox.Graphics;
import Toybox.Lang;
import Toybox.WatchUi;

class HelmLinkView extends WatchUi.View {

    function initialize() {
        View.initialize();
    }

    function onUpdate(dc as Graphics.Dc) as Void {
        var width = dc.getWidth();
        var cx = width / 2;

        dc.setColor(Graphics.COLOR_BLACK, Graphics.COLOR_BLACK);
        dc.clear();

        // Connection indicator dot
        if (AutopilotState.phoneConnected) {
            dc.setColor(0x00AA00, 0x00AA00);
        } else {
            dc.setColor(0xAA0000, 0xAA0000);
        }
        dc.fillCircle(cx, Layout.scaleY(18), Layout.scaleY(5));

        // Title
        dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_TRANSPARENT);
        dc.drawText(cx, Layout.scaleY(30), Graphics.FONT_SMALL, "HELMLINK", Graphics.TEXT_JUSTIFY_CENTER);

        // Mode with color coding
        dc.setColor(getModeColor(), Graphics.COLOR_TRANSPARENT);
        dc.drawText(cx, Layout.scaleY(75), Graphics.FONT_MEDIUM, getModeDisplay(), Graphics.TEXT_JUSTIFY_CENTER);

        // Heading
        var headingStr = AutopilotState.heading.format("%03d");
        dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_TRANSPARENT);
        dc.drawText(cx, Layout.scaleY(120), Graphics.FONT_NUMBER_MEDIUM, headingStr, Graphics.TEXT_JUSTIFY_CENTER);

        // Increment selector buttons
        var btnW = Layout.btnW();
        var btnH = Layout.btnH();
        var btnY = Layout.btnY();
        var btn1X = Layout.btn1X();
        var btn10X = Layout.btn10X();

        if (AutopilotState.increment == 1) {
            dc.setColor(0x5588FF, 0x5588FF);
        } else {
            dc.setColor(0x333333, 0x333333);
        }
        dc.fillRoundedRectangle(btn1X, btnY, btnW, btnH, 6);
        dc.setColor(AutopilotState.increment == 1 ? Graphics.COLOR_WHITE : Graphics.COLOR_LT_GRAY, Graphics.COLOR_TRANSPARENT);
        dc.drawText(btn1X + btnW / 2, btnY + Layout.scaleY(2), Graphics.FONT_SMALL, "+/-1", Graphics.TEXT_JUSTIFY_CENTER);

        if (AutopilotState.increment == 10) {
            dc.setColor(0x5588FF, 0x5588FF);
        } else {
            dc.setColor(0x333333, 0x333333);
        }
        dc.fillRoundedRectangle(btn10X, btnY, btnW, btnH, 6);
        dc.setColor(AutopilotState.increment == 10 ? Graphics.COLOR_WHITE : Graphics.COLOR_LT_GRAY, Graphics.COLOR_TRANSPARENT);
        dc.drawText(btn10X + btnW / 2, btnY + Layout.scaleY(2), Graphics.FONT_SMALL, "+/-10", Graphics.TEXT_JUSTIFY_CENTER);

        // Status badge
        var badgeY = Layout.scaleY(265);
        var badgeW = Layout.scaleX(180);
        var badgeH = Layout.scaleY(44);

        if (!AutopilotState.phoneConnected) {
            dc.setColor(0x880000, 0x880000);
            dc.fillRoundedRectangle(cx - badgeW / 2, badgeY, badgeW, badgeH, 8);
            dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_TRANSPARENT);
            dc.drawText(cx, badgeY + Layout.scaleY(4), Graphics.FONT_MEDIUM, "NO PHONE", Graphics.TEXT_JUSTIFY_CENTER);
        } else if (AutopilotState.pending) {
            dc.setColor(0xCC8800, 0xCC8800);
            dc.fillRoundedRectangle(cx - badgeW / 2, badgeY, badgeW, badgeH, 8);
            dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_TRANSPARENT);
            dc.drawText(cx, badgeY + Layout.scaleY(4), Graphics.FONT_MEDIUM, "WAIT...", Graphics.TEXT_JUSTIFY_CENTER);
        } else if (AutopilotState.engaged) {
            dc.setColor(0x00AA00, 0x00AA00);
            dc.fillRoundedRectangle(cx - badgeW / 2, badgeY, badgeW, badgeH, 8);
            dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_TRANSPARENT);
            dc.drawText(cx, badgeY + Layout.scaleY(4), Graphics.FONT_MEDIUM, "ENGAGED", Graphics.TEXT_JUSTIFY_CENTER);
        } else {
            dc.setColor(0x880000, 0x880000);
            dc.fillRoundedRectangle(cx - badgeW / 2, badgeY, badgeW, badgeH, 8);
            dc.setColor(Graphics.COLOR_LT_GRAY, Graphics.COLOR_TRANSPARENT);
            dc.drawText(cx, badgeY + Layout.scaleY(4), Graphics.FONT_MEDIUM, "STANDBY", Graphics.TEXT_JUSTIFY_CENTER);
        }
    }

    private function getModeDisplay() as String {
        switch (AutopilotState.mode) {
            case AutopilotState.MODE_NO_DRIFT:
                return "NO DRIFT";
            default:
                return AutopilotState.getModeName();
        }
    }

    private function getModeColor() as Number {
        switch (AutopilotState.mode) {
            case AutopilotState.MODE_AUTO:
                return 0x5588FF;
            case AutopilotState.MODE_WIND:
                return 0x00CCCC;
            case AutopilotState.MODE_NO_DRIFT:
                return 0xFFAA00;
            default:
                return Graphics.COLOR_WHITE;
        }
    }
}
