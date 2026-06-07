import Toybox.Lang;

(:glance)
module AutopilotState {
    const MODE_AUTO = 0;
    const MODE_WIND = 1;
    const MODE_NO_DRIFT = 2;
    const NUM_MODES = 3;

    var engaged as Boolean = false;
    var heading as Number = 270;
    var mode as Number = MODE_AUTO;
    var phoneConnected as Boolean = false;
    var lastPongTime as Number = 0;
    var pending as Boolean = false;
    var pendingStartTime as Number = 0;
    var cmdSeq as Number = 0;
    var increment as Number = 1;

    function toggleEngage() as Void {
        engaged = !engaged;
    }

    function adjustHeading(degrees as Number) as Void {
        heading = (heading + degrees + 360) % 360;
    }

    function cycleMode() as Void {
        mode = (mode + 1) % NUM_MODES;
    }

    function getModeName() as String {
        switch (mode) {
            case MODE_AUTO:
                return "AUTO";
            case MODE_WIND:
                return "WIND";
            case MODE_NO_DRIFT:
                return "NO_DRIFT";
            default:
                return "---";
        }
    }
}
