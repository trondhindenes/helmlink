import Toybox.Graphics;
import Toybox.Lang;
import Toybox.WatchUi;

(:glance)
class HelmLinkGlanceView extends WatchUi.GlanceView {

    function initialize() {
        GlanceView.initialize();
    }

    function onUpdate(dc as Graphics.Dc) as Void {
        dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_BLACK);
        dc.clear();

        var height = dc.getHeight();

        // Title line, like native glances
        dc.setColor(Graphics.COLOR_LT_GRAY, Graphics.COLOR_TRANSPARENT);
        dc.drawText(0, height / 4, Graphics.FONT_GLANCE, "HELMLINK", Graphics.TEXT_JUSTIFY_LEFT | Graphics.TEXT_JUSTIFY_VCENTER);

        // Status line
        var status = AutopilotState.engaged ? "ON" : "OFF";
        var text = status + " " + AutopilotState.heading.format("%03d") + " " + AutopilotState.getModeName();
        dc.setColor(AutopilotState.engaged ? Graphics.COLOR_GREEN : Graphics.COLOR_WHITE, Graphics.COLOR_TRANSPARENT);
        dc.drawText(0, height * 3 / 4, Graphics.FONT_GLANCE, text, Graphics.TEXT_JUSTIFY_LEFT | Graphics.TEXT_JUSTIFY_VCENTER);
    }
}
